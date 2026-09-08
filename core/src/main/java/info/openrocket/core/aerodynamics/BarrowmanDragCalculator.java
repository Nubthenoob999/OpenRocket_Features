package info.openrocket.core.aerodynamics;

import static info.openrocket.core.util.MathUtil.pow2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import info.openrocket.core.aerodynamics.barrowman.RocketComponentCalc;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.ComponentAssembly;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.ExternalComponent;
import info.openrocket.core.rocketcomponent.ExternalComponent.Finish;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.InstanceContext;
import info.openrocket.core.rocketcomponent.InstanceMap;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.MathUtil;
import info.openrocket.core.util.PolyInterpolator;
import info.openrocket.core.util.Reflection;

/**
 * Drag portion of the extended Barrowman aerodynamic calculator.
 */
public class BarrowmanDragCalculator implements DragCalculator {

	private static final String BARROWMAN_PACKAGE = "info.openrocket.core.aerodynamics.barrowman";
	private static final String BARROWMAN_SUFFIX = "Calc";
	private static final double FINNED_BASE_AUGMENTATION = 0.55;
	private static final double EXPANDING_FIN_CAN_AUGMENTATION = 1.35;
	private static final double THICK_BL_K = 2.2;
	private static final double THICK_BL_DELTA_R_THRESHOLD = 0.5;
	private static final double THICK_BL_MACH_LOW = 0.9;
	private static final double THICK_BL_MACH_HIGH = 1.1;
	private static final double THICK_BL_MACH_DECAY_END = 3.0;
	private static final double THICK_BL_LD_LOW = 25.0;
	private static final double THICK_BL_LD_HIGH = 30.0;
	private static final double THICK_BL_MAX_MULTIPLIER = 1.8;
	private static final double SLENDER_BODY_PRESSURE_K = 0.0025;
	private static final double SLENDER_BODY_LD_THRESHOLD = 15;
	private static final double SLENDER_BODY_LD_EXCESS_CAP = 25;
	private static final double SLENDER_BODY_MACH_LOW = 1.05;
	private static final double SLENDER_BODY_MACH_HIGH = 1.3;
	private static final double SLENDER_BODY_MACH_DECAY_START = 3;
	private static final double SLENDER_BODY_MACH_DECAY_END = 5;

	private final WarningSet ignoreWarningSet = new WarningSet();

	private Map<RocketComponent, RocketComponentCalc> calcMap = null;

	private static final double[] axialDragPoly1;
	private static final double[] axialDragPoly2;

	static {
		PolyInterpolator interpolator;
		interpolator = new PolyInterpolator(
				new double[] { 0, 17 * Math.PI / 180 },
				new double[] { 0, 17 * Math.PI / 180 });
		axialDragPoly1 = interpolator.interpolator(1, 1.3, 0, 0);

		interpolator = new PolyInterpolator(
				new double[] { 17 * Math.PI / 180, Math.PI / 2 },
				new double[] { 17 * Math.PI / 180, Math.PI / 2 },
				new double[] { Math.PI / 2 });
		axialDragPoly2 = interpolator.interpolator(1.3, 0, 0, 0, 0);
	}

	@Override
	public DragCalculator newInstance() {
		return new BarrowmanDragCalculator();
	}

	@Override
	public void calculateDrag(FlightConfiguration configuration,
			FlightConditions conditions,
			Map<RocketComponent, AerodynamicForces> componentForces,
			Map<RocketComponent, AerodynamicForces> assemblyForces,
			AerodynamicForces totalForces,
			WarningSet warnings) {
		ensureCalcMap(configuration);
		WarningSet actualWarnings = (warnings != null) ? warnings : ignoreWarningSet;

		double frictionCD = calculateFrictionCD(configuration, conditions, componentForces, actualWarnings);
		double pressureCD = calculatePressureCD(configuration, conditions, componentForces, actualWarnings);
		double baseCD = calculateBaseCD(configuration, conditions, componentForces, actualWarnings);
		double overrideCD = calculateOverrideCD(configuration, componentForces, assemblyForces);

		totalForces.setFrictionCD(frictionCD);
		totalForces.setPressureCD(pressureCD);
		totalForces.setBaseCD(baseCD);
		totalForces.setOverrideCD(overrideCD);
		totalForces.setCD(frictionCD + pressureCD + baseCD + overrideCD);
		totalForces.setCDaxial(calculateAxialCD(conditions, totalForces.getCD()));
	}

