package info.openrocket.core.aerodynamics.physicsaero.subsonic;
public final class AllenPerkinsCrossflowModel {
	public double coefficient(double normalMach,double reynoldsDiameter,double alphaTotalRad,double planformArea,double referenceArea,double efficiency){
		if(normalMach<0||reynoldsDiameter<=0||referenceArea<=0||efficiency<0||efficiency>1.5)throw new IllegalArgumentException("invalid crossflow state");
		double cylinderCd=reynoldsDiameter<2e5?1.2:.9;return efficiency*cylinderCd*Math.sin(alphaTotalRad)*Math.abs(Math.sin(alphaTotalRad))*planformArea/referenceArea;
	}
}
