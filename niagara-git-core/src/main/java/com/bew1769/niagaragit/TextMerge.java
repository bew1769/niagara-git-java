package com.bew1769.niagaragit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Insert, replace, or remove a marked block in a text file. */
final class TextMerge {
    static final String BLOCK_BEGIN = "# niagara-git:begin";
    static final String BLOCK_END = "# niagara-git:end";
    static final String STORE_BEGIN = "# niagara-git-store:begin";
    static final String STORE_END = "# niagara-git-store:end";

    private TextMerge() {
    }

    static String mergeMarked(String existing, String begin, String end, String block) {
        Pattern pattern = Pattern.compile(Pattern.quote(begin) + ".*?" + Pattern.quote(end) + "\\n?", Pattern.DOTALL);
        String text = existing.replace("\r\n", "\n").replace("\r", "\n");
        String snippet = trimNewlineEdges(block) + "\n";
        if (text.length() > 0 && !text.endsWith("\n")) {
            text = text + "\n";
        }
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String replaced = pattern.matcher(text).replaceAll("\u0000");
            int first = replaced.indexOf('\u0000');
            if (first >= 0) {
                replaced = replaced.substring(0, first) + snippet + replaced.substring(first + 1).replace("\u0000", "");
            }
            return replaced;
        }
        if (text.isEmpty()) {
            return snippet;
        }
        if (!text.endsWith("\n\n")) {
            return text + "\n" + snippet;
        }
        return text + snippet;
    }

    static String removeMarked(String existing, String begin, String end) {
        Pattern pattern = Pattern.compile(Pattern.quote(begin) + ".*?" + Pattern.quote(end) + "\\n?", Pattern.DOTALL);
        String text = existing.replace("\r\n", "\n").replace("\r", "\n");
        text = pattern.matcher(text).replaceAll("");
        while (text.contains("\n\n\n")) {
            text = text.replace("\n\n\n", "\n\n");
        }
        return text;
    }

    static String mergeBlock(String existing, String block) {
        return mergeMarked(existing, BLOCK_BEGIN, BLOCK_END, block);
    }

    /** POSIX single quotes. Git runs textconv through a shell, including Git for Windows. */
    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static String trimNewlineEdges(String block) {
        int start = 0;
        int end = block.length();
        while (start < end && block.charAt(start) == '\n') {
            start++;
        }
        while (end > start && block.charAt(end - 1) == '\n') {
            end--;
        }
        return block.substring(start, end);
    }
}
