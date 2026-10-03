package com.referralconnect.ui;

import com.referralconnect.model.ReferralRequest;
import com.referralconnect.model.ReferralRequest.Actor;
import com.referralconnect.model.RequestStatus;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.ReferralService;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * The messages between a seeker and a referrer on one request, as chat bubbles, with a box to
 * reply. The other side gets a notification for every message.
 */
final class Conversation extends JPanel {

    Conversation(AppServices app, String accountId, ReferralRequest r, Runnable onSent) {
        super();
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setName("conversation");
        Actor me = accountId.equals(r.seekerId()) ? Actor.SEEKER : Actor.REFERRER;
        String them = me == Actor.SEEKER ? r.referrerName() : r.candidate().name();

        if (r.messages().isEmpty()) {
            add(Ui.text(me == Actor.SEEKER
                    ? "No messages yet. You can ask " + them + " a question or add context here."
                    : "No messages yet. Ask " + them + " anything before you refer — they'll be notified.",
                    Theme.SMALL, Theme.MUTED));
            add(Box.createVerticalStrut(8));
        }
        for (ReferralRequest.Message m : r.messages()) {
            add(bubble(m, m.from() == me, m.from() == me ? "You" : them));
            add(Box.createVerticalStrut(6));
        }
        if (r.status() == RequestStatus.WITHDRAWN) {
            add(Ui.text("This request was withdrawn, so the conversation is closed.", Theme.SMALL, Theme.MUTED));
            return;
        }
        JTextArea box = Form.textArea("", 2, "Write a message to " + them + "…");
        box.setName("messageBox");
        JScrollPane sp = Form.areaScroll(box);
        sp.setPreferredSize(new Dimension(200, 62));
        Ui.FlatButton send = Ui.button("Send", Icons.Glyph.SEND, Ui.Kind.PRIMARY, () -> { }).compact();
        send.setName("sendMessage");
        Runnable doSend = () -> Ui.attempt(this, () -> {
            app.referrals.sendMessage(accountId, r.id(), box.getText());
            box.setText("");
            onSent.run();
        });
        send.addActionListener(e -> doSend.run());
        KeyStroke submit = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        box.getInputMap().put(submit, "send");
        box.getActionMap().put("send", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doSend.run();
            }
        });
        send.setToolTipText("Send (" + Shell.shortcut("Enter") + ")");
        JPanel compose = Ui.flexRow(new BorderLayout(8, 0));
        compose.add(sp, BorderLayout.CENTER);
        JPanel sendHolder = new JPanel(new BorderLayout());
        sendHolder.setOpaque(false);
        sendHolder.add(send, BorderLayout.SOUTH);
        compose.add(sendHolder, BorderLayout.EAST);
        add(compose);
        add(Box.createVerticalStrut(2));
        add(Ui.label("Up to " + ReferralService.MAX_MESSAGE_LENGTH + " characters", Theme.SMALL, Theme.FAINT));
    }

    private static JComponent bubble(ReferralRequest.Message m, boolean mine, String who) {
        JPanel row = Ui.flexRow(new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        JPanel b = new JPanel(new BorderLayout(0, 3)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Laf.smooth(g);
                g2.setColor(mine ? Theme.PRIMARY : Theme.NEUTRAL_SOFT);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                int max = getParent() == null || getParent().getWidth() == 0 ? 320
                        : (int) (getParent().getWidth() * 0.8);
                return new Dimension(Math.min(d.width, max), d.height);
            }
        };
        b.setOpaque(false);
        b.setBorder(Ui.padding(8, 12, 8, 12));
        Color fg = mine ? Color.WHITE : Theme.TEXT;
        Ui.WrapText text = Ui.text(m.text(), Theme.BODY, fg);
        b.add(text, BorderLayout.CENTER);
        b.add(Ui.label(who + " · " + Ui.dateTime(m.at()), Theme.SMALL,
                mine ? new Color(255, 255, 255, 190) : Theme.MUTED), BorderLayout.SOUTH);
        row.add(b);
        return row;
    }
}
