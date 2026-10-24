package dev.t800.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Random;

public class HudRenderer implements HudElement {
    private static final int RED      = 0xFFFF2020;
    private static final int RED_DIM  = 0xFF8A1010;
    private static final int RED_SOFT = 0x88FF2020;
    private static final int WHITE    = 0xFFFFD0D0;
    private static final int AMBER    = 0xFFFFA000;
    private static final String[] COMPASS8 = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final Identifier SKULL = Identifier.fromNamespaceAndPath(T800HudClient.MOD_ID, "textures/gui/skull.png");

    private static final String[] BOOT = {
            "CYBERDYNE SYSTEMS MODEL 101",
            "SERIES 800 // T-800",
            "NEURAL NET PROCESSOR ... ONLINE",
            "VISUAL SENSORS .......... CALIBRATED",
            "THREAT ASSESSMENT ....... ACTIVE",
            "PRIME DIRECTIVE LOADED",
            "READY."
    };

    // state
    private Object lastLevel;
    private long bootStart;
    private float lastHealth = -1;
    private long dmgTime;
    private float dmgAmount;

    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (!T800HudClient.enabled || mc.player == null || mc.level == null || mc.options.hideGui) return;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        float pt = dt.getGameTimeDeltaPartialTick(true);
        TargetTracker t = T800HudClient.TRACKER;
        long now = Util.getMillis();

        if (mc.level != lastLevel) { lastLevel = mc.level; bootStart = now; lastHealth = -1; }
        updateDamage(mc.player, now);

        drawVignette(g, w, h);
        if (T800HudClient.vision) drawVision(g, w, h, now);
        drawDamageFlash(g, w, h, now);

        t.project(mc, pt, w, h);
        drawSystemPanel(g, mc, now);
        drawCompass(g, mc, w, now);
        drawStatusPanel(g, mc, w, now);
        drawWarnings(g, mc, w, h, now);

