package info.openrocket.swing.gui.simulation;

import java.awt.Component;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.UIManager;

final class SimulationTabLayoutUtils {
	static final String THREE_COLUMN_LAYOUT = "fillx, insets 6, gap 8 8, wrap 3";
	static final String THREE_COLUMN_COLUMNS = "[grow,fill][grow,fill][grow,fill]";
	static final String SECTION_CELL = "growx, top, aligny top";
	static final String SECTION_SPAN_ALL = "span 3, grow, top";

	private static final int SCROLL_UNIT_INCREMENT = 16;
	private static final int COMPACT_VALUE_LIMIT = 56;
	private static final double MAX_SCREEN_FRACTION = 0.90;

	private SimulationTabLayoutUtils() {
	}

	static JScrollPane wrapFormScrollable(Component component) {
		JScrollPane scrollPane = createScrollPane(component);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setViewportBorder(null);
		forceViewportWidth(component);
		return scrollPane;
	}

	static JScrollPane wrapDataScrollable(Component component) {
		JScrollPane scrollPane = createScrollPane(component);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		return scrollPane;
	}

	private static JScrollPane createScrollPane(Component component) {
		JScrollPane scrollPane = new JScrollPane(component);
		configureScrollPane(scrollPane);
		return scrollPane;
	}

	private static void configureScrollPane(JScrollPane scrollPane) {
		scrollPane.setBorder(null);
		scrollPane.setViewportBorder(null);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
		scrollPane.getHorizontalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
		configureBlockIncrement(scrollPane.getVerticalScrollBar());
		configureBlockIncrement(scrollPane.getHorizontalScrollBar());
	}

	private static void configureBlockIncrement(JScrollBar scrollBar) {
		scrollBar.setBlockIncrement(SCROLL_UNIT_INCREMENT * 4);
	}

	static JTextArea createBoundedWrappingText(String text, Color foreground) {
		JTextArea area = createWrappingDisplayText(text);
		if (foreground != null) {
			area.setForeground(foreground);
		}
		return area;
	}

	/**
	 * Scroll pane that never advertises a width of its own, so the enclosing tile
	 * stays as narrow as the tab allows, while still tracking the live height of
	 * its view as rows are added and removed.
	 */
	static JScrollPane createContainedScrollPane(Component component) {
		JScrollPane scrollPane = new JScrollPane(component) {
			private static final long serialVersionUID = 1L;

			@Override
			public Dimension getPreferredSize() {
				Insets insets = getInsets();
				Dimension view = component.getPreferredSize();
				return new Dimension(0, view.height + insets.top + insets.bottom);
			}
		};
		configureScrollPane(scrollPane);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setOpaque(false);
		scrollPane.getViewport().setOpaque(false);
		forceViewportWidth(scrollPane);
		forceViewportWidth(component);
		return scrollPane;
	}

	static void forceViewportWidth(Component component) {
		if (!(component instanceof JComponent jComponent)) {
			return;
		}
		Dimension minimum = jComponent.getMinimumSize();
		int minHeight = minimum != null ? minimum.height : 0;
		jComponent.setMinimumSize(new Dimension(0, minHeight));

		Dimension maximum = jComponent.getMaximumSize();
		int maxHeight = maximum != null && maximum.height > 0 ? maximum.height : Integer.MAX_VALUE;
		jComponent.setMaximumSize(new Dimension(Integer.MAX_VALUE, maxHeight));
	}

	static JTextArea createWrappingDisplayText(String text) {
		JTextArea area = new WrappingDisplayText();
		area.setEditable(false);
		area.setOpaque(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setFocusable(false);
		area.setFont(UIManager.getFont("Label.font"));
		area.setBorder(BorderFactory.createEmptyBorder());
		area.setColumns(1);
		setWrappingDisplayText(area, text);
		return area;
	}

	static void setWrappingDisplayText(JTextArea area, String text) {
		String display = plainText(text);
		area.setText(display);
		area.setToolTipText(display);
		area.setCaretPosition(0);
		if (area instanceof WrappingDisplayText wrapping) {
			wrapping.updateRowEstimate();
		}
	}

	/** Several translated strings are authored as HTML fragments for JLabel. */
	private static String plainText(String text) {
		if (text == null || text.isBlank()) {
			return "-";
		}
		String stripped = text.replaceAll("<\\s*br\\s*/?\\s*>", " ")
				.replaceAll("<[^>]*>", "")
				.replace("&nbsp;", " ")
				.replace("&amp;", "&")
				.replaceAll("\\s{2,}", " ")
				.trim();
		return stripped.isEmpty() ? "-" : stripped;
	}

	/**
	 * A wrapped text area normally reports the height of one unwrapped line, so a
	 * multi-line description gets clipped. Deriving the height from a word-wrap
	 * estimate keeps every line visible without making the preferred size depend
	 * on the width the layout manager is still in the middle of deciding, which
	 * is what makes a self-measuring text area explode to hundreds of pixels.
	 */
	private static final class WrappingDisplayText extends JTextArea {
		private static final long serialVersionUID = 1L;
		private static final int ASSUMED_WIDTH = 330;

		private WrappingDisplayText() {
			addComponentListener(new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent event) {
					if (updateRowEstimate()) {
						Container parent = getParent();
						if (parent != null) {
							parent.revalidate();
						}
					}
				}
			});
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(0, wrappedHeight());
		}

