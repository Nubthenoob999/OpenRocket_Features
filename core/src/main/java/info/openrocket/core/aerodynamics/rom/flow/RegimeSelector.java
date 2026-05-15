package info.openrocket.core.aerodynamics.rom.flow;

public class RegimeSelector {
	public FlowRegime select(FlowState flowState) {
		double mach = flowState.getMach();
		if (mach < 0.80) {
			return FlowRegime.SUBSONIC;
		}
		if (mach < 1.20) {
			return FlowRegime.TRANSONIC;
		}
		if (mach < 3.00) {
			return FlowRegime.SUPERSONIC;
		}
		return FlowRegime.HYPERSONIC_LEANING;
	}

	public MachTransitionMap.RegimeBand selectBand(FlowState flowState) {
		return MachTransitionMap.band(flowState != null ? flowState.getMach() : 0.0);
	}

	public double transonicProximity(FlowState flowState, double bandHalfWidth) {
		double distance = Math.abs(flowState.getMach() - 1.0);
		if (distance >= bandHalfWidth) {
			return 0.0;
		}
		return 1.0 - distance / Math.max(1e-6, bandHalfWidth);
	}
}
