package info.openrocket.core.aerodynamics.physicsaero.subsonic;

import java.util.ArrayList;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricEdgeStateHistory;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.geometry.*;
public final class SubsonicComponentAssembler {
	public record Result(AerodynamicCoefficients coefficients,AxisymmetricEdgeStateHistory bodyEdgeStates,List<String> methods,List<String> diagnostics,double confidence){public Result{methods=List.copyOf(methods);diagnostics=List.copyOf(diagnostics);}}
	public Result evaluate(AeroGeometry geometry,FlowCondition flow){
		if(flow.mach()>=.95)throw new IllegalArgumentException("SUBSONIC_COMPONENT_RANGE");double ref=geometry.references().referenceAreaM2(),length=geometry.references().referenceLengthM();
		double alphaT=Math.atan(Math.hypot(Math.tan(flow.alphaRad()),Math.tan(flow.betaRad()))),planform=geometry.components().stream().filter(c->c.axisymmetricProfile()!=null).mapToDouble(AeroComponent::projectedAreaM2).sum();
		double bodyMag=new JorgensenBodyForceModel().normalCoefficient(alphaT,geometry.references().exposedBaseAreaM2(),planform,ref),bodySlope=alphaT>1e-12?bodyMag/alphaT:2*geometry.references().exposedBaseAreaM2()/ref;
		double finSlope=0;for(AeroComponent c:geometry.components())if(c.finGeometry()!=null){FinGeometry f=c.finGeometry();double ar=f.spanM()*f.spanM()/f.planformAreaM2();finSlope+=new SubsonicDatcomFinModel().liftSlopePerRad(flow.mach(),ar,0,1)*f.planformAreaM2()/ref;}
		double totalSlope=bodySlope+finSlope,cn=totalSlope*flow.alphaRad(),cy=totalSlope*flow.betaRad();double re=Math.max(1,flow.atmosphere().densityKgM3()*flow.velocityBody().length()*geometry.references().maximumBodyDiameterM()/flow.atmosphere().dynamicViscosityPaS());
		double cpb=new SubsonicBaseDragModel().basePressureCoefficient(flow.mach(),re),ca=-cpb*geometry.references().exposedBaseAreaM2()/ref;double xac=.6*geometry.references().vehicleLengthM();double arm=(xac-geometry.references().momentOriginM().x)/length;
		List<String> diagnostics=new ArrayList<>();AxisymmetricEdgeStateHistory edge;
		try{edge=new SubsonicBodyEdgeStateModel().evaluate(geometry,flow);}
		catch(IllegalArgumentException ex){
			// Edge states are diagnostic output in the subsonic assembler; the component correlations
			// above do not consume them.  A source-distribution pressure validity failure therefore must
			// not discard otherwise valid coefficients, but it must remain visible to callers.
			edge=new AxisymmetricEdgeStateHistory(List.of(),List.of());
			diagnostics.add("SUBSONIC_EDGE_STATE_INVALID:"+ex.getMessage());
		}
		if(alphaT>Math.toRadians(12))diagnostics.add("NONLINEAR_CROSSFLOW_NEAR_LIMIT");
		return new Result(new AerodynamicCoefficients(ca,cn,cy,0,-cn*arm,cy*arm),edge,List.of("SUBSONIC_SOURCE_DISTRIBUTION_V1","JORGENSEN_BODY_FORCE_V1","SUBSONIC_DATCOM_FIN_V1","SUBSONIC_BASE_V1"),diagnostics,.75);
	}
}
