package info.openrocket.core.aerodynamics.physicsaero.fin;

import java.util.List;
import java.util.Set;
import info.openrocket.core.aerodynamics.physicsaero.api.*;

/**
 * Missile-DATCOM-style finite-wing fallback. The equation is exposed as a diagnostic engineering
 * closure and is never combined with a complete pressure-derived normal force.
 */
public final class DatcomFinLiftModel {
	public static final String METHOD_ID = "DATCOM_FIN_LIFT_ENGINEERING_V1";
	public static final String SOURCE = "USAF Stability and Control DATCOM, Section 4.1.3.2, wing lift-curve-slope form; "
			+ "Phase 3 supersonic beta continuation; exposed planform area; SI/radians";
	public CorrelationMetadata metadata() {
		return new CorrelationMetadata(new MethodId(METHOD_ID), "1.0.0", "DATCOM-style finite-fin lift fallback", SOURCE,
				List.of("DATCOM_SECTION_4.1.3.2_FINITE_WING_FORM", "BETA_M=SQRT(M^2-1)"), Set.of("FIN"),
				new ValidityDomain(1.2, 5, 0, Double.POSITIVE_INFINITY, -Math.toRadians(15), Math.toRadians(15),
						"AR in (0,12], exposed planform, half-chord sweep", "calorically-perfect air"),
				Set.of(PhysicalTerm.FIN_LIFT), OwnershipMode.REPLACES,
				List.of("linear incidence", "eta=1 until section data are supplied", "not combined with pressure lift"),
				"ENGINEERING_FALLBACK_NOT_SOURCE_CASE_LOCKED");
	}
	public boolean isValid(double mach, double aspectRatio, double incidenceRad) {
		return mach >= 1.2 && mach <= 5 && aspectRatio > 0 && aspectRatio <= 12 && Math.abs(incidenceRad) <= Math.toRadians(15);
	}
	public Result evaluate(double mach, double aspectRatio, double halfChordSweepRad, double incidenceRad) {
		if (!isValid(mach, aspectRatio, incidenceRad)) throw new IllegalArgumentException("DATCOM fallback outside declared domain");
		double beta = Math.sqrt(mach * mach - 1);
		double sweepTerm = 1 + Math.pow(Math.tan(halfChordSweepRad) / beta, 2);
		double slope = 2 * Math.PI * aspectRatio /
				(2 + Math.sqrt(4 + aspectRatio * aspectRatio * beta * beta * sweepTerm));
		return new Result(slope, slope * incidenceRad, METHOD_ID, SOURCE);
	}
	public record Result(double normalForceSlopePerRad, double normalForceCoefficient,
			String methodId, String source) {}
}
