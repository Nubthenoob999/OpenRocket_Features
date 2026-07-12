package info.openrocket.core.structures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StructuresReport {
	private final List<StructuresResult> results;
	private final StructuresSummary summary;

	public StructuresReport(List<StructuresResult> results) {
		this.results = Collections.unmodifiableList(new ArrayList<>(results));
		this.summary = new StructuresSummary(results);
	}

	public List<StructuresResult> getResults() {
		return results;
	}

	public StructuresSummary getSummary() {
		return summary;
	}
}
