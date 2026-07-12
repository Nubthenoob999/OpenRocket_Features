package info.openrocket.core.aerodynamics.physicsaero.integration;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ThermodynamicModel;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.table.*;
public final class PhysicsAeroTableService {
	public record Request(AeroGeometry geometry,double[] mach,double[] alphaRad,double[] betaRad,AtmosphereState atmosphere,ThermodynamicModel gas,String settingsHash,Path tableFile,Path manifestFile){ }
	public record Result(AerodynamicTable table,Path tableFile,Path manifestFile,PhysicsAeroValidationGate.Report validation,String tableHash){ }
	public Result build(Request request,AtomicBoolean cancelled,IntConsumer progress)throws IOException{
		var validation=new PhysicsAeroValidationGate().evaluate();if(validation.status()!=PhysicsAeroValidationGate.Status.PASS)throw new IllegalStateException("HEADLESS_VALIDATION_GATE_FAILED");if(cancelled.get())throw new IllegalStateException("BUILD_CANCELLED");progress.accept(5);
		TableMetadata metadata=new TableMetadata(TableMetadata.CURRENT_SCHEMA,request.geometry().geometryHash(),request.settingsHash(),"phase6","full-regime-v1","SI;radians","OPENROCKET_BODY_AXES_V1",Instant.now(),Map.of("referenceAreaM2",request.geometry().references().referenceAreaM2()),Map.of("joinJump",.01),"EXPERIMENTAL_HEADLESS_PASS");
		AerodynamicTable table=new FullRegimeTableBuilder().build(request.geometry(),request.mach(),request.alphaRad(),request.betaRad(),request.atmosphere(),request.gas(),metadata);if(cancelled.get())throw new IllegalStateException("BUILD_CANCELLED_LAST_VALID_TABLE_UNCHANGED");progress.accept(90);
		for(TableCell cell:table.cells())new LegacyRomRemovalGuard().verify(cell);new TableWriter().write(table,request.tableFile(),request.manifestFile());progress.accept(100);return new Result(table,request.tableFile(),request.manifestFile(),validation,request.geometry().geometryHash()+":"+request.settingsHash());
	}
}
