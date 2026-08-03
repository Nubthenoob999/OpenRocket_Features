package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.EngineeringSkinFrictionCorrelation;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AxisymmetricProfile;
import info.openrocket.core.aerodynamics.physicsaero.geometry.FinGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.aerodynamics.physicsaero.thermal.RecoveryTemperatureModel;
import info.openrocket.core.util.Coordinate;

class EngineeringSkinFrictionCorrelationTest {
	@Test
	void transitionCompressibilityAndRoughnessMoveFrictionInPhysicalDirections() {
		EngineeringSkinFrictionCorrelation correlation = new EngineeringSkinFrictionCorrelation();
		double lateTransition = correlation.compressibleAverageCf(1_000_000, 500_000, 0,
				288.15, 288.15);
		double earlyTransition = correlation.compressibleAverageCf(1_000_000, 10_000, 0,
				288.15, 288.15);
		double compressible = correlation.compressibleAverageCf(1_000_000, 500_000, 2,
				288.15, adiabaticWallTemperature(2));

		assertTrue(earlyTransition > lateTransition);
		assertTrue(compressible < lateTransition);
		assertEquals(lateTransition, correlation.roughnessLimitedCf(lateTransition, 1, 0), 0);
		assertTrue(correlation.roughnessLimitedCf(lateTransition, 1, 1e-3) > lateTransition);
		double incompressibleRough = correlation.roughnessLimitedCf(0, 1, 1e-3, 0);
		double supersonicRough = correlation.roughnessLimitedCf(0, 1, 1e-3, 3);
		assertEquals(incompressibleRough / (1 + 0.18 * 9), supersonicRough, 1e-15);
		assertTrue(supersonicRough < incompressibleRough);
	}

	@Test
	void rasaeroCompressibilityCorrelationUsesItsPublishedPiecewiseBranches() {
		EngineeringSkinFrictionCorrelation correlation = new EngineeringSkinFrictionCorrelation();
		assertEquals(1, EngineeringSkinFrictionCorrelation.rasaeroCompressibilityFactor(1), 0);
		assertEquals(1 - 0.256 * 0.5,
				EngineeringSkinFrictionCorrelation.rasaeroCompressibilityFactor(1.5), 1e-15);
		assertEquals(1 / Math.pow(1 + 0.144 * 9, 0.65),
				EngineeringSkinFrictionCorrelation.rasaeroCompressibilityFactor(3), 1e-15);
		assertEquals(0.1691,
				EngineeringSkinFrictionCorrelation.rasaeroCompressibilityFactor(12), 0);

		double incompressible = correlation.compressibleAverageCf(
				10_000_000, 500_000, 0, 250, 250);
		double machThree = correlation.compressibleAverageCf(
				10_000_000, 500_000, 3, 250, 250);
		assertEquals(incompressible
				* EngineeringSkinFrictionCorrelation.engineeringCompressibilityFactor(3),
				machThree, 1e-15);
	}

	@Test
	void datcomToRasaeroEngineeringHandoffIsContinuousAndPreservesSubsonicBranch() {
		double machHalfDatcom = 1 / Math.pow(1 + 0.144 * 0.25, 0.65);
		assertEquals(machHalfDatcom,
				EngineeringSkinFrictionCorrelation.engineeringCompressibilityFactor(0.5), 0);
		double epsilon = 1e-7;
		for (double join : new double[] {0.9, 1.1}) {
			double left = EngineeringSkinFrictionCorrelation.engineeringCompressibilityFactor(
					join - epsilon);
			double right = EngineeringSkinFrictionCorrelation.engineeringCompressibilityFactor(
					join + epsilon);
			assertEquals(left, right, 1e-6, "skin-friction handoff at Mach " + join);
		}
	}

	@Test
	void bodyAndFinWettedAreasRemainSeparatePositiveOwners() {
		AeroGeometry geometry = geometry();
		var result = new EngineeringSkinFrictionCorrelation().evaluate(geometry, flow(0.8));

		assertTrue(result.bodyCd() > 0);
		assertTrue(result.finCd() > 0);
		assertTrue(result.finInterferenceCd() > 0);
		assertEquals(result.bodyCd() + result.finCd() + result.finInterferenceCd(),
				result.totalCd(), 0);
		assertTrue(result.bodyReynolds() > result.finReynolds());
		assertEquals(EngineeringSkinFrictionCorrelation.METHOD_ID, result.methodId());
	}

	@Test
	void virtualFinOverlapMatchesTrapezoidExtensionToBodyCenterline() {
		var result = new EngineeringSkinFrictionCorrelation().evaluate(
				geometry(), flow(0.5));
		double root = 0.2;
		double tip = 0.1;
		double span = 0.1;
		double radius = 0.05;
		double centerlineChord = root + (root - tip) * radius / span;
		double overlapArea = 0.5 * radius * (root + centerlineChord);
		double exposedArea = 0.5 * (root + tip) * span;

		assertEquals(result.finCd() * overlapArea / exposedArea,
				result.finInterferenceCd(), 1.0e-15);
	}

