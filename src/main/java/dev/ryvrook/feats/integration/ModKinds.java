package dev.ryvrook.feats.integration;

import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.trackerapi.api.ModKeys;
import dev.ryvrook.trackerapi.api.Subscription;
import dev.ryvrook.trackerapi.api.TrackerApi;
import dev.ryvrook.trackerapi.api.TrackerEvent;
import dev.ryvrook.trackerapi.api.event.CompanionChanged;
import dev.ryvrook.trackerapi.api.event.DungeonRun;
import dev.ryvrook.trackerapi.api.event.EnchantmentTriggered;
import dev.ryvrook.trackerapi.api.event.EntityDied;
import dev.ryvrook.trackerapi.api.event.ModBound;
import dev.ryvrook.trackerapi.api.Actor;
import dev.ryvrook.trackerapi.api.ModInfo;
import dev.ryvrook.trackerapi.api.event.ItemEnchanted;
import dev.ryvrook.trackerapi.api.event.LuckyDrop;
import dev.ryvrook.trackerapi.api.event.ModAbilityUsed;
import dev.ryvrook.trackerapi.api.event.ModAction;
import dev.ryvrook.trackerapi.api.event.ModBossDefeated;
import dev.ryvrook.trackerapi.api.event.ModChoiceChanged;
import dev.ryvrook.trackerapi.api.event.ModLevelUp;
import dev.ryvrook.trackerapi.api.event.ModMilestone;
import dev.ryvrook.trackerapi.api.event.ModXpGained;
import dev.ryvrook.trackerapi.api.event.VeinMined;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Mirrors the generic mod records of TrackerAPI 1.2.0 into the Feats counters, under the same keys TrackerAPI's
 * own counter sink writes, {@code <mod>:<stat>} and {@code <mod>:<stat>:<id>} with the binding id as the prefix
 * ({@code rpg:level}, {@code dungeons:cleared:Crypt}), the way the {@code dominion:} counters are mirrored. The
 * mod feats under {@code model/mods} read these keys.
 *
 * <p>Every TrackerAPI 1.2.0 type lives in this class and nowhere else in Feats, so {@link TrackerBridge} still
 * loads and links against TrackerAPI 1.1.x. The bridge only touches this class once {@code api.version()} is at
 * least {@link TrackerBridge#MOD_KINDS_MINIMUM}.
 *
 * <p>The rules follow TrackerAPI's {@code CounterSink}. Sums go through {@code increment}, bests
 * ({@code <mod>:level}, {@code <mod>:skill_level:<track>}, {@code <mod>:highest_level}, {@code <mod>:largest},
 * {@code <mod>:streak_best}, {@code <mod>:companion_level_best}) through {@code raiseTo}, and the one time marks
 * ({@code <mod>:<slot>:<id>}, {@code <mod>:<category>:<id>}) through {@code discover} with the plural total
 * ({@code rpg:class:<id>} and {@code rpg:classes}, {@code mmo:quest:<id>} and {@code mmo:quests}). Three things
 * differ from the sink, since Feats has no lowering counter and every requirement is an at least. The fastest run
 * is kept as counts of good runs instead, {@code dungeons:fast_clear} for a completed run under ten minutes,
 * {@code endgame:fast_trial} under five, plus {@code <mod>:hardcore_clear} for the Hardcore mode and
 * {@code <mod>:solo_clear} for a party of one. The max level is a flag, {@code <mod>:max_level_reached} raised to 1.
 * And Feats adds the distinct kinds the feats need, {@code <mod>:boss_type:<id>} with {@code <mod>:boss_types},
 * {@code <mod>:dungeon_type:<id>} with {@code <mod>:dungeon_types} on a clear, {@code relics:relic_kind:<id>} with
 * {@code relics:relic_kinds} for a relic whose ability was used, {@code tamework:species:<id>} with
 * {@code tamework:species}, {@code enchant:enchant_kind:<id>} with {@code enchant:enchant_kinds},
 * {@code zephyr:move_kind:<action>} with {@code zephyr:move_kinds} and {@code <mod>:skill_level_best} over every skill.
 *
 * <p>{@code ModBound} carries no player and no counter, so it is not subscribed. A record without a player or a mod
 * counts nothing. Every listener runs on the TrackerAPI dispatch thread, the tracker is safe from any thread.
 */
final class ModKinds {
    /** The record kinds subscribed, for the listener count the bridge logs. */
    static final int KINDS = 15;
    /** A completed Major Dungeons run under this counts as a fast clear. */
    static final long FAST_CLEAR_MILLIS = 10 * 60_000L;
    /** A completed Endgame trial under this counts as a fast trial. */
    static final long FAST_TRIAL_MILLIS = 5 * 60_000L;
    /** The difficulty name a hardcore clear is counted on, compared ignoring case. */
    static final String HARDCORE_MODE = "Hardcore";
    static final String DUNGEONS = "dungeons";
    static final String ENDGAME = "endgame";
    static final String RELICS = "relics";
    static final String ZEPHYR = "zephyr";
    static final String TAMEWORK = "tamework";

    /** The three writes the mirror makes, so a test can record them without a tracker. */
    interface Sink {
        void add(UUID player, String key, long amount);

        void raise(UUID player, String key, long value);

        /** A one time mark {@code prefix + id} that raises {@code total} the first time it is seen. */
        void discover(UUID player, String prefix, String id, String total);
    }

    /** The sink over the tracker, the production one. */
    static Sink sink(FeatsTracker tracker) {
        return new Sink() {
            @Override
            public void add(UUID player, String key, long amount) {
                tracker.increment(player, key, amount);
            }

            @Override
            public void raise(UUID player, String key, long value) {
                tracker.raiseTo(player, key, value);
            }

            @Override
            public void discover(UUID player, String prefix, String id, String total) {
                tracker.discover(player, prefix, id, total);
            }
        };
    }

    /**
     * The bests TrackerAPI's bindings write straight into its counters without a record, read back when an event
     * of that mod arrives. A test gives a fake, production reads the live TrackerAPI.
     */
    interface Reads {
        long get(UUID player, String key);

        Map<String, Long> all(UUID player);

        /** True while TrackerAPI holds the binding with this id. */
        boolean bound(String mod);

        Reads NONE = new Reads() {
            @Override
            public long get(UUID player, String key) {
                return 0;
            }

            @Override
            public Map<String, Long> all(UUID player) {
                return Map.of();
            }

            @Override
            public boolean bound(String mod) {
                return false;
            }
        };
    }

    /** The reads over the live TrackerAPI, the bound set refreshed on every ModBound. */
    static final class ApiReads implements Reads {
        private final TrackerApi api;
        private volatile java.util.Set<String> bound = java.util.Set.of();

        ApiReads(TrackerApi api) {
            this.api = api;
            refresh();
        }

        void refresh() {
            java.util.Set<String> next = new java.util.HashSet<>();
            try {
                for (ModInfo m : api.mods()) {
                    if (m.bound()) {
                        next.add(m.id());
                    }
                }
            } catch (RuntimeException | LinkageError ignored) {
                // an older TrackerAPI or one that is stopping, nothing is bound
            }
            bound = java.util.Set.copyOf(next);
        }

        @Override
        public long get(UUID player, String key) {
            try {
                return api.counters().get(player, key);
            } catch (RuntimeException | LinkageError e) {
                return 0;
            }
        }

        @Override
        public Map<String, Long> all(UUID player) {
            try {
                Map<String, Long> all = api.counters().all(player);
                return all == null ? Map.of() : all;
            } catch (RuntimeException | LinkageError e) {
                return Map.of();
            }
        }

        @Override
        public boolean bound(String mod) {
            return bound.contains(mod);
        }
    }

    private final Sink sink;
    private volatile Reads reads;

    ModKinds(Sink sink) {
        this(sink, Reads.NONE);
    }

    ModKinds(Sink sink, Reads reads) {
        this.sink = sink;
        this.reads = reads == null ? Reads.NONE : reads;
    }

    /** Subscribes the mirror to every mod kind and gives the subscriptions to close on unbind. */
    static List<Subscription> install(TrackerApi api, String owner, FeatsTracker tracker) {
        ApiReads reads = new ApiReads(api);
        ModKinds kinds = new ModKinds(sink(tracker), reads);
        List<Subscription> out = kinds.bind(api, owner);
        sub(out, api, owner, ModBound.class, e -> reads.refresh());
        return out;
    }

    List<Subscription> bind(TrackerApi api, String owner) {
        List<Subscription> out = new ArrayList<>(KINDS);
        sub(out, api, owner, ModLevelUp.class, this::onLevelUp);
        sub(out, api, owner, ModXpGained.class, this::onXpGained);
        sub(out, api, owner, ModChoiceChanged.class, this::onChoiceChanged);
        sub(out, api, owner, ModAbilityUsed.class, this::onAbilityUsed);
        sub(out, api, owner, ModAction.class, this::onAction);
        sub(out, api, owner, ModMilestone.class, this::onMilestone);
        sub(out, api, owner, DungeonRun.class, this::onDungeonRun);
        sub(out, api, owner, ModBossDefeated.class, this::onBossDefeated);
        sub(out, api, owner, ItemEnchanted.class, this::onItemEnchanted);
        sub(out, api, owner, EnchantmentTriggered.class, this::onEnchantmentTriggered);
        sub(out, api, owner, VeinMined.class, this::onVeinMined);
        sub(out, api, owner, LuckyDrop.class, this::onLuckyDrop);
        sub(out, api, owner, CompanionChanged.class, this::onCompanionChanged);
        sub(out, api, owner, EntityDied.class, this::onDeath);
        return out;
    }

    // ---- bests read back from TrackerAPI ------------------------------------------------------------------

    static final String PARRIES = "parries";
    static final String STREAK_BEST = "streak_best";
    static final String WAVE_BEST = "wave_best";
    static final String COMBO_BEST = "combo_best";
    static final String KILLS = "kills";

    /** Raises the Feats copy of a best TrackerAPI's binding wrote itself, when it is above zero. */
    void mirror(UUID p, String key) {
        long v = reads.get(p, key);
        if (v > 0) {
            sink.raise(p, key, v);
        }
    }

    /** Raises the Feats copies of a total and every per id key under it, {@code relics:kills} and {@code relics:kills:<relic>}. */
    void mirrorFamily(UUID p, String total) {
        String prefix = total + ':';
        for (Map.Entry<String, Long> e : reads.all(p).entrySet()) {
            String k = e.getKey();
            if (e.getValue() != null && e.getValue() > 0 && (k.equals(total) || k.startsWith(prefix))) {
                sink.raise(p, k, e.getValue());
            }
        }
    }

    /**
     * A kill by a player while Wan's Wonder Weapons is bound. TrackerAPI's relics binding counts the kill under
     * {@code relics:kills} and the relic in hand on the world thread before this async delivery, so the counters
     * read here are current.
     */
    void onDeath(EntityDied e) {
        Actor killer = e.killer();
        if (killer == null || killer.kind() != Actor.Kind.PLAYER || killer.uuid() == null || !reads.bound(RELICS)) {
            return;
        }
        mirrorFamily(killer.uuid(), key(RELICS, KILLS));
    }

    private static <E extends TrackerEvent> void sub(List<Subscription> out, TrackerApi api, String owner, Class<E> type,
                                                     Consumer<? super E> listener) {
        out.add(api.subscribe(owner, type, listener));
    }

    // ---- keys ---------------------------------------------------------------------------------------------

    /** {@code mod:stat}. */
    static String key(String mod, String stat) {
        return mod + ':' + stat;
    }

    /** {@code mod:stat:id}. */
    static String key(String mod, String stat, String id) {
        return mod + ':' + stat + ':' + id;
    }

    /** The marker prefix {@code mod:stat:} a discovery adds its id to. */
    private static String prefix(String mod, String stat) {
        return mod + ':' + stat + ':';
    }

    private static boolean blank(String s) {
        return s == null || s.isEmpty();
    }

    /** The total and, when the id is known, the per id count. */
    private void addId(UUID p, String mod, String stat, String id, long amount) {
        sink.add(p, key(mod, stat), amount);
        if (!blank(id)) {
            sink.add(p, key(mod, stat, id), amount);
        }
    }

    // ---- levels and experience -------------------------------------------------------------------------------

    /**
     * The main level is a best with its level ups summed and the cap a flag. A skill level is a best per track, with
     * {@code skill_level_best} over every skill and the skill level ups summed.
     */
    void onLevelUp(ModLevelUp e) {
        UUID p = e.player();
        String mod = e.mod();
        if (p == null || blank(mod)) {
            return;
        }
        long gained = Math.max(1, e.newLevel() - e.oldLevel());
        if (isMain(e.track())) {
            sink.raise(p, key(mod, ModKeys.LEVEL), e.newLevel());
            sink.add(p, key(mod, ModKeys.LEVEL_UPS), gained);
            if (e.maxLevel() > 0 && e.newLevel() >= e.maxLevel()) {
                sink.raise(p, key(mod, ModKeys.MAX_LEVEL_REACHED), 1);
            }
        } else {
            sink.raise(p, key(mod, ModKeys.SKILL_LEVEL, e.track()), e.newLevel());
            sink.raise(p, key(mod, SKILL_LEVEL_BEST), e.newLevel());
            sink.add(p, key(mod, ModKeys.SKILL_LEVEL_UPS), gained);
        }
    }

    /** Feats only, the highest skill level over every skill of a mod. */
    static final String SKILL_LEVEL_BEST = "skill_level_best";

    private static boolean isMain(String track) {
        return blank(track) || ModKeys.PLAYER_TRACK.equals(track);
    }

    /** Whole points of experience, with the source as the id on the main track and the skill otherwise. */
    void onXpGained(ModXpGained e) {
        UUID p = e.player();
        String mod = e.mod();
        if (p == null || blank(mod)) {
            return;
        }
        long amount = Math.round(e.amount());
        if (amount <= 0) {
            return;
        }
        addId(p, mod, ModKeys.XP, isMain(e.track()) ? e.source() : e.track(), amount);
    }

    // ---- picks, abilities, moves and marks ------------------------------------------------------------------

    /** A pick is a mark under its slot, {@code rpg:class:<id>}, with the plural total {@code rpg:classes}. */
    void onChoiceChanged(ModChoiceChanged e) {
        UUID p = e.player();
        String mod = e.mod();
        if (p == null || blank(mod) || blank(e.slot()) || blank(e.id())) {
            return;
        }
        sink.discover(p, prefix(mod, e.slot()), e.id(), key(mod, ModKeys.plural(e.slot())));
    }

    /** Casts in total and per ability. A relic that carried one is a relic kind. */
    void onAbilityUsed(ModAbilityUsed e) {
        UUID p = e.player();
        String mod = e.mod();
        if (p == null || blank(mod)) {
            return;
        }
        addId(p, mod, ModKeys.ABILITIES, e.abilityId(), 1);
        if (RELICS.equals(mod) && !blank(e.itemId())) {
            sink.discover(p, prefix(mod, RELIC_KIND), e.itemId(), key(mod, RELIC_KINDS));
        }
    }

    static final String RELIC_KIND = "relic_kind";
    static final String RELIC_KINDS = "relic_kinds";
    static final String MOVE_KIND = "move_kind";
    static final String MOVE_KINDS = "move_kinds";
    static final String BOSS_TYPE = "boss_type";
    static final String BOSS_TYPES = "boss_types";
    static final String DUNGEON_TYPE = "dungeon_type";
    static final String DUNGEON_TYPES = "dungeon_types";
    static final String ENCHANT_KIND = "enchant_kind";
    static final String ENCHANT_KINDS = "enchant_kinds";
    static final String SPECIES = "species";
    static final String FAST_CLEAR = "fast_clear";
    static final String FAST_TRIAL = "fast_trial";
    static final String HARDCORE_CLEAR = "hardcore_clear";
    static final String SOLO_CLEAR = "solo_clear";

    /** A move counts under its own name, {@code parries:parry}, and again under {@code :pvp} against a player. */
    void onAction(ModAction e) {
        UUID p = e.player();
        String mod = e.mod();
        if (p == null || blank(mod) || blank(e.action())) {
            return;
        }
        String key = key(mod, e.action());
        sink.add(p, key, 1);
        if (e.pvp()) {
            sink.add(p, key + ':' + ModKeys.PVP, 1);
        }
        if (ZEPHYR.equals(mod)) {
            sink.discover(p, prefix(mod, MOVE_KIND), e.action(), key(mod, MOVE_KINDS));
        }
        if (PARRIES.equals(mod)) {
            mirror(p, key(mod, STREAK_BEST));
        }
    }

    /** A one time mark under its category, {@code mmo:quest:<id>}, with the plural total {@code mmo:quests}. */
    void onMilestone(ModMilestone e) {
        UUID p = e.player();
        String mod = e.mod();
        if (p == null || blank(mod) || blank(e.category()) || blank(e.id())) {
            return;
        }
        sink.discover(p, prefix(mod, e.category()), e.id(), key(mod, ModKeys.plural(e.category())));
        if (ENDGAME.equals(mod)) {
            mirror(p, key(mod, WAVE_BEST));
            mirror(p, key(mod, COMBO_BEST));
        }
    }

    // ---- dungeons and bosses --------------------------------------------------------------------------------

    /**
     * Entries, clears and failures in total and per dungeon, clears per mode, the dungeon kinds cleared, and the
     * fast, hardcore and solo clear counts a time or mode feat reads.
     */
    void onDungeonRun(DungeonRun e) {
        UUID p = e.player();
        String mod = e.mod();
        if (p == null || blank(mod) || e.phase() == null) {
            return;
        }
        switch (e.phase()) {
            case ENTERED -> addId(p, mod, ModKeys.RUNS, e.dungeonId(), 1);
            case COMPLETED -> {
                addId(p, mod, ModKeys.CLEARED, e.dungeonId(), 1);
                if (!blank(e.mode())) {
                    sink.add(p, key(mod, ModKeys.CLEARED_MODE, e.mode()), 1);
                    if (HARDCORE_MODE.equalsIgnoreCase(e.mode())) {
                        sink.add(p, key(mod, HARDCORE_CLEAR), 1);
                    }
                }
                if (e.partySize() == 1) {
                    sink.add(p, key(mod, SOLO_CLEAR), 1);
                }
                if (!blank(e.dungeonId())) {
                    sink.discover(p, prefix(mod, DUNGEON_TYPE), e.dungeonId(), key(mod, DUNGEON_TYPES));
                }
                long took = e.durationMillis();
                if (took > 0) {
                    if (DUNGEONS.equals(mod) && took < FAST_CLEAR_MILLIS) {
                        sink.add(p, key(mod, FAST_CLEAR), 1);
                    } else if (ENDGAME.equals(mod) && took < FAST_TRIAL_MILLIS) {
                        sink.add(p, key(mod, FAST_TRIAL), 1);
                    }
                }
            }
            case FAILED -> addId(p, mod, ModKeys.FAILED, e.dungeonId(), 1);
            case LEFT -> {
                // nothing to count, the run neither ended well nor badly
            }
        }
    }

    /** One record per credited player, so only the record's own player is counted. The boss id is a kind. */
    void onBossDefeated(ModBossDefeated e) {
        UUID p = e.player();
        String mod = e.mod();
        if (p == null || blank(mod)) {
            return;
        }
        addId(p, mod, ModKeys.BOSSES, e.bossId(), 1);
        if (!blank(e.bossId())) {
            sink.discover(p, prefix(mod, BOSS_TYPE), e.bossId(), key(mod, BOSS_TYPES));
        }
    }

    // ---- enchanting, mining and companions -----------------------------------------------------------------

    /** Items enchanted in total and per enchantment, the highest level applied, and the enchantment kinds. */
    void onItemEnchanted(ItemEnchanted e) {
        UUID p = e.player();
        String mod = e.mod();
        if (p == null || blank(mod)) {
            return;
        }
        addId(p, mod, ModKeys.ITEMS, e.enchantId(), 1);
        if (e.level() > 0) {
            sink.raise(p, key(mod, ModKeys.HIGHEST_LEVEL), e.level());
        }
        if (!blank(e.enchantId())) {
            sink.discover(p, prefix(mod, ENCHANT_KIND), e.enchantId(), key(mod, ENCHANT_KINDS));
        }
    }

    /** Activations in total and per enchantment. */
    void onEnchantmentTriggered(EnchantmentTriggered e) {
        UUID p = e.player();
        String mod = e.mod();
        if (p == null || blank(mod)) {
            return;
        }
        addId(p, mod, ModKeys.PROCS, e.enchantId(), 1);
    }

    /** One vein, its blocks in total and per block, and the largest vein. */
    void onVeinMined(VeinMined e) {
        UUID p = e.player();
        String mod = e.mod();
        if (p == null || blank(mod) || e.blocks() <= 0) {
            return;
        }
        sink.add(p, key(mod, ModKeys.VEINS), 1);
        addId(p, mod, ModKeys.BLOCKS, e.blockId(), e.blocks());
        sink.raise(p, key(mod, ModKeys.LARGEST), e.blocks());
    }

    /** Drops in total and per ore, and the longest streak. */
    void onLuckyDrop(LuckyDrop e) {
        UUID p = e.player();
        String mod = e.mod();
        if (p == null || blank(mod)) {
            return;
        }
        addId(p, mod, ModKeys.DROPS, e.blockId(), 1);
        if (e.streak() > 0) {
            sink.raise(p, key(mod, ModKeys.STREAK_BEST), e.streak());
        }
    }

    /** Tames and breedings per species with the species tamed as kinds, level ups with the best level, and losses. */
    void onCompanionChanged(CompanionChanged e) {
        UUID p = e.player();
        String mod = e.mod();
        if (p == null || blank(mod) || e.change() == null) {
            return;
        }
        switch (e.change()) {
            case TAMED -> {
                addId(p, mod, ModKeys.TAMED, e.npcTypeId(), 1);
                if (!blank(e.npcTypeId())) {
                    sink.discover(p, prefix(mod, SPECIES), e.npcTypeId(), key(mod, SPECIES));
                }
            }
            case BRED -> addId(p, mod, ModKeys.BRED, e.npcTypeId(), 1);
            case LEVEL_UP -> {
                sink.add(p, key(mod, ModKeys.COMPANION_LEVEL_UPS), 1);
                if (e.level() > 0) {
                    sink.raise(p, key(mod, ModKeys.COMPANION_LEVEL_BEST), e.level());
                }
            }
            case DIED -> sink.add(p, key(mod, ModKeys.COMPANIONS_LOST), 1);
            case RENAMED, MOUNTED, RELEASED -> {
                // nothing to count
            }
        }
    }
}
