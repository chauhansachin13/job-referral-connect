package com.referralconnect.ui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** Gradient bar across the top of each dashboard: brand on the left, who is signed in on the right. */
public final class HeaderBar extends JPanel {

    public HeaderBar(String subtitle, String who, String role, Runnable onSignOut) {
        super(new BorderLayout());
        setBorder(Ui.padding(14, 24, 14, 24));

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.add(Ui.label("Job Referral Connect", Theme.font(java.awt.Font.BOLD, 20), Color.WHITE));
        brand.add(Ui.label(subtitle, Theme.SMALL, new Color(0xC7D2FE)));
        add(brand, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        JPanel person = new JPanel();
        person.setOpaque(false);
        person.setLayout(new BoxLayout(person, BoxLayout.Y_AXIS));
        JLabel name = Ui.label(who, Theme.BODY_BOLD, Color.WHITE);
        name.setAlignmentX(RIGHT_ALIGNMENT);
        JLabel roleLabel = Ui.label(role, Theme.SMALL, new Color(0xC7D2FE));
        roleLabel.setAlignmentX(RIGHT_ALIGNMENT);
        person.add(name);
        person.add(roleLabel);
        right.add(person);
        right.add(Box.createHorizontalStrut(4));
        right.add(Ui.button("Sign out", Ui.Kind.GHOST, onSignOut));
        add(right, BorderLayout.EAST);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setPaint(new GradientPaint(0, 0, Theme.HEADER_FROM, getWidth(), 0, Theme.HEADER_TO));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }
}
