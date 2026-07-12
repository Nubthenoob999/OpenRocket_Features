package info.openrocket.core.structures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StructuresResult {
	private final String componentName;
	private final String analysisType;
	private final double factorOfSafety;
	private final StructuresStatus status;
	private final String governingCaseDescription;
	private final Map<String, Double> values;
	private final List<String> warnings;

	public StructuresResult(String componentName, String analysisType, double factorOfSafety,
			StructuresStatus status, String governingCaseDescription, Map<String, Double> values,
			List<String> warnings) {
		this.componentName = componentName;
		this.analysisType = analysisType;
		this.factorOfSafety = factorOfSafety;
		this.status = status;
		this.governingCaseDescription = governingCaseDescription;
		this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
		this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
	}

	public static StructuresResult insufficientData(String componentName, String analysisType, String warning) {
		List<String> warnings = new ArrayList<>();
		warnings.add(warning);
		return new StructuresResult(componentName, analysisType, Double.NaN, StructuresStatus.INSUFFICIENT_DATA,
				"Insufficient Data", Collections.emptyMap(), warnings);
	}

	public String getComponentName() {
		return componentName;
	}

	public String getAnalysisType() {
		return analysisType;
	}

	public double getFactorOfSafety() {
		return factorOfSafety;
	}

	public StructuresStatus getStatus() {
		return status;
	}

	public String getGoverningCaseDescription() {
		return governingCaseDescription;
	}

	public Map<String, Double> getValues() {
		return values;
	}

	public List<String> getWarnings() {
		return warnings;
	}
}