	@Override
	public double toAxialDrag(FlightConditions conditions, double cd) {
		return calculateAxialCD(conditions, cd);
	}

	@Override
	public void voidAerodynamicCache() {
		calcMap = null;
	}

	private double calculateFrictionCD(FlightConfiguration configuration, FlightConditions conditions,
			Map<RocketComponent, AerodynamicForces> forceMap, WarningSet warningSet) {
		double mach = conditions.getMach();
		double Re = calculateReynoldsNumber(configuration, conditions);
		double Cf = calculateFrictionCoefficient(configuration, mach, Re);
		double roughnessCorrection = calculateRoughnessCorrection(mach);

		ensureCalcMap(configuration);

		double otherFrictionCD = 0;
		double bodyFrictionCD = 0;
		double maxR = 0;
		double minX = Double.MAX_VALUE;
		double maxX = 0;

		double[] roughnessLimited = new double[Finish.values().length];
		Arrays.fill(roughnessLimited, Double.NaN);

		InstanceMap imap = configuration.getActiveInstances();
		for (Map.Entry<RocketComponent, ArrayList<InstanceContext>> entry : imap.entrySet()) {
			RocketComponent c = entry.getKey();

			if (!c.isAerodynamic()) {
				continue;
			}

			if (c.isCDOverridden() || c.isCDOverriddenByAncestor()) {
				continue;
			}

			Finish finish = ((ExternalComponent) c).getFinish();
			if (Double.isNaN(roughnessLimited[finish.ordinal()])) {
				roughnessLimited[finish.ordinal()] = 0.032
						* Math.pow(finish.getRoughnessSize() / configuration.getLengthAerodynamic(), 0.2)
						* roughnessCorrection;
			}

			double componentCf;
			if (configuration.getRocket().isPerfectFinish()) {
				if ((Re > 1.0e6) && (roughnessLimited[finish.ordinal()] > Cf)) {
					componentCf = roughnessLimited[finish.ordinal()];
				} else {
					componentCf = Cf;
				}
			} else {
				componentCf = Math.max(Cf, roughnessLimited[finish.ordinal()]);
			}

			double componentFrictionCD = calcMap.get(c).calculateFrictionCD(conditions, componentCf, warningSet);
			int instanceCount = entry.getValue().size();

			if (c instanceof SymmetricComponent) {
				SymmetricComponent s = (SymmetricComponent) c;

				bodyFrictionCD += instanceCount * componentFrictionCD;

				double componentMinX = c.getAxialOffset(AxialMethod.ABSOLUTE);
				minX = Math.min(minX, componentMinX);

				double componentMaxX = componentMinX + c.getLength();
				maxX = Math.max(maxX, componentMaxX);

				double componentMaxR = Math.max(s.getForeRadius(), s.getAftRadius());
				maxR = Math.max(maxR, componentMaxR);

			} else {
				otherFrictionCD += instanceCount * componentFrictionCD;
			}

			if (forceMap != null && forceMap.get(c) != null) {
				forceMap.get(c).setFrictionCD(componentFrictionCD);
			}
		}

		double bodyLength = maxX - minX + 0.0001;
		double correction = calculateBodyFrictionCorrection(bodyLength, maxR);

		if (forceMap != null) {
			for (Map.Entry<RocketComponent, AerodynamicForces> entry : forceMap.entrySet()) {
				if (entry.getKey() instanceof SymmetricComponent) {
					entry.getValue().setFrictionCD(entry.getValue().getFrictionCD() * correction);
				}
			}
		}

		return otherFrictionCD + correction * bodyFrictionCD;
	}

