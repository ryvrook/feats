# Changelog

Every notable change to Feats is recorded here. The format follows Keep a Changelog and the project uses
semantic versioning.

## 1.7.3 - 2026-10-16

Needs the Hytale 0.7.0 release server or newer. With every pack and every sibling mod installed there are 528
feats worth 23,440 points.

### For players

- New Rank in the Factions tab, Bronze, for being promoted or demoted in your faction. It reads
  `dominion:rank_changes`, which Dominion Factions sends from 0.7.2. The Factions tab has 30 feats worth 900 points.

### For admins

- Four new settings, `Coinage`, `Dominion`, `Chronicle` and `Atlas`, each `auto`, `always` or `never` like
  `Tracker`. `auto` links when the mod is installed and new enough, as before. `always` also warns at start when
  it is missing. `never` leaves its feats, bindings and coin rewards out. Read at start.
- Coin rewards now link to Coinage only when it is 1.2.0 or newer, the same check as the Coinage tab.
- A `/plugin reload` of TrackerAPI 1.0.3 or newer no longer logs the warning that TrackerAPI was unloaded. Feats
  logs one INFO line that TrackerAPI reloads it by itself, and warns only when TrackerAPI is still gone after 30
  seconds.
- `/feats admin status` prints the Feats version and one line per suite mod, TrackerAPI, Coinage, Dominion
  Factions, Chronicle Stats, Atlas, SocialBridge, RestAPI and Lullaby, with its version, the link and its setting.

## 1.7.2 - 2026-10-16

Needs the Hytale 0.7.0 release server or newer. There was no 1.7.1, the version went from 1.7.0 straight to 1.7.2.
A plain server goes from 327 feats worth 15,075 points to 445 worth 20,445, and to 455 worth 20,735 with the
default packs. With every pack and every sibling mod installed there are 527 feats worth 23,430 points.

### For players

- 118 new feats on every server, 105 in the nine tabs and 13 for the endgame. Exploration gains 13 for biomes,
  weather, the moon, the Forgotten Temple, respawn points and dungeon checkpoints and health packs. Mining gains 6
  for tools worn out, repairs and tree species, Building 2 for kinds of block placed, Crafting 5 for knowledge
  recipes and full armour sets. Combat gains 21 for damage kinds, blocking, projectiles fired, kills underwater,
  gliding, mounted, at night, under a Blood Moon, on low health and from 50 metres, multi kills, close calls, hard
  landings and co-op bosses. Survival gains 16 for nights slept through, game days alive, join streaks, join
  hours, oxygen, Mana and hardcore lives. Nature gains 19 for seeds, watering, fertiliser, coops, capture crates,
  fishing traps, every fish, mounts, minecarts, every tameable animal and creature kinds. Chapter I gains 12 for
  world events, portal worlds, merchants, the Kweebec Elder and the Gobliterator on hard mode without dying or
  within 20 minutes. Misc gains 11 for seats, jumps, rolls, climbing, sprinting, crouching, hub referrals and
  permission groups.
- The endgame. Each of the first eight tabs ends with a hidden Platinum closer that unlocks once every other feat
  of the tab is yours. Misc ends with Grand Slam for every Platinum, and gains Ten Thousand and Twenty Thousand
  points and Grandmaster and All of It at 350 and 425 feats.
- Five packs, each a tab of its own. Social, 6 feats for time with other players, and Calendar, 4 feats on real
  dates, are on by default. Hardcore, 2 feats, turns on with hardcore lives. PvP, 3 feats, and Grind, 3 very long
  ladders, are off. Last Life and Ten Days stay in Survival whatever the Hardcore switch says.
- Sibling tabs. A Coinage tab of 24 feats worth 720 points with Coinage, a Chronicle tab of 6 feats worth 300
  points with Chronicle Stats, 2 Exploration feats with Atlas, 14 more Factions feats with Dominion, 29 worth 890
  points in all, and 3 cross mod feats with both Coinage and Dominion.
- 12 new World records. Nine milestones read the new server totals, such as A Million Blocks, Full House and First
  Year, and three firsts follow Leviathan, Bestiary and Grand Slam. The World tab has 30 records.
- A banner at the top of the screen for six seconds on a Platinum, a feat owned by under 1 percent of players, a
  tab closer, a server first or a milestone, with the feat, its description and why it is there. Other players
  online see a milestone or first with the holder's name. At most three a minute, the rest stay in chat.
  `/feats notify off` mutes it with the toast. Feats that always get the banner show a dot after their tier.
- The Statistics tab has 44 rows, up from 35, with the join streak, nights slept through, the longest life in
  game days, biomes, creature kinds, jumps, tools worn out, players met and points.

### For admins

