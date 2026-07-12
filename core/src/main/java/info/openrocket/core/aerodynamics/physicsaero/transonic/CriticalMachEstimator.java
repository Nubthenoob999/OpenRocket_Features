package info.openrocket.core.aerodynamics.physicsaero.transonic;
public interface CriticalMachEstimator { Estimate estimate(double thicknessRatio,double sweepRad,double incidenceRad); record Estimate(double criticalMach,double confidence,String methodId){public Estimate{if(criticalMach<=0||criticalMach>=1.2||confidence<0||confidence>1)throw new IllegalArgumentException();}} }
