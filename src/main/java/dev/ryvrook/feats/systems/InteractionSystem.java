package dev.ryvrook.feats.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.interaction.event.InteractionChainStartEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.util.AbilityCastUtil;
import com.hypixel.hytale.server.core.modules.projectile.component.ImpactModifiers;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;

import java.util.List;
import java.util.UUID;

/**
 * Counts ability casts, spyglass use and the farming tools from the interaction chains a player starts.
 * This fires for every chain (attacks, block use and so on), so it only looks at the root id and returns
 * early for anything it does not know.
 *
 * <p>Farming. A filled watering can's Secondary root is Watering_Can_Filled_Use, which places the can as
 * a block when crouching and otherwise tries a refill and then waters, so it counts as a watering when the
 * player is not crouching [inferred]. Fertilizer_Use and Fertilizer_Crystal_Use are the fertilizer bags.
 * Trap bait shares the Root_ChangeFarmingStage_Set root with other stage tools, so it only counts with
 * Tool_Trap_Bait in hand [inferred, the root ids come from the item json in the game assets].
 *
 * <p>The engine posts this event before the chain runs, after its own cooldown check. An ability
 * chain then checks the mana cost and plays a fail effect if the player cannot pay, so a cast only
 * counts here when the player can pay every cost the ability asks for, compared the same way the
 * engine's StatsCondition does it (current stat value at least the cost).
 */
public final class InteractionSystem extends PlayerEventSystem<InteractionChainStartEvent> {
    public InteractionSystem(FeatsTracker tracker) {
        super(InteractionChainStartEvent.class, tracker);
    }

    @Override
    protected void handlePlayer(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store, InteractionChainStartEvent event) {
        if (event.isCancelled() || !isPlayerAction(event.getType())) {
            return;
        }
        String root = event.getRootInteractionId();
        if (Classify.FARMING_STAGE_ROOT.equals(root)) {
            if (holding(event.getContext(), Classify.TRAP_BAIT)) {
                tracker.increment(uuid, StatKeys.TRAPS_BAITED, 1);
            }
            return;
        }
        List<String> keys = Classify.interaction(root);
        if (keys.isEmpty()) {
            return;
        }
        if (keys.contains(StatKeys.ABILITY_CASTS) && !canAfford(event.getContext(), ref, store)) {
            return;
        }
        if (keys.contains(StatKeys.WATERINGS) && crouching(ref, store)) {
            return; // crouching with the can places it as a block instead
        }
        tracker.incrementAll(uuid, keys, 1);
    }

    /** True when the chain started with the given item in hand. */
    private static boolean holding(InteractionContext context, String itemId) {
        ItemStack held = context != null ? context.getHeldItem() : null;
        return held != null && !held.isEmpty() && itemId.equals(held.getItemId());
    }

    /** True when the client reports the player crouching. A missing component counts as standing. */
    private static boolean crouching(Ref<EntityStore> ref, Store<EntityStore> store) {
        MovementStatesComponent states = store.getComponent(ref, MovementStatesComponent.getComponentType());
        return states != null && states.getMovementStates() != null && states.getMovementStates().crouching;
    }

    /**
     * Mirrors the ability chain's opening StatsCondition. AbilityCastUtil.resolveCostMap returns the
     * cost per stat index, or null when the ability is free or the player is in Creative. The engine
     * applies cast modifiers (such as the Thrift rune) from a snapshot taken on this same event, so
     * when that snapshot is not there yet the same modifiers are collected here.
     */
    private static boolean canAfford(InteractionContext context, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (context == null) {
            return false;
        }
        Int2FloatMap costs = AbilityCastUtil.resolveCostMap(context, false);
        if (costs == null || costs.isEmpty()) {
            return true;
        }
        float multiplier = 1f;
        if (context.getMetaStore().getIfPresentMetaObject(ImpactModifiers.CAST_SNAPSHOT) == null) {
            ImpactModifiers modifiers = ImpactModifiers.collect(context, ref, store);
            if (modifiers != null) {
                multiplier = modifiers.getAttribute(AbilityCastUtil.COST_ATTRIBUTE, 1f);
            }
        }
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) {
            return false;
        }
        for (Int2FloatMap.Entry cost : costs.int2FloatEntrySet()) {
            EntityStatValue value = stats.get(cost.getIntKey());
            if (value == null || value.get() < cost.getFloatValue() * multiplier) {
                return false;
            }
        }
        return true;
    }

    /**
     * Only chains the player starts directly count. Follow up chains such as projectile hits, ability
     * charge activations or held and equipped effects use other types and would count one cast twice.
     */
    private static boolean isPlayerAction(InteractionType type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case Primary, Secondary, Ability1, Ability2, Ability3, Ability4, Use -> true;
            default -> false;
        };
    }
}
