# Feats

Achievements for Hytale servers. Feats adds 455 of them, called feats, in eleven tabs worth 20,735 points,
with an unlock toast, a chat line, a top of screen banner for the rarest unlocks and a `/feats` browser that
shows progress on every one, lifetime statistics and a points leaderboard. Two of the tabs are packs an admin can
turn off, and four more appear with Coinage, Dominion Factions, Chronicle Stats and Atlas, up to 528 feats worth
23,440 points. It runs on the server with a small UI pack inside the jar, so players join with an unmodified
client.

## Requirements

Hytale server 0.7.0 release or newer, built against 0.7.1, and Java 25. Feats 1.0.0 is the last version for
0.6.8, because the Chapter I hooks do not exist there. TrackerAPI 1.0.0, Coinage 1.2.0, Dominion Factions 0.5.1,
Chronicle Stats 1.5.0, Atlas 1.9.0 and PlaceholderAPI 1.0.8, each that version or newer, are optional, and so are
SocialBridge and RestAPI, which read Feats.

## Install

Drop `Feats-1.7.3.jar` into `UserData/Mods` for a singleplayer world or into the server's `mods/` folder and
start it. Tracking starts the first time a player joins. Type `/feats`.

To update a running server, swap the jar and run `/plugin reload Ryvrook:Feats`. The engine prints
`Failed to reload plugin Ryvrook:Feats!` and then enables Feats with its pages anyway. The log line
`Feats UI pack registered from` followed by the jar path tells you the pages are there. If it warns that the pack
is not registered, restart.

## What players get

Feats go Bronze 10 points, Silver 25, Gold 50, Platinum 100. Most are ladders on one counter, from "Break your
first block" to a million blocks broken or 500 kilometres travelled. Some ask for one of every kind, such as
Goblinologist for every goblin, Aquarium for all 30 kinds of fish or Menagerie for every animal that can be tamed.
Hidden feats show as `???` until earned, and meta feats unlock at 10, 25, 50, 100, 150, 250, 350 and 425 feats
and at 10,000 and 20,000 points.

Every server has nine tabs, Exploration with 35 feats, Mining 34, Building 28, Crafting 41, Combat 64, Survival 48,
Nature 52, Chapter I 118 and Misc 25, which is 445 feats worth 20,445 points. The Chapter I tab holds the 0.7.0
content, the goblin ladder, every Voidspawn, the world events, the merchants and the Gobliterator on hard mode
with no armour on. The last row of each of the first eight tabs is a hidden Platinum that unlocks once every
other feat of the tab is yours, and Misc ends with Grand Slam for every Platinum.

Five packs sit in Feats.json, each a tab of its own that exists while its switch is on. Social, 6 feats for
playing alongside other players, and Calendar, 4 feats for being online on the first of January, Midsummer,
Midwinter and the server's install anniversary, are on by default, which makes the 455 feats and 20,735 points
above. Hardcore, 2 feats, turns itself on when the server runs hardcore lives. PvP, 3 feats, and Grind, 3 ladders
that run to ten million blocks, 100,000 kills and 1,000 hours, are off.

An unlock shows the game's own toast with the Memories unlock sound. Platinum also puts a big title on screen,
and Silver or better is announced to everyone online. A Platinum, a feat owned by under 1 percent of players once
rarity is known, a tab closer and every server first or milestone also shows a banner at the top of the screen for
six seconds, with the feat, its description and why it is there. Feats that always get the banner show a dot after
their tier in the browser. The browser has a Statistics tab with 44 rows, time played,
distance, blocks, ore, kills, deaths, damage, chests, fish, the join streak, nights slept through, the longest
life in game days, biomes, creature kinds and players met among them. Players can wear a Gold or Platinum feat as
a chat title and see how rare each feat is once 10 players count toward rarity.

The World tab holds 30 records that belong to the server rather than to a player, 18 milestones and 12 firsts.
Nine of the milestones read server totals, blocks broken, creatures and bosses defeated, players who have joined,
players online at once, chunks generated and days since Feats was installed.

The tabs appear in this order, Exploration to Misc, then Factions, Coinage and Chronicle when their mod is
installed, then Social, Hardcore, PvP, Calendar and Grind while their pack is on, then World. A tab with no feats
in it is not shown.

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
| `/feats notify <on or off>` | Mute or bring back your own toasts, sounds, big titles and banners. The chat line stays |
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
| `/feats admin totals` | Every server total and each total milestone with its value, target and whether it is reached |
| `/feats admin scan <player or all> [--dry-run]` | Credit what Hytale already recorded, see below |
| `/feats admin reload` | Re-read `Feats.json` and `Rewards.json`, then list the packs that are on |

