package info.openrocket.core.aerodynamics.physicsaero.subsonic;

import java.util.ArrayList;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricEdgeStateHistory;
import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.geometry.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ThermodynamicModel;

public final class SubsonicBodyEdgeStateModel {
	public AxisymmetricEdgeStateHistory evaluate(AeroGeometry geometry,FlowCondition flow){
		if(flow.mach()>=1||flow.mach()<0)throw new IllegalArgumentException("SUBSONIC_EDGE_STATE_ONLY");
		java.util.TreeMap<Double,GeometryStation> merged=new java.util.TreeMap<>();geometry.components().stream().filter(c->c.axisymmetricProfile()!=null)
				.flatMap(c->c.axisymmetricProfile().stations().stream()).forEach(s->merged.put(s.xM(),s));List<GeometryStation> stations=List.copyOf(merged.values());
		if(stations.size()<2)throw new IllegalArgumentException("NO_AXISYMMETRIC_PROFILE");double vinf=flow.velocityBody().length(),q=.5*flow.atmosphere().densityKgM3()*vinf*vinf;
		ThermodynamicModel gas=flow.thermodynamics();List<SurfaceState> states=new ArrayList<>();AxisymmetricSourceDistribution source=new AxisymmetricSourceDistribution();
		for(int i=0;i<stations.size();i++){
			GeometryStation g=stations.get(i);var perturb=source.velocityAt(stations,i,vinf);double tx=1/Math.sqrt(1+g.slope()*g.slope()),tr=g.slope()*tx;
			double edge=Math.max(0,(vinf+perturb.axialMS())*tx+perturb.radialMS()*tr);double cp0=vinf>0?1-edge*edge/(vinf*vinf):0;
			double cp=cp0;if(flow.mach()>.3){var corrected=new KarmanTsienCorrection().apply(cp0,flow.mach());if(!corrected.valid())throw new IllegalArgumentException(corrected.reason());cp=corrected.pressureCoefficient();}
			double p=flow.atmosphere().pressurePa()+cp*q;if(p<=0)throw new IllegalArgumentException("NONPOSITIVE_LOCAL_PRESSURE");
			double totalT=flow.atmosphere().temperatureK()*(1+(gas.gamma(flow.atmosphere().temperatureK())-1)*.5*flow.mach()*flow.mach());
			double localMach=vinf==0?0:Math.max(0,edge/gas.speedOfSound(flow.atmosphere().temperatureK()));double t=totalT/(1+(gas.gamma(totalT)-1)*.5*localMach*localMach);
			double rho=p/(gas.gasConstant()*t);GasState local=new GasState(localMach,p,t,rho,edge);
			double totalP=p*Math.pow(1+(gas.gamma(t)-1)*.5*localMach*localMach,gas.gamma(t)/(gas.gamma(t)-1));
			states.add(SurfaceState.of(g.xM(),g.radiusM(),local,new TotalState(totalP,totalT,totalP/(gas.gasConstant()*totalT)),Math.atan(g.slope()),cp,gas,"SUBSONIC_SOURCE_DISTRIBUTION_V1"));
		}
		return new AxisymmetricEdgeStateHistory(states,List.of());
	}
}
