package com.referralconnect.scan.source;

import com.referralconnect.json.Json;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.scan.RawPosting;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Oracle Recruiting Cloud career sites (e.g. JPMorgan Chase) via the candidate-experience REST
 * resource. The India location id differs per company, so it is looked up from the location facet
 * first; results are then read newest first.
 */
final class OracleSource implements BoardSource {

    static final int PAGE_SIZE = 25;
    static final int MAX_PAGES = 20;

    @Override
    public List<RawPosting> fetch(CompanyBoard board, Context ctx) throws Exception {
        String[] p = board.tokenParts();
        if (p.length != 2) {
            throw new IOException("Oracle board must look like host|siteNumber, got " + board.token());
        }
        String host = p[0];
        String site = p[1];
        Map<String, Object> facets = firstItem(ctx.http().get(url(host, site, 1, 0, null)));
        String indiaId = null;
        for (Object o : Json.arr(facets, "locationsFacet")) {
            Map<String, Object> f = Json.asObject(o);
            if (Json.str(f, "Name").trim().equalsIgnoreCase("India")) {
                indiaId = Json.str(f, "Id");
            }
        }
        if (indiaId == null) {
            throw new IOException("no India location on this Oracle careers site");
        }
        List<RawPosting> out = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            if (!out.isEmpty() && ctx.outOfTime()) {
                break; // this company has used its time: keep what we have
            }
            Map<String, Object> item;
            try {
                item = firstItem(ctx.http().get(url(host, site, PAGE_SIZE, page * PAGE_SIZE, indiaId)));
            } catch (IOException e) {
                if (out.isEmpty()) {
                    throw e;
                }
                break; // keep the newest pages we already have
            }
            List<Object> reqs = Json.arr(item, "requisitionList");
            Instant oldest = null;
            for (Object o : reqs) {
                Map<String, Object> r = Json.asObject(o);
                Instant posted = date(Json.str(r, "PostedDate"));
                if (posted != null && (oldest == null || posted.isBefore(oldest))) {
                    oldest = posted;
                }
                List<String> locations = new ArrayList<>();
                locations.add(Json.str(r, "PrimaryLocation"));
                for (Object s : Json.arr(r, "secondaryLocations")) {
                    locations.add(Json.str(Json.asObject(s), "Name"));
                }
                locations.add("India");
                String id = Json.str(r, "Id");
                out.add(new RawPosting(id, Json.str(r, "Title").trim(), locations, posted,
                        "https://" + host + "/hcmUI/CandidateExperience/en/sites/" + site + "/job/" + id,
                        Json.str(r, "WorkerType") + " " + Json.str(r, "JobType")));
            }
            if (reqs.size() < PAGE_SIZE || (oldest != null && oldest.isBefore(ctx.cutoff()))) {
                break;
            }
        }
        return out;
    }

    /** The requisition list has no description; the details resource has qualifications and duties. */
    @Override
    public String details(CompanyBoard board, RawPosting posting, Context ctx) throws Exception {
        String[] p = board.tokenParts();
        Map<String, Object> item = firstItem(ctx.http().get("https://" + p[0]
                + "/hcmRestApi/resources/latest/recruitingCEJobRequisitionDetails?expand=all&onlyData=true"
                + "&finder=ById;Id=%22" + posting.atsId() + "%22,siteNumber=" + p[1]));
        return Json.str(item, "ExternalQualificationsStr") + "\n" + Json.str(item, "ExternalDescriptionStr");
    }

    @Override
    public int detailsPerScan(CompanyBoard board) {
        return 100;
    }

    private static String url(String host, String site, int limit, int offset, String locationId) {
        return "https://" + host + "/hcmRestApi/resources/latest/recruitingCEJobRequisitions?onlyData=true"
                + "&expand=requisitionList.secondaryLocations&finder=findReqs;siteNumber=" + site
                + ",facetsList=LOCATIONS,limit=" + limit + ",offset=" + offset
                + (locationId == null ? "" : ",locationId=" + locationId)
                + ",sortBy=POSTING_DATES_DESC";
    }

    private static Map<String, Object> firstItem(String body) {
        List<Object> items = Json.arr(Json.asObject(Json.parse(body)), "items");
        return items.isEmpty() ? Map.of() : Json.asObject(items.get(0));
    }

    private static Instant date(String s) {
        try {
            return s.isBlank() ? null : LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
