package info.openrocket.core.aerodynamics.physicsaero.subsonic;

import java.util.ArrayList;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.api.*;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.force.ForceContribution;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.util.Coordinate;

/** Independent low-speed equation adapter; it never imports the legacy calculator or owns skin/base drag. */
public final class BarrowmanLowSpeedAdapter {
	public List<ForceContribution> evaluate(AeroGeometry geometry,FlowCondition flow){
		if(flow.mach()>.35 || Math.abs(flow.alphaRad())>Math.toRadians(15)
				|| Math.abs(flow.betaRad())>Math.toRadians(5))
			throw new IllegalArgumentException("BARROWMAN_LOW_SPEED_OUT_OF_RANGE");
		double q=flow.dynamicPressurePa(),area=geometry.references().referenceAreaM2(),slope=2,normal=q*area*slope;
		List<ForceContribution> out=new ArrayList<>();double x=.65*geometry.references().vehicleLengthM();
		// Alpha and beta are components of one authoritative body-normal force owner.  Emitting one
		// owner per axis collides in the ownership ledger whenever both angles are non-zero.
		if(flow.alphaRad()!=0||flow.betaRad()!=0)out.add(force("barrowman-body",PhysicalTerm.BODY_NORMAL_POTENTIAL,
				new Coordinate(0,normal*flow.betaRad(),normal*flow.alphaRad()),x));
		return List.copyOf(out);
	}
	private ForceContribution force(String id,PhysicalTerm term,Coordinate force,double x){return new ForceContribution(id,new PhysicalOwner(term,OwnershipMode.REPLACES,"low-speed",null),new MethodId("BARROWMAN_LOW_SPEED_ADAPTER_V1"),force,new Coordinate(),new Coordinate(x,0,0),"low-speed",List.of("NO_SKIN_FRICTION","NO_BASE_DRAG"),.8,.15,null);}
}
