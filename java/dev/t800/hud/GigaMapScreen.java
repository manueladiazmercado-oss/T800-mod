package dev.t800.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Client-side surface map. Only reads chunks already loaded by the client;
 * zoom changes the number of blocks represented by each sampled map cell.
 */
public class GigaMapScreen extends Screen {
    private static final int CELLS = 41;
    private static final int[] ZOOM = {1, 2, 4, 8, 16};
    private int zoomIndex = 0;
    private boolean rotateWithPlayer = true;
    private boolean relief3D = true;
    private boolean showHeight = true;
    private final int[][] terrainHeights = new int[CELLS][CELLS];
    private final int[][] terrainColors = new int[CELLS][CELLS];
    private int cachedX = Integer.MIN_VALUE, cachedZ = Integer.MIN_VALUE, cachedYaw = Integer.MIN_VALUE, cachedZoom = -1;
    private boolean cachedTerrainLayer;
    private boolean cacheReady;

    public GigaMapScreen() { super(Component.literal("T-800 // GIGAMAP")); }

    @Override
    protected void init() {
        int y = height - 27;
        addRenderableWidget(Button.builder(Component.literal("ROTATE: " + (rotateWithPlayer ? "PLAYER" : "NORTH")), b -> {
            rotateWithPlayer = !rotateWithPlayer;
            cacheReady = false;
            b.setMessage(Component.literal("ROTATE: " + (rotateWithPlayer ? "PLAYER" : "NORTH")));
        }).bounds(8, y, Math.max(82, width / 5 - 6), 20).build());
        addRenderableWidget(Button.builder(Component.literal("ZOOM -"), b -> {
            zoomIndex = Math.min(ZOOM.length - 1, zoomIndex + 1);
            cacheReady = false;
        }).bounds(width / 5 + 5, y, Math.max(55, width / 7 - 8), 20).build());
        addRenderableWidget(Button.builder(Component.literal("ZOOM +"), b -> {
            zoomIndex = Math.max(0, zoomIndex - 1);
            cacheReady = false;
        }).bounds(width / 5 + width / 7, y, Math.max(55, width / 7 - 8), 20).build());
        addRenderableWidget(Button.builder(Component.literal("VIEW: " + (relief3D ? "3D RELIEF" : "2D")), b -> {
            relief3D = !relief3D;
            b.setMessage(Component.literal("VIEW: " + (relief3D ? "3D RELIEF" : "2D")));
        }).bounds(width / 2 + 2, y, Math.max(86, width / 6), 20).build());
        addRenderableWidget(Button.builder(Component.literal("HEIGHT: " + (showHeight ? "ON" : "OFF")), b -> {
            showHeight = !showHeight;
            b.setMessage(Component.literal("HEIGHT: " + (showHeight ? "ON" : "OFF")));
        }).bounds(width / 2 + Math.max(90, width / 6) + 4, y, Math.max(76, width / 7), 20).build());
        addRenderableWidget(Button.builder(Component.literal("TERRAIN: " + (Config.terrainLayer ? "ON" : "OFF")), b -> {
            Config.terrainLayer = !Config.terrainLayer;
            Config.save();
            cacheReady = false;
            b.setMessage(Component.literal("TERRAIN: " + (Config.terrainLayer ? "ON" : "OFF")));
        }).bounds(width - Math.max(100, width / 4) - 8, y, Math.max(100, width / 4), 20).build());
    }

