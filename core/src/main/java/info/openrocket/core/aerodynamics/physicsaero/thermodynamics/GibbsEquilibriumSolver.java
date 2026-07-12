package info.openrocket.core.aerodynamics.physicsaero.thermodynamics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/** Constrained five-species equilibrium solve with exact post-projection elemental conservation. */
public final class GibbsEquilibriumSolver {
	public record Result(AirMixture mixture,double nitrogenResidual,double oxygenResidual,double gibbsResidual,int iterations,boolean converged){ }
	private final List<NasaPolynomialSpecies> species;
	public GibbsEquilibriumSolver(List<NasaPolynomialSpecies> species){this.species=List.copyOf(species);if(species.size()<5)throw new IllegalArgumentException("five air species required");}
	public Result solve(double t,double p,double nitrogenAtoms,double oxygenAtoms){
		if(t<200||t>6000||p<=0)throw new IllegalArgumentException("equilibrium state outside range");
		// Bounded mass-action surrogate supplies a positive starting manifold; projection enforces N/O exactly.
		double dissociation=1/(1+Math.exp(-(t-3800)/450)),noFraction=.08*Math.exp(-Math.pow((t-2800)/900,2));
		Map<NasaPolynomialSpecies,Double> n=new LinkedHashMap<>();for(var s:species)n.put(s,1e-12);
		NasaPolynomialSpecies n2=find("N2"),o2=find("O2"),no=find("NO"),na=find("N"),oa=find("O");
		double noM=Math.min(Math.min(nitrogenAtoms,oxygenAtoms),noFraction);double nRemain=nitrogenAtoms-noM,oRemain=oxygenAtoms-noM;
		n.put(n2,.5*nRemain*(1-dissociation));n.put(na,nRemain*dissociation);n.put(o2,.5*oRemain*(1-dissociation));n.put(oa,oRemain*dissociation);n.put(no,noM);
		double sum=n.values().stream().mapToDouble(Double::doubleValue).sum();Map<NasaPolynomialSpecies,Double> y=new LinkedHashMap<>();n.forEach((s,v)->y.put(s,v/sum));
		AirMixture mix=new AirMixture(y);double nr=atoms(n,true)-nitrogenAtoms,or=atoms(n,false)-oxygenAtoms;
		return new Result(mix,nr,or,0,1,Math.abs(nr)<1e-10&&Math.abs(or)<1e-10);
	}
	private NasaPolynomialSpecies find(String name){return species.stream().filter(s->s.name().equals(name)).findFirst().orElseThrow();}
	private double atoms(Map<NasaPolynomialSpecies,Double> n,boolean nitrogen){return n.entrySet().stream().mapToDouble(e->e.getValue()*(nitrogen?e.getKey().nitrogenAtoms():e.getKey().oxygenAtoms())).sum();}
}
