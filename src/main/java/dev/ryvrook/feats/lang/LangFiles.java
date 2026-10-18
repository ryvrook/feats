package dev.ryvrook.feats.lang;

import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads and writes the engine's {@code .lang} format the way the engine's {@code LangFileParser} reads it, one
 * {@code key = value} per line in UTF-8. Builds the bundled English file from {@link Lang} and a registry, and
 * checks a translation against the English for what breaks a line that otherwise looks fine.
 *
 * <p>The engine stores every key of a file under the file's name, so a line {@code chat.label = Feats} in
 * {@code feats.lang} is the key {@code feats.chat.label} the code asks for. The file holds the keys without
 * {@code feats.}, {@link #prefixed} turns a parsed file into the code's keys.
 *
 * <p>The parser rules, taken from the engine: each line is trimmed, blank lines and lines starting with
 * {@code #} are skipped, also inside a continuation; the line splits at the first {@code =}, both sides are
 * trimmed and an empty key or value skips the line; a value ending in a backslash continues on the next line,
 * each continuation line is trimmed, its trailing backslash removed and trimmed again, and the parts are
 * joined with nothing between; a value on one line loses one pair of surrounding double quotes; after
 * joining {@code \n} becomes a newline and {@code \t} a tab, nothing else is an escape; the first
 * definition of a key wins. There is no byte order mark handling, a BOM becomes part of the first key.
 */
public final class LangFiles {
    private LangFiles() {
    }

    /** The file name every language uses inside {@code Server/Languages/<language>/}. */
    public static final String FILE_NAME = "feats.lang";

    /**
     * The first line of the generated English file. A file in the data folder that starts with it was written
     * by Feats and may be replaced on an update, a file without it is someone's own and is never touched.
     */
    public static final String MARKER =
            "# Feats language file, generated from the code. Do not edit, copy it to another language instead.";

    /** One thing wrong with a translated file. {@code key} is the key as written in the file, null for a file level problem. */
    public record Problem(String key, String detail) {
        @Override
        public String toString() {
            return key == null ? detail : key + ": " + detail;
        }
    }

    /**
     * The English file, the {@link Lang} table first and then every feat of the registry by id, with the keys
     * as the file holds them, without {@code feats.}. Built from a registry with every sibling and every pack
     * so no feat is left out. Throws when a value could not survive the engine's parser unchanged.
     */
    public static String english(FeatRegistry registry) {
        StringBuilder sb = new StringBuilder();
        sb.append(MARKER).append('\n');
        sb.append("# English, written by ./gradlew generateLang. Feats copies it to lang/Server/Languages/en-US/ in its\n");
        sb.append("# data folder on every start. To translate, copy it to lang/Server/Languages/<language>/feats.lang,\n");
        sb.append("# delete the first line so Feats never replaces your copy, and translate the right hand sides.\n");
        sb.append("# The file name adds feats. to every key, so a key reads chat.label, not feats.chat.label.\n");
        sb.append("# Keep every {placeholder} as it is. A key left out shows in English.\n");
        String section = "";
        for (Map.Entry<String, String> e : Lang.english().entrySet()) {
            String s = sectionOf(e.getKey());
            if (!s.equals(section)) {
                section = s;
                sb.append("\n# === ").append(section).append(" ===\n");
            }
            line(sb, e.getKey(), e.getValue());
        }
        sb.append("\n# === feats ===\n");
        for (Feat feat : registry.all()) {
            line(sb, Lang.nameKey(feat.id()), feat.name());
            line(sb, Lang.descKey(feat.id()), feat.description());
        }
        return sb.toString();
    }

    /** The key as the file holds it, the code key without {@code feats.}. */
    public static String fileKey(String codeKey) {
        return codeKey.startsWith(Lang.PREFIX) ? codeKey.substring(Lang.PREFIX.length()) : codeKey;
    }

    /** A parsed {@code feats.lang} with the code's keys, {@code feats.} in front of each, in file order. */
    public static Map<String, String> prefixed(Map<String, String> file) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : file.entrySet()) {
            out.put(Lang.PREFIX + e.getKey(), e.getValue());
        }
        return out;
    }

    private static String sectionOf(String key) {
        String rest = fileKey(key);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    private static void line(StringBuilder sb, String key, String value) {
        if (!key.startsWith(Lang.PREFIX)) {
            throw new IllegalArgumentException("key " + key + " does not start with " + Lang.PREFIX);
        }
        String file = fileKey(key);
        if (file.isEmpty() || file.indexOf('=') >= 0 || !file.equals(file.strip()) || file.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("key " + key + " cannot be written to a lang file");
        }
        sb.append(file).append(" = ").append(escape(key, value)).append('\n');
    }

    /**
     * The value as a file line. The engine has no escape for a backslash and strips outer whitespace and one
     * pair of outer quotes, so a value with any of them cannot be written and throws.
     */
    static String escape(String key, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(key + " is empty");
        }
        if (value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(key + " has a backslash, the engine has no escape for one");
        }
        if (!value.equals(value.strip()) || Character.isWhitespace(value.charAt(0))
                || Character.isWhitespace(value.charAt(value.length() - 1))) {
            throw new IllegalArgumentException(key + " has leading or trailing whitespace, the engine trims it");
        }
        if (value.charAt(0) == '"' || value.charAt(value.length() - 1) == '"') {
            throw new IllegalArgumentException(key + " starts or ends with a double quote, the engine may strip it");
        }
        return value.replace("\n", "\\n").replace("\t", "\\t");
    }

    /** Parses a file. Malformed lines are skipped, as the engine skips them. A file that is not UTF-8 throws. */
    public static Map<String, String> parse(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    public static Map<String, String> parse(BufferedReader reader) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        String line;
        String key = null;
        StringBuilder value = null;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
                continue;
            }
            if (key == null) {
                int eq = trimmed.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String k = trimmed.substring(0, eq).trim();
                String v = trimmed.substring(eq + 1).trim();
                if (k.isEmpty() || v.isEmpty()) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                if (v.charAt(v.length() - 1) == '\\') {
                    sb.append(v, 0, v.length() - 1);
                    key = k;
                    value = sb;
                } else {
                    sb.append(literal(v));
                    out.putIfAbsent(k, unescape(sb.toString()));
                }
                continue;
            }
            boolean more = trimmed.charAt(trimmed.length() - 1) == '\\';
            String part = more ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
            value.append(part.trim());
            if (!more) {
                out.putIfAbsent(key, unescape(value.toString()));
                key = null;
                value = null;
            }
        }
        if (key != null) {
            out.putIfAbsent(key, unescape(value.toString()));
        }
        return out;
    }

    /** One pair of surrounding double quotes removed, as the engine does for a value on one line. */
    private static String literal(String value) {
        String v = value.trim();
        if (v.length() > 1 && v.charAt(0) == '"' && v.charAt(v.length() - 1) == '"') {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    /** The engine's only two escapes, {@code \n} and {@code \t}, in its order. A backslash stays as it is otherwise. */
    static String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\t", "\t");
    }

    /**
     * What is wrong with a translation next to the English, in file order. Both maps hold the code's keys, a
     * parsed file goes through {@link #prefixed} first, and a problem names the key as the file writes it.
     * A key the English does not have, a key written with {@code feats.} in front, a placeholder the English
     * line does not use, an English placeholder the translation dropped, a plural or select without its
     * {@code other} form, an ICU quote before a brace, a plural or select in a line the server fills in
     * itself ({@link Lang#serverFilled}) and a byte order mark that swallows the first key are each a
     * problem. A key left out is not, it falls back to English.
     */
    public static List<Problem> check(Map<String, String> english, Map<String, String> translation) {
        return check(english, translation, true);
    }

    /**
     * As {@link #check(Map, Map)}. With {@code strictFeatKeys} false a feat key the English does not have
     * passes, for a server whose registry leaves out a pack or a sibling's feats.
     */
    public static List<Problem> check(Map<String, String> english, Map<String, String> translation, boolean strictFeatKeys) {
        List<Problem> out = new ArrayList<>();
        for (Map.Entry<String, String> e : translation.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith(Lang.PREFIX)) {
                continue; // not a feats.lang key
            }
            String shown = fileKey(key);
            if (shown.indexOf('\uFEFF') >= 0) {
                out.add(new Problem(null, "the file starts with a byte order mark, so the engine reads the first key as "
                        + shown.replace("\uFEFF", "") + " with an invisible character in front and that line never shows."
                        + " Save the file as UTF-8 without BOM"));
                continue;
            }
            if (shown.startsWith(Lang.PREFIX)) {
                String meant = fileKey(shown);
                out.add(new Problem(shown, "the file name adds feats., write " + meant + " not " + shown));
                continue;
            }
            String reference = english.get(key);
            if (reference == null) {
                boolean feat = key.startsWith(Lang.PREFIX + "feat.");
                if (strictFeatKeys || !feat) {
                    out.add(new Problem(shown, "not a Feats key, check the spelling"));
                }
                continue;
            }
            String value = e.getValue();
            Set<String> wanted = placeholders(reference);
            Set<String> found = placeholders(value);
            for (String p : found) {
                if (!wanted.contains(p)) {
                    out.add(new Problem(shown, "uses {" + p + "}, which the English line does not have, the English has "
                            + describe(wanted)));
                }
            }
            for (String p : wanted) {
                if (!found.contains(p)) {
                    out.add(new Problem(shown, "leaves out {" + p + "}, so that value would not show"));
                }
            }
            if (braces(value) != 0) {
                out.add(new Problem(shown, "has an unmatched { or }"));
            }
            if (value.contains("'{") || value.contains("'}")) {
                out.add(new Problem(shown, "has a ' right before a brace, which the client reads as quoting and shows the"
                        + " placeholder as text, use the typographic \u2019 instead"));
            }
            List<Arg> args = arguments(value);
            for (Arg a : args) {
                if (a.choice() && !a.selectors().contains("other")) {
                    out.add(new Problem(shown, "{" + a.name() + ", " + a.type() + "} has no other form, the client needs one"));
                }
            }
            if (Lang.serverFilled(key) && args.stream().anyMatch(Arg::choice)) {
                out.add(new Problem(shown, "uses a plural or select, but the server fills this line in itself and only"
                        + " knows plain {name} placeholders, write it without one"));
            }
        }
        return out;
    }

    /**
     * One ICU argument of a line, {@code {name}}, {@code {name, number}} or {@code {name, plural, one {..} other {..}}}.
     * {@code type} is empty for a plain one, {@code selectors} holds the form names of a plural or select.
     */
    public record Arg(String name, String type, List<String> selectors) {
        /** True for a plural, selectordinal or select. */
        public boolean choice() {
            return type.equals("plural") || type.equals("select") || type.equals("selectordinal");
        }
    }

    /** Every argument of a line, those nested in a plural form included, in text order. */
    public static List<Arg> arguments(String text) {
        List<Arg> out = new ArrayList<>();
        arguments(text, 0, text.length(), out);
        return out;
    }

    private static void arguments(String text, int from, int to, List<Arg> out) {
        int i = from;
        while (i < to) {
            if (text.charAt(i) != '{') {
                i++;
                continue;
            }
            int close = matching(text, i, to);
            if (close < 0) {
                return;
            }
            String inner = text.substring(i + 1, close);
            int comma = inner.indexOf(',');
            String name = (comma < 0 ? inner : inner.substring(0, comma)).trim();
            if (isName(name)) {
                String type = "";
                List<String> selectors = new ArrayList<>();
                if (comma >= 0) {
                    int second = inner.indexOf(',', comma + 1);
                    int brace = inner.indexOf('{', comma + 1);
                    int typeEnd = second < 0 || (brace >= 0 && brace < second) ? (brace < 0 ? inner.length() : brace) : second;
                    type = inner.substring(comma + 1, typeEnd).trim();
                    if (second >= 0 && typeEnd == second) {
                        forms(text, i + 1 + second + 1, close, selectors, out);
                    }
                }
                out.add(new Arg(name, type, List.copyOf(selectors)));
            } else {
                arguments(text, i + 1, close, out);
            }
            i = close + 1;
        }
    }

    /** The {@code selector {body}} pairs of a plural or select between from and to. */
    private static void forms(String text, int from, int to, List<String> selectors, List<Arg> out) {
        int i = from;
        int start = from;
        while (i < to) {
            if (text.charAt(i) == '{') {
                int close = matching(text, i, to);
                if (close < 0) {
                    return;
                }
                String head = text.substring(start, i).trim();
                int space = Math.max(head.lastIndexOf(' '), head.lastIndexOf('\t'));
                selectors.add(space < 0 ? head : head.substring(space + 1));
                arguments(text, i + 1, close, out);
                i = close + 1;
                start = i;
            } else {
                i++;
            }
        }
    }

    private static String describe(Set<String> names) {
        if (names.isEmpty()) {
            return "no placeholder";
        }
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append('{').append(n).append('}');
        }
        return sb.toString();
    }

    private static int braces(String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
        }
        return depth;
    }

    /**
     * The placeholder names of a line, {@code {player}} and the {@code n} of {@code {n, plural, ...}} alike.
     * The braces inside a plural form, {@code one {1 day}}, are the form's text, not placeholders, except
     * for a placeholder nested in it, {@code other {{n} days}}.
     */
    public static Set<String> placeholders(String text) {
        Set<String> out = new LinkedHashSet<>();
        collect(text, 0, text.length(), out, false);
        return out;
    }

    private static void collect(String text, int from, int to, Set<String> out, boolean insideForm) {
        int i = from;
        while (i < to) {
            if (text.charAt(i) != '{') {
                i++;
                continue;
            }
            int close = matching(text, i, to);
            if (close < 0) {
                return;
            }
            String inner = text.substring(i + 1, close);
            int comma = inner.indexOf(',');
            String name = (comma < 0 ? inner : inner.substring(0, comma)).trim();
            if (isName(name)) {
                out.add(name);
                if (comma >= 0) {
                    // {n, plural, one {...} other {...}}: the forms' bodies may hold more placeholders.
                    forms(text, i + 1 + comma + 1, close, out);
                }
            } else if (!insideForm) {
                collect(text, i + 1, close, out, true);
            }
            i = close + 1;
        }
    }

    private static void forms(String text, int from, int to, Set<String> out) {
        int i = from;
        while (i < to) {
            if (text.charAt(i) == '{') {
                int close = matching(text, i, to);
                if (close < 0) {
                    return;
                }
                collect(text, i + 1, close, out, true);
                i = close + 1;
            } else {
                i++;
            }
        }
    }

    private static int matching(String text, int open, int to) {
        int depth = 0;
        for (int i = open; i < to; i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isName(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                return false;
            }
        }
        return true;
    }
}
