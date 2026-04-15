package info.openrocket.core.motor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import info.openrocket.core.database.motor.ThrustCurveMotorSet;
import info.openrocket.core.database.motor.ThrustCurveMotorSetDatabase;
import info.openrocket.core.masscalc.MassCalculator;
import info.openrocket.core.masscalc.RigidBody;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.MotorMount;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.startup.Application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Analyzes a rocket design and recommends suitable motors based on
 * safety and performance criteria including thrust-to-weight ratio,
 * physical fit, and estimated performance.
 *
 * @author Alex Zoghlin
 */
public class MotorRecommendation {

	private static final Logger log = LoggerFactory.getLogger(MotorRecommendation.class);

	/** Minimum thrust-to-weight ratio for a safe launch (standard safety margin). */
	public static final double MIN_THRUST_TO_WEIGHT = 5.0;

	/** Gravitational acceleration constant (m/s^2). */
	private static final double G = 9.81;

	/** Diameter tolerance when matching motors to mounts (meters). */
	private static final double DIAMETER_TOLERANCE = 0.0005;

	/**
	 * Result data class holding a recommended motor and its analysis metrics.
	 */
	public static class MotorRecommendationResult {
		private final ThrustCurveMotor motor;
		private final double thrustToWeightRatio;
		private final double estimatedApogee;
		private final double safetyScore;
		private final String recommendation;
		private final String mountDescription;

		public MotorRecommendationResult(ThrustCurveMotor motor, double thrustToWeightRatio,
				double estimatedApogee, double safetyScore, String recommendation, String mountDescription) {
			this.motor = motor;
			this.thrustToWeightRatio = thrustToWeightRatio;
			this.estimatedApogee = estimatedApogee;
			this.safetyScore = safetyScore;
			this.recommendation = recommendation;
			this.mountDescription = mountDescription;
		}

		public ThrustCurveMotor getMotor() {
			return motor;
		}

		public double getThrustToWeightRatio() {
			return thrustToWeightRatio;
		}

		public double getEstimatedApogee() {
			return estimatedApogee;
		}

		public double getSafetyScore() {
			return safetyScore;
		}

		public String getRecommendation() {
			return recommendation;
		}

		public String getMountDescription() {
			return mountDescription;
		}

		/**
		 * Returns the impulse class letter derived from total impulse.
		 */
		public String getImpulseClass() {
			return MotorRecommendation.getImpulseClass(motor.getTotalImpulseEstimate());
		}
	}

	private final FlightConfiguration configuration;
	private final double rocketStructureMass;
	private final List<MountInfo> mounts;

	/**
	 * Info about a motor mount found in the configuration.
	 */
	private static class MountInfo {
		final MotorMount mount;
		final double diameter;
		final double length;
		final String description;

		MountInfo(MotorMount mount, double diameter, double length, String description) {
			this.mount = mount;
			this.diameter = diameter;
			this.length = length;
			this.description = description;
		}
	}

	/**
	 * Creates a new MotorRecommendation engine for the given flight configuration.
	 *
	 * @param configuration the flight configuration to analyze
	 */
	public MotorRecommendation(FlightConfiguration configuration) {
		this.configuration = configuration;

		// Calculate structure mass (without motors)
		RigidBody structureData = MassCalculator.calculateStructure(configuration);
		this.rocketStructureMass = structureData.getMass();

		// Find all active motor mounts
		this.mounts = new ArrayList<>();
		for (RocketComponent comp : configuration.getActiveComponents()) {
			if ((comp instanceof MotorMount) && ((MotorMount) comp).isMotorMount()) {
				MotorMount mount = (MotorMount) comp;
				double mountDiameter = mount.getMotorMountDiameter();
				double mountLength = mount.getLength();
				String desc = comp.getName();
				mounts.add(new MountInfo(mount, mountDiameter, mountLength, desc));
			}
		}

		log.info("MotorRecommendation initialized: structureMass={} kg, {} motor mount(s) found",
				String.format("%.4f", rocketStructureMass), mounts.size());
	}

