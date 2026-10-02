package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.model.JobCategory;
import com.referralconnect.model.JobPosting;
import com.referralconnect.model.ReferralRequest;
import com.referralconnect.model.RequestStatus;
import com.referralconnect.scan.IndiaLocations;
import com.referralconnect.scan.JobScanner;
import com.referralconnect.service.AppServices;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Recently posted openings from the last scan. Seekers see whether a referrer is available for
 * each one and can request a referral; referrers see the openings at their own company.
 */
final class JobsPanel extends JPanel implements AppFrame.Live {

    enum Mode { SEEKER, COMPANY }

    private static final String ALL_ROLES = "All roles";
    private static final String ALL_TYPES = "Jobs + internships";
    private static final String ALL_CITIES = "All of India";
    private static final String ALL_COMPANIES = "All companies";
    private static final String[] WINDOWS = {"Last 24 hours", "Last 3 days", "Last 7 days", "Last 14 days", "Last 30 days"};
    private static final int[] WINDOW_DAYS = {1, 3, 7, 14, 30};

    private final AppServices app;
    private final Account account;
    private final Mode mode;
    private final Runnable onRequestSent;

    private final Form.HintField search = new Form.HintField("", "Search role or company");
    private final JComboBox<String> role = new JComboBox<>();
    private final JComboBox<String> type = new JComboBox<>(new String[]{ALL_TYPES, "Full-time", "Internship"});
    private final JComboBox<String> city = new JComboBox<>();
    private final JComboBox<String> company = new JComboBox<>();
    private final JComboBox<String> window = new JComboBox<>(WINDOWS);
    private List<String> companyNames = List.of();
    private boolean refillingCompanies;
    private final JCheckBox onlyWithReferrer = new JCheckBox("Only openings with a referrer");
    private final JLabel countLabel = Ui.label(" ", Theme.BODY_BOLD, Theme.TEXT);
    private final JLabel scanLabel = Ui.label(" ", Theme.SMALL, Theme.MUTED);
    private final JProgressBar progress = new JProgressBar();
    private final Ui.FlatButton scanButton;

    // Built in the constructor: the model's columns depend on mode, which field initialisers can't see yet.
    private final JobsModel model;
    private final JTable table;
    private final JPanel detail = new ScrollablePanel(null);

    private Map<String, Integer> referrerCounts = Map.of();
    private Map<String, RequestStatus> myStatuses = Map.of();
    private boolean scanning;

