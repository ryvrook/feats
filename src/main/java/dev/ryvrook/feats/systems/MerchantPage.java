package dev.ryvrook.feats.systems;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.logging.Level;

/**
 * Names the merchant a player is trading with. A barter trade runs inside the engine's BarterPage, a
 * custom UI page the player has open, and the page keeps its BarterShopAsset in a private field with no
 * getter, so the shop id is read through a method handle made once. The shop ids in 0.7.1 are
 * Klops_Merchant, Kweebec_Merchant and Dungeon_Klops_Scrap_Trader [inferred, from the BarterShops folder
 * of the game assets]. Everything here is guarded, a server without the barter classes switches the read
 * off with one warning and a page whose asset cannot be read names the merchant {@link #UNKNOWN}.
 */
final class MerchantPage {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PAGE_CLASS = "com.hypixel.hytale.builtin.adventure.shop.barter.BarterPage";
    private static final String ASSET_CLASS = "com.hypixel.hytale.builtin.adventure.shop.barter.BarterShopAsset";
    /** The merchant id used when the page is a barter page but its shop asset cannot be read. */
    static final String UNKNOWN = "Barter";

    private static volatile boolean off;
    private static volatile Class<?> pageClass;
    private static volatile MethodHandle shopAsset;
    private static volatile MethodHandle shopId;

    private MerchantPage() {}

    /** The shop id of the barter page the player has open, or null when no barter page is open. */
    static String merchantOf(Player player) {
        if (off || player == null) {
            return null;
        }
        try {
            PageManager pages = player.getPageManager();
            CustomUIPage page = pages != null ? pages.getCustomPage() : null;
            if (page == null) {
                return null;
            }
            Class<?> barter = pageClass();
            if (barter == null || !barter.isInstance(page)) {
                return null;
            }
            MethodHandle asset = shopAsset;
            MethodHandle id = shopId;
            if (asset == null || id == null) {
                return UNKNOWN;
            }
            Object shop = asset.invoke(page);
            Object name = shop != null ? id.invoke(shop) : null;
            return name instanceof String s && !s.isEmpty() ? s : UNKNOWN;
        } catch (Throwable e) {
            if (e instanceof Error error && !(error instanceof LinkageError)) {
                throw error;
            }
            switchOff(e);
            return null;
        }
    }

    private static Class<?> pageClass() {
        Class<?> barter = pageClass;
        if (barter != null) {
            return barter;
        }
        try {
            barter = Class.forName(PAGE_CLASS, false, MerchantPage.class.getClassLoader());
            Field field = barter.getDeclaredField("shopAsset");
            field.setAccessible(true);
            Class<?> assetClass = Class.forName(ASSET_CLASS, false, MerchantPage.class.getClassLoader());
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            shopAsset = lookup.unreflectGetter(field);
            shopId = lookup.unreflect(assetClass.getMethod("getId"));
            pageClass = barter;
            return barter;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            switchOff(e);
            return null;
        }
    }

    private static void switchOff(Throwable cause) {
        if (!off) {
            off = true;
            LOGGER.at(Level.WARNING).withCause(cause).log("Feats cannot read barter pages on this server, merchant tracking is off");
        }
    }
}
