package com.referralconnect.ui;

import com.referralconnect.model.RequestStatus;
import com.referralconnect.service.ServiceException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
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
import java.awt.RenderingHints;
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

/** Small shared widgets and helpers so every screen looks and behaves the same. */
public final class Ui {

    private Ui() {
    }

    // ---------------------------------------------------------------- buttons

    public enum Kind { PRIMARY, SECONDARY, SUCCESS, WARNING, DANGER, GHOST }

    /** A flat, rounded button painted by hand so it looks the same under any look-and-feel. */
    public static final class FlatButton extends JButton {
        private Kind kind;
        private boolean hover;

        public FlatButton(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setFont(Theme.BODY_BOLD);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
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

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            boolean compact = getFont().getSize() < Theme.BODY_BOLD.getSize();
            return new Dimension(fm.stringWidth(getText()) + (compact ? 20 : 32), compact ? 26 : 36);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
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
                    text = Color.WHITE;
                }
                case WARNING -> {
                    fill = hover ? new Color(0xFDE68A) : Theme.WARNING_SOFT;
                    text = Theme.WARNING;
                }
                case DANGER -> {
                    fill = hover ? new Color(0xFECACA) : Theme.DANGER_SOFT;
                    text = Theme.DANGER;
                }
                case GHOST -> {
                    fill = hover ? new Color(255, 255, 255, 40) : new Color(0, 0, 0, 0);
                    text = Color.WHITE;
                    outline = new Color(255, 255, 255, 120);
                }
                default -> {
                    fill = hover ? Theme.PRIMARY_SOFT : Theme.SURFACE;
                    text = Theme.PRIMARY_DARK;
                    outline = Theme.BORDER;
                }
            }
            if (!isEnabled()) {
                fill = kind == Kind.GHOST ? new Color(0, 0, 0, 0) : new Color(0xE2E8F0);
                text = new Color(0x94A3B8);
                outline = null;
            }
            int w = getWidth();
            int h = getHeight();
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
            if (outline != null) {
                g2.setColor(outline);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
            }
            g2.setFont(getFont());
            g2.setColor(text);
            FontMetrics fm = g2.getFontMetrics();
            int x = (w - fm.stringWidth(getText())) / 2;
            int y = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), x, y);
            g2.dispose();
        }
    }

    public static FlatButton button(String text, Kind kind, Runnable action) {
        FlatButton b = new FlatButton(text, kind);
        b.addActionListener(e -> action.run());
        return b;
    }

    /** A shorter button for inline use next to text, e.g. "Open" beside a link. */
    public static FlatButton smallButton(String text, Kind kind, Runnable action) {
        FlatButton b = button(text, kind, action);
        b.setFont(Theme.SMALL_BOLD);
        return b;
    }

    // ---------------------------------------------------------------- panels & labels

    /** White rounded card with a hairline border. */
    public static final class Card extends JPanel {
        public Card(LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.SURFACE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.setColor(Theme.BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
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
            setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
            setColors(fg, bg);
        }

        public void setColors(Color fg, Color bg) {
            this.fill = bg;
            setForeground(fg);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static Pill statusPill(RequestStatus s) {
        return new Pill(s.label(), Theme.statusColor(s), Theme.statusSoft(s));
    }

    public static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    /**
     * Word-wrapping text. Swing labels do not wrap, and a read-only JTextArea both ignores
     * transparency under Nimbus and reports a one-line height until it has been laid out once.
     * This component wraps to whatever width it is given and asks for a fresh layout whenever
     * that width changes, so its height is always right.
     */
    public static final class WrapText extends JComponent {
        private static final int DEFAULT_WIDTH = 380;
        private String text;
        private int laidOutWidth = -1;

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
                javax.swing.SwingUtilities.invokeLater(this::revalidate);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
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

    public static JScrollPane scroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        // Nimbus otherwise draws its own frame around the viewport, on top of ours.
        sp.setViewportBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Theme.SURFACE);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    public static JScrollPane bareScroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setViewportBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    // ---------------------------------------------------------------- tables

    public static void styleTable(JTable table) {
        table.setRowHeight(34);
        table.setFont(Theme.BODY);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setSelectionBackground(Theme.SELECTION);
        table.setSelectionForeground(Theme.TEXT);
        table.getTableHeader().setFont(Theme.SMALL_BOLD);
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoCreateRowSorter(false);
        DefaultTableCellRenderer base = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, false, r, c);
                // This renderer is shared by every column; reset what centerColumn may have changed.
                setHorizontalAlignment(SwingConstants.LEFT);
                setBorder(padding(0, 10, 0, 10));
                setBackground(sel ? Theme.SELECTION : (r % 2 == 0 ? Theme.SURFACE : Theme.ROW_ALT));
                setForeground(Theme.TEXT);
                return this;
            }
        };
        table.setDefaultRenderer(Object.class, base);
    }

    /** Renders a {@link RequestStatus} cell as a coloured pill. */
    public static final class StatusRenderer extends DefaultTableCellRenderer {
        private final Pill pill = new Pill("", Theme.TEXT, Theme.NEUTRAL_SOFT);
        private final JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 7));

        public StatusRenderer() {
            holder.add(pill);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            holder.setBackground(sel ? Theme.SELECTION : (r % 2 == 0 ? Theme.SURFACE : Theme.ROW_ALT));
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

    public static String date(Instant t) {
        return t == null ? "" : DATE.format(t);
    }

    public static String dateTime(Instant t) {
        return t == null ? "" : DATE_TIME.format(t);
    }

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());

    public static String dayMonth(Instant t) {
        return t == null ? "" : DAY_MONTH.format(t);
    }

    /** "45m", "16h", "3d" — for narrow table columns. */
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
            info(parent, "Could not open a browser here, so the link was copied:\n" + target);
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
