package com.referralconnect.scan;

import java.time.Instant;
import java.util.List;

/**
 * A posting exactly as one careers site reported it, before any role/location filtering.
 *
 * @param locations      every location string the site lists for the posting
 * @param postedAt       first-published time, or null when the site gave nothing parseable
 * @param employmentHint the site's employment-type field, if it has one
 * @param undated        true when the site never publishes posting dates (some Radancy sites);
 *                       the scanner then uses the time the app first saw the job instead
 */
public record RawPosting(
        String atsId,
        String title,
        List<String> locations,
        Instant postedAt,
        String url,
        String employmentHint,
        boolean undated) {

    public RawPosting(String atsId, String title, List<String> locations, Instant postedAt, String url,
                      String employmentHint) {
        this(atsId, title, locations, postedAt, url, employmentHint, false);
    }
}
