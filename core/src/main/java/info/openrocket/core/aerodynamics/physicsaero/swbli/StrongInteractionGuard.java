package info.openrocket.core.aerodynamics.physicsaero.swbli;
import java.util.ArrayList;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ShockSolution;
public final class StrongInteractionGuard {
	public record Result(boolean valid,List<String> reasons,double confidence){public Result{reasons=List.copyOf(reasons);}}
	public Result evaluate(ShockInteractionInput input){
		List<String> reasons=new ArrayList<>();
		if(input.attachment()==ShockSolution.Attachment.DETACHED)reasons.add("DETACHED_SHOCK_GEOMETRY_UNRESOLVED");
		if(input.shockShockInteraction())reasons.add("SHOCK_SHOCK_OR_EDNEY_PATTERN");
		if(input.geometryDiscontinuity())reasons.add("SEPARATION_REACHES_GEOMETRY_DISCONTINUITY");
		if(input.strongCrossflow())reasons.add("STRONGLY_THREE_DIMENSIONAL_INCOMING_LAYER");
		if(input.displacementRatio()>.1||input.delta99Ratio()>.25)reasons.add("BOUNDARY_LAYER_NOT_THIN");
		return new Result(reasons.isEmpty(),reasons,reasons.isEmpty()?.9:.15);
	}
}
