package dev.t800.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.network.chat.Component;

/** GigaMap prototype: only samples terrain from chunks already loaded by the client. */
public class GigaMapScreen extends Screen {
    private static final int CELLS = 41;
    private static final int CELL = 5;
    private boolean rotateWithPlayer = true;
    private final int[][] terrainColors = new int[CELLS][CELLS];
    private int cachedX = Integer.MIN_VALUE, cachedZ = Integer.MIN_VALUE, cachedYaw = Integer.MIN_VALUE;
    private boolean cachedTerrainLayer;
    private boolean cacheReady;

    public GigaMapScreen() { super(Component.literal("T-800 // GIGAMAP")); }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("ROTATION: " + (rotateWithPlayer ? "PLAYER" : "NORTH")), b -> {
            rotateWithPlayer = !rotateWithPlayer;
            b.setMessage(Component.literal("ROTATION: " + (rotateWithPlayer ? "PLAYER" : "NORTH")));
        }).bounds(width / 2 - 145, height - 28, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("TERRAIN LAYER: " + (Config.terrainLayer ? "ON" : "OFF")), b -> {
            Config.terrainLayer = !Config.terrainLayer;
            Config.save();
            b.setMessage(Component.literal("TERRAIN LAYER: " + (Config.terrainLayer ? "ON" : "OFF")));
        }).bounds(width / 2 + 15, height - 28, 130, 20).build());
    }

    private void rebuildTerrainCache(Minecraft mc, int px, int pz, int centerY, int yawStep) {
        for (int gz = -CELLS / 2; gz <= CELLS / 2; gz++) {
            for (int gx = -CELLS / 2; gx <= CELLS / 2; gx++) {
                int rx = gx, rz = gz;
                switch (Math.floorMod(yawStep, 4)) {
                    case 1 -> { rx = -gz; rz = gx; }
                    case 2 -> { rx = -gx; rz = -gz; }
                    case 3 -> { rx = gz; rz = -gx; }
                }
                int wx = px + rx, wz = pz + rz;
                int color = 0xAA16191F;
                BlockPos probe = new BlockPos(wx, centerY, wz);
                if (mc.level.hasChunkAt(probe)) {
                    int topY = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
                    BlockPos surface = new BlockPos(wx, Math.max(mc.level.getMinY(), topY - 1), wz);
                    BlockState state = mc.level.getBlockState(surface);
                    int rgb = state.getMapColor(mc.level, surface).col;
                    if (!Config.terrainLayer) color = 0xFF202830;
                    else {
                        int shade = Math.max(0x55, Math.min(0xFF, 0xA0 + (topY - centerY) * 2));
                        color = (shade << 24) | (rgb & 0x00FFFFFF);
                    }
                }
                terrainColors[gz + CELLS / 2][gx + CELLS / 2] = color;
            }
        }
        cachedX = px; cachedZ = pz; cachedYaw = yawStep;
        cachedTerrainLayer = Config.terrainLayer;
        cacheReady = true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        // Avoid Screen.renderBackground: Minecraft 1.21.11 can throw if blur is requested twice in one frame.
        g.fill(0, 0, width, height, 0xE610141B);
        super.render(g, mouseX, mouseY, pt);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        int cx = width / 2, cy = height / 2 - 4;
        int px = mc.player.blockPosition().getX(), pz = mc.player.blockPosition().getZ();
        int startX = cx - CELLS * CELL / 2, startY = cy - CELLS * CELL / 2;
        HudRenderer.text(g, font, "T-800 // GIGAMAP PROTOTYPE", startX, startY - 18, 0.85f, Config.main());
        HudRenderer.text(g, font, "LOADED CHUNKS ONLY // NO SERVER OR UNEXPLORED-TERRAIN REVEAL", startX, startY - 8, 0.45f, Config.dim());
        int yawStep = rotateWithPlayer ? Math.round(mc.player.getYRot() / 90f) : 0;
        int centerY = mc.player.blockPosition().getY();
        if (!cacheReady || px != cachedX || pz != cachedZ || yawStep != cachedYaw
                || cachedTerrainLayer != Config.terrainLayer) {
            rebuildTerrainCache(mc, px, pz, centerY, yawStep);
        }
        for (int gz = -CELLS / 2; gz <= CELLS / 2; gz++) {
            for (int gx = -CELLS / 2; gx <= CELLS / 2; gx++) {
                int sx = startX + (gx + CELLS / 2) * CELL;
                int sy = startY + (gz + CELLS / 2) * CELL;
                g.fill(sx, sy, sx + CELL - 1, sy + CELL - 1,
                        terrainColors[gz + CELLS / 2][gx + CELLS / 2]);
            }
        }
        g.fill(cx - 3, cy - 3, cx + 4, cy + 4, Config.main());
        HudRenderer.text(g, font, "N", cx - 2, startY - 2, 0.5f, Config.text());
        HudRenderer.text(g, font, String.format("X %d  Y %d  Z %d  |  SCALE 1 CELL = 1 BLOCK",
                px, centerY, pz), startX, startY + CELLS * CELL + 5, 0.55f, Config.text());
        HudRenderer.text(g, font, "CAVE VIEW / STRUCTURE BOOKMARKS / 3D ROTATION ARE NOT YET IMPLEMENTED", startX, startY + CELLS * CELL + 15, 0.48f, HudRenderer.AMBER);
    }
}
