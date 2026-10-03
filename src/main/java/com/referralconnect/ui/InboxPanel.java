package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.model.CandidateProfile;
import com.referralconnect.model.ReferralRequest;
import com.referralconnect.model.RequestStatus;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.CsvExport;
import com.referralconnect.service.ReferralService;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The referrer's inbox. Each request arrives as a referral packet (candidate details, resume,
 * pitch and the job with its minimum experience); the referrer can message the candidate, refer,
 * ask for more info, or decline.
 */
final class InboxPanel extends JPanel implements AppFrame.Live {

    private static final String[] FILTERS = {"Needs action", "All requests", "Referred", "Closed"};

    /** Ready-made notes for each decision; the referrer can edit them before sending. */
    private static final String[] REFERRED_NOTES = {
            "Submitted in our referral portal — expect an email from the recruiting team within a week.",
            "Referred! Keep an eye on your inbox (and spam) for the online assessment.",
            "Done. I've also told the hiring manager about your projects."};
    private static final String[] INFO_NOTES = {
            "Please share a resume link that I can open (view access for anyone with the link).",
            "Can you add your project links (GitHub / portfolio) and how many years you've worked with the main stack?",
            "Which team or location do you prefer? I'll route the referral accordingly."};
    private static final String[] DECLINE_NOTES = {
            "This role needs more years of experience than your profile shows; I'd be glad to help with a junior opening.",
            "The position has closed internally. Please request again for a newer opening.",
            "Your skills don't match this team's stack closely enough; another role may fit you better."};

    private final AppServices app;
    private final Account referrer;
    private final Runnable onChanged;
    private final JComboBox<String> filter = new JComboBox<>(FILTERS);
    private final JLabel countLabel = Ui.label(" ", Theme.BODY_BOLD, Theme.TEXT);
    private final InboxModel model = new InboxModel();
    private final JTable table = new JTable(model);
    private final PacketView packet = new PacketView();
    private List<ReferralRequest> all = List.of();
    private String shownStamp;

    InboxPanel(AppServices app, Account referrer, Runnable onChanged) {
        super(new BorderLayout(0, 12));
        this.app = app;
        this.referrer = referrer;
        this.onChanged = onChanged;
        setOpaque(false);
        setBorder(Ui.padding(4, 24, 20, 24));

        Ui.Card bar = new Ui.Card(new BorderLayout());
        bar.setBorder(Ui.padding(12, 16, 12, 16));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        filter.setName("inboxFilter");
        filter.addActionListener(e -> refreshData());
        left.add(filter);
        left.add(countLabel);
        bar.add(left, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        right.add(Ui.label("Requests go to the referrer with the fewest pending ones.", Theme.SMALL, Theme.MUTED));
        right.add(Ui.button("Export CSV", Icons.Glyph.DOWNLOAD, Ui.Kind.SECONDARY, () -> {
            if (all.isEmpty()) {
                Ui.toast(this, "No requests to export yet.", Toast.Tone.WARNING);
            } else {
                JobsPanel.exportTo(this, "referral-inbox.csv", CsvExport.requests(all), all.size() + " requests");
            }
        }).compact());
        bar.add(right, BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);

        Ui.styleTable(table);
        table.setName("inboxTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(54);
        table.getColumnModel().getColumn(0).setCellRenderer(new Ui.TwoLineRenderer());
        table.getColumnModel().getColumn(1).setCellRenderer(new Ui.StatusRenderer());
        int[] widths = {300, 120, 90};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelected();
            }
        });

