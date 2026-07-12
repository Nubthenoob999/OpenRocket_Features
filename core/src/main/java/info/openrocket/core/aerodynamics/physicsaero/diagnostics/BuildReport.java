package info.openrocket.core.aerodynamics.physicsaero.diagnostics;

import java.util.List;

public record BuildReport(int totalCells, int completedCells, int failedCells, boolean cancelled,
		List<ReproductionCase> reproductionCases) { public BuildReport { reproductionCases = List.copyOf(reproductionCases); } }