    JobsPanel(AppServices app, Account account, Mode mode, Runnable onRequestSent) {
        super(new BorderLayout(0, 12));
        this.app = app;
        this.account = account;
        this.mode = mode;
        this.onRequestSent = onRequestSent;
        this.model = new JobsModel();
        this.table = new JTable(model);
        setOpaque(false);
        setBorder(Ui.padding(14, 16, 16, 16));
        scanButton = Ui.button("Scan now", Ui.Kind.PRIMARY, this::scan);

        add(filters(), BorderLayout.NORTH);

        Ui.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableRowSorter<JobsModel> sorter = new TableRowSorter<>(model);
        sorter.setComparator(0, Comparator.naturalOrder());
        if (mode == Mode.SEEKER) {
            sorter.setComparator(6, Comparator.comparingInt((ReferralCell c) -> c.status() != null ? 2 : c.referrers() > 0 ? 1 : 0));
        }
        sorter.setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.DESCENDING)));
        table.setRowSorter(sorter);
        table.getColumnModel().getColumn(0).setCellRenderer(new AgeRenderer());
        if (mode == Mode.SEEKER) {
            table.getColumnModel().getColumn(6).setCellRenderer(new ReferralRenderer());
        }
        sizeColumns();
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDetail(selected());
            }
        });

        detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
        detail.setOpaque(false);
        Ui.Card detailCard = new Ui.Card(new BorderLayout());
        detailCard.add(Ui.bareScroll(detail), BorderLayout.CENTER);
        detailCard.setMinimumSize(new Dimension(320, 200));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, Ui.scroll(table), detailCard);
        split.setResizeWeight(1);
        split.setDividerSize(10);
        split.setBorder(null);
        split.setOpaque(false);
        split.setDividerLocation(820);
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
        for (JComboBox<String> box : List.of(role, type, city, window, company)) {
            box.setFont(Theme.BODY);
            box.addActionListener(e -> {
                if (!refillingCompanies) {
                    applyFilters();
                }
            });
        }
        search.setPreferredSize(new Dimension(200, 34));
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                applyFilters();
            }

            public void removeUpdate(DocumentEvent e) {
                applyFilters();
            }

            public void changedUpdate(DocumentEvent e) {
                applyFilters();
            }
        });
        onlyWithReferrer.setOpaque(false);
        onlyWithReferrer.setFont(Theme.BODY);
        onlyWithReferrer.addActionListener(e -> applyFilters());

        Ui.Card bar = new Ui.Card(new BorderLayout(0, 10));
        bar.setBorder(Ui.padding(12, 14, 12, 14));
        JPanel top = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 0));
        top.setOpaque(false);
        top.add(search);
        if (mode == Mode.SEEKER) {
            top.add(company);
        }
        top.add(role);
        top.add(type);
        top.add(city);
        top.add(window);
        if (mode == Mode.SEEKER) {
            top.add(onlyWithReferrer);
        }
        bar.add(top, BorderLayout.NORTH);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(countLabel, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        progress.setIndeterminate(true);
        progress.setVisible(false);
        progress.setPreferredSize(new Dimension(120, 8));
        right.add(scanLabel);
        right.add(progress);
        right.add(scanButton);
        bottom.add(right, BorderLayout.EAST);
        bar.add(bottom, BorderLayout.SOUTH);
        return bar;
    }

    // ---------------------------------------------------------------- data

    @Override
    public void refreshData() {
        if (mode == Mode.SEEKER) {
            referrerCounts = app.referrals.referrerCountsByCompany();
            myStatuses = app.referrals.latestStatusByJob(account.id());
            refillCompanies();
        }
        applyFilters();
        updateScanLabel();
    }

    /** Keeps the company list in step with the companies that currently have openings. */
    private void refillCompanies() {
        List<String> names = app.jobs.jobs().stream().map(JobPosting::company).distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        if (names.equals(companyNames)) {
            return;
        }
        companyNames = names;
        Object chosen = company.getSelectedItem();
        refillingCompanies = true;
        try {
            company.removeAllItems();
            company.addItem(ALL_COMPANIES);
            names.forEach(company::addItem);
            if (chosen != null && (ALL_COMPANIES.equals(chosen) || names.contains(chosen))) {
                company.setSelectedItem(chosen);
            }
        } finally {
            refillingCompanies = false;
        }
    }

    /** Shows every recent opening at one company (used by the Companies tab). */
    void showCompany(String name) {
        refillCompanies();
        refillingCompanies = true;
        try {
            if (!companyNames.contains(name)) {
                company.addItem(name);
            }
            company.setSelectedItem(name);
            role.setSelectedIndex(0);
            type.setSelectedIndex(0);
            city.setSelectedIndex(0);
            window.setSelectedIndex(WINDOWS.length - 1);
            onlyWithReferrer.setSelected(false);
            search.setText("");
        } finally {
            refillingCompanies = false;
        }
        applyFilters();
    }

    private void applyFilters() {
        String keep = Optional.ofNullable(selected()).map(JobPosting::id).orElse(null);
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofDays(WINDOW_DAYS[Math.max(0, window.getSelectedIndex())]));
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        String roleChoice = (String) role.getSelectedItem();
        String typeChoice = (String) type.getSelectedItem();
        String cityChoice = (String) city.getSelectedItem();
        String companyChoice = (String) company.getSelectedItem();

        List<JobPosting> rows = new ArrayList<>();
        for (JobPosting j : app.jobs.jobs()) {
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
            if (!ALL_CITIES.equals(cityChoice) && !j.city().contains(cityChoice)) {
                continue;
            }
            if (mode == Mode.SEEKER && companyChoice != null && !ALL_COMPANIES.equals(companyChoice)
                    && !j.company().equals(companyChoice)) {
                continue;
            }
            if (!q.isEmpty() && !(j.title() + " " + j.company() + " " + j.location()).toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            if (mode == Mode.SEEKER && onlyWithReferrer.isSelected() && referrerCounts.getOrDefault(j.companyKey(), 0) == 0) {
                continue;
            }
            rows.add(j);
        }
        model.setRows(rows);
        long withReferrer = rows.stream().filter(j -> referrerCounts.getOrDefault(j.companyKey(), 0) > 0).count();
        long interns = rows.stream().filter(JobPosting::internship).count();
        countLabel.setText(rows.size() + (rows.size() == 1 ? " opening" : " openings")
                + (interns > 0 ? "  ·  " + interns + " internships" : "")
                + (mode == Mode.SEEKER ? "  ·  " + withReferrer + " with a referrer" : ""));

        int restore = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(keep)) {
                restore = table.convertRowIndexToView(i);
            }
        }
        if (restore < 0 && !rows.isEmpty()) {
            restore = 0;
        }
        if (restore >= 0) {
            table.setRowSelectionInterval(restore, restore);
            table.scrollRectToVisible(table.getCellRect(restore, 0, true));
        }
        showDetail(selected());
    }

    private JobPosting selected() {
        int view = table.getSelectedRow();
        return view < 0 ? null : model.rows.get(table.convertRowIndexToModel(view));
    }

    private void updateScanLabel() {
        if (scanning) {
            return;
        }
        Instant last = app.jobs.lastScanAt();
        int failures = app.jobs.lastScanFailures().size();
        scanLabel.setText(last == null
                ? "Not scanned yet"
                : "Last scan " + Ui.ago(last) + " · " + app.jobs.lastScanBoards() + " company boards"
                + (failures > 0 ? " (" + failures + " unreachable)" : ""));
    }

    // ---------------------------------------------------------------- scanning

    /** Scans in the background if the last scan is older than {@link com.referralconnect.service.JobService#STALE_AFTER}. */
    void scanIfStale() {
        if (app.jobs.isStale(Instant.now())) {
            scan();
        }
    }

    void scan() {
        if (scanning) {
            return;
        }
        scanning = true;
        scanButton.setEnabled(false);
        progress.setVisible(true);
        scanLabel.setText("Starting scan…");
        new SwingWorker<JobScanner.ScanReport, String>() {
            @Override
            protected JobScanner.ScanReport doInBackground() {
                return app.jobs.scanNow(this::publish);
            }

            @Override
            protected void process(List<String> lines) {
                scanLabel.setText(lines.get(lines.size() - 1));
            }

            @Override
            protected void done() {
                scanning = false;
                scanButton.setEnabled(true);
                progress.setVisible(false);
                try {
                    get();
                } catch (Exception e) {
                    Ui.error(JobsPanel.this, e.getCause() != null ? e.getCause() : e);
                }
                refreshData();
            }
        }.execute();
    }

    // ---------------------------------------------------------------- detail pane

    private void showDetail(JobPosting job) {
        detail.removeAll();
        if (job == null) {
            detail.add(Ui.label("No openings match these filters.", Theme.H3, Theme.TEXT));
            detail.add(Box.createVerticalStrut(8));
            detail.add(Ui.text(app.jobs.lastScanAt() == null
                    ? "Click \"Scan now\" to fetch recent openings from company job boards."
                    : "Try a longer time window, another city or role, or clear the search.", Theme.BODY, Theme.MUTED));
            detail.revalidate();
            detail.repaint();
            return;
        }
        detail.add(Ui.text(job.title(), Theme.H2, Theme.TEXT));
        detail.add(Box.createVerticalStrut(6));
        detail.add(Ui.text(job.company() + "  ·  " + job.location(), Theme.BODY, Theme.MUTED));
        detail.add(Box.createVerticalStrut(12));
        detail.add(Ui.row(6,
                new Ui.Pill(job.category().label(), Theme.PRIMARY_DARK, Theme.PRIMARY_SOFT),
                new Ui.Pill(job.typeLabel(), job.internship() ? Theme.WARNING : Theme.SUCCESS,
                        job.internship() ? Theme.WARNING_SOFT : Theme.SUCCESS_SOFT)));
        detail.add(Box.createVerticalStrut(14));
        if (job.dateKnown()) {
            detail.add(fact("Posted", Ui.ago(job.postedAt()) + " (" + Ui.date(job.postedAt()) + ")"));
        } else {
            detail.add(fact("Seen", "first seen " + Ui.ago(job.postedAt()) + " — " + job.company()
                    + " doesn't publish posting dates"));
        }
        detail.add(fact("City", job.city()));
        detail.add(fact("Source", job.company() + " careers board via " + job.source().label()));
        detail.add(Box.createVerticalStrut(16));

        if (mode == Mode.SEEKER) {
            addReferralSection(job);
        } else {
            detail.add(Ui.text("Seekers browsing this opening can send you a referral request; it will "
                    + "appear in your Referral inbox.", Theme.BODY, Theme.MUTED));
            detail.add(Box.createVerticalStrut(14));
            detail.add(Ui.row(8,
                    Ui.button("Open job posting", Ui.Kind.PRIMARY, () -> Ui.openUrl(this, job.url())),
                    Ui.button("Copy link", Ui.Kind.SECONDARY, () -> Ui.copy(job.url()))));
        }
        detail.revalidate();
        detail.repaint();
    }

    private void addReferralSection(JobPosting job) {
        int referrers = referrerCounts.getOrDefault(job.companyKey(), 0);
        RequestStatus status = myStatuses.get(job.id());
        detail.add(Ui.label("Referral", Theme.H3, Theme.TEXT));
        detail.add(Box.createVerticalStrut(6));
        boolean canRequest = referrers > 0 && (status == null
                || status == RequestStatus.DECLINED || status == RequestStatus.WITHDRAWN);
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
            detail.add(Ui.text(referrers + (referrers == 1 ? " employee" : " employees") + " at " + job.company()
                    + (referrers == 1 ? " is" : " are") + " accepting referral requests. Your profile, resume "
                    + "and pitch go to the one with the fewest pending requests.", Theme.BODY, Theme.MUTED));
        } else {
            detail.add(Ui.text("No one from " + job.company() + " has signed up as a referrer yet. Know someone "
                    + "there? Ask them to create a referrer account, then come back.", Theme.BODY, Theme.MUTED));
        }
        detail.add(Box.createVerticalStrut(14));
        Ui.FlatButton request = Ui.button(status == RequestStatus.DECLINED || status == RequestStatus.WITHDRAWN
                ? "Request again" : "Get referral", Ui.Kind.PRIMARY, () -> {
            ReferralRequest sent = ReferralRequestDialog.compose(this, app, account, job);
            if (sent != null) {
                refreshData();
                onRequestSent.run();
                Ui.info(this, "Request " + sent.id() + " sent to " + sent.referrerTitle()
                        + ".\nTrack it under \"My referral requests\".");
            }
        });
        request.setEnabled(canRequest);
        if (!canRequest) {
            request.setToolTipText(referrers == 0 ? "No referrer at this company yet" : "You already requested this one");
        }
        detail.add(Ui.row(8, request, Ui.button("Open job posting", Ui.Kind.SECONDARY,
                () -> Ui.openUrl(this, job.url()))));
    }

    private static JComponent fact(String label, String value) {
        JPanel p = Ui.flexRow(new BorderLayout(10, 0));
        JLabel l = Ui.label(label, Theme.SMALL_BOLD, Theme.MUTED);
        l.setPreferredSize(new Dimension(58, 18));
        p.add(l, BorderLayout.WEST);
        p.add(Ui.text(value, Theme.SMALL, Theme.TEXT), BorderLayout.CENTER);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setBorder(Ui.padding(0, 0, 6, 0));
        return p;
    }

    private void sizeColumns() {
        int[] widths = mode == Mode.SEEKER
                ? new int[]{82, 105, 290, 110, 80, 120, 130}
                : new int[]{82, 400, 120, 90, 160};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    // ---------------------------------------------------------------- table plumbing

    record ReferralCell(int referrers, RequestStatus status) {
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
            return mode == Mode.SEEKER ? 7 : 5;
        }

        @Override
        public String getColumnName(int c) {
            String[] seeker = {"Posted", "Company", "Role", "Category", "Type", "City", "Referral"};
            String[] company = {"Posted", "Role", "Category", "Type", "City"};
            return (mode == Mode.SEEKER ? seeker : company)[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            if (c == 0) {
                return Posted.class;
            }
            return mode == Mode.SEEKER && c == 6 ? ReferralCell.class : String.class;
        }

        @Override
        public Object getValueAt(int r, int c) {
            JobPosting j = rows.get(r);
            if (mode == Mode.COMPANY) {
                return switch (c) {
                    case 0 -> new Posted(j.postedAt(), j.dateKnown());
                    case 1 -> j.title();
                    case 2 -> j.category().shortLabel();
                    case 3 -> j.typeLabel();
                    default -> j.city();
                };
            }
            return switch (c) {
                case 0 -> new Posted(j.postedAt(), j.dateKnown());
                case 1 -> j.company();
                case 2 -> j.title();
                case 3 -> j.category().shortLabel();
                case 4 -> j.typeLabel();
                case 5 -> j.city();
                default -> new ReferralCell(referrerCounts.getOrDefault(j.companyKey(), 0), myStatuses.get(j.id()));
            };
        }
    }

    private static final class AgeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            Component base = t.getDefaultRenderer(Object.class)
                    .getTableCellRendererComponent(t, v instanceof Posted p
                            ? (p.known() ? "" : "~") + Ui.agoShort(p.at()) : v, sel, focus, r, c);
            ((JLabel) base).setForeground(Theme.MUTED);
            ((JLabel) base).setToolTipText(v instanceof Posted p && !p.known()
                    ? "First seen by this app — the company doesn't publish posting dates" : null);
            return base;
        }
    }

    private static final class ReferralRenderer extends DefaultTableCellRenderer {
        private final Ui.Pill pill = new Ui.Pill("", Theme.TEXT, Theme.NEUTRAL_SOFT);
        private final JLabel none = Ui.label("No referrer yet", Theme.SMALL, new java.awt.Color(0x94A3B8));
        private final JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 7));

        ReferralRenderer() {
            none.setBorder(Ui.padding(3, 2, 0, 0));
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            holder.removeAll();
            holder.setBackground(sel ? Theme.SELECTION : (r % 2 == 0 ? Theme.SURFACE : Theme.ROW_ALT));
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
                holder.add(none);
            }
            return holder;
        }
    }
}
