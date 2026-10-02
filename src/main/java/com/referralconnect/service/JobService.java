package com.referralconnect.service;

import com.referralconnect.model.CompanyBoard;
import com.referralconnect.model.JobPosting;
import com.referralconnect.scan.AtsParsers;
import com.referralconnect.scan.JobScanner;
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
    private final JobScanner.Fetcher fetcher;

    public JobService(DataStore store, CompanyDirectory directory, JobScanner.Fetcher fetcher) {
        this.store = store;
        this.directory = directory;
        this.fetcher = fetcher;
        this.scanner = new JobScanner(fetcher);
    }

    public JobScanner.ScanReport scanNow(Consumer<String> progress) {
        List<CompanyBoard> boards = directory.all();
        progress.accept("Scanning " + boards.size() + " company job boards…");
        JobScanner.ScanReport report = scanner.scan(boards, SCAN_WINDOW, progress);
        store.update(s -> {
            s.jobs.clear();
            s.jobs.addAll(report.jobs());
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
            return AtsParsers.parse(board.ats(), fetcher.fetch(board.apiUrl())).size();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("Check was interrupted.");
        } catch (Exception e) {
            throw new ServiceException("Could not read the " + board.ats().label() + " board \"" + board.token()
                    + "\": " + e.getMessage());
        }
    }
}
