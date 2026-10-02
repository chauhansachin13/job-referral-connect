package com.referralconnect.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * A {@link FlowLayout} whose preferred size accounts for wrapping, so a row of buttons that
 * wraps onto a second line actually gets the height for it.
 */
final class WrapLayout extends FlowLayout {

    WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension d = layoutSize(target, false);
        d.width -= getHgap() + 1;
        return d;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            // Use the nearest ancestor that has been laid out to know how wide a row may be.
            Container sized = target;
            while (sized.getWidth() == 0 && sized.getParent() != null) {
                sized = sized.getParent();
            }
            int targetWidth = sized.getWidth() == 0 ? Integer.MAX_VALUE : sized.getWidth();
            Insets insets = target.getInsets();
            int horizontal = insets.left + insets.right + getHgap() * 2;
            int maxRowWidth = targetWidth - horizontal;

            Dimension total = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;
            for (Component c : target.getComponents()) {
                if (!c.isVisible()) {
                    continue;
                }
                Dimension d = preferred ? c.getPreferredSize() : c.getMinimumSize();
                if (rowWidth > 0 && rowWidth + getHgap() + d.width > maxRowWidth) {
                    addRow(total, rowWidth, rowHeight);
                    rowWidth = 0;
                    rowHeight = 0;
                }
                rowWidth += (rowWidth > 0 ? getHgap() : 0) + d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            addRow(total, rowWidth, rowHeight);
            total.width += horizontal;
            total.height += insets.top + insets.bottom + getVgap() * 2;
            return total;
        }
    }

    private void addRow(Dimension total, int rowWidth, int rowHeight) {
        total.width = Math.max(total.width, rowWidth);
        if (total.height > 0) {
            total.height += getVgap();
        }
        total.height += rowHeight;
    }
}
