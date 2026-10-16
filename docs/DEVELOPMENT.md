# Feats development notes

Notes for whoever maintains Feats, mostly me. The README covers what a server owner needs. This file covers
how the plugin is put together, which thread does what, and how I test it.

## Layout

`FeatsPlugin` owns the lifecycle. Setup builds the registry, the stores, the tracker, the exporter and the
reward service, installs `FeatsApi`, then picks the tracking path. Start loads players already online after a
reload, registers the placeholders, starts the flag watcher, links TrackerAPI and Coinage, starts the sibling
bindings and the hook sets, and starts the timers. Shutdown undoes it in reverse and drains the file thread for up to 10 seconds.

`model` holds the feat definitions. `FeatRegistry` builds all of them in code from the siblings found at setup
and the packs that are on. A plain server has 445 personal feats in nine tabs, the default packs make it 455,
and every pack and sibling 528, with 30 world milestones and server firsts on top. A feat carries `requires`, the
`Sibling` set it needs, `pack`, the `Pack` it sits in, and `banner`. `add` leaves out a feat whose sibling is
missing or whose pack is off and remembers why, which `whySkipped` hands to the admin grant. `track` holds
`FeatsTracker`, the one entry point every count goes through, plus `Classify`, which turns a block, item,
creature or cause id into the counter keys it feeds, the small anti farm memories and the 1.7.2 helpers
(`Presence`, `Windows`, `GameDays`, `JoinCalendar`, `Vitals`, `MovementEdges`, `ArmourSets`, `LastKillers`,
`BannerQueue`). `systems` holds the engine systems Feats registers when it tracks on its own. `integration`
holds the only classes that touch TrackerAPI, Coinage and Atlas types and the reflective Chronicle and Dominion
bindings. `stats` holds the player and server files. `export` holds the leaderboard file
and the Discord webhook. `rewards` reads `Rewards.json`. `scan` is the admin scan of existing worlds.

Files added in 1.7.2.

| File | What it does |
|---|---|
| `model/Pack.java`, `model/Sibling.java` | The five packs with their setting keys, the five siblings with plugin name and minimum version |
| `track/Hooks.java`, `track/HookSet.java` | The hook seam, see Hook sets |
| `systems/CombatHooks.java`, `CombatContext`, `CombatKinds`, `CombatMemory`, `ProjectileLaunchSystem` | Kill context, creature kinds, settled falls and drowning, range notes, shots fired |
| `systems/WorldHooks.java`, `InstanceDiscoverySystem`, `WorldEventEndSystem`, `SleepWatch`, `WorldPortals`, `WorldKeys` | Biomes, weather, moon, instances, trigger volumes, world events, nights slept, portal time, the calendar, presence and the totals raised by hooks |
| `systems/ItemHooks.java`, `MountSystem`, `MovementSystem`, `UseEntitySystem`, `MerchantPage`, `RecipePoll` | Seats, mounts, movement edges, NPC talk, the merchant read, known recipes |
| `track/BannerQueue.java`, `ui/FeatsBannerHud.java`, `Common/UI/Custom/Hud/Feats_Banner.ui` | The banner rules, the keyed HUD and its document |
| `integration/SiblingLinks.java`, `CoinageFeats`, `ChronicleFeats`, `AtlasFeats`, `CrossModFeats` | The sibling bindings, see Integrations |

## Tracking paths

`TrackerSwitch` decides in setup, from the `Tracker` setting and the engine's plugin list, whether Feats
registers its own systems or listens to TrackerAPI. ECS systems can only be registered in setup and TrackerAPI
publishes its API in its own start, so the decision cannot wait for the API. With TrackerAPI the 13 engine hooks
in `registerOwnTracking` (twelve systems and the portal listener) and every `registerOwn` of the hook sets are
never registered, and `TrackerBridge` subscribes 34 listeners that call the tracker exactly as the systems did.
The player ready, disconnect, world and chat listeners stay either way, since they load players, keep the
Creative and disabled world pause and put titles in chat. Time played, session length and the Goblin Lab flag poll stay with Feats on both paths.

