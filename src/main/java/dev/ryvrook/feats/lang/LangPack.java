package dev.ryvrook.feats.lang;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.ryvrook.feats.FeatsPlugin;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;

/**
 * The {@code lang} folder of the data directory as an asset pack, the one place the engine reads Feats' lines
 * from. The jar keeps its files at {@code lang/<language>/feats.lang}, outside the engine's
 * {@code Server/Languages} scan, and {@link #register} copies them into
 * {@code <data>/lang/Server/Languages/<language>/feats.lang} before it registers the folder. One source matters
 * because the engine keeps the first definition of a key across packs and never drops a line when a pack
 * goes away, so a second copy of a key in the jar would always win over the folder.
 *
 * <p>The copy rule: {@code en-US} is rewritten from the jar whenever it differs, it is the generated English
 * and the fallback for every other language, so it must be complete. Another bundled language is written when
 * its file is missing, or when the file's first line is {@link LangFiles#MARKER} and the content differs. A
 * file without the marker on line 1 is someone's own and is never touched.
 *
 * <p>Reloading, as found on a 0.7.1 server: the engine's asset monitor is on unless the server runs with
 * {@code --disable-file-watcher}, and it watches the folder's {@code Server/Languages} with every language
 * folder in it, also one made later. When a {@code .lang} file is saved it drops that file's keys for that
 * language, reads the file again and sends the changed lines to online players, all on its own. A file
 * written in the same moment as its new language folder is missed until it is saved again. Registering
 * the pack again would change nothing, the engine keeps the first definition of every key and unregistering
 * removes no line, so {@link #reload} never does it, see there.
 *
 * <p>Every file is checked against the English when the folder is registered and each problem is logged,
 * since a wrong placeholder is what breaks a line. Nothing in here throws into the plugin.
 */
public final class LangPack {
    /** The folder inside the data directory. */
    public static final String FOLDER = "lang";
    /** Where the jar keeps its language files, {@code lang/<language>/feats.lang}. */
    public static final String BUNDLED_ROOT = "lang/";
    /** The generated English, always rewritten from the jar. */
    public static final String ENGLISH = "en-US";
    private static final String README = "README.txt";
    /** Languages looked up by name when the jar cannot be listed, Hytale's own and a few more. */
    private static final List<String> KNOWN = List.of(
            "en-US", "es-ES", "fr-FR", "pt-BR", "ru-RU", "uk-UA", "zh-CN", "de-DE", "it-IT", "ja-JP", "ko-KR",
            "pl-PL", "tr-TR", "nl-NL", "sv-SE", "zh-TW", "cs-CZ", "pt-PT");

    private final JavaPlugin plugin;
    private final HytaleLogger logger;
    private final String packName;
    private volatile boolean registered;
    private volatile boolean warnedNoMonitor;

    public LangPack(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.packName = plugin.getIdentifier() + "/Languages";
    }

    /** The folder, {@code <data>/lang}. */
    public Path folder() {
        return plugin.getDataDirectory().resolve(FOLDER);
    }

    private Path languages() {
        return folder().resolve("Server").resolve("Languages");
    }

    /** True while the folder is registered with the engine. */
    public boolean registered() {
        return registered;
    }

    /**
     * Creates the folder with its README on first start, copies the bundled files into it, checks the files
     * and registers it. Called in start(). A pack that is already registered stays as it is. Never throws.
     */
    public void register(FeatRegistry registry) {
        registered = false;
        try {
            prepare(registry);
            AssetModule assets = AssetModule.get();
            if (assets == null) {
                return;
            }
            if (find(assets) != null) {
                registered = true;
                return;
            }
            registerPack(assets);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            logger.at(Level.WARNING).withCause(e).log("Feats could not set up its language folder");
        }
    }

    /** The folder on disk, the bundled files copied in, every file checked. Returns how many files there are. */
    private int prepare(FeatRegistry registry) throws IOException {
        Path folder = folder();
        Path languages = languages();
        Files.createDirectories(languages);
        // The engine's common asset module watches a pack's Common folder and throws when asked to stop
        // watching one that never existed, so the folder is there even though nothing goes in it.
        Files.createDirectories(folder.resolve("Common"));
        Path readme = folder.resolve(README);
        if (!Files.exists(readme)) {
            Files.writeString(readme, readme(), StandardCharsets.UTF_8);
        }
        for (String language : extract(bundled(), languages)) {
            logger.at(Level.INFO).log("Feats wrote %s/%s from the jar", language, LangFiles.FILE_NAME);
        }
        return report(languages, registry);
    }

