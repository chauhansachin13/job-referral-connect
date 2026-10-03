package com.referralconnect.ui;

import com.referralconnect.scan.JobScanner;
import com.referralconnect.service.AppServices;

import javax.swing.SwingWorker;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs job scans in the background, one at a time, and tells every interested screen (the top
 * bar, the openings list) how it is going. Started by "Scan now", by opening the app when the
 * results are stale, and by the auto-scan timer.
 */
final class ScanController {

    interface Listener {
        /** @param line the latest progress line, or the summary once finished */
        void scanChanged(boolean running, String line);
    }

    private final AppServices app;
    private final List<Listener> listeners = new ArrayList<>();
    private final List<Runnable> onFinished = new ArrayList<>();
    private boolean running;
    private String line = "";

    ScanController(AppServices app) {
        this.app = app;
    }

    boolean running() {
        return running;
    }

    String line() {
        return line;
    }

    void addListener(Listener l) {
        listeners.add(l);
        l.scanChanged(running, line);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Runs after every finished scan (successful or not), on the event thread. */
    void onFinished(Runnable r) {
        onFinished.add(r);
    }

    void clearCallbacks() {
        listeners.clear();
        onFinished.clear();
    }

    /** Starts a scan unless one is running; errors are shown relative to {@code parent}. */
    void start(Component parent) {
        if (running) {
            return;
        }
        running = true;
        update("Starting scan…");
        new SwingWorker<JobScanner.ScanReport, String>() {
            @Override
            protected JobScanner.ScanReport doInBackground() {
                return app.jobs.scanNow(this::publish);
            }

            @Override
            protected void process(List<String> lines) {
                update(lines.get(lines.size() - 1));
            }

            @Override
            protected void done() {
                running = false;
                String summary;
                try {
                    JobScanner.ScanReport report = get();
                    summary = "Found " + report.jobs().size() + " openings at " + report.boardsScanned()
                            + " companies";
                } catch (Exception e) {
                    summary = "Scan failed";
                    if (parent != null && parent.isShowing()) {
                        Ui.error(parent, e.getCause() != null ? e.getCause() : e);
                    }
                }
                update(summary);
                for (Runnable r : List.copyOf(onFinished)) {
                    r.run();
                }
            }
        }.execute();
    }

    private void update(String text) {
        line = text;
        for (Listener l : List.copyOf(listeners)) {
            l.scanChanged(running, line);
        }
    }
}
