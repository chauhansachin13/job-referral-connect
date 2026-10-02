package com.referralconnect.scan;

import java.time.Instant;
import java.util.List;

/**
 * A posting exactly as one ATS reported it, before any role/location filtering.
 *
 * @param locations      every location string the ATS lists for the posting
 * @param postedAt       first-published time, or null when the ATS gave nothing parseable
 * @param employmentHint the ATS's employment-type field, if it has one
 */
public record RawPosting(
        String atsId,
        String title,
        List<String> locations,
        Instant postedAt,
        String url,
        String employmentHint) {
}
