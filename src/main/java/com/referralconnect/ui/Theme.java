package com.referralconnect.ui;

import com.referralconnect.model.RequestStatus;
import com.referralconnect.model.TrackedJob;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Set;

/**
 * Colours and fonts in one place. There is a light and a dark palette; {@link #apply(boolean)}
 * switches between them and re-tunes Swing's own widgets (dialogs, menus, scroll bars, combo
 * boxes) to match. Screens read these colours when they are built or painted, so the app rebuilds
 * the current screen after a switch.
 */
public final class Theme {

    private Theme() {
    }

    private static boolean dark;

    public static Color BG;
    public static Color SURFACE;
    public static Color SURFACE_2;
    public static Color BORDER;
    public static Color BORDER_STRONG;
    public static Color TEXT;
    public static Color TEXT_2;
    public static Color MUTED;
    public static Color FAINT;
    public static Color PRIMARY;
    public static Color PRIMARY_DARK;
    public static Color PRIMARY_SOFT;
    public static Color PRIMARY_TEXT;
    public static Color SUCCESS;
    public static Color SUCCESS_SOFT;
    public static Color WARNING;
    public static Color WARNING_SOFT;
    public static Color DANGER;
    public static Color DANGER_SOFT;
    public static Color INFO;
    public static Color INFO_SOFT;
    public static Color NEUTRAL_SOFT;
    public static Color ROW_ALT;
    public static Color ROW_HOVER;
    public static Color SELECTION;
    public static Color SIDEBAR;
    public static Color SIDEBAR_TEXT;
    public static Color SIDEBAR_MUTED;
    public static Color SIDEBAR_ACTIVE;
    public static Color SHADOW;
    public static Color HEADER_FROM;
    public static Color HEADER_TO;

    /** Bars and slices in charts, in order. */
    public static Color[] CHART;

    static {
        palette(false);
    }

    public static boolean isDark() {
        return dark;
    }

    private static void palette(boolean darkMode) {
        dark = darkMode;
        if (!darkMode) {
            BG = new Color(0xF4F6FB);
            SURFACE = Color.WHITE;
            SURFACE_2 = new Color(0xF8FAFC);
            BORDER = new Color(0xE4E8F0);
            BORDER_STRONG = new Color(0xCBD5E1);
            TEXT = new Color(0x0F172A);
            TEXT_2 = new Color(0x334155);
            MUTED = new Color(0x64748B);
            FAINT = new Color(0x94A3B8);
            PRIMARY = new Color(0x4F46E5);
            PRIMARY_DARK = new Color(0x4338CA);
            PRIMARY_SOFT = new Color(0xEEF2FF);
            PRIMARY_TEXT = new Color(0x3730A3);
            SUCCESS = new Color(0x047857);
            SUCCESS_SOFT = new Color(0xD1FAE5);
            WARNING = new Color(0xB45309);
            WARNING_SOFT = new Color(0xFEF3C7);
            DANGER = new Color(0xB91C1C);
            DANGER_SOFT = new Color(0xFEE2E2);
            INFO = new Color(0x1D4ED8);
            INFO_SOFT = new Color(0xDBEAFE);
            NEUTRAL_SOFT = new Color(0xEEF1F6);
            ROW_ALT = new Color(0xFAFBFD);
            ROW_HOVER = new Color(0xF1F5F9);
            SELECTION = new Color(0xE8EAFD);
            SHADOW = new Color(15, 23, 42, 18);
        } else {
            BG = new Color(0x0B0F1A);
            SURFACE = new Color(0x131A29);
            SURFACE_2 = new Color(0x182133);
            BORDER = new Color(0x253047);
            BORDER_STRONG = new Color(0x34405A);
            TEXT = new Color(0xE6E9F0);
            TEXT_2 = new Color(0xC8CFDC);
            MUTED = new Color(0x95A1B6);
            FAINT = new Color(0x6B7790);
            PRIMARY = new Color(0x6D68F7);
            PRIMARY_DARK = new Color(0x8582FA);
            PRIMARY_SOFT = new Color(0x24234D);
            PRIMARY_TEXT = new Color(0xC7CBFE);
            SUCCESS = new Color(0x34D399);
            SUCCESS_SOFT = new Color(0x0D3329);
            WARNING = new Color(0xFBBF24);
            WARNING_SOFT = new Color(0x3A2E0E);
            DANGER = new Color(0xF87171);
            DANGER_SOFT = new Color(0x3D1A1C);
            INFO = new Color(0x7CB4FF);
            INFO_SOFT = new Color(0x172745);
            NEUTRAL_SOFT = new Color(0x1E2738);
            ROW_ALT = new Color(0x151D2D);
            ROW_HOVER = new Color(0x1B2538);
            SELECTION = new Color(0x2A2F5E);
            SHADOW = new Color(0, 0, 0, 60);
        }
        SIDEBAR = darkMode ? new Color(0x080C15) : new Color(0x111633);
        SIDEBAR_TEXT = new Color(0xE2E6F3);
        SIDEBAR_MUTED = new Color(0x8D96B8);
        SIDEBAR_ACTIVE = new Color(0x6D68F7);
        HEADER_FROM = new Color(0x1E1B4B);
        HEADER_TO = new Color(0x4338CA);
        CHART = new Color[]{PRIMARY, new Color(0x0EA5E9), new Color(0x10B981), new Color(0xF59E0B),
                new Color(0xEC4899), new Color(0x8B5CF6), new Color(0x14B8A6), new Color(0xF97316)};
    }

