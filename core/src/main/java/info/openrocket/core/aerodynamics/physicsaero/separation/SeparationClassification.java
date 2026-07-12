package info.openrocket.core.aerodynamics.physicsaero.separation;
import java.util.List;
public record SeparationClassification(SeparationState state, double severity, int persistentStations,
		SeparationSignals signals, List<String> reasons, double confidence) {
	public SeparationClassification { reasons=List.copyOf(reasons); if(state==null||signals==null||severity<0||severity>1||persistentStations<0||confidence<0||confidence>1)throw new IllegalArgumentException("invalid separation classification"); }
}
