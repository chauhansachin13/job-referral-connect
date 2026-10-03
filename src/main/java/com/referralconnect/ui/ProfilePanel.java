package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.model.CandidateProfile;
import com.referralconnect.model.JobCategory;
import com.referralconnect.model.UserPrefs;
import com.referralconnect.scan.IndiaLocations;
import com.referralconnect.service.AppServices;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Seekers keep the details a referrer needs here (they pre-fill every request), plus their years
 * of experience and the roles and cities they want, which drive match scores. Referrers set their
 * role and whether they are taking requests. Everyone can switch dark mode and auto-scan.
 */
final class ProfilePanel extends JPanel implements AppFrame.Live {

    private final AppServices app;
    private final AppFrame.Context ctx;
    private final Consumer<Account> onSaved;
    private Account account;
    private final JPanel body = new ScrollablePanel(null);

    ProfilePanel(AppServices app, Account account, AppFrame.Context ctx, Consumer<Account> onSaved) {
        super(new BorderLayout());
        this.app = app;
        this.account = account;
        this.ctx = ctx;
        this.onSaved = onSaved;
        setOpaque(false);
        body.setLayout(new BorderLayout());
        body.setBorder(Ui.padding(4, 24, 24, 24));
        add(Ui.bareScroll(body), BorderLayout.CENTER);
        build();
    }

    @Override
    public void refreshData() {
        // The forms keep what the user is typing; they are rebuilt only after a save.
    }

    /** The form on the left takes the spare width; the side column is a fixed width. */
    private static final int SIDE_WIDTH = 430;

    private void build() {
        body.removeAll();
        account = app.auth.require(account.id());
        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.add(card(account.isReferrer() ? referrerForm() : seekerForm()), BorderLayout.NORTH);
        body.add(left, BorderLayout.CENTER);

        JPanel side = new JPanel() {
            @Override
            public java.awt.Dimension getPreferredSize() {
                return new java.awt.Dimension(SIDE_WIDTH, super.getPreferredSize().height);
            }
        };
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(Ui.padding(0, 14, 0, 0));
        if (!account.isReferrer()) {
            side.add(card(strength()));
            side.add(Box.createVerticalStrut(14));
            side.add(card(preferences()));
            side.add(Box.createVerticalStrut(14));
        }
        side.add(card(settings()));
        side.add(Box.createVerticalGlue());
        body.add(side, BorderLayout.EAST);
        body.revalidate();
        body.repaint();
    }

