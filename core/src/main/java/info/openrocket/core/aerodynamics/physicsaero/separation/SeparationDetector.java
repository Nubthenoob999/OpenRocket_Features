package info.openrocket.core.aerodynamics.physicsaero.separation;

import java.util.ArrayList;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.*;
import info.openrocket.core.aerodynamics.physicsaero.thermal.RecoveryTemperatureModel;
import info.openrocket.core.aerodynamics.physicsaero.transition.TransitionState;

public final class SeparationDetector {
	public List<SeparationClassification> classify(BoundaryLayerHistory history,double gamma,double prandtl){
		List<SeparationClassification> out=new ArrayList<>();SeparationPersistenceFilter filter=new SeparationPersistenceFilter();BoundaryLayerState previous=null;
		for(int i=0;i<history.states().size();i++){
			BoundaryLayerState state=history.states().get(i);BoundaryLayerStation station=history.track().stations().get(i);
			double tr=new RecoveryTemperatureModel().recoveryTemperatureK(station.temperatureK(),station.mach(),gamma,prandtl,state.transition()!=TransitionState.LAMINAR);
			SeparationSignals signals=SeparationSignals.from(station,state,previous,tr);
			SeparationClassification raw=state.transition()==TransitionState.LAMINAR?new LaminarSeparationModel().classify(signals,filter.count()):new TurbulentSeparationModel().classify(signals,filter.count());
			boolean explicit=!"NONE".equals(station.event());SeparationState filtered=filter.update(raw.state(),explicit);
			out.add(new SeparationClassification(filtered,raw.severity(),filter.count(),signals,raw.reasons(),raw.confidence()));previous=state;
		}
		return List.copyOf(out);
	}
}
