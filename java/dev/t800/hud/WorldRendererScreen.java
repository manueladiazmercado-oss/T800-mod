package dev.t800.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Independent client-side voxel/height renderer. It draws an isometric terrain
 * model from block states in chunks already loaded by Minecraft; it never forces
 * chunks to load and never replaces the vanilla world renderer.
 */
public final class WorldRendererScreen extends Screen {
    private static final int GRID = 19;
    private static final int RADIUS = GRID / 2;
    private static final int[] ZOOM = {5, 7, 9, 11};
    private int zoom = 1;
    private int rotation;
    private boolean showBlocks = true;
    private int[][] heights = new int[GRID][GRID];
    private int[][] colors = new int[GRID][GRID];
    private int centerX, centerZ, centerY;
    private boolean cacheReady;

    public WorldRendererScreen() {
        super(Component.literal("T-800 // VOXEL WORLD RENDERER"));
    }

    @Override
    protected void init() {
        int y = height - 27;
        addRenderableWidget(Button.builder(Component.literal("ROTATE"), b -> {
            rotation = (rotation + 1) & 3;
            cacheReady = false;
        }).bounds(8, y, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ZOOM -"), b -> {
            zoom = Math.max(0, zoom - 1);
            cacheReady = false;
        }).bounds(90, y, 66, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ZOOM +"), b -> {
            zoom = Math.min(ZOOM.length - 1, zoom + 1);
            cacheReady = false;
        }).bounds(162, y, 66, 20).build());
        addRenderableWidget(Button.builder(Component.literal("BLOCKS: ON"), b -> {
            showBlocks = !showBlocks;
            b.setMessage(Component.literal("BLOCKS: " + (showBlocks ? "ON" : "OFF")));
        }).bounds(234, y, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("BACK TO MAP"), b -> {
            Minecraft.getInstance().setScreen(new GigaMapScreen());
        }).bounds(Math.max(332, width - 110), y, 102, 20).build());
    }

    private void rebuild(Minecraft mc) {
        centerX = mc.player.blockPosition().getX();
        centerZ = mc.player.blockPosition().getZ();
        centerY = mc.player.blockPosition().getY();
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                int dx = gx - RADIUS, dz = gz - RADIUS;
                int rx = dx, rz = dz;
                switch (rotation) {
                    case 1 -> { rx = -dz; rz = dx; }
                    case 2 -> { rx = -dx; rz = -dz; }
                    case 3 -> { rx = dz; rz = -dx; }
                }
                int wx = centerX + rx, wz = centerZ + rz;
                BlockPos probe = new BlockPos(wx, centerY, wz);
                heights[gz][gx] = Integer.MIN_VALUE;
                colors[gz][gx] = 0xFF111722;
                if (!mc.level.hasChunkAt(probe)) continue;
                int top = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1;
                top = Math.max(mc.level.getMinY(), top);
                BlockPos surface = new BlockPos(wx, top, wz);
                BlockState state = mc.level.getBlockState(surface);
                heights[gz][gx] = top;
                colors[gz][gx] = 0xFF000000 | (state.getMapColor(mc.level, surface).col & 0xFFFFFF);
            }
        }
        cacheReady = true;
    }

    private void diamond(GuiGraphics g, int cx, int cy, int w, int h, int color) {
        int halfW = Math.max(1, w / 2);
        int halfH = Math.max(1, h / 2);
        for (int row = 0; row < h; row++) {
            int inset = row < halfH ? (int)((halfH - row) * (double)halfW / halfH)
                    : (int)((row - halfH) * (double)halfW / Math.max(1, h - halfH));
            g.fill(cx - halfW + inset, cy - halfH + row, cx + halfW - inset + 1, cy - halfH + row + 1, color);
        }
    }

    private void side(GuiGraphics g, int x, int y, int w, int h, int color, boolean right) {
        if (h <= 0) return;
        int half = Math.max(1, w / 2);
        for (int row = 0; row < h; row++) {
            int inset = (int)((row / (double)h) * half);
            if (right) g.fill(x + inset, y + row, x + w, y + row + 1, color);
            else g.fill(x, y + row, x + w - inset, y + row + 1, color);
        }
    }

    private int shade(int color, double factor) {
        int r = Math.min(255, (int)(((color >> 16) & 255) * factor));
        int gr = Math.min(255, (int)(((color >> 8) & 255) * factor));
        int b = Math.min(255, (int)((color & 255) * factor));
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF070B12);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        if (!cacheReady || centerX != mc.player.blockPosition().getX()
                || centerZ != mc.player.blockPosition().getZ()) rebuild(mc);

        HudRenderer.text(g, font, "T-800 // VOXEL WORLD RENDERER", 12, 10, 0.9f, Config.main());
        HudRenderer.text(g, font, "CLIENT-SIDE BLOCK SCAN • LOADED CHUNKS ONLY • ISOMETRIC VIEW", 12, 23, 0.48f, Config.dim());
        int cell = ZOOM[zoom];
        int originX = width / 2;
        int originY = Math.max(48, height / 2 - GRID * cell / 2);
        int minH = Integer.MAX_VALUE, maxH = Integer.MIN_VALUE;
        for (int[] row : heights) for (int h : row) if (h != Integer.MIN_VALUE) {
            minH = Math.min(minH, h); maxH = Math.max(maxH, h);
        }
        if (minH == Integer.MAX_VALUE) { minH = centerY; maxH = centerY; }

        // Draw far-to-near so nearer terrain columns naturally overlap the rear ones.
        for (int depth = 0; depth < GRID * 2 - 1; depth++) {
            for (int gz = 0; gz < GRID; gz++) for (int gx = 0; gx < GRID; gx++) {
                if (gx + gz != depth) continue;
                int h = heights[gz][gx];
                if (h == Integer.MIN_VALUE) continue;
                int sx = originX + (gx - gz) * cell - cell;
                int sy = originY + (gx + gz) * (cell / 2) - (h - minH) * Math.max(1, cell / 2);
                int color = colors[gz][gx];
                int vertical = Math.min((maxH - minH + 1) * Math.max(1, cell / 2), Math.max(cell, (h - minH + 1) * Math.max(1, cell / 2)));
                if (showBlocks) {
                    side(g, sx, sy + cell / 2, cell * 2, Math.max(1, vertical), shade(color, 0.58), false);
                    side(g, sx + cell, sy + cell / 2, cell, Math.max(1, vertical), shade(color, 0.76), true);
                }
                diamond(g, sx + cell, sy, cell * 2, cell, color);
                if (gx == RADIUS && gz == RADIUS) {
                    diamond(g, sx + cell, sy - 1, cell * 2 + 2, cell + 2, 0xFFFFC857);
                    diamond(g, sx + cell, sy, cell * 2, cell, color);
                }
            }
        }
        HudRenderer.text(g, font, "CENTER X " + centerX + "  Y " + centerY + "  Z " + centerZ, 12, height - 48, 0.5f, Config.text());
        HudRenderer.text(g, font, "HEIGHT RANGE " + minH + "–" + maxH + " Y  •  ROTATION " + (rotation * 90) + "°  •  BLOCK COLOR = MAP COLOR", 12, height - 38, 0.43f, Config.dim());
        HudRenderer.text(g, font, "Prototype renderer: terrain is reconstructed from surface samples, not a full cave/texture renderer.", 12, height - 59, 0.42f, HudRenderer.AMBER);
        // Draw controls last so the terrain cannot cover the buttons.
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
