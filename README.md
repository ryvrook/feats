# Feats

Achievements and lifetime statistics for Hytale servers.

Feats is a server plugin that adds 327 achievements, called feats, across nine categories. Each feat has a
rarity tier worth points, and unlocks arrive as in game toasts with a sound and a chat line. The `/feats`
command opens a browser with progress for every feat, a Statistics tab with lifetime totals and a points
leaderboard. Everything runs on the server, so it works in singleplayer worlds and on dedicated servers, and
players join with an unmodified client.

## Features

- **327 feats** with progress bars, from "Break your first block" to "Travel 500 kilometres".
- **Nine categories**, namely Exploration, Mining, Building, Crafting, Combat, Survival, Nature, Chapter I and Misc.
  A tenth, Factions, with 15 feats worth 460 points, appears when Dominion Factions is installed.
- **Four tiers**. Bronze is worth 10 points, Silver 25, Gold 50 and Platinum 100, for 15,075 points in total.
- **Hidden feats** show as `???` until you earn them.
- **Multi step feats** such as Goblinologist, which asks for one of every kind of goblin, and Full Net, which
  asks for 15 different kinds of fish.
- **Long ladders** that carry every counter on to Platinum, such as 25,000 kills, 500 kilometres travelled
  and a million blocks broken.
- **Meta feats** that unlock at 10, 25, 50, 100, 150 and 250 feats earned.
- **Unlock toasts** using the game's own notification system and the Memories unlock sound, plus a big title in
  the middle of the screen for Platinum feats.
- **Chat announcements** of Silver and better unlocks to other online players, with a configurable minimum tier.
- **Rarity** on every feat row, such as "12% of players", with rare feats marked as rare.
- **Titles**. Wear a Gold or Platinum feat in front of your name in chat.
- **Rewards**, off by default. Run commands, add permissions or a group and send a message when a feat unlocks.
- **Statistics tab** with time played, distance, blocks, ore, kills, deaths, damage, chests, fish and more.
- **Leaderboard** with `/feats top`, covering everyone who has played on the server, online or not, plus
  `/feats recent`, `/feats compare` and `/feats view`.
- **World milestones and server firsts** in a World tab, such as restoring power to the Goblin Lab or being
  the first on the server to defeat the Gobliterator.
- **Admin commands** to grant, revoke, reset, set counters and reload the config without a restart.
- **A scan for existing worlds** that catches counters up from what Hytale already recorded, for players who
  played before Feats was installed.
- **Leaderboard export** to a JSON file that other tools can read.
- **Discord webhook**, off by default, that posts high tier unlocks to a channel.
- **PlaceholderAPI support** when the PlaceholderAPI plugin is installed.
- **Public API** and an unlock event for other plugins.
- **No client setup**. The UI files ship inside the jar as an asset pack.

## Exploration

The default world names five zones on discovery, Emerald Wilds, Howling Sands, Whisperfrost Frontiers, the
Devastated Lands and the Oceans. The zone ladder follows that since 1.3.0. New Horizons asks for 1 zone,
Sightseer for 2, Cartographer for 3, Pathfinder for 4 and Master Explorer for all 5. The tiers stay Bronze,
Bronze, Silver, Gold and Platinum. Players who already meet a lower target get the feat on their next join.

Version 1.4.0 adds Circumnavigator at 500 kilometres, Vault Breaker at 2,500 chests and four feats for getting
around. Sea Legs asks for a kilometre swum, Wingspan for 5 kilometres on a Scrap Glider, Bedrock Bound for
reaching the bottom of the world and Head in the Clouds for standing at the top.

## Chapter I

The Chapter I tab holds 105 feats worth 4,650 points for the content that arrived with Hytale 0.7.0. Version
1.2.0 adds the endgame, from the Ancient Gateway to the Gobliterator on hard mode, and 1.4.0 carries the
Chapter I ladders further.

- **Goblins and the Void.** A goblin ladder from Goblin Slayer at 50 defeats up to Goblin Warlord, Goblinologist
  for one of every kind of goblin, Know the Void for every kind of Voidspawn, spiders, Void Spectres, Void
  anomaly crystals and the anomaly keystone.
- **Bosses.** Defeat the Goblin Feastmaster, the Gobliterator, the Voidtaken bear, the Revenant Guardian and The
  Hedera in The Dread Wade, and the Gobliterator five times over. Every one of them counts toward Giant Killer
  and Boss Rush.
- **The finale.** Craft the key to The Deepworks, step through the Dungeon Completion Portal after the
  Gobliterator falls and trade two Gobliterator Cores for the largest backpack.
- **Hard mode and challenges.** Hidden feats for pressing the hard mode button, beating the Gobliterator on hard
  mode, beating it without dying in that run of The Deepworks and beating it on hard mode wearing no armour.
- **Runestones and abilities.** Craft every Ability and Modifier Runestone, cast abilities thousands of times
  and master each of the seven abilities.
- **Gadgets and gear.** Craft the Scrap Drill, Scrap Glider, Rocket Boots, Hookshot, Spyglass, Bangsticks,
  repair kits and ten kinds of weapon.
- **Portal worlds and Fragments.** Enter the Goblin Breach, The Deepworks and three different kinds of portal
  world, repair a Breach Console for more time, and enter all five Fragments the Ancient Gateway can reach.
- **Dungeons, loot and lore.** Use the Goblin Lab bench, collect Voidhearts, Runestone Shards, Scraps, Coffer
  Keys and Spider Silk from drops and chests, and collect all twelve lore notes.
- **Memories.** Record your first Memory and keep going to 10, 25, 50 and 100.
- **Blocks.** Mine Kenolite and Void crystal, decorate with goblin furniture and metal blocks, and hang one of
  each goblin painting.

The Statistics tab has five rows for Chapter I. They are Abilities cast, Portals entered, Memories recorded,
Meals eaten and Potions drunk.

