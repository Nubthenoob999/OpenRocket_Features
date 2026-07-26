package info.openrocket.core.aerodynamics.physicsaero.body;

import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AxisymmetricProfile;

/**
 * Engineering pressure-recovery envelope for a contracting axisymmetric
 * afterbody whose boundary layer may separate.
 *
 * <p>The local base-pressure magnitude supplies the separated-flow pressure
 * scale. A short, steep contraction receives that pressure over its annular
 * projected area; recovery increases linearly with contraction fineness and is
 * complete at {@code L/(D_fore-D_aft) >= 3}. This is the historical
 * extended-Barrowman/OpenRocket boattail closure, isolated here so it has
 * explicit geometry, pressure-area, method, and ownership semantics.</p>
 */
public final class SeparatedBoattailPressureDragModel {
	public static final String METHOD_ID =
			"EXTENDED_BARROWMAN_SEPARATED_BOATTAIL_PRESSURE_V1";

	public double dragCoefficient(AeroGeometry geometry,
			double basePressureMagnitude) {
		if (geometry == null || !Double.isFinite(basePressureMagnitude)
				|| basePressureMagnitude < 0) {
			throw new IllegalArgumentException("invalid boattail pressure state");
		}
		double referenceArea = geometry.references().referenceAreaM2();
		double coefficient = 0;
		for (AeroComponent component : geometry.components()) {
			AxisymmetricProfile profile = component.axisymmetricProfile();
			if (profile == null || profile.stations().size() < 2) continue;
			double foreRadius = profile.stations().get(0).radiusM();
			double aftRadius = profile.stations()
					.get(profile.stations().size() - 1).radiusM();
			double length = component.axialEndM() - component.axialStartM();
			coefficient += dragCoefficient(foreRadius, aftRadius, length,
					referenceArea, basePressureMagnitude);
		}
		return coefficient;
	}

	double dragCoefficient(AxisymmetricBodySegment segment,
			double referenceArea, double basePressureMagnitude) {
		return dragCoefficient(segment.startRadiusM(), segment.endRadiusM(),
				segment.endXM() - segment.startXM(), referenceArea,
				basePressureMagnitude);
	}

	private static double dragCoefficient(double foreRadius, double aftRadius,
			double length, double referenceArea, double basePressureMagnitude) {
		double radialContraction = foreRadius - aftRadius;
		if (!(radialContraction > 0)) return 0;
		double recoveryFactor;
		if (length < 0.001) {
			recoveryFactor = 1;
		} else {
			double contractionFineness = length / (2 * radialContraction);
			recoveryFactor = Math.max(0, Math.min(1,
					(3 - contractionFineness) / 2));
		}
		double annularProjectedArea =
				Math.PI * (foreRadius * foreRadius - aftRadius * aftRadius);
		return basePressureMagnitude * annularProjectedArea / referenceArea
				* recoveryFactor;
	}
}
