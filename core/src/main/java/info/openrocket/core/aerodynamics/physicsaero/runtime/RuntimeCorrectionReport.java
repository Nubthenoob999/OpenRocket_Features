package info.openrocket.core.aerodynamics.physicsaero.runtime;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
public record RuntimeCorrectionReport(AerodynamicCoefficients original,AerodynamicCoefficients corrected,double referenceReynolds,double currentReynolds,boolean valid,String methodId,List<String> diagnostics){public RuntimeCorrectionReport{diagnostics=List.copyOf(diagnostics);}}
