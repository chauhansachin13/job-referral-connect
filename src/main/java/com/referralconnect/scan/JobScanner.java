package com.referralconnect.scan;

import com.referralconnect.model.CompanyBoard;
import com.referralconnect.model.JobCategory;
import com.referralconnect.model.JobPosting;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Fetches every registered company board in parallel and keeps only recent, India-located
 * postings in the tracked role families.
 */
public final class JobScanner {

    /** Seam for tests: returns the response body for a URL or throws. */
    @FunctionalInterface
    public interface Fetcher {
        String fetch(String url) throws IOException, InterruptedException;
    }

    /** Be polite to the ATS hosts: never more than this many requests in flight. */
    private static final int MAX_CONCURRENT_REQUESTS = 12;

    private final Fetcher fetcher;

    public JobScanner() {
        this(httpFetcher());
    }

    public JobScanner(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    public record ScanReport(
            List<JobPosting> jobs,
            Map<String, String> failures,
            int boardsScanned,
            int postingsSeen,
            Instant scannedAt) {
    }

    /**
     * @param window   how far back a posting may have been published, e.g. 30 days
     * @param progress receives human-readable progress lines; may be called from worker threads
     */
    public ScanReport scan(Collection<CompanyBoard> boards, Duration window, Consumer<String> progress) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);
        Map<String, String> failures = new ConcurrentHashMap<>();
        AtomicInteger done = new AtomicInteger();
        AtomicInteger seen = new AtomicInteger();
        Semaphore permits = new Semaphore(MAX_CONCURRENT_REQUESTS);
        int total = boards.size();

        List<JobPosting> found = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<JobPosting>>> futures = new ArrayList<>();
            for (CompanyBoard board : boards) {
                futures.add(pool.submit(() -> {
                    permits.acquire();
                    try {
                        List<RawPosting> raw = AtsParsers.parse(board.ats(), fetcher.fetch(board.apiUrl()));
                        seen.addAndGet(raw.size());
                        return extract(board, raw, cutoff);
                    } catch (Exception e) {
                        failures.put(board.name(), describe(e));
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
                .sorted(Comparator.comparing(JobPosting::postedAt).reversed()
                        .thenComparing(JobPosting::company)
                        .thenComparing(JobPosting::title))
                .collect(Collectors.toCollection(ArrayList::new));
        return new ScanReport(jobs, new LinkedHashMap<>(failures), total, seen.get(), now);
    }

    /** Applies the role, India and recency filters to one board's raw postings. */
    public static List<JobPosting> extract(CompanyBoard board, List<RawPosting> raw, Instant cutoff) {
        List<JobPosting> out = new ArrayList<>();
        for (RawPosting r : raw) {
            if (r.postedAt() == null || r.postedAt().isBefore(cutoff)) {
                continue;
            }
            Optional<JobCategory> category = RoleClassifier.classify(r.title());
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
                    r.postedAt(),
                    r.url(),
                    board.ats()));
        }
        return out;
    }

    private static String describe(Exception e) {
        String msg = e.getMessage();
        return e.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }

    public static Fetcher httpFetcher() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return url -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "JobReferralConnect/1.0 (+https://github.com/chauhansachin13/job-referral-connect)")
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw new IOException("board not found (HTTP 404)");
            }
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            return response.body();
        };
    }
}
