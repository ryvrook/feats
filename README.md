# Feats

Achievements for Hytale servers. Feats adds 327 of them, called feats, in nine categories worth 15,075 points,
with an unlock toast, a chat line and a `/feats` browser that shows progress on every one, lifetime statistics
and a points leaderboard. It runs on the server with a small UI pack inside the jar, so players join with an
unmodified client.

## Requirements

Hytale server 0.7.0 release or newer, built against 0.7.1, and Java 25. Feats 1.0.0 is the last version for
0.6.8, because the Chapter I hooks do not exist there. TrackerAPI, Coinage, Dominion Factions and
PlaceholderAPI are optional.

## Install

Drop `Feats-1.6.0.jar` into `UserData/Mods` for a singleplayer world or into the server's `mods/` folder and
start it. Tracking starts the first time a player joins. Type `/feats`.

To update a running server, swap the jar and run `/plugin reload Ryvrook:Feats`. The engine prints
`Failed to reload plugin Ryvrook:Feats!` and then enables Feats with its pages anyway. The log line
`Feats UI pack registered from` followed by the jar path tells you the pages are there. If it warns that the pack
is not registered, restart.

## What players get

Feats go Bronze 10 points, Silver 25, Gold 50, Platinum 100. Most are ladders on one counter, from "Break your
first block" to a million blocks broken or 500 kilometres travelled. Some ask for one of every kind, such as
Goblinologist for every goblin or Full Net for 15 kinds of fish. Hidden feats show as `???` until earned, and
meta feats unlock at 10, 25, 50, 100, 150 and 250 feats.

The categories are Exploration, Mining, Building, Crafting, Combat, Survival, Nature, Chapter I and Misc. The
Chapter I tab holds 105 feats worth 4,650 points for the 0.7.0 content, the goblin ladder, every Voidspawn
and the Gobliterator on hard mode with no armour on. A tenth tab, Factions, adds 15 feats worth 460
points when Dominion Factions is installed.

An unlock shows the game's own toast with the Memories unlock sound. Platinum also puts a big title on screen,
and Silver or better is announced to everyone online. The browser has a Statistics tab with time played,
distance, blocks, ore, kills, deaths, damage, chests and fish. Players can wear a Gold or Platinum feat as a
chat title and see how rare each feat is once 10 players count toward rarity.

## Commands

| Command | What it does |
|---|---|
| `/feats` (aliases `/feat`, `/achievements`) | Open the browser |
| `/feats stats` | Open it on the Statistics tab |
| `/feats top` | Top 10 by points, offline players included |
| `/feats recent` | Last 10 unlocks on the server |
| `/feats view <name>` | Another player's feats, read only |
| `/feats compare <name>` | Your points and per category counts next to theirs |
| `/feats title [<feat> or off]` | List the titles you can wear, wear one, or take it off |
| `/feats notify <on or off>` | Mute or bring back your own toasts, sounds and big titles. The chat line stays |
| `/feats share <feat>` | Post a feat you own to chat, once every 30 seconds |
| `/feats reset confirm` | Erase your own feats and statistics |

Player commands only act on the player who runs them. `view` and `compare` need `AllowViewOthers`. A feat you
do not own and a feat that does not exist get the same answer from `share`, so it cannot find hidden feats.

Admin commands need two generated nodes together, `ryvrook.feats.command.feats.admin` and the sub command's
own, such as `ryvrook.feats.command.feats.admin.grant`. All of them except `view` work from the console, and
they find offline players by the name in their file.

| Command | What it does |
|---|---|
| `/feats admin view <player>` | Open the browser on anyone |
| `/feats admin grant <player> <feat> [--rewards]` | Unlock a feat. Rewards only with `--rewards` |
| `/feats admin revoke <player> <feat>` | Take a feat away with the meta feats it no longer meets |
| `/feats admin reset <player> confirm` | Erase a player's feats and statistics |
| `/feats admin setstat <player> <key> <value>` | Set a counter, such as `blocks_broken`, and unlock what it meets. Never gives rewards |
| `/feats admin rates` | The ten least and ten most earned feats |
| `/feats admin scan <player or all> [--dry-run]` | Credit what Hytale already recorded, see below |
| `/feats admin reload` | Re-read `Feats.json` and `Rewards.json` |

