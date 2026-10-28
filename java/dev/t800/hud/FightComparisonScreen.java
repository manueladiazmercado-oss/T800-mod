package dev.t800.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Side-by-side summary of the two most recent locally recorded fights for a target. */
public final class FightComparisonScreen extends Screen {
    private final String uuid;
    private List<Path> replays = List.of();
    private List<TargetDossierService.ReplayFrame> first = List.of();
    private List<TargetDossierService.ReplayFrame> second = List.of();
    private String firstName = "NO FIGHT";
    private String secondName = "NO FIGHT";

    public FightComparisonScreen(String uuid) {
        super(Component.literal("T-800 // FIGHT COMPARISON"));
        this.uuid = uuid == null ? "" : uuid;
    }

    @Override protected void init() {
        replays = TargetDossierService.INSTANCE.replaysFor(uuid);
        if (!replays.isEmpty()) { firstName = replays.get(0).getFileName().toString(); first = TargetDossierService.INSTANCE.readReplay(replays.get(0)); }
        if (replays.size() > 1) { secondName = replays.get(1).getFileName().toString(); second = TargetDossierService.INSTANCE.readReplay(replays.get(1)); }
        addRenderableWidget(Button.builder(Component.literal("SWAP FIGHTS"), b -> {
            List<TargetDossierService.ReplayFrame> tmp = first; first = second; second = tmp;
            String n = firstName; firstName = secondName; secondName = n;
        }).bounds(16, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("BACK TO DOSSIER"), b -> {
            if (minecraft != null) minecraft.setScreen(new TargetDossierScreen());
        }).bounds(122, height - 28, 132, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CLOSE"), b -> onClose())
                .bounds(width - 96, height - 28, 80, 20).build());
    }

    private void line(GuiGraphics g, String s, int x, int y, float scale, int color) {
        HudRenderer.text(g, font, s, x, y, scale, color);
    }

    private String stats(List<TargetDossierService.ReplayFrame> frames, String metric) {
        if (frames.isEmpty()) return "NO RECORDED DATA";
        var a = frames.get(0); var b = frames.get(frames.size()-1);
        double avgDistance = frames.stream().mapToDouble(TargetDossierService.ReplayFrame::distance).average().orElse(0);
        double minDistance = frames.stream().mapToDouble(TargetDossierService.ReplayFrame::distance).min().orElse(0);
        double targetLoss = Math.max(0, a.targetHealth() - b.targetHealth());
        double selfLoss = Math.max(0, a.selfHealth() - b.selfHealth());
        double duration = Math.max(0, b.elapsedMs() - a.elapsedMs()) / 1000.0;
        return switch (metric) {
            case "duration" -> String.format(Locale.ROOT, "DURATION %.1fs", duration);
            case "distance" -> String.format(Locale.ROOT, "AVG DIST %.2fm  |  CLOSEST %.2fm", avgDistance, minDistance);
            case "target" -> String.format(Locale.ROOT, "OBSERVED TARGET HP LOSS %.1f", targetLoss);
            case "self" -> String.format(Locale.ROOT, "OBSERVED SELF HP LOSS %.1f", selfLoss);
            default -> String.format(Locale.ROOT, "SAMPLES %d  |  TARGET ITEM %s", frames.size(), b.targetItem());
        };
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xF0080B10);
        super.render(g, mouseX, mouseY, partialTick);
        line(g, "T-800 // FIGHT COMPARISON", 16, 12, 0.9f, Config.main());
        line(g, "OBSERVED LOCAL TELEMETRY ONLY // HP CHANGES MAY HAVE OTHER CAUSES", 16, 28, 0.42f, Config.dim());
        int split = width / 2;
        g.fill(12, 48, split - 6, height - 42, 0xB018202B);
        g.fill(split + 4, 48, width - 12, height - 42, 0xB018202B);
        line(g, "FIGHT A // " + trim(firstName, 36), 22, 58, 0.48f, 0xFF43C9FF);
        line(g, "FIGHT B // " + trim(secondName, 36), split + 14, 58, 0.48f, 0xFFFF777D);
        String[] metrics = {"samples", "duration", "distance", "target", "self"};
        int y = 84;
        for (String m : metrics) {
            line(g, stats(first, m), 22, y, 0.48f, Config.text());
            line(g, stats(second, m), split + 14, y, 0.48f, Config.text());
            y += 22;
        }
        if (second.isEmpty()) line(g, "RECORD ANOTHER FIGHT TO COMPARE TWO SESSIONS", split + 14, y + 8, 0.43f, HudRenderer.AMBER);
        line(g, "Fight comparison uses sampled positions and HP; it is not a full video replay.", 16, height - 39, 0.42f, Config.dim());
    }

    private static String trim(String s, int max) { return s.length() <= max ? s : s.substring(0, max - 2) + ".."; }

    @Override public void onClose() {
        super.onClose();
        if (minecraft != null) minecraft.setScreen(new TargetDossierScreen());
    }
}
