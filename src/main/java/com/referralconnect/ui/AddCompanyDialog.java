package com.referralconnect.ui;

import com.referralconnect.model.CompanyBoard;
import com.referralconnect.scan.BoardUrlParser;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.ServiceException;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.Optional;

/**
 * Lets a referrer whose company is missing from the directory add its public careers board.
 * The board is fetched once before it is accepted, so typos never enter the directory.
 */
final class AddCompanyDialog extends JDialog {

    private final AppServices app;
    private CompanyBoard result;

    private AddCompanyDialog(Window owner, AppServices app) {
        super(owner, "Add your company", ModalityType.APPLICATION_MODAL);
        this.app = app;
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(Theme.SURFACE);
        content.setBorder(Ui.padding(20, 22, 18, 22));

        Form form = new Form(1);
        form.cell(null, Ui.label("Add your company's careers board", Theme.H3, Theme.TEXT));
        form.cell(null, Ui.text("Paste the link to your company's public job board. Supported: Greenhouse "
                + "(job-boards.greenhouse.io/…), Lever (jobs.lever.co/…) and Ashby (jobs.ashbyhq.com/…).",
                Theme.SMALL, Theme.MUTED));
        JTextField name = form.field("Company name", "", "e.g. Acme Technologies");
        JTextField link = form.field("Careers board link", "", "https://jobs.lever.co/acme");
        JLabel status = Ui.label(" ", Theme.SMALL, Theme.MUTED);
        form.cell(null, status);
        content.add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        Ui.FlatButton check = new Ui.FlatButton("Check & add", Ui.Kind.PRIMARY);
        buttons.add(Ui.button("Cancel", Ui.Kind.SECONDARY, this::dispose));
        buttons.add(check);
        content.add(buttons, BorderLayout.SOUTH);

        check.addActionListener(e -> {
            Optional<CompanyBoard> parsed = BoardUrlParser.parse(name.getText(), link.getText());
            if (name.getText().isBlank()) {
                Ui.error(this, new ServiceException("Enter the company name."));
                return;
            }
            if (parsed.isEmpty()) {
                Ui.error(this, new ServiceException("That link is not a Greenhouse, Lever or Ashby job board."));
                return;
            }
            CompanyBoard board = parsed.get();
            check.setEnabled(false);
            status.setForeground(Theme.MUTED);
            status.setText("Checking " + board.ats().label() + " board \"" + board.token() + "\"…");
            new SwingWorker<Integer, Void>() {
                @Override
                protected Integer doInBackground() {
                    return app.jobs.verifyBoard(board);
                }

                @Override
                protected void done() {
                    check.setEnabled(true);
                    try {
                        int postings = get();
                        result = app.directory.add(board);
                        Ui.info(AddCompanyDialog.this, "Found " + postings + " open postings on " + board.name()
                                + "'s board. It will be included in every scan from now on.");
                        dispose();
                    } catch (Exception ex) {
                        status.setText(" ");
                        Ui.error(AddCompanyDialog.this, ex.getCause() != null ? ex.getCause() : ex);
                    }
                }
            }.execute();
        });

        setContentPane(content);
        setSize(480, 400);
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog; returns the added board, or null if the user cancelled. */
    static CompanyBoard ask(Component parent, AppServices app) {
        AddCompanyDialog d = new AddCompanyDialog(SwingUtilities.getWindowAncestor(parent), app);
        d.setVisible(true);
        return d.result;
    }
}
