package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import info.openrocket.core.aerodynamics.physicsaero.api.*;
import info.openrocket.core.aerodynamics.physicsaero.force.*;
import info.openrocket.core.util.Coordinate;

class ForceOwnershipTest {
	private static ForceContribution contribution(MethodId id, OwnershipMode mode, MethodId primary, Coordinate force) {
		return new ForceContribution("body", new PhysicalOwner(PhysicalTerm.BODY_PRESSURE, mode, "all", primary), id,
				force, new Coordinate(), new Coordinate(1, 0, 0), "all", List.of(), 1, 0, null);
	}
	@Test void duplicateAuthoritativeOwnersFailFast() {
		ContributionLedger ledger = new ContributionLedger(); ledger.add(contribution(new MethodId("a"), OwnershipMode.REPLACES, null, new Coordinate(10, 0, 0)));
		assertThrows(IllegalStateException.class, () -> ledger.add(contribution(new MethodId("b"), OwnershipMode.REPLACES, null, new Coordinate(10, 0, 0))));
	}
	@Test void residualRequiresNamedPrimaryAndDiagnosticDoesNotChangeTotals() {
		ContributionLedger missing = new ContributionLedger(); assertThrows(IllegalStateException.class,
				() -> missing.add(contribution(new MethodId("r"), OwnershipMode.RESIDUAL, new MethodId("p"), new Coordinate(-1, 0, 0))));
		ContributionLedger ledger = new ContributionLedger(); MethodId primary = new MethodId("p");
		ledger.add(contribution(primary, OwnershipMode.REPLACES, null, new Coordinate(10, 0, 0)));
		ledger.add(contribution(new MethodId("d"), OwnershipMode.DIAGNOSTIC_ONLY, null, new Coordinate(-1000, 0, 0)));
		AerodynamicCoefficients c = CoefficientAssembler.assemble(ledger, new ReferenceState(100, 1, 1, new Coordinate()));
		assertEquals(0.1, c.ca(), 1e-15);
	}
	@Test void momentTranslationAndCpUndefinedAtZeroNormalForce() {
		ContributionLedger ledger = new ContributionLedger(); ledger.add(contribution(new MethodId("p"), OwnershipMode.REPLACES, null, new Coordinate(0, 0, 10)));
		ReferenceState reference = new ReferenceState(100, 1, 2, new Coordinate()); AerodynamicCoefficients c = CoefficientAssembler.assemble(ledger, reference);
		assertEquals(-0.05, c.cm(), 1e-15); assertTrue(CenterOfPressureDiagnostic.derive(new AerodynamicCoefficients(0, 0, 0, 0, 0, 0), reference, 1e-9).pitchXM().isEmpty());
	}
}
