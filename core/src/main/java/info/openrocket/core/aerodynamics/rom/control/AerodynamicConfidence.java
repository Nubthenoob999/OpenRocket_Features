package info.openrocket.core.aerodynamics.rom.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AerodynamicConfidence {
	private final double overallScore;
	private final double transonicPenalty;
	private final double anglePenalty;
	private final double separationPenalty;
	private final double stiffnessPenalty;
	private final List<String> reasons;

	public AerodynamicConfidence(double overallScore, double transonicPenalty, double anglePenalty,
			double separationPenalty, double stiffnessPenalty, List<String> reasons) {
		this.overallScore = overallScore;
		this.transonicPenalty = transonicPenalty;
		this.anglePenalty = anglePenalty;
		this.separationPenalty = separationPenalty;
		this.stiffnessPenalty = stiffnessPenalty;
		this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
	}

	public double getOverallScore() {
		return overallScore;
	}

	public double getTransonicPenalty() {
		return transonicPenalty;
	}

	public double getAnglePenalty() {
		return anglePenalty;
	}

	public double getSeparationPenalty() {
		return separationPenalty;
	}

	public double getStiffnessPenalty() {
		return stiffnessPenalty;
	}

	public List<String> getReasons() {
		return reasons;
	}
}