Memories are a server wide race in Hytale 0.7.0. Once any player hands a Memory in, nobody else on the server
can record that one. Solo and in co-op this is fair, but on a busy shared server a player may never reach the
higher Memory feats such as Living Memory at 100.

## New in 1.4.0

Version 1.4.0 adds 109 personal feats worth 7,205 points and three server firsts. Most of them carry an
existing ladder on to a new Gold or Platinum step, a handful are sets that ask for one of everything, and a few
follow counters that are new in this release.

- **Longer ladders.** Most lifetime totals get a further step. Circumnavigator at 500 kilometres, Million Mark
  at a million blocks broken, Megastructure at a million placed, Assembly Line at 50,000 crafts, Mythic at
  25,000 defeats, Apocalypse and Immortal at a million damage dealt and taken, Clear Cut and Old Growth for
  logs, Harvest Moon for crops and Dragon's Hoard at 100,000 items picked up.
- **Ore and metal.** Every metal gets a second step, from Copper Magnate at 500 copper ore to Adamant Heart at
  100 adamantite, and Deep Vein asks for 25,000 ore blocks.
- **Building and crafting.** Larger targets for torches, lanterns, beds, furniture, chests and benches, plus
  Glazier for glass, Stonemason for stone and brick, Timber Frame for wood and Arborist for saplings. The craft
  ladders for tools, weapons, armour, bars, meals, potions and furniture each get a top step.
- **Combat.** Further steps for ranged defeats, undead, Trorks, Outlanders, Scaraks, Feran, golems and spirits
  and bosses. Rampage asks for 50 defeats without dying, and the hidden Overkill, Untouchable, Gladiator and
  Warmonger wait for the bold.
- **Survival.** More sessions, more hours, more deaths, more nights, more meals and more potions. Marathon asks
  for 8 hours in one session, and two hidden feats wait for anyone who burns to death or dies in lava.
- **Nature.** Larger targets for logs, crops, harvests, fish, livestock and predators, Tamer for taming an
  animal and Saddle Up for a kilometre on a mount.
- **Chapter I.** Goblin Bane, Arachnid Apex and Void Reaver at the top of the defeat ladders, a second step for
  every ability, more portal worlds, Breach and Deepworks trips, Gobliterator defeats, Runestones, shards,
  Scraps, silk, Coffer Keys, Kenolite, Void crystal and metal blocks. Speedrunner is hidden and asks for the
  Gobliterator within 20 minutes of entering The Deepworks.
- **Misc.** Loudspeaker at 10,000 chat messages, Icon at 150 feats and the hidden Completionist at 250.

The sets ask for one of each.

| Feat | Category | Tier | Asks for |
|---|---|---|---|
| Pick Collector | Mining | Gold | A wood, copper, iron, cobalt, thorium and adamantite pickaxe |
| Copper Kit | Crafting | Bronze | Every piece of copper armour |
| Cobalt Clad | Crafting | Gold | Every piece of cobalt armour |
| Thorium Plate | Crafting | Gold | Every piece of thorium armour |
| Sword Collector | Crafting | Gold | A copper, iron, cobalt, thorium and adamantite sword |
| Crop Rotation | Nature | Gold | One harvest of each of the 14 crops |
| Full Net | Nature | Platinum | One catch of each of 15 kinds of fish and shellfish |

Three Platinum server firsts join the World tab. Founder goes to the first player to unlock 100 feats, Warpath
to the first to defeat 5,000 creatures and Surveyor to the first to discover all 5 zones.

Players who already pass a new target get the feat on their next join. On a world that ran before 1.4.0,
`/feats admin scan all` credits what the game already recorded.

## Parked feats

Five feats point at content that ships in the 0.7.0 assets but never appears in a survival world. Mithril,
onyxium and prisma ore are never placed by world generation and werewolves never spawn. These feats are
parked in 1.2.0. They are not in the browser, not counted in the totals and not earnable.

| Feat | Tier | Why it is parked |
|---|---|---|
| Mithril Miner | Gold | Mithril ore is never generated |
| Mithril Smith | Gold | No mithril ore, so no mithril bar |
| Onyx Eye | Platinum | Onyxium ore is never generated |
| Prismatic | Platinum | Prisma ore is never generated |
| Silver Bullet | Gold | Werewolves never spawn |

Full Spectrum stays, now asking for every metal that world generation places, from copper to adamantite
without mithril, at Gold. Parked feats keep their ids and return once the content reaches the world. A player
who earned one before keeps it in their player file, but it is worth no points while parked.

Five kill 10 of a kind feats were retired in the same release and folded into Goblinologist. They are Fire
Drill, Return to Sender, Shield Breaker, Pickpocketed and Coffer Cracker.

## World milestones and server firsts

The World tab holds 18 feats that belong to the server rather than to one player. Nine are world milestones,
reached once for everyone, and nine are server firsts, held by the one player who got there before anyone else.
They are not part of the 327 feats or the 15,075 points, they never count toward a player's unlocked count or
completion, and they have no rarity. The start up log says
`Feats loaded 327 feats worth 15075 points, plus 18 world feats`.

| Milestone | Tier | Reached when |
|---|---|---|
| Lights On | Silver | Power is restored to the Goblin Lab |
| Full Steam | Silver | The Boiler in the Goblin Lab is repaired |
| Engine Room | Silver | The Engine in the Goblin Lab is repaired |
| Pressure Rising | Silver | The Compressor in the Goblin Lab is repaired |
| Scrap Yard | Gold | Anyone defeats the Gobliterator |
| Redline | Gold | Anyone defeats the Gobliterator on hard mode |
| End of the Chapter | Gold | Anyone steps through the Dungeon Completion Portal |
| Open Gates | Gold | Anyone builds an Ancient Gateway |
| Uprooted | Gold | Anyone defeats The Hedera |