    private void registerPack(AssetModule assets) {
        boolean ok = assets.registerPack(packName, folder(), plugin.getManifest(), AssetPack.PackSource.RUNTIME);
        registered = ok;
        if (!ok) {
            logger.at(Level.WARNING).log("Feats could not register %s as a language pack, its files are not used", folder());
        }
    }

    /** Takes the folder out of the engine again. Last thing in shutdown(). Never throws. */
    public void unregister() {
        registered = false;
        try {
            AssetModule assets = AssetModule.get();
            if (assets != null) {
                dropExisting(assets);
            }
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            logger.at(Level.FINE).withCause(e).log("Feats could not release its language folder");
        }
    }

    /**
     * {@code /feats admin reload}. Copies and checks the files again and registers the folder if it is not.
     * With the engine's asset monitor on, the default, the folder is never registered again, since the engine
     * keeps the first definition of a key and would ignore the new lines. A saved file is read by the monitor
     * on its own. Only a file written in the same moment as its new language folder slips past it, so every
     * file whose lines the engine does not have yet is saved again, which the monitor then reads within
     * seconds, and every online player is sent their language. Without the monitor
     * ({@code --disable-file-watcher}) the files are read at start only. Returns one line for the admin.
     * Never throws. Runs on the caller's thread.
     */
    public String reload(FeatRegistry registry) {
        try {
            prepare(registry);
            AssetModule assets = AssetModule.get();
            if (assets == null) {
                registered = false;
                return "Language folder not registered, see the server log";
            }
            AssetPack pack = find(assets);
            if (pack == null) {
                registerPack(assets);
                return registered ? "Language folder registered, sent to " + resend() + " online player(s)"
                        : "Language folder not registered, see the server log";
            }
            registered = true;
            if (assets.getAssetMonitor() == null || pack.isImmutable()) {
                if (!warnedNoMonitor) {
                    warnedNoMonitor = true;
                    logger.at(Level.WARNING).log("Feats language files are read at start only, the engine's file watcher is off");
                }
                return "Language files are read at start, restart to apply";
            }
            List<String> stale = touchStale(languages());
            int sent = resend();
            return "Language files are read by the engine when saved"
                    + (stale.isEmpty() ? "" : ", saved " + String.join(", ", stale) + " again so it reads them in a few seconds")
                    + ", sent to " + sent + " online player(s)";
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            logger.at(Level.WARNING).withCause(e).log("Feats could not reload its language folder");
            return "Language files could not be reloaded, see the server log";
        }
    }

    /**
     * Saves again each {@code feats.lang} with a line the engine does not hold as written, so the asset monitor
     * reads it. Returns the languages touched.
     */
    private List<String> touchStale(Path languages) {
        List<String> out = new ArrayList<>();
        for (Path file : langFiles(languages)) {
            if (!LangFiles.FILE_NAME.equals(file.getFileName().toString())) {
                continue;
            }
            String language = file.getParent().getFileName().toString();
            try {
                boolean stale = false;
                for (Map.Entry<String, String> e : LangFiles.prefixed(LangFiles.parse(file)).entrySet()) {
                    String loaded = Lang.resolve(language, e.getKey());
                    if (loaded != null && !loaded.equals(e.getValue()) || loaded == null && engineHasNo(language, e.getKey())) {
                        stale = true;
                        break;
                    }
                }
                if (stale) {
                    Files.write(file, Files.readAllBytes(file));
                    out.add(language);
                }
            } catch (IOException | RuntimeException e) {
                logger.at(Level.FINE).withCause(e).log("Feats could not compare %s with the engine", file);
            }
        }
        return out;
    }

