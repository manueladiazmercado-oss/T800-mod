package dev.t800.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Options menu (default key: Right Shift). */
public class SettingsScreen extends Screen {
    private static final class Opt {
        final String name; final Supplier<String> value; final Runnable click;
        Opt(String name, Supplier<String> value, Runnable click) { this.name = name; this.value = value; this.click = click; }
        String label() { return name + ": " + value.get(); }
    }

    private final List<Opt> opts = new ArrayList<>();

    public SettingsScreen() { super(Component.literal("T-800 // SYSTEM CONFIGURATION")); }

    private static String onOff(boolean b) { return b ? "ON" : "OFF"; }

    private void buildOpts() {
        opts.clear();
        opts.add(new Opt("HUD", () -> onOff(Config.hud), () -> Config.hud = !Config.hud));
        opts.add(new Opt("Theme", () -> Config.THEMES[Config.theme], () -> Config.theme = (Config.theme + 1) % Config.THEMES.length));
        opts.add(new Opt("Night Vision", () -> onOff(Config.nightVision), () -> Config.nightVision = !Config.nightVision));
        opts.add(new Opt("Infrared Vision [I]", () -> onOff(Config.infraredVision), () -> Config.infraredVision = !Config.infraredVision));
        opts.add(new Opt("Terminator Vision", () -> onOff(Config.vision), () -> Config.vision = !Config.vision));
        opts.add(new Opt("Custom Bars", () -> onOff(Config.customBars), () -> Config.customBars = !Config.customBars));
        opts.add(new Opt("Target Mode", () -> T800HudClient.TRACKER.mode.name(), () -> T800HudClient.TRACKER.cycleMode()));
        opts.add(new Opt("Target Range", () -> Config.RANGES[Config.rangeIdx] + "m", () -> Config.rangeIdx = (Config.rangeIdx + 1) % Config.RANGES.length));
        opts.add(new Opt("Lock Cone", () -> Config.CONES[Config.coneIdx], () -> Config.coneIdx = (Config.coneIdx + 1) % Config.CONES.length));
        opts.add(new Opt("Text Size", () -> Config.SIZES[Config.sizeIdx], () -> Config.sizeIdx = (Config.sizeIdx + 1) % Config.SIZES.length));
        opts.add(new Opt("System Panel", () -> onOff(Config.showSystem), () -> Config.showSystem = !Config.showSystem));
        opts.add(new Opt("Status Panel", () -> onOff(Config.showStatus), () -> Config.showStatus = !Config.showStatus));
        opts.add(new Opt("Effects List", () -> onOff(Config.showEffects), () -> Config.showEffects = !Config.showEffects));
        opts.add(new Opt("Compass", () -> onOff(Config.showCompass), () -> Config.showCompass = !Config.showCompass));
        opts.add(new Opt("Target Panel", () -> onOff(Config.showTarget), () -> Config.showTarget = !Config.showTarget));
        opts.add(new Opt("Vignette", () -> onOff(Config.vignette), () -> Config.vignette = !Config.vignette));
        opts.add(new Opt("Damage Flash", () -> onOff(Config.damageFlash), () -> Config.damageFlash = !Config.damageFlash));
        opts.add(new Opt("Boot Sequence", () -> onOff(Config.boot), () -> Config.boot = !Config.boot));
        opts.add(new Opt("Sounds", () -> onOff(Config.sounds), () -> Config.sounds = !Config.sounds));
        opts.add(new Opt("Termination Lock", () -> onOff(Config.terminationMode), () -> Config.terminationMode = !Config.terminationMode));
        opts.add(new Opt("Combat Analytics", () -> onOff(Config.combatAnalytics), () -> Config.combatAnalytics = !Config.combatAnalytics));\n        opts.add(new Opt("Adaptive Combat AI", () -> onOff(Config.adaptiveCombatAI), () -> Config.adaptiveCombatAI = !Config.adaptiveCombatAI));\n        opts.add(new Opt("Multi-Target Scan", () -> onOff(Config.multiTargetAnalysis), () -> Config.multiTargetAnalysis = !Config.multiTargetAnalysis));\n        opts.add(new Opt("Combat Memory", () -> onOff(Config.combatMemory), () -> Config.combatMemory = !Config.combatMemory));\n        opts.add(new Opt("Survival Protocol", () -> onOff(Config.survivalProtocol), () -> Config.survivalProtocol = !Config.survivalProtocol));\n        opts.add(new Opt("Termination 2.0", () -> onOff(Config.terminationV2), () -> Config.terminationV2 = !Config.terminationV2));\n        opts.add(new Opt("Voice Assistant (OS TTS)", () -> onOff(Config.voiceAssistant), () -> Config.voiceAssistant = !Config.voiceAssistant));\n        opts.add(new Opt("GigaMap Combat Link", () -> onOff(Config.gigaMapCombatLink), () -> Config.gigaMapCombatLink = !Config.gigaMapCombatLink));\n        opts.add(new Opt("Self Diagnostics", () -> onOff(Config.selfDiagnostics), () -> Config.selfDiagnostics = !Config.selfDiagnostics));
        opts.add(new Opt("Armor Alerts", () -> onOff(Config.armorAlerts), () -> Config.armorAlerts = !Config.armorAlerts));
        opts.add(new Opt("Terrain Layer", () -> onOff(Config.terrainLayer), () -> Config.terrainLayer = !Config.terrainLayer));
        opts.add(new Opt("Performance Profile", () -> onOff(Config.performanceProfile), () -> Config.performanceProfile = !Config.performanceProfile));
        opts.add(new Opt("Death Recovery", () -> onOff(Config.deathRecovery), () -> Config.deathRecovery = !Config.deathRecovery));
    }

    @Override
    protected void init() {
        buildOpts();
        int cols = this.height >= 300 ? 2 : 3;
        int bw = 140, bh = 18, gap = 4;
        int totalW = cols * bw + (cols - 1) * gap;
        int x0 = (this.width - totalW) / 2;
        int y0 = 34;
        for (int i = 0; i < opts.size(); i++) {
            final Opt o = opts.get(i);
            int bx = x0 + (i % cols) * (bw + gap);
            int by = y0 + (i / cols) * (bh + gap);
            Button b = Button.builder(Component.literal(o.label()), btn -> {
                o.click.run();
                btn.setMessage(Component.literal(o.label()));
                Config.save();
            }).bounds(bx, by, bw, bh).build();
            this.addRenderableWidget(b);
        }
        int rows = (opts.size() + cols - 1) / cols;
        int doneY = y0 + rows * (bh + gap) + 6;
        this.addRenderableWidget(Button.builder(Component.literal("DONE"), b -> this.onClose())
                .bounds(this.width / 2 - 50, doneY, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        String title = "T-800 // SYSTEM CONFIGURATION";
        HudRenderer.skull(g, this.width / 2.0 - this.font.width(title) * 0.5 - 18, 9, 12);
        HudRenderer.text(g, this.font, title, this.width / 2.0 - this.font.width(title) * 0.5, 12, 1.0f, Config.main());
        String hint = "H HUD | J Mode | K Vision | N Night | Y Armor | F4 Analytics | F6 GigaMap | F8 Lock | F9 Performance | I Infrared";
        HudRenderer.text(g, this.font, hint, Math.max(4, this.width / 2.0 - this.font.width(hint) * 0.25), 24, 0.5f, Config.dim());
    }

    @Override
    public void onClose() {
        Config.save();
        super.onClose();
    }
}
