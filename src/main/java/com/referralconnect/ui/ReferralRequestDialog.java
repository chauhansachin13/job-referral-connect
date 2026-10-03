package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.model.CandidateProfile;
import com.referralconnect.model.JobPosting;
import com.referralconnect.model.ReferralRequest;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.PitchWriter;
import com.referralconnect.service.ReferralService;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.time.Instant;

/**
 * Where a seeker writes the referral request: their details (pre-filled from the profile and
 * frozen into the request) plus a pitch. This is exactly what the referrer receives.
 */
final class ReferralRequestDialog extends JDialog {

    private final AppServices app;
    private final Account seeker;
    private final JobPosting job;
    private final ReferralRequest resubmitting;
    private ReferralRequest result;

    private final JTextField name;
    private final JTextField email;
    private final JTextField phone;
    private final JTextField linkedin;
    private final JTextField github;
    private final JTextField resume;
    private final JTextField education;
    private final JTextField experience;
    private final JComboBox<String> years;
    private final JTextField skills;
    private final JTextArea pitch;
    private final JCheckBox saveToProfile = new JCheckBox("Also save these details to my profile", true);

    private ReferralRequestDialog(Window owner, AppServices app, Account seeker, JobPosting job,
                                  ReferralRequest resubmitting) {
        super(owner, resubmitting == null ? "Request a referral" : "Update and resubmit", ModalityType.APPLICATION_MODAL);
        this.app = app;
        this.seeker = app.auth.require(seeker.id());
        this.job = job;
        this.resubmitting = resubmitting;
        CandidateProfile p = resubmitting != null ? resubmitting.candidate() : this.seeker.profile();

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBackground(Theme.SURFACE);
        content.setBorder(Ui.padding(20, 24, 18, 24));

        JPanel head = new JPanel(new BorderLayout(0, 4));
        head.setOpaque(false);
        head.add(Ui.label(resubmitting == null ? "Request a referral" : "Send the referrer what they asked for",
                Theme.H2, Theme.TEXT), BorderLayout.NORTH);
        head.add(Ui.text(job.title() + "  ·  " + job.company() + "  ·  " + job.location(), Theme.BODY, Theme.MUTED),
                BorderLayout.CENTER);
        JPanel notes = new JPanel();
        notes.setOpaque(false);
        notes.setLayout(new javax.swing.BoxLayout(notes, javax.swing.BoxLayout.Y_AXIS));
        notes.add(javax.swing.Box.createVerticalStrut(6));
        notes.add(Ui.row(6, Ui.experiencePill(job), new Ui.Pill(job.category().label(), Theme.PRIMARY_TEXT,
                Theme.PRIMARY_SOFT)));
        if (resubmitting != null && !resubmitting.referrerNote().isEmpty()) {
            notes.add(javax.swing.Box.createVerticalStrut(8));
            notes.add(callout("Referrer's note: " + resubmitting.referrerNote(), Theme.WARNING, Theme.WARNING_SOFT));
        } else if (p.yearsKnown() && job.requirements().known() && !job.requirements().fits(p.years())) {
            notes.add(javax.swing.Box.createVerticalStrut(8));
            notes.add(callout("This role " + (job.requirements().preferredOnly() ? "prefers " : "asks for ")
                    + job.requirements().yearsText() + " of experience; you have " + p.experiencePhrase()
                    + ". You can still ask — say clearly why you're ready.", Theme.WARNING, Theme.WARNING_SOFT));
        }
        head.add(notes, BorderLayout.SOUTH);
        content.add(head, BorderLayout.NORTH);

        Form form = new Form(2);
        form.full(null, Ui.label("Your details — sent to the referrer", Theme.H3, Theme.TEXT));
        name = form.field("Full name *", p.name(), "");
        email = form.field("Email *", p.email(), "");
        phone = form.field("Phone", p.phone(), "+91 …");
        resume = form.field("Resume link *", p.resumeLink(), "Google Drive / Dropbox link");
        linkedin = form.field("LinkedIn", p.linkedin(), "linkedin.com/in/…");
        github = form.field("GitHub / portfolio", p.github(), "github.com/…");
        education = form.field("Education", p.education(), "e.g. B.Tech CSE, 2026");
        years = ProfilePanel.yearsBox(p.years());
        form.cell("Total work experience", years);
        experience = new Form.HintField(p.experience(), "e.g. Fresher, 1 internship");
        form.full("Experience details", experience);
        skills = new Form.HintField(p.skills(), "Java, SQL, Python, React …");
        form.full("Key skills *", skills);
        Ui.FlatButton draft = Ui.button("Draft for me", Icons.Glyph.SPARKLE, Ui.Kind.SECONDARY, () -> { }).compact();
        draft.setName("draftPitch");
        draft.setToolTipText("Write a first draft from your profile and this posting's skills");
        JPanel pitchHead = new JPanel(new BorderLayout());
        pitchHead.setOpaque(false);
        pitchHead.add(Ui.label("Why are you a good fit for this role? *", Theme.SMALL_BOLD, Theme.TEXT_2),
                BorderLayout.WEST);
        pitchHead.add(draft, BorderLayout.EAST);
        form.full(null, pitchHead);
        pitch = form.area(null, resubmitting != null ? resubmitting.pitch() : "", 5,
                "2–4 lines: relevant projects, internships, and why this team.");
        pitch.setName("pitch");
        draft.addActionListener(e -> {
            if (!pitch.getText().isBlank() && !Ui.confirm(this, "Replace what you wrote with a draft?", "Draft pitch")) {
                return;
            }
            pitch.setText(PitchWriter.draft(candidate(), job));
            pitch.requestFocusInWindow();
        });
        JLabel counter = Ui.label(" ", Theme.SMALL, Theme.MUTED);
        pitch.getDocument().addDocumentListener(new DocumentListener() {
            void update() {
                int n = pitch.getText().trim().length();
                counter.setText(n < ReferralService.MIN_PITCH_LENGTH
                        ? (ReferralService.MIN_PITCH_LENGTH - n) + " more characters needed"
                        : n + " characters");
                counter.setForeground(n < ReferralService.MIN_PITCH_LENGTH ? Theme.WARNING : Theme.SUCCESS);
            }

            public void insertUpdate(DocumentEvent e) {
                update();
            }

            public void removeUpdate(DocumentEvent e) {
                update();
            }

            public void changedUpdate(DocumentEvent e) {
                update();
            }
        });
        form.full(null, counter);
        saveToProfile.setOpaque(false);
        saveToProfile.setFont(Theme.BODY);
        form.full(null, saveToProfile);
        form.finish();
        content.add(Ui.bareScroll(form), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setOpaque(false);
        buttons.add(Ui.button("Preview what the referrer sees", Icons.Glyph.SEARCH, Ui.Kind.SUBTLE, this::preview), BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(Ui.button("Cancel", Ui.Kind.SECONDARY, this::dispose));
        Ui.FlatButton sendButton = Ui.button(resubmitting == null ? "Send request" : "Resubmit", Icons.Glyph.SEND, Ui.Kind.PRIMARY, this::send);
        sendButton.setName("sendRequest");
        right.add(sendButton);
        buttons.add(right, BorderLayout.EAST);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        setSize(760, 800);
        setLocationRelativeTo(owner);
    }

    private CandidateProfile candidate() {
        return new CandidateProfile(name.getText(), email.getText(), phone.getText(), linkedin.getText(),
                github.getText(), resume.getText(), education.getText(), experience.getText(), skills.getText(),
                years.getSelectedIndex() - 1);
    }

    private static JComponent callout(String text, java.awt.Color fg, java.awt.Color bg) {
        JPanel box = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = Laf.smooth(g);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        box.setOpaque(false);
        box.setBorder(Ui.padding(9, 12, 9, 12));
        box.add(Ui.text(text, Theme.BODY, fg), BorderLayout.CENTER);
        return box;
    }

    private void preview() {
        ReferralRequest draft = new ReferralRequest("(draft)", Instant.now(), seeker.id(), "", "", job,
                candidate(), pitch.getText());
        JTextArea text = new JTextArea(ReferralService.packet(draft), 26, 70);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        text.setEditable(false);
        text.setBackground(Theme.SURFACE);
        text.setForeground(Theme.TEXT);
        text.setBorder(Ui.padding(10, 12, 10, 12));
        JOptionPane.showMessageDialog(this, Ui.scroll(text), "Referral packet preview", JOptionPane.PLAIN_MESSAGE);
    }

    private void send() {
        Ui.attempt(this, () -> {
            CandidateProfile c = candidate();
            result = resubmitting == null
                    ? app.referrals.requestReferral(seeker.id(), job, c, pitch.getText())
                    : app.referrals.resubmit(seeker.id(), resubmitting.id(), c, pitch.getText());
            if (saveToProfile.isSelected()) {
                Ui.attempt(this, () -> app.auth.updateProfile(seeker.id(), c));
            }
            dispose();
        });
    }

    /** Opens the dialog for a new request; returns the sent request, or null if cancelled. */
    static ReferralRequest compose(Component parent, AppServices app, Account seeker, JobPosting job) {
        ReferralRequestDialog d = new ReferralRequestDialog(SwingUtilities.getWindowAncestor(parent), app, seeker,
                job, null);
        d.setVisible(true);
        return d.result;
    }

    /** Opens the dialog to answer a "needs more info"; returns the updated request, or null. */
    static ReferralRequest resubmit(Component parent, AppServices app, Account seeker, ReferralRequest request) {
        ReferralRequestDialog d = new ReferralRequestDialog(SwingUtilities.getWindowAncestor(parent), app, seeker,
                request.job(), request);
        d.setVisible(true);
        return d.result;
    }
}
