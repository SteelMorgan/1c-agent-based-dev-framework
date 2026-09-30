package io.github.onec.xmlgen.editor.skd;

import java.util.Objects;

/**
 * Движок текстовых замен для {@code patch-query}.
 *
 * <p>Реализует контракт из skill {@code skd-edit/references/query.md}:
 * <ul>
 *     <li>{@code OnceMode.OFF} — заменяет все вхождения; если 0 совпадений — ошибка
 *         «no matches».</li>
 *     <li>{@code OnceMode.ON} — требует ровно одно вхождение; при 0 → «substring not found»,
 *         при ≥2 → «substring is ambiguous: N matches».</li>
 * </ul>
 */
public final class PatchQueryEngine {

    public enum OnceMode { OFF, ON }

    private PatchQueryEngine() {}

    /** Результат замены: новый текст + количество выполненных замен. */
    public static final class Result {
        public final String text;
        public final int replacements;
        public Result(String text, int replacements) {
            this.text = text;
            this.replacements = replacements;
        }
    }

    public static Result replace(String text, String from, String to, OnceMode mode) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isEmpty()) {
            throw new SkdParseException("patch-query: empty 'from' substring", 0);
        }

        int count = countOccurrences(text, from);

        if (mode == OnceMode.ON) {
            if (count == 0) {
                throw new SkdParseException("patch-query @once: substring not found", -1);
            }
            if (count >= 2) {
                throw new SkdParseException(
                        "patch-query @once: substring is ambiguous: " + count + " matches (file unchanged)",
                        -1);
            }
        } else {
            if (count == 0) {
                throw new SkdParseException("patch-query: no matches", -1);
            }
        }

        // Buffer-based replace to handle overlapping safely (left-to-right, non-overlapping).
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        int replaced = 0;
        while (i < text.length()) {
            int p = text.indexOf(from, i);
            if (p < 0) {
                sb.append(text, i, text.length());
                break;
            }
            sb.append(text, i, p);
            sb.append(to);
            replaced++;
            i = p + from.length();
        }
        return new Result(sb.toString(), replaced);
    }

    public static int countOccurrences(String text, String sub) {
        if (sub.isEmpty()) return 0;
        int count = 0;
        int i = 0;
        while (true) {
            int p = text.indexOf(sub, i);
            if (p < 0) break;
            count++;
            i = p + sub.length();
        }
        return count;
    }
}
