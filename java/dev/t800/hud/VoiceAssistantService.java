package dev.t800.hud;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;

import javax.sound.sampled.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * Optional voice bridge: hold the configured key to record, whisper.cpp transcribes,
 * Ollama answers locally, and OS TTS reads the answer. All expensive work is off-thread.
 */
public final class VoiceAssistantService {
    public static final VoiceAssistantService INSTANCE = new VoiceAssistantService();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "t800-assistant"); t.setDaemon(true); return t;
    });
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private volatile boolean recording;
    private volatile TargetDataLine line;
    private volatile String status = "READY // HOLD F10 TO TALK";
    private volatile String lastAnswer = "";
    private volatile PendingAction pending;
    private Path recordingPath() { return FabricLoader.getInstance().getConfigDir().resolve("t800hud/voice-input.wav"); }
    private Path modelPath() { return FabricLoader.getInstance().getConfigDir().resolve("t800hud/ggml-base.bin"); }
    private Path memoryPath() { return FabricLoader.getInstance().getConfigDir().resolve("t800hud-assistant-memory.txt"); }
    private String whisperCommand() { return System.getProperty("t800.whisper.command", "whisper-cli"); }
    private String ollamaUrl() { return System.getProperty("t800.ollama.url", "http://127.0.0.1:11434/api/chat"); }
    private String ollamaModel() { return System.getProperty("t800.ollama.model", "qwen2.5:3b"); }

    public String status() { return status; }
    public String lastAnswer() { return lastAnswer; }
    public boolean isRecording() { return recording; }
    public boolean hasPendingAction() { return pending != null; }

    public void startRecording(Minecraft mc) {
        if (recording || !Config.voiceAssistant || mc.player == null || mc.screen != null) return;
        recording = true; status = "MIC // LISTENING — RELEASE F10 TO SEND";
        worker.submit(() -> {
            AudioFormat format = new AudioFormat(16000f, 16, 1, true, false);
            try {
                Files.createDirectories(recordingPath().getParent());
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(format); line.start();
                try (AudioInputStream input = new AudioInputStream(line)) {
                    // Recording ends when stopRecording closes the active line.
                    AudioSystem.write(input, AudioFileFormat.Type.WAVE, recordingPath().toFile());
                }
            } catch (Exception ex) {
                status = "MIC ERROR // " + ex.getClass().getSimpleName();
                recording = false;
                post(mc, "T-800 microphone error: " + ex.getMessage());
            }
        });
    }

    public void stopRecording(Minecraft mc) {
        if (!recording) return;
        recording = false;
        TargetDataLine active = line; line = null;
        if (active != null) { try { active.stop(); } catch (Exception ignored) {} try { active.close(); } catch (Exception ignored) {} }
        status = "PROCESSING AUDIO...";
        worker.submit(() -> transcribeAndAsk(mc));
    }

    private void transcribeAndAsk(Minecraft mc) {
        try {
            if (!Files.exists(modelPath())) {
                status = "WHISPER MODEL MISSING";
                post(mc, "T-800: Speech input model missing: " + modelPath() + ". Place a Whisper.cpp model such as ggml-base.bin there.");
                return;
            }
            Process process = new ProcessBuilder(whisperCommand(), "-m", modelPath().toString(),
                    "-f", recordingPath().toString(), "-nt").redirectErrorStream(true).start();
            boolean ended = process.waitFor(90, TimeUnit.SECONDS);
            if (!ended) { process.destroyForcibly(); status = "STT TIMEOUT"; post(mc, "T-800: Speech recognition timed out."); return; }
            String transcript = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0 || transcript.isBlank()) {
                status = "SPEECH NOT RECOGNIZED";
                post(mc, "T-800: Speech was not recognized. Check whisper-cli and the Whisper model.");
                return;
            }
            // whisper-cli prints progress lines too; keep the final text-like lines.
            transcript = transcript.lines().map(String::trim).filter(s -> !s.isBlank() && !s.startsWith("whisper_")
                    && !s.startsWith("main:") && !s.startsWith("system_info:") && !s.startsWith("ggml_"))
                    .reduce((a,b) -> b).orElse("").replaceAll("^\\[[^]]*]\\s*", "").trim();
            final String prompt = transcript;
            status = "LOCAL TACTICAL CORE...";
            post(mc, "YOU ▶ " + prompt);
            Optional<String> action = detectAction(prompt);
            if (action.isPresent()) {
                pending = new PendingAction(action.get(), prompt);
                status = "ACTION NEEDS CONFIRMATION";
                post(mc, "T-800 ACTION PROPOSAL: " + action.get() + " — press F11 to confirm or F12 to cancel.");
                speak("Action requires confirmation.");
                appendMemory("YOU: " + prompt + "\nPROPOSED ACTION: " + action.get());
                return;
            }
            String answer = answerLocally(prompt, mc, snapshot(mc));
            lastAnswer = answer; status = "ONLINE // LOCAL TACTICAL CORE";
            appendMemory("YOU: " + prompt + "\nT-800: " + answer);
            post(mc, "T-800 ▶ " + answer);
            speak(answer);
        } catch (Exception ex) {
            status = "ASSISTANT ERROR // " + ex.getClass().getSimpleName();
            post(mc, "T-800: Voice assistant error: " + ex.getMessage());
        }
    }

    /**
     * Uses the mod's own local tactical systems only. No Ollama, cloud API, or external
     * language model is called; the response is composed from live CombatAIService data.
     */
    private String answerLocally(String prompt, Minecraft mc, String gameState) {
        String q = prompt.toLowerCase(Locale.ROOT);
        var player = mc.player;
        if (player == null || mc.level == null) return "No world is currently loaded.";
        LivingEntity target = T800HudClient.TRACKER.target;
        if (containsAny(q, "status", "health", "armor", "armour", "survival")) {
            return String.format(Locale.ROOT,
                    "System status. Health %.0f percent. Armor %d. Armor integrity %d percent. Hunger %d. %s",
                    100.0 * player.getHealth() / Math.max(1.0f, player.getMaxHealth()),
                    player.getArmorValue(), CombatAIService.INSTANCE.armorDurability(player),
                    player.getFoodData().getFoodLevel(), CombatAIService.INSTANCE.diagnosis());
        }
        if (containsAny(q, "target", "enemy", "opponent", "skill", "pattern", "scan")) {
            if (target == null || !target.isAlive()) return "No target locked. Aim at a loaded entity to acquire a target.";
            return "Target locked. " + target.getName().getString() + ". Range " +
                    String.format(Locale.ROOT, "%.1f blocks. ", target.distanceTo(player)) +
                    "Health " + Math.round(target.getHealth()) + " of " + Math.round(target.getMaxHealth()) +
                    ". Tactical skill estimate " + CombatAIService.INSTANCE.skillScore(target) + " out of 100. " +
                    CombatAIService.INSTANCE.patternSummary(target) + ". " + CombatAIService.INSTANCE.advice();
        }
        if (containsAny(q, "threat", "projectile", "arrow", "incoming", "danger")) {
            String projectile = CombatAIService.INSTANCE.projectileThreats().stream().limit(2)
                    .map(CombatAIService.ProjectileThreat::summary).reduce((a,b) -> a + ". " + b).orElse("No incoming projectile trajectory detected.");
            var top = CombatAIService.INSTANCE.threats().stream().findFirst();
            String threat = top.map(t -> "Top threat " + t.entity.getName().getString() +
                    ", score " + Math.round(t.score) + " out of 100, range " +
                    String.format(Locale.ROOT, "%.1f blocks. ", t.distance) + t.pattern + ".")
                    .orElse("No nearby threats detected.");
            return threat + " " + projectile;
        }
        if (containsAny(q, "map", "location", "coordinates", "where am i")) {
            var pos = player.blockPosition();
            return "Current coordinates X " + pos.getX() + ", Y " + pos.getY() + ", Z " + pos.getZ() +
                    ". Press F6 to open GigaMap. Saved bookmarks are shown in gold.";
        }
        if (containsAny(q, "effect", "potion", "buff", "debuff")) {
            String effects = player.getActiveEffects().stream().limit(5)
                    .map(e -> e.getEffect().value().getDisplayName().getString() +
                            (e.isInfiniteDuration() ? " active" : " for " + Math.max(1, e.getDuration() / 20) + " seconds"))
                    .reduce((a,b) -> a + ". " + b).orElse("No active status effects.");
            return "Status effect scan. " + effects;
        }
        if (containsAny(q, "help", "commands", "what can you do")) {
            return "T-800 local assistant online. Ask for status, target scan, threat analysis, effects, or coordinates. " +
                    "Hold F10 to speak. F4 opens Combat Analytics, F6 opens GigaMap, I toggles thermal vision. " +
                    "I use the mod's local tactical analysis only; no external language model is connected.";
        }
        var top = CombatAIService.INSTANCE.threats().stream().findFirst();
        if (top.isPresent()) {
            var t = top.get();
            return "Local tactical core online. Nearest priority is " + t.entity.getName().getString() +
                    ", threat score " + Math.round(t.score) + ", range " +
                    String.format(Locale.ROOT, "%.1f blocks. ", t.distance) + t.pattern + ". " + t.advice;
        }
        return "Local tactical core online. No immediate threat detected. " +
                "I can report status, scan a target, analyze incoming projectiles, read effects, or report coordinates.";
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private String snapshot(Minecraft mc) {
        if (mc.player == null || mc.level == null) return "No world loaded.";
        var p = mc.player;
        String target = T800HudClient.TRACKER.target == null ? "none" :
                T800HudClient.TRACKER.target.getType().toString() + ", health " +
                String.format(Locale.ROOT, "%.1f/%.1f", T800HudClient.TRACKER.target.getHealth(), T800HudClient.TRACKER.target.getMaxHealth());
        String projectiles = CombatAIService.INSTANCE.projectileThreats().stream().limit(3)
                .map(CombatAIService.ProjectileThreat::summary).reduce((a,b)->a+"; "+b).orElse("none");
        String threat = CombatAIService.INSTANCE.threats().stream().limit(3)
                .map(t -> t.entity.getType()+ " score "+Math.round(t.score)+" distance "+Math.round(t.distance))
                .reduce((a,b)->a+"; "+b).orElse("none");
        return "dimension="+mc.level.dimension().identifier()+", xyz="+p.blockPosition()+", health="+
                String.format(Locale.ROOT,"%.1f/%.1f",p.getHealth(),p.getMaxHealth())+", hunger="+p.getFoodData().getFoodLevel()+
                ", armor durability="+CombatAIService.INSTANCE.armorDurability(p)+"%, target="+target+
                ", threats="+threat+", incoming projectiles="+projectiles;
    }

    private Optional<String> detectAction(String text) {
        String s = text.toLowerCase(Locale.ROOT);
        if (s.contains("open map") || s.contains("gigamap")) return Optional.of("OPEN_GIGAMAP");
        if (s.contains("open settings")) return Optional.of("OPEN_SETTINGS");
        if (s.contains("enable night vision")) return Optional.of("NIGHT_ON");
        if (s.contains("disable night vision")) return Optional.of("NIGHT_OFF");
        if (s.contains("enable terminator vision")) return Optional.of("VISION_ON");
        if (s.contains("disable terminator vision")) return Optional.of("VISION_OFF");
        if (s.contains("enable hud")) return Optional.of("HUD_ON");
        if (s.contains("disable hud")) return Optional.of("HUD_OFF");
        return Optional.empty();
    }

    public void confirm(Minecraft mc) {
        PendingAction a = pending;
        if (a == null) { post(mc, "T-800: No action is waiting for confirmation."); return; }
        pending = null;
        switch (a.action) {
            case "OPEN_GIGAMAP" -> mc.setScreen(new GigaMapScreen());
            case "OPEN_SETTINGS" -> mc.setScreen(new SettingsScreen());
            case "NIGHT_ON" -> Config.nightVision = true;
            case "NIGHT_OFF" -> Config.nightVision = false;
            case "VISION_ON" -> Config.vision = true;
            case "VISION_OFF" -> Config.vision = false;
            case "HUD_ON" -> Config.hud = true;
            case "HUD_OFF" -> Config.hud = false;
            default -> { post(mc, "T-800: Unknown action rejected."); return; }
        }
        Config.save(); status = "ACTION CONFIRMED"; post(mc, "T-800: Confirmed action executed: " + a.action); speak("Action confirmed.");
    }
    public void cancel(Minecraft mc) {
        if (pending == null) { post(mc, "T-800: No action to cancel."); return; }
        pending = null; status = "ACTION CANCELLED"; post(mc, "T-800: Action cancelled.");
    }
    private record PendingAction(String action, String originalText) {}

    private void appendMemory(String s) {
        try { Files.createDirectories(memoryPath().getParent()); Files.writeString(memoryPath(), s + "\n---\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (Exception ignored) {}
    }
    private String readMemory() {
        try {
            if (!Files.exists(memoryPath())) return "(empty)";
            List<String> lines = Files.readAllLines(memoryPath(), StandardCharsets.UTF_8);
            return String.join("\n", lines.subList(Math.max(0, lines.size()-24), lines.size()));
        } catch (Exception ignored) { return "(memory unavailable)"; }
    }
    private static String json(String s) {
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");
    }
    private static String unjson(String s) {
        return s.replace("\\n","\n").replace("\\r","\r").replace("\\t","\t").replace("\\\"","\"").replace("\\\\","\\");
    }
    private void post(Minecraft mc, String message) {
        mc.execute(() -> { if (mc.player != null) mc.player.displayClientMessage(Component.literal(message), false); });
    }
    private void speak(String message) {
        String text = message.replaceAll("[^\\p{L}\\p{N} .,;:!?'-]", " ")
                .replaceAll("\\s{2,}", " ").trim();
        if (text.isBlank()) return;
        if (text.length() > 350) text = text.substring(0, 350);
        final String safe = text;
        // Never enumerate installed voices here: that starts an extra process and can delay every utterance.
        // Run TTS on its own daemon thread so keybinds and the Minecraft client never wait for speech startup.
        Thread tts = new Thread(() -> {
            try {
                String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                boolean english = true;
                if (os.contains("win")) {
                    String culture = english ? "en-US" : "de-DE";
                    String script = "Add-Type -AssemblyName System.Speech; " +
                            "$s=New-Object System.Speech.Synthesis.SpeechSynthesizer; $s.Rate=0; $s.Volume=100; " +
                            "try {$s.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::NotSet, " +
                            "[System.Speech.Synthesis.VoiceAge]::NotSet, 0, [Globalization.CultureInfo]::GetCultureInfo('" + culture + "'))} catch {}; " +
                            "$s.Speak('" + safe.replace("'","''") + "')";
                    new ProcessBuilder("powershell", "-NoProfile", "-Command", script).redirectErrorStream(true).start();
                } else if (os.contains("mac")) {
                    // Use the default macOS voice immediately; set a preferred language voice in macOS settings if needed.
                    new ProcessBuilder("say", "-v", "Samantha", "-r", "170", safe).redirectErrorStream(true).start();
                } else {
                    new ProcessBuilder("espeak", "-s", "155", "-p", "38", "-v", "en-us+m3", safe)
                            .redirectErrorStream(true).start();
                }
            } catch (Exception ex) {
                status = "TTS UNAVAILABLE // " + ex.getClass().getSimpleName();
            }
        }, "t800-tts");
        tts.setDaemon(true);
        tts.start();
    }

    private boolean looksEnglish(String text) {
        String s = " " + text.toLowerCase(Locale.ROOT) + " ";
        if (s.matches(".*\\b(the|this|that|with|you|your|enemy|target|health|confirmed|action|open|map|settings|night vision)\\b.*")) return true;
        if (s.matches(".*\\b(der|die|das|und|nicht|bitte|dein|deine|ziel|gegner|leben|bestätigt|aktion|öffne|karte|einstellungen|nachtsicht)\\b.*")) return false;
        return !s.matches(".*[äöüß].*");
    }
}
