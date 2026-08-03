package info.openrocket.core.aerodynamics.physicsaero.geometry;

import java.util.ArrayList;
import java.util.List;

import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;

/**
 * Geometry-owned drag for rail buttons and launch lugs.
 *
 * <p>Rail-button cross-flow drag uses the circular-cylinder Mach correlation
 * digitized from Gowen and Perkins, NACA TN 2960, figure 7.  The local dynamic
 * pressure is reduced when the button is partly immersed in the turbulent body
 * boundary layer.  Launch-lug drag is the sum of annular fore/aft pressure drag
 * and Darcy-Weisbach loss through the bore.  These terms are intentionally
 * separate from vehicle skin friction and body base drag.
 */
public final class ProtuberanceDragModel {
	public static final String RAIL_BUTTON_METHOD_ID =
			"NACA_TN_2960_CIRCULAR_PROTUBERANCE_V1";
	public static final String RASAERO_RAIL_GUIDE_METHOD_ID =
			"RASAERO_II_1_0_2_RAIL_GUIDE_EMPIRICAL_V1";
	public static final String LAUNCH_LUG_METHOD_ID =
			"ANNULAR_LUG_PRESSURE_DARCY_WEISBACH_V1";

	private static final double MINIMUM_COEFFICIENT_MACH = 0.05;
	private static final double[] CYLINDER_MACH = {
			0.0, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 1.0, 1.6, 2.0, 2.8, 100.0
	};
	private static final double[] CYLINDER_CD = {
			1.20, 1.22, 1.25, 1.30, 1.40, 1.50, 1.60, 2.10, 1.50, 1.45, 1.33, 1.33
	};

	public record ComponentDrag(String componentId, double pressureCd,
			double internalFlowCd, String methodId, List<String> validityFlags,
			double confidence, double relativeUncertainty) {
		public ComponentDrag {
			if (componentId == null || componentId.isBlank()
					|| pressureCd < 0 || internalFlowCd < 0
					|| !Double.isFinite(pressureCd + internalFlowCd)
					|| methodId == null || methodId.isBlank()
					|| confidence < 0 || confidence > 1
					|| relativeUncertainty < 0) {
				throw new IllegalArgumentException("invalid protuberance drag result");
			}
			validityFlags = List.copyOf(validityFlags);
		}

		public double totalCd() {
			return pressureCd + internalFlowCd;
		}
	}

	public record Result(List<ComponentDrag> components) {
		public Result {
			components = List.copyOf(components);
		}

		public double totalCd() {
			return components.stream().mapToDouble(ComponentDrag::totalCd).sum();
		}

		public double pressureCd() {
			return components.stream().mapToDouble(ComponentDrag::pressureCd).sum();
		}

		public double internalFlowCd() {
			return components.stream().mapToDouble(ComponentDrag::internalFlowCd).sum();
		}
	}

	public Result evaluate(AeroGeometry geometry, FlowCondition flow) {
		if (geometry == null || flow == null) {
			throw new IllegalArgumentException("geometry and flow are required");
		}
		double referenceArea = geometry.references().referenceAreaM2();
		if (!(referenceArea > 0)) {
			throw new IllegalArgumentException("positive reference area required");
		}
		List<ComponentDrag> components = new ArrayList<>();
		for (AeroComponent component : geometry.components()) {
			ProtuberanceGeometry protuberance = component.protuberanceGeometry();
			if (protuberance == null) {
				continue;
			}
			components.add(switch (protuberance.type()) {
				case "RASAERO_RAIL_GUIDE" ->
						rasaeroRailGuide(component, protuberance, flow, referenceArea);
				case "RAIL_BUTTON", "LAUNCH_SHOE" ->
						railButton(component, protuberance, flow, referenceArea);
				case "LAUNCH_LUG" -> launchLug(component, protuberance, flow, referenceArea);
				default -> throw new IllegalArgumentException(
						"UNSUPPORTED_PROTUBERANCE_DRAG_TYPE:" + protuberance.type());
			});
		}
		return new Result(components);
	}

