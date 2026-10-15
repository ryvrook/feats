package dev.ryvrook.feats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetPackFixTest {
    @TempDir
    Path dir;

    @Test
    void samePathMatchesTheSameJarWrittenTwoWays() throws Exception {
        Path jar = Files.writeString(dir.resolve("Feats-1.5.1.jar"), "x");
        assertTrue(AssetPackFix.samePath(jar, dir.resolve("./Feats-1.5.1.jar")));
        assertFalse(AssetPackFix.samePath(jar, dir.resolve("Feats-1.5.0.jar")));
        assertFalse(AssetPackFix.samePath(jar, null));
    }

    @Test
    void samePathNeverHandsAJarFileSystemPathToTheDefaultProvider() throws Exception {
        Path jar = dir.resolve("pack.jar");
        try (FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + jar.toUri()), Map.of("create", "true"))) {
            Path inside = zip.getPath("/Common");
            assertFalse(AssetPackFix.samePath(jar, inside)); // would throw ProviderMismatchException through isSameFile
        }
    }
}
