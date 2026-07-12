package info.openrocket.core.aerodynamics.physicsaero.fin;

import java.util.*;
import info.openrocket.core.aerodynamics.physicsaero.api.*;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.flow.ShockEvent;
import info.openrocket.core.aerodynamics.physicsaero.force.*;
import info.openrocket.core.aerodynamics.physicsaero.geometry.*;
import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricEdgeStateHistory;
import info.openrocket.core.aerodynamics.physicsaero.interaction.*;
import info.openrocket.core.util.Coordinate;

/** Individual-fin, individual-strip isolated loading with exclusive physical ownership. */
public final class SupersonicFinSolver {
	private final int stripCount;
	public SupersonicFinSolver() { this(24); }
	public SupersonicFinSolver(int stripCount) { if (stripCount < 2) throw new IllegalArgumentException(); this.stripCount = stripCount; }
	public FinResult evaluate(AeroGeometry geometry, FlowCondition flow) {
		return evaluate(geometry, flow, new AxisymmetricEdgeStateHistory(List.of(), List.of()));
	}
	public FinResult evaluate(AeroGeometry geometry, FlowCondition flow, AxisymmetricEdgeStateHistory bodyHistory) {
		if (flow.mach() < 1.2 || flow.mach() > 5) throw new IllegalArgumentException("OUTSIDE_PHASE3_MACH_RANGE");
		ContributionLedger ledger = new ContributionLedger(); List<FinLocalFlow> localFlows = new ArrayList<>(); Map<String, String> diagnostics = new LinkedHashMap<>();
		FinGeometryAdapter adapter = new FinGeometryAdapter(); FinStripDiscretizer discretizer = new FinStripDiscretizer();
		AckeretThinFinModel ackeret = new AckeretThinFinModel(); DatcomFinLiftModel datcom = new DatcomFinLiftModel(); FinMethodSelector selector = new FinMethodSelector();
		List<AeroComponent> finSets = geometry.components().stream().filter(c -> c.finGeometry() != null)
				.sorted(Comparator.comparingDouble(AeroComponent::axialStartM)).toList();
		if (finSets.size() > 1) diagnostics.put("interactionTopology", "CANARD_AFT_FIN_INTERACTION_PENDING");
		for (AeroComponent component : finSets) for (FinGeometryAdapter.PhysicalFin fin : adapter.expand(component,
				finSets.size() > 1 && component == finSets.get(0) ? FinRole.CANARD : FinRole.FIN)) {
			List<FinStrip> strips = discretizer.discretize(fin, stripCount); List<FinLocalFlow> finFlows = new ArrayList<>();
			List<FinLocalFlow> isolatedFlows = new ArrayList<>(), upwashedFlows = new ArrayList<>();
			FinSectionFamily family = strips.get(0).sectionFamily();
			boolean allPressureValid = true; double totalN = 0, upwashedN = 0, isolatedN = 0; double totalWaveDrag = 0; double weightedX = 0;
			for (FinStrip strip : strips) {
				BodyUpwashModel.UpwashResult upwash = new SlenderCircularBodyUpwashModel().evaluate(geometry, strip, flow, bodyHistory);
				Coordinate upwashVelocity = upwash.valid() ? upwash.velocityIncrementBody() : new Coordinate();
				FinLocalFlowFactory flowFactory = new FinLocalFlowFactory();
				FinLocalFlow isolated = flowFactory.fromFreestream(flow, strip, new Coordinate(), 0.02);
				FinLocalFlow upwashed = flowFactory.fromFreestream(flow, strip, upwashVelocity, 0.02);
				FinLocalFlow local = upwashed;
				for (var event : bodyHistory.events()) if (event instanceof ShockEvent shock) {
					var intersection = new ShockToFinIntersectionResolver().resolve(shock, strip, Math.max(1e-4, strip.spanwiseWidthM() * 0.5));
					if (intersection.intersects()) {
						double angle = intersection.replacementState().flowAngleRad(); Coordinate radial = strip.localFrame().spanwise();
						Coordinate direction = new Coordinate(Math.cos(angle), radial.y * Math.sin(angle), radial.z * Math.sin(angle));
						local = flowFactory.replaceWithPostShock(intersection.replacementState(), direction, strip, 0.02,
								shock.methodId() + "@" + shock.xM()); break;
					}
				}
				isolatedFlows.add(isolated); upwashedFlows.add(upwashed); finFlows.add(local); localFlows.add(local);
				PressureLoad actualLoad = pressureLoad(family, local, strip, flow, ackeret);
				PressureLoad upwashedLoad = pressureLoad(family, upwashed, strip, flow, ackeret);
				PressureLoad isolatedLoad = pressureLoad(family, isolated, strip, flow, ackeret);
				allPressureValid &= actualLoad.valid();
				if (actualLoad.valid()) { totalN += actualLoad.normalN(); totalWaveDrag += actualLoad.axialN(); weightedX += actualLoad.normalN() * strip.centroidBodyM().x; }
				if (upwashedLoad.valid()) upwashedN += upwashedLoad.normalN();
				if (isolatedLoad.valid()) isolatedN += isolatedLoad.normalN();
			}
			double area = strips.stream().mapToDouble(FinStrip::areaM2).sum(); double ar = fin.geometry().spanM() * fin.geometry().spanM() / area;
			double meanIncidence = finFlows.stream().mapToDouble(FinLocalFlow::effectiveIncidenceRad).average().orElse(0);
			double meanUpwashedIncidence = upwashedFlows.stream().mapToDouble(FinLocalFlow::effectiveIncidenceRad).average().orElse(0);
			double meanIsolatedIncidence = isolatedFlows.stream().mapToDouble(FinLocalFlow::effectiveIncidenceRad).average().orElse(0);
			// DATCOM owns the isolated fin load.  Body upwash and shock impingement are interaction
			// increments, and must not invalidate an otherwise valid base method.  In particular, a
			// requested incidence inside the table domain can be pushed just outside DATCOM's declared
			// +/-15 degree range by the circular-body upwash model.  Extrapolating DATCOM there would be
			// silent out-of-domain physics; rejecting the complete fin load aborts an otherwise valid
			// full-regime build.  Select from the isolated state and explicitly drop only an invalid
			// interaction increment below.
			boolean datcomValid = datcom.isValid(flow.mach(), ar, meanIsolatedIncidence);
			FinMethodSelector.Selection selection = selector.select(family, allPressureValid, datcomValid);
			MethodId method; boolean localDatcomFallback = false;
			if (selection.authoritative() == FinMethodSelector.Method.DATCOM) {
				double sweep = strips.stream().mapToDouble(FinStrip::halfChordSweepRad).average().orElse(0);
				boolean actualValid = datcom.isValid(flow.mach(), ar, meanIncidence);
				boolean upwashedValid = datcom.isValid(flow.mach(), ar, meanUpwashedIncidence);
				DatcomFinLiftModel.Result isolatedResult = datcom.evaluate(flow.mach(), ar, sweep, meanIsolatedIncidence);
				DatcomFinLiftModel.Result result = actualValid
						? datcom.evaluate(flow.mach(), ar, sweep, meanIncidence) : isolatedResult;
				totalN = flow.dynamicPressurePa() * area * result.normalForceCoefficient(); method = new MethodId(result.methodId());
				upwashedN = flow.dynamicPressurePa() * area * (upwashedValid
						? datcom.evaluate(flow.mach(), ar, sweep, meanUpwashedIncidence).normalForceCoefficient()
						: isolatedResult.normalForceCoefficient());
				isolatedN = flow.dynamicPressurePa() * area * isolatedResult.normalForceCoefficient();
				localDatcomFallback = !actualValid || !upwashedValid;
				if (localDatcomFallback) diagnostics.put(fin.id() + ":interactionFallback",
						"ISOLATED_DATCOM_LOAD_LOCAL_INTERACTION_OUTSIDE_INCIDENCE_DOMAIN");
				weightedX = totalN * new DatcomFinCenterOfPressureModel().halfMeanAerodynamicChordFallback(component.axialStartM(), area / fin.geometry().spanM()).xM();
				diagnostics.put(fin.id() + ":cp", "HALF_MAC_LOW_CONFIDENCE");
			} else if (selection.authoritative() == FinMethodSelector.Method.ACKERET) method = new MethodId(AckeretThinFinModel.METHOD_ID);
			else if (selection.authoritative() == FinMethodSelector.Method.SHOCK_EXPANSION) method = new MethodId(WedgeDiamondShockExpansionModel.METHOD_ID);
			else throw new IllegalArgumentException("NO_VALID_FIN_METHOD:" + fin.id());
			double xcp = Math.abs(totalN) > 1e-12 ? weightedX / totalN : strips.stream().mapToDouble(s -> s.centroidBodyM().x).average().orElse(component.axialStartM());
			Coordinate forceNormal = (Coordinate) fin.frame().normal().multiply(isolatedN);
			String region = fin.id(); PhysicalOwner liftOwner = new PhysicalOwner(PhysicalTerm.FIN_LIFT, OwnershipMode.REPLACES, region, null);
			ledger.add(new ForceContribution(fin.id(), liftOwner, method, forceNormal, new Coordinate(), new Coordinate(xcp,
					fin.frame().spanwise().y * component.rootRadiusM(), fin.frame().spanwise().z * component.rootRadiusM()), region,
					localDatcomFallback
							? List.of("INDIVIDUAL_FIN", fin.role().name(), selection.authoritative().name(),
									"LOCAL_INTERACTION_OUTSIDE_DATCOM_INCIDENCE_DOMAIN")
							: List.of("INDIVIDUAL_FIN", fin.role().name(), selection.authoritative().name()),
					localDatcomFallback ? 0.6 : 0.8, localDatcomFallback ? 0.3 : 0.15,
					localDatcomFallback ? "LOCAL_INTERACTION_OUTSIDE_DATCOM_INCIDENCE_DOMAIN" : null));
			double upwashDelta = upwashedN - isolatedN;
			if (Math.abs(upwashDelta) > 1e-12) ledger.add(new ForceContribution(fin.id(), new PhysicalOwner(
					PhysicalTerm.BODY_UPWASH_NORMAL_FORCE, OwnershipMode.MODIFIES, region, method),
					new MethodId(SlenderCircularBodyUpwashModel.METHOD_ID), (Coordinate) fin.frame().normal().multiply(upwashDelta),
					new Coordinate(), new Coordinate(xcp, fin.frame().spanwise().y * component.rootRadiusM(),
							fin.frame().spanwise().z * component.rootRadiusM()), region, List.of("UPWASH_INCREMENT_ONLY"), .6, .3, null));
			double shockDelta = totalN - upwashedN;
			if (Math.abs(shockDelta) > 1e-12) ledger.add(new ForceContribution(fin.id(), new PhysicalOwner(
					PhysicalTerm.SHOCK_IMPINGEMENT_PRESSURE, OwnershipMode.MODIFIES, region, method),
					new MethodId("BODY_SHOCK_TO_FIN_STATE_REPLACEMENT_V1"), (Coordinate) fin.frame().normal().multiply(shockDelta),
					new Coordinate(), new Coordinate(xcp, fin.frame().spanwise().y * component.rootRadiusM(),
							fin.frame().spanwise().z * component.rootRadiusM()), region, List.of("POST_SHOCK_STATE_INCREMENT_ONLY"), .6, .3, null));
			double drag = new FinLiftDependentDragModel().forceN(totalN, meanIncidence);
			ledger.add(new ForceContribution(fin.id(), new PhysicalOwner(PhysicalTerm.FIN_LIFT_DEPENDENT_DRAG, OwnershipMode.REPLACES, region, null),
					method, new Coordinate(drag, 0, 0), new Coordinate(), new Coordinate(xcp, 0, 0), region,
					List.of("PROJECTED_FROM_AUTHORITATIVE_LIFT"), 0.75, 0.2, null));
			if (totalWaveDrag > 0) ledger.add(new ForceContribution(fin.id(),
					new PhysicalOwner(PhysicalTerm.FIN_ZERO_LIFT_WAVE_DRAG, OwnershipMode.REPLACES, region, null), method,
					new Coordinate(totalWaveDrag, 0, 0), new Coordinate(), new Coordinate(xcp, 0, 0), region,
					List.of("PRESSURE_INTEGRATED_WAVE_DRAG"), 0.8, 0.15, null));
			diagnostics.put(fin.id(), selection.authoritative().name());
		}
		ReferenceState reference = new ReferenceState(flow.dynamicPressurePa(), geometry.references().referenceAreaM2(), geometry.references().referenceLengthM(), geometry.references().momentOriginM());
		return new FinResult(CoefficientAssembler.assemble(ledger, reference), ledger.entries(), localFlows, diagnostics);
	}
	private static PressureLoad pressureLoad(FinSectionFamily family, FinLocalFlow local, FinStrip strip,
			FlowCondition flow, AckeretThinFinModel ackeret) {
		if (family == FinSectionFamily.FLAT_PLATE) {
			boolean valid = ackeret.isValid(local.normalMach(), strip.thicknessToChord(), local.effectiveIncidenceRad(), local.effectiveIncidenceRad());
			return valid ? new PressureLoad(true, ackeret.evaluate(local.normalMach(), local.effectiveIncidenceRad(), strip.areaM2(), local.dynamicPressurePa()).normalForceN(), 0)
					: new PressureLoad(false, 0, 0);
		}
		if (family == FinSectionFamily.SYMMETRIC_DIAMOND && local.leadingEdge() == LeadingEdgeClassification.SUPERSONIC_LEADING_EDGE) {
			double angle = Math.atan(strip.thicknessToChord()); var result = new WedgeDiamondShockExpansionModel().evaluate(local.staticState(), flow.thermodynamics(),
					local.effectiveIncidenceRad(), new double[] {angle,-angle}, new double[] {angle,-angle}, new double[] {.5,.5}, strip.areaM2());
			return new PressureLoad(result.valid(), result.normalForceN(), result.axialForceN());
		}
		return new PressureLoad(false, 0, 0);
	}
	private record PressureLoad(boolean valid, double normalN, double axialN) {}
}
