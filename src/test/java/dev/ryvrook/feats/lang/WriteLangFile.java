package dev.ryvrook.feats.lang;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Pack;
import dev.ryvrook.feats.model.Sibling;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

/**
 * Writes the bundled English file, {@code ./gradlew generateLang}. The file is what {@link LangFileTest}
 * compares the code against, so run this after adding a feat or a string and commit the result.
 */
public final class WriteLangFile {
    private WriteLangFile() {
    }

    /** The bundled file, relative to the project root. */
    public static final Path FILE = Path.of("src", "main", "resources", "lang", "en-US", LangFiles.FILE_NAME);

    /** A registry with every sibling, every pack and TrackerAPI 1.2.0, so every feat has its lines. */
    public static FeatRegistry fullRegistry() {
        return new FeatRegistry(EnumSet.allOf(Sibling.class), EnumSet.allOf(Pack.class), FeatRegistry.MODS_MINIMUM);
    }

    public static void main(String[] args) throws IOException {
        Path file = args.length > 0 ? Path.of(args[0]) : FILE;
        Files.createDirectories(file.getParent());
        String content = LangFiles.english(fullRegistry());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        System.out.println("Wrote " + file + " with " + LangFiles.parse(file).size() + " keys");
    }
}
