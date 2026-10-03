package com.referralconnect.scan;

import java.time.Instant;
import java.util.List;

/**
 * A posting exactly as one careers site reported it, before any role/location filtering.
 *
 * @param locations      every location string the site lists for the posting
 * @param postedAt       first-published time, or null when the site gave nothing parseable
 * @param employmentHint the site's employment-type or experience-level field, if it has one
 * @param undated        true when the site never publishes posting dates (some Radancy sites);
 *                       the scanner then uses the time the app first saw the job instead
 * @param details        the description or qualifications text when the listing includes it (HTML
 *                       is fine), else empty; sources without it fetch it per job later
 */
public record RawPosting(
        String atsId,
        String title,
        List<String> locations,
        Instant postedAt,
        String url,
        String employmentHint,
        boolean undated,
        String details) {

    public RawPosting {
        details = details == null ? "" : details;
    }

    public RawPosting(String atsId, String title, List<String> locations, Instant postedAt, String url,
                      String employmentHint, boolean undated) {
        this(atsId, title, locations, postedAt, url, employmentHint, undated, "");
    }

    public RawPosting(String atsId, String title, List<String> locations, Instant postedAt, String url,
                      String employmentHint) {
        this(atsId, title, locations, postedAt, url, employmentHint, false, "");
    }

    public RawPosting withUrl(String newUrl) {
        return new RawPosting(atsId, title, locations, postedAt, newUrl, employmentHint, undated, details);
    }
}
