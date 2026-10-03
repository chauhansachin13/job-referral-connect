package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.model.JobAlert;
import com.referralconnect.model.JobCategory;
import com.referralconnect.scan.IndiaLocations;
import com.referralconnect.service.AppServices;

import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;

/** Creates a job alert (a saved search), pre-filled from the current filters when there are any. */
final class AlertDialog extends JDialog {

    private static final int[] MAX_YEARS = {-1, 0, 1, 2, 3, 5};
    private static final String[] EXPERIENCE = {"Any experience", "Freshers & interns", "Up to 1 year", "Up to 2 years",
            "Up to 3 years", "Up to 5 years"};

    private JobAlert result;

    private AlertDialog(Window owner, AppServices app, Account account, JobsPanel.Preset p) {
        super(owner, "New job alert", ModalityType.APPLICATION_MODAL);
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBackground(Theme.SURFACE);
        content.setBorder(Ui.padding(22, 24, 18, 24));

        Form form = new Form(2);
        form.full(null, Ui.label("New job alert", Theme.H2, Theme.TEXT));
        form.full(null, Ui.text("After every scan, openings matching this alert are counted under Alerts and the "
                + "bell, so you never miss a fresh posting.", Theme.SMALL, Theme.MUTED));
        JTextField name = form.field("Alert name *", suggestName(p), "e.g. Fresher data jobs in Pune");
        name.setName("alertName");
        JTextField query = form.field("Keywords", p.query(), "e.g. python, backend, react");
        JComboBox<String> role = new JComboBox<>();
        role.addItem("Any role");
        for (JobCategory c : JobCategory.values()) {
            role.addItem(c.label());
        }
        role.setSelectedItem(p.role() == null ? "Any role" : p.role().label());
        form.cell("Role", role);
        JComboBox<String> city = new JComboBox<>();
        city.addItem("All of India");
        IndiaLocations.cityChoices().forEach(city::addItem);
        city.setSelectedItem(p.city() == null || p.city().isEmpty() ? "All of India" : p.city());
        form.cell("City", city);
        JComboBox<String> type = new JComboBox<>(new String[]{"Jobs + internships", "Full-time", "Internship"});
        type.setSelectedIndex(p.internship() == null ? 0 : p.internship() ? 2 : 1);
        form.cell("Type", type);
        JComboBox<String> exp = new JComboBox<>(EXPERIENCE);
        for (int i = 0; i < MAX_YEARS.length; i++) {
            if (MAX_YEARS[i] == p.maxYears()) {
                exp.setSelectedIndex(i);
            }
        }
        form.cell("Experience", exp);
        form.finish();
        content.add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(Ui.button("Cancel", Ui.Kind.SECONDARY, this::dispose));
        Ui.FlatButton save = Ui.button("Save alert", Icons.Glyph.BELL, Ui.Kind.PRIMARY, () -> Ui.attempt(this, () -> {
            JobCategory cat = null;
            for (JobCategory c : JobCategory.values()) {
                if (c.label().equals(role.getSelectedItem())) {
                    cat = c;
                }
            }
            String c = city.getSelectedIndex() == 0 ? "" : String.valueOf(city.getSelectedItem());
            Boolean internship = type.getSelectedIndex() == 0 ? null : type.getSelectedIndex() == 2;
            result = app.alerts.create(account.id(), name.getText(), query.getText(), cat, c, internship,
                    MAX_YEARS[exp.getSelectedIndex()]);
            dispose();
        }));
        save.setName("saveAlertConfirm");
        buttons.add(save);
        getRootPane().setDefaultButton(save);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        setSize(560, 440);
        setLocationRelativeTo(owner);
    }

    private static String suggestName(JobsPanel.Preset p) {
        StringBuilder sb = new StringBuilder();
        if (p.maxYears() == 0) {
            sb.append("Fresher ");
        }
        sb.append(p.role() == null ? (p.query().isEmpty() ? "Tech" : p.query()) : p.role().label());
        sb.append(Boolean.TRUE.equals(p.internship()) ? " internships" : " jobs");
        if (p.city() != null && !p.city().isEmpty()) {
            sb.append(" in ").append(p.city());
        }
        return sb.toString();
    }

    /** Shows the dialog; returns the new alert or null. */
    static JobAlert create(Component parent, AppServices app, Account account, JobsPanel.Preset preset) {
        AlertDialog d = new AlertDialog(SwingUtilities.getWindowAncestor(parent), app, account, preset);
        d.setVisible(true);
        return d.result;
    }
}