    private void rebuildTerrainCache(Minecraft mc, int anchorX, int anchorZ, int yawStep) {
        int scale = ZOOM[zoomIndex];
        int centerY = mc.player.blockPosition().getY();
        for (int gz = 0; gz < CELLS; gz++) {
            for (int gx = 0; gx < CELLS; gx++) {
                int dx = (gx - CELLS / 2) * scale;
                int dz = (gz - CELLS / 2) * scale;
                int rx = dx, rz = dz;
                switch (Math.floorMod(yawStep, 4)) {
                    case 1 -> { rx = -dz; rz = dx; }
                    case 2 -> { rx = -dx; rz = -dz; }
                    case 3 -> { rx = dz; rz = -dx; }
                }
                int wx = anchorX + rx, wz = anchorZ + rz;
                int color = 0xFF111720;
                terrainHeights[gz][gx] = Integer.MIN_VALUE;
                BlockPos probe = new BlockPos(wx, centerY, wz);
                if (mc.level.hasChunkAt(probe)) {
                    int topY = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
                    terrainHeights[gz][gx] = topY;
                    BlockPos surface = new BlockPos(wx, Math.max(mc.level.getMinY(), topY - 1), wz);
                    BlockState state = mc.level.getBlockState(surface);
                    int rgb = state.getMapColor(mc.level, surface).col;
                    if (!Config.terrainLayer) {
                        color = 0xFF252D38;
                    } else {
                        int shade = Math.max(70, Math.min(255, 160 + (topY - centerY) * 3));
                        color = (0xFF << 24) | ((rgb & 0x00FFFFFF) * shade / 255 & 0x00FFFFFF);
                        // Preserve terrain hues while adding clear height contrast.
                        int r = ((rgb >> 16) & 255) * shade / 190;
                        int g = ((rgb >> 8) & 255) * shade / 190;
                        int b = (rgb & 255) * shade / 190;
                        color = 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
                    }
                }
                terrainColors[gz][gx] = color;
            }
        }
        cachedX = anchorX; cachedZ = anchorZ; cachedYaw = yawStep;
        cachedZoom = zoomIndex;
        cachedTerrainLayer = Config.terrainLayer;
        cacheReady = true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        g.fill(0, 0, width, height, 0xF00A0E14);
        super.render(g, mouseX, mouseY, pt);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        int scale = ZOOM[zoomIndex];
        int px = mc.player.blockPosition().getX(), pz = mc.player.blockPosition().getZ();
        int anchorX = Math.floorDiv(px, scale) * scale;
        int anchorZ = Math.floorDiv(pz, scale) * scale;
        int yawStep = rotateWithPlayer ? Math.round(mc.player.getYRot() / 90f) : 0;
        if (!cacheReady || anchorX != cachedX || anchorZ != cachedZ || yawStep != cachedYaw
                || cachedZoom != zoomIndex || cachedTerrainLayer != Config.terrainLayer) {
            rebuildTerrainCache(mc, anchorX, anchorZ, yawStep);
        }

        int margin = 12;
        int mapSize = Math.max(82, Math.min(width - margin * 2, height - 76));
        int cellSize = Math.max(1, mapSize / CELLS);
        mapSize = cellSize * CELLS;
        int mapX = (width - mapSize) / 2;
        int mapY = Math.max(32, (height - mapSize - 32) / 2);
        g.fill(mapX - 3, mapY - 3, mapX + mapSize + 3, mapY + mapSize + 3, 0xFF05080D);
        HudRenderer.text(g, font, "T-800 // GIGAMAP", mapX, Math.max(4, mapY - 20), 0.8f, Config.main());
        HudRenderer.text(g, font, "SURFACE SCAN • LOADED CHUNKS ONLY", mapX, Math.max(14, mapY - 10), 0.45f, Config.dim());

        if (!relief3D) {
            for (int gz = 0; gz < CELLS; gz++) {
                for (int gx = 0; gx < CELLS; gx++) {
                    int sx = mapX + gx * cellSize;
                    int sy = mapY + gz * cellSize;
                    g.fill(sx, sy, sx + cellSize, sy + cellSize, terrainColors[gz][gx]);
                    if (showHeight && terrainHeights[gz][gx] != Integer.MIN_VALUE && cellSize >= 5
                            && Math.floorMod(gx + gz, 5) == 0) {
                        HudRenderer.text(g, font, Integer.toString(terrainHeights[gz][gx]), sx, sy, 0.38f, 0xFFEAF2FF);
                    }
                }
            }
        } else {
            // Height-aware oblique relief: sampled surface columns are offset by actual Y level.
            int minH = Integer.MAX_VALUE, maxH = Integer.MIN_VALUE;
            for (int[] row : terrainHeights) for (int yv : row) if (yv != Integer.MIN_VALUE) {
                minH = Math.min(minH, yv); maxH = Math.max(maxH, yv);
            }
            if (minH == Integer.MAX_VALUE) { minH = 0; maxH = 0; }
            int relief = Math.max(1, Math.min(4, mapSize / (CELLS * 12)));
            for (int gz = 0; gz < CELLS; gz++) {
                for (int gx = 0; gx < CELLS; gx++) {
                    int sx = mapX + gx * cellSize;
                    int sy = mapY + gz * cellSize;
                    int hy = terrainHeights[gz][gx];
                    int rise = hy == Integer.MIN_VALUE ? 0 : (hy - minH) * relief;
                    int drawY = sy - rise / 2;
                    int col = terrainColors[gz][gx];
                    if (hy != Integer.MIN_VALUE && maxH > minH) {
                        int norm = (hy - minH) * 255 / (maxH - minH);
                        int rr = (col >> 16) & 255, gg = (col >> 8) & 255, bb = col & 255;
                        rr = Math.min(255, (rr * 3 + norm) / 4);
                        gg = Math.min(255, (gg * 3 + norm / 2) / 4);
                        bb = Math.min(255, (bb * 3 + (255 - norm) / 3) / 4);
                        col = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
                    }
                    if (rise > 0 && cellSize > 1)
                        g.fill(sx, drawY + cellSize, sx + cellSize, sy + cellSize, 0xFF101820);
                    g.fill(sx, drawY, sx + cellSize, drawY + cellSize, col);
                    if (showHeight && hy != Integer.MIN_VALUE && cellSize >= 5
                            && Math.floorMod(gx + gz, 7) == 0) {
                        HudRenderer.text(g, font, Integer.toString(hy), sx, drawY, 0.38f, 0xFFFFFFFF);
                    }
                }
            }
            HudRenderer.text(g, font, "RELIEF " + minH + " - " + maxH + " Y", mapX + 3, mapY + 3, 0.45f, Config.text());
        }

        // Plot currently loaded living entities relative to the player. No chunk is forced to load.
        double halfBlocks = CELLS * scale / 2.0;
        var entities = mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(halfBlocks + scale),
                e -> e != mc.player && e.isAlive());
        for (LivingEntity e : entities) {
            double dx = e.getX() - px, dz = e.getZ() - pz;
            double rx = dx, rz = dz;
            switch (Math.floorMod(yawStep, 4)) {
                case 1 -> { rx = -dz; rz = dx; }
                case 2 -> { rx = -dx; rz = -dz; }
                case 3 -> { rx = dz; rz = -dx; }
            }
            int gx = CELLS / 2 + (int) Math.round(rx / scale);
            int gz = CELLS / 2 + (int) Math.round(rz / scale);
            if (gx < 0 || gx >= CELLS || gz < 0 || gz >= CELLS) continue;
            int color = e instanceof Player ? 0xFF4FC3FF : e instanceof Enemy ? 0xFFFF4242 : 0xFFFFD166;
            int dot = Math.max(2, cellSize);
            g.fill(mapX + gx * cellSize, mapY + gz * cellSize,
                    mapX + gx * cellSize + dot, mapY + gz * cellSize + dot, color);
        }