    /** True when the engine runs and has no text at all for the key, so the line was never read. */
    private static boolean engineHasNo(String language, String key) {
        try {
            I18nModule i18n = I18nModule.get();
            return i18n != null && i18n.getMessage(language, key) == null;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** Sends every online player the translations of their language, returns how many got them. */
    private int resend() {
        int sent = 0;
        try {
            I18nModule i18n = I18nModule.get();
            Universe universe = Universe.get();
            if (i18n != null && universe != null) {
                for (PlayerRef player : universe.getPlayers()) {
                    try {
                        i18n.queueTranslations(player.getPacketHandler(), player.getLanguage());
                        sent++;
                    } catch (RuntimeException e) {
                        logger.at(Level.FINE).withCause(e).log("Feats could not resend translations to %s", player.getUsername());
                    }
                }
            }
        } catch (RuntimeException | LinkageError e) {
            logger.at(Level.WARNING).withCause(e).log("Feats could not resend translations after a reload");
        }
        return sent;
    }

    private AssetPack find(AssetModule assets) {
        for (AssetPack pack : assets.getAssetPacks()) {
            if (pack != null && packName.equalsIgnoreCase(pack.getName())) {
                return pack;
            }
        }
        return null;
    }

    private void dropExisting(AssetModule assets) {
        AssetPack pack = find(assets);
        if (pack == null) {
            return;
        }
        try {
            assets.unregisterPack(pack.getName());
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            logger.at(Level.FINE).withCause(e).log("Feats could not unregister its language folder");
        }
    }

    // ---- the bundled files ------------------------------------------------------------------------------

    /** Every {@code lang/<language>/feats.lang} in the jar, by language. Never throws, empty when none is found. */
    static Map<String, byte[]> bundled() {
        Set<String> names = new TreeSet<>(KNOWN);
        names.addAll(listJar());
        Map<String, byte[]> out = new TreeMap<>();
        for (String language : names) {
            try (InputStream in = FeatsPlugin.class.getResourceAsStream("/" + BUNDLED_ROOT + language + "/" + LangFiles.FILE_NAME)) {
                if (in != null) {
                    out.put(language, in.readAllBytes());
                }
            } catch (IOException | RuntimeException ignored) {
                // a file that cannot be read is left out
            }
        }
        return out;
    }

    /** The language folders under {@code lang/} of the jar or classes directory Feats runs from. */
    private static Set<String> listJar() {
        Set<String> out = new LinkedHashSet<>();
        try {
            CodeSource source = FeatsPlugin.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return out;
            }
            Path where = Path.of(source.getLocation().toURI());
            if (Files.isDirectory(where)) {
                Path root = where.resolve(BUNDLED_ROOT);
                if (Files.isDirectory(root)) {
                    try (DirectoryStream<Path> dirs = Files.newDirectoryStream(root, Files::isDirectory)) {
                        for (Path d : dirs) {
                            out.add(d.getFileName().toString());
                        }
                    }
                }
            } else if (Files.isRegularFile(where)) {
                try (JarFile jar = new JarFile(where.toFile())) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement().getName();
                        if (name.startsWith(BUNDLED_ROOT) && name.endsWith("/" + LangFiles.FILE_NAME)) {
                            String language = name.substring(BUNDLED_ROOT.length(), name.length() - LangFiles.FILE_NAME.length() - 1);
                            if (!language.isEmpty() && language.indexOf('/') < 0) {
                                out.add(language);
                            }
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException | RuntimeException | LinkageError ignored) {
            // the known list still applies
        }
        return out;
    }

    /**
     * Writes the bundled files into {@code languages}, by the rule in the class comment. Returns the languages
     * written, in order. A file that cannot be written is skipped, the rest still go.
     */
    static List<String> extract(Map<String, byte[]> bundled, Path languages) {
        List<String> written = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : bundled.entrySet()) {
            String language = e.getKey();
            byte[] content = e.getValue();
            Path file = languages.resolve(language).resolve(LangFiles.FILE_NAME);
            try {
                if (Files.exists(file)) {
                    byte[] current = Files.readAllBytes(file);
                    if (Arrays.equals(current, content)) {
                        continue;
                    }
                    if (!language.equals(ENGLISH) && !generated(current)) {
                        continue; // someone's own file
                    }
                }
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(LangFiles.FILE_NAME + ".tmp");
                Files.write(tmp, content);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                written.add(language);
            } catch (IOException | RuntimeException ignored) {
                // left as it is, the report shows what is there
            }
        }
        return written;
    }

    /** True when the first line of the file is the generated marker. */
    static boolean generated(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        int nl = text.indexOf('\n');
        String first = (nl < 0 ? text : text.substring(0, nl)).strip();
        return first.equals(LangFiles.MARKER);
    }

    // ---- the report -------------------------------------------------------------------------------------

    /** Logs what is wrong with each file in the folder, the generated English left out. Returns how many files there are. */
    private int report(Path languages, FeatRegistry registry) {
        Map<String, String> english = new HashMap<>(Lang.english());
        for (Feat feat : registry.all()) {
            english.put(Lang.nameKey(feat.id()), feat.name());
            english.put(Lang.descKey(feat.id()), feat.description());
        }
        Map<String, byte[]> bundled = bundled();
        int files = 0;
        for (Path file : langFiles(languages)) {
            files++;
            String shown = languages.relativize(file).toString().replace('\\', '/');
            if (shown.equals(ENGLISH + "/" + LangFiles.FILE_NAME)) {
                continue; // ours, LangFileTest checks it
            }
            try {
                List<LangFiles.Problem> problems = LangFiles.FILE_NAME.equals(file.getFileName().toString())
                        ? problems(file, english, bundled.keySet())
                        : List.of(new LangFiles.Problem(null, "only feats.lang holds Feats lines, the engine stores this"
                                + " file's keys under " + file.getFileName().toString().replace(".lang", "") + "."));
                for (LangFiles.Problem p : problems) {
                    logger.at(Level.WARNING).log("Feats language file %s, %s", shown, p);
                }
                logger.at(Level.INFO).log("Feats language file %s checked%s", shown,
                        problems.isEmpty() ? ", no problem" : ", " + problems.size() + " problem(s), see above");
            } catch (IOException | RuntimeException e) {
                logger.at(Level.WARNING).withCause(e).log("Feats could not read language file %s, it must be UTF-8", shown);
            }
        }
        return files;
    }

    /**
     * What {@link LangFiles#check} finds in one file, plus a note when a hand copy still carries the generated
     * line. A language the jar bundles keeps that line on purpose, so later releases can update it.
     */
    static List<LangFiles.Problem> problems(Path file, Map<String, String> english, Set<String> bundledLanguages) throws IOException {
        Map<String, String> lines = LangFiles.prefixed(LangFiles.parse(file));
        List<LangFiles.Problem> out = new ArrayList<>(LangFiles.check(english, lines, false));
        String language = file.getParent().getFileName().toString();
        if (!language.equals(ENGLISH) && !bundledLanguages.contains(language) && generated(Files.readAllBytes(file))) {
            out.add(new LangFiles.Problem(null, "line 1 is the generated line, so a Feats update that ships this language"
                    + " would replace the file, delete that line to keep your copy"));
        }
        return out;
    }

    private static List<Path> langFiles(Path languages) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(languages)) {
            return out;
        }
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(languages, Files::isDirectory)) {
            for (Path dir : dirs) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.lang")) {
                    for (Path f : files) {
                        if (Files.isRegularFile(f)) {
                            out.add(f);
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            // an unreadable folder registers as empty
        }
        out.sort(null);
        return out;
    }

    private static String readme() {
        return """
                Feats language files

                Server/Languages/en-US/feats.lang is the English with every key. Feats writes it on every start,
                so do not edit it. To translate, copy it to Server/Languages/<language>/feats.lang, for example
                Server/Languages/fr-FR/feats.lang, delete its first line (the "generated" line) so Feats never
                replaces your copy, and translate the right hand sides. The languages Hytale ships are en-US,
                es-ES, fr-FR, pt-BR, ru-RU, uk-UA and zh-CN, and each player gets the one their client runs in.
                A key left out stays English, so a file only needs the lines it changes.

                One line per key, key = text, UTF-8 without a byte order mark, # starts a comment. The file name
                adds feats. to every key, so write chat.label = ..., not feats.chat.label = .... Keep every
                {placeholder} exactly as the English line has it, the server checks that when it reads the
                files and logs each line that does not match. Plurals use the ICU form,
                {n, plural, one {1 day} other {{n} days}}, and always need the other form. Write an apostrophe
                next to a placeholder as the typographic one, not ', which the client reads as quoting.
                \\n is a line break and \\t a tab, there is no other escape.

                A saved file reaches online players on its own within a few seconds. If a new language does
                not show, run /feats admin reload, it saves again any file the server has not read yet. With
                the server's file watcher off (--disable-file-watcher) the files are read at start only.
                """;
    }
}
