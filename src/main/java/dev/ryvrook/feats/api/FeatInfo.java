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
 * @param banner      true when the unlock shows the top of screen banner whatever its rarity. Since API version 3
 * @param pack        the pack the feat belongs to, such as SOCIAL, or null for feats every server has. Since API version 3
 * @param requires    the plugin names the feat needs installed, such as Coinage, empty for vanilla. Since API version 3
 */
public record FeatInfo(String id, String name, String description, String category, String tier, boolean hidden,
                       int points, String scope, boolean banner, String pack, java.util.Set<String> requires) {
    public FeatInfo {
        scope = scope == null || scope.isEmpty() ? "PERSONAL" : scope;
        pack = pack == null || pack.isEmpty() ? null : pack;
        requires = requires == null ? java.util.Set.of() : java.util.Set.copyOf(requires);
    }

    /** The form from before banners, packs and sibling requirements, API version 2 and older. */
    public FeatInfo(String id, String name, String description, String category, String tier, boolean hidden,
                    int points, String scope) {
        this(id, name, description, category, tier, hidden, points, scope, false, null, java.util.Set.of());
    }

    /** The form from before scopes existed, describing a personal feat. */
    public FeatInfo(String id, String name, String description, String category, String tier, boolean hidden,
                    int points) {
        this(id, name, description, category, tier, hidden, points, "PERSONAL");
    }
}
