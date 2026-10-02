package com.referralconnect.service;

import com.referralconnect.model.CompanyBoard;
import com.referralconnect.model.JobPosting;
import com.referralconnect.scan.Http;
import com.referralconnect.scan.JobScanner;
import com.referralconnect.scan.source.BoardSource;
import com.referralconnect.store.DataStore;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Runs scans over the company directory and keeps the latest results for everyone to see. */
public final class JobService {

    /** Postings older than this are never kept; the UI offers narrower windows on top. */
    public static final Duration SCAN_WINDOW = Duration.ofDays(30);

    /** A scan older than this is refreshed automatically when someone opens the job list. */
    public static final Duration STALE_AFTER = Duration.ofMinutes(30);

    private final DataStore store;
    private final CompanyDirectory directory;
    private final JobScanner scanner;
    private final Http http;

    public JobService(DataStore store, CompanyDirectory directory, Http http) {
        this.store = store;
        this.directory = directory;
        this.http = http;
        this.scanner = new JobScanner(http);
    }

    public JobScanner.ScanReport scanNow(Consumer<String> progress) {
        List<CompanyBoard> boards = directory.all();
        progress.accept("Scanning " + boards.size() + " companies…");
        // Publish each company's openings as soon as it is read: open windows refresh from the
        // data file, so people see jobs within seconds while slow career sites are still loading.
        Instant scanStart = Instant.now();
        Instant windowStart = scanStart.minus(SCAN_WINDOW);
        JobScanner.ScanReport report = scanner.scan(boards, SCAN_WINDOW, progress, (board, found) ->
                store.update(s -> {
                    List<JobPosting> merged = keepFirstSeen(found, s.jobs, windowStart);
                    s.jobs.removeIf(j -> j.companyKey().equals(board.key()));
                    s.jobs.addAll(merged);
                    s.jobs.sort(JobScanner.NEWEST_FIRST);
                }));
        Instant cutoff = report.scannedAt().minus(SCAN_WINDOW);
        store.update(s -> {
            // A company that could not be read this time keeps its previous, still-recent openings.
            List<JobPosting> kept = s.jobs.stream()
                    .filter(j -> report.failedBoardKeys().contains(j.companyKey()))
                    .filter(j -> !j.postedAt().isBefore(cutoff))
                    .toList();
            List<JobPosting> fresh = keepFirstSeen(report.jobs(), s.jobs, cutoff);
            s.jobs.clear();
            s.jobs.addAll(fresh);
            s.jobs.addAll(kept);
            s.jobs.sort(JobScanner.NEWEST_FIRST);
            s.lastScanAt = report.scannedAt();
            s.lastScanBoards = report.boardsScanned();
            s.lastScanFailures.clear();
            s.lastScanFailures.putAll(report.failures());
        });
        return report;
    }

    /**
     * For postings from sites that publish no dates, keeps the time an earlier scan first saw them
     * (so a job does not look new on every scan) and drops those first seen before {@code cutoff}.
     * Dated postings pass through unchanged.
     */
    public static List<JobPosting> keepFirstSeen(List<JobPosting> fresh, Collection<JobPosting> previous, Instant cutoff) {
        Map<String, Instant> firstSeen = new HashMap<>();
        for (JobPosting j : previous) {
            if (!j.dateKnown()) {
                firstSeen.merge(j.id(), j.postedAt(), (a, b) -> a.isBefore(b) ? a : b);
            }
        }
        List<JobPosting> out = new ArrayList<>();
        for (JobPosting j : fresh) {
            if (j.dateKnown()) {
                out.add(j);
                continue;
            }
            Instant seen = firstSeen.get(j.id());
            JobPosting kept = seen == null || seen.isAfter(j.postedAt()) ? j : j.withPostedAt(seen);
            if (!kept.postedAt().isBefore(cutoff)) {
                out.add(kept);
            }
        }
        return out;
    }

    public List<JobPosting> jobs() {
        return store.read(s -> List.copyOf(s.jobs));
    }

    public Instant lastScanAt() {
        return store.read(s -> s.lastScanAt);
    }

    public int lastScanBoards() {
        return store.read(s -> s.lastScanBoards);
    }

    public Map<String, String> lastScanFailures() {
        return store.read(s -> new LinkedHashMap<>(s.lastScanFailures));
    }

    public boolean isStale(Instant now) {
        Instant last = lastScanAt();
        return last == null || last.plus(STALE_AFTER).isBefore(now);
    }

    /**
     * Checks a board before it is added: fetches it once and returns how many postings it lists.
     * Throws a {@link ServiceException} with a readable reason when the board cannot be read.
     */
    public int verifyBoard(CompanyBoard board) {
        try {
            Instant now = Instant.now();
            BoardSource.Context ctx = new BoardSource.Context(http, now, now.minus(SCAN_WINDOW),
                    now.plus(JobScanner.BOARD_TIME_BUDGET));
            return BoardSource.of(board.ats()).fetch(board, ctx).size();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("Check was interrupted.");
        } catch (Exception e) {
            throw new ServiceException("Could not read the " + board.ats().label() + " board \"" + board.token()
                    + "\": " + e.getMessage());
        }
    }
}
