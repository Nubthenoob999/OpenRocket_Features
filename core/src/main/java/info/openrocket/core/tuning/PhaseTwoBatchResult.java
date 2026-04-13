package info.openrocket.core.tuning;

import java.util.Collections;
import java.util.List;

public final class PhaseTwoBatchResult {
	private final List<PhaseTwoDatasetResult> datasets;

	public PhaseTwoBatchResult(List<PhaseTwoDatasetResult> datasets) {
		this.datasets = Collections.unmodifiableList(datasets);
	}

	public List<PhaseTwoDatasetResult> getDatasets() {
		return datasets;
	}
}