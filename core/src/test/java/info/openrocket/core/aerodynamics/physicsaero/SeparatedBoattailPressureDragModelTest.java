package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.body.SeparatedBoattailPressureDragModel;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AxisymmetricProfile;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.util.Coordinate;

class SeparatedBoattailPressureDragModelTest {
	@Test
	void steepContractionUsesBasePressureOverAnnularProjectedArea() {
		double foreRadius = 0.05;
		double aftRadius = 0.03125;
		double length = 2 * (foreRadius - aftRadius);
		AeroGeometry geometry = geometry(foreRadius, aftRadius, length);
		double pressureMagnitude = 0.20;

		double expected = pressureMagnitude
				* (foreRadius * foreRadius - aftRadius * aftRadius)
				/ (foreRadius * foreRadius);
		assertEquals(expected, new SeparatedBoattailPressureDragModel()
				.dragCoefficient(geometry, pressureMagnitude), 1e-12);
	}

	@Test
	void slenderContractionReceivesCompletePressureRecovery() {
		double foreRadius = 0.05;
		double aftRadius = 0.04;
		AeroGeometry geometry = geometry(foreRadius, aftRadius,
				6 * (foreRadius - aftRadius));
		assertEquals(0, new SeparatedBoattailPressureDragModel()
				.dragCoefficient(geometry, 0.25), 1e-12);
	}

	private static AeroGeometry geometry(double foreRadius, double aftRadius,
			double length) {
		AxisymmetricProfile profile = new AxisymmetricProfile(List.of(
				new GeometryStation(0, foreRadius,
						(aftRadius - foreRadius) / length, 0),
				new GeometryStation(length, aftRadius,
						(aftRadius - foreRadius) / length, 0)),
				List.of(), "TEST_BOATTAIL", 1e-12);
		AeroComponent component = new AeroComponent("boattail", "/boattail",
				"test", "BOATTAIL", "stage", 0, new Coordinate(), 0, length,
				foreRadius, profile.wettedAreaM2(), 2 * foreRadius * length,
				Math.PI * aftRadius * aftRadius, 0, "ADIABATIC", Map.of(),
				List.of(), profile, null, null);
		double referenceArea = Math.PI * foreRadius * foreRadius;
		ReferenceGeometry references = new ReferenceGeometry(referenceArea,
				Math.PI * aftRadius * aftRadius, length, 2 * foreRadius,
				Map.of(component.id(), component.wettedAreaM2()), new Coordinate(),
				2 * foreRadius);
		return new AeroGeometry(List.of(component), references, "boattail-test");
	}
}