	private ComponentDrag railButton(AeroComponent component,
			ProtuberanceGeometry geometry, FlowCondition flow, double referenceArea) {
		double analysisMach = Math.max(MINIMUM_COEFFICIENT_MACH, flow.mach());
		double dynamicPressureRatio = boundaryLayerDynamicPressureRatio(
				geometry.axialPositionM(), geometry.heightM(), analysisMach, flow);
		double localMach = analysisMach * Math.sqrt(dynamicPressureRatio);
		double sectionCd = interpolate(CYLINDER_MACH, CYLINDER_CD, localMach);
		double pressureCd = sectionCd * dynamicPressureRatio
				* geometry.projectedAreaM2() / referenceArea;
		List<String> validity = new ArrayList<>(List.of(
				"CIRCULAR_CYLINDER_CROSS_FLOW",
				"TURBULENT_ONE_SEVENTH_POWER_BOUNDARY_LAYER_EXPOSURE",
				"ACTUAL_SOLID_PROJECTED_AREA",
				"INSTANCE_WAKE_SHIELDING_NOT_MODELED"));
		if (flow.mach() < MINIMUM_COEFFICIENT_MACH) {
			validity.add("LOW_SPEED_COEFFICIENT_FLOOR_MACH_0P05");
		}
		return new ComponentDrag(component.id(), pressureCd, 0,
				RAIL_BUTTON_METHOD_ID, validity, 0.68, 0.32);
	}

	/**
	 * RASAero II v1.0.2.0 rail-guide correlation.  RASAero defines the
	 * geometry as diameter times total height for one guide and states that the
	 * resulting drag represents two guides.  The piecewise coefficient is the
	 * program's published-model implementation; the 0.90--1.05 gap is joined
	 * with a monotone smoothstep because RASAero reports only blended total drag
	 * in that transonic interval.
	 */
	private ComponentDrag rasaeroRailGuide(AeroComponent component,
			ProtuberanceGeometry geometry, FlowCondition flow, double referenceArea) {
		double mach = Math.max(0, flow.mach());
		double sectionCoefficient;
		if (mach <= 0.7) {
			sectionCoefficient = 4.5 * 1.12
					/ Math.sqrt(Math.max(1.0e-12, 1 - mach * mach));
		} else if (mach <= 0.8) {
			sectionCoefficient = 4.5 * (1.57 - 0.7 * (mach - 0.7));
		} else if (mach <= 0.9) {
			sectionCoefficient = 4.5 * (1.5 + 6 * (mach - 0.8));
		} else if (mach < 1.05) {
			double below = 4.5 * (1.5 + 6 * (0.9 - 0.8));
			double above = 4.5 * (4.715 - 3.7 * 1.05
					+ 0.986 * 1.05 * 1.05);
			double fraction = (mach - 0.9) / (1.05 - 0.9);
			double smooth = fraction * fraction * (3 - 2 * fraction);
			sectionCoefficient = below + smooth * (above - below);
		} else if (mach <= 1.8) {
			sectionCoefficient = 4.5 * (4.715 - 3.7 * mach
					+ 0.986 * mach * mach);
		} else {
			sectionCoefficient = 5.625;
		}
		double pressureCd = sectionCoefficient * geometry.projectedAreaM2()
				/ referenceArea;
		return new ComponentDrag(component.id(), pressureCd, 0,
				RASAERO_RAIL_GUIDE_METHOD_ID,
				List.of("RASAERO_DIAMETER_TIMES_TOTAL_HEIGHT_INPUT",
						"TWO_RAIL_GUIDES_EMBEDDED_IN_EMPIRICAL_COEFFICIENT",
						"RASAERO_TRANSONIC_ENDPOINT_BLEND"),
				0.82, 0.18);
	}

	private ComponentDrag launchLug(AeroComponent component,
			ProtuberanceGeometry geometry, FlowCondition flow, double referenceArea) {
		double analysisMach = Math.max(MINIMUM_COEFFICIENT_MACH, flow.mach());
		double dynamicPressureRatio = boundaryLayerDynamicPressureRatio(
				geometry.axialPositionM(), geometry.heightM(), analysisMach, flow);
		double gamma = flow.thermodynamics().gamma(flow.atmosphere().temperatureK());
		double foreCp = stagnationPressureCoefficient(analysisMach, gamma);
		double baseCpMagnitude = basePressureMagnitude(analysisMach);
		double pressureCd = 0.7 * (foreCp + baseCpMagnitude)
				* dynamicPressureRatio * geometry.frontalAreaM2() / referenceArea;

		double innerAreaEach = component.localReferences().getOrDefault("innerAreaM2", 0.0);
		double innerDiameter = component.localReferences().getOrDefault("innerDiameterM", 0.0);
		double internalCd = 0;
		if (innerAreaEach > 0 && innerDiameter > 0) {
			double localSpeed = analysisMach
					* flow.thermodynamics().speedOfSound(flow.atmosphere().temperatureK())
					* Math.sqrt(dynamicPressureRatio);
			double reynoldsDiameter = flow.atmosphere().densityKgM3()
					* localSpeed * innerDiameter
					/ flow.atmosphere().dynamicViscosityPaS();
			double frictionFactor = darcyFrictionFactor(reynoldsDiameter,
					component.roughnessM(), innerDiameter);
			internalCd = frictionFactor * geometry.lengthM() / innerDiameter
					* innerAreaEach * geometry.count() / referenceArea
					* dynamicPressureRatio;
		}
		List<String> validity = new ArrayList<>(List.of(
				"ANNULAR_FORE_AND_AFT_PRESSURE_DRAG",
				"DARCY_WEISBACH_INTERNAL_BORE_LOSS",
				"TURBULENT_ONE_SEVENTH_POWER_BOUNDARY_LAYER_EXPOSURE",
				"INSTANCE_WAKE_SHIELDING_NOT_MODELED"));
		if (flow.mach() < MINIMUM_COEFFICIENT_MACH) {
			validity.add("LOW_SPEED_COEFFICIENT_FLOOR_MACH_0P05");
		}
		return new ComponentDrag(component.id(), pressureCd, internalCd,
				LAUNCH_LUG_METHOD_ID, validity, 0.60, 0.40);
	}

