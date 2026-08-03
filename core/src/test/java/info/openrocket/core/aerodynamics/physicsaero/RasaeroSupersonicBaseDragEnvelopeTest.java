package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.body.RasaeroSupersonicBaseDragEnvelope;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AxisymmetricProfile;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.util.Coordinate;

class RasaeroSupersonicBaseDragEnvelopeTest {
	@Test
	void reproducesMachThreeRasaeroTerminalBaseExports() {
		assertEquals(0.073, evaluate(8.375, 6.7, 1.1), 5e-4);
		assertEquals(0.068, evaluate(6.0, 5.0, 1.0), 5e-4);
	}

	private double evaluate(double foreDiameter, double aftDiameter,
			double length) {
		double fore = foreDiameter / 2;
		double aft = aftDiameter / 2;
		AxisymmetricProfile profile = new AxisymmetricProfile(List.of(
				new GeometryStation(0, fore, (aft - fore) / length, 0),
				new GeometryStation(length, aft, (aft - fore) / length, 0)),
				List.of(), "RASAERO_BASE_TEST", 1e-12);
		AeroComponent tail = new AeroComponent("tail", "/tail", "tail",
				"BOATTAIL", "stage", 0, new Coordinate(), 0, length, fore,
				profile.wettedAreaM2(), 2 * fore * length, Math.PI * aft * aft,
				0, "ADIABATIC", Map.of(), List.of(), profile, null, null);
		double referenceArea = Math.PI * fore * fore;
		ReferenceGeometry references = new ReferenceGeometry(referenceArea,
				Math.PI * aft * aft, length, 2 * fore,
				Map.of(tail.id(), tail.wettedAreaM2()), new Coordinate(), 2 * fore);
		return new RasaeroSupersonicBaseDragEnvelope().dragCoefficient(
				new AeroGeometry(List.of(tail), references, "base-test"), 3);
	}
}
