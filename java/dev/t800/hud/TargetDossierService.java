package dev.t800.hud;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Client-local player dossiers and timestamped combat telemetry replays. Profiles are not capped. */
public final class TargetDossierService {
    public static final TargetDossierService INSTANCE = new TargetDossierService();
    private static final int DISPLAY_EVENT_LIMIT = 240;
    private static final long SAMPLE_INTERVAL_MS = 100;
    private final Map<String, Dossier> dossiers = new LinkedHashMap<>();
    private final List<ReplayEvent> events = new ArrayList<>();
    private boolean loaded;
    private String activeKey = "";
    private float lastTargetHealth = -1, lastSelfHealth = -1;
    private long lastSampleAt, lastSaveAt, replayStartedAt, lastReplayFrameAt;
    private Path activeReplayFile;

    public static final class Dossier {
        public String uuid = "", name = "UNKNOWN";
        public long firstSeen, lastSeen;
        public int sightings, observedLocks, observedHealthDrops, observedDamageTakenEvents;
        public double observedTargetHealthLoss, observedSelfHealthLoss;
        public String server = "Singleplayer", dimension = "Unknown";
        public int x, y, z;
        public String lastHeldItem = "Unknown", lastPattern = "NO DATA", latestReplay = "";
        public int replayCount;
    }

    public record ReplayEvent(long time, String uuid, String name, String kind, String detail) {
        public String display() { return String.format(Locale.ROOT, "%tT  %s: %s", time, kind, detail); }
    }
    public record ReplayFrame(long timestamp, long elapsedMs, double selfX, double selfY, double selfZ,
        double targetX, double targetY, double targetZ, float selfYaw, float selfPitch, float targetYaw,
        float targetPitch, float selfHealth, float targetHealth, double distance, String targetItem, String event) { }

    private Path root() { return FabricLoader.getInstance().getConfigDir().resolve("t800hud"); }
    private Path profileFile() { return root().resolve("target-dossiers.properties"); }
    private Path replayDir() { return root().resolve("replays"); }

