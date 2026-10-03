package com.referralconnect.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Line icons drawn with Java2D on a 24×24 grid, so they stay sharp at any size and take any
 * colour. Each glyph is a list of outlined shapes, plus optional filled ones.
 */
public final class Icons {

    private Icons() {
    }

    public enum Glyph {
        HOME, BRIEFCASE, BOOKMARK, BOOKMARK_FILLED, SEND, BELL, BUILDING, USER, INBOX, CHART, SLIDERS, MOON, SUN,
        LOGOUT, SEARCH, REFRESH, DOWNLOAD, EXTERNAL, STAR, PLUS, CHECK, X, MESSAGE, CLOCK, PIN, ZAP, MORE,
        CHEVRON_LEFT, CHEVRON_RIGHT, TRASH, COPY, EYE_OFF, SPARKLE, CAP, TARGET, KANBAN, FILTER, USERS, AWARD
    }

    /** An icon of {@code glyph} at {@code size} pixels, drawn in {@code color}. */
    public static Icon get(Glyph glyph, int size, Color color) {
        return new GlyphIcon(glyph, size, () -> color);
    }

    /** An icon whose colour is looked up each time it is painted (follows theme switches and hover). */
    public static Icon live(Glyph glyph, int size, Supplier<Color> color) {
        return new GlyphIcon(glyph, size, color);
    }

    private record Drawing(List<Shape> strokes, List<Shape> fills) {
    }

    private static final class GlyphIcon implements Icon {
        private final Glyph glyph;
        private final int size;
        private final Supplier<Color> color;

        GlyphIcon(Glyph glyph, int size, Supplier<Color> color) {
            this.glyph = glyph;
            this.size = size;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = Laf.smooth(g);
            g2.translate(x, y);
            double scale = size / 24.0;
            g2.scale(scale, scale);
            g2.setColor(color.get());
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Drawing d = drawing(glyph);
            for (Shape s : d.strokes()) {
                g2.draw(s);
            }
            for (Shape s : d.fills()) {
                g2.fill(s);
            }
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

    // ---------------------------------------------------------------- shapes

    private static Path2D path(double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }
        return p;
    }

    private static Path2D closed(double... xy) {
        Path2D p = path(xy);
        p.closePath();
        return p;
    }

    private static Shape line(double x1, double y1, double x2, double y2) {
        return new Line2D.Double(x1, y1, x2, y2);
    }

    private static Shape circle(double cx, double cy, double r) {
        return new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
    }

    private static Shape round(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2);
    }

