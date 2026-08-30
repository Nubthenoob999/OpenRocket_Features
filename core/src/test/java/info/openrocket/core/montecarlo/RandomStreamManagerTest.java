package info.openrocket.core.montecarlo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class RandomStreamManagerTest {
	@Test
	public void testNamedStreamsAreStableAndIndependentOfRequestOrder() {
		RandomStreamManager first = new RandomStreamManager(0x5EEDL);
		long gustSeed = first.seedFor("gust");
		long shearSeed = first.seedFor("shear");

		RandomStreamManager reordered = new RandomStreamManager(0x5EEDL);
		assertEquals(shearSeed, reordered.seedFor("shear"));
		assertEquals(gustSeed, reordered.seedFor("gust"));
		assertNotEquals(gustSeed, shearSeed);
		assertEquals(first.stream("gust").nextLong(), reordered.stream("gust").nextLong());
	}

	@Test
	public void testMasterSeedChangesEveryNamedStream() {
		assertNotEquals(new RandomStreamManager(1).seedFor("wind"),
				new RandomStreamManager(2).seedFor("wind"));
	}

	@Test
	public void testStreamNamesMustBeMeaningful() {
		RandomStreamManager streams = new RandomStreamManager(1);
		assertThrows(NullPointerException.class, () -> streams.seedFor(null));
		assertThrows(IllegalArgumentException.class, () -> streams.seedFor(""));
		assertThrows(IllegalArgumentException.class, () -> streams.seedFor(" \t"));
	}
}
