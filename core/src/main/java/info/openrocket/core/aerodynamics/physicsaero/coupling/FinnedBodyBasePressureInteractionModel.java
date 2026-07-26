package info.openrocket.core.aerodynamics.physicsaero.coupling;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Low-confidence envelope for the change in body-base suction caused by fins
 * whose trailing edges are at, or close to, the body base.
 *
 * <p>NACA RM A53D02, pp. 10-11 and figure 9, explicitly says that no adequate
 * theory or sufficiently broad data existed to correlate this interaction.
 * It nevertheless identifies fin planform, thickness ratio, count, and axial
 * proximity to the base as important variables.  It also assumes that the fin
 * trailing-edge base pressure equals the body-base pressure.  This class
 * therefore implements a bounded engineering envelope, not a new fundamental
 * base-flow correlation.</p>
 *
 * <p>The geometry coordinate is a fin-solidity proxy</p>
 *
 * <pre>
 * S = sum(N A_planform / A_body-base * t/c * exp(-2 gap / D)).
 * </pre>
 *
 * <p>It is normalized by the A53D02 basic configuration ({@code S=0.22918})
	 * and smoothly saturated.  The Mach envelope grows smoothly while the report
	 * first becomes supersonic and develops while the report says the fin-tip
	 * Mach lines sweep the body base (about M=1.5 to 1.9), then
	 * tends rapidly to zero as the finned and finless base-pressure data converge.
	 * The maximum added suction is
 * bounded both absolutely and relative to the supplied primary body-base
 * pressure.  None of the constants are fitted to total-drag ordinates.</p>
 *
 * <p>Ownership is deliberately split.  The returned pressure increment is a
 * modifier to the primary body-base pressure on the actual body-base area.
 * The same corrected pressure may be applied separately to the actual fin
 * trailing-edge base area.  Fin planform area is only an interaction
 * coordinate and must never be used as a pressure-loaded base area.</p>
 */
public final class FinnedBodyBasePressureInteractionModel {
	public static final String METHOD_ID = "A53D02_FINNED_BODY_BASE_INTERACTION_ENVELOPE_V1";
	public static final String SOURCE = "NACA RM A53D02, pp. 10-11, 14 and figure 9";

	/** A53D02: four fins, per-fin planform/body-base area = 4.5/pi, t/c = 0.04. */
	public static final double REFERENCE_FIN_SOLIDITY = 4.0 * (4.5 / Math.PI) * 0.04;
	public static final double MAX_ABSOLUTE_PRESSURE_INCREMENT = 0.06;
	public static final double MAX_RELATIVE_PRESSURE_INCREMENT = 0.50;
	public static final double MAX_CONFIDENCE = 0.20;

	private static final double MIN_MACH = 1.20;
	private static final double SOURCE_THEORY_MIN_MACH = 1.50;
	private static final double MAX_MACH = 6.50;
	private static final double MACH_ENVELOPE_CENTER = 1.90;
	private static final double MACH_ENVELOPE_WIDTH = 1.00;
	private static final double INTERACTION_FULLY_DEVELOPED_MACH = 1.90;
	private static final double PROXIMITY_DECAY_PER_DIAMETER = 2.00;
	private static final double REFERENCE_SATURATION = -Math.expm1(-1.0);

	/** Primary body-base state.  Pressure coefficient is negative for suction. */
	public record BaseState(double mach, boolean powered, double bodyBasePressureCoefficient,
			double bodyBaseAreaM2, double bodyDiameterM) {
		public BaseState {
			if (!Double.isFinite(mach) || !Double.isFinite(bodyBasePressureCoefficient)
					|| !Double.isFinite(bodyBaseAreaM2) || !Double.isFinite(bodyDiameterM)
					|| mach <= 0 || bodyBasePressureCoefficient > 0
					|| bodyBaseAreaM2 <= 0 || bodyDiameterM <= 0) {
				throw new IllegalArgumentException("invalid body-base interaction state");
			}
		}
	}

	/**
	 * One geometrically similar fin set.  The planform area is for one exposed
	 * fin; count supplies the number of physical fins in the set.
	 */
	public record FinSet(int count, double planformAreaPerFinM2, double thicknessRatio,
			double trailingEdgeDistanceFromBodyBaseM) {
		public FinSet {
			if (count <= 0 || !Double.isFinite(planformAreaPerFinM2)
					|| !Double.isFinite(thicknessRatio)
					|| !Double.isFinite(trailingEdgeDistanceFromBodyBaseM)
					|| planformAreaPerFinM2 <= 0 || thicknessRatio < 0
					|| trailingEdgeDistanceFromBodyBaseM < 0) {
				throw new IllegalArgumentException("invalid fin-set interaction geometry");
			}
		}
	}

