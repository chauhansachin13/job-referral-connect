package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.model.JobPosting;
import com.referralconnect.model.RequestStatus;
import com.referralconnect.model.TrackedJob;
import com.referralconnect.model.TrackedJob.Stage;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.CsvExport;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The seeker's own application pipeline as a board: one column per stage, a card per job. Cards
 * move with the arrow buttons; notes keep track of dates, contacts and interview rounds.
 */
final class TrackerPanel extends JPanel implements AppFrame.Live {

    private final AppServices app;
    private final Account account;
    private final Consumer<JobPosting> onShowJob;
    private final Runnable onChanged;
    private final JLabel summary = Ui.label(" ", Theme.BODY_BOLD, Theme.TEXT);
    private final Map<Stage, JPanel> columns = new EnumMap<>(Stage.class);
    private final Map<Stage, JLabel> counts = new EnumMap<>(Stage.class);

    TrackerPanel(AppServices app, Account account, Consumer<JobPosting> onShowJob, Runnable onChanged) {
        super(new BorderLayout(0, 14));
        this.app = app;
        this.account = account;
        this.onShowJob = onShowJob;
        this.onChanged = onChanged;
        setOpaque(false);
        setBorder(Ui.padding(4, 24, 20, 24));

        Ui.Card bar = new Ui.Card(new BorderLayout());
        bar.setBorder(Ui.padding(12, 16, 12, 16));
        summary.setName("trackerSummary");
        bar.add(summary, BorderLayout.WEST);
        bar.add(Ui.button("Export CSV", Icons.Glyph.DOWNLOAD, Ui.Kind.SECONDARY, () -> {
            List<TrackedJob> all = app.tracker.list(account.id());
            if (all.isEmpty()) {
                Ui.toast(this, "Nothing to export yet.", Toast.Tone.WARNING);
            } else {
                JobsPanel.exportTo(this, "applications.csv", CsvExport.tracker(all), all.size() + " jobs");
            }
        }).compact(), BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);

        JPanel board = new JPanel(new GridLayout(1, Stage.values().length, 12, 0));
        board.setOpaque(false);
        for (Stage s : Stage.values()) {
            board.add(column(s));
        }
        add(board, BorderLayout.CENTER);
        refreshData();
    }

    private JComponent column(Stage stage) {
        JPanel col = new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Laf.smooth(g);
                g2.setColor(Theme.isDark() ? Theme.SURFACE : Theme.NEUTRAL_SOFT);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        col.setOpaque(false);
        col.setBorder(Ui.padding(12, 10, 10, 10));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JLabel title = Ui.label(stage.label(), Theme.BODY_BOLD, Theme.TEXT);
        title.setIcon(new DotIcon(Theme.stageColor(stage)));
        title.setIconTextGap(8);
        head.add(title, BorderLayout.WEST);
        JLabel count = Ui.label("0", Theme.SMALL_BOLD, Theme.MUTED);
        counts.put(stage, count);
        head.add(count, BorderLayout.EAST);
        head.setBorder(Ui.padding(0, 4, 2, 4));
        col.add(head, BorderLayout.NORTH);
        JPanel cards = new ScrollablePanel(null);
        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        cards.setName("column-" + stage.name());
        columns.put(stage, cards);
        JScrollPane sp = Ui.bareScroll(cards);
        col.add(sp, BorderLayout.CENTER);
        return col;
    }

    @Override
    public void refreshData() {
        List<TrackedJob> all = app.tracker.list(account.id());
        Map<String, RequestStatus> referral = app.referrals.latestStatusByJob(account.id());
        for (Stage s : Stage.values()) {
            JPanel cards = columns.get(s);
            cards.removeAll();
            List<TrackedJob> mine = all.stream().filter(t -> t.stage() == s).toList();
            counts.get(s).setText(String.valueOf(mine.size()));
            for (TrackedJob t : mine) {
                cards.add(card(t, referral.get(t.job().id())));
                cards.add(Box.createVerticalStrut(8));
            }
            if (mine.isEmpty()) {
                JLabel hint = Ui.label(s == Stage.SAVED ? "Save openings to plan your applications" : "Nothing here",
                        Theme.SMALL, Theme.FAINT);
                hint.setBorder(Ui.padding(10, 6, 0, 0));
                cards.add(hint);
            }
            cards.add(Box.createVerticalGlue());
            cards.revalidate();
            cards.repaint();
        }
        long active = all.stream().filter(t -> t.stage() != Stage.CLOSED && t.stage() != Stage.SAVED).count();
        summary.setText(all.size() + (all.size() == 1 ? " job tracked" : " jobs tracked") + "  ·  " + active
                + " in progress");
    }