	/**
	 * Calculate the cylindrical-body wetted-area correction from OpenRocket
	 * technical documentation equation (3.85). The body fineness ratio is the
	 * body length divided by its maximum diameter, not its maximum radius.
	 *
	 * @param bodyLength aerodynamic body length
	 * @param maxRadius maximum body radius
	 * @return body skin-friction correction multiplier
	 */
	static double calculateBodyFrictionCorrection(double bodyLength, double maxRadius) {
		double bodyDiameter = 2 * maxRadius;
		double finenessRatio = bodyLength / bodyDiameter;
		return 1 + 1.0 / (2 * finenessRatio);
	}

	private double calculateReynoldsNumber(FlightConfiguration configuration, FlightConditions conditions) {
		return conditions.getVelocity() * configuration.getLengthAerodynamic() /
				conditions.getAtmosphericConditions().getKinematicViscosity();
	}

	private double calculateFrictionCoefficient(FlightConfiguration configuration, double mach, double Re) {
		double Cf;
		double c1 = 1.0;
		double c2 = 1.0;

		if (configuration.getRocket().isPerfectFinish()) {
			if (Re < 1.0e4) {
				Cf = 1.33e-2;
			} else if (Re < 5.39e5) {
				Cf = 1.328 / MathUtil.safeSqrt(Re);
			} else {
				Cf = 1.0 / pow2(1.50 * Math.log(Re) - 5.6) - 1700 / Re;
			}

			if (mach < 1.1) {
				if (Re > 1.0e6) {
					if (Re < 3.0e6) {
						c1 = 1 - 0.1 * pow2(mach) * (Re - 1.0e6) / 2.0e6;
					} else {
						c1 = 1 - 0.1 * pow2(mach);
					}
				}
			}
			if (mach > 0.9) {
				if (Re > 1.0e6) {
					if (Re < 3.0e6) {
						c2 = 1 + (1.0 / Math.pow(1 + 0.045 * pow2(mach), 0.25) - 1) * (Re - 1.0e6) / 2.0e6;
					} else {
						c2 = 1.0 / Math.pow(1 + 0.045 * pow2(mach), 0.25);
					}
				}
			}

			if (mach < 0.9) {
				Cf *= c1;
			} else if (mach < 1.1) {
				Cf *= (c2 * (mach - 0.9) / 0.2 + c1 * (1.1 - mach) / 0.2);
			} else {
				Cf *= c2;
			}

		} else {
			if (Re < 1.0e4) {
				Cf = 1.48e-2;
			} else {
				Cf = 1.0 / pow2(1.50 * Math.log(Re) - 5.6);
			}

			if (mach < 1.1) {
				c1 = 1 - 0.1 * pow2(mach);
			}
			if (mach > 0.9) {
				c2 = 1 / Math.pow(1 + 0.15 * pow2(mach), 0.58);
			}
			if (mach < 0.9) {
				Cf *= c1;
			} else if (mach < 1.1) {
				Cf *= c2 * (mach - 0.9) / 0.2 + c1 * (1.1 - mach) / 0.2;
			} else {
				Cf *= c2;
			}
		}

		return Cf;
	}

	private double calculateRoughnessCorrection(double mach) {
		double roughnessCorrection;
		if (mach < 0.9) {
			roughnessCorrection = 1 - 0.1 * pow2(mach);
		} else if (mach > 1.1) {
			roughnessCorrection = 1 / (1 + 0.18 * pow2(mach));
		} else {
			double c1 = 1 - 0.1 * pow2(0.9);
			double c2 = 1.0 / (1 + 0.18 * pow2(1.1));
			roughnessCorrection = c2 * (mach - 0.9) / 0.2 + c1 * (1.1 - mach) / 0.2;
		}
		return roughnessCorrection;
	}

