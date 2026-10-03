package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.NotificationService;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * The frame around every signed-in screen: a dark sidebar with the pages and their badges, a top
 * bar with the page title, scan status and notifications, and the current page in the middle.
 */
final class Shell extends JPanel implements AppFrame.Live {

    /**
     * One page of the app.
     *
     * @param badge a count shown next to the page name (0 hides it), or null
     */
    record Page(String id, String title, String subtitle, Icons.Glyph glyph, JComponent view, IntSupplier badge) {
    }

    private final AppServices app;
    private final Account account;
    private final AppFrame.Context ctx;
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, NavButton> nav = new LinkedHashMap<>();
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JLabel title = Ui.label(" ", Theme.H1, Theme.TEXT);
    private final JLabel subtitle = Ui.label(" ", Theme.SMALL, Theme.MUTED);
    private final JLabel scanText = Ui.label(" ", Theme.SMALL, Theme.MUTED);
    private final JProgressBar scanBar = new JProgressBar();
    private static final java.util.regex.Pattern SCAN_PROGRESS =
            java.util.regex.Pattern.compile("Scanned (\\d+)/(\\d+) boards \\((.*)\\)");
    private final Ui.FlatButton scanButton;
    private final BellButton bell = new BellButton();
    private final ScanController.Listener scanListener;
    private Runnable searchAction = () -> { };
    private java.util.function.Consumer<NotificationService.Notification> notificationAction = n -> { };
    private String current;

    Shell(AppServices app, Account account, AppFrame.Context ctx, List<Page> pageList) {
        super(new BorderLayout());
        this.app = app;
        this.account = account;
        this.ctx = ctx;
        setBackground(Theme.BG);
        for (Page p : pageList) {
            pages.put(p.id(), p);
            JPanel holder = new JPanel(new BorderLayout());
            holder.setOpaque(false);
            holder.add(p.view(), BorderLayout.CENTER);
            content.add(holder, p.id());
        }
        content.setOpaque(false);

        add(sidebar(), BorderLayout.WEST);
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Theme.BG);
        scanButton = Ui.iconButton(Icons.Glyph.REFRESH, "Scan careers sites now (" + shortcut("R") + ")",
                Ui.Kind.SECONDARY, () -> ctx.scans().start(this));
        scanButton.setName("scanNow");
        main.add(topBar(), BorderLayout.NORTH);
        main.add(content, BorderLayout.CENTER);
        add(main, BorderLayout.CENTER);