| First | Tier | Held by the first player to |
|---|---|---|
| Pioneer | Platinum | Defeat the Gobliterator |
| Trailblazer | Platinum | Step through the Dungeon Completion Portal |
| Historian | Platinum | Record 50 Memories |
| Keeper of Memories | Platinum | Record 100 Memories |
| Gatewright | Platinum | Build an Ancient Gateway |
| Party Crasher | Platinum | Defeat the Goblin Feastmaster |
| Founder | Platinum | Unlock 100 feats |
| Warpath | Platinum | Defeat 5,000 creatures |
| Surveyor | Platinum | Discover all 5 zones |

**Milestones.** The player who raises a milestone, the initiator, unlocks it like any other feat, with the
toast, the chat line, its points and its rewards. Only the initiator gets points. Every other player shares in
it. They see it as achieved in the World tab and get one quiet chat line, for example
`Server milestone reached, Lights On, by Steve`, with no toast, sound or points. Players who are offline at the
time get that line on their next join, up to three of them, then a single line saying how many more milestones
were reached.

The four Goblin Lab milestones come from the game's own server wide flags, which the Generator and the three
repairable devices raise. Feats reads those flags every 5 seconds. The initiator is the player who used a Goblin
Lab device in the last 60 seconds, the device behind that flag first. When nobody did, the milestone is still
reached, names nobody and gives nobody points. The other five milestones are raised by the first unlock of the
personal feat behind them anywhere on the server, such as Gobliterated for Scrap Yard.

**Firsts.** A first goes to the one player who unlocks the personal feat behind it before anyone else on the
server, and that player holds it forever. When two players finish the same fight at the same moment, the record
is claimed with a single atomic check, so exactly one of them gets it. The holder unlocks the first with its
toast and its 100 points, and nobody can earn it after that.

**The World tab.** Its header counts the records the server has, such as `4 / 18 achieved`. Each row shows
Milestone or First in place of the tier. An open row reads `Not yet achieved` or `Unclaimed` with the tier and
its points. A reached row reads `Achieved by Steve` or `Held by Steve` with the date, plus the points when you
earned it yourself. A milestone reached with nobody named reads `Achieved` alone. The All feats and Unlocked
tabs only list personal feats.

Admin grants and `setstat` never raise a milestone or a first. Points from the World tab are added on top of a
player's personal points, so a points total can pass 15,075 while the unlocked count and the completion
percentage only ever count personal feats. The records are kept in `server.json`, see Configuration.

## Server versions

| Feats | Hytale server |
|---|---|
| 1.5.1 | 0.7.0 release or newer, built against 0.7.1 |
| 1.5.0 | 0.7.0 release or newer |
| 1.4.0 | 0.7.0 release or newer |
| 1.3.0 | 0.7.0 release or newer |
| 1.2.1 | 0.7.0 release or newer |
| 1.2.0 | 0.7.0 release or newer |
| 1.1.0 | 0.7.0 release or newer |
| 1.0.0 | 0.6.8 |

Feats 1.1.0 and later need the 0.7.0 server because the events behind the Chapter I feats do not exist on older
servers. Feats 1.0.0 remains available for 0.6.8 servers.

Pre-release servers log a warning that the plugin targets `>=0.7.0`, because the server treats a pre-release
version as outside that range. The plugin still loads.

## Commands

| Command | What it does |
|---|---|
| `/feats` | Open the feats browser. Aliases are `/feat` and `/achievements` |
| `/feats stats` | Open the browser on the Statistics tab |
| `/feats top` | Show the top 10 players by points |
| `/feats recent` | Show the last 10 feats unlocked on the server, newest first |
| `/feats view <name>` | Browse another player's feats, read only |
| `/feats compare` | Your points, unlocks and per category counts next to another player's, named after the command |
| `/feats title` | List the Gold and Platinum feats you can wear as a title |
| `/feats title <feat>` | Wear a feat as your chat title, by id or name. `/feats title off` takes it off |
| `/feats notify off` | Mute your own unlock toasts, sounds and big titles. `/feats notify on` brings them back |
| `/feats share <feat>` | Show off a feat you own to everyone online, once every 30 seconds |
| `/feats reset` | Explain what a reset erases. `/feats reset confirm` erases your own feats and statistics |

Every player command is open to everyone and acts on the player who runs it. Nobody can change another
player's progress without the admin commands below.

`/feats view` and `/feats compare` work while `AllowViewOthers` is on, which is the default. Offline players are
read from their file. Hidden feats the other player has not earned stay `???`. `/feats recent` shows "a hidden
feat" in place of hidden feats you have not earned yourself. `/feats notify off` keeps the chat line, so you
still see every unlock.

`/feats share` takes a feat id such as `rock_bottom`, ignoring case. The line it posts names the feat, its tier,
the day you unlocked it and how rare it is. A feat you do not own and a feat that does not exist get the same
answer, so share cannot be used to find hidden feats.

`/feats top` answers from the ranking the leaderboard export keeps in memory, with online players brought up
to date. With the export turned off it reads the player files instead, at most once every 10 seconds per
player. Either way it sorts players exactly like the export does.

## Admin commands

> **Permissions.** Every `/feats admin` command is guarded by permission nodes the server generates. A sender
> needs `ryvrook.feats.command.feats.admin` and the node of the sub command, which is
> `ryvrook.feats.command.feats.admin.<sub>`, for example `ryvrook.feats.command.feats.admin.grant`. One of
> the two on its own is not enough. Grant both to your staff in `permissions.json` or your permission plugin.
> The server console has every permission.

| Command | What it does |
|---|---|
| `/feats admin view` | Open the browser read only on any player, whatever `AllowViewOthers` says |
| `/feats admin grant` | Unlock a feat whatever its requirements. Rewards follow only with `--rewards` |
| `/feats admin revoke` | Take a feat away, along with meta feats that are no longer met |
| `/feats admin reset` | Erase a player's feats and statistics after a confirm |
| `/feats admin setstat` | Set one counter to an exact value and unlock what it now meets |
| `/feats admin reload` | Re-read `Feats.json`, and `Rewards.json` while rewards are on |
| `/feats admin rates` | List the ten least and ten most earned feats on the server |
| `/feats admin scan` | Catch counters up from what Hytale itself recorded, see below |

