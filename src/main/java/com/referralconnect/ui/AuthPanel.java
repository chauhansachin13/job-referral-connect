package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.CompanyDirectory;

import javax.swing.BorderFactory;
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
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, Theme.HEADER_FROM, getWidth(), getHeight(), Theme.HEADER_TO));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setMaximumSize(new Dimension(460, 600));

        JLabel title = Ui.label("Job Referral Connect", Theme.font(Font.BOLD, 34), Color.WHITE);
        content.add(title);
        content.add(Box.createVerticalStrut(8));
        content.add(Ui.label("Fresh openings in India. One click to ask for a referral.", Theme.font(Font.PLAIN, 16),
                new Color(0xC7D2FE)));
        content.add(Box.createVerticalStrut(36));
        content.add(step("1", "Scan", "Recent Software, Data and FDE jobs and internships in India from "
                + CompanyDirectory.SEED.size() + " companies — Google, Microsoft, Amazon, Apple, NVIDIA, "
                + "Salesforce, JPMorgan and many more."));
        content.add(Box.createVerticalStrut(20));
        content.add(step("2", "Request", "Pick an opening and send your profile, resume and pitch to an "
                + "employee there who has signed up to refer."));
        content.add(Box.createVerticalStrut(20));
        content.add(step("3", "Track", "Referrers mark requests Referred, ask for more info or decline. "
                + "You see every update."));
        for (Component c : content.getComponents()) {
            ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        }
        p.add(content);
        return p;
    }

    private JComponent step(String number, String heading, String body) {
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(false);
        JLabel badge = new JLabel(number, JLabel.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 40));
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setFont(Theme.H3);
        badge.setForeground(Color.WHITE);
        badge.setPreferredSize(new Dimension(36, 36));
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
        row.setMaximumSize(new Dimension(460, 90));
        return row;
    }

    // ---------------------------------------------------------------- right: forms

    private JComponent formSide() {
        JPanel side = new JPanel(new GridBagLayout());
        side.setBackground(Theme.BG);
        Ui.Card card = new Ui.Card(new BorderLayout(0, 16));
        card.setBorder(Ui.padding(26, 28, 26, 28));
        card.setPreferredSize(new Dimension(450, 600));

        JPanel tabs = new JPanel(new GridLayout(1, 2, 8, 0));
        tabs.setOpaque(false);
        signInTab = Ui.button("Sign in", Ui.Kind.PRIMARY, () -> showCard("signin"));
        createTab = Ui.button("Create account", Ui.Kind.SECONDARY, () -> showCard("create"));
        tabs.add(signInTab);
        tabs.add(createTab);
        card.add(tabs, BorderLayout.NORTH);

        cardHolder.setOpaque(false);
        cardHolder.add(signInForm(), "signin");
        cardHolder.add(createForm(), "create");
        card.add(cardHolder, BorderLayout.CENTER);
        side.add(card);
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
        form.cell(null, Ui.label("Welcome back", Theme.H2, Theme.TEXT));
        JTextField email = form.field("Email", "", "you@example.com");
        JPasswordField password = Form.password();
        form.cell("Password", password);
        Runnable submit = () -> Ui.attempt(this, () ->
                onSignedIn.accept(app.auth.login(email.getText(), new String(password.getPassword()))));
        password.addActionListener(e -> submit.run());
        form.cell(null, Ui.row(0, Ui.button("Sign in", Ui.Kind.PRIMARY, submit)));
        form.cell(null, Ui.text("Job seekers and referrers sign in here. New? Choose \"Create account\".",
                Theme.SMALL, Theme.MUTED));
        form.finish();
        p.add(form, BorderLayout.CENTER);
        return p;
    }

    private JComponent createForm() {
        Form form = new Form(1);
        JRadioButton seeker = new JRadioButton("I'm looking for a job", true);
        JRadioButton referrer = new JRadioButton("I can refer at my company");
        for (JRadioButton b : new JRadioButton[]{seeker, referrer}) {
            b.setOpaque(false);
            b.setFont(Theme.BODY);
        }
        ButtonGroup g = new ButtonGroup();
        g.add(seeker);
        g.add(referrer);
        form.cell("I am…", Ui.row(12, seeker, referrer));
        JTextField name = form.field("Full name", "", "e.g. Priya Sharma");
        JTextField email = form.field("Email", "", "you@example.com");
        JPasswordField password = Form.password();
        form.cell("Password (8+ characters)", password);

        // Referrer-only fields.
        JComboBox<CompanyBoard> company = new JComboBox<>(app.directory.all().toArray(new CompanyBoard[0]));
        company.setFont(Theme.BODY);
        Ui.FlatButton addCompany = Ui.button("Not listed? Add it", Ui.Kind.SECONDARY, () -> {
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
        });
        JPanel companyRow = new JPanel(new BorderLayout(8, 0));
        companyRow.setOpaque(false);
        companyRow.add(company, BorderLayout.CENTER);
        companyRow.add(addCompany, BorderLayout.EAST);
        JLabel companyLabel = Ui.label("Company you can refer for", Theme.SMALL_BOLD, Theme.MUTED);
        JPanel companyBlock = new JPanel(new BorderLayout(0, 4));
        companyBlock.setOpaque(false);
        companyBlock.add(companyLabel, BorderLayout.NORTH);
        companyBlock.add(companyRow, BorderLayout.CENTER);
        form.cell(null, companyBlock);
        JTextField designation = new Form.HintField("", "e.g. SDE-2, Data Scientist");
        JPanel designationBlock = new JPanel(new BorderLayout(0, 4));
        designationBlock.setOpaque(false);
        designationBlock.add(Ui.label("Your role there", Theme.SMALL_BOLD, Theme.MUTED), BorderLayout.NORTH);
        designationBlock.add(designation, BorderLayout.CENTER);
        form.cell(null, designationBlock);

        Runnable toggle = () -> {
            companyBlock.setVisible(referrer.isSelected());
            designationBlock.setVisible(referrer.isSelected());
        };
        seeker.addActionListener(e -> toggle.run());
        referrer.addActionListener(e -> toggle.run());
        toggle.run();

        form.cell(null, Ui.row(0, Ui.button("Create account", Ui.Kind.PRIMARY, () -> Ui.attempt(this, () -> {
            String pw = new String(password.getPassword());
            Account account = referrer.isSelected()
                    ? app.auth.registerReferrer(name.getText(), email.getText(), pw,
                    (CompanyBoard) company.getSelectedItem(), designation.getText())
                    : app.auth.registerSeeker(name.getText(), email.getText(), pw);
            onSignedIn.accept(account);
        }))));
        form.finish();
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(Ui.bareScroll(form), BorderLayout.CENTER);
        wrap.setBorder(BorderFactory.createEmptyBorder());
        return wrap;
    }
}
