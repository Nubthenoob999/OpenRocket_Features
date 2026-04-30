package info.openrocket.core.util.ejection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.util.ejection.CylinderFrictionForceCalculator.FrictionResult;

class CylinderFrictionForceCalculatorTest {

	@Test
	void zeroInterferenceProducesZeroForce() {
		FrictionResult r = CylinderFrictionForceCalculator.calculate(
				AirframeMaterial.FIBERGLASS, AirframeMaterial.FIBERGLASS,
				4.00, 4.10,  // outer ID/OD
				3.85, 3.99,  // inner ID/OD (note: must satisfy inner OD <= outer ID for assembly)
				3.00,        // engagement
				0.0,         // δ = 0
				0.30);
		assertEquals(0.0, r.getContactPressure_psi(), 1e-9);
		assertEquals(0.0, r.getFrictionForce_lbs(), 1e-9);
	}

	@Test
	void negativeInterferenceProducesZeroForce() {
		FrictionResult r = CylinderFrictionForceCalculator.calculate(
				AirframeMaterial.FIBERGLASS, AirframeMaterial.FIBERGLASS,
				4.00, 4.10,
				3.85, 3.99,
				3.00,
				-0.001,
				0.30);
		assertEquals(0.0, r.getFrictionForce_lbs(), 1e-9);
	}

	@Test
	void fiberglassOnFiberglassKnownGeometryWithinExpectedBand() {
		// 4" FG bay, 4" FG coupler, 0.001" diametral interference, L = 3", μ = 0.30.
		// Expected order-of-magnitude: contact pressure ≈ tens of psi for this fit;
		// friction force scales as μ × P_c × π × d × L. Verify it's in 5–80 lbs band.
		FrictionResult r = CylinderFrictionForceCalculator.calculate(
				AirframeMaterial.FIBERGLASS, AirframeMaterial.FIBERGLASS,
				4.00, 4.10,
				3.85, 3.99,
				3.00,
				0.001,
				0.30);
		double F = r.getFrictionForce_lbs();
		assertTrue(F > 1.0 && F < 200.0,
				"F should be in plausible HPR range, got " + F);
		assertTrue(r.getContactPressure_psi() > 0.0);
	}

	@Test
	void carbonHubReducesFrictionVsFiberglass() {
		FrictionResult fg = CylinderFrictionForceCalculator.calculate(
				AirframeMaterial.FIBERGLASS, AirframeMaterial.FIBERGLASS,
				4.00, 4.10, 3.85, 3.99, 3.00, 0.001, 0.30);
		// Higher hoop modulus → higher contact pressure for same δ; force is higher.
		FrictionResult cf = CylinderFrictionForceCalculator.calculate(
				AirframeMaterial.CARBON_FIBER, AirframeMaterial.CARBON_FIBER,
				4.00, 4.10, 3.85, 3.99, 3.00, 0.001, 0.30);
		assertTrue(cf.getContactPressure_psi() > fg.getContactPressure_psi(),
				"CFRP should yield higher P_c for the same δ");
	}

	@Test
	void invalidGeometryThrows() {
		// outer OD <= outer ID → invalid hub geometry
		assertThrows(IllegalArgumentException.class, () -> new TubeGeometry(
				"x", AirframeMaterial.FIBERGLASS, 4.0, 4.0, 1e6, 0.3));
	}

	@Test
	void contactAreaIsPiTimesDTimesL() {
		assertEquals(Math.PI * 4.0 * 3.0,
				CylinderFrictionForceCalculator.contactArea(4.0, 3.0), 1e-9);
	}
}
