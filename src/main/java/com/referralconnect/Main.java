package com.referralconnect;

import com.referralconnect.model.JobPosting;
import com.referralconnect.model.Requirements;
import com.referralconnect.scan.JobScanner;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.DemoData;
import com.referralconnect.store.DataStore;
import com.referralconnect.ui.AppFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Entry point.
 *
 * <pre>
 *   java -cp out com.referralconnect.Main            open the app
 *   java -cp out com.referralconnect.Main --demo     add demo referrers + a demo seeker, then open
 *   java -cp out com.referralconnect.Main --scan     scan job boards and print results (no window)
 *   ... --home /some/folder                         keep data somewhere other than ~/.job-referral-connect
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Path home = DataStore.defaultHome();
        boolean demo = false;
        boolean scanOnly = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--demo" -> demo = true;
                case "--scan" -> scanOnly = true;
                case "--home" -> {
                    if (i + 1 >= args.length) {
                        usage("--home needs a folder");
                    }
                    home = Path.of(args[++i]);
                }
                case "-h", "--help" -> usage(null);
                default -> usage("Unknown option " + args[i]);
            }
        }

        AppServices app;
        try {
            app = AppServices.live(home);
        } catch (IllegalStateException | UncheckedIOException e) {
            // A damaged or unreadable data file: say so plainly instead of crashing with a stack trace.
            // The file itself is left untouched so nothing is lost.
            String message = "Can't open the app's data. " + e.getMessage();
            System.err.println(message);
            if (!scanOnly && !GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null, message, "Job Referral Connect", JOptionPane.ERROR_MESSAGE);
            }
            System.exit(1);
            return;
        }
        if (demo) {
            List<String> created = DemoData.seed(app);
            System.out.println(created.isEmpty()
                    ? "Demo accounts already exist."
                    : "Created demo accounts (password " + DemoData.PASSWORD + "): " + String.join(", ", created));
        }
        if (scanOnly) {
            runConsoleScan(app);
            return;
        }
        SwingUtilities.invokeLater(() -> AppFrame.open(app));
    }

    private static void runConsoleScan(AppServices app) {
        JobScanner.ScanReport report = app.jobs.scanNow(line -> {
            if (line.startsWith("Scanning")) {
                System.out.println(line);
            }
        });
        Map<String, Integer> referrers = app.referrals.referrerCountsByCompany();
        Instant now = Instant.now();
        System.out.printf("%n%-6s %-18s %-60s %-20s %-10s %-10s %-22s %s%n",
                "AGE", "COMPANY", "ROLE", "CATEGORY", "TYPE", "MIN EXP", "CITY", "REFERRERS");
        for (JobPosting j : report.jobs()) {
            System.out.printf("%-6s %-18s %-60s %-20s %-10s %-10s %-22s %s%n",
                    j.ageDays(now) + "d", cut(j.company(), 18), cut(j.title(), 60), j.category().label(),
                    j.typeLabel(), j.experienceLabel(), cut(j.city(), 22), referrers.getOrDefault(j.companyKey(), 0));
        }
        long interns = report.jobs().stream().filter(JobPosting::internship).count();
        long stated = report.jobs().stream().filter(j -> j.requirements().basis() == Requirements.Basis.STATED).count();
        System.out.printf("%n%d openings in India (%d internships) from %d boards, %d postings checked. Saved to %s%n",
                report.jobs().size(), interns, report.boardsScanned(), report.postingsSeen(), app.store.file());
        long unread = report.jobs().stream()
                .filter(j -> !j.requirements().known() && !j.requirements().detailsRead()).count();
        System.out.printf("Minimum experience: stated for %d openings, only a preferred figure for %d, not read yet "
                        + "for %d (the next scan reads them), not stated for the rest (nothing is guessed).%n", stated,
                report.jobs().stream().filter(j -> j.requirements().preferredOnly()).count(), unread);
        report.failures().forEach((board, why) -> System.out.println("  ! " + board + ": " + why));
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static void usage(String error) {
        if (error != null) {
            System.err.println(error);
        }
        (error == null ? System.out : System.err).println("""
                Usage: java -cp out com.referralconnect.Main [options]
                   or: java -jar job-referral-connect.jar [options]

                  --demo           add demo referrers and a demo seeker (password demo1234), then open the app
                  --scan           scan every company's careers site and print the openings, without a window
                  --home <folder>  keep data in <folder> instead of ~/.job-referral-connect
                  -h, --help       show this help""");
        System.exit(error == null ? 0 : 2);
    }
}
