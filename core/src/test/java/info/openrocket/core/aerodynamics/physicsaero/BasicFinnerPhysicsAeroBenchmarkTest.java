package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryExtractor;
import info.openrocket.core.aerodynamics.physicsaero.integration.FullRegimeTableBuilder;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.ExternalComponent;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.util.BaseTestCase;

/**
 * Whole-vehicle source gate against the ADA636861 Basic Finner free-flight
 * axial-force data.  This tests the assembled physics solver rather than the
 * finned-base closure formula in isolation.
 */
class BasicFinnerPhysicsAeroBenchmarkTest extends BaseTestCase {
	private static final double MAXIMUM_MAPE_PERCENT = 30;

	@Test
	void assembledZeroYawAxialForceTracksAda636861WithinDeclaredEnvelope()
			throws Exception {
		List<Point> source = sourcePoints();
		double[] mach = source.stream().mapToDouble(Point::mach).toArray();
		AeroGeometry geometry = geometry();
		PerfectGasAir air = new PerfectGasAir();
		double pressure = 101325;
		double temperature = 288.15;
		AtmosphereState atmosphere = new AtmosphereState(pressure, temperature,
				pressure / (air.gasConstant() * temperature),
				air.viscosity(temperature));
		var table = new FullRegimeTableBuilder().build(geometry, mach,
				new double[] {0}, new double[] {0}, atmosphere, air,
				metadata(geometry));

		double sumAbsolutePercentError = 0;
		for (int i = 0; i < source.size(); i++) {
			double predicted = table.cell(i, 0, 0).coefficients().ca();
			double error = 100 * (predicted - source.get(i).cx0())
					/ source.get(i).cx0();
			System.out.printf(java.util.Locale.ROOT,
					"Basic Finner M=%.3f source=%.4f physics=%.4f error=%+.2f%%%n",
					source.get(i).mach(), source.get(i).cx0(),
					predicted, error);
			assertTrue(Double.isFinite(predicted) && predicted > 0,
					"invalid Basic Finner coefficient at M="
							+ source.get(i).mach());
			sumAbsolutePercentError += Math.abs(error);
		}
		double mape = sumAbsolutePercentError / source.size();
		System.out.printf(java.util.Locale.ROOT,
				"Basic Finner assembled-physics MAPE=%.3f%%%n", mape);
		assertTrue(mape <= MAXIMUM_MAPE_PERCENT,
				"Basic Finner assembled-physics MAPE " + mape
						+ "% exceeds source envelope "
						+ MAXIMUM_MAPE_PERCENT + "%");
	}

	private static AeroGeometry geometry() {
		double diameter = 0.030;
		Rocket rocket = new Rocket();
		rocket.setName("Basic Finner ADA636861");
		AxialStage stage = new AxialStage();
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(Transition.Shape.CONICAL,
				2.84 * diameter, diameter / 2);
		nose.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(nose);

		BodyTube body = new BodyTube(7.16 * diameter, diameter / 2,
				0.001);
		body.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(body);

		TrapezoidFinSet fins = new TrapezoidFinSet(4, diameter,
				diameter, 0, diameter);
		fins.setThickness(0.08 * diameter);
		fins.setCrossSection(FinSet.CrossSection.AIRFOIL);
		fins.setFinish(ExternalComponent.Finish.POLISHED);
		fins.setAxialMethod(AxialMethod.BOTTOM);
		body.addChild(fins);
		rocket.enableEvents();

		return new GeometryExtractor().extractWithComponentRoughness(
				rocket, "ADIABATIC", "ada636861-basic-finner");
	}

	private static TableMetadata metadata(AeroGeometry geometry) {
		return new TableMetadata(TableMetadata.CURRENT_SCHEMA,
				geometry.geometryHash(), "ada636861-basic-finner",
				"ADA636861", "source-gate",
				TableMetadata.REQUIRED_UNITS,
				TableMetadata.REQUIRED_AXIS_CONVENTION,
				Instant.parse("2026-01-01T00:00:00Z"),
				Map.of(), Map.of(), "PRIMARY_SOURCE");
	}

	private static List<Point> sourcePoints() throws Exception {
		var stream = BasicFinnerPhysicsAeroBenchmarkTest.class
				.getResourceAsStream(
						"/physicsaero/ada636861_basic_finner_cx0.csv");
		assertTrue(stream != null, "missing ADA636861 source fixture");
		List<Point> points = new ArrayList<>();
		try (var reader = new BufferedReader(new InputStreamReader(stream,
				StandardCharsets.UTF_8))) {
			for (String line; (line = reader.readLine()) != null;) {
				if (line.isBlank() || line.startsWith("#")
						|| line.startsWith("mach,")) continue;
				String[] values = line.split(",");
				assertEquals(2, values.length);
				points.add(new Point(Double.parseDouble(values[0]),
						Double.parseDouble(values[1])));
			}
		}
		assertEquals(8, points.size());
		return List.copyOf(points);
	}

	private record Point(double mach, double cx0) {}
}
