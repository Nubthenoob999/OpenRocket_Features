package info.openrocket.core.aerodynamics.physicsaero.thermodynamics;
/** NASA Glenn seven-coefficient ideal-gas species polynomial, pinned by temperature interval. */
public record NasaPolynomialSpecies(String name,double molarMassKgMol,int nitrogenAtoms,int oxygenAtoms,double minimumK,double maximumK,double[] coefficients,String source){
	public static final double RU=8.31446261815324;
	public NasaPolynomialSpecies{coefficients=coefficients.clone();if(name==null||molarMassKgMol<=0||minimumK<=0||maximumK<=minimumK||coefficients.length!=7||source==null)throw new IllegalArgumentException();}
	@Override public double[] coefficients(){return coefficients.clone();}
	public double cpMolar(double t){check(t);double[]a=coefficients;return RU*(a[0]+a[1]*t+a[2]*t*t+a[3]*t*t*t+a[4]*t*t*t*t);}
	public double enthalpyMolar(double t){check(t);double[]a=coefficients;return RU*t*(a[0]+a[1]*t/2+a[2]*t*t/3+a[3]*t*t*t/4+a[4]*t*t*t*t/5+a[5]/t);}
	public double entropyMolar(double t){check(t);double[]a=coefficients;return RU*(a[0]*Math.log(t)+a[1]*t+a[2]*t*t/2+a[3]*t*t*t/3+a[4]*t*t*t*t/4+a[6]);}
	public double gibbsMolar(double t){return enthalpyMolar(t)-t*entropyMolar(t);}
	private void check(double t){if(t<minimumK||t>maximumK)throw new IllegalArgumentException(name+" NASA polynomial outside range");}
}
