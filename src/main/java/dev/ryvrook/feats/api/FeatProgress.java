package dev.ryvrook.feats.api;

/**
 * How far a player is toward one feat. For feats with several requirements both numbers are sums of
 * the requirements, each capped at its target, the same as the browser's progress bar. Part of the
 * stable Feats API.
 *
 * @param current progress so far, equal to {@code target} once unlocked
 * @param target  what the feat asks for
 */
public record FeatProgress(long current, long target) {
}
