package com.referralconnect.ui;

import com.referralconnect.service.NotificationService.Notification;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

/** The list under the bell: newest first, unread ones marked; click one to go to it. */
final class NotificationsPopup {

    private NotificationsPopup() {
    }

    static void show(JComponent anchor, List<Notification> items, Consumer<Notification> onOpen, Runnable onRead) {
        JPopupMenu popup = new JPopupMenu();
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.SURFACE);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(Ui.padding(10, 14, 8, 10));
        head.add(Ui.label("Notifications", Theme.H3, Theme.TEXT), BorderLayout.WEST);
        head.add(Ui.button("Mark all read", Ui.Kind.SUBTLE, () -> {
            onRead.run();
            popup.setVisible(false);
        }).compact(), BorderLayout.EAST);
        panel.add(head, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        if (items.isEmpty()) {
            list.add(Ui.label("You're all caught up.", Theme.BODY, Theme.MUTED));
        }
        for (Notification n : items.subList(0, Math.min(40, items.size()))) {
            JPanel row = Ui.flexRow(new BorderLayout(10, 2));
            row.setBorder(Ui.padding(8, 14, 8, 14));
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            row.add(Ui.label(n.unread() ? "●" : " ", Theme.SMALL_BOLD, Theme.PRIMARY), BorderLayout.WEST);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(Ui.text(n.title(), n.unread() ? Theme.BODY_BOLD : Theme.BODY, Theme.TEXT));
            text.add(Ui.text(n.body(), Theme.SMALL, Theme.MUTED).maxLines(2));
            text.add(Ui.label(Ui.ago(n.at()), Theme.SMALL, Theme.FAINT));
            row.add(text, BorderLayout.CENTER);
            row.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    popup.setVisible(false);
                    onOpen.accept(n);
                }
            });
            list.add(row);
        }
        list.add(Box.createVerticalGlue());
        JScrollPane sp = Ui.bareScroll(list);
        sp.setPreferredSize(new Dimension(380, Math.min(420, Math.max(80, items.size() * 78))));
        panel.add(sp, BorderLayout.CENTER);
        popup.add(panel);
        popup.show(anchor, anchor.getWidth() - 380, anchor.getHeight() + 6);
    }
}
