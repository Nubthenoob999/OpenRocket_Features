package info.openrocket.core.aerodynamics.physicsaero.swbli;

import java.util.LinkedHashMap;
import java.util.Map;
import info.openrocket.core.aerodynamics.physicsaero.force.ReferenceState;

/** Stores named response and thermal scenarios; it never averages them into the nominal coefficient. */
public final class SwbliUncertaintyRunner {
	public record Envelope(ShockInteractionResult nominal,Map<String,ShockInteractionResult> scenarios,double minimumResidualCd,double maximumResidualCd,
			String minimumScenario,String maximumScenario){public Envelope{scenarios=Map.copyOf(scenarios);}}
	public Envelope run(ShockInteractionInput input,ReferenceState reference){
		ShockBoundaryLayerCoupler coupler=new ShockBoundaryLayerCoupler();Map<String,ShockInteractionResult> values=new LinkedHashMap<>();
		values.put("weak-response",coupler.couple(input.withPressureRiseScale(.8),reference));
		values.put("nominal",coupler.couple(input,reference));
		values.put("strong-response",coupler.couple(input.withPressureRiseScale(1.2),reference));
		values.put("cold-wall",coupler.couple(input.withWallTemperatureRatio(.6),reference));
		values.put("adiabatic-wall",coupler.couple(input.withWallTemperatureRatio(1),reference));
		values.put("hot-wall",coupler.couple(input.withWallTemperatureRatio(1.4),reference));
		double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;String minName="",maxName="";
		for(var e:values.entrySet()){double cd=e.getValue().drag().residualDeltaCd();if(cd<min){min=cd;minName=e.getKey();}if(cd>max){max=cd;maxName=e.getKey();}}
		return new Envelope(values.get("nominal"),values,min,max,minName,maxName);
	}
}
