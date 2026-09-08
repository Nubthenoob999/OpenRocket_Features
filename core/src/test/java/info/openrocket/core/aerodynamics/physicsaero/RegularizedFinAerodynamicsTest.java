package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.fin.RegularizedFinAerodynamics;

class RegularizedFinAerodynamicsTest {
	@Test
	void lowAspectRatioCenterOfPressureStaysFiniteAndBounded() {
		for (double aspectRatio = 0.05; aspectRatio <= 0.8; aspectRatio += 0.005) {
			for (double mach = 0.5; mach <= 5; mach += 0.025) {
				double fraction = RegularizedFinAerodynamics
						.centerOfPressureChordFraction(mach, aspectRatio);
				assertTrue(Double.isFinite(fraction));
				assertTrue(fraction >= 0.25 && fraction <= 0.5,
						"fraction=" + fraction + ", M=" + mach + ", AR=" + aspectRatio);
			}
		}
	}

	@Test
	void ordinaryFinUsesThePublishedSupersonicEquation() {
		double mach = 3;
		double aspectRatio = 2.5;
		double arBeta = aspectRatio * Math.sqrt(mach * mach - 1);
		double expected = (arBeta - 0.67) / (2 * arBeta - 1);
		assertEquals(expected, RegularizedFinAerodynamics
				.centerOfPressureChordFraction(mach, aspectRatio), 1e-14);
	}

	@Test
	void bodyFinInterferenceMatchesBothRegimeLimits() {
		double tau = 0.4;
		assertEquals((1 + tau) * (1 + tau), RegularizedFinAerodynamics
				.bodyFinInterferenceFactor(tau, 0.8), 1e-14);
		assertEquals(1 + tau, RegularizedFinAerodynamics
				.bodyFinInterferenceFactor(tau, 1.5), 1e-14);
	}
}
