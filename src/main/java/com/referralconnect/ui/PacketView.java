package com.referralconnect.ui;

import com.referralconnect.model.CandidateProfile;
import com.referralconnect.model.JobPosting;
import com.referralconnect.model.ReferralRequest;
import com.referralconnect.model.RequestStatus;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * Renders one referral request as the "referral packet": who the candidate is, how to reach
 * them, the job, their pitch and what has happened so far.
 */
final class PacketView extends ScrollablePanel {

    PacketView() {
        super(null);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(Ui.padding(0, 0, 0, 14));
    }

    void showEmpty(String title, String body) {
        removeAll();
        add(Ui.label(title, Theme.H3, Theme.TEXT));
        add(Box.createVerticalStrut(8));
        add(Ui.text(body, Theme.BODY, Theme.MUTED));
        revalidate();
        repaint();
    }

    /**
     * @param forReferrer true when the referrer is reading it (shows "received"), false for the
     *                    seeker's own copy (shows who it was sent to)
     * @param actions     buttons shown under the header
     */
    void show(ReferralRequest r, boolean forReferrer, List<JComponent> actions) {
        removeAll();
        CandidateProfile c = r.candidate();
        JobPosting j = r.job();

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.add(Ui.text(forReferrer ? c.name() : j.title(), Theme.H2, Theme.TEXT), BorderLayout.CENTER);
        JPanel pillHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 2));
        pillHolder.setOpaque(false);
        pillHolder.add(Ui.statusPill(r.status()));
        header.add(pillHolder, BorderLayout.EAST);
        add(stretch(header));
        add(Box.createVerticalStrut(4));
        String meta = r.id() + "  ·  " + (forReferrer ? "received " : "sent ") + Ui.ago(r.createdAt());
        if (!forReferrer) {
            meta += "  ·  to " + r.referrerTitle();
        } else if (!c.experience().isEmpty() || !c.education().isEmpty()) {
            meta = join(c.experience(), c.education()) + "\n" + meta;
        }
        add(Ui.text(meta, Theme.SMALL, Theme.MUTED));

        if (!actions.isEmpty()) {
            add(Box.createVerticalStrut(12));
            JPanel bar = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 4));
            bar.setOpaque(false);
            bar.setBorder(Ui.padding(0, -8, 0, 0));
            for (JComponent a : actions) {
                bar.add(a);
            }
            add(stretch(bar));
        }

        if (!r.referrerNote().isEmpty()) {
            add(Box.createVerticalStrut(12));
            add(callout((r.status() == RequestStatus.REFERRED ? "Referrer's note: " : "Referrer says: ")
                    + r.referrerNote(), Theme.statusColor(r.status()), Theme.statusSoft(r.status())));
        }

        section("Applying for");
        if (forReferrer) {
            add(Ui.text(j.title(), Theme.H3, Theme.TEXT));
            add(Box.createVerticalStrut(3));
        }
        add(Ui.text(j.company() + "  ·  " + j.location(), Theme.BODY, Theme.MUTED));
        add(Box.createVerticalStrut(6));
        add(Ui.row(6,
                new Ui.Pill(j.category().label(), Theme.PRIMARY_DARK, Theme.PRIMARY_SOFT),
                new Ui.Pill(j.typeLabel(), j.internship() ? Theme.WARNING : Theme.SUCCESS,
                        j.internship() ? Theme.WARNING_SOFT : Theme.SUCCESS_SOFT),
                Ui.label((j.dateKnown() ? "  posted " : "  first seen ") + Ui.date(j.postedAt()),
                        Theme.SMALL, Theme.MUTED)));
        add(Box.createVerticalStrut(6));
        add(linkRow("Job link", j.url()));

        section(forReferrer ? "Candidate" : "Details you sent");
        if (!forReferrer) {
            add(fact("Name", c.name()));
        }
        add(fact("Email", c.email()));
        add(fact("Phone", c.phone()));
        add(linkRow("Resume", c.resumeLink()));
        add(linkRow("LinkedIn", c.linkedin()));
        add(linkRow("GitHub", c.github()));
        add(fact("Education", c.education()));
        add(fact("Experience", c.experience()));
        add(fact("Skills", c.skills()));

        section("Why I'm a good fit");
        add(callout(r.pitch(), Theme.TEXT, Theme.ROW_ALT));

        section("Timeline");
        List<ReferralRequest.TimelineEntry> events = r.timeline();
        for (int i = events.size() - 1; i >= 0; i--) {
            ReferralRequest.TimelineEntry e = events.get(i);
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            JLabel when = Ui.label(Ui.dateTime(e.at()), Theme.SMALL, Theme.MUTED);
            when.setPreferredSize(new Dimension(96, 18));
            row.add(when, BorderLayout.WEST);
            row.add(Ui.text(e.text(), Theme.SMALL, Theme.TEXT), BorderLayout.CENTER);
            row.setBorder(Ui.padding(0, 0, 5, 0));
            add(stretch(row));
        }
        add(Box.createVerticalGlue());
        revalidate();
        repaint();
    }

    private void section(String title) {
        add(Box.createVerticalStrut(18));
        JLabel l = Ui.label(title.toUpperCase(), Theme.SMALL_BOLD, Theme.MUTED);
        add(l);
        add(Box.createVerticalStrut(6));
    }

    private static JComponent fact(String label, String value) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        JLabel l = Ui.label(label, Theme.SMALL_BOLD, Theme.MUTED);
        l.setPreferredSize(new Dimension(76, 18));
        p.add(l, BorderLayout.WEST);
        p.add(Ui.text(value.isEmpty() ? "—" : value, Theme.BODY, value.isEmpty() ? Theme.MUTED : Theme.TEXT),
                BorderLayout.CENTER);
        p.setBorder(Ui.padding(0, 0, 6, 0));
        return stretch(p);
    }

    private JComponent linkRow(String label, String url) {
        if (url == null || url.isBlank()) {
            return fact(label, "");
        }
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        JLabel l = Ui.label(label, Theme.SMALL_BOLD, Theme.MUTED);
        l.setPreferredSize(new Dimension(76, 18));
        p.add(l, BorderLayout.WEST);
        p.add(Ui.text(url, Theme.BODY, Theme.INFO), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.setOpaque(false);
        buttons.add(Ui.smallButton("Open", Ui.Kind.SECONDARY, () -> Ui.openUrl(this, url)));
        p.add(buttons, BorderLayout.EAST);
        p.setBorder(Ui.padding(0, 0, 6, 0));
        return stretch(p);
    }

    private static JComponent callout(String text, Color fg, Color bg) {
        JPanel box = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
            }
        };
        box.setOpaque(false);
        box.setBorder(Ui.padding(10, 12, 10, 12));
        box.add(Ui.text(text, Theme.BODY, fg), BorderLayout.CENTER);
        return stretch(box);
    }

    private static String join(String a, String b) {
        if (a.isEmpty()) {
            return b;
        }
        return b.isEmpty() ? a : a + "  ·  " + b;
    }

    /** Wraps a row so BoxLayout lets it grow sideways but never taller than it needs. */
    private static JComponent stretch(JComponent c) {
        JPanel holder = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        holder.setOpaque(false);
        holder.add(c, BorderLayout.CENTER);
        holder.setAlignmentX(LEFT_ALIGNMENT);
        return holder;
    }
}
