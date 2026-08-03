package info.openrocket.core.aerodynamics.physicsaero.coupling;

import java.util.Comparator;
import java.util.List;

import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;

/**
 * Bounded finned base-wake closure on the actual pressure-loaded base area.
 *
 * <p>The smooth-body baseline {@code 0.064 + 0.186/M^2} is the empirical
 * correlation independently checked against the turbulent NACA TN 3393 base
 * pressure points.  The 0.55 four-fin increment is the Basic Finner
 * (ADA636861) component-scale anchor.  Below the direct TN 3393 Mach range the
 * smooth-body baseline is joined to the measured Hart NACA RM L52E06
 * transonic base-pressure endpoint; that extrapolation is explicitly flagged.
 *
 * <p>The Basic Finner anchor has a flat cylindrical afterbody.  No independent
 * source supports transporting its wake increment across a terminal boattail
 * or an expanding fin-can sleeve.  Those topologies therefore retain the
 * primary body-base and boattail correlations but receive no Basic Finner
 * increment.  The same source-domain rule prevents transporting the four-fin
 * result to a three-fin vehicle.
 *
 * <p>The result is an increment relative to the base suction already present
 * in the caller.  This prevents double counting the independent A53D02
 * fin/body interaction envelope.
 */
public final class FinnedBasePressureClosureModel {
	public static final String METHOD_ID = "TN3393_ADA636861_FINNED_BASE_PRESSURE_CLOSURE_V1";
	public static final String SOURCE =
			"NACA TN 3393 turbulent base pressure; NACA RM L52E06 transonic base pressure; "
			+ "ADA636861 Basic Finner zero-yaw axial force";

	static final double BASIC_FINNER_STRENGTH = 0.55;
	private static final double TN3393_MINIMUM_MACH = 2.73;
	private static final double SUPERSONIC_HANDOFF_MACH = 1.50;
	private static final double MAXIMUM_MACH = 5.0;

	public record Result(double currentPressureMagnitude,
			double targetPressureMagnitude, double pressureMagnitudeIncrement,
			double smoothBodyPressureMagnitude, int finCount,
			double spanToBaseRadius, double machInfluence,
			boolean expandingFinCanSleeve, double confidence,
			double uncertainty, List<String> validityFlags) {
		public Result {
			validityFlags = List.copyOf(validityFlags);
			if (!Double.isFinite(currentPressureMagnitude)
					|| !Double.isFinite(targetPressureMagnitude)
					|| !Double.isFinite(pressureMagnitudeIncrement)
					|| !Double.isFinite(smoothBodyPressureMagnitude)
					|| !Double.isFinite(spanToBaseRadius)
					|| !Double.isFinite(machInfluence)
					|| currentPressureMagnitude < 0 || targetPressureMagnitude < 0
					|| pressureMagnitudeIncrement < 0 || smoothBodyPressureMagnitude < 0
					|| finCount < 0 || spanToBaseRadius < 0
					|| machInfluence < 0 || machInfluence > 1
					|| confidence < 0 || confidence > 1
					|| uncertainty < 0) {
				throw new IllegalArgumentException("invalid finned-base closure result");
			}
		}
	}

	public Result evaluate(AeroGeometry geometry, double mach,
			double currentPressureMagnitude) {
		if (geometry == null || !Double.isFinite(mach)
				|| !Double.isFinite(currentPressureMagnitude)
				|| mach < 0.70 || mach > MAXIMUM_MACH
				|| currentPressureMagnitude < 0) {
			throw new IllegalArgumentException("invalid finned-base closure state");
		}

		double baseRadius = Math.sqrt(
				geometry.references().exposedBaseAreaM2() / Math.PI);
		double terminalBodyEnd = terminalBodyEndM(geometry);
		double proximity = Math.max(0.05,
				1.25 * geometry.references().maximumBodyDiameterM());
		int finCount = 0;
		double maximumSpan = 0;
		for (AeroComponent component : geometry.components()) {
			if (component.finGeometry() == null) continue;
			if ("ROUNDED_LEADING_EDGE".equals(
					component.finGeometry().section())) continue;
			double gap = terminalBodyEnd - component.axialEndM();
			if (gap < -1e-9 || gap > proximity) continue;
			finCount += component.finGeometry().count();
			maximumSpan = Math.max(maximumSpan,
					component.finGeometry().spanM());
		}
		double spanToBaseRadius = baseRadius > 0 ? maximumSpan / baseRadius : 0;
		double machInfluence = machInfluence(mach);
		boolean expandingSleeve =
				hasTerminalExpandingFinCanSleeve(geometry);
		boolean flatCylindricalAfterbody =
				hasTerminalFlatCylindricalAfterbody(geometry);

		if (finCount < 4
				|| !(spanToBaseRadius > 0)
				|| !(machInfluence > 0)
				|| expandingSleeve
				|| !flatCylindricalAfterbody) {
			java.util.ArrayList<String> flags = new java.util.ArrayList<>();
			if (finCount < 4 || !(spanToBaseRadius > 0)
					|| !(machInfluence > 0)) {
				flags.add("FOUR_FIN_SOURCE_TOPOLOGY_NOT_MET");
			}
			if (expandingSleeve) {
				flags.add("EXPANDING_FIN_CAN_SLEEVE_OUTSIDE_BASIC_FINNER_SOURCE_TOPOLOGY");
			}
			if (!flatCylindricalAfterbody) {
				flags.add("TERMINAL_NONCYLINDRICAL_AFTERBODY_OUTSIDE_BASIC_FINNER_SOURCE_TOPOLOGY");
			}
			return new Result(currentPressureMagnitude,
					currentPressureMagnitude, 0,
					smoothBodyPressureMagnitude(mach), finCount,
					spanToBaseRadius, machInfluence, expandingSleeve,
					0, 1, flags);
		}

		double fourFinAnchor = 1 - Math.exp(-4.0 / 1.4);
		double finInfluence = (1 - Math.exp(-finCount / 1.4))
				/ fourFinAnchor;
		finInfluence = clamp(finInfluence, 0, 1.25);
		double spanInfluence = clamp(spanToBaseRadius, 0.3, 1);
		double smoothBody = smoothBodyPressureMagnitude(mach);
		double target = smoothBody * (1
				+ BASIC_FINNER_STRENGTH * finInfluence
						* spanInfluence * machInfluence);
		double increment = Math.max(0, target - currentPressureMagnitude);

		java.util.ArrayList<String> flags = new java.util.ArrayList<>();
		flags.add("ACTUAL_PRESSURE_LOADED_BASE_AREA_ONLY");
		flags.add("FOUR_FIN_BASIC_FINNER_TOPOLOGY");
		flags.add("FIN_COUNT_INFLUENCE_SATURATED");
		if (mach < TN3393_MINIMUM_MACH) {
			flags.add("TN3393_BASELINE_EXTRAPOLATED_BELOW_DIRECT_MACH_RANGE");
		} else {
			flags.add("TN3393_TURBULENT_BASELINE_DIRECT_MACH_RANGE");
		}
		double confidence = mach >= TN3393_MINIMUM_MACH ? 0.35 : 0.25;
		flags.add("ADA636861_BASIC_FINNER_WAKE_SCALE");
		if (increment == 0) {
			flags.add("EXISTING_BASE_SUCTION_MEETS_OR_EXCEEDS_CLOSURE_TARGET");
		}
		return new Result(currentPressureMagnitude, target, increment,
				smoothBody, finCount, spanToBaseRadius, machInfluence,
				expandingSleeve, confidence, 1 - confidence, flags);
	}

