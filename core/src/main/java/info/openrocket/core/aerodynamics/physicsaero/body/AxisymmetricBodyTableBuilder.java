package info.openrocket.core.aerodynamics.physicsaero.body;

import java.util.*;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.*;
import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.force.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ThermodynamicModel;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.table.*;

public final class AxisymmetricBodyTableBuilder {
	public AerodynamicTable buildAdaptive(AeroGeometry geometry, double[] initialMachAxis, AtmosphereState atmosphere,
			ThermodynamicModel model, TableMetadata metadata, double holdoutTolerance, int maximumPasses) {
		double[] refined = new AdaptiveMachRefiner().refine(initialMachAxis, mach -> {
			FlowCondition flow = FlowCondition.fromAngles(mach, 0, 0, atmosphere, model, false, geometry.geometryHash());
			AxisymmetricBodyResult result = new AxisymmetricBodySolver().evaluate(geometry, flow);
			String region = result.edgeStateHistory().states().stream().map(SurfaceState::methodId).distinct().sorted()
					.reduce((a, b) -> a + "|" + b).orElse("NONE");
			return new AdaptiveMachRefiner.Sample(mach, result.coefficients().ca(), region);
		}, holdoutTolerance, maximumPasses);
		return build(geometry, refined, atmosphere, model, metadata);
	}
	public AerodynamicTable build(AeroGeometry geometry, double[] machAxis, AtmosphereState atmosphere,
			ThermodynamicModel model, TableMetadata metadata) {
		TableAxes axes = new TableAxes(machAxis, new double[] {0}, new double[] {0}); List<TableCell> cells = new ArrayList<>();
		for (double mach : machAxis) {
			FlowCondition flow = FlowCondition.fromAngles(mach, 0, 0, atmosphere, model, false, geometry.geometryHash());
			AxisymmetricBodyResult result = new AxisymmetricBodySolver().evaluate(geometry, flow);
			ReferenceState reference = new ReferenceState(flow.dynamicPressurePa(), geometry.references().referenceAreaM2(),
					geometry.references().referenceLengthM(), geometry.references().momentOriginM());
			Map<String, AerodynamicCoefficients> componentTotals = componentTotals(result.contributions(), reference);
			Map<String, AerodynamicCoefficients> ownerTotals = ownerTotals(result.contributions(), reference);
			List<String> methods = result.contributions().stream().map(c -> c.methodId().value()).distinct().sorted().toList();
			Set<DiagnosticFlag> flags = new HashSet<>(); flags.add(DiagnosticFlag.DIRECT_GENERATION);
			if (result.edgeStateHistory().events().stream().anyMatch(e -> e instanceof ShockEvent)) flags.add(DiagnosticFlag.SHOCK);
			if (result.edgeStateHistory().states().stream().anyMatch(s -> s.methodId().contains("FALLBACK"))) flags.add(DiagnosticFlag.FALLBACK_USED);
			List<String> validity = result.diagnostics().values().stream().filter(v -> v.contains("RISK") || v.contains("VALID")).sorted().toList();
			cells.add(new TableCell(result.coefficients(), componentTotals, ownerTotals, methods,
					new double[] {0.8, 1, 1, 1, 1, 1}, new double[] {0.1, 0, 0, 0, 0, 0}, validity,
					reference, new CellDiagnostics(flags, flags.contains(DiagnosticFlag.FALLBACK_USED),
							flags.contains(DiagnosticFlag.FALLBACK_USED) ? ModifiedNewtonianMethod.ID : null,
							flags.contains(DiagnosticFlag.FALLBACK_USED) ? "DETACHED_OR_BLUNT" : null, List.of()), true));
		}
		return new AerodynamicTable(axes, cells, metadata);
	}
	private static Map<String, AerodynamicCoefficients> componentTotals(List<ForceContribution> contributions, ReferenceState reference) {
		Map<String, List<ForceContribution>> groups = new TreeMap<>(); for (ForceContribution c : contributions) groups.computeIfAbsent(c.componentId(), key -> new ArrayList<>()).add(c);
		Map<String, AerodynamicCoefficients> totals = new TreeMap<>(); groups.forEach((id, values) -> totals.put(id, assemble(values, reference))); return totals;
	}
	private static Map<String, AerodynamicCoefficients> ownerTotals(List<ForceContribution> contributions, ReferenceState reference) {
		Map<String, List<ForceContribution>> groups = new TreeMap<>(); for (ForceContribution c : contributions) groups.computeIfAbsent(c.owner().term().name(), key -> new ArrayList<>()).add(c);
		Map<String, AerodynamicCoefficients> totals = new TreeMap<>(); groups.forEach((id, values) -> totals.put(id, assemble(values, reference))); return totals;
	}
	private static AerodynamicCoefficients assemble(List<ForceContribution> contributions, ReferenceState reference) {
		ContributionLedger ledger = new ContributionLedger(); contributions.stream().sorted().forEach(ledger::add); return CoefficientAssembler.assemble(ledger, reference);
	}
	private static final class ModifiedNewtonianMethod { static final String ID = "MODIFIED_NEWTONIAN_FALLBACK_V1"; }
}
