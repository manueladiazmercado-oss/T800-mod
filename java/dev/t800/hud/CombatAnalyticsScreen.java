package dev.t800.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Live, local T-800 combat telemetry and target assessment. */
public class CombatAnalyticsScreen extends Screen {
    public CombatAnalyticsScreen() { super(Component.literal("T-800 // COMBAT ANALYTICS")); }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("TERMINATION MODE: " + (Config.terminationMode ? "ON" : "OFF")), b -> {
            Config.terminationMode = !Config.terminationMode;
            Config.save();
            b.setMessage(Component.literal("TERMINATION MODE: " + (Config.terminationMode ? "ON" : "OFF")));
        }).bounds(width / 2 - 105, height - 52, 210, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CLOSE"), b -> onClose())
                .bounds(width / 2 - 40, height - 27, 80, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        // Avoid Screen.renderBackground: Minecraft 1.21.11 can throw if blur is requested twice in one frame.
        g.fill(0, 0, width, height, 0xE610141B);
        super.render(g, mouseX, mouseY, pt);
        Minecraft mc = Minecraft.getInstance();
        int x = Math.max(12, width / 2 - 150);
        int y = 24;
        HudRenderer.skull(g, x, y, 14);
        HudRenderer.text(g, font, "T-800 // COMBAT ANALYTICS", x + 20, y + 3, 0.95f, Config.main());
        y += 24;
        HudRenderer.text(g, font, "LOCAL CLIENT TELEMETRY // HEURISTIC ASSESSMENT", x, y, 0.55f, Config.dim());
        y += 18;
        LivingEntity target = T800HudClient.TRACKER.target;
        if (mc.player == null) {
            HudRenderer.text(g, font, "PLAYER DATA UNAVAILABLE", x, y, 0.7f, HudRenderer.AMBER);
        } else {
            var p = mc.player;
            ItemStack off = p.getOffhandItem();
            HudRenderer.text(g, font, String.format("SELF  HP %.1f/%.1f  ARM %d  ABS %.1f",
                    p.getHealth(), p.getMaxHealth(), p.getArmorValue(), p.getAbsorptionAmount()), x, y, 0.65f, Config.text());
            y += 12;
            HudRenderer.text(g, font, "OFFHAND  " + (off.isEmpty() ? "EMPTY" : off.getHoverName().getString()), x, y, 0.62f, Config.text());
            y += 17;
            if (target == null || !target.isAlive()) {
                HudRenderer.text(g, font, "NO ACTIVE TARGET // AIM AT A LOADED ENTITY", x, y, 0.65f, HudRenderer.AMBER);
            } else {
                double hp = target.getHealth() / Math.max(1.0, target.getMaxHealth());
                double armor = Math.min(1.0, target.getArmorValue() / 20.0);
                double weapon = target.getMainHandItem().isEmpty() ? 0.12 : 0.55;
                double score = Math.max(0, Math.min(100, hp * 35 + armor * 25 + weapon * 25 + 10));
                String rank = score >= 75 ? "EXTREME" : score >= 55 ? "HIGH" : score >= 30 ? "MODERATE" : "LOW";
                HudRenderer.text(g, font, "TARGET  " + target.getName().getString(), x, y, 0.75f, Config.main()); y += 14;
                HudRenderer.text(g, font, String.format("HEALTH %.1f/%.1f  ARMOR %d  RANGE %.1fm",
                        target.getHealth(), target.getMaxHealth(), target.getArmorValue(), target.distanceTo(mc.player)), x, y, 0.62f, Config.text()); y += 12;
                HudRenderer.text(g, font, "MAIN HAND  " + target.getMainHandItem().getHoverName().getString(), x, y, 0.62f, Config.text()); y += 12;
                if (target instanceof Player player) {
                    ItemStack shield = player.getOffhandItem();
                    HudRenderer.text(g, font, "PLAYER OFFHAND  " + (shield.isEmpty() ? "EMPTY" : shield.getHoverName().getString()), x, y, 0.62f, Config.text()); y += 12;
                }
                HudRenderer.text(g, font, String.format("THREAT INDEX  %.0f/100  %s", score, rank), x, y, 0.7f, score >= 55 ? HudRenderer.AMBER : Config.main()); y += 13;
                HudRenderer.text(g, font, String.format("OBSERVED DPS  %.1f  | LOCK %s",
                        T800HudClient.TRACKER.estimatedDps, Config.terminationMode ? "ENGAGED" : "MANUAL"), x, y, 0.62f, Config.text()); y += 12;
                HudRenderer.text(g, font, "TACTIC  " + (target.distanceTo(mc.player) < 4 && score > 55 ? "KEEP DISTANCE" :
                        target.getHealth() < target.getMaxHealth() * 0.25f ? "TARGET WEAK" : "TRACK TARGET"), x, y, 0.62f, Config.main());
            }
        }
        HudRenderer.text(g, font, "F4 ANALYTICS  |  F6 GIGAMAP  |  F8 TERMINATION LOCK", x, height - 68, 0.55f, Config.dim());
    }
}
