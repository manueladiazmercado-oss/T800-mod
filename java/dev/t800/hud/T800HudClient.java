package dev.t800.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.lwjgl.glfw.GLFW;

public class T800HudClient implements ClientModInitializer {
    public static final String MOD_ID = "t800hud";
    public static final TargetTracker TRACKER = new TargetTracker();

    private static KeyMapping toggleKey, modeKey, visionKey, nightKey, armorKey, settingsKey,
            analyticsKey, gigaMapKey, terminationKey, performanceKey, infraredKey, voiceKey, confirmActionKey, cancelActionKey, pvpModeKey;
    private static boolean voiceWasDown;
    private static boolean nvApplied;
    private static boolean deathRecorded;
    /** F4 toggles the combat-analysis overlay without opening a separate screen. */
    public static boolean analyticsOverlayVisible;

    private static boolean hideVanillaBars() {
        Minecraft mc = Minecraft.getInstance();
        return Config.hud && Config.customBars && HudRenderer.survival(mc);
    }

    @Override
    public void onInitializeClient() {
        Config.load();
        TRACKER.applyConfig();

        // optional built-in resource packs (toggle in Options > Resource Packs)
        ModContainer self = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow();
        ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath(MOD_ID, "t800_gui"), self,
                Component.literal("T-800 HUD Textures"), PackActivationType.DEFAULT_ENABLED);
        ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath(MOD_ID, "t800_food_color"), self,
                Component.literal("T-800 Liquid Metal Food (colored)"), PackActivationType.DEFAULT_ENABLED);
        ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath(MOD_ID, "t800_food_chrome"), self,
                Component.literal("T-800 Liquid Metal Food (pure chrome)"), PackActivationType.NORMAL);
        ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath(MOD_ID, "t800_elytra"), self,
                Component.literal("T-800 Cybernetic Elytra"), PackActivationType.DEFAULT_ENABLED);

        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        toggleKey = reg("key.t800hud.toggle", GLFW.GLFW_KEY_H, category);
        modeKey = reg("key.t800hud.mode", GLFW.GLFW_KEY_J, category);
        visionKey = reg("key.t800hud.vision", GLFW.GLFW_KEY_K, category);
        nightKey = reg("key.t800hud.night", GLFW.GLFW_KEY_N, category);
        armorKey = reg("key.t800hud.armor", GLFW.GLFW_KEY_Y, category);
        settingsKey = reg("key.t800hud.settings", GLFW.GLFW_KEY_RIGHT_SHIFT, category);
        analyticsKey = reg("key.t800hud.analytics", GLFW.GLFW_KEY_F4, category);
        gigaMapKey = reg("key.t800hud.gigamap", GLFW.GLFW_KEY_F6, category);
        terminationKey = reg("key.t800hud.termination", GLFW.GLFW_KEY_F8, category);
        performanceKey = reg("key.t800hud.performance", GLFW.GLFW_KEY_F9, category);
        infraredKey = reg("key.t800hud.infrared", GLFW.GLFW_KEY_I, category);
        voiceKey = reg("key.t800hud.voice", GLFW.GLFW_KEY_F10, category);
        confirmActionKey = reg("key.t800hud.voice_confirm", GLFW.GLFW_KEY_F11, category);
        cancelActionKey = reg("key.t800hud.voice_cancel", GLFW.GLFW_KEY_F12, category);
        pvpModeKey = reg("key.t800hud.pvp_mode", GLFW.GLFW_KEY_F7, category);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean dirty = false;
            while (toggleKey.consumeClick()) { Config.hud = !Config.hud; dirty = true; }
            while (modeKey.consumeClick()) { TRACKER.cycleMode(); dirty = true; }
            while (visionKey.consumeClick()) { Config.vision = !Config.vision; dirty = true; }
            while (nightKey.consumeClick()) { Config.nightVision = !Config.nightVision; dirty = true; }
            while (armorKey.consumeClick()) { if (mc.screen == null && mc.player != null) mc.setScreen(new ArmorScreen()); }
            while (settingsKey.consumeClick()) { if (mc.screen == null) mc.setScreen(new SettingsScreen()); }
            while (analyticsKey.consumeClick()) { if (mc.screen == null && mc.player != null) analyticsOverlayVisible = !analyticsOverlayVisible; }
            while (gigaMapKey.consumeClick()) { if (mc.screen == null && mc.player != null) mc.setScreen(new GigaMapScreen()); }
            while (terminationKey.consumeClick()) { Config.terminationMode = !Config.terminationMode; dirty = true; }
            while (performanceKey.consumeClick()) { Config.performanceProfile = !Config.performanceProfile; dirty = true; }
            while (infraredKey.consumeClick()) { Config.infraredVision = !Config.infraredVision; dirty = true; }
            boolean voiceDown = voiceKey.isDown() && mc.screen == null;
            if (voiceDown && !voiceWasDown) VoiceAssistantService.INSTANCE.startRecording(mc);
            if (!voiceDown && voiceWasDown) VoiceAssistantService.INSTANCE.stopRecording(mc);
            voiceWasDown = voiceDown;
            while (confirmActionKey.consumeClick()) VoiceAssistantService.INSTANCE.confirm(mc);
            while (cancelActionKey.consumeClick()) VoiceAssistantService.INSTANCE.cancel(mc);
            while (pvpModeKey.consumeClick()) {
                String[] modes = {"AUTO", "SMP", "CRYSTAL", "SWORD", "AXE", "POT", "UHC", "PVP CLUB"};
                int next = 0;
                for (int i = 0; i < modes.length; i++) if (modes[i].equalsIgnoreCase(Config.pvpMode)) next = (i + 1) % modes.length;
                Config.pvpMode = modes[next]; Config.save();
                if (mc.player != null) mc.player.displayClientMessage(Component.literal("T-800 PvP profile: " + Config.pvpMode), true);
            }
            if (dirty) Config.save();

            tickNightVision(mc);
            if (mc.player != null && Config.deathRecovery) {
                if (mc.player.isDeadOrDying() && !deathRecorded) {
                    Config.deathX = mc.player.blockPosition().getX();
                    Config.deathY = mc.player.blockPosition().getY();
                    Config.deathZ = mc.player.blockPosition().getZ();
                    Config.hasDeathLocation = true;
                    Config.save();
                    deathRecorded = true;
                } else if (mc.player.isAlive()) {
                    deathRecorded = false;
                }
            }
            if (Config.hud) TRACKER.tick(mc); else TRACKER.clear();
            if (Config.adaptiveCombatAI || Config.multiTargetAnalysis || Config.combatMemory || Config.selfDiagnostics || Config.voiceAssistant) CombatAIService.INSTANCE.tick(mc);
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), new HudRenderer());

        // hide the vanilla bars while our custom bars are active
        HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR,
                orig -> (g, dt) -> { if (!hideVanillaBars()) orig.render(g, dt); });
        HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR,
                orig -> (g, dt) -> { if (!hideVanillaBars()) orig.render(g, dt); });
        HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR,
                orig -> (g, dt) -> { if (!hideVanillaBars()) orig.render(g, dt); });
    }

    private static KeyMapping reg(String name, int key, KeyMapping.Category cat) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(name, InputConstants.Type.KEYSYM, key, cat));
    }

    /** Fullbright via a client-side, invisible, infinite night vision effect. */
    private static void tickNightVision(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) { nvApplied = false; return; }
        if (Config.nightVision) {
            MobEffectInstance cur = p.getEffect(MobEffects.NIGHT_VISION);
            if (cur == null || (nvApplied && cur.getDuration() >= 0 && cur.getDuration() < 400)) {
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                        MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
                nvApplied = true;
            }
        } else if (nvApplied) {
            p.removeEffect(MobEffects.NIGHT_VISION);
            nvApplied = false;
        }
    }
}
