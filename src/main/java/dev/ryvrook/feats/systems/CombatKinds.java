package dev.ryvrook.feats.systems;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns an NPC role id into the creature kind it belongs to, for the {@code kill_kind:} counters. Roles
 * come in behaviour variants such as {@code Skeleton_Scout_Patrol} and {@code Skeleton_Scout_Wander},
 * numbered patrols such as {@code Temple_Kweebec_Razorleaf_Patrol5}, and boss phases such as
 * {@code Goblin_Duke_Phase_2} or {@code Goblin_Gobliterator_Phase3_HM}. All of those are one kind. The
 * result is remembered per role id, so a kill costs one map lookup.
 */
public final class CombatKinds {
    private CombatKinds() {}

    static final int MEMO_LIMIT = 4096;
    private static final String PHASE = "_Phase";
    private static final String[] VARIANTS = {"Patrol", "Wander"};
    private static final ConcurrentHashMap<String, String> MEMO = new ConcurrentHashMap<>();

    /** The kind of a role, or null when the id is empty. */
    public static String kind(String roleId) {
        if (roleId == null || roleId.isEmpty()) {
            return null;
        }
        String hit = MEMO.get(roleId);
        if (hit != null) {
            return hit;
        }
        String kind = baseRole(roleId);
        if (MEMO.size() < MEMO_LIMIT) {
            MEMO.putIfAbsent(roleId, kind);
        }
        return kind;
    }

    /** The role without its phase and without trailing patrol or wander segments. Pure, see {@link #kind}. */
    static String baseRole(String roleId) {
        String id = roleId;
        int phase = id.indexOf(PHASE);
        if (phase > 0) {
            id = id.substring(0, phase);
        }
        while (true) {
            int cut = id.lastIndexOf('_');
            if (cut <= 0 || !isVariant(id, cut + 1)) {
                break;
            }
            id = id.substring(0, cut);
        }
        return id.isEmpty() ? roleId : id;
    }

    /** True when the segment starting at {@code from} is Patrol or Wander, with or without a number. */
    private static boolean isVariant(String id, int from) {
        for (String variant : VARIANTS) {
            if (id.startsWith(variant, from)) {
                for (int i = from + variant.length(); i < id.length(); i++) {
                    char c = id.charAt(i);
                    if (c < '0' || c > '9') {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }
}
