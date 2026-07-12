package info.openrocket.core.aerodynamics.physicsaero.table;

import java.util.*;
import info.openrocket.core.aerodynamics.physicsaero.body.*;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.*;
import info.openrocket.core.aerodynamics.physicsaero.fin.*;
import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.force.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ThermodynamicModel;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.interaction.BodyFinInterferenceSolver;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.*;
import info.openrocket.core.aerodynamics.physicsaero.coupling.OneWayViscousCoupling;
import info.openrocket.core.aerodynamics.physicsaero.flow.ShockEvent;

/** Populates direct Mach/alpha/beta six-axis cells from Phase 2 body drag plus Phase 3 individual fins. */
public final class CombinedBodyFinTableBuilder {
	private final boolean pnkEnabled;
	private final boolean phaseFiveEnabled;
	public CombinedBodyFinTableBuilder() { this(false, false); }
	/** PNK is opt-in so the isolated-fin validation gate remains enforceable. */
	public CombinedBodyFinTableBuilder(boolean pnkEnabled) { this(pnkEnabled, false); }
	/** Phase-5 coupling is explicit so Phase-1-to-3 pressure validation remains independently reproducible. */
	public CombinedBodyFinTableBuilder(boolean pnkEnabled, boolean phaseFiveEnabled) { this.pnkEnabled = pnkEnabled; this.phaseFiveEnabled = phaseFiveEnabled; }
	public AerodynamicTable build(AeroGeometry geometry, double[] machAxis, double[] alphaAxis, double[] betaAxis,
			AtmosphereState atmosphere, ThermodynamicModel model, TableMetadata metadata) {
		TableAxes axes = new TableAxes(machAxis, alphaAxis, betaAxis); List<TableCell> cells = new ArrayList<>();
		Map<Double, AxisymmetricBodyResult> bodies = new HashMap<>();
		for (double mach : machAxis) for (double alpha : alphaAxis) for (double beta : betaAxis) {
			AxisymmetricBodyResult body = bodies.computeIfAbsent(mach, value -> new AxisymmetricBodySolver().evaluate(geometry,
					FlowCondition.fromAngles(value, 0, 0, atmosphere, model, false, geometry.geometryHash())));
			FlowCondition flow = FlowCondition.fromAngles(mach, alpha, beta, atmosphere, model, false, geometry.geometryHash());
			FinResult fins = new SupersonicFinSolver().evaluate(geometry, flow, body.edgeStateHistory()); ContributionLedger ledger = new ContributionLedger();
			body.contributions().forEach(ledger::add); fins.contributions().forEach(ledger::add);
			if (pnkEnabled) new BodyFinInterferenceSolver().evaluate(geometry, mach, fins.contributions()).forEach(ledger::add);
			ReferenceState reference = new ReferenceState(flow.dynamicPressurePa(), geometry.references().referenceAreaM2(),
					geometry.references().referenceLengthM(), geometry.references().momentOriginM());
			List<String> phaseFiveMessages = new ArrayList<>(); boolean phaseFiveInvalid = false;
			if (phaseFiveEnabled) {
				try {
					double roughness = geometry.components().stream().filter(c -> c.axisymmetricProfile() != null).mapToDouble(c -> c.roughnessM()).max().orElse(0);
					String componentId = body.segments().isEmpty() ? "axisymmetric-body" : body.segments().get(0).componentId();
					BoundaryLayerConfiguration blConfig = BoundaryLayerConfiguration.defaults();
					SurfaceTrack track = new SurfaceTrackBuilder().fromAxisymmetricHistory(componentId, "axisymmetric-body-stack", body.edgeStateHistory(), roughness, blConfig);
					BoundaryLayerResult bl = new BoundaryLayerMarcher().march(track, blConfig, reference.momentOriginM());
					List<ShockEvent> shocks = body.edgeStateHistory().events().stream().filter(ShockEvent.class::isInstance).map(ShockEvent.class::cast).toList();
					OneWayViscousCoupling.Result coupled = new OneWayViscousCoupling().apply(bl.history(), shocks,
							geometry.references().maximumBodyDiameterM(), geometry.references().vehicleLengthM(), reference,
							reference.momentOriginM(), flow.thermodynamics().gamma(flow.atmosphere().temperatureK()), .72);
					coupled.contributions().forEach(ledger::add);
					for (var interaction : coupled.interactions()) phaseFiveMessages.add("SWBLI:" + interaction.interactionId() + ':' + interaction.classification()
							+ ":risk=" + interaction.thermalRisk() + ":confidence=" + interaction.confidence());
					for (var wake : coupled.wakes()) phaseFiveMessages.add("WAKE:" + wake.originComponent() + ':' + wake.startM() + ':' + wake.endM() + ':' + wake.validity());
					phaseFiveMessages.addAll(coupled.diagnostics());
				} catch (BoundaryLayerException | IllegalArgumentException ex) {
					phaseFiveInvalid = true; phaseFiveMessages.add("PHASE5_INVALID:" + ex.getClass().getSimpleName() + ':' + ex.getMessage());
				}
			}
			List<ForceContribution> all = ledger.entries(); boolean fallback = all.stream().anyMatch(c -> c.methodId().value().contains("DATCOM"));
			List<String> validity = new ArrayList<>(); validity.add("INDIVIDUAL_FIN_3D");
			if (alpha != 0 || beta != 0) validity.add("BODY_NONZERO_INCIDENCE_UNOWNED_PHASE3");
			validity.add(pnkEnabled ? "PNK_ENABLED_AFTER_ISOLATED_VALIDATION_GATE" : "PNK_DISABLED_ISOLATED_VALIDATION_GATE");
			if (phaseFiveEnabled) validity.add(phaseFiveInvalid ? "PHASE5_VISCOUS_COUPLING_INVALID" : "PHASE5_ONE_WAY_VISCOUS_COUPLING");
			cells.add(new TableCell(CoefficientAssembler.assemble(ledger, reference), grouped(all, reference, true), grouped(all, reference, false),
					all.stream().map(c -> c.methodId().value()).distinct().sorted().toList(), new double[] {.75,.75,.75,.7,.7,.7},
					new double[] {.2,.2,.2,.25,.25,.25}, validity, reference,
					new CellDiagnostics(phaseFiveInvalid ? Set.of(DiagnosticFlag.DIRECT_GENERATION, DiagnosticFlag.SEPARATION) :
							fallback ? Set.of(DiagnosticFlag.DIRECT_GENERATION, DiagnosticFlag.FALLBACK_USED) : Set.of(DiagnosticFlag.DIRECT_GENERATION),
							fallback, fallback ? DatcomFinLiftModel.METHOD_ID : null, phaseFiveInvalid ? "INVALID_LOW_ORDER_VISCOUS_COUPLING" : fallback ? "LOCAL_PRESSURE_INVALID" : null, phaseFiveMessages), true));
		}
		return new AerodynamicTable(axes, cells, metadata);
	}
	private static Map<String, AerodynamicCoefficients> grouped(List<ForceContribution> values, ReferenceState reference, boolean component) {
		Map<String, List<ForceContribution>> groups = new TreeMap<>();
		for (ForceContribution c : values) groups.computeIfAbsent(component ? c.componentId() : c.owner().term().name(), key -> new ArrayList<>()).add(c);
		Map<String, AerodynamicCoefficients> result = new TreeMap<>();
		groups.forEach((key, contributions) -> {
			ContributionLedger ledger = new ContributionLedger();
			for (ForceContribution contribution : contributions) {
				if (!component && contribution.owner().mode() != info.openrocket.core.aerodynamics.physicsaero.api.OwnershipMode.REPLACES
						&& contribution.owner().mode() != info.openrocket.core.aerodynamics.physicsaero.api.OwnershipMode.DIAGNOSTIC_ONLY) {
					var owner = new info.openrocket.core.aerodynamics.physicsaero.api.PhysicalOwner(contribution.owner().term(),
							info.openrocket.core.aerodynamics.physicsaero.api.OwnershipMode.REPLACES, contribution.regionId(), null);
					ledger.add(new ForceContribution(contribution.componentId(), owner, contribution.methodId(), contribution.forceBodyN(),
							contribution.intrinsicMomentBodyNm(), contribution.applicationPointM(), contribution.regionId(),
							contribution.validityFlags(), contribution.confidence(), contribution.uncertainty(), contribution.fallbackReason()));
				} else ledger.add(contribution);
			}
			result.put(key, CoefficientAssembler.assemble(ledger, reference));
		});
		return result;
	}
}