	/**
	 * Pressure result shared by the body base and fin trailing edges.
	 * {@code pressureCoefficientIncrement} is non-positive: adding it to the
	 * primary coefficient can only increase suction within this envelope.
	 */
	public record Result(double primaryBodyBasePressureCoefficient,
			double pressureCoefficientIncrement, double correctedBodyBasePressureCoefficient,
			double finBasePressureCoefficient, double effectiveFinSolidity,
			double geometryInfluence, double machInfluence, double confidence,
			double uncertainty, List<String> validityFlags) {
		public Result {
			validityFlags = List.copyOf(validityFlags);
			if (!Double.isFinite(primaryBodyBasePressureCoefficient)
					|| !Double.isFinite(pressureCoefficientIncrement)
					|| !Double.isFinite(correctedBodyBasePressureCoefficient)
					|| !Double.isFinite(finBasePressureCoefficient)
					|| !Double.isFinite(effectiveFinSolidity)
					|| !Double.isFinite(geometryInfluence) || !Double.isFinite(machInfluence)
					|| pressureCoefficientIncrement > 0 || effectiveFinSolidity < 0
					|| geometryInfluence < 0 || geometryInfluence > 1
					|| machInfluence < 0 || machInfluence > 1
					|| confidence < 0 || confidence > MAX_CONFIDENCE || uncertainty < 0) {
				throw new IllegalArgumentException("invalid finned-base interaction result");
			}
		}

		/**
		 * Additive drag-coefficient modifier for an actual pressure-loaded area.
		 * Use the body-base area and fin trailing-edge area in separate ownership
		 * entries; never pass fin planform area here.
		 */
		public double pressureDragCoefficientIncrement(double pressureLoadedAreaM2,
				double referenceAreaM2) {
			if (!Double.isFinite(pressureLoadedAreaM2) || !Double.isFinite(referenceAreaM2)
					|| pressureLoadedAreaM2 < 0 || referenceAreaM2 <= 0) {
				throw new IllegalArgumentException("invalid pressure/reference area");
			}
			return -pressureCoefficientIncrement * pressureLoadedAreaM2 / referenceAreaM2;
		}
	}

	public Result evaluate(BaseState base, List<FinSet> finSets) {
		Objects.requireNonNull(base, "base");
		Objects.requireNonNull(finSets, "finSets");
		if (base.powered()) {
			throw new IllegalArgumentException("POWERED_FINNED_BASE_INTERACTION_NOT_SUPPORTED");
		}
		if (base.mach() < MIN_MACH || base.mach() > MAX_MACH) {
			throw new IllegalArgumentException("OUTSIDE_A53D02_FINNED_BASE_INTERACTION_RANGE");
		}

		double effectiveSolidity = 0;
		for (FinSet finSet : finSets) {
			Objects.requireNonNull(finSet, "finSet");
			double proximity = Math.exp(-PROXIMITY_DECAY_PER_DIAMETER
					* finSet.trailingEdgeDistanceFromBodyBaseM() / base.bodyDiameterM());
			effectiveSolidity += finSet.count() * finSet.planformAreaPerFinM2()
					/ base.bodyBaseAreaM2() * finSet.thicknessRatio() * proximity;
		}

		double normalizedSolidity = effectiveSolidity / REFERENCE_FIN_SOLIDITY;
		double geometryInfluence = normalizedSolidity <= 0 ? 0
				: Math.min(1, -Math.expm1(-normalizedSolidity) / REFERENCE_SATURATION);
		double normalizedMach = (base.mach() - MACH_ENVELOPE_CENTER) / MACH_ENVELOPE_WIDTH;
		double development = smoothStep((base.mach() - MIN_MACH)
				/ (INTERACTION_FULLY_DEVELOPED_MACH - MIN_MACH));
		double machInfluence = development * Math.exp(-normalizedMach * normalizedMach);
		double absoluteBound = MAX_ABSOLUTE_PRESSURE_INCREMENT
				* geometryInfluence * machInfluence;
		double relativeBound = MAX_RELATIVE_PRESSURE_INCREMENT
				* Math.abs(base.bodyBasePressureCoefficient());
		double pressureIncrement = -Math.min(absoluteBound, relativeBound);
		double correctedPressure = base.bodyBasePressureCoefficient() + pressureIncrement;

		List<String> flags = new ArrayList<>();
		flags.add("LOW_CONFIDENCE_EMPIRICAL_ENVELOPE");
		flags.add("BODY_AND_FIN_BASE_PRESSURE_EQUAL");
		flags.add("BODY_BASE_AREA_MODIFIER_ONLY");
		flags.add("FIN_TRAILING_EDGE_AREA_OWNED_SEPARATELY");
		flags.add("FIN_PLANFORM_IS_INTERACTION_COORDINATE_NOT_PRESSURE_AREA");
		if (base.mach() < SOURCE_THEORY_MIN_MACH) {
			flags.add("LOW_MACH_EXTRAPOLATION_BELOW_A53D02_THEORY_COMPARISON");
		}
		if (geometryInfluence >= 1) {
			flags.add("GEOMETRY_INFLUENCE_SATURATED");
		}
		if (effectiveSolidity == 0) {
			flags.add("NO_FIN_SOLIDITY_INTERACTION");
		}

		double confidence = base.mach() >= SOURCE_THEORY_MIN_MACH && base.mach() <= 3.0
				? MAX_CONFIDENCE : MAX_CONFIDENCE / 2;
		return new Result(base.bodyBasePressureCoefficient(), pressureIncrement,
				correctedPressure, correctedPressure, effectiveSolidity,
				geometryInfluence, machInfluence, confidence, 1 - confidence, flags);
	}

	private static double smoothStep(double value) {
		double bounded = Math.max(0, Math.min(1, value));
		return bounded * bounded * (3 - 2 * bounded);
	}
}