	private double calculatePressureCD(FlightConfiguration configuration, FlightConditions conditions,
			Map<RocketComponent, AerodynamicForces> forceMap, WarningSet warningSet) {
		ensureCalcMap(configuration);

		double stagnation = calculateStagnationCD(conditions.getMach());
		double base = calculateBaseCD(conditions.getMach());

		double total = 0;
		InstanceMap imap = configuration.getActiveInstances();
		for (Map.Entry<RocketComponent, ArrayList<InstanceContext>> entry : imap.entrySet()) {
			RocketComponent c = entry.getKey();

			if (!c.isAerodynamic()) {
				continue;
			}

			if (c.isCDOverridden() || c.isCDOverriddenByAncestor()) {
				continue;
			}

			int instanceCount = entry.getValue().size();

			double cd = calcMap.get(c).calculatePressureCD(conditions, stagnation, base, warningSet);

			if (forceMap != null && forceMap.get(c) != null) {
				forceMap.get(c).setPressureCD(cd);
			}

			total += cd * instanceCount;

			if (c instanceof SymmetricComponent) {
				SymmetricComponent s = (SymmetricComponent) c;
				double foreRadius = s.getForeRadius();
				double aftRadius = s.getAftRadius();
				if (s.getLength() == 0) {
					foreRadius = Math.max(foreRadius, aftRadius);
				}
				double radius = 0;
				SymmetricComponent prevComponent = s.getPreviousSymmetricComponent();
				if (prevComponent != null && configuration.isComponentActive(prevComponent)) {
					radius = prevComponent.getAftRadius();
				}

				if (radius < foreRadius) {
					double area = Math.PI * (pow2(foreRadius) - pow2(radius));
					double diskCd = stagnation * area / conditions.getRefArea();
					total += instanceCount * diskCd;

					if (forceMap != null && forceMap.get(c) != null) {
						forceMap.get(c).setPressureCD(forceMap.get(c).getPressureCD() + diskCd);
					}
				}
			}
		}

		double slenderBodyPressure = calculateSlenderBodyPressureCD(configuration, conditions);
		if (slenderBodyPressure > 0) {
			total += slenderBodyPressure;
			distributeSlenderBodyPressureCD(configuration, forceMap, slenderBodyPressure);
		}
		return total;
	}

	private static void distributeSlenderBodyPressureCD(FlightConfiguration configuration,
			Map<RocketComponent, AerodynamicForces> forceMap, double totalPressureCd) {
		if (forceMap == null || totalPressureCd <= 0) return;
		double totalLength = computeBodyTubeLength(configuration);
		if (totalLength < MathUtil.EPSILON) return;
		for (Map.Entry<RocketComponent, ArrayList<InstanceContext>> entry
				: configuration.getActiveInstances().entrySet()) {
			if (!(entry.getKey() instanceof SymmetricComponent component)) continue;
			double maximumRadius = Math.max(component.getForeRadius(), component.getAftRadius());
			if (maximumRadius < MathUtil.EPSILON
					|| Math.abs(component.getForeRadius() - component.getAftRadius())
							/ maximumRadius >= 0.01) {
				continue;
			}
			double share = totalPressureCd * component.getLength()
					* entry.getValue().size() / totalLength;
			AerodynamicForces forces = forceMap.get(component);
			if (forces != null) forces.setPressureCD(forces.getPressureCD() + share);
		}
	}

