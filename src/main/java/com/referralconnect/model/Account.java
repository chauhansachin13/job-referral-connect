package com.referralconnect.model;

import com.referralconnect.json.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A registered user. Seekers fill in the full {@link CandidateProfile}; referrers only need a
 * name and email in it, plus the company board they can refer for.
 */
public final class Account {

    private final String id;
    private final Role role;
    private final Instant createdAt;
    private String passwordHash;
    private String passwordSalt;
    private CandidateProfile profile;

    // Referrer-only fields.
    private String companyKey = "";
    private String companyName = "";
    private String designation = "";
    private boolean acceptingRequests = true;

    public Account(String id, Role role, Instant createdAt, String passwordHash, String passwordSalt,
                   CandidateProfile profile) {
        this.id = id;
        this.role = role;
        this.createdAt = createdAt;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.profile = profile;
    }

    public String id() {
        return id;
    }

    public Role role() {
        return role;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String passwordSalt() {
        return passwordSalt;
    }

    public void setPassword(String hash, String salt) {
        this.passwordHash = hash;
        this.passwordSalt = salt;
    }

    public CandidateProfile profile() {
        return profile;
    }

    public void setProfile(CandidateProfile profile) {
        this.profile = profile;
    }

    public String name() {
        return profile.name();
    }

    public String email() {
        return profile.email();
    }

    public boolean isReferrer() {
        return role == Role.REFERRER;
    }

    public String companyKey() {
        return companyKey;
    }

    public String companyName() {
        return companyName;
    }

    public void setCompany(CompanyBoard board) {
        this.companyKey = board.key();
        this.companyName = board.name();
    }

    public String designation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation == null ? "" : designation.trim();
    }

    public boolean acceptingRequests() {
        return acceptingRequests;
    }

    public void setAcceptingRequests(boolean acceptingRequests) {
        this.acceptingRequests = acceptingRequests;
    }

    /** "Priya Sharma, SDE-2 at MongoDB" — how a referrer is introduced to seekers. */
    public String referrerTitle() {
        StringBuilder sb = new StringBuilder(name());
        if (!designation.isEmpty()) {
            sb.append(", ").append(designation);
        }
        if (!companyName.isEmpty()) {
            sb.append(" at ").append(companyName);
        }
        return sb.toString();
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("role", role.name());
        m.put("createdAt", createdAt.toString());
        m.put("passwordHash", passwordHash);
        m.put("passwordSalt", passwordSalt);
        m.put("profile", profile.toJson());
        if (role == Role.REFERRER) {
            m.put("companyKey", companyKey);
            m.put("companyName", companyName);
            m.put("designation", designation);
            m.put("acceptingRequests", acceptingRequests);
        }
        return m;
    }

    public static Account fromJson(Map<String, Object> m) {
        Account a = new Account(
                Json.str(m, "id"),
                Role.valueOf(Json.str(m, "role")),
                Instant.parse(Json.str(m, "createdAt")),
                Json.str(m, "passwordHash"),
                Json.str(m, "passwordSalt"),
                CandidateProfile.fromJson(Json.obj(m, "profile")));
        a.companyKey = Json.str(m, "companyKey");
        a.companyName = Json.str(m, "companyName");
        a.designation = Json.str(m, "designation");
        a.acceptingRequests = !m.containsKey("acceptingRequests") || Json.bool(m, "acceptingRequests");
        return a;
    }
}
