package dev.t800.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class T800HudClient implements ClientModInitializer {
    public static final String MOD_ID = "t800hud";
    public static final TargetTracker TRACKER = new TargetTracker();
    public static boolean enabled = true;
    public static boolean vision = false;

    private static KeyMapping toggleKey;
    private static KeyMapping modeKey;
    private static KeyMapping visionKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.t800hud.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, category));
        modeKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.t800hud.mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, category));

        visionKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.t800hud.vision", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggleKey.consumeClick()) enabled = !enabled;
            while (modeKey.consumeClick()) TRACKER.cycleMode();
            while (visionKey.consumeClick()) vision = !vision;
            if (enabled) TRACKER.tick(mc); else TRACKER.clear();
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), new HudRenderer());
    }
}
