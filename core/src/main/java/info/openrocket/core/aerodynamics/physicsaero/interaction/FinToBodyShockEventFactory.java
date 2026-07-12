package info.openrocket.core.aerodynamics.physicsaero.interaction;

import info.openrocket.core.aerodynamics.physicsaero.fin.FinLocalFlow;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinStrip;
import info.openrocket.core.util.Coordinate;

/** Phase 3 typed event; quantitative body pressure/SWBLI ownership remains deferred. */
public final class FinToBodyShockEventFactory {
	public FinToBodyShockEvent create(String finId, int stripIndex, FinStrip strip, FinLocalFlow incoming,
			Coordinate directionBody, String targetBodyRegion, double confidence) {
		return new FinToBodyShockEvent(finId, stripIndex, strip.centroidBodyM(), directionBody, targetBodyRegion,
				incoming, confidence, "BODY_RESPONSE_PENDING_PHASE4");
	}
	public record FinToBodyShockEvent(String sourceFinId, int sourceStripIndex, Coordinate originBodyM,
			Coordinate directionBody, String targetBodyRegion, FinLocalFlow incomingState, double confidence,
			String resolutionStatus) {
		public FinToBodyShockEvent { if (sourceFinId == null || sourceFinId.isBlank() || sourceStripIndex < 0 || originBodyM == null || directionBody == null || targetBodyRegion == null || incomingState == null || confidence < 0 || confidence > 1) throw new IllegalArgumentException("invalid fin shock event"); }
	}
}
