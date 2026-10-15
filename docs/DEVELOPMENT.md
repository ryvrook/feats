# Feats development notes

Notes for whoever maintains Feats, mostly me. The README covers what a server owner needs. This file covers
how the plugin is put together, which thread does what, and how I test it.

## Layout

`FeatsPlugin` owns the lifecycle. Setup builds the registry, the stores, the tracker, the exporter and the
reward service, installs `FeatsApi`, then picks the tracking path. Start loads players already online after a
reload, registers the placeholders, starts the flag watcher, links TrackerAPI and Coinage, and starts the
timers. Shutdown undoes it in reverse and drains the file thread for up to 10 seconds.

`model` holds the feat definitions. `FeatRegistry` builds all of them in code, 327 personal feats in nine tabs,
15 more in the Factions tab when Dominion is installed, and 18 world milestones and server firsts. `track`
holds `FeatsTracker`, the one entry point every count goes through, plus `Classify`, which turns a block, item,
creature or cause id into the counter keys it feeds, and the small anti farm memories. `systems` holds the
engine systems Feats registers when it tracks on its own. `integration` holds the only classes that touch
TrackerAPI and Coinage types. `stats` holds the player and server files. `export` holds the leaderboard file
and the Discord webhook. `rewards` reads `Rewards.json`. `scan` is the admin scan of existing worlds.

## Tracking paths

`TrackerSwitch` decides in setup, from the `Tracker` setting and the engine's plugin list, whether Feats
registers its own systems or listens to TrackerAPI. ECS systems can only be registered in setup and TrackerAPI
publishes its API in its own start, so the decision cannot wait for the API. With TrackerAPI the 13 engine hooks
in `registerOwnTracking` (twelve systems and the portal listener) are never registered, and `TrackerBridge`
subscribes 19 listeners that call the tracker exactly as the systems did. The player ready, disconnect, world
and chat listeners stay either way, since they load players, keep the Creative and disabled world pause and put
titles in chat. Time played, session length and the Goblin Lab flag poll stay with Feats on both paths.

`TrackerBridgeTest` feeds synthetic TrackerAPI records through the bridge and checks the counters come out as
the Feats systems made them. A reload of TrackerAPI leaves Feats with hooks on the old classes. TrackerAPI 1.0.3
and newer reload their dependents themselves, so the 5 second watch in `TrackerSwitch` stays quiet for those and
only logs a reminder for an older TrackerAPI or an unload.

## Threads

- World threads run the engine systems, `PlayerReadyEvent` and the game mode read back. The ready handler reads
  the player's file through `tracker.load`. That read measured about 0.1 ms for a file with 150 counters and
  120 unlocks, so it stays there.
- The engine scheduler runs the 1 second tick (time played, and the movement sample handed to each world
  thread when Feats tracks on its own, and the hand over of Coins waiting for Coinage), the autosave timer, the export timer, the 5 second flag poll, the 5
  second TrackerAPI watch and the reward queue, one step every 100 ms.
- `Feats-IO` is a single daemon thread. Every save, the export, the rarity scan and the Discord posts run
  there. Shutdown is the only place that saves inline, after the timers are cancelled.
- The TrackerAPI dispatch thread runs the bridge listeners. The tracker is safe from any thread.
- Chat runs on the engine's async chat future, at LATE priority so moderation plugins cancel first.

Nothing in Feats touches a store, ref or component off its world thread, and nothing does file work on a world
thread except that one read at join.

## Files

Everything lives in `mods/Ryvrook_Feats/`.

| File | Written by | Notes |
|---|---|---|
| `Feats.json` | the engine config | written once at setup with defaults filled in |
| `Rewards.json` | `RewardService` | template written only when `RewardsEnabled` is on and the file is missing |
| `players/<uuid>.json` | `ProgressStore` | temp file then atomic move. A file that fails to parse moves to `<uuid>.corrupt-<time>.json`, only the newest kept |
| `server.json` | `WorldStore` | milestones and firsts, same atomic write, corrupt copy kept the same way |
| `export/feats.json` | `LeaderboardExporter` | rewritten only when the body changed, read by Atlas and Chronicle Stats |
| `CoinsWaiting.json` | `RewardService` | only when a shutdown found Coins rewards waiting for Coinage, read and deleted at the next start |

