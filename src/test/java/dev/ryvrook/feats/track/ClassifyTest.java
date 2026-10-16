package dev.ryvrook.feats.track;

import dev.ryvrook.feats.stats.StatKeys;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassifyTest {
    @Test
    void blockBrokenGroups() {
        assertEquals(List.of("blocks_broken"), Classify.blockBroken(null));
        assertEquals(List.of("blocks_broken"), Classify.blockBroken(""));
        assertEquals(List.of("blocks_broken", "broken:Ore_Copper_Stone", "ore_mined", "ore:Copper"),
                Classify.blockBroken("Ore_Copper_Stone"));
        assertEquals(List.of("blocks_broken", "broken:Wood_Oak_Trunk", "wood_chopped"),
                Classify.blockBroken("Wood_Oak_Trunk"));
        assertEquals(List.of("blocks_broken", "broken:Rock_Crystal_Void_Large", "broken:void_crystal"),
                Classify.blockBroken("Rock_Crystal_Void_Large"));
        assertEquals(List.of("blocks_broken", "broken:Soil_Dirt"), Classify.blockBroken("Soil_Dirt"));
    }

    @Test
    void blockPlacedOldGroups() {
        assertEquals(List.of("blocks_placed"), Classify.blockPlaced(null));
        assertEquals(List.of("blocks_placed", "placed:Furniture_Crude_Torch", "placed:torch", "placed:furniture"),
                Classify.blockPlaced("Furniture_Crude_Torch"));
        assertEquals(List.of("blocks_placed", "placed:Furniture_Crude_Bed", "placed:bed", "placed:furniture"),
                Classify.blockPlaced("Furniture_Crude_Bed"));
        assertEquals(List.of("blocks_placed", "placed:Furniture_Goblin_Chest", "placed:chest", "placed:furniture",
                "placed:goblin_furniture"), Classify.blockPlaced("Furniture_Goblin_Chest"));
        assertEquals(List.of("blocks_placed", "placed:Bench_WorkBench", "placed:bench"),
                Classify.blockPlaced("Bench_WorkBench"));
        assertEquals(List.of("blocks_placed", "placed:Plant_Crop_Wheat", "placed:crop"),
                Classify.blockPlaced("Plant_Crop_Wheat"));
        assertEquals(List.of("blocks_placed", "placed:Goblin_Painting_Duke_Replica", "placed:goblin_painting"),
                Classify.blockPlaced("Goblin_Painting_Duke_Replica"));
        assertEquals(List.of("blocks_placed", "placed:Metal_Iron_Block", "placed:metal"),
                Classify.blockPlaced("Metal_Iron_Block"));
        assertEquals(List.of("blocks_placed", "placed:Deco_Lantern", "placed:lantern"),
                Classify.blockPlaced("Deco_Lantern"));
    }

    @Test
    void blockPlacedMaterialGroups() {
        assertTrue(Classify.blockPlaced("Glass_Test_Blue").contains("placed:glass"));
        assertTrue(Classify.blockPlaced("Furniture_Crude_Window").contains("placed:glass"));
        assertTrue(Classify.blockPlaced("Furniture_Cybercity_Windows_Full").contains("placed:glass"));
        assertEquals(List.of("blocks_placed", "placed:Rock_Stone", "placed:stone"), Classify.blockPlaced("Rock_Stone"));
        assertTrue(Classify.blockPlaced("Rock_Stone_Brick").contains("placed:stone"));
        assertTrue(Classify.blockPlaced("Rock_Sandstone_Brick_Ornate").contains("placed:stone"));
        assertEquals(List.of("blocks_placed", "placed:Wood_Hardwood_Planks", "placed:wood"),
                Classify.blockPlaced("Wood_Hardwood_Planks"));
        assertEquals(List.of("blocks_placed", "placed:Plant_Sapling_Oak", "placed:sapling"),
                Classify.blockPlaced("Plant_Sapling_Oak"));
        assertTrue(Classify.blockPlaced("Plant_Sapling_Spruce_Frozen").contains("placed:sapling"));
        // Neighbours that must stay out of the groups
        assertFalse(Classify.blockPlaced("Soil_Dirt").contains("placed:stone"));
        assertFalse(Classify.blockPlaced("Ore_Copper_Stone").contains("placed:stone"));
        assertFalse(Classify.blockPlaced("Furniture_Crude_Table").contains("placed:wood"));
        assertFalse(Classify.blockPlaced("Plant_Crop_Wheat").contains("placed:sapling"));
        assertFalse(Classify.blockPlaced("Tool_Spyglass").contains("placed:glass"));
    }

    @Test
    void craftedGroups() {
        assertEquals(List.of("crafted", "crafted:Tool_Pickaxe_Iron", "craft:pickaxe", "craft:tool"),
                Classify.crafted("Tool_Pickaxe_Iron"));
        assertEquals(List.of("crafted", "crafted:Weapon_Sword_Adamantite", "craft:weapon", "craft:weapon_sword",
                "craft:weapon_adamantite"), Classify.crafted("Weapon_Sword_Adamantite"));
        assertEquals(List.of("crafted", "crafted:Glider_Scrap", "craft:gadget"), Classify.crafted("Glider_Scrap"));
        assertEquals(List.of("crafted", "crafted:Goblin_Painting_Duke_Replica", "craft:painting"),
                Classify.crafted("Goblin_Painting_Duke_Replica"));
        assertEquals(List.of("crafted"), Classify.crafted(null));
    }

    @Test
    void pickupObtainedAndConsumed() {
        assertEquals(List.of("picked_up", "picked:Fish_Trout_Item", "fish_caught"), Classify.pickedUp("Fish_Trout_Item"));
        assertEquals(List.of("picked_up", "picked:Plant_Crop_Wheat_Item", "harvested"),
                Classify.pickedUp("Plant_Crop_Wheat_Item"));
        assertEquals(List.of("picked_up", "picked:Lore_Note_1", "picked:lore"), Classify.pickedUp("Lore_Note_1"));
        assertEquals(List.of("obtained", "obtained:Ingredient_Stick"), Classify.obtained("Ingredient_Stick"));
        assertEquals(List.of("obtained"), Classify.obtained(null));
        assertEquals("obtained:Ingredient_Stick", Classify.obtainedKey("Ingredient_Stick"));
        assertEquals("stash:Ingredient_Stick", Classify.stashKey("Ingredient_Stick"));
        assertEquals(List.of("food_eaten"), Classify.consumed("Food_Apple"));
        assertEquals(List.of("potions_drunk"), Classify.consumed("Potion_Health"));
        assertEquals(List.of(), Classify.consumed("Potion_Empty"));
        assertEquals(List.of(), Classify.consumed(null));
    }

    @Test
    void blockUseAndInteraction() {
        assertEquals(List.of("use:GoblinLab_Tinkering_Bench"), Classify.blockUsed("*GoblinLab_Tinkering_Bench_Recipeset_A"));
        assertEquals(List.of("use:Goblin_Gobliterator_Difficulty_Button"),
                Classify.blockUsed("Goblin_Gobliterator_Difficulty_Button_Active"));
        assertEquals(List.of("use:chest"), Classify.blockUsed("Furniture_Crude_Chest_Small"));
        assertEquals(List.of("use:bench", "use:Bench_WorkBench"), Classify.blockUsed("Bench_WorkBench"));
        assertEquals(List.of(), Classify.blockUsed(null));
        assertEquals(List.of("ability_casts", "cast:Fireball"), Classify.interaction("Root_Ability_Fireball"));
        assertEquals(List.of("use_spyglass"), Classify.interaction("Root_Spyglass_Zoom"));
        assertEquals(List.of(), Classify.interaction("Root_Attack_Sword"));
        assertEquals(List.of(), Classify.interaction("Attack"));
    }

    @Test
    void killGroups() {
        assertEquals(List.of("kills"), Classify.kill(null, false));
        assertEquals(List.of("kills", "kills_ranged"), Classify.kill("", true));
        assertEquals(List.of("kills", "kill:Player", "kills_player"), Classify.kill("Player", false));
        assertEquals(List.of("kills", "kills_ranged", "kill:Goblin_Scrapper", "kills_goblin", "kills_goblin_scrapper"),
                Classify.kill("Goblin_Scrapper", true));
        assertEquals(List.of("kills", "kill:Tamed_Cow_Calf", "kills_livestock"), Classify.kill("Tamed_Cow_Calf", false));
        assertEquals(List.of("kills", "kill:Crawler_Void_Surge", "kills_void", "kills_void_crawler"),
                Classify.kill("Crawler_Void_Surge", false));
        List<String> boss = Classify.kill("Goblin_Gobliterator_Phase3_HM", false);
        assertTrue(boss.containsAll(List.of("kills_goblin", "kills_gobliterator", "kills_gobliterator_hm", "kills_boss")));
        assertTrue(Classify.kill("Bear_Voidtaken", false).containsAll(List.of("kills_predator", "kills_boss", "kills_void")));
        assertTrue(Classify.kill("Skeleton_Elite_Phase_2", false).containsAll(List.of("kills_undead", "kills_revenant",
                "kills_boss")));
    }

    @Test
    void memoReturnsOneSharedImmutableList() {
        List<String> first = Classify.blockBroken("Rock_Basalt");
        assertSame(first, Classify.blockBroken("Rock_Basalt"));
        assertSame(Classify.kill("Spider", true), Classify.kill("Spider", true));
        assertSame(Classify.obtainedKey("Food_Apple"), Classify.obtainedKey("Food_Apple"));
        assertThrows(UnsupportedOperationException.class, () -> first.add("x"));
        assertTrue(Classify.memoised() > 0 && Classify.memoised() < Classify.MEMO_LIMIT * 9);
    }

    @Test
    void woodSpeciesAndSeeds() {
        assertEquals("Oak", Classify.woodSpecies("Wood_Oak_Trunk"));
        assertEquals("Oak", Classify.woodSpecies("Wood_Oak_Trunk_Stairs"));
        assertEquals("Bamboo", Classify.woodSpecies("Wood_Bamboo_Trunk_Full_Deco"));
        assertNull(Classify.woodSpecies("Wood_Hardwood_Planks"));
        assertNull(Classify.woodSpecies("Rock_Stone"));
        assertNull(Classify.woodSpecies(null));
        assertEquals(List.of("seeds_planted", "seed:Wheat"), Classify.seedPlanted("Plant_Seeds_Wheat"));
        assertEquals(List.of("seeds_planted", "seed:Chilli"), Classify.seedPlanted("Plant_Seeds_Chilli"));
        assertEquals(List.of(), Classify.seedPlanted("Plant_Seeds_Wheat_Eternal"));
        assertEquals(List.of(), Classify.seedPlanted("Plant_Seeds_Test_Tree"));
        assertEquals(List.of(), Classify.seedPlanted("Plant_Crop_Wheat"));
        assertEquals(List.of(), Classify.seedPlanted(null));
    }

    @Test
    void tamedKindsFishAndWear() {
        assertEquals(List.of("tamed", "tamed:Tamed_Cow"), Classify.tamed("Tamed_Cow"));
        assertEquals(List.of("tamed", "tamed:Tamed_Cow"), Classify.tamed("Tamed_Cow_Calf"));
        assertEquals(List.of("tamed", "tamed:Tamed_Chicken_Desert"), Classify.tamed("Tamed_Chicken_Desert_Chick"));
        assertEquals(List.of("tamed", "tamed:Tamed_Pig_Wild"), Classify.tamed("Tamed_Pig_Wild_Piglet"));
        assertEquals(List.of("tamed", "tamed:Tamed_Mosshorn_Plain"), Classify.tamed("Tamed_Mosshorn_Plain"));
        assertEquals(List.of("tamed"), Classify.tamed(null));
        assertTrue(Classify.isRareFish("Fish_Whale_Humpback_Item"));
        assertTrue(Classify.isRareFish("Fish_Jellyfish_Man_Of_War_Item"));
        assertFalse(Classify.isRareFish("Fish_Trout_Rainbow_Item"));
        assertFalse(Classify.isRareFish("Fish_Crab"));
        assertFalse(Classify.isRareFish(null));
        assertEquals(List.of("tools_broken", "tool_broken:Tool_Pickaxe_Iron"), Classify.toolBroken("Tool_Pickaxe_Iron"));
        assertEquals(List.of("tools_broken", "tool_broken:Weapon_Sword_Iron"), Classify.toolBroken("Weapon_Sword_Iron"));
        assertEquals(List.of("tools_broken", "tool_broken:Armor_Iron_Chest"), Classify.toolBroken("Armor_Iron_Chest"));
        assertEquals(List.of(), Classify.toolBroken("Tool_Watering_Can"));
        assertEquals(List.of(), Classify.toolBroken("Tool_Fertilizer_Crystal"));
        assertEquals(List.of(), Classify.toolBroken("Tool_Repair_Kit_Iron"));
        assertEquals(List.of(), Classify.toolBroken("Ingredient_Stick"));
        assertEquals(List.of(), Classify.toolBroken(null));
        assertSame(Classify.talkKey("Kweebec_Elder"), Classify.talkKey("Kweebec_Elder"));
        assertEquals("talk:Kweebec_Elder", Classify.talkKey("Kweebec_Elder"));
        assertEquals("mount:Tamed_Horse", Classify.mountKey("Tamed_Horse"));
    }

    @Test
    void farmingRootsAndCoops() {
        assertEquals(List.of("waterings"), Classify.interaction("Watering_Can_Filled_Use"));
        assertEquals(List.of(), Classify.interaction("Watering_Can_Empty_Use"));
        assertEquals(List.of(), Classify.interaction("Watering_Can_Fill"));
        assertEquals(List.of("fertilizings"), Classify.interaction("Fertilizer_Use"));
        assertEquals(List.of("fertilizings"), Classify.interaction("Fertilizer_Crystal_Use"));
        assertEquals(List.of(), Classify.interaction("Root_ChangeFarmingStage_Set"), "trap bait needs the held item");
        assertEquals(List.of(), Classify.interaction("Block_Secondary"));
        assertEquals(List.of("coop_uses"), Classify.blockUsed("Coop_Chicken"));
        assertEquals(List.of("produce_collected"), Classify.blockUsed("*Coop_Chicken_Produce_Ready"));
    }

    @Test
    void portalKeys() {
        assertEquals(List.of(StatKeys.PORTALS_ENTERED, "portal:The_Deepworks"), Classify.portal("The_Deepworks"));
        assertEquals(List.of(StatKeys.PORTALS_ENTERED), Classify.portal(null));
    }
}
