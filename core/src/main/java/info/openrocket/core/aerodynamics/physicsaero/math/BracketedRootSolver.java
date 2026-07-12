package info.openrocket.core.aerodynamics.physicsaero.math;

import java.util.function.DoubleUnaryOperator;

public interface BracketedRootSolver {
	double solve(DoubleUnaryOperator function, double lower, double upper, double absoluteTolerance, int maxIterations);
}
