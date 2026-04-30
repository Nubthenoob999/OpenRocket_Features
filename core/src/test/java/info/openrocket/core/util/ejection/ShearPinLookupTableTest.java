package info.openrocket.core.util.ejection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShearPinLookupTableTest {

	@Test
	void allDesignationsLoad() {
		// 5 imperial nylon + 3 metric nylon + 4 styrene + 3 ABS = 15
		assertEquals(15, ShearPinLookupTable.getAllDesignations().size());
		for (String d : ShearPinLookupTable.getAllDesignations()) {
			assertNotNull(ShearPinLookupTable.getSpec(d));
		}
	}

	@Test
	void common440PinForceMatches() {
		// 3 × #4-40 nylon, tested-min: per ShearPinSpec table 38.2 lbs each → 114.6 lbs
		double f = ShearPinLookupTable.calcTotalShearForce(
				"#4-40", 3, StrengthSource.TESTED_MIN);
		assertEquals(114.6, f, 0.01);
	}

	@Test
	void testedFallsBackToTheoreticalForMetric() {
		// M3 has no tested data — TESTED_MIN should fall back to THEORETICAL_MIN.
		ShearPinSpec m3 = ShearPinLookupTable.getSpec("M3");
		assertFalse(m3.hasTestedData());
		assertEquals(m3.getShearStrengthTheoreticalMin_lbs(),
				m3.getShearStrength(StrengthSource.TESTED_MIN), 1e-6);
	}

	@Test
	void unknownDesignationThrows() {
		assertThrows(IllegalArgumentException.class,
				() -> ShearPinLookupTable.getSpec("#99-99"));
	}

	@Test
	void zeroQuantityYieldsZeroForce() {
		assertEquals(0.0,
				ShearPinLookupTable.calcTotalShearForce("#4-40", 0, StrengthSource.TESTED_MIN),
				1e-9);
	}

	@Test
	void filterByMaterialReturnsExpectedCounts() {
		assertEquals(5, ShearPinLookupTable.getPinsByMaterial(
				PinMaterial.NYLON_6_6_IMPERIAL).size());
		assertEquals(3, ShearPinLookupTable.getPinsByMaterial(
				PinMaterial.NYLON_6_6_METRIC).size());
		assertEquals(4, ShearPinLookupTable.getPinsByMaterial(
				PinMaterial.STYRENE_ROD).size());
		assertEquals(3, ShearPinLookupTable.getPinsByMaterial(
				PinMaterial.ABS_ROD).size());
	}

	@Test
	void hasTestedDataOnlyForImperialNylon() {
		for (String d : ShearPinLookupTable.getAllDesignations()) {
			ShearPinSpec spec = ShearPinLookupTable.getSpec(d);
			boolean expectsTested = spec.getMaterial() == PinMaterial.NYLON_6_6_IMPERIAL;
			assertEquals(expectsTested, spec.hasTestedData(),
					"hasTestedData mismatch for " + d);
		}
		assertTrue(true);
	}
}
