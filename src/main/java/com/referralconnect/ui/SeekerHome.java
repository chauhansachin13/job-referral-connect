package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.model.CandidateProfile;
import com.referralconnect.model.JobCategory;
import com.referralconnect.model.JobPosting;
import com.referralconnect.model.ReferralRequest;
import com.referralconnect.model.RequestStatus;
import com.referralconnect.model.TrackedJob.Stage;
import com.referralconnect.model.UserPrefs;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.JobMatcher;
import com.referralconnect.service.NotificationService;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The seeker's start page: what's new, what fits, where their applications stand. */
final class SeekerHome extends JPanel implements AppFrame.Live {

    /** Where the home page's cards and charts lead. */
    interface Nav {
        void openings(JobsPanel.Preset preset);

        void openJob(String jobId);

        void page(String pageId);

        void notification(NotificationService.Notification n);
    }

    private final AppServices app;
    private final Account account;
    private final Nav nav;
    private final JPanel body = new ScrollablePanel(null);

    private final JLabel greeting = Ui.label(" ", Theme.font(java.awt.Font.BOLD, 22), Color.WHITE);
    private final Ui.WrapText greetingLine = Ui.text(" ", Theme.BODY, new Color(0xE0E7FF));
    private final Widgets.StatCard newCard;
    private final Widgets.StatCard fresherCard;
    private final Widgets.StatCard referrerCard;
    private final Widgets.StatCard requestsCard;
    private final JPanel matchesList = new JPanel();
    private final Charts.Ring strength = new Charts.Ring(76);
    private final JPanel strengthText = new JPanel();
    private final JPanel pipeline = new JPanel();
    private final Charts.BarChart byRole = new Charts.BarChart();
    private final Charts.BarChart byCompany = new Charts.BarChart();
    private final Charts.BarChart byCity = new Charts.BarChart();
    private final JPanel activity = new JPanel();

    SeekerHome(AppServices app, Account account, Nav nav) {
        super(new BorderLayout());
        this.app = app;
        this.account = account;
        this.nav = nav;
        setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(Ui.padding(4, 24, 24, 24));
        body.setName("home");

        newCard = new Widgets.StatCard("New since your last visit", Icons.Glyph.SPARKLE, Theme.CHART[4],
                () -> nav.openings(new JobsPanel.Preset("", null, null, null, "", -1, 30, true, false,
                        JobsPanel.Sort.NEWEST)));
        newCard.setName("statNew");
        fresherCard = new Widgets.StatCard("Open to freshers & students", Icons.Glyph.CAP, Theme.CHART[2],
                () -> nav.openings(new JobsPanel.Preset("", null, null, null, "", 0, 30, false, false,
                        JobsPanel.Sort.MATCH)));
        fresherCard.setName("statFresher");
        referrerCard = new Widgets.StatCard("Openings with a referrer", Icons.Glyph.USERS, Theme.PRIMARY,
                () -> nav.openings(new JobsPanel.Preset("", null, null, null, "", -1, 30, false, true,
                        JobsPanel.Sort.MATCH)));
        referrerCard.setName("statReferrer");
        requestsCard = new Widgets.StatCard("Referral requests open", Icons.Glyph.SEND, Theme.CHART[3],
                () -> nav.page("requests"));
        requestsCard.setName("statRequests");

        body.add(banner());
        body.add(Box.createVerticalStrut(16));
        JPanel stats = new JPanel(new GridLayout(1, 4, 14, 0));
        stats.setOpaque(false);
        stats.add(newCard);
        stats.add(fresherCard);
        stats.add(referrerCard);
        stats.add(requestsCard);
        body.add(stretch(stats));
        body.add(Box.createVerticalStrut(16));

        JPanel middle = new JPanel(new GridBagLayout());
        middle.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.gridy = 0;
        c.gridx = 0;
        c.weightx = 0.62;
        c.weighty = 1;
        c.gridheight = 2;
        c.insets = new Insets(0, 0, 0, 14);
        middle.add(matchesCard(), c);
        c.gridx = 1;
        c.weightx = 0.38;
        c.gridheight = 1;
        c.insets = new Insets(0, 0, 14, 0);
        middle.add(strengthCard(), c);
        c.gridy = 1;
        c.insets = new Insets(0, 0, 0, 0);
        middle.add(pipelineCard(), c);
        body.add(stretch(middle));
        body.add(Box.createVerticalStrut(16));

        JPanel charts = new JPanel(new GridLayout(1, 3, 14, 0));
        charts.setOpaque(false);
        charts.add(chartCard("Openings by role", "Last 30 days · click a bar to browse", byRole));
        charts.add(chartCard("Top hiring companies", "Most India openings right now", byCompany));
        charts.add(chartCard("Where the jobs are", "Openings by city", byCity));
        byRole.onClick(b -> {
            for (JobCategory cat : JobCategory.values()) {
                if (cat.label().equals(b.label())) {
                    nav.openings(new JobsPanel.Preset("", null, cat, null, "", -1, 30, false, false, JobsPanel.Sort.MATCH));
                }
            }
        });
        byCompany.onClick(b -> nav.openings(new JobsPanel.Preset("", b.label(), null, null, "", -1, 30, false, false,
                JobsPanel.Sort.NEWEST)));
        byCity.onClick(b -> nav.openings(new JobsPanel.Preset("", null, null, null, b.label(), -1, 30, false, false,
                JobsPanel.Sort.MATCH)));
        body.add(stretch(charts));
        body.add(Box.createVerticalStrut(16));
        body.add(stretch(activityCard()));
        body.add(Box.createVerticalGlue());

        add(Ui.bareScroll(body), BorderLayout.CENTER);
        refreshData();
    }