A revoked feat whose counter still meets the target comes back on the next matching action. Lower the counter
with `setstat` first. A reset keeps the record of rewards already given, so they cannot be earned twice.

## Settings

`Feats.json` sits in the plugin's data folder. `/feats admin reload` applies everything except
`AutosaveSeconds` and `ExportSeconds`, which need a restart.

| Key | Default | What it does |
|---|---|---|
| `ShowToasts` | `true` | Toast on unlock |
| `PlaySound` | `true` | Unlock sound |
| `UnlockSound` | `SFX_Memories_Unlock_Local` | Sound id. Empty or unknown falls back to this |
| `BigTitleMinimumTier` | `"PLATINUM"` | Lowest tier with a big title on screen. `OFF` turns it off |
| `BroadcastUnlocks` | `true` | Announce unlocks to other players |
| `BroadcastMinimumTier` | `"SILVER"` | Lowest tier announced |
| `WelcomeHint` | `true` | "x/327 unlocked, /feats to browse" on join |
| `TrackDistance` | `true` | Sample distance once a second |
| `IgnoreCreative` | `true` | Stop every counter in Creative |
| `DisabledWorlds` | `[]` | Worlds where every counter stops, such as `["creative", "lobby"]` |
| `PausePlaytimeWhenDisabled` | `true` | Also stop time played in Creative and disabled worlds |
| `AllowViewOthers` | `true` | Allow `/feats view` and `/feats compare` |
| `Titles` | `"PREFIX"` | Put the chosen title in front of chat lines. `OFF` leaves chat alone |
| `ShowRarity` | `true` | Rarity in the browser, in `share` and on rare unlock lines |
| `RarityMinimumPlayers` | `10` | Hide rarity until this many players count |
| `RareBelowPercent` | `5` | Owned by fewer than this percent counts as rare |
| `RewardsEnabled` | `false` | Hand out `Rewards.json`. While off the file is never read |
| `DiscordWebhookUrl` | `""` | Webhook for unlock posts. Empty is off |
| `DiscordMinimumTier` | `"GOLD"` | Lowest tier posted |
| `DiscordVia` | `"auto"` | Who posts unlocks. `auto` leaves it to SocialBridge when installed, else the webhook. `webhook` always uses the webhook, `off` posts nothing |
| `AutosaveSeconds` | `60` | Save interval for changed players, at least 10 |
| `ExportSeconds` | `15` | Leaderboard export check, at least 5. `0` turns the export off |
| `Tracker` | `"auto"` | Where counts come from. `auto` uses TrackerAPI when present, `always` only TrackerAPI, `never` own hooks. Read at start |

A player who counts toward rarity has at least one unlock or 10 minutes played, so visitors do not make every
feat look rare.

Player progress lives in `players/<uuid>.json`. Delete a file while the player is offline to reset them. A file
that fails to load is renamed `<uuid>.corrupt-<timestamp>.json` and the player starts fresh. World records live
in `server.json`.

## Rewards

Rewards are off until `RewardsEnabled` is true. After a reload Feats writes an empty `Rewards.json` template
next to `Feats.json`. Fill it in and reload again.

```json
{
  "Feats":  { "digger": { "Commands": ["give {player} Tool_Pickaxe_Iron"], "Message": "Your {name} reward is in your inventory" } },
  "Tiers":  { "GOLD": { "Coins": 50 } },
  "Points": [ { "At": 1000, "Permissions": ["example.kit.veteran"] } ],
  "Count":  [ { "At": 50, "Group": "Veteran" } ]
}
```

`Feats` keys one feat by id, `Tiers` every unlock of a tier, `Points` and `Count` a total reached once. A reward
can run `Commands` as the console, add `Permissions`, put the player in a `Group`, send a `Message` and, with
Coinage installed, pay `Coins` in whole units into their account, online or not, with `Currency` naming a
currency other than the primary one. Placeholders are `{player}`, `{uuid}`, `{feat}`, `{name}`, `{tier}` and
`{points}`.