	private double calculateBaseCD(FlightConfiguration configuration, FlightConditions conditions,
			Map<RocketComponent, AerodynamicForces> forceMap, WarningSet warningSet) {
		ensureCalcMap(configuration);

		double base = calculateBaseCD(conditions.getMach());
		double total = 0;

		InstanceMap imap = configuration.getActiveInstances();
		for (Map.Entry<RocketComponent, ArrayList<InstanceContext>> entry : imap.entrySet()) {
			RocketComponent c = entry.getKey();

			if (c.isCDOverridden() || c.isCDOverriddenByAncestor()) {
				continue;
			}

			int instanceCount = entry.getValue().size();

			// Base drag for symmetric components (body tubes, nose cones, transitions)
			if (c instanceof SymmetricComponent) {
				SymmetricComponent s = (SymmetricComponent) c;
				double foreRadius = s.getForeRadius();
				double aftRadius = s.getAftRadius();
				if (s.getLength() == 0) {
					double componentMaxR = Math.max(foreRadius, aftRadius);
					foreRadius = componentMaxR;
					aftRadius = componentMaxR;
				}

				SymmetricComponent nextComponent = s.getNextSymmetricComponent();
				double nextRadius;
				if ((nextComponent != null) && configuration.isComponentActive(nextComponent)) {
					nextRadius = nextComponent.getForeRadius();
				} else {
					nextRadius = 0.0;
				}

				if (nextRadius < aftRadius) {
					double area = Math.PI * (pow2(aftRadius) - pow2(nextRadius));
					double correctedBase = base;
					if (s instanceof Transition && foreRadius > aftRadius && s.getLength() > 0) {
						correctedBase *= calculateBoattailFactor(
								foreRadius, aftRadius, s.getLength(), conditions.getMach());
					}
					correctedBase *= calculateFinnedBaseAugmentation(s, conditions.getMach());
					correctedBase *= calculateThickBLBaseMultiplier(
							s, entry.getValue(), configuration, conditions, aftRadius);
					// During thrust, the nozzle exit occupies part of the terminal wake.
					// Clamp the exclusion to this assembly so clustered/pod motors cannot
					// subtract base area from another independent body.
					double areaScale = 1;
					if (nextComponent == null || !configuration.isComponentActive(nextComponent)) {
						double totalComponentArea = instanceCount * area;
						double nozzleExitArea = conditions.getThrustingNozzleExitArea(s.getAssembly());
						double excludedNozzleArea = Math.min(nozzleExitArea, totalComponentArea);
						areaScale = (totalComponentArea - excludedNozzleArea) / totalComponentArea;
					}
					double cd = correctedBase * area * areaScale / conditions.getRefArea();
					total += instanceCount * cd;
					if (forceMap != null && forceMap.get(s) != null) {
						forceMap.get(s).setBaseCD(cd);
					}
				}
			} else if (c.isAerodynamic()) {
				// Base drag for non-symmetric components (fins, etc.)
				double cd = calcMap.get(c).calculateComponentBaseCD(conditions, base, warningSet);
				if (cd > 0) {
					total += cd * instanceCount;
					if (forceMap != null && forceMap.get(c) != null) {
						forceMap.get(c).setBaseCD(cd);
					}
				}
			}
		}

		return total;
	}

	private static double calculateFinnedBaseAugmentation(SymmetricComponent component, double mach) {
		if (mach < 0.2 || component.getAftRadius() <= MathUtil.EPSILON) return 1;
		int finCount = 0;
		double maximumSpan = 0;
		for (int index = 0; index < component.getChildCount(); index++) {
			RocketComponent child = component.getChild(index);
			if (child instanceof FinSet fin && finTrailingEdgeNearAftFace(component, fin)) {
				finCount += fin.getFinCount();
				maximumSpan = Math.max(maximumSpan, fin.getSpan());
			}
		}
		if (finCount == 0 && component instanceof Transition
				&& component.getForeRadius() > component.getAftRadius()) {
			SymmetricComponent previous = component.getPreviousSymmetricComponent();
			if (previous != null) {
				for (int index = 0; index < previous.getChildCount(); index++) {
					RocketComponent child = previous.getChild(index);
					if (child instanceof FinSet fin && finTrailingEdgeNearAftFace(component, fin)) {
						finCount += fin.getFinCount();
						maximumSpan = Math.max(maximumSpan, fin.getSpan());
					}
				}
			}
		}
		double strength = finCount >= 4 && hasExpandingFinCanSleeve(component)
				? EXPANDING_FIN_CAN_AUGMENTATION : FINNED_BASE_AUGMENTATION;
		return finnedBaseAugmentationFactor(
				finCount, maximumSpan / component.getAftRadius(), mach, strength);
	}