	static double smoothBodyPressureMagnitude(double mach) {
		if (mach < 0.70 || mach > MAXIMUM_MACH || !Double.isFinite(mach)) {
			throw new IllegalArgumentException("outside base-pressure closure range");
		}
		if (mach <= SUPERSONIC_HANDOFF_MACH) {
			// Hart owns through M=1.30, followed by the C1 source handoff.
			return new info.openrocket.core.aerodynamics.physicsaero.transonic.TransonicBaseDragModel()
					.basePressureCoefficient(mach, 0) * -1;
		}
		double empirical = tn3393Correlation(mach);
		return empirical;
	}

	private static double tn3393Correlation(double mach) {
		return 0.064 + 0.186 / (mach * mach);
	}

	private static double machInfluence(double mach) {
		if (mach < 0.8) {
			return 0.30 * clamp((mach - 0.2) / 0.6, 0, 1);
		}
		if (mach < 1.3) {
			return 0.30 + 0.70 * (mach - 0.8) / 0.5;
		}
		return mach < 3.0 ? 1 : 3.0 / mach;
	}

	private static boolean hasTerminalExpandingFinCanSleeve(
			AeroGeometry geometry) {
		List<AeroComponent> bodies = geometry.components().stream()
				.filter(component -> component.axisymmetricProfile() != null)
				.sorted(Comparator.comparingDouble(AeroComponent::axialStartM))
				.toList();
		if (bodies.size() < 2) return false;
		AeroComponent sleeve = bodies.get(bodies.size() - 1);
		AeroComponent shoulder = bodies.get(bodies.size() - 2);
		if (!"CYLINDER".equals(sleeve.classification())
				|| !"TRANSITION".equals(shoulder.classification())
				|| shoulder.axialEndM() < sleeve.axialStartM() - 1e-6
				|| shoulder.axialEndM() > sleeve.axialStartM() + 1e-6
				|| shoulder.axialEndM() - shoulder.axialStartM() > 0.035) {
			return false;
		}
		double shoulderFore = endpointRadius(shoulder, true);
		double shoulderAft = endpointRadius(shoulder, false);
		double sleeveFore = endpointRadius(sleeve, true);
		if (!(shoulderAft > shoulderFore)
				|| Math.abs(shoulderAft - sleeveFore) > 0.003) {
			return false;
		}
		return geometry.components().stream()
				.filter(component -> component.finGeometry() != null)
				.anyMatch(component ->
						component.axialEndM() >= sleeve.axialStartM()
						&& component.axialStartM() <= sleeve.axialEndM()
						&& Math.abs(component.rootRadiusM() - sleeveFore) <= 0.003);
	}

	private static boolean hasTerminalFlatCylindricalAfterbody(
			AeroGeometry geometry) {
		return geometry.components().stream()
				.filter(component -> component.axisymmetricProfile() != null)
				.max(Comparator.comparingDouble(AeroComponent::axialEndM))
				.map(component -> "CYLINDER".equals(component.classification()))
				.orElse(false);
	}

	private static double terminalBodyEndM(AeroGeometry geometry) {
		return geometry.components().stream()
				.filter(component ->
						component.axisymmetricProfile() != null)
				.mapToDouble(AeroComponent::axialEndM).max()
				.orElseThrow(() -> new IllegalArgumentException(
						"missing terminal axisymmetric body"));
	}

	private static double endpointRadius(AeroComponent component,
			boolean fore) {
		List<GeometryStation> stations =
				component.axisymmetricProfile().stations();
		return fore ? stations.get(0).radiusM()
				: stations.get(stations.size() - 1).radiusM();
	}

	private static double clamp(double value, double minimum,
			double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
