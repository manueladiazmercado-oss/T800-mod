package dev.t800.hud;

/** Tiny replacement for net.minecraft.Util (moved/renamed in 1.21.11). */
final class Util {
    private Util() {}
    static long getMillis() { return System.currentTimeMillis(); }
}
