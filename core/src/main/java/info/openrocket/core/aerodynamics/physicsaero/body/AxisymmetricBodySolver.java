package info.openrocket.core.aerodynamics.physicsaero.body;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalTerm;
import info.openrocket.core.aerodynamics.physicsaero.config.NumericalTolerances;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.force.*;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.util.Coordinate;

/** First authoritative unpowered, zero-incidence, supersonic body-pressure slice. */
public final class AxisymmetricBodySolver {
	private final NumericalTolerances tolerances;
	private final BasePressureCorrelation baseCorrelation;
	public AxisymmetricBodySolver() { this(NumericalTolerances.defaults(), new OpenRocketSupersonicBasePressureCorrelation()); }
	public AxisymmetricBodySolver(NumericalTolerances tolerances, BasePressureCorrelation baseCorrelation) {
		this.tolerances = tolerances; this.baseCorrelation = baseCorrelation;
	}
	public AxisymmetricBodyResult evaluate(AeroGeometry geometry, FlowCondition flow) {
		validate(flow);
		List<AxisymmetricBodySegment> segments = new AxisymmetricBodyPreprocessor().preprocess(geometry, flow.mach(), tolerances);
		List<BodyMethodSelector.Decision> decisions = segments.stream().map(s -> new BodyMethodSelector().select(s, flow.mach())).toList();
		if (decisions.stream().anyMatch(d -> !d.selected())) throw new IllegalArgumentException("NO_VALID_BODY_METHOD");
		AxisymmetricEdgeStateHistory history = new SurfaceStateMarcher().march(segments, flow);
		ContributionLedger ledger = new ContributionLedger(); List<ForceContribution> pressure = new ArrayList<>();
		ForebodyPressureIntegrator integrator = new ForebodyPressureIntegrator(); Map<String, String> diagnostics = new LinkedHashMap<>();
		for (AxisymmetricBodySegment segment : segments) {
			ForceContribution contribution = switch (segment.type()) {
				case TRUE_CONE, SMOOTH_COMPRESSION -> integrator.integrate(segment, history,
						flow.atmosphere().pressurePa(), PhysicalTerm.BODY_PRESSURE_FOREBODY);
				case DISCRETE_COMPRESSION_CORNER -> segment.endXM() > segment.startXM()
						? integrator.integrate(segment, history, flow.atmosphere().pressurePa(), PhysicalTerm.BODY_PRESSURE_TRANSITION) : null;
				case BOATTAIL, SMOOTH_EXPANSION -> new BoattailPressureModel().integrate(segment, history, flow.atmosphere().pressurePa());
				default -> null;
			};
			if (contribution != null) { ledger.add(contribution); pressure.add(contribution); }
			if (segment.type() == BodySegmentType.BOATTAIL
					&& new BoattailPressureModel().separationRiskPendingBoundaryLayer(segment, flow.mach()))
				diagnostics.put(segment.regionId(), "BOATTAIL_SEPARATION_RISK_PENDING_BL");
		}
		String baseComponent = segments.get(segments.size() - 1).componentId();
		ForceContribution base = new BaseDragModel(baseCorrelation).evaluate(geometry.references(), flow, baseComponent);
		ledger.add(base);
		ReferenceState reference = new ReferenceState(flow.dynamicPressurePa(), geometry.references().referenceAreaM2(),
				geometry.references().referenceLengthM(), geometry.references().momentOriginM());
		AerodynamicCoefficients coefficients = CoefficientAssembler.assemble(ledger, reference);
		double pressureCa = pressure.stream().mapToDouble(c -> c.forceBodyN().x).sum()
				/ (flow.dynamicPressurePa() * geometry.references().referenceAreaM2());
		AreaRuleWaveDragCheck.Result area = new AreaRuleWaveDragCheck().evaluate(segments,
				geometry.references().referenceAreaM2(), geometry.references().referenceLengthM(), pressureCa);
		diagnostics.put("baseMethod", baseCorrelation.methodId()); diagnostics.put("baseValidity", baseCorrelation.validity(flow.mach(), false).reason());
		diagnostics.put("areaRuleOwnership", "DIAGNOSTIC_ONLY");
		diagnostics.put("edgeStateCount", Integer.toString(history.states().size()));
		return new AxisymmetricBodyResult(coefficients, ledger.entries(), history, segments, decisions, area, diagnostics);
	}
	private static void validate(FlowCondition flow) {
		if (flow.powered()) throw new IllegalArgumentException("POWERED_BASE_MODEL_NOT_IMPLEMENTED");
		if (Math.abs(flow.alphaRad()) > 1e-12 || Math.abs(flow.betaRad()) > 1e-12) throw new IllegalArgumentException("ZERO_INCIDENCE_ONLY");
		if (flow.mach() < 1.2) throw new IllegalArgumentException("OUTSIDE_PHASE2_SUPERSONIC_RANGE");
		if (flow.mach() > 5) throw new IllegalArgumentException("THERMODYNAMIC_MODEL_NOT_VALIDATED_ABOVE_MACH_5");
	}
}
