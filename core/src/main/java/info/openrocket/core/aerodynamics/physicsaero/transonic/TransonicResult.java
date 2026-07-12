package info.openrocket.core.aerodynamics.physicsaero.transonic;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
public record TransonicResult(AerodynamicCoefficients coefficients,double criticalMach,double dragDivergenceMach,double sonicAreaFraction,double excessDragCoefficient,double confidence,List<String> diagnostics){public TransonicResult{diagnostics=List.copyOf(diagnostics);}}
