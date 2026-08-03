package info.openrocket.core.aerodynamics.physicsaero.body;

import java.util.Comparator;

import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;

/**
 * RASAero II 1.0.2.0 empirical supersonic base-drag envelope.
 *
 * <p>The smooth-base pressure polynomial is applied to exposed base area.  A
 * terminal boattail uses the implementation's cubic diameter-ratio recovery;
 * boattails steeper than 17.5 degrees use the equivalent 17.5-degree aft
 * diameter.  This is an upper envelope on already assembled finned-base drag,
 * so it never invents extra suction when the resolved correlations are lower.</p>
 */
public final class RasaeroSupersonicBaseDragEnvelope {
	public static final String METHOD_ID =
			"RASAERO_102_SUPERSONIC_CUBIC_BASE_DRAG_ENVELOPE_V1";
	private static final double MAX_BOATTAIL_ANGLE_RAD = Math.toRadians(17.5);

	public double dragCoefficient(AeroGeometry geometry, double mach) {
		if (geometry == null || !Double.isFinite(mach) || mach < 1.15
				|| mach > 5) {
			return Double.NaN;
		}
		double pressureMagnitude = 0.34464 - 0.14358 * mach
				+ 0.024909 * mach * mach - 0.0015993 * mach * mach * mach;
		double referenceArea = geometry.references().referenceAreaM2();
		double exposedBaseArea = geometry.references().exposedBaseAreaM2();
		if (!(pressureMagnitude > 0) || !(referenceArea > 0)
				|| !(exposedBaseArea > 0)) {
			return Double.NaN;
		}

		AeroComponent terminal = geometry.components().stream()
				.filter(component -> component.axisymmetricProfile() != null)
				.max(Comparator.comparingDouble(AeroComponent::axialEndM))
				.orElse(null);
		if (terminal == null || !"BOATTAIL".equals(terminal.classification())) {
			return pressureMagnitude * exposedBaseArea / referenceArea;
		}

		var stations = terminal.axisymmetricProfile().stations();
		double foreRadius = stations.get(0).radiusM();
		double aftRadius = stations.get(stations.size() - 1).radiusM();
		double length = terminal.axialEndM() - terminal.axialStartM();
		if (!(foreRadius > aftRadius) || !(length > 0)) {
			return pressureMagnitude * exposedBaseArea / referenceArea;
		}
		double actualAngle = Math.atan2(foreRadius - aftRadius, length);
		double effectiveAftRadius = actualAngle > MAX_BOATTAIL_ANGLE_RAD
				? Math.max(0, foreRadius - length * Math.tan(MAX_BOATTAIL_ANGLE_RAD))
				: aftRadius;
		double cubicRecovery = Math.pow(effectiveAftRadius / foreRadius, 3);
		double maximumBodyArea = Math.PI * Math.pow(
				geometry.references().maximumBodyDiameterM() / 2, 2);
		return pressureMagnitude * cubicRecovery * maximumBodyArea / referenceArea;
	}
}
