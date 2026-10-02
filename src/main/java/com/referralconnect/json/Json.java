package com.referralconnect.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer so the project needs nothing beyond the JDK.
 *
 * <p>Parsing maps JSON onto plain Java types: objects become {@code LinkedHashMap<String,Object>},
 * arrays become {@code ArrayList<Object>}, numbers become {@code Long} when integral and
 * {@code Double} otherwise, plus {@code String}, {@code Boolean} and {@code null}.
 */
public final class Json {

    private Json() {
    }

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object value = p.readValue();
        p.skipWhitespace();
        if (!p.atEnd()) {
            throw p.error("Unexpected trailing content");
        }
        return value;
    }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, 0, true);
        return sb.toString();
    }

    public static String writeCompact(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, 0, false);
        return sb.toString();
    }

    // ---------------------------------------------------------------- typed accessors

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object value) {
        return value instanceof List ? (List<Object>) value : List.of();
    }

    public static String str(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    public static long num(Map<String, Object> obj, String key, long fallback) {
        Object v = obj.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static boolean bool(Map<String, Object> obj, String key) {
        return Boolean.TRUE.equals(obj.get(key));
    }

    public static Map<String, Object> obj(Map<String, Object> obj, String key) {
        return asObject(obj.get(key));
    }

    public static List<Object> arr(Map<String, Object> obj, String key) {
        return asArray(obj.get(key));
    }

    // ---------------------------------------------------------------- writer

    private static void writeValue(StringBuilder sb, Object value, int depth, boolean pretty) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte) {
            sb.append(value);
        } else if (value instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                newline(sb, depth + 1, pretty);
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(pretty ? ": " : ":");
                writeValue(sb, e.getValue(), depth + 1, pretty);
            }
            newline(sb, depth, pretty);
            sb.append('}');
        } else if (value instanceof Iterable<?> list) {
            if (!list.iterator().hasNext()) {
                sb.append("[]");
                return;
            }
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                newline(sb, depth + 1, pretty);
                writeValue(sb, item, depth + 1, pretty);
            }
            newline(sb, depth, pretty);
            sb.append(']');
        } else if (value instanceof Enum<?> e) {
            writeString(sb, e.name());
        } else {
            writeString(sb, value.toString());
        }
    }

    private static void newline(StringBuilder sb, int depth, boolean pretty) {
        if (pretty) {
            sb.append('\n');
            sb.append("  ".repeat(depth));
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- parser

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        JsonException error(String message) {
            return new JsonException(message + " at position " + pos);
        }

        void skipWhitespace() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object readValue() {
            if (atEnd()) {
                throw error("Unexpected end of input");
            }
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield readNumber();
                    }
                    throw error("Unexpected character '" + c + "'");
                }
            };
        }

        Object literal(String word, Object value) {
            if (!s.startsWith(word, pos)) {
                throw error("Expected " + word);
            }
            pos += word.length();
            return value;
        }

        Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw error("Expected object key");
                }
                String key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                map.put(key, readValue());
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    pos--;
                    throw error("Expected ',' or '}'");
                }
            }
        }

        List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(readValue());
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    pos--;
                    throw error("Expected ',' or ']'");
                }
            }
        }

        String readString() {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("Unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (atEnd()) {
                    throw error("Unterminated escape");
                }
                char e = s.charAt(pos++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > s.length()) {
                            throw error("Bad unicode escape");
                        }
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("Bad unicode escape");
                        }
                        pos += 4;
                    }
                    default -> throw error("Bad escape '\\" + e + "'");
                }
            }
        }

        Object readNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            boolean fractional = false;
            while (!atEnd()) {
                char c = s.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    fractional = true;
                    pos++;
                } else {
                    break;
                }
            }
            String token = s.substring(start, pos);
            try {
                if (!fractional) {
                    return Long.parseLong(token);
                }
                return Double.parseDouble(token);
            } catch (NumberFormatException ex) {
                // Integers too large for a long still parse as doubles.
                try {
                    return Double.parseDouble(token);
                } catch (NumberFormatException ex2) {
                    pos = start;
                    throw error("Bad number '" + token + "'");
                }
            }
        }

        char peek() {
            return atEnd() ? '\0' : s.charAt(pos);
        }

        char next() {
            if (atEnd()) {
                throw error("Unexpected end of input");
            }
            return s.charAt(pos++);
        }

        void expect(char c) {
            if (next() != c) {
                pos--;
                throw error("Expected '" + c + "'");
            }
        }
    }

    public static final class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }
}
