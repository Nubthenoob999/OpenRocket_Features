package info.openrocket.core.correlation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AxisymmetricProfile;
import info.openrocket.core.aerodynamics.physicsaero.geometry.FinGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.util.Coordinate;

/** Source-derived, test-only geometry and atmosphere fixtures. */
final class RasaeroBenchmarkFixtures {
	static final PerfectGasAir AIR = new PerfectGasAir();
	static final double TEMPERATURE_K = 288.15;
	static final double INCH_M = 0.0254;
	static final double FOOT_M = 0.3048;
	static final double ARCAS_REYNOLDS_PER_M = 3_000_000.0 / FOOT_M;
	static final String ARCAS_GEOMETRY_LIMITATION =
			"ARCAS_REFLEX_LIP_AND_FIN_ANCHOR_PROTUBERANCES_UNREPRESENTED";

	private static final double A53_DIAMETER_M = 0.250 * INCH_M;
	private static final double A53_RADIUS_M = A53_DIAMETER_M / 2;
	private static final double A53_LENGTH_M = 2.50 * INCH_M;
	private static final double A53_NOSE_LENGTH_M = 1.00 * INCH_M;

	private static final double ARCAS_DIAMETER_M = 2.25 * INCH_M;
	private static final double ARCAS_RADIUS_M = ARCAS_DIAMETER_M / 2;
	private static final double ARCAS_NOSE_LENGTH_M = 4.71 * ARCAS_DIAMETER_M;
	private static final double ARCAS_TAIL_LENGTH_M = 1.78 * ARCAS_DIAMETER_M;
	// D-4014 figure 1 gives a 1.470-inch boattail-base diameter.  The nearby
	// 0.654-inch callout belongs to detail A and is not the external base radius.
	private static final double ARCAS_BASE_RADIUS_M = 0.5 * 1.470 * INCH_M;
	private static final double ARCAS_REFERENCE_AREA_M2 = 3.976 * INCH_M * INCH_M;

	private RasaeroBenchmarkFixtures() {
	}

	static double a53LengthM() {
		return A53_LENGTH_M;
	}

	static AtmosphereState standardAtmosphere() {
		return atmosphereFromDensity(101325.0 / (AIR.gasConstant() * TEMPERATURE_K));
	}

	static AtmosphereState reynoldsMatchedTotal(double mach, double reynolds, double vehicleLengthM) {
		double density = reynolds * AIR.viscosity(TEMPERATURE_K)
				/ (mach * AIR.speedOfSound(TEMPERATURE_K) * vehicleLengthM);
		return atmosphereFromDensity(density);
	}

	static AtmosphereState reynoldsMatchedPerMeter(double mach, double reynoldsPerMeter) {
		double density = reynoldsPerMeter * AIR.viscosity(TEMPERATURE_K)
				/ (mach * AIR.speedOfSound(TEMPERATURE_K));
		return atmosphereFromDensity(density);
	}

	private static AtmosphereState atmosphereFromDensity(double density) {
		double pressure = density * AIR.gasConstant() * TEMPERATURE_K;
		return new AtmosphereState(pressure, TEMPERATURE_K, density, AIR.viscosity(TEMPERATURE_K));
	}

	static AeroGeometry a53d02() {
		AxisymmetricProfile noseProfile = tangentOgiveProfile(A53_NOSE_LENGTH_M, A53_RADIUS_M, 64,
				"SOURCE_TANGENT_OGIVE");
		AxisymmetricProfile tubeProfile = linearProfile(A53_NOSE_LENGTH_M, A53_RADIUS_M,
				A53_LENGTH_M, A53_RADIUS_M, "SOURCE_EXACT_CYLINDER");
		AeroComponent nose = body("nose", "NOSE_OGIVE", noseProfile, A53_RADIUS_M,
				Map.of("transitionReynolds", 1_600_000.0, "transitionMachMinimum", 1.5,
						"wallTemperatureK", TEMPERATURE_K), "ISOTHERMAL_COMPONENT", 0);
		AeroComponent tube = body("tube", "CYLINDER", tubeProfile, A53_RADIUS_M,
				Map.of("transitionReynolds", 1_600_000.0, "transitionMachMinimum", 1.5,
						"wallTemperatureK", TEMPERATURE_K), "ISOTHERMAL_COMPONENT", 1);

		double root = 0.75 * INCH_M;
		double tip = 0.375 * INCH_M;
		double span = (0.500 * INCH_M - A53_DIAMETER_M) / 2;
		double sweep = root - tip;
		AeroComponent fins = fins("fins", A53_LENGTH_M - root, A53_RADIUS_M, root, tip,
				span, sweep, 0, "SINGLE_WEDGE", 0.04, 2);
		Map<String, Double> wetted = wetted(nose, tube, fins);
		ReferenceGeometry references = new ReferenceGeometry(Math.PI * A53_RADIUS_M * A53_RADIUS_M,
				Math.PI * A53_RADIUS_M * A53_RADIUS_M, A53_LENGTH_M, A53_DIAMETER_M, wetted,
				new Coordinate(), A53_DIAMETER_M);
		return new AeroGeometry(List.of(nose, tube, fins), references, "naca-rm-a53d02-figure-1");
	}