	static double boundaryLayerDynamicPressureRatio(double xM, double heightM,
			double analysisMach, FlowCondition flow) {
		if (!(heightM > 0) || !(analysisMach > 0)) {
			return 1;
		}
		double speed = analysisMach
				* flow.thermodynamics().speedOfSound(flow.atmosphere().temperatureK());
		double x = Math.max(xM, 1.0e-6);
		double reynoldsX = flow.atmosphere().densityKgM3() * speed * x
				/ flow.atmosphere().dynamicViscosityPaS();
		if (!(reynoldsX > 0)) {
			return 1;
		}
		double thickness = 0.37 * x / Math.pow(reynoldsX, 0.2);
		if (!(thickness > 0)) {
			return 1;
		}
		// Mean of (u/U)^2 for the turbulent one-seventh-power velocity profile.
		if (heightM <= thickness) {
			return (7.0 / 9.0) * Math.pow(heightM / thickness, 2.0 / 7.0);
		}
		return 1.0 - (2.0 / 9.0) * thickness / heightM;
	}

	static double stagnationPressureCoefficient(double mach, double gamma) {
		if (!(mach > 0) || !(gamma > 1)
				|| !Double.isFinite(mach + gamma)) {
			throw new IllegalArgumentException("invalid stagnation-pressure state");
		}
		double totalToStatic;
		if (mach <= 1) {
			totalToStatic = Math.pow(1 + 0.5 * (gamma - 1) * mach * mach,
					gamma / (gamma - 1));
		} else {
			double pressureRatio = 1 + 2 * gamma / (gamma + 1)
					* (mach * mach - 1);
			double downstreamMachSquared =
					(1 + 0.5 * (gamma - 1) * mach * mach)
					/ (gamma * mach * mach - 0.5 * (gamma - 1));
			totalToStatic = pressureRatio * Math.pow(
					1 + 0.5 * (gamma - 1) * downstreamMachSquared,
					gamma / (gamma - 1));
		}
		return (totalToStatic - 1) / (0.5 * gamma * mach * mach);
	}

	static double basePressureMagnitude(double mach) {
		double subsonic = Math.min(0.3, 0.12 + 0.13 * mach * mach);
		if (mach <= 0.85) {
			return subsonic;
		}
		double supersonic = 0.25 / Math.max(1, mach);
		if (mach >= 1.3) {
			return supersonic;
		}
		double fraction = (mach - 0.85) / (1.3 - 0.85);
		double smooth = fraction * fraction * (3 - 2 * fraction);
		return (1 - smooth) * subsonic + smooth * supersonic;
	}

	static double darcyFrictionFactor(double reynolds, double roughnessM,
			double diameterM) {
		if (!(reynolds > 0) || roughnessM < 0 || !(diameterM > 0)) {
			throw new IllegalArgumentException("invalid internal-flow state");
		}
		if (reynolds < 2300) {
			return 64 / reynolds;
		}
		double relativeRoughness = roughnessM / diameterM;
		double denominator = Math.log10(relativeRoughness / 3.7
				+ 5.74 / Math.pow(reynolds, 0.9));
		return 0.25 / (denominator * denominator);
	}

	private static double interpolate(double[] domain, double[] range, double value) {
		for (int index = 1; index < domain.length; index++) {
			if (value <= domain[index]) {
				double fraction = (value - domain[index - 1])
						/ (domain[index] - domain[index - 1]);
				return range[index - 1] + fraction * (range[index] - range[index - 1]);
			}
		}
		return range[range.length - 1];
	}
}
