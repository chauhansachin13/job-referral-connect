package com.referralconnect;

import com.referralconnect.TestRunner.Test;
import com.referralconnect.model.Ats;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.store.DataStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static com.referralconnect.TestRunner.check;
import static com.referralconnect.TestRunner.equal;
import static com.referralconnect.TestRunner.fails;

class DataStoreTest {

    @Test
    void persistsAcrossRestarts() {
        Path dir = Fixtures.tempDir();
        try {
            DataStore first = new DataStore(dir);
            first.update(s -> {
                s.customBoards.add(new CompanyBoard("Acme", Ats.LEVER, "acme"));
                s.jobs.add(Fixtures.job("lever:acme", "Acme", "1", "SDE"));
                s.lastScanAt = Instant.parse("2026-10-01T00:00:00Z");
                s.lastScanFailures.put("Gone", "HTTP 404");
            });
            DataStore second = new DataStore(dir);
            equal(1, second.read(s -> s.customBoards.size()));
            equal("SDE", second.read(s -> s.jobs.get(0).title()));
            equal(Instant.parse("2026-10-01T00:00:00Z"), second.read(s -> s.lastScanAt));
            equal("HTTP 404", second.read(s -> s.lastScanFailures.get("Gone")));
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void secondWindowSeesChangesAndDoesNotOverwriteThem() {
        Path dir = Fixtures.tempDir();
        try {
            DataStore seekerWindow = new DataStore(dir);
            DataStore referrerWindow = new DataStore(dir);
            long before = referrerWindow.poll();
            seekerWindow.update(s -> s.customBoards.add(new CompanyBoard("A", Ats.LEVER, "a")));
            check(referrerWindow.poll() != before, "change detected by the other window");
            // Each write re-reads the file first, so neither window loses the other's change.
            referrerWindow.update(s -> s.customBoards.add(new CompanyBoard("B", Ats.LEVER, "b")));
            seekerWindow.update(s -> s.customBoards.add(new CompanyBoard("C", Ats.LEVER, "c")));
            equal(3, new DataStore(dir).read(s -> s.customBoards.size()));
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void failedChangeIsRolledBack() {
        Path dir = Fixtures.tempDir();
        try {
            DataStore store = new DataStore(dir);
            store.update(s -> s.customBoards.add(new CompanyBoard("Keep", Ats.LEVER, "keep")));
            fails(IllegalStateException.class, () -> store.update(s -> {
                s.customBoards.add(new CompanyBoard("Half", Ats.LEVER, "half"));
                throw new IllegalStateException("rule broken");
            }));
            equal(1, store.read(s -> s.customBoards.size()));
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void aDamagedCachedOpeningIsSkippedNotFatal() throws Exception {
        Path dir = Fixtures.tempDir();
        try {
            // One good-looking but broken opening among the cached ones; accounts are fine.
            Files.writeString(dir.resolve("data.json"), "{\"jobs\": [{\"id\": \"a\", \"category\": \"NOPE\"}, 5, null],"
                    + " \"lastScan\": {\"at\": \"not a date\"}}");
            DataStore store = new DataStore(dir);
            equal(0, store.read(s -> s.jobs.size()), "damaged openings dropped");
            check(store.read(s -> s.lastScanAt) == null, "so a fresh scan runs");
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void refusesToSilentlyReplaceADamagedFile() throws Exception {
        Path dir = Fixtures.tempDir();
        try {
            Files.writeString(dir.resolve("data.json"), "{not json");
            IllegalStateException e = fails(IllegalStateException.class, () -> new DataStore(dir));
            check(e.getMessage().contains("damaged"), e.getMessage());
            equal("{not json", Files.readString(dir.resolve("data.json")));
        } finally {
            Fixtures.delete(dir);
        }
    }
}