A revoked feat whose counter still meets the target comes back on the next matching action. Lower the counter
with `setstat` first. A reset keeps the record of rewards already given, so they cannot be earned twice. A grant
of a feat whose pack is off or whose sibling is missing answers with the reason, such as `pack GRIND is off`, since
packs and siblings are read at start.

## Settings

`Feats.json` sits in the plugin's data folder. `/feats admin reload` applies everything except
`AutosaveSeconds`, `ExportSeconds`, `Tracker`, the four sibling link keys and the five `Pack` keys, which need a restart or
`/plugin reload Ryvrook:Feats`. The reload reply lists the packs that are on so a change that has not taken
effect yet is easy to spot.

| Key | Default | What it does |
|---|---|---|
| `ShowToasts` | `true` | Toast on unlock |
| `PlaySound` | `true` | Unlock sound |
| `UnlockSound` | `SFX_Memories_Unlock_Local` | Sound id. Empty or unknown falls back to this |
| `BigTitleMinimumTier` | `"PLATINUM"` | Lowest tier with a big title on screen. `OFF` turns it off |
| `BroadcastUnlocks` | `true` | Announce unlocks to other players |
| `BroadcastMinimumTier` | `"SILVER"` | Lowest tier announced |
| `WelcomeHint` | `true` | "x/455 unlocked, /feats to browse" on join |
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
| `Coinage` | `"auto"` | The Coinage tab, the cross mod feats and coin rewards. `auto` links when Coinage is installed, `always` also warns at start when it is missing, `never` ignores it. Read at start |
| `Dominion` | `"auto"` | The Factions tab, same three values. Read at start |
| `Chronicle` | `"auto"` | The Chronicle tab, same three values. Read at start |
| `Atlas` | `"auto"` | On the Map and Surveyor's Mark, same three values. Read at start |
| `PackSocial` | `true` | The Social tab, 6 feats for time with other players. Read at start |
| `PackHardcore` | `"auto"` | The Hardcore tab, 2 feats. `auto` turns it on when the server runs hardcore lives, `true` and `false` decide outright. Read at start |
| `PackPvp` | `false` | The PvP tab, 3 feats. Read at start |
| `PackCalendar` | `true` | The Calendar tab, 4 feats on real dates. Read at start |
| `PackGrind` | `false` | The Grind tab, 3 very long ladders. Read at start |
| `Banner` | `"RARE"` | When the top of screen banner shows. `OFF` never, `PLATINUM` for Platinum unlocks and server records, `RARE` also for banner feats and feats under `BannerRareBelowPercent`, `ALL` for every unlock |
| `BannerSeconds` | `6` | How long the banner stays, 2 to 30 |
| `BannerRareBelowPercent` | `1` | Owned by fewer than this percent of players earns the banner on `RARE` |
| `BannerBroadcast` | `true` | Milestones and firsts show the banner to everyone online, with the holder's name |
| `BannerSound` | `SFX_Event_Chapter_1_Complete_Sting` | Sound with the banner. Empty for none, unknown falls back to the Memories sound |
| `BannerParticles` | `false` | A particle burst at the player's feet with their own banner |
| `FullHouse` | `20` | Players online at once for the Full House milestone, at least 2 |
| `CalendarZone` | `""` | Time zone id for the Calendar pack, the join streak and the join hour feats, such as `Europe/London`. Empty is the server's own |
| `ServerTotals` | `true` | Keep the server totals in `server.json` and the nine milestones that read them |

A player who counts toward rarity has at least one unlock or 10 minutes played, so visitors do not make every
feat look rare. A player sees at most three banners a minute, the rest stay in chat.

Player progress lives in `players/<uuid>.json`. Delete a file while the player is offline to reset them. A file
that fails to load is renamed `<uuid>.corrupt-<timestamp>.json` and the player starts fresh. World records and
the server totals live in `server.json`.

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

The World tab has 30 records that belong to the server. Eighteen milestones are reached once for everyone, such
as Lights On when power comes back to the Goblin Lab, Scrap Yard when anyone beats the Gobliterator or A Million
Blocks when the server's blocks broken total passes a million. Twelve firsts go to the one player who got there
first, such as Pioneer for the first Gobliterator kill, Founder for the first to 100 feats or Peerless for the
first to every Platinum. They sit outside the 455 feats and 20,735 points.

