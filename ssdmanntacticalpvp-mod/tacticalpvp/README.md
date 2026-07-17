# Tactical PvP: Linear Sector Control (Forge 1.20.1)

A round-based Team Deathmatch / Linear Sector Control gamemode mod. See **README_uk.md**
for the full Ukrainian command reference requested for end users/admins.

## Project layout

```
src/main/java/com/tacticalpvp/
├── TacticalPvpMod.java          – mod entry point, command registration, team lookup
├── match/
│   ├── TeamColor.java           – RED / BLUE / NONE enum
│   ├── MatchState.java          – IDLE / RUNNING / PAUSED / ENDED
│   ├── MatchSavedData.java      – SavedData: points (keyed by designation+index), lobbies, config, scores (persisted)
│   ├── MatchManager.java        – start/stop/pause/resume, win checks, respawn queue, HUD sync
│   └── MatchEventHandler.java   – server tick driver, death -> respawn -> lobby routing
├── capture/
│   ├── CapturePoint.java        – single sector: fixed designation, dynamic owner, chain position, neutralize+capture progress
│   ├── CapturePhase.java        – IDLE / NEUTRALIZING / CAPTURING
│   └── CapturePointTicker.java  – per-tick presence/contest/capture/particle logic
├── command/
│   ├── MatchCommand.java        – /match init|start|stop|pause|resume|duration|scorelimit|lobbytimer|setlobby|randomize|purge
│   ├── PointCommand.java        – /point create <index> <red|blue|neutral> <capture_time_sec> <length> <width> <height_y>, /point delete
│   └── TeamRandomizer.java      – balanced random red/blue split, optional admin-exclude keyword
├── network/
│   ├── NetworkHandler.java      – SimpleChannel registration
│   └── packet/
│       ├── HudSyncPacket.java   – server -> client score/timer sync
│       └── ActionBarPacket.java – server -> client action bar messages
└── client/
    ├── ClientHudState.java      – cached HUD data on the client
    ├── HudOverlay.java          – top-center score/timer overlay renderer
    └── ClientSetup.java         – overlay registration
```

## Building via Gradle / GitHub

The project ships with a real Gradle Wrapper (`gradlew` / `gradlew.bat` / `gradle/wrapper/...`),
so nobody needs Gradle pre-installed — the wrapper downloads the exact right Gradle version
(8.1.1, matching the official 1.20.1 Forge MDK) automatically on first run.

**Local build:**
```bash
./gradlew build          # Linux/macOS
gradlew.bat build         # Windows
```
The compiled jar lands in `build/libs/tacticalpvp-1.0.0.jar`.

**Pushing to GitHub:** the archive already contains an initialized git repo with one commit.
```bash
cd tacticalpvp
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```

**CI build on every push:** `.github/workflows/build.yml` runs `./gradlew build` on GitHub's
runners for every push/PR to `main`, and uploads the resulting jar as a downloadable workflow
artifact. Push a tag like `v1.0.0` and it will also attach the jar to a GitHub Release
automatically.

## Setup on a running server

1. Build the mod (`./gradlew build`) and drop the jar from `build/libs/` into your server's `mods/` folder.
2. Run `/match init` to create the vanilla `red`/`blue` scoreboard teams and set the HUD timer
   to its static preset value.
3. Assign players either with `/match randomize teams` (balanced random split; add any keyword
   argument to exclude the executing admin from the pool) or manually via `/team join red <player>`.
4. Build a mirrored chain of capture points around a single shared center, e.g.
   `/point create 1 neutral 30 10 10 4` at the center, then `/point create 2 red ...`,
   `/point create 2 blue ...`, and so on outward to each team's base (index 1 must be
   `neutral`; indices 2-30 must be `red` or `blue`). Stand at the exact center of each
   zone before running the command (odd length/width values are auto-nudged one block
   North to resolve the halving imbalance). Use `/point delete <index> <red|blue|neutral>`
   to remove a specific point.
5. Set each team's waiting lobby with `/match setlobby red` / `/match setlobby blue`.
6. Configure `/match duration`, `/match scorelimit`, and `/match lobbytimer` to taste — the timer
   stays static (no countdown) until you run `/match start`.
7. Run `/match start` to begin a round. Use `/match pause` / `/match resume` to freeze and
   unfreeze captures, scoring, and the timer mid-round without ending it.

## Notes on scope

This is a complete, compilable-shape reference implementation covering every system requested:
teams/scoring/HUD, a mirrored linear tug-of-war chain (Red base ... neutral center ... Blue base)
with contested-freeze logic and a two-phase neutralize-then-capture progression for taking an
enemy-held point, dynamic frontline spawning (teleports to the team's most advanced held point),
particle-outlined zones, action-bar neutralize/capture progress + level-up sound on capture,
per-team waiting lobbies with Adventure-mode invulnerability and a respawn queue timer, the full
`/match` and `/point` admin command set (including pause/resume and point deletion), World-Save-Data
persistence, and an externalized `en_us.json` translation file. Because Forge/mapping APIs shift
subtly between 1.20.1 build environments, double-check method names (e.g.
`FriendlyByteBuf#writeComponent`, `GameRules.RULE_KEEPINVENTORY`) against the exact Forge version
you compile against, and run a local `./gradlew build` to catch any mapping drift before deploying
to a live server.