The full forms are `/feats admin view <player>`, `/feats admin grant <player> <feat> [--rewards]`,
`/feats admin revoke <player> <feat>`, `/feats admin reset <player> confirm`,
`/feats admin setstat <player> <key> <value>`, `/feats admin reload`, `/feats admin rates` and
`/feats admin scan <player|all> [--dry-run]`. Feats are named
by id here. Counter keys are the ones in player files, such as `blocks_broken`.

Every admin command works from the server console except `view`, which opens a page. Players are found by their
online name first, then by the name stored in their player file, so offline players work too. Offline files are
read and written on the Feats thread, and a player who joins during an edit picks up the edited copy.

- A revoked feat whose counters still meet its target unlocks again on the next matching action or join. Lower
  the counter with `setstat` first when that matters. Lowering a counter never removes a feat on its own.
- Unlocks caused by `setstat` never hand out rewards.
- `/feats admin rates` needs rarity data, so it answers only once `RarityMinimumPlayers` players count.
- `/feats admin reset` keeps the record of rewards already given, so a reset player cannot earn them again.

### Scanning existing worlds

After upgrading to 1.3.0 on a server that already has players, run the scan once so firsts and milestones go to
whoever earned them first rather than to the next player who happens to unlock them.

A world that ran before Feats was installed already holds a record of what its players did. The scan reads
that record and raises the matching counters, so long time players get the feats they already earned.

```text
/feats admin scan all --dry-run
/feats admin scan all
/feats admin scan Steve
```

The player can be an online name, a name stored in a player file, a uuid or `all`. `all` covers every player
with a Feats file plus every player the engine has saved data for. Start with `--dry-run`, which works out
everything and reports what would be raised, unlocked and backfilled without writing a single file.

The scan only uses what Hytale records, never inventories.

- **Discovered zones** raise Zones discovered and the zone feats. The game saves region names, which Feats maps
  to zone names through the generators of the loaded worlds. A region no loaded world generates is skipped and
  counted in the report.
- **Recorded Memories** the player still holds raise Memories recorded and the Memory feats.
- **Death markers** set a floor for Deaths. The engine keeps at most five per world, so this is a lower bound.
- **A first spawn** in any world sets a floor of 1 for joins.

Respawn points, discovered instances and known recipes are not read.

Counters are only ever raised. A discovery Feats already has, or a total already at or above the game's number,
changes nothing, so running the scan again finds nothing new. Online players are updated through the usual
path on their world thread, so their unlocks arrive at once with toasts, rewards and meta feats. Offline
players are read from the engine's saved player data and updated on the Feats thread, and they unlock what
they now meet at their next join, through the usual catch up. A player the engine knows but Feats has never
seen gets a new player file. Players in Creative mode or a disabled world, and players another admin command
is editing, are skipped and named in the report.

`all` runs in batches of 25 players, each batch its own task on the Feats thread, so saves keep running in
between and a progress line follows every batch. It also backfills the world milestones and server firsts that
follow a personal feat and are not recorded yet. Each one goes to the scanned player with the earliest existing
unlock of that personal feat. The Goblin Lab milestones are left to the flag watcher. A scan of one player never
backfills.

The report has one line per player, players with news first, and ends with a totals line such as this one.

```text
Scan finished. 12 players, 3 with news. Raised 9 counters, 2 feats unlocked now and 4 feats unlock at the next join. Backfilled 2 server records.
```

Chat shows at most 20 lines. When the sender is a player, or the report
is longer than that, the whole report is also written to the server console. The scan needs the node
`ryvrook.feats.command.feats.admin.scan` and works from the console.

## Install

1. Download `Feats-<version>.jar` for your server version.
2. Put it in your Mods folder.
   - Windows `%APPDATA%\Hytale\UserData\Mods`
   - macOS `~/Library/Application Support/Hytale/UserData/Mods`
   - Dedicated server, the server's `mods/` directory
3. Start the world or server. Tracking begins the first time a player joins.
4. Type `/feats` in game to open the browser.

No other mods are required.

To update a running server, drop the new jar in, remove the old one and run `/plugin load Ryvrook:Feats` or
`/plugin reload Ryvrook:Feats`. The new jar may have a different file name. The server log then shows
"Feats UI pack registered from" followed by the jar path. If it warns that the pack is not registered, restart
the server. PlaceholderAPI is optional.

## Configuration

`Feats.json` is created in the plugin's data folder on first start. Edit it and run `/feats admin reload`, or
restart the server.

