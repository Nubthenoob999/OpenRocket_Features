package info.openrocket.core.tuning;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PhaseTwoDatasetResult {
	private final String datasetName;
	private final boolean airbrakeEnabled;
	private final AbPluginExecutionResult pluginResult;
	private final Map<FlightPhaseWindow, PhaseTwoScoreResult> windowScores;
	private final List<TuningFlag> tuningFlags;
	private final DerivedTelemetryQuantities.Quantities referenceQuantities;
	private final DerivedTelemetryQuantities.Quantities candidateQuantities;
	private final TelemetryParserDiagnostics referenceParserDiagnostics;
	private final TelemetryParserDiagnostics candidateParserDiagnostics;
	private final String datasetClass;
	private final String truthSource;
	private final String candidateSource;
	private final String orkProvenance;
	private final String physicsAeroMode;
	private final String tableSource;
	private final double candidateMaxMach;
	private final String alignmentChannel;
	private final double alignmentLagSec;
	private final double alignmentQuality;
	private final double referenceAlignedApogeeTimeSec;
	private final double candidateAlignedApogeeTimeSec;
	private final double alignedApogeeTimeDeltaSec;
	private final double alignedApogeeTimeErrorSec;
	private final Map<FlightPhaseWindow, PhaseResidualMetrics> phaseResiduals;
	private final VerticalIntegratorDiagnostics integratorDiagnostics;

	public PhaseTwoDatasetResult(String datasetName,
								 boolean airbrakeEnabled,
								 AbPluginExecutionResult pluginResult,
								 Map<FlightPhaseWindow, PhaseTwoScoreResult> windowScores,
								 List<TuningFlag> tuningFlags,
								 DerivedTelemetryQuantities.Quantities referenceQuantities,
								 DerivedTelemetryQuantities.Quantities candidateQuantities,
								 TelemetryParserDiagnostics referenceParserDiagnostics,
								 TelemetryParserDiagnostics candidateParserDiagnostics) {
		this(datasetName,
				airbrakeEnabled,
				pluginResult,
				windowScores,
				tuningFlags,
				referenceQuantities,
				candidateQuantities,
				referenceParserDiagnostics,
				candidateParserDiagnostics,
				"",
				"",
				"",
				"",
				"",
				"",
				Double.NaN,
				"",
				Double.NaN,
				Double.NaN,
				Double.NaN,
				Double.NaN,
				Double.NaN,
				Double.NaN,
				Collections.emptyMap(),
				VerticalIntegratorDiagnostics.EMPTY);
	}

	public PhaseTwoDatasetResult(String datasetName,
								 boolean airbrakeEnabled,
								 AbPluginExecutionResult pluginResult,
								 Map<FlightPhaseWindow, PhaseTwoScoreResult> windowScores,
								 List<TuningFlag> tuningFlags,
								 DerivedTelemetryQuantities.Quantities referenceQuantities,
								 DerivedTelemetryQuantities.Quantities candidateQuantities,
								 TelemetryParserDiagnostics referenceParserDiagnostics,
								 TelemetryParserDiagnostics candidateParserDiagnostics,
								 String datasetClass,
								 String candidateSource,
								 String orkProvenance,
								 String physicsAeroMode,
								 String tableSource,
								 double candidateMaxMach,
								 double referenceAlignedApogeeTimeSec,
								 double candidateAlignedApogeeTimeSec,
								 double alignedApogeeTimeDeltaSec,
								 double alignedApogeeTimeErrorSec) {
		this(datasetName,
				airbrakeEnabled,
				pluginResult,
				windowScores,
				tuningFlags,
				referenceQuantities,
				candidateQuantities,
				referenceParserDiagnostics,
				candidateParserDiagnostics,
				datasetClass,
				"",
				candidateSource,
				orkProvenance,
				physicsAeroMode,
				tableSource,
				candidateMaxMach,
				"",
				Double.NaN,
				Double.NaN,
				referenceAlignedApogeeTimeSec,
				candidateAlignedApogeeTimeSec,
				alignedApogeeTimeDeltaSec,
				alignedApogeeTimeErrorSec,
				Collections.emptyMap(),
				VerticalIntegratorDiagnostics.EMPTY);
	}

	public PhaseTwoDatasetResult(String datasetName,
								 boolean airbrakeEnabled,
								 AbPluginExecutionResult pluginResult,
								 Map<FlightPhaseWindow, PhaseTwoScoreResult> windowScores,
								 List<TuningFlag> tuningFlags,
								 DerivedTelemetryQuantities.Quantities referenceQuantities,
								 DerivedTelemetryQuantities.Quantities candidateQuantities,
								 TelemetryParserDiagnostics referenceParserDiagnostics,
								 TelemetryParserDiagnostics candidateParserDiagnostics,
								 String datasetClass,
								 String truthSource,
								 String candidateSource,
								 String orkProvenance,
								 String physicsAeroMode,
								 String tableSource,
								 double candidateMaxMach,
								 String alignmentChannel,
								 double alignmentLagSec,
								 double alignmentQuality,
								 double referenceAlignedApogeeTimeSec,
								 double candidateAlignedApogeeTimeSec,
								 double alignedApogeeTimeDeltaSec,
								 double alignedApogeeTimeErrorSec,
								 Map<FlightPhaseWindow, PhaseResidualMetrics> phaseResiduals,
								 VerticalIntegratorDiagnostics integratorDiagnostics) {
		this.datasetName = datasetName;
		this.airbrakeEnabled = airbrakeEnabled;
		this.pluginResult = pluginResult;
		this.windowScores = Collections.unmodifiableMap(new LinkedHashMap<>(windowScores));
		this.tuningFlags = Collections.unmodifiableList(tuningFlags);
		this.referenceQuantities = referenceQuantities;
		this.candidateQuantities = candidateQuantities;
		this.referenceParserDiagnostics = referenceParserDiagnostics == null ? TelemetryParserDiagnostics.EMPTY : referenceParserDiagnostics;
		this.candidateParserDiagnostics = candidateParserDiagnostics == null ? TelemetryParserDiagnostics.EMPTY : candidateParserDiagnostics;
		this.datasetClass = datasetClass == null ? "" : datasetClass;
		this.truthSource = truthSource == null ? "" : truthSource;
		this.candidateSource = candidateSource == null ? "" : candidateSource;
		this.orkProvenance = orkProvenance == null ? "" : orkProvenance;
		this.physicsAeroMode = physicsAeroMode == null ? "" : physicsAeroMode;
		this.tableSource = tableSource == null ? "" : tableSource;
		this.candidateMaxMach = candidateMaxMach;
		this.alignmentChannel = alignmentChannel == null ? "" : alignmentChannel;
		this.alignmentLagSec = alignmentLagSec;
		this.alignmentQuality = alignmentQuality;
		this.referenceAlignedApogeeTimeSec = referenceAlignedApogeeTimeSec;
		this.candidateAlignedApogeeTimeSec = candidateAlignedApogeeTimeSec;
		this.alignedApogeeTimeDeltaSec = alignedApogeeTimeDeltaSec;
		this.alignedApogeeTimeErrorSec = alignedApogeeTimeErrorSec;
		this.phaseResiduals = Collections.unmodifiableMap(new LinkedHashMap<>(phaseResiduals));
		this.integratorDiagnostics = integratorDiagnostics == null ? VerticalIntegratorDiagnostics.EMPTY : integratorDiagnostics;
	}

	public String getDatasetName() {
		return datasetName;
	}

	public boolean isAirbrakeEnabled() {
		return airbrakeEnabled;
	}

	public AbPluginExecutionResult getPluginResult() {
		return pluginResult;
	}

	public Map<FlightPhaseWindow, PhaseTwoScoreResult> getWindowScores() {
		return windowScores;
	}

	public List<TuningFlag> getTuningFlags() {
		return tuningFlags;
	}

	public DerivedTelemetryQuantities.Quantities getReferenceQuantities() {
		return referenceQuantities;
	}

	public DerivedTelemetryQuantities.Quantities getCandidateQuantities() {
		return candidateQuantities;
	}

	public TelemetryParserDiagnostics getReferenceParserDiagnostics() {
		return referenceParserDiagnostics;
	}

	public TelemetryParserDiagnostics getCandidateParserDiagnostics() {
		return candidateParserDiagnostics;
	}

	public String getDatasetClass() {
		return datasetClass;
	}

	public String getTruthSource() {
		return truthSource;
	}

	public String getCandidateSource() {
		return candidateSource;
	}

	public String getOrkProvenance() {
		return orkProvenance;
	}

	@Deprecated
	public String getRomMode() {
		return physicsAeroMode;
	}

	@Deprecated
	public String getRomSurfaceSource() {
		return tableSource;
	}

	public String getPhysicsAeroMode() {
		return physicsAeroMode;
	}

	public String getTableSource() {
		return tableSource;
	}

	public double getCandidateMaxMach() {
		return candidateMaxMach;
	}

	public String getAlignmentChannel() {
		return alignmentChannel;
	}

	public double getAlignmentLagSec() {
		return alignmentLagSec;
	}

	public double getAlignmentQuality() {
		return alignmentQuality;
	}

	public double getReferenceAlignedApogeeTimeSec() {
		return referenceAlignedApogeeTimeSec;
	}

	public double getCandidateAlignedApogeeTimeSec() {
		return candidateAlignedApogeeTimeSec;
	}

	public double getAlignedApogeeTimeDeltaSec() {
		return alignedApogeeTimeDeltaSec;
	}

	public double getAlignedApogeeTimeErrorSec() {
		return alignedApogeeTimeErrorSec;
	}

	public Map<FlightPhaseWindow, PhaseResidualMetrics> getPhaseResiduals() {
		return phaseResiduals;
	}

	public VerticalIntegratorDiagnostics getIntegratorDiagnostics() {
		return integratorDiagnostics;
	}
}
