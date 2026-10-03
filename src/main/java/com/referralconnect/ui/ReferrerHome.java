package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.model.JobPosting;
import com.referralconnect.model.ReferralRequest;
import com.referralconnect.model.RequestStatus;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.ReferralService;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** The referrer's start page: what's waiting, how quickly they answer, and their company's openings. */
final class ReferrerHome extends JPanel implements AppFrame.Live {

    private final AppServices app;
    private final Account account;
    private final Consumer<String> openRequest;
    private final Consumer<String> openPage;
    private final JPanel body = new ScrollablePanel(null);
    private final JLabel greeting = Ui.label(" ", Theme.font(java.awt.Font.BOLD, 22), Color.WHITE);
    private final Ui.WrapText greetingLine = Ui.text(" ", Theme.BODY, new Color(0xE0E7FF));
    private final Widgets.StatCard pending;
    private final Widgets.StatCard referred;
    private final Widgets.StatCard rate;
    private final Widgets.StatCard speed;
    private final Charts.ColumnChart weekly = new Charts.ColumnChart();
    private final Charts.Donut outcomes = new Charts.Donut();
    private final JPanel waiting = new JPanel();
    private final Charts.BarChart roles = new Charts.BarChart();

    ReferrerHome(AppServices app, Account account, Consumer<String> openRequest, Consumer<String> openPage) {
        super(new BorderLayout());
        this.app = app;
        this.account = account;
        this.openRequest = openRequest;
        this.openPage = openPage;
        setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(Ui.padding(4, 24, 24, 24));

        pending = new Widgets.StatCard("Waiting for you", Icons.Glyph.INBOX, Theme.CHART[3], () -> openPage.accept("inbox"));
        pending.setName("statPending");
        referred = new Widgets.StatCard("Candidates referred", Icons.Glyph.AWARD, Theme.CHART[2], null);
        rate = new Widgets.StatCard("Response rate", Icons.Glyph.TARGET, Theme.PRIMARY, null);
        speed = new Widgets.StatCard("Typical response time", Icons.Glyph.CLOCK, Theme.CHART[1], null);

        body.add(banner());
        body.add(Box.createVerticalStrut(16));
        JPanel stats = new JPanel(new GridLayout(1, 4, 14, 0));
        stats.setOpaque(false);
        stats.add(pending);
        stats.add(referred);
        stats.add(rate);
        stats.add(speed);
        body.add(stretch(stats));
        body.add(Box.createVerticalStrut(16));

        JPanel row = new JPanel(new GridLayout(1, 2, 14, 0));
        row.setOpaque(false);
        row.add(card("Requests per week", "The last 8 weeks", weekly));
        row.add(card("Outcomes", "Everything you've received", outcomes));
        body.add(stretch(row));
        body.add(Box.createVerticalStrut(16));

        JPanel row2 = new JPanel(new GridLayout(1, 2, 14, 0));
        row2.setOpaque(false);
        waiting.setOpaque(false);
        waiting.setLayout(new BoxLayout(waiting, BoxLayout.Y_AXIS));
        row2.add(card("Waiting longest", "Oldest pending requests first", waiting));
        row2.add(card("Openings at " + account.companyName(), "By role, last 30 days", roles));
        roles.onClick(b -> openPage.accept("openings"));
        body.add(stretch(row2));
        body.add(Box.createVerticalGlue());
        add(Ui.bareScroll(body), BorderLayout.CENTER);
        refreshData();
    }

    private JComponent banner() {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Laf.smooth(g);
                g2.setPaint(new GradientPaint(0, 0, new Color(0x064E3B), getWidth(), getHeight(), new Color(0x0F766E)));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.setColor(new Color(255, 255, 255, 18));
                g2.fillOval(getWidth() - 220, -70, 260, 260);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(Ui.padding(22, 26, 22, 26));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(greeting);
        text.add(Box.createVerticalStrut(6));
        text.add(greetingLine);
        p.add(text, BorderLayout.CENTER);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(Ui.button("Open inbox", Icons.Glyph.INBOX, Ui.Kind.GHOST, () -> openPage.accept("inbox")),
                BorderLayout.CENTER);
        p.add(holder, BorderLayout.EAST);
        return stretch(p);
    }

