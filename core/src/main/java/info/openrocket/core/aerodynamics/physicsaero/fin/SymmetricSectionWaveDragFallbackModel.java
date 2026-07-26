package info.openrocket.core.aerodynamics.physicsaero.fin;

import java.util.ArrayList;
import java.util.List;

import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ModifiedNewtonianPressure;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ThermodynamicModel;

/**
 * Bounded linearized profile-drag fallback for a symmetric polygonal section.
 *
 * <p>For a piecewise-linear section, first-order Ackeret theory gives
 * {@code Cd = 4 / beta * integral((dy/dx)^2 dx/c)}.  The normal-flow Mach
 * number and a finite-span cosine relief are used while the leading edge is
 * locally supersonic.  If the swept leading edge is locally subsonic, the
 * freestream Mach supplies a named finite-wing engineering fallback.</p>
 *
 * <p>The linear result is capped by two independent physical limits: modified
 * Newtonian stagnation pressure on the leading bevel and zero absolute pressure
 * on the trailing bevel.  Skin friction and trailing-edge base pressure are
 * excluded.</p>
 */
public final class SymmetricSectionWaveDragFallbackModel {
	public static final String METHOD_ID =
			"SYMMETRIC_SECTION_ACKERET_PROFILE_DRAG_FALLBACK_V1";
	public static final String MIXED_METHOD_ID =
			"SYMMETRIC_SECTION_MIXED_EXACT_LINEARIZED_PROFILE_DRAG_V1";

	private static final double NORMAL_MACH_MARGIN = 0.05;

	private final ModifiedNewtonianPressure pressureLimit =
			new ModifiedNewtonianPressure();

	public Result evaluate(FinLocalFlow local, FinStrip strip,
			FinSectionPanelGeometry.PanelLayout layout,
			ThermodynamicModel thermodynamics, String reason) {
		if (local == null || strip == null || layout == null
				|| thermodynamics == null || reason == null
				|| reason.isBlank()) {
			throw new IllegalArgumentException(
					"missing symmetric-section fallback input");
		}
		double streamMach = local.staticState().mach();
		if (!(streamMach > 1)) {
			return new Result(false, 0, 0, 0, false,
					List.of("LOCAL_STATIC_MACH_NOT_SUPERSONIC"),
					"LOCAL_STATIC_MACH_NOT_SUPERSONIC");
		}

		double cosine = Math.max(0,
				Math.cos(strip.leadingEdgeSweepRad()));
		boolean normalFlow = local.normalMach() > 1 + NORMAL_MACH_MARGIN;
		double betaMach = normalFlow ? local.normalMach() : streamMach;
		double slopeIntegral = 0;
		double[] angles = layout.surfaceAnglesRad();
		double[] fractions = layout.chordFractions();
		for (int i = 0; i < angles.length; i++) {
			double slope = Math.tan(angles[i]);
			slopeIntegral += fractions[i] * slope * slope;
		}
		double linearizedCd = 4 * slopeIntegral * cosine
				/ Math.sqrt(betaMach * betaMach - 1);

		double gamma = thermodynamics.gamma(
				local.staticState().temperatureK());
		double maximumCompressionCp =
				pressureLimit.stagnationPressureCoefficient(
						local.staticState(), thermodynamics);
		double maximumExpansionMagnitudeCp =
				2 / (gamma * streamMach * streamMach);
		double pressureBoundCd = (maximumCompressionCp
				+ maximumExpansionMagnitudeCp)
				* strip.thicknessToChord() * cosine;
		double coefficient = Math.min(linearizedCd, pressureBoundCd);
		double force = local.dynamicPressurePa() * strip.areaM2()
				* coefficient;

		ArrayList<String> flags = new ArrayList<>();
		flags.add(reason);
		flags.add("PIECEWISE_LINEAR_ACKERET_SECTION_INTEGRAL");
		flags.add(normalFlow ? "SUPERSONIC_NORMAL_FLOW"
				: "LOCALLY_SUBSONIC_LEADING_EDGE_FINITE_SPAN_FALLBACK");
		flags.add("SWEPT_FINITE_SPAN_PRESSURE_RELIEF");
		flags.add("MODIFIED_NEWTONIAN_LEADING_FACE_PRESSURE_BOUND");
		flags.add("VACUUM_TRAILING_FACE_PRESSURE_BOUND");
		flags.add("TRAILING_EDGE_BASE_PRESSURE_EXCLUDED");
		return new Result(true, force, coefficient, pressureBoundCd,
				coefficient < linearizedCd, flags,
				reason + "_LINEARIZED_PROFILE_DRAG_FALLBACK");
	}

	public record Result(boolean valid, double axialForceN,
			double dragCoefficient, double pressureBoundCoefficient,
			boolean pressureBoundActive, List<String> validityFlags,
			String fallbackReason) {
		public Result {
			validityFlags = List.copyOf(validityFlags);
			if (!Double.isFinite(axialForceN)
					|| !Double.isFinite(dragCoefficient)
					|| !Double.isFinite(pressureBoundCoefficient)
					|| axialForceN < 0 || dragCoefficient < 0
					|| pressureBoundCoefficient < 0) {
				throw new IllegalArgumentException(
						"invalid symmetric-section fallback result");
			}
		}
	}
}
