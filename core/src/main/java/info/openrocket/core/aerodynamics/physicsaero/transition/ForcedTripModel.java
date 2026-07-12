package info.openrocket.core.aerodynamics.physicsaero.transition;
public final class ForcedTripModel {
	public TransitionDecision evaluate(double stationM, double tripM) {
		double margin = stationM - tripM;
		return new TransitionDecision(margin >= 0, margin, "forced-trip-v1", margin >= 0 ? "FORCED_TRIP" : "UPSTREAM_OF_TRIP");
	}
}