- New `Feats.json` keys, `PackSocial` (true), `PackHardcore` ("auto"), `PackPvp` (false), `PackCalendar` (true),
  `PackGrind` (false), `Banner` ("RARE"), `BannerSeconds` (6), `BannerRareBelowPercent` (1), `BannerBroadcast`
  (true), `BannerSound` (`SFX_Event_Chapter_1_Complete_Sting`), `BannerParticles` (false), `FullHouse` (20),
  `CalendarZone` ("") and `ServerTotals` (true). The five pack keys need a restart or `/plugin reload`, the rest
  apply on `/feats admin reload`.
- `/feats admin totals` lists every server total and each total milestone with its value, target and whether it
  is reached.
- `/feats admin reload` adds a line with the packs that are on, so a pack change that has not taken effect yet is
  easy to spot.
- `/feats admin grant` of a feat whose pack is off or whose sibling is missing says why, such as
  `pack GRIND is off`, instead of saying the feat does not exist.
- `server.json` keeps the server totals, written with the autosave and at shutdown.
- The start log adds `Packs on`, one line per sibling with the feats it brings, and one line per sibling binding
  that links. Biome names and instance discovery keys are logged the first time each is seen.
- Placeholders `%feats_streak%`, `%feats_best_streak%`, `%feats_server_<key>%`, `%feats_pack_<name>%` and
  `%feats_siblings%`.
- `export/feats.json` gains `packs`, `siblings`, `totals` and `feats`, and `banner`, `pack` and `requires` on the
  feat rows, recent unlocks and world records, see the README.
- The optional dependency on Atlas is now 1.9.0 or newer.

### Upgrading

- Swap the jar. `Feats.json` gains the new keys with their defaults and every setting already in it
  stays.
- Player files gain `FirstSeen`, `LastJoinDay` and `Met`. Old files load unchanged, get the first two at the
  player's next join and `Met` once they share the server with someone. `FirstSeen` for a player who joined
  before is that join, so Anniversary counts a year from then.
- `server.json` gains `totals`. The install date behind First Year and Server Birthday is the first 1.7.2 start,
  and the players who have joined total counts first joins from then on.
- The join streak starts at one at each player's first join after the upgrade.
- Players who already meet a new target, such as a tab they have finished or a points total, get those feats at
  their next join. The first five get the full toast and the rest unlock quietly with one summary line.
- Sibling mods are read at start. After adding Coinage, Chronicle Stats, Atlas or Dominion, restart or run
  `/plugin reload Ryvrook:Feats`. The 13 new Factions feats need Dominion 0.7.1 to move.

### For plugin authors

- `FeatsApi.API_VERSION` is 3.
- `FeatsApi.packs()`, the packs that are on, `siblings()`, the sibling plugins found at start, and
  `serverStat(key)`, a server total.
- `FeatInfo` gains `banner`, `pack`, the pack name or null, and `requires`, the plugin names the feat needs. The
  older constructors still work.
- `FeatUnlockedEvent` gains `banner()`. The 1.7.0 constructor still works and fills it with false.
- Category names gain Coinage, Chronicle, Social, Hardcore, PvP, Calendar and Grind, in `FeatInfo`, the event and
  the export.

### Other changes

- The TrackerAPI bridge takes 34 event kinds, up from 19. Every new kind ships in TrackerAPI 1.0.0, so that stays
  the minimum. Vitals, game days, respawn points, hardcore lives, known recipes, permission groups, referrals, the
  calendar, players met and the server totals are read by Feats on both paths. Rolls, mantles, double jumps,
  seats, shield blocks, repairs, capture crates, trap bait, Memories only a kill unlocks, void stages, late portal
  exits and merchants have no TrackerAPI record, so their feats only move with `Tracker` on `never`.
- The tracking hooks are split into three hook sets, combat, world and items, so the plugin class does not change
  when a hook is added. A set that fails is logged and the others carry on.
- The points total, the tab closer flags and the Grand Slam flag are counters in the player file, raised after
  each unlock and brought in line once per load, so a revoke lowers them and an old file gets them at its first
  join.

## 1.7.0 - 2026-10-16

Needs the Hytale 0.7.0 release server or newer. The feats are the same as in 1.6.0.

### For admins

- `DiscordVia` in `Feats.json`, `auto` by default. On `auto` Feats stops posting unlocks through
  `DiscordWebhookUrl` while SocialBridge is installed and SocialBridge posts them instead, so nothing is posted
  twice. `webhook` keeps the Feats webhook either way, `off` posts nothing from Feats. The log says so once at start
  when a webhook is set and SocialBridge takes over.
- A `/plugin reload` of Feats now reloads Dominion Factions, SocialBridge, RestAPI and any other enabled plugin that
  lists Feats, two seconds after Feats started, so they link to the new instance.

### Upgrading

