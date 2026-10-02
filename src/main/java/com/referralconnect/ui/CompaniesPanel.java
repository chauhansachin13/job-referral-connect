package com.referralconnect.ui;

import com.referralconnect.model.CompanyBoard;
import com.referralconnect.model.JobPosting;
import com.referralconnect.service.AppServices;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Every company the app scans, with how many recent India openings and referrers each has and
 * whether its careers site could be read. Double-click (or "Show openings") jumps to its jobs.
 */
final class CompaniesPanel extends JPanel implements AppFrame.Live {

    private final AppServices app;
    private final Consumer<String> onShowOpenings;
    private final CompaniesModel model = new CompaniesModel();
    private final JTable table = new JTable(model);
    private final TableRowSorter<CompaniesModel> sorter = new TableRowSorter<>(model);
    private final Form.HintField search = new Form.HintField("", "Find a company");
    private final JLabel summary = Ui.label(" ", Theme.BODY_BOLD, Theme.TEXT);

    record Row(String name, String platform, int openings, int internships, int referrers, String status) {
    }

    CompaniesPanel(AppServices app, Consumer<String> onShowOpenings) {
        super(new BorderLayout(0, 12));
        this.app = app;
        this.onShowOpenings = onShowOpenings;
        setOpaque(false);
        setBorder(Ui.padding(14, 16, 16, 16));

        Ui.Card bar = new Ui.Card(new BorderLayout());
        bar.setBorder(Ui.padding(10, 14, 10, 14));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        search.setPreferredSize(new Dimension(220, 34));
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                applySearch();
            }

            public void removeUpdate(DocumentEvent e) {
                applySearch();
            }

            public void changedUpdate(DocumentEvent e) {
                applySearch();
            }
        });
        left.add(search);
        left.add(summary);
        bar.add(left, BorderLayout.WEST);
        bar.add(Ui.button("Show openings", Ui.Kind.PRIMARY, this::showSelected), BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);

        Ui.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSorter(sorter);
        sorter.setSortKeys(List.of(new RowSorter.SortKey(2, SortOrder.DESCENDING)));
        int[] widths = {230, 150, 100, 100, 90, 380};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        for (int c = 2; c <= 4; c++) {
            Ui.centerColumn(table, c);
        }
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    showSelected();
                }
            }
        });
        add(Ui.scroll(table), BorderLayout.CENTER);
        refreshData();
    }

    @Override
    public void refreshData() {
        String keep = selectedName();
        Map<String, int[]> counts = new HashMap<>(); // key -> {openings, internships}
        for (JobPosting j : app.jobs.jobs()) {
            int[] c = counts.computeIfAbsent(j.companyKey(), k -> new int[2]);
            c[0]++;
            if (j.internship()) {
                c[1]++;
            }
        }
        Map<String, Integer> referrers = app.referrals.referrerCountsByCompany();
        Map<String, String> failures = app.jobs.lastScanFailures();
        boolean scanned = app.jobs.lastScanAt() != null;
        List<Row> rows = new ArrayList<>();
        int withOpenings = 0;
        for (CompanyBoard b : app.directory.all()) {
            int[] c = counts.getOrDefault(b.key(), new int[2]);
            String status = failures.containsKey(b.name()) ? "Unreachable last scan: " + failures.get(b.name())
                    : !scanned ? "Not scanned yet" : c[0] > 0 ? "OK" : "OK — no matching India openings right now";
            rows.add(new Row(b.name(), b.ats().label(), c[0], c[1], referrers.getOrDefault(b.key(), 0), status));
            if (c[0] > 0) {
                withOpenings++;
            }
        }
        model.setRows(rows);
        summary.setText(rows.size() + " companies scanned  ·  " + withOpenings + " with openings now  ·  "
                + rows.stream().filter(r -> r.referrers() > 0).count() + " with referrers");
        for (int i = 0; i < table.getRowCount(); i++) {
            if (model.rows.get(table.convertRowIndexToModel(i)).name().equals(keep)) {
                table.setRowSelectionInterval(i, i);
            }
        }
    }

    private void applySearch() {
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        sorter.setRowFilter(q.isEmpty() ? null : new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends CompaniesModel, ? extends Integer> e) {
                return model.rows.get(e.getIdentifier()).name().toLowerCase(Locale.ROOT).contains(q);
            }
        });
    }

    private String selectedName() {
        int view = table.getSelectedRow();
        return view < 0 ? null : model.rows.get(table.convertRowIndexToModel(view)).name();
    }

    private void showSelected() {
        String name = selectedName();
        if (name == null) {
            Ui.info(this, "Select a company first.");
            return;
        }
        onShowOpenings.accept(name);
    }

    private static final class CompaniesModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Company", "Careers site", "India openings", "Internships", "Referrers", "Status"};
        private List<Row> rows = List.of();

        void setRows(List<Row> rows) {
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
        public Class<?> getColumnClass(int c) {
            return c >= 2 && c <= 4 ? Integer.class : String.class;
        }

        @Override
        public Object getValueAt(int r, int c) {
            Row row = rows.get(r);
            return switch (c) {
                case 0 -> row.name();
                case 1 -> row.platform();
                case 2 -> row.openings();
                case 3 -> row.internships();
                case 4 -> row.referrers();
                default -> row.status();
            };
        }
    }
}