| Key | Default | Meaning |
|---|---|---|
| `ShowToasts` | `true` | Show the on-screen notification when a feat unlocks |
| `PlaySound` | `true` | Play the unlock sound |
| `UnlockSound` | Memories sound | Sound played on unlock. An empty or unknown id falls back to the Memories sound |
| `BigTitleMinimumTier` | `"PLATINUM"` | Lowest tier that also shows a big title in the middle of the screen. `OFF` turns it off |
| `BroadcastUnlocks` | `true` | Announce unlocks to other online players in chat |
| `BroadcastMinimumTier` | `"SILVER"` | Lowest tier that gets announced. One of `BRONZE`, `SILVER`, `GOLD` or `PLATINUM` |
| `WelcomeHint` | `true` | Show a short "x/327 unlocked, /feats to browse" line on join |
| `TrackDistance` | `true` | Track distance travelled once per second |
| `IgnoreCreative` | `true` | Stop every counter for players in Creative mode |
| `DisabledWorlds` | `[]` | World names where every counter stops, ignoring case |
| `AllowViewOthers` | `true` | Let players use `/feats view` and `/feats compare` |
| `Titles` | `"PREFIX"` | `PREFIX` puts a player's chosen title in front of their chat lines. `OFF` leaves chat alone |
| `ShowRarity` | `true` | Show rarity in the browser, in `/feats share` and on the unlock chat line of rare feats |
| `RarityMinimumPlayers` | `10` | Rarity stays hidden until at least this many players count toward it. Values below 1 read as 1 |
| `RareBelowPercent` | `5` | A feat owned by fewer than this percent of players counts as rare, 0 to 100 |
| `RewardsEnabled` | `false` | Hand out the rewards in `Rewards.json`, Coinage coins included. While off the file is never read |
| `DiscordWebhookUrl` | `""` | Discord webhook to post unlocks to. Empty turns it off |
| `DiscordMinimumTier` | `"GOLD"` | Lowest tier that gets posted to Discord |
| `AutosaveSeconds` | `60` | How often changed progress is written to disk. Values below 10 are raised to 10 |
| `ExportSeconds` | `15` | How often the leaderboard export is checked and rewritten if it changed. `0` or less turns the export off. Other values below 5 are raised to 5 |
| `Tracker` | `"auto"` | Where the counters come from. `auto` uses TrackerAPI when it is installed, else Feats tracks on its own. `always` uses TrackerAPI only. `never` ignores TrackerAPI. Read at startup, see [Works with TrackerAPI](#works-with-trackerapi) |

`PausePlaytimeWhenDisabled` defaults to `true`, which also stops time played in Creative mode and in disabled
worlds. Set it to `false` to keep time played counting there.

The default `UnlockSound` is `SFX_Memories_Unlock_Local`. A `DisabledWorlds` value looks like
`["creative", "lobby"]`. Tier settings that hold an unknown value fall back to Silver for broadcasts and Gold for
Discord, and show no big title.

`/feats admin reload` applies every key without a restart, except `AutosaveSeconds` and `ExportSeconds`. Their
timers keep the old interval until the server restarts, and turning the export on or off also needs a
restart.

Player progress lives in `players/<uuid>.json` next to the config. Delete a file while that player is offline
to reset them. Files are written atomically, and a file that fails to load is renamed to
`<uuid>.corrupt-<timestamp>.json` so the player starts fresh instead of breaking the server. Only the newest
corrupt copy of each player is kept. Counters that no feat or Statistics row reads are dropped when a file
loads, so player files stay small.

World milestones and server firsts live in `server.json` in the same data folder. Each record has the time it
was reached and the player who raised or holds it, and a milestone reached with nobody named keeps `null` there.

```json
{"version": 1,
 "milestones": {"world_power": {"at": 1760000000000, "by": null, "byName": null}},
 "firsts": {"first_gobliterator": {"at": 1760000000000, "by": "…", "byName": "Steve"}}}
```

The file is read before anyone joins, written on the Feats thread whenever a record is added and replaced
atomically. A file that fails to load is renamed to `server.corrupt-<timestamp>.json` and the server starts with
no records. Player files keep a `Shared` list of the milestones that player shares in, and a milestone that is
no longer in `server.json` drops from that list on the player's next join.

## Rewards

> **Rewards are off by default.** Set `RewardsEnabled` to `true` in `Feats.json`, then run
> `/feats admin reload` or restart the server. Feats then writes `Rewards.json` next to `Feats.json` as an
> empty template if it does not exist yet. Fill it in and run `/feats admin reload` again. While
> `RewardsEnabled` is `false` the file is never read or written.

The template holds four empty sections, a `_readme` line that explains the placeholders and an `_example`
section that shows the shape and is never read. A filled in file looks like this.

```json
{
  "Feats": {
    "digger": {"Commands": ["give {player} Tool_Pickaxe_Iron"], "Permissions": [], "Group": "", "Message": "Your {name} reward is in your inventory"}
  },
  "Tiers": {
    "PLATINUM": {"Commands": [], "Permissions": [], "Group": "", "Message": "Platinum feat earned, you now have {points} points"}
  },
  "Points": [
    {"At": 1000, "Commands": [], "Permissions": ["example.kit.veteran"], "Group": "", "Message": "You reached {points} points"}
  ],
  "Count": [
    {"At": 50, "Commands": [], "Permissions": [], "Group": "Veteran", "Message": "Fifty feats unlocked"}
  ]
}
```

- `Feats` rewards one feat, keyed by its id.
- `Tiers` rewards every unlock of a tier, keyed by `BRONZE`, `SILVER`, `GOLD` or `PLATINUM`.
- `Points` rewards reaching a points total once, and `Count` rewards reaching a number of unlocked feats. Each
  entry needs a positive `At`.
- Every reward has `Commands`, run as the console, `Permissions` added to the player, a `Group` the player
  joins and a `Message` sent to them in chat while they are online. Leave any of them empty.
- With Coinage installed a reward can also pay `Coins`, an amount in whole units such as `25` or `"2.50"`, into
  the player's Coinage account, online or not. `Currency` names the currency, empty means the primary one. So
  `"Tiers": {"GOLD": {"Coins": 50}}` pays 50 coins for every Gold feat. Without Coinage the coins are not paid
  and the server log says so once a minute.
- Commands and messages can use `{player}`, `{uuid}`, `{feat}` for the id, `{name}` for the feat name, `{tier}`
  and `{points}` for the total after the unlock.

Each reward is given once per player and recorded in their player file. The record survives a reset, so
resetting cannot earn the same rewards again. Feats unlocked on join because the counters already met them are
real unlocks and get their rewards. API grants get rewards, admin grants only with `--rewards`, and unlocks from
`setstat` never. Unknown feat ids and tier names are logged and skipped, and a file that does not parse keeps
the rewards loaded before. Commands run one every 100 ms, so a big catch up never floods a single tick, and a
command that keeps failing is logged at most once a minute.

## Titles

`/feats title <feat>` lets a player wear one of their Gold or Platinum feats as a title. With `Titles` set to
`PREFIX` it shows in square brackets in front of their chat lines, in the tier's colour.

Titles show in chat only when no other plugin formats chat. Feats adds the title only to messages that still use
the server's default chat format, so a chat formatting plugin keeps full control and the title simply does not
show there. Such plugins can read the title through the API or the `%feats_title%` placeholder. Losing the feat
through a reset or a revoke clears the title.

## Rarity

Rarity is the share of players who own a feat. Only players with at least one unlock or 10 minutes of play time
count, so a visitor who joined and left at once does not make every feat look rare. The numbers come from the
same read of the player files the leaderboard uses, at most once a minute on the Feats thread, and that read
keeps going when the export is turned off.

With `ShowRarity` on, the browser adds the share to each feat's progress line, for example
`Unlocked · 3 Oct 2026 · +50 pts · rare · 3% of players`. A feat owned by fewer than `RareBelowPercent`
percent of players is marked rare, and its unlock chat line ends with a tag such as "(rare, 3% of players)".
Hidden feats show no rarity until you have earned them. Nothing shows until `RarityMinimumPlayers` players
count, because a share of three players says very little.

## Creative mode and disabled worlds

Creative mode is ignored by default. With `IgnoreCreative` on, nothing counts while a player is in Creative
mode. Worlds listed in `DisabledWorlds` work the same way. Feats follows game mode changes and world changes as
they happen, and time played keeps counting in both cases only when `PausePlaytimeWhenDisabled` is `false`.
Feats already unlocked stay unlocked, and admin grants and API grants still work. Inventory gains in Creative mode
never count toward collection feats, even with `IgnoreCreative` off.

## Boss fights

A boss kill credits every player who hit the boss in the last 60 seconds, not only the player who landed the
killing blow. That covers every boss, the Gobliterator included, and multi phase fights count as one fight
across every phase. Only the killing blow counts toward ranged kills.

The Gobliterator challenge feats are checked for each credited player who is still in that world when it
falls. Not a Scratch needs a player who has not died in that run of The Deepworks, and Nothing to Lose needs
the hard mode Gobliterator and an empty armour bar at that moment.

## Discord

The Discord webhook is off until a URL is set. Set `DiscordWebhookUrl` to a webhook from your channel's
Integrations settings and run `/feats admin reload`. Every unlock at or above `DiscordMinimumTier` is posted
as an embed with the player and the feat in the title, the feat's description, a colour for the tier and a
footer with the points and the rarity. Player names can never ping anyone.

Posts go out one at a time from the Feats thread, never from the game, with a 10 second timeout and at most one
every 2 seconds. Up to 50 posts wait in line, and anything beyond that is dropped with one warning a minute.
When Discord asks Feats to slow down it waits as long as Discord says and then sends the same post again. The
webhook address is never written to the log.

## PlaceholderAPI

PlaceholderAPI is optional. When the PlaceholderAPI plugin is installed, Feats registers the `feats`
expansion at start. Without it Feats loads as usual and nothing changes.

| Placeholder | Value |
|---|---|
| `%feats_points%` | The player's points |
| `%feats_unlocked%` | How many feats the player has unlocked |
| `%feats_total%` | How many feats exist |
| `%feats_total_points%` | Points available in total |
| `%feats_rank%` | The player's leaderboard position, empty before they have one |
| `%feats_title%` | The name of the player's chosen title feat, empty for none |
| `%feats_last%` | The name of the player's latest unlock |
| `%feats_rarity_<id>%` | The share of players who own that feat, such as `3.1%`, empty while unknown |
| `%feats_stat_<key>%` | A raw counter, such as time played in seconds |
| `%feats_has_<id>%` | `true` or `false` |
| `%feats_first_<id>%` | The name of the player who holds that server first, empty while unclaimed |
| `%feats_milestone_<id>%` | `true` once that world milestone is reached, else `false` |

Replace `<id>` with a feat id and `<key>` with a counter key, for example `%feats_has_rock_bottom%` or
`%feats_stat_playtime%` for time played in seconds. Server records take the World tab ids, for example
`%feats_first_first_gobliterator%` and `%feats_milestone_world_power%`. `%feats_total%` and
`%feats_total_points%` count personal feats only.

## How tracking works

Feats listens to the server's ECS events (block break and place, craft, pickup, use block, zone discovery,
damage, death, interaction chain start, inventory change) plus player join, leave, world change, game mode
change and chat.

