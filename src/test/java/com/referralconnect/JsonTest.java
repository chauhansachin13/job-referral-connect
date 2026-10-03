package com.referralconnect;

import com.referralconnect.TestRunner.Test;
import com.referralconnect.json.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.referralconnect.TestRunner.check;
import static com.referralconnect.TestRunner.equal;
import static com.referralconnect.TestRunner.fails;

class JsonTest {

    @Test
    void parsesNestedStructures() {
        Map<String, Object> root = Json.asObject(Json.parse(
                "{\"jobs\":[{\"id\":42,\"title\":\"SDE\",\"remote\":true,\"pay\":null,\"score\":4.5}],\"n\":-3}"));
        Map<String, Object> job = Json.asObject(Json.arr(root, "jobs").get(0));
        equal(42L, job.get("id"));
        equal("SDE", job.get("title"));
        equal(true, job.get("remote"));
        check(job.containsKey("pay") && job.get("pay") == null, "null kept");
        equal(4.5, job.get("score"));
        equal(-3L, root.get("n"));
    }

    @Test
    void decodesEscapes() {
        equal("a\"b\\c/d\ne\tf", Json.parse("\"a\\\"b\\\\c\\/d\\ne\\tf\""));
        equal("Bengaluru – India", Json.parse("\"Bengaluru \\u2013 India\""));
    }

    @Test
    void parsesNumbersOutsideLongRange() {
        Object big = Json.parse("123456789012345678901234567890");
        check(big instanceof Double, "falls back to double");
        equal(1.0e-3, Json.parse("1e-3"));
    }

    @Test
    void roundTripsThroughWriter() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "Priya \"P\" Sharma\n");
        m.put("tags", List.of("java", "sql"));
        m.put("count", 3L);
        m.put("ok", false);
        m.put("none", null);
        equal(m, Json.parse(Json.write(m)));
        equal(m, Json.parse(Json.writeCompact(m)));
    }

    @Test
    void rejectsMalformedInput() {
        fails(Json.JsonException.class, () -> Json.parse("{\"a\":1,}"));
        fails(Json.JsonException.class, () -> Json.parse("[1 2]"));
        fails(Json.JsonException.class, () -> Json.parse("\"unterminated"));
        fails(Json.JsonException.class, () -> Json.parse("{} trailing"));
        // Nested 100,000 deep: refused cleanly instead of overflowing the stack.
        fails(Json.JsonException.class, () -> Json.parse("[".repeat(100_000) + "]".repeat(100_000)));
        fails(Json.JsonException.class, () -> Json.parse("{\"a\":".repeat(100_000) + "1" + "}".repeat(100_000)));
        equal(1, Json.asArray(Json.parse("[".repeat(100) + "1" + "]".repeat(100))).size());
    }

    @Test
    void typedAccessorsTolerateMissingOrWrongTypes() {
        Map<String, Object> m = Json.asObject(Json.parse("{\"n\":\"17\",\"s\":5}"));
        equal(17L, Json.num(m, "n", -1));
        equal(-1L, Json.num(m, "missing", -1));
        equal("5", Json.str(m, "s"));
        equal("", Json.str(m, "missing"));
        check(Json.arr(m, "s").isEmpty(), "non-array reads as empty");
    }
}
