package info.openrocket.core.aerodynamics.physicsaero.subsonic;
public final class JorgensenBodyForceModel {
	/**
	 * The Jorgensen slender-body crossflow formulation is a very-high-incidence
	 * method.  Ninety degrees is the complete unique radial-incidence interval;
	 * orientations beyond it are represented by the opposite body direction.
	 */
	public static final double MAX_INCIDENCE_RAD = Math.PI / 2;

	public double normalCoefficient(double alphaTotalRad,double baseAreaM2,double planformAreaM2,double referenceAreaM2){
		if(referenceAreaM2<=0||baseAreaM2<0||planformAreaM2<0||Math.abs(alphaTotalRad)>MAX_INCIDENCE_RAD)throw new IllegalArgumentException("outside Jorgensen/Galejs radial-incidence range");
		return 2*baseAreaM2/referenceAreaM2*Math.sin(alphaTotalRad)*Math.cos(alphaTotalRad)+1.1*planformAreaM2/referenceAreaM2*Math.sin(alphaTotalRad)*Math.abs(Math.sin(alphaTotalRad));
	}
}
