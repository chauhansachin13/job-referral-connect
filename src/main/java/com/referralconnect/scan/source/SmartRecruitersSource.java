package com.referralconnect.scan.source;

import com.referralconnect.json.Json;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.scan.AtsParsers;
import com.referralconnect.scan.RawPosting;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** SmartRecruiters' public postings API (Bosch, ServiceNow, Freshworks, …), filtered to India. */
final class SmartRecruitersSource implements BoardSource {

    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 10;

    @Override
    public List<RawPosting> fetch(CompanyBoard board, Context ctx) throws Exception {
        String company = URLEncoder.encode(board.token(), StandardCharsets.UTF_8);
        List<RawPosting> out = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            if (!out.isEmpty() && ctx.outOfTime()) {
                break; // this company has used its time: keep what we have
            }
            int offset = page * PAGE_SIZE;
            String body;
            try {
                body = ctx.http().get("https://api.smartrecruiters.com/v1/companies/" + company
                        + "/postings?country=in&limit=" + PAGE_SIZE + "&offset=" + offset);
            } catch (IOException e) {
                if (out.isEmpty()) {
                    throw e;
                }
                break; // keep the newest pages we already have
            }
            Map<String, Object> r = Json.asObject(Json.parse(body));
            List<Object> content = Json.arr(r, "content");
            boolean anyRecent = false;
            for (Object o : content) {
                Map<String, Object> j = Json.asObject(o);
                Map<String, Object> loc = Json.obj(j, "location");
                Instant posted = AtsParsers.parseTime(Json.str(j, "releasedDate"));
                anyRecent |= posted != null && !posted.isBefore(ctx.cutoff());
                out.add(new RawPosting(
                        Json.str(j, "id"),
                        Json.str(j, "name").trim(),
                        List.of(Json.str(loc, "fullLocation"), Json.str(loc, "city"), "India"),
                        posted,
                        "https://jobs.smartrecruiters.com/" + board.token() + "/" + Json.str(j, "id"),
                        Json.str(Json.obj(j, "typeOfEmployment"), "label") + " "
                                + Json.str(Json.obj(j, "experienceLevel"), "label")));
            }
            // Results come newest first: a page with nothing recent means the rest is older still.
            if (content.isEmpty() || !anyRecent || offset + PAGE_SIZE >= Json.num(r, "totalFound", 0)) {
                break;
            }
        }
        return out;
    }
}
