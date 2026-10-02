package com.referralconnect.model;

import com.referralconnect.json.Json;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One company's job board. The {@link #key()} ties jobs, referrers and referral requests to the
 * same company, so a referrer registered against a board receives requests for its jobs.
 *
 * @param token where the board lives on its platform; see {@link Ats} for the format
 */
public record CompanyBoard(String name, Ats ats, String token) {

    public CompanyBoard {
        name = name == null ? "" : name.trim();
        token = token == null ? "" : token.trim();
        if (ats.caseInsensitiveToken()) {
            token = token.toLowerCase(Locale.ROOT);
        }
    }

    public String key() {
        return ats.name().toLowerCase(Locale.ROOT) + ":" + token.toLowerCase(Locale.ROOT);
    }

    /** The token split on its platform's separator ("/" for Workday, "|" for Eightfold and Oracle). */
    public String[] tokenParts() {
        return token.split(ats == Ats.WORKDAY ? "/" : "\\|");
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
