package com.referralconnect.ui;

import com.referralconnect.model.RequestStatus;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Set;

/** Colours and fonts in one place, plus the Nimbus look-and-feel tuned to match. */
public final class Theme {

    private Theme() {
    }

    public static final Color BG = new Color(0xF4F6FB);
    public static final Color SURFACE = Color.WHITE;
    public static final Color BORDER = new Color(0xE2E8F0);
    public static final Color TEXT = new Color(0x1E293B);
    public static final Color MUTED = new Color(0x64748B);
    public static final Color PRIMARY = new Color(0x4F46E5);
    public static final Color PRIMARY_DARK = new Color(0x3730A3);
    public static final Color PRIMARY_SOFT = new Color(0xEEF2FF);
    public static final Color SUCCESS = new Color(0x047857);
    public static final Color SUCCESS_SOFT = new Color(0xD1FAE5);
    public static final Color WARNING = new Color(0xB45309);
    public static final Color WARNING_SOFT = new Color(0xFEF3C7);
    public static final Color DANGER = new Color(0xB91C1C);
    public static final Color DANGER_SOFT = new Color(0xFEE2E2);
    public static final Color INFO = new Color(0x1D4ED8);
    public static final Color INFO_SOFT = new Color(0xDBEAFE);
    public static final Color NEUTRAL_SOFT = new Color(0xE2E8F0);
    public static final Color ROW_ALT = new Color(0xF8FAFC);
    public static final Color SELECTION = new Color(0xE0E7FF);
    public static final Color HEADER_FROM = new Color(0x1E1B4B);
    public static final Color HEADER_TO = new Color(0x4338CA);

    public static final String FONT_FAMILY = pickFont();

    public static Font font(int style, float size) {
        return new Font(FONT_FAMILY, style, Math.round(size));
    }

    public static final Font BODY = font(Font.PLAIN, 13);
    public static final Font BODY_BOLD = font(Font.BOLD, 13);
    public static final Font SMALL = font(Font.PLAIN, 12);
    public static final Font SMALL_BOLD = font(Font.BOLD, 12);
    public static final Font H1 = font(Font.BOLD, 26);
    public static final Font H2 = font(Font.BOLD, 19);
    public static final Font H3 = font(Font.BOLD, 15);

    public static Color statusColor(RequestStatus s) {
        return switch (s) {
            case PENDING -> INFO;
            case NEEDS_INFO -> WARNING;
            case REFERRED -> SUCCESS;
            case DECLINED -> DANGER;
            case WITHDRAWN -> MUTED;
        };
    }

    public static Color statusSoft(RequestStatus s) {
        return switch (s) {
            case PENDING -> INFO_SOFT;
            case NEEDS_INFO -> WARNING_SOFT;
            case REFERRED -> SUCCESS_SOFT;
            case DECLINED -> DANGER_SOFT;
            case WITHDRAWN -> NEUTRAL_SOFT;
        };
    }

    private static String pickFont() {
        Set<String> available = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String candidate : new String[]{"Inter", "Segoe UI", "Helvetica Neue", "Roboto", "Noto Sans", "DejaVu Sans"}) {
            if (available.contains(candidate)) {
                return candidate;
            }
        }
        return Font.SANS_SERIF;
    }

    public static void install() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            // Nimbus ships with every JDK; if it is somehow missing the default look still works.
        }
        UIManager.put("control", BG);
        // Nimbus derives combo boxes, tabs and scrollbars from these; keep them light and neutral.
        UIManager.put("nimbusBase", new Color(0x8A99B4));
        UIManager.put("nimbusBlueGrey", new Color(0xD5DCE6));
        UIManager.put("nimbusFocus", PRIMARY);
        UIManager.put("nimbusSelectionBackground", PRIMARY);
        UIManager.put("nimbusSelection", PRIMARY);
        UIManager.put("nimbusLightBackground", SURFACE);
        UIManager.put("text", TEXT);
        UIManager.put("defaultFont", BODY);
        UIManager.put("Table.alternateRowColor", ROW_ALT);
        UIManager.put("Table.showGrid", false);
        UIManager.put("Table.intercellSpacing", new java.awt.Dimension(0, 0));
        UIManager.put("TabbedPane.font", BODY_BOLD);
        UIManager.put("ToolTip.font", SMALL);
    }
}
