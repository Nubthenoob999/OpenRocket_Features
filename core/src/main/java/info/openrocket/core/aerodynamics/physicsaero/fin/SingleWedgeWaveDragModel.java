package info.openrocket.core.aerodynamics.physicsaero.fin;

import java.util.List;

import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ModifiedNewtonianPressure;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ThermodynamicModel;

/**
 * Zero-lift pressure drag on an asymmetric single-wedge leading-edge bevel.
 *
 * <p>The section has one inclined bevel face, one freestream-parallel face, and
 * parallel surfaces downstream of the bevel.  The supplied thickness ratio is
 * the full section thickness divided by local chord.  If no explicit bevel angle
 * is supplied, the bevel is conservatively taken to extend over the full chord,
 * giving {@code 2(t/c)^2 / sqrt(M^2 - 1)} in first-order Ackeret theory.  This
 * model deliberately excludes skin friction and pressure on the blunt trailing
 * edge.  NACA RM A53D02 uses this geometry with a measured 20-degree bevel.</p>
 *
 * <p>Shock expansion is locally valid only for a supersonic leading edge.  The
 * exact branch applies the swept-wing independence principle by marching the flow
 * normal to the leading edge.  A locally subsonic or near-sonic leading edge needs
 * a finite-wing solution that is not available in this strip solver, so the model
 * returns a named, conservative unswept Ackeret fallback instead of silently using
 * two-dimensional shock expansion outside its domain.</p>
 */
public final class SingleWedgeWaveDragModel {
	public static final String EXACT_METHOD_ID = "SINGLE_WEDGE_NORMAL_SHOCK_EXPANSION_PROFILE_DRAG_V1";
	public static final String LINEARIZED_FALLBACK_METHOD_ID = "SINGLE_WEDGE_ACKERET_PROFILE_DRAG_FALLBACK_V1";
	public static final String MIXED_METHOD_ID = "SINGLE_WEDGE_MIXED_EXACT_LINEARIZED_PROFILE_DRAG_V1";
	public static final String UNAVAILABLE_METHOD_ID = "SINGLE_WEDGE_PROFILE_DRAG_UNAVAILABLE_V1";
	public static final String SUBSONIC_LEADING_EDGE_FLAG = "LOCALLY_SUBSONIC_LEADING_EDGE";
	public static final String NEAR_SONIC_LEADING_EDGE_FLAG = "LOCALLY_NEAR_SONIC_LEADING_EDGE";
	public static final String LINEARIZED_FALLBACK_FLAG = "LINEARIZED_PROFILE_DRAG_FALLBACK";
	public static final String FORWARD_FACE_OWNERSHIP_FLAG =
			"FORWARD_FACES_ONLY_BLUNT_BASE_PRESSURE_EXCLUDED";

	private final WedgeDiamondShockExpansionModel shockExpansion = new WedgeDiamondShockExpansionModel();
	private final FinWaveDragModel linearized = new FinWaveDragModel();
	private final ModifiedNewtonianPressure pressureLimit = new ModifiedNewtonianPressure();

	public Result evaluate(FinLocalFlow local, FinStrip strip, ThermodynamicModel thermodynamics) {
		return evaluate(local, strip, thermodynamics, Math.atan(strip.thicknessToChord()));
	}

	public Result evaluate(FinLocalFlow local, FinStrip strip, ThermodynamicModel thermodynamics,
			double wedgeAngleRad) {
		if (local == null || strip == null || thermodynamics == null) {
			throw new NullPointerException("single-wedge inputs must not be null");
		}
		validateBevel(strip.thicknessToChord(), wedgeAngleRad);
		if (local.leadingEdge() == LeadingEdgeClassification.SUPERSONIC_LEADING_EDGE) {
			Result exact = exact(local, strip, thermodynamics, wedgeAngleRad);
			if (exact.valid()) return exact;
			return linearizedFallback(local, strip, thermodynamics, wedgeAngleRad,
					"ATTACHED_SHOCK_EXPANSION_INVALID");
		}
		String leadingEdgeFlag = local.leadingEdge() == LeadingEdgeClassification.SUBSONIC_LEADING_EDGE
				? SUBSONIC_LEADING_EDGE_FLAG : NEAR_SONIC_LEADING_EDGE_FLAG;
		return linearizedFallback(local, strip, thermodynamics, wedgeAngleRad, leadingEdgeFlag);
	}