	/**
	 * Returns the number of motor mounts found in the configuration.
	 */
	public int getMountCount() {
		return mounts.size();
	}

	/**
	 * Returns the rocket structure mass in kg (without motors).
	 */
	public double getRocketStructureMass() {
		return rocketStructureMass;
	}

	/**
	 * Analyzes all motors in the database and returns a sorted list of recommendations.
	 * Motors are filtered by physical fit and safety criteria, then scored by estimated performance.
	 *
	 * @param filterTypes if non-null and non-empty, only include motors of these types
	 * @return sorted list of motor recommendations (best first)
	 */
	public List<MotorRecommendationResult> recommend(List<Motor.Type> filterTypes) {
		ThrustCurveMotorSetDatabase db = Application.getThrustCurveMotorSetDatabase();
		if (db == null) {
			log.warn("Motor database not available");
			return Collections.emptyList();
		}

		List<ThrustCurveMotorSet> motorSets = db.getMotorSets();
		List<MotorRecommendationResult> results = new ArrayList<>();

		for (ThrustCurveMotorSet motorSet : motorSets) {
			List<ThrustCurveMotor> motors = motorSet.getMotors();
			if (motors.isEmpty()) {
				continue;
			}

			// Use the first (preferred) motor from the set as the representative
			ThrustCurveMotor motor = motors.get(0);

			// Apply type filter
			if (filterTypes != null && !filterTypes.isEmpty()) {
				if (!filterTypes.contains(motor.getMotorType())) {
					continue;
				}
			}

			// Check if motor fits any mount
			for (MountInfo mountInfo : mounts) {
				if (motorFitsMount(motor, mountInfo)) {
					MotorRecommendationResult result = analyzeMotor(motor, mountInfo);
					if (result != null) {
						results.add(result);
					}
					break; // Only add motor once even if it fits multiple mounts
				}
			}
		}

		// Sort by estimated apogee descending (best performance first),
		// but prioritize safe motors over unsafe ones
		Collections.sort(results, new Comparator<MotorRecommendationResult>() {
			@Override
			public int compare(MotorRecommendationResult a, MotorRecommendationResult b) {
				// First sort by safety (safe motors first)
				boolean aSafe = a.safetyScore >= 1.0;
				boolean bSafe = b.safetyScore >= 1.0;
				if (aSafe != bSafe) {
					return aSafe ? -1 : 1;
				}
				// Then by estimated apogee descending
				return Double.compare(b.estimatedApogee, a.estimatedApogee);
			}
		});

		return results;
	}

	/**
	 * Checks whether a motor physically fits in the given mount.
	 */
	private boolean motorFitsMount(ThrustCurveMotor motor, MountInfo mountInfo) {
		double motorDiameter = motor.getDiameter();
		double mountDiameter = mountInfo.diameter;

		// Motor diameter must be within tolerance of mount diameter
		if (motorDiameter > mountDiameter + DIAMETER_TOLERANCE) {
			return false;
		}
		// Motor should not be too small for the mount (allow some tolerance)
		if (motorDiameter < mountDiameter - 0.005) {
			return false;
		}

		// Motor length must not exceed mount length
		double motorLength = motor.getLength();
		double mountLength = mountInfo.length;
		if (motorLength > mountLength + 0.001) {
			return false;
		}

		return true;
	}