	static double finnedBaseAugmentationFactor(int finCount, double spanToRadius, double mach) {
		return finnedBaseAugmentationFactor(
				finCount, spanToRadius, mach, FINNED_BASE_AUGMENTATION);
	}

	private static double finnedBaseAugmentationFactor(int finCount, double spanToRadius,
			double mach, double strength) {
		// The available Basic Finner component anchor is a four-fin dataset.
		// Keep lower fin counts outside this correlation instead of extrapolating
		// the four-fin wake deficit into an unvalidated topology.
		if (finCount < 4 || !(spanToRadius > 0) || mach < 0.2) return 1;
		double machFactor;
		if (mach < 0.8) {
			machFactor = 0.30 * (mach - 0.2) / 0.6;
		} else if (mach < 1.3) {
			machFactor = 0.30 + 0.70 * (mach - 0.8) / 0.5;
		} else if (mach < 3.0) {
			machFactor = 1;
		} else {
			machFactor = 3.0 / mach;
		}
		double fourFinAnchor = 1 - Math.exp(-4.0 / 1.4);
		double finFactor = (1 - Math.exp(-finCount / 1.4)) / fourFinAnchor;
		finFactor = MathUtil.clamp(finFactor, 0, 1.25);
		double spanFactor = MathUtil.clamp(spanToRadius, 0.3, 1);
		return 1 + strength * finFactor * spanFactor * machFactor;
	}

	private static boolean hasExpandingFinCanSleeve(SymmetricComponent component) {
		if (!(component instanceof BodyTube) || component.getParent() == null) return false;
		RocketComponent previous = null;
		for (int index = 0; index < component.getParent().getChildCount(); index++) {
			RocketComponent child = component.getParent().getChild(index);
			if (child == component && previous instanceof Transition shoulder) {
				double radiusStep = shoulder.getAftRadius() - shoulder.getForeRadius();
				return radiusStep > 0
						&& Math.abs(shoulder.getAftRadius() - component.getForeRadius()) < 0.003
						&& shoulder.getLength() <= 0.035;
			}
			previous = child;
		}
		return false;
	}

	private static boolean finTrailingEdgeNearAftFace(SymmetricComponent component, FinSet fin) {
		double tolerance = Math.max(0.05, 2.5 * component.getAftRadius());
		double baseX = component.toAbsolute(new Coordinate(component.getLength(), 0, 0))[0].getX();
		double leadingX = fin.toAbsolute(Coordinate.ZERO)[0].getX();
		double trailingX = fin.toAbsolute(new Coordinate(fin.getLength(), 0, 0))[0].getX();
		return Math.abs(trailingX - baseX) < tolerance
				|| (leadingX < baseX && trailingX >= baseX - tolerance);
	}

	static double calculateBoattailFactor(double foreRadius, double aftRadius,
			double length, double mach) {
		if (aftRadius >= foreRadius || length <= 0) return 1;
		double diameterRatio = aftRadius / foreRadius;
		double angle = Math.atan2(foreRadius - aftRadius, length);
		double angleFactor;
		if (angle <= Math.toRadians(12)) {
			angleFactor = 1;
		} else if (angle < Math.toRadians(20)) {
			angleFactor = (Math.toRadians(20) - angle) / Math.toRadians(8);
		} else {
			angleFactor = 0;
		}
		double reduction = mach <= 1 ? 0.25 : 0.25 + 0.15 * Math.min(mach - 1, 1);
		return MathUtil.clamp(1 - angleFactor * reduction * (1 - diameterRatio), 0.3, 1);
	}

