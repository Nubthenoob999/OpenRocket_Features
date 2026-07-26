package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AxisymmetricProfile;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.aerodynamics.physicsaero.integration.FullRegimeTableBuilder;
import info.openrocket.core.aerodynamics.physicsaero.interaction.SlenderCruciformCorrelation;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.util.Coordinate;

class FullRegimeReynoldsEnvelopeTest {
	@Test
	void launchAltitudeReynoldsRatioRemainsInsideEngineeringEnvelope() {
		var correction = buildCell(10, 0.3).runtimeCorrection();

		assertFalse(correction.requiresRebuild());
		assertTrue(correction.supportsRatio(0.85),
				"a typical elevated launch site must not immediately force fallback");
		assertEquals(0.10, correction.minimumRatio(), 0,
				"a natural-transition topology retains the validated one-decade envelope");
		assertEquals(1.25, correction.maximumRatio(), 0);
	}

	@Test
	void lowMachInterpolationDoesNotInheritFalseRebuildFromMachZeroLimit() {
		AeroGeometry geometry = geometry(10);
		PerfectGasAir air = new PerfectGasAir();
		double pressurePa = 101325;
		double temperatureK = 288.15;
		AtmosphereState atmosphere = new AtmosphereState(pressurePa, temperatureK,
				pressurePa / (air.gasConstant() * temperatureK), air.viscosity(temperatureK));
		TableMetadata metadata = metadata(geometry);
		var table = new FullRegimeTableBuilder().build(geometry, new double[] {0, 0.1},
				new double[] {0}, new double[] {0}, atmosphere, air, metadata);

		var correction = new info.openrocket.core.aerodynamics.physicsaero.interpolation.TableQueryEngine()
				.query(table, 0.05, 0, 0).runtimeCorrection();

		assertFalse(correction.requiresRebuild());
		assertTrue(correction.referenceReynolds() > 0);
		assertTrue(correction.supportsRatio(0.85));
		assertTrue(correction.supportsRatio(0.10),
				"the zero-speed coefficient limit must not narrow a validated positive-Mach envelope");
	}

	@Test
	void authoritativeVehicleClosureMayBeReynoldsInvariant() {
		var cell = buildCell(sourceSimilarSlenderGeometry(), 0.8);
		var correction = cell.runtimeCorrection();

		assertTrue(cell.methodIds().contains(SlenderCruciformCorrelation.METHOD_ID));
		assertTrue(java.util.Arrays.stream(correction.dCoefficientDLogRe())
				.allMatch(value -> Math.abs(value) < 1e-14));
		assertFalse(correction.requiresRebuild(),
				"a valid zero sensitivity must not be misclassified as missing correction data");
		assertTrue(correction.supportsRatio(1));
	}

	@Test
	void d4013TotalDragIsNotAddedOutsideMeasuredGeometryFamily() {
		SlenderCruciformCorrelation correlation = new SlenderCruciformCorrelation();
		AeroGeometry unrelatedSlenderBody = geometry(20);

		assertTrue(correlation.axialCoefficient(unrelatedSlenderBody, 2.0).isEmpty());
		assertEquals(0, correlation.axialDragIncrement(unrelatedSlenderBody, 2.0), 0,
				"a source-family total coefficient is not a universal component increment");
	}

	@Test
	void anchoredQuadraticTracksDirectLowReynoldsSolutions() {
		AeroGeometry geometry = geometry(10);
		for (double mach : new double[] {0.3, 0.8, 1.2, 2.0, 4.0}) {
			var reference = buildCell(geometry, mach);
			for (double ratio : new double[] {0.5, 0.25, 0.10}) {
				var direct = buildCell(geometry, mach, ratio);
				double logRatio = Math.log(ratio);
				double predicted = reference.coefficients().ca()
						+ reference.runtimeCorrection().dCoefficientDLogRe()[0] * logRatio
						+ reference.runtimeCorrection().dCoefficientDLogReSquared()[0]
								* logRatio * logRatio
						+ reference.runtimeCorrection().dCoefficientDLogReCubed()[0]
								* logRatio * logRatio * logRatio;
				double relativeError = Math.abs(predicted - direct.coefficients().ca())
						/ Math.max(1e-9, Math.abs(direct.coefficients().ca()));
				assertTrue(relativeError < 0.05,
						"anchored correction error at M=" + mach + ", ratio=" + ratio
								+ " was " + relativeError);
			}
		}
	}

