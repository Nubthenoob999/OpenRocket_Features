package info.openrocket.core.aerodynamics.physicsaero.runtime;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
public final class RuntimeAtmosphereCorrection {
	public RuntimeCorrectionReport apply(AerodynamicCoefficients base,double currentRe,ReynoldsSensitivitySurface surface,boolean topologyWouldChange){double ratio=currentRe/surface.referenceReynolds();if(ratio<surface.minimumRatio()||ratio>surface.maximumRatio()||topologyWouldChange&&surface.topologySensitive())return new RuntimeCorrectionReport(base,base,surface.referenceReynolds(),currentRe,false,"REBUILD_REQUIRED",List.of(topologyWouldChange?"TOPOLOGY_CHANGE_REQUIRES_TABLE_VARIANT":"REYNOLDS_OUTSIDE_SENSITIVITY_RANGE"));double log=Math.log(ratio);double[]a=base.toArray(),d=surface.derivatives();for(int i=0;i<6;i++)a[i]+=d[i]*log;return new RuntimeCorrectionReport(base,new AerodynamicCoefficients(a[0],a[1],a[2],a[3],a[4],a[5]),surface.referenceReynolds(),currentRe,true,"NAMED_LOG_REYNOLDS_SENSITIVITY_V1",List.of(surface.sourceId()));}
}
