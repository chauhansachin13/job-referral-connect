package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.model.ReferralRequest;
import com.referralconnect.model.RequestStatus;
import com.referralconnect.service.AppServices;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/** The seeker's sent referral requests, their status, and what the referrer said. */
final class SeekerRequestsPanel extends JPanel implements AppFrame.Live {

    private final AppServices app;
    private final Account seeker;
    private final Runnable onChanged;
    private final RequestsModel model = new RequestsModel();
    private final JTable table = new JTable(model);
    private final PacketView packet = new PacketView();
    private String shownId;
    private long shownStamp;

    SeekerRequestsPanel(AppServices app, Account seeker, Runnable onChanged) {
        super(new BorderLayout());
        this.app = app;
        this.seeker = seeker;
        this.onChanged = onChanged;
        setOpaque(false);
        setBorder(Ui.padding(14, 16, 16, 16));

        Ui.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(4).setCellRenderer(new Ui.StatusRenderer());
        int[] widths = {70, 95, 250, 170, 130, 80};
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
        card.setMinimumSize(new Dimension(380, 200));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, Ui.scroll(table), card);
        split.setResizeWeight(0.55);
        split.setDividerSize(10);
        split.setBorder(null);
        split.setOpaque(false);
        split.setDividerLocation(700);
        add(split, BorderLayout.CENTER);
        refreshData();
    }

    @Override
    public void refreshData() {
        String keep = selected() == null ? null : selected().id();
        model.setRows(app.referrals.forSeeker(seeker.id()));
        int index = 0;
        for (int i = 0; i < model.rows.size(); i++) {
            if (model.rows.get(i).id().equals(keep)) {
                index = i;
            }
        }
        if (!model.rows.isEmpty()) {
            table.setRowSelectionInterval(index, index);
        }
        showSelected(true);
    }

    long openCount() {
        return model.rows.stream().filter(r -> r.status().isOpen()).count();
    }

    private ReferralRequest selected() {
        int row = table.getSelectedRow();
        return row < 0 || row >= model.rows.size() ? null : model.rows.get(row);
    }

    /** Rebuilds the detail view only when the selection or its content changed, to keep scroll position. */
    private void showSelected(boolean onlyIfChanged) {
        ReferralRequest r = selected();
        if (r == null) {
            shownId = null;
            packet.showEmpty("No referral requests yet",
                    "Open the \"Openings\" tab, pick a job with a referrer, and click \"Get referral\".");
            return;
        }
        long stamp = r.updatedAt().toEpochMilli() + r.timeline().size();
        if (onlyIfChanged && r.id().equals(shownId) && stamp == shownStamp) {
            return;
        }
        shownId = r.id();
        shownStamp = stamp;
        List<JComponent> actions = new ArrayList<>();
        if (r.status() == RequestStatus.NEEDS_INFO) {
            actions.add(Ui.button("Update & resubmit", Ui.Kind.PRIMARY, () -> {
                if (ReferralRequestDialog.resubmit(this, app, seeker, r) != null) {
                    refreshData();
                    onChanged.run();
                }
            }));
        }
        if (r.status().isOpen()) {
            actions.add(Ui.button("Withdraw", Ui.Kind.DANGER, () -> {
                int ok = JOptionPane.showConfirmDialog(this, "Withdraw your request for " + r.job().title() + "?",
                        "Withdraw request", JOptionPane.YES_NO_OPTION);
                if (ok == JOptionPane.YES_OPTION) {
                    Ui.attempt(this, () -> {
                        app.referrals.withdraw(seeker.id(), r.id());
                        refreshData();
                        onChanged.run();
                    });
                }
            }));
        }
        actions.add(Ui.button("Open job posting", Ui.Kind.SECONDARY, () -> Ui.openUrl(this, r.job().url())));
        packet.show(r, false, actions);
    }

    private static final class RequestsModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Sent", "Company", "Role", "Referrer", "Status", "Updated"};
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
                case 0 -> Ui.dayMonth(r.createdAt());
                case 1 -> r.job().company();
                case 2 -> r.job().title();
                case 3 -> r.referrerTitle().split(",")[0];
                case 4 -> r.status();
                default -> Ui.agoShort(r.updatedAt());
            };
        }
    }
}
