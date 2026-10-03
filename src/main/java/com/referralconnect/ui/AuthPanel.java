package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.DemoData;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.function.Consumer;

/** Sign-in and account creation for both seekers and referrers. */
public final class AuthPanel extends JPanel {

    private final AppServices app;
    private final Consumer<Account> onSignedIn;
    private final CardLayout cards = new CardLayout();
    private final JPanel cardHolder = new JPanel(cards);
    private Ui.FlatButton signInTab;
    private Ui.FlatButton createTab;
    private JTextField signInEmail;
    private JPasswordField signInPassword;

    public AuthPanel(AppServices app, Consumer<Account> onSignedIn) {
        super(new GridLayout(1, 2));
        this.app = app;
        this.onSignedIn = onSignedIn;
        add(brandPanel());
        add(formSide());
    }

    // ---------------------------------------------------------------- left: what this is

    private JComponent brandPanel() {
        JPanel p = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Laf.smooth(g);
                g2.setPaint(new GradientPaint(0, 0, new Color(0x111633), getWidth(), getHeight(), new Color(0x4338CA)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(255, 255, 255, 14));
                g2.fillOval(getWidth() - 260, -120, 420, 420);
                g2.fillOval(-120, getHeight() - 220, 340, 340);
                g2.dispose();
            }
        };
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setMaximumSize(new Dimension(470, 640));

