package com.referralconnect.store;

import com.referralconnect.json.Json;
import com.referralconnect.model.Account;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.model.JobAlert;
import com.referralconnect.model.JobPosting;
import com.referralconnect.model.ReferralRequest;
import com.referralconnect.model.TrackedJob;
import com.referralconnect.model.UserPrefs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * All application state in one human-readable JSON file.
 *
 * <p>A seeker and a referrer can run the app at the same time against the same data folder
 * (two windows on one machine, or a shared folder): every write takes an OS file lock, re-reads
 * the file, applies the change and replaces the file atomically, so neither side overwrites the
 * other. Reads pick up the other side's changes whenever the file has changed on disk.
 */
public final class DataStore {

    /** The whole data model. Only mutate it inside {@link #write}. */
    public static final class State {
        public final List<Account> accounts = new ArrayList<>();
        public final List<ReferralRequest> requests = new ArrayList<>();
        public final List<CompanyBoard> customBoards = new ArrayList<>();
        public final List<JobPosting> jobs = new ArrayList<>();
        public final Map<String, String> lastScanFailures = new LinkedHashMap<>();
        public Instant lastScanAt;
        public int lastScanBoards;
        /** jobId → when a scan first found it; drives "new" badges and alert counts. */
        public final Map<String, Instant> firstSeen = new LinkedHashMap<>();
        public final List<TrackedJob> tracked = new ArrayList<>();
        public final List<JobAlert> alerts = new ArrayList<>();
        /** accountId → that account's settings. */
        public final Map<String, UserPrefs> prefs = new LinkedHashMap<>();
        /** App-wide display settings, shared by everyone using this data folder. */
        public boolean darkMode;
        public boolean autoScan = true;
    }

    /** Serialises access from different DataStore instances on the same file inside one JVM. */
    private static final Map<Path, Object> JVM_LOCKS = new ConcurrentHashMap<>();

    private final Path file;
    private final Path lockFile;
    private final Object monitor;
    private State state = new State();
    private String loadedStamp = "";
    private long version;