	static AeroGeometry arcasShort(double finCantDeg, boolean finsPresent, boolean d4014Origins) {
		return arcas(18.20, finCantDeg, finsPresent, d4014Origins ? 0.6337 : 0.70, "short");
	}

	static AeroGeometry arcasLong(double finCantDeg, boolean finsPresent, boolean d4014Origins) {
		return arcas(23.77, finCantDeg, finsPresent, d4014Origins ? 0.6636 : 0.70, "long");
	}

	private static AeroGeometry arcas(double fineness, double finCantDeg, boolean finsPresent,
			double momentFraction, String configuration) {
		double length = fineness * ARCAS_DIAMETER_M;
		double cylinderEnd = length - ARCAS_TAIL_LENGTH_M;
		AxisymmetricProfile noseProfile = arcasOgiveProfile();
		AxisymmetricProfile cylinderProfile = linearProfile(ARCAS_NOSE_LENGTH_M, ARCAS_RADIUS_M,
				cylinderEnd, ARCAS_RADIUS_M, "SOURCE_EXACT_CYLINDER");
		AxisymmetricProfile tailProfile = linearProfile(cylinderEnd, ARCAS_RADIUS_M, length,
				ARCAS_BASE_RADIUS_M, "SOURCE_CONICAL_BOATTAIL");
		AeroComponent nose = body("nose", "NOSE_OGIVE", noseProfile, ARCAS_RADIUS_M,
				Map.of(), "ADIABATIC", 0);
		AeroComponent cylinder = body("centerbody", "CYLINDER", cylinderProfile, ARCAS_RADIUS_M,
				Map.of(), "ADIABATIC", 1);
		AeroComponent tail = body("boattail", "BOATTAIL", tailProfile, ARCAS_RADIUS_M,
				Map.of(), "ADIABATIC", 2);
		List<AeroComponent> components = new ArrayList<>(List.of(nose, cylinder, tail));
		if (finsPresent) {
			double root = 3.380 * INCH_M;
			double tip = 2.165 * INCH_M;
			double span = (2.103 - 1.125) * INCH_M;
			components.add(fins("fins", length - root, ARCAS_RADIUS_M, root, tip, span,
					0.500 * INCH_M, Math.toRadians(finCantDeg), "DOUBLE_WEDGE", 0.04, 3));
		}
		Map<String, Double> wetted = new LinkedHashMap<>();
		components.forEach(component -> wetted.put(component.id(), component.wettedAreaM2()));
		ReferenceGeometry references = new ReferenceGeometry(ARCAS_REFERENCE_AREA_M2,
				Math.PI * ARCAS_BASE_RADIUS_M * ARCAS_BASE_RADIUS_M, length, ARCAS_DIAMETER_M,
				wetted, new Coordinate(momentFraction * length, 0, 0), ARCAS_DIAMETER_M);
		return new AeroGeometry(components, references, "nasa-arcas-" + configuration + "-cant-"
				+ finCantDeg + "-fins-" + finsPresent + "-origin-" + momentFraction);
	}

	static AeroGeometry l54d27() {
		double length = 66.11 * INCH_M;
		// NACA RM L54D27 figure 1 supplies these dimensions directly; the stated
		// 7.87 fineness ratio is the rounded ratio of 66.11 to 8.40 inches.
		double diameter = 8.40 * INCH_M;
		double radius = diameter / 2;
		double noseLength = 23.82 * INCH_M;
		double baseRadius = 0.5 * 5.60 * INCH_M;
		double boattailLength = (radius - baseRadius) / Math.tan(Math.toRadians(10));
		double boattailStart = length - boattailLength;
		AxisymmetricProfile noseProfile = linearProfile(0, 0, noseLength, radius, "SOURCE_10_DEG_CONE");
		AxisymmetricProfile cylinderProfile = linearProfile(noseLength, radius, boattailStart, radius,
				"SOURCE_EXACT_CYLINDER");
		AxisymmetricProfile tailProfile = linearProfile(boattailStart, radius, length, baseRadius,
				"SOURCE_10_DEG_BOATTAIL");
		AeroComponent nose = body("nose", "NOSE_CONICAL", noseProfile, radius,
				Map.of("noseHalfAngleDeg", 10.0), "ADIABATIC", 0);
		AeroComponent cylinder = body("centerbody", "CYLINDER", cylinderProfile, radius,
				Map.of("jetExitMach", 2.91), "ADIABATIC", 1);
		AeroComponent tail = body("boattail", "BOATTAIL", tailProfile, radius,
				Map.of("boattailHalfAngleDeg", 10.0, "jetExitMach", 2.91), "ADIABATIC", 2);
		double finRoot = 6.20 * INCH_M;
		double finSpan = (0.5 * 12.00 - 0.5 * 8.40) * INCH_M;
		// The production fin representation requires a nonzero tip chord; one
		// micron represents the report's 60-degree delta apex without changing area.
		double deltaApexChord = 1e-6;
		AeroComponent fins = fins("fins", length - finRoot, radius, finRoot, deltaApexChord,
				finSpan, finRoot - deltaApexChord, 0, "DOUBLE_WEDGE", 0.04, 3);
		Map<String, Double> wetted = wetted(nose, cylinder, tail, fins);
		ReferenceGeometry reference = new ReferenceGeometry(Math.PI * radius * radius,
				Math.PI * baseRadius * baseRadius, length, diameter, wetted,
				new Coordinate(0.60 * length, 0, 0), diameter);
		return new AeroGeometry(List.of(nose, cylinder, tail, fins), reference,
				"naca-rm-l54d27-powered-model");
	}

