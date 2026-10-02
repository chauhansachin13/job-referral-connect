package com.referralconnect.scan.source;

import com.referralconnect.json.Json;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.scan.RawPosting;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Radancy TalentBrew career sites (Synopsys, Arm, NetApp, Moody's, …). Their search endpoint returns
 * the results list as an HTML fragment inside JSON. Locations are filtered with GeoNames ids, and
 * 1269750 is India.
 *
 * <p>Several of these sites never show a posting date. Those postings are marked {@code undated} so
 * the app records when it first saw them instead of pretending to know when they were posted.
 */
final class RadancySource implements BoardSource {

    static final String INDIA_GEONAME_ID = "1269750";
    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 10;

    // Sites differ in the details: links may carry a locale ("/en/job/…"), the title may sit in an
    // <h2> or be the link text itself, and the location class is "job-location" or just "location".
    private static final Pattern ITEM = Pattern.compile("<li[^>]*>(.*?)</li>", Pattern.DOTALL);
    private static final Pattern LINK = Pattern.compile(
            "<a[^>]*href=\"((?:/[a-z]{2}(?:-[A-Za-z]{2})?)?/job/[^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern TITLE = Pattern.compile("<h[23][^>]*>(.*?)</h[23]>", Pattern.DOTALL);
    private static final Pattern LOCATION = Pattern.compile(
            "class=\"(?:[^\"]*[ -])?location(?: [^\"]*)?\"[^>]*>(.*?)</", Pattern.DOTALL);
    private static final Pattern DATE = Pattern.compile("class=\"[^\"]*job-date-posted[^\"]*\"[^>]*>(.*?)</", Pattern.DOTALL);
    private static final Pattern TOTAL = Pattern.compile("data-(?:total-results|results-count)=\"(\\d+)\"");
    private static final DateTimeFormatter US_DATE = DateTimeFormatter.ofPattern("M/d/yyyy");

    @Override
    public List<RawPosting> fetch(CompanyBoard board, Context ctx) throws Exception {
        String host = board.token();
        List<RawPosting> out = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            if (!out.isEmpty() && ctx.outOfTime()) {
                break; // this company has used its time: keep what we have
            }
            String body;
            try {
                body = ctx.http().get("https://" + host + "/search-jobs/results?ActiveFacetID=" + INDIA_GEONAME_ID
                        + "&CurrentPage=" + page + "&RecordsPerPage=" + PAGE_SIZE + "&Distance=50&RadiusUnitType=0"
                        + "&Keywords=&Location=&ShowRadius=False&IsPagination=False&CustomFacetName=&FacetTerm=&FacetType=0"
                        + "&FacetFilters%5B0%5D.ID=" + INDIA_GEONAME_ID + "&FacetFilters%5B0%5D.FacetType=2"
                        + "&FacetFilters%5B0%5D.Count=0&FacetFilters%5B0%5D.Display=India"
                        + "&FacetFilters%5B0%5D.IsApplied=true&FacetFilters%5B0%5D.FieldName="
                        + "&SearchResultsModuleName=Search+Results&SearchFiltersModuleName=Search+Filters"
                        + "&SortCriteria=0&SortDirection=0&SearchType=1");
            } catch (IOException e) {
                if (out.isEmpty()) {
                    throw e;
                }
                break; // keep the pages we already have
            }
            String html = Json.str(Json.asObject(Json.parse(body)), "results");
            List<RawPosting> items = parse(host, html);
            out.addAll(items);
            Matcher total = TOTAL.matcher(html);
            long count = total.find() ? Long.parseLong(total.group(1)) : 0;
            if (items.isEmpty() || (long) page * PAGE_SIZE >= count) {
                break;
            }
        }
        return out;
    }

    /** Reads one results fragment: each job is an {@code <li>} with a /job/… link, a heading and a location. */
    static List<RawPosting> parse(String host, String html) {
        List<RawPosting> out = new ArrayList<>();
        Matcher item = ITEM.matcher(html);
        while (item.find()) {
            String li = item.group(1);
            Matcher link = LINK.matcher(li);
            if (!link.find()) {
                continue;
            }
            Matcher heading = TITLE.matcher(li);
            String title = heading.find() ? text(heading.group(1)) : text(link.group(2));
            if (title.isEmpty()) {
                continue;
            }
            String path = link.group(1);
            String id = path.substring(path.lastIndexOf('/') + 1);
            Matcher location = LOCATION.matcher(li);
            Matcher date = DATE.matcher(li);
            Instant posted = date.find() ? usDate(text(date.group(1))) : null;
            out.add(new RawPosting(id, title,
                    List.of(location.find() ? text(location.group(1)) : "", "India"),
                    posted, "https://" + host + path, "", posted == null));
        }
        return out;
    }

    /** Tag-free, entity-decoded, whitespace-collapsed text. */
    static String text(String html) {
        String s = html.replaceAll("<[^>]+>", " ")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
        Matcher num = Pattern.compile("&#(x?)([0-9A-Fa-f]+);").matcher(s);
        StringBuilder sb = new StringBuilder();
        while (num.find()) {
            int code = Integer.parseInt(num.group(2), num.group(1).isEmpty() ? 10 : 16);
            num.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(code))));
        }
        num.appendTail(sb);
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static Instant usDate(String s) {
        try {
            return LocalDate.parse(s.trim(), US_DATE).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
