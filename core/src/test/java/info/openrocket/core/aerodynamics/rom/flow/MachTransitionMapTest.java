package info.openrocket.core.aerodynamics.rom.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class MachTransitionMapTest {

	@Test
	public void assignsMachZeroToOneBands() {
		assertEquals(MachTransitionMap.RegimeBand.INCOMPRESSIBLE, MachTransitionMap.band(0.0));
		assertEquals(MachTransitionMap.RegimeBand.INCOMPRESSIBLE, MachTransitionMap.band(0.29));
		assertEquals(MachTransitionMap.RegimeBand.COMPRESSIBLE_SUBSONIC, MachTransitionMap.band(0.30));
		assertEquals(MachTransitionMap.RegimeBand.COMPRESSIBLE_SUBSONIC, MachTransitionMap.band(0.79));
		assertEquals(MachTransitionMap.RegimeBand.PRESONIC_TRANSONIC, MachTransitionMap.band(0.80));
		assertEquals(MachTransitionMap.RegimeBand.PRESONIC_TRANSONIC, MachTransitionMap.band(0.99));
	}

	@Test
	public void presonicHermiteWeightHasFixedEndpoints() {
		assertEquals(0.0, MachTransitionMap.presonicWeight(0.80), 1e-12);
		assertEquals(1.0, MachTransitionMap.presonicWeight(1.00), 1e-12);
		assertEquals(0.0, MachTransitionMap.cubicHermiteUnit(-1.0), 1e-12);
		assertEquals(1.0, MachTransitionMap.cubicHermiteUnit(2.0), 1e-12);
	}

	@Test
	public void firstDifferenceAroundPresonicEntryIsBounded() {
		double left = MachTransitionMap.presonicWeight(0.799);
		double center = MachTransitionMap.presonicWeight(0.800);
		double right = MachTransitionMap.presonicWeight(0.801);

		assertTrue(Math.abs(center - left) < 1.0e-3);
		assertTrue(Math.abs(right - center) < 1.0e-3);
	}

	@Test
	public void finitePrandtlGlauertBetaNeverApproachesSonicSingularity() {
		assertEquals(0.6, MachTransitionMap.finitePrandtlGlauertBeta(0.80), 1e-12);
		assertEquals(0.6, MachTransitionMap.finitePrandtlGlauertBeta(0.99), 1e-12);
		assertEquals(0.6, MachTransitionMap.finitePrandtlGlauertBeta(2.00), 1e-12);
	}
}
