package info.openrocket.core.aerodynamics.physicsaero.powered;

import java.util.List;

import info.openrocket.core.aerodynamics.physicsaero.flow.PoweredFlowState;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;

/**
 * Transonic jet-on base and boattail closure calibrated from NACA RM L54D27.
 * Inputs are dimensionless geometry and nozzle coordinates so the closure is not
 * tied to a named vehicle.
 */
public final class PoweredBaseFlowModel {
	public static final String METHOD_ID = "L54D27_POWERED_BASE_BOATTAIL_DECOMPOSITION_V2";
	public static final String SOURCE = "NACA RM L54D27, power-on minus power-off discussion at M=1.075";

	private static final double REFERENCE_EXIT_MACH = 2.91;
	private static final double REFERENCE_BASE_AREA_RATIO = 4.0 / 9.0;
	private static final double REFERENCE_BOATTAIL_ANGLE_RAD = Math.toRadians(10);
	private static final double MIN_MACH = 0.8;
	private static final double MAX_MACH = 1.2;

	public boolean supportsMach(double freestreamMach) {
		return freestreamMach >= MIN_MACH && freestreamMach <= MAX_MACH;
	}

	public PoweredFlowResult evaluate(AeroGeometry geometry, double freestreamMach,
			PoweredFlowState powered) {
		if (!powered.powered()) {
			return new PoweredFlowResult(0, 0, 0, 0, List.of(METHOD_ID), List.of("COAST_STATE"));
		}
		if (!supportsMach(freestreamMach)) {
			throw new IllegalArgumentException("OUTSIDE_L54D27_POWERED_TRANSONIC_RANGE");
		}
		if (!powered.hasPoweredClosureState()) {
			throw new IllegalArgumentException("POWERED_FLOW_STATE_UNRESOLVED");
		}
		double areaRatio = geometry.references().exposedBaseAreaM2()
				/ geometry.references().referenceAreaM2();
		double nozzleFillRatio = Math.min(1, powered.nozzleExitAreaM2()
				/ geometry.references().exposedBaseAreaM2());
		double exitMachFactor = powered.hasResolvedNozzleState()
				? powered.nozzleExitMach() / REFERENCE_EXIT_MACH : 1;
		double jetScale = nozzleFillRatio * exitMachFactor;
		double base = 0.022 * (areaRatio / REFERENCE_BASE_AREA_RATIO) * jetScale;

		double boattailAngle = geometry.components().stream()
				.filter(component -> component.classification().equals("BOATTAIL"))
				.mapToDouble(PoweredBaseFlowModel::boattailAngle).max().orElse(0);
		double boattail = 0.012 * (boattailAngle / REFERENCE_BOATTAIL_ANGLE_RAD)
				* Math.sqrt(Math.max(0, areaRatio / REFERENCE_BASE_AREA_RATIO)) * jetScale;

		// The report isolates the residual after measured base and estimated boattail
		// terms. Preserve it as a separately owned installation/plume term.
		double plume = 0.073 * jetScale
				* Math.sqrt(Math.max(0, geometry.references().maximumBodyDiameterM()
						/ geometry.references().vehicleLengthM() * 7.87));
		double development = smoothStep((freestreamMach - 0.8) / 0.275);
		double decay = smoothStep((1.2 - freestreamMach) / 0.125);
		double machWeight = freestreamMach <= 1.075 ? development : decay;
		base *= powered.poweredFraction() * machWeight;
		boattail *= powered.poweredFraction() * machWeight;
		plume *= powered.poweredFraction() * machWeight;
		List<String> validity = powered.hasResolvedNozzleState()
				? List.of("SOURCE_CALIBRATED_POWERED_FLOW", "EXPLICIT_POWERED_LEDGER",
						"NOZZLE_TO_BASE_AREA_SCALING")
				: List.of("SOURCE_CALIBRATED_POWERED_FLOW", "EXPLICIT_POWERED_LEDGER",
						"NOZZLE_TO_BASE_AREA_SCALING",
						"NOZZLE_GEOMETRY_ONLY_REFERENCE_EXIT_MACH",
						"LOW_CONFIDENCE_POWERED_FLOW");
		return new PoweredFlowResult(base + boattail + plume, base, boattail, plume,
				List.of(METHOD_ID), validity);
	}

	private static double boattailAngle(AeroComponent component) {
		Double explicit = component.localReferences().get("boattailHalfAngleDeg");
		if (explicit != null) return Math.toRadians(explicit);
		if (component.axisymmetricProfile() == null || component.axisymmetricProfile().stations().size() < 2) return 0;
		var first = component.axisymmetricProfile().stations().get(0);
		var last = component.axisymmetricProfile().stations().get(component.axisymmetricProfile().stations().size() - 1);
		return Math.atan2(Math.abs(last.radiusM() - first.radiusM()), last.xM() - first.xM());
	}

	private static double smoothStep(double value) {
		double bounded = Math.max(0, Math.min(1, value));
		return bounded * bounded * (3 - 2 * bounded);
	}
}