The player who raises a milestone gets it with points like any feat. Everyone else shares in it with one quiet
chat line and, with `BannerBroadcast` on, the banner, and offline players get the line at their next join. The
four Goblin Lab milestones come from the game's own flags, read every 5 seconds, and go to whoever used a Lab
device in the last 60 seconds. The nine total milestones go to the player whose action crossed the line when
there is one, and are recorded with nobody named when the total moved on its own, such as the peak online count.
When two players finish the same fight in the same tick, one atomic check gives the first to exactly one of them.
Admin grants and `setstat` never raise a record.

The server totals are `blocks_broken`, `kills`, `kills_boss`, `deaths`, `players_known` (players who have
joined), `peak_online` (the most players online at once), `chunks_generated` and `server_days` (days since Feats
first ran), plus `installed_at`, the time of that first run in epoch milliseconds. They move in memory and are
written to `server.json` with the autosave and at shutdown. `/feats admin totals` lists them with each total
milestone. With `ServerTotals` off nothing is added to them and their milestones wait.

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

Each link is found by plugin name when Feats starts and has a setting in `Feats.json`. Feats is complete with
none of them.

| Mod | What appears when it is installed | Setting |
|---|---|---|
| TrackerAPI | Feats takes 34 event kinds from it instead of its own hooks | `Tracker` |
| Coinage | Coinage tab of 24 feats, coin rewards, with Dominion three cross mod feats | `Coinage` |
| Dominion Factions | Factions tab of 30 feats, unlocks in faction chat | `Dominion` |
| Chronicle Stats | Chronicle tab of 6 feats, feats on its player sheet | `Chronicle` |
| Atlas | Two Exploration feats, the leaderboard panel on the web map | `Atlas` |
| SocialBridge | Unlock posts and Feats on its `/profile` in Discord | `DiscordVia` |
| RestAPI | Feats routes under `/api/feats` | RestAPI's own |
| Lullaby | Nothing, the two do not touch | none |

**TrackerAPI.** On `auto`, with TrackerAPI 1.0.0 or newer installed, Feats registers none of its own tracking
hooks and takes 34 event kinds from TrackerAPI, the 19 it took before plus movement edges, mounts, vitals, armour
worn, items that broke, projectiles launched and landed, instances discovered, trigger volumes, world events,
sleep, weather, the game clock, NPC use and players leaving. Counts carry on in the same player files, so adding
or removing TrackerAPI loses nothing. Time played, session length, the Goblin Lab flags, titles, rewards and the
export stay with Feats either way, and so do close calls, low oxygen, emptied Mana, game days, respawn points,
hardcore lives, known recipes, permission groups, hub referrals, the join streak and dates, players met and the
server totals, which Feats reads itself on both paths. Rolls, mantles, double jumps, seats, shield blocks,
repairs, capture crates, trap bait, Memories only a kill unlocks, void stages, late portal exits and merchants
have no TrackerAPI record behind them, so Tumbler, Over the Top, Double Jump, Take a Seat, Shield Up, Bulwark,
Good as New, Catch and Carry, Bait Shop, Hard Won, Eviction, Last Second, Haggler and Trade Route, and the Mining,
Combat, Nature and Chapter I closers that need them, only move with `Tracker` on `never`. On this path a world
event credits every player in its world, a mount counts by the kind it has while ridden, a night slept counts for
anyone asleep when the world's clock reaches morning, and a full armour set counts only once each of its pieces
was put on during the session. TrackerAPI 1.0.3 and newer reload Feats after their own reload. With an
older one, reload Feats by hand and the log reminds you.

**Coinage.** Rewards can pay `Coins`, see Rewards. With Coinage installed Feats also adds the Coinage tab of 24
feats worth 720 points on what players do with their coins, payments received and sent, the highest balance and
net worth, shop and market sales, auctions, trades, savings and interest, loans, bets, the lottery, the daily
reward streak and coin notes. Feats reads Coinage's own events, so the tab needs Coinage 1.2.0 or newer and
nothing else. Amounts count in whole coins, and the fraction left over is carried to the next payment or
interest so small amounts add up. The highest balance counts in the primary currency only, net worth is
read every five minutes for the players online, and those two and the daily streak only move while the player is
online. Everything else reaches offline players in their file.

