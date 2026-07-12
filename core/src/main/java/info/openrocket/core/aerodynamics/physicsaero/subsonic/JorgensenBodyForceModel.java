package info.openrocket.core.aerodynamics.physicsaero.subsonic;
public final class JorgensenBodyForceModel {
	public double normalCoefficient(double alphaTotalRad,double baseAreaM2,double planformAreaM2,double referenceAreaM2){
		if(referenceAreaM2<=0||baseAreaM2<0||planformAreaM2<0||Math.abs(alphaTotalRad)>Math.toRadians(20))throw new IllegalArgumentException("outside Jorgensen range");
		return 2*baseAreaM2/referenceAreaM2*Math.sin(alphaTotalRad)*Math.cos(alphaTotalRad)+1.1*planformAreaM2/referenceAreaM2*Math.sin(alphaTotalRad)*Math.abs(Math.sin(alphaTotalRad));
	}
}
