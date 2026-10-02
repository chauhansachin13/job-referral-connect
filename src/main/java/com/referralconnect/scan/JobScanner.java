package com.referralconnect.scan;

import com.referralconnect.model.Ats;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.model.JobCategory;
import com.referralconnect.model.JobPosting;
import com.referralconnect.scan.source.BoardSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Fetches every registered company board in parallel and keeps only recent, India-located
 * postings in the tracked role families.
 */
public final class JobScanner {

    /** Boards read at the same time; {@link Http#live()} separately caps requests in flight. */
    private static final int MAX_BOARDS_AT_ONCE = 12;

    /** After this long a board stops paging and keeps what it has, so the scan always finishes. */
    public static final Duration BOARD_TIME_BUDGET = Duration.ofSeconds(75);

    private final Http http;

    public JobScanner(Http http) {
        this.http = http;
    }

    /**
     * Jobs with real posting dates first, newest first; then jobs from sites that publish no dates,
     * by when they were first seen (that time says little about age on a company's first scan).
     * Ties are broken by company then title so the order is stable.
     */
    public static final Comparator<JobPosting> NEWEST_FIRST = Comparator.comparing(JobPosting::dateKnown).reversed()
            .thenComparing(Comparator.comparing(JobPosting::postedAt).reversed())
            .thenComparing(JobPosting::company)
            .thenComparing(JobPosting::title);

    /**
     * @param failures        board name → why it could not be read
     * @param failedBoardKeys {@link CompanyBoard#key()} of each board in {@code failures}
     */
    public record ScanReport(
            List<JobPosting> jobs,
            Map<String, String> failures,
            Set<String> failedBoardKeys,
            int boardsScanned,
            int postingsSeen,
            Instant scannedAt) {
    }

    public ScanReport scan(Collection<CompanyBoard> boards, Duration window, Consumer<String> progress) {
        return scan(boards, window, progress, (board, jobs) -> { });
    }

    /**
     * @param window      how far back a posting may have been published, e.g. 30 days
     * @param progress    receives human-readable progress lines; may be called from worker threads
     * @param onBoardDone receives each board's openings as soon as that board has been read, so
     *                    results can be shown while slower boards are still loading
     */
    public ScanReport scan(Collection<CompanyBoard> boards, Duration window, Consumer<String> progress,
                           BiConsumer<CompanyBoard, List<JobPosting>> onBoardDone) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);
        Map<String, String> failures = new ConcurrentHashMap<>();
        Set<String> failedKeys = ConcurrentHashMap.newKeySet();
        AtomicInteger done = new AtomicInteger();
        AtomicInteger seen = new AtomicInteger();
        Semaphore permits = new Semaphore(MAX_BOARDS_AT_ONCE);
        int total = boards.size();

        List<JobPosting> found = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<JobPosting>>> futures = new ArrayList<>();
            for (CompanyBoard board : boards) {
                futures.add(pool.submit(() -> {
                    permits.acquire();
                    try {
                        // Each board's time budget starts when it actually begins, not when it was queued.
                        BoardSource.Context context = new BoardSource.Context(http, now, cutoff,
                                Instant.now().plus(BOARD_TIME_BUDGET));
                        List<RawPosting> raw = BoardSource.of(board.ats()).fetch(board, context);
                        seen.addAndGet(raw.size());
                        List<JobPosting> jobs = extract(board, raw, cutoff, now);
                        onBoardDone.accept(board, jobs);
                        return jobs;
                    } catch (Exception e) {
                        failures.put(board.name(), describe(e));
                        failedKeys.add(board.key());
                        return List.<JobPosting>of();
                    } finally {
                        permits.release();
                        progress.accept("Scanned " + done.incrementAndGet() + "/" + total + " boards ("
                                + board.name() + ")");
                    }
                }));
            }
            for (Future<List<JobPosting>> f : futures) {
                try {
                    found.addAll(f.get());
                } catch (Exception e) {
                    // Each task already records its own failure; nothing else to do here.
                }
            }
        }

        // The same job can appear twice when one board lists it under several ids; keep one.
        Map<String, JobPosting> unique = new LinkedHashMap<>();
        for (JobPosting j : found) {
            unique.putIfAbsent(j.id(), j);
        }
        List<JobPosting> jobs = unique.values().stream()
                .sorted(NEWEST_FIRST)
                .collect(Collectors.toCollection(ArrayList::new));
        return new ScanReport(jobs, new LinkedHashMap<>(failures), Set.copyOf(failedKeys), total, seen.get(), now);
    }

    public static List<JobPosting> extract(CompanyBoard board, List<RawPosting> raw, Instant cutoff) {
        return extract(board, raw, cutoff, Instant.now());
    }

    /**
     * Applies the role, India and recency filters to one board's raw postings.
     *
     * @param now used as the "first seen" time for postings from sites that publish no dates;
     *            {@link com.referralconnect.service.JobService} later swaps in the time the job was
     *            really first seen if an earlier scan already had it
     */
    public static List<JobPosting> extract(CompanyBoard board, List<RawPosting> raw, Instant cutoff, Instant now) {
        List<JobPosting> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        // The startups and tech companies on these job boards hire engineers to write software, so a
        // plain "Staff Engineer" there is a software role; elsewhere the title must say so.
        boolean softwareCompany = board.ats() == Ats.GREENHOUSE || board.ats() == Ats.LEVER || board.ats() == Ats.ASHBY;
        for (RawPosting r : raw) {
            boolean undated = r.postedAt() == null && r.undated();
            Instant posted = undated ? now : r.postedAt();
            // Paged results can repeat an entry when the listing shifts between page requests.
            if (posted == null || posted.isBefore(cutoff) || !ids.add(r.atsId())) {
                continue;
            }
            Optional<JobCategory> category = RoleClassifier.classify(r.title(), softwareCompany);
            if (category.isEmpty()) {
                continue;
            }
            List<String> indian = r.locations().stream()
                    .filter(IndiaLocations::isIndia)
                    .distinct()
                    .toList();
            if (indian.isEmpty()) {
                continue;
            }
            String location = indian.stream()
                    .filter(l -> !l.equalsIgnoreCase("India"))
                    .collect(Collectors.joining(" / "));
            if (location.isEmpty()) {
                location = "India";
            }
            out.add(new JobPosting(
                    board.key() + ":" + r.atsId(),
                    board.key(),
                    board.name(),
                    r.title(),
                    location,
                    IndiaLocations.cities(String.join(" / ", indian)),
                    category.get(),
                    RoleClassifier.isInternship(r.title(), r.employmentHint()),
                    posted,
                    r.url(),
                    board.ats(),
                    !undated));
        }
        return out;
    }

    private static String describe(Exception e) {
        String msg = e.getMessage();
        return e.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }

}
