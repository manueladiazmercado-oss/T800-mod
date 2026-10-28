package dev.t800.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Playback viewer reconstructing a top-down movement trace from locally captured combat telemetry. */
public final class CombatReplayScreen extends Screen {
    private final String uuid;
    private List<TargetDossierService.ReplayFrame> frames = List.of();
    private List<Path> replayFiles = List.of();
    private int replayFileIndex;
    private int frameIndex;
    private boolean playing;
    private int tickCounter;
    private String replayName = "NO REPLAY";

    public CombatReplayScreen(String uuid) {
        super(Component.literal("T-800 // COMBAT REPLAY"));
        this.uuid = uuid == null ? "" : uuid;
    }

    @Override protected void init() {
        replayFiles = TargetDossierService.INSTANCE.replaysFor(uuid);
        Path path = replayFiles.isEmpty() ? TargetDossierService.INSTANCE.latestReplay(uuid) : replayFiles.get(0);
        if (path != null) loadReplay(path);
        addRenderableWidget(Button.builder(Component.literal("PLAY / PAUSE"), b -> playing = !playing)
                .bounds(16, height - 28, 104, 20).build());
        addRenderableWidget(Button.builder(Component.literal("STEP +"), b -> {
            if (!frames.isEmpty()) frameIndex = Math.min(frames.size() - 1, frameIndex + 1);
        }).bounds(126, height - 28, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("STEP -"), b -> {
            if (!frames.isEmpty()) frameIndex = Math.max(0, frameIndex - 1);
        }).bounds(202, height - 28, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("OLDER FIGHT"), b -> {
            if (!replayFiles.isEmpty() && replayFileIndex + 1 < replayFiles.size()) loadReplay(replayFiles.get(++replayFileIndex));
        }).bounds(278, height - 28, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("NEWER FIGHT"), b -> {
            if (!replayFiles.isEmpty() && replayFileIndex > 0) loadReplay(replayFiles.get(--replayFileIndex));
        }).bounds(374, height - 28, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CLOSE"), b -> onClose())
                .bounds(width - 96, height - 28, 80, 20).build());
    }

    private void loadReplay(Path path) {
        playing = false;
        frameIndex = 0;
        tickCounter = 0;
        replayName = path.getFileName().toString();
        frames = TargetDossierService.INSTANCE.readReplay(path);
    }

    @Override public void tick() {
        super.tick();
        if (playing && !frames.isEmpty() && ++tickCounter >= 2) {
            tickCounter = 0;
            if (frameIndex + 1 >= frames.size()) { frameIndex = 0; playing = false; }
            else frameIndex++;
        }
    }

    private void line(GuiGraphics g, String s, int x, int y, float scale, int color) {
        HudRenderer.text(g, font, s, x, y, scale, color);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xF0080B10);
        super.render(g, mouseX, mouseY, partialTick);
        line(g, "T-800 // COMBAT REPLAY", 16, 12, 0.9f, Config.main());
        line(g, replayName.length() > 72 ? replayName.substring(0, 70) + ".." : replayName, 16, 28, 0.42f, Config.dim());
        if (frames.isEmpty()) {
            line(g, "NO REPLAY DATA FOUND", 22, 58, 0.7f, HudRenderer.AMBER);
            line(g, "Aim at a player and keep the target locked to record movement samples.", 22, 75, 0.5f, Config.text());
            line(g, "Replay files are local CSV telemetry; no server-side data is accessed.", 22, 89, 0.45f, Config.dim());
            return;
        }

        TargetDossierService.ReplayFrame f = frames.get(Math.min(frameIndex, frames.size() - 1));
        int mapX = 18, mapY = 52, mapW = Math.max(180, width - 36), mapH = Math.max(100, height - 112);
        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0xC0121821);
        for (int x = mapX + 20; x < mapX + mapW; x += 20) g.fill(x, mapY, x + 1, mapY + mapH, 0x332C3A4A);
        for (int y = mapY + 20; y < mapY + mapH; y += 20) g.fill(mapX, y, mapX + mapW, y + 1, 0x332C3A4A);

