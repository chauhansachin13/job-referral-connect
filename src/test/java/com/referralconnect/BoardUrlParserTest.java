package com.referralconnect;

import com.referralconnect.TestRunner.Test;
import com.referralconnect.model.Ats;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.scan.BoardUrlParser;

import java.util.Optional;

import static com.referralconnect.TestRunner.check;
import static com.referralconnect.TestRunner.equal;

class BoardUrlParserTest {

    private static void parses(String url, Ats ats, String token) {
        Optional<CompanyBoard> board = BoardUrlParser.parse("Acme", url);
        check(board.isPresent(), "should parse " + url);
        equal(ats, board.get().ats(), url);
        equal(token, board.get().token(), url);
    }

    @Test
    void recognisesCareersLinks() {
        parses("https://job-boards.greenhouse.io/stripe", Ats.GREENHOUSE, "stripe");
        parses("https://boards.greenhouse.io/mongodb/jobs/123", Ats.GREENHOUSE, "mongodb");
        parses("boards-api.greenhouse.io/v1/boards/okta/jobs", Ats.GREENHOUSE, "okta");
        parses("https://jobs.lever.co/Paytm/abc-123", Ats.LEVER, "paytm");
        parses("https://api.lever.co/v0/postings/meesho?mode=json", Ats.LEVER, "meesho");
        parses("https://jobs.ashbyhq.com/notion", Ats.ASHBY, "notion");
        parses("https://nvidia.wd5.myworkdayjobs.com/en-US/NVIDIAExternalCareerSite/job/India-Bengaluru/SDE_JR1",
                Ats.WORKDAY, "nvidia/wd5/NVIDIAExternalCareerSite");
        parses("https://Citi.wd5.myworkdayjobs.com/2", Ats.WORKDAY, "citi/wd5/2");
        parses("https://jobs.smartrecruiters.com/BoschGroup/744000152968069", Ats.SMARTRECRUITERS, "BoschGroup");
    }

    @Test
    void rejectsOtherLinks() {
        check(BoardUrlParser.parse("Acme", "https://www.linkedin.com/jobs/view/1").isEmpty(), "LinkedIn");
        check(BoardUrlParser.parse("Acme", "https://acme.com/careers").isEmpty(), "own site");
        check(BoardUrlParser.parse("Acme", "").isEmpty(), "empty");
    }

    @Test
    void boardKeyIsStableAcrossCase() {
        equal(new CompanyBoard("A", Ats.LEVER, "Paytm").key(), new CompanyBoard("B", Ats.LEVER, "paytm").key());
        equal("lever:paytm", new CompanyBoard("A", Ats.LEVER, " Paytm ").key());
    }
}
