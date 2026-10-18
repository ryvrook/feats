package dev.ryvrook.feats.lang;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The copy of the bundled language files into the data folder. */
class LangPackTest {
    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String read(Path dir, String language) throws IOException {
        return Files.readString(dir.resolve(language).resolve(LangFiles.FILE_NAME), StandardCharsets.UTF_8);
    }

    private static void write(Path dir, String language, String content) throws IOException {
        Files.createDirectories(dir.resolve(language));
        Files.writeString(dir.resolve(language).resolve(LangFiles.FILE_NAME), content, StandardCharsets.UTF_8);
    }

    @Test
    void englishIsAlwaysRewrittenOthersOnlyWhenGenerated(@TempDir Path dir) throws IOException {
        String en = LangFiles.MARKER + "\nchat.label = Feats\n";
        String fr = LangFiles.MARKER + "\nchat.label = Exploits\n";
        Map<String, byte[]> bundled = new TreeMap<>(Map.of("en-US", bytes(en), "fr-FR", bytes(fr), "de-DE", bytes(fr)));

        assertEquals(List.of("de-DE", "en-US", "fr-FR"), LangPack.extract(bundled, dir), "missing files are written");
        assertEquals(List.of(), LangPack.extract(bundled, dir), "nothing changes when the files match");

        write(dir, "en-US", "chat.label = edited by hand\n");
        write(dir, "fr-FR", LangFiles.MARKER + "\nchat.label = old release\n");
        write(dir, "de-DE", "chat.label = Errungenschaften\n");
        assertEquals(List.of("en-US", "fr-FR"), LangPack.extract(bundled, dir));
        assertEquals(en, read(dir, "en-US"), "the English is ours and always complete");
        assertEquals(fr, read(dir, "fr-FR"), "a generated copy follows the jar");
        assertEquals("chat.label = Errungenschaften\n", read(dir, "de-DE"), "a hand-made file is never touched");
        assertTrue(Files.notExists(dir.resolve("fr-FR").resolve(LangFiles.FILE_NAME + ".tmp")));
    }

    @Test
    void theJarCarriesTheEnglish() {
        Map<String, byte[]> bundled = LangPack.bundled();
        assertTrue(bundled.containsKey("en-US"), bundled.keySet().toString());
        assertTrue(LangPack.generated(bundled.get("en-US")));
    }

    @Test
    void aHandCopyWithTheMarkerIsNotedABundledOneIsNot(@TempDir Path dir) throws IOException {
        write(dir, "fr-FR", LangFiles.MARKER + "\nchat.label = Exploits\n");
        Path file = dir.resolve("fr-FR").resolve(LangFiles.FILE_NAME);
        Map<String, String> english = Map.of(Lang.CHAT_LABEL, "Feats");
        List<LangFiles.Problem> problems = LangPack.problems(file, english, Set.of("en-US"));
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).detail().contains("delete that line"));
        assertTrue(LangPack.problems(file, english, Set.of("en-US", "fr-FR")).isEmpty(),
                "a bundled translation keeps the marker so later releases can update it");
    }
}
