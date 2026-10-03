package com.referralconnect.ui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JSplitPane;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.text.JTextComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/**
 * The look of Swing's standard widgets: flat, rounded and in the app's palette. Installed on top
 * of Metal so that dialogs, file choosers and menus match the custom-painted screens.
 */
public final class Laf {

    private Laf() {
    }

    private static boolean focusHookInstalled;

    static void install() {
        Theme.swingDefaults();
        ui("ScrollBarUI", ScrollBar.class);
        ui("ComboBoxUI", Combo.class);
        ui("ButtonUI", Button.class);
        ui("ToggleButtonUI", Button.class);
        ui("ProgressBarUI", Progress.class);
        ui("SplitPaneUI", Split.class);

        UIManager.put("TextField.border", new BorderUIResource(new FieldBorder()));
        UIManager.put("PasswordField.border", new BorderUIResource(new FieldBorder()));
        UIManager.put("FormattedTextField.border", new BorderUIResource(new FieldBorder()));
        UIManager.put("ComboBox.border", new BorderUIResource(new FieldBorder()));
        UIManager.put("CheckBox.icon", new CheckIcon(false));
        UIManager.put("RadioButton.icon", new CheckIcon(true));
        UIManager.put("ScrollPane.border", new BorderUIResource(BorderFactory.createEmptyBorder()));
        UIManager.put("ToolTip.border", new BorderUIResource(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.isDark() ? Theme.BORDER_STRONG : new Color(0x1E293B)),
                BorderFactory.createEmptyBorder(5, 8, 5, 8))));
        UIManager.put("PopupMenu.border", new BorderUIResource(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER), BorderFactory.createEmptyBorder(4, 0, 4, 0))));
        UIManager.put("MenuItem.border", new BorderUIResource(BorderFactory.createEmptyBorder(6, 10, 6, 14)));
        UIManager.put("Button.margin", new InsetsUIResource(6, 16, 6, 16));
        UIManager.put("OptionPane.buttonFont", new FontUIResource(Theme.BODY_BOLD));

        // Rounded field borders show focus; repaint fields when focus moves so they update at once.
        if (!focusHookInstalled) {
            focusHookInstalled = true;
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("permanentFocusOwner", e -> {
                for (Object o : new Object[]{e.getOldValue(), e.getNewValue()}) {
                    if (o instanceof JComponent c) {
                        Component target = c.getParent() instanceof JComboBox<?> combo ? combo : c;
                        target.repaint();
                    }
                }
            });
        }
    }

    private static void ui(String key, Class<?> cls) {
        UIManager.put(key, cls.getName());
        UIManager.put(cls.getName(), cls);
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    // ---------------------------------------------------------------- borders and icons

    /** Rounded 1px outline that turns the primary colour while the field has focus. */
    public static final class FieldBorder extends AbstractBorder {
        private final Insets pad;

        public FieldBorder() {
            this(new Insets(7, 11, 7, 11));
        }

        /** @param pad space between the outline and the content */
        public FieldBorder(Insets pad) {
            this.pad = pad;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = smooth(g);
            boolean focused = c.hasFocus() || c instanceof JComboBox<?> combo && combo.isPopupVisible()
                    || c instanceof JComboBox<?> cb && cb.getEditor() != null && cb.getEditor().getEditorComponent().hasFocus()
                    || c instanceof javax.swing.JScrollPane sp && sp.getViewport().getView() != null
                    && sp.getViewport().getView().hasFocus();
            if (focused && c.isEnabled()) {
                g2.setColor(Theme.alpha(Theme.PRIMARY, 60));
                g2.setStroke(new BasicStroke(3f));
                g2.drawRoundRect(x + 1, y + 1, w - 3, h - 3, 10, 10);
            }
            g2.setColor(focused ? Theme.PRIMARY : Theme.BORDER_STRONG);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(x, y, w - 1, h - 1, 10, 10);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return (Insets) pad.clone();
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(pad.top, pad.left, pad.bottom, pad.right);
            return insets;
        }
    }

    /** A rounded box with a tick, or a ring with a dot for radio buttons. */
    static final class CheckIcon implements Icon {
        private final boolean radio;

        CheckIcon(boolean radio) {
            this.radio = radio;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            AbstractButton b = (AbstractButton) c;
            boolean on = b.isSelected();
            Graphics2D g2 = smooth(g);
            int s = 16;
            Color fill = on ? Theme.PRIMARY : Theme.SURFACE;
            if (!b.isEnabled()) {
                fill = on ? Theme.BORDER_STRONG : Theme.SURFACE_2;
            }
            g2.setColor(fill);
            if (radio) {
                g2.fillOval(x, y, s, s);
            } else {
                g2.fillRoundRect(x, y, s, s, 6, 6);
            }
            if (!on) {
                g2.setColor(Theme.BORDER_STRONG);
                if (radio) {
                    g2.drawOval(x, y, s - 1, s - 1);
                } else {
                    g2.drawRoundRect(x, y, s - 1, s - 1, 6, 6);
                }
            } else if (radio) {
                g2.setColor(Color.WHITE);
                g2.fillOval(x + 5, y + 5, 6, 6);
            } else {
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D tick = new Path2D.Float();
                tick.moveTo(x + 4, y + 8.5);
                tick.lineTo(x + 7, y + 11.5);
                tick.lineTo(x + 12, y + 5);
                g2.draw(tick);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }

    // ---------------------------------------------------------------- delegates

    /** Thin rounded thumb, no arrows, invisible track. */
    public static final class ScrollBar extends BasicScrollBarUI {
        public static ComponentUI createUI(JComponent c) {
            return new ScrollBar();
        }

        @Override
        protected void configureScrollBarColors() {
            thumbColor = Theme.BORDER_STRONG;
            trackColor = Theme.SURFACE;
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            return scrollbar.getOrientation() == JScrollBar.VERTICAL ? new Dimension(11, 48) : new Dimension(48, 11);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private static JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            // Transparent: the thumb alone shows where you are.
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = smooth(g);
            g2.setColor(isThumbRollover() || isDragging ? Theme.FAINT : Theme.BORDER_STRONG);
            boolean vertical = scrollbar.getOrientation() == JScrollBar.VERTICAL;
            int pad = 3;
            if (vertical) {
                g2.fillRoundRect(r.x + pad, r.y + 2, r.width - pad * 2, r.height - 4, r.width - pad * 2, r.width - pad * 2);
            } else {
                g2.fillRoundRect(r.x + 2, r.y + pad, r.width - 4, r.height - pad * 2, r.height - pad * 2, r.height - pad * 2);
            }
            g2.dispose();
        }
    }

    /** Flat combo box with a chevron, matching the text fields. */
    public static final class Combo extends BasicComboBoxUI {
        public static ComponentUI createUI(JComponent c) {
            return new Combo();
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            c.setOpaque(false);
            c.setBackground(Theme.SURFACE);
            c.setForeground(Theme.TEXT);
            c.setFont(Theme.BODY);
        }

        @Override
        protected JButton createArrowButton() {
            JButton b = new BasicArrowButton(BasicArrowButton.SOUTH) {
                @Override
                public void paint(Graphics g) {
                    Graphics2D g2 = smooth(g);
                    g2.setColor(comboBox.isEnabled() ? Theme.MUTED : Theme.FAINT);
                    g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cx = getWidth() / 2 - 2;
                    int cy = getHeight() / 2;
                    g2.drawLine(cx - 4, cy - 2, cx, cy + 2);
                    g2.drawLine(cx, cy + 2, cx + 4, cy - 2);
                    g2.dispose();
                }

                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(22, 22);
                }
            };
            b.setBorder(BorderFactory.createEmptyBorder());
            b.setOpaque(false);
            b.setFocusable(false);
            return b;
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            Graphics2D g2 = smooth(g);
            g2.setColor(c.isEnabled() ? Theme.SURFACE : Theme.SURFACE_2);
            g2.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 10, 10);
            g2.dispose();
            super.paint(g, c);
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
            // The rounded background painted above shows through.
        }

        @Override
        public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
            super.paintCurrentValue(g, bounds, false);
        }

        @Override
        protected Insets getInsets() {
            return new Insets(5, 10, 5, 4);
        }

        @Override
        public Dimension getMinimumSize(JComponent c) {
            Dimension d = super.getMinimumSize(c);
            return new Dimension(d.width + 8, Math.max(d.height, 36));
        }
    }

    /** Rounded buttons for Swing's own dialogs; the default button is filled with the primary colour. */
    public static final class Button extends BasicButtonUI {
        public static ComponentUI createUI(JComponent c) {
            return new Button();
        }

        @Override
        public void installDefaults(AbstractButton b) {
            super.installDefaults(b);
            b.setOpaque(false);
            b.setFocusPainted(false);
            b.setRolloverEnabled(true);
            b.setFont(Theme.BODY_BOLD);
            b.setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 16));
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            boolean primary = b instanceof JButton jb && jb.isDefaultButton();
            boolean hover = b.getModel().isRollover();
            boolean pressed = b.getModel().isPressed();
            Graphics2D g2 = smooth(g);
            Color fill = primary ? (hover || pressed ? Theme.PRIMARY_DARK : Theme.PRIMARY)
                    : (hover || pressed ? Theme.PRIMARY_SOFT : Theme.SURFACE);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 10, 10);
            if (!primary) {
                g2.setColor(Theme.BORDER_STRONG);
                g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 10, 10);
            }
            g2.dispose();
            b.setForeground(primary ? Color.WHITE : Theme.TEXT);
            super.paint(g, c);
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            Dimension d = super.getPreferredSize(c);
            return new Dimension(Math.max(d.width, 76), Math.max(d.height, 34));
        }
    }

    /** A slim rounded bar; the indeterminate state is a sliding highlight. */
    public static final class Progress extends BasicProgressBarUI {
        private Timer timer;
        private float phase;

        public static ComponentUI createUI(JComponent c) {
            return new Progress();
        }

        @Override
        protected void installDefaults() {
            super.installDefaults();
            progressBar.setBorderPainted(false);
            progressBar.setOpaque(false);
        }

        @Override
        protected void startAnimationTimer() {
            if (timer == null) {
                timer = new Timer(16, e -> {
                    phase = (phase + 0.012f) % 1f;
                    progressBar.repaint();
                });
            }
            timer.start();
        }

        @Override
        protected void stopAnimationTimer() {
            if (timer != null) {
                timer.stop();
            }
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            JProgressBar bar = (JProgressBar) c;
            Graphics2D g2 = smooth(g);
            int h = Math.min(c.getHeight(), 6);
            int y = (c.getHeight() - h) / 2;
            int w = c.getWidth();
            g2.setColor(Theme.NEUTRAL_SOFT);
            g2.fillRoundRect(0, y, w, h, h, h);
            g2.setColor(Theme.PRIMARY);
            if (bar.isIndeterminate()) {
                int seg = Math.max(30, w / 3);
                int x = Math.round(phase * (w + seg)) - seg;
                g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, y, w, h, h, h));
                g2.fillRoundRect(x, y, seg, h, h, h);
            } else {
                int done = (int) Math.round(w * bar.getPercentComplete());
                g2.fillRoundRect(0, y, done, h, h, h);
            }
            g2.dispose();
        }
    }

    /** A plain gap between the panes; the divider shows only as the resize cursor. */
    public static final class Split extends BasicSplitPaneUI {
        public static ComponentUI createUI(JComponent c) {
            return new Split();
        }

        @Override
        public BasicSplitPaneDivider createDefaultDivider() {
            return new BasicSplitPaneDivider(this) {
                @Override
                public void paint(Graphics g) {
                    // Nothing: the cards on either side already separate the panes.
                }

                @Override
                public void setBorder(Border b) {
                    super.setBorder(BorderFactory.createEmptyBorder());
                }
            };
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            JSplitPane sp = (JSplitPane) c;
            sp.setBorder(BorderFactory.createEmptyBorder());
            sp.setOpaque(false);
        }
    }

    /** Draws text left-aligned and vertically centred; used by a few custom components. */
    static void drawCentered(Graphics2D g2, String text, int x, int height) {
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, x, (height - fm.getHeight()) / 2 + fm.getAscent());
    }

    static boolean isText(Component c) {
        return c instanceof JTextComponent;
    }
}
