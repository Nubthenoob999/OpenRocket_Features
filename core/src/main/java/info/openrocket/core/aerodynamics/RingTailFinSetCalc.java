package info.openrocket.core.aerodynamics.barrowman;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.RingTailFinSet;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.MathUtil;
import info.openrocket.core.util.Transformation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aerodynamic calculator for the RingTailFinSet component.
 *
 * Uses ring airfoil theory based on Ribner, "The ring airfoil in nonaxial flow",
 * Journal of the Aeronautical Sciences 14(9) pp 529-530 (1947).
 *
 * @author Alex Zoghlin
 */
public class RingTailFinSetCalc extends RocketComponentCalc {
	private static final Logger log = LoggerFactory.getLogger(RingTailFinSetCalc.class);

	private static final double STALL_ANGLE = 20 * Math.PI / 180;

	private final double ringRadius;
	private final double chord;
	private final double thickness;
	private final double wettedArea;
	private final double frontalArea;
	private final double cnaConst;

	public RingTailFinSetCalc(RocketComponent component) {
		super(component);

		if (!(component instanceof RingTailFinSet)) {
			throw new IllegalArgumentException("Illegal component type " + component);
		}

		RingTailFinSet ring = (RingTailFinSet) component;
		this.ringRadius = ring.getRingRadius();
		this.chord = ring.getRingChord();
		this.thickness = ring.getRingThickness();

		// Wetted area: outer and inner cylindrical surfaces of the ring
		double outerRadius = ringRadius + thickness / 2.0;
		double innerRadius = ringRadius - thickness / 2.0;
		this.wettedArea = 2 * Math.PI * (outerRadius + innerRadius) * chord;

		// Frontal area: annular cross-section of the ring
		this.frontalArea = Math.PI * (MathUtil.pow2(outerRadius) - MathUtil.pow2(innerRadius));

		// CNa using ring airfoil theory (Ribner 1947)
		// aspect ratio for a ring: AR = 2 * diameter / chord = 4 * ringRadius / chord
		double ar = 2.0 * (2.0 * ringRadius) / chord;
		double arPrime = 2.0 * ar / Math.PI;
		this.cnaConst = 2.0 * (arPrime / (1.0 + arPrime)) * Math.PI * Math.PI * ringRadius * chord;
	}

	@Override
	public void calculateNonaxialForces(FlightConditions conditions, Transformation transform,
			AerodynamicForces forces, WarningSet warnings) {

		if (chord < 0.001 || ringRadius < 0.001) {
			forces.setCm(0);
			forces.setCN(0);
			forces.setCP(Coordinate.ZERO);
			forces.setCroll(0);
			forces.setCrollDamp(0);
			forces.setCrollForce(0);
			forces.setCside(0);
			forces.setCyaw(0);
			return;
		}

		double cna = cnaConst / conditions.getRefArea();

		// CP at quarter-chord for subsonic, adjusted for supersonic
		double cpPos;
		double mach = conditions.getMach();
		if (mach <= 0.5) {
			cpPos = 0.25 * chord;
		} else if (mach >= 2.0) {
			double beta = conditions.getBeta();
			double ar = 4.0 * ringRadius / chord;
			cpPos = ((ar * beta - 0.67) / (2.0 * ar * beta - 1.0)) * chord;
		} else {
			// Linear interpolation between subsonic and supersonic
			double frac = (mach - 0.5) / 1.5;
			double subPos = 0.25;
			double beta = Math.sqrt(Math.abs(4.0 - 1.0));  // at mach 2
			double ar = 4.0 * ringRadius / chord;
			double superPos = (ar * beta - 0.67) / (2.0 * ar * beta - 1.0);
			cpPos = (subPos + frac * (superPos - subPos)) * chord;
		}

		forces.setCN(cna * Math.min(conditions.getAOA(), STALL_ANGLE));
		forces.setCP(new Coordinate(cpPos, 0, 0, cna));
		forces.setCm(forces.getCN() * cpPos / conditions.getRefLength());

		// Ring-tail has no roll forcing (symmetric, no cant)
		forces.setCrollForce(0);
		forces.setCrollDamp(0);
		forces.setCroll(0);

		forces.setCside(0);
		forces.setCyaw(0);
	}

	@Override
	public double calculateFrictionCD(FlightConditions conditions, double componentCf, WarningSet warnings) {
		return componentCf * wettedArea / conditions.getRefArea();
	}

	@Override
	public double calculatePressureCD(FlightConditions conditions,
			double stagnationCD, double baseCD, WarningSet warnings) {
		// Pressure drag from the frontal area of the ring cross-section
		return 0.7 * (stagnationCD + baseCD) * frontalArea / conditions.getRefArea();
	}
}