- Swap the jar. The new key is added to `Feats.json` with its default and every player file carries on.
- With SocialBridge installed and a webhook set, unlocks move from the webhook to SocialBridge's channel. Set
  `DiscordVia` to `webhook` to keep the old posts.

### For plugin authors

- `FeatsApi.onEnable`, `onDisable`, `removeHooks` and `isEnabled`. Enable hooks run at the end of start, at once
  for a hook added later, disable hooks first thing in shutdown.
- `FeatsApi.top(limit)` returns the points leaderboard as `LeaderboardEntry` rows, the same order as `/feats top`.
- `FeatUnlockedEvent` gains `admin()`, `hidden()`, `rarity()`, `category()` and `description()`. The old
  constructor still works and fills them with false, -1 and empty.
- `FeatsApi.API_VERSION`, 2 for this release.

## 1.6.0 - 2026-10-15

Needs the Hytale 0.7.0 release server or newer. 327 feats as before, plus 15 in a Factions tab when Dominion
Factions is installed.

### For players

- A Factions tab with 15 feats worth 460 points, there only when Dominion Factions is installed. They read the
  `dominion:` counters Dominion already sends, such as claims, enemy kills, sieges won and lost, missions and
  coins put in the faction bank. Siege and mission credits for members who were offline now reach them at their
  next join instead of being lost.
- Armorer is spelled Armourer to match the other armour feats. Touch Grass and Kweebec Grudge lost their
  questions, and Pick Collector says "every material", since wood is not a metal.

### For admins

- Rewards in `Rewards.json` can pay `Coins`, with an optional `Currency`, into the player's Coinage account.
  Coinage is a new optional dependency. Coins that come due while Coinage is stopped or reloading wait for it,
  and a Feats shutdown meanwhile keeps them in `CoinsWaiting.json`.
- A reload of TrackerAPI 1.0.3 or newer no longer logs a reminder to reload Feats, since that TrackerAPI does it.
- `RarityMinimumPlayers` reads as at least 1 and `RareBelowPercent` as 0 to 100.

### Upgrading

- Swap the jar. Nothing in `Feats.json` changed and every player file carries on.
- After adding Dominion Factions, restart or run `/plugin reload Ryvrook:Feats` so the Factions tab appears.

### Other changes

- `FeatsApi.addStat` takes offline players. The add waits in memory and the file thread writes it, where feats
  unlock quietly and rewards wait for the next join. A player Feats has never seen gets nothing. An API object
  kept from before a Feats reload answers false, so ask `FeatsApi.get()` again.
- A `hidden` flag on every milestone and first in `export/feats.json`, and `hidden()` on `WorldRecord`.
- The export and the rarity scan only parse player files saved since their last read. With 2,000 player files a
  read with nothing changed takes about 12 ms instead of 170 to 300 ms.
- The stale asset pack fix matches the one in Coinage. It compares jar paths without mixing file systems, drops
  a jar pack directly while the asset monitor runs and lets go of the pack at shutdown. The engine still prints
  `Failed to reload plugin Ryvrook:Feats!` on a reload and then enables Feats with its pages.
- Without TrackerAPI, dropping 256 cheap items no longer made Feats forget the drop of a counted item.
- `%feats_rarity_<featId>%` ignores the case of the id, like the other feat placeholders.

## 1.5.1
- Queued bench crafts count one run per craft event. The engine fires one event per finished unit of a queued batch and stamps each with the whole batch quantity, so a batch of a hundred counted as ten thousand crafts. Instant crafts are unchanged.

## 1.5.0 - 2026-10-14

Needs the Hytale 0.7.0 release server or newer. No feats changed.

### Added

- Works with TrackerAPI. When TrackerAPI 1.0.0 or newer is installed, Feats takes block, item, combat,
  movement, portal, zone, Memories, taming, ability and chat counts from it and registers none of its own
  tracking hooks. Without it Feats tracks on its own as before.
- `Tracker` setting in `Feats.json`, `auto` by default. `always` uses TrackerAPI only and `never` ignores it.
- One line in the server log at startup saying which way Feats tracks, and a reminder to reload Feats when
  TrackerAPI is reloaded.

### Upgrading

- Nothing to do. Every counter carries on in the same player files.
- With TrackerAPI, items taken back out of a chest are matched against the last 10 minutes of what the player
  put away, where Feats used to remember it for as long as the player kept it there.

## 1.4.0 - 2026-10-14

Needs the Hytale 0.7.0 release server or newer. 327 personal feats worth 15,075 points in nine categories, plus
18 world feats in the World tab that never count toward the totals.

### Upgrading

- Players who already pass a new target get the feat on their next join through the catch up, the same as in
  1.3.0. The first 5 get the usual toast and the rest unlock quietly with one summary line.