- Ability casts and spyglass use come from the interaction chains a player starts. A cast only counts when the
  player has the Mana to pay for it.
- Items obtained, meals and potions come from changes to the player's own inventory. Moves and sorting are told
  apart from real gains, and a meal only counts once the food has left the hand.
- Portal entries come from the world a player joins.
- Memories come from the player's recorded Memories, read once per second.
- The Goblin Lab milestones come from the server's game flags, read every 5 seconds.
- Chat messages count at most once every 5 seconds per player.
- Distance ignores steps of more than 64 metres in a second, so teleports and world changes do not count.
  Time played keeps counting while a player is idle.
- Breaking a block you placed recently earns nothing, and neither does placing again on a spot you just
  built on and broke. Feats remembers the last 256 spots each player placed.
- Items you dropped in the last 5 minutes do not count again when you pick them back up, and items you put
  in a chest and take back out do not count twice toward collection feats.
- This anti farming memory survives a relog and is cleared an hour after the player leaves.

Counters are grouped automatically, so a feat can require "any pickaxe" or "any ore" without listing every
item id. Groups cover item families (pickaxes, tools, weapons, armour, bars, food, potions, furniture,
benches), creature families (undead, goblins and each kind of goblin, spiders, each kind of Voidspawn, bosses,
livestock, predators and more) and causes of death.

Progress is cached in memory while a player is online and written to JSON on autosave, disconnect and
shutdown. Autosave only writes players whose progress changed. Autosaves and the saves for players leaving
and for resets run on a dedicated Feats thread, so they never hold up the game, and a save that fails is
logged and tried again later.

