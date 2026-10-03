package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.model.JobAlert;
import com.referralconnect.model.JobPosting;
import com.referralconnect.service.AppServices;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;

/** The seeker's saved searches, each with how many new openings matched since it was last opened. */
final class AlertsPanel extends JPanel implements AppFrame.Live {

    private final AppServices app;
    private final Account account;
    private final Consumer<JobAlert> onOpen;
    private final Runnable onChanged;
    private final JPanel list = new ScrollablePanel(null);
    private final JLabel summary = Ui.label(" ", Theme.BODY_BOLD, Theme.TEXT);

    AlertsPanel(AppServices app, Account account, Consumer<JobAlert> onOpen, Runnable onChanged) {
        super(new BorderLayout(0, 14));
        this.app = app;
        this.account = account;
        this.onOpen = onOpen;
        this.onChanged = onChanged;
        setOpaque(false);
        setBorder(Ui.padding(4, 24, 20, 24));

        Ui.Card bar = new Ui.Card(new BorderLayout());
        bar.setBorder(Ui.padding(12, 16, 12, 16));
        bar.add(summary, BorderLayout.WEST);
        Ui.FlatButton add = Ui.button("New alert", Icons.Glyph.PLUS, Ui.Kind.PRIMARY, () -> {
            if (AlertDialog.create(this, app, account, JobsPanel.Preset.all()) != null) {
                refreshData();
                onChanged.run();
                Ui.toast(this, "Alert saved.");
            }
        });
        add.setName("newAlert");
        bar.add(add, BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setName("alertsList");
        add(Ui.bareScroll(list), BorderLayout.CENTER);
        refreshData();
    }

    @Override
    public void refreshData() {
        list.removeAll();
        List<JobAlert> alerts = app.alerts.list(account.id());
        int totalNew = 0;
        if (alerts.isEmpty()) {
            Ui.Card empty = new Ui.Card(new BorderLayout(16, 0));
            empty.setBorder(Ui.padding(26, 26, 26, 26));
            empty.add(new JLabel(Icons.get(Icons.Glyph.BELL, 36, Theme.PRIMARY)), BorderLayout.WEST);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(Ui.label("No alerts yet", Theme.H3, Theme.TEXT));
            text.add(Box.createVerticalStrut(6));
            text.add(Ui.text("Create one with \"New alert\", or set filters on Openings and click \"Save as alert\". "
                    + "Example: Data Scientist · Bengaluru · freshers.", Theme.BODY, Theme.MUTED));
            empty.add(text, BorderLayout.CENTER);
            list.add(stretch(empty));
        }
        JPanel grid = new JPanel(new GridLayout(0, 2, 14, 14));
        grid.setOpaque(false);
        for (JobAlert a : alerts) {
            List<JobPosting> fresh = app.alerts.newMatches(a);
            int all = app.alerts.matches(a).size();
            totalNew += fresh.size();
            grid.add(card(a, fresh, all));
        }
        if (!alerts.isEmpty()) {
            list.add(stretch(grid));
        }
        list.add(Box.createVerticalGlue());
        summary.setText(alerts.size() + (alerts.size() == 1 ? " alert" : " alerts")
                + (totalNew > 0 ? "  ·  " + totalNew + " new openings" : "  ·  nothing new"));
        list.revalidate();
        list.repaint();
    }

    /** Total new openings across all alerts, for the sidebar badge. */
    int newCount() {
        return app.alerts.list(account.id()).stream().mapToInt(a -> app.alerts.newMatches(a).size()).sum();
    }

    private JComponent card(JobAlert a, List<JobPosting> fresh, int all) {
        Ui.Card c = new Ui.Card(new BorderLayout(0, 10));
        c.setName("alert-" + a.name());
        c.setBorder(Ui.padding(16, 18, 16, 18));
        JPanel head = new JPanel(new BorderLayout(10, 0));
        head.setOpaque(false);
        JLabel name = Ui.label(a.name(), Theme.H3, Theme.TEXT);
        name.setIcon(Icons.get(Icons.Glyph.BELL, 18, Theme.PRIMARY));
        name.setIconTextGap(8);
        head.add(name, BorderLayout.CENTER);
        JPanel badge = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        badge.setOpaque(false);
        badge.add(fresh.isEmpty() ? Ui.neutralPill("Nothing new")
                : new Ui.Pill(fresh.size() + " new", java.awt.Color.WHITE, Theme.CHART[4]));
        head.add(badge, BorderLayout.EAST);
        c.add(head, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(Ui.text(a.describe(), Theme.SMALL, Theme.MUTED));
        body.add(Box.createVerticalStrut(6));
        body.add(Ui.label(all + (all == 1 ? " opening matches now" : " openings match now"), Theme.SMALL_BOLD,
                Theme.TEXT_2));
        for (JobPosting j : fresh.stream().limit(3).toList()) {
            body.add(Box.createVerticalStrut(4));
            body.add(Ui.text("• " + j.title() + " — " + j.company(), Theme.SMALL, Theme.TEXT).maxLines(1));
        }
        c.add(body, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        Ui.FlatButton view = Ui.button("View openings", Icons.Glyph.SEARCH, Ui.Kind.PRIMARY, () -> {
            app.alerts.markChecked(account.id(), a.id());
            onOpen.accept(a);
            onChanged.run();
        }).compact();
        view.setName("viewAlert");
        buttons.add(view);
        buttons.add(Ui.button("Delete", Icons.Glyph.TRASH, Ui.Kind.SUBTLE, () -> {
            if (Ui.confirm(this, "Delete the alert \"" + a.name() + "\"?", "Delete alert")) {
                app.alerts.delete(account.id(), a.id());
                refreshData();
                onChanged.run();
            }
        }).compact());
        c.add(buttons, BorderLayout.SOUTH);
        return c;
    }

    private static JComponent stretch(Component c) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.setOpaque(false);
        p.add(c, BorderLayout.CENTER);
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }
}
