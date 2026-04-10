package info.openrocket.swing.gui.simulation;

import java.awt.Component;

import javax.swing.JScrollBar;
import javax.swing.JScrollPane;

final class SimulationTabLayoutUtils {
	static final String THREE_COLUMN_LAYOUT = "fillx, insets 6, gap 8 8, wrap 3";
	static final String THREE_COLUMN_COLUMNS = "[grow,fill][grow,fill][grow,fill]";
	static final String SECTION_CELL = "growx, top, aligny top";
	static final String SECTION_SPAN_ALL = "span 3, grow, top";

	private static final int SCROLL_UNIT_INCREMENT = 16;

	private SimulationTabLayoutUtils() {
	}

	static JScrollPane wrapFormScrollable(Component component) {
		JScrollPane scrollPane = createScrollPane(component);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		return scrollPane;
	}

	static JScrollPane wrapDataScrollable(Component component) {
		JScrollPane scrollPane = createScrollPane(component);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		return scrollPane;
	}

	private static JScrollPane createScrollPane(Component component) {
		JScrollPane scrollPane = new JScrollPane(component);
		scrollPane.setBorder(null);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
		scrollPane.getHorizontalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
		configureBlockIncrement(scrollPane.getVerticalScrollBar());
		configureBlockIncrement(scrollPane.getHorizontalScrollBar());
		return scrollPane;
	}

	private static void configureBlockIncrement(JScrollBar scrollBar) {
		scrollBar.setBlockIncrement(SCROLL_UNIT_INCREMENT * 4);
	}
}
