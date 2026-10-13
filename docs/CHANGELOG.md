# Changelog

Every notable change to Feats is recorded here. The format follows Keep a Changelog and the project uses
semantic versioning.

## 1.3.0 - 2026-10-13

Needs the Hytale 0.7.0 release server or newer. 218 personal feats worth 7,870 points in nine categories, plus
15 world feats in a new World tab that never count toward the totals.

### Added

- After upgrading, run `/feats admin scan all` once. It raises counters from what the game already recorded and records firsts and milestones for the earliest existing unlocks, so the next live unlock cannot claim a first that someone earned earlier.

- A World tab with nine world milestones and six server firsts. The milestones are Lights On, Full Steam, Engine
  Room and Pressure Rising for the Goblin Lab, and Scrap Yard, Redline, End of the Chapter, Open Gates and
  Uprooted for the first Gobliterator, hard mode Gobliterator, Chapter I completion, Ancient Gateway and Hedera
  on the server. The Platinum firsts are Pioneer, Trailblazer, Historian, Keeper of Memories, Gatewright and
  Party Crasher.
- World milestones give their points only to the player who raised them. Every other player sees them as
  achieved in the World tab and gets a quiet chat line, and players who were offline get it on their next join,
  three lines at most and then a summary line.
- Server firsts go to the one player who got there first and are held forever. Two players finishing together
  cannot both claim one.
- The Goblin Lab milestones follow the game's own server wide flags, read every 5 seconds. The player who used a
  Goblin Lab device in the last 60 seconds is credited, and nobody when no one did.
- `server.json` in the data folder with every reached milestone and claimed first, the time and the player.
- `/feats admin scan <player|all> [--dry-run]`, guarded by `ryvrook.feats.command.feats.admin.scan`. It raises
  counters from discovered zones, recorded Memories, death markers and the first spawn, all of which Hytale
  itself records, and never reads inventories. Death markers give a lower bound, since the engine keeps at most
  five per world. Respawn points, discovered instances and known recipes are not read.
- The scan only raises counters, so a second run changes nothing. Online players unlock at once, offline players
  are updated on the Feats thread and unlock at their next join, and a player Feats has never seen gets a new
  file. A scan of everyone runs in batches of 25 and backfills milestones and firsts to the player with the
  earliest existing unlock.
- `milestones` and `firsts` in `export/feats.json`, each record with `at`, `by`, `byName`, `featName` and
  `tier`.
- `FeatsApi.milestones()`, `FeatsApi.firsts()`, the `WorldRecord` type and a `scope` on `FeatInfo`.
- The placeholders `%feats_first_<featId>%` for the holder of a first and `%feats_milestone_<featId>%` for
  whether a milestone is reached.

### Changed

- The All feats and Unlocked tabs list personal feats only. Totals, completion, the feats unlocked counter,
  meta feats and rarity count personal feats only, while points earned from World feats are added to a
  player's points.
- The start up log line reads `Feats loaded 218 feats worth 7870 points, plus 15 world feats`.
- Player files keep a `Shared` list of the milestones that player shares in.

### Fixed

- The zone feats could not be completed, because the default world names only five zones on discovery.
  Sightseer, Cartographer, Pathfinder and Master Explorer now ask for 2, 3, 4 and all 5 zones instead of 5, 10,
  20 and 35. New Horizons stays at 1 and every tier is unchanged. Players who already meet a new target unlock
  it on their next join.

## 1.2.1 - 2026-10-13

### Fixed

- Loading a new Feats jar with a different file name while the server runs no longer fails with a duplicate asset pack. Feats now clears the engine registration left behind by the old jar before it registers its own, and logs at start whether its UI pack is registered.

## 1.2.0 - 2026-10-13

Needs the Hytale 0.7.0 release server or newer. 218 feats worth 7,870 points in nine categories, 15 of them
hidden.

### Added

- The Chapter I endgame. Chapter Closed for the Dungeon Completion Portal, Under Pressure for the key to The
  Deepworks, Bigger Pockets for the largest backpack, Scrap Metal for five Gobliterator defeats and Weed Killer
  for The Hedera in The Dread Wade.
- Fragments of Orbis for entering all five Fragments the Ancient Gateway can reach, Gatekeeper for building an
  Ancient Gateway and Overtime for repairing a Breach Console.
- Goblinologist for one of every kind of goblin, Know the Void for every kind of Voidspawn, Keystone for the
  Void anomaly keystone, Loremaster for all twelve lore notes and Gallery Opening for one of each goblin
  painting.
- Archivist and Living Memory at 50 and 100 Memories.
- Adamant Wall for a full set of adamantite armour and Adamant Edge for an adamantite weapon.
- Four new hidden feats, among them a deathless run of The Deepworks and a hard mode Gobliterator fight with no
  armour.
