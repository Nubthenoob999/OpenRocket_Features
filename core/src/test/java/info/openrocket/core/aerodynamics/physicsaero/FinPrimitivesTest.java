package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.fin.FinLocalFrame;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinSectionFamily;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinStrip;
import info.openrocket.core.util.Coordinate;

class FinPrimitivesTest {
	private static final double EPSILON = 1.0e-12;

	@Test
	void constructsRightHandedFrameAndProjectsCoordinates() {
		FinLocalFrame frame = new FinLocalFrame(
				new Coordinate(1, 0, 0), new Coordinate(0, 0, 1));

		assertEquals(new Coordinate(0, -1, 0), frame.normal());
		Coordinate body = new Coordinate(2, -4, 3);
		assertEquals(new Coordinate(2, 3, 4), frame.toLocal(body));
		assertEquals(body, frame.toBody(frame.toLocal(body)));
		assertEquals(new Coordinate(1, 2, 3),
				frame.projectPoint(new Coordinate(11, -3, 4), new Coordinate(10, 0, 2)));
		assertEquals(2.0, frame.chordwiseComponent(body), EPSILON);
		assertEquals(3.0, frame.spanwiseComponent(body), EPSILON);
		assertEquals(4.0, frame.normalComponent(body), EPSILON);
	}

	@Test
	void rejectsNonOrthonormalAndLeftHandedFrames() {
		assertThrows(IllegalArgumentException.class, () -> new FinLocalFrame(
				new Coordinate(2, 0, 0), new Coordinate(0, 1, 0), new Coordinate(0, 0, 1)));
		assertThrows(IllegalArgumentException.class, () -> new FinLocalFrame(
				new Coordinate(1, 0, 0), new Coordinate(1, 0, 0), new Coordinate(0, 0, 1)));
		assertThrows(IllegalArgumentException.class, () -> new FinLocalFrame(
				new Coordinate(1, 0, 0), new Coordinate(0, 1, 0), new Coordinate(0, 0, -1)));
	}

	@Test
	void finStripIsImmutableAndValidatesStoredGeometry() {
		FinLocalFrame frame = new FinLocalFrame(new Coordinate(1, 0, 0), new Coordinate(0, 1, 0));
		Coordinate centroid = new Coordinate(0.15, 0.12, 0.01, 7.0);
		FinStrip strip = new FinStrip(0.10, 0.04, 0.02, 0.22, 0.20,
				0.3, 0.2, 0.05, FinSectionFamily.SYMMETRIC_DIAMOND, centroid, 0.008, frame, 0.05);

		assertNotSame(centroid, strip.centroidBodyM());
		assertEquals(new Coordinate(0.15, 0.12, 0.01), strip.centroidBodyM());
		assertEquals(0.20, strip.chordM(), EPSILON);
		assertEquals(frame, strip.localFrame());

		assertThrows(IllegalArgumentException.class, () -> new FinStrip(0.10, 0.04, 0.02, 0.23, 0.20,
				0.3, 0.2, 0.05, FinSectionFamily.FLAT_PLATE, centroid, 0.008, frame, 0.05));
		assertThrows(IllegalArgumentException.class, () -> new FinStrip(0.10, -0.04, 0.02, 0.22, 0.20,
				0.3, 0.2, 0.05, FinSectionFamily.FLAT_PLATE, centroid, 0.008, frame, 0.05));
	}
}
