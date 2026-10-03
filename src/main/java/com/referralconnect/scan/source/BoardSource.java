package com.referralconnect.scan.source;

import com.referralconnect.model.Ats;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.scan.Http;
import com.referralconnect.scan.RawPosting;

import java.time.Instant;
import java.util.List;

/**
 * Reads one company's openings from one careers platform. Implementations ask the platform for
 * India-located jobs where it can filter, page through results politely, and stop early once
 * postings are older than {@link Context#cutoff()} on platforms that sort newest first.
 */
public interface BoardSource {

    /**
     * @param http     the network
     * @param now      the scan's notion of "now" (Workday reports dates as "Posted 3 Days Ago")
     * @param cutoff   postings published before this are not needed
     * @param deadline when this board's time is up: sources stop paging and return what they have,
     *                 so one slow or throttled careers site cannot hold up the whole scan
     */
    record Context(Http http, Instant now, Instant cutoff, Instant deadline) {
        public boolean outOfTime() {
            return Instant.now().isAfter(deadline);
        }
    }

    List<RawPosting> fetch(CompanyBoard board, Context ctx) throws Exception;

    /**
     * For platforms whose listing has no job description: fetches one posting's description or
     * qualifications (HTML is fine), so its minimum experience can be read. The scanner calls this
     * only for postings that passed the role and India filters and that it has not read before.
     *
     * @return the text, or null when this platform's listing already carries it
     */
    default String details(CompanyBoard board, RawPosting posting, Context ctx) throws Exception {
        return null;
    }

    /** At most this many descriptions are fetched per company per scan; later scans fetch the rest. */
    default int detailsPerScan(CompanyBoard board) {
        return 0;
    }

    static BoardSource of(Ats ats) {
        return switch (ats) {
            case GREENHOUSE, LEVER, ASHBY -> new PublicBoardApiSource();
            case WORKDAY -> new WorkdaySource();
            case SMARTRECRUITERS -> new SmartRecruitersSource();
            case EIGHTFOLD -> new EightfoldSource();
            case ORACLE -> new OracleSource();
            case JIBE -> new JibeSource();
            case RADANCY -> new RadancySource();
            case AMAZON -> new AmazonSource();
            case APPLE -> new AppleSource();
            case GOOGLE -> new GoogleSource();
            case IBM -> new IbmSource();
        };
    }
}
