package info.openrocket.swing.gui.widgets;

import info.openrocket.swing.gui.util.Icons;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;

/**
 * Button specifically for displaying an icon.
 *
 * @author Sibo Van Gool <sibo.vangool@hotmail.com>
 */
public class IconButton extends JButton {
    private static final int ICON_GAP = 10;
    private static final double ICON_SCALE = 0.9;

    // Swing asks for icons during both layout and painting.  In particular,
    // deriving a raster icon can wait for ImageIcon's MediaTracker on the EDT.
    // Keep one derived icon per button state, replacing it when its source changes.
    private ScaledIcon[] scaledIcons;

    private record ScaledIcon(Icon source, int width, int height, Icon scaled) { }

    private Icon scaledIcon(Icon source, int state) {
        if (source == null) {
            if (scaledIcons != null) scaledIcons[state] = null;
            return null;
        }
        // Lazy initialization also handles calls from JButton's constructor.
        if (scaledIcons == null) scaledIcons = new ScaledIcon[6];
        ScaledIcon cached = scaledIcons[state];
        int width = source.getIconWidth();
        int height = source.getIconHeight();
        if (cached == null || cached.source() != source || cached.width() != width || cached.height() != height) {
            cached = new ScaledIcon(source, width, height, Icons.deriveScaledIcon(source, (float) ICON_SCALE));
            scaledIcons[state] = cached;
        }
        return cached.scaled();
    }

    public IconButton() {
        setIconTextGap(ICON_GAP);
    }

    public IconButton(Icon icon) {
        super(icon);
        setIconTextGap(ICON_GAP);
    }

    public IconButton(String text) {
        super(text);
        setIconTextGap(ICON_GAP);
    }

    public IconButton(Action a) {
        super(a);
        setIconTextGap(ICON_GAP);
    }

    public IconButton(String text, Icon icon) {
        super(text, icon);
        setIconTextGap(ICON_GAP);
    }

    @Override
    public void setIcon(Icon defaultIcon) {
        super.setIcon(defaultIcon);
        // There is a bug where the normal override of the pressed icon does not work, so we have to assign it here.
        setPressedIcon(scaledIcon(defaultIcon, 0));
    }

    @Override
    public Icon getIcon() {
        return scaledIcon(super.getIcon(), 0);
    }

    @Override
    public Icon getSelectedIcon() {
        return scaledIcon(super.getSelectedIcon(), 1);
    }

    @Override
    public Icon getDisabledIcon() {
        return scaledIcon(super.getDisabledIcon(), 2);
    }

    @Override
    public Icon getDisabledSelectedIcon() {
        return scaledIcon(super.getDisabledSelectedIcon(), 3);
    }

    @Override
    public Icon getRolloverIcon() {
        return scaledIcon(super.getRolloverIcon(), 4);
    }

    @Override
    public Icon getRolloverSelectedIcon() {
        return scaledIcon(super.getRolloverSelectedIcon(), 5);
    }
}
