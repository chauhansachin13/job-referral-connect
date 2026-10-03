package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.model.CandidateProfile;
import com.referralconnect.model.JobCategory;
import com.referralconnect.model.JobPosting;
import com.referralconnect.model.ReferralRequest;
import com.referralconnect.model.RequestStatus;
import com.referralconnect.model.Requirements;
import com.referralconnect.model.TrackedJob.Stage;
import com.referralconnect.model.UserPrefs;
import com.referralconnect.scan.IndiaLocations;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.CsvExport;
import com.referralconnect.service.JobMatcher;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Recently posted openings from the last scan, with their minimum experience. Seekers see how
 * well each fits them and whether a referrer is available, and can save, hide or request a
 * referral; referrers see the openings at their own company.
 */
final class JobsPanel extends JPanel implements AppFrame.Live {

    enum Mode { SEEKER, COMPANY }

    enum Sort {
        NEWEST("Newest first"), MATCH("Best match"), EXPERIENCE("Least experience"), COMPANY("Company A–Z");

        final String label;

        Sort(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Filter choices to apply at once, e.g. from a dashboard card or a job alert.
     *
     * @param maxYears   -1 for any; otherwise the most experience a job may ask for
     * @param windowDays how far back to show
     */
    record Preset(String query, String company, JobCategory role, Boolean internship, String city, int maxYears,
                  int windowDays, boolean newOnly, boolean withReferrer, Sort sort) {
        static Preset all() {
            return new Preset("", null, null, null, "", -1, 30, false, false, Sort.NEWEST);
        }
    }

    private static final String ALL_ROLES = "All roles";
    private static final String ALL_TYPES = "Jobs + internships";
    private static final String ALL_CITIES = "All of India";
    private static final String ALL_COMPANIES = "All companies";
    private static final String[] WINDOWS = {"Last 24 hours", "Last 3 days", "Last 7 days", "Last 14 days", "Last 30 days"};
    private static final int[] WINDOW_DAYS = {1, 3, 7, 14, 30};
    static final String[] EXPERIENCE = {"Any experience", "Freshers (0 years)", "Up to 1 year", "Up to 2 years",
            "Up to 3 years", "Up to 5 years", "Fits my experience"};
    private static final int[] EXPERIENCE_MAX = {-1, 0, 1, 2, 3, 5, -2};

    private final AppServices app;
    private final Account account;
    private final Mode mode;
    private final Runnable onChanged;
    private java.util.function.Consumer<String> openRequest = id -> { };

    private final Form.HintField search = new Form.HintField("", "Search role, company or skill").withIcon(Icons.Glyph.SEARCH);
    private final JComboBox<String> role = new JComboBox<>();
    private final JComboBox<String> type = new JComboBox<>(new String[]{ALL_TYPES, "Full-time", "Internship"});
    private final JComboBox<String> city = new JComboBox<>();
    private final JComboBox<String> company = new JComboBox<>();
    private final JComboBox<String> window = new JComboBox<>(WINDOWS);
    private final JComboBox<String> experience = new JComboBox<>(EXPERIENCE);
    private final JComboBox<Sort> sort = new JComboBox<>();
    private final JCheckBox onlyWithReferrer = new JCheckBox("With a referrer");
    private final JCheckBox newOnly = new JCheckBox("New since last visit");
    private final JCheckBox showHidden = new JCheckBox("Show hidden");
    private final JLabel countLabel = Ui.label(" ", Theme.BODY_BOLD, Theme.TEXT);
    private List<String> companyNames = List.of();
    private boolean quiet;

    // Built in the constructor: the model's columns depend on mode, which field initialisers can't see yet.
    private final JobsModel model;
    private final JTable table;
    private final TableRowSorter<JobsModel> sorter;
    private final JPanel detail = new ScrollablePanel(null);

    private List<JobPosting> allJobs = List.of();
    private Map<String, Integer> referrerCounts = Map.of();
    private Map<String, RequestStatus> myStatuses = Map.of();
    private Map<String, Stage> stages = Map.of();
    private Map<String, Instant> firstSeen = Map.of();
    private Map<String, JobMatcher.Match> matches = Map.of();
    private UserPrefs prefs;
    private CandidateProfile profile;
    private String shownId;
    private String detailStamp;

    JobsPanel(AppServices app, Account account, Mode mode, Runnable onChanged) {
        super(new BorderLayout(0, 12));
        this.app = app;
        this.account = account;
        this.mode = mode;
        this.onChanged = onChanged;
        this.model = new JobsModel();
        this.table = new JTable(model);
        this.sorter = new TableRowSorter<>(model);
        setOpaque(false);
        setBorder(Ui.padding(4, 24, 20, 24));

        add(filters(), BorderLayout.NORTH);

        Ui.styleTable(table);
        table.setName("jobsTable");
        table.setRowHeight(52);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSorter(sorter);
        sorter.setSortsOnUpdates(false);
        installRenderers();
        sizeColumns();
        applySort();
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !quiet) {
                showDetailIfChanged(selected());
            }
        });

        detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
        detail.setOpaque(false);
        detail.setName("jobDetail");
        Ui.Card detailCard = new Ui.Card(new BorderLayout());
        detailCard.setBorder(Ui.padding(18, 20, 18, 8));
        detailCard.add(Ui.bareScroll(detail), BorderLayout.CENTER);
        detailCard.setMinimumSize(new Dimension(340, 200));
        detailCard.setPreferredSize(new Dimension(390, 400));
        javax.swing.JScrollPane tableScroll = Ui.scroll(table);
        tableScroll.setPreferredSize(new Dimension(mode == Mode.SEEKER ? 680 : 640, 400));
        tableScroll.setMinimumSize(new Dimension(420, 200));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, detailCard);
        split.setResizeWeight(0.62);
        split.setDividerSize(14);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);

        refreshData();
    }

    // ---------------------------------------------------------------- filters bar

    private JComponent filters() {
        role.addItem(ALL_ROLES);
        for (JobCategory c : JobCategory.values()) {
            role.addItem(c.label());
        }
        city.addItem(ALL_CITIES);
        IndiaLocations.cityChoices().forEach(city::addItem);
        window.setSelectedIndex(mode == Mode.SEEKER ? 2 : 4);
        company.addItem(ALL_COMPANIES);
        company.setPrototypeDisplayValue("Warner Bros. Discovery");
        company.setMaximumRowCount(20);
        sort.addItem(Sort.NEWEST);
        if (mode == Mode.SEEKER) {
            sort.addItem(Sort.MATCH);
        }
        sort.addItem(Sort.EXPERIENCE);
        if (mode == Mode.SEEKER) {
            sort.addItem(Sort.COMPANY);
        }
        experience.setToolTipText("Uses the minimum experience each posting states. Openings that don't state "
                + "one are left out of these filters — nothing is guessed.");
        search.setName("jobSearch");
        company.setName("companyFilter");
        experience.setName("experienceFilter");
        sort.setName("sort");
        role.setName("roleFilter");
        window.setName("windowFilter");
        for (JComboBox<?> box : List.of(role, type, city, window, company, experience, sort)) {
            box.addActionListener(e -> {
                if (!quiet) {
                    if (box == sort) {
                        applySort();
                    }
                    applyFilters();
                }
            });
        }
        search.setPreferredSize(new Dimension(250, 36));
        Form.onChange(search, () -> {
            if (!quiet) {
                applyFilters();
            }
        });
        for (JCheckBox b : List.of(onlyWithReferrer, newOnly, showHidden)) {
            b.setOpaque(false);
            b.setFont(Theme.BODY);
            b.setForeground(Theme.TEXT_2);
            b.setFocusPainted(false);
            b.addActionListener(e -> applyFilters());
        }
        onlyWithReferrer.setName("withReferrer");
        newOnly.setName("newOnly");

        Ui.Card bar = new Ui.Card(new BorderLayout(0, 10));
        bar.setBorder(Ui.padding(14, 16, 14, 16));
        JPanel top = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 4));
        top.setOpaque(false);
        top.add(search);
        if (mode == Mode.SEEKER) {
            top.add(company);
        }
        top.add(role);
        top.add(type);
        top.add(city);
        top.add(experience);
        top.add(window);
        top.add(sort);
        // The action buttons flow after the filters, so nothing overlaps in a narrow window.
        Ui.FlatButton reset = Ui.button("Reset", Icons.Glyph.X, Ui.Kind.SUBTLE, () -> applyPreset(Preset.all()));
        reset.setToolTipText("Clear all filters");
        top.add(reset);
        if (mode == Mode.SEEKER) {
            Ui.FlatButton alert = Ui.button("Save as alert", Icons.Glyph.BELL, Ui.Kind.SECONDARY, this::saveAsAlert);
            alert.setName("saveAlert");
            alert.setToolTipText("Get notified when new openings match these filters");
            top.add(alert);
        }
        Ui.FlatButton export = Ui.button("Export CSV", Icons.Glyph.DOWNLOAD, Ui.Kind.SECONDARY, this::exportCsv);
        export.setName("exportJobs");
        top.add(export);
        bar.add(top, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new WrapLayout(FlowLayout.LEFT, 14, 0));
        bottom.setOpaque(false);
        countLabel.setName("jobCount");
        bottom.add(countLabel);
        if (mode == Mode.SEEKER) {
            bottom.add(onlyWithReferrer);
            bottom.add(newOnly);
            bottom.add(showHidden);
        }
        bar.add(bottom, BorderLayout.SOUTH);
        return bar;
    }

    void focusSearch() {
        search.requestFocusInWindow();
        search.selectAll();
    }

    // ---------------------------------------------------------------- data

    @Override
    public void refreshData() {
        allJobs = app.jobs.jobs();
        if (mode == Mode.SEEKER) {
            referrerCounts = app.referrals.referrerCountsByCompany();
            myStatuses = app.referrals.latestStatusByJob(account.id());
            stages = app.tracker.stages(account.id());
            prefs = app.prefs.of(account.id());
            firstSeen = app.jobs.firstSeen();
            profile = app.auth.require(account.id()).profile();
            JobMatcher.Scorer scorer = JobMatcher.scorer(profile, prefs);
            Map<String, JobMatcher.Match> m = new HashMap<>();
            for (JobPosting j : allJobs) {
                m.put(j.id(), scorer.match(j));
            }
            matches = m;
            refillCompanies();
        }
        applyFilters();
    }

    /** Keeps the company list in step with the companies that currently have openings. */
    private void refillCompanies() {
        List<String> names = allJobs.stream().map(JobPosting::company).distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        if (names.equals(companyNames)) {
            return;
        }
        companyNames = names;
        Object chosen = company.getSelectedItem();
        quiet = true;
        try {
            company.removeAllItems();
            company.addItem(ALL_COMPANIES);
            names.forEach(company::addItem);
            if (chosen != null && (ALL_COMPANIES.equals(chosen) || names.contains(chosen))) {
                company.setSelectedItem(chosen);
            }
        } finally {
            quiet = false;
        }
    }

    /** Shows every recent opening at one company (used by the Companies page). */
    void showCompany(String name) {
        applyPreset(new Preset("", name, null, null, "", -1, 30, false, false, Sort.NEWEST));
    }

    void applyPreset(Preset p) {
        refillCompanies();
        quiet = true;
        try {
            search.setText(p.query() == null ? "" : p.query());
            if (p.company() != null && !companyNames.contains(p.company())) {
                company.addItem(p.company());
            }
            company.setSelectedItem(p.company() == null ? ALL_COMPANIES : p.company());
            role.setSelectedItem(p.role() == null ? ALL_ROLES : p.role().label());
            type.setSelectedIndex(p.internship() == null ? 0 : p.internship() ? 2 : 1);
            city.setSelectedItem(p.city() == null || p.city().isEmpty() ? ALL_CITIES : p.city());
            int exp = 0;
            for (int i = 0; i < EXPERIENCE_MAX.length; i++) {
                if (EXPERIENCE_MAX[i] == p.maxYears()) {
                    exp = i;
                }
            }
            experience.setSelectedIndex(exp);
            int w = WINDOWS.length - 1;
            for (int i = 0; i < WINDOW_DAYS.length; i++) {
                if (WINDOW_DAYS[i] >= p.windowDays()) {
                    w = i;
                    break;
                }
            }
            window.setSelectedIndex(w);
            newOnly.setSelected(p.newOnly());
            onlyWithReferrer.setSelected(p.withReferrer());
            showHidden.setSelected(false);
            sort.setSelectedItem(mode == Mode.COMPANY && (p.sort() == Sort.MATCH || p.sort() == Sort.COMPANY)
                    ? Sort.NEWEST : p.sort());
        } finally {
            quiet = false;
        }
        applySort();
        applyFilters();
    }

    /** The filters as they are now, for "Save as alert". */
    Preset currentPreset() {
        String roleChoice = (String) role.getSelectedItem();
        JobCategory cat = null;
        for (JobCategory c : JobCategory.values()) {
            if (c.label().equals(roleChoice)) {
                cat = c;
            }
        }
        String typeChoice = (String) type.getSelectedItem();
        String cityChoice = (String) city.getSelectedItem();
        Object companyChoice = company.getSelectedItem();
        return new Preset(search.getText().trim(),
                companyChoice == null || ALL_COMPANIES.equals(companyChoice) ? null : companyChoice.toString(),
                cat, "Internship".equals(typeChoice) ? Boolean.TRUE : "Full-time".equals(typeChoice) ? Boolean.FALSE : null,
                ALL_CITIES.equals(cityChoice) ? "" : cityChoice, maxYearsChoice(),
                WINDOW_DAYS[Math.max(0, window.getSelectedIndex())], newOnly.isSelected(),
                onlyWithReferrer.isSelected(), (Sort) sort.getSelectedItem());
    }

    private int maxYearsChoice() {
        int i = Math.max(0, experience.getSelectedIndex());
        if (EXPERIENCE_MAX[i] == -2) {
            return profile != null && profile.yearsKnown() ? profile.years() : -1;
        }
        return EXPERIENCE_MAX[i];
    }

    boolean isNew(JobPosting j) {
        if (prefs == null) {
            return false;
        }
        Instant seen = firstSeen.getOrDefault(j.id(), j.postedAt());
        return seen.isAfter(prefs.previousVisitAt());
    }

    private void applyFilters() {
        String keep = shownId;
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofDays(WINDOW_DAYS[Math.max(0, window.getSelectedIndex())]));
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        String roleChoice = (String) role.getSelectedItem();
        String typeChoice = (String) type.getSelectedItem();
        String cityChoice = (String) city.getSelectedItem();
        String companyChoice = (String) company.getSelectedItem();
        int maxYears = maxYearsChoice();
        Set<String> hidden = prefs == null ? Set.of() : prefs.hiddenJobIds();

        List<JobPosting> rows = new ArrayList<>();
        int hiddenCount = 0;
        for (JobPosting j : allJobs) {
            if (mode == Mode.COMPANY && !j.companyKey().equals(account.companyKey())) {
                continue;
            }
            if (j.postedAt().isBefore(cutoff)) {
                continue;
            }
            if (!ALL_ROLES.equals(roleChoice) && !j.category().label().equals(roleChoice)) {
                continue;
            }
            if ("Internship".equals(typeChoice) && !j.internship() || "Full-time".equals(typeChoice) && j.internship()) {
                continue;
            }
            if (cityChoice != null && !ALL_CITIES.equals(cityChoice) && !j.city().contains(cityChoice)) {
                continue;
            }
            if (mode == Mode.SEEKER && companyChoice != null && !ALL_COMPANIES.equals(companyChoice)
                    && !j.company().equals(companyChoice)) {
                continue;
            }
            if (maxYears >= 0) {
                Requirements r = j.requirements();
                // Only openings whose posting states its experience: nothing is guessed.
                if (!r.known() || r.minYears() > maxYears) {
                    continue;
                }
            }
            if (!q.isEmpty() && !matchesQuery(j, q)) {
                continue;
            }
            if (mode == Mode.SEEKER) {
                if (onlyWithReferrer.isSelected() && referrerCounts.getOrDefault(j.companyKey(), 0) == 0) {
                    continue;
                }
                if (newOnly.isSelected() && !isNew(j)) {
                    continue;
                }
                if (hidden.contains(j.id()) && !showHidden.isSelected()) {
                    hiddenCount++;
                    continue;
                }
            }
            rows.add(j);
        }
        quiet = true;
        try {
            model.setRows(rows);
            sorter.sort();
        } finally {
            quiet = false;
        }
        long withReferrer = rows.stream().filter(j -> referrerCounts.getOrDefault(j.companyKey(), 0) > 0).count();
        long fresher = rows.stream().filter(j -> j.requirements().minYears() == 0).count();
        countLabel.setText(String.format("%,d", rows.size()) + (rows.size() == 1 ? " opening" : " openings")
                + (fresher > 0 ? "  ·  " + fresher + " for freshers" : "")
                + (mode == Mode.SEEKER ? "  ·  " + withReferrer + " with a referrer" : "")
                + (hiddenCount > 0 ? "  ·  " + hiddenCount + " hidden" : ""));

        int restore = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(keep)) {
                restore = table.convertRowIndexToView(i);
            }
        }
        if (restore < 0 && !rows.isEmpty()) {
            restore = 0;
        }
        quiet = true;
        try {
            if (restore >= 0) {
                table.setRowSelectionInterval(restore, restore);
                table.scrollRectToVisible(table.getCellRect(restore, 0, true));
            } else {
                table.clearSelection();
            }
        } finally {
            quiet = false;
        }
        showDetailIfChanged(selected());
    }

    /**
     * Rebuilds the detail pane only when what it shows has changed, so live refreshes (every few
     * seconds during a scan) don't reset its scroll position.
     */
    private void showDetailIfChanged(JobPosting job) {
        String stamp = job == null ? "none" : job + "|" + myStatuses.get(job.id()) + "|" + stages.get(job.id())
                + "|" + referrerCounts.get(job.companyKey()) + "|" + (prefs == null ? "" : prefs.hiddenJobIds().contains(job.id()))
                + "|" + (matches.containsKey(job.id()) ? matches.get(job.id()).score() : -1)
                + "|" + (profile == null ? "" : profile.years());
        if (!stamp.equals(detailStamp)) {
            detailStamp = stamp;
            showDetail(job);
        }
    }

    private static boolean matchesQuery(JobPosting j, String q) {
        String hay = (j.title() + " " + j.company() + " " + j.location() + " "
                + String.join(" ", j.requirements().skills())).toLowerCase(Locale.ROOT);
        for (String word : q.split("\\s+")) {
            if (!hay.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private void applySort() {
        Sort s = (Sort) sort.getSelectedItem();
        int expCol = 2;
        List<RowSorter.SortKey> keys = switch (s == null ? Sort.NEWEST : s) {
            case MATCH -> List.of(new RowSorter.SortKey(3, SortOrder.DESCENDING),
                    new RowSorter.SortKey(0, SortOrder.DESCENDING));
            case EXPERIENCE -> List.of(new RowSorter.SortKey(expCol, SortOrder.ASCENDING),
                    new RowSorter.SortKey(0, SortOrder.DESCENDING));
            case COMPANY -> List.of(new RowSorter.SortKey(1, SortOrder.ASCENDING),
                    new RowSorter.SortKey(0, SortOrder.DESCENDING));
            default -> List.of(new RowSorter.SortKey(0, SortOrder.DESCENDING));
        };
        sorter.setSortKeys(keys);
    }

    private JobPosting selected() {
        int view = table.getSelectedRow();
        return view < 0 || view >= table.getRowCount() ? null : model.rows.get(table.convertRowIndexToModel(view));
    }

    /** Selects a job, clearing the filters first if they hide it. Returns false if it is gone. */
    boolean select(String jobId) {
        if (selectInTable(jobId)) {
            return true;
        }
        if (allJobs.stream().noneMatch(j -> j.id().equals(jobId))) {
            return false;
        }
        applyPreset(Preset.all());
        if (prefs != null && prefs.hiddenJobIds().contains(jobId)) {
            showHidden.setSelected(true);
            applyFilters();
        }
        return selectInTable(jobId);
    }

    private boolean selectInTable(String jobId) {
        for (int i = 0; i < model.rows.size(); i++) {
            if (model.rows.get(i).id().equals(jobId)) {
                int view = table.convertRowIndexToView(i);
                if (view >= 0) {
                    table.setRowSelectionInterval(view, view);
                    table.scrollRectToVisible(table.getCellRect(view, 0, true));
                    // Once laid out, put the chosen opening in the middle rather than half-hidden at an edge.
                    javax.swing.SwingUtilities.invokeLater(() -> centerRow(view));
                    return true;
                }
            }
        }
        return false;
    }

    private void centerRow(int row) {
        if (row >= table.getRowCount() || !(table.getParent() instanceof javax.swing.JViewport port)) {
            return;
        }
        java.awt.Rectangle cell = table.getCellRect(row, 0, true);
        int y = cell.y - (port.getHeight() - cell.height) / 2;
        y = Math.max(0, Math.min(y, table.getHeight() - port.getHeight()));
        port.setViewPosition(new java.awt.Point(port.getViewPosition().x, y));
    }

    // ---------------------------------------------------------------- actions

    private void saveAsAlert() {
        Preset p = currentPreset();
        if (AlertDialog.create(this, app, account, p) != null) {
            onChanged.run();
            Ui.toast(this, "Alert saved. New matching openings will show up under Alerts and the bell.");
        }
    }

    private void exportCsv() {
        List<JobPosting> rows = new ArrayList<>();
        for (int v = 0; v < table.getRowCount(); v++) {
            rows.add(model.rows.get(table.convertRowIndexToModel(v)));
        }
        if (rows.isEmpty()) {
            Ui.toast(this, "Nothing to export with these filters.", Toast.Tone.WARNING);
            return;
        }
        exportTo(this, "openings.csv", CsvExport.jobs(rows), rows.size() + " openings");
    }

    /** Asks where to save a CSV file and writes it. */
    static void exportTo(Component parent, String suggested, String csv, String what) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(System.getProperty("user.home"), suggested));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = chooser.getSelectedFile();
        if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            f = new File(f.getParentFile(), f.getName() + ".csv");
        }
        try {
            // A byte-order mark makes Excel read the file as UTF-8.
            Files.writeString(f.toPath(), "﻿" + csv, StandardCharsets.UTF_8);
            Ui.toast(parent, "Exported " + what + " to " + f.getName());
        } catch (Exception e) {
            Ui.error(parent, e);
        }
    }

    private void toggleSaved(JobPosting job) {
        Ui.attempt(this, () -> {
            Stage stage = stages.get(job.id());
            if (stage == null) {
                app.tracker.save(account.id(), job);
                Ui.toast(this, "Saved to your tracker.");
            } else if (stage == Stage.SAVED) {
                app.tracker.remove(account.id(), job.id());
                Ui.toast(this, "Removed from your tracker.", Toast.Tone.INFO);
            } else {
                Ui.toast(this, "This job is on your tracker as \"" + stage.label() + "\". Change it under Saved & applied.",
                        Toast.Tone.INFO);
                return;
            }
            refreshData();
            onChanged.run();
        });
    }

    private void markApplied(JobPosting job) {
        Ui.attempt(this, () -> {
            app.tracker.track(account.id(), job, Stage.APPLIED);
            refreshData();
            onChanged.run();
            Ui.toast(this, "Marked as applied. Track it under Saved & applied.");
        });
    }

    private void setHidden(JobPosting job, boolean hide) {
        app.prefs.hide(account.id(), job.id(), hide);
        refreshData();
        Ui.toast(this, hide ? "Hidden. Tick \"Show hidden\" to see it again." : "Shown again.", Toast.Tone.INFO);
    }

    private void requestReferral(JobPosting job) {
        ReferralRequest sent = ReferralRequestDialog.compose(this, app, account, job);
        if (sent != null) {
            if (!stages.containsKey(job.id())) {
                Ui.attempt(this, () -> app.tracker.save(account.id(), job));
            }
            refreshData();
            onChanged.run();
            Ui.toast(this, "Request " + sent.id() + " sent to " + sent.referrerName()
                    + ". Track it under Referral requests.");
        }
    }

    // ---------------------------------------------------------------- detail pane

    private void showDetail(JobPosting job) {
        shownId = job == null ? null : job.id();
        detail.removeAll();
        if (job == null) {
            detail.add(Box.createVerticalStrut(30));
            JLabel icon = new JLabel(Icons.get(Icons.Glyph.SEARCH, 40, Theme.FAINT));
            icon.setAlignmentX(LEFT_ALIGNMENT);
            detail.add(icon);
            detail.add(Box.createVerticalStrut(12));
            detail.add(Ui.label("No openings match these filters", Theme.H3, Theme.TEXT));
            detail.add(Box.createVerticalStrut(8));
            detail.add(Ui.text(app.jobs.lastScanAt() == null
                    ? "Click the refresh button at the top right (" + Shell.shortcut("R") + ") to fetch recent "
                    + "openings from " + app.directory.all().size() + " company careers sites."
                    : "Try a longer time window, another city, role or experience level, or clear the search.",
                    Theme.BODY, Theme.MUTED));
            detail.revalidate();
            detail.repaint();
            return;
        }
        JPanel head = Ui.flexRow(new BorderLayout(14, 0));
        JLabel avatar = Ui.avatar(job.company(), 48);
        JPanel avatarHolder = new JPanel(new BorderLayout());
        avatarHolder.setOpaque(false);
        avatarHolder.add(avatar, BorderLayout.NORTH);
        head.add(avatarHolder, BorderLayout.WEST);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        Ui.WrapText title = Ui.text(job.title(), Theme.H2, Theme.TEXT);
        title.setName("detailTitle");
        titles.add(title);
        titles.add(Box.createVerticalStrut(4));
        titles.add(Ui.text(job.company() + "  ·  " + job.location(), Theme.BODY, Theme.MUTED));
        head.add(titles, BorderLayout.CENTER);
        detail.add(head);
        detail.add(Box.createVerticalStrut(12));

        List<Component> chips = new ArrayList<>();
        chips.add(new Ui.Pill(job.category().label(), Theme.PRIMARY_TEXT, Theme.PRIMARY_SOFT));
        chips.add(new Ui.Pill(job.typeLabel(), job.internship() ? Theme.WARNING : Theme.SUCCESS,
                job.internship() ? Theme.WARNING_SOFT : Theme.SUCCESS_SOFT));
        chips.add(Ui.experiencePill(job));
        if (mode == Mode.SEEKER && isNew(job)) {
            chips.add(Ui.newBadge());
        }
        Stage stage = stages.get(job.id());
        if (stage != null) {
            chips.add(new Ui.Pill(stage.label(), Theme.stageColor(stage), Theme.alpha(Theme.stageColor(stage), 40))
                    .withIcon(Icons.Glyph.BOOKMARK));
        }
        detail.add(Ui.row(6, chips.toArray(new Component[0])));
        detail.add(Box.createVerticalStrut(14));
        detail.add(actions(job));

        section("Eligibility");
        detail.add(eligibility(job));

        if (mode == Mode.SEEKER) {
            JobMatcher.Match m = matches.get(job.id());
            if (m != null) {
                section("Your match");
                detail.add(matchCard(m));
            }
        }
        if (!job.requirements().skills().isEmpty()) {
            section("Skills in the posting");
            List<Component> skills = new ArrayList<>();
            for (String s : job.requirements().skills()) {
                skills.add(Ui.neutralPill(s));
            }
            JPanel skillRow = Ui.row(6, skills.toArray(new Component[0]));
            ((FlowLayout) skillRow.getLayout()).setVgap(6);
            detail.add(skillRow);
        }
        if (mode == Mode.SEEKER) {
            section("Referral");
            addReferralSection(job);
        } else {
            detail.add(Box.createVerticalStrut(14));
            detail.add(Ui.text("Seekers browsing this opening can send you a referral request; it will appear in your "
                    + "inbox.", Theme.BODY, Theme.MUTED));
        }
        section("Details");
        if (job.dateKnown()) {
            detail.add(fact("Posted", Ui.ago(job.postedAt()) + " (" + Ui.date(job.postedAt()) + ")"));
        } else {
            detail.add(fact("Seen", "first seen " + Ui.ago(job.postedAt()) + " — " + job.company()
                    + " doesn't publish posting dates"));
        }
        detail.add(fact("City", job.city()));
        detail.add(fact("Source", job.company() + " careers site via " + job.source().label()));
        detail.add(Box.createVerticalGlue());
        detail.revalidate();
        detail.repaint();
    }

    /**
     * One full-width main action that always says where things stand ("Get referral", "Referral
     * requested · Pending", "No referrer here yet"), then the secondary actions on one row.
     */
    private JComponent actions(JobPosting job) {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setAlignmentX(LEFT_ALIGNMENT);
        if (mode == Mode.SEEKER) {
            box.add(stretch(referralButton(job)));
            box.add(Box.createVerticalStrut(8));
        }
        JPanel secondary = new JPanel(new java.awt.GridLayout(1, 0, 8, 0));
        secondary.setOpaque(false);
        if (mode == Mode.SEEKER) {
            Stage stage = stages.get(job.id());
            Ui.FlatButton save = Ui.button(stage == null ? "Save" : "Saved",
                    stage == null ? Icons.Glyph.BOOKMARK : Icons.Glyph.BOOKMARK_FILLED, Ui.Kind.SECONDARY,
                    () -> toggleSaved(job));
            save.setName("saveJob");
            save.setToolTipText(stage == null ? "Save to your tracker" : "On your tracker: " + stage.label());
            secondary.add(save);
        }
        Ui.FlatButton open = Ui.button("Open posting", Icons.Glyph.EXTERNAL, Ui.Kind.SECONDARY,
                () -> Ui.openUrl(this, job.url()));
        open.setName("openPosting");
        open.setToolTipText(job.url());
        secondary.add(open);
        Ui.FlatButton more = Ui.iconButton(Icons.Glyph.MORE, "More actions", Ui.Kind.SECONDARY, () -> { });
        more.setName("moreActions");
        more.addActionListener(e -> moreMenu(job).show(more, 0, more.getHeight() + 4));
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(secondary, BorderLayout.CENTER);
        row.add(more, BorderLayout.EAST);
        box.add(stretch(row));
        return box;
    }

    /** The seeker's main action for an opening, labelled with the state of their request. */
    private Ui.FlatButton referralButton(JobPosting job) {
        int referrers = referrerCounts.getOrDefault(job.companyKey(), 0);
        RequestStatus status = myStatuses.get(job.id());
        Ui.FlatButton b;
        if (status != null && status != RequestStatus.DECLINED && status != RequestStatus.WITHDRAWN) {
            // Already asked: the button opens that request instead of being a dead, greyed-out "Get referral".
            Optional<ReferralRequest> mine = app.referrals.forSeeker(account.id()).stream()
                    .filter(r -> r.job().id().equals(job.id())).findFirst();
            b = Ui.button("Referral requested · " + status.label(), status == RequestStatus.REFERRED
                            ? Icons.Glyph.CHECK : Icons.Glyph.SEND,
                    status == RequestStatus.REFERRED ? Ui.Kind.SUCCESS
                            : status == RequestStatus.NEEDS_INFO ? Ui.Kind.WARNING : Ui.Kind.SECONDARY,
                    () -> mine.ifPresent(r -> openRequest.accept(r.id())));
            b.setToolTipText("Open your request and its conversation");
        } else if (referrers == 0) {
            b = Ui.button("No referrer at " + job.company() + " yet", Icons.Glyph.USERS, Ui.Kind.SECONDARY, () -> { });
            b.setEnabled(false);
            b.setToolTipText("No one from " + job.company() + " has signed up as a referrer yet. You can still apply "
                    + "through Open posting.");
        } else {
            b = Ui.button(status == null ? "Get referral" : "Request again", Icons.Glyph.SEND, Ui.Kind.PRIMARY,
                    () -> requestReferral(job));
            b.setToolTipText(referrers + (referrers == 1 ? " referrer" : " referrers") + " at " + job.company());
        }
        b.setName("getReferral");
        return b;
    }

    /** Lets a component use the full width of the detail column. */
    private static JComponent stretch(JComponent c) {
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.setAlignmentX(LEFT_ALIGNMENT);
        holder.add(c, BorderLayout.CENTER);
        holder.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return holder;
    }

    /** Called with a request id when the seeker asks to see a request they already sent. */
    void onOpenRequest(java.util.function.Consumer<String> open) {
        this.openRequest = open;
    }

    private JPopupMenu moreMenu(JobPosting job) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Copy link", Icons.Glyph.COPY, () -> {
            Ui.copy(job.url());
            Ui.toast(this, "Link copied.");
        }));
        if (mode == Mode.SEEKER) {
            menu.add(item("Mark as applied", Icons.Glyph.CHECK, () -> markApplied(job)));
            boolean hidden = prefs != null && prefs.hiddenJobIds().contains(job.id());
            menu.add(item(hidden ? "Show again" : "Not interested — hide", Icons.Glyph.EYE_OFF,
                    () -> setHidden(job, !hidden)));
            menu.add(item("Show all at " + job.company(), Icons.Glyph.BUILDING, () -> showCompany(job.company())));
        }
        return menu;
    }

    static JMenuItem item(String text, Icons.Glyph glyph, Runnable action) {
        JMenuItem i = new JMenuItem(text, Icons.live(glyph, 16, () -> Theme.MUTED));
        i.setIconTextGap(10);
        i.addActionListener(e -> action.run());
        return i;
    }

    private JComponent eligibility(JobPosting job) {
        Requirements r = job.requirements();
        Ui.Card box = new Ui.Card(new BorderLayout()).fill(() -> Theme.SURFACE_2);
        box.setName("eligibility");
        box.setBorder(Ui.padding(14, 16, 16, 16));
        JPanel in = new JPanel();
        in.setOpaque(false);
        in.setLayout(new BoxLayout(in, BoxLayout.Y_AXIS));

        String years = r.yearsText();
        String headline;
        if (!r.known()) {
            headline = r.detailsRead() ? "Experience not stated" : "Experience not read yet";
        } else if (r.preferredOnly()) {
            headline = (r.minYears() == 0 && r.maxYears() < 0 ? "Freshers" : years) + " preferred";
        } else if (r.minYears() == 0 && r.maxYears() < 0) {
            headline = "Freshers welcome";
        } else {
            headline = years + " of experience";
        }
        JLabel big = Ui.label(headline, Theme.H3, Theme.TEXT);
        big.setName("minExperience");
        big.setIcon(Icons.get(Icons.Glyph.CLOCK, 18, Theme.PRIMARY));
        big.setIconTextGap(8);
        Ui.Pill basis = switch (r.basis()) {
            case STATED -> new Ui.Pill("Stated in posting", Theme.SUCCESS, Theme.SUCCESS_SOFT);
            case PREFERRED -> new Ui.Pill("Preferred, not required", Theme.TEXT_2, Theme.NEUTRAL_SOFT);
            case UNKNOWN -> null;
        };
        in.add(basis == null ? Ui.row(8, big) : Ui.row(8, big, basis));
        if (!r.evidence().isEmpty()) {
            in.add(Box.createVerticalStrut(6));
            in.add(Ui.text("“" + r.evidence() + "”", Theme.SMALL, Theme.MUTED));
        } else if (!r.known()) {
            in.add(Box.createVerticalStrut(6));
            in.add(Ui.text(r.detailsRead()
                    ? "The posting doesn't state a number of years. Open the posting to check its requirements."
                    : "The full description hasn't been read yet; the next scan reads it and shows what it states.",
                    Theme.SMALL, Theme.MUTED));
        }
        if (!r.degree().isEmpty()) {
            in.add(Box.createVerticalStrut(8));
            in.add(Ui.iconLabel(r.degree(), Icons.Glyph.CAP, Theme.SMALL, Theme.TEXT_2));
        }
        if (!r.batch().isEmpty()) {
            in.add(Box.createVerticalStrut(6));
            in.add(Ui.iconLabel("Batch: " + r.batch(), Icons.Glyph.AWARD, Theme.SMALL, Theme.TEXT_2));
        }
        if (mode == Mode.SEEKER && profile != null && r.known()) {
            in.add(Box.createVerticalStrut(10));
            if (!profile.yearsKnown()) {
                in.add(Ui.iconLabel("Add your years of experience in Profile to see if you qualify",
                        Icons.Glyph.USER, Theme.SMALL, Theme.MUTED));
            } else if (r.fits(profile.years())) {
                in.add(Ui.iconLabel("You meet " + (r.preferredOnly() ? "the preference" : "the minimum") + " (you have "
                        + profile.experiencePhrase() + ")", Icons.Glyph.CHECK, Theme.SMALL_BOLD,
                        Theme.SUCCESS));
            } else {
                double gap = r.minYears() - profile.years();
                in.add(Ui.iconLabel((r.preferredOnly() ? "Prefers " : "Asks for ") + Requirements.years(gap) + " more "
                        + (gap == 1 ? "year" : "years") + " than you have", Icons.Glyph.ZAP, Theme.SMALL_BOLD,
                        Theme.WARNING));
            }
        }
        box.add(in, BorderLayout.CENTER);
        return wrap(box);
    }

    private JComponent matchCard(JobMatcher.Match m) {
        JPanel p = Ui.flexRow(new BorderLayout(14, 0));
        Charts.Ring ring = new Charts.Ring(58);
        ring.setValue(m.score(), Theme.matchColor(m.score()));
        JPanel ringHolder = new JPanel(new BorderLayout());
        ringHolder.setOpaque(false);
        ringHolder.add(ring, BorderLayout.NORTH);
        p.add(ringHolder, BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(Ui.label(m.label(), Theme.BODY_BOLD, Theme.TEXT));
        text.add(Box.createVerticalStrut(4));
        if (!m.matched().isEmpty()) {
            List<Component> have = new ArrayList<>();
            have.add(Ui.label("You have", Theme.SMALL, Theme.MUTED));
            for (String s : m.matched()) {
                have.add(new Ui.Pill(s, Theme.SUCCESS, Theme.SUCCESS_SOFT));
            }
            JPanel row = Ui.row(5, have.toArray(new Component[0]));
            ((FlowLayout) row.getLayout()).setVgap(4);
            text.add(row);
        }
        if (!m.missing().isEmpty()) {
            List<Component> need = new ArrayList<>();
            need.add(Ui.label("To brush up", Theme.SMALL, Theme.MUTED));
            for (String s : m.missing()) {
                need.add(Ui.neutralPill(s));
            }
            JPanel row = Ui.row(5, need.toArray(new Component[0]));
            ((FlowLayout) row.getLayout()).setVgap(4);
            text.add(row);
        }
        if (m.matched().isEmpty() && m.missing().isEmpty()) {
            text.add(Ui.text("Add your skills in Profile to compare them with each posting.", Theme.SMALL, Theme.MUTED));
        }
        p.add(text, BorderLayout.CENTER);
        return p;
    }

    private void addReferralSection(JobPosting job) {
        int referrers = referrerCounts.getOrDefault(job.companyKey(), 0);
        RequestStatus status = myStatuses.get(job.id());
        if (status != null) {
            Optional<ReferralRequest> mine = app.referrals.forSeeker(account.id()).stream()
                    .filter(r -> r.job().id().equals(job.id())).findFirst();
            detail.add(Ui.row(6, Ui.label("Your request:", Theme.BODY, Theme.TEXT), Ui.statusPill(status)));
            mine.ifPresent(r -> {
                detail.add(Box.createVerticalStrut(4));
                detail.add(Ui.text("Sent to " + r.referrerTitle() + " · updated " + Ui.ago(r.updatedAt()),
                        Theme.SMALL, Theme.MUTED));
            });
            detail.add(Box.createVerticalStrut(8));
        }
        if (referrers > 0) {
            detail.add(Ui.iconLabel(referrers + (referrers == 1 ? " referrer" : " referrers") + " at " + job.company(),
                    Icons.Glyph.USERS, Theme.BODY_BOLD, Theme.SUCCESS));
            detail.add(Box.createVerticalStrut(4));
            detail.add(Ui.text("Your profile, resume and pitch go to the one with the fewest pending requests.",
                    Theme.SMALL, Theme.MUTED));
        } else {
            detail.add(Ui.text("No one from " + job.company() + " has signed up as a referrer yet. Know someone there? "
                    + "Ask them to create a referrer account, then come back.", Theme.SMALL, Theme.MUTED));
        }
    }

    private void section(String title) {
        detail.add(Box.createVerticalStrut(20));
        JLabel l = Ui.sectionTitle(title);
        l.setAlignmentX(LEFT_ALIGNMENT);
        detail.add(l);
        detail.add(Box.createVerticalStrut(8));
    }

    private static JComponent fact(String label, String value) {
        JPanel p = Ui.flexRow(new BorderLayout(10, 0));
        JLabel l = Ui.label(label, Theme.SMALL_BOLD, Theme.MUTED);
        l.setPreferredSize(new Dimension(58, 18));
        p.add(l, BorderLayout.WEST);
        p.add(Ui.text(value, Theme.SMALL, Theme.TEXT), BorderLayout.CENTER);
        p.setBorder(Ui.padding(0, 0, 6, 0));
        return p;
    }

    private static JComponent wrap(JComponent c) {
        JPanel holder = Ui.flexRow(new BorderLayout());
        holder.add(c, BorderLayout.CENTER);
        return holder;
    }

    private void sizeColumns() {
        int[] widths = mode == Mode.SEEKER
                ? new int[]{72, 340, 96, 74, 124}
                : new int[]{78, 380, 120, 110, 90};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    // ---------------------------------------------------------------- table plumbing

    record ReferralCell(int referrers, RequestStatus status) {
    }

    /** Sorts by minimum experience, with "not stated" last; internships count as zero. */
    record ExpCell(JobPosting job) implements Comparable<ExpCell> {
        double key() {
            return job.requirements().known() ? job.requirements().minYears() : 99;
        }

        @Override
        public int compareTo(ExpCell o) {
            return Double.compare(key(), o.key());
        }
    }

    /**
     * When a job was posted, or (known = false) when this app first saw it. Sorts by time, with real
     * dates ranked above first-seen times so a company's first scan doesn't flood the top of the list.
     */
    record Posted(Instant at, boolean known) implements Comparable<Posted> {
        @Override
        public int compareTo(Posted o) {
            if (known != o.known) {
                return known ? 1 : -1;
            }
            return at.compareTo(o.at);
        }
    }

    private void installRenderers() {
        table.getColumnModel().getColumn(0).setCellRenderer(new AgeRenderer());
        if (mode == Mode.SEEKER) {
            table.getColumnModel().getColumn(1).setCellRenderer(new RoleRenderer());
            table.getColumnModel().getColumn(2).setCellRenderer(new ExpRenderer());
            table.getColumnModel().getColumn(3).setCellRenderer(new MatchRenderer());
            table.getColumnModel().getColumn(4).setCellRenderer(new ReferralRenderer());
            sorter.setComparator(1, Comparator.comparing(JobPosting::company, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(JobPosting::title, String.CASE_INSENSITIVE_ORDER));
            sorter.setComparator(4, Comparator.comparingInt((ReferralCell c) -> c.status() != null ? 2
                    : c.referrers() > 0 ? 1 : 0));
        } else {
            table.getColumnModel().getColumn(1).setCellRenderer(new RoleRenderer());
            table.getColumnModel().getColumn(2).setCellRenderer(new ExpRenderer());
            sorter.setComparator(1, Comparator.comparing(JobPosting::title, String.CASE_INSENSITIVE_ORDER));
        }
    }

    private final class JobsModel extends AbstractTableModel {
        private List<JobPosting> rows = List.of();

        void setRows(List<JobPosting> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 5;
        }

        @Override
        public String getColumnName(int c) {
            String[] seeker = {"Posted", "Company & role", "Min. exp.", "Match", "Referral"};
            String[] company = {"Posted", "Role", "Min. exp.", "City", "Type"};
            return (mode == Mode.SEEKER ? seeker : company)[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            if (c == 0) {
                return Posted.class;
            }
            if (mode == Mode.COMPANY) {
                return switch (c) {
                    case 1 -> JobPosting.class;
                    case 2 -> ExpCell.class;
                    default -> String.class;
                };
            }
            return switch (c) {
                case 1 -> JobPosting.class;
                case 2 -> ExpCell.class;
                case 3 -> Integer.class;
                default -> ReferralCell.class;
            };
        }

        @Override
        public Object getValueAt(int r, int c) {
            JobPosting j = rows.get(r);
            if (mode == Mode.COMPANY) {
                return switch (c) {
                    case 0 -> new Posted(j.postedAt(), j.dateKnown());
                    case 1 -> j;
                    case 2 -> new ExpCell(j);
                    case 3 -> j.city();
                    default -> j.typeLabel();
                };
            }
            return switch (c) {
                case 0 -> new Posted(j.postedAt(), j.dateKnown());
                case 1 -> j;
                case 2 -> new ExpCell(j);
                case 3 -> matches.containsKey(j.id()) ? matches.get(j.id()).score() : 0;
                default -> new ReferralCell(referrerCounts.getOrDefault(j.companyKey(), 0), myStatuses.get(j.id()));
            };
        }
    }

    private static final class AgeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            Component base = t.getDefaultRenderer(Object.class)
                    .getTableCellRendererComponent(t, v instanceof Posted p
                            ? (p.known() ? "" : "~") + Ui.agoShort(p.at()).replace(" ago", "") : v, sel, focus, r, c);
            JLabel l = (JLabel) base;
            l.setForeground(Theme.MUTED);
            l.setFont(Theme.SMALL);
            l.setToolTipText(v instanceof Posted p && !p.known()
                    ? "First seen by this app — the company doesn't publish posting dates"
                    : v instanceof Posted p ? "Posted " + Ui.date(p.at()) : null);
            return base;
        }
    }

    /**
     * The company's avatar, the title in bold, and "Company · City · Category · Type" underneath, with
     * a NEW badge for openings found since the last visit.
     */
    private final class RoleRenderer implements javax.swing.table.TableCellRenderer {
        private final JPanel panel = new JPanel(new BorderLayout(10, 0));
        private final JLabel avatar = new JLabel();
        private final JLabel title = Ui.label("", Theme.BODY_BOLD, Theme.TEXT);
        private final JLabel sub = Ui.label("", Theme.SMALL, Theme.MUTED);
        private final Ui.Pill badge = Ui.newBadge();
        private final JPanel badgeHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 11));

        RoleRenderer() {
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(title);
            text.add(Box.createVerticalStrut(2));
            text.add(sub);
            if (mode == Mode.SEEKER) {
                panel.add(avatar, BorderLayout.WEST);
            }
            panel.add(text, BorderLayout.CENTER);
            badgeHolder.setOpaque(false);
            badgeHolder.add(badge);
            panel.add(badgeHolder, BorderLayout.EAST);
            panel.setBorder(Ui.padding(8, 12, 6, 10));
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            JobPosting j = (JobPosting) v;
            panel.setBackground(Ui.rowBackground(t, sel, r));
            title.setForeground(Theme.TEXT);
            sub.setForeground(Theme.MUTED);
            title.setText(j.title());
            if (mode == Mode.SEEKER) {
                avatar.setIcon(new Ui.Avatar(j.company(), 30, false));
                sub.setText(j.company() + " · " + j.city() + " · " + j.category().shortLabel()
                        + (j.internship() ? " · Internship" : "")
                        + (stages.containsKey(j.id()) ? " · " + stages.get(j.id()).label() : ""));
            } else {
                sub.setText(j.category().shortLabel() + " · " + j.typeLabel());
            }
            badgeHolder.setVisible(mode == Mode.SEEKER && isNew(j));
            panel.setToolTipText(j.title());
            return panel;
        }
    }

    private static final class ExpRenderer implements javax.swing.table.TableCellRenderer {
        private final JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 14));
        private Ui.Pill pill;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            holder.removeAll();
            holder.setBackground(Ui.rowBackground(t, sel, r));
            JobPosting j = ((ExpCell) v).job();
            pill = Ui.experiencePill(j);
            pill.setIcon(null);
            if (!j.requirements().known()) {
                pill.setText(j.requirements().shortLabel());
            }
            pill.setToolTipText(j.requirements().longLabel());
            holder.add(pill);
            holder.setToolTipText(j.requirements().longLabel());
            return holder;
        }
    }

    /** A short bar and the percentage. */
    private static final class MatchRenderer extends JComponent implements javax.swing.table.TableCellRenderer {
        private int score;
        private Color bg;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            score = v instanceof Integer i ? i : 0;
            bg = Ui.rowBackground(t, sel, r);
            setToolTipText("How well this opening fits your skills, experience and preferences");
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Laf.smooth(g);
            g2.setColor(bg);
            g2.fillRect(0, 0, getWidth(), getHeight());
            int x = 12;
            int y = getHeight() / 2 - 3;
            g2.setFont(Theme.SMALL_BOLD);
            String text = score + "%";
            int textW = g2.getFontMetrics().stringWidth(text);
            int w = Math.min(40, getWidth() - x - textW - 10);
            if (w >= 16) {
                // Room for a bar as well as the number; in a narrow column only the number is shown.
                g2.setColor(Theme.NEUTRAL_SOFT);
                g2.fillRoundRect(x, y, w, 6, 6, 6);
                g2.setColor(Theme.matchColor(score));
                g2.fillRoundRect(x, y, Math.max(4, w * score / 100), 6, 6, 6);
                x += w + 6;
            } else {
                g2.setColor(Theme.matchColor(score));
                g2.fillOval(x, getHeight() / 2 - 3, 6, 6);
                x += 10;
            }
            g2.setColor(Theme.TEXT);
            g2.drawString(text, x, getHeight() / 2 + g2.getFontMetrics().getAscent() / 2 - 1);
            g2.dispose();
        }
    }

    private static final class ReferralRenderer extends DefaultTableCellRenderer {
        private final Ui.Pill pill = new Ui.Pill("", Theme.TEXT, Theme.NEUTRAL_SOFT);
        private final JLabel none = Ui.label("No referrer yet", Theme.SMALL, Theme.FAINT);
        private final JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 14));

        ReferralRenderer() {
            none.setBorder(Ui.padding(3, 2, 0, 0));
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            holder.removeAll();
            holder.setBackground(Ui.rowBackground(t, sel, r));
            ReferralCell cell = (ReferralCell) v;
            if (cell.status() != null) {
                pill.setText(cell.status().label());
                pill.setColors(Theme.statusColor(cell.status()), Theme.statusSoft(cell.status()));
                holder.add(pill);
            } else if (cell.referrers() > 0) {
                pill.setText("Get referral · " + cell.referrers());
                pill.setColors(Theme.SUCCESS, Theme.SUCCESS_SOFT);
                holder.add(pill);
            } else {
                none.setForeground(Theme.FAINT);
                holder.add(none);
            }
            return holder;
        }
    }
}
