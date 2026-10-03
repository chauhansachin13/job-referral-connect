package com.referralconnect.ui;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Small hand-drawn charts for the dashboards: horizontal bars, columns, a donut and a progress ring. */
public final class Charts {

    private Charts() {
    }

    public record Bar(String label, long value, Color color) {
        public Bar(String label, long value) {
            this(label, value, null);
        }
    }

    /** Labelled horizontal bars, largest first as given; click a bar to act on it. */
    public static final class BarChart extends JComponent {
        private List<Bar> bars = List.of();
        private Consumer<Bar> onClick;
        private int hover = -1;
        private static final int ROW = 30;

        public BarChart() {
            setOpaque(false);
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int i = e.getY() / ROW;
                    int h = i >= 0 && i < bars.size() ? i : -1;
                    if (h != hover) {
                        hover = h;
                        setCursor(Cursor.getPredefinedCursor(h >= 0 && onClick != null ? Cursor.HAND_CURSOR
                                : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    int i = e.getY() / ROW;
                    if (onClick != null && i >= 0 && i < bars.size()) {
                        onClick.accept(bars.get(i));
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        public void setBars(List<Bar> bars) {
            this.bars = List.copyOf(bars);
            revalidate();
            repaint();
        }

        public void onClick(Consumer<Bar> action) {
            this.onClick = action;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(300, Math.max(ROW, bars.size() * ROW));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Laf.smooth(g);
            long max = bars.stream().mapToLong(Bar::value).max().orElse(1);
            g2.setFont(Theme.SMALL);
            FontMetrics fm = g2.getFontMetrics();
            int labelW = 0;
            for (Bar b : bars) {
                labelW = Math.max(labelW, fm.stringWidth(b.label()));
            }
            labelW = Math.min(labelW + 12, getWidth() / 2);
            int valueW = 44;
            int barX = labelW;
            int barMax = Math.max(10, getWidth() - labelW - valueW);
            for (int i = 0; i < bars.size(); i++) {
                Bar b = bars.get(i);
                int y = i * ROW;
                if (i == hover && onClick != null) {
                    g2.setColor(Theme.ROW_HOVER);
                    g2.fillRoundRect(0, y + 2, getWidth(), ROW - 4, 8, 8);
                }
                g2.setColor(Theme.TEXT_2);
                g2.setFont(Theme.SMALL);
                String label = b.label();
                while (fm.stringWidth(label) > labelW - 12 && label.length() > 3) {
                    label = label.substring(0, label.length() - 2) + "…";
                }
                g2.drawString(label, 4, y + (ROW - fm.getHeight()) / 2 + fm.getAscent());
                g2.setColor(Theme.NEUTRAL_SOFT);
                g2.fillRoundRect(barX, y + ROW / 2 - 5, barMax, 10, 10, 10);
                int w = (int) Math.max(6, Math.round((double) b.value() / Math.max(1, max) * barMax));
                g2.setColor(b.color() != null ? b.color() : Theme.CHART[i % Theme.CHART.length]);
                g2.fillRoundRect(barX, y + ROW / 2 - 5, w, 10, 10, 10);
                g2.setColor(Theme.TEXT);
                g2.setFont(Theme.SMALL_BOLD);
                String v = String.valueOf(b.value());
                g2.drawString(v, getWidth() - g2.getFontMetrics().stringWidth(v) - 4,
                        y + (ROW - fm.getHeight()) / 2 + fm.getAscent());
            }
            if (bars.isEmpty()) {
                g2.setColor(Theme.MUTED);
                g2.drawString("No data yet", 4, fm.getAscent() + 6);
            }
            g2.dispose();
        }
    }

    /** Vertical columns with labels underneath, e.g. requests per week. */
    public static final class ColumnChart extends JComponent {
        private List<Bar> bars = List.of();

        public ColumnChart() {
            setOpaque(false);
        }

        public void setBars(List<Bar> bars) {
            this.bars = List.copyOf(bars);
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(320, 170);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Laf.smooth(g);
            g2.setFont(Theme.SMALL);
            FontMetrics fm = g2.getFontMetrics();
            int bottom = getHeight() - fm.getHeight() - 6;
            int top = 18;
            long max = Math.max(1, bars.stream().mapToLong(Bar::value).max().orElse(1));
            int n = Math.max(1, bars.size());
            double slot = (double) getWidth() / n;
            int colW = (int) Math.min(34, slot * 0.6);
            g2.setColor(Theme.BORDER);
            g2.setStroke(new BasicStroke(1f));
            for (int i = 0; i <= 3; i++) {
                int y = bottom - (bottom - top) * i / 3;
                g2.drawLine(0, y, getWidth(), y);
            }
            for (int i = 0; i < bars.size(); i++) {
                Bar b = bars.get(i);
                int x = (int) (slot * i + (slot - colW) / 2);
                int h = (int) Math.round((double) b.value() / max * (bottom - top));
                g2.setColor(b.color() != null ? b.color() : Theme.PRIMARY);
                if (h > 0) {
                    g2.fillRoundRect(x, bottom - h, colW, h, 8, 8);
                    g2.fillRect(x, bottom - Math.min(h, 4), colW, Math.min(h, 4));
                }
                g2.setColor(Theme.MUTED);
                g2.drawString(b.label(), (int) (slot * i + (slot - fm.stringWidth(b.label())) / 2),
                        getHeight() - fm.getDescent() - 2);
                if (b.value() > 0) {
                    g2.setColor(Theme.TEXT);
                    g2.setFont(Theme.SMALL_BOLD);
                    String v = String.valueOf(b.value());
                    g2.drawString(v, x + (colW - g2.getFontMetrics().stringWidth(v)) / 2f, bottom - h - 5);
                    g2.setFont(Theme.SMALL);
                }
            }
            g2.dispose();
        }
    }

    /** A ring split by value, with the total in the middle and a legend beside it. */
    public static final class Donut extends JComponent {
        private List<Bar> slices = List.of();
        private String centerLabel = "total";

        public Donut() {
            setOpaque(false);
        }

        public void setSlices(List<Bar> slices, String centerLabel) {
            this.slices = List.copyOf(slices);
            this.centerLabel = centerLabel;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(320, 150);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Laf.smooth(g);
            int d = Math.min(getHeight() - 8, 136);
            int x = 4;
            int y = (getHeight() - d) / 2;
            long total = slices.stream().mapToLong(Bar::value).sum();
            float stroke = 16f;
            g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            double inset = stroke / 2;
            if (total == 0) {
                g2.setColor(Theme.NEUTRAL_SOFT);
                g2.draw(new Arc2D.Double(x + inset, y + inset, d - stroke, d - stroke, 0, 360, Arc2D.OPEN));
            } else {
                double start = 90;
                for (int i = 0; i < slices.size(); i++) {
                    Bar s = slices.get(i);
                    double extent = -360.0 * s.value() / total;
                    g2.setColor(s.color() != null ? s.color() : Theme.CHART[i % Theme.CHART.length]);
                    g2.draw(new Arc2D.Double(x + inset, y + inset, d - stroke, d - stroke, start, extent, Arc2D.OPEN));
                    start += extent;
                }
            }
            g2.setColor(Theme.TEXT);
            g2.setFont(Theme.font(Font.BOLD, 22));
            FontMetrics fm = g2.getFontMetrics();
            String t = String.valueOf(total);
            g2.drawString(t, x + (d - fm.stringWidth(t)) / 2f, y + d / 2f + 4);
            g2.setFont(Theme.SMALL);
            g2.setColor(Theme.MUTED);
            fm = g2.getFontMetrics();
            g2.drawString(centerLabel, x + (d - fm.stringWidth(centerLabel)) / 2f, y + d / 2f + 4 + fm.getHeight());

            int lx = x + d + 22;
            int ly = y + 10;
            for (int i = 0; i < slices.size(); i++) {
                Bar s = slices.get(i);
                g2.setColor(s.color() != null ? s.color() : Theme.CHART[i % Theme.CHART.length]);
                g2.fillRoundRect(lx, ly + i * 22, 10, 10, 4, 4);
                g2.setColor(Theme.TEXT_2);
                g2.setFont(Theme.SMALL);
                g2.drawString(s.label(), lx + 18, ly + i * 22 + 9);
                g2.setFont(Theme.SMALL_BOLD);
                g2.setColor(Theme.TEXT);
                String v = String.valueOf(s.value());
                g2.drawString(v, getWidth() - g2.getFontMetrics().stringWidth(v) - 6, ly + i * 22 + 9);
            }
            g2.dispose();
        }
    }

    /** A percentage ring, e.g. profile strength or match score. */
    public static final class Ring extends JComponent {
        private int percent;
        private Color color;
        private final int size;

        public Ring(int size) {
            this.size = size;
            setOpaque(false);
        }

        public void setValue(int percent, Color color) {
            this.percent = Math.max(0, Math.min(100, percent));
            this.color = color;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(size, size);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Laf.smooth(g);
            float stroke = Math.max(4f, size / 9f);
            double inset = stroke / 2 + 1;
            g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Theme.NEUTRAL_SOFT);
            g2.draw(new Arc2D.Double(inset, inset, size - inset * 2, size - inset * 2, 0, 360, Arc2D.OPEN));
            g2.setColor(color == null ? Theme.PRIMARY : color);
            if (percent > 0) {
                g2.draw(new Arc2D.Double(inset, inset, size - inset * 2, size - inset * 2, 90, -3.6 * percent,
                        Arc2D.OPEN));
            }
            g2.setColor(Theme.TEXT);
            g2.setFont(Theme.font(Font.BOLD, Math.max(10, size / 3.6f)));
            FontMetrics fm = g2.getFontMetrics();
            String t = percent + (size >= 56 ? "%" : "");
            g2.drawString(t, (size - fm.stringWidth(t)) / 2f, (size - fm.getHeight()) / 2f + fm.getAscent());
            g2.dispose();
        }
    }

    /** Sorted bars from label → count, keeping the top {@code limit}. */
    public static List<Bar> top(java.util.Map<String, Long> counts, int limit) {
        List<Bar> out = new ArrayList<>();
        counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .forEach(e -> out.add(new Bar(e.getKey(), e.getValue())));
        return out;
    }
}