- Optional rewards from `Rewards.json`. Commands, permissions, a group and a message per feat, per tier, per
  points total and per unlock count, given once per player and remembered through a reset. Off by default
  behind `RewardsEnabled`.
- Admin commands `view`, `grant`, `revoke`, `reset`, `setstat`, `reload` and `rates` under `/feats admin`,
  guarded by the generated nodes `ryvrook.feats.command.feats.admin` and
  `ryvrook.feats.command.feats.admin.<sub>`, both of which a sender needs. They work on offline players, and
  reload applies `Feats.json` without a restart except the `AutosaveSeconds` and `ExportSeconds` timers.
- Player commands `/feats view`, `/feats compare`, `/feats title`, `/feats notify`, `/feats recent` and
  `/feats share`.
- Rarity, the share of players who own each feat, in the browser, on the unlock chat line of rare feats, in
  `/feats share` and in the export.
- Titles. Players can wear a Gold or Platinum feat in front of their chat lines when no other plugin formats
  chat.
- A big title in the middle of the screen for Platinum unlocks and a configurable unlock sound.
- Creative mode pausing and a list of disabled worlds where every counter stops.
- A Discord webhook for high tier unlocks, off until a URL is set.
- PlaceholderAPI placeholders under the `feats` expansion when PlaceholderAPI is installed.
- A public API in `dev.ryvrook.feats.api` and the `FeatUnlockedEvent` on the server's event bus.
- New config keys `RewardsEnabled`, `IgnoreCreative`, `DisabledWorlds`, `PausePlaytimeWhenDisabled`,
  `AllowViewOthers`, `BigTitleMinimumTier`, `UnlockSound`, `Titles`, `ShowRarity`, `RarityMinimumPlayers`,
  `RareBelowPercent`, `DiscordWebhookUrl` and `DiscordMinimumTier`.
- `rarityPlayers` and `rarity` fields in `export/feats.json`.

### Changed

- A boss kill credits every player who hit the boss in the last 60 seconds, not only the killing blow.
- Flip the Switch now asks for the hard mode button The Deepworks actually uses, in place of the old lever.
- Full Spectrum asks for every metal that world generation places, without mithril, and drops to Gold.
- Portal Hopper asks for three kinds of portal world and drops to Silver.
- The seven per ability cast feats and Into the Breach drop to Bronze. Ironclad, Void Walker and Duke Dethroned
  drop to Silver. Deep Dive rises to Gold.
- Well Fed, Glutton and Potion Addict move to Survival, Movie Night moves to Crafting, Duke Dethroned moves to
  Combat and Goblin Slayer moves to Chapter I as the first rung of the goblin ladder.

### Removed

- Mithril Miner, Mithril Smith, Onyx Eye, Prismatic and Silver Bullet are parked, because mithril, onyxium and
  prisma ore are never generated and werewolves never spawn in 0.7.0. They keep their ids and return once the
  content reaches the world.
- Fire Drill, Return to Sender, Shield Breaker, Pickpocketed and Coffer Cracker are folded into Goblinologist.

## 1.1.0 - 2026-10-12

Needs the Hytale 0.7.0 release server or newer. 207 feats worth 7,395 points in nine categories.

### Added

- A Chapter I tab with 71 feats covering goblin breaches, portal worlds, the Gobliterator, the Feastmaster, hunt
  sites, Memories, runes, gadgets and the new weapon families.
- Tracking for ability casts and interactions, items obtained through crafting, looting and trading, portal
  worlds entered, Memories recorded, meals eaten and potions drunk.
- `export/feats.json`, a leaderboard file with the top 100 players and the last 50 unlocks, read by the Atlas
  live map.
- Catch up on join, which unlocks feats whose counters are already met when a player's progress loads.
- The mod icon and issue templates.

### Changed

- Targets the Hytale 0.7.0 release and replaces the chest icon the game removed.
- Saves, the export and other file work run on a dedicated Feats thread instead of the game thread, and a
  failed save is retried.
- Stat tracking is harder to farm. Placed blocks, dropped items and chest round trips are remembered across a
  relog, and counters no feat reads are dropped from player files.
- `/feats top` answers from the export's ranking, or from the player files at most once every 10 seconds when
  the export is off.

### Fixed

- Crashes in event handlers.
- Only the newest corrupt copy of each player file is kept.

## 1.0.0 - 2026-10-12

Needs the Hytale 0.6.8 server.

### Added

- First release. 136 feats in eight categories, lifetime statistics, a points leaderboard, unlock toasts with a
  sound and the `/feats` browser.
- The feats commands are open to every player.