Each reward is given once per player and that survives a reset. Join catch up and API grants pay rewards, admin
grants only with `--rewards`, `setstat` never. Commands run one every 100 ms. Coins that come due while Coinage
is reloading wait for it, and a Feats shutdown meanwhile keeps them in `CoinsWaiting.json`. Without Coinage they
are not paid and the log says so once a minute.

## World milestones and server firsts

The World tab has 18 records that belong to the server. Nine milestones are reached once for everyone, such as
Lights On when power comes back to the Goblin Lab or Scrap Yard when anyone beats the Gobliterator. Nine firsts
go to the one player who got there first, such as Pioneer for the first Gobliterator kill or Founder for the
first to 100 feats. They sit outside the 327 feats and 15,075 points.

The player who raises a milestone gets it with points like any feat. Everyone else shares in it with one quiet
chat line, and offline players get that line at their next join. The four Goblin Lab milestones come from the
game's own flags, read every 5 seconds, and go to whoever used a Lab device in the last 60 seconds. When two
players finish the same fight in the same tick, one atomic check gives the first to exactly one of them. Admin
grants and `setstat` never raise a record.

## Scanning an existing world

A world that ran before Feats knows some of what its players did. The scan reads that and raises counters,
never lowers them.

```text
/feats admin scan all --dry-run
/feats admin scan all
```

It reads discovered zones, the Memories a player still holds, death markers (the engine keeps five per world,
so deaths are a floor) and first spawns. It does not read inventories, respawn points, instances or recipes.
Online players unlock at once. Offline players unlock at their next join. `all` runs 25 players per batch on the
Feats thread and also gives each missing milestone and first to the player with the earliest unlock behind it,
so run it once after installing on a server that already has players. Running it again finds nothing new.

## With the other mods

**TrackerAPI.** On `auto`, with TrackerAPI 1.0.0 or newer installed, Feats registers none of its own tracking
hooks and takes 19 event kinds from TrackerAPI. Counts carry on in the same player files, so adding or removing
TrackerAPI loses nothing. Time played, session length, the Goblin Lab flags, titles, rewards and the export stay
with Feats either way. TrackerAPI 1.0.3 and newer reload Feats after their own reload. With an older one, reload
Feats by hand and the log reminds you.

**Coinage.** Rewards can pay `Coins`, see Rewards. Nothing else.

**Dominion Factions.** Dominion sends its `dominion:` counters through `FeatsApi.addStat`, and Feats adds the
Factions tab of 15 feats on them, such as chunks claimed, sieges won and coins put in the faction bank. Credits
for offline members land in their file. Dominion also posts Feats unlocks in faction chat. Feats checks for
Dominion when it starts, so after adding it restart or reload Feats.

**Chronicle Stats** reads progress through the API and shows feats on its player sheet.

**Atlas** reads `export/feats.json` for its leaderboard panel.

Lullaby does not touch Feats.

## PlaceholderAPI

With the PlaceholderAPI plugin installed Feats registers the `feats` expansion.

| Placeholder | Value |
|---|---|
| `%feats_points%`, `%feats_unlocked%`, `%feats_rank%` | The player's points, unlock count and leaderboard place |
| `%feats_total%`, `%feats_total_points%` | Feats and points available, personal feats only |
| `%feats_title%`, `%feats_last%` | Chosen title and latest unlock, by name |
| `%feats_has_<id>%` | `true` or `false` |
| `%feats_rarity_<id>%` | Share of players who own it, such as `3.1%` |
| `%feats_stat_<key>%` | A raw counter, such as `%feats_stat_playtime%` in seconds |
| `%feats_first_<id>%`, `%feats_milestone_<id>%` | Holder of a first, or whether a milestone is reached |

Titles show in chat only when no other plugin formats chat. A chat plugin can use `%feats_title%` instead.

## Leaderboard export

`export/feats.json` in the data folder holds the top 100 players by points, offline ones included, the last 50
unlocks, the rarity of every feat and the world records with a `hidden` flag. It is rewritten on the
`ExportSeconds` interval when something changed, and at shutdown, atomically. Players sort by points, then by
who got there first, then by name. Timestamps are epoch milliseconds. Atlas reads this file, and any other tool
can without linking against Feats.

## For plugin authors

