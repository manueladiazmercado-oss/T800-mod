package dev.t800.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Optional opponent intelligence. Uses in-game observations by default and only
 * calls third-party APIs asynchronously. External statistics are never treated
 * as proof of skill or as a guaranteed prediction.
 */
public final class OpponentIntelService {
    public static final OpponentIntelService INSTANCE = new OpponentIntelService();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final AtomicReference<Profile> profile = new AtomicReference<>(Profile.empty());
    private volatile String requestedName = "";
    private volatile long lastRequest;
    private volatile String status = "PROFILE LOOKUP IDLE";

    public record Profile(String username, String uuid, String namemcUrl, String donutSummary, String status) {
        static Profile empty() { return new Profile("-", "-", "", "Donut stats not loaded", "PROFILE IDLE"); }
        public String hudLine() {
            String s = username + " // " + status;
            if (!donutSummary.isBlank()) s += " // " + donutSummary;
            return s;
        }
    }

    private Path apiKeyPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("t800hud/donutstats.key");
    }

    public void observeTarget(LivingEntity target) {
        if (!(target instanceof Player player)) {
            requestedName = "";
            profile.set(Profile.empty());
            status = "NO PLAYER TARGET";
            return;
        }
        String name = player.getName().getString();
        if (name.equalsIgnoreCase(requestedName) && System.currentTimeMillis() - lastRequest < 60_000) return;
        requestedName = name;
        lastRequest = System.currentTimeMillis();
        String uuid = player.getUUID().toString();
        String url = "https://namemc.com/profile/" + uuid;
        profile.set(new Profile(name, uuid, url, "", "LOOKUP..."));
        status = "LOOKUP...";
        lookupDonut(name, uuid, url);
    }

    private void lookupDonut(String name, String uuid, String nameMcUrl) {
        CompletableFuture.runAsync(() -> {
            String key = readApiKey();
            if (key.isBlank()) {
                Profile p = new Profile(name, uuid, nameMcUrl, "", "UUID OBSERVED / API KEY NOT SET");
                profile.set(p); status = p.status();
                return;
            }
            try {
                String endpoint = "https://api.donutstats.io/v1/players/" + name + "?lookup=none";
                HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(8))
                        .header("x-api-key", key)
                        .header("Accept", "application/json")
                        .GET().build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (res.statusCode() < 200 || res.statusCode() >= 300) {
                    Profile p = new Profile(name, uuid, nameMcUrl, "", "DONUT API HTTP " + res.statusCode());
                    profile.set(p); status = p.status(); return;
                }
                JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
                JsonObject player = root.has("player") && root.get("player").isJsonObject()
                        ? root.getAsJsonObject("player") : new JsonObject();
                JsonObject stats = player.has("stats") && player.get("stats").isJsonObject()
                        ? player.getAsJsonObject("stats") : new JsonObject();
                String kills = number(stats, "kills");
                String deaths = number(stats, "deaths");
                String playtime = number(stats, "playtime");
                String money = number(stats, "money");
                String summary = "DONUT K " + kills + " D " + deaths + " PT " + playtime + "s";
                if (!money.equals("?")) summary += " $ " + money;
                String state = player.size() == 0 ? "DONUT PROFILE NOT FOUND" : "DONUT DATA (UNOFFICIAL TRACKER)";
                Profile p = new Profile(name, uuid, nameMcUrl, summary, state);
                profile.set(p); status = state;
            } catch (Exception ex) {
                Profile p = new Profile(name, uuid, nameMcUrl, "", "API UNAVAILABLE");
                profile.set(p); status = p.status();
            }
        });
    }

    private String readApiKey() {
        try {
            Path p = apiKeyPath();
            if (!Files.exists(p)) return "";
            return Files.readString(p, StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) { return ""; }
    }

    private static String number(JsonObject o, String key) {
        try { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "?"; }
        catch (Exception ignored) { return "?"; }
    }

    public Profile profile() { return profile.get(); }
    public String status() { return status; }

    public static String detectMode(Minecraft mc) {
        if (Config.pvpMode != null && !Config.pvpMode.equals("AUTO")) return Config.pvpMode;
        if (mc.getCurrentServer() == null) return "SMP";
        String server = mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        if (server.contains("pvpclub") || server.contains("pvp.club")) return "PVP CLUB";
        if (server.contains("donut")) return "SMP";
        return "SMP";
    }

    public static String advice(String mode, LivingEntity target, double distance, boolean projectileNearby) {
        String item = target == null ? "" : target.getMainHandItem().getHoverName().getString().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "SMP" -> projectileNearby ? "SMP: COVER; WATCH CRYSTALS / ARROWS"
                    : distance < 4 ? "SMP: STRAFE; WATCH CRYSTAL PLACEMENT"
                    : "SMP: TRACK GEAR; KEEP ESCAPE ROUTE";
            case "CRYSTAL" -> "CRYSTAL: WATCH OBSIDIAN / CRYSTAL PLACEMENT";
            case "SWORD" -> distance < 4 ? "SWORD: STRAFE; TIME HITS" : "SWORD: CLOSE DISTANCE CAREFULLY";
            case "AXE" -> "AXE: WATCH SHIELD DISABLE / COOLDOWN";
            case "POT" -> "POT: TRACK HEALING / SPLASH POTIONS";
            case "UHC" -> projectileNearby ? "UHC: DODGE PROJECTILES; USE COVER" : "UHC: TRACK BOW AND HEALING";
            case "PVP CLUB" -> item.contains("bow") ? "PVP CLUB: TRACK RANGED SHOTS" : "PVP CLUB: USE MODE PRESET";
            default -> projectileNearby ? "DODGE PROJECTILES; CHECK COVER" : "OBSERVE ATTACK RHYTHM";
        };
    }
}
