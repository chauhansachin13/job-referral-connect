package com.referralconnect.model;

import com.referralconnect.json.Json;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One company's public job board. The {@link #key()} ties jobs, referrers and referral requests
 * to the same company, so a referrer registered against a board receives requests for its jobs.
 */
public record CompanyBoard(String name, Ats ats, String token) {

    public CompanyBoard {
        name = name == null ? "" : name.trim();
        token = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
    }

    public String key() {
        return ats.name().toLowerCase(Locale.ROOT) + ":" + token;
    }

    public String apiUrl() {
        return ats.apiUrl(token);
    }

    public String boardUrl() {
        return ats.boardUrl(token);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("ats", ats.name());
        m.put("token", token);
        return m;
    }

    public static CompanyBoard fromJson(Map<String, Object> m) {
        return new CompanyBoard(Json.str(m, "name"), Ats.valueOf(Json.str(m, "ats")), Json.str(m, "token"));
    }

    @Override
    public String toString() {
        return name;
    }
}
