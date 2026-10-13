package dev.ryvrook.feats.model;

/** A single stat threshold. A feat may require several of these at once. */
public record Requirement(String key, long target) {
    public Requirement {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("requirement key must not be blank");
        }
        if (target <= 0) {
            throw new IllegalArgumentException("requirement target must be positive for " + key);
        }
    }
}
