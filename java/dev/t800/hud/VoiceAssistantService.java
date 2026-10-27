package dev.t800.hud;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
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
                post(mc, "T-800: Für Spracheingabe fehlt " + modelPath() + ". Lege dort ein Whisper-.cpp-Modell wie ggml-base.bin ab.");
                return;
            }
            Process process = new ProcessBuilder(whisperCommand(), "-m", modelPath().toString(),
                    "-f", recordingPath().toString(), "-nt").redirectErrorStream(true).start();
            boolean ended = process.waitFor(90, TimeUnit.SECONDS);
            if (!ended) { process.destroyForcibly(); status = "STT TIMEOUT"; post(mc, "T-800: Spracherkennung hat zu lange gebraucht."); return; }
            String transcript = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0 || transcript.isBlank()) {
                status = "SPEECH NOT RECOGNIZED";
                post(mc, "T-800: Sprache nicht erkannt. Prüfe whisper-cli und das Modell.");
                return;
            }
            // whisper-cli prints progress lines too; keep the final text-like lines.
            transcript = transcript.lines().map(String::trim).filter(s -> !s.isBlank() && !s.startsWith("whisper_")
                    && !s.startsWith("main:") && !s.startsWith("system_info:") && !s.startsWith("ggml_"))
                    .reduce((a,b) -> b).orElse("").replaceAll("^\\[[^]]*]\\s*", "").trim();
            final String prompt = transcript;
            status = "ASKING LOCAL MODEL...";
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
            String answer = askOllama(prompt, snapshot(mc));
            lastAnswer = answer; status = "ONLINE // LOCAL LLM";
            appendMemory("YOU: " + prompt + "\nT-800: " + answer);
            post(mc, "T-800 ▶ " + answer);
            speak(answer);
        } catch (Exception ex) {
            status = "ASSISTANT ERROR // " + ex.getClass().getSimpleName();
            post(mc, "T-800: Sprachassistent-Fehler: " + ex.getMessage());
        }
    }

    private String askOllama(String prompt, String gameState) throws Exception {
        String history = readMemory();
        String system = "You are T-800, a concise tactical Minecraft Fabric assistant. Speak German unless the user uses another language. " +
                "Use only the supplied game state; do not invent observations. Give advice, not automatic combat control. " +
                "Never claim a game action was executed. Game state: " + gameState + "\nRecent local conversation memory:\n" + history;
        String body = "{\"model\":\"" + json(ollamaModel()) + "\",\"stream\":false,\"messages\":[{\"role\":\"system\",\"content\":\"" +
                json(system) + "\"},{\"role\":\"user\",\"content\":\"" + json(prompt) + "\"}],\"options\":{\"temperature\":0.4}}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(ollamaUrl())).timeout(Duration.ofSeconds(75))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("Ollama HTTP " + response.statusCode() + "; starte Ollama und lade Modell " + ollamaModel());
        Matcher m = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(response.body());
        if (!m.find()) throw new IOException("Keine Antwort im Ollama-JSON; Modell/Server prüfen.");
        String answer = unjson(m.group(1)).trim();
        if (answer.isBlank()) throw new IOException("Das Sprachmodell lieferte eine leere Antwort.");
        return answer.length() > 1200 ? answer.substring(0, 1200) : answer;
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
        if (s.contains("öffne die karte") || s.contains("open map") || s.contains("gigamap")) return Optional.of("OPEN_GIGAMAP");
        if (s.contains("öffne einstellungen") || s.contains("open settings")) return Optional.of("OPEN_SETTINGS");
        if (s.contains("aktiviere nachtsicht") || s.contains("enable night vision")) return Optional.of("NIGHT_ON");
        if (s.contains("deaktiviere nachtsicht") || s.contains("disable night vision")) return Optional.of("NIGHT_OFF");
        if (s.contains("aktiviere terminator sicht") || s.contains("enable terminator vision")) return Optional.of("VISION_ON");
        if (s.contains("deaktiviere terminator sicht") || s.contains("disable terminator vision")) return Optional.of("VISION_OFF");
        if (s.contains("aktiviere hud") || s.contains("enable hud")) return Optional.of("HUD_ON");
        if (s.contains("deaktiviere hud") || s.contains("disable hud")) return Optional.of("HUD_OFF");
        return Optional.empty();
    }

    public void confirm(Minecraft mc) {
        PendingAction a = pending;
        if (a == null) { post(mc, "T-800: Keine Aktion wartet auf Bestätigung."); return; }
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
            default -> { post(mc, "T-800: Unbekannte Aktion abgelehnt."); return; }
        }
        Config.save(); status = "ACTION CONFIRMED"; post(mc, "T-800: Bestätigte Aktion ausgeführt: " + a.action); speak("Action confirmed.");
    }
    public void cancel(Minecraft mc) {
        if (pending == null) { post(mc, "T-800: Keine Aktion zum Abbrechen."); return; }
        pending = null; status = "ACTION CANCELLED"; post(mc, "T-800: Aktion abgebrochen.");
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
        String text = message.replaceAll("[^\\p{L}\\p{N} .,;:!?'-]", " ");
        if (text.length() > 500) text = text.substring(0, 500);
        final String safe = text;
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            ProcessBuilder pb;
            if (os.contains("mac")) pb = new ProcessBuilder("say", safe);
            else if (os.contains("win")) pb = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "Add-Type -AssemblyName System.Speech; (New-Object System.Speech.Synthesis.SpeechSynthesizer).Speak('" + safe.replace("'","") + "')");
            else pb = new ProcessBuilder("espeak", safe);
            pb.redirectErrorStream(true); pb.start();
        } catch (Exception ex) { status = "TTS UNAVAILABLE // " + ex.getClass().getSimpleName(); }
    }
}
