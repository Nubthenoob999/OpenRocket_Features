package info.openrocket.core.aerodynamics.physicsaero.integration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.subsonic.KarmanTsienCorrection;
import info.openrocket.core.aerodynamics.physicsaero.thermodynamics.ThermallyPerfectAir;
import info.openrocket.core.aerodynamics.physicsaero.transonic.TransonicDragRiseModel;
public final class PhysicsAeroValidationGate {
	public enum Status{PASS,FAIL}
	public record Report(Status status,Instant evaluatedAt,List<String> checks,List<String> failures,String version){public Report{checks=List.copyOf(checks);failures=List.copyOf(failures);}}
	public Report evaluate(){List<String>checks=new ArrayList<>(),failures=new ArrayList<>();try{var kt=new KarmanTsienCorrection().apply(-.2,.5);if(!kt.valid())failures.add("KARMAN_TSIEN");else checks.add("KARMAN_TSIEN");var p=new TransonicDragRiseModel.Parameters(.8,.9,1.02,.2,1.35,2,4,"gate");double peak=new TransonicDragRiseModel().deltaCd(1.02,1,p);if(Math.abs(peak-.2)>1e-12)failures.add("TRANSONIC_PEAK");else checks.add("TRANSONIC_PEAK");double g=new ThermallyPerfectAir().gamma(1000);if(!(g>1.2&&g<1.4))failures.add("THERMALLY_PERFECT_AIR");else checks.add("THERMALLY_PERFECT_AIR");}catch(RuntimeException e){failures.add(e.toString());}return new Report(failures.isEmpty()?Status.PASS:Status.FAIL,Instant.now(),checks,failures,"physics-aero-v1-phase6");}
}
