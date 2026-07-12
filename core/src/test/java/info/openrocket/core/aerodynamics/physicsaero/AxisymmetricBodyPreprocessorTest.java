package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricBodyPreprocessor;
import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricBodySegment;
import info.openrocket.core.aerodynamics.physicsaero.body.BodySegmentType;
import info.openrocket.core.aerodynamics.physicsaero.config.NumericalTolerances;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AxisymmetricProfile;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.util.Coordinate;

class AxisymmetricBodyPreprocessorTest {
	@Test
	void coneCylinderJunctionCreatesOnePhysicalCornerAndOneConeSegment() {
		double slope = 0.1;
		AeroComponent cone = component("cone", "NOSE_CONICAL", 0.0, 1.0, 0.0, 0.1,
				List.of(station(0.0, 0.0, slope), station(1.0, 0.1, slope)));
		AeroComponent cylinder = component("tube", "CYLINDER", 1.0, 2.0, 0.1, 0.1,
				List.of(station(1.0, 0.1, 0.0), station(2.0, 0.1, 0.0)));

		List<AxisymmetricBodySegment> segments = new AxisymmetricBodyPreprocessor().preprocess(
				geometry(List.of(cone, cylinder)), 3.0, NumericalTolerances.defaults());

		assertEquals(1, segments.stream().filter(s -> s.type() == BodySegmentType.TRUE_CONE).count());
		assertEquals(1, segments.stream().filter(s -> s.type() == BodySegmentType.DISCRETE_EXPANSION_CORNER).count());
		assertEquals(1, segments.stream().filter(s -> s.type() == BodySegmentType.CYLINDER).count());
		assertEquals(1, segments.stream().filter(s -> s.type() == BodySegmentType.BASE).count());
	}

	@Test
	void fineSmoothProfileStationsDoNotBecomeDiscreteShockEvents() {
		AeroComponent ogive = component("ogive", "NOSE_OGIVE", 0.0, 1.0, 0.0, 0.1,
				List.of(station(0.0, 0.0, 0.20), station(0.25, 0.04, 0.16),
						station(0.50, 0.07, 0.12), station(0.75, 0.09, 0.08), station(1.0, 0.1, 0.0)));

		List<AxisymmetricBodySegment> segments = new AxisymmetricBodyPreprocessor().preprocess(
				geometry(List.of(ogive)), 3.0, NumericalTolerances.defaults());

		assertEquals(1, segments.stream().filter(s -> s.type() == BodySegmentType.SMOOTH_COMPRESSION).count());
		assertEquals(0, segments.stream().filter(s -> s.type() == BodySegmentType.DISCRETE_COMPRESSION_CORNER).count());
		assertEquals(0, segments.stream().filter(s -> s.type() == BodySegmentType.DISCRETE_EXPANSION_CORNER).count());
	}

	private static GeometryStation station(double x, double radius, double slope) {
		return new GeometryStation(x, radius, slope, 0.0);
	}

	private static AeroGeometry geometry(List<AeroComponent> components) {
		ReferenceGeometry references = new ReferenceGeometry(Math.PI * 0.01, Math.PI * 0.01, 2.0, 0.2,
				Map.of(), new Coordinate(), 0.2);
		return new AeroGeometry(components, references, "phase-2-preprocessor");
	}

	private static AeroComponent component(String id, String classification, double x0, double x1,
			double r0, double r1, List<GeometryStation> stations) {
		AxisymmetricProfile profile = new AxisymmetricProfile(stations, List.of(), "TEST_PROFILE", 1.0e-9);
		return new AeroComponent(id, "/" + id, "test", classification, "stage", 0, new Coordinate(x0, 0, 0),
				x0, x1, r1, profile.wettedAreaM2(), 0.0, Math.PI * r1 * r1, 0.0, "ADIABATIC",
				Map.of(), List.of(), profile, null, null);
	}
}
