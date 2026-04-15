package info.openrocket.core.rocketcomponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import info.openrocket.core.l10n.Translator;
import info.openrocket.core.preset.ComponentPreset;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.BoundingBox;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.MathUtil;

/**
 * A ring-tail fin component representing an annular (ring-shaped) fin that
 * wraps completely around the rocket body. This is a single cylindrical fin
 * surface typically mounted at the aft end of the rocket.
 *
 * The ring-tail is essentially a hollow cylinder section (band) centered on
 * the rocket's axis at a specified radius from the centerline.
 *
 * @author Alex Zoghlin
 */
public class RingTailFinSet extends ExternalComponent implements BoxBounded {
	private static final Translator trans = Application.getTranslator();

	// Default values
	private static final double DEFAULT_RING_RADIUS = 0.04;   // 40mm from centerline
	private static final double DEFAULT_RING_CHORD = 0.05;    // 50mm axial extent
	private static final double DEFAULT_RING_THICKNESS = 0.002; // 2mm wall thickness

	/**
	 * Radius of the ring measured from the rocket centerline to the ring center.
	 */
	private double ringRadius = DEFAULT_RING_RADIUS;

	/**
	 * Chord length of the ring (axial extent along the rocket axis).
	 */
	private double ringChord = DEFAULT_RING_CHORD;

	/**
	 * Wall thickness of the ring material.
	 */
	private double ringThickness = DEFAULT_RING_THICKNESS;

	/**
	 * Create a new RingTailFinSet with default values.
	 * Sets the component relative position to BOTTOM,
	 * i.e. positioned at the bottom of the parent component.
	 */
	public RingTailFinSet() {
		super(AxialMethod.BOTTOM);
		this.length = DEFAULT_RING_CHORD;
		super.displayOrder_side = 3;
		super.displayOrder_back = 3;
	}

	/**
	 * Return the radius of the ring from the rocket centerline.
	 *
	 * @return the ring radius in standard units
	 */
	public double getRingRadius() {
		return ringRadius;
	}

	/**
	 * Set the radius of the ring from the rocket centerline.
	 *
	 * @param radius the ring radius in standard units
	 */
	public void setRingRadius(double radius) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof RingTailFinSet) {
				((RingTailFinSet) listener).setRingRadius(radius);
			}
		}

		if (MathUtil.equals(this.ringRadius, radius))
			return;
		this.ringRadius = Math.max(radius, 0);
		fireComponentChangeEvent(ComponentChangeEvent.BOTH_CHANGE);
	}

	/**
	 * Return the chord length of the ring (axial extent).
	 *
	 * @return the ring chord in standard units
	 */
	public double getRingChord() {
		return ringChord;
	}

	/**
	 * Set the chord length of the ring (axial extent).
	 *
	 * @param chord the ring chord in standard units
	 */
	public void setRingChord(double chord) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof RingTailFinSet) {
				((RingTailFinSet) listener).setRingChord(chord);
			}
		}

		if (MathUtil.equals(this.ringChord, chord))
			return;
		this.ringChord = Math.max(chord, 0);
		this.length = this.ringChord;
		fireComponentChangeEvent(ComponentChangeEvent.BOTH_CHANGE);
	}

	/**
	 * Return the wall thickness of the ring.
	 *
	 * @return the ring thickness in standard units
	 */
	public double getRingThickness() {
		return ringThickness;
	}

	/**
	 * Set the wall thickness of the ring.
	 *
	 * @param thickness the ring thickness in standard units
	 */
	public void setRingThickness(double thickness) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof RingTailFinSet) {
				((RingTailFinSet) listener).setRingThickness(thickness);
			}
		}

		if (MathUtil.equals(this.ringThickness, thickness))
			return;
		this.ringThickness = Math.max(thickness, 0);
		fireComponentChangeEvent(ComponentChangeEvent.BOTH_CHANGE);
	}

	@Override
	public double getLength() {
		return ringChord;
	}

	/**
	 * Calculate the volume of the ring-tail fin.
	 * Volume = 2 * PI * ringRadius * ringChord * ringThickness
	 * This is the volume of a thin cylindrical shell.
	 */
	@Override
	public double getComponentVolume() {
		return 2 * Math.PI * ringRadius * ringChord * ringThickness;
	}

	@Override
	public String getComponentName() {
		return trans.get("RingTailFinSet.RingTailFinSet");
	}

	@Override
	public Coordinate getComponentCG() {
		double mass = getComponentMass();
		double halfChord = ringChord / 2;
		// CG is at the center of the ring along the axis, on the centerline
		return new Coordinate(halfChord, 0, 0, mass);
	}

	@Override
	public double getLongitudinalUnitInertia() {
		// Longitudinal inertia of a thin cylindrical shell about its center:
		// I = (1/12) * (6 * R^2 + L^2)  where R is radius and L is length
		return (6 * MathUtil.pow2(ringRadius) + MathUtil.pow2(ringChord)) / 12;
	}

	@Override
	public double getRotationalUnitInertia() {
		// Rotational inertia of a thin cylindrical shell about its axis:
		// I = R^2
		return MathUtil.pow2(ringRadius);
	}

	@Override
	public boolean allowsChildren() {
		return false;
	}

	@Override
	public boolean isCompatible(Class<? extends RocketComponent> type) {
		return false;
	}

	@Override
	public boolean isAfter() {
		return false;
	}

	@Override
	public ComponentPreset.Type getPresetType() {
		return null;
	}

	@Override
	public Collection<CoordinateIF> getComponentBounds() {
		List<CoordinateIF> bounds = new ArrayList<>();
		addBound(bounds, 0, 2 * ringRadius);
		addBound(bounds, ringChord, 2 * ringRadius);
		return bounds;
	}

	@Override
	public BoundingBox getInstanceBoundingBox() {
		BoundingBox box = new BoundingBox();
		double outerR = ringRadius + ringThickness / 2;
		box.update(new Coordinate(0, -outerR, -outerR));
		box.update(new Coordinate(ringChord, outerR, outerR));
		return box;
	}

	@Override
	public void setAxialMethod(AxialMethod position) {
		super.setAxialMethod(position);
		fireComponentChangeEvent(ComponentChangeEvent.NONFUNCTIONAL_CHANGE);
	}

	@Override
	protected List<RocketComponent> copyFrom(RocketComponent c) {
		RingTailFinSet src = (RingTailFinSet) c;
		this.ringRadius = src.ringRadius;
		this.ringChord = src.ringChord;
		this.ringThickness = src.ringThickness;
		return super.copyFrom(c);
	}
}
