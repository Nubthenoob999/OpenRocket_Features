package info.openrocket.core.structures;

import java.util.List;

public final class StructuresSummary {
	private final int passCount;
	private final int warningCount;
	private final int failCount;
	private final int insufficientDataCount;

	public StructuresSummary(List<StructuresResult> results) {
		int pass = 0;
		int warning = 0;
		int fail = 0;
		int insufficient = 0;
		for (StructuresResult result : results) {
			switch (result.getStatus()) {
			case PASS:
				pass++;
				break;
			case WARNING:
				warning++;
				break;
			case FAIL:
				fail++;
				break;
			case INSUFFICIENT_DATA:
				insufficient++;
				break;
			default:
				break;
			}
		}
		this.passCount = pass;
		this.warningCount = warning;
		this.failCount = fail;
		this.insufficientDataCount = insufficient;
	}

	public int getPassCount() {
		return passCount;
	}

	public int getWarningCount() {
		return warningCount;
	}

	public int getFailCount() {
		return failCount;
	}

	public int getInsufficientDataCount() {
		return insufficientDataCount;
	}
}
