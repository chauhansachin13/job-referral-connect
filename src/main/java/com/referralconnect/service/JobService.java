package com.referralconnect.service;

import com.referralconnect.model.CompanyBoard;
import com.referralconnect.model.JobPosting;
import com.referralconnect.scan.Http;
import com.referralconnect.scan.JobScanner;
import com.referralconnect.scan.source.BoardSource;
import com.referralconnect.store.DataStore;

import java.time.Duration;
import java.time.Instant;
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
        JobScanner.ScanReport report = scanner.scan(boards, SCAN_WINDOW, progress, (board, found) ->
                store.update(s -> {
                    s.jobs.removeIf(j -> j.companyKey().equals(board.key()));
                    s.jobs.addAll(found);
                    s.jobs.sort(JobScanner.NEWEST_FIRST);
                }));
        Instant cutoff = report.scannedAt().minus(SCAN_WINDOW);
        store.update(s -> {
            // A company that could not be read this time keeps its previous, still-recent openings.
            List<JobPosting> kept = s.jobs.stream()
                    .filter(j -> report.failedBoardKeys().contains(j.companyKey()))
                    .filter(j -> !j.postedAt().isBefore(cutoff))
                    .toList();
            s.jobs.clear();
            s.jobs.addAll(report.jobs());
            s.jobs.addAll(kept);
            s.jobs.sort(JobScanner.NEWEST_FIRST);
            s.lastScanAt = report.scannedAt();
            s.lastScanBoards = report.boardsScanned();
            s.lastScanFailures.clear();
            s.lastScanFailures.putAll(report.failures());
        });
        return report;
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
