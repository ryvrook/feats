package dev.ryvrook.feats.api;

/**
 * One feat as the API describes it. Part of the stable Feats API.
 *
 * @param id          stable id stored in player files
 * @param name        display name
 * @param description what the player has to do
 * @param category    category display name, for example Mining
 * @param tier        BRONZE, SILVER, GOLD or PLATINUM
 * @param hidden      true when the browser shows the feat as ??? until it is unlocked
 * @param points      points the feat is worth
 * @param scope       PERSONAL for feats each player earns, WORLD for server milestones and FIRST for
 *                    server firsts. Only PERSONAL feats count toward the totals
 */
public record FeatInfo(String id, String name, String description, String category, String tier, boolean hidden,
                       int points, String scope) {
    public FeatInfo {
        scope = scope == null || scope.isEmpty() ? "PERSONAL" : scope;
    }

    /** The form from before scopes existed, describing a personal feat. */
    public FeatInfo(String id, String name, String description, String category, String tier, boolean hidden,
                    int points) {
        this(id, name, description, category, tier, hidden, points, "PERSONAL");
    }
}
