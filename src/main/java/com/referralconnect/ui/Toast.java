package com.referralconnect.ui;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A short non-blocking message in the bottom-right corner of the window, e.g. "Profile saved".
 * It fades out after a few seconds or when clicked; several stack upwards.
 */
public final class Toast extends JComponent {

    public enum Tone { SUCCESS, INFO, WARNING }

    private static final int SHOW_MS = 3200;
    private static final int WIDTH = 360;

    private final String message;
    private final Tone tone;
    private float opacity = 0f;
    private Timer fade;

    private Toast(String message, Tone tone) {
        this.message = message;
        this.tone = tone;
        setOpaque(false);
        setToolTipText(message);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                dismiss();
            }
        });
    }

    String message() {
        return message;
    }

    /** Shows {@code message} in the window that contains {@code parent}; falls back to nothing if none. */
    public static void show(Component parent, String message, Tone tone) {
        JRootPane root = parent == null ? null : SwingUtilities.getRootPane(parent);
        if (root == null) {
            return;
        }
        JLayeredPane layers = root.getLayeredPane();
        Toast t = new Toast(message, tone);
        t.setName("toast");
        layers.add(t, JLayeredPane.POPUP_LAYER);
        t.place(layers);
        t.fadeIn();
    }

    private void place(JLayeredPane layers) {
        Dimension d = getPreferredSize();
        int stacked = 0;
        for (Component c : layers.getComponentsInLayer(JLayeredPane.POPUP_LAYER)) {
            if (c instanceof Toast other && other != this) {
                stacked += other.getHeight() + 10;
            }
        }
        setBounds(layers.getWidth() - d.width - 24, layers.getHeight() - d.height - 24 - stacked, d.width, d.height);
    }

    private void fadeIn() {
        Timer in = new Timer(15, null);
        in.addActionListener(e -> {
            opacity = Math.min(1f, opacity + 0.12f);
            repaint();
            if (opacity >= 1f) {
                in.stop();
            }
        });
        in.start();
        Timer stay = new Timer(SHOW_MS, e -> dismiss());
        stay.setRepeats(false);
        stay.start();
    }

    private void dismiss() {
        if (fade != null) {
            return;
        }
        fade = new Timer(15, null);
        fade.addActionListener(e -> {
            opacity = Math.max(0f, opacity - 0.1f);
            repaint();
            if (opacity <= 0f) {
                fade.stop();
                java.awt.Container parent = getParent();
                if (parent != null) {
                    parent.remove(this);
                    parent.repaint();
                }
            }
        });
        fade.start();
    }

    private java.util.List<String> lines(FontMetrics fm) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        int max = WIDTH - 64;
        for (String word : message.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(candidate) > max && !line.isEmpty()) {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        out.add(line.toString());
        return out.size() > 3 ? out.subList(0, 3) : out;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(Theme.BODY);
        return new Dimension(WIDTH, Math.max(52, lines(fm).size() * fm.getHeight() + 28));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Laf.smooth(g);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        Color bg = Theme.isDark() ? new Color(0x222B40) : new Color(0x111827);
        g2.setColor(new Color(0, 0, 0, 40));
        g2.fillRoundRect(0, 3, getWidth() - 1, getHeight() - 4, 14, 14);
        g2.setColor(bg);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 4, 14, 14);
        Color accent = switch (tone) {
            case SUCCESS -> new Color(0x34D399);
            case WARNING -> new Color(0xFBBF24);
            case INFO -> new Color(0x93C5FD);
        };
        Icons.Glyph glyph = switch (tone) {
            case SUCCESS -> Icons.Glyph.CHECK;
            case WARNING -> Icons.Glyph.ZAP;
            case INFO -> Icons.Glyph.BELL;
        };
        Icons.get(glyph, 18, accent).paintIcon(this, g2, 16, (getHeight() - 4 - 18) / 2);
        g2.setFont(Theme.BODY);
        g2.setColor(new Color(0xF1F5F9));
        FontMetrics fm = g2.getFontMetrics();
        java.util.List<String> lines = lines(fm);
        int y = (getHeight() - 4 - lines.size() * fm.getHeight()) / 2 + fm.getAscent();
        for (String line : lines) {
            g2.drawString(line, 46, y);
            y += fm.getHeight();
        }
        g2.dispose();
    }
}