**Dominion Factions.** Dominion sends its `dominion:` counters through `FeatsApi.addStat`, and Feats adds the
Factions tab of 30 feats worth 900 points on them, such as chunks claimed, sieges won and coins put in the
faction bank. Dominion 0.5.1 or newer keeps the first 15 moving, and Downsizing for a released claim, since it
reads a key Dominion has always sent. The other 13 added in 1.7.2, joining and being kicked, wars, alliances,
upgrades, the faction market, foreign land, sieges defended, breaches, hill events, outposts and full power, need
Dominion 0.7.1, which sends their keys. New Rank, added in 1.7.3, reads `dominion:rank_changes`, one for each
promotion or demotion of the member, which Dominion sends from 0.7.2. Credits for offline members land in their file. Dominion also posts Feats unlocks in faction chat. Feats
checks for Dominion when it starts, so after adding it restart or reload Feats.

**Chronicle Stats** reads progress through the API and shows feats on its player sheet. With Chronicle Stats
1.5.0 installed Feats also adds the Chronicle tab of 6 feats worth 300 points on what only Chronicle keeps, the
most blocks broken in one day, the most creatures defeated in one week, a month with every day active, and
levels 10, 25 and 50 in RPG Leveling. The three level feats only move while Chronicle reports an RPG Leveling
level. Chronicle and Feats each find the other by name, since neither can list the other as a dependency, so Feats
looks for Chronicle every 5 seconds and follows it through a reload. Each player's window is read at most once
every 10 seconds per counter, a change held back by that is read on the next look, and the month of active days
is read once a minute from the poll.

**Atlas** reads `export/feats.json` for its leaderboard panel. With Atlas 1.9.0 installed Feats adds two feats
to Exploration, On the Map for linking an account to the web map and Surveyor's Mark for placing 10 shared map
markers. A marker counts when a player places it on the in game map, so server markers from `/atlas marker` do
not count. On the Map counts when a player redeems a web map link code, even from a browser while offline. An
Atlas without marker events, older than 1.9.0, only moves On the Map, and the log says so once.

**Both Coinage and Dominion** add three more, War Chest and Treasurer in Factions and Allied Trade in Coinage.
War Chest is checked with the net worth read, at 50,000 coins while the player's faction has an enemy. Allied
Trade counts a settled trade between members of two allied factions. Treasurer reads the coins Dominion reports
as put in the faction bank. Feats reads the faction facts from Dominion by name, the same way as Chronicle.

**SocialBridge** posts unlocks to its Discord channel from `FeatUnlockedEvent` and adds points, rank and title to
its `/profile`. With `DiscordVia` on `auto` Feats stops posting through `DiscordWebhookUrl` while SocialBridge is
installed, so an unlock is never posted twice. Set `DiscordVia` to `webhook` to keep the webhook as well, or `off`
for neither from Feats.

**RestAPI** serves feats, player progress, world records and the points leaderboard under `/api/feats`.

Lullaby does not touch Feats. Nights slept through come from the engine's own sleep state, so they count with or
without it.

A sibling that is added later is picked up at the next restart or reload of Feats, and the log names each tab
that is in. A sibling that stops while Feats runs leaves its feats in the browser with frozen counters.

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
| `%feats_streak%`, `%feats_best_streak%` | The player's join streak in days, and their best |
| `%feats_server_<key>%` | A server total, such as `%feats_server_blocks_broken%` |
| `%feats_pack_<name>%` | `true` while that pack is on, such as `%feats_pack_social%` |
| `%feats_siblings%` | The sibling plugins found at start, comma separated |

Titles show in chat only when no other plugin formats chat. A chat plugin can use `%feats_title%` instead.

## Leaderboard export

`export/feats.json` in the data folder holds the top 100 players by points, offline ones included, the last 50
unlocks, the rarity of every feat and the world records with a `hidden` flag. Since 1.7.2 it also holds `packs`,
the packs that are on, `siblings`, the sibling plugins found at start, `totals`, the server totals, and `feats`,
every feat with its id, name, tier, points, category, scope and hidden flag. Every feat row, recent unlock and
world record carries `banner`, and feat rows and recent unlocks carry `pack`, the pack name or null, and
`requires`, the plugin names the feat needs. The file is rewritten on the `ExportSeconds` interval when
something changed, and at shutdown, atomically. Players sort by points, then by who got there first, then by
name. Timestamps are epoch milliseconds. Atlas reads this file, and any other tool can without linking against
Feats.

## For plugin authors

List Feats as a dependency or optional dependency, `"Ryvrook:Feats": ">=1.7.2"`, and use
`dev.ryvrook.feats.api.FeatsApi`.

