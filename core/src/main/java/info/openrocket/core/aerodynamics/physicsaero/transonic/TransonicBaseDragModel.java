package info.openrocket.core.aerodynamics.physicsaero.transonic;
public final class TransonicBaseDragModel {public double basePressureCoefficient(double mach,double displacementRatio){if(mach<.7||mach>1.3||displacementRatio<0)throw new IllegalArgumentException();return -Math.min(.45,.18+.16*Math.exp(-Math.pow((mach-1)/.16,2))+.3*displacementRatio);}}
