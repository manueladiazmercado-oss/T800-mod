package dev.t800.hud;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Local-only target dossiers and a compact observation replay log.
 * Only data visible to this client is recorded; no packets are sent and no external lookups are made.
 */
public final class TargetDossierService {
    public static final TargetDossierService INSTANCE = new TargetDossierService();
    private static final int MAX_PROFILES = 500;
    private static final int MAX_EVENTS = 240;
    private final Map<String, Dossier> dossiers = new LinkedHashMap<>();
    private final List<ReplayEvent> events = new ArrayList<>();
    private boolean loaded;
    private String activeKey = "";
    private float lastTargetHealth = -1;
    private float lastSelfHealth = -1;
    private long lastSampleAt;
    private long lastSaveAt;

    public static final class Dossier {
        public String uuid = "";
        public String name = "UNKNOWN";
        public long firstSeen;
        public long lastSeen;
        public int sightings;
        public int observedLocks;
        public int observedHealthDrops;
        public int observedDamageTakenEvents;
        public double observedTargetHealthLoss;
        public double observedSelfHealthLoss;
        public String server = "Singleplayer";
        public String dimension = "Unknown";
        public int x, y, z;
        public String lastHeldItem = "Unknown";
        public String lastPattern = "NO DATA";
    }

    public record ReplayEvent(long time, String uuid, String name, String kind, String detail) {
        public String display() {
            return String.format(Locale.ROOT, "%tT  %s: %s", time, kind, detail);
        }
    }

