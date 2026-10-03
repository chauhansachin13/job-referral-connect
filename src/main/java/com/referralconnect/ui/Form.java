package com.referralconnect.ui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

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

    /** A text field that shows a grey hint while empty, optionally with an icon in front. */
    public static final class HintField extends JTextField {
        private final String hint;
        private Icons.Glyph glyph;

        public HintField(String value, String hint) {
            super(value);
            this.hint = hint == null ? "" : hint;
            setFont(Theme.BODY);
            setBorder(fieldBorder());
            setBackground(Theme.SURFACE);
            setForeground(Theme.TEXT);
            setCaretColor(Theme.TEXT);
            setOpaque(false);
        }

        /** Puts an icon (e.g. a magnifier) inside the field, before the text. */
        public HintField withIcon(Icons.Glyph g) {
            this.glyph = g;
            setBorder(BorderFactory.createCompoundBorder(fieldBorder(), BorderFactory.createEmptyBorder(0, 24, 0, 0)));
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width, 120), 36);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D bg = Laf.smooth(g);
            bg.setColor(isEnabled() && isEditable() ? Theme.SURFACE : Theme.SURFACE_2);
            bg.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            bg.dispose();
            super.paintComponent(g);
            Graphics2D g2 = Laf.smooth(g);
            if (glyph != null) {
                Icons.get(glyph, 16, Theme.FAINT).paintIcon(this, g2, 11, (getHeight() - 16) / 2);
            }
            if (getText().isEmpty() && !hint.isEmpty()) {
                g2.setColor(Theme.FAINT);
                g2.setFont(Theme.BODY);
                Insets in = getInsets();
                g2.drawString(hint, in.left, (getHeight() - g2.getFontMetrics().getHeight()) / 2
                        + g2.getFontMetrics().getAscent());
            }
            g2.dispose();
        }
    }

    public static Border fieldBorder() {
        return new Laf.FieldBorder();
    }

    public static JPasswordField password() {
        JPasswordField f = new JPasswordField() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(Math.max(super.getPreferredSize().width, 120), 36);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D bg = Laf.smooth(g);
                bg.setColor(Theme.SURFACE);
                bg.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                bg.dispose();
                super.paintComponent(g);
            }
        };
        f.setOpaque(false);
        f.setFont(Theme.BODY);
        f.setBorder(fieldBorder());
        f.setBackground(Theme.SURFACE);
        f.setForeground(Theme.TEXT);
        f.setCaretColor(Theme.TEXT);
        return f;
    }

    /** Calls {@code r} on every edit of the field. */
    public static void onChange(javax.swing.text.JTextComponent field, Runnable r) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        });
    }

    public JTextField field(String label, String value, String hint) {
        HintField f = new HintField(value, hint);
        cell(label, f);
        return f;
    }

    /** A multi-line text box with a hint, inside a rounded outline. */
    public static JTextArea textArea(String value, int rows, String hint) {
        JTextArea a = new JTextArea(value, rows, 20) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && hint != null) {
                    Graphics2D g2 = Laf.smooth(g);
                    g2.setColor(Theme.FAINT);
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
        a.setBackground(Theme.SURFACE);
        a.setForeground(Theme.TEXT);
        a.setCaretColor(Theme.TEXT);
        a.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        return a;
    }

    /** {@code area} in a scroll pane with the same rounded outline as the text fields. */
    public static JScrollPane areaScroll(JTextArea area) {
        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(new Laf.FieldBorder(new Insets(2, 2, 2, 2)));
        sp.getViewport().setBackground(Theme.SURFACE);
        // The outline follows the text area's focus, not the scroll pane's.
        area.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                sp.repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                sp.repaint();
            }
        });
        return sp;
    }

    public JTextArea area(String label, String value, int rows, String hint) {
        JTextArea a = textArea(value, rows, hint);
        full(label, areaScroll(a));
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
        c.insets = new Insets(0, col == 0 ? 0 : 8, 5, col + span >= columns ? 0 : 8);
        if (label != null) {
            super.add(Ui.label(label, Theme.SMALL_BOLD, Theme.TEXT_2), c);
        }
        c.gridy = row + 1;
        c.insets = new Insets(0, c.insets.left, 14, c.insets.right);
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
