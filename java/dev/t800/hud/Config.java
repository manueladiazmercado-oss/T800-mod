package dev.t800.hud;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** All user options (saved in config/t800hud.properties). */
public final class Config {
    private Config() {}

    public static boolean hud = true, vision = false, nightVision = false, customBars = true,
            terminationMode = false, combatAnalytics = true, armorAlerts = true, terrainLayer = true,
            performanceProfile = false, deathRecovery = true,
            showSystem = true, showStatus = true, showCompass = true, showTarget = true,
            showEffects = true, boot = true, vignette = true, damageFlash = true, sounds = true;
    public static int theme = 0, rangeIdx = 1, coneIdx = 1, sizeIdx = 1;
    public static boolean hasDeathLocation = false;
    public static int deathX, deathY, deathZ;
    public static String mode = "ALL";

    public static final String[] THEMES = {"T-800 RED", "CYBER BLUE", "MATRIX GREEN", "AMBER"};
    private static final int[] THEME_RGB = {0xFF2020, 0x20B8FF, 0x20FF60, 0xFFA000};
    public static final int[] RANGES = {32, 64, 96, 128};
    public static final String[] CONES = {"NARROW", "NORMAL", "WIDE"};
    private static final double[] CONE_DEG = {12, 22, 35};
    public static final String[] SIZES = {"SMALL", "NORMAL", "LARGE"};
    private static final float[] SIZE_MUL = {0.85f, 1f, 1.2f};

    // ---- derived values
    public static int rgb() { return THEME_RGB[theme]; }
    public static int main() { return 0xFF000000 | THEME_RGB[theme]; }
    public static int dim() { return 0xFF000000 | shade(THEME_RGB[theme], 0.55); }
    public static int soft() { return 0x88000000 | THEME_RGB[theme]; }
    public static int text() { return 0xFF000000 | mix(THEME_RGB[theme], 0xFFFFFF, 0.80); }
    public static int shadeRgb(double f) { return shade(THEME_RGB[theme], f); }
    public static double range() { return RANGES[rangeIdx]; }
    public static double coneDeg() { return CONE_DEG[coneIdx]; }
    public static float scaleMul() { return SIZE_MUL[sizeIdx]; }

    private static int shade(int rgb, double f) {
        int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    // ---- persistence
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("t800hud.properties"); }

    private static boolean b(Properties p, String k, boolean d) { return Boolean.parseBoolean(p.getProperty(k, String.valueOf(d))); }

    private static int i(Properties p, String k, int d, int max) {
        try { return Math.max(0, Math.min(max, Integer.parseInt(p.getProperty(k, String.valueOf(d))))); }
        catch (Exception e) { return d; }
    }

    private static int signed(Properties p, String k, int d) {
        try { return Integer.parseInt(p.getProperty(k, String.valueOf(d))); }
        catch (Exception e) { return d; }
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(f)) { p.load(in); }
            hud = b(p, "hud", true); vision = b(p, "vision", false); nightVision = b(p, "nightVision", false);
            customBars = b(p, "customBars", true);
            terminationMode = b(p, "terminationMode", false);
            combatAnalytics = b(p, "combatAnalytics", true);
            armorAlerts = b(p, "armorAlerts", true);
            terrainLayer = b(p, "terrainLayer", true);
            performanceProfile = b(p, "performanceProfile", false);
            deathRecovery = b(p, "deathRecovery", true); showSystem = b(p, "showSystem", true);
            showStatus = b(p, "showStatus", true); showCompass = b(p, "showCompass", true);
            showTarget = b(p, "showTarget", true); showEffects = b(p, "showEffects", true);
            boot = b(p, "boot", true); vignette = b(p, "vignette", true);
            damageFlash = b(p, "damageFlash", true); sounds = b(p, "sounds", true);
            theme = i(p, "theme", 0, THEMES.length - 1); rangeIdx = i(p, "rangeIdx", 1, RANGES.length - 1);
            coneIdx = i(p, "coneIdx", 1, CONES.length - 1); sizeIdx = i(p, "sizeIdx", 1, SIZES.length - 1);
            mode = p.getProperty("mode", "ALL");
            hasDeathLocation = b(p, "hasDeathLocation", false);
            deathX = signed(p, "deathX", 0);
            deathY = signed(p, "deathY", 0);
            deathZ = signed(p, "deathZ", 0);
        } catch (Exception ignored) { }
    }

    public static void save() {
        try {
            Properties p = new Properties();
            p.setProperty("hud", String.valueOf(hud)); p.setProperty("vision", String.valueOf(vision));
            p.setProperty("nightVision", String.valueOf(nightVision)); p.setProperty("customBars", String.valueOf(customBars));
            p.setProperty("terminationMode", String.valueOf(terminationMode));
            p.setProperty("combatAnalytics", String.valueOf(combatAnalytics));
            p.setProperty("armorAlerts", String.valueOf(armorAlerts));
            p.setProperty("terrainLayer", String.valueOf(terrainLayer));
            p.setProperty("performanceProfile", String.valueOf(performanceProfile));
            p.setProperty("deathRecovery", String.valueOf(deathRecovery));
            p.setProperty("showSystem", String.valueOf(showSystem)); p.setProperty("showStatus", String.valueOf(showStatus));
            p.setProperty("showCompass", String.valueOf(showCompass)); p.setProperty("showTarget", String.valueOf(showTarget));
            p.setProperty("showEffects", String.valueOf(showEffects)); p.setProperty("boot", String.valueOf(boot));
            p.setProperty("vignette", String.valueOf(vignette)); p.setProperty("damageFlash", String.valueOf(damageFlash));
            p.setProperty("sounds", String.valueOf(sounds));
            p.setProperty("theme", String.valueOf(theme)); p.setProperty("rangeIdx", String.valueOf(rangeIdx));
            p.setProperty("coneIdx", String.valueOf(coneIdx)); p.setProperty("sizeIdx", String.valueOf(sizeIdx));
            p.setProperty("mode", mode);
            p.setProperty("hasDeathLocation", String.valueOf(hasDeathLocation));
            p.setProperty("deathX", String.valueOf(deathX));
            p.setProperty("deathY", String.valueOf(deathY));
            p.setProperty("deathZ", String.valueOf(deathZ));
            try (OutputStream out = Files.newOutputStream(file())) { p.store(out, "T-800 HUD"); }
        } catch (Exception ignored) { }
    }
}
