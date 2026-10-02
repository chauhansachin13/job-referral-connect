package com.referralconnect.scan;

import com.referralconnect.model.Ats;
import com.referralconnect.model.CompanyBoard;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lets a referrer paste their company's careers-board link instead of knowing what an
 * "ATS board token" is. Recognises Greenhouse, Lever and Ashby board and API URLs.
 */
public final class BoardUrlParser {

    private BoardUrlParser() {
    }

    private static final Pattern GREENHOUSE = Pattern.compile(
            "(?:job-)?boards(?:-api)?\\.greenhouse\\.io/(?:v1/boards/|embed/job_board\\?for=)?([A-Za-z0-9_-]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEVER = Pattern.compile(
            "(?:jobs|api)\\.lever\\.co/(?:v0/postings/)?([A-Za-z0-9_.-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ASHBY = Pattern.compile(
            "(?:jobs\\.ashbyhq\\.com/|api\\.ashbyhq\\.com/posting-api/job-board/)([A-Za-z0-9_.%-]+)",
            Pattern.CASE_INSENSITIVE);

    public static Optional<CompanyBoard> parse(String companyName, String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        String u = url.trim();
        Matcher m;
        if ((m = GREENHOUSE.matcher(u)).find()) {
            return Optional.of(new CompanyBoard(companyName, Ats.GREENHOUSE, m.group(1)));
        }
        if ((m = LEVER.matcher(u)).find()) {
            return Optional.of(new CompanyBoard(companyName, Ats.LEVER, m.group(1)));
        }
        if ((m = ASHBY.matcher(u)).find()) {
            return Optional.of(new CompanyBoard(companyName, Ats.ASHBY, m.group(1)));
        }
        return Optional.empty();
    }
}
