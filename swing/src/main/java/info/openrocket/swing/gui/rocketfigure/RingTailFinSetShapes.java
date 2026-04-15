package info.openrocket.swing.gui.rocketfigure;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

import info.openrocket.core.rocketcomponent.RingTailFinSet;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.Transformation;

/**
 * Shape provider for the RingTailFinSet component.
 * Side view: rectangle spanning the ring diameter and chord length.
 * Back view: circle representing the ring cross-section.
 *
 * @author Alex Zoghlin
 */
public class RingTailFinSetShapes extends RocketComponentShapes {
	@Override
	public Class<? extends RocketComponent> getShapeClass() {
		return RingTailFinSet.class;
	}

	@Override
	public RocketComponentShapes[] getShapesSide(final RocketComponent component, final Transformation transformation) {
		final RingTailFinSet ring = (RingTailFinSet) component;
		final double radius = ring.getRingRadius();
		final double chord = ring.getRingChord();

		// From the side, the ring projects as a rectangle spanning the full diameter
		final CoordinateIF location = transformation.transform(Coordinate.ZERO);

		final Shape[] shapes = new Shape[] {
				new Rectangle2D.Double(location.getX(), location.getY() - radius, chord, 2 * radius)
		};

		return RocketComponentShapes.toArray(shapes, component);
	}

	@Override
	public RocketComponentShapes[] getShapesBack(final RocketComponent component, final Transformation transformation) {
		final RingTailFinSet ring = (RingTailFinSet) component;
		final double radius = ring.getRingRadius();
		final double thickness = ring.getRingThickness();
		final CoordinateIF location = transformation.transform(Coordinate.ZERO);

		double outerR = radius + thickness / 2;
		double innerR = radius - thickness / 2;

		final Shape[] shapes = new Shape[] {
				// Outer circle
				new Ellipse2D.Double(location.getZ() - outerR, location.getY() - outerR, 2 * outerR, 2 * outerR),
				// Inner circle (will create ring appearance when drawn)
				new Ellipse2D.Double(location.getZ() - innerR, location.getY() - innerR, 2 * innerR, 2 * innerR)
		};

		return RocketComponentShapes.toArray(shapes, component);
	}
}
