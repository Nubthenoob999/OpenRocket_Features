package info.openrocket.core.aerodynamics.physicsaero.thermodynamics;
import java.util.LinkedHashMap;
import java.util.Map;
public record AirMixture(Map<NasaPolynomialSpecies,Double> moleFractions){
	public AirMixture{double sum=moleFractions.values().stream().mapToDouble(Double::doubleValue).sum();if(Math.abs(sum-1)>1e-8||moleFractions.values().stream().anyMatch(v->v<0))throw new IllegalArgumentException("invalid mixture fractions");moleFractions=Map.copyOf(new LinkedHashMap<>(moleFractions));}
	public double molarMass(){return moleFractions.entrySet().stream().mapToDouble(e->e.getKey().molarMassKgMol()*e.getValue()).sum();}
	public double gasConstant(){return NasaPolynomialSpecies.RU/molarMass();}
	public double cpMass(double t){return moleFractions.entrySet().stream().mapToDouble(e->e.getValue()*e.getKey().cpMolar(t)).sum()/molarMass();}
	public double enthalpyMass(double t){return moleFractions.entrySet().stream().mapToDouble(e->e.getValue()*e.getKey().enthalpyMolar(t)).sum()/molarMass();}
	public double entropyMass(double t,double pressurePa){double s=moleFractions.entrySet().stream().mapToDouble(e->e.getValue()*e.getKey().entropyMolar(t)).sum()/molarMass();return s-gasConstant()*Math.log(pressurePa/101325.0);}
}
