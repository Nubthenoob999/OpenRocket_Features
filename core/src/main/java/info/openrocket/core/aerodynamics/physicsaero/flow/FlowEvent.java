package info.openrocket.core.aerodynamics.physicsaero.flow;

public sealed interface FlowEvent permits ShockEvent, ExpansionEvent {
	double xM();
	SurfaceState upstream();
	SurfaceState downstream();
	String methodId();
}
