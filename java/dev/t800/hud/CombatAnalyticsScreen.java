package dev.t800.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Live local combat telemetry with a rotating preview of the tracked target. */
public class CombatAnalyticsScreen extends Screen {
    public CombatAnalyticsScreen() { super(Component.literal("T-800 // COMBAT ANALYTICS")); }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("TERMINATION MODE: " + (Config.terminationMode ? "ON" : "OFF")), b -> {
            Config.terminationMode = !Config.terminationMode;
            Config.save();
            b.setMessage(Component.literal("TERMINATION MODE: " + (Config.terminationMode ? "ON" : "OFF")));
        }).bounds(18, height - 28, 172, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CLOSE"), b -> onClose())
                .bounds(width - 98, height - 28, 80, 20).build());
    }

    private void line(GuiGraphics g, String text, int x, int y, float scale, int color) {
        HudRenderer.text(g, font, text, x, y, scale, color);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        // A flat background avoids Minecraft 1.21.11's duplicate-blur exception.
        g.fill(0, 0, width, height, 0xF00A0E14);
        super.render(g, mouseX, mouseY, pt);
        Minecraft mc = Minecraft.getInstance();
        int margin = 18;
        int split = Math.max(width / 2, 175);
        int top = 18;
        g.fill(margin - 5, top - 4, split - 10, height - 38, 0xB019202A);
        g.fill(split, top - 4, width - margin, height - 38, 0xB019202A);
        line(g, "T-800 // COMBAT ANALYTICS", margin, top, 0.9f, Config.main());
        line(g, "LIVE LOCAL SENSOR DATA", margin, top + 13, 0.5f, Config.dim());

        int y = top + 30;
        LivingEntity target = T800HudClient.TRACKER.target;
        if (mc.player == null) {
            line(g, "PLAYER DATA UNAVAILABLE", margin, y, 0.65f, HudRenderer.AMBER);
        } else {
            var p = mc.player;
            ItemStack off = p.getOffhandItem();
            line(g, "SELF STATUS", margin, y, 0.62f, Config.main()); y += 12;
            line(g, String.format("HP       %.1f / %.1f", p.getHealth(), p.getMaxHealth()), margin, y, 0.58f, Config.text()); y += 10;
            line(g, String.format("ARMOR    %d", p.getArmorValue()), margin, y, 0.58f, Config.text()); y += 10;
            line(g, String.format("ABSORB   %.1f", p.getAbsorptionAmount()), margin, y, 0.58f, Config.text()); y += 10;
            line(g, "OFFHAND  " + (off.isEmpty() ? "EMPTY" : off.getHoverName().getString()), margin, y, 0.52f, Config.text()); y += 15;
            line(g, "TARGET ASSESSMENT", margin, y, 0.62f, Config.main()); y += 12;
            if (target == null || !target.isAlive() || target.level() != mc.level) {
                line(g, "NO ACTIVE TARGET", margin, y, 0.62f, HudRenderer.AMBER); y += 12;
                line(g, "Aim at a loaded entity to acquire", margin, y, 0.48f, Config.dim());
                line(g, "a target, then reopen this screen.", margin, y + 9, 0.48f, Config.dim());
                line(g, "TARGET MODEL: --", split + 14, top + 30, 0.62f, HudRenderer.AMBER);
            } else {
                double hpRatio = target.getHealth() / Math.max(1.0, target.getMaxHealth());
                double armorRatio = Math.min(1.0, target.getArmorValue() / 20.0);
                double weapon = target.getMainHandItem().isEmpty() ? 0.12 : 0.55;
                double score = Math.max(0, Math.min(100, hpRatio * 35 + armorRatio * 25 + weapon * 25 + 10));
                String rank = score >= 75 ? "EXTREME" : score >= 55 ? "HIGH" : score >= 30 ? "MODERATE" : "LOW";
                line(g, "NAME     " + target.getName().getString(), margin, y, 0.56f, Config.text()); y += 11;
                line(g, "TYPE     " + (target instanceof Player ? "PLAYER" : TargetTracker.isHostile(target) ? "HOSTILE" : "NEUTRAL"), margin, y, 0.56f, Config.text()); y += 11;
                line(g, String.format("HEALTH   %.1f / %.1f", target.getHealth(), target.getMaxHealth()), margin, y, 0.56f, Config.text()); y += 11;
                line(g, String.format("ARMOR    %d", target.getArmorValue()), margin, y, 0.56f, Config.text()); y += 11;
                line(g, String.format("RANGE    %.1f m", target.distanceTo(mc.player)), margin, y, 0.56f, Config.text()); y += 11;
                line(g, "WEAPON   " + (target.getMainHandItem().isEmpty() ? "NONE" : target.getMainHandItem().getHoverName().getString()), margin, y, 0.50f, Config.text()); y += 11;
                if (target instanceof Player player) {
                    ItemStack shield = player.getOffhandItem();
                    line(g, "OFFHAND  " + (shield.isEmpty() ? "EMPTY" : shield.getHoverName().getString()), margin, y, 0.50f, Config.text()); y += 11;
                }
                line(g, String.format("THREAT   %.0f / 100  [%s]", score, rank), margin, y, 0.58f,
                        score >= 55 ? HudRenderer.AMBER : Config.main()); y += 12;
                line(g, String.format("OBS. DPS %.1f", T800HudClient.TRACKER.estimatedDps), margin, y, 0.56f, Config.text()); y += 11;
                String tactic = target.distanceTo(mc.player) < 4 && score > 55 ? "KEEP DISTANCE"
                        : target.getHealth() < target.getMaxHealth() * 0.25f ? "TARGET WEAK" : "TRACK TARGET";
                line(g, "TACTIC   " + tactic, margin, y, 0.56f, Config.main()); y += 11;
                line(g, "SKILL EST. " + CombatAIService.INSTANCE.skillScore(target) + "/100", margin, y, 0.54f, Config.main()); y += 10;
                String patternData = CombatAIService.INSTANCE.patternSummary(target);
                if (patternData.length() > 34) patternData = patternData.substring(0, 34);
                line(g, "PATTERN  " + patternData, margin, y, 0.45f, Config.text());
                // Use the real tracked entity and animate the mouse-following preview to create a slow turn.
                int panelX = split + 8;
                int panelW = width - panelX - margin - 8;
                int modelTop = top + 28;
                g.fill(panelX, modelTop, panelX + panelW, height - 44, 0x88101620);
                line(g, "TARGET HOLOGRAM // LIVE MODEL", panelX + 6, modelTop + 5, 0.48f, Config.main());
                int modelSize = Math.max(28, Math.min(90, Math.min(panelW - 18, height - modelTop - 70)));
                float turn = (float) (Math.sin(System.currentTimeMillis() / 1400.0) * 45.0);
                try {
                    InventoryScreen.renderEntityInInventoryFollowsMouse(g,
                            panelX + 5, modelTop + 18,
                            panelX + panelW - 5, height - 48,
                            modelSize, 0.0f,
                            panelX + panelW / 2f + turn, modelTop + 45, target);
                } catch (RuntimeException ignored) {
                    // A model preview must never take down the entire analytics screen.
                    line(g, "MODEL PREVIEW UNAVAILABLE", panelX + 6, modelTop + 24, 0.48f, HudRenderer.AMBER);
                }
                line(g, "ROTATING TARGET PREVIEW", panelX + 6, height - 57, 0.45f, Config.dim());
            }
        }
        line(g, "F4 ANALYTICS  |  F6 GIGAMAP  |  F8 TERMINATION LOCK", margin, height - 39, 0.45f, Config.dim());
    }
}