        // Combat-linked priority markers use the same sorted threat list as the HUD.
        if (Config.gigaMapCombatLink) {
            int rank = 0;
            for (CombatAIService.Threat threat : CombatAIService.INSTANCE.threats()) {
                LivingEntity e = threat.entity;
                if (e == null || !e.isAlive()) continue;
                double dx = e.getX() - px, dz = e.getZ() - pz;
                double rx = dx, rz = dz;
                switch (Math.floorMod(yawStep, 4)) {
                    case 1 -> { rx = -dz; rz = dx; }
                    case 2 -> { rx = -dx; rz = -dz; }
                    case 3 -> { rx = dz; rz = -dx; }
                }
                int gx = CELLS / 2 + (int) Math.round(rx / scale);
                int gz = CELLS / 2 + (int) Math.round(rz / scale);
                if (gx < 0 || gx >= CELLS || gz < 0 || gz >= CELLS) continue;
                int mx = mapX + gx * cellSize, my = mapY + gz * cellSize;
                int c = threat.score >= 75 ? 0xFFFF3434 : threat.score >= 45 ? 0xFFFFB52E : 0xFF6BFF88;
                g.fill(mx - 1, my - 1, mx + Math.max(3, cellSize) + 1, my + Math.max(3, cellSize) + 1, c);
                HudRenderer.text(g, font, Integer.toString(++rank), mx + 2, my + 1, 0.45f, 0xFFFFFFFF);
                if (rank >= 5) break;
            }
        }

        int cx = mapX + (CELLS / 2) * cellSize;
        int cy = mapY + (CELLS / 2) * cellSize;
        g.fill(cx - 2, cy - 2, cx + cellSize + 2, cy + cellSize + 2, Config.main());
        HudRenderer.text(g, font, "N", mapX + mapSize - 7, mapY + 2, 0.5f, Config.text());
        HudRenderer.text(g, font, String.format("X %d  Y %d  Z %d   •   1 CELL = %d BLOCK(S)", px,
                mc.player.blockPosition().getY(), pz, scale), 10, height - 39, 0.48f, Config.text());
        HudRenderer.text(g, font, (Config.gigaMapCombatLink ? "PRIORITY NUMBERS = COMBAT AI RANK  •  " : "") + "RED HOSTILE  •  BLUE PLAYER  •  YELLOW OTHER  •  CAVES/STRUCTURE BOOKMARKS NOT INCLUDED",
                10, height - 48, 0.40f, HudRenderer.AMBER);
    }
}