        double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minZ=Double.POSITIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        for (var p : frames) {
            minX=Math.min(minX,Math.min(p.selfX(),p.targetX())); maxX=Math.max(maxX,Math.max(p.selfX(),p.targetX()));
            minZ=Math.min(minZ,Math.min(p.selfZ(),p.targetZ())); maxZ=Math.max(maxZ,Math.max(p.selfZ(),p.targetZ()));
        }
        double dx=Math.max(4,maxX-minX), dz=Math.max(4,maxZ-minZ);
        int innerW=mapW-24, innerH=mapH-24;
        int prevSx=-1,prevSy=-1,prevTx=-1,prevTy=-1;
        for (int i=0;i<=frameIndex && i<frames.size();i++) {
            var p=frames.get(i);
            int sx=mapX+12+(int)((p.selfX()-minX)/dx*innerW), sy=mapY+12+(int)((p.selfZ()-minZ)/dz*innerH);
            int tx=mapX+12+(int)((p.targetX()-minX)/dx*innerW), ty=mapY+12+(int)((p.targetZ()-minZ)/dz*innerH);
            if(prevSx>=0) { g.fill(Math.min(prevSx,sx),Math.min(prevSy,sy),Math.max(prevSx,sx)+1,Math.max(prevSy,sy)+1,0xAA43C9FF); }
            if(prevTx>=0) { g.fill(Math.min(prevTx,tx),Math.min(prevTy,ty),Math.max(prevTx,tx)+1,Math.max(prevTy,ty)+1,0xAAFF4C55); }
            prevSx=sx;prevSy=sy;prevTx=tx;prevTy=ty;
        }
        int sx=mapX+12+(int)((f.selfX()-minX)/dx*innerW), sy=mapY+12+(int)((f.selfZ()-minZ)/dz*innerH);
        int tx=mapX+12+(int)((f.targetX()-minX)/dx*innerW), ty=mapY+12+(int)((f.targetZ()-minZ)/dz*innerH);
        g.fill(sx-4,sy-4,sx+5,sy+5,0xFF43C9FF);
        g.fill(tx-4,ty-4,tx+5,ty+5,0xFFFF4C55);
        line(g,"YOU",sx+6,sy-4,0.4f,0xFF43C9FF);
        line(g,"TARGET",tx+6,ty-4,0.4f,0xFFFF777D);
        int infoY=mapY+mapH+5;
        line(g,String.format(Locale.ROOT,"FRAME %d / %d   TIME %.1fs   DIST %.2fm",frameIndex+1,frames.size(),f.elapsedMs()/1000.0,f.distance()),16,infoY,0.48f,Config.text());
        line(g,String.format(Locale.ROOT,"YOU HP %.1f   TARGET HP %.1f   TARGET ITEM %s",f.selfHealth(),f.targetHealth(),f.targetItem()),16,infoY+11,0.44f,Config.text());
        line(g,String.format(Locale.ROOT,"YOU %.1f %.1f %.1f   TARGET %.1f %.1f %.1f",f.selfX(),f.selfY(),f.selfZ(),f.targetX(),f.targetY(),f.targetZ()),16,infoY+21,0.39f,Config.dim());
        if(!f.event().isBlank()) line(g,"EVENT: "+f.event(),16,infoY+31,0.44f,HudRenderer.AMBER);
        line(g,"BLUE = YOU PATH   RED = TARGET PATH   PLAYBACK RECONSTRUCTED FROM SAMPLED POSITIONS",16,height-39,0.39f,Config.dim());
    }

    @Override public void onClose() {
        playing = false;
        super.onClose();
        if (minecraft != null) minecraft.setScreen(new TargetDossierScreen());
    }
}
