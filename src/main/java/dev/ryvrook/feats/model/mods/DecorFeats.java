package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.Category;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Requirement;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.model.Tier;
import dev.ryvrook.feats.stats.StatKeys;

import java.util.ArrayList;
import java.util.List;

import static dev.ryvrook.feats.model.FeatRegistry.Definer.req;
import static dev.ryvrook.feats.model.Tier.BRONZE;
import static dev.ryvrook.feats.model.Tier.GOLD;
import static dev.ryvrook.feats.model.Tier.PLATINUM;
import static dev.ryvrook.feats.model.Tier.SILVER;

/**
 * The Decor tab, the feats of five asset packs, see docs/PACKS-RESEARCH.md sections 3, 4, 6, 7 and 8: Sig's Fancy
 * Storage 1.6.3, Sig's Potion Shop 1.5.7, Blahaj Plushie 1.2.5, Infinite Water Supply 1.1.1 and Infinite Teleporters
 * 1.0.5. The registry calls {@link #define} with no section requirement and each feat names its own pack, so a pack
 * that is not loaded takes only its own feats out and the tab shows while any of the five is loaded. Every key is
 * one Feats counts itself, {@code crafted:}, {@code placed:} and {@code use:}. The tab is shared, so every icon is a
 * vanilla item id.
 */
public final class DecorFeats {
    private DecorFeats() {
    }

    static final String C = StatKeys.CRAFTED_PREFIX;

    // ---- Sig's Fancy Storage ----
    static final String FANCY_BENCH = C + "Sig_Benches_FancyStorage";
    static final List<String> PRODUCE = List.of(C + "Sig_Crate_Carrots", C + "Sig_Crate_Lettuce", C + "Sig_Crate_Tomato",
            C + "Sig_Crate_Corn", C + "Sig_Barrel_Apples");
    static final List<String> FISH_CRATES = List.of(C + "Sig_Crate_Raw_Fish", C + "Sig_Crate_Raw_Catfish",
            C + "Sig_Crate_Raw_Salmon", C + "Sig_Crate_Raw_Trout");
    static final List<String> GEMS = List.of(C + "Sig_Storage_Diamond", C + "Sig_Storage_Emerald", C + "Sig_Storage_Ruby",
            C + "Sig_Storage_Sapphire", C + "Sig_Storage_Topaz", C + "Sig_Storage_Voidstone", C + "Sig_Storage_Zephyr");
    // ---- Sig's Potion Shop ----
    static final String POTION_BENCH = C + "Sig_Benches_AlchemyProps";
    static final List<String> MERCHANT = List.of(C + "Sig_Armor_Merchant_Head", C + "Sig_Armor_Merchant_Chest",
            C + "Sig_Armor_Merchant_Hands", C + "Sig_Armor_Merchant_Legs", C + "Sig_Armor_Alchemy_Goggles");
    static final List<String> PETALS = List.of("Azure", "Black", "Blood", "Blue", "Cyan", "Green", "Orange", "Pink",
            "Purple", "Red", "Storm", "White", "Yellow").stream().map(c -> C + "Sig_Storage_Petals_" + c).toList();
    static final List<String> GRIMOIRES = List.of(C + "Sig_Magic_Grimoires_All", C + "Sig_Magic_Grimoires_All2",
            C + "Sig_Magic_Grimoires_EarthBOS", C + "Sig_Magic_Grimoires_FireIce");
    // ---- Blahaj Plushie ----
    static final String BLAHAJ_CRAFTED = C + "Deco_Blahaj_Plush";
    static final String BLAHAJ_PLACED = StatKeys.PLACED_PREFIX + "Deco_Blahaj_Plush";
    static final String BLAHAJ_POTION = C + "Potion_Morph_Blahaj";
    // ---- Infinite Water Supply ----
    static final String WELL_CRAFTED = C + "Bench_Water_Well";
    static final String WELL_USED = StatKeys.USE_PREFIX + "Bench_Water_Well";
    // ---- Infinite Teleporters ----
    static final String TELEPORTER_PLACED = StatKeys.PLACED_PREFIX + "Teleporter";

    /** Every key the Decor feats read. */
    public static final List<String> KEYS = keys();

