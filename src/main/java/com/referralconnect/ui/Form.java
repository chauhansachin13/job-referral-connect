package com.referralconnect.ui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.Border;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;

/** Label-above-field forms laid out on a grid of N columns. */
public final class Form extends JPanel {

    private final int columns;
    private int col;
    private int row;

    public Form(int columns) {
        super(new GridBagLayout());
        this.columns = columns;
        setOpaque(false);
    }

    /** A text field that shows a grey hint while empty. */
    public static class HintField extends JTextField {
        private final String hint;

        public HintField(String value, String hint) {
            super(value);
            this.hint = hint == null ? "" : hint;
            setFont(Theme.BODY);
            setBorder(fieldBorder());
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width, 120), 34);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty() && !hint.isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(new java.awt.Color(0x94A3B8));
                g2.setFont(Theme.BODY);
                Insets in = getInsets();
                g2.drawString(hint, in.left, (getHeight() - g2.getFontMetrics().getHeight()) / 2
                        + g2.getFontMetrics().getAscent());
                g2.dispose();
            }
        }
    }

    public static Border fieldBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(0xCBD5E1)),
                BorderFactory.createEmptyBorder(6, 9, 6, 9));
    }

    public static JPasswordField password() {
        JPasswordField f = new JPasswordField() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(Math.max(super.getPreferredSize().width, 120), 34);
            }
        };
        f.setFont(Theme.BODY);
        f.setBorder(fieldBorder());
        return f;
    }

    public JTextField field(String label, String value, String hint) {
        HintField f = new HintField(value, hint);
        cell(label, f);
        return f;
    }

    public JTextArea area(String label, String value, int rows, String hint) {
        JTextArea a = new JTextArea(value, rows, 20) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && hint != null) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(new java.awt.Color(0x94A3B8));
                    g2.setFont(Theme.BODY);
                    Insets in = getInsets();
                    g2.drawString(hint, in.left, in.top + g2.getFontMetrics().getAscent());
                    g2.dispose();
                }
            }
        };
        a.setFont(Theme.BODY);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JScrollPane sp = new JScrollPane(a);
        sp.setBorder(BorderFactory.createLineBorder(new java.awt.Color(0xCBD5E1)));
        full(label, sp);
        return a;
    }

    /** Adds a labelled component in the next grid cell. */
    public void cell(String label, JComponent component) {
        place(label, component, 1);
    }

    /** Adds a labelled component spanning the whole row. */
    public void full(String label, JComponent component) {
        if (col != 0) {
            col = 0;
            row += 2;
        }
        place(label, component, columns);
    }

    private void place(String label, JComponent component, int span) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = col;
        c.gridy = row;
        c.gridwidth = span;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, col == 0 ? 0 : 7, 4, col + span >= columns ? 0 : 7);
        if (label != null) {
            super.add(Ui.label(label, Theme.SMALL_BOLD, Theme.MUTED), c);
        }
        c.gridy = row + 1;
        c.insets = new Insets(0, c.insets.left, 12, c.insets.right);
        super.add(component, c);
        col += span;
        if (col >= columns) {
            col = 0;
            row += 2;
        }
    }

    /** Pushes everything up so the form does not stretch vertically. */
    public void finish() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row + 2;
        c.weighty = 1;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        super.add(filler, c);
    }
}