	/**
	 * Analyzes a motor for a given mount, computing safety and performance metrics.
	 */
	private MotorRecommendationResult analyzeMotor(ThrustCurveMotor motor, MountInfo mountInfo) {
		double motorMass = motor.getLaunchMass();
		double totalMass = rocketStructureMass + motorMass;

		if (totalMass <= 0) {
			return null;
		}

		double totalWeight = totalMass * G;

		// Compute thrust-to-weight ratio using average thrust
		double avgThrust = motor.getAverageThrustEstimate();
		if (Double.isNaN(avgThrust) || avgThrust <= 0) {
			return null;
		}
		double thrustToWeight = avgThrust / totalWeight;

		// Estimate apogee using simplified rocket equation
		// This is a rough estimate: apogee ~ (totalImpulse^2) / (2 * mass * g * mass)
		// Simplified: use impulse / (mass * g) as a performance proxy (specific impulse-like metric)
		// More accurately: v_burnout = totalImpulse / totalMass, apogee ~ v^2 / (2*g) + burnAltitude
		double totalImpulse = motor.getTotalImpulseEstimate();
		double burnTime = motor.getBurnTimeEstimate();
		if (Double.isNaN(totalImpulse) || totalImpulse <= 0) {
			return null;
		}

		// Average mass during burn (rough estimate)
		double burnoutMass = rocketStructureMass + motor.getBurnoutMass();
		double avgMassDuringBurn = (totalMass + burnoutMass) / 2.0;

		// Velocity at burnout (simplified, ignoring drag and gravity loss during burn)
		double vBurnout = totalImpulse / avgMassDuringBurn;

		// Altitude gained during burn (assuming constant acceleration)
		double burnAltitude = 0.5 * vBurnout * (Double.isNaN(burnTime) ? 1.0 : burnTime);

		// Coast altitude after burnout
		double coastAltitude = (vBurnout * vBurnout) / (2.0 * G);

		// Total estimated apogee (very rough, ignoring drag)
		double estimatedApogee = burnAltitude + coastAltitude;

		// Safety score: based on thrust-to-weight ratio
		// 1.0 = meets minimum (5:1 TWR), higher = better
		double safetyScore = thrustToWeight / MIN_THRUST_TO_WEIGHT;

		// Generate recommendation text
		String recommendation;
		if (thrustToWeight >= MIN_THRUST_TO_WEIGHT) {
			if (thrustToWeight >= MIN_THRUST_TO_WEIGHT * 2) {
				recommendation = "Excellent - high thrust margin";
			} else if (thrustToWeight >= MIN_THRUST_TO_WEIGHT * 1.5) {
				recommendation = "Good - adequate thrust margin";
			} else {
				recommendation = "Acceptable - meets minimum safety";
			}
		} else if (thrustToWeight >= MIN_THRUST_TO_WEIGHT * 0.6) {
			recommendation = "Marginal - below recommended 5:1 TWR";
		} else {
			recommendation = "Not recommended - insufficient thrust";
		}

		return new MotorRecommendationResult(motor, thrustToWeight, estimatedApogee,
				safetyScore, recommendation, mountInfo.description);
	}

	/**
	 * Derives the impulse class letter from the total impulse in Newton-seconds.
	 *
	 * @param totalImpulse the total impulse in Ns
	 * @return the impulse class letter (e.g., "A", "B", "C", ...)
	 */
	public static String getImpulseClass(double totalImpulse) {
		if (totalImpulse <= 0.3125) return "1/8A";
		if (totalImpulse <= 0.625) return "1/4A";
		if (totalImpulse <= 1.25) return "1/2A";
		if (totalImpulse <= 2.5) return "A";
		if (totalImpulse <= 5.0) return "B";
		if (totalImpulse <= 10.0) return "C";
		if (totalImpulse <= 20.0) return "D";
		if (totalImpulse <= 40.0) return "E";
		if (totalImpulse <= 80.0) return "F";
		if (totalImpulse <= 160.0) return "G";
		if (totalImpulse <= 320.0) return "H";
		if (totalImpulse <= 640.0) return "I";
		if (totalImpulse <= 1280.0) return "J";
		if (totalImpulse <= 2560.0) return "K";
		if (totalImpulse <= 5120.0) return "L";
		if (totalImpulse <= 10240.0) return "M";
		if (totalImpulse <= 20480.0) return "N";
		if (totalImpulse <= 40960.0) return "O";
		return "O+";
	}
}
