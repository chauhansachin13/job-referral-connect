package com.referralconnect.service;

import com.referralconnect.model.Account;
import com.referralconnect.model.CandidateProfile;
import com.referralconnect.model.CompanyBoard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sample accounts for trying the app on one machine ({@code --demo}). Every demo account uses
 * an {@code @demo.local} address so it can never be mistaken for a real person.
 */
public final class DemoData {

    private DemoData() {
    }

    public static final String PASSWORD = "demo1234";
    public static final String SEEKER_EMAIL = "seeker@demo.local";

    private static final String[][] REFERRERS = {
            {"MongoDB", "Software Engineer 3"},
            {"Okta", "Senior Software Engineer"},
            {"Zscaler", "Staff Engineer"},
            {"GitLab", "Senior Data Analyst"},
            {"Stripe", "Software Engineer"},
            {"Databricks", "Solutions Architect"},
            {"Pure Storage", "Member of Technical Staff"},
            {"Rubrik", "SDE-2"},
            {"Notion", "Forward Deployed Engineer"},
            {"Paytm", "Analytics Lead"},
            {"Netskope", "Senior Software Engineer"},
            {"Celonis", "Data Scientist"},
    };

    /** Creates any demo accounts that do not exist yet; returns the emails it created. */
    public static List<String> seed(AppServices app) {
        List<String> created = new ArrayList<>();
        for (String[] r : REFERRERS) {
            CompanyBoard board = app.directory.all().stream()
                    .filter(b -> b.name().equals(r[0])).findFirst().orElse(null);
            if (board == null) {
                continue;
            }
            String email = "referrer." + board.token().replaceAll("[^a-z0-9]", "") + "@demo.local";
            if (exists(app, email)) {
                continue;
            }
            app.auth.registerReferrer("Demo Referrer (" + r[0] + ")", email, PASSWORD, board, r[1]);
            created.add(email);
        }
        if (!exists(app, SEEKER_EMAIL)) {
            Account seeker = app.auth.registerSeeker("Demo Seeker", SEEKER_EMAIL, PASSWORD);
            app.auth.updateProfile(seeker.id(), new CandidateProfile(
                    "Demo Seeker", SEEKER_EMAIL, "+91 90000 00000",
                    "https://www.linkedin.com/in/demo-seeker", "https://github.com/demo-seeker",
                    "https://drive.google.com/file/d/demo-resume/view",
                    "B.Tech Computer Science, 2026", "Fresher · 2 internships (backend, data)",
                    "Java, Spring Boot, SQL, Python, Data Structures, AWS"));
            created.add(SEEKER_EMAIL);
        }
        return created;
    }

    private static boolean exists(AppServices app, String email) {
        String e = email.toLowerCase(Locale.ROOT);
        return app.store.read(s -> s.accounts.stream().anyMatch(a -> a.email().equals(e)));
    }
}