    // ---------------------------------------------------------------- fonts

    public static final String FONT_FAMILY = pickFont();

    public static Font font(int style, float size) {
        return new Font(FONT_FAMILY, style, Math.round(size));
    }

    public static final Font BODY = font(Font.PLAIN, 13);
    public static final Font BODY_BOLD = font(Font.BOLD, 13);
    public static final Font SMALL = font(Font.PLAIN, 12);
    public static final Font SMALL_BOLD = font(Font.BOLD, 12);
    public static final Font TINY_BOLD = font(Font.BOLD, 10);
    public static final Font H1 = font(Font.BOLD, 24);
    public static final Font H2 = font(Font.BOLD, 19);
    public static final Font H3 = font(Font.BOLD, 15);
    public static final Font STAT = font(Font.BOLD, 28);

    private static String pickFont() {
        Set<String> available = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String candidate : new String[]{"Inter", "Segoe UI", "Helvetica Neue", "Roboto", "Noto Sans", "DejaVu Sans"}) {
            if (available.contains(candidate)) {
                return candidate;
            }
        }
        return Font.SANS_SERIF;
    }

    // ---------------------------------------------------------------- semantic colours

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

    public static Color stageColor(TrackedJob.Stage s) {
        return switch (s) {
            case SAVED -> PRIMARY;
            case APPLIED -> INFO;
            case INTERVIEWING -> WARNING;
            case OFFER -> SUCCESS;
            case CLOSED -> MUTED;
        };
    }

    /** Green for a strong match, through amber, to grey for a weak one. */
    public static Color matchColor(int score) {
        return score >= 80 ? SUCCESS : score >= 65 ? CHART[2] : score >= 45 ? WARNING : FAINT;
    }

    /** {@code c} at the given opacity (0–255). */
    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    // ---------------------------------------------------------------- Swing defaults

    /** Picks the palette and sets up Swing's own widgets to match. Call before building any screen. */
    public static void apply(boolean darkMode) {
        palette(darkMode);
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try {
            MetalLookAndFeel.setCurrentTheme(new MetalPalette());
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception e) {
            // Metal ships with every JDK; if it somehow fails the defaults still work.
        }
        Laf.install();
    }

    /** Back-compat entry point: the light theme. */
    public static void install() {
        apply(dark);
    }

    static ColorUIResource res(Color c) {
        return new ColorUIResource(c);
    }

    /** Colours and fonts for Swing's own widgets, from the current palette. */
    static void swingDefaults() {
        Object[][] colors = {
                {"Panel.background", BG}, {"Panel.foreground", TEXT},
                {"Label.foreground", TEXT}, {"Label.disabledForeground", FAINT},
                {"OptionPane.background", SURFACE}, {"OptionPane.messageForeground", TEXT},
                {"RootPane.background", SURFACE},
                {"TextField.background", SURFACE}, {"TextField.foreground", TEXT},
                {"TextField.caretForeground", TEXT}, {"TextField.selectionBackground", SELECTION},
                {"TextField.selectionForeground", TEXT}, {"TextField.inactiveForeground", FAINT},
                {"TextField.inactiveBackground", SURFACE_2}, {"TextField.disabledBackground", SURFACE_2},
                {"PasswordField.background", SURFACE}, {"PasswordField.foreground", TEXT},
                {"PasswordField.caretForeground", TEXT}, {"PasswordField.selectionBackground", SELECTION},
                {"PasswordField.selectionForeground", TEXT}, {"PasswordField.inactiveBackground", SURFACE_2},
                {"FormattedTextField.background", SURFACE}, {"FormattedTextField.foreground", TEXT},
                {"TextArea.background", SURFACE}, {"TextArea.foreground", TEXT}, {"TextArea.caretForeground", TEXT},
                {"TextArea.selectionBackground", SELECTION}, {"TextArea.selectionForeground", TEXT},
                {"TextArea.inactiveBackground", SURFACE_2},
                {"EditorPane.background", SURFACE}, {"EditorPane.foreground", TEXT},
                {"ComboBox.background", SURFACE}, {"ComboBox.foreground", TEXT},
                {"ComboBox.selectionBackground", PRIMARY_SOFT}, {"ComboBox.selectionForeground", TEXT},
                {"ComboBox.disabledBackground", SURFACE_2}, {"ComboBox.disabledForeground", FAINT},
                {"ComboBox.buttonBackground", SURFACE},
                {"List.background", SURFACE}, {"List.foreground", TEXT},
                {"List.selectionBackground", PRIMARY_SOFT}, {"List.selectionForeground", TEXT},
                {"Table.background", SURFACE}, {"Table.foreground", TEXT}, {"Table.gridColor", BORDER},
                {"Table.selectionBackground", SELECTION}, {"Table.selectionForeground", TEXT},
                {"TableHeader.background", SURFACE}, {"TableHeader.foreground", MUTED},
                {"ScrollPane.background", SURFACE}, {"Viewport.background", SURFACE},
                {"ScrollBar.background", SURFACE}, {"ScrollBar.track", SURFACE}, {"ScrollBar.thumb", BORDER_STRONG},
                {"CheckBox.background", SURFACE}, {"CheckBox.foreground", TEXT},
                {"RadioButton.background", SURFACE}, {"RadioButton.foreground", TEXT},
                {"Button.background", SURFACE}, {"Button.foreground", TEXT}, {"Button.select", PRIMARY_SOFT},
                {"ToggleButton.background", SURFACE}, {"ToggleButton.foreground", TEXT},
                {"ToolTip.background", dark ? SURFACE_2 : new Color(0x1E293B)},
                {"ToolTip.foreground", dark ? TEXT : Color.WHITE},
                {"PopupMenu.background", SURFACE}, {"PopupMenu.foreground", TEXT},
                {"MenuItem.background", SURFACE}, {"MenuItem.foreground", TEXT},
                {"MenuItem.selectionBackground", PRIMARY_SOFT}, {"MenuItem.selectionForeground", TEXT},
                {"MenuItem.acceleratorForeground", MUTED}, {"MenuItem.acceleratorSelectionForeground", MUTED},
                {"Menu.background", SURFACE}, {"Menu.foreground", TEXT},
                {"Separator.foreground", BORDER}, {"Separator.background", SURFACE},
                {"PopupMenuSeparator.foreground", BORDER}, {"PopupMenuSeparator.background", SURFACE},
                {"SplitPane.background", BG}, {"SplitPaneDivider.draggingColor", BORDER},
                {"ProgressBar.foreground", PRIMARY}, {"ProgressBar.background", NEUTRAL_SOFT},
                {"TabbedPane.background", BG}, {"TabbedPane.foreground", TEXT},
                {"TabbedPane.selected", SURFACE}, {"TabbedPane.contentAreaColor", BG},
                {"FileChooser.background", SURFACE}, {"Desktop.background", BG},
                {"control", BG}, {"text", TEXT}, {"textText", TEXT}, {"controlText", TEXT},
                {"window", SURFACE}, {"info", SURFACE}, {"infoText", TEXT},
        };
        for (Object[] kv : colors) {
            UIManager.put(kv[0], res((Color) kv[1]));
        }
        Object[][] fonts = {
                {"Label.font", BODY}, {"Button.font", BODY_BOLD}, {"ToggleButton.font", BODY_BOLD},
                {"TextField.font", BODY}, {"PasswordField.font", BODY}, {"TextArea.font", BODY},
                {"ComboBox.font", BODY}, {"List.font", BODY}, {"Table.font", BODY}, {"TableHeader.font", SMALL_BOLD},
                {"CheckBox.font", BODY}, {"RadioButton.font", BODY}, {"ToolTip.font", SMALL},
                {"MenuItem.font", BODY}, {"Menu.font", BODY}, {"PopupMenu.font", BODY}, {"OptionPane.messageFont", BODY},
                {"TabbedPane.font", BODY_BOLD}, {"TitledBorder.font", SMALL_BOLD},
        };
        for (Object[] kv : fonts) {
            UIManager.put(kv[0], new FontUIResource((Font) kv[1]));
        }
        UIManager.put("Table.showGrid", Boolean.FALSE);
        UIManager.put("Table.intercellSpacing", new java.awt.Dimension(0, 0));
        UIManager.put("Button.rollover", Boolean.TRUE);
        UIManager.put("ComboBox.squareButton", Boolean.FALSE);
        UIManager.put("ToolTipManager.enableToolTipMode", "allWindows");
    }

    /** Feeds the palette and fonts into Metal so its dialogs and menus fit in. */
    private static final class MetalPalette extends DefaultMetalTheme {
        @Override
        public String getName() {
            return "Referral Connect";
        }

        @Override
        protected ColorUIResource getPrimary1() {
            return res(PRIMARY);
        }

        @Override
        protected ColorUIResource getPrimary2() {
            return res(BORDER_STRONG);
        }

        @Override
        protected ColorUIResource getPrimary3() {
            return res(SELECTION);
        }

        @Override
        protected ColorUIResource getSecondary1() {
            return res(BORDER_STRONG);
        }

        @Override
        protected ColorUIResource getSecondary2() {
            return res(BORDER);
        }

        @Override
        protected ColorUIResource getSecondary3() {
            return res(BG);
        }

        @Override
        protected ColorUIResource getBlack() {
            return res(TEXT);
        }

        @Override
        protected ColorUIResource getWhite() {
            return res(SURFACE);
        }

        @Override
        public ColorUIResource getControlTextColor() {
            return res(TEXT);
        }

        @Override
        public ColorUIResource getSystemTextColor() {
            return res(TEXT);
        }

        @Override
        public ColorUIResource getUserTextColor() {
            return res(TEXT);
        }

        @Override
        public ColorUIResource getMenuForeground() {
            return res(TEXT);
        }

        @Override
        public FontUIResource getControlTextFont() {
            return new FontUIResource(BODY);
        }

        @Override
        public FontUIResource getSystemTextFont() {
            return new FontUIResource(BODY);
        }

        @Override
        public FontUIResource getUserTextFont() {
            return new FontUIResource(BODY);
        }

        @Override
        public FontUIResource getMenuTextFont() {
            return new FontUIResource(BODY);
        }

        @Override
        public FontUIResource getWindowTitleFont() {
            return new FontUIResource(BODY_BOLD);
        }

        @Override
        public FontUIResource getSubTextFont() {
            return new FontUIResource(SMALL);
        }
    }

    static javax.swing.border.Border empty(int t, int l, int b, int r) {
        return BorderFactory.createEmptyBorder(t, l, b, r);
    }
}
