package com.referralconnect.scan.source;

import com.referralconnect.json.Json;
import com.referralconnect.model.Ats;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.scan.AtsParsers;
import com.referralconnect.scan.RawPosting;

import java.util.List;

/** Greenhouse, Lever and Ashby: one public JSON document lists every opening on the board. */
final class PublicBoardApiSource implements BoardSource {

    static String apiUrl(CompanyBoard board) {
        return switch (board.ats()) {
            case GREENHOUSE -> "https://boards-api.greenhouse.io/v1/boards/" + board.token() + "/jobs";
            case LEVER -> "https://api.lever.co/v0/postings/" + board.token() + "?mode=json";
            case ASHBY -> "https://api.ashbyhq.com/posting-api/job-board/" + board.token();
            default -> throw new IllegalArgumentException(board.ats() + " is not a public board API");
        };
    }

    /**
     * Greenhouse's own job page for a posting: the description plus the application form.
     *
     * <p>The API's {@code absolute_url} points at each company's own careers site, and those pages
     * are unreliable — some load the job only with JavaScript, Okta's returned 404 and Rubrik's 403 —
     * while this page is served by Greenhouse itself and always shows the job.
     */
    static String greenhouseJobPage(String boardToken, String jobId) {
        return "https://job-boards.greenhouse.io/embed/job_app?for=" + boardToken + "&token=" + jobId;
    }

    @Override
    public List<RawPosting> fetch(CompanyBoard board, Context ctx) throws Exception {
        List<RawPosting> raw = AtsParsers.parse(board.ats(), ctx.http().get(apiUrl(board)));
        if (board.ats() != Ats.GREENHOUSE) {
            return raw;
        }
        return raw.stream().map(r -> r.withUrl(greenhouseJobPage(board.token(), r.atsId()))).toList();
    }

    /** Lever and Ashby list descriptions with every job; Greenhouse needs one request per job. */
    @Override
    public String details(CompanyBoard board, RawPosting posting, Context ctx) throws Exception {
        if (board.ats() != Ats.GREENHOUSE) {
            return null;
        }
        String body = ctx.http().get("https://boards-api.greenhouse.io/v1/boards/" + board.token() + "/jobs/"
                + posting.atsId());
        // Greenhouse escapes the description's HTML ("&lt;p&gt;"); undo that so tags become line breaks.
        return Json.str(Json.asObject(Json.parse(body)), "content")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&amp;", "&");
    }

    @Override
    public int detailsPerScan(CompanyBoard board) {
        return board.ats() == Ats.GREENHOUSE ? 400 : 0;
    }
}
