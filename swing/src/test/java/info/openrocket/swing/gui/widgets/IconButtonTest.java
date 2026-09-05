package info.openrocket.swing.gui.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import info.openrocket.swing.util.BaseTestCase;

class IconButtonTest extends BaseTestCase {
    @Test
    void layoutAndPaintReuseScaledRasterIconsForEveryState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ImageIcon source = image(20);
            IconButton button = new IconButton("Monte Carlo", source);
            button.setSelectedIcon(source);
            button.setDisabledIcon(source);
            button.setDisabledSelectedIcon(source);
            button.setRolloverIcon(source);
            button.setRolloverSelectedIcon(source);
            List<Supplier<Icon>> getters = List.of(button::getIcon, button::getSelectedIcon,
                    button::getDisabledIcon, button::getDisabledSelectedIcon,
                    button::getRolloverIcon, button::getRolloverSelectedIcon);
            for (Supplier<Icon> getter : getters) {
                Icon scaled = getter.get();
                assertEquals(18, scaled.getIconWidth());
                for (int i = 0; i < 100; i++) {
                    button.getPreferredSize();
                    assertSame(scaled, getter.get(), "Layout must not recreate and load raster images");
                }
            }
            assertSame(button.getIcon(), button.getPressedIcon());
        });
    }

    @Test
    void changingOrClearingTheIconInvalidatesItsCachedSize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            IconButton button = new IconButton(image(20));
            Icon old = button.getIcon();
            button.setIcon(image(40));
            assertNotSame(old, button.getIcon());
            assertEquals(36, button.getIcon().getIconWidth());
            assertSame(button.getIcon(), button.getPressedIcon());
            button.setIcon(null);
            assertNull(button.getIcon());
            assertNull(button.getPressedIcon());
        });
    }

    private static ImageIcon image(int size) {
        return new ImageIcon(new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB));
    }
}
