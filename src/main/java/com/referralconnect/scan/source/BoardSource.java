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

    static BoardSource of(Ats ats) {
        return switch (ats) {
            case GREENHOUSE, LEVER, ASHBY -> new PublicBoardApiSource();
            case WORKDAY -> new WorkdaySource();
            case SMARTRECRUITERS -> new SmartRecruitersSource();
            case EIGHTFOLD -> new EightfoldSource();
            case ORACLE -> new OracleSource();
            case AMAZON -> new AmazonSource();
            case APPLE -> new AppleSource();
            case GOOGLE -> new GoogleSource();
        };
    }
}