		/**
		 * Left to itself a wrapping text area reports a minimum height tall enough
		 * to stack the text one word per line, and a layout that honours it hands
		 * the description hundreds of unused pixels.
		 */
		@Override
		public Dimension getMinimumSize() {
			return new Dimension(0, wrappedHeight());
		}

		@Override
		public Dimension getMaximumSize() {
			return new Dimension(Integer.MAX_VALUE, wrappedHeight());
		}

		private int wrappedHeight() {
			Insets insets = getInsets();
			return Math.max(1, getRows()) * getRowHeight() + insets.top + insets.bottom;
		}

		private boolean updateRowEstimate() {
			int wanted = estimateRows();
			if (wanted == getRows()) {
				return false;
			}
			setRows(wanted);
			return true;
		}

		private int estimateRows() {
			Insets insets = getInsets();
			int available = getWidth() - insets.left - insets.right;
			if (available <= 0) {
				available = ASSUMED_WIDTH;
			}
			FontMetrics metrics = getFontMetrics(getFont());
			int spaceWidth = Math.max(1, metrics.charWidth(' '));
			int rows = 1;
			int lineWidth = 0;
			for (String word : getText().trim().split("\\s+")) {
				int wordWidth = metrics.stringWidth(word);
				int candidate = lineWidth == 0 ? wordWidth : lineWidth + spaceWidth + wordWidth;
				if (lineWidth == 0 || candidate <= available) {
					lineWidth = candidate;
				} else {
					rows++;
					lineWidth = wordWidth;
				}
			}
			return rows;
		}
	}

	static JLabel createCompactValueLabel(String text) {
		JLabel label = new JLabel();
		setCompactValueLabel(label, text);
		return label;
	}

	static void setCompactValueLabel(JLabel label, String text) {
		String display = text == null || text.isBlank() ? "-" : text;
		label.setToolTipText(display);
		label.setText(compact(display));
	}

	static void constrainDialogToScreen(Window window, Dimension desiredMinimum) {
		Rectangle usableBounds = getUsableScreenBounds(window);
		int maxWidth = Math.max(640, (int) Math.floor(usableBounds.width * MAX_SCREEN_FRACTION));
		int maxHeight = Math.max(480, (int) Math.floor(usableBounds.height * MAX_SCREEN_FRACTION));

		Dimension preferred = window.getSize();
		if (preferred.width <= 0 || preferred.height <= 0) {
			preferred = window.getPreferredSize();
		}

		int width = Math.min(Math.max(preferred.width, Math.min(desiredMinimum.width, maxWidth)), maxWidth);
		int height = Math.min(Math.max(preferred.height, Math.min(desiredMinimum.height, maxHeight)), maxHeight);

		window.setMinimumSize(new Dimension(Math.min(desiredMinimum.width, maxWidth),
				Math.min(desiredMinimum.height, maxHeight)));
		window.setSize(width, height);
		keepWindowOnScreen(window, usableBounds);
	}

	private static String compact(String value) {
		if (value.length() <= COMPACT_VALUE_LIMIT) {
			return value;
		}
		int head = Math.max(12, COMPACT_VALUE_LIMIT / 2 - 3);
		int tail = Math.max(12, COMPACT_VALUE_LIMIT - head - 3);
		return value.substring(0, head) + "..." + value.substring(value.length() - tail);
	}

	private static Rectangle getUsableScreenBounds(Window window) {
		GraphicsConfiguration config = window.getGraphicsConfiguration();
		if (config == null) {
			config = GraphicsEnvironment.getLocalGraphicsEnvironment()
					.getDefaultScreenDevice()
					.getDefaultConfiguration();
		}
		Rectangle bounds = new Rectangle(config.getBounds());
		Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
		bounds.x += insets.left;
		bounds.y += insets.top;
		bounds.width -= insets.left + insets.right;
		bounds.height -= insets.top + insets.bottom;
		return bounds;
	}

	private static void keepWindowOnScreen(Window window, Rectangle bounds) {
		int x = window.getX();
		int y = window.getY();
		if (x < bounds.x || x + window.getWidth() > bounds.x + bounds.width) {
			x = bounds.x + Math.max(0, (bounds.width - window.getWidth()) / 2);
		}
		if (y < bounds.y || y + window.getHeight() > bounds.y + bounds.height) {
			y = bounds.y + Math.max(0, (bounds.height - window.getHeight()) / 2);
		}
		window.setLocation(x, y);
	}
}
