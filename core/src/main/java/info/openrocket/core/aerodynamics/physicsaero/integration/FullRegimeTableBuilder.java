package info.openrocket.core.aerodynamics.physicsaero.integration;

import java.util.*;
import info.openrocket.core.aerodynamics.physicsaero.blending.*;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.*;
import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.force.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ThermodynamicModel;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.subsonic.*;
import info.openrocket.core.aerodynamics.physicsaero.table.*;
import info.openrocket.core.aerodynamics.physicsaero.thermodynamics.*;
import info.openrocket.core.aerodynamics.physicsaero.transonic.*;
import info.openrocket.core.util.Coordinate;

/** Deterministic Mach-0-to-7 component table. Supersonic Phases 2-5 remain authoritative in their validated range. */
public final class FullRegimeTableBuilder {
	public AerodynamicTable build(AeroGeometry geometry,double[] mach,double[] alpha,double[] beta,AtmosphereState atmosphere,ThermodynamicModel gas,TableMetadata metadata){
		validateAxes(mach,alpha,beta);TableAxes axes=new TableAxes(mach,alpha,beta);Map<Double,AerodynamicTable> supersonic=new HashMap<>();List<TableCell> cells=new ArrayList<>();
		for(double m:mach)if(m>=1.2&&m<=5)supersonic.put(m,new CombinedBodyFinTableBuilder(false,true).build(geometry,new double[]{m},alpha,beta,atmosphere,gas,metadata));
		for(int mi=0;mi<mach.length;mi++)for(int ai=0;ai<alpha.length;ai++)for(int bi=0;bi<beta.length;bi++)cells.add(cell(geometry,mach[mi],alpha[ai],beta[bi],ai,bi,atmosphere,gas,supersonic.get(mach[mi])));
		return new AerodynamicTable(axes,cells,metadata);
	}
	private TableCell cell(AeroGeometry g,double m,double alpha,double beta,int ai,int bi,AtmosphereState atm,ThermodynamicModel gas,AerodynamicTable sup){
		double q=Math.max(1,.5*atm.densityKgM3()*Math.pow(m*gas.speedOfSound(atm.temperatureK()),2));ReferenceState reference=new ReferenceState(q,g.references().referenceAreaM2(),g.references().referenceLengthM(),g.references().momentOriginM());
		AerodynamicCoefficients c;List<String>methods=new ArrayList<>(),validity=new ArrayList<>(),messages=new ArrayList<>();double confidence;
		if(m<.9){double evaluationMach=m==0?1e-9:m;FlowCondition flow=FlowCondition.fromAngles(evaluationMach,alpha,beta,atm,gas,false,g.geometryHash());var corr=new SubsonicComponentAssembler().evaluate(g,flow);c=corr.coefficients();methods.addAll(corr.methods());messages.addAll(corr.diagnostics());confidence=corr.confidence();if(m==0)validity.add("MACH_ZERO_COEFFICIENT_LIMIT");if(m<.35){ContributionLedger ledger=new ContributionLedger();new BarrowmanLowSpeedAdapter().evaluate(g,flow).forEach(ledger::add);AerodynamicCoefficients bar=ledger.entries().isEmpty()?new AerodynamicCoefficients(c.ca(),0,0,0,0,0):CoefficientAssembler.assemble(ledger,reference);double w=new LowSpeedRegimeSelector().correlationWeight(m);c=new ComponentRegimeBlender().blend(bar,1-w,c,w==0?1e-12:w);methods.add("BARROWMAN_LOW_SPEED_ADAPTER_V1");validity.add(m>=.25?"MACH_0P3_SMOOTH_OVERLAP":"BARROWMAN_OWNER");}}
		else if(m<1.2){c=transonic(g,m,alpha,beta);methods.add("DEDICATED_TRANSONIC_ROCKET_PEAK_V1");validity.add("NEAR_SONIC");validity.add("TRANSONIC_CORRELATION_DOMINANT");confidence=.45;}
		else if(m<=5){TableCell s=sup.cell(0,ai,bi);c=s.coefficients();methods.addAll(s.methodIds());validity.addAll(s.validityFlags());messages.addAll(s.diagnostics().messages());confidence=.7;}
		else{c=highMach(g,m,alpha,beta,atm,gas);HighMachThermodynamicSelector.Selection selection=new HighMachThermodynamicSelector().select(m,atm.temperatureK(),m*gas.speedOfSound(atm.temperatureK()));if(selection.model()==HighMachThermodynamicSelector.Model.INVALID_IONIZATION)throw new IllegalArgumentException(selection.reason());methods.add(selection.model().name()+"_HIGH_MACH_PRESSURE_V1");validity.add(selection.reason());confidence=selection.model()==HighMachThermodynamicSelector.Model.EQUILIBRIUM_AIR?.5:.65;}
		double[]conf=new double[6],unc=new double[6];Arrays.fill(conf,confidence);Arrays.fill(unc,1-confidence);Map<String,AerodynamicCoefficients> owners=Map.of("FULL_REGIME_OWNER",c);
		return new TableCell(c,Map.of("vehicle",c),owners,methods.stream().distinct().toList(),conf,unc,validity,reference,new CellDiagnostics(Set.of(DiagnosticFlag.DIRECT_GENERATION),false,null,null,messages),true);
	}
	private AerodynamicCoefficients transonic(AeroGeometry g,double m,double alpha,double beta){double fineness=g.references().vehicleLengthM()/g.references().maximumBodyDiameterM(),mcr=new BodyCriticalMachEstimator().estimate(1/fineness,0,Math.hypot(alpha,beta)).criticalMach();double mdd=Math.max(mcr+.04,new FinDragDivergenceEstimator().estimate(.05,0,0,.92).dragDivergenceMach());var p=new TransonicDragRiseHierarchy().select(fineness,.05,mcr,mdd);double ca=.12+new TransonicDragRiseModel().deltaCd(m,Math.max(0,Math.min(1,(m-mcr)/.15)),p);double slope=2.5*new TransonicFinLiftCorrection().factor(m,.05,2),cn=slope*alpha,cy=slope*beta,xac=new TransonicMomentModel().aerodynamicCenterFraction(m,.55,.65)*g.references().vehicleLengthM(),arm=(xac-g.references().momentOriginM().x)/g.references().referenceLengthM();return new AerodynamicCoefficients(ca,cn,cy,0,-cn*arm,cy*arm);}
	private AerodynamicCoefficients highMach(AeroGeometry g,double m,double alpha,double beta,AtmosphereState atm,ThermodynamicModel gas){HighMachThermodynamicSelector.Selection s=new HighMachThermodynamicSelector().select(m,atm.temperatureK(),m*gas.speedOfSound(atm.temperatureK()));double gamma=s.model()==HighMachThermodynamicSelector.Model.PERFECT_GAS?gas.gamma(atm.temperatureK()):new ThermallyPerfectAir().gamma(Math.min(5999,s.stagnationTemperatureK()));double ca=.25/m+.08*Math.exp(-1.5*(m-1.05));double slope=4/Math.sqrt(Math.max(.1,m*m-1)),cn=slope*alpha,cy=slope*beta;return new AerodynamicCoefficients(ca,cn,cy,0,-.55*cn,.55*cy);}
	private void validateAxes(double[]m,double[]a,double[]b){if(m[0]<0||m[m.length-1]>7||a[0]<-Math.toRadians(15)||a[a.length-1]>Math.toRadians(15)||b[0]<-Math.toRadians(5)||b[b.length-1]>Math.toRadians(5))throw new IllegalArgumentException("PHASE6_DOMAIN_EXCEEDED");for(double x:a)for(double y:b)if(Math.atan(Math.hypot(Math.tan(x),Math.tan(y)))>Math.toRadians(15)+1e-12)throw new IllegalArgumentException("COMBINED_INCIDENCE_OUTSIDE_VALIDATED_RANGE");}
}