List Feats as a dependency, `"Ryvrook:Feats": ">=1.3.0"`, and use `dev.ryvrook.feats.api.FeatsApi`.

```java
FeatsApi feats = FeatsApi.get(); // null when Feats is not loaded
if (feats != null) {
    int points = feats.points(uuid);
    boolean digger = feats.hasFeat(uuid, "digger");
    feats.addStat(uuid, "fish_caught", 1);
    Runnable stop = feats.onUnlock(e -> log(e.playerName() + " unlocked " + e.featName()));
}
```

The API has `points`, `rank`, `unlocked`, `hasFeat`, `progress`, `title`, `stats`, `feats`, `milestones`,
`firsts`, `addStat`, `grant` and `onUnlock`. `addStat` works for offline players, the add waits in memory and the
file thread writes it, and their feats unlock quietly with rewards at the next join. `grant` needs the player
online. Every unlock also fires `FeatUnlockedEvent` on the server's event bus from the thread that caused it,
usually a world thread, so keep listeners short. Ask `FeatsApi.get()` again after a reload of Feats. Methods are
only ever added.

## How counting works

These are the rules that decide whether something counts.

- Breaking a block you placed recently counts nothing, and neither does placing again on that spot. Feats
  remembers the last 256 spots per player.
- Items you dropped in the last 5 minutes do not count again when picked up, and items moved through a chest
  count once.
- Chat counts at most once every 5 seconds per player.
- Distance ignores jumps of more than 64 metres in a second, so teleports do not count. Swimming, gliding and
  riding count toward their own totals as well.
- A boss kill credits everyone who hit the boss in the last 60 seconds, across every phase.
- An ability cast counts only when the player had the Mana for it. A meal counts once the food left the hand.
- Taming credits the player the animal was looking at when it turned tame, since the game keeps no owner.
- Lava sets players alight, so a death to that burn counts as both a lava and a fire death.

Players who already meet a feat get it at their next join. The first five catch up unlocks get the full toast,
the rest unlock quietly with one summary line.

## Limits

- Memories are a server wide race in 0.7.0. Once anyone hands one in, nobody else can record it, so on a busy
  server some players never reach Living Memory at 100.
- Five feats are parked because their content never shows up in a survival world. Mithril, onyxium and prisma
  ore are not generated and werewolves do not spawn. Mithril Miner, Mithril Smith, Onyx Eye, Prismatic and
  Silver Bullet keep their ids, are hidden from the browser and come back when the content does.
- The only blocks named glass are developer test blocks, so windows count as glass.
- Titles do not show next to a chat formatting plugin.
- The engine logs `Failed to reload plugin Ryvrook:Feats!` on every reload even when it works.

## Building

JDK 25, wrapper included.

```bash
./gradlew build          # jar in build/libs/
./gradlew installMod     # also copies it to your local UserData/Mods, -PmodsDir=<path> for elsewhere
```

The server comes from Hytale's Maven repository and PlaceholderAPI from HelpChat's, both compile only.
`hytale_channel` and `hytale_version` in `gradle.properties` pick the server, `release` and `0.7.1` by default.
The TrackerAPI and Coinage bridges compile against jars found in sibling `trackerapi` and `coinage` checkouts or
under `~/Projects`. Point elsewhere with `-PtrackerApiJar=<path>` and `-PcoinageJar=<path>`. Neither is bundled.

Every feat is defined in `defineAll()` in `model/FeatRegistry.java`, in display order.

```java
feat("digger", "Digger", "Break 100 blocks.", MINING, BRONZE, "Tool_Pickaxe_Copper", StatKeys.BLOCKS_BROKEN, 100);
```

`hidden(...)` takes the same arguments, and `multi(...)` takes the first six followed by several
`req(key, target)`. World records live in `defineWorld()`. Ids are stored in player files, so never rename one
that shipped. Counter keys are in `stats/StatKeys.java` and the groups such as "any pickaxe" in
`track/Classify.java`. [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) has the rest.

Issues and pull requests are welcome on [GitHub](https://github.com/ryvrook/feats).

## Licence

MIT, copyright (c) 2026 ryvrook. See [LICENSE](LICENSE).
