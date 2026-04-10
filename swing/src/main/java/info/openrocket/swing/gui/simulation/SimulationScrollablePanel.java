package info.openrocket.swing.gui.simulation;

import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

class SimulationScrollablePanel extends JPanel implements Scrollable {
	private static final int SCROLL_INCREMENT = 16;

	SimulationScrollablePanel() {
		super();
	}

	SimulationScrollablePanel(LayoutManager layout) {
		super(layout);
	}

	@Override
	public Dimension getPreferredScrollableViewportSize() {
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
		return SCROLL_INCREMENT;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
		if (orientation == SwingConstants.VERTICAL) {
			return Math.max(SCROLL_INCREMENT * 4, visibleRect.height * 8 / 10);
		}
		return Math.max(SCROLL_INCREMENT * 4, visibleRect.width * 8 / 10);
	}

	@Override
	public boolean getScrollableTracksViewportWidth() {
		return true;
	}

	@Override
	public boolean getScrollableTracksViewportHeight() {
		return false;
	}
}