The 15 kinds added in 1.7.2 are MovementEdge, MountChanged, VitalsSampled, ItemEquipped, ItemBroke,
ProjectileLaunched, ProjectileLanded, InstanceDiscovered, TriggerVolumeFired, WorldEventEnded, PlayerSlept,
WeatherChanged, GameTimeSampled, EntityUsed and PlayerLeft, all in TrackerAPI 1.0.0, so `TrackerSwitch.MINIMUM`
stays 1.0.0. The bridge keeps per player `Body` and per world `Sky` notes on its dispatch thread for what the own
path reads off the engine at the moment of a kill, a landing or a sunrise. Three rules are approximations of
the own path. An armour set counts once each of its slots changed in the session, since no record lists what is
already worn. A world event credits every player in its world. A night slept counts for anyone seen asleep when
the world's clock reached morning. RecipeLearned, PermissionChanged,
PortalEntered and MoonPhaseChanged are left out on purpose, since a Feats hook or another record already covers
them. Two kinds of counter do not follow the path. Those the hook sets register in `registerAlways` or read in
`sampleWorld` whatever `ownPath` says (vitals, game days, respawn points, hardcore lives, recipes, permission
groups, referrals, the calendar, presence and the totals raised by hooks) run on both paths. Those no TrackerAPI
record carries (rolls, mantles, double jumps, seats, shield blocks, repairs, capture crates, trap bait,
`memory_by_kill`, void stages, late portal exits and merchants) only move on the own path, and the README says
which feats that leaves out.

`TrackerBridgeTest` feeds synthetic TrackerAPI records through the bridge and checks the counters come out as
the Feats systems made them. A reload of TrackerAPI leaves Feats with hooks on the old classes. TrackerAPI 1.0.3
and newer reload their dependents themselves, so the 5 second watch in `TrackerSwitch` stays quiet for those and
only logs a reminder for an older TrackerAPI or an unload.

## Hook sets

`Hooks` fans the plugin's life out to a list of `HookSet`s, so a new hook never touches `FeatsPlugin`. Each set
gets `registerOwn` (setup, own path only), `registerAlways` (setup, both paths), `start`, `stop`, `tick` (the 1 s
engine scheduler with everyone online, memory only), `sampleWorld` (once a second on each world's thread with
the players in it, with `ownPath` false on the TrackerAPI path), `joined` and `left`. Every call is wrapped, so a
set that throws or meets a missing engine class is logged and the others still run. `Hooks.Context` carries the
plugin, tracker, registry, config supplier, logger, world store, siblings, `ownPath` and the session clock.

- `CombatHooks` registers `ProjectileLaunchSystem` and settles the falls and drowning episodes in `CombatMemory`
  once a second. `DamageSystem` and `DeathSystem` stay in `registerOwnTracking` and feed the kill context, the
  multikill `Windows`, creature kinds, damage kinds, blocks, co-op bosses, vendettas and the player streak.
- `WorldHooks` registers the instance discovery and world event end systems and the trigger volume listener on
  the own path, and on both paths the referral, permission group and chunk generation listeners. Its tick runs `Presence` and `JoinCalendar` and raises the peak online and server days
  totals. Its world sample reads the clock, weather, sleep and portal state once per world, then each player's
  biome, weather, moon, vitals, respawn points and lives, with the biome, weather, moon, portal and sleep reads
  only on the own path. Start reads the hardcore mode and `installed_at`.
- `ItemHooks` registers `UseEntitySystem`, `MountSystem` and `MovementSystem` on the own path, each on its own so a
  missing class costs one group, and polls known recipes every fifth world sample on both paths. The item
  counters that ride on systems Feats already had (inventory, placing, breaking, pickups, interactions, taming)
  live in those systems.

Every engine read in a set is guarded. A read whose class is missing switches off with one warning, a read that
fails once is skipped for that sample.

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
- Coinage and Atlas call the Coinage and Atlas bindings on their own threads. The Coinage net worth read runs
  every 5 minutes and the Chronicle poll every 5 seconds on the engine scheduler.
- The banner hops twice. `UnlockNotifier.showBanner` asks `BannerQueue` for a slot on the unlocking thread and
  schedules the removal on the engine scheduler at once, so a removal exists whether or not the add works, then
  posts to the player's world with `world.execute`, where it adds the keyed `FeatsBannerHud` unless the slot has
  already been replaced or timed out, plays the sound and spawns the particles. The removal looks the player up
  by uuid when it fires, posts to whichever world they are in now, removes the HUD there and only then marks the
  slot gone. A player with no world is tried again once a second for 10 seconds, an offline player is marked gone
  at once. Generations come from one counter shared by every player, so a timer from before a reconnect never
  matches a new banner. `UnlockNotifier.stop` cancels every timer and takes every banner down at shutdown, so a
  reload's new instance starts clean.