        Ui.Card card = new Ui.Card(new BorderLayout());
        card.setBorder(Ui.padding(18, 20, 18, 8));
        card.add(Ui.bareScroll(packet), BorderLayout.CENTER);
        card.setMinimumSize(new Dimension(440, 200));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, Ui.scroll(table), card);
        split.setResizeWeight(0.45);
        split.setDividerSize(14);
        add(split, BorderLayout.CENTER);
        refreshData();
    }

    @Override
    public void refreshData() {
        String keep = selected() == null ? null : selected().id();
        Predicate<ReferralRequest> show = switch (filter.getSelectedIndex()) {
            case 0 -> r -> r.status() == RequestStatus.PENDING || lastFromSeeker(r) && r.status().isOpen();
            case 2 -> r -> r.status() == RequestStatus.REFERRED;
            case 3 -> r -> !r.status().isOpen();
            default -> r -> true;
        };
        all = app.referrals.inbox(referrer.id());
        List<ReferralRequest> rows = all.stream().filter(show).toList();
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
        showSelected();
    }

    private static boolean lastFromSeeker(ReferralRequest r) {
        return !r.messages().isEmpty() && r.messages().get(r.messages().size() - 1).from() == ReferralRequest.Actor.SEEKER;
    }

    void select(String requestId) {
        if (model.rows.stream().noneMatch(r -> r.id().equals(requestId))) {
            filter.setSelectedIndex(1);
        }
        for (int i = 0; i < model.rows.size(); i++) {
            if (model.rows.get(i).id().equals(requestId)) {
                table.setRowSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
            }
        }
    }

    private ReferralRequest selected() {
        int row = table.getSelectedRow();
        return row < 0 || row >= model.rows.size() ? null : model.rows.get(row);
    }

    private void showSelected() {
        ReferralRequest r = selected();
        if (r == null) {
            shownStamp = null;
            packet.showEmpty(filter.getSelectedIndex() == 0 ? "You're all caught up" : "Nothing here",
                    "When a job seeker requests a referral for an opening at " + referrer.companyName()
                            + ", their details, resume and pitch show up here.");
            return;
        }
        if (r.viewedAt() == null) {
            Ui.attempt(this, () -> app.referrals.markViewed(referrer.id(), r.id()));
        }
        String stamp = r.id() + r.lastActivityAt() + r.timeline().size() + "/" + r.messages().size() + r.status();
        if (stamp.equals(shownStamp)) {
            return;
        }
        shownStamp = stamp;

        List<JComponent> actions = new ArrayList<>();
        var moves = r.status().referrerMoves();
        if (moves.contains(RequestStatus.REFERRED)) {
            Ui.FlatButton b = Ui.button("Mark as referred", Icons.Glyph.CHECK, Ui.Kind.SUCCESS, () -> act(r,
                    RequestStatus.REFERRED, "Optional note for the candidate:", false, REFERRED_NOTES));
            b.setName("markReferred");
            actions.add(b);
        }
        if (moves.contains(RequestStatus.NEEDS_INFO)) {
            Ui.FlatButton b = Ui.button("Ask for more info", Icons.Glyph.MESSAGE, Ui.Kind.WARNING, () -> act(r,
                    RequestStatus.NEEDS_INFO, "What do you need from the candidate?", true, INFO_NOTES));
            b.setName("askInfo");
            actions.add(b);
        }
        if (moves.contains(RequestStatus.DECLINED)) {
            Ui.FlatButton b = Ui.button("Decline", Icons.Glyph.X, Ui.Kind.DANGER, () -> act(r, RequestStatus.DECLINED,
                    "A short reason, so the candidate can improve:", true, DECLINE_NOTES));
            b.setName("decline");
            actions.add(b);
        }
        CandidateProfile c = r.candidate();
        actions.add(Ui.button("Email", Icons.Glyph.SEND, Ui.Kind.SECONDARY, () -> Ui.email(this, c.email(),
                "Your referral request for " + r.job().title() + " at " + r.job().company(),
                "Hi " + c.name() + ",\n\nI got your referral request (" + r.id() + ") for " + r.job().title()
                        + ".\n\n\n— " + referrer.referrerTitle())));
        actions.add(Ui.button("Copy details", Icons.Glyph.COPY, Ui.Kind.SECONDARY, () -> {
            Ui.copy(ReferralService.packet(r));
            Ui.toast(this, "Referral details copied. Paste them into your company's referral form.");
        }));
        Conversation chat = new Conversation(app, referrer.id(), r, () -> {
            refreshData();
            onChanged.run();
        });
        packet.show(r, true, actions, chat);
    }

    private void act(ReferralRequest r, RequestStatus next, String prompt, boolean noteRequired, String[] templates) {
        JTextArea note = Form.textArea("", 4, noteRequired ? "Required" : "Optional");
        note.setName("decisionNote");
        JComboBox<String> pick = new JComboBox<>();
        pick.addItem("Quick replies…");
        for (String t : templates) {
            pick.addItem(t);
        }
        pick.setRenderer(new DefaultTableCellRendererList());
        pick.addActionListener(e -> {
            if (pick.getSelectedIndex() > 0) {
                note.setText((String) pick.getSelectedItem());
            }
        });
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        body.add(Ui.text(prompt, Theme.BODY, Theme.TEXT), BorderLayout.NORTH);
        JScrollPane sp = Form.areaScroll(note);
        body.add(sp, BorderLayout.CENTER);
        body.add(pick, BorderLayout.SOUTH);
        body.setPreferredSize(new Dimension(500, 190));
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
                Ui.toast(this, switch (next) {
                    case REFERRED -> r.candidate().name() + " marked as referred.";
                    case NEEDS_INFO -> "Asked " + r.candidate().name() + " for more information.";
                    default -> "Request declined.";
                });
                return;
            } catch (RuntimeException e) {
                Ui.error(this, e);
                return;
            }
        }
    }

    /** Shows long quick-reply texts cut to one line in the drop-down. */
    private static final class DefaultTableCellRendererList extends javax.swing.DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                      boolean selected, boolean focus) {
            String s = String.valueOf(value);
            JLabel l = (JLabel) super.getListCellRendererComponent(list, s.length() > 70 ? s.substring(0, 68) + "…" : s,
                    index, selected, focus);
            l.setToolTipText(s);
            l.setBorder(Ui.padding(5, 8, 5, 8));
            return l;
        }
    }

    private static final class InboxModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Candidate", "Status", "When"};
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
            boolean unread = r.viewedAt() == null || lastFromSeeker(r) && r.status().isOpen();
            String years = r.candidate().yearsKnown() ? r.candidate().yearsLabel() + " · " : "";
            return switch (c) {
                case 0 -> new Ui.TwoLines(r.candidate().name(), true, (unread ? "● " : "") + r.candidate().name(),
                        years + r.job().title(), unread);
                case 1 -> r.status();
                default -> Ui.agoShort(r.createdAt());
            };
        }
    }
}