	static double calculateThickBLBaseMultiplier(SymmetricComponent component,
			ArrayList<InstanceContext> contexts, FlightConfiguration configuration,
			FlightConditions conditions, double baseRadius) {
		double mach = conditions.getMach();
		if (mach <= THICK_BL_MACH_LOW || baseRadius < MathUtil.EPSILON) return 1;
		double bodyLength = computeBodyTubeLength(configuration);
		double bodyLD = bodyLength / (2 * baseRadius);
		if (bodyLD <= THICK_BL_LD_LOW) return 1;
		double ldRamp = bodyLD >= THICK_BL_LD_HIGH ? 1
				: smoothstep((bodyLD - THICK_BL_LD_LOW)
						/ (THICK_BL_LD_HIGH - THICK_BL_LD_LOW));
		double machRamp;
		if (mach <= THICK_BL_MACH_HIGH) {
			machRamp = smoothstep((mach - THICK_BL_MACH_LOW)
					/ (THICK_BL_MACH_HIGH - THICK_BL_MACH_LOW));
		} else if (mach >= THICK_BL_MACH_DECAY_END) {
			return 1;
		} else {
			machRamp = 1 - smoothstep((mach - THICK_BL_MACH_HIGH)
					/ (THICK_BL_MACH_DECAY_END - THICK_BL_MACH_HIGH));
		}
		double baseX = computeBaseStationX(component, contexts);
		double velocity = conditions.getVelocity();
		double kinematicViscosity = conditions.getAtmosphericConditions().getKinematicViscosity();
		if (baseX <= MathUtil.EPSILON || velocity < 1.0e-3 || kinematicViscosity < 1.0e-10) return 1;
		double reynolds = velocity * baseX / kinematicViscosity;
		if (reynolds < 1.0e4) return 1;
		double boundaryLayerThickness = baseX * 0.37 / Math.pow(reynolds, 0.2);
		double excess = Math.max(0,
				boundaryLayerThickness / baseRadius - THICK_BL_DELTA_R_THRESHOLD);
		return Math.min(1 + THICK_BL_K * excess * machRamp * ldRamp,
				THICK_BL_MAX_MULTIPLIER);
	}

	static double calculateSlenderBodyPressureCD(FlightConfiguration configuration,
			FlightConditions conditions) {
		double mach = conditions.getMach();
		if (mach <= SLENDER_BODY_MACH_LOW || mach >= SLENDER_BODY_MACH_DECAY_END
				|| conditions.getRefLength() < MathUtil.EPSILON) {
			return 0;
		}
		double bodyLD = computeBodyTubeLength(configuration) / conditions.getRefLength();
		if (bodyLD <= SLENDER_BODY_LD_THRESHOLD) return 0;
		double machFactor;
		if (mach <= SLENDER_BODY_MACH_HIGH) {
			machFactor = smoothstep((mach - SLENDER_BODY_MACH_LOW)
					/ (SLENDER_BODY_MACH_HIGH - SLENDER_BODY_MACH_LOW));
		} else if (mach >= SLENDER_BODY_MACH_DECAY_START) {
			machFactor = 1 - smoothstep((mach - SLENDER_BODY_MACH_DECAY_START)
					/ (SLENDER_BODY_MACH_DECAY_END - SLENDER_BODY_MACH_DECAY_START));
		} else {
			machFactor = 1;
		}
		double ldExcess = Math.min(
				bodyLD - SLENDER_BODY_LD_THRESHOLD, SLENDER_BODY_LD_EXCESS_CAP);
		return SLENDER_BODY_PRESSURE_K * ldExcess * machFactor;
	}

