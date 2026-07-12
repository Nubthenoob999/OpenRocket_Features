package info.openrocket.core.aerodynamics.physicsaero.api;

import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.force.ReferenceState;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;

public record CorrelationInput(AeroComponent component, FlowCondition flow, ReferenceState reference) {}