- The new ladder steps read counters Feats has kept since 1.0, so a player with 100,000 blocks broken gets
  Million Mark on the way there and Deep Vein at 25,000 ore without any scan. `/feats admin scan all` still only
  reads zones, Memories, death markers and the first spawn, and it records the new firsts for the earliest
  existing unlocks, so run it once after upgrading.

### Added

- 109 personal feats worth 7,205 points, 14 of them hidden.
- Exploration. Circumnavigator at 500 kilometres and Vault Breaker at 2,500 chests. Sea Legs for a kilometre
  swum, Wingspan for 5 kilometres on a Scrap Glider, Bedrock Bound for reaching the bottom of the world and
  Head in the Clouds for standing at the top.
- Mining. Million Mark, hidden, at 1,000,000 blocks and Deep Vein at 25,000 ore blocks. A second step for every
  metal with Copper Magnate, Ironbound, Gold Standard, Silver Service, Cobalt Crown, Thorium Reactor and
  Adamant Heart. Pick Collector for a pickaxe of every metal from wood to adamantite.
- Building. Megastructure, hidden, at 1,000,000 blocks placed, Light of Orbis, Festival of Lights, Bed and
  Breakfast, Show Home, Industrial Park and Grand Vault. Glazier for glass, Stonemason for stone and brick,
  Timber Frame for wood and Arborist for saplings.
- Crafting. Assembly Line, Master Toolmaker, Arms Dealer, Quartermaster, Foundry, Banquet, Apothecary and Master
  Carpenter at the top of the craft ladders. Copper Kit, Cobalt Clad and Thorium Plate for full armour sets and
  Sword Collector for a sword of every metal from copper to adamantite.
- Combat. Mythic, Marksman, Lich Bane, Trork Scourge, Outlander Outlaw, Hive Breaker, Feran Hunter, Elemental
  Master, Boss Hunter, Apocalypse and Immortal. Rampage for 50 defeats without dying. The hidden Gladiator,
  Warmonger, Untouchable and Overkill.
- Survival. Fixture, Part of the Furniture, Death Wish, Long Winter, Bench Veteran, Bottomless and Potion
  Problem. Marathon for 8 hours in one session. The hidden Old Timer, Respawn Point, Hot Stuff and Lava Bath.
- Nature. Timber, Clear Cut, Breadbasket, Granary, Harvest Moon, Dragon's Hoard, Sea Legend, Abattoir and Apex
  Predator, plus the hidden Old Growth. Crop Rotation for all 14 crops, Full Net for 15 kinds of fish, Tamer for
  taming an animal and Saddle Up for a kilometre on a mount.
- Chapter I. Goblin Bane, Arachnid Apex, Void Reaver, Grand Archmage, Rune Lord, Frequent Flyer, Breach Veteran,
  Deepworks Regular and Scrap Dealer. A second step for every ability with Inferno, Tremor, Berserker,
  Plaguebringer, Tempest, Hooked and Overcharged. Rune Hoard, Scrap Baron, Silk Merchant, Key Ring, Kenolite
  Quarry, Crystal Cavern and Steelworks. The hidden Hard Habit and Speedrunner.
- Misc. Loudspeaker at 10,000 chat messages, Icon at 150 feats and the hidden Completionist at 250.
- Three Platinum server firsts. Founder for the first player to unlock 100 feats, Warpath for the first to
  defeat 5,000 creatures and Surveyor for the first to discover all 5 zones.
- New counters for 1.4.0 feats. Glass (and window), stone, wood and sapling placements, fire and lava deaths, swim, glide and ride distance, reaching the bottom and the top of the world, animals tamed, best kill streak, biggest hit, longest session, untouched boss kills and Gobliterator kills within 20 minutes of entering The Deepworks.
- Stats tab rows for distance swum, glided and ridden, best kill streak, biggest hit, animals tamed and longest session.
- Marksman and Berserker use the ids `master_marksman` and `berserker_rage`, because `marksman` and
  `berserker` already belong to Charged Up and Seeing Red.

### Changed

- The start up log line reads `Feats loaded 327 feats worth 15075 points, plus 18 world feats`.
- Tracking is faster and allocates nothing on the busy paths. A block break, kill or pickup now takes about 90 to 110 ns instead of 260 to 850 ns, measured over a million events each.
- Key lists for block, item and creature ids are worked out once per id and remembered.
- One event updates every counter it touches and checks the feats that read them in a single pass, under one lock on the player.
- Counters no longer box a new number on every bump.
- The once a second distance and Memories pass runs as one task per world instead of one per player.
- Session length is saved every five minutes while online, on leaving, and when the server stops or Feats reloads.
- Builds against server 0.7.1. gradle.properties still pinned 0.7.0 before.
- Big unlock titles use the event title style the 0.7.1 engine asks for, the old switch is due to be removed.

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