```java
FeatsApi.onEnable(feats -> {           // now when Feats is up, and after every start
    int points = feats.points(uuid);
    boolean digger = feats.hasFeat(uuid, "digger");
    feats.addStat(uuid, "fish_caught", 1);
    List<LeaderboardEntry> top = feats.top(10);
    long blocks = feats.serverStat("blocks_broken");
    Runnable stop = feats.onUnlock(e -> log(e.playerName() + " unlocked " + e.featName()));
});
FeatsApi.onDisable(() -> { /* drop what you kept */ });
```

The API has `points`, `rank`, `top`, `unlocked`, `hasFeat`, `progress`, `title`, `stats`, `feats`, `milestones`,
`firsts`, `addStat`, `grant` and `onUnlock`, plus the static `onEnable`, `onDisable`, `removeHooks` and
`API_VERSION`, which is 3 since 1.7.2. Version 3 adds `packs`, the packs that are on, `siblings`, the sibling
plugins found at start, and `serverStat`, a server total by key. `FeatInfo` gains `banner`, `pack`, the pack
name or null, and `requires`, the plugin names the feat needs. `FeatUnlockedEvent` carries the player, the feat,
its tier and points, the new total, `catchUp`, since 1.7.0 `admin`, `hidden`, `rarity` (0 to 1, or -1 when not
known), `category` and `description`, and since 1.7.2 `banner`. The older constructors still work and fill the
new fields with false, null and empty. After a `/plugin reload` of Feats every enabled plugin that lists Feats
is reloaded two seconds later, so its hooks land on the new copy. `addStat` works for offline players, the add
waits in memory and the file thread writes it, and their feats unlock quietly with rewards at the next join.
`grant` needs the player online. Every unlock also fires `FeatUnlockedEvent` on the server's event bus from the
thread that caused it, usually a world thread, so keep listeners short. Ask `FeatsApi.get()` again after a
reload of Feats. Methods are only ever added.

## How counting works

These are the rules that decide whether something counts.

- Breaking a block you placed recently counts nothing, and neither does placing again on that spot. Feats
  remembers the last 256 spots per player. Seeds and fishing traps count on every placement.
- Items you dropped in the last 5 minutes do not count again when picked up, and items moved through a chest
  count once.
- Chat counts at most once every 5 seconds per player.
- Distance ignores jumps of more than 64 metres in a second, so teleports do not count. Swimming, gliding and
  riding count toward their own totals as well.
- A boss kill credits everyone who hit the boss in the last 60 seconds, across every phase. Raid Party counts a
  boss kill credited to three players or more, War Band to five or more.
- An ability cast counts only when the player had the Mana for it. A meal counts once the food left the hand.
- Taming credits the player the animal was looking at when it turned tame, since the game keeps no owner.
- Lava sets players alight, so a death to that burn counts as both a lava and a fire death.
- A biome counts when you stand in it. Weather counts after 10 seconds in it. A moon phase counts while it is
  dark out.
- A kill is at night from 19:00 to 05:00 on the game clock, under a Blood Moon when that weather is active, from
  far away at 50 metres or more by projectile, and on low health under 10 percent.
- Double, Triple and Ten in Fifteen pay once each time their window fills and again only after it has emptied, so
  four kills in three seconds count one Double and one Triple.
- A close call is health under 10 percent and then back over 25 percent without dying. A survived fall and a
  drowning episode are settled a second after the hit, once it is clear the player lived.
- A night slept counts when the world wakes with its clock at the slumber's target time, for every player who
  was asleep during it. Waking early counts nothing.
- Game days roll over on the clock of the world you are in. A death starts the life day count over.
- The join streak runs on real dates in the server's zone or `CalendarZone`. A join, or a midnight that passes
  while you are online, marks the day. The day after the last one continues the streak, the same day keeps it,
  anything else starts it over at one. The best streak stays.
- Night Owl is a join between midnight and 04:00, Early Bird between 05:00 and 07:00, in the same zone.
- A seat counts once per block kind, a mount by the kind it was before it was ridden, and a minecart ride when
  you get in. The longest ride is measured at dismount.
- Trap bait counts only with the bait in hand. A coop use houses an animal, and a use while the coop shows
  produce ready collects it.
- A world event credits every player within 64 metres of it when it ends in success. An event with no known
  position credits everyone in its world.
- A tool worn out is a tool or weapon in your hand that reaches zero durability on its slot. A repair is
  durability rising on the same slot. Armour wearing through on your body is not a tool worn out.