    private static Drawing drawing(Glyph glyph) {
        List<Shape> s = new ArrayList<>();
        List<Shape> f = new ArrayList<>();
        switch (glyph) {
            case HOME -> {
                s.add(path(3, 10.5, 12, 3.5, 21, 10.5));
                s.add(path(5.5, 9, 5.5, 20, 18.5, 20, 18.5, 9));
                s.add(path(10, 20, 10, 14.5, 14, 14.5, 14, 20));
            }
            case BRIEFCASE -> {
                s.add(round(3, 7, 18, 13, 2.5));
                s.add(path(9, 7, 9, 5.2, 15, 5.2, 15, 7));
                s.add(line(3, 12.5, 21, 12.5));
            }
            case BOOKMARK -> s.add(closed(6.5, 3.5, 17.5, 3.5, 17.5, 20.5, 12, 16.5, 6.5, 20.5));
            case BOOKMARK_FILLED -> {
                Shape b = closed(6.5, 3.5, 17.5, 3.5, 17.5, 20.5, 12, 16.5, 6.5, 20.5);
                s.add(b);
                f.add(b);
            }
            case SEND -> {
                s.add(closed(21, 3, 3, 10.5, 10.5, 13.5, 13.5, 21));
                s.add(line(21, 3, 10.5, 13.5));
            }
            case BELL -> {
                Path2D p = new Path2D.Double();
                p.moveTo(6, 16.5);
                p.lineTo(6, 11);
                p.curveTo(6, 7.7, 8.7, 5, 12, 5);
                p.curveTo(15.3, 5, 18, 7.7, 18, 11);
                p.lineTo(18, 16.5);
                p.lineTo(19.5, 18.5);
                p.lineTo(4.5, 18.5);
                p.closePath();
                s.add(p);
                s.add(new Arc2D.Double(10, 18.5, 4, 3.4, 180, 180, Arc2D.OPEN));
                s.add(line(12, 3, 12, 5));
            }
            case BUILDING -> {
                s.add(round(5, 3, 14, 18, 1.5));
                for (double y : new double[]{7.5, 11, 14.5}) {
                    s.add(line(9, y, 10, y));
                    s.add(line(14, y, 15, y));
                }
                s.add(path(10.5, 21, 10.5, 17.5, 13.5, 17.5, 13.5, 21));
            }
            case USER -> {
                s.add(circle(12, 8, 3.8));
                Path2D p = new Path2D.Double();
                p.moveTo(4.5, 20.5);
                p.curveTo(4.5, 16, 8, 14, 12, 14);
                p.curveTo(16, 14, 19.5, 16, 19.5, 20.5);
                s.add(p);
            }
            case USERS -> {
                s.add(circle(9, 8, 3.4));
                Path2D p = new Path2D.Double();
                p.moveTo(2.5, 20);
                p.curveTo(2.5, 16, 5.5, 14, 9, 14);
                p.curveTo(12.5, 14, 15.5, 16, 15.5, 20);
                s.add(p);
                s.add(new Arc2D.Double(13.2, 4.6, 6.8, 6.8, 100, -200, Arc2D.OPEN));
                Path2D q = new Path2D.Double();
                q.moveTo(17, 14.2);
                q.curveTo(19.6, 14.8, 21.5, 16.8, 21.5, 20);
                s.add(q);
            }
            case INBOX -> {
                s.add(closed(3, 13, 5.5, 5, 18.5, 5, 21, 13, 21, 19, 3, 19));
                s.add(path(3, 13, 8, 13, 9.5, 15.5, 14.5, 15.5, 16, 13, 21, 13));
            }
            case CHART -> {
                s.add(line(4, 20, 20, 20));
                s.add(line(7.5, 17, 7.5, 12));
                s.add(line(12, 17, 12, 6));
                s.add(line(16.5, 17, 16.5, 10));
            }
            case SLIDERS -> {
                s.add(line(4, 7, 20, 7));
                s.add(line(4, 12, 20, 12));
                s.add(line(4, 17, 20, 17));
                for (double[] k : new double[][]{{9, 7}, {15, 12}, {8, 17}}) {
                    s.add(circle(k[0], k[1], 2));
                }
            }
            case MOON -> {
                Area a = new Area(circle(12, 12, 8));
                a.subtract(new Area(circle(16.5, 8, 7)));
                s.add(a);
            }
            case SUN -> {
                s.add(circle(12, 12, 4));
                for (int i = 0; i < 8; i++) {
                    double ang = Math.PI / 4 * i;
                    s.add(line(12 + Math.cos(ang) * 7, 12 + Math.sin(ang) * 7, 12 + Math.cos(ang) * 9.3,
                            12 + Math.sin(ang) * 9.3));
                }
            }
            case LOGOUT -> {
                s.add(path(9.5, 4, 5, 4, 5, 20, 9.5, 20));
                s.add(path(14.5, 8, 18.5, 12, 14.5, 16));
                s.add(line(18.5, 12, 9.5, 12));
            }
            case SEARCH -> {
                s.add(circle(10.8, 10.8, 6.3));
                s.add(line(15.6, 15.6, 20, 20));
            }
            case REFRESH -> {
                s.add(new Arc2D.Double(4.5, 4.5, 15, 15, 40, 290, Arc2D.OPEN));
                s.add(path(19.5, 4.5, 19.5, 9, 15, 9));
            }
            case DOWNLOAD -> {
                s.add(line(12, 4, 12, 15));
                s.add(path(7.5, 10.5, 12, 15, 16.5, 10.5));
                s.add(line(5, 19.5, 19, 19.5));
            }
            case EXTERNAL -> {
                s.add(path(14, 4, 20, 4, 20, 10));
                s.add(line(20, 4, 11, 13));
                s.add(path(18, 14, 18, 19.5, 4.5, 19.5, 4.5, 6, 10, 6));
            }
            case STAR -> {
                Path2D p = new Path2D.Double();
                for (int i = 0; i < 10; i++) {
                    double r = i % 2 == 0 ? 9 : 4;
                    double ang = -Math.PI / 2 + Math.PI / 5 * i;
                    double x = 12 + Math.cos(ang) * r;
                    double y = 12.6 + Math.sin(ang) * r;
                    if (i == 0) {
                        p.moveTo(x, y);
                    } else {
                        p.lineTo(x, y);
                    }
                }
                p.closePath();
                s.add(p);
            }
            case PLUS -> {
                s.add(line(12, 5, 12, 19));
                s.add(line(5, 12, 19, 12));
            }
            case CHECK -> s.add(path(5, 12.5, 10, 17, 19, 7.5));
            case X -> {
                s.add(line(6.5, 6.5, 17.5, 17.5));
                s.add(line(17.5, 6.5, 6.5, 17.5));
            }
            case MESSAGE -> {
                Path2D p = new Path2D.Double();
                p.append(round(3.5, 4.5, 17, 12, 3), false);
                s.add(p);
                s.add(path(8.5, 16.5, 7.5, 20.5, 12.5, 16.5));
            }
            case CLOCK -> {
                s.add(circle(12, 12, 8.5));
                s.add(path(12, 7.5, 12, 12, 15, 14));
            }
            case PIN -> {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 21);
                p.curveTo(12, 21, 5, 14.5, 5, 9.8);
                p.curveTo(5, 5.9, 8.1, 3, 12, 3);
                p.curveTo(15.9, 3, 19, 5.9, 19, 9.8);
                p.curveTo(19, 14.5, 12, 21, 12, 21);
                p.closePath();
                s.add(p);
                s.add(circle(12, 9.8, 2.5));
            }
            case ZAP -> s.add(closed(13, 3, 5, 13.5, 11.5, 13.5, 10.5, 21, 19, 10, 12.5, 10));
            case MORE -> {
                f.add(circle(6, 12, 1.7));
                f.add(circle(12, 12, 1.7));
                f.add(circle(18, 12, 1.7));
            }
            case CHEVRON_LEFT -> s.add(path(14.5, 6, 8.5, 12, 14.5, 18));
            case CHEVRON_RIGHT -> s.add(path(9.5, 6, 15.5, 12, 9.5, 18));
            case TRASH -> {
                s.add(line(4.5, 7, 19.5, 7));
                s.add(path(9.5, 7, 9.5, 4.5, 14.5, 4.5, 14.5, 7));
                s.add(path(6.5, 7, 7.5, 20, 16.5, 20, 17.5, 7));
            }
            case COPY -> {
                s.add(round(8.5, 8.5, 11.5, 11.5, 2));
                s.add(path(15.5, 8.5, 15.5, 4, 4, 4, 4, 15.5, 8.5, 15.5));
            }
            case EYE_OFF -> {
                Path2D p = new Path2D.Double();
                p.moveTo(2.5, 12);
                p.curveTo(5, 7, 8.5, 5.5, 12, 5.5);
                p.curveTo(15.5, 5.5, 19, 7, 21.5, 12);
                p.curveTo(19, 17, 15.5, 18.5, 12, 18.5);
                p.curveTo(8.5, 18.5, 5, 17, 2.5, 12);
                p.closePath();
                s.add(p);
                s.add(circle(12, 12, 3));
                s.add(line(4, 4, 20, 20));
            }
            case SPARKLE -> {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 3);
                p.quadTo(13, 11, 21, 12);
                p.quadTo(13, 13, 12, 21);
                p.quadTo(11, 13, 3, 12);
                p.quadTo(11, 11, 12, 3);
                p.closePath();
                s.add(p);
            }
            case CAP -> {
                s.add(closed(2.5, 9.5, 12, 5, 21.5, 9.5, 12, 14));
                Path2D p = new Path2D.Double();
                p.moveTo(6.5, 11.6);
                p.lineTo(6.5, 16);
                p.curveTo(9.5, 19, 14.5, 19, 17.5, 16);
                p.lineTo(17.5, 11.6);
                s.add(p);
            }
            case TARGET -> {
                s.add(circle(12, 12, 8.5));
                s.add(circle(12, 12, 4.8));
                f.add(circle(12, 12, 1.6));
            }
            case KANBAN -> {
                s.add(round(3.5, 4, 17, 16, 2.5));
                s.add(line(9.5, 8, 9.5, 15));
                s.add(line(14.5, 8, 14.5, 12));
                s.add(line(7, 8, 7, 11.5));
                s.add(line(17, 8, 17, 16));
            }
            case FILTER -> s.add(closed(3.5, 5, 20.5, 5, 14, 12.5, 14, 19, 10, 17, 10, 12.5));
            case AWARD -> {
                s.add(circle(12, 9, 5.5));
                s.add(path(8.5, 13.2, 7, 21, 12, 18.5, 17, 21, 15.5, 13.2));
            }
        }
        return new Drawing(s, f);
    }
}