	@Test
	void fullyTurbulentModeRemovesTheLaminarRunFromBodyAndFinFriction() {
		Map<String, Double> turbulentBody = Map.of("forceFullyTurbulent", 1.0);
		Map<String, Double> turbulentFin = Map.of(
				"forceFullyTurbulent", 1.0, "thicknessRatio", 0.04);
		EngineeringSkinFrictionCorrelation correlation =
				new EngineeringSkinFrictionCorrelation();
		var natural = correlation.evaluate(geometry(), flow(0.8));
		var turbulent = correlation.evaluate(
				geometry(turbulentBody, turbulentFin), flow(0.8));

		assertTrue(turbulent.bodyCf() > natural.bodyCf());
		assertTrue(turbulent.finCf() > natural.finCf());
		assertTrue(turbulent.totalCd() > natural.totalCd());
		double expected = correlation.compressibleAverageCf(
				turbulent.bodyReynolds(),
				EngineeringSkinFrictionCorrelation.DEFAULT_TRANSITION_REYNOLDS,
				0.8, 288.15, adiabaticWallTemperature(0.8), true);
		assertEquals(expected, turbulent.bodyCf(), 1e-15);
	}

	@Test
	void measuredTransitionMetadataStartsOnlyInsideItsDeclaredMachRange() {
		Map<String, Double> transition = Map.of("transitionReynolds", 1_600_000.0,
				"transitionMachMinimum", 1.5, "thicknessRatio", 0.04);
		AeroGeometry measured = geometry(transition, transition);
		AeroGeometry defaults = geometry();
		EngineeringSkinFrictionCorrelation correlation = new EngineeringSkinFrictionCorrelation();

		assertEquals(correlation.evaluate(defaults, flow(0.8)).totalCd(),
				correlation.evaluate(measured, flow(0.8)).totalCd(), 1e-15);
		assertTrue(correlation.evaluate(measured, flow(2)).totalCd()
				< correlation.evaluate(defaults, flow(2)).totalCd());
	}

	private static FlowCondition flow(double mach) {
		PerfectGasAir air = new PerfectGasAir();
		double pressure = 101325;
		double temperature = 288.15;
		AtmosphereState atmosphere = new AtmosphereState(pressure, temperature,
				pressure / (air.gasConstant() * temperature), air.viscosity(temperature));
		return FlowCondition.fromAngles(mach, 0, 0, atmosphere, air, false, "skin-friction-test");
	}

	private static double adiabaticWallTemperature(double mach) {
		return adiabaticWallTemperature(mach, 288.15);
	}

	private static double adiabaticWallTemperature(double mach, double edgeTemperatureK) {
		return new RecoveryTemperatureModel().recoveryTemperatureK(
				edgeTemperatureK, mach, 1.4, 0.72, true);
	}

	private static AeroGeometry geometry() {
		return geometry(Map.of(), Map.of("thicknessRatio", 0.04));
	}

	private static AeroGeometry geometry(Map<String, Double> bodyReferences,
			Map<String, Double> finReferences) {
		double radius = 0.05;
		AxisymmetricProfile profile = new AxisymmetricProfile(List.of(
				new GeometryStation(0, radius, 0, 0),
				new GeometryStation(1, radius, 0, 0)), List.of(), "TEST", 1e-12);
		AeroComponent body = new AeroComponent("body", "/body", "test", "CYLINDER", "stage", 0,
				new Coordinate(), 0, 1, radius, profile.wettedAreaM2(), 2 * radius, Math.PI * radius * radius,
				0, "ADIABATIC", bodyReferences, List.of(), profile, null, null);
		double root = 0.2;
		double tip = 0.1;
		double span = 0.1;
		double area = 0.5 * (root + tip) * span;
		FinGeometry fin = new FinGeometry("TRAPEZOIDAL", "SINGLE_WEDGE", 4, root, span, area, 0,
				List.of(new GeometryStation(0, 0, 0, 0), new GeometryStation(root - tip, span, 0, 0),
						new GeometryStation(root, span, 0, 0), new GeometryStation(root, 0, 0, 0)));
		AeroComponent fins = new AeroComponent("fins", "/fins", "test", "FIN_TRAPEZOIDAL", "stage", 1,
				new Coordinate(0.8, 0, 0), 0.8, 1, radius, 2 * area * fin.count(), area * fin.count(), 0,
				0, "ADIABATIC", finReferences, List.of(), null, fin, null);
		double referenceArea = Math.PI * radius * radius;
		return new AeroGeometry(List.of(body, fins), new ReferenceGeometry(referenceArea, referenceArea, 1,
				2 * radius, Map.of("body", body.wettedAreaM2(), "fins", fins.wettedAreaM2()),
				new Coordinate(), 2 * radius), "skin-friction-test");
	}
}
