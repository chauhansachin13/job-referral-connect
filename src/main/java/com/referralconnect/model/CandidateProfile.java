package com.referralconnect.model;

import com.referralconnect.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything a referrer needs to know about a candidate. A copy is frozen into each referral
 * request, so later profile edits do not change what a referrer has already been sent.
 */
public record CandidateProfile(
        String name,
        String email,
        String phone,
        String linkedin,
        String github,
        String resumeLink,
        String education,
        String experience,
        String skills) {

    public CandidateProfile {
        name = clean(name);
        email = clean(email).toLowerCase(Locale.ROOT);
        phone = clean(phone);
        linkedin = clean(linkedin);
        github = clean(github);
        resumeLink = clean(resumeLink);
        education = clean(education);
        experience = clean(experience);
        skills = clean(skills);
    }

    public static CandidateProfile of(String name, String email) {
        return new CandidateProfile(name, email, "", "", "", "", "", "", "");
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    /** Fields a referrer cannot work without; empty when the profile is complete enough to send. */
    public List<String> missingForReferral() {
        List<String> missing = new ArrayList<>();
        if (name.isEmpty()) {
            missing.add("name");
        }
        if (email.isEmpty()) {
            missing.add("email");
        }
        if (resumeLink.isEmpty()) {
            missing.add("resume link");
        }
        if (education.isEmpty() && experience.isEmpty()) {
            missing.add("education or experience");
        }
        if (skills.isEmpty()) {
            missing.add("skills");
        }
        return missing;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("email", email);
        m.put("phone", phone);
        m.put("linkedin", linkedin);
        m.put("github", github);
        m.put("resumeLink", resumeLink);
        m.put("education", education);
        m.put("experience", experience);
        m.put("skills", skills);
        return m;
    }

    public static CandidateProfile fromJson(Map<String, Object> m) {
        return new CandidateProfile(
                Json.str(m, "name"), Json.str(m, "email"), Json.str(m, "phone"),
                Json.str(m, "linkedin"), Json.str(m, "github"), Json.str(m, "resumeLink"),
                Json.str(m, "education"), Json.str(m, "experience"), Json.str(m, "skills"));
    }
}
