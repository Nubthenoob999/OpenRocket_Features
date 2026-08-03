package info.openrocket.core.aerodynamics.physicsaero.powered;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.flow.PoweredFlowState;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.util.Coordinate;

class RasaeroPoweredNozzleDragModelTest {
	private static final double REFERENCE_AREA = 0.02;
	private static final double AMBIENT_PRESSURE = 80000;
	private static final AeroComponent BODY = new AeroComponent(
			"body", "body", "test", "CYLINDER", "stage", 0,
			new Coordinate(), 0, 2.0, 0.08, 0, 0, REFERENCE_AREA,
			0, "ADIABATIC", Map.of(), List.of(), null, null, null);
	private static final AeroGeometry GEOMETRY = new AeroGeometry(List.of(BODY),
			new ReferenceGeometry(REFERENCE_AREA, REFERENCE_AREA, 2.0, 0.16,
					Map.of(), new Coordinate(), 0.16), "powered-nozzle-test");

	@Test
	void proteusAreaRatioReproducesMachTwoPowerOnDecrement() {
		double nozzleArea = 0.64 * REFERENCE_AREA;
		PoweredFlowState state = PoweredFlowState.nozzleGeometryOnly(
				1, nozzleArea, AMBIENT_PRESSURE);
		double coefficient = 0.1478 + (2.0 - 1.97) * 0.037916667;

		var result = new RasaeroPoweredNozzleDragModel().evaluate(
				GEOMETRY, 2.0, state);

		assertEquals(-coefficient * 0.64, result.totalDeltaCd(), 1.0e-12);
		assertEquals(result.totalDeltaCd(), result.baseDeltaCd(), 0);
		assertEquals(0, result.boattailDeltaCd(), 0);
	}

	@Test
	void correlationIsContinuousAtEveryPiecewiseBoundary() {
		for (double boundary : new double[] {
				0.58, 0.925, 1.0, 1.1935, 1.97, 2.45, 3.0, 3.85, 4.387, 7.5
		}) {
			double below = RasaeroPoweredNozzleDragModel.coefficient(
					boundary - 1.0e-9);
			double above = RasaeroPoweredNozzleDragModel.coefficient(
					boundary + 1.0e-9);
			assertEquals(below, above, 2.0e-4, "boundary " + boundary);
		}
	}

	@Test
	void resolvedThermodynamicStateCannotEnterGeometryOnlyCorrelation() {
		PoweredFlowState resolved = new PoweredFlowState(1, 1000, 2.5,
				0.01, 150000, 1.25, AMBIENT_PRESSURE);
		assertThrows(IllegalArgumentException.class,
				() -> new RasaeroPoweredNozzleDragModel().evaluate(
						GEOMETRY, 2.0, resolved));
	}
}
