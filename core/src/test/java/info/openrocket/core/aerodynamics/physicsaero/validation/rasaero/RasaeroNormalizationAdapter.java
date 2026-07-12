package info.openrocket.core.aerodynamics.physicsaero.validation.rasaero;

import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.validation.rasaero.RasaeroAeroPlotReader.SourceRow;
import info.openrocket.core.aerodynamics.physicsaero.validation.rasaero.RasaeroColumnMap.Column;

public final class RasaeroNormalizationAdapter {
	private static final double INCH_TO_M = 0.0254;
	public List<NormalizedRow> normalize(SourceRow source, Context context) {
		double alphaDeg = source.value(Column.ALPHA), alphaRad = Math.toRadians(alphaDeg);
		double cpM = source.value(Column.CP) * INCH_TO_M, referenceArea = Math.PI * context.referenceDiameterM() * context.referenceDiameterM() / 4;
		return List.of(row(source, context, PowerState.OFF, source.value(Column.CA_OFF), source.value(Column.CD_OFF), alphaDeg, alphaRad, cpM, referenceArea),
				row(source, context, PowerState.ON, source.value(Column.CA_ON), source.value(Column.CD_ON), alphaDeg, alphaRad, cpM, referenceArea));
	}
	private static NormalizedRow row(SourceRow source, Context context, PowerState powerState, double ca, double cd,
			double alphaDeg, double alphaRad, double cpM, double referenceArea) {
		return new NormalizedRow(context.caseId(), context.geometryId(), source.value(Column.MACH), source.value(Column.REYNOLDS),
				alphaRad, alphaDeg, powerState, ca, cd, source.value(Column.CL), source.value(Column.CN), source.value(Column.CN_POTENTIAL),
				source.value(Column.CN_VISCOUS), source.value(Column.CN_ALPHA), cpM, cpM / context.referenceDiameterM(), referenceArea,
				context.referenceLengthM(), context.altitudeM(), context.sourceFile(), context.sourceSha256(), "NOSE_TIP");
	}
	public enum PowerState { OFF, ON }
	public record Context(String caseId, String geometryId, double referenceDiameterM, double referenceLengthM,
			double altitudeM, String sourceFile, String sourceSha256) {
		public Context { if (caseId == null || caseId.isBlank() || geometryId == null || geometryId.isBlank() || referenceDiameterM <= 0 || referenceLengthM <= 0 || sourceFile == null || sourceSha256 == null) throw new IllegalArgumentException("invalid normalization context"); }
	}
	public record NormalizedRow(String caseId, String geometryId, double mach, double reynolds, double alphaRad,
			double alphaDeg, PowerState powerState, double ca, double cd, double cl, double cn, double cnPotential,
			double cnViscous, double cnAlphaPerRad, double cpM, double cpCalibers, double referenceAreaM2,
			double referenceLengthM, double altitudeM, String sourceFile, String sourceSha256, String cpOrigin) {}
}
