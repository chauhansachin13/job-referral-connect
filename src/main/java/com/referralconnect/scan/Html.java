package com.referralconnect.scan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small helpers for the HTML that careers sites send. */
public final class Html {

    private Html() {
    }

    /**
     * Turns the Unicode spaces sites use ("&#xa0;" no-break spaces, thin and narrow spaces) into plain
     * spaces and drops zero-width ones, so "12+&#xa0;years" reads like "12+ years".
     */
    public static String plainSpaces(String s) {
        return s == null ? null : s.replaceAll("[\\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000]", " ")
                .replaceAll("[\\u200B-\\u200D\\u2060\\uFEFF]", "");
    }

    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(?:[xX]([0-9A-Fa-f]{1,8})|([0-9]{1,10}));");

    /**
     * Decodes numeric character references ("&#8217;", "&#x2019;"). A reference that names no real
     * character ("&#99999999999;", "&#xFFFFFFFF;", "&#0;") is left as it is: one malformed posting
     * must never stop a company's openings from being read.
     */
    public static String decodeNumericEntities(String s) {
        if (s == null || s.indexOf("&#") < 0) {
            return s;
        }
        Matcher m = NUMERIC_ENTITY.matcher(s);
        StringBuilder sb = new StringBuilder(s.length());
        while (m.find()) {
            String replacement = m.group();
            try {
                long code = m.group(1) != null ? Long.parseLong(m.group(1), 16) : Long.parseLong(m.group(2));
                if (code > 0 && code <= Character.MAX_CODE_POINT && Character.isValidCodePoint((int) code)
                        && !(code >= Character.MIN_SURROGATE && code <= Character.MAX_SURROGATE)) {
                    replacement = new String(Character.toChars((int) code));
                }
            } catch (NumberFormatException ignored) {
                // Keep the text as it was.
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
