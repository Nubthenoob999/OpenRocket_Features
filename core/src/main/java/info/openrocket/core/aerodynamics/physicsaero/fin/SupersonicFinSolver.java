package info.openrocket.core.aerodynamics.physicsaero.fin;

import java.util.*;
import info.openrocket.core.aerodynamics.physicsaero.api.*;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;
import info.openrocket.core.aerodynamics.physicsaero.flow.ShockEvent;
import info.openrocket.core.aerodynamics.physicsaero.force.*;
import info.openrocket.core.aerodynamics.physicsaero.geometry.*;
import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricEdgeStateHistory;
import info.openrocket.core.aerodynamics.physicsaero.body.HartTn3393SupersonicBasePressureCorrelation;
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
		SingleWedgeWaveDragModel singleWedgeWaveDrag = new SingleWedgeWaveDragModel();
		FinTrailingEdgeBaseDragModel finBaseDrag = new FinTrailingEdgeBaseDragModel();
		List<AeroComponent> finSets = geometry.components().stream().filter(c -> c.finGeometry() != null)
				.sorted(Comparator.comparingDouble(AeroComponent::axialStartM)).toList();
		if (finSets.size() > 1) diagnostics.put("interactionTopology", "CANARD_AFT_FIN_INTERACTION_PENDING");
		for (AeroComponent component : finSets) for (FinGeometryAdapter.PhysicalFin fin : adapter.expand(component,
				finSets.size() > 1 && component == finSets.get(0) ? FinRole.CANARD : FinRole.FIN)) {
			List<FinStrip> strips = discretizer.discretize(fin, stripCount); List<FinLocalFlow> finFlows = new ArrayList<>();
			List<FinLocalFlow> isolatedFlows = new ArrayList<>(), upwashedFlows = new ArrayList<>();
			List<SingleWedgeWaveDragModel.Result> singleWedgeProfileResults = new ArrayList<>();
			List<SymmetricSectionWaveDragFallbackModel.Result>
					symmetricSectionFallbackResults = new ArrayList<>();
			FinSectionFamily family = strips.get(0).sectionFamily();
			boolean allPressureValid = true; double totalN = 0, upwashedN = 0, isolatedN = 0; double totalWaveDrag = 0; double weightedX = 0;
			if (family == FinSectionFamily.FLAT_PLATE
					|| family == FinSectionFamily.ROUNDED_LEADING_EDGE) {
				double leadingEdgeCd = new FinLeadingEdgePressureDragModel().dragCoefficientPerFin(
						component, flow.mach(), geometry.references().referenceAreaM2());
				totalWaveDrag += flow.dynamicPressurePa()
						* geometry.references().referenceAreaM2() * leadingEdgeCd;
			}
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
				if (family == FinSectionFamily.SINGLE_WEDGE) {
					double wedgeAngle = component.localReferences().getOrDefault(
							"leadingEdgeWedgeAngleRad", Math.atan(strip.thicknessToChord()));
					SingleWedgeWaveDragModel.Result profile = singleWedgeWaveDrag.evaluate(
							isolated, strip, flow.thermodynamics(), wedgeAngle);
					singleWedgeProfileResults.add(profile);
					if (profile.valid()) totalWaveDrag += profile.axialForceN();
				}
				PressureLoad actualLoad = pressureLoad(component, family, local, strip, flow, ackeret);
				PressureLoad upwashedLoad = pressureLoad(component, family, upwashed, strip, flow, ackeret);
				PressureLoad isolatedLoad = pressureLoad(component, family, isolated, strip, flow, ackeret);
				allPressureValid &= actualLoad.valid();
				if (!actualLoad.valid()
						&& family == FinSectionFamily.SYMMETRIC_DIAMOND) {
					var layout = new FinSectionPanelGeometry().layout(
							component, strip);
					var fallback =
							new SymmetricSectionWaveDragFallbackModel().evaluate(
									local, strip, layout,
									flow.thermodynamics(),
									"ATTACHED_SHOCK_EXPANSION_INVALID");
					symmetricSectionFallbackResults.add(fallback);
					if (fallback.valid()) {
						totalWaveDrag += fallback.axialForceN();
					}
				}
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
			double isolatedDatcomIncidence = boundedDatcomIncidence(meanIsolatedIncidence);
			boolean datcomIncidenceHeld = isolatedDatcomIncidence != meanIsolatedIncidence;
			boolean datcomValid = datcom.isValid(flow.mach(), ar, isolatedDatcomIncidence);
			FinMethodSelector.Selection selection = selector.select(family, allPressureValid, datcomValid);
			MethodId method; boolean localDatcomFallback = false;
			if (selection.authoritative() == FinMethodSelector.Method.DATCOM) {
				double sweep = strips.stream().mapToDouble(FinStrip::halfChordSweepRad).average().orElse(0);
				double actualDatcomIncidence = boundedDatcomIncidence(meanIncidence);
				double upwashedDatcomIncidence = boundedDatcomIncidence(meanUpwashedIncidence);
				datcomIncidenceHeld |= actualDatcomIncidence != meanIncidence
						|| upwashedDatcomIncidence != meanUpwashedIncidence;
				boolean actualValid = datcom.isValid(flow.mach(), ar, actualDatcomIncidence);
				boolean upwashedValid = datcom.isValid(flow.mach(), ar, upwashedDatcomIncidence);
				DatcomFinLiftModel.Result isolatedResult = datcom.evaluate(flow.mach(), ar, sweep, isolatedDatcomIncidence);
				DatcomFinLiftModel.Result result = actualValid
						? datcom.evaluate(flow.mach(), ar, sweep, actualDatcomIncidence) : isolatedResult;
				totalN = flow.dynamicPressurePa() * area * result.normalForceCoefficient(); method = new MethodId(result.methodId());
				upwashedN = flow.dynamicPressurePa() * area * (upwashedValid
						? datcom.evaluate(flow.mach(), ar, sweep, upwashedDatcomIncidence).normalForceCoefficient()
						: isolatedResult.normalForceCoefficient());
				isolatedN = flow.dynamicPressurePa() * area * isolatedResult.normalForceCoefficient();
				localDatcomFallback = !actualValid || !upwashedValid;
				if (localDatcomFallback) diagnostics.put(fin.id() + ":interactionFallback",
						"ISOLATED_DATCOM_LOAD_LOCAL_INTERACTION_OUTSIDE_INCIDENCE_DOMAIN");
				if (datcomIncidenceHeld) diagnostics.put(fin.id() + ":incidenceBoundary",
						"DATCOM_FIN_LOAD_HELD_AT_15DEG_SOURCE_BOUNDARY");
				weightedX = totalN * new DatcomFinCenterOfPressureModel().halfMeanAerodynamicChordFallback(component.axialStartM(), area / fin.geometry().spanM()).xM();
				diagnostics.put(fin.id() + ":cp", "HALF_MAC_LOW_CONFIDENCE");
			} else if (selection.authoritative() == FinMethodSelector.Method.ACKERET) method = new MethodId(AckeretThinFinModel.METHOD_ID);
			else if (selection.authoritative() == FinMethodSelector.Method.SHOCK_EXPANSION) method = new MethodId(WedgeDiamondShockExpansionModel.METHOD_ID);
			else throw new IllegalArgumentException("NO_VALID_FIN_METHOD:" + fin.id()
					+ ":family=" + family + ":mach=" + flow.mach()
					+ ":aspectRatio=" + ar + ":isolatedIncidenceRad="
					+ meanIsolatedIncidence + ":pressureValid=" + allPressureValid
					+ ":datcomValid=" + datcomValid);
			double xcp = Math.abs(totalN) > 1e-12 ? weightedX / totalN : strips.stream().mapToDouble(s -> s.centroidBodyM().x).average().orElse(component.axialStartM());
			Coordinate forceNormal = (Coordinate) fin.frame().normal().multiply(isolatedN);
			String region = fin.id(); PhysicalOwner liftOwner = new PhysicalOwner(PhysicalTerm.FIN_LIFT, OwnershipMode.REPLACES, region, null);
			List<String> finValidity = new ArrayList<>(List.of(
					"INDIVIDUAL_FIN", fin.role().name(), selection.authoritative().name()));
			if (localDatcomFallback) finValidity.add(
					"LOCAL_INTERACTION_OUTSIDE_DATCOM_INCIDENCE_DOMAIN");
			if (datcomIncidenceHeld) finValidity.add(
					"DATCOM_INCIDENCE_HELD_AT_15DEG_SOURCE_BOUNDARY");
			boolean reducedConfidence = localDatcomFallback || datcomIncidenceHeld;
			ledger.add(new ForceContribution(fin.id(), liftOwner, method, forceNormal, new Coordinate(), new Coordinate(xcp,
					fin.frame().spanwise().y * component.rootRadiusM(), fin.frame().spanwise().z * component.rootRadiusM()), region,
					finValidity,
					reducedConfidence ? 0.5 : 0.8, reducedConfidence ? 0.4 : 0.15,
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
			if (totalWaveDrag > 0) {
				ProfileDragAttribution profileAttribution = family == FinSectionFamily.SINGLE_WEDGE
						? profileDragAttribution(singleWedgeProfileResults)
						: family == FinSectionFamily.FLAT_PLATE
								|| family == FinSectionFamily.ROUNDED_LEADING_EDGE
								? new ProfileDragAttribution(
										new MethodId(FinLeadingEdgePressureDragModel.METHOD_ID),
										List.of("PROJECTED_LEADING_EDGE_FRONTAL_AREA",
												"SWEPT_EDGE_PRESSURE_RELIEF",
												"TRAILING_EDGE_BASE_PRESSURE_EXCLUDED"),
										0.75, 0.20, null)
						: !symmetricSectionFallbackResults.isEmpty()
								? symmetricSectionFallbackAttribution(
										symmetricSectionFallbackResults,
										strips.size())
								: new ProfileDragAttribution(method,
										List.of("PRESSURE_INTEGRATED_WAVE_DRAG"),
										0.8, 0.15, null);
				ledger.add(new ForceContribution(fin.id(),
						new PhysicalOwner(PhysicalTerm.FIN_ZERO_LIFT_WAVE_DRAG, OwnershipMode.REPLACES, region, null),
						profileAttribution.method(), new Coordinate(totalWaveDrag, 0, 0), new Coordinate(),
						new Coordinate(xcp, 0, 0), region, profileAttribution.validityFlags(),
						profileAttribution.confidence(), profileAttribution.uncertainty(),
						profileAttribution.fallbackReason()));
				diagnostics.put(fin.id() + ":profileDrag", profileAttribution.method().value());
				if (profileAttribution.fallbackReason() != null) diagnostics.put(
						fin.id() + ":profileDragFallback", profileAttribution.fallbackReason());
			}
			double finBaseArea = finBaseDrag.trailingEdgeAreaPerFinM2(component);
			if (finBaseArea > 0) {
				double baseCpMagnitude = -new HartTn3393SupersonicBasePressureCorrelation()
						.basePressureCoefficient(flow.mach(), flow.thermodynamics().gamma(flow.atmosphere().temperatureK()));
				double baseForce = baseCpMagnitude * flow.dynamicPressurePa() * finBaseArea;
				ledger.add(new ForceContribution(fin.id(), new PhysicalOwner(PhysicalTerm.FIN_BASE_PRESSURE_DRAG,
						OwnershipMode.REPLACES, region, null), new MethodId(FinTrailingEdgeBaseDragModel.METHOD_ID),
						new Coordinate(baseForce, 0, 0), new Coordinate(),
						new Coordinate(component.axialEndM(), 0, 0), region,
						List.of("ACTUAL_FIN_TRAILING_EDGE_AREA",
								"BODY_AND_FIN_BASE_PRESSURE_EQUAL_A53D02",
								"SHARED_PRIMARY_BASE_PRESSURE_CORRELATION"),
						0.65, 0.30,
						"FIN_BASE_PRESSURE_USES_A53D02_EQUAL_PRESSURE_ASSUMPTION"));
			}
			diagnostics.put(fin.id(), selection.authoritative().name());
			if (family == FinSectionFamily.SYMMETRIC_DIAMOND) {
				diagnostics.put(fin.id() + ":sectionGeometry",
						new FinSectionPanelGeometry().layout(component,
								strips.get(0)).methodId());
			}
		}
		ReferenceState reference = new ReferenceState(flow.dynamicPressurePa(), geometry.references().referenceAreaM2(), geometry.references().referenceLengthM(), geometry.references().momentOriginM());
		return new FinResult(CoefficientAssembler.assemble(ledger, reference), ledger.entries(), localFlows, diagnostics);
	}

	private static double boundedDatcomIncidence(double incidenceRad) {
		double limit = Math.toRadians(15);
		return Math.max(-limit, Math.min(limit, incidenceRad));
	}
	private static PressureLoad pressureLoad(AeroComponent component,
			FinSectionFamily family, FinLocalFlow local, FinStrip strip,
			FlowCondition flow, AckeretThinFinModel ackeret) {
		if (family == FinSectionFamily.FLAT_PLATE) {
			boolean valid = ackeret.isValid(local.normalMach(), strip.thicknessToChord(), local.effectiveIncidenceRad(), local.effectiveIncidenceRad());
			return valid ? new PressureLoad(true, ackeret.evaluate(local.normalMach(), local.effectiveIncidenceRad(), strip.areaM2(), local.dynamicPressurePa()).normalForceN(), 0)
					: new PressureLoad(false, 0, 0);
		}
		if (family == FinSectionFamily.SYMMETRIC_DIAMOND && local.leadingEdge() == LeadingEdgeClassification.SUPERSONIC_LEADING_EDGE) {
			// Apply the supersonic swept-wing independence principle.  The
			// shock-expansion march sees the velocity and section slopes normal to
			// the leading edge, while the resulting gauge pressures still project
			// onto the actual fin-panel slopes in the rocket axial direction.
			double streamMach = local.staticState().mach();
			double normalMach = local.normalMach();
			double normalVelocityFraction = normalMach / streamMach;
			double normalVelocity = local.staticState().velocityMS() * normalVelocityFraction;
			GasState normalState = new GasState(normalMach,
					local.staticState().pressurePa(), local.staticState().temperatureK(),
					local.staticState().densityKgM3(), normalVelocity);
			FinSectionPanelGeometry.PanelLayout layout =
					new FinSectionPanelGeometry().layout(component, strip);
			double[] actualPanelAngles = layout.surfaceAnglesRad();
			double[] normalPanelAngles = new double[actualPanelAngles.length];
			for (int i = 0; i < actualPanelAngles.length; i++) {
				normalPanelAngles[i] = Math.atan(
						Math.tan(actualPanelAngles[i])
								/ normalVelocityFraction);
			}
			double normalIncidence = Math.atan(
					Math.tan(local.effectiveIncidenceRad()) / normalVelocityFraction);
			double[] fractions = layout.chordFractions();
			var result = new WedgeDiamondShockExpansionModel().evaluate(
					normalState, flow.thermodynamics(), normalIncidence,
					normalPanelAngles, normalPanelAngles, fractions, strip.areaM2());
			if (!result.valid()) return new PressureLoad(false, 0, 0);
			double normalForce = 0;
			double axialForce = 0;
			for (int i = 0; i < fractions.length; i++) {
				double panelArea = strip.areaM2() * fractions[i];
				double upperGauge = result.upper().panelStates().get(i).pressurePa()
						- normalState.pressurePa();
				double lowerGauge = result.lower().panelStates().get(i).pressurePa()
						- normalState.pressurePa();
				normalForce += (lowerGauge - upperGauge) * panelArea;
				axialForce += (upperGauge * Math.tan(actualPanelAngles[i])
						+ lowerGauge * Math.tan(actualPanelAngles[i])) * panelArea;
			}
			return new PressureLoad(true, normalForce, Math.max(0, axialForce));
		}
		return new PressureLoad(false, 0, 0);
	}
	private static ProfileDragAttribution profileDragAttribution(
			List<SingleWedgeWaveDragModel.Result> results) {
		List<SingleWedgeWaveDragModel.Result> valid = results.stream()
				.filter(SingleWedgeWaveDragModel.Result::valid).toList();
		if (valid.isEmpty()) throw new IllegalArgumentException("no valid single-wedge profile-drag strips");
		Set<String> methodIds = new LinkedHashSet<>();
		LinkedHashSet<String> flags = new LinkedHashSet<>();
		for (SingleWedgeWaveDragModel.Result result : valid) {
			methodIds.add(result.methodId()); flags.addAll(result.validityFlags());
		}
		if (valid.size() != results.size()) flags.add("PARTIAL_PROFILE_DRAG_LOCAL_STATE_UNAVAILABLE");
		String methodId = methodIds.size() == 1 ? methodIds.iterator().next()
				: SingleWedgeWaveDragModel.MIXED_METHOD_ID;
		List<String> fallbackReasons = valid.stream().map(SingleWedgeWaveDragModel.Result::fallbackReason)
				.filter(Objects::nonNull).distinct().toList();
		String fallbackReason = fallbackReasons.isEmpty() ? null : fallbackReasons.size() == 1
				? fallbackReasons.get(0) : "MIXED_LOCAL_LEADING_EDGE_PROFILE_DRAG_FALLBACK";
		boolean fallback = fallbackReason != null || valid.size() != results.size();
		return new ProfileDragAttribution(new MethodId(methodId), List.copyOf(flags), fallback ? 0.55 : 0.8,
				fallback ? 0.35 : 0.15, fallbackReason);
	}
	private static ProfileDragAttribution symmetricSectionFallbackAttribution(
			List<SymmetricSectionWaveDragFallbackModel.Result> results,
			int stripCount) {
		List<SymmetricSectionWaveDragFallbackModel.Result> valid =
				results.stream().filter(
						SymmetricSectionWaveDragFallbackModel.Result::valid)
						.toList();
		if (valid.isEmpty()) {
			throw new IllegalArgumentException(
					"no valid symmetric-section profile-drag fallback strips");
		}
		LinkedHashSet<String> flags = new LinkedHashSet<>();
		for (var result : valid) flags.addAll(result.validityFlags());
		boolean mixed = valid.size() < stripCount;
		if (mixed) flags.add(
				"PARTIAL_EXACT_PARTIAL_LINEARIZED_PROFILE_DRAG");
		String fallbackReason = valid.stream()
				.map(SymmetricSectionWaveDragFallbackModel.Result::fallbackReason)
				.filter(Objects::nonNull).distinct()
				.collect(java.util.stream.Collectors.joining("|"));
		return new ProfileDragAttribution(
				new MethodId(mixed
						? SymmetricSectionWaveDragFallbackModel.MIXED_METHOD_ID
						: SymmetricSectionWaveDragFallbackModel.METHOD_ID),
				List.copyOf(flags), 0.5, 0.4,
				fallbackReason.isBlank()
						? "SYMMETRIC_SECTION_PROFILE_DRAG_FALLBACK"
						: fallbackReason);
	}
	private record PressureLoad(boolean valid, double normalN, double axialN) {}
	private record ProfileDragAttribution(MethodId method, List<String> validityFlags,
			double confidence, double uncertainty, String fallbackReason) {}
}