    private static List<String> keys() {
        List<String> out = new ArrayList<>(List.of(FANCY_BENCH, POTION_BENCH, BLAHAJ_CRAFTED, BLAHAJ_PLACED, BLAHAJ_POTION,
                WELL_CRAFTED, WELL_USED, TELEPORTER_PLACED));
        out.addAll(PRODUCE);
        out.addAll(FISH_CRATES);
        out.addAll(GEMS);
        out.addAll(MERCHANT);
        out.addAll(PETALS);
        out.addAll(GRIMOIRES);
        return List.copyOf(out);
    }

    private static Requirement[] once(List<String> keys) {
        return keys.stream().map(k -> req(k, 1)).toArray(Requirement[]::new);
    }

    /** One Decor feat that exists only while {@code pack} is loaded. */
    private static void add(FeatRegistry.Definer d, Sibling pack, String id, String name, String desc, Tier tier, String icon,
                            boolean hidden, Requirement... reqs) {
        Feat feat = FeatRegistry.Definer.of(id, name, desc, Category.DECOR, tier, icon, hidden, reqs);
        d.define(feat.requiring(pack));
    }

    /** Adds the 15 Decor feats, each requiring its own pack. */
    public static void define(FeatRegistry.Definer d) {
        Sibling fancy = Sibling.FANCY_STORAGE;
        add(d, fancy, "decor_fancy_bench", "Fancy Storage", "Craft the Fancy Storage bench.", BRONZE, "Furniture_Village_Crate", false,
                req(FANCY_BENCH, 1));
        add(d, fancy, "decor_market_stall", "Market Stall", "Craft 5 produce crates.", SILVER, "Plant_Crop_Carrot_Item", false,
                once(PRODUCE));
        add(d, fancy, "decor_fish_market", "Fish Market", "Craft the 4 raw fish crates.", SILVER, "Food_Fish_Raw", false,
                once(FISH_CRATES));
        add(d, fancy, "decor_gem_vault", "Gem Vault", "Craft all 7 gem piles.", PLATINUM, "Deco_Treasure_Pile_Large", false,
                once(GEMS));

        Sibling potions = Sibling.POTION_SHOP;
        add(d, potions, "decor_potion_shop", "Potion Shop", "Craft the Alchemy Props bench.", BRONZE, "Bench_Alchemy", false,
                req(POTION_BENCH, 1));
        add(d, potions, "decor_merchant_outfit", "Merchant Outfit", "Craft all 5 pieces of the merchant outfit, the goggles included.",
                SILVER, "Armor_Cloth_Linen_Chest", false, once(MERCHANT));
        add(d, potions, "decor_petal_archive", "Petal Archive", "Craft all 13 petal storages.", GOLD, "Plant_Flower_Common_Pink", false,
                once(PETALS));
        add(d, potions, "decor_grimoires", "Grimoire Collection", "Craft the 4 grimoire sets.", SILVER, "Weapon_Spellbook_Grimoire_Brown",
                false, once(GRIMOIRES));

        Sibling blahaj = Sibling.BLAHAJ;
        add(d, blahaj, "decor_blahaj", "Shark Plushie", "Craft a Blahaj plushie.", BRONZE, "Fish_Shark_Hammerhead_Item", false,
                req(BLAHAJ_CRAFTED, 1));
        add(d, blahaj, "decor_blahaj_25", "Plushie Collection", "Place 25 Blahaj plushies.", SILVER, "Deco_Bat_Plush", false,
                req(BLAHAJ_PLACED, 25));
        add(d, blahaj, "decor_blahaj_potion", "Morph Potion", "Brew a Blahaj morph potion.", SILVER, "Potion_Health", true,
                req(BLAHAJ_POTION, 1));

        Sibling water = Sibling.INFINITE_WATER;
        add(d, water, "decor_well", "Well Built", "Craft a water well.", BRONZE, "Container_Bucket", false, req(WELL_CRAFTED, 1));
        add(d, water, "decor_well_used", "Well Used", "Use a water well 100 times.", SILVER, "Deco_Bucket", false, req(WELL_USED, 100));

        Sibling teleporters = Sibling.INFINITE_TELEPORTERS;
        add(d, teleporters, "decor_teleporters_10", "Teleporter Network", "Place 10 teleporters.", SILVER, "Teleporter", false,
                req(TELEPORTER_PLACED, 10));
        add(d, teleporters, "decor_teleporters_50", "Teleporter Grid", "Place 50 teleporters.", GOLD, "Teleporter", false,
                req(TELEPORTER_PLACED, 50));
    }
}