- Chat runs on the engine's async chat future, at LATE priority so moderation plugins cancel first.

Nothing in Feats touches a store, ref or component off its world thread, and nothing does file work on a world
thread except that one read at join.

## Files

Everything lives in `mods/Ryvrook_Feats/`.

| File | Written by | Notes |
|---|---|---|
| `Feats.json` | the engine config | written once at setup with defaults filled in |
| `Rewards.json` | `RewardService` | template written only when `RewardsEnabled` is on and the file is missing |
| `players/<uuid>.json` | `ProgressStore` | temp file then atomic move. A file that fails to parse moves to `<uuid>.corrupt-<time>.json`, only the newest kept. Since 1.7.2 also `FirstSeen`, `LastJoinDay` and `Met`, each optional |
| `server.json` | `WorldStore` | milestones, firsts and since 1.7.2 `totals`, same atomic write, corrupt copy kept the same way |
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
Feats, and adds the Factions tab when the jar is there. `FactionFeatsTest` holds the 25 keys Dominion 0.7.1
lists, the ten from 0.5.1 and fifteen more, so a rename on either side fails the build. New Rank, added in
1.7.3, reads `dominion:rank_changes`, which Dominion sends from 0.7.2, one per promotion or demotion credited to
the member. No feat reads `dominion:tutorial_done`.

SocialBridge and RestAPI read Feats through `FeatsApi` and list it as an optional dependency, Dominion does too.
They bind through `FeatsApi.onEnable`, which runs at the end of start, and let go in `onDisable`, which runs first
thing in shutdown. A `/plugin reload` of Feats leaves them holding the old `FeatsApi` class, so `Dependents`
reloads every enabled plugin that lists Feats two seconds after start, on the `Feats-dependents` thread under the
suite lock in the System property `ryvrook.dependents.lock`, the same class and lock TrackerAPI and Coinage use.
On a fresh boot none of them is enabled yet and the pass does nothing.

`DiscordVia` decides who posts unlocks to Discord. On `auto` Feats asks the plugin manager for
`Ryvrook:SocialBridge` before each webhook post and leaves the post to SocialBridge when it is there, which reads
`FeatUnlockedEvent` and its admin, hidden, rarity, category and description fields. Feats never names a
SocialBridge class.

A stop path must never load a sibling class for the first time. The plugin manager holds its write lock during
shutdown and the bridge class loader needs the read lock, which showed up as a 5 second hang. Everything
`shutdown` touches of TrackerAPI, Coinage and PlaceholderAPI was loaded in setup or start.

The 1.7.2 sibling bindings start from `SiblingLinks`, in start, and close in reverse order at shutdown. Each is
loaded by name only when `Siblings.present()` listed its sibling in setup, so no class of a missing sibling is
touched. `CoinageFeats` and `AtlasFeats` link directly, since both mods publish an API Feats can list as an
optional dependency and compile against. They follow the sibling with its enable and disable hooks, subscribe an
`EconomyListener` under owner `Feats` and an Atlas marker and session listener, and survive a reload of either
side. The Coinage binding reads the currency units live, counts amounts in whole coins and carries the minor unit
remainder per player and counter so small payments and interest add up, and a payment under one coin still counts
as the first coin received. The Atlas binding retries the session API every 2 seconds for 30 seconds after Atlas starts, since
Atlas publishes it a moment after the marker API. `ChronicleFeats` and `CrossModFeats` are reflective. Chronicle Stats and Dominion list Feats as an optional
dependency, so Feats cannot list them back without a cycle and their classes are not on its class path. Both find
the plugin in the plugin manager and build method handles through its own class loader, the way Chronicle binds
Feats. `ChronicleFeats` polls every 5 seconds, binds again when a reload gives a new plugin object, limits
window reads to one per player and key per 10 seconds, and reads the month window of days active from the poll
once a minute per player, since Chronicle closes a day without a change event. `CrossModFeats` answers the two Dominion facts the Coinage
binding asks for, allied and at war, and switches off with one warning when the API lacks a method. Every
sibling counter goes through the tracker, offline players through `addOffline`.

