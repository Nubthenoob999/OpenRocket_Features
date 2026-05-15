package info.openrocket.core.aerodynamics.rom.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class RegimeSelectorTest {

	private final RegimeSelector selector = new RegimeSelector();

	@Test
	public void dispatchesAcrossMachBands() {
		assertEquals(FlowRegime.SUBSONIC, selector.select(flowState(0.79)));
		assertEquals(FlowRegime.TRANSONIC, selector.select(flowState(0.80)));
		assertEquals(FlowRegime.TRANSONIC, selector.select(flowState(1.19)));
		assertEquals(FlowRegime.SUPERSONIC, selector.select(flowState(1.20)));
		assertEquals(FlowRegime.HYPERSONIC_LEANING, selector.select(flowState(5.00)));
	}

	@Test
	public void dispatchesDetailedMachBands() {
		assertEquals(MachTransitionMap.RegimeBand.INCOMPRESSIBLE, selector.selectBand(flowState(0.29)));
		assertEquals(MachTransitionMap.RegimeBand.COMPRESSIBLE_SUBSONIC, selector.selectBand(flowState(0.30)));
		assertEquals(MachTransitionMap.RegimeBand.COMPRESSIBLE_SUBSONIC, selector.selectBand(flowState(0.79)));
		assertEquals(MachTransitionMap.RegimeBand.PRESONIC_TRANSONIC, selector.selectBand(flowState(0.80)));
		assertEquals(MachTransitionMap.RegimeBand.PRESONIC_TRANSONIC, selector.selectBand(flowState(0.99)));
		assertEquals(MachTransitionMap.RegimeBand.SUPERSONIC, selector.selectBand(flowState(1.00)));
	}

	@Test
	public void transonicProximityPeaksAtMachOneAndFallsOffSmoothly() {
		double peak = selector.transonicProximity(flowState(1.00), 0.20);
		double shoulder = selector.transonicProximity(flowState(0.90), 0.20);
		double outside = selector.transonicProximity(flowState(1.30), 0.20);

		assertEquals(1.0, peak, 1e-12);
		assertTrue(shoulder > 0.0);
		assertEquals(0.0, outside, 1e-12);
	}

	private static FlowState flowState(double mach) {
		return new FlowState(mach, 1.0e6, 100.0, 101325.0, 288.15, 1.225, 340.0, 1.5e-5,
				mach * 340.0, 0.0, 0.0, 0.0, 0.0, false, 0.0, 1.0);
	}
}
