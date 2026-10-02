package com.referralconnect.scan.source;

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

    @Override
    public List<RawPosting> fetch(CompanyBoard board, Context ctx) throws Exception {
        return AtsParsers.parse(board.ats(), ctx.http().get(apiUrl(board)));
    }
}
