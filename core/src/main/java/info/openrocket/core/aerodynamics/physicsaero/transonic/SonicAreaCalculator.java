package info.openrocket.core.aerodynamics.physicsaero.transonic;
public final class SonicAreaCalculator {
	public double fraction(double[] localMach,double[] area,double tolerance){if(localMach.length!=area.length||area.length==0)throw new IllegalArgumentException();double sonic=0,total=0;for(int i=0;i<area.length;i++){if(area[i]<0||!Double.isFinite(localMach[i]))throw new IllegalArgumentException();total+=area[i];if(localMach[i]>=1-tolerance)sonic+=area[i];}return total>0?sonic/total:0;}
}
