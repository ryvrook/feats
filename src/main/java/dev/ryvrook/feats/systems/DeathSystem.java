package dev.ryvrook.feats.systems;

import com.hypixel.hytale.builtin.portals.resources.PortalWorld;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.portalworld.PortalType;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.BossLedger;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Fires once per death of any entity and records player deaths and player kills. A boss kill credits
 * every player the {@link BossLedger} saw hit it in the last minute, as well as the killer, and an
 * untouched boss kill to each of them the ledger never saw take damage during the fight. Every credited
 * kill extends the player's kill streak and a death ends it.
 */
public final class DeathSystem extends DeathSystems.OnDeathSystem {
    private final FeatsTracker tracker;
    private final BossLedger ledger;
    private final DeepworksRuns runs;

    public DeathSystem(FeatsTracker tracker) {
        this.tracker = tracker;
        this.ledger = BossLedger.of(tracker);
        this.runs = DeepworksRuns.of(tracker);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Override
    public void onComponentAdded(Ref<EntityStore> ref, DeathComponent death, Store<EntityStore> store,
                                 CommandBuffer<EntityStore> commandBuffer) {
        try {
            track(ref, death, store);
        } catch (RuntimeException | LinkageError e) {
            PlayerEventSystem.failed(e);
        }
    }

    private void track(Ref<EntityStore> ref, DeathComponent death, Store<EntityStore> store) {
        PlayerRef victimPlayer = store.getComponent(ref, PlayerRef.getComponentType());
        Damage info = death.getDeathInfo();

        if (victimPlayer != null) {
            DamageCause damageCause = death.getDeathCause();
            String cause = damageCause != null ? damageCause.getId() : null;
            tracker.incrementAll(victimPlayer.getUuid(), Classify.death(cause, effectId(info)), 1);
            tracker.countDeath(victimPlayer.getUuid());
            if (isDeepworks(store)) {
                runs.died(victimPlayer.getUuid(), PlayerEventSystem.worldName(store));
            }
        }

        PlayerRef killerPlayer = null;
        boolean ranged = false;
        if (info != null && info.getSource() instanceof Damage.EntitySource source) {
            Ref<EntityStore> killer = source.getRef();
            if (killer != null && killer.isValid() && killer.getStore() == store && !killer.equals(ref)) {
                killerPlayer = store.getComponent(killer, PlayerRef.getComponentType());
                ranged = source instanceof Damage.ProjectileSource;
            }
        }

        String victimType;
        if (victimPlayer != null) {
            victimType = "Player";
        } else {
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            victimType = npc != null ? npc.getNPCTypeId() : null;
        }

        if (victimPlayer == null && Classify.isBoss(victimType)) {
            creditBoss(ref, store, victimType, killerPlayer != null ? killerPlayer.getUuid() : null, ranged);
            return;
        }
        if (killerPlayer != null) {
            tracker.incrementAll(killerPlayer.getUuid(), Classify.kill(victimType, ranged), 1);
            tracker.countKill(killerPlayer.getUuid());
        }
    }

    /**
     * The id of the status effect whose damage killed, such as Lava_Burn, or null when the death came
     * from anything else. Effect damage carries the effect as its source, wrapped in an entity source
     * when the effect has a living owner.
     */
    static String effectId(Damage info) {
        if (info == null) {
            return null;
        }
        try {
            Damage.Source source = info.getSource();
            ActiveEntityEffect effect = source instanceof Damage.EntityEffectSource owned ? owned.getEffect()
                    : source instanceof ActiveEntityEffect bare ? bare : null;
            if (effect == null) {
                return null;
            }
            EntityEffect asset = EntityEffect.getAssetMap().getAsset(effect.getEntityEffectIndex());
            return asset != null ? asset.getId() : null;
        } catch (RuntimeException | LinkageError e) {
            return null; // the cause key still counts, only the effect key is lost
        }
    }

    /**
     * Credits a boss kill to the killer and to everyone who hit the boss within the ledger window, then
     * clears the boss from the ledger. Only the killer gets the ranged kill, since that is about the
     * killing blow.
     */
    private void creditBoss(Ref<EntityStore> ref, Store<EntityStore> store, String bossType, UUID killer, boolean ranged) {
        Set<UUID> credited = new LinkedHashSet<>();
        if (killer != null) {
            credited.add(killer);
        }
        UUIDComponent id = store.getComponent(ref, UUIDComponent.getComponentType());
        BossLedger.Outcome fight = BossLedger.Outcome.NONE;
        if (id != null && store.isInThread()) {
            fight = ledger.finish(id.getUuid(), System.nanoTime());
            credited.addAll(fight.credited());
        }
        if (credited.isEmpty()) {
            return;
        }
        for (UUID player : credited) {
            boolean blow = player.equals(killer);
            tracker.incrementAll(player, Classify.kill(bossType, blow && ranged), 1);
            tracker.countKill(player);
            if (fight.untouched(player)) {
                tracker.increment(player, StatKeys.BOSS_FLAWLESS, 1);
            }
        }
        if (Classify.isGobliteratorFinal(bossType)) {
            creditGobliterator(store, bossType, credited);
        }
    }

    /** The Gobliterator challenge feats, read for each credited player who is still in this world. */
    private void creditGobliterator(Store<EntityStore> store, String bossType, Set<UUID> credited) {
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (world == null || !store.isInThread()) {
            return;
        }
        boolean hard = Classify.isGobliteratorFinalHard(bossType);
        boolean deepworks = isDeepworks(store);
        String worldName = PlayerEventSystem.worldName(store);
        long now = System.currentTimeMillis();
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            UUID player = playerRef.getUuid();
            if (player == null || !credited.contains(player)) {
                continue;
            }
            if (deepworks && runs.isClean(player, worldName)) {
                tracker.increment(player, StatKeys.GOBLITERATOR_DEATHLESS, 1);
            }
            if (deepworks && runs.isFast(player, worldName, now)) {
                tracker.increment(player, StatKeys.GOBLITERATOR_FAST, 1);
            }
            if (hard && wearsNoArmour(store, playerRef.getReference())) {
                tracker.increment(player, StatKeys.GOBLITERATOR_HM_UNARMOURED, 1);
            }
        }
    }

    private static boolean wearsNoArmour(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid() || ref.getStore() != store) {
            return false;
        }
        InventoryComponent.Armor armor = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        ItemContainer slots = armor != null ? armor.getInventory() : null;
        return slots != null && slots.isEmpty();
    }

    /** True when this store is a Deepworks instance. A failed read counts as no, so kills still count. */
    private static boolean isDeepworks(Store<EntityStore> store) {
        try {
            PortalWorld portal = store.getResource(PortalWorld.getResourceType());
            if (portal == null || !portal.exists()) {
                return false;
            }
            PortalType type = portal.getPortalType();
            return type != null && DeepworksRuns.PORTAL_TYPE.equals(type.getId());
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