    private static JComponent card(JComponent content) {
        Ui.Card card = new Ui.Card(new BorderLayout()) {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        card.setBorder(Ui.padding(20, 22, 18, 22));
        card.add(content, BorderLayout.CENTER);
        card.setAlignmentX(LEFT_ALIGNMENT);
        return card;
    }

    static String[] yearChoices() {
        List<String> out = new ArrayList<>();
        out.add("Not set");
        out.add("Fresher (0 years)");
        for (int y = 1; y <= 20; y++) {
            out.add(y + (y == 1 ? " year" : " years") + (y == 20 ? "+" : ""));
        }
        return out.toArray(new String[0]);
    }

    static JComboBox<String> yearsBox(int years) {
        JComboBox<String> box = new JComboBox<>(yearChoices());
        box.setSelectedIndex(Math.min(21, years + 1));
        box.setMaximumRowCount(12);
        box.setName("yearsBox");
        return box;
    }

    private JComponent seekerForm() {
        CandidateProfile p = account.profile();
        Form form = new Form(2);
        form.full(null, SeekerHome.cardHead("Your referral profile", "Pre-filled into every request and shown to "
                + "the referrer exactly as you write it.", null));
        JLabel readiness;
        List<String> missing = p.missingForReferral();
        if (missing.isEmpty()) {
            readiness = Ui.iconLabel("Ready — referrers will get everything they need.", Icons.Glyph.CHECK,
                    Theme.BODY_BOLD, Theme.SUCCESS);
        } else {
            readiness = Ui.iconLabel("Still needed before you can request referrals: " + String.join(", ", missing),
                    Icons.Glyph.ZAP, Theme.BODY_BOLD, Theme.WARNING);
        }
        readiness.setName("readiness");
        form.full(null, readiness);
        JTextField name = form.field("Full name *", p.name(), "");
        JTextField email = form.field("Email *", p.email(), "");
        JTextField phone = form.field("Phone", p.phone(), "+91 …");
        JTextField resume = form.field("Resume link *", p.resumeLink(), "Google Drive / Dropbox link (view access)");
        resume.setName("resumeField");
        JTextField linkedin = form.field("LinkedIn", p.linkedin(), "linkedin.com/in/…");
        JTextField github = form.field("GitHub / portfolio", p.github(), "github.com/…");
        JTextField education = form.field("Education", p.education(), "e.g. B.Tech CSE, 2026");
        JComboBox<String> years = yearsBox(p.years());
        form.cell("Total work experience", years);
        JTextField experience = new Form.HintField(p.experience(), "e.g. 2 internships (backend, data)");
        form.full("Experience details", experience);
        JTextField skills = new Form.HintField(p.skills(), "Java, SQL, Python, React …");
        skills.setName("skillsField");
        form.full("Key skills * (comma-separated — used for match scores)", skills);
        Ui.FlatButton save = Ui.button("Save profile", Icons.Glyph.CHECK, Ui.Kind.PRIMARY, () -> Ui.attempt(this, () -> {
            account = app.auth.updateProfile(account.id(), new CandidateProfile(name.getText(), email.getText(),
                    phone.getText(), linkedin.getText(), github.getText(), resume.getText(), education.getText(),
                    experience.getText(), skills.getText(), years.getSelectedIndex() - 1));
            onSaved.accept(account);
            build();
            Ui.toast(this, "Profile saved.");
        }));
        save.setName("saveProfile");
        form.full(null, Ui.row(0, save));
        form.finish();
        return form;
    }

    private JComponent strength() {
        CandidateProfile p = account.profile();
        JPanel panel = new JPanel(new BorderLayout(16, 0));
        panel.setOpaque(false);
        Charts.Ring ring = new Charts.Ring(72);
        int pct = p.completeness();
        ring.setValue(pct, pct >= 90 ? Theme.SUCCESS : pct >= 60 ? Theme.PRIMARY : Theme.WARNING);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(ring, BorderLayout.NORTH);
        panel.add(holder, BorderLayout.WEST);
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.add(Ui.label("Profile strength", Theme.H3, Theme.TEXT));
        list.add(Box.createVerticalStrut(6));
        String[][] items = {{"Resume link", p.resumeLink()}, {"Skills", p.skills()}, {"Years of experience",
                p.yearsKnown() ? "y" : ""}, {"Education", p.education()}, {"LinkedIn", p.linkedin()},
                {"GitHub / portfolio", p.github()}, {"Phone", p.phone()}};
        for (String[] item : items) {
            boolean done = !item[1].isEmpty();
            list.add(Ui.iconLabel(item[0], done ? Icons.Glyph.CHECK : Icons.Glyph.PLUS, Theme.SMALL,
                    done ? Theme.SUCCESS : Theme.MUTED));
            list.add(Box.createVerticalStrut(3));
        }
        panel.add(list, BorderLayout.CENTER);
        return panel;
    }

    private JComponent preferences() {
        UserPrefs prefs = app.prefs.of(account.id());
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(SeekerHome.cardHead("Job preferences", "Openings in these roles and cities score higher", null));
        p.add(Box.createVerticalStrut(12));
        p.add(Ui.sectionTitle("Roles"));
        p.add(Box.createVerticalStrut(6));
        List<Widgets.Chip> roleChips = new ArrayList<>();
        JPanel roles = Ui.row(6);
        ((FlowLayout) roles.getLayout()).setVgap(6);
        for (JobCategory c : JobCategory.values()) {
            Widgets.Chip chip = new Widgets.Chip(c.label(), prefs.preferredRoles().contains(c));
            chip.setName("role-" + c.name());
            roleChips.add(chip);
            roles.add(chip);
        }
        p.add(roles);
        p.add(Box.createVerticalStrut(10));
        p.add(Ui.sectionTitle("Cities"));
        p.add(Box.createVerticalStrut(6));
        List<Widgets.Chip> cityChips = new ArrayList<>();
        JPanel cities = Ui.row(6);
        ((FlowLayout) cities.getLayout()).setVgap(6);
        for (String city : IndiaLocations.cityChoices()) {
            Widgets.Chip chip = new Widgets.Chip(city, prefs.preferredCities().contains(city));
            cityChips.add(chip);
            cities.add(chip);
        }
        p.add(cities);
        p.add(Box.createVerticalStrut(12));
        Ui.FlatButton save = Ui.button("Save preferences", Icons.Glyph.CHECK, Ui.Kind.SECONDARY, () -> {
            Set<JobCategory> r = EnumSet.noneOf(JobCategory.class);
            for (int i = 0; i < roleChips.size(); i++) {
                if (roleChips.get(i).isSelected()) {
                    r.add(JobCategory.values()[i]);
                }
            }
            Set<String> cs = new LinkedHashSet<>();
            cityChips.stream().filter(Widgets.Chip::isSelected).forEach(ch -> cs.add(ch.text()));
            app.prefs.setPreferences(account.id(), r, cs);
            onSaved.accept(account);
            Ui.toast(this, "Preferences saved. Match scores now favour " + (r.isEmpty() ? "any role" : r.size()
                    + (r.size() == 1 ? " role" : " roles")) + (cs.isEmpty() ? "" : " in " + String.join(", ", cs)) + ".");
        }).compact();
        save.setName("savePreferences");
        p.add(Ui.row(0, save));
        return p;
    }

    private JComponent settings() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(SeekerHome.cardHead("App settings", null, null));
        p.add(Box.createVerticalStrut(6));
        Widgets.Toggle dark = new Widgets.Toggle(Theme.isDark());
        dark.setName("darkModeSetting");
        dark.addActionListener(e -> ctx.toggleTheme().run());
        p.add(Widgets.settingRow("Dark mode", "Easier on the eyes at night (" + Shell.shortcut("D") + ")", dark));
        Widgets.Toggle auto = new Widgets.Toggle(app.prefs.autoScan());
        auto.setName("autoScanSetting");
        auto.addActionListener(e -> {
            app.prefs.setAutoScan(auto.isSelected());
            Ui.toast(this, auto.isSelected() ? "Auto-scan on: openings refresh every 30 minutes while the app is open."
                    : "Auto-scan off. Use the refresh button to scan.", Toast.Tone.INFO);
        });
        p.add(Widgets.settingRow("Auto-scan", "Fetch fresh openings every 30 minutes while the app is open", auto));
        p.add(Box.createVerticalStrut(6));
        p.add(Ui.text("Keyboard: " + Shell.shortcut("1") + "–" + Shell.shortcut("7") + " switch pages · "
                + Shell.shortcut("F") + " search openings · " + Shell.shortcut("R") + " scan · "
                + Shell.shortcut("D") + " dark mode", Theme.SMALL, Theme.MUTED));
        return p;
    }