    // ---------------------------------------------------------------- layout

    private JComponent banner() {
        JPanel p = new JPanel(new BorderLayout(20, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Laf.smooth(g);
                g2.setPaint(new GradientPaint(0, 0, new Color(0x312E81), getWidth(), getHeight(), new Color(0x6D28D9)));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.setColor(new Color(255, 255, 255, 18));
                g2.fillOval(getWidth() - 200, -80, 260, 260);
                g2.fillOval(getWidth() - 330, 40, 160, 160);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(Ui.padding(22, 26, 22, 26));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        greeting.setName("greeting");
        text.add(greeting);
        text.add(Box.createVerticalStrut(6));
        text.add(greetingLine);
        p.add(text, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.setOpaque(false);
        Ui.FlatButton browse = Ui.button("Browse openings", Icons.Glyph.BRIEFCASE, Ui.Kind.GHOST,
                () -> nav.page("openings"));
        buttons.add(browse);
        Ui.FlatButton best = new Ui.FlatButton("Best matches", Ui.Kind.PRIMARY) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Laf.smooth(g);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 11, 11);
                g2.setFont(getFont());
                g2.setColor(new Color(0x3730A3));
                Icons.get(Icons.Glyph.TARGET, 17, new Color(0x3730A3)).paintIcon(this, g2, 16, (getHeight() - 17) / 2);
                java.awt.FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), 40, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(getFontMetrics(getFont()).stringWidth(getText()) + 56, 36);
            }
        };
        best.addActionListener(e -> nav.openings(new JobsPanel.Preset("", null, null, null, "", -1, 30, false, false,
                JobsPanel.Sort.MATCH)));
        buttons.add(best);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(buttons, BorderLayout.CENTER);
        p.add(holder, BorderLayout.EAST);
        return stretch(p);
    }

    private JComponent matchesCard() {
        Ui.Card card = new Ui.Card(new BorderLayout(0, 10));
        card.setBorder(Ui.padding(16, 18, 14, 18));
        card.add(cardHead("Top matches for you", "Based on your skills, experience and preferences",
                Ui.button("See all", Icons.Glyph.CHEVRON_RIGHT, Ui.Kind.SUBTLE, () -> nav.openings(
                        new JobsPanel.Preset("", null, null, null, "", -1, 30, false, false, JobsPanel.Sort.MATCH)))
                        .compact()), BorderLayout.NORTH);
        matchesList.setOpaque(false);
        matchesList.setLayout(new BoxLayout(matchesList, BoxLayout.Y_AXIS));
        matchesList.setName("topMatches");
        card.add(matchesList, BorderLayout.CENTER);
        return card;
    }

    private JComponent strengthCard() {
        Ui.Card card = new Ui.Card(new BorderLayout(16, 0));
        card.setBorder(Ui.padding(16, 18, 16, 18));
        JPanel ringHolder = new JPanel(new BorderLayout());
        ringHolder.setOpaque(false);
        ringHolder.add(strength, BorderLayout.NORTH);
        card.add(ringHolder, BorderLayout.WEST);
        strengthText.setOpaque(false);
        strengthText.setLayout(new BoxLayout(strengthText, BoxLayout.Y_AXIS));
        card.add(strengthText, BorderLayout.CENTER);
        return card;
    }

    private JComponent pipelineCard() {
        Ui.Card card = new Ui.Card(new BorderLayout(0, 10));
        card.setBorder(Ui.padding(16, 18, 16, 18));
        card.add(cardHead("Your pipeline", "Saved and applied jobs, and referral requests",
                Ui.button("Open", Icons.Glyph.CHEVRON_RIGHT, Ui.Kind.SUBTLE, () -> nav.page("tracker")).compact()),
                BorderLayout.NORTH);
        pipeline.setOpaque(false);
        pipeline.setLayout(new BoxLayout(pipeline, BoxLayout.Y_AXIS));
        card.add(pipeline, BorderLayout.CENTER);
        return card;
    }

    private JComponent chartCard(String title, String subtitle, JComponent chart) {
        Ui.Card card = new Ui.Card(new BorderLayout(0, 12));
        card.setBorder(Ui.padding(16, 18, 16, 18));
        card.add(cardHead(title, subtitle, null), BorderLayout.NORTH);
        card.add(chart, BorderLayout.CENTER);
        return card;
    }

    private JComponent activityCard() {
        Ui.Card card = new Ui.Card(new BorderLayout(0, 10));
        card.setBorder(Ui.padding(16, 18, 14, 18));
        card.add(cardHead("Recent activity", "Updates on your requests, messages and alerts", null), BorderLayout.NORTH);
        activity.setOpaque(false);
        activity.setLayout(new BoxLayout(activity, BoxLayout.Y_AXIS));
        card.add(activity, BorderLayout.CENTER);
        return card;
    }

    static JComponent cardHead(String title, String subtitle, JComponent action) {
        JPanel head = Ui.flexRow(new BorderLayout(10, 0));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(Ui.label(title, Theme.H3, Theme.TEXT));
        if (subtitle != null) {
            text.add(Box.createVerticalStrut(2));
            text.add(Ui.text(subtitle, Theme.SMALL, Theme.MUTED));
        }
        head.add(text, BorderLayout.CENTER);
        if (action != null) {
            JPanel holder = new JPanel(new BorderLayout());
            holder.setOpaque(false);
            holder.add(action, BorderLayout.NORTH);
            head.add(holder, BorderLayout.EAST);
        }
        return head;
    }

    // ---------------------------------------------------------------- data

    @Override
    public void refreshData() {
        Account me = app.auth.require(account.id());
        CandidateProfile profile = me.profile();
        UserPrefs prefs = app.prefs.of(account.id());
        List<JobPosting> jobs = app.jobs.jobs();
        Map<String, Instant> firstSeen = app.jobs.firstSeen();
        Map<String, Integer> referrers = app.referrals.referrerCountsByCompany();
        Set<String> hidden = prefs.hiddenJobIds();
        List<ReferralRequest> requests = app.referrals.forSeeker(account.id());
        Map<String, Stage> stages = app.tracker.stages(account.id());

        long fresh = jobs.stream().filter(j -> firstSeen.getOrDefault(j.id(), j.postedAt())
                .isAfter(prefs.previousVisitAt())).count();
        long freshers = jobs.stream().filter(j -> j.internship() && j.requirements().minYears() <= 0
                || j.requirements().minYears() == 0).count();
        long withRef = jobs.stream().filter(j -> referrers.getOrDefault(j.companyKey(), 0) > 0).count();
        long open = requests.stream().filter(r -> r.status().isOpen()).count();
        long referred = requests.stream().filter(r -> r.status() == RequestStatus.REFERRED).count();

        String first = profile.name().split("\\s+")[0];
        int hour = LocalTime.now().getHour();
        greeting.setText((hour < 12 ? "Good morning, " : hour < 17 ? "Good afternoon, " : "Good evening, ") + first);
        long fits = profile.yearsKnown() ? jobs.stream().filter(j -> j.requirements().known()
                && j.requirements().minYears() <= profile.years()).count() : -1;
        greetingLine.setText(app.jobs.lastScanAt() == null
                ? "Your first scan is collecting fresh openings from " + app.directory.all().size()
                + " companies — results appear here as they arrive."
                : String.format("%,d", jobs.size()) + " openings in India from the last 30 days · " + fresh
                + " new since your last visit" + (fits >= 0 ? " · " + String.format("%,d", fits)
                + " match your " + profile.yearsLabel().toLowerCase(java.util.Locale.ROOT) + " of experience" : ""));

        newCard.set(fresh, "found since " + Ui.ago(prefs.previousVisitAt()));
        fresherCard.set(freshers, "0 years or internships");
        referrerCard.set(withRef, referrers.size() + " companies have referrers");
        requestsCard.set(open, referred + " referred so far");

        // Top matches: recent, not hidden, not already saved or requested.
        Map<String, RequestStatus> statuses = app.referrals.latestStatusByJob(account.id());
        JobMatcher.Scorer scorer = JobMatcher.scorer(profile, prefs);
        Instant recent = Instant.now().minus(Duration.ofDays(14));
        record Scored(JobPosting job, JobMatcher.Match match) {
        }
        List<Scored> ranked = jobs.stream()
                .filter(j -> !hidden.contains(j.id()) && !stages.containsKey(j.id()) && !statuses.containsKey(j.id()))
                .filter(j -> j.postedAt().isAfter(recent))
                .map(j -> new Scored(j, scorer.match(j)))
                .sorted(Comparator.comparingInt((Scored s) -> -s.match().score())
                        .thenComparing(s -> s.job().postedAt(), Comparator.reverseOrder()))
                .toList();
        // At most two per company, so one big employer doesn't fill the list.
        Map<String, Integer> perCompany = new java.util.HashMap<>();
        List<Scored> top = new java.util.ArrayList<>();
        for (Scored s : ranked) {
            if (top.size() == 6) {
                break;
            }
            if (perCompany.merge(s.job().company(), 1, Integer::sum) <= 2) {
                top.add(s);
            }
        }
        matchesList.removeAll();
        if (top.isEmpty()) {
            matchesList.add(Ui.text(jobs.isEmpty() ? "Openings will appear here after the first scan."
                    : "You've saved or requested every recent match. Check Openings for more.", Theme.BODY, Theme.MUTED));
        }
        for (Scored s : top) {
            matchesList.add(matchRow(s.job(), s.match(), referrers.getOrDefault(s.job().companyKey(), 0) > 0));
        }
        matchesList.revalidate();
        matchesList.repaint();

        // Profile strength.
        int pct = profile.completeness();
        strength.setValue(pct, pct >= 90 ? Theme.SUCCESS : pct >= 60 ? Theme.PRIMARY : Theme.WARNING);
        strengthText.removeAll();
        strengthText.add(Ui.label("Profile strength", Theme.H3, Theme.TEXT));
        strengthText.add(Box.createVerticalStrut(4));
        List<String> missing = new java.util.ArrayList<>(profile.missingForReferral());
        if (!profile.yearsKnown()) {
            missing.add("years of experience");
        }
        if (profile.linkedin().isEmpty()) {
            missing.add("LinkedIn");
        }
        strengthText.add(Ui.text(missing.isEmpty() ? "Everything a referrer needs is there."
                : "Add " + String.join(", ", missing) + " to get better matches and referrals.", Theme.SMALL, Theme.MUTED));
        strengthText.add(Box.createVerticalStrut(8));
        strengthText.add(Ui.row(0, Ui.button(missing.isEmpty() ? "View profile" : "Complete profile", Icons.Glyph.USER,
                missing.isEmpty() ? Ui.Kind.SECONDARY : Ui.Kind.PRIMARY, () -> nav.page("profile")).compact()));
        strengthText.revalidate();

        // Pipeline.
        Map<Stage, Long> counts = app.tracker.counts(account.id());
        Map<RequestStatus, Long> reqCounts = new EnumMap<>(RequestStatus.class);
        requests.forEach(r -> reqCounts.merge(r.status(), 1L, Long::sum));
        pipeline.removeAll();
        JPanel stageRow = new JPanel(new GridLayout(1, Stage.values().length, 6, 0));
        stageRow.setOpaque(false);
        for (Stage st : Stage.values()) {
            stageRow.add(miniStat(String.valueOf(counts.getOrDefault(st, 0L)), st.label(), Theme.stageColor(st)));
        }
        pipeline.add(stretch(stageRow));
        pipeline.add(Box.createVerticalStrut(10));
        JPanel reqRow = new JPanel(new GridLayout(1, 3, 6, 0));
        reqRow.setOpaque(false);
        reqRow.add(miniStat(String.valueOf(reqCounts.getOrDefault(RequestStatus.PENDING, 0L)
                + reqCounts.getOrDefault(RequestStatus.NEEDS_INFO, 0L)), "Requests waiting", Theme.INFO));
        reqRow.add(miniStat(String.valueOf(reqCounts.getOrDefault(RequestStatus.REFERRED, 0L)), "Referred", Theme.SUCCESS));
        reqRow.add(miniStat(String.valueOf(app.referrals.unansweredMessages(account.id())), "Unread chats", Theme.CHART[5]));
        pipeline.add(stretch(reqRow));
        pipeline.revalidate();

        // Charts.
        Instant month = Instant.now().minus(Duration.ofDays(30));
        List<JobPosting> lastMonth = jobs.stream().filter(j -> j.postedAt().isAfter(month)).toList();
        Map<String, Long> roles = lastMonth.stream().collect(Collectors.groupingBy(j -> j.category().label(),
                Collectors.counting()));
        byRole.setBars(Charts.top(roles, 6));
        byCompany.setBars(Charts.top(lastMonth.stream().collect(Collectors.groupingBy(JobPosting::company,
                Collectors.counting())), 6));
        Map<String, Long> cities = lastMonth.stream()
                .flatMap(j -> java.util.Arrays.stream(j.city().split("\\s*/\\s*")))
                .filter(s -> !s.isBlank() && !s.equalsIgnoreCase("India"))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        byCity.setBars(Charts.top(cities, 6));

        // Activity.
        activity.removeAll();
        List<NotificationService.Notification> notes = app.notifications.list(me);
        if (notes.isEmpty()) {
            activity.add(Ui.text("Nothing yet. When referrers reply or alerts find new openings, it shows up here.",
                    Theme.BODY, Theme.MUTED));
        }
        for (NotificationService.Notification n : notes.stream().limit(5).toList()) {
            activity.add(activityRow(n));
        }
        activity.revalidate();
        body.revalidate();
        body.repaint();
    }

    private JComponent matchRow(JobPosting j, JobMatcher.Match m, boolean hasReferrer) {
        JPanel row = new JPanel(new BorderLayout(12, 0)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setOpaque(false);
        row.setName("match-" + j.id());
        row.setBorder(Ui.padding(8, 0, 8, 0));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setToolTipText("Open in Openings");
        row.add(Ui.avatar(j.company(), 38), BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(Ui.text(j.title(), Theme.BODY_BOLD, Theme.TEXT).maxLines(1));
        text.add(Box.createVerticalStrut(2));
        JPanel meta = Ui.row(6, Ui.label(j.company() + " · " + j.city(), Theme.SMALL, Theme.MUTED), Ui.experiencePill(j));
        if (hasReferrer) {
            meta.add(new Ui.Pill("Referrer", Theme.SUCCESS, Theme.SUCCESS_SOFT).withIcon(Icons.Glyph.USERS));
        }
        text.add(meta);
        row.add(text, BorderLayout.CENTER);
        Charts.Ring ring = new Charts.Ring(40);
        ring.setValue(m.score(), Theme.matchColor(m.score()));
        ring.setToolTipText(m.label() + ": " + m.score() + "%");
        JPanel ringHolder = new JPanel(new BorderLayout());
        ringHolder.setOpaque(false);
        ringHolder.add(ring, BorderLayout.CENTER);
        row.add(ringHolder, BorderLayout.EAST);
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                nav.openJob(j.id());
            }
        });
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }

    private static JComponent miniStat(String value, String label, Color color) {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Laf.smooth(g);
                g2.setColor(Theme.alpha(color, Theme.isDark() ? 40 : 22));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(Ui.padding(8, 10, 8, 6));
        p.add(Ui.label(value, Theme.H3, Theme.TEXT));
        JLabel l = Ui.label(label, Theme.SMALL, Theme.MUTED);
        l.setToolTipText(label);
        p.add(l);
        return p;
    }

    private JComponent activityRow(NotificationService.Notification n) {
        JPanel row = Ui.flexRow(new BorderLayout(12, 0));
        row.setBorder(Ui.padding(7, 0, 7, 0));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Icons.Glyph glyph = switch (n.kind()) {
            case MESSAGE -> Icons.Glyph.MESSAGE;
            case ALERT -> Icons.Glyph.BELL;
            case REMINDER -> Icons.Glyph.CLOCK;
            case NEW_REQUEST -> Icons.Glyph.INBOX;
            case STATUS -> Icons.Glyph.SEND;
        };
        JLabel icon = new JLabel(Icons.get(glyph, 18, Theme.PRIMARY)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Laf.smooth(g);
                g2.setColor(Theme.PRIMARY_SOFT);
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        icon.setHorizontalAlignment(JLabel.CENTER);
        icon.setPreferredSize(new Dimension(34, 34));
        JPanel iconHolder = new JPanel(new BorderLayout());
        iconHolder.setOpaque(false);
        iconHolder.add(icon, BorderLayout.NORTH);
        row.add(iconHolder, BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(Ui.text(n.title(), n.unread() ? Theme.BODY_BOLD : Theme.BODY, Theme.TEXT).maxLines(1));
        text.add(Ui.text(n.body(), Theme.SMALL, Theme.MUTED).maxLines(1));
        row.add(text, BorderLayout.CENTER);
        row.add(Ui.label(Ui.ago(n.at()), Theme.SMALL, Theme.FAINT), BorderLayout.EAST);
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                nav.notification(n);
            }
        });
        return row;
    }

    private static JComponent stretch(JComponent c) {
        JPanel p = Ui.flexRow(new BorderLayout());
        p.add(c, BorderLayout.CENTER);
        return p;
    }
}
