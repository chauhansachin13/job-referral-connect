package com.referralconnect.service;

import com.referralconnect.model.JobPosting;
import com.referralconnect.model.ReferralRequest;
import com.referralconnect.model.TrackedJob;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Spreadsheet-ready CSV (RFC 4180) of openings, referral requests and the application tracker. */
public final class CsvExport {

    private CsvExport() {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());

    public static String jobs(List<JobPosting> jobs) {
        StringBuilder sb = new StringBuilder();
        row(sb, "Posted", "Date known", "Company", "Role", "Category", "Type", "City", "Location",
                "Min experience (years)", "Experience", "Degree", "Batch", "Skills", "Link");
        for (JobPosting j : jobs) {
            row(sb, DATE.format(j.postedAt()), j.dateKnown() ? "yes" : "first seen", j.company(), j.title(),
                    j.category().label(), j.typeLabel(), j.city(), j.location(),
                    j.requirements().known() ? String.valueOf(j.requirements().minYears()) : "",
                    j.internship() && j.requirements().minYears() <= 0 ? "Internship (students)"
                            : j.requirements().longLabel(),
                    j.requirements().degree(), j.requirements().batch(), String.join("; ", j.requirements().skills()),
                    j.url());
        }
        return sb.toString();
    }

    public static String requests(List<ReferralRequest> requests) {
        StringBuilder sb = new StringBuilder();
        row(sb, "Request", "Sent", "Company", "Role", "Referrer", "Status", "Updated", "Referrer note", "Link");
        for (ReferralRequest r : requests) {
            row(sb, r.id(), DATE.format(r.createdAt()), r.job().company(), r.job().title(), r.referrerTitle(),
                    r.status().label(), DATE.format(r.updatedAt()), r.referrerNote(), r.job().url());
        }
        return sb.toString();
    }

    public static String tracker(List<TrackedJob> tracked) {
        StringBuilder sb = new StringBuilder();
        row(sb, "Stage", "Company", "Role", "City", "Min experience", "Saved", "Updated", "Note", "Link");
        for (TrackedJob t : tracked) {
            JobPosting j = t.job();
            row(sb, t.stage().label(), j.company(), j.title(), j.city(), j.experienceLabel(),
                    DATE.format(t.savedAt()), DATE.format(t.updatedAt()), t.note(), j.url());
        }
        return sb.toString();
    }

    private static void row(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cell(cells[i]));
        }
        sb.append("\r\n");
    }

    /** Quotes a cell when needed. A leading =, +, - or @ is escaped so spreadsheets don't run it as a formula. */
    static String cell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