	private Result exact(FinLocalFlow local, FinStrip strip, ThermodynamicModel thermodynamics,
			double wedgeAngleRad) {
		double streamMach = local.staticState().mach();
		double normalMach = local.normalMach();
		if (streamMach <= 1 || normalMach <= 1) return unavailable("NON_SUPERSONIC_LOCAL_STATE");
		double normalVelocity = local.staticState().velocityMS() * normalMach / streamMach;
		GasState normalState = new GasState(normalMach, local.staticState().pressurePa(),
				local.staticState().temperatureK(), local.staticState().densityKgM3(), normalVelocity);
		double normalVelocityFraction = normalMach / streamMach;
		double normalWedgeAngle = Math.atan(Math.tan(wedgeAngleRad) / normalVelocityFraction);
		var pressure = shockExpansion.evaluate(normalState, thermodynamics, 0,
				new double[] { normalWedgeAngle }, new double[] { 0 },
				new double[] { 1 }, strip.areaM2());
		if (!pressure.valid()) return unavailable("ATTACHED_SHOCK_EXPANSION_INVALID");
		double upperGauge = pressure.upper().panelStates().get(0).pressurePa() - normalState.pressurePa();
		double axialForce = Math.max(0, upperGauge * strip.thicknessToChord() * strip.areaM2());
		return new Result(true, axialForce, EXACT_METHOD_ID,
				List.of("ZERO_LIFT_ASYMMETRIC_SINGLE_WEDGE_BEVEL", "SUPERSONIC_LEADING_EDGE",
						"NORMAL_FLOW_SHOCK_EXPANSION", FORWARD_FACE_OWNERSHIP_FLAG), null);
	}

	private Result linearizedFallback(FinLocalFlow local, FinStrip strip,
			ThermodynamicModel thermodynamics, double wedgeAngleRad, String reason) {
		double mach = local.staticState().mach();
		if (mach <= 1) return unavailable("LOCAL_STATIC_MACH_NOT_SUPERSONIC");
		double coefficient = linearized.linearizedAsymmetricBevelCoefficient(mach,
				strip.thicknessToChord(), wedgeAngleRad, strip.leadingEdgeSweepRad());
		double finiteSpanRelief = Math.max(0, Math.cos(strip.leadingEdgeSweepRad()));
		double pressureUpperBound = pressureLimit.stagnationPressureCoefficient(
				local.staticState(), thermodynamics) * strip.thicknessToChord() * finiteSpanRelief;
		coefficient = Math.min(coefficient, pressureUpperBound);
		double axialForce = local.dynamicPressurePa() * strip.areaM2() * coefficient;
		return new Result(true, axialForce, LINEARIZED_FALLBACK_METHOD_ID,
				List.of("ZERO_LIFT_ASYMMETRIC_SINGLE_WEDGE_BEVEL", reason,
						"SWEPT_FINITE_SPAN_PRESSURE_RELIEF", LINEARIZED_FALLBACK_FLAG,
						FORWARD_FACE_OWNERSHIP_FLAG),
				reason + "_LINEARIZED_PROFILE_DRAG_FALLBACK");
	}

	private static void validateBevel(double thicknessToChord, double wedgeAngleRad) {
		if (!Double.isFinite(wedgeAngleRad) || wedgeAngleRad <= 0 || wedgeAngleRad >= Math.PI / 2
				|| thicknessToChord / Math.tan(wedgeAngleRad) > 1 + 1e-12) {
			throw new IllegalArgumentException("invalid asymmetric single-wedge bevel");
		}
	}

	private static Result unavailable(String reason) {
		return new Result(false, 0, UNAVAILABLE_METHOD_ID, List.of(reason), reason);
	}

	public record Result(boolean valid, double axialForceN, String methodId,
			List<String> validityFlags, String fallbackReason) {
		public Result {
			validityFlags = List.copyOf(validityFlags);
			if (!Double.isFinite(axialForceN) || axialForceN < 0 || methodId == null || methodId.isBlank()) {
				throw new IllegalArgumentException("invalid single-wedge wave-drag result");
			}
		}
	}
}
