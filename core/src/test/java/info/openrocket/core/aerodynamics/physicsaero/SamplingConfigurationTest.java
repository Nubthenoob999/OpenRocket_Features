package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.config.SamplingConfiguration;

class SamplingConfigurationTest {
	@Test
	void defaultFlightDomainBracketsRepositoryHighMachFlightInsideSourceDomain() {
		double[] mach = SamplingConfiguration.flightDomainDefaults().mach();
		assertEquals(8.0, mach[mach.length - 1]);
		assertTrue(mach[mach.length - 1] > 7.22,
				"the measured Black Brant peak must be bracketed, not extrapolated");
		assertTrue(mach[mach.length - 1] <= 10.0,
				"the default axis must remain inside the A53D02/high-Mach solver domain");
	}
}
