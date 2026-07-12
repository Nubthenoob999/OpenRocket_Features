package info.openrocket.core.aerodynamics.physicsaero.force;

import java.util.OptionalDouble;

public record CenterOfPressureDiagnostic(OptionalDouble pitchXM, OptionalDouble yawXM) {
	public static CenterOfPressureDiagnostic derive(AerodynamicCoefficients c, ReferenceState reference, double threshold) {
		OptionalDouble pitch = Math.abs(c.cn()) <= threshold ? OptionalDouble.empty()
				: OptionalDouble.of(reference.momentOriginM().x - c.cm() * reference.referenceLengthM() / c.cn());
		OptionalDouble yaw = Math.abs(c.cy()) <= threshold ? OptionalDouble.empty()
				: OptionalDouble.of(reference.momentOriginM().x + c.cYaw() * reference.referenceLengthM() / c.cy());
		return new CenterOfPressureDiagnostic(pitch, yaw);
	}
}