	private static AxisymmetricProfile arcasOgiveProfile() {
		double[] xIn = {0, 2.375, 3.375, 4.375, 5.375, 6.375, 7.375, 8.375, 9.375, 10.600};
		double[] rIn = {0, 0.414, 0.554, 0.688, 0.804, 0.900, 0.988, 1.062, 1.125, 1.125};
		List<GeometryStation> stations = new ArrayList<>();
		for (int index = 0; index < xIn.length; index++) {
			double slope;
			if (index == 0) slope = (rIn[1] - rIn[0]) / (xIn[1] - xIn[0]);
			else if (index == xIn.length - 1) slope = 0;
			else slope = (rIn[index + 1] - rIn[index - 1]) / (xIn[index + 1] - xIn[index - 1]);
			stations.add(new GeometryStation(xIn[index] * INCH_M, rIn[index] * INCH_M, slope, 0));
		}
		return new AxisymmetricProfile(stations, List.of(), "SOURCE_TABULATED_OGIVE_STATIONS", 1e-9);
	}

	private static AxisymmetricProfile tangentOgiveProfile(double length, double radius, int intervals,
			String method) {
		double rho = (length * length + radius * radius) / (2 * radius);
		List<GeometryStation> stations = new ArrayList<>();
		for (int index = 0; index <= intervals; index++) {
			double x = length * index / intervals;
			double u = length - x;
			double root = Math.sqrt(Math.max(1e-30, rho * rho - u * u));
			stations.add(new GeometryStation(x, Math.max(0, root + radius - rho), u / root,
					-rho * rho / (root * root * root)));
		}
		return new AxisymmetricProfile(stations, List.of(), method, 1e-12);
	}

	private static AxisymmetricProfile linearProfile(double startX, double startRadius, double endX,
			double endRadius, String method) {
		double slope = (endRadius - startRadius) / (endX - startX);
		return new AxisymmetricProfile(List.of(new GeometryStation(startX, startRadius, slope, 0),
				new GeometryStation(endX, endRadius, slope, 0)), List.of(), method, 1e-12);
	}

	private static AeroComponent body(String id, String classification, AxisymmetricProfile profile,
			double rootRadius, Map<String, Double> local, String wallModel, int order) {
		List<GeometryStation> stations = profile.stations();
		double start = stations.get(0).xM();
		double end = stations.get(stations.size() - 1).xM();
		double endRadius = stations.get(stations.size() - 1).radiusM();
		return new AeroComponent(id, "/" + id, "benchmark", classification, "stage", order,
				new Coordinate(start, 0, 0), start, end, rootRadius, profile.wettedAreaM2(),
				2 * rootRadius * (end - start), Math.PI * endRadius * endRadius, 0,
				wallModel, local, List.of(), profile, null, null);
	}

	private static AeroComponent fins(String id, double start, double bodyRadius, double root,
			double tip, double span, double sweep, double cantRad, String section,
			double thicknessRatio, int order) {
		double area = 0.5 * (root + tip) * span;
		List<GeometryStation> outline = List.of(new GeometryStation(0, 0, 0, 0),
				new GeometryStation(sweep, span, 0, 0),
				new GeometryStation(sweep + tip, span, 0, 0),
				new GeometryStation(root, 0, 0, 0));
		FinGeometry fin = new FinGeometry("TRAPEZOIDAL", section, 4, root, span, area, cantRad, outline);
		return new AeroComponent(id, "/" + id, "benchmark", "FIN_TRAPEZOIDAL", "stage", order,
				new Coordinate(start, 0, 0), start, start + root, bodyRadius, 2 * area * fin.count(),
				area * fin.count(), 0, 0, "ADIABATIC",
				Map.of("thicknessRatio", thicknessRatio,
						"thicknessM", thicknessRatio * 0.5 * (root + tip),
						"baseRotationRad", 0.0), List.of(), null, fin, null);
	}

	private static Map<String, Double> wetted(AeroComponent... components) {
		Map<String, Double> result = new LinkedHashMap<>();
		for (AeroComponent component : components) result.put(component.id(), component.wettedAreaM2());
		return result;
	}
}