        JLabel logo = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Laf.smooth(g);
                g2.setPaint(new GradientPaint(0, 0, new Color(0x818CF8), getWidth(), getHeight(), new Color(0x6366F1)));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                Icons.get(Icons.Glyph.ZAP, 26, Color.WHITE).paintIcon(this, g2, (getWidth() - 26) / 2, (getHeight() - 26) / 2);
                g2.dispose();
            }
        };
        logo.setPreferredSize(new Dimension(48, 48));
        logo.setMaximumSize(new Dimension(48, 48));
        content.add(logo);
        content.add(Box.createVerticalStrut(18));
        content.add(Ui.label("Job Referral Connect", Theme.font(Font.BOLD, 32), Color.WHITE));
        content.add(Box.createVerticalStrut(8));
        content.add(Ui.text("Fresh tech openings in India, the experience each one asks for, and one click to ask an "
                + "employee for a referral.", Theme.font(Font.PLAIN, 15), new Color(0xC7D2FE)));
        content.add(Box.createVerticalStrut(30));
        content.add(step(Icons.Glyph.SEARCH, "Scan", "Software, data, AI / ML and data-engineering jobs and internships "
                + "from " + app.directory.all().size() + " companies — Google, Microsoft, Amazon, Apple, Qualcomm, "
                + "NVIDIA, Walmart, JPMorgan and many more."));
        content.add(Box.createVerticalStrut(18));
        content.add(step(Icons.Glyph.CLOCK, "Check eligibility", "See the minimum experience, degree and batch each "
                + "posting asks for, and how well it matches your skills."));
        content.add(Box.createVerticalStrut(18));
        content.add(step(Icons.Glyph.SEND, "Get referred", "Send your profile, resume and pitch to an employee there, "
                + "chat with them, and track every update."));
        for (Component c : content.getComponents()) {
            ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        }
        p.add(content);
        return p;
    }

    private JComponent step(Icons.Glyph glyph, String heading, String body) {
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(false);
        JLabel badge = new JLabel(Icons.get(glyph, 18, Color.WHITE), JLabel.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Laf.smooth(g);
                g2.setColor(new Color(255, 255, 255, 34));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setPreferredSize(new Dimension(38, 38));
        JPanel badgeHolder = new JPanel(new BorderLayout());
        badgeHolder.setOpaque(false);
        badgeHolder.add(badge, BorderLayout.NORTH);
        row.add(badgeHolder, BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(Ui.label(heading, Theme.H3, Color.WHITE));
        text.add(Box.createVerticalStrut(3));
        text.add(Ui.text(body, Theme.BODY, new Color(0xE0E7FF)));
        row.add(text, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(470, 96));
        return row;
    }

    // ---------------------------------------------------------------- right: forms

    private JComponent formSide() {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Theme.BG);
        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 14));
        top.setOpaque(false);
        Widgets.Toggle dark = new Widgets.Toggle(Theme.isDark());
        dark.setName("authDarkMode");
        JLabel darkLabel = Ui.label("Dark mode", Theme.SMALL, Theme.MUTED);
        darkLabel.setIcon(Icons.get(Icons.Glyph.MOON, 15, Theme.MUTED));
        darkLabel.setIconTextGap(6);
        top.add(darkLabel);
        top.add(dark);
        dark.addActionListener(e -> {
            java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(this);
            if (w instanceof AppFrame f) {
                f.toggleTheme();
            }
        });
        side.add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        Ui.Card card = new Ui.Card(new BorderLayout(0, 18));
        card.setBorder(Ui.padding(28, 30, 26, 30));
        card.setPreferredSize(new Dimension(460, 620));

        JPanel tabs = new JPanel(new GridLayout(1, 2, 8, 0));
        tabs.setOpaque(false);
        signInTab = Ui.button("Sign in", Ui.Kind.PRIMARY, () -> showCard("signin"));
        signInTab.setName("signInTab");
        createTab = Ui.button("Create account", Ui.Kind.SECONDARY, () -> showCard("create"));
        createTab.setName("createTab");
        tabs.add(signInTab);
        tabs.add(createTab);
        card.add(tabs, BorderLayout.NORTH);

        cardHolder.setOpaque(false);
        cardHolder.add(signInForm(), "signin");
        cardHolder.add(createForm(), "create");
        card.add(cardHolder, BorderLayout.CENTER);
        center.add(card);
        side.add(center, BorderLayout.CENTER);
        return side;
    }

    private void showCard(String name) {
        cards.show(cardHolder, name);
        boolean signIn = name.equals("signin");
        signInTab.setKind(signIn ? Ui.Kind.PRIMARY : Ui.Kind.SECONDARY);
        createTab.setKind(signIn ? Ui.Kind.SECONDARY : Ui.Kind.PRIMARY);
    }

    private JComponent signInForm() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        Form form = new Form(1);
        form.cell(null, SeekerHome.cardHead("Welcome back", "Sign in as a job seeker or a referrer", null));
        signInEmail = form.field("Email", "", "you@example.com");
        signInEmail.setName("signInEmail");
        signInPassword = Form.password();
        signInPassword.setName("signInPassword");
        form.cell("Password", signInPassword);
        Runnable submit = () -> Ui.attempt(this, () ->
                onSignedIn.accept(app.auth.login(signInEmail.getText(), new String(signInPassword.getPassword()))));
        signInPassword.addActionListener(e -> submit.run());
        Ui.FlatButton signIn = Ui.button("Sign in", Icons.Glyph.CHEVRON_RIGHT, Ui.Kind.PRIMARY, submit);
        signIn.setName("signIn");
        form.cell(null, Ui.row(0, signIn));
        if (demoAvailable()) {
            form.cell(null, Ui.label("Try the demo accounts", Theme.SMALL_BOLD, Theme.MUTED));
            form.cell(null, Ui.row(8,
                    Ui.button("As a job seeker", Icons.Glyph.USER, Ui.Kind.SECONDARY,
                            () -> fill(DemoData.SEEKER_EMAIL)).compact(),
                    Ui.button("As a Google referrer", Icons.Glyph.USERS, Ui.Kind.SECONDARY,
                            () -> fill(DemoData.referrerEmail("Google"))).compact()));
        } else {
            form.cell(null, Ui.text("New here? Choose \"Create account\" — it takes a minute.", Theme.SMALL, Theme.MUTED));
        }
        form.finish();
        p.add(form, BorderLayout.CENTER);
        return p;
    }

    private boolean demoAvailable() {
        return app.store.read(s -> s.accounts.stream().anyMatch(a -> a.email().equals(DemoData.SEEKER_EMAIL)));
    }

    private void fill(String email) {
        signInEmail.setText(email);
        signInPassword.setText(DemoData.PASSWORD);
        signInPassword.requestFocusInWindow();
    }

    private JComponent createForm() {
        Form form = new Form(1);
        JRadioButton seeker = new JRadioButton("I'm looking for a job", true);
        JRadioButton referrer = new JRadioButton("I can refer at my company");
        for (JRadioButton b : new JRadioButton[]{seeker, referrer}) {
            b.setOpaque(false);
            b.setFont(Theme.BODY);
            b.setForeground(Theme.TEXT);
            b.setFocusPainted(false);
        }
        referrer.setName("roleReferrer");
        ButtonGroup g = new ButtonGroup();
        g.add(seeker);
        g.add(referrer);
        form.cell("I am…", Ui.row(12, seeker, referrer));
        JTextField name = form.field("Full name", "", "e.g. Priya Sharma");
        name.setName("createName");
        JTextField email = form.field("Email", "", "you@example.com");
        email.setName("createEmail");
        JPasswordField password = Form.password();
        password.setName("createPassword");
        form.cell("Password (8+ characters)", password);

        // Referrer-only fields.
        JComboBox<CompanyBoard> company = new JComboBox<>(app.directory.all().toArray(new CompanyBoard[0]));
        company.setName("createCompany");
        company.setMaximumRowCount(16);
        Ui.FlatButton addCompany = Ui.button("Not listed?", Icons.Glyph.PLUS, Ui.Kind.SECONDARY, () -> {
            CompanyBoard added = AddCompanyDialog.ask(this, app);
            if (added != null) {
                company.removeAllItems();
                app.directory.all().forEach(company::addItem);
                for (int i = 0; i < company.getItemCount(); i++) {
                    if (company.getItemAt(i).key().equals(added.key())) {
                        company.setSelectedIndex(i);
                    }
                }
            }
        }).compact();
        JPanel companyRow = new JPanel(new BorderLayout(8, 0));
        companyRow.setOpaque(false);
        companyRow.add(company, BorderLayout.CENTER);
        companyRow.add(addCompany, BorderLayout.EAST);
        JPanel companyBlock = new JPanel(new BorderLayout(0, 5));
        companyBlock.setOpaque(false);
        companyBlock.add(Ui.label("Company you can refer for", Theme.SMALL_BOLD, Theme.TEXT_2), BorderLayout.NORTH);
        companyBlock.add(companyRow, BorderLayout.CENTER);
        form.cell(null, companyBlock);
        JTextField designation = new Form.HintField("", "e.g. SDE-2, Data Scientist");
        designation.setName("createDesignation");
        JPanel designationBlock = new JPanel(new BorderLayout(0, 5));
        designationBlock.setOpaque(false);
        designationBlock.add(Ui.label("Your role there", Theme.SMALL_BOLD, Theme.TEXT_2), BorderLayout.NORTH);
        designationBlock.add(designation, BorderLayout.CENTER);
        form.cell(null, designationBlock);

        Runnable toggle = () -> {
            companyBlock.setVisible(referrer.isSelected());
            designationBlock.setVisible(referrer.isSelected());
        };
        seeker.addActionListener(e -> toggle.run());
        referrer.addActionListener(e -> toggle.run());
        toggle.run();

        Ui.FlatButton create = Ui.button("Create account", Icons.Glyph.CHECK, Ui.Kind.PRIMARY, () -> Ui.attempt(this, () -> {
            String pw = new String(password.getPassword());
            Account account = referrer.isSelected()
                    ? app.auth.registerReferrer(name.getText(), email.getText(), pw,
                    (CompanyBoard) company.getSelectedItem(), designation.getText())
                    : app.auth.registerSeeker(name.getText(), email.getText(), pw);
            onSignedIn.accept(account);
        }));
        create.setName("createAccount");
        form.cell(null, Ui.row(0, create));
        form.finish();
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(Ui.bareScroll(form), BorderLayout.CENTER);
        return wrap;
    }
}
