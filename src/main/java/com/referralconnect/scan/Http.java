package com.referralconnect.scan;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Semaphore;

/**
 * The one way the scanner talks to the network. A seam for tests (a lambda can stand in for the
 * internet) and the place where politeness is enforced for the live client.
 */
@FunctionalInterface
public interface Http {

    /** @param jsonBody null for GET, otherwise POSTed as application/json */
    String send(String method, String url, String jsonBody) throws IOException, InterruptedException;

    default String get(String url) throws IOException, InterruptedException {
        return send("GET", url, null);
    }

    default String post(String url, String jsonBody) throws IOException, InterruptedException {
        return send("POST", url, jsonBody);
    }

    /** Never more than this many requests in flight across the whole scan, whichever boards they are for. */
    int MAX_IN_FLIGHT = 16;

    /** Attempts per request when a server says it is busy (429 or 5xx). */
    int MAX_ATTEMPTS = 3;

    /** Longest one request may take from sending to having the whole body. */
    Duration MAX_EXCHANGE = Duration.ofSeconds(45);

    /**
     * The real client: shared connection pool, timeouts, a global cap on concurrent requests,
     * and retries with growing pauses (or the server's Retry-After, up to 10 s) when busy.
     */
    static Http live() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT);
        return (method, url, body) -> {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0 (compatible; JobReferralConnect/1.1; "
                            + "+https://github.com/chauhansachin13/job-referral-connect)")
                    .header("Accept", "application/json, text/html;q=0.8");
            if (body == null) {
                b.GET();
            } else {
                b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            }
            HttpRequest request = b.build();
            for (int attempt = 1; ; attempt++) {
                HttpResponse<String> response;
                inFlight.acquire();
                // The request timeout only covers waiting for the response headers; a server that then
                // trickles (or stalls) the body would hold this slot forever. Cap the whole exchange.
                java.util.concurrent.CompletableFuture<HttpResponse<String>> pending =
                        client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
                try {
                    response = pending.get(MAX_EXCHANGE.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (java.util.concurrent.TimeoutException e) {
                    pending.cancel(true);
                    throw new IOException("no complete answer within " + MAX_EXCHANGE.toSeconds() + " s");
                } catch (java.util.concurrent.ExecutionException e) {
                    throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
                } catch (InterruptedException e) {
                    pending.cancel(true);
                    throw e;
                } finally {
                    inFlight.release();
                }
                int status = response.statusCode();
                if (status == 200) {
                    return response.body();
                }
                boolean busy = status == 429 || status >= 500;
                if (busy && attempt < MAX_ATTEMPTS) {
                    long waitMs = 2000L * attempt;
                    String retryAfter = response.headers().firstValue("Retry-After").orElse("");
                    if (retryAfter.matches("\\d+")) {
                        waitMs = Math.min(10_000, Long.parseLong(retryAfter) * 1000);
                    }
                    Thread.sleep(waitMs);
                    continue;
                }
                throw new IOException(status == 404 ? "board not found (HTTP 404)" : "HTTP " + status);
            }
        };
    }
}