Catch up at join unlocks feats whose counters are already met. When a player's progress loads, every feat is
checked once against their counters. A feat added by an update, or one whose target went down, unlocks on that
join instead of waiting for the next matching action. The first 5 such unlocks get the usual toast, sound and
chat line. Any beyond that unlock quietly with a single chat line saying how many more there were. Catch up
unlocks are real unlocks and get their rewards.

- **Swimming, gliding and riding.** The once a second distance sample also looks at how the player is moving. Metres covered while swimming or gliding count towards swim and glide distance as well as the total. Riding a mount or a minecart is measured from the mount itself and counts towards ride distance. These follow the TrackDistance setting.
- **Top and bottom of the world.** The same sample notes when a player stands within three blocks of the lowest or the highest block layer of a world. Falling past the bottom into the void does not count, and portal instances such as The Deepworks are left out.
- **Taming.** Feeding a wild animal until it turns tame counts for the player who fed it. The game keeps no owner on a tamed animal, so Feats catches the moment the animal changes into its tamed form and credits the player the animal was looking at.
- **Kill streaks.** Every kill extends a running streak and a death ends it. The best streak is saved. The running streak lives in memory only, survives a relog and is forgotten an hour after the player leaves, like the anti farming memory. While a player is in Creative or a disabled world their streak neither grows nor ends.
- **Biggest hit and longest session.** The largest single hit a player deals is kept as a record. Session length is written every five minutes while online and again on leaving, so a crash loses at most five minutes of it. A Feats reload does not split a session, and the session keeps counting in Creative or a disabled world whenever time played does.
- **Untouched boss kills.** A boss fight starts with its first hit. A player credited with the kill who took no damage at all in the boss's world from then until the boss died earns the untouched boss kill, whether they joined the fight early or late and even if the fight went quiet for a while. A boss nobody touches for ten minutes starts a fresh fight.
- **Fast Gobliterator runs.** Each Deepworks run remembers when the player first entered that instance. Stepping out and back in keeps the first time.
- **Lava.** The game has no lava damage of its own, lava sets players alight with a stronger burn. A death to that burn counts as both a lava death and a fire death.
- **Glass.** The only blocks named glass in the game are developer test blocks, so windows count as glass.
- **Cheap tracking.** Block, item and creature ids are classified once and remembered, and a whole event updates a player's counters and checks their feats in one pass. A block break, kill or pickup costs well under a microsecond and allocates nothing.

## Works with TrackerAPI

