package info.openrocket.core.aerodynamics.rom.force;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AerodynamicCoefficientAssembler {
	private final List<PressureContribution> pressureContributions = new ArrayList<>();
	private final List<FrictionContribution> frictionContributions = new ArrayList<>();
	private double pressureCA;
	private double frictionCA;
	private double cnDelta;
	private double cmDelta;
	private double cpWeightedX;
	private double cpWeight;

	public void addPressureContribution(PressureContribution contribution) {
		pressureContributions.add(contribution);
		pressureCA += contribution.getDeltaCA();
		cnDelta += contribution.getDeltaCN();
		cmDelta += contribution.getDeltaCm();
		cpWeightedX += Math.abs(contribution.getDeltaCN()) * contribution.getXCp();
		cpWeight += Math.abs(contribution.getDeltaCN());
	}

	public void addFrictionContribution(FrictionContribution contribution) {
		frictionContributions.add(contribution);
		frictionCA += contribution.getDeltaCA();
		cmDelta += contribution.getDeltaCm();
	}

	public double getPressureCA() {
		return pressureCA;
	}

	public double getFrictionCA() {
		return frictionCA;
	}

	public double getCnDelta() {
		return cnDelta;
	}

	public double getCmDelta() {
		return cmDelta;
	}

	public double getCenterOfPressureX(double fallbackX) {
		if (cpWeight <= 1e-9) {
			return fallbackX;
		}
		return cpWeightedX / cpWeight;
	}

	public List<PressureContribution> getPressureContributions() {
		return Collections.unmodifiableList(pressureContributions);
	}

	public List<FrictionContribution> getFrictionContributions() {
		return Collections.unmodifiableList(frictionContributions);
	}
}
