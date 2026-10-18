package dev.ryvrook.feats.lang;

import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundled English file, {@code src/main/resources/lang/en-US/feats.lang}, is generated from the code and
 * committed. This keeps it in step and checks every other language file shipped in the jar under
 * {@code lang/<language>/} against it, so a placeholder that does not match fails the build before a player
 * sees a broken line.
 */
class LangFileTest {
    private static final Path LANGUAGES = WriteLangFile.FILE.getParent().getParent();

    @Test
    void bundledEnglishMatchesTheCode() throws IOException {
        assertTrue(Files.exists(WriteLangFile.FILE), "run ./gradlew generateLang to create " + WriteLangFile.FILE);
        String expected = LangFiles.english(WriteLangFile.fullRegistry());
        String actual = Files.readString(WriteLangFile.FILE, StandardCharsets.UTF_8);
        assertEquals(expected, actual, "the bundled English file is out of date, run ./gradlew generateLang and commit it");
    }

    @Test
    void englishFileParsesBackToEveryKey() throws IOException {
        FeatRegistry registry = WriteLangFile.fullRegistry();
        Map<String, String> parsed = LangFiles.prefixed(LangFiles.parse(WriteLangFile.FILE));
        for (Map.Entry<String, String> e : Lang.english().entrySet()) {
            assertEquals(e.getValue(), parsed.get(e.getKey()), e.getKey());
        }
        for (Feat feat : registry.all()) {
            assertEquals(feat.name(), parsed.get(Lang.nameKey(feat.id())), feat.id());
            assertEquals(feat.description(), parsed.get(Lang.descKey(feat.id())), feat.id());
        }
        assertEquals(Lang.english().size() + 2 * registry.all().size(), parsed.size(), "no stray key");
    }

    @Test
    void everyBundledTranslationMatchesTheEnglish() throws IOException {
        Map<String, String> english = LangFiles.prefixed(LangFiles.parse(WriteLangFile.FILE));
        List<String> problems = new ArrayList<>();
        int files = 0;
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(LANGUAGES, Files::isDirectory)) {
            for (Path dir : dirs) {
                Path file = dir.resolve(LangFiles.FILE_NAME);
                if (dir.getFileName().toString().equals("en-US") || !Files.isRegularFile(file)) {
                    continue;
                }
                files++;
                Map<String, String> translation = LangFiles.prefixed(LangFiles.parse(file));
                assertTrue(translation.size() > 0, file + " is empty");
                for (LangFiles.Problem p : LangFiles.check(english, translation)) {
                    problems.add(dir.getFileName() + ": " + p);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertTrue(files >= 0);
    }

    @Test
    void nothingIsLeftInTheEngineScanPath() {
        // The engine reads Server/Languages of the jar's own pack and keeps the first definition of a key, so a
        // copy there would always win over the data folder.
        assertTrue(!Files.exists(Path.of("src", "main", "resources", "Server", "Languages")),
                "language files belong in src/main/resources/lang/<language>/");
    }

    @Test
    void theEnglishFileStartsWithTheMarker() throws IOException {
        String first = Files.readAllLines(WriteLangFile.FILE, StandardCharsets.UTF_8).get(0);
        assertEquals(LangFiles.MARKER, first);
    }

    @Test
    void noEnglishLineNeedsLeadingOrTrailingSpace() {
        // The engine trims values, so a line that relied on an outer space would lose it.
        for (Map.Entry<String, String> e : Lang.english().entrySet()) {
            assertEquals(e.getValue().trim(), e.getValue(), e.getKey() + " has outer whitespace");
            assertTrue(!e.getValue().isEmpty(), e.getKey() + " is empty");
        }
    }
}