    private static JComponent card(String title, String subtitle, JComponent content) {
        Ui.Card card = new Ui.Card(new BorderLayout(0, 12));
        card.setBorder(Ui.padding(16, 18, 16, 18));
        card.add(SeekerHome.cardHead(title, subtitle, null), BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    @Override
    public void refreshData() {
        ReferralService.ReferrerStats s = app.referrals.stats(account.id());
        Account me = app.auth.require(account.id());
        int hour = LocalTime.now().getHour();
        greeting.setText((hour < 12 ? "Good morning, " : hour < 17 ? "Good afternoon, " : "Good evening, ")
                + me.name().split("\\s+")[0]);
        long messages = app.referrals.unansweredMessages(account.id());
        greetingLine.setText((s.pending() == 0 ? "No requests waiting" : s.pending() + (s.pending() == 1
                ? " request is" : " requests are") + " waiting for you")
                + (messages > 0 ? " · " + messages + " with new messages" : "")
                + (me.acceptingRequests() ? " · you're accepting new requests" : " · new requests are paused"));
        pending.set(s.pending() + s.needsInfo(), s.needsInfo() + " waiting on the candidate");
        referred.set(s.referred(), s.total() + " requests received in total");
        rate.set(s.total() == 0 ? "—" : s.responseRate() + "%", "of requests got an answer");
        long h = s.medianResponseHours();
        speed.set(h < 0 ? "—" : h < 48 ? h + " h" : (h / 24) + " days",
                h < 0 ? "no answers yet" : "median time to your first answer");

        List<Charts.Bar> weeks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            weeks.add(new Charts.Bar(i == 7 ? "This wk" : (7 - i) + "w ago", s.perWeek()[i]));
        }
        weekly.setBars(weeks);
        outcomes.setSlices(List.of(
                new Charts.Bar("Pending", s.pending(), Theme.INFO),
                new Charts.Bar("Needs info", s.needsInfo(), Theme.WARNING),
                new Charts.Bar("Referred", s.referred(), Theme.SUCCESS),
                new Charts.Bar("Declined", s.declined(), Theme.DANGER),
                new Charts.Bar("Withdrawn", s.withdrawn(), Theme.FAINT)), "requests");

        waiting.removeAll();
        List<ReferralRequest> open = app.referrals.inbox(account.id()).stream()
                .filter(r -> r.status() == RequestStatus.PENDING)
                .sorted(Comparator.comparing(ReferralRequest::createdAt))
                .limit(5)
                .toList();
        if (open.isEmpty()) {
            waiting.add(Ui.text("You're all caught up.", Theme.BODY, Theme.MUTED));
        }
        for (ReferralRequest r : open) {
            waiting.add(waitingRow(r));
        }
        waiting.revalidate();

        List<JobPosting> jobs = app.jobs.jobs().stream().filter(j -> j.companyKey().equals(account.companyKey())).toList();
        roles.setBars(Charts.top(jobs.stream().collect(java.util.stream.Collectors.groupingBy(
                j -> j.category().label(), java.util.stream.Collectors.counting())), 6));
        body.revalidate();
        body.repaint();
    }

    private JComponent waitingRow(ReferralRequest r) {
        JPanel row = Ui.flexRow(new BorderLayout(10, 0));
        row.setBorder(Ui.padding(6, 0, 6, 0));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.add(new JLabel(new Ui.Avatar(r.candidate().name(), 32, true)), BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(Ui.label(r.candidate().name(), Theme.BODY_BOLD, Theme.TEXT));
        text.add(Ui.text(r.job().title(), Theme.SMALL, Theme.MUTED).maxLines(1));
        row.add(text, BorderLayout.CENTER);
        row.add(Ui.label(Ui.agoShort(r.createdAt()), Theme.SMALL, Theme.FAINT), BorderLayout.EAST);
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openRequest.accept(r.id());
            }
        });
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        return row;
    }

    private static JComponent stretch(JComponent c) {
        JPanel p = Ui.flexRow(new BorderLayout());
        p.add(c, BorderLayout.CENTER);
        return p;
    }
}