The exporter and the rarity scan read every player file at most once a minute. `ProgressStore.loadAll` keeps
the size, modified time and file key of each file it read and parses a file again when any of the three differ,
so a file edited by hand is read again even when its time went backwards. With 2,000 player files a read with
nothing changed takes about 12 ms, where parsing every file takes 170 to 300 ms on my machine. `ExportReadBenchmarkTest` measures it and writes
`build/benchmark-export.txt`.

## Integrations

TrackerAPI is listed under `OptionalDependencies`, so its classes are visible, and `TrackerBridge` is loaded
only after the switch found it. Coinage works the same way through `CoinageRewards`, loaded by name in start
when the plugin list has `Ryvrook:Coinage`. It follows Coinage with the enable and disable hooks and pays the
`Coins` of a reward with `Ledger.deposit`, plugin `Feats`, reason `feats:<feat id>`. Rewards are recorded in
the player file before they run, so a refused deposit is logged and not retried. A deposit made while Coinage
is stopped moves nothing, so it waits in `WaitingCoins` (at most 10,000) and the 1 second tick queues it again
once Coinage is linked. A Coinage reload stops Coinage first and reloads Feats about two seconds later, so a
Feats shutdown in between writes what waits to `CoinsWaiting.json` and the next start reads it back.

Dominion is not a dependency. It lists Feats and calls `FeatsApi.addStat` with its `dominion:` counters.
`addStat` for an offline player goes through `FeatsTracker.addOffline`, which merges the adds per player in
memory (at most 10,000 players waiting) and queues one task on Feats-IO. That task takes the player with
`acquire`, adds, checks the feats that read those keys as an offline catch up and releases, which saves. A join
before the task runs gets the adds on the live copy instead, and a join during it shares the held copy. A player
without a file is skipped. An admin edit holding the player puts the adds back and queues the task again.
`FeatsApi` objects from before a Feats reload refuse `addStat`, since the old file thread is gone and the
write would run on the caller's thread. `OfflineAddsTest` covers each case.
`Siblings.installed("Dominion")` looks at the engine's available plugins in setup, since Dominion sets up after
Feats, and adds the Factions tab when the jar is there. `FactionFeatsTest` holds the list of keys Dominion 0.5.1
sends, so a rename on either side fails the build.

PlaceholderAPI is found by class name and registered in start. `FeatsExpansion` never names a PlaceholderAPI
type outside its nested `Hook`.

## The asset pack rule

The engine registers the jar's UI pack right after setup and refuses a pack whose name is already registered.
After a jar swap under another file name, or a `/plugin reload Feats`, the old registration blocks the new one
until a restart. `AssetPackFix.dropStale` runs first in setup and removes it, `report` logs one line in start,
and `release` drops the registration at shutdown so the engine's own unload does not ask the asset monitor to
unwatch a jar file system, which logs a SEVERE `ProviderMismatchException`. Coinage carries the same class.

## Build and test

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
./gradlew build
./gradlew test -Pbench   # prints the tracking and export benchmarks
```

The tests run without a server. The server jar is only on the test classpath for bson and the logger, and the
TrackerAPI and Coinage jars come from the sibling checkouts, see `build.gradle.kts`.

For a headless boot I copy the jars into a fresh folder under `mods/`, write an empty `permissions.json` and run
the server with `--auth-mode offline --disable-sentry --bind 127.0.0.1:<port>`, feeding console commands through
a fifo. TrackerAPI's `docs/DEVELOPMENT.md` has the full recipe. The lines to look for are the tracking path line,
`Feats loaded 327 feats worth 15075 points, plus 18 world feats`, the Factions line with Dominion and the
Coinage line with Coinage. The boot also logs `Skipping pack at Ryvrook_Feats: missing or invalid
manifest.json`, which is the asset scan looking into the data folder and is harmless.
