package info.openrocket.core.aerodynamics.physicsaero.transonic;

import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.*;
public final class EmbeddedShockModel {
	public ShockEvent terminatePocket(double xM,SurfaceState upstream,ThermodynamicModel gas){
		if(upstream.staticState().mach()<=1)throw new IllegalArgumentException("LOCAL_UPSTREAM_MACH_NOT_SUPERSONIC");
		ShockSolution shock=NormalShockCalculator.solve(upstream.staticState(),gas);SurfaceState downstream=SurfaceState.of(xM,upstream.radiusM(),shock.downstream(),shock.downstreamTotal(),upstream.flowAngleRad(),upstream.pressureCoefficient(),gas,"EMBEDDED_WEAK_NORMAL_SHOCK_V1");
		return new ShockEvent(xM,0,Math.PI/2,shock.attachment(),shock.downstreamTotal().pressurePa()/shock.upstreamTotal().pressurePa(),upstream,downstream,"EMBEDDED_TRANSONIC_SHOCK_V1");
	}
}
