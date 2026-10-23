package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.StatKeys;

import java.util.List;

import static dev.ryvrook.feats.model.Category.MUSIC;
import static dev.ryvrook.feats.model.Tier.BRONZE;
import static dev.ryvrook.feats.model.Tier.GOLD;
import static dev.ryvrook.feats.model.Tier.PLATINUM;
import static dev.ryvrook.feats.model.Tier.SILVER;

/**
 * The Ymmersive Melodies feats, read from the {@code melodies:} counters TrackerAPI 1.3.0 publishes for the binding
 * {@code melodies}, mirrored by {@code integration/ModKinds} (songs and instruments) and
 * {@code integration/TallyKinds} (plays, finishes, time played, bands and the bests). The registry calls {@link #define} with
 * {@code Sibling.MELODIES} and {@code Sibling.TRACKER_API} as the section requirement, and only when TrackerAPI is at
 * least 1.3.0. Personal feats sit in {@code Category.MUSIC}.
 *
 * <p>The numbers follow Ymmersive Melodies 1.1.5, which ships 55 songs and 9 instruments. Songs a player uploads are
 * never discovered, so the song ladder ends at the 55 built in songs.
 */
public final class MusicFeats {
    private MusicFeats() {
    }

    // ---- every key the binding writes, in one place so a change of the binding is one edit here ----
    /** Songs started, with {@code melodies:plays:<song>} per song, a per id key. */
    static final String PLAYS = StatKeys.MELODIES_PREFIX + "plays";
    static final String PLAYS_ID = StatKeys.MELODIES_PREFIX + "plays:";
    /** Songs played to the end, with {@code melodies:finished:<song>}. */
    static final String FINISHED = StatKeys.MELODIES_PREFIX + "finished";
    static final String FINISHED_ID = StatKeys.MELODIES_PREFIX + "finished:";
    static final String STOPPED = StatKeys.MELODIES_PREFIX + "stopped";
    /** Seconds played, with {@code melodies:seconds:<instrument>}. */
    static final String SECONDS = StatKeys.MELODIES_PREFIX + "seconds";
    static final String SECONDS_ID = StatKeys.MELODIES_PREFIX + "seconds:";
    /** The longest song finished, in seconds. */
    static final String SONG_LENGTH_BEST = StatKeys.MELODIES_PREFIX + "song_length_best";
    /** Songs played together with another player. */
    static final String BAND = StatKeys.MELODIES_PREFIX + "band";
    /** The most players sharing one song. */
    static final String BAND_BEST = StatKeys.MELODIES_PREFIX + "band_best";
    /** Distinct built in songs played, marks {@code melodies:song:<id>}. */
    static final String SONGS = StatKeys.MELODIES_PREFIX + "songs";
    /** Distinct instruments played, marks {@code melodies:instrument:<id>}. */
    static final String INSTRUMENTS = StatKeys.MELODIES_PREFIX + "instruments";

    /** The keys above. One ending in a colon is a per id prefix. */
    public static final List<String> KEYS = List.of(PLAYS, PLAYS_ID, FINISHED, FINISHED_ID, STOPPED, SECONDS, SECONDS_ID,
            SONG_LENGTH_BEST, BAND, BAND_BEST, SONGS, INSTRUMENTS);

    /** Adds the 8 Music feats. */
    public static void define(FeatRegistry.Definer d) {
        d.feat("melodies_first_song", "First Song", "Play a song on an instrument.", MUSIC, BRONZE, "Prototype_Tool_Guitar_Mana", PLAYS, 1);
        d.feat("melodies_finished_25", "Twenty Five Songs", "Play 25 songs to the end.", MUSIC, SILVER, "Furniture_Tavern_Stool", FINISHED, 25);
        d.feat("melodies_finished_250", "Seasoned Musician", "Play 250 songs to the end.", MUSIC, GOLD, "Furniture_Tavern_Chandelier", FINISHED, 250);
        d.feat("melodies_songs_10", "Song Book", "Play 10 different songs.", MUSIC, BRONZE, "Deco_Scroll", SONGS, 10);
        d.feat("melodies_songs_55", "Every Song", "Play all 55 songs.", MUSIC, PLATINUM, "Deco_Book_Pile_Large", SONGS, 55);
        d.feat("melodies_instruments_9", "Every Instrument", "Play all 9 instruments.", MUSIC, GOLD, "Furniture_Tavern_Table", INSTRUMENTS, 9);
        d.feat("melodies_together", "Shared Song", "Play a song together with another player.", MUSIC, SILVER, "Furniture_Tavern_Bench", BAND, 1);
        d.feat("melodies_band", "Four Piece Band", "Play a song with a band of 4 players.", MUSIC, GOLD, "Furniture_Tavern_Sign", BAND_BEST, 4);
    }
}
