package dev.ryvrook.feats.lang;

import dev.ryvrook.feats.model.FeatRegistry;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangFilesTest {
    private static Map<String, String> parse(String text) throws IOException {
        return LangFiles.parse(new BufferedReader(new StringReader(text)));
    }

    private static List<String> problems(Map<String, String> english, Map<String, String> file) {
        return LangFiles.check(english, LangFiles.prefixed(file)).stream().map(Object::toString).toList();
    }

    @Test
    void parsesTheEngineFormat() throws IOException {
        Map<String, String> m = parse("""
                # a comment
                a.b = Hello {name}
                  c = spaced = value
                d = first
                d = second
                e = line one \\
                    line two
                bad line
                f = tab\\there\\nnew
                g = "quoted"
                h = "only one
                i = back\\\\slash \\= kept
                empty =
                 = no key
                """);
        assertEquals("Hello {name}", m.get("a.b"));
        assertEquals("spaced = value", m.get("c"));
        assertEquals("first", m.get("d"), "the first definition wins, as in the engine");
        assertEquals("line one line two", m.get("e"));
        assertEquals("tab\there\nnew", m.get("f"));
        assertEquals("quoted", m.get("g"), "one pair of outer quotes goes");
        assertEquals("\"only one", m.get("h"));
        assertEquals("back\\\\slash \\= kept", m.get("i"), "only \\n and \\t are escapes");
        assertEquals(8, m.size(), m.toString());
    }

    @Test
    void continuationsFollowTheEngine() throws IOException {
        Map<String, String> m = parse("""
                a = one\\
                # a comment inside the continuation is skipped

                    two  \\
                  three
                b = "kept quotes \\
                  on two lines"
                c = last \\
                """);
        assertEquals("onetwothree", m.get("a"), "joined with nothing between, each part trimmed");
        assertEquals("\"kept quotes on two lines\"", m.get("b"), "quotes only go from a one line value");
        assertEquals("last ", m.get("c"), "an open continuation at the end keeps what was read");
    }

    @Test
    void aByteOrderMarkStaysInTheFirstKeyAndIsReported() throws IOException {
        Map<String, String> m = parse("﻿chat.label = Exploits\ncmd.top.empty = Personne\n");
        assertTrue(m.containsKey("﻿chat.label"), "the engine does not strip a BOM");
        Map<String, String> english = Map.of("feats.chat.label", "Feats", "feats.cmd.top.empty", "Nobody");
        List<String> text = problems(english, m);
        assertEquals(1, text.size(), text.toString());
        assertTrue(text.get(0).contains("byte order mark"), text.toString());
        assertTrue(problems(english, parse("﻿# comment\nchat.label = Exploits\n")).isEmpty(),
                "a BOM on a comment line without = loses nothing");
    }

    @Test
    void placeholdersIncludePluralArguments() {
        assertEquals(Set.of("label", "n", "unlocked", "total"),
                LangFiles.placeholders(Lang.english().get(Lang.CHAT_MORE)));
        assertEquals(Set.of("points", "max", "percent"), LangFiles.placeholders(Lang.english().get(Lang.PAGE_POINTS)));
        assertEquals(Set.of(), LangFiles.placeholders("no braces"));
        assertEquals(Set.of("n"), LangFiles.placeholders("{n, plural, one {1 jour} other {{n} jours}}"));
    }

    @Test
    void argumentsKnowTheirForms() {
        List<LangFiles.Arg> args = LangFiles.arguments("{a} {b, number} {n, plural, =0 {none} one {1} other {{n} x}}");
        assertEquals(List.of("a", "b", "n", "n"), args.stream().map(LangFiles.Arg::name).toList());
        LangFiles.Arg plural = args.stream().filter(LangFiles.Arg::choice).findFirst().orElseThrow();
        assertEquals("plural", plural.type());
        assertEquals(List.of("=0", "one", "other"), plural.selectors());
        assertEquals("number", args.get(1).type());
    }

    @Test
    void checkFindsPlaceholderMistakes() {
        Map<String, String> english = Map.of(
                "feats.chat.broadcast", "{player} unlocked the feat {feat} [{tier}]",
                "feats.cmd.top.empty", "Nobody has unlocked anything yet.",
                "feats.feat.rock_bottom.name", "Rock Bottom");
        Map<String, String> fr = new LinkedHashMap<>();
        fr.put("chat.broadcast", "{joueur} a débloqué {feat} [{tier}]");
        fr.put("cmd.top.empty", "Personne n’a encore rien débloqué.");
        fr.put("feat.rock_bottom.name", "Fond de cale");
        fr.put("feat.unknown_feat.name", "Inconnu");
        fr.put("cmd.top.emtpy", "Typo");
        List<String> text = problems(english, fr);
        assertTrue(text.stream().anyMatch(s -> s.contains("uses {joueur}")), text.toString());
        assertTrue(text.stream().anyMatch(s -> s.contains("leaves out {player}")), text.toString());
        assertTrue(text.stream().anyMatch(s -> s.startsWith("cmd.top.emtpy")), "named as the file writes it " + text);
        assertTrue(text.stream().anyMatch(s -> s.startsWith("feat.unknown_feat.name")), text.toString());
        assertEquals(4, text.size(), text.toString());

        List<LangFiles.Problem> lenient = LangFiles.check(english, LangFiles.prefixed(fr), false);
        assertTrue(lenient.stream().noneMatch(p -> "feat.unknown_feat.name".equals(p.key())),
                "a feat from a pack that is off passes on a server");
        assertEquals(3, lenient.size());
    }

    @Test
    void checkFindsAKeyWrittenWithThePrefix() {
        Map<String, String> english = Map.of("feats.chat.label", "Feats");
        List<String> text = problems(english, Map.of("feats.chat.label", "Exploits"));
        assertEquals(List.of("feats.chat.label: the file name adds feats., write chat.label not feats.chat.label"), text);
    }

    @Test
    void checkFindsUnmatchedBraces() {
        Map<String, String> english = Map.of("feats.x", "{n} things");
        List<String> text = problems(english, Map.of("x", "{n choses"));
        assertTrue(text.stream().anyMatch(s -> s.contains("unmatched")), text.toString());
    }

    @Test
    void checkFindsAPluralWithoutOther() {
        Map<String, String> english = Map.of("feats.cmd.recent.ago.days", "{n, plural, one {1 day ago} other {{n} days ago}}");
        List<String> text = problems(english, Map.of("cmd.recent.ago.days", "{n, plural, one {il y a 1 jour} many {il y a {n} jours}}"));
        assertTrue(text.stream().anyMatch(s -> s.contains("no other form")), text.toString());
        assertTrue(problems(english, Map.of("cmd.recent.ago.days", "{n, plural, one {il y a 1 jour} other {il y a {n} jours}}")).isEmpty());
        assertTrue(problems(Map.of("feats.x", "{g, select, a {A} other {B}}"), Map.of("x", "{g, select, a {A} b {B}}"))
                .stream().anyMatch(s -> s.contains("{g, select} has no other form")));
    }

    @Test
    void checkFindsAnIcuQuoteBeforeABrace() {
        Map<String, String> english = Map.of("feats.cmd.noFeats", "{player} has no feats yet.");
        List<String> text = problems(english, Map.of("cmd.noFeats", "Les exploits d'{player}"));
        assertTrue(text.stream().anyMatch(s -> s.contains("typographic")), text.toString());
        assertTrue(problems(english, Map.of("cmd.noFeats", "Les exploits d’{player}")).isEmpty());
        assertTrue(problems(english, Map.of("cmd.noFeats", "{player} n'a rien")).isEmpty(), "a quote away from a brace is fine");
    }

    @Test
    void checkFindsAPluralInALineTheServerFills() {
        assertTrue(Lang.serverFilled("feats.tier.gold"));
        assertTrue(Lang.serverFilled("feats.rarity.rare"));
        assertTrue(Lang.serverFilled(Lang.PAGE_ROW_POINTS));
        assertTrue(Lang.serverFilled(Lang.PAGE_WORLD_SOMEONE));
        assertFalse(Lang.serverFilled(Lang.CHAT_MORE));
        Map<String, String> english = Map.of("feats.page.row.points", "+{points} pts", "feats.chat.more", "{n} more");
        List<String> text = problems(english, Map.of("page.row.points", "+{points, plural, one {1 pt} other {{points} pts}}"));
        assertTrue(text.stream().anyMatch(s -> s.contains("fills this line in itself")), text.toString());
        assertTrue(problems(english, Map.of("chat.more", "{n, plural, one {1 de plus} other {{n} de plus}}")).isEmpty(),
                "a client rendered line may use a plural");
    }

    @Test
    void englishFileKeysHaveNoPrefix() throws IOException {
        String file = LangFiles.english(new FeatRegistry(java.util.EnumSet.noneOf(dev.ryvrook.feats.model.Sibling.class),
                java.util.EnumSet.noneOf(dev.ryvrook.feats.model.Pack.class)));
        assertTrue(file.startsWith(LangFiles.MARKER + "\n"), "the marker is line 1");
        assertTrue(file.contains("\nchat.label = Feats\n"), "the file name supplies feats.");
        Map<String, String> parsed = parse(file);
        assertTrue(parsed.keySet().stream().noneMatch(k -> k.startsWith(Lang.PREFIX)), parsed.keySet().toString());
        assertEquals("Feats", LangFiles.prefixed(parsed).get(Lang.CHAT_LABEL));
    }

    @Test
    void escapeRefusesWhatTheEngineCannotRead() throws IOException {
        assertEquals("a\\nb\\tc", LangFiles.escape("k", "a\nb\tc"));
        assertEquals("a\nb\tc", parse("k = " + LangFiles.escape("k", "a\nb\tc")).get("k"));
        assertThrows(IllegalArgumentException.class, () -> LangFiles.escape("k", "back\\slash"));
        assertThrows(IllegalArgumentException.class, () -> LangFiles.escape("k", "\"quoted\""));
        assertThrows(IllegalArgumentException.class, () -> LangFiles.escape("k", "ends with a quote\""));
        assertThrows(IllegalArgumentException.class, () -> LangFiles.escape("k", " space"));
        assertThrows(IllegalArgumentException.class, () -> LangFiles.escape("k", "space\n"));
        assertThrows(IllegalArgumentException.class, () -> LangFiles.escape("k", ""));
    }
}
