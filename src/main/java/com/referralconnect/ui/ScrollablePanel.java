package com.referralconnect.ui;

import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;

/**
 * A vertical-scrolling panel that always matches the scroll pane's width, so wrapped text
 * inside it re-flows when the window is resized instead of forcing a horizontal scrollbar.
 */
class ScrollablePanel extends JPanel implements Scrollable {

    ScrollablePanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(16, visible.height - 32);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport v && v.getHeight() > getPreferredSize().height;
    }
}
