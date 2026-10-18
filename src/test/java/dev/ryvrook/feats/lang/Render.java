package dev.ryvrook.feats.lang;

import com.hypixel.hytale.protocol.BoolParamValue;
import com.hypixel.hytale.protocol.DoubleParamValue;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.IntParamValue;
import com.hypixel.hytale.protocol.LongParamValue;
import com.hypixel.hytale.protocol.ParamValue;
import com.hypixel.hytale.protocol.StringParamValue;
import com.hypixel.hytale.server.core.Message;

import java.util.Map;

/**
 * Renders a message to plain English the way the client would, from the {@link Lang} table and a map of feat
 * keys, enough of the ICU message format for the keys Feats uses, {@code {name}}, {@code {n, number}} and
 * {@code {n, plural, one {..} other {..}}}. For tests only.
 */
public final class Render {
    private Render() {
    }

    /** The English text of a message, with the {@link Lang} table and no feats. */
    public static String english(Message message) {
        return english(message, Lang.english());
    }

    public static String english(Message message, Map<String, String> table) {
        return message == null ? "" : render(message.getFormattedMessage(), table);
    }

    private static String render(FormattedMessage f, Map<String, String> table) {
        StringBuilder sb = new StringBuilder();
        if (f.messageId != null) {
            String template = table.get(f.messageId);
            if (template == null) {
                sb.append('«').append(f.messageId).append('»');
            } else {
                sb.append(fill(template, f, table));
            }
        } else if (f.rawText != null) {
            sb.append(f.rawText);
        }
        if (f.children != null) {
            for (FormattedMessage child : f.children) {
                sb.append(render(child, table));
            }
        }
        return sb.toString();
    }

    private static String fill(String template, FormattedMessage f, Map<String, String> table) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c != '{') {
                sb.append(c);
                i++;
                continue;
            }
            int close = matching(template, i);
            if (close < 0) {
                sb.append(template.substring(i));
                break;
            }
            sb.append(argument(template.substring(i + 1, close), f, table));
            i = close + 1;
        }
        return sb.toString();
    }

    private static String argument(String inner, FormattedMessage f, Map<String, String> table) {
        int comma = inner.indexOf(',');
        String name = (comma < 0 ? inner : inner.substring(0, comma)).trim();
        String value = value(name, f, table);
        if (comma < 0) {
            return value;
        }
        String rest = inner.substring(comma + 1).trim();
        if (rest.startsWith("number")) {
            try {
                return String.format("%,d", Long.parseLong(value));
            } catch (NumberFormatException e) {
                return value;
            }
        }
        if (rest.startsWith("plural")) {
            String forms = rest.substring("plural".length()).trim();
            if (forms.startsWith(",")) {
                forms = forms.substring(1).trim();
            }
            String form = "1".equals(value) ? form(forms, "one") : form(forms, "other");
            if (form == null) {
                form = form(forms, "other");
            }
            return form == null ? value : fill(form.replace("#", value), f, table);
        }
        return value;
    }

    private static String form(String forms, String wanted) {
        int i = 0;
        while (i < forms.length()) {
            int open = forms.indexOf('{', i);
            if (open < 0) {
                return null;
            }
            String label = forms.substring(i, open).trim();
            int close = matching(forms, open);
            if (close < 0) {
                return null;
            }
            if (label.equals(wanted)) {
                return forms.substring(open + 1, close);
            }
            i = close + 1;
        }
        return null;
    }

    private static String value(String name, FormattedMessage f, Map<String, String> table) {
        if (f.messageParams != null && f.messageParams.containsKey(name)) {
            return render(f.messageParams.get(name), table);
        }
        ParamValue p = f.params == null ? null : f.params.get(name);
        if (p instanceof StringParamValue s) {
            return s.value;
        }
        if (p instanceof IntParamValue n) {
            return Integer.toString(n.value);
        }
        if (p instanceof LongParamValue n) {
            return Long.toString(n.value);
        }
        if (p instanceof DoubleParamValue n) {
            return Double.toString(n.value);
        }
        if (p instanceof BoolParamValue b) {
            return Boolean.toString(b.value);
        }
        return "{" + name + "}";
    }

    private static int matching(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
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
}