    public synchronized void tick(Minecraft mc, LivingEntity target) {
        load();
        if (mc.player == null || mc.level == null || !(target instanceof Player player) || !player.isAlive()) {
            stopReplay("TARGET LOST");
            return;
        }
        long now = System.currentTimeMillis();
        String uuid = player.getUUID().toString();
        boolean returningTarget = dossiers.containsKey(uuid);
        Dossier d = dossiers.computeIfAbsent(uuid, key -> {
            Dossier fresh = new Dossier(); fresh.uuid = key; fresh.name = player.getName().getString(); fresh.firstSeen = now; return fresh;
        });
        d.name = player.getName().getString(); d.lastSeen = now;
        d.server = mc.getCurrentServer() == null ? "Singleplayer" : mc.getCurrentServer().ip;
        d.dimension = mc.level.dimension().identifier().toString();
        d.x = player.blockPosition().getX(); d.y = player.blockPosition().getY(); d.z = player.blockPosition().getZ();
        d.lastHeldItem = player.getMainHandItem().isEmpty() ? "Empty" : player.getMainHandItem().getHoverName().getString();
        String event = "";
        if (!uuid.equals(activeKey)) {
            stopReplay("TARGET SWITCH");
            activeKey = uuid; d.sightings++; d.observedLocks++;
            lastTargetHealth = player.getHealth(); lastSelfHealth = mc.player.getHealth(); lastSampleAt = now;
            replayStartedAt = now; lastReplayFrameAt = 0;
            try {
                Files.createDirectories(replayDir());
                String safeName = player.getName().getString().replaceAll("[^A-Za-z0-9._-]", "_");
                activeReplayFile = replayDir().resolve(safeName + "-" + uuid + "-" + now + ".csv");
                Files.writeString(activeReplayFile,
                    "timestamp,elapsedMs,selfX,selfY,selfZ,targetX,targetY,targetZ,selfYaw,selfPitch,targetYaw,targetPitch,selfHealth,targetHealth,distance,targetItem,event\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                d.latestReplay = activeReplayFile.getFileName().toString(); d.replayCount++;
                event = "TARGET LOCK";
                addEvent(uuid, "TARGET LOCK", "Acquired " + d.name + " at " + d.x + " " + d.y + " " + d.z);
                if (returningTarget) {
                    mc.player.displayClientMessage(Component.literal("T-800 // TARGET RECOGNIZED: " + d.name
                            + " | sightings " + d.sightings + " | recorded fights " + d.replayCount), true);
                }
            } catch (IOException ex) {
                activeReplayFile = null; addEvent(uuid, "REPLAY ERROR", "Could not create local replay: " + ex.getMessage());
            }
        } else if (now - lastSampleAt >= 250) {
            float hp = player.getHealth(), selfHp = mc.player.getHealth();
            if (lastTargetHealth >= 0 && hp < lastTargetHealth - 0.05f) {
                double loss = lastTargetHealth - hp; d.observedHealthDrops++; d.observedTargetHealthLoss += loss;
                event = "TARGET HP DROP";
                addEvent(uuid, "TARGET HP", String.format(Locale.ROOT, "-%.1f observed; source may be another player", loss));
            }
            if (lastSelfHealth >= 0 && selfHp < lastSelfHealth - 0.05f) {
                double loss = lastSelfHealth - selfHp; d.observedDamageTakenEvents++; d.observedSelfHealthLoss += loss;
                event = event.isEmpty() ? "SELF HP DROP" : event + " + SELF HP DROP";
                addEvent(uuid, "SELF HP", String.format(Locale.ROOT, "-%.1f while target tracked", loss));
            }
            lastTargetHealth = hp; lastSelfHealth = selfHp; lastSampleAt = now;
        }
        if (activeReplayFile != null && now - lastReplayFrameAt >= SAMPLE_INTERVAL_MS) {
            appendFrame(mc, player, now, event); lastReplayFrameAt = now;
        }
        String pattern = CombatAIService.INSTANCE.patternSummary(player);
        d.lastPattern = pattern.length() > 180 ? pattern.substring(0, 180) : pattern;
        saveIfDue(false);
    }

    private void appendFrame(Minecraft mc, Player target, long now, String event) {
        if (activeReplayFile == null || mc.player == null) return;
        try {
            Vec3 s = mc.player.position(), t = target.position();
            String row = String.format(Locale.ROOT,
                "%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.3f,%.3f,%.3f,%.3f,%.2f,%.2f,%.4f,%s,%s%n",
                now, now-replayStartedAt, s.x,s.y,s.z,t.x,t.y,t.z, mc.player.getYRot(),mc.player.getXRot(),
                target.getYRot(),target.getXRot(),mc.player.getHealth(),target.getHealth(),mc.player.distanceTo(target),
                csv(target.getMainHandItem().isEmpty() ? "Empty" : target.getMainHandItem().getHoverName().getString()),csv(event));
            Files.writeString(activeReplayFile,row,StandardCharsets.UTF_8,StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
    }
    private static String csv(String v) {
        if (v == null) return "";
        return "\"" + v.replace("\"", "\"\"").replace("\r"," ").replace("\n"," ") + "\"";
    }
    private void stopReplay(String reason) {
        if (!activeKey.isEmpty()) {
            if (reason != null && !reason.isBlank()) addEvent(activeKey,reason,"Replay capture ended");
            saveIfDue(true);
        }
        activeKey=""; activeReplayFile=null; lastTargetHealth=lastSelfHealth=-1; lastSampleAt=lastReplayFrameAt=0;
    }
    public synchronized void recordTermination(LivingEntity target) {
        if (target == null) return; load(); Dossier d=dossiers.get(target.getUUID().toString());
        if (d != null) { addEvent(d.uuid,"TARGET DOWN","Target died while tracked"); saveIfDue(true); }
    }
    public synchronized Dossier currentDossier() { load(); Dossier d=dossiers.get(activeKey); return d==null?null:copy(d); }
    /** Returns all profiles; there is no application-level profile cap. */
    public synchronized List<Dossier> recentDossiers(int limit) {
        load();
        var stream = dossiers.values().stream().sorted(Comparator.comparingLong((Dossier d)->d.lastSeen).reversed());
        return (limit <= 0 ? stream : stream.limit(limit)).map(TargetDossierService::copy).toList();
    }
    public synchronized List<ReplayEvent> recentEvents(String uuid,int limit) {
        load(); return events.stream().filter(e->uuid==null||uuid.isBlank()||e.uuid().equals(uuid))
            .sorted(Comparator.comparingLong(ReplayEvent::time).reversed()).limit(Math.max(1,limit)).toList();
    }
    public synchronized String activeUuid() { return activeKey; }
    public synchronized Path latestReplay(String uuid) {
        load(); Dossier d=dossiers.get(uuid);
        if (d==null||d.latestReplay.isBlank()) return null;
        Path p=replayDir().resolve(d.latestReplay).normalize();
        return p.startsWith(replayDir())&&Files.isRegularFile(p)?p:null;
    }
    public synchronized List<ReplayFrame> readReplay(Path path) {
        List<ReplayFrame> frames=new ArrayList<>();
        if (path==null||!path.normalize().startsWith(replayDir())||!Files.isRegularFile(path)) return frames;
        try (BufferedReader br=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            String line; boolean header=true;
            while ((line=br.readLine())!=null) {
                if (header) { header=false; continue; }
                List<String> f=parseCsv(line); if (f.size()<17) continue;
                try { frames.add(new ReplayFrame(Long.parseLong(f.get(0)),Long.parseLong(f.get(1)),
                    Double.parseDouble(f.get(2)),Double.parseDouble(f.get(3)),Double.parseDouble(f.get(4)),
                    Double.parseDouble(f.get(5)),Double.parseDouble(f.get(6)),Double.parseDouble(f.get(7)),
                    Float.parseFloat(f.get(8)),Float.parseFloat(f.get(9)),Float.parseFloat(f.get(10)),Float.parseFloat(f.get(11)),
                    Float.parseFloat(f.get(12)),Float.parseFloat(f.get(13)),Double.parseDouble(f.get(14)),f.get(15),f.get(16)); }
                catch (RuntimeException ignored) { }
            }
        } catch (IOException ignored) { }
        return frames;
    }
    private static List<String> parseCsv(String line) {
        List<String> out=new ArrayList<>(); StringBuilder field=new StringBuilder(); boolean quoted=false;
        for(int i=0;i<line.length();i++) { char c=line.charAt(i);
            if(c=='"') { if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"'){field.append('"');i++;}else quoted=!quoted; }
            else if(c==','&&!quoted){out.add(field.toString());field.setLength(0);}else field.append(c);
        }
        out.add(field.toString()); return out;
    }
    private static Dossier copy(Dossier s) {
        Dossier d=new Dossier(); d.uuid=s.uuid;d.name=s.name;d.firstSeen=s.firstSeen;d.lastSeen=s.lastSeen;d.sightings=s.sightings;
        d.observedLocks=s.observedLocks;d.observedHealthDrops=s.observedHealthDrops;d.observedDamageTakenEvents=s.observedDamageTakenEvents;
        d.observedTargetHealthLoss=s.observedTargetHealthLoss;d.observedSelfHealthLoss=s.observedSelfHealthLoss;d.server=s.server;d.dimension=s.dimension;
        d.x=s.x;d.y=s.y;d.z=s.z;d.lastHeldItem=s.lastHeldItem;d.lastPattern=s.lastPattern;d.latestReplay=s.latestReplay;d.replayCount=s.replayCount;return d;
    }
    private void addEvent(String uuid,String kind,String detail) {
        Dossier d=dossiers.get(uuid); events.add(new ReplayEvent(System.currentTimeMillis(),uuid,d==null?"UNKNOWN":d.name,kind,detail));
        while(events.size()>DISPLAY_EVENT_LIMIT) events.remove(0);
    }
    private void saveIfDue(boolean force) {
        long now=System.currentTimeMillis(); if(!force&&now-lastSaveAt<3000)return; lastSaveAt=now;
        try {
            Files.createDirectories(root()); Properties p=new Properties(); p.setProperty("profiles.count",Integer.toString(dossiers.size()));
            for(Dossier d:dossiers.values()){String k="profile."+d.uuid+".";
                p.setProperty(k+"name",d.name);p.setProperty(k+"first",Long.toString(d.firstSeen));p.setProperty(k+"last",Long.toString(d.lastSeen));
                p.setProperty(k+"sightings",Integer.toString(d.sightings));p.setProperty(k+"locks",Integer.toString(d.observedLocks));
                p.setProperty(k+"drops",Integer.toString(d.observedHealthDrops));p.setProperty(k+"takenEvents",Integer.toString(d.observedDamageTakenEvents));
                p.setProperty(k+"targetLoss",Double.toString(d.observedTargetHealthLoss));p.setProperty(k+"selfLoss",Double.toString(d.observedSelfHealthLoss));
                p.setProperty(k+"server",d.server);p.setProperty(k+"dimension",d.dimension);p.setProperty(k+"pos",d.x+","+d.y+","+d.z);
                p.setProperty(k+"item",d.lastHeldItem);p.setProperty(k+"pattern",d.lastPattern);p.setProperty(k+"latestReplay",d.latestReplay);
                p.setProperty(k+"replayCount",Integer.toString(d.replayCount));
            }
            Path tmp=profileFile().resolveSibling("target-dossiers.properties.tmp");
            try(var out=Files.newOutputStream(tmp)){p.store(out,"T-800 local target dossiers; no profile limit");}
            try{Files.move(tmp,profileFile(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}
            catch(IOException ex){Files.move(tmp,profileFile(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
        }catch(Exception ignored){}
    }
    private void load() {
        if(loaded)return; loaded=true; Path path=profileFile(); if(!Files.exists(path))return;
        try(var in=Files.newInputStream(path)){Properties p=new Properties();p.load(in);
            for(String key:p.stringPropertyNames()){if(!key.startsWith("profile.")||!key.endsWith(".name"))continue;
                String uuid=key.substring(8,key.length()-5); Dossier d=new Dossier();d.uuid=uuid;d.name=p.getProperty("profile."+uuid+".name","UNKNOWN");
                d.firstSeen=lng(p,uuid,"first");d.lastSeen=lng(p,uuid,"last");d.sightings=num(p,uuid,"sightings");d.observedLocks=num(p,uuid,"locks");
                d.observedHealthDrops=num(p,uuid,"drops");d.observedDamageTakenEvents=num(p,uuid,"takenEvents");
                d.observedTargetHealthLoss=dec(p,uuid,"targetLoss");d.observedSelfHealthLoss=dec(p,uuid,"selfLoss");
                d.server=p.getProperty("profile."+uuid+".server","Unknown");d.dimension=p.getProperty("profile."+uuid+".dimension","Unknown");
                String[] pos=p.getProperty("profile."+uuid+".pos","0,0,0").split(",");
                if(pos.length==3){d.x=parseInt(pos[0]);d.y=parseInt(pos[1]);d.z=parseInt(pos[2]);}
                d.lastHeldItem=p.getProperty("profile."+uuid+".item","Unknown");d.lastPattern=p.getProperty("profile."+uuid+".pattern","NO DATA");
                d.latestReplay=p.getProperty("profile."+uuid+".latestReplay","");d.replayCount=num(p,uuid,"replayCount");dossiers.put(uuid,d);
            }
        }catch(Exception ignored){}
    }
    private static long lng(Properties p,String u,String f){try{return Long.parseLong(p.getProperty("profile."+u+"."+f,"0"));}catch(Exception e){return 0;}}
    private static int num(Properties p,String u,String f){try{return Integer.parseInt(p.getProperty("profile."+u+"."+f,"0"));}catch(Exception e){return 0;}}
    private static double dec(Properties p,String u,String f){try{return Double.parseDouble(p.getProperty("profile."+u+"."+f,"0"));}catch(Exception e){return 0;}}
    private static int parseInt(String s){try{return Integer.parseInt(s);}catch(Exception e){return 0;}}
}
