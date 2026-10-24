# T-800 HUD v1.1 (Fabric, client-only, MC 1.21.11, Mojang mappings)

Die Mod bringt ALLE Texturen selbst mit (HUD, Hotbar, Herzen, Buttons, Liquid-Metal-Essen ...).
Kein extra Resource Pack nötig.

## Features
- Folgendes Ziel-Fadenkreuz (nur Sichtlinie), ACQUIRING -> [ LOCKED ], Lock-Sound, Scan-Tick
- Zielanalyse: Name, Klasse, Distanz, HP, Rüstung, Speed, Item, Threat, Sensor-Hexdaten
- "TARGET TERMINATED"-Meldung + Kill-Zähler
- System-Panel (POS, HDG, SPD, ENT, FPS, PING), Kompass-Band oben, Status oben rechts
  (INTEGRITY, POWER CELL, PLATING, SYS TIME), Warnungen bei wenig HP/Hunger, Schadens-Flash
- T-800-Schädel-Logo, Boot-Sequenz beim Beitritt, Vignette
- Tasten: H = HUD an/aus, J = Zielmodus, K = Terminator-Sicht (rote Scanlines)
- Texturen: Hotbar/Herzen (alle Varianten)/Hunger/Luftblasen/XP/Buttons;
  Liquid-Metal-Items: Apfel, goldener Apfel, Brot, Steak, Kotelett, Hammel, Hähnchen, Kaninchen,
  Ofenkartoffel, Karotte, goldene Karotte, Keks, Melone, Fisch, Pie, Eisenbarren (Chrom), Nether Star (T-800-Core)

## Jar bauen
**Lokal:** 1. https://fabricmc.net/develop/template/ -> 1.21.11, *Mojang Mappings* -> entpacken
2. `src/` und `.github/` aus diesem Zip hineinkopieren (fabric.mod.json ersetzen, Example-Klassen löschen)
3. `./gradlew build` -> `build/libs/t800hud-1.0.0.jar` (Java 21)

**Ohne lokale Installation:** den Template-Ordner (mit meinen Dateien) auf GitHub pushen ->
Tab "Actions" -> "build" -> Artifact `t800hud-jar` herunterladen.

Voraussetzung im Spiel: Fabric Loader + Fabric API.

## Mögliche Compile-Anpassungen (1.21.11)
`KeyBindingHelper` ↔ `KeyMappingHelper`, `KeyMapping.Category.register`, `mc.getFps()`,
`SimpleSoundInstance.forUI(...)`-Überladung. Fehlermeldung schicken -> wird gefixt.

Hinweis: Nur Ziele mit Sichtlinie (kein Wallhack). FOV-Effekte werden nicht einberechnet.