	private static double computeBodyTubeLength(FlightConfiguration configuration) {
		double total = 0;
		for (Map.Entry<RocketComponent, ArrayList<InstanceContext>> entry
				: configuration.getActiveInstances().entrySet()) {
			if (!(entry.getKey() instanceof SymmetricComponent component)) continue;
			double maximumRadius = Math.max(component.getForeRadius(), component.getAftRadius());
			if (maximumRadius > MathUtil.EPSILON
					&& Math.abs(component.getForeRadius() - component.getAftRadius())
							/ maximumRadius < 0.01) {
				total += component.getLength() * entry.getValue().size();
			}
		}
		return total;
	}

	private static double computeBaseStationX(SymmetricComponent component,
			ArrayList<InstanceContext> contexts) {
		if (contexts != null && !contexts.isEmpty()) {
			CoordinateIF location = contexts.get(0).getLocation();
			return location.getX() + component.getLength();
		}
		return component.getLength();
	}

	private static double smoothstep(double value) {
		double bounded = MathUtil.clamp(value, 0, 1);
		return bounded * bounded * (3 - 2 * bounded);
	}

	private double calculateOverrideCD(FlightConfiguration configuration,
			Map<RocketComponent, AerodynamicForces> componentForces,
			Map<RocketComponent, AerodynamicForces> assemblyForces) {
		ensureCalcMap(configuration);

		double total = 0;
		InstanceMap imap = configuration.getActiveInstances();
		for (Map.Entry<RocketComponent, ArrayList<InstanceContext>> entry : imap.entrySet()) {
			RocketComponent c = entry.getKey();
			int instanceCount = entry.getValue().size();

			if (!c.isAerodynamic() && !(c instanceof ComponentAssembly)) {
				continue;
			}

			if (c.isCDOverridden() && !c.isCDOverriddenByAncestor()) {
				double cd = instanceCount * c.getOverrideCD();
				Map<RocketComponent, AerodynamicForces> targetMap = (c instanceof ComponentAssembly) ? assemblyForces
						: componentForces;
				if (targetMap != null && targetMap.get(c) != null) {
					targetMap.get(c).setOverrideCD(cd);
				}
				total += cd;
			}
		}

		return total;
	}

	private double calculateAxialCD(FlightConditions conditions, double cd) {
		double aoa = MathUtil.clamp(conditions.getAOA(), 0, Math.PI);
		double mul;

		if (aoa > Math.PI / 2) {
			aoa = Math.PI - aoa;
		}
		if (aoa < 17 * Math.PI / 180) {
			mul = PolyInterpolator.eval(aoa, axialDragPoly1);
		} else {
			mul = PolyInterpolator.eval(aoa, axialDragPoly2);
		}

		if (conditions.getAOA() < Math.PI / 2) {
			return mul * cd;
		}
		return -mul * cd;
	}

	public static double calculateStagnationCD(double m) {
		double pressure;
		if (m <= 1) {
			pressure = 1 + pow2(m) / 4 + pow2(pow2(m)) / 40;
		} else {
			pressure = 1.84 - 0.76 / pow2(m) + 0.166 / pow2(pow2(m)) + 0.035 / pow2(m * m * m);
		}
		return 0.85 * pressure;
	}

	public static double calculateBaseCD(double m) {
		if (m <= 1) {
			return 0.12 + 0.13 * m * m;
		}
		return 0.25 / m;
	}

	private void ensureCalcMap(FlightConfiguration configuration) {
		if (calcMap == null) {
			buildCalcMap(configuration);
		}
	}

	private void buildCalcMap(FlightConfiguration configuration) {
		calcMap = new HashMap<>();

		for (RocketComponent comp : configuration.getAllComponents()) {
			if (!comp.isAerodynamic() && !(comp instanceof ComponentAssembly)) {
				continue;
			}

			RocketComponentCalc calcObj = (RocketComponentCalc) Reflection.construct(
					BARROWMAN_PACKAGE,
					comp,
					BARROWMAN_SUFFIX,
					comp);

			calcMap.put(comp, calcObj);
		}
	}
}
