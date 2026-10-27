# T-800 local voice assistant setup

The mod uses a hybrid design: Java computes live Minecraft state locally; Ollama runs the language model on the same computer; whisper.cpp performs speech-to-text locally; the operating system speaks answers. The mod does not bundle model weights or executables.

## 1. Install Ollama and a model

Install Ollama for your operating system, then run:

```sh
ollama pull qwen2.5:3b
```

Keep Ollama running. The default API is `http://127.0.0.1:11434/api/chat`. Optional Java system properties:
- `-Dt800.ollama.url=http://127.0.0.1:11434/api/chat`
- `-Dt800.ollama.model=qwen2.5:3b`

The first model load can be slow. The game does not wait for Ollama on the render thread.

## 2. Install whisper.cpp

Install/build whisper.cpp for your operating system and make its CLI executable available as `whisper-cli` in PATH. Download a Whisper model such as `ggml-base.bin` separately. Create this folder in the Minecraft instance config directory:

```
config/t800hud/
```

Put `ggml-base.bin` in that folder. If the executable is named differently, set Java property `-Dt800.whisper.command=/full/path/to/whisper-cli`. The expected model path is `config/t800hud/ggml-base.bin`.

Speech is recorded as 16 kHz mono WAV while the key is held. The audio file is written to `config/t800hud/voice-input.wav` and replaced by the next recording. Microphone permission and a working input device are required.

## 3. In-game controls

- Enable **Voice Assistant (OS TTS)** in Right Shift > System Configuration.
- Hold **F10** while speaking, then release it to transcribe and send the question to Ollama.
- **F11** confirms a supported proposed action.
- **F12** cancels a pending action.

F10/F11/F12 are configurable in Options > Controls. Supported confirmed voice actions currently include opening GigaMap/settings and toggling HUD, Terminator vision, or night vision. Other requests are sent to the language model as questions; the model cannot arbitrarily execute Java or shell commands.

Conversation history is stored locally in `config/t800hud-assistant-memory.txt`. Audio is stored locally in `config/t800hud/voice-input.wav`. Delete these files to remove the corresponding local history/audio. No speech or game data is sent to a cloud service by this implementation; Ollama and whisper.cpp run locally.

## Limitations

This is a first integration, not a packaged, turnkey voice model. Users must install Ollama, download its LLM, install whisper.cpp, and download the speech model. TTS uses macOS `say`, Windows PowerShell/System.Speech, or Linux `espeak`. The mod has been compiled by GitHub Actions; in-game microphone, model response, and TTS still require manual testing.
