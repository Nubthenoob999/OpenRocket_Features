package info.openrocket.core.aerodynamics.physicsaero.separation;

/** Requires 2-3 stations before promoting noisy continuous signals; explicit discontinuities may promote immediately. */
public final class SeparationPersistenceFilter {
	private final int incipientStations, separatedStations; private SeparationState candidate=SeparationState.ATTACHED; private int count;
	public SeparationPersistenceFilter(){this(2,3);}
	public SeparationPersistenceFilter(int incipientStations,int separatedStations){if(incipientStations<2||separatedStations<incipientStations)throw new IllegalArgumentException();this.incipientStations=incipientStations;this.separatedStations=separatedStations;}
	public SeparationState update(SeparationState raw,boolean explicitInteraction){
		if(raw==SeparationState.INVALID||explicitInteraction&&raw==SeparationState.SEPARATED){candidate=raw;count=1;return raw;}
		if(raw==candidate)count++;else{candidate=raw;count=1;}
		if(raw==SeparationState.SEPARATED)return count>=separatedStations?raw:SeparationState.PROBABLE_SEPARATION;
		if(raw==SeparationState.INCIPIENT_SEPARATION||raw==SeparationState.PROBABLE_SEPARATION)return count>=incipientStations?raw:SeparationState.ADVERSE_GRADIENT_WARNING;
		return raw;
	}
	public int count(){return count;}
}
