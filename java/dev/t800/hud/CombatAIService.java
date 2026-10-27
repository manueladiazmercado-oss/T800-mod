package dev.t800.hud;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Mth;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Client-side advisory only: observes loaded entities; never automates combat. */
public final class CombatAIService {
    public static final CombatAIService INSTANCE = new CombatAIService();
    private static final int LIMIT = 8;
    private final Map<Integer, Sample> samples = new HashMap<>();
    private final Map<String, Memory> memory = new HashMap<>();
    private final List<Threat> threats = new ArrayList<>();
    private long lastTick, lastVoice;
    private float lastPlayerHealth = -1;
    private boolean loaded;
    private String advice = "Acquire a target to begin analysis.";
    private String diagnosis = "SELF-CHECK: WAITING";
    private int encounters, lastTargetId = Integer.MIN_VALUE;
    private static final class Sample {
        float hp; double x,z; long seen; int ticks; String pattern="OBSERVING";
        Sample(LivingEntity e,long n){hp=e.getHealth();x=e.getX();z=e.getZ();seen=n;}
    }
    private static final class Memory { int seen,hits; String pattern="UNKNOWN"; }
    public static final class Threat {
        public final LivingEntity entity; public final double score,distance; public final String pattern,advice;
        Threat(LivingEntity e,double s,double d,String p,String a){entity=e;score=s;distance=d;pattern=p;advice=a;}
    }
    public void tick(Minecraft mc) {
        if(mc.player==null||mc.level==null||mc.isPaused()) return;
        long now=System.currentTimeMillis(); if(now-lastTick<100)return; lastTick=now; loadMemory();
        var p=mc.player; boolean hurt=lastPlayerHealth>=0&&p.getHealth()<lastPlayerHealth-.01f; lastPlayerHealth=p.getHealth();
        threats.clear();
        var list=mc.level.getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(Math.min(48,Math.max(24,Config.range()))),e->e!=p&&e.isAlive()&&!e.isSpectator());
        LivingEntity likely=null; double nearest=Double.MAX_VALUE;
        for(LivingEntity e:list){
            double d=e.distanceTo(p); Sample s=samples.computeIfAbsent(e.getId(),id->new Sample(e,now));
            double speed=Math.hypot(e.getX()-s.x,e.getZ()-s.z)*10;
            if(e.getHealth()<s.hp-.01f)s.pattern="UNDER ATTACK";
            else if(d<4.5&&speed>1.5)s.pattern="RUSHING";
            else if(e instanceof Enemy&&d<14)s.pattern="ENGAGING";
            else if(speed>3.5)s.pattern="FAST MOVING";
            else if(e instanceof Player)s.pattern="PLAYER / UNKNOWN"; else s.pattern="PATROLLING";
            s.hp=e.getHealth();s.x=e.getX();s.z=e.getZ();s.seen=now;s.ticks++;
            if(hurt&&d<nearest){nearest=d;likely=e;}
            threats.add(new Threat(e,score(e,d,speed,s.pattern),d,s.pattern,tactic(e,d,speed,s.pattern)));
        }
        threats.sort(Comparator.comparingDouble((Threat t)->t.score).reversed());
        if(threats.size()>LIMIT)threats.subList(LIMIT,threats.size()).clear();
        if(likely!=null){Memory m=memory.computeIfAbsent(key(likely),k->new Memory());m.hits++;m.pattern=samples.get(likely.getId()).pattern;saveMemory();}
        LivingEntity target=T800HudClient.TRACKER.target;
        for(Threat t:threats)if(t.entity==target){advice=t.advice+" ["+t.pattern+"]";break;}
        if(target==null || !target.isAlive()) lastTargetId = Integer.MIN_VALUE;
        else if(target.getId()!=lastTargetId){
            Memory m=memory.computeIfAbsent(key(target),k->new Memory());
            m.seen++; Sample sample=samples.get(target.getId()); if(sample!=null)m.pattern=sample.pattern;
            encounters++; lastTargetId=target.getId(); saveMemory();
        }
        samples.entrySet().removeIf(e->now-e.getValue().seen>30000);
        List<String> issues=new ArrayList<>(); if(!Config.hud)issues.add("HUD DISABLED");if(!Config.combatAnalytics)issues.add("ANALYTICS DISABLED");
        if(Float.isNaN(p.getHealth())||p.getMaxHealth()<=0)issues.add("HEALTH SENSOR INVALID");
        diagnosis=issues.isEmpty()?"SELF-CHECK OK • "+threats.size()+" THREATS • "+memory.size()+" TYPES":"CHECK: "+String.join(" / ",issues);
        voice(mc,now);
    }
    private double score(LivingEntity e,double d,double speed,String pattern){
        double hp=Math.sqrt(Math.max(1,e.getMaxHealth()))*3, attack=0;
        try{attack=Math.max(0,e.getAttributeValue(Attributes.ATTACK_DAMAGE));}catch(RuntimeException ignored){}
        double boss=0;String id=key(e);
        if(id.contains("warden")||id.contains("ender_dragon")||id.contains("wither"))boss=25;
        else if(id.contains("ravager")||id.contains("elder_guardian")||id.contains("evoker"))boss=12;
        Memory m=memory.get(id);double learned=m==null?0:Math.min(8,m.hits*1.5);
        return Mth.clamp(hp+attack*1.8+e.getArmorValue()*.6+weapon(e.getMainHandItem())*5+boss+(e instanceof Player?8:e instanceof Enemy?4:0)+(d<4?12:d<10?7:d<20?3:0)+(speed>4?8:speed>1.5?4:0)+(pattern.equals("RUSHING")?7:0)+learned,0,100);
    }
    private String tactic(LivingEntity e,double d,double speed,String p){
        if(d<4&&(e instanceof Enemy||e instanceof Player))return "BLOCK / CREATE SPACE";
        if(p.equals("RUSHING"))return "STRAFE; WATCH ATTACK TIMING";
        if(e instanceof Player&&d>10)return "WATCH PROJECTILES / USE COVER";
        if(e.getHealth()<e.getMaxHealth()*.25f)return "TARGET WEAK — CHECK YOUR HEALTH";
        if(speed>3.5)return "TRACK AHEAD OF MOVEMENT";
        return "KEEP CROSSHAIR; CHECK SURROUNDINGS";
    }
    private static double weapon(ItemStack i){
        if(i.isEmpty())return 0;
        if(i.is(Items.NETHERITE_SWORD)||i.is(Items.NETHERITE_AXE)||i.is(Items.MACE))return 1;
        if(i.is(Items.DIAMOND_SWORD)||i.is(Items.DIAMOND_AXE)||i.is(Items.TRIDENT))return .85;
        if(i.is(Items.IRON_SWORD)||i.is(Items.IRON_AXE)||i.is(Items.BOW)||i.is(Items.CROSSBOW))return .65;
        return .3;
    }
    private static String key(LivingEntity e){return e.getType().toString();}
    private Path path(){return FabricLoader.getInstance().getConfigDir().resolve("t800hud-combat-memory.properties");}
    private void loadMemory(){
        if(loaded)return;loaded=true;if(!Files.exists(path()))return;
        try(InputStream in=Files.newInputStream(path())){Properties p=new Properties();p.load(in);for(String k:p.stringPropertyNames())if(k.startsWith("mob.")){String[] v=p.getProperty(k,"0,0,UNKNOWN").split(",",3);Memory m=new Memory();m.seen=Integer.parseInt(v[0]);m.hits=Integer.parseInt(v[1]);m.pattern=v.length>2?v[2]:"UNKNOWN";memory.put(k.substring(4),m);}}
        catch(Exception ex){diagnosis="MEMORY LOAD WARNING: "+ex.getClass().getSimpleName();}
    }
    private void saveMemory(){try{Properties p=new Properties();memory.forEach((k,m)->p.setProperty("mob."+k,m.seen+","+m.hits+","+m.pattern.replace(",","")));Files.createDirectories(path().getParent());try(OutputStream out=Files.newOutputStream(path())){p.store(out,"T-800 client combat memory");}}catch(Exception ex){diagnosis="MEMORY SAVE WARNING: "+ex.getClass().getSimpleName();}}
    private void voice(Minecraft mc,long now){
        if(!Config.voiceAssistant||now-lastVoice<12000||threats.isEmpty())return;
        Threat t=threats.get(0);if(t.score<65&&mc.player.getHealth()>mc.player.getMaxHealth()*.35f)return;lastVoice=now;
        String msg=(t.score>=80?"High threat. ":"Threat detected. ")+t.advice;
        Thread th=new Thread(()->{try{String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);ProcessBuilder b;
            if(os.contains("mac"))b=new ProcessBuilder("say",msg);
            else if(os.contains("win"))b=new ProcessBuilder("powershell","-NoProfile","-Command","Add-Type -AssemblyName System.Speech; (New-Object System.Speech.Synthesis.SpeechSynthesizer).Speak('"+msg.replace("'","")+"')");
            else b=new ProcessBuilder("espeak",msg);b.redirectErrorStream(true);b.start();}catch(Exception ignored){}},"t800-voice");th.setDaemon(true);th.start();
    }
    public List<Threat> threats(){return List.copyOf(threats);}
    public String advice(){return advice;}
    public String diagnosis(){return diagnosis;}
    public int rememberedTypes(){return memory.size();}
    public int encounters(){return encounters;}
    public String memory(LivingEntity e){Memory m=e==null?null:memory.get(key(e));return m==null?"FIRST OBSERVATION":"SEEN "+m.seen+" / DAMAGE EVENTS "+m.hits;}
    public int armorDurability(LivingEntity e){int total=0,left=0;for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}){ItemStack a=e.getItemBySlot(slot);if(!a.isEmpty()&&a.getMaxDamage()>0){total+=a.getMaxDamage();left+=Math.max(0,a.getMaxDamage()-a.getDamageValue());}}return total==0?100:(int)Math.round(left*100.0/total);}
}
