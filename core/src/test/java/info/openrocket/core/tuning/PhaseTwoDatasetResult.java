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

	public PhaseTwoDatasetResult(String datasetName,
							 boolean airbrakeEnabled,
							 AbPluginExecutionResult pluginResult,
							 Map<FlightPhaseWindow, PhaseTwoScoreResult> windowScores,
							 List<TuningFlag> tuningFlags,
							 DerivedTelemetryQuantities.Quantities referenceQuantities,
							 DerivedTelemetryQuantities.Quantities candidateQuantities,
							 TelemetryParserDiagnostics referenceParserDiagnostics,
							 TelemetryParserDiagnostics candidateParserDiagnostics) {
		this.datasetName = datasetName;
		this.airbrakeEnabled = airbrakeEnabled;
		this.pluginResult = pluginResult;
		this.windowScores = Collections.unmodifiableMap(new LinkedHashMap<>(windowScores));
		this.tuningFlags = Collections.unmodifiableList(tuningFlags);
		this.referenceQuantities = referenceQuantities;
		this.candidateQuantities = candidateQuantities;
		this.referenceParserDiagnostics = referenceParserDiagnostics == null ? TelemetryParserDiagnostics.EMPTY : referenceParserDiagnostics;
		this.candidateParserDiagnostics = candidateParserDiagnostics == null ? TelemetryParserDiagnostics.EMPTY : candidateParserDiagnostics;
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
}