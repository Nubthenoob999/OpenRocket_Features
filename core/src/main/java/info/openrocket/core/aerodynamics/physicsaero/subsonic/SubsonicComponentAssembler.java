package info.openrocket.core.aerodynamics.physicsaero.subsonic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricEdgeStateHistory;
import info.openrocket.core.aerodynamics.physicsaero.body.SeparatedBoattailPressureDragModel;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.EngineeringSkinFrictionCorrelation;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinLeadingEdgePressureDragModel;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinTrailingEdgeBaseDragModel;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.geometry.*;

public final class SubsonicComponentAssembler {
	public record Result(AerodynamicCoefficients coefficients,
			AxisymmetricEdgeStateHistory bodyEdgeStates, List<String> methods,
			List<String> diagnostics, double confidence,
			Map<String, AerodynamicCoefficients> componentTotals,
			Map<String, AerodynamicCoefficients> ownerTotals) {
		public Result {
			methods = List.copyOf(methods);
			diagnostics = List.copyOf(diagnostics);
			componentTotals = Map.copyOf(componentTotals);
			ownerTotals = Map.copyOf(ownerTotals);
		}
	}

	public Result evaluate(AeroGeometry geometry,FlowCondition flow){
		if(flow.mach()>=.95)throw new IllegalArgumentException("SUBSONIC_COMPONENT_RANGE");double ref=geometry.references().referenceAreaM2(),length=geometry.references().referenceLengthM();
		double alphaT=Math.atan(Math.hypot(Math.tan(flow.alphaRad()),Math.tan(flow.betaRad()))),planform=geometry.components().stream().filter(c->c.axisymmetricProfile()!=null).mapToDouble(AeroComponent::projectedAreaM2).sum();
		double bodyMag=new JorgensenBodyForceModel().normalCoefficient(alphaT,geometry.references().exposedBaseAreaM2(),planform,ref),bodySlope=alphaT>1e-12?bodyMag/alphaT:2*geometry.references().exposedBaseAreaM2()/ref;
		double finSlope=0;for(AeroComponent c:geometry.components())if(c.finGeometry()!=null){FinGeometry f=c.finGeometry();double ar=f.spanM()*f.spanM()/f.planformAreaM2();finSlope+=new SubsonicDatcomFinModel().liftSlopePerRad(flow.mach(),ar,0,1)*f.planformAreaM2()/ref;}
		double totalSlope=bodySlope+finSlope,cn=totalSlope*flow.alphaRad(),cy=totalSlope*flow.betaRad();double re=Math.max(1,flow.atmosphere().densityKgM3()*flow.velocityBody().length()*geometry.references().maximumBodyDiameterM()/flow.atmosphere().dynamicViscosityPaS());
		double cpb=new SubsonicBaseDragModel().basePressureCoefficient(flow.mach(),re);
		double bodyBaseCd=-cpb*geometry.references().exposedBaseAreaM2()/ref;
		SeparatedBoattailPressureDragModel boattailModel =
				new SeparatedBoattailPressureDragModel();
		double boattailCd=boattailModel.dragCoefficient(geometry,-cpb);
		EngineeringSkinFrictionCorrelation.Result friction=new EngineeringSkinFrictionCorrelation().evaluate(geometry,flow);
		FinTrailingEdgeBaseDragModel finBaseModel=new FinTrailingEdgeBaseDragModel();
		double finBaseCd=geometry.components().stream().filter(c->c.finGeometry()!=null)
				.mapToDouble(c->finBaseModel.dragCoefficient(c,-cpb,ref)).sum();
		FinLeadingEdgePressureDragModel finLeadingEdgeModel=new FinLeadingEdgePressureDragModel();
		double finLeadingEdgeCd=geometry.components().stream().filter(c->c.finGeometry()!=null)
				.mapToDouble(c->finLeadingEdgeModel.dragCoefficient(c,flow.mach(),ref)).sum();
		double ca=bodyBaseCd+boattailCd+friction.totalCd()+finBaseCd+finLeadingEdgeCd;double xac=.6*geometry.references().vehicleLengthM();double arm=(xac-geometry.references().momentOriginM().x)/length;
		List<String> diagnostics=new ArrayList<>();AxisymmetricEdgeStateHistory edge;
		try{edge=new SubsonicBodyEdgeStateModel().evaluate(geometry,flow);}
		catch(IllegalArgumentException ex){
			// Edge states are diagnostic output in the subsonic assembler; the component correlations
			// above do not consume them.  A source-distribution pressure validity failure therefore must
			// not discard otherwise valid coefficients, but it must remain visible to callers.
			edge=new AxisymmetricEdgeStateHistory(List.of(),List.of());
			diagnostics.add("SUBSONIC_EDGE_STATE_INVALID:"+ex.getMessage());
		}
		if(alphaT>Math.toRadians(12))diagnostics.add("NONLINEAR_CROSSFLOW_NEAR_LIMIT");
		List<String> methods=new ArrayList<>(List.of("SUBSONIC_SOURCE_DISTRIBUTION_V1","JORGENSEN_BODY_FORCE_V1","SUBSONIC_DATCOM_FIN_V1",SubsonicBaseDragModel.METHOD_ID,EngineeringSkinFrictionCorrelation.METHOD_ID));
		if(finBaseCd>0)methods.add(FinTrailingEdgeBaseDragModel.METHOD_ID);
		if(finLeadingEdgeCd>0)methods.add(FinLeadingEdgePressureDragModel.METHOD_ID);
		if(boattailCd>0)methods.add(SeparatedBoattailPressureDragModel.METHOD_ID);
		AerodynamicCoefficients incidenceLoads =
				new AerodynamicCoefficients(0,cn,cy,0,-cn*arm,cy*arm);
		Map<String,AerodynamicCoefficients> components=Map.of(
				"axisymmetric-body",axial(bodyBaseCd+boattailCd+friction.bodyCd()),
				"fins",axial(finBaseCd+finLeadingEdgeCd+friction.finCd()),
				"body-fin-interference",axial(0),
				"vehicle-incidence",incidenceLoads);
		Map<String,AerodynamicCoefficients> owners=Map.of(
				"BODY_BASE_PRESSURE_DRAG",axial(bodyBaseCd),
				"FIN_BASE_PRESSURE_DRAG",axial(finBaseCd),
				"FIN_LEADING_EDGE_PRESSURE_DRAG",axial(finLeadingEdgeCd),
				"BODY_SKIN_FRICTION",axial(friction.bodyCd()),
				"FIN_SKIN_FRICTION",axial(friction.finCd()),
				"BOATTAIL_PRESSURE_DRAG",axial(boattailCd),
				"SUBSONIC_INCIDENCE_LOADS",incidenceLoads);
		return new Result(new AerodynamicCoefficients(ca,cn,cy,0,-cn*arm,cy*arm),
				edge,methods,diagnostics,.75,components,owners);
	}

	private static AerodynamicCoefficients axial(double coefficient){
		return new AerodynamicCoefficients(coefficient,0,0,0,0,0);
	}
}
