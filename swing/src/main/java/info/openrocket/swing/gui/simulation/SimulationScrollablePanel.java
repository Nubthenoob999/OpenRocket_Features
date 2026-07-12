package info.openrocket.swing.gui.simulation;

import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

class SimulationScrollablePanel extends JPanel implements Scrollable {
	private static final int SCROLL_INCREMENT = 16;
	private final boolean tracksViewportWidth;

	SimulationScrollablePanel() {
		super();
		this.tracksViewportWidth = true;
	}

	SimulationScrollablePanel(LayoutManager layout) {
		super(layout);
		this.tracksViewportWidth = true;
	}

	SimulationScrollablePanel(LayoutManager layout, boolean tracksViewportWidth) {
		super(layout);
		this.tracksViewportWidth = tracksViewportWidth;
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
		return tracksViewportWidth;
	}

	@Override
	public boolean getScrollableTracksViewportHeight() {
		return false;
	}
}
