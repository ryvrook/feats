package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.StatKeys;

import java.util.List;

import static dev.ryvrook.feats.model.Category.STORAGE;
import static dev.ryvrook.feats.model.Tier.BRONZE;
import static dev.ryvrook.feats.model.Tier.GOLD;
import static dev.ryvrook.feats.model.Tier.PLATINUM;
import static dev.ryvrook.feats.model.Tier.SILVER;

/**
 * The Modern Storage feats. Modern Storage has no binding, so every feat reads a {@code crafted:} or {@code placed:}
 * key Feats counts itself on either tracking path, with the item ids of Modern Storage 1.0.33-U7. The registry calls
 * {@link #define} with {@code Sibling.STORAGE} alone as the section requirement, TrackerAPI is not needed. Personal
 * feats sit in {@code Category.STORAGE}. The keys are per id keys, kept because these feats watch them.
 */
public final class StorageFeats {
    private StorageFeats() {
    }

    // ---- every key the feats read, in one place ----
    static final String BENCH = StatKeys.CRAFTED_PREFIX + "ModernStorage_modern_bench";
    static final String CONTROLLER = StatKeys.PLACED_PREFIX + "ModernStorage_controller";
    static final String CABLE = StatKeys.PLACED_PREFIX + "ModernStorage_cable";
    static final String CRAFTING_TERMINAL = StatKeys.CRAFTED_PREFIX + "ModernStorage_crafting_terminal";
    static final String DISK_DRIVE = StatKeys.PLACED_PREFIX + "ModernStorage_disk_drive";
    static final String DISK_64K = StatKeys.CRAFTED_PREFIX + "ModernStorage_storage_disk_64k";
    static final String WIRELESS_CRAFTING = StatKeys.CRAFTED_PREFIX + "ModernStorage_wireless_crafting_terminal";
    static final String INFINITE_RANGE = StatKeys.CRAFTED_PREFIX + "ModernStorage_infinite_range_card";

    public static final List<String> KEYS = List.of(BENCH, CONTROLLER, CABLE, CRAFTING_TERMINAL, DISK_DRIVE, DISK_64K,
            WIRELESS_CRAFTING, INFINITE_RANGE);

    /** Adds the 8 Storage feats. */
    public static void define(FeatRegistry.Definer d) {
        d.feat("storage_bench", "Modern Storage", "Craft the Modern Storage Workbench.", STORAGE, BRONZE, "Bench_WorkBench", BENCH, 1);
        d.feat("storage_controller", "Network Online", "Place a storage controller.", STORAGE, BRONZE, "Furniture_Village_Crate", CONTROLLER, 1);
        d.feat("storage_cables", "Cable Run", "Place 256 storage cables.", STORAGE, SILVER, "Metal_Copper_Pipe_Long", CABLE, 256);
        d.feat("storage_crafting_terminal", "Crafting Terminal", "Craft a crafting terminal.", STORAGE, SILVER, "Furniture_Ancient_Crate", CRAFTING_TERMINAL, 1);
        d.feat("storage_disk_drives", "Disk Array", "Place 8 disk drives.", STORAGE, GOLD, "Furniture_Tavern_Chest_Large", DISK_DRIVE, 8);
        d.feat("storage_disk_64k", "Largest Disk", "Craft a 64k storage disk.", STORAGE, GOLD, "Furniture_Ancient_Chest_Large", DISK_64K, 1);
        d.feat("storage_wireless", "Wireless Access", "Craft a wireless crafting terminal.", STORAGE, GOLD, "Ingredient_Crystal_Blue", WIRELESS_CRAFTING, 1);
        d.feat("storage_infinite_range", "Infinite Range", "Craft an infinite range card.", STORAGE, PLATINUM, "Ingredient_Crystal_Void", INFINITE_RANGE, 1);
    }
}