    private JComponent card(TrackedJob t, RequestStatus referral) {
        JobPosting j = t.job();
        Ui.Card c = new Ui.Card(new BorderLayout(0, 8)).arc(14);
        c.setName("card-" + j.title());
        c.setBorder(Ui.padding(12, 12, 12, 10));
        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        head.add(Ui.avatar(j.company(), 28), BorderLayout.WEST);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(Ui.text(j.title(), Theme.SMALL_BOLD, Theme.TEXT).maxLines(2));
        titles.add(Ui.label(j.company() + " · " + j.city(), Theme.SMALL, Theme.MUTED));
        head.add(titles, BorderLayout.CENTER);
        c.add(head, BorderLayout.NORTH);

        JPanel mid = new JPanel();
        mid.setOpaque(false);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        JPanel pills = Ui.row(4, Ui.experiencePill(j));
        if (referral != null) {
            pills.add(Ui.statusPill(referral));
        }
        ((FlowLayout) pills.getLayout()).setVgap(3);
        mid.add(pills);
        if (!t.note().isEmpty()) {
            mid.add(Box.createVerticalStrut(4));
            mid.add(Ui.text(t.note(), Theme.SMALL, Theme.TEXT_2).maxLines(3));
        }
        mid.add(Box.createVerticalStrut(4));
        mid.add(Ui.label((t.stage() == Stage.SAVED ? "Saved " : "Updated ") + Ui.ago(t.updatedAt()), Theme.SMALL,
                Theme.FAINT));
        c.add(mid, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setOpaque(false);
        JPanel moves = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        moves.setOpaque(false);
        Stage[] stages = Stage.values();
        int i = t.stage().ordinal();
        Ui.FlatButton back = Ui.iconButton(Icons.Glyph.CHEVRON_LEFT, i > 0 ? "Move to " + stages[i - 1].label() : "",
                Ui.Kind.SUBTLE, () -> move(t, stages[i - 1])).compact();
        back.setEnabled(i > 0);
        Ui.FlatButton next = Ui.iconButton(Icons.Glyph.CHEVRON_RIGHT,
                i < stages.length - 1 ? "Move to " + stages[i + 1].label() : "", Ui.Kind.SUBTLE,
                () -> move(t, stages[i + 1])).compact();
        next.setEnabled(i < stages.length - 1);
        next.setName("moveNext");
        moves.add(back);
        moves.add(next);
        buttons.add(moves, BorderLayout.WEST);
        Ui.FlatButton more = Ui.iconButton(Icons.Glyph.MORE, "More", Ui.Kind.SUBTLE, () -> { }).compact();
        more.addActionListener(e -> menu(t).show(more, 0, more.getHeight()));
        buttons.add(more, BorderLayout.EAST);
        c.add(buttons, BorderLayout.SOUTH);
        JPanel holder = Ui.flexRow(new BorderLayout());
        holder.add(c, BorderLayout.CENTER);
        return holder;
    }

    private JPopupMenu menu(TrackedJob t) {
        JPopupMenu m = new JPopupMenu();
        for (Stage s : Stage.values()) {
            if (s != t.stage()) {
                m.add(JobsPanel.item("Move to " + s.label(), Icons.Glyph.KANBAN, () -> move(t, s)));
            }
        }
        m.addSeparator();
        m.add(JobsPanel.item(t.note().isEmpty() ? "Add note" : "Edit note", Icons.Glyph.MESSAGE, () -> editNote(t)));
        m.add(JobsPanel.item("Open posting", Icons.Glyph.EXTERNAL, () -> Ui.openUrl(this, t.job().url())));
        m.add(JobsPanel.item("Show in Openings", Icons.Glyph.BRIEFCASE, () -> onShowJob.accept(t.job())));
        m.add(JobsPanel.item("Remove from tracker", Icons.Glyph.TRASH, () -> {
            app.tracker.remove(account.id(), t.job().id());
            refreshData();
            onChanged.run();
        }));
        return m;
    }

    private void move(TrackedJob t, Stage to) {
        Ui.attempt(this, () -> {
            app.tracker.move(account.id(), t.job().id(), to);
            refreshData();
            onChanged.run();
            Ui.toast(this, t.job().title() + " → " + to.label());
        });
    }

    private void editNote(TrackedJob t) {
        JTextArea area = Form.textArea(t.note(), 5, "e.g. Applied 2 Oct · HR call on Monday · round 1 DSA");
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.add(Ui.label("Note for " + t.job().title(), Theme.BODY_BOLD, Theme.TEXT), BorderLayout.NORTH);
        JScrollPane sp = Form.areaScroll(area);
        sp.setPreferredSize(new Dimension(420, 120));
        p.add(sp, BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(this, p, "Note", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                == JOptionPane.OK_OPTION) {
            Ui.attempt(this, () -> {
                app.tracker.note(account.id(), t.job().id(), area.getText());
                refreshData();
            });
        }
    }

    /** A small coloured dot before each column title. */
    private record DotIcon(java.awt.Color color) implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = Laf.smooth(g);
            g2.setColor(color);
            g2.fillOval(x, y + 1, 9, 9);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 9;
        }

        @Override
        public int getIconHeight() {
            return 11;
        }
    }
}