        scanListener = (running, line) -> {
            scanBar.setVisible(running);
            scanButton.setEnabled(!running);
            // "Scanned 87/159 boards (Cisco)" becomes a filling bar and "Scanning 87 of 159 companies".
            java.util.regex.Matcher m = SCAN_PROGRESS.matcher(line == null ? "" : line);
            if (running && m.matches()) {
                int done = Integer.parseInt(m.group(1));
                int total = Integer.parseInt(m.group(2));
                scanBar.setIndeterminate(false);
                scanBar.setMaximum(total);
                scanBar.setValue(done);
                scanText.setText("Scanning " + done + " of " + total + " companies");
                scanText.setToolTipText("Just finished: " + m.group(3) + ". Openings appear as each company is read.");
            } else {
                scanBar.setIndeterminate(running);
                scanText.setText(running ? line : lastScanText());
                scanText.setToolTipText(null);
            }
        };
        ctx.scans().addListener(scanListener);
        installShortcuts(pageList);
        show(pageList.get(0).id());
    }

    /** Stops listening to the shared scan controller (called when this screen is replaced). */
    void dispose() {
        ctx.scans().removeListener(scanListener);
    }

    void setSearchAction(Runnable r) {
        this.searchAction = r;
    }

    void setNotificationAction(java.util.function.Consumer<NotificationService.Notification> r) {
        this.notificationAction = r;
    }

    String current() {
        return current;
    }

    void show(String id) {
        Page p = pages.get(id);
        if (p == null) {
            return;
        }
        current = id;
        cards.show(content, id);
        title.setText(p.title());
        subtitle.setText(p.subtitle());
        nav.forEach((k, b) -> b.setActive(k.equals(id)));
        if (p.view() instanceof AppFrame.Live live) {
            live.refreshData();
        }
        updateBadges();
    }

    @Override
    public void refreshData() {
        Page p = pages.get(current);
        if (p != null && p.view() instanceof AppFrame.Live live) {
            live.refreshData();
        }
        updateBadges();
        if (!ctx.scans().running()) {
            scanText.setText(lastScanText());
        }
    }

    void updateBadges() {
        for (Page p : pages.values()) {
            NavButton b = nav.get(p.id());
            if (b != null) {
                b.setBadge(p.badge() == null ? 0 : p.badge().getAsInt());
            }
        }
        bell.setCount((int) app.notifications.unreadCount(app.auth.require(account.id())));
    }

    private String lastScanText() {
        Instant last = app.jobs.lastScanAt();
        if (last == null) {
            return "Not scanned yet";
        }
        int failures = app.jobs.lastScanFailures().size();
        return "Updated " + Ui.ago(last) + " · " + app.jobs.lastScanBoards() + " companies"
                + (failures > 0 ? " · " + failures + " unreachable" : "");
    }

    // ---------------------------------------------------------------- sidebar

    private JComponent sidebar() {
        JPanel side = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.SIDEBAR);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        side.setPreferredSize(new Dimension(236, 600));
        side.setBorder(Ui.padding(20, 14, 16, 14));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(brand());
        top.add(Box.createVerticalStrut(26));
        JLabel menu = Ui.label("MENU", Theme.TINY_BOLD, Theme.SIDEBAR_MUTED);
        menu.setBorder(Ui.padding(0, 10, 8, 0));
        menu.setAlignmentX(LEFT_ALIGNMENT);
        top.add(menu);
        int i = 1;
        for (Page p : pages.values()) {
            NavButton b = new NavButton(p, i <= 9 ? shortcut(String.valueOf(i)) : "");
            b.setAlignmentX(LEFT_ALIGNMENT);
            nav.put(p.id(), b);
            top.add(b);
            top.add(Box.createVerticalStrut(3));
            i++;
        }
        side.add(top, BorderLayout.NORTH);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        Widgets.Toggle darkToggle = new Widgets.Toggle(Theme.isDark());
        darkToggle.setName("darkMode");
        darkToggle.setToolTipText("Dark mode (" + shortcut("D") + ")");
        darkToggle.addActionListener(e -> ctx.toggleTheme().run());
        JPanel themeRow = new JPanel(new BorderLayout(10, 0));
        themeRow.setOpaque(false);
        themeRow.setBorder(Ui.padding(8, 10, 8, 6));
        JLabel themeLabel = Ui.label("Dark mode", Theme.BODY, Theme.SIDEBAR_TEXT);
        themeLabel.setIcon(Icons.get(Theme.isDark() ? Icons.Glyph.MOON : Icons.Glyph.SUN, 17, Theme.SIDEBAR_MUTED));
        themeLabel.setIconTextGap(10);
        themeRow.add(themeLabel, BorderLayout.CENTER);
        themeRow.add(darkToggle, BorderLayout.EAST);
        themeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        themeRow.setAlignmentX(LEFT_ALIGNMENT);
        bottom.add(themeRow);
        bottom.add(Box.createVerticalStrut(10));

        JPanel user = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Laf.smooth(g);
                g2.setColor(new Color(255, 255, 255, 14));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.dispose();
            }
        };
        user.setOpaque(false);
        user.setBorder(Ui.padding(10, 10, 10, 6));
        user.add(new JLabel(new Ui.Avatar(account.name(), 34, true)), BorderLayout.WEST);
        JPanel who = new JPanel();
        who.setOpaque(false);
        who.setLayout(new BoxLayout(who, BoxLayout.Y_AXIS));
        JLabel name = Ui.label(account.name(), Theme.BODY_BOLD, Theme.SIDEBAR_TEXT);
        String role = account.isReferrer() ? "Referrer · " + account.companyName() : "Job seeker";
        JLabel roleLabel = Ui.label(role, Theme.SMALL, Theme.SIDEBAR_MUTED);
        who.add(name);
        who.add(roleLabel);
        user.add(who, BorderLayout.CENTER);
        Ui.FlatButton signOut = Ui.iconButton(Icons.Glyph.LOGOUT, "Sign out", Ui.Kind.GHOST, ctx.signOut());
        signOut.setName("signOut");
        signOut.compact();
        JPanel signOutHolder = new JPanel(new BorderLayout());
        signOutHolder.setOpaque(false);
        signOutHolder.add(signOut, BorderLayout.CENTER);
        user.add(signOutHolder, BorderLayout.EAST);
        user.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        user.setAlignmentX(LEFT_ALIGNMENT);
        bottom.add(user);
        side.add(bottom, BorderLayout.SOUTH);
        return side;
    }

    private JComponent brand() {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        JLabel logo = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Laf.smooth(g);
                g2.setPaint(new GradientPaint(0, 0, new Color(0x818CF8), getWidth(), getHeight(), new Color(0x4F46E5)));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                Icons.get(Icons.Glyph.ZAP, 20, Color.WHITE).paintIcon(this, g2, (getWidth() - 20) / 2,
                        (getHeight() - 20) / 2);
                g2.dispose();
            }
        };
        logo.setPreferredSize(new Dimension(38, 38));
        p.add(logo, BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(Ui.label("Referral Connect", Theme.font(java.awt.Font.BOLD, 16), Color.WHITE));
        text.add(Ui.label("India tech jobs · referrals", Theme.SMALL, Theme.SIDEBAR_MUTED));
        p.add(text, BorderLayout.CENTER);
        p.setBorder(Ui.padding(0, 6, 0, 0));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    /** A sidebar entry: icon, label and an optional count. */
    final class NavButton extends JComponent {
        private final Page page;
        private boolean active;
        private boolean hover;
        private int badge;

        NavButton(Page page, String shortcut) {
            this.page = page;
            setName("nav-" + page.id());
            setToolTipText(page.title() + (shortcut.isEmpty() ? "" : "  (" + shortcut + ")"));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(false);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Shell.this.show(page.id());
                }

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

        void setActive(boolean a) {
            active = a;
            repaint();
        }

        void setBadge(int n) {
            if (n != badge) {
                badge = n;
                repaint();
            }
        }

        int badge() {
            return badge;
        }

        /** Navigates as a click would (for tests and shortcuts). */
        void click() {
            Shell.this.show(page.id());
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(208, 40);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, 40);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Laf.smooth(g);
            int w = getWidth();
            int h = getHeight();
            if (active) {
                g2.setColor(Theme.alpha(Theme.SIDEBAR_ACTIVE, 70));
                g2.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
                g2.setColor(new Color(0xA5B4FC));
                g2.fillRoundRect(0, 10, 3, h - 20, 3, 3);
            } else if (hover) {
                g2.setColor(new Color(255, 255, 255, 16));
                g2.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
            }
            Color fg = active ? Color.WHITE : Theme.SIDEBAR_TEXT;
            Icons.get(page.glyph(), 18, active ? Color.WHITE : Theme.SIDEBAR_MUTED).paintIcon(this, g2, 12, (h - 18) / 2);
            g2.setFont(active ? Theme.BODY_BOLD : Theme.BODY);
            g2.setColor(fg);
            FontMetrics fm = g2.getFontMetrics();
            String label = navLabel();
            g2.drawString(label, 40, (h - fm.getHeight()) / 2 + fm.getAscent());
            if (badge > 0) {
                String b = badge > 999 ? "999+" : String.valueOf(badge);
                g2.setFont(Theme.TINY_BOLD);
                FontMetrics bf = g2.getFontMetrics();
                int bw = Math.max(20, bf.stringWidth(b) + 12);
                int bx = w - bw - 8;
                int by = (h - 18) / 2;
                g2.setColor(active ? new Color(255, 255, 255, 50) : Theme.alpha(Theme.SIDEBAR_ACTIVE, 200));
                g2.fillRoundRect(bx, by, bw, 18, 18, 18);
                g2.setColor(Color.WHITE);
                g2.drawString(b, bx + (bw - bf.stringWidth(b)) / 2, by + (18 - bf.getHeight()) / 2 + bf.getAscent());
            }
            g2.dispose();
        }

        private String navLabel() {
            String t = page.title();
            return t.length() > 22 ? t.substring(0, 21) + "…" : t;
        }
    }

    NavButton navButton(String id) {
        return nav.get(id);
    }

    // ---------------------------------------------------------------- top bar

    private JComponent topBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.BG);
        bar.setBorder(Ui.padding(18, 26, 10, 22));
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        title.setName("pageTitle");
        left.add(title);
        left.add(Box.createVerticalStrut(2));
        left.add(subtitle);
        bar.add(left, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        right.setOpaque(false);
        scanBar.setIndeterminate(true);
        scanBar.setPreferredSize(new Dimension(120, 8));
        scanBar.setVisible(false);
        scanText.setName("scanStatus");
        scanText.setIcon(Icons.live(Icons.Glyph.CLOCK, 14, () -> Theme.MUTED));
        scanText.setIconTextGap(6);
        right.add(scanText);
        right.add(scanBar);
        right.add(scanButton);
        bell.setName("bell");
        bell.addActionListener(e -> openNotifications());
        right.add(bell);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void openNotifications() {
        Account fresh = app.auth.require(account.id());
        NotificationsPopup.show(bell, app.notifications.list(fresh), n -> notificationAction.accept(n), () -> {
            app.prefs.markNotificationsRead(account.id());
            updateBadges();
        });
    }

    /** The bell with a red count of unread notifications. */
    static final class BellButton extends Ui.FlatButton {
        private int count;

        BellButton() {
            super("", Ui.Kind.SECONDARY);
            glyph(Icons.Glyph.BELL);
            setToolTipText("Notifications");
            getAccessibleContext().setAccessibleName("Notifications");
        }

        void setCount(int n) {
            if (n != count) {
                count = n;
                repaint();
            }
        }

        int count() {
            return count;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (count <= 0) {
                return;
            }
            Graphics2D g2 = Laf.smooth(g);
            String t = count > 99 ? "99+" : String.valueOf(count);
            g2.setFont(Theme.TINY_BOLD);
            FontMetrics fm = g2.getFontMetrics();
            int w = Math.max(17, fm.stringWidth(t) + 8);
            int x = getWidth() - w + 4;
            g2.setColor(Theme.BG);
            g2.fillRoundRect(x - 2, -2, w + 4, 21, 21, 21);
            g2.setColor(new Color(0xEF4444));
            g2.fillRoundRect(x, 0, w, 17, 17, 17);
            g2.setColor(Color.WHITE);
            g2.drawString(t, x + (w - fm.stringWidth(t)) / 2, (17 - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(40, 36);
        }
    }

    BellButton bell() {
        return bell;
    }

    // ---------------------------------------------------------------- keyboard

    static String shortcut(String key) {
        boolean mac = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
        return (mac ? "⌘" : "Ctrl+") + key;
    }

    private void installShortcuts(List<Page> pageList) {
        int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        for (int i = 0; i < Math.min(9, pageList.size()); i++) {
            String id = pageList.get(i).id();
            bind(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, menu), "page-" + id, () -> show(id));
        }
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_R, menu), "scan", () -> ctx.scans().start(this));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_F, menu), "search", () -> searchAction.run());
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_D, menu), "theme", () -> ctx.toggleTheme().run());
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_N, menu | InputEvent.SHIFT_DOWN_MASK), "notifications",
                this::openNotifications);
    }

    private void bind(KeyStroke key, String name, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(key, name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    static javax.swing.border.Border line() {
        return BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER);
    }
}