    public DataStore(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create data folder " + directory, e);
        }
        this.file = directory.resolve("data.json").toAbsolutePath().normalize();
        this.lockFile = directory.resolve("data.lock").toAbsolutePath().normalize();
        this.monitor = JVM_LOCKS.computeIfAbsent(file, k -> new Object());
        synchronized (monitor) {
            reloadIfChanged();
        }
    }

    /** {@code -Dreferralconnect.home}, else {@code REFERRALCONNECT_HOME}, else {@code ~/.job-referral-connect}. */
    public static Path defaultHome() {
        String prop = System.getProperty("referralconnect.home");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop);
        }
        String env = System.getenv("REFERRALCONNECT_HOME");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        return Path.of(System.getProperty("user.home"), ".job-referral-connect");
    }

    public Path file() {
        return file;
    }

    public <T> T read(Function<State, T> query) {
        synchronized (monitor) {
            reloadIfChanged();
            return query.apply(state);
        }
    }

    @SuppressWarnings("try") // the lock is held by the try block itself and never referenced
    public <T> T write(Function<State, T> change) {
        synchronized (monitor) {
            try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                // Always start from what is on disk: a timestamp check alone can miss a change
                // on file systems with coarse modification times, and that would lose an update.
                loadedStamp = "";
                reloadIfChanged();
                T result;
                try {
                    result = change.apply(state);
                } catch (RuntimeException e) {
                    // Throw away any half-applied change by re-reading the last saved state.
                    loadedStamp = "";
                    reloadIfChanged();
                    throw e;
                }
                save();
                return result;
            } catch (IOException e) {
                throw new UncheckedIOException("Could not save " + file, e);
            }
        }
    }

    public void update(Consumer<State> change) {
        write(s -> {
            change.accept(s);
            return null;
        });
    }

    /**
     * Picks up changes another window made. Returns the state version, which increases every
     * time the in-memory state changes, so callers can cheaply ask "did anything change?".
     */
    public long poll() {
        synchronized (monitor) {
            reloadIfChanged();
            return version;
        }
    }

    // ---------------------------------------------------------------- persistence

    private String stamp() {
        try {
            if (!Files.exists(file)) {
                return "missing";
            }
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            return attrs.lastModifiedTime().to(TimeUnit.NANOSECONDS) + "/" + attrs.size();
        } catch (IOException e) {
            return "unreadable";
        }
    }

    private void reloadIfChanged() {
        String current = stamp();
        if (current.equals(loadedStamp)) {
            return;
        }
        State fresh = new State();
        if (Files.exists(file)) {
            try {
                fill(fresh, Json.asObject(Json.parse(Files.readString(file, StandardCharsets.UTF_8))));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + file, e);
            } catch (RuntimeException e) {
                throw new IllegalStateException("Data file " + file + " is damaged (" + e.getMessage()
                        + "). Fix or move it away, then restart.", e);
            }
        }
        state = fresh;
        loadedStamp = current;
        version++;
    }

    private void save() throws IOException {
        Path tmp = file.resolveSibling("data.json.tmp");
        Files.writeString(tmp, Json.write(toJson(state)), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        loadedStamp = stamp();
        version++;
    }

    private static Map<String, Object> toJson(State s) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", 1);
        root.put("accounts", s.accounts.stream().map(Account::toJson).toList());
        root.put("requests", s.requests.stream().map(ReferralRequest::toJson).toList());
        root.put("customBoards", s.customBoards.stream().map(CompanyBoard::toJson).toList());
        Map<String, Object> firstSeen = new LinkedHashMap<>();
        s.firstSeen.forEach((id, at) -> firstSeen.put(id, at.toString()));
        root.put("firstSeen", firstSeen);
        root.put("tracked", s.tracked.stream().map(TrackedJob::toJson).toList());
        root.put("alerts", s.alerts.stream().map(JobAlert::toJson).toList());
        Map<String, Object> prefs = new LinkedHashMap<>();
        s.prefs.forEach((id, p) -> prefs.put(id, p.toJson()));
        root.put("prefs", prefs);
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("darkMode", s.darkMode);
        settings.put("autoScan", s.autoScan);
        root.put("settings", settings);
        Map<String, Object> scan = new LinkedHashMap<>();
        scan.put("at", s.lastScanAt == null ? null : s.lastScanAt.toString());
        scan.put("boards", s.lastScanBoards);
        scan.put("failures", s.lastScanFailures);
        root.put("lastScan", scan);
        root.put("jobs", s.jobs.stream().map(JobPosting::toJson).toList());
        return root;
    }

    private static void fill(State s, Map<String, Object> root) {
        for (Object o : Json.arr(root, "accounts")) {
            s.accounts.add(Account.fromJson(Json.asObject(o)));
        }
        for (Object o : Json.arr(root, "requests")) {
            s.requests.add(ReferralRequest.fromJson(Json.asObject(o)));
        }
        for (Object o : Json.arr(root, "customBoards")) {
            s.customBoards.add(CompanyBoard.fromJson(Json.asObject(o)));
        }
        Json.obj(root, "firstSeen").forEach((id, at) -> s.firstSeen.put(id, Instant.parse(String.valueOf(at))));
        for (Object o : Json.arr(root, "tracked")) {
            s.tracked.add(TrackedJob.fromJson(Json.asObject(o)));
        }
        for (Object o : Json.arr(root, "alerts")) {
            s.alerts.add(JobAlert.fromJson(Json.asObject(o)));
        }
        Json.obj(root, "prefs").forEach((id, p) -> s.prefs.put(id, UserPrefs.fromJson(Json.asObject(p))));
        Map<String, Object> settings = Json.obj(root, "settings");
        s.darkMode = Json.bool(settings, "darkMode");
        s.autoScan = !settings.containsKey("autoScan") || Json.bool(settings, "autoScan");
        for (Object o : Json.arr(root, "jobs")) {
            s.jobs.add(JobPosting.fromJson(Json.asObject(o)));
        }
        Map<String, Object> scan = Json.obj(root, "lastScan");
        Object at = scan.get("at");
        s.lastScanAt = at == null ? null : Instant.parse(at.toString());
        s.lastScanBoards = (int) Json.num(scan, "boards", 0);
        Json.obj(scan, "failures").forEach((k, v) -> s.lastScanFailures.put(k, String.valueOf(v)));
    }
}
