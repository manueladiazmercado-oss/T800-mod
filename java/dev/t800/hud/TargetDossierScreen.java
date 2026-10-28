package dev.t800.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;\nimport java.nio.file.Path;

/** Persistent local dossier and compact event replay browser. */
public final class TargetDossierScreen extends Screen {
    private String selectedUuid = "";
    private int page;\n    private String searchQuery = "";

    public TargetDossierScreen() {
        super(Component.literal("T-800 // TARGET DOSSIER"));
        selectedUuid = TargetDossierService.INSTANCE.activeUuid();
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("PREVIOUS"), b -> {
            List<TargetDossierService.Dossier> all = filteredDossiers();
            if (!all.isEmpty()) {
                page = (page + 1) % all.size();
                selectedUuid = all.get(page).uuid;
            }
        }).bounds(16, height - 28, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("NEXT"), b -> {
            List<TargetDossierService.Dossier> all = TargetDossierService.INSTANCE.recentDossiers(0);
            if (!all.isEmpty()) {
                page = Math.floorMod(page - 1, all.size());
                selectedUuid = all.get(page).uuid;
            }
        }).bounds(112, height - 28, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("OPEN REPLAY"), b -> {
            TargetDossierService.Dossier d = TargetDossierService.INSTANCE.recentDossiers(0).stream()
                    .filter(p -> p.uuid.equals(selectedUuid)).findFirst().orElse(null);
            if (d != null) minecraft.setScreen(new CombatReplayScreen(d.uuid));
        }).bounds(210, height - 28, 108, 20).build());
        addRenderableWidget(Button.builder(Component.literal("EXPORT"), b -> {
            Path exported = TargetDossierService.INSTANCE.exportDossier(selectedUuid);
            if (minecraft != null && minecraft.player != null) minecraft.player.displayClientMessage(
                    Component.literal(exported == null ? "T-800 // EXPORT FAILED" : "T-800 // EXPORTED: config/t800hud/exports/" + exported.getFileName()), true);
        }).bounds(444, height - 28, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("COMPARE"), b -> {
            if (minecraft != null && !selectedUuid.isBlank()) minecraft.setScreen(new FightComparisonScreen(selectedUuid));
        }).bounds(520, height - 28, 82, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CLOSE"), b -> onClose())
                .bounds(width - 96, height - 28, 80, 20).build());
    }

    private List<TargetDossierService.Dossier> filteredDossiers() {\n        String q = searchQuery.toLowerCase(Locale.ROOT).trim();\n        return TargetDossierService.INSTANCE.recentDossiers(0).stream().filter(d -> q.isEmpty() || d.name.toLowerCase(Locale.ROOT).contains(q) || d.uuid.toLowerCase(Locale.ROOT).contains(q) || d.server.toLowerCase(Locale.ROOT).contains(q)).toList();\n    }\n\n    @Override public boolean charTyped(char chr, int modifiers) {\n        if (!Character.isISOControl(chr)) { searchQuery += chr; page = 0; selectedUuid = ""; return true; }\n        return super.charTyped(chr, modifiers);\n    }\n\n    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {\n        if (keyCode == 256) { onClose(); return true; }\n        if (keyCode == 259 && !searchQuery.isEmpty()) { searchQuery = searchQuery.substring(0, searchQuery.length()-1); page = 0; selectedUuid = ""; return true; }\n        if (keyCode == 261) { searchQuery = ""; page = 0; selectedUuid = ""; return true; }\n        return super.keyPressed(keyCode, scanCode, modifiers);\n    }\n\n    private void line(GuiGraphics g, String text, int x, int y, float scale, int color) {
        HudRenderer.text(g, font, text, x, y, scale, color);
    }

    private static String safe(String s, int max) {
        if (s == null) return "-";
        return s.length() <= max ? s : s.substring(0, max - 2) + "..";
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xF0090D13);
        super.render(g, mouseX, mouseY, partialTick);
        int left = 16, top = 16, split = Math.max(155, width / 2);
        g.fill(left - 4, top - 4, split - 8, height - 36, 0xB018202B);
        g.fill(split, top - 4, width - 16, height - 36, 0xB018202B);
        line(g, "T-800 // TARGET DOSSIER", left, top, 0.9f, Config.main());
        line(g, "LOCAL OBSERVATIONS ONLY // NO SERVER DATA EXTRACTION", left, top + 13, 0.42f, Config.dim());\n        line(g, "GLOBAL SEARCH: " + (searchQuery.isEmpty() ? "TYPE NAME / UUID / SERVER" : searchQuery) + "  [BACKSPACE DELETE / DELETE CLEAR]", left, top + 22, 0.40f, HudRenderer.AMBER);

        List<TargetDossierService.Dossier> profiles = filteredDossiers();
        TargetDossierService.Dossier d = profiles.stream().filter(p -> p.uuid.equals(selectedUuid)).findFirst().orElse(null);
        if (d == null && !profiles.isEmpty()) {
            d = profiles.get(0);
            selectedUuid = d.uuid;
        }
        int y = top + 40;
        if (d == null) {
            line(g, "NO PLAYER DOSSIERS YET", left, y, 0.65f, HudRenderer.AMBER);
            line(g, "Aim at a player to acquire a target.", left, y + 14, 0.5f, Config.text());
            line(g, "Dossiers are saved locally between sessions.", left, y + 25, 0.46f, Config.dim());
        } else {
            line(g, "IDENTITY // " + safe(d.name, 26), left, y, 0.75f, Config.main()); y += 15;
            line(g, "UUID " + safe(d.uuid, 36), left, y, 0.42f, Config.dim()); y += 12;
            line(g, "SIGHTINGS " + d.sightings + "   TARGET LOCKS " + d.observedLocks, left, y, 0.55f, Config.text()); y += 12;
            line(g, "REPLAYS " + d.replayCount + "   RECORDED FRAMES IN LATEST FILE", left, y, 0.44f, Config.dim()); y += 11;
            line(g, "OBSERVED TARGET HP LOSS " + String.format(Locale.ROOT, "%.1f", d.observedTargetHealthLoss), left, y, 0.52f, Config.text()); y += 11;
            line(g, "SELF HP LOSS WHILE TRACKED " + String.format(Locale.ROOT, "%.1f", d.observedSelfHealthLoss), left, y, 0.52f, Config.text()); y += 11;
            line(g, "HP-DROP EVENTS " + d.observedHealthDrops + "   DAMAGE EVENTS " + d.observedDamageTakenEvents, left, y, 0.48f, Config.text()); y += 13;
            line(g, "LAST HELD ITEM " + safe(d.lastHeldItem, 28), left, y, 0.5f, Config.text()); y += 11;
            line(g, "LAST PATTERN " + safe(d.lastPattern, 32), left, y, 0.44f, Config.dim()); y += 13;
            line(g, "LAST LOCATION " + d.x + " " + d.y + " " + d.z, left, y, 0.52f, Config.text()); y += 11;
            line(g, "DIMENSION " + safe(d.dimension, 28), left, y, 0.46f, Config.dim()); y += 11;
            line(g, "SERVER " + safe(d.server, 32), left, y, 0.46f, Config.dim()); y += 11;
            line(g, "LAST SEEN " + (d.lastSeen == 0 ? "-" : String.format(Locale.ROOT, "%tF %tT", d.lastSeen, d.lastSeen)), left, y, 0.46f, Config.dim());
        }

        int rx = split + 10;
        line(g, "COMBAT REPLAY // OBSERVED EVENTS", rx, top, 0.66f, Config.main());
        List<TargetDossierService.ReplayEvent> events = TargetDossierService.INSTANCE.recentEvents(d == null ? "" : d.uuid, 18);
        int ey = top + 17;
        if (events.isEmpty()) {
            line(g, "No recorded events for this dossier.", rx, ey, 0.48f, Config.dim());
            line(g, "Lock a player and keep the target tracked.", rx, ey + 11, 0.45f, Config.dim());
        } else {
            for (TargetDossierService.ReplayEvent e : events) {
                String row = e.display();
                if (row.length() > 48) row = row.substring(0, 46) + "..";
                line(g, row, rx, ey, 0.45f, e.kind().equals("TARGET HP") ? HudRenderer.AMBER : Config.text());
                ey += 9;
                if (ey > height - 48) break;
            }
        }
        line(g, "TYPE TO SEARCH ALL LOCAL DOSSIERS  |  EXPORT SAVES TXT TO config/t800hud/exports", 16, height - 39, 0.40f, Config.dim());
    }

    @Override
    public void onClose() {
        super.onClose();
    }
}