	@Test
	void fullyTurbulentTopologyAdmitsOnlyDirectlyValidatedLowReynoldsEnvelope() {
		AeroGeometry geometry = geometry(10, true);
		for (double mach : new double[] {0.3, 0.8, 1.2, 2.0, 4.0}) {
			var reference = buildCell(geometry, mach);
			double expectedMinimum = mach <= 1.3 ? 0.001 : 0.01;
			double[] ratios = mach <= 1.3
					? new double[] {0.5, 0.25, 0.10, 0.03162277660168379, 0.01,
							0.0031622776601683794, 0.001}
					: new double[] {0.5, 0.25, 0.10, 0.03162277660168379, 0.01};
			for (double ratio : ratios) {
				var direct = buildCell(geometry, mach, ratio);
				double logRatio = Math.log(ratio);
				double predicted = reference.coefficients().ca()
						+ reference.runtimeCorrection().dCoefficientDLogRe()[0] * logRatio
						+ reference.runtimeCorrection().dCoefficientDLogReSquared()[0]
								* logRatio * logRatio
						+ reference.runtimeCorrection().dCoefficientDLogReCubed()[0]
								* logRatio * logRatio * logRatio;
				double relativeError = Math.abs(predicted - direct.coefficients().ca())
						/ Math.max(1e-9, Math.abs(direct.coefficients().ca()));
				assertTrue(relativeError < 0.05,
						"directly validated correction error at M=" + mach + ", ratio=" + ratio
								+ " was " + relativeError);
			}
			assertEquals(expectedMinimum, reference.runtimeCorrection().minimumRatio(), 0,
					"fully turbulent ownership should retain only its directly validated envelope at M="
							+ mach);
		}
	}

	private static info.openrocket.core.aerodynamics.physicsaero.table.TableCell buildCell(
			double finenessRatio, double mach) {
		return buildCell(geometry(finenessRatio), mach);
	}

	private static info.openrocket.core.aerodynamics.physicsaero.table.TableCell buildCell(
			AeroGeometry geometry, double mach) {
		return buildCell(geometry, mach, 1);
	}

	private static info.openrocket.core.aerodynamics.physicsaero.table.TableCell buildCell(
			AeroGeometry geometry, double mach, double reynoldsRatio) {
		PerfectGasAir air = new PerfectGasAir();
		double pressurePa = 101325;
		double temperatureK = 288.15;
		AtmosphereState atmosphere = new AtmosphereState(pressurePa * reynoldsRatio, temperatureK,
				reynoldsRatio * pressurePa / (air.gasConstant() * temperatureK),
				air.viscosity(temperatureK));
		TableMetadata metadata = metadata(geometry);
		return new FullRegimeTableBuilder().build(geometry, new double[] {mach},
				new double[] {0}, new double[] {0}, atmosphere, air, metadata).cell(0, 0, 0);
	}

	private static TableMetadata metadata(AeroGeometry geometry) {
		return new TableMetadata(TableMetadata.CURRENT_SCHEMA,
				geometry.geometryHash(), "reynolds-envelope-test", "test", "test",
				TableMetadata.REQUIRED_UNITS, TableMetadata.REQUIRED_AXIS_CONVENTION,
				Instant.parse("2026-01-01T00:00:00Z"), Map.of(), Map.of(), "TEST");
	}

	private static AeroGeometry geometry(double finenessRatio) {
		return geometry(finenessRatio, false);
	}

