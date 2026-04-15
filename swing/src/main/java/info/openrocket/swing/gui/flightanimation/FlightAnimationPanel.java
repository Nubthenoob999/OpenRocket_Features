package info.openrocket.swing.gui.dialogs.flightanimation;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.List;

import javax.swing.JPanel;

import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.FlightEvent;

/**
 * A custom JPanel that renders a 2D side-view visualization of a rocket flight trajectory.
 * <p>
 * The panel draws the trajectory as a velocity-colored line, marks key flight events,
 * and shows the current rocket position as an oriented triangle.
 *
 * @author Alex Zoghlin
 */
public class FlightAnimationPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private static final int MARGIN = 60;
	private static final int GRID_LINES = 8;
	private static final Color GROUND_COLOR = new Color(139, 119, 101);
	private static final Color SKY_TOP_COLOR = new Color(135, 206, 235);
	private static final Color SKY_BOTTOM_COLOR = new Color(200, 230, 255);
	private static final Color GRID_COLOR = new Color(200, 200, 200, 100);
	private static final Color EVENT_MARKER_COLOR = new Color(60, 60, 60);
	private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
	private static final Font EVENT_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 10);

	private List<Double> timeData;
	private List<Double> altitudeData;
	private List<Double> posXData;
	private List<Double> velocityData;
	private List<FlightEvent> events;

	private int currentIndex = 0;
	private double maxAltitude = 1.0;
	private double maxPosX = 1.0;
	private double minPosX = 0.0;
	private double maxVelocity = 1.0;

	public FlightAnimationPanel() {
		setPreferredSize(new Dimension(800, 500));
		setBackground(Color.WHITE);
	}

	/**
	 * Loads flight data from a FlightDataBranch for rendering.
	 *
	 * @param branch the flight data branch containing simulation results
	 */
	public void loadData(FlightDataBranch branch) {
		this.timeData = branch.get(FlightDataType.TYPE_TIME);
		this.altitudeData = branch.get(FlightDataType.TYPE_ALTITUDE);
		this.posXData = branch.get(FlightDataType.TYPE_POSITION_X);
		this.velocityData = branch.get(FlightDataType.TYPE_VELOCITY_TOTAL);
		this.events = branch.getEvents();

		// Compute ranges for scaling
		maxAltitude = 1.0;
		maxPosX = 0.0;
		minPosX = 0.0;
		maxVelocity = 1.0;

		if (altitudeData != null) {
			for (Double v : altitudeData) {
				if (v != null && v > maxAltitude) maxAltitude = v;
			}
		}
		if (posXData != null) {
			for (Double v : posXData) {
				if (v != null) {
					if (v > maxPosX) maxPosX = v;
					if (v < minPosX) minPosX = v;
				}
			}
		}
		if (velocityData != null) {
			for (Double v : velocityData) {
				if (v != null && v > maxVelocity) maxVelocity = v;
			}
		}

		// Add some padding to ranges
		maxAltitude *= 1.1;
		double range = maxPosX - minPosX;
		if (range < maxAltitude * 0.5) {
			// Ensure horizontal range is at least half of altitude range
			double center = (maxPosX + minPosX) / 2.0;
			minPosX = center - maxAltitude * 0.3;
			maxPosX = center + maxAltitude * 0.3;
		} else {
			minPosX -= range * 0.1;
			maxPosX += range * 0.1;
		}

		currentIndex = 0;
		repaint();
	}

	/**
	 * Sets the current time index for the animation.
	 *
	 * @param index the data index to display
	 */
	public void setCurrentIndex(int index) {
		this.currentIndex = index;
		repaint();
	}

	/**
	 * Returns the total number of data points.
	 *
	 * @return number of data points, or 0 if no data is loaded
	 */
	public int getDataSize() {
		return timeData != null ? timeData.size() : 0;
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		int w = getWidth();
		int h = getHeight();

		drawBackground(g2, w, h);

		if (timeData == null || altitudeData == null || posXData == null) {
			g2.setColor(Color.GRAY);
			g2.setFont(LABEL_FONT.deriveFont(Font.BOLD, 14f));
			g2.drawString("No flight data available", w / 2 - 80, h / 2);
			g2.dispose();
			return;
		}

		drawGrid(g2, w, h);
		drawTrajectory(g2, w, h);
		drawEventMarkers(g2, w, h);
		drawRocketMarker(g2, w, h);
		drawAxisLabels(g2, w, h);

		g2.dispose();
	}

	private void drawBackground(Graphics2D g2, int w, int h) {
		// Sky gradient
		for (int y = 0; y < h - MARGIN; y++) {
			float ratio = (float) y / (h - MARGIN);
			int r = (int) (SKY_TOP_COLOR.getRed() * ratio + SKY_BOTTOM_COLOR.getRed() * (1 - ratio));
			int gv = (int) (SKY_TOP_COLOR.getGreen() * ratio + SKY_BOTTOM_COLOR.getGreen() * (1 - ratio));
			int b = (int) (SKY_TOP_COLOR.getBlue() * ratio + SKY_BOTTOM_COLOR.getBlue() * (1 - ratio));
			g2.setColor(new Color(
					Math.max(0, Math.min(255, r)),
					Math.max(0, Math.min(255, gv)),
					Math.max(0, Math.min(255, b))));
			g2.drawLine(0, y, w, y);
		}
		// Ground
		g2.setColor(GROUND_COLOR);
		g2.fillRect(0, h - MARGIN, w, MARGIN);
	}

	private void drawGrid(Graphics2D g2, int w, int h) {
		g2.setColor(GRID_COLOR);
		g2.setStroke(new BasicStroke(0.5f));
		g2.setFont(LABEL_FONT);
		FontMetrics fm = g2.getFontMetrics();

		int plotW = w - 2 * MARGIN;
		int plotH = h - 2 * MARGIN;

		// Horizontal grid lines (altitude)
		for (int i = 0; i <= GRID_LINES; i++) {
			int y = h - MARGIN - (int) ((double) i / GRID_LINES * plotH);
			g2.setColor(GRID_COLOR);
			g2.drawLine(MARGIN, y, w - MARGIN, y);

			double alt = maxAltitude * i / GRID_LINES;
			String label = formatValue(alt) + " m";
			g2.setColor(Color.DARK_GRAY);
			g2.drawString(label, MARGIN - fm.stringWidth(label) - 4, y + fm.getAscent() / 2);
		}

		// Vertical grid lines (horizontal distance)
		for (int i = 0; i <= GRID_LINES; i++) {
			int x = MARGIN + (int) ((double) i / GRID_LINES * plotW);
			g2.setColor(GRID_COLOR);
			g2.drawLine(x, MARGIN, x, h - MARGIN);

			double dist = minPosX + (maxPosX - minPosX) * i / GRID_LINES;
			String label = formatValue(dist) + " m";
			g2.setColor(Color.DARK_GRAY);
			g2.drawString(label, x - fm.stringWidth(label) / 2, h - MARGIN + fm.getHeight() + 2);
		}
	}

	private void drawTrajectory(Graphics2D g2, int w, int h) {
		int plotW = w - 2 * MARGIN;
		int plotH = h - 2 * MARGIN;
		int size = Math.min(currentIndex + 1, timeData.size());

		g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		for (int i = 1; i < size; i++) {
			Double x0 = posXData.get(i - 1);
			Double y0 = altitudeData.get(i - 1);
			Double x1 = posXData.get(i);
			Double y1 = altitudeData.get(i);
			Double v = velocityData.get(i);

			if (x0 == null || y0 == null || x1 == null || y1 == null || v == null) continue;

			// Map velocity to color (blue = slow, green = medium, red = fast)
			g2.setColor(velocityToColor(v));

			int px0 = MARGIN + (int) ((x0 - minPosX) / (maxPosX - minPosX) * plotW);
			int py0 = h - MARGIN - (int) (y0 / maxAltitude * plotH);
			int px1 = MARGIN + (int) ((x1 - minPosX) / (maxPosX - minPosX) * plotW);
			int py1 = h - MARGIN - (int) (y1 / maxAltitude * plotH);

			g2.drawLine(px0, py0, px1, py1);
		}
	}

	private void drawEventMarkers(Graphics2D g2, int w, int h) {
		if (events == null) return;

		int plotW = w - 2 * MARGIN;
		int plotH = h - 2 * MARGIN;
		g2.setFont(EVENT_FONT);
		FontMetrics fm = g2.getFontMetrics();

		for (FlightEvent event : events) {
			FlightEvent.Type type = event.getType();

			// Only draw certain interesting event types
			if (type != FlightEvent.Type.LAUNCH &&
					type != FlightEvent.Type.BURNOUT &&
					type != FlightEvent.Type.APOGEE &&
					type != FlightEvent.Type.RECOVERY_DEVICE_DEPLOYMENT &&
					type != FlightEvent.Type.GROUND_HIT &&
					type != FlightEvent.Type.LIFTOFF) {
				continue;
			}

			double eventTime = event.getTime();
			int dataIndex = findTimeIndex(eventTime);
			if (dataIndex < 0 || dataIndex >= timeData.size()) continue;
			if (dataIndex > currentIndex) continue;

			Double px = posXData.get(dataIndex);
			Double alt = altitudeData.get(dataIndex);
			if (px == null || alt == null) continue;

			int sx = MARGIN + (int) ((px - minPosX) / (maxPosX - minPosX) * plotW);
			int sy = h - MARGIN - (int) (alt / maxAltitude * plotH);

			// Draw diamond marker
			g2.setColor(getEventColor(type));
			int ms = 6;
			int[] xPoints = {sx, sx + ms, sx, sx - ms};
			int[] yPoints = {sy - ms, sy, sy + ms, sy};
			g2.fillPolygon(xPoints, yPoints, 4);
			g2.setColor(EVENT_MARKER_COLOR);
			g2.drawPolygon(xPoints, yPoints, 4);

			// Draw label
			String label = getEventLabel(type);
			g2.setColor(EVENT_MARKER_COLOR);
			g2.drawString(label, sx + ms + 3, sy + fm.getAscent() / 2);
		}
	}

	private void drawRocketMarker(Graphics2D g2, int w, int h) {
		if (currentIndex < 0 || currentIndex >= timeData.size()) return;

		int plotW = w - 2 * MARGIN;
		int plotH = h - 2 * MARGIN;

		Double px = posXData.get(currentIndex);
		Double alt = altitudeData.get(currentIndex);
		if (px == null || alt == null) return;

		int sx = MARGIN + (int) ((px - minPosX) / (maxPosX - minPosX) * plotW);
		int sy = h - MARGIN - (int) (alt / maxAltitude * plotH);

		// Compute velocity direction for orientation
		double angle = -Math.PI / 2; // default: pointing up
		if (currentIndex > 0) {
			Double prevX = posXData.get(currentIndex - 1);
			Double prevAlt = altitudeData.get(currentIndex - 1);
			if (prevX != null && prevAlt != null) {
				double dx = px - prevX;
				double dy = alt - prevAlt;
				if (Math.abs(dx) > 1e-10 || Math.abs(dy) > 1e-10) {
					// In screen coordinates, Y is flipped
					angle = Math.atan2(-dy, dx);
				}
			}
		}

		// Draw a triangle oriented along the velocity direction
		AffineTransform old = g2.getTransform();
		g2.translate(sx, sy);
		g2.rotate(angle);

		Path2D rocket = new Path2D.Double();
		rocket.moveTo(10, 0);     // nose
		rocket.lineTo(-6, -5);    // left fin
		rocket.lineTo(-4, 0);     // tail center
		rocket.lineTo(-6, 5);     // right fin
		rocket.closePath();

		g2.setColor(new Color(220, 50, 50));
		g2.fill(rocket);
		g2.setColor(Color.BLACK);
		g2.setStroke(new BasicStroke(1.0f));
		g2.draw(rocket);

		g2.setTransform(old);
	}

	private void drawAxisLabels(Graphics2D g2, int w, int h) {
		g2.setFont(LABEL_FONT.deriveFont(Font.BOLD, 12f));
		g2.setColor(Color.DARK_GRAY);

		// Y-axis label (altitude)
		AffineTransform old = g2.getTransform();
		g2.translate(14, h / 2);
		g2.rotate(-Math.PI / 2);
		g2.drawString("Altitude (m)", 0, 0);
		g2.setTransform(old);

		// X-axis label (distance)
		FontMetrics fm = g2.getFontMetrics();
		String xLabel = "Horizontal Distance (m)";
		g2.drawString(xLabel, w / 2 - fm.stringWidth(xLabel) / 2, h - 5);
	}

	/**
	 * Maps a velocity value to a color on a blue-green-red gradient.
	 */
	private Color velocityToColor(double velocity) {
		double ratio = Math.min(1.0, velocity / maxVelocity);
		if (ratio < 0.5) {
			// Blue to green
			float t = (float) (ratio * 2.0);
			return new Color(0, (int) (255 * t), (int) (255 * (1 - t)));
		} else {
			// Green to red
			float t = (float) ((ratio - 0.5) * 2.0);
			return new Color((int) (255 * t), (int) (255 * (1 - t)), 0);
		}
	}

	private int findTimeIndex(double time) {
		if (timeData == null) return -1;
		for (int i = 0; i < timeData.size(); i++) {
			if (timeData.get(i) != null && timeData.get(i) >= time) {
				return i;
			}
		}
		return timeData.size() - 1;
	}

	private Color getEventColor(FlightEvent.Type type) {
		switch (type) {
			case LAUNCH: return new Color(0, 180, 0);
			case LIFTOFF: return new Color(0, 200, 100);
			case BURNOUT: return new Color(255, 140, 0);
			case APOGEE: return new Color(0, 100, 255);
			case RECOVERY_DEVICE_DEPLOYMENT: return new Color(180, 0, 180);
			case GROUND_HIT: return new Color(160, 82, 45);
			default: return Color.GRAY;
		}
	}

	private String getEventLabel(FlightEvent.Type type) {
		switch (type) {
			case LAUNCH: return "Launch";
			case LIFTOFF: return "Liftoff";
			case BURNOUT: return "Burnout";
			case APOGEE: return "Apogee";
			case RECOVERY_DEVICE_DEPLOYMENT: return "Deploy";
			case GROUND_HIT: return "Landing";
			default: return type.toString();
		}
	}

	private String formatValue(double value) {
		if (Math.abs(value) >= 1000) {
			return String.format("%.0f", value);
		} else if (Math.abs(value) >= 100) {
			return String.format("%.0f", value);
		} else if (Math.abs(value) >= 10) {
			return String.format("%.1f", value);
		} else {
			return String.format("%.1f", value);
		}
	}
}
