package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.model.ReferralRequest;
import com.referralconnect.model.RequestStatus;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.CsvExport;
import com.referralconnect.service.ReferralService;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** The seeker's sent referral requests: status, the referrer's replies, messages and reminders. */
final class SeekerRequestsPanel extends JPanel implements AppFrame.Live {

    private static final String[] FILTERS = {"All requests", "Waiting", "Referred", "Closed"};

    private final AppServices app;
    private final Account seeker;
    private final Runnable onChanged;
    private final JComboBox<String> filter = new JComboBox<>(FILTERS);
    private final JLabel countLabel = Ui.label(" ", Theme.BODY_BOLD, Theme.TEXT);
    private final RequestsModel model = new RequestsModel();
    private final JTable table = new JTable(model);
    private final PacketView packet = new PacketView();
    private List<ReferralRequest> all = List.of();
    private String shownStamp;

    SeekerRequestsPanel(AppServices app, Account seeker, Runnable onChanged) {
        super(new BorderLayout(0, 12));
        this.app = app;
        this.seeker = seeker;
        this.onChanged = onChanged;
        setOpaque(false);
        setBorder(Ui.padding(4, 24, 20, 24));

        Ui.Card bar = new Ui.Card(new BorderLayout());
        bar.setBorder(Ui.padding(12, 16, 12, 16));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        filter.setName("requestFilter");
        filter.addActionListener(e -> refreshData());
        left.add(filter);
        left.add(countLabel);
        bar.add(left, BorderLayout.WEST);
        bar.add(Ui.button("Export CSV", Icons.Glyph.DOWNLOAD, Ui.Kind.SECONDARY, () -> {
            if (all.isEmpty()) {
                Ui.toast(this, "No requests to export yet.", Toast.Tone.WARNING);
            } else {
                JobsPanel.exportTo(this, "referral-requests.csv", CsvExport.requests(all), all.size() + " requests");
            }
        }).compact(), BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);

        Ui.styleTable(table);
        table.setName("requestsTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(54);
        table.getColumnModel().getColumn(0).setCellRenderer(new Ui.TwoLineRenderer());
        table.getColumnModel().getColumn(1).setCellRenderer(new Ui.StatusRenderer());
        int[] widths = {330, 130, 70};
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
        card.setMinimumSize(new Dimension(420, 200));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, Ui.scroll(table), card);
        split.setResizeWeight(0.45);
        split.setDividerSize(14);
        add(split, BorderLayout.CENTER);
        refreshData();
    }

    @Override
    public void refreshData() {
        String keep = selected() == null ? null : selected().id();
        all = app.referrals.forSeeker(seeker.id());
        Predicate<ReferralRequest> show = switch (filter.getSelectedIndex()) {
            case 1 -> r -> r.status().isOpen();
            case 2 -> r -> r.status() == RequestStatus.REFERRED;
            case 3 -> r -> !r.status().isOpen() && r.status() != RequestStatus.REFERRED;
            default -> r -> true;
        };
        model.setRows(all.stream().filter(show).toList());
        countLabel.setText(model.rows.size() + (model.rows.size() == 1 ? " request" : " requests") + "  ·  "
                + all.stream().filter(r -> r.status() == RequestStatus.REFERRED).count() + " referred");
        int index = 0;
        for (int i = 0; i < model.rows.size(); i++) {
            if (model.rows.get(i).id().equals(keep)) {
                index = i;
            }
        }
        if (!model.rows.isEmpty()) {
            table.setRowSelectionInterval(index, index);
        }
        showSelected();
    }

    long openCount() {
        return app.referrals.forSeeker(seeker.id()).stream().filter(r -> r.status().isOpen()).count();
    }

    /** Selects a request (e.g. from a notification), clearing the filter if needed. */
    void select(String requestId) {
        if (model.rows.stream().noneMatch(r -> r.id().equals(requestId))) {
            filter.setSelectedIndex(0);
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

    /** Rebuilds the detail view only when the selection or its content changed, to keep scroll position. */
    private void showSelected() {
        ReferralRequest r = selected();
        if (r == null) {
            shownStamp = null;
            packet.showEmpty(all.isEmpty() ? "No referral requests yet" : "Nothing here",
                    "Open Openings, pick a job with a referrer, and click \"Get referral\". Your profile, resume and "
                            + "pitch go to an employee there who has offered to refer.");
            return;
        }
        String stamp = r.id() + r.lastActivityAt() + r.timeline().size() + "/" + r.messages().size() + r.status();
        if (stamp.equals(shownStamp)) {
            return;
        }
        shownStamp = stamp;
        List<JComponent> actions = new ArrayList<>();
        if (r.status() == RequestStatus.NEEDS_INFO) {
            Ui.FlatButton update = Ui.button("Update & resubmit", Icons.Glyph.SEND, Ui.Kind.PRIMARY, () -> {
                if (ReferralRequestDialog.resubmit(this, app, seeker, r) != null) {
                    refreshData();
                    onChanged.run();
                    Ui.toast(this, "Sent back to " + r.referrerName() + ".");
                }
            });
            update.setName("resubmit");
            actions.add(update);
        }
        Instant remindAt = ReferralService.reminderAllowedAt(r);
        if (remindAt != null) {
            Ui.FlatButton remind = Ui.button("Send reminder", Icons.Glyph.CLOCK, Ui.Kind.SECONDARY,
                    () -> Ui.attempt(this, () -> {
                        app.referrals.remind(seeker.id(), r.id());
                        refreshData();
                        onChanged.run();
                        Ui.toast(this, "Reminder sent to " + r.referrerName() + ".");
                    }));
            remind.setName("remind");
            boolean ready = !Instant.now().isBefore(remindAt);
            remind.setEnabled(ready);
            remind.setToolTipText(ready ? "Nudge the referrer politely" : "Available " + Ui.dateTime(remindAt)
                    + " — give the referrer a few days");
            actions.add(remind);
        }
        if (r.status().isOpen()) {
            Ui.FlatButton withdraw = Ui.button("Withdraw", Icons.Glyph.X, Ui.Kind.DANGER, () -> {
                if (Ui.confirm(this, "Withdraw your request for " + r.job().title() + "?", "Withdraw request")) {
                    Ui.attempt(this, () -> {
                        app.referrals.withdraw(seeker.id(), r.id());
                        refreshData();
                        onChanged.run();
                    });
                }
            });
            withdraw.setName("withdraw");
            actions.add(withdraw);
        }
        actions.add(Ui.button("Open posting", Icons.Glyph.EXTERNAL, Ui.Kind.SECONDARY, () -> Ui.openUrl(this, r.job().url())));
        Conversation chat = new Conversation(app, seeker.id(), r, () -> {
            refreshData();
            onChanged.run();
        });
        packet.show(r, false, actions, chat);
    }

    private static final class RequestsModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Request", "Status", "When"};
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
            boolean unread = !r.messages().isEmpty()
                    && r.messages().get(r.messages().size() - 1).from() == ReferralRequest.Actor.REFERRER;
            return switch (c) {
                case 0 -> new Ui.TwoLines(r.job().company(), false, (unread ? "● " : "") + r.job().title(),
                        r.job().company() + " · to " + r.referrerName() + " · sent " + Ui.dayMonth(r.createdAt()), unread);
                case 1 -> r.status();
                default -> Ui.agoShort(r.lastActivityAt());
            };
        }
    }
}