    private Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("t800hud-target-dossiers.properties");
    }

    public synchronized void tick(Minecraft mc, LivingEntity target) {
        load();
        if (mc.player == null || mc.level == null || !(target instanceof Player player) || !player.isAlive()) {
            if (!activeKey.isEmpty()) {
                addEvent(activeKey, "TARGET LOST", "Target lock ended");
                activeKey = "";
                lastTargetHealth = -1;
                lastSelfHealth = -1;
                saveIfDue(true);
            }
            return;
        }

        String uuid = player.getUUID().toString();
        long now = System.currentTimeMillis();
        Dossier d = dossiers.computeIfAbsent(uuid, k -> {
            Dossier fresh = new Dossier();
            fresh.uuid = k;
            fresh.name = player.getName().getString();
            fresh.firstSeen = now;
            return fresh;
        });
        d.name = player.getName().getString();
        d.lastSeen = now;
        d.server = mc.getCurrentServer() == null ? "Singleplayer" : mc.getCurrentServer().ip;
        d.dimension = mc.level.dimension().identifier().toString();
        d.x = player.blockPosition().getX();
        d.y = player.blockPosition().getY();
        d.z = player.blockPosition().getZ();
        d.lastHeldItem = player.getMainHandItem().isEmpty() ? "Empty" : player.getMainHandItem().getHoverName().getString();

        if (!uuid.equals(activeKey)) {
            activeKey = uuid;
            d.sightings++;
            d.observedLocks++;
            lastTargetHealth = player.getHealth();
            lastSelfHealth = mc.player.getHealth();
            lastSampleAt = now;
            addEvent(uuid, "TARGET LOCK", "Acquired " + d.name + " at " + d.x + " " + d.y + " " + d.z);
        } else if (now - lastSampleAt >= 250) {
            float hp = player.getHealth();
            float selfHp = mc.player.getHealth();
            if (lastTargetHealth >= 0 && hp < lastTargetHealth - 0.05f) {
                double loss = lastTargetHealth - hp;
                d.observedHealthDrops++;
                d.observedTargetHealthLoss += loss;
                addEvent(uuid, "TARGET HP", String.format(Locale.ROOT, "-%.1f observed (not necessarily dealt by you)", loss));
            }
            if (lastSelfHealth >= 0 && selfHp < lastSelfHealth - 0.05f) {
                double loss = lastSelfHealth - selfHp;
                d.observedDamageTakenEvents++;
                d.observedSelfHealthLoss += loss;
                addEvent(uuid, "SELF HP", String.format(Locale.ROOT, "-%.1f while target tracked", loss));
            }
            lastTargetHealth = hp;
            lastSelfHealth = selfHp;
            lastSampleAt = now;
        }

        CombatAIService ai = CombatAIService.INSTANCE;
        d.lastPattern = ai.patternSummary(player);
        if (d.lastPattern.length() > 180) d.lastPattern = d.lastPattern.substring(0, 180);
        trim();
        saveIfDue(false);
    }

    public synchronized void recordTermination(LivingEntity target) {
        if (target == null) return;
        load();
        String uuid = target.getUUID().toString();
        Dossier d = dossiers.get(uuid);
        if (d != null) {
            addEvent(uuid, "TARGET DOWN", "Target died while tracked");
            saveIfDue(true);
        }
    }

    public synchronized Dossier currentDossier() {
        load();
        Dossier d = dossiers.get(activeKey);
        return d == null ? null : copy(d);
    }

    public synchronized List<Dossier> recentDossiers(int limit) {
        load();
        return dossiers.values().stream()
                .sorted(Comparator.comparingLong((Dossier d) -> d.lastSeen).reversed())
                .limit(Math.max(1, limit)).map(TargetDossierService::copy).toList();
    }

    public synchronized List<ReplayEvent> recentEvents(String uuid, int limit) {
        load();
        return events.stream().filter(e -> uuid == null || uuid.isBlank() || e.uuid().equals(uuid))
                .sorted(Comparator.comparingLong(ReplayEvent::time).reversed())
                .limit(Math.max(1, limit)).toList();
    }

    public synchronized String activeUuid() { return activeKey; }

    private static Dossier copy(Dossier source) {
        Dossier d = new Dossier();
        d.uuid = source.uuid; d.name = source.name; d.firstSeen = source.firstSeen; d.lastSeen = source.lastSeen;
        d.sightings = source.sightings; d.observedLocks = source.observedLocks;
        d.observedHealthDrops = source.observedHealthDrops; d.observedDamageTakenEvents = source.observedDamageTakenEvents;
        d.observedTargetHealthLoss = source.observedTargetHealthLoss; d.observedSelfHealthLoss = source.observedSelfHealthLoss;
        d.server = source.server; d.dimension = source.dimension; d.x = source.x; d.y = source.y; d.z = source.z;
        d.lastHeldItem = source.lastHeldItem; d.lastPattern = source.lastPattern;
        return d;
    }

    private void addEvent(String uuid, String kind, String detail) {
        Dossier d = dossiers.get(uuid);
        String name = d == null ? "UNKNOWN" : d.name;
        events.add(new ReplayEvent(System.currentTimeMillis(), uuid, name, kind, detail));
        while (events.size() > MAX_EVENTS) events.remove(0);
    }

    private void trim() {
        while (dossiers.size() > MAX_PROFILES) {
            String oldest = dossiers.values().stream().min(Comparator.comparingLong(d -> d.lastSeen)).map(d -> d.uuid).orElse(null);
            if (oldest == null) break;
            dossiers.remove(oldest);
        }
    }

    private void saveIfDue(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastSaveAt < 3000) return;
        lastSaveAt = now;
        try {
            Files.createDirectories(file().getParent());
            Properties p = new Properties();
            for (Dossier d : dossiers.values()) {
                String prefix = "profile." + d.uuid + ".";
                p.setProperty(prefix + "name", d.name);
                p.setProperty(prefix + "first", Long.toString(d.firstSeen));
                p.setProperty(prefix + "last", Long.toString(d.lastSeen));
                p.setProperty(prefix + "sightings", Integer.toString(d.sightings));
                p.setProperty(prefix + "locks", Integer.toString(d.observedLocks));
                p.setProperty(prefix + "drops", Integer.toString(d.observedHealthDrops));
                p.setProperty(prefix + "takenEvents", Integer.toString(d.observedDamageTakenEvents));
                p.setProperty(prefix + "targetLoss", Double.toString(d.observedTargetHealthLoss));
                p.setProperty(prefix + "selfLoss", Double.toString(d.observedSelfHealthLoss));
                p.setProperty(prefix + "server", d.server);
                p.setProperty(prefix + "dimension", d.dimension);
                p.setProperty(prefix + "pos", d.x + "," + d.y + "," + d.z);
                p.setProperty(prefix + "item", d.lastHeldItem);
                p.setProperty(prefix + "pattern", d.lastPattern);
            }
            p.setProperty("events.count", Integer.toString(events.size()));
            for (int i = 0; i < events.size(); i++) {
                ReplayEvent e = events.get(i);
                String raw = e.time() + "\t" + e.uuid() + "\t" + e.name() + "\t" + e.kind() + "\t" + e.detail();
                p.setProperty("event." + i, Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
            }
            try (OutputStream out = Files.newOutputStream(file())) { p.store(out, "T-800 local target dossiers and replay events"); }
        } catch (Exception ignored) { }
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        Path path = file();
        if (!Files.exists(path)) return;
        try (InputStream in = Files.newInputStream(path)) {
            Properties p = new Properties(); p.load(in);
            Map<String, Dossier> restored = new LinkedHashMap<>();
            for (String key : p.stringPropertyNames()) {
                if (!key.startsWith("profile.") || !key.endsWith(".name")) continue;
                String uuid = key.substring("profile.".length(), key.length() - ".name".length());
                Dossier d = new Dossier(); d.uuid = uuid; d.name = p.getProperty("profile." + uuid + ".name", "UNKNOWN");
                d.firstSeen = parseLong(p, uuid, "first"); d.lastSeen = parseLong(p, uuid, "last");
                d.sightings = parseInt(p, uuid, "sightings"); d.observedLocks = parseInt(p, uuid, "locks");
                d.observedHealthDrops = parseInt(p, uuid, "drops"); d.observedDamageTakenEvents = parseInt(p, uuid, "takenEvents");
                d.observedTargetHealthLoss = parseDouble(p, uuid, "targetLoss"); d.observedSelfHealthLoss = parseDouble(p, uuid, "selfLoss");
                d.server = p.getProperty("profile." + uuid + ".server", "Unknown");
                d.dimension = p.getProperty("profile." + uuid + ".dimension", "Unknown");
                String[] pos = p.getProperty("profile." + uuid + ".pos", "0,0,0").split(",");
                if (pos.length == 3) { d.x = parseInt(pos[0]); d.y = parseInt(pos[1]); d.z = parseInt(pos[2]); }
                d.lastHeldItem = p.getProperty("profile." + uuid + ".item", "Unknown");
                d.lastPattern = p.getProperty("profile." + uuid + ".pattern", "NO DATA");
                restored.put(uuid, d);
            }
            restored.values().stream().sorted(Comparator.comparingLong(d -> d.lastSeen))
                    .skip(Math.max(0, restored.size() - MAX_PROFILES)).forEach(d -> dossiers.put(d.uuid, d));
            int count = parseInt(p.getProperty("events.count", "0"));
            for (int i = Math.max(0, count - MAX_EVENTS); i < count; i++) {
                try {
                    String raw = new String(Base64.getDecoder().decode(p.getProperty("event." + i, "")), StandardCharsets.UTF_8);
                    String[] v = raw.split("\t", 5);
                    if (v.length == 5) events.add(new ReplayEvent(Long.parseLong(v[0]), v[1], v[2], v[3], v[4]));
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
    }

    private static long parseLong(Properties p, String uuid, String field) {
        try { return Long.parseLong(p.getProperty("profile." + uuid + "." + field, "0")); } catch (Exception ignored) { return 0; }
    }
    private static int parseInt(Properties p, String uuid, String field) {
        try { return Integer.parseInt(p.getProperty("profile." + uuid + "." + field, "0")); } catch (Exception ignored) { return 0; }
    }
    private static double parseDouble(Properties p, String uuid, String field) {
        try { return Double.parseDouble(p.getProperty("profile." + uuid + "." + field, "0")); } catch (Exception ignored) { return 0; }
    }
    private static int parseInt(String value) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return 0; }
    }
}
