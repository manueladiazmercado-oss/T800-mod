package dev.t800.hud;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Picks the target nearest to the crosshair (line of sight required) and projects it to screen space. */
public class TargetTracker {
    public enum Mode { ALL, PLAYERS, HOSTILE }

    public static final double RANGE = 64.0;
    private static final double COS_ACQUIRE = Math.cos(Math.toRadians(22)); // cone to acquire
    private static final double COS_KEEP = Math.cos(Math.toRadians(38));    // wider cone to keep lock

    public Mode mode = Mode.ALL;
    public LivingEntity target;
    public long lockSince;

    // terminated targets
    public int kills;
    public long killTime;
    public String killName = "";

    // counts (last tick)
    public int total, players, hostile, other;

    // screen projection (per frame)
    public boolean onScreen;      // reticle can be drawn
    public boolean behindOrOff;   // target not in view
    public boolean targetRight;   // for edge indicator
    public double sx, sy;         // smoothed screen position (gui px)
    public double halfW, halfH;   // reticle half size (gui px)
    public double distance;
    private boolean smoothInit;
    private long lastFrame;

    public void cycleMode() {
        mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
        target = null;
    }

    public void clear() {
        target = null;
        total = players = hostile = other = 0;
        onScreen = false;
    }

    public static boolean isHostile(LivingEntity e) { return e instanceof Enemy; }

    private boolean matches(LivingEntity e) {
        return switch (mode) {
            case ALL -> true;
            case PLAYERS -> e instanceof Player;
            case HOSTILE -> isHostile(e);
        };
    }

    public static Vec3 center(LivingEntity e, float pt) {
        return new Vec3(
                Mth.lerp(pt, e.xo, e.getX()),
                Mth.lerp(pt, e.yo, e.getY()) + e.getBbHeight() * 0.5,
                Mth.lerp(pt, e.zo, e.getZ()));
    }

    /** Called every client tick. */
    public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel lvl = mc.level;
        if (p == null || lvl == null) { clear(); return; }

        if (target != null && target.isDeadOrDying()) {
            kills++;
            killTime = Util.getMillis();
            killName = target.getName().getString();
            target = null;
        }

        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();

        List<LivingEntity> list = lvl.getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(RANGE),
                e -> e != p && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand));

        total = 0; players = 0; hostile = 0; other = 0;
        LivingEntity best = null;
        double bestScore = -1;

        for (LivingEntity e : list) {
            if (e.distanceTo(p) > RANGE) continue;
            total++;
            if (e instanceof Player) players++;
            else if (isHostile(e)) hostile++;
            else other++;

            if (!matches(e)) continue;
            Vec3 d = center(e, 1f).subtract(eye);
            double len = d.length();
            if (len < 0.5) continue;
            double dot = d.dot(look) / len;
            if (dot < (e == target ? COS_KEEP : COS_ACQUIRE)) continue;
            if (!p.hasLineOfSight(e)) continue; // only visible targets
            double score = dot + (e == target ? 0.02 : 0);
            if (score > bestScore) { bestScore = score; best = e; }
        }

        if (best != target) {
            if (best != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.9f));
            }
            target = best;
            lockSince = Util.getMillis();
            smoothInit = false;
        }
    }

    /** Called every frame: world -> screen projection with smoothing. */
    public void project(Minecraft mc, float pt, int w, int h) {
        onScreen = false; behindOrOff = false;
        LocalPlayer p = mc.player;
        if (p == null || target == null || !target.isAlive()) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;

        Vec3 eye = p.getEyePosition(pt);
        double yr = Math.toRadians(p.getViewYRot(pt));
        double pr = Math.toRadians(p.getViewXRot(pt));
        Vec3 fwd = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
        Vec3 right = new Vec3(-Math.cos(yr), 0, -Math.sin(yr));
        Vec3 up = right.cross(fwd);

        Vec3 d = center(target, pt).subtract(eye);
        double depth = d.dot(fwd);
        double dr = d.dot(right);
        distance = d.length();
        targetRight = dr > 0;

        if (depth < 0.1) { behindOrOff = true; return; }

        double fov = mc.options.fov().get();
        double tanHalf = Math.tan(Math.toRadians(fov) / 2.0);
        double aspect = (double) w / h;

        double tx = w / 2.0 + (dr / depth) / (tanHalf * aspect) * (w / 2.0);
        double ty = h / 2.0 - (d.dot(up) / depth) / tanHalf * (h / 2.0);

        if (tx < -20 || tx > w + 20 || ty < -20 || ty > h + 20) { behindOrOff = true; return; }

        double pxPerBlock = (1.0 / depth) / tanHalf * (h / 2.0);
        double tw = Mth.clamp(target.getBbWidth() * 0.5 * pxPerBlock * 1.15, 8, 90);
        double th = Mth.clamp(target.getBbHeight() * 0.5 * pxPerBlock * 1.10, 10, 110);

        long now = Util.getMillis();
        double dt = Math.min(0.1, (now - lastFrame) / 1000.0);
        lastFrame = now;
        if (!smoothInit) { sx = tx; sy = ty; halfW = tw; halfH = th; smoothInit = true; }
        else {
            double k = 1 - Math.exp(-dt * 28.0); // snappy but visibly "tracking"
            sx += (tx - sx) * k; sy += (ty - sy) * k;
            halfW += (tw - halfW) * k; halfH += (th - halfH) * k;
        }
        onScreen = true;
    }
}
