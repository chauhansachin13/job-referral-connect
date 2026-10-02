package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.model.CandidateProfile;
import com.referralconnect.model.ReferralRequest;
import com.referralconnect.model.RequestStatus;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.ReferralService;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The referrer's inbox. Each request arrives as a referral packet (candidate details, resume,
 * pitch and the job); the referrer refers, asks for more info, or declines.
 */
final class InboxPanel extends JPanel implements AppFrame.Live {

    private static final String[] FILTERS = {"Needs action", "All requests", "Referred", "Closed"};

    private final AppServices app;
    private final Account referrer;
    private final Runnable onChanged;
    private final JComboBox<String> filter = new JComboBox<>(FILTERS);
    private final JLabel countLabel = Ui.label(" ", Theme.BODY_BOLD, Theme.TEXT);
    private final InboxModel model = new InboxModel();
    private final JTable table = new JTable(model);
    private final PacketView packet = new PacketView();
    private String shownId;
    private long shownStamp;

    InboxPanel(AppServices app, Account referrer, Runnable onChanged) {
        super(new BorderLayout(0, 12));
        this.app = app;
        this.referrer = referrer;
        this.onChanged = onChanged;
        setOpaque(false);
        setBorder(Ui.padding(14, 16, 16, 16));

        Ui.Card bar = new Ui.Card(new BorderLayout());
        bar.setBorder(Ui.padding(10, 14, 10, 14));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        left.add(Ui.label("Show", Theme.SMALL_BOLD, Theme.MUTED));
        filter.setFont(Theme.BODY);
        filter.addActionListener(e -> refreshData());
        left.add(filter);
        left.add(countLabel);
        bar.add(left, BorderLayout.WEST);
        bar.add(Ui.label("Requests are routed to the referrer with the fewest pending requests.", Theme.SMALL,
                Theme.MUTED), BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);

        Ui.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(3).setCellRenderer(new Ui.StatusRenderer());
        int[] widths = {80, 160, 230, 130};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelected(false);
            }
        });

        Ui.Card card = new Ui.Card(new BorderLayout());
        card.add(Ui.bareScroll(packet), BorderLayout.CENTER);
        card.setMinimumSize(new Dimension(420, 200));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, Ui.scroll(table), card);
        split.setResizeWeight(0.45);
        split.setDividerSize(10);
        split.setBorder(null);
        split.setOpaque(false);
        split.setDividerLocation(600);
        add(split, BorderLayout.CENTER);
        refreshData();
    }

    @Override
    public void refreshData() {
        String keep = selected() == null ? null : selected().id();
        Predicate<ReferralRequest> show = switch (filter.getSelectedIndex()) {
            case 0 -> r -> r.status() == RequestStatus.PENDING;
            case 2 -> r -> r.status() == RequestStatus.REFERRED;
            case 3 -> r -> !r.status().isOpen();
            default -> r -> true;
        };
        List<ReferralRequest> rows = app.referrals.inbox(referrer.id()).stream().filter(show).toList();
        model.setRows(rows);
        countLabel.setText(rows.size() + (rows.size() == 1 ? " request" : " requests"));
        int index = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(keep)) {
                index = i;
            }
        }
        if (!rows.isEmpty()) {
            table.setRowSelectionInterval(index, index);
        }
        showSelected(true);
    }

    private ReferralRequest selected() {
        int row = table.getSelectedRow();
        return row < 0 || row >= model.rows.size() ? null : model.rows.get(row);
    }

    private void showSelected(boolean onlyIfChanged) {
        ReferralRequest r = selected();
        if (r == null) {
            shownId = null;
            packet.showEmpty(filter.getSelectedIndex() == 0 ? "You're all caught up" : "Nothing here",
                    "When a job seeker requests a referral for an opening at " + referrer.companyName()
                            + ", their details, resume and pitch show up here.");
            return;
        }
        if (r.viewedAt() == null) {
            Ui.attempt(this, () -> app.referrals.markViewed(referrer.id(), r.id()));
        }
        long stamp = r.updatedAt().toEpochMilli() + r.timeline().size();
        if (onlyIfChanged && r.id().equals(shownId) && stamp == shownStamp) {
            return;
        }
        shownId = r.id();
        shownStamp = stamp;

        List<JComponent> actions = new ArrayList<>();
        var moves = r.status().referrerMoves();
        if (moves.contains(RequestStatus.REFERRED)) {
            actions.add(Ui.button("Mark as referred", Ui.Kind.SUCCESS, () -> act(r, RequestStatus.REFERRED,
                    "Optional note for the candidate (e.g. \"Submitted in our portal, expect an email from HR\"):",
                    false)));
        }
        if (moves.contains(RequestStatus.NEEDS_INFO)) {
            actions.add(Ui.button("Ask for more info", Ui.Kind.WARNING, () -> act(r, RequestStatus.NEEDS_INFO,
                    "What do you need from the candidate? (e.g. \"Please share a resume with your projects\")",
                    true)));
        }
        if (moves.contains(RequestStatus.DECLINED)) {
            actions.add(Ui.button("Decline", Ui.Kind.DANGER, () -> act(r, RequestStatus.DECLINED,
                    "Short reason, so the candidate can improve (e.g. \"Role needs 3+ years\"):", true)));
        }
        CandidateProfile c = r.candidate();
        actions.add(Ui.button("Email candidate", Ui.Kind.SECONDARY, () -> Ui.email(this, c.email(),
                "Your referral request for " + r.job().title() + " at " + r.job().company(),
                "Hi " + c.name() + ",\n\nI got your referral request (" + r.id() + ") for " + r.job().title()
                        + ".\n\n\n— " + referrer.referrerTitle())));
        actions.add(Ui.button("Copy details", Ui.Kind.SECONDARY, () -> {
            Ui.copy(ReferralService.packet(r));
            Ui.info(this, "Referral details copied. Paste them into your company's referral form.");
        }));
        packet.show(r, true, actions);
    }

    private void act(ReferralRequest r, RequestStatus next, String prompt, boolean noteRequired) {
        JTextArea note = new JTextArea(4, 40);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setFont(Theme.BODY);
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.add(Ui.text(prompt, Theme.BODY, Theme.TEXT), BorderLayout.NORTH);
        body.add(Ui.scroll(note), BorderLayout.CENTER);
        body.setPreferredSize(new Dimension(440, 150));
        String title = switch (next) {
            case REFERRED -> "Mark " + r.candidate().name() + " as referred";
            case NEEDS_INFO -> "Ask for more information";
            default -> "Decline request";
        };
        while (true) {
            int choice = JOptionPane.showConfirmDialog(this, body, title, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
            if (noteRequired && note.getText().isBlank()) {
                Ui.info(this, "Please write a short note for the candidate.");
                continue;
            }
            try {
                app.referrals.respond(referrer.id(), r.id(), next, note.getText());
                refreshData();
                onChanged.run();
                return;
            } catch (RuntimeException e) {
                Ui.error(this, e);
                return;
            }
        }
    }

    private static final class InboxModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Received", "Candidate", "Role", "Status"};
        private List<ReferralRequest> rows = List.of();

        void setRows(List<ReferralRequest> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLUMNS[c];
        }

        @Override
        public Object getValueAt(int row, int c) {
            ReferralRequest r = rows.get(row);
            return switch (c) {
                case 0 -> Ui.agoShort(r.createdAt());
                case 1 -> (r.viewedAt() == null ? "● " : "") + r.candidate().name();
                case 2 -> r.job().title();
                default -> r.status();
            };
        }
    }
}
