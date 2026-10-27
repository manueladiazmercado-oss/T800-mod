package dev.t800.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HudRenderer implements HudElement {
    static final int AMBER = 0xFFFFA000;
    private static final String[] COMPASS8 = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
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

    private Object lastLevel;
    private long bootStart;
    private float lastHealth = -1;
    private long dmgTime;
    private float dmgAmount;

    static boolean survival(Minecraft mc) {
        return mc.player != null && !mc.player.isCreative() && !mc.player.isSpectator();
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (!Config.hud || mc.player == null || mc.level == null || mc.options.hideGui) return;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        float pt = dt.getGameTimeDeltaPartialTick(true);
        TargetTracker t = T800HudClient.TRACKER;
        long now = Util.getMillis();

        if (mc.level != lastLevel) { lastLevel = mc.level; bootStart = now; lastHealth = -1; }
        updateDamage(mc.player, now);

        if (Config.vignette && !Config.performanceProfile) drawVignette(g, w, h);
        if (Config.vision && !Config.performanceProfile) drawVision(g, w, h, now);
        if (Config.infraredVision && !Config.performanceProfile) drawInfrared(g, mc, w, h, now);
        if (Config.damageFlash && !Config.performanceProfile) drawDamageFlash(g, w, h, now);

        t.project(mc, pt, w, h);
        if (Config.showSystem) drawSystemPanel(g, mc, now);
        if (Config.showCompass && !Config.performanceProfile) drawCompass(g, mc, w, now);
        double yEnd = 8;
        if (Config.showStatus) yEnd = drawStatusPanel(g, mc, w, now);
        if (Config.showEffects && !Config.performanceProfile) drawEffects(g, mc, w, yEnd + 4);
        drawWarnings(g, mc, w, h, now);
        drawProjectileWarning(g, mc, w, now);
        if (Config.customBars && survival(mc)) drawBars(g, mc, w, h, now);

        if (Config.showTarget && t.target != null) {
            if (t.onScreen) { drawReticle(g, t, now); drawTargetPanel(g, mc, t, w, now); }
            else if (t.behindOrOff) drawEdgeHint(g, mc, t, w, h, now);
        }
        if (T800HudClient.analyticsOverlayVisible && Config.combatAnalytics) drawCombatOverlay(g, mc, w, h, t);
        drawKillNotice(g, mc, t, w, h, now);
        if (Config.boot) drawBoot(g, mc, w, h, now);
    }

    // ---------------------------------------------------------------- primitives (shared with screens)
    static void rect(GuiGraphics g, double x, double y, double w, double h, int color) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(0.5f, 0.5f);
        g.fill(0, 0, Math.max(1, (int) Math.round(w * 2)), Math.max(1, (int) Math.round(h * 2)), color);
        g.pose().popMatrix();
    }

    static void text(GuiGraphics g, Font f, String s, double x, double y, float scale, int color) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(scale, scale);
        g.drawString(f, s, 0, 0, color, false);
        g.pose().popMatrix();
    }

    static double textW(Font f, String s, float scale) { return f.width(s) * scale; }

    static int alpha(int rgb, double a) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    static void skull(GuiGraphics g, double x, double y, double size) {
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
            int c = alpha(Config.shadeRgb(0.2), a);
            g.fill(i * sz, 0, i * sz + sz, h, c);
            g.fill(w - i * sz - sz, 0, w - i * sz, h, c);
            g.fill(0, i * sz, w, i * sz + sz, c);
            g.fill(0, h - i * sz - sz, w, h - i * sz, c);
        }
    }

    private void drawVision(GuiGraphics g, int w, int h, long now) {
        int rgb = Config.rgb();
        g.fill(0, 0, w, h, alpha(rgb, 0.10));
        for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, 0x16000000);
        int sweep = (int) ((now / 18) % (h + 60)) - 30;
        g.fillGradient(0, sweep - 26, w, sweep, alpha(rgb, 0.0), alpha(rgb, 0.15));
        g.fill(0, sweep, w, sweep + 1, alpha(rgb, 0.25));
    }

    /** Lightweight thermal-style overlay; deliberately HUD-only to avoid unsafe renderer hooks. */
    private void drawInfrared(GuiGraphics g, Minecraft mc, int w, int h, long now) {
        g.fill(0, 0, w, h, alpha(0x16251B, 0.28));
        for (int y = 0; y < h; y += 4) g.fill(0, y, w, y + 1, 0x1800FF88);
        int sweep = (int) ((now / 14) % (h + 80)) - 40;
        g.fillGradient(0, sweep - 24, w, sweep, 0x0000FF00, 0x5530FF80);
        g.fill(0, sweep, w, sweep + 1, 0xAA60FFB0);
        Font f = mc.font;
        String label = "THERMAL / IR SENSOR ACTIVE";
        text(g, f, label, w / 2.0 - textW(f, label, 0.65f) / 2, 8, 0.65f, 0xFF70FFB0);
        String light = String.format("LUX %d", mc.level.getBrightness(LightLayer.BLOCK, mc.player.blockPosition()));
        text(g, f, light, w - textW(f, light, 0.5f) - 8, 8, 0.5f, 0xFF70FFB0);
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
        float k = Config.scaleMul();
        double x = 8, y = 8, lh = 6.2 * k;
        float s = 0.55f * k;

        float yaw = Mth.wrapDegrees(p.getYRot());
        float heading = (yaw + 180f + 360f) % 360f;
        String dir = COMPASS8[Math.floorMod(Math.round(heading / 45f), 8)];
        double speed = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo) * 20.0;

        int ping = -1;
        if (mc.getConnection() != null && mc.getConnection().getPlayerInfo(p.getUUID()) != null)
            ping = mc.getConnection().getPlayerInfo(p.getUUID()).getLatency();

        List<String> lines = new ArrayList<>();
        lines.add(String.format("POS  %d  %d  %d", Mth.floor(p.getX()), Mth.floor(p.getY()), Mth.floor(p.getZ())));
        if (mc.level.dimension() == Level.OVERWORLD)
            lines.add(String.format("NTH  %d  %d", Mth.floor(p.getX() / 8.0), Mth.floor(p.getZ() / 8.0)));
        else if (mc.level.dimension() == Level.NETHER)
            lines.add(String.format("OVW  %d  %d", Mth.floor(p.getX() * 8.0), Mth.floor(p.getZ() * 8.0)));
        lines.add(String.format("HDG  %s  %03.0f", dir, heading));
        lines.add(String.format("SPD  %.1f M/S", speed));
        int bl = mc.level.getBrightness(LightLayer.BLOCK, p.blockPosition());
        int sl = mc.level.getBrightness(LightLayer.SKY, p.blockPosition());
        lines.add(String.format("LUM  B:%d  S:%d%s", bl, sl, bl == 0 ? "  DARK" : ""));
        lines.add(String.format("ENT  %d   P:%d  H:%d  O:%d", t.total, t.players, t.hostile, t.other));
        lines.add(String.format("FPS  %d%s", mc.getFps(), ping >= 0 ? "   PING " + ping + "MS" : ""));
        ItemStack hand = p.getMainHandItem();
        if (!hand.isEmpty()) {
            String hn = hand.getHoverName().getString().toUpperCase();
            if (hn.length() > 16) hn = hn.substring(0, 16);
            String dur = "";
            if (hand.isDamageableItem())
                dur = String.format("  %d%%", 100 * (hand.getMaxDamage() - hand.getDamageValue()) / Math.max(1, hand.getMaxDamage()));
            lines.add("HLD  " + hn + dur);
        }
        lines.add("MODE " + t.mode.name() + (Config.nightVision ? "  NV" : "") + (Config.vision ? "  VIS" : "") + (Config.infraredVision ? "  IR" : ""));
        if (Config.deathRecovery && Config.hasDeathLocation)
            lines.add(String.format("LAST DEATH %d %d %d", Config.deathX, Config.deathY, Config.deathZ));

        skull(g, x, y - 2, 9);
        boolean blink = (now / 600) % 2 == 0;
        text(g, f, "T-800 // " + (blink ? "ONLINE" : "ONLINE_"), x + 12, y, s, Config.main());
        rect(g, x, y + 9, 0.5, lh * lines.size() + 2, Config.dim());
        rect(g, x, y + 9, 10, 0.5, Config.main());
        for (int i = 0; i < lines.size(); i++)
            text(g, f, lines.get(i), x + 4, y + 10 + lh * i, s, i == lines.size() - 1 ? AMBER : Config.text());
    }

    // ---------------------------------------------------------------- compass tape (top center)
    private void drawCompass(GuiGraphics g, Minecraft mc, int w, long now) {
        Font f = mc.font;
        float yaw = Mth.wrapDegrees(mc.player.getYRot());
        double heading = (yaw + 180.0 + 360.0) % 360.0;
        double cx = w / 2.0, y = 8, pxPerDeg = 1.1;
        int half = 55;

        rect(g, cx - half * pxPerDeg, y + 9, half * 2 * pxPerDeg, 0.5, Config.dim());
        for (int a = -half; a <= half; a++) {
            double ang = (Math.floor(heading) + a);
            double wa = ((ang % 360) + 360) % 360;
            if (wa % 5 != 0) continue;
            double px = cx + (ang - heading) * pxPerDeg;
            double edge = 1 - Math.abs(a) / (double) half;
            int col = alpha(Config.rgb(), 0.25 + 0.75 * edge);
            boolean major = wa % 45 == 0;
            boolean mid = wa % 15 == 0;
            rect(g, px, y + (major ? 4 : mid ? 6 : 7.5), 0.5, major ? 5.5 : mid ? 3.5 : 2, col);
            if (major) {
                String lbl = COMPASS8[(int) (wa / 45)];
                text(g, f, lbl, px - textW(f, lbl, 0.55f) / 2, y - 2, 0.55f, alpha(Config.text(), 0.3 + 0.7 * edge));
            }
        }
        rect(g, cx - 0.25, y + 10, 0.5, 3, Config.main());
        String deg = String.format("%03.0f", heading);
        text(g, f, deg, cx - textW(f, deg, 0.5f) / 2, y + 13, 0.5f, Config.main());
    }

    // ---------------------------------------------------------------- status (top right)
    private double drawStatusPanel(GuiGraphics g, Minecraft mc, int w, long now) {
        LocalPlayer p = mc.player;
        TargetTracker t = T800HudClient.TRACKER;
        Font f = mc.font;
        float k = Config.scaleMul();
        float s = 0.55f * k;
        double lh = 6.4 * k, xr = w - 8;

        // keep clear of the vanilla effect icons (top right)
        boolean good = false, bad = false;
        for (MobEffectInstance e : p.getActiveEffects()) {
            if (e.getEffect().value().isBeneficial()) good = true; else bad = true;
        }
        double y = 8 + (good ? 26 : 0) + (bad ? 26 : 0);

        double integ = Mth.clamp(p.getHealth() / Math.max(1f, p.getMaxHealth()), 0, 1);
        double power = Mth.clamp(p.getFoodData().getFoodLevel() / 20.0, 0, 1);
        double tough = p.getAttributeValue(Attributes.ARMOR_TOUGHNESS);

        int totems = 0, arrows = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.is(Items.TOTEM_OF_UNDYING)) totems += st.getCount();
            if (st.is(Items.ARROW) || st.is(Items.SPECTRAL_ARROW)) arrows += st.getCount();
        }
        ItemStack hand = p.getMainHandItem();
        boolean ranged = hand.is(Items.BOW) || hand.is(Items.CROSSBOW);

        long dayTime = mc.level.getDayTime() % 24000L;
        int hh = (int) ((dayTime / 1000 + 6) % 24), mm = (int) ((dayTime % 1000) * 60 / 1000);

        List<String> lines = new ArrayList<>();
        lines.add(String.format("INTEGRITY %3.0f%%", integ * 100));
        lines.add(String.format("POWER CELL %3.0f%%", power * 100));
        int barLines = lines.size();
        lines.add(String.format("PLATING  %d  TGH %.0f", p.getArmorValue(), tough));
        if (totems > 0) lines.add("TOTEMS  " + totems);
        if (ranged) lines.add("AMMO  " + arrows);
        lines.add("TERMINATED  " + t.kills);
        lines.add(String.format("DAY %d  %02d:%02d", mc.level.getDayTime() / 24000L, hh, mm));

        double maxW = 0;
        for (String l : lines) maxW = Math.max(maxW, textW(f, l, s));
        double x = xr - maxW;

        rect(g, xr + 3.5, y - 1, 0.5, lh * lines.size() + 4, Config.dim());
        double yy = y;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            int col = (i == 0 && integ < 0.3) ? Config.main() : Config.text();
            if (l.startsWith("TOTEMS")) col = 0xFFFFD040;
            if (l.startsWith("AMMO")) col = AMBER;
            text(g, f, l, xr - textW(f, l, s), yy, s, col);
            yy += lh;
            if (i == barLines - 1) {
                double by0 = yy - 1.0;
                rect(g, x, by0, maxW, 1.2, Config.dim());
                rect(g, x, by0, maxW * integ, 1.2, integ < 0.3 ? Config.main() : Config.soft() | 0xFF000000);
                rect(g, x, by0 + 1.8, maxW, 0.8, 0xFF5A3A00);
                rect(g, x, by0 + 1.8, maxW * power, 0.8, AMBER);
                yy += 3;
            }
        }
        return yy;
    }

    private void drawEffects(GuiGraphics g, Minecraft mc, int w, double y0) {
        Font f = mc.font;
        float k = Config.scaleMul();
        float s = 0.5f * k;
        double lh = 5.8 * k, y = y0;
        int n = 0;
        for (MobEffectInstance e : mc.player.getActiveEffects()) {
            if (n >= 8) break;
            String name = e.getEffect().value().getDisplayName().getString().toUpperCase();
            String lvl = e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "";
            String time = e.isInfiniteDuration() ? "INF"
                    : String.format("%d:%02d", e.getDuration() / 1200, (e.getDuration() / 20) % 60);
            String line = name + lvl + "  " + time;
            int col = e.getEffect().value().isBeneficial() ? Config.text() : 0xFFFF6040;
            text(g, f, line, w - 8 - textW(f, line, s), y, s, col);
            y += lh;
            n++;
        }
    }

    // ---------------------------------------------------------------- custom health / armor / hunger bars
    private void drawBars(GuiGraphics g, Minecraft mc, int w, int h, long now) {
        LocalPlayer p = mc.player;
        Font f = mc.font;
        double cx = w / 2.0, barW = 81, barH = 5, y = h - 31;
        double lx = cx - 91, rx = cx + 10;
        int bg = 0x99000000 | Config.shadeRgb(0.22);
        boolean blink = (now / 250) % 2 == 0;

        // ---- health (left)
        float hp = p.getHealth(), max = p.getMaxHealth(), abs = p.getAbsorptionAmount();
        int hcol = Config.main();
        if (p.hasEffect(MobEffects.POISON)) hcol = 0xFF9ACD32;
        else if (p.hasEffect(MobEffects.WITHER)) hcol = 0xFF8A7A7A;
        else if (p.isFullyFrozen()) hcol = 0xFF60C8FF;
        if (hp <= max * 0.3f && blink) hcol = 0xFFFFFFFF;
        int segs = Mth.clamp((int) Math.ceil(max / 2f), 1, 20);
        double period = barW / segs, sw = period - 1;
        for (int i = 0; i < segs; i++) {
            double sx = lx + i * period;
            rect(g, sx, y, sw, barH, bg);
            double fill = Mth.clamp((hp - i * 2) / 2.0, 0, 1);
            if (fill > 0) rect(g, sx, y, sw * fill, barH, hcol);
            if (abs > 0) {
                double af = Mth.clamp((abs - i * 2) / 2.0, 0, 1);
                if (af > 0) rect(g, sx, y - 1.6, sw * af, 1.2, 0xFFFFD040);
            }
        }
        String hpTxt = String.format("HP %.0f/%.0f%s", hp, max, abs > 0 ? "  +" + (int) Math.ceil(abs) : "");
        text(g, f, hpTxt, lx, y - 9, 0.5f, Config.text());

        // ---- armor (thin line above health + text)
        int armor = p.getArmorValue();
        if (armor > 0) {
            double tough = p.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            for (int i = 0; i < 10; i++) {
                double sx = lx + i * (barW / 10.0);
                double aw = barW / 10.0 - 1;
                rect(g, sx, y - 3.4, aw, 1.2, 0x55AAB4C0);
                double fill = Mth.clamp((armor - i * 2) / 2.0, 0, 1);
                if (fill > 0) rect(g, sx, y - 3.4, aw * fill, 1.2, 0xFFC8D4E0);
            }
            String at = String.format("ARM %d  TGH %.0f", armor, tough);
            text(g, f, at, lx + barW - textW(f, at, 0.5f), y - 9, 0.5f, 0xFFC8D4E0);
        }

        // ---- hunger (right, fills from the right like vanilla)
        FoodData fd = p.getFoodData();
        int food = fd.getFoodLevel();
        float sat = fd.getSaturationLevel();
        int fcol = AMBER;
        if (p.hasEffect(MobEffects.HUNGER)) fcol = 0xFF8AB028;
        if (food <= 6 && blink) fcol = 0xFFFF4040;

        ItemStack st = p.getMainHandItem();
        FoodProperties fp = st.get(DataComponents.FOOD);
        if (fp == null) { st = p.getOffhandItem(); fp = st.get(DataComponents.FOOD); }
        int newFood = food;
        float newSat = sat;
        boolean preview = false;
        if (fp != null && (food < 20 || fp.canAlwaysEat())) {
            preview = true;
            newFood = Math.min(20, food + fp.nutrition());
            newSat = Math.min(newFood, sat + fp.saturation());
        }
        double pulse = 0.45 + 0.35 * Math.sin(now / 160.0);

        double fperiod = barW / 10.0, fsw = fperiod - 1;
        for (int i = 0; i < 10; i++) {
            double sx = rx + barW - (i + 1) * fperiod + 1;
            rect(g, sx, y, fsw, barH, bg);
            double fill = Mth.clamp((food - i * 2) / 2.0, 0, 1);
            if (fill > 0) rect(g, sx + fsw * (1 - fill), y, fsw * fill, barH, fcol);
            if (preview) {
                double nf = Mth.clamp((newFood - i * 2) / 2.0, 0, 1);
                if (nf > fill) rect(g, sx + fsw * (1 - nf), y, fsw * (nf - fill), barH, alpha(0xFFE070, pulse));
            }
        }
        // saturation line
        rect(g, rx, y - 1.6, barW, 1.2, 0x55806010);
        double satW = barW * Mth.clamp(sat / 20.0, 0, 1);
        rect(g, rx + barW - satW, y - 1.6, satW, 1.2, 0xFFFFE070);
        if (preview && newSat > sat) {
            double nW = barW * Mth.clamp(newSat / 20.0, 0, 1);
            rect(g, rx + barW - nW, y - 1.6, nW - satW, 1.2, alpha(0xFFFFFF, pulse));
        }
        String ft = String.format("PWR %d  SAT %.1f%s", food, sat, preview ? "  +" + (newFood - food) : "");
        text(g, f, ft, rx + barW - textW(f, ft, 0.5f), y - 9, 0.5f, AMBER);
    }

    // ---------------------------------------------------------------- warnings / damage
    private void drawWarnings(GuiGraphics g, Minecraft mc, int w, int h, long now) {
        Font f = mc.font;
        LocalPlayer p = mc.player;
        double integ = p.getHealth() / Math.max(1f, p.getMaxHealth());
        double wy = 30;
        if (integ < 0.3 && (now / 400) % 2 == 0) {
            String s = "WARNING // SYSTEM INTEGRITY CRITICAL";
            text(g, f, s, w / 2.0 - textW(f, s, 0.7f) / 2, wy, 0.7f, Config.main());
        }
        wy += 9;
        double age = (now - dmgTime) / 1600.0;
        if (dmgTime != 0 && age < 1) {
            String s = String.format("DAMAGE DETECTED  -%.1f", dmgAmount);
            text(g, f, s, w / 2.0 - textW(f, s, 0.55f) / 2, wy, 0.55f, alpha(0xFFA000, 1 - age));
        }
        wy += 8;
        if (p.getFoodData().getFoodLevel() <= 6 && (now / 800) % 2 == 0) {
            String s = "POWER CELL LOW";
            text(g, f, s, w / 2.0 - textW(f, s, 0.55f) / 2, wy, 0.55f, AMBER);
            wy += 8;
        }
        if (Config.armorAlerts) for (EquipmentSlot slot : ARMOR) {
            ItemStack a = p.getItemBySlot(slot);
            if (!a.isEmpty() && a.isDamageableItem()
                    && (a.getMaxDamage() - a.getDamageValue()) < a.getMaxDamage() * 0.15 && (now / 600) % 2 == 0) {
                String s = "PLATING DAMAGED // " + slot.getName().toUpperCase();
                text(g, f, s, w / 2.0 - textW(f, s, 0.55f) / 2, wy, 0.55f, AMBER);
                break;
            }
        }
    }

    /** Shows the closest predicted projectile path and estimated time to closest approach. */
    private void drawProjectileWarning(GuiGraphics g, Minecraft mc, int w, long now) {
        List<CombatAIService.ProjectileThreat> incoming = CombatAIService.INSTANCE.projectileThreats();
        if (incoming.isEmpty()) return;
        CombatAIService.ProjectileThreat p = incoming.get(0); Font f = mc.font;
        String line = "PROJECTILE " + p.label + " // " + p.direction + " // ETA "
                + String.format(java.util.Locale.ROOT, "%.1fs", p.impactTicks / 20.0);
        int color = p.impactTicks <= 8 ? 0xFFFF3030 : AMBER;
        if (p.impactTicks <= 8 && (now / 180) % 2 != 0) color = 0xFFFFA000;
        float scale = 0.68f; double x = w / 2.0 - textW(f, line, scale) / 2; double y = 43;
        g.fill((int) (x - 5), (int) (y - 3), (int) (x + textW(f, line, scale) + 5), (int) (y + 10), 0xC0100808);
        g.fill((int) (x - 5), (int) (y - 3), (int) (x - 3), (int) (y + 10), color);
        text(g, f, line, x, y, scale, color);
        if (incoming.size() > 1) {
            String more = "+" + (incoming.size() - 1) + " ADDITIONAL PROJECTILE PATH(S)";
            text(g, f, more, w / 2.0 - textW(f, more, 0.48f) / 2, y + 12, 0.48f, Config.text());
        }
    }
    // ---------------------------------------------------------------- reticle
    private void drawReticle(GuiGraphics g, TargetTracker t, long now) {
        long lockMs = now - t.lockSince;
        boolean locked = lockMs > 350;

        double k = locked ? 1.0 : 1.0 + 0.8 * (1.0 - Mth.clamp(lockMs / 350.0, 0, 1));
        double hw = t.halfW * k, hh = t.halfH * k;
        double len = Math.max(4, Math.min(hw, hh) * 0.45);
        int c = locked ? Config.main() : Config.soft();

        double l = t.sx - hw, r = t.sx + hw, tp = t.sy - hh, b = t.sy + hh, th = 1.0;
        rect(g, l, tp, len, th, c); rect(g, l, tp, th, len, c);
        rect(g, r - len, tp, len, th, c); rect(g, r - th, tp, th, len, c);
        rect(g, l, b - th, len, th, c); rect(g, l, b - len, th, len, c);
        rect(g, r - len, b - th, len, th, c); rect(g, r - th, b - len, th, len, c);

        rect(g, t.sx - 0.5, t.sy - 0.5, 1, 1, c);
        rect(g, l - 4, t.sy, 3, 0.5, c); rect(g, r + 1, t.sy, 3, 0.5, c);
        rect(g, t.sx, tp - 4, 0.5, 3, c); rect(g, t.sx, b + 1, 0.5, 3, c);

        if (locked) {
            double sx = l + ((now / 12) % (long) Math.max(4, hw * 2));
            rect(g, sx, tp - 1.5, 2, 0.5, Config.text());
        }

        Font f = Minecraft.getInstance().font;
        boolean blink = (now / 250) % 2 == 0;
        String status = locked ? (blink ? "[ LOCKED ]" : "[        ]") : "ACQUIRING...";
        text(g, f, status, t.sx - textW(f, status, 0.5f) / 2, tp - 8, 0.5f, locked ? Config.main() : Config.text());
    }

    // ---------------------------------------------------------------- target analysis
    private void drawTargetPanel(GuiGraphics g, Minecraft mc, TargetTracker t, int w, long now) {
        LivingEntity e = t.target;
        Font f = mc.font;
        float k = Config.scaleMul();
        float s = 0.5f * k;
        double lh = 5.6 * k;

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

        // Same damage/health/armor/boss-aware score as the left-side combat overlay.
        double combatScore = combatScore(e, dist);
        String rating = combatScore >= 80 ? "EXTREME" : combatScore >= 60 ? "HIGH"
                : combatScore >= 35 ? "MODERATE" : "LOW";
        String tactic = dist < 4 && combatScore >= 55 ? "KEEP DISTANCE"
                : e.getHealth() < e.getMaxHealth() * 0.25f ? "TARGET WEAK"
                : spd > 5 ? "FAST MOVING" : "TRACK TARGET";

        String[] lines = {
                "ID   " + name.toUpperCase(),
                "CLS  " + cls,
                String.format("RNG  %.1f M", dist),
                String.format("HP   %.1f / %.0f", e.getHealth(), e.getMaxHealth()),
                "ARM  " + e.getArmorValue(),
                String.format("SPD  %.1f M/S", spd),
                "HLD  " + hold.toUpperCase(),
                "THREAT " + threat,
                String.format("COMBAT INDEX %.0f/100", combatScore),
                "ASSESSMENT " + rating,
                String.format("OBSERVED DPS %.1f", t.estimatedDps),
                "TACTIC " + tactic
        };

        Random rnd = new Random(e.getId() * 31L + now / 120);
        String hex = String.format("ID %04X  SIG %04X", e.getId() & 0xFFFF, (int) (combatScore * 100) & 0xFFFF);

        double maxW = textW(f, hex, s);
        for (String l : lines) maxW = Math.max(maxW, textW(f, l, s));
        double px = t.sx + t.halfW + 8;
        if (px + maxW + 6 > w) px = t.sx - t.halfW - 8 - maxW - 4;
        double py = t.sy - t.halfH;

        rect(g, px - 3, py - 1, 0.5, lh * (lines.length + 2) + 6, Config.dim());
        text(g, f, "ANALYSIS", px, py - 6.5, s, Config.dim());
        for (int i = 0; i < lines.length; i++) {
            int col = (i == 7) ? (threat.equals("HIGH") ? Config.main() : threat.equals("MED") ? AMBER : Config.text())
                    : (i == 8 || i == 9) ? (combatScore >= 75 ? Config.main() : combatScore >= 55 ? AMBER : Config.text())
                    : Config.text();
            text(g, f, lines[i], px, py + lh * i, s, col);
        }
        double ratio = Mth.clamp(e.getHealth() / Math.max(1f, e.getMaxHealth()), 0, 1);
        double by = py + lh * lines.length + 0.5;
        rect(g, px, by, 40, 1.5, Config.dim());
        rect(g, px, by, 40 * ratio, 1.5, Config.main());
        text(g, f, hex, px, by + 3.5, s, Config.dim());
    }

    /** Compact live combat-analysis panel rendered over the world on the left side of the HUD. */
    private void drawCombatOverlay(GuiGraphics g, Minecraft mc, int screenW, int screenH, TargetTracker tracker) {
        LocalPlayer self = mc.player;
        if (self == null) return;

        int x = 8;
        int panelW = Math.min(218, Math.max(174, screenW / 3));
        int panelH = Math.min(178, screenH - 24);
        int y = Math.max(72, Math.min(screenH - panelH - 8, screenH / 2 - panelH / 2));
        g.fill(x, y, x + panelW, y + panelH, 0xD0091018);
        g.fill(x, y, x + 2, y + panelH, Config.main());
        g.fill(x + 2, y, x + panelW, y + 1, Config.dim());

        Font f = mc.font;
        float sc = 0.52f * Config.scaleMul();
        double lineH = 6.8 * Config.scaleMul();
        double tx = x + 7;
        double ty = y + 5;
        text(g, f, "T-800 // COMBAT ANALYSIS", tx, ty, sc, Config.main());
        ty += lineH + 1;

        LivingEntity target = tracker.target;
        if (target == null || !target.isAlive() || target.level() != mc.level) {
            text(g, f, "NO TARGET LOCK", tx, ty + 2, sc, AMBER);
            text(g, f, "Aim at a loaded entity", tx, ty + lineH + 3, sc, Config.text());
            text(g, f, "to scan combat data.", tx, ty + lineH * 2 + 3, sc, Config.dim());
            text(g, f, "F4  CLOSE PANEL", tx, y + panelH - 10, sc, Config.dim());
            return;
        }

        double targetAttack = safeAttribute(target, Attributes.ATTACK_DAMAGE);
        double targetToughness = safeAttribute(target, Attributes.ARMOR_TOUGHNESS);
        double playerAttack = safeAttribute(self, Attributes.ATTACK_DAMAGE);
        double playerToughness = safeAttribute(self, Attributes.ARMOR_TOUGHNESS);
        double targetScore = combatScore(target, tracker.distance);
        double termChance = terminationChance(self, target, playerAttack, playerToughness,
                targetAttack, targetToughness, tracker.distance);
        String type = target instanceof Player ? "PLAYER" : TargetTracker.isHostile(target) ? "HOSTILE" : "PASSIVE";
        String weapon = target.getMainHandItem().isEmpty() ? "NONE" : target.getMainHandItem().getHoverName().getString();
        if (weapon.length() > 20) weapon = weapon.substring(0, 20);
        int armorDurability = armorDurabilityPercent(target);
        String rank = targetScore >= 80 ? "EXTREME" : targetScore >= 60 ? "HIGH"
                : targetScore >= 35 ? "MODERATE" : "LOW";
        String chanceLabel = termChance >= 75 ? "FAVOURABLE" : termChance >= 45 ? "RISKY" : "DANGEROUS";

        CombatAIService ai = CombatAIService.INSTANCE;
        String[] rows = {
                target.getName().getString().toUpperCase() + " / " + type,
                String.format("HP %.0f/%.0f  DIST %.1fm", target.getHealth(), target.getMaxHealth(), target.distanceTo(self)),
                String.format("ATTACK %.1f DMG", targetAttack),
                "WEAPON " + weapon.toUpperCase(),
                String.format("ARMOR %d  TOUGH %.1f", target.getArmorValue(), targetToughness),
                String.format("ARMOR CONDITION %d%%", armorDurability),
                String.format("OBSERVED DPS %.1f", tracker.estimatedDps),
                String.format("THREAT %02.0f/100  %s", targetScore, rank),
                String.format(Config.terminationV2 ? "TERM. EST. 2.0 %02.0f%%" : "TERM. BASIC %02.0f%%", termChance),
                "OUTLOOK " + chanceLabel,
                Config.terminationV2 ? "ESTIMATE: HP / GEAR / FOOD / RANGE" : "BASIC HEURISTIC — NOT A WIN ODDS",
                Config.adaptiveCombatAI ? "TACTIC " + ai.advice() : "TACTIC MODULE OFF",
                Config.combatMemory ? "MEMORY " + ai.memory(target) : "MEMORY MODULE OFF",
                Config.survivalProtocol ? String.format("SELF HP %.0f%% ARM %d", self.getHealth() * 100.0 / Math.max(1, self.getMaxHealth()), self.getArmorValue()) : "SURVIVAL MODULE OFF",
                Config.survivalProtocol ? String.format("ARMOR DURABILITY %d%% FOOD %d/20", ai.armorDurability(self), self.getFoodData().getFoodLevel()) : "RESOURCE SCAN OFF",
                Config.multiTargetAnalysis ? "THREATS " + ai.threats().size() + " / PRIORITY SORTED" : "MULTI-TARGET OFF",
                Config.selfDiagnostics ? ai.diagnosis() : "DIAGNOSTICS OFF"
        };
        int colorThreat = targetScore >= 80 ? 0xFFFF4040 : targetScore >= 60 ? AMBER : Config.text();
        for (int i = 0; i < rows.length; i++) {
            int color = (i == 7) ? colorThreat : (i == 8 || i == 9) ? (termChance < 35 ? 0xFFFF4040 : termChance < 60 ? AMBER : Config.main()) : Config.text();
            text(g, f, rows[i].length() > 38 ? rows[i].substring(0, 38) : rows[i], tx, ty + lineH * i, sc, color);
        }
        double barY = y + panelH - 7;
        g.fill((int) tx, (int) barY, x + panelW - 7, (int) barY + 2, Config.dim());
        g.fill((int) tx, (int) barY, (int) (tx + (panelW - 14) * targetScore / 100.0), (int) barY + 2, colorThreat);
        text(g, f, "F4 CLOSE", x + panelW - 38, y + panelH - 15, 0.42f, Config.dim());
    }

    private static double safeAttribute(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        try { return Math.max(0.0, entity.getAttributeValue(attribute)); }
        catch (RuntimeException ignored) { return 0.0; }
    }

    /** A visible, deliberately heuristic 0-100 danger index; it is not a guaranteed win probability. */
    private static double combatScore(LivingEntity e, double distance) {
        double maxHealth = Math.max(1.0, e.getMaxHealth());
        double healthScore = Mth.clamp(Math.sqrt(maxHealth / 100.0), 0, 1);
        double attack = safeAttribute(e, Attributes.ATTACK_DAMAGE);
        double attackScore = Mth.clamp(attack / 30.0, 0, 1);
        double toughness = safeAttribute(e, Attributes.ARMOR_TOUGHNESS);
        double armorScore = Mth.clamp((e.getArmorValue() + toughness * 1.2) / 30.0, 0, 1);
        double weaponScore = weaponThreat(e);
        String id = e.getType().toString().toLowerCase(java.util.Locale.ROOT);
        double bossScore = (id.contains("warden") || id.contains("ender_dragon") || id.contains("wither")) ? 1.0
                : (id.contains("ravager") || id.contains("elder_guardian") || id.contains("evoker")) ? 0.55 : 0.0;
        double currentHealth = Mth.clamp(e.getHealth() / maxHealth, 0, 1);
        double rangeFactor = distance > 20 ? 0.85 : 1.0;
        return Mth.clamp((healthScore * 20 + attackScore * 25 + armorScore * 15
                + weaponScore * 10 + bossScore * 25 + currentHealth * 5) * rangeFactor, 0, 100);
    }

    /** Estimate only: compares current player resources against the target's health, armor and damage. */
    private static double terminationChance(LivingEntity self, LivingEntity target,
                                            double playerAttack, double playerToughness,
                                            double targetAttack, double targetToughness, double distance) {
        double playerPower = self.getHealth() * 1.5 + self.getArmorValue() * 1.4
                + playerToughness + playerAttack * 3.0 + (weaponThreat(self) * 8.0);
        if (Config.terminationV2) {
            // Transparent heuristic adjustments: this is a tactical index, not a true probability.
            playerPower += armorDurabilityPercent(self) * 0.06;
            if (self instanceof Player player) playerPower += player.getFoodData().getFoodLevel() * 0.35;
            playerPower += self.getAbsorptionAmount() * 0.8;
            if (self.hasEffect(MobEffects.STRENGTH)) playerPower += 5;
            if (self.hasEffect(MobEffects.WEAKNESS)) playerPower -= 5;
        }
        double targetPower = target.getHealth() * 0.4 + target.getArmorValue() * 1.4
                + targetToughness + targetAttack * 4.0 + (weaponThreat(target) * 5.0);
        String id = target.getType().toString().toLowerCase(java.util.Locale.ROOT);
        if (id.contains("warden") || id.contains("ender_dragon") || id.contains("wither")) targetPower *= 1.8;
        double chance = 100.0 * playerPower / Math.max(1.0, playerPower + targetPower);
        if (self.getHealth() < self.getMaxHealth() * 0.5) chance -= 15;
        if (distance > 8) chance -= Math.min(12, (distance - 8) * 0.7);
        if (self.getMainHandItem().isEmpty()) chance -= 8;
        return Mth.clamp(chance, 1, 99);
    }

    private static int armorDurabilityPercent(LivingEntity entity) {
        int total = 0;
        int remaining = 0;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty() || stack.getMaxDamage() <= 0) continue;
            total += stack.getMaxDamage();
            remaining += Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        }
        return total == 0 ? 100 : (int) Math.round(remaining * 100.0 / total);
    }

    private static double weaponThreat(LivingEntity e) {
        ItemStack held = e.getMainHandItem();
        double itemScore;
        if (held.isEmpty()) itemScore = 0.05;
        else if (held.is(Items.NETHERITE_SWORD) || held.is(Items.NETHERITE_AXE)) itemScore = 1.0;
        else if (held.is(Items.DIAMOND_SWORD) || held.is(Items.DIAMOND_AXE)) itemScore = 0.88;
        else if (held.is(Items.IRON_SWORD) || held.is(Items.IRON_AXE) || held.is(Items.BOW)
                || held.is(Items.CROSSBOW) || held.is(Items.TRIDENT)) itemScore = 0.68;
        else if (held.is(Items.STONE_SWORD) || held.is(Items.STONE_AXE)) itemScore = 0.48;
        else if (held.is(Items.WOODEN_SWORD) || held.is(Items.WOODEN_AXE)) itemScore = 0.30;
        else itemScore = 0.20;
        // For mobs, the attack-damage attribute is often more informative than a held item.
        return Mth.clamp(Math.max(itemScore, safeAttribute(e, Attributes.ATTACK_DAMAGE) / 30.0), 0, 1);
    }

    private void drawEdgeHint(GuiGraphics g, Minecraft mc, TargetTracker t, int w, int h, long now) {
        if ((now / 300) % 2 != 0) return;
        Font f = mc.font;
        String s = t.targetRight ? "TGT >>>" : "<<< TGT";
        float sc = 0.6f;
        double x = t.targetRight ? w - 10 - textW(f, s, sc) : 10;
        text(g, f, s, x, h / 2.0 - 3, sc, Config.main());
    }

    private void drawKillNotice(GuiGraphics g, Minecraft mc, TargetTracker t, int w, int h, long now) {
        double age = (now - t.killTime) / 2200.0;
        if (t.killTime == 0 || age >= 1) return;
        Font f = mc.font;
        String a = "TARGET TERMINATED";
        String b = t.killName.toUpperCase();
        double cy = h / 2.0 + 28;
        text(g, f, a, w / 2.0 - textW(f, a, 0.8f) / 2, cy, 0.8f, alpha(Config.rgb(), 1 - age * age));
        text(g, f, b, w / 2.0 - textW(f, b, 0.5f) / 2, cy + 9, 0.5f, alpha(Config.text(), 1 - age * age));
    }

    private void drawBoot(GuiGraphics g, Minecraft mc, int w, int h, long now) {
        long el = now - bootStart;
        long total = BOOT.length * 420L + 2200L;
        if (el > total) return;
        double fade = el > total - 700 ? (total - el) / 700.0 : 1.0;
        Font f = mc.font;
        double x = w / 2.0 - 70, y = h / 2.0 - 64;
        skull(g, w / 2.0 - 12, y - 30, 24);
        for (int i = 0; i < BOOT.length; i++) {
            long start = i * 420L;
            if (el < start) break;
            String line = BOOT[i];
            int chars = (int) Math.min(line.length(), (el - start) / 14);
            boolean last = i == BOOT.length - 1;
            text(g, f, "> " + line.substring(0, chars), x, y + i * 7, 0.55f,
                    alpha(last ? Config.rgb() : Config.text(), fade));
        }
    }
}