    private JComponent referrerForm() {
        CandidateProfile p = account.profile();
        Form form = new Form(2);
        form.full(null, SeekerHome.cardHead("Referrer settings", "Seekers only see your name, role and company. Your "
                + "email is never shown to them; you reach candidates from the inbox.", null));
        JTextField name = form.field("Full name", p.name(), "");
        JTextField email = form.field("Email", p.email(), "");
        JTextField designation = form.field("Your role", account.designation(), "e.g. SDE-2");
        JTextField company = form.field("Company", account.companyName(), "");
        company.setEditable(false);
        Widgets.Toggle accepting = new Widgets.Toggle(account.acceptingRequests());
        accepting.setName("acceptingToggle");
        form.full(null, Widgets.settingRow("Accepting new referral requests", "Turn this off when you're busy. New "
                + "requests go to other referrers at your company; ones already in your inbox stay.", accepting));
        Ui.FlatButton save = Ui.button("Save settings", Icons.Glyph.CHECK, Ui.Kind.PRIMARY, () -> Ui.attempt(this, () -> {
            app.auth.updateProfile(account.id(), new CandidateProfile(name.getText(), email.getText(), p.phone(),
                    p.linkedin(), p.github(), p.resumeLink(), p.education(), p.experience(), p.skills(), p.years()));
            account = app.auth.updateReferrerSettings(account.id(), designation.getText(), accepting.isSelected());
            onSaved.accept(account);
            build();
            Ui.toast(this, "Settings saved.");
        }));
        save.setName("saveSettings");
        form.full(null, Ui.row(0, save));
        form.finish();
        return form;
    }
}
