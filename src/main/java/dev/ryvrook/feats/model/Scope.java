package dev.ryvrook.feats.model;

/**
 * Who a feat belongs to. Personal feats are earned by each player on their own and are the only ones
 * counted in totals, points and completion. World feats are server milestones, reached once for the
 * whole server. First feats go to the one player who got somewhere before anyone else.
 */
public enum Scope {
    PERSONAL,
    WORLD,
    FIRST
}