- A full armour set counts once per material while every piece is worn at once. Bronze Ornate has no legs,
  Kweebec only a head and a chest and Trooper no hands, so those count with the pieces they ship.
- A watering counts a filled watering can used while not crouching, since crouching places the can as a block.
  A capture crate counts when an animal goes into it.
- Respawn points, known recipes and hardcore lives are read every 5 seconds. Waypoints counts the most respawn
  points held at once in the world you are in.
- Creature kinds count a creature once, whatever its patrol, wander or boss phase variant.
- Hardcore days are game days while the server runs hardcore lives. Full Pool's clean days start over when a
  life is lost.
- A tab closer needs every other feat of its tab, sibling feats in that tab included. Grand Slam needs every
  Platinum on the server, pack and sibling ones included, except the closers and the feat count ladders.
- Players met stop at 2,000 per file. Time with company is handed over an hour at a time, so the part of a
  session under an hour is not kept.
- Shared First goes to every player online when a milestone with a named holder is recorded.

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
- The banner is centred with the engine's HUD layout, which none of the sibling mods' HUDs use. Whether it lands
  in the middle of the screen, how it sits next to the compass, the objective panel and the boss bar, and whether
  it survives a world transfer or a respawn have not been checked in a client yet. The removal is scheduled either
  way, so a lost banner costs nothing.
- Several 1.7.2 counters rest on ids and rules read from the game assets and the engine rather than from a
  running client, and have not been checked in one yet. These are the instance title key behind Temple Raider,
  the trigger volume id prefixes behind Checkpoint and Patched Up, the coop block states behind Henhouse and
  Produce, and the trap bait and watering roots. Biome names and instance keys are logged the first time each is
  seen, so the log on a new server shows what the game sends.
- The merchant behind Haggler and Trade Route is read from the trade page the player has open, through a field
  the engine does not expose. A server where that read fails logs one warning and the two feats stop counting.
- A repair is a durability rise on the same slot without a swap, and a water landing is a fall of 30 metres that
  ends in fluid. Both rules come from reading the engine and need a client to confirm.
- World event credit goes by distance only. A player who fought the event from more than 64 metres away is not
  credited.
- A milestone that was reached with nobody named, such as Full House, does not count for Shared First.
- On a server that ran Feats before 1.7.2, the install date behind First Year and Server Birthday is the first
  1.7.2 start, and the players who have joined total counts first joins from then on. A player who joined before
  1.7.2 has their first visit set at their first 1.7.2 join, so Anniversary counts a year from then.

## Building

JDK 25, wrapper included.

```bash
./gradlew build          # jar in build/libs/
./gradlew installMod     # also copies it to your local UserData/Mods, -PmodsDir=<path> for elsewhere
```

The server comes from Hytale's Maven repository and PlaceholderAPI from HelpChat's, both compile only.
`hytale_channel` and `hytale_version` in `gradle.properties` pick the server, `release` and `0.7.1` by default.
The TrackerAPI, Coinage and Atlas bindings compile against jars found in sibling `trackerapi`, `coinage` and
`atlas` checkouts or under `~/Projects`. Point elsewhere with `-PtrackerApiJar=<path>`, `-PcoinageJar=<path>` and
`-PatlasJar=<path>`. None is bundled. Chronicle Stats and Dominion are read by name at run time and need no jar.

Every feat is defined in `defineAll()` in `model/FeatRegistry.java`, in display order.

```java
feat("digger", "Digger", "Break 100 blocks.", MINING, BRONZE, "Tool_Pickaxe_Copper", StatKeys.BLOCKS_BROKEN, 100);
```

`hidden(...)` takes the same arguments, and `multi(...)` takes the first six followed by several
`req(key, target)`. A feat that needs a sibling, sits in a pack or carries the banner is built with `of(...)` and
`define(...)`, as in `define(of(...).requiring(Sibling.ATLAS))`, `.inPack(Pack.SOCIAL)` or `.withBanner()`.
World records live in `defineWorld()`, with `total(key, target)` for the ones that read a server total. Ids are
stored in player files, so never rename one that shipped. Counter keys are in `stats/StatKeys.java` and the
groups such as "any pickaxe" in `track/Classify.java`. [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) has the rest.

Issues and pull requests are welcome on [GitHub](https://github.com/ryvrook/feats).

## Licence

MIT, copyright (c) 2026 ryvrook. See [LICENSE](LICENSE).
