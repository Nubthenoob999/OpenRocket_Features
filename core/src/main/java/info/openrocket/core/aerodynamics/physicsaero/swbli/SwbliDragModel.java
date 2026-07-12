package info.openrocket.core.aerodynamics.physicsaero.swbli;

import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.api.*;
import info.openrocket.core.aerodynamics.physicsaero.force.*;
import info.openrocket.core.util.Coordinate;

/** Reconciles component correlation drag against pressure already integrated locally. */
public final class SwbliDragModel {
	public record Result(double correlationDeltaCd,double localDeltaCd,double residualDeltaCd,ForceContribution residualForce){ }
	public Result reconcile(ShockInteractionInput input,double severity,double localDeltaCd,ReferenceState reference){
		if(severity<0||severity>1||localDeltaCd<0)throw new IllegalArgumentException("invalid SWBLI drag reconciliation");
		double correlation=Math.min(.25,.04*severity*severity*input.pressureRatio());
		double residual=Math.max(0,correlation-localDeltaCd);double forceN=residual*reference.dynamicPressurePa()*reference.referenceAreaM2();
		PhysicalOwner owner=new PhysicalOwner(PhysicalTerm.SEPARATION_DRAG,OwnershipMode.REPLACES,input.interactionId(),null);
		ForceContribution force=new ForceContribution(input.componentId(),owner,new MethodId("swbli-residual-reconciliation-v1"),new Coordinate(-forceN,0,0),
				new Coordinate(),new Coordinate(input.locationM(),0,0),input.interactionId(),List.of("EXCLUDES_LOCAL_PRESSURE_INCREMENT","BOUNDED_DELTA_CD"),.6,.35,null);
		return new Result(correlation,localDeltaCd,residual,force);
	}
}
