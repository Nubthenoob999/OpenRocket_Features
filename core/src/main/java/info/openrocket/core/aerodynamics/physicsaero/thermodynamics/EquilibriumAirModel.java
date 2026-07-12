package info.openrocket.core.aerodynamics.physicsaero.thermodynamics;

import java.util.List;
public final class EquilibriumAirModel {
	public static final NasaPolynomialSpecies NO=new NasaPolynomialSpecies("NO",.0300061,1,1,200,6000,new double[]{3.26071234,1.19101135e-3,-4.29122646e-7,6.94481463e-11,-4.03295681e-15,9921.43132,6.36900518},ThermallyPerfectAir.SOURCE);
	public static final NasaPolynomialSpecies N=new NasaPolynomialSpecies("N",.0140067,1,0,200,6000,new double[]{2.41594293,1.74890650e-4,-1.19023667e-7,3.02262449e-11,-2.03609825e-15,56133.775,4.649609},ThermallyPerfectAir.SOURCE);
	public static final NasaPolynomialSpecies O=new NasaPolynomialSpecies("O",.0159994,0,1,200,6000,new double[]{2.54205966,-2.75506191e-5,-3.10280335e-9,4.55106742e-12,-4.36805150e-16,29230.800,4.920308},ThermallyPerfectAir.SOURCE);
	private final GibbsEquilibriumSolver solver=new GibbsEquilibriumSolver(List.of(ThermallyPerfectAir.N2,ThermallyPerfectAir.O2,NO,N,O));
	public GibbsEquilibriumSolver.Result equilibrium(double t,double p){return solver.solve(t,p,1.581,0.419);}
}
