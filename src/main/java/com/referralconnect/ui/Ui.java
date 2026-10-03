package com.referralconnect.ui;

import com.referralconnect.model.RequestStatus;
import com.referralconnect.service.ServiceException;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Supplier;

/** Small shared widgets and helpers so every screen looks and behaves the same. */
public final class Ui {

    private Ui() {
    }

    // ---------------------------------------------------------------- buttons

    /**
     * PRIMARY filled; SECONDARY outlined; SUBTLE text-only with a hover tint; GHOST for dark
     * backgrounds; SUCCESS / WARNING / DANGER for decisions.
     */
    public enum Kind { PRIMARY, SECONDARY, SUBTLE, SUCCESS, WARNING, DANGER, GHOST }

    /** A flat, rounded button painted by hand, with an optional icon before the text. */
    public static class FlatButton extends JButton {
        private Kind kind;
        private boolean hover;
        private Icons.Glyph glyph;
        private boolean compact;

        @SuppressWarnings("this-escape") // subclasses only repaint differently; nothing they add is used here
        public FlatButton(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setFont(Theme.BODY_BOLD);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
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

        public void setKind(Kind kind) {
            this.kind = kind;
            repaint();
        }

        public Kind kind() {
            return kind;
        }

        public FlatButton glyph(Icons.Glyph g) {
            this.glyph = g;
            revalidate();
            repaint();
            return this;
        }

        /** A smaller button for toolbars and inline use. */
        public FlatButton compact() {
            this.compact = true;
            setFont(Theme.SMALL_BOLD);
            revalidate();
            return this;
        }

        private boolean iconOnly() {
            return glyph != null && (getText() == null || getText().isEmpty());
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int h = compact ? 30 : 36;
            if (iconOnly()) {
                return new Dimension(h, h);
            }
            int iconW = glyph == null ? 0 : (compact ? 15 : 17) + 7;
            return new Dimension(fm.stringWidth(getText()) + iconW + (compact ? 22 : 32), h);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Laf.smooth(g);
            Color fill;
            Color text;
            Color outline = null;
            switch (kind) {
                case PRIMARY -> {
                    fill = hover ? Theme.PRIMARY_DARK : Theme.PRIMARY;
                    text = Color.WHITE;
                }
                case SUCCESS -> {
                    fill = hover ? Theme.SUCCESS.darker() : Theme.SUCCESS;
                    text = Theme.isDark() ? new Color(0x06281F) : Color.WHITE;
                }
                case WARNING -> {
                    fill = hover ? Theme.alpha(Theme.WARNING, 70) : Theme.WARNING_SOFT;
                    text = Theme.WARNING;
                }
                case DANGER -> {
                    fill = hover ? Theme.alpha(Theme.DANGER, 60) : Theme.DANGER_SOFT;
                    text = Theme.DANGER;
                }
                case GHOST -> {
                    fill = hover ? new Color(255, 255, 255, 34) : new Color(0, 0, 0, 0);
                    text = Theme.SIDEBAR_TEXT;
                    outline = new Color(255, 255, 255, 60);
                }
                case SUBTLE -> {
                    fill = hover ? Theme.NEUTRAL_SOFT : new Color(0, 0, 0, 0);
                    text = Theme.TEXT_2;
                }
                default -> {
                    fill = hover ? Theme.PRIMARY_SOFT : Theme.SURFACE;
                    text = Theme.isDark() ? Theme.TEXT : Theme.PRIMARY_TEXT;
                    outline = hover ? Theme.alpha(Theme.PRIMARY, 90) : Theme.BORDER_STRONG;
                }
            }
            if (!isEnabled()) {
                fill = kind == Kind.GHOST || kind == Kind.SUBTLE ? new Color(0, 0, 0, 0) : Theme.NEUTRAL_SOFT;
                text = Theme.FAINT;
                outline = null;
            }
            int w = getWidth();
            int h = getHeight();
            int arc = compact ? 9 : 11;
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            if (outline != null) {
                g2.setColor(outline);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }
            if (isFocusOwner() && isEnabled()) {
                g2.setColor(Theme.alpha(Theme.PRIMARY, 110));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
            }
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int iconSize = compact ? 15 : 17;
            String label = getText() == null ? "" : getText();
            int contentW = (glyph == null ? 0 : iconSize) + (glyph != null && !label.isEmpty() ? 7 : 0)
                    + fm.stringWidth(label);
            int x = (w - contentW) / 2;
            if (glyph != null) {
                Icons.get(glyph, iconSize, text).paintIcon(this, g2, x, (h - iconSize) / 2);
                x += iconSize + (label.isEmpty() ? 0 : 7);
            }
            g2.setColor(text);
            g2.drawString(label, x, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    public static FlatButton button(String text, Kind kind, Runnable action) {
        FlatButton b = new FlatButton(text, kind);
        b.addActionListener(e -> action.run());
        return b;
    }

    public static FlatButton button(String text, Icons.Glyph glyph, Kind kind, Runnable action) {
        return button(text, kind, action).glyph(glyph);
    }

    /** A square icon-only button; the tooltip says what it does. */
    public static FlatButton iconButton(Icons.Glyph glyph, String tooltip, Kind kind, Runnable action) {
        FlatButton b = button("", kind, action).glyph(glyph);
        b.setToolTipText(tooltip);
        b.getAccessibleContext().setAccessibleName(tooltip);
        return b;
    }

    /** A shorter button for inline use next to text, e.g. "Open" beside a link. */
    public static FlatButton smallButton(String text, Kind kind, Runnable action) {
        return button(text, kind, action).compact();
    }

    // ---------------------------------------------------------------- panels & labels

    /** Rounded surface with a hairline border and a soft shadow below. */
    public static class Card extends JPanel {
        private Supplier<Color> fill = () -> Theme.SURFACE;
        private int arc = 16;

        @SuppressWarnings("this-escape") // subclasses add children afterwards; the setters here touch none
        public Card(LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(16, 18, 18, 18));
        }

        public Card fill(Supplier<Color> f) {
            this.fill = f;
            repaint();
            return this;
        }

        public Card arc(int a) {
            this.arc = a;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Laf.smooth(g);
            int w = getWidth() - 1;
            int h = getHeight() - 3;
            g2.setColor(Theme.SHADOW);
            g2.fillRoundRect(0, 2, w, h + 1, arc, arc);
            g2.setColor(fill.get());
            g2.fillRoundRect(0, 0, w, h, arc, arc);
            g2.setColor(Theme.BORDER);
            g2.drawRoundRect(0, 0, w, h, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Small rounded label, e.g. a status or a category tag. */
    public static final class Pill extends JLabel {
        private Color fill;

        public Pill(String text, Color fg, Color bg) {
            super(text);
            setFont(Theme.SMALL_BOLD);
            setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));
            setColors(fg, bg);
        }

        public Pill withIcon(Icons.Glyph glyph) {
            setIcon(Icons.get(glyph, 13, getForeground()));
            setIconTextGap(5);
            return this;
        }

        public void setColors(Color fg, Color bg) {
            this.fill = bg;
            setForeground(fg);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Laf.smooth(g);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static Pill statusPill(RequestStatus s) {
        return new Pill(s.label(), Theme.statusColor(s), Theme.statusSoft(s));
    }

    public static Pill neutralPill(String text) {
        return new Pill(text, Theme.TEXT_2, Theme.NEUTRAL_SOFT);
    }

    /** The "NEW" badge for openings found since the last visit. */
    public static Pill newBadge() {
        Pill p = new Pill("NEW", Color.WHITE, Theme.CHART[4]);
        p.setFont(Theme.TINY_BOLD);
        p.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return p;
    }

    /** A colourful pill for the minimum experience: green for freshers, amber for 5+ years. */
    public static Pill experiencePill(com.referralconnect.model.JobPosting j) {
        double y = j.requirements().minYears();
        String text = j.experienceLabel();
        Pill p;
        if (!j.requirements().known()) {
            p = new Pill(j.requirements().detailsRead() ? "Exp. not stated" : "Exp. not read yet", Theme.MUTED,
                    Theme.NEUTRAL_SOFT);
        } else if (j.requirements().preferredOnly()) {
            p = new Pill(text, Theme.TEXT_2, Theme.NEUTRAL_SOFT);
        } else if (y == 0) {
            p = new Pill(text, Theme.SUCCESS, Theme.SUCCESS_SOFT);
        } else if (y <= 2) {
            p = new Pill(text, Theme.INFO, Theme.INFO_SOFT);
        } else if (y <= 5) {
            p = new Pill(text, Theme.PRIMARY_TEXT, Theme.PRIMARY_SOFT);
        } else {
            p = new Pill(text, Theme.WARNING, Theme.WARNING_SOFT);
        }
        return p.withIcon(Icons.Glyph.CLOCK);
    }

    public static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    public static JLabel iconLabel(String text, Icons.Glyph glyph, Font font, Color color) {
        JLabel l = label(text, font, color);
        l.setIcon(Icons.get(glyph, font.getSize() + 3, color));
        l.setIconTextGap(6);
        return l;
    }

    /** Small grey capitals over a section. */
    public static JLabel sectionTitle(String text) {
        JLabel l = label(text.toUpperCase(Locale.ROOT), Theme.TINY_BOLD, Theme.MUTED);
        l.setBorder(padding(0, 0, 0, 0));
        return l;
    }

    // ---------------------------------------------------------------- avatars

    /** Initials on a colour picked from the name, used for companies and people. */
    public static final class Avatar implements Icon {
        private static final Color[] COLORS = {
                new Color(0x4F46E5), new Color(0x0891B2), new Color(0x059669), new Color(0xD97706),
                new Color(0xDB2777), new Color(0x7C3AED), new Color(0x0D9488), new Color(0xEA580C),
                new Color(0x2563EB), new Color(0x65A30D), new Color(0xDC2626), new Color(0x9333EA)};
        private final String initials;
        private final Color color;
        private final int size;
        private final boolean round;

        public Avatar(String name, int size, boolean round) {
            this.initials = initials(name);
            this.color = COLORS[Math.floorMod(name == null ? 0 : name.hashCode(), COLORS.length)];
            this.size = size;
            this.round = round;
        }

        static String initials(String name) {
            if (name == null || name.isBlank()) {
                return "?";
            }
            String[] words = name.replaceAll("[^\\p{L}\\p{N} ]", " ").trim().split("\\s+");
            if (words.length == 1 || words[0].isEmpty()) {
                String w = words[0].isEmpty() ? name.trim() : words[0];
                return w.substring(0, Math.min(2, w.length())).toUpperCase(Locale.ROOT);
            }
            return ("" + words[0].charAt(0) + words[1].charAt(0)).toUpperCase(Locale.ROOT);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = Laf.smooth(g);
            g2.setColor(Theme.isDark() ? Theme.alpha(color, 70) : Theme.alpha(color, 30));
            if (round) {
                g2.fillOval(x, y, size, size);
            } else {
                g2.fillRoundRect(x, y, size, size, size / 3, size / 3);
            }
            g2.setColor(Theme.isDark() ? color.brighter().brighter() : color);
            g2.setFont(Theme.font(Font.BOLD, Math.max(9, size * 0.38f)));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(initials, x + (size - fm.stringWidth(initials)) / 2f,
                    y + (size - fm.getHeight()) / 2f + fm.getAscent());
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    public static JLabel avatar(String name, int size) {
        return new JLabel(new Avatar(name, size, false));
    }

    // ---------------------------------------------------------------- wrapping text

    /**
     * Word-wrapping text. Swing labels do not wrap, and a read-only JTextArea both ignores
     * transparency and reports a one-line height until it has been laid out once. This component
     * wraps to whatever width it is given and asks for a fresh layout whenever that width changes,
     * so its height is always right.
     */
    public static final class WrapText extends JComponent {
        private static final int DEFAULT_WIDTH = 380;
        private String text;
        private int laidOutWidth = -1;
        private int maxLines = Integer.MAX_VALUE;

        public WrapText(String text, Font font, Color color) {
            this.text = text == null ? "" : text;
            setFont(font);
            setForeground(color);
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        public void setText(String text) {
            this.text = text == null ? "" : text;
            revalidate();
            repaint();
        }

        public String getText() {
            return text;
        }

        /** Shows at most this many lines, ending the last with "…". */
        public WrapText maxLines(int n) {
            this.maxLines = n;
            return this;
        }

        private java.util.List<String> lines(int width) {
            FontMetrics fm = getFontMetrics(getFont());
            java.util.List<String> out = new java.util.ArrayList<>();
            for (String paragraph : text.split("\n", -1)) {
                StringBuilder line = new StringBuilder();
                for (String word : paragraph.split(" ")) {
                    String candidate = line.isEmpty() ? word : line + " " + word;
                    if (fm.stringWidth(candidate) <= width || line.isEmpty()) {
                        line.setLength(0);
                        line.append(candidate);
                    } else {
                        out.add(line.toString());
                        line.setLength(0);
                        line.append(word);
                    }
                    // A single word wider than the line (a long URL) is broken by characters.
                    while (fm.stringWidth(line.toString()) > width && line.length() > 1) {
                        int cut = line.length() - 1;
                        while (cut > 1 && fm.stringWidth(line.substring(0, cut)) > width) {
                            cut--;
                        }
                        out.add(line.substring(0, cut));
                        line.delete(0, cut);
                    }
                }
                out.add(line.toString());
            }
            if (out.size() > maxLines) {
                java.util.List<String> cut = new java.util.ArrayList<>(out.subList(0, maxLines));
                String last = cut.get(maxLines - 1);
                while (!last.isEmpty() && fm.stringWidth(last + "…") > width) {
                    last = last.substring(0, last.length() - 1);
                }
                cut.set(maxLines - 1, last + "…");
                return cut;
            }
            return out;
        }

        private int availableWidth() {
            Insets in = getInsets();
            int w = getWidth() > 0 ? getWidth() : Math.min(DEFAULT_WIDTH, naturalWidth());
            return Math.max(20, w - in.left - in.right);
        }

        private int naturalWidth() {
            FontMetrics fm = getFontMetrics(getFont());
            int widest = 0;
            for (String paragraph : text.split("\n", -1)) {
                widest = Math.max(widest, fm.stringWidth(paragraph));
            }
            Insets in = getInsets();
            return widest + in.left + in.right + 1;
        }

        @Override
        public Dimension getPreferredSize() {
            Insets in = getInsets();
            int lineCount = lines(availableWidth()).size();
            int height = lineCount * getFontMetrics(getFont()).getHeight() + in.top + in.bottom;
            return new Dimension(Math.min(DEFAULT_WIDTH, naturalWidth()), height);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(40, getPreferredSize().height);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        public void setBounds(int x, int y, int width, int height) {
            super.setBounds(x, y, width, height);
            if (width != laidOutWidth) {
                laidOutWidth = width;
                // Our height depends on our width, so the parents need another layout pass. It must
                // be queued: a revalidate() issued during the current pass is swallowed when Swing
                // marks the tree valid at the end of it.
                SwingUtilities.invokeLater(this::revalidate);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Laf.smooth(g);
            g2.setFont(getFont());
            g2.setColor(getForeground());
            FontMetrics fm = g2.getFontMetrics();
            Insets in = getInsets();
            int y = in.top + fm.getAscent();
            for (String line : lines(availableWidth())) {
                g2.drawString(line, in.left, y);
                y += fm.getHeight();
            }
            g2.dispose();
        }
    }

    public static WrapText text(String text, Font font, Color color) {
        return new WrapText(text, font, color);
    }

    public static JPanel row(int gap, Component... parts) {
        JPanel p = new JPanel(new WrapLayout(FlowLayout.LEFT, gap, 0)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.setOpaque(false);
        for (Component c : parts) {
            p.add(c);
        }
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    /** Transparent panel that a vertical BoxLayout may widen but never stretch taller than needed. */
    public static JPanel flexRow(LayoutManager layout) {
        JPanel p = new JPanel(layout) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    public static Border padding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /** A scroll pane inside a rounded card outline. */
    public static JScrollPane scroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(new RoundLineBorder());
        sp.setViewportBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Theme.SURFACE);
        sp.setBackground(Theme.SURFACE);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getHorizontalScrollBar().setUnitIncrement(16);
        return sp;
    }

    public static JScrollPane bareScroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setViewportBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getVerticalScrollBar().setOpaque(false);
        return sp;
    }

    /** 1px rounded outline, for scroll panes holding tables. */
    static final class RoundLineBorder implements Border {
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = Laf.smooth(g);
            g2.setColor(Theme.BORDER);
            g2.drawRoundRect(x, y, w - 1, h - 1, 14, 14);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(4, 1, 4, 1);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }

    // ---------------------------------------------------------------- tables

    /** Rows of 44px, no grid, a hover tint, and quiet capital headers. */
    public static void styleTable(JTable table) {
        table.setRowHeight(44);
        table.setFont(Theme.BODY);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setBackground(Theme.SURFACE);
        table.setForeground(Theme.TEXT);
        table.setSelectionBackground(Theme.SELECTION);
        table.setSelectionForeground(Theme.TEXT);
        table.setAutoCreateRowSorter(false);
        table.putClientProperty("hoverRow", -1);
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (!Integer.valueOf(row).equals(table.getClientProperty("hoverRow"))) {
                    table.putClientProperty("hoverRow", row);
                    table.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                table.putClientProperty("hoverRow", -1);
                table.repaint();
            }
        };
        table.addMouseMotionListener(hover);
        table.addMouseListener(hover);
        table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setBackground(Theme.SURFACE);
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 36));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
                super.getTableCellRendererComponent(t, v == null ? "" : v.toString().toUpperCase(Locale.ROOT),
                        false, false, r, c);
                setFont(Theme.TINY_BOLD);
                setForeground(Theme.MUTED);
                setBackground(Theme.SURFACE);
                setHorizontalAlignment(SwingConstants.LEFT);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER), padding(0, 12, 0, 10)));
                return this;
            }
        });

        DefaultTableCellRenderer base = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, false, r, c);
                // This renderer is shared by every column; reset what centerColumn may have changed.
                setHorizontalAlignment(SwingConstants.LEFT);
                setBorder(padding(0, 12, 0, 10));
                setBackground(rowBackground(t, sel, r));
                setForeground(Theme.TEXT);
                setFont(Theme.BODY);
                setIcon(null);
                return this;
            }
        };
        table.setDefaultRenderer(Object.class, base);
        table.setDefaultRenderer(Integer.class, base);
    }

    /** Selection, hover or plain background for a table row. */
    public static Color rowBackground(JTable t, boolean selected, int row) {
        if (selected) {
            return Theme.SELECTION;
        }
        Object hover = t.getClientProperty("hoverRow");
        return hover instanceof Integer h && h == row ? Theme.ROW_HOVER : Theme.SURFACE;
    }

    /** Renders a {@link RequestStatus} cell as a coloured pill. */
    public static final class StatusRenderer extends DefaultTableCellRenderer {
        private final Pill pill = new Pill("", Theme.TEXT, Theme.NEUTRAL_SOFT);
        private final JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 11));

        public StatusRenderer() {
            holder.add(pill);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            holder.setBackground(rowBackground(t, sel, r));
            if (v instanceof RequestStatus s) {
                pill.setText(s.label());
                pill.setColors(Theme.statusColor(s), Theme.statusSoft(s));
                pill.setVisible(true);
            } else {
                pill.setVisible(false);
            }
            return holder;
        }
    }

    /** What a {@link TwoLineRenderer} shows for one row. */
    public record TwoLines(String avatarName, boolean roundAvatar, String title, String subtitle, boolean bold) {
    }

    /** An avatar, a bold title and a muted second line, for list-like tables. */
    public static final class TwoLineRenderer implements javax.swing.table.TableCellRenderer {
        private final JPanel panel = new JPanel(new java.awt.BorderLayout(10, 0));
        private final JLabel avatar = new JLabel();
        private final JLabel title = label("", Theme.BODY_BOLD, Theme.TEXT);
        private final JLabel sub = label("", Theme.SMALL, Theme.MUTED);

        public TwoLineRenderer() {
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
            text.add(title);
            text.add(javax.swing.Box.createVerticalStrut(2));
            text.add(sub);
            panel.add(avatar, java.awt.BorderLayout.WEST);
            panel.add(text, java.awt.BorderLayout.CENTER);
            panel.setBorder(padding(8, 12, 6, 10));
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            TwoLines l = (TwoLines) v;
            panel.setBackground(rowBackground(t, sel, r));
            avatar.setIcon(new Avatar(l.avatarName(), 30, l.roundAvatar()));
            title.setText(l.title());
            title.setFont(l.bold() ? Theme.BODY_BOLD : Theme.BODY);
            title.setForeground(Theme.TEXT);
            sub.setText(l.subtitle());
            sub.setForeground(Theme.MUTED);
            panel.setToolTipText(l.title());
            return panel;
        }
    }

    public static void centerColumn(JTable table, int column) {
        DefaultTableCellRenderer r = (DefaultTableCellRenderer) table.getDefaultRenderer(Object.class);
        DefaultTableCellRenderer centered = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int c) {
                Component comp = r.getTableCellRendererComponent(t, v, sel, focus, row, c);
                ((JLabel) comp).setHorizontalAlignment(SwingConstants.CENTER);
                return comp;
            }
        };
        table.getColumnModel().getColumn(column).setCellRenderer(centered);
    }

    // ---------------------------------------------------------------- time

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());

    public static String date(Instant t) {
        return t == null ? "" : DATE.format(t);
    }

    public static String dateTime(Instant t) {
        return t == null ? "" : DATE_TIME.format(t);
    }

    public static String dayMonth(Instant t) {
        return t == null ? "" : DAY_MONTH.format(t);
    }

    /** "45m ago", "16h ago", "3d ago" — for narrow table columns. */
    public static String agoShort(Instant t) {
        if (t == null) {
            return "";
        }
        Duration d = Duration.between(t, Instant.now());
        if (d.isNegative() || d.toMinutes() < 1) {
            return "now";
        }
        if (d.toHours() < 1) {
            return d.toMinutes() + "m ago";
        }
        return d.toDays() < 1 ? d.toHours() + "h ago" : d.toDays() + "d ago";
    }

    public static String ago(Instant t) {
        if (t == null) {
            return "never";
        }
        Duration d = Duration.between(t, Instant.now());
        if (d.isNegative() || d.toMinutes() < 1) {
            return "just now";
        }
        if (d.toHours() < 1) {
            return d.toMinutes() + " min ago";
        }
        if (d.toDays() < 1) {
            return d.toHours() + " h ago";
        }
        long days = d.toDays();
        return days == 1 ? "1 day ago" : days + " days ago";
    }

    // ---------------------------------------------------------------- actions

    public static void openUrl(Component parent, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String target = url.matches("(?i)^(https?|mailto):.*") ? url : "https://" + url;
        try {
            Desktop.getDesktop().browse(URI.create(target));
        } catch (Exception e) {
            copy(target);
            toast(parent, "Couldn't open a browser here, so the link was copied.");
        }
    }

    public static void email(Component parent, String to, String subject, String body) {
        String uri = "mailto:" + to + "?subject=" + enc(subject) + "&body=" + enc(body);
        try {
            Desktop.getDesktop().mail(URI.create(uri));
        } catch (Exception e) {
            copy(body);
            info(parent, "No email app is set up, so the message was copied. Send it to " + to + ".");
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static void copy(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Job Referral Connect", JOptionPane.INFORMATION_MESSAGE);
    }

    /** A short message that slides in at the bottom right and fades away; never blocks. */
    public static void toast(Component parent, String message) {
        Toast.show(parent, message, Toast.Tone.SUCCESS);
    }

    public static void toast(Component parent, String message, Toast.Tone tone) {
        Toast.show(parent, message, tone);
    }

    public static boolean confirm(Component parent, String message, String title) {
        return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    public static void error(Component parent, Throwable t) {
        String message = t instanceof ServiceException ? t.getMessage() : "Something went wrong: " + t.getMessage();
        JOptionPane.showMessageDialog(parent, message, "Job Referral Connect", JOptionPane.WARNING_MESSAGE);
    }

    /** Runs a UI action and turns any rule violation into a friendly dialog. */
    public static void attempt(Component parent, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            error(parent, e);
        }
    }

    public static Insets insets(int v, int h) {
        return new Insets(v, h, v, h);
    }
}