        if (t.target != null) {
            if (t.onScreen) { drawReticle(g, t, now); drawTargetPanel(g, mc, t, w, now); }
            else if (t.behindOrOff) drawEdgeHint(g, mc, t, w, h, now);
        }
        drawKillNotice(g, mc, t, w, h, now);
        drawBoot(g, mc, w, h, now);
    }

    // ---------------------------------------------------------------- primitives
    private static void rect(GuiGraphics g, double x, double y, double w, double h, int color) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(0.5f, 0.5f);
        g.fill(0, 0, Math.max(1, (int) Math.round(w * 2)), Math.max(1, (int) Math.round(h * 2)), color);
        g.pose().popMatrix();
    }

    private static void text(GuiGraphics g, Font f, String s, double x, double y, float scale, int color) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(scale, scale);
        g.drawString(f, s, 0, 0, color, false);
        g.pose().popMatrix();
    }

    private static double textW(Font f, String s, float scale) { return f.width(s) * scale; }

    private static int alpha(int rgb, double a) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static void skull(GuiGraphics g, double x, double y, double size, boolean blink) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        float sc = (float) (size / 16.0);
        g.pose().scale(sc, sc);
        g.blit(RenderPipelines.GUI_TEXTURED, SKULL, 0, 0, 0f, 0f, 16, 16, 16, 16);
        g.pose().popMatrix();
    }

    // ---------------------------------------------------------------- screen effects
    private void drawVignette(GuiGraphics g, int w, int h) {
        int steps = 12, sz = 3;
        for (int i = 0; i < steps; i++) {
            double a = 0.28 * Math.pow(1 - i / (double) steps, 2);
            int c = alpha(0x300000, a);
            g.fill(i * sz, 0, i * sz + sz, h, c);
            g.fill(w - i * sz - sz, 0, w - i * sz, h, c);
            g.fill(0, i * sz, w, i * sz + sz, c);
            g.fill(0, h - i * sz - sz, w, h - i * sz, c);
        }
    }

    private void drawVision(GuiGraphics g, int w, int h, long now) {
        g.fill(0, 0, w, h, 0x1AFF0000);
        for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, 0x16000000);
        int sweep = (int) ((now / 18) % (h + 60)) - 30;
        g.fillGradient(0, sweep - 26, w, sweep, 0x00FF2020, 0x26FF2020);
        g.fill(0, sweep, w, sweep + 1, 0x40FF4040);
    }

    private void updateDamage(LocalPlayer p, long now) {
        float hp = p.getHealth();
        if (lastHealth >= 0 && hp < lastHealth - 0.01f) { dmgTime = now; dmgAmount = lastHealth - hp; }
        lastHealth = hp;
    }

    private void drawDamageFlash(GuiGraphics g, int w, int h, long now) {
        double age = (now - dmgTime) / 450.0;
        if (dmgTime == 0 || age >= 1) return;
        g.fill(0, 0, w, h, alpha(0xFF0000, 0.30 * (1 - age)));
    }

    // ---------------------------------------------------------------- system info (top left)
    private void drawSystemPanel(GuiGraphics g, Minecraft mc, long now) {
        LocalPlayer p = mc.player;
        TargetTracker t = T800HudClient.TRACKER;
        Font f = mc.font;
        double x = 8, y = 8, lh = 6.2;
        float s = 0.55f;

        float yaw = Mth.wrapDegrees(p.getYRot());
        float heading = (yaw + 180f + 360f) % 360f;
        String dir = COMPASS8[Math.floorMod(Math.round(heading / 45f), 8)];
        double speed = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo) * 20.0;

        int ping = -1;
        if (mc.getConnection() != null && mc.getConnection().getPlayerInfo(p.getUUID()) != null)
            ping = mc.getConnection().getPlayerInfo(p.getUUID()).getLatency();

        skull(g, x, y - 2, 9, false);
        boolean blink = (now / 600) % 2 == 0;
        text(g, f, "T-800 // " + (blink ? "ONLINE" : "ONLINE_"), x + 12, y, s, RED);
        rect(g, x, y + 9, 0.5, lh * 6 + 2, RED_DIM);
        rect(g, x, y + 9, 10, 0.5, RED);

        String[] lines = {
                String.format("POS  %d  %d  %d", Mth.floor(p.getX()), Mth.floor(p.getY()), Mth.floor(p.getZ())),
                String.format("HDG  %s  %03.0f", dir, heading),
                String.format("SPD  %.1f M/S", speed),
                String.format("ENT  %d   P:%d  H:%d  O:%d", t.total, t.players, t.hostile, t.other),
                String.format("FPS  %d%s", mc.getFps(), ping >= 0 ? "   PING " + ping + "MS" : ""),
                "MODE " + t.mode.name() + (T800HudClient.vision ? "  VIS:ON" : "")
        };
        for (int i = 0; i < lines.length; i++)
            text(g, f, lines[i], x + 4, y + 10 + lh * i, s, i == 5 ? AMBER : WHITE);
    }

    // ---------------------------------------------------------------- compass tape (top center)
    private void drawCompass(GuiGraphics g, Minecraft mc, int w, long now) {
        Font f = mc.font;
        float yaw = Mth.wrapDegrees(mc.player.getYRot());
        double heading = (yaw + 180.0 + 360.0) % 360.0;
        double cx = w / 2.0, y = 8, pxPerDeg = 1.1;
        int half = 55;

        rect(g, cx - half * pxPerDeg, y + 9, half * 2 * pxPerDeg, 0.5, RED_DIM);
        for (int a = -half; a <= half; a++) {
            double ang = (Math.floor(heading) + a);
            double wa = ((ang % 360) + 360) % 360;
            if (wa % 5 != 0) continue;
            double px = cx + (ang - heading) * pxPerDeg;
            double edge = 1 - Math.abs(a) / (double) half;
            int col = alpha(0xFF2020, 0.25 + 0.75 * edge);
            boolean major = wa % 45 == 0;
            boolean mid = wa % 15 == 0;
            rect(g, px, y + (major ? 4 : mid ? 6 : 7.5), 0.5, major ? 5.5 : mid ? 3.5 : 2, col);
            if (major) {
                String lbl = COMPASS8[(int) (wa / 45)];
                text(g, f, lbl, px - textW(f, lbl, 0.55f) / 2, y - 2, 0.55f, alpha(0xFFD0D0, 0.3 + 0.7 * edge));
            }
        }
        rect(g, cx - 0.25, y + 10, 0.5, 3, RED);
        String deg = String.format("%03.0f", heading);
        text(g, f, deg, cx - textW(f, deg, 0.5f) / 2, y + 13, 0.5f, RED);
    }

    // ---------------------------------------------------------------- status (top right)
    private void drawStatusPanel(GuiGraphics g, Minecraft mc, int w, long now) {
        LocalPlayer p = mc.player;
        TargetTracker t = T800HudClient.TRACKER;
        Font f = mc.font;
        float s = 0.55f;
        double lh = 6.4, y = 8, xr = w - 8;

        double integ = Mth.clamp(p.getHealth() / Math.max(1f, p.getMaxHealth()), 0, 1);
        double power = Mth.clamp(p.getFoodData().getFoodLevel() / 20.0, 0, 1);
        int plating = p.getArmorValue();

        long dayTime = mc.level.getDayTime() % 24000L;
        int hh = (int) ((dayTime / 1000 + 6) % 24), mm = (int) ((dayTime % 1000) * 60 / 1000);

        String[] labels = {
                String.format("INTEGRITY %3.0f%%", integ * 100),
                String.format("POWER CELL %3.0f%%", power * 100),
                "PLATING  " + plating,
                "TERMINATED  " + t.kills,
                String.format("SYS TIME  %02d:%02d", hh, mm)
        };
        double maxW = 0;
        for (String l : labels) maxW = Math.max(maxW, textW(f, l, s));
        double x = xr - maxW;

        rect(g, xr + 3.5, y - 1, 0.5, lh * 5 + 8, RED_DIM);
        for (int i = 0; i < labels.length; i++) {
            double ly = y + lh * i + (i >= 2 ? 3 : 0) + (i >= 3 ? 0 : 0);
            // bars under the first two lines
            text(g, f, labels[i], xr - textW(f, labels[i], s), ly, s, i == 0 && integ < 0.3 ? RED : WHITE);
        }
        // bars
        double by0 = y + lh * 2 - 1.2;
        rect(g, x, by0, maxW, 1.2, RED_DIM);
        rect(g, x, by0, maxW * integ, 1.2, integ < 0.3 ? RED : 0xFFE03030);
        rect(g, x, by0 + 1.8, maxW, 0.8, 0xFF5A3A00);
        rect(g, x, by0 + 1.8, maxW * power, 0.8, AMBER);
    }

    // ---------------------------------------------------------------- warnings / damage
    private void drawWarnings(GuiGraphics g, Minecraft mc, int w, int h, long now) {
        Font f = mc.font;
        LocalPlayer p = mc.player;
        double integ = p.getHealth() / Math.max(1f, p.getMaxHealth());
        if (integ < 0.3 && (now / 400) % 2 == 0) {
            String s = "WARNING // SYSTEM INTEGRITY CRITICAL";
            text(g, f, s, w / 2.0 - textW(f, s, 0.7f) / 2, 30, 0.7f, RED);
        }
        double age = (now - dmgTime) / 1600.0;
        if (dmgTime != 0 && age < 1) {
            String s = String.format("DAMAGE DETECTED  -%.1f", dmgAmount);
            text(g, f, s, w / 2.0 - textW(f, s, 0.55f) / 2, 40, 0.55f, alpha(0xFFA000, 1 - age));
        }
        if (p.getFoodData().getFoodLevel() <= 6 && (now / 800) % 2 == 0) {
            String s = "POWER CELL LOW";
            text(g, f, s, w / 2.0 - textW(f, s, 0.55f) / 2, 48, 0.55f, AMBER);
        }
    }

    // ---------------------------------------------------------------- reticle
    private void drawReticle(GuiGraphics g, TargetTracker t, long now) {
        long lockMs = now - t.lockSince;
        boolean locked = lockMs > 350;

        double k = locked ? 1.0 : 1.0 + 0.8 * (1.0 - Mth.clamp(lockMs / 350.0, 0, 1));
        double hw = t.halfW * k, hh = t.halfH * k;
        double len = Math.max(4, Math.min(hw, hh) * 0.45);
        int c = locked ? RED : RED_SOFT;

        double l = t.sx - hw, r = t.sx + hw, tp = t.sy - hh, b = t.sy + hh, th = 1.0;
        rect(g, l, tp, len, th, c); rect(g, l, tp, th, len, c);
        rect(g, r - len, tp, len, th, c); rect(g, r - th, tp, th, len, c);
        rect(g, l, b - th, len, th, c); rect(g, l, b - len, th, len, c);
        rect(g, r - len, b - th, len, th, c); rect(g, r - th, b - len, th, len, c);

        rect(g, t.sx - 0.5, t.sy - 0.5, 1, 1, c);
        rect(g, l - 4, t.sy, 3, 0.5, c); rect(g, r + 1, t.sy, 3, 0.5, c);
        rect(g, t.sx, tp - 4, 0.5, 3, c); rect(g, t.sx, b + 1, 0.5, 3, c);

        // rotating scan tick along the top edge when locked
        if (locked) {
            double sx = l + ((now / 12) % (long) Math.max(4, hw * 2));
            rect(g, sx, tp - 1.5, 2, 0.5, WHITE);
        }

        Font f = Minecraft.getInstance().font;
        boolean blink = (now / 250) % 2 == 0;
        String status = locked ? (blink ? "[ LOCKED ]" : "[        ]") : "ACQUIRING...";
        text(g, f, status, t.sx - textW(f, status, 0.5f) / 2, tp - 8, 0.5f, locked ? RED : WHITE);
    }

    // ---------------------------------------------------------------- target analysis
    private void drawTargetPanel(GuiGraphics g, Minecraft mc, TargetTracker t, int w, long now) {
        LivingEntity e = t.target;
        Font f = mc.font;
        float s = 0.5f;
        double lh = 5.6;

        boolean player = e instanceof Player;
        boolean hostile = TargetTracker.isHostile(e);
        String cls = player ? "PLAYER" : hostile ? "HOSTILE" : "NEUTRAL";
        double dist = t.distance;
        String threat = (player && dist < 8) ? "HIGH" : (hostile && dist < 10) ? "MED" : (player || hostile) ? "LOW" : "NONE";
        double spd = Math.hypot(e.getX() - e.xo, e.getZ() - e.zo) * 20.0;
        String hold = e.getMainHandItem().isEmpty() ? "NONE" : e.getMainHandItem().getHoverName().getString();
        if (hold.length() > 18) hold = hold.substring(0, 18);
        String name = e.getName().getString();
        if (name.length() > 18) name = name.substring(0, 18);

        String[] lines = {
                "ID   " + name.toUpperCase(),
                "CLS  " + cls,
                String.format("RNG  %.1f M", dist),
                String.format("HP   %.1f / %.0f", e.getHealth(), e.getMaxHealth()),
                "ARM  " + e.getArmorValue(),
                String.format("SPD  %.1f M/S", spd),
                "HLD  " + hold.toUpperCase(),
                "THREAT " + threat
        };

        // fake sensor data stream (changes ~8x/s, stable per entity)
        Random rnd = new Random(e.getId() * 31L + now / 120);
        String hex = String.format("%04X %04X %04X", rnd.nextInt(0x10000), rnd.nextInt(0x10000), rnd.nextInt(0x10000));

        double maxW = textW(f, hex, s);
        for (String l : lines) maxW = Math.max(maxW, textW(f, l, s));
        double px = t.sx + t.halfW + 8;
        if (px + maxW + 6 > w) px = t.sx - t.halfW - 8 - maxW - 4;
        double py = t.sy - t.halfH;

        rect(g, px - 3, py - 1, 0.5, lh * (lines.length + 2) + 6, RED_DIM);
        text(g, f, "ANALYSIS", px, py - 6.5, s, RED_DIM);
        for (int i = 0; i < lines.length; i++) {
            int col = (i == 7) ? (threat.equals("HIGH") ? RED : threat.equals("MED") ? AMBER : WHITE) : WHITE;
            text(g, f, lines[i], px, py + lh * i, s, col);
        }
        double ratio = Mth.clamp(e.getHealth() / Math.max(1f, e.getMaxHealth()), 0, 1);
        double by = py + lh * lines.length + 0.5;
        rect(g, px, by, 40, 1.5, RED_DIM);
        rect(g, px, by, 40 * ratio, 1.5, RED);
        text(g, f, hex, px, by + 3.5, s, RED_DIM);
    }

    private void drawEdgeHint(GuiGraphics g, Minecraft mc, TargetTracker t, int w, int h, long now) {
        if ((now / 300) % 2 != 0) return;
        Font f = mc.font;
        String s = t.targetRight ? "TGT >>>" : "<<< TGT";
        float sc = 0.6f;
        double x = t.targetRight ? w - 10 - textW(f, s, sc) : 10;
        text(g, f, s, x, h / 2.0 - 3, sc, RED);
    }

    // ---------------------------------------------------------------- kill notice
    private void drawKillNotice(GuiGraphics g, Minecraft mc, TargetTracker t, int w, int h, long now) {
        double age = (now - t.killTime) / 2200.0;
        if (t.killTime == 0 || age >= 1) return;
        Font f = mc.font;
        String a = "TARGET TERMINATED";
        String b = t.killName.toUpperCase();
        double cy = h / 2.0 + 28;
        text(g, f, a, w / 2.0 - textW(f, a, 0.8f) / 2, cy, 0.8f, alpha(0xFF2020, 1 - age * age));
        text(g, f, b, w / 2.0 - textW(f, b, 0.5f) / 2, cy + 9, 0.5f, alpha(0xFFD0D0, 1 - age * age));
    }

    // ---------------------------------------------------------------- boot sequence
    private void drawBoot(GuiGraphics g, Minecraft mc, int w, int h, long now) {
        long el = now - bootStart;
        long total = BOOT.length * 420L + 2200L;
        if (el > total) return;
        double fade = el > total - 700 ? (total - el) / 700.0 : 1.0;
        Font f = mc.font;
        double x = w / 2.0 - 70, y = h / 2.0 - 64;
        skull(g, w / 2.0 - 12, y - 30, 24, false);
        for (int i = 0; i < BOOT.length; i++) {
            long start = i * 420L;
            if (el < start) break;
            String line = BOOT[i];
            int chars = (int) Math.min(line.length(), (el - start) / 14);
            boolean last = i == BOOT.length - 1;
            text(g, f, "> " + line.substring(0, chars), x, y + i * 7, 0.55f,
                    alpha(last ? 0xFF2020 : 0xFFD0D0, fade));
        }
    }
}