	private static AeroGeometry geometry(double finenessRatio, boolean fullyTurbulent) {
		double radius = 0.05;
		double length = finenessRatio * 2 * radius;
		double noseLength = 0.25 * length;
		double slope = radius / noseLength;
		AxisymmetricProfile noseProfile = new AxisymmetricProfile(List.of(
				new GeometryStation(0, 0, slope, 0),
				new GeometryStation(noseLength, radius, slope, 0)), List.of(), "TEST", 1e-9);
		AxisymmetricProfile tubeProfile = new AxisymmetricProfile(List.of(
				new GeometryStation(noseLength, radius, 0, 0),
				new GeometryStation(length, radius, 0, 0)), List.of(), "TEST", 1e-9);
		Map<String, Double> localReferences = fullyTurbulent
				? Map.of("forceFullyTurbulent", 1.0) : Map.of();
		AeroComponent nose = component("nose", "NOSE_CONICAL", 0, noseLength,
				0, radius, noseProfile, localReferences);
		AeroComponent tube = component("tube", "CYLINDER", noseLength, length,
				radius, radius, tubeProfile, localReferences);
		double referenceArea = Math.PI * radius * radius;
		return new AeroGeometry(List.of(nose, tube), new ReferenceGeometry(referenceArea,
				referenceArea, length, 2 * radius, Map.of(), new Coordinate(), 2 * radius),
				"reynolds-envelope-" + finenessRatio);
	}

	private static AeroGeometry sourceSimilarSlenderGeometry() {
		double radius = 0.05;
		double baseRadius = 0.653 * radius;
		double length = 18.20 * 2 * radius;
		double noseEnd = 0.25 * length;
		double boattailStart = 0.90 * length;
		double noseSlope = radius / noseEnd;
		double tailSlope = (baseRadius - radius) / (length - boattailStart);
		AxisymmetricProfile noseProfile = new AxisymmetricProfile(List.of(
				new GeometryStation(0, 0, noseSlope, 0),
				new GeometryStation(noseEnd, radius, noseSlope, 0)), List.of(), "TEST", 1e-9);
		AxisymmetricProfile tubeProfile = new AxisymmetricProfile(List.of(
				new GeometryStation(noseEnd, radius, 0, 0),
				new GeometryStation(boattailStart, radius, 0, 0)), List.of(), "TEST", 1e-9);
		AxisymmetricProfile tailProfile = new AxisymmetricProfile(List.of(
				new GeometryStation(boattailStart, radius, tailSlope, 0),
				new GeometryStation(length, baseRadius, tailSlope, 0)), List.of(), "TEST", 1e-9);
		AeroComponent nose = component("nose", "NOSE_CONICAL", 0, noseEnd,
				0, radius, noseProfile);
		AeroComponent tube = component("tube", "CYLINDER", noseEnd, boattailStart,
				radius, radius, tubeProfile);
		AeroComponent tail = component("tail", "BOATTAIL", boattailStart, length,
				radius, baseRadius, tailProfile);
		double referenceArea = Math.PI * radius * radius;
		return new AeroGeometry(List.of(nose, tube, tail), new ReferenceGeometry(referenceArea,
				Math.PI * baseRadius * baseRadius, length, 2 * radius, Map.of(),
				new Coordinate(), 2 * radius), "reynolds-envelope-source-slender");
	}

	private static AeroComponent component(String id, String classification,
			double x0, double x1, double r0, double r1, AxisymmetricProfile profile) {
		return component(id, classification, x0, x1, r0, r1, profile, Map.of());
	}

	private static AeroComponent component(String id, String classification,
			double x0, double x1, double r0, double r1, AxisymmetricProfile profile,
			Map<String, Double> localReferences) {
		double radius = Math.max(r0, r1);
		return new AeroComponent(id, "/" + id, "test", classification, "stage", 0,
				new Coordinate(x0, 0, 0), x0, x1, radius, profile.wettedAreaM2(),
				2 * radius * (x1 - x0), Math.PI * r1 * r1, 0, "ADIABATIC",
				localReferences, List.of(), profile, null, null);
	}
}
