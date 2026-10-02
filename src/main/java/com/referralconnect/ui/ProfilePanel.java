package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.model.CandidateProfile;
import com.referralconnect.service.AppServices;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.util.List;
import java.util.function.Consumer;

/**
 * Seekers keep the details a referrer needs here (they pre-fill every request); referrers set
 * their role and whether they are taking requests right now.
 */
final class ProfilePanel extends JPanel {

    private final AppServices app;
    private final Consumer<Account> onSaved;
    private Account account;

    ProfilePanel(AppServices app, Account account, Consumer<Account> onSaved) {
        super(new GridBagLayout());
        this.app = app;
        this.account = account;
        this.onSaved = onSaved;
        setOpaque(false);
        setBorder(Ui.padding(18, 16, 16, 16));
        build();
    }

    private void build() {
        removeAll();
        Ui.Card card = new Ui.Card(new BorderLayout(0, 14));
        card.setBorder(Ui.padding(22, 26, 22, 26));
        card.setPreferredSize(new Dimension(760, account.isReferrer() ? 440 : 540));
        card.add(account.isReferrer() ? referrerForm() : seekerForm(), BorderLayout.CENTER);
        add(card);
        revalidate();
        repaint();
    }

    private JComponent seekerForm() {
        CandidateProfile p = account.profile();
        Form form = new Form(2);
        form.full(null, Ui.label("Your referral profile", Theme.H2, Theme.TEXT));
        List<String> missing = p.missingForReferral();
        JLabel readiness = missing.isEmpty()
                ? Ui.label("Ready — referrers will get everything they need.", Theme.BODY_BOLD, Theme.SUCCESS)
                : Ui.label("Still needed before you can request referrals: " + String.join(", ", missing),
                Theme.BODY_BOLD, Theme.WARNING);
        form.full(null, readiness);
        JTextField name = form.field("Full name *", p.name(), "");
        JTextField email = form.field("Email *", p.email(), "");
        JTextField phone = form.field("Phone", p.phone(), "+91 …");
        JTextField resume = form.field("Resume link *", p.resumeLink(), "Google Drive / Dropbox link (view access)");
        JTextField linkedin = form.field("LinkedIn", p.linkedin(), "linkedin.com/in/…");
        JTextField github = form.field("GitHub / portfolio", p.github(), "github.com/…");
        JTextField education = form.field("Education", p.education(), "e.g. B.Tech CSE, 2026");
        JTextField experience = form.field("Experience", p.experience(), "e.g. Fresher, 1 internship");
        JTextField skills = new Form.HintField(p.skills(), "Java, SQL, Python, React …");
        form.full("Key skills *", skills);
        form.full(null, Ui.row(0, Ui.button("Save profile", Ui.Kind.PRIMARY, () -> Ui.attempt(this, () -> {
            account = app.auth.updateProfile(account.id(), new CandidateProfile(name.getText(), email.getText(),
                    phone.getText(), linkedin.getText(), github.getText(), resume.getText(), education.getText(),
                    experience.getText(), skills.getText()));
            onSaved.accept(account);
            build();
            Ui.info(this, "Profile saved.");
        }))));
        form.finish();
        return form;
    }

    private JComponent referrerForm() {
        CandidateProfile p = account.profile();
        Form form = new Form(2);
        form.full(null, Ui.label("Referrer settings", Theme.H2, Theme.TEXT));
        form.full(null, Ui.text("Seekers only see your name, role and company. Your email is never shown to "
                + "them; you reach out to candidates yourself from the inbox.", Theme.BODY, Theme.MUTED));
        JTextField name = form.field("Full name", p.name(), "");
        JTextField email = form.field("Email", p.email(), "");
        JTextField designation = form.field("Your role", account.designation(), "e.g. SDE-2");
        JTextField company = form.field("Company", account.companyName(), "");
        company.setEditable(false);
        JCheckBox accepting = new JCheckBox("I'm accepting new referral requests", account.acceptingRequests());
        accepting.setOpaque(false);
        accepting.setFont(Theme.BODY);
        form.full(null, accepting);
        form.full(null, Ui.text("Turn this off when you're busy. New requests go to other referrers at your "
                + "company; requests already in your inbox stay there.", Theme.SMALL, Theme.MUTED));
        form.full(null, Ui.row(0, Ui.button("Save settings", Ui.Kind.PRIMARY, () -> Ui.attempt(this, () -> {
            app.auth.updateProfile(account.id(), new CandidateProfile(name.getText(), email.getText(), p.phone(),
                    p.linkedin(), p.github(), p.resumeLink(), p.education(), p.experience(), p.skills()));
            account = app.auth.updateReferrerSettings(account.id(), designation.getText(), accepting.isSelected());
            onSaved.accept(account);
            build();
            Ui.info(this, "Settings saved.");
        }))));
        form.finish();
        return form;
    }
}
