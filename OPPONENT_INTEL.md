# T-800 Opponent Intelligence

## What the mod can know

The live Minecraft client can observe the target's current in-game name/UUID, held item, armor, health, distance, movement and combat patterns. These are local observations and remain available without an external service.

The mod also constructs a NameMC profile URL for a player UUID:
`https://namemc.com/profile/<uuid>`
NameMC is not scraped by this mod. Its terms prohibit automated scraping, and its documented/public API does not provide a general combat-skill profile. The link is a manual reference only.

## Optional DonutSMP stats

For DonutSMP community statistics, the mod supports the DonutStats read API. It requires your own API key and uses `lookup=none` so combat ticks do not trigger repeated live lookups. Stats are external, may be stale or unavailable, and are not evidence of PvP skill.

1. Obtain a read-scope API key from DonutStats.
2. Create the folder `config/t800hud/` inside your Modrinth Minecraft instance.
3. Create a text file named `donutstats.key` in that folder and paste only the API key into it.
4. Restart the game and target a player. The combat overlay may show tracked DonutSMP kills, deaths, playtime and economy information when the API has that data.

Keep this key private. Do not commit it to GitHub or share the file. If the file is missing, the mod performs no DonutStats request.

## PvP profiles

Press **F7** to cycle the profile, or select **PvP Profile [F7]** in the T-800 settings. Modes currently include AUTO, SMP, CRYSTAL, SWORD, AXE, POT, UHC and PVP CLUB. AUTO detects recognizable server address fragments (Donut and PvP Club) and otherwise falls back to SMP. You can explicitly select a mode if the server address is hidden or custom.

The profiles change the tactical wording and priorities; they do not claim perfect mode detection. For example:
- SMP: survival awareness, cover, crystal placement and ranged threats.
- Crystal: obsidian/crystal placement awareness.
- Sword: spacing and attack timing.
- Axe: shield-disabling and cooldown awareness.
- Pot: healing and splash-potion awareness.
- UHC: bow threats, cover and healing resources.
- PvP Club: generic profile for the server; more precise per-queue support needs a documented API or mode signal from that server.

## Privacy and reliability

Lookups are asynchronous and cached for 60 seconds per target; they do not block the render thread. If the API fails, the mod continues with live local combat analysis. The DonutStats response is treated as optional community data, not official proof of a player's strength. No API key is bundled with the mod.
