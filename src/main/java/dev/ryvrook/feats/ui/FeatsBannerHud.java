package dev.ryvrook.feats.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * The keyed top of screen banner for rare unlocks, drawn from {@code Hud/Feats_Banner.ui}. The engine calls
 * {@link #build} when the HUD is added and {@link #push} sends a partial update into the same four slots.
 * Both run on the player's world thread, where {@code UnlockNotifier} posts them. Adding a second banner
 * under the same key replaces this one, so a fresh unlock swaps the content in place.
 */
public final class FeatsBannerHud extends CustomUIHud {
    public static final String KEY = "Ryvrook_Feats_Banner";
    static final String DOCUMENT = "Hud/Feats_Banner.ui";

    /**
     * One banner's content.
     *
     * @param icon        the vanilla item id drawn in the icon slot
     * @param name        the feat name
     * @param color       the hex colour of the name, normally the tier colour
     * @param description what the player did
     * @param why         the small line that says why the banner is on screen
     */
    public record Frame(String icon, String name, String color, String description, String why) {
        public Frame {
            icon = icon == null ? "" : icon;
            name = name == null ? "" : name;
            color = color == null || color.isBlank() ? "#d3d6db" : color;
            description = description == null ? "" : description;
            why = why == null ? "" : why;
        }
    }

    private volatile Frame frame;

    public FeatsBannerHud(PlayerRef player, Frame frame) {
        super(player, KEY);
        this.frame = frame;
    }

    /** The content last built or pushed. */
    public Frame frame() {
        return frame;
    }

    @Override
    protected void build(UICommandBuilder cmd) {
        cmd.append(DOCUMENT);
        fill(cmd, frame);
    }

    /** World thread. Sends the frame as a partial update. */
    public void push(Frame next) {
        frame = next;
        UICommandBuilder cmd = new UICommandBuilder();
        fill(cmd, next);
        update(false, cmd);
    }

    static void fill(UICommandBuilder cmd, Frame f) {
        if (f == null) {
            return;
        }
        cmd.set("#Icon.ItemId", f.icon());
        cmd.set("#Name.Text", f.name());
        cmd.set("#Name.Style.TextColor", f.color());
        cmd.set("#Desc.Text", f.description());
        cmd.set("#Why.Text", f.why());
    }
}