[TrackerAPI](https://www.curseforge.com/hytale/mods/trackerapi/) is an optional server plugin that tracks what players do once and shares it with other plugins.
When it is installed, Feats takes its counts from TrackerAPI and registers none of its own tracking hooks.
Every count carries on in the Feats files with no gap, so nothing is lost when you add or remove it.

- Without TrackerAPI, Feats tracks on its own exactly as before.
- Feats needs TrackerAPI 1.0.0 or newer. An older one is ignored.
- The server log says at startup which way Feats is tracking.
- The `Tracker` setting picks the way. `auto` is the default. `always` uses TrackerAPI only and tracks nothing
  without it. `never` keeps Feats on its own hooks even when TrackerAPI is installed.
- The choice is made when Feats starts. After `/plugin reload TrackerAPI`, run `/plugin reload Feats` as
  well. Feats logs a reminder when it notices.
- Time played, session length, the Goblin Lab milestones, chat titles, rewards and the export stay with Feats
  either way.

## Works with Dominion Factions

When Dominion Factions is installed, Feats adds a Factions tab with 15 feats worth 460 points. They read the
counters Dominion hands to Feats, such as chunks claimed, enemy kills, sieges won, missions and coins put in the
faction bank. A counter only moves while the player is online. Feats looks for Dominion when it starts, so after
adding Dominion restart the server or run `/plugin reload Feats`. Without Dominion the tab does not exist and
the totals stay at 327 feats.

## Leaderboard export

Feats writes `export/feats.json` in its data folder so other tools can show a leaderboard and recent unlocks
without linking against Feats. The Atlas live map, a separate plugin, reads this file. The export is written
at start, on the `ExportSeconds` interval when something changed, and at shutdown. A reset drops that player
from the ranking and the recent unlocks and the file is rewritten on the next interval. The export runs on the
dedicated Feats thread and the file is replaced atomically.

The file holds the top 100 players by points, including offline players, the last 50 unlocks on the server, the
rarity of every feat and the world milestones and server firsts. Offline player files are re-read at most once
a minute.

```json
{"updatedAt": 1760000000000, "totalFeats": 327, "totalPoints": 15075, "online": 3, "rarityPlayers": 42,
 "rarity": {"first_steps": 0.9524, "rock_bottom": 0.0310},
 "players": [
  {"uuid": "…", "name": "Steve", "points": 1240, "unlocked": 58, "lastUnlockAt": 1759990000000}
 ],
 "recent": [
  {"at": 1759990000000, "uuid": "…", "name": "Steve", "feat": "rock_bottom", "featName": "Rock Bottom",
   "tier": "Bronze", "points": 10, "category": "Mining", "hidden": false}
 ],
 "milestones": {
  "world_power": {"at": 1759980000000, "by": null, "byName": null, "featName": "Lights On", "tier": "Silver", "hidden": false}
 },
 "firsts": {
  "first_gobliterator": {"at": 1759990000000, "by": "…", "byName": "Steve", "featName": "Pioneer",
   "tier": "Platinum", "hidden": false}
 }}
```

Players are sorted by points, then by who reached that score first, then by name. Timestamps are milliseconds
since the Unix epoch. `rarityPlayers` is the number of players who count toward rarity. `rarity` holds the share
of them who own each feat, from 0 to 1, for every feat, and stays empty until `RarityMinimumPlayers` players
count. Set `ExportSeconds` to `0` to turn the export off.

`milestones` and `firsts` hold the records of `server.json` keyed by feat id, oldest first, each with `at`,
`by`, `byName`, `featName`, `tier` and `hidden`. `by` and `byName` are `null` for a milestone reached with nobody named.
`totalFeats`, `totalPoints`, `unlocked` and `rarity` cover personal feats only, while a player's `points`
include the points of the World feats they earned.

## For plugin developers

Other plugins can read progress and listen for unlocks. List Feats as a dependency in your manifest, for example
`"Dependencies": {"Ryvrook:Feats": ">=1.3.0"}`, then use `dev.ryvrook.feats.api.FeatsApi`. The server
records need 1.3.0, everything else works from 1.2.0.

```java
FeatsApi feats = FeatsApi.get(); // null when Feats is not loaded
if (feats != null) {
    int points = feats.points(uuid);
    boolean digger = feats.hasFeat(uuid, "digger");
    feats.addStat(uuid, "fish_caught", 1);
    Runnable stop = feats.onUnlock(event ->
            getLogger().at(Level.INFO).log("%s unlocked %s", event.playerName(), event.featName()));
}
```

| Method | Returns |
|---|---|
| `points`, `rank` | Points total and leaderboard place, `0` when unranked |
| `unlocked`, `hasFeat` | Unlocked feat ids, or whether one is unlocked |
| `progress` | Current and target values toward a feat |
| `title` | The chosen title feat id, or `null` |
| `stats` | One counter |
| `feats` | Every feat with id, name, description, category, tier, hidden flag, points and scope |
| `milestones` | Every reached world milestone by feat id, as a `WorldRecord` |
| `firsts` | Every claimed server first by feat id, as a `WorldRecord` |
| `addStat` | Adds to a counter for an online player and unlocks what it meets |
| `grant` | Unlocks a feat for an online player, with the usual toast and rewards |
| `onUnlock` | Calls the listener on every unlock and returns a handle that removes it |

The scope of a `FeatInfo` is `PERSONAL`, `WORLD` or `FIRST`, and only `PERSONAL` feats count toward the
totals. A `WorldRecord` holds the feat id, the time in epoch milliseconds, the player's uuid and their name,
both `null` for a milestone reached with nobody named.

Every unlock also fires `FeatUnlockedEvent` (in `dev.ryvrook.feats.api`) on the server's event bus with the
player, the feat, its tier and points, the new total and whether it was a catch up on join. Register for it like
any other global event, for example with `getEventRegistry().registerGlobal(FeatUnlockedEvent.class, ...)`. It is
delivered on the thread that caused the unlock, usually a world thread, so keep listeners short. It cannot be
cancelled.

Reads for online players come from memory. Offline players answer from the leaderboard's last read of the
player files, at most a minute old. `addStat` respects the Creative mode and disabled world pause, and writes
only work for online players. Look `FeatsApi.get()` up again after a `/plugin reload` of Feats instead of
keeping it. The API is stable. Methods are only ever added.

## Building from source

Building needs JDK 25. The Gradle wrapper is included.

```bash
./gradlew build          # jar lands in build/libs/
./gradlew installMod     # also copies it into your local UserData/Mods folder
```

`installMod` picks the default Mods folder for Windows, macOS or Linux. Pass `-PmodsDir=<path>` to copy the
jar somewhere else.

The build pulls `com.hypixel.hytale:Server` from Hytale's Maven repository as a compile only dependency, so no
manual server jar is needed. PlaceholderAPI comes from the HelpChat repository as a compile only dependency and
is never bundled. Two properties in `gradle.properties` choose the server artifact. `hytale_channel` is
`release` or `pre-release`, and `hytale_version` defaults to `0.7.1`. Override both on the command line to
compile against a newer release or a pre-release.

```bash
./gradlew build -Phytale_channel=pre-release -Phytale_version=<version>
```

Versions older than 0.7.0 no longer compile because the Chapter I hooks need its classes.

The TrackerAPI and Coinage bridges compile against `TrackerAPI-1.0.0.jar` and `Coinage-api-1.0.0.jar`, found in a
`trackerapi` and a `coinage` checkout next to this one or under `~/Projects`. Point at other copies with
`-PtrackerApiJar=<path>` and `-PcoinageJar=<path>`. Neither is bundled.

## Contributing

Issues and pull requests are welcome. Please keep changes focused and make sure `./gradlew build` passes.

Every feat is defined in code in `src/main/java/dev/ryvrook/feats/model/FeatRegistry.java`, inside
`defineAll()`. Order within a category is display order. To add a feat, call one of the helpers there.

```java
feat("digger", "Digger", "Break 100 blocks.", MINING, BRONZE, "Tool_Pickaxe_Copper", StatKeys.BLOCKS_BROKEN, 100);
```

- `feat(...)` takes an id, name, description, category, tier, icon item id, stat key and target.
- `hidden(...)` takes the same arguments and shows the feat as `???` until it unlocks.
- `multi(...)` takes the same first six arguments, then several `req(key, target)` requirements that must
  all be met.

World milestones and server firsts live in `defineWorld()` in the same file. `world(...)` and `first(...)` take
an id, name, description, tier and icon item id, then a trigger. `after("<feat id>")` raises the feat the first
time anyone unlocks that personal feat, and `flag("<game flag>", level)` raises a world milestone once the
server wide game flag reaches that level. A first must follow a personal feat.

Feat ids must be unique, and the registry throws at startup on a duplicate. Ids are stored in player files, so
never rename one that has shipped. Feats that cannot be earned yet live in `parked()` in the same file, which is
never called, so they keep their ids for later. Stat keys and key prefixes live in `stats/StatKeys.java`. The
rules that map block, item, creature and portal ids to counters and groups live in `track/Classify.java`. If a
new feat needs a counter that does not exist yet, add the key to `StatKeys.java`, map it in `Classify.java` if
it belongs to a group, and record it from the matching system in `systems/`. Categories and tiers are enums in
`model/Category.java` and `model/Tier.java`. The browser UI files are in
`src/main/resources/Common/UI/Custom/Pages/`.

## License

Feats is released under the MIT License. Copyright (c) 2026 ryvrook. See [LICENSE](LICENSE) for the full text.
