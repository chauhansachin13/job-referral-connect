package com.referralconnect.ui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultButtonModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Larger building blocks shared by the dashboards: stat cards, a switch, selectable chips. */
final class Widgets {

    private Widgets() {
    }

    /**
     * A number with a label and an icon in a tinted square, e.g. "128 · New since your last visit".
     * Clicking it runs {@code onClick} (if given).
     */
    static final class StatCard extends Ui.Card {
        private final JLabel value = Ui.label("0", Theme.STAT, Theme.TEXT);
        private final JLabel caption;
        private final JLabel hint = Ui.label(" ", Theme.SMALL, Theme.MUTED);

        StatCard(String captionText, Icons.Glyph glyph, Color accent, Runnable onClick) {
            super(new BorderLayout(14, 0));
            setBorder(Ui.padding(16, 18, 18, 18));
            caption = Ui.label(captionText, Theme.SMALL_BOLD, Theme.MUTED);
            JLabel icon = new JLabel(Icons.get(glyph, 20, accent)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = Laf.smooth(g);
                    g2.setColor(Theme.alpha(accent, Theme.isDark() ? 50 : 28));
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            icon.setHorizontalAlignment(JLabel.CENTER);
            icon.setPreferredSize(new Dimension(44, 44));
            JPanel iconHolder = new JPanel(new BorderLayout());
            iconHolder.setOpaque(false);
            iconHolder.add(icon, BorderLayout.NORTH);
            add(iconHolder, BorderLayout.WEST);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(caption);
            text.add(Box.createVerticalStrut(2));
            text.add(value);
            text.add(hint);
            add(text, BorderLayout.CENTER);
            if (onClick != null) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText("Show these");
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        onClick.run();
                    }
                });
            }
        }

        void set(long number, String hintText) {
            set(String.format("%,d", number), hintText);
        }

        void set(String text, String hintText) {
            value.setText(text);
            hint.setText(hintText == null || hintText.isEmpty() ? " " : hintText);
        }

        String value() {
            return value.getText();
        }
    }

    /** An iOS-style on/off switch. */
    static final class Toggle extends AbstractButton {
        private float knob;

        Toggle(boolean on) {
            setModel(new DefaultButtonModel());
            setSelected(on);
            knob = on ? 1f : 0f;
            setOpaque(false);
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (isEnabled()) {
                        setSelected(!isSelected());
                        fireActionPerformed(new java.awt.event.ActionEvent(Toggle.this,
                                java.awt.event.ActionEvent.ACTION_PERFORMED, "toggle"));
                    }
                }
            });
            addItemListener(e -> animate());
        }

        /** Clicks it programmatically, as a person would (used by keyboard shortcuts and tests). */
        @Override
        public void doClick() {
            setSelected(!isSelected());
            fireActionPerformed(new java.awt.event.ActionEvent(this, java.awt.event.ActionEvent.ACTION_PERFORMED, "toggle"));
        }

        private void animate() {
            float target = isSelected() ? 1f : 0f;
            javax.swing.Timer t = new javax.swing.Timer(12, null);
            t.addActionListener(e -> {
                knob += (target - knob) * 0.35f;
                if (Math.abs(target - knob) < 0.02f) {
                    knob = target;
                    t.stop();
                }
                repaint();
            });
            t.start();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(40, 22);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Laf.smooth(g);
            int w = 38;
            int h = 22;
            Color off = Theme.BORDER_STRONG;
            Color on = Theme.PRIMARY;
            g2.setColor(blend(off, on, knob));
            g2.fillRoundRect(0, 0, w, h, h, h);
            g2.setColor(Color.WHITE);
            int d = h - 6;
            int x = 3 + Math.round((w - d - 6) * knob);
            g2.fillOval(x, 3, d, d);
            g2.dispose();
        }

        private static Color blend(Color a, Color b, float t) {
            return new Color(
                    Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                    Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                    Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
        }
    }

    /** A setting row: title and description on the left, a switch on the right. */
    static JComponent settingRow(String title, String description, Toggle toggle) {
        JPanel p = Ui.flexRow(new BorderLayout(16, 0));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(Ui.label(title, Theme.BODY_BOLD, Theme.TEXT));
        text.add(Box.createVerticalStrut(2));
        text.add(Ui.text(description, Theme.SMALL, Theme.MUTED));
        p.add(text, BorderLayout.CENTER);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(toggle, BorderLayout.NORTH);
        p.add(holder, BorderLayout.EAST);
        p.setBorder(Ui.padding(8, 0, 8, 0));
        return p;
    }

    /** A pill that toggles on click, for picking preferred roles and cities. */
    static final class Chip extends JComponent {
        private final String text;
        private boolean selected;
        private boolean hover;

        Chip(String text, boolean selected) {
            this.text = text;
            this.selected = selected;
            setFont(Theme.SMALL_BOLD);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toggle();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        void toggle() {
            selected = !selected;
            revalidate();
            repaint();
        }

        boolean isSelected() {
            return selected;
        }

        String text() {
            return text;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(text) + (selected ? 40 : 26), 30);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Laf.smooth(g);
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            g2.setColor(selected ? Theme.PRIMARY_SOFT : hover ? Theme.ROW_HOVER : Theme.SURFACE);
            g2.fillRoundRect(0, 0, w, h, h, h);
            g2.setColor(selected ? Theme.alpha(Theme.PRIMARY, 140) : Theme.BORDER_STRONG);
            g2.drawRoundRect(0, 0, w, h, h, h);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int x = 13;
            if (selected) {
                Icons.get(Icons.Glyph.CHECK, 13, Theme.PRIMARY).paintIcon(this, g2, x - 1, (h - 13) / 2 + 1);
                x += 15;
            }
            g2.setColor(selected ? Theme.PRIMARY_TEXT : Theme.TEXT_2);
            g2.drawString(text, x, (h - fm.getHeight()) / 2 + fm.getAscent() + 1);
            g2.dispose();
        }
    }
}