Each sibling has a link setting in `Feats.json`, `Tracker`, `Coinage`, `Dominion`, `Chronicle` and `Atlas`, read
once in setup through `FeatsConfig.link`. `Siblings.present(mode)` drops a sibling on `never` before it looks the
plugin up, so nothing of it is loaded, and warns once for a missing sibling on `always`. TrackerAPI's `always`
warning comes from `TrackerSwitch`. The coin reward link reads the same set, so `Coinage` on `never` also stops
coin rewards.

`Sibling.minimum` names the oldest version each binding is written against. `Siblings.present` compares the
loaded plugin's manifest version with it and leaves an older sibling out with one warning, so its tab never
appears with counters that cannot move. A sibling that sets up after Feats has no version yet and is kept. The
bindings still check what they need at bind time, such as `AtlasApi.version()` 3 for marker events.

PlaceholderAPI is found by class name and registered in start. `FeatsExpansion` never names a PlaceholderAPI
type outside its nested `Hook`.

## Server totals

`WorldStore` keeps the totals in a map of `AtomicLong` by key, safe from any thread, and marks itself dirty on a
change. `blocks_broken`, `kills`, `kills_boss` and `deaths` are mirrored by the tracker from the player counters
of the same name (`StatKeys.MIRRORED_TOTALS`), `chunks_generated`, `players_known`, `peak_online` and
`server_days` are raised by `WorldHooks`, and `installed_at` is written once at the first start. Nothing writes the
file on the hot path. The autosave task saves `server.json` on Feats-IO when it is dirty, and shutdown saves it
after the players. `ServerTotals` off stops every add, and `FullHouse` is read when Full House is checked, so both
apply on reload. A mirrored add checks the milestones on that key straight away and names the player whose add
crossed the line. `FeatsTracker.checkTotals(by)` is the same check for the totals the hooks raise. `WorldHooks` calls it after a
peak or day count rose, once per thousand generated chunks and at a first join, so Full House, First Year,
Chunk Factory and the two player count milestones are recorded as well.

## Meta counters

`feat_points`, `category_done:<TAB>` and `platinums_done` are plain counters in the player file, so the ladders,
the closers and Grand Slam are ordinary feats on them. `afterUnlocks` runs after fresh personal unlocks, never on
a plain increment. It adds to `feats_unlocked` and `feat_points`, counts only the tabs the unlocks sit in and
raises `category_done` when every feat of the tab but the closers is unlocked, and after a Platinum counts the
Platinum pool for Grand Slam. The pool leaves out the closers and the feat count ladders. World and first feats
add to `feat_points` only. A revoke lowers the points and calls `lowerMeta`, which drops any flag that no longer
holds. `syncMeta` runs once per load from disk before the catch up and sets all three from the unlocks in the
file, which is how a file from before 1.7.2 gets its points and a tab finished earlier gets its closer at the
next join.

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

The tests run without a server. The server jar is only on the test classpath for bson and the logger. Feats
compiles and tests against `TrackerAPI-1.0.0.jar`, `Coinage-api-1.2.0.jar` and `Atlas-1.9.0.jar` from the sibling
checkouts, the oldest version of each it supports so nothing newer is used by accident, see `siblingJar` in
`build.gradle.kts`. Chronicle Stats and Dominion need no jar, and their bindings are tested over fakes.

For a headless boot I copy the jars into a fresh folder under `mods/`, write an empty `permissions.json` and run
the server with `--auth-mode offline --disable-sentry --bind 127.0.0.1:<port>`, feeding console commands through
a fifo. TrackerAPI's `docs/DEVELOPMENT.md` has the full recipe. The lines to look for are the tracking path line,
`Feats loaded 455 feats worth 20735 points, plus 30 world feats` with the default packs, `Packs on: [SOCIAL,
CALENDAR]`, the Factions line with Dominion, one `<plugin> is installed, so its <n> feats are in` line per other
sibling and one line per sibling binding that linked. With TrackerAPI the bridge logs `Feats is linked to
TrackerAPI <version> with 34 listeners`. The boot also logs `Skipping pack at Ryvrook_Feats: missing or invalid
manifest.json`, which is the asset scan looking into the data folder and is harmless.
