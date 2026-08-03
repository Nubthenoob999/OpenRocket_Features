package info.openrocket.core.aerodynamics.physicsaero.subsonic;

import java.util.ArrayList;
import java.util.Comparator;
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
	private static final int FIN_PLANFORM_QUADRATURE_STRIPS = 256;
	private static final double FIN_STALL_INCIDENCE_RAD = Math.toRadians(20);
	private static final String CENTER_OF_PRESSURE_METHOD_ID =
			"JORGENSEN_BODY_DATCOM_QUARTER_MAC_AC_V1";
	private static final String FIN_INSTALLATION_METHOD_ID =
			"BARROWMAN_FIN_ORIENTATION_BODY_INTERFERENCE_V1";

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
		double requestedAlphaT=Math.atan(Math.hypot(Math.tan(flow.alphaRad()),Math.tan(flow.betaRad()))),planform=geometry.components().stream().filter(c->c.axisymmetricProfile()!=null).mapToDouble(AeroComponent::projectedAreaM2).sum();
		double bodyIncidence=Math.min(requestedAlphaT,JorgensenBodyForceModel.MAX_INCIDENCE_RAD);
		double finIncidence=Math.min(requestedAlphaT,FIN_STALL_INCIDENCE_RAD);
		double potentialMagnitude=2*geometry.references().exposedBaseAreaM2()/ref*Math.sin(bodyIncidence)*Math.cos(bodyIncidence);
		double crossflowMagnitude=1.1*planform/ref*Math.sin(bodyIncidence)*Math.abs(Math.sin(bodyIncidence));
		double bodyMag=new JorgensenBodyForceModel().normalCoefficient(bodyIncidence,geometry.references().exposedBaseAreaM2(),planform,ref);
		double momentMagnitude=potentialMagnitude*potentialBodyCenterM(geometry)
				+crossflowMagnitude*crossflowBodyCenterM(geometry);
		double finSlope=0;
		for(AeroComponent c:geometry.components())if(c.finGeometry()!=null){
			FinGeometry f=c.finGeometry();
			FinPlanformMetrics metrics=finPlanformMetrics(c);
			// DATCOM's finite-wing aspect ratio uses the equivalent full wing made by
			// mirroring one rocket fin across its root: AR = (2s)^2/(2S) = 2s^2/S.
			double ar=2*f.spanM()*f.spanM()/f.planformAreaM2();
			double oneFinSlope=new SubsonicDatcomFinModel().liftSlopePerRad(
					flow.mach(),ar,metrics.halfChordSweepRad(),1)*f.planformAreaM2()/ref;
			// For evenly spaced fins, sum(sin^2(theta_i)) = N/2.  The classical
			// Barrowman body-fin interference multiplier is 1+r/(r+s).
			double orientationFactor=f.count()/2.0;
			double bodyInterference=1+c.rootRadiusM()/(c.rootRadiusM()+f.spanM());
			double installedSlope=oneFinSlope*orientationFactor*bodyInterference;
			finSlope+=installedSlope;
			momentMagnitude+=installedSlope*finIncidence*metrics.quarterMacXM();
		}
		double normalMagnitude=bodyMag+finSlope*finIncidence;
		double directionDenominator=requestedAlphaT>1e-12?requestedAlphaT:1;
		double cn=normalMagnitude*flow.alphaRad()/directionDenominator;
		double cy=normalMagnitude*flow.betaRad()/directionDenominator;
		EngineeringSkinFrictionCorrelation.Result friction=new EngineeringSkinFrictionCorrelation().evaluate(geometry,flow);
		double exposedBaseArea=geometry.references().exposedBaseAreaM2();
		double bodyBaseCd=exposedBaseArea>0
				?new SubsonicBaseDragModel().dragCoefficient(geometry,friction.totalCd()):0;
		double cpb=exposedBaseArea>0?-bodyBaseCd*ref/exposedBaseArea:0;
		SeparatedBoattailPressureDragModel boattailModel =
				new SeparatedBoattailPressureDragModel();
		double boattailCd=boattailModel.dragCoefficient(geometry,-cpb);
		FinTrailingEdgeBaseDragModel finBaseModel=new FinTrailingEdgeBaseDragModel();
		double finBaseCd=geometry.components().stream().filter(c->c.finGeometry()!=null)
				.mapToDouble(c->finBaseModel.dragCoefficient(c,-cpb,ref)).sum();
		FinLeadingEdgePressureDragModel finLeadingEdgeModel=new FinLeadingEdgePressureDragModel();
		double finLeadingEdgeCd=geometry.components().stream().filter(c->c.finGeometry()!=null)
				.mapToDouble(c->finLeadingEdgeModel.dragCoefficient(c,flow.mach(),ref)).sum();
		double ca=bodyBaseCd+boattailCd+friction.totalCd()+finBaseCd+finLeadingEdgeCd;
		double xac=normalMagnitude>1e-12?momentMagnitude/normalMagnitude:potentialBodyCenterM(geometry);
		double arm=(xac-geometry.references().momentOriginM().x)/length;
		List<String> diagnostics=new ArrayList<>();AxisymmetricEdgeStateHistory edge;
		try{edge=new SubsonicBodyEdgeStateModel().evaluate(geometry,flow);}
		catch(IllegalArgumentException ex){
			// Edge states are diagnostic output in the subsonic assembler; the component correlations
			// above do not consume them.  A source-distribution pressure validity failure therefore must
			// not discard otherwise valid coefficients, but it must remain visible to callers.
			edge=new AxisymmetricEdgeStateHistory(List.of(),List.of());
			diagnostics.add("SUBSONIC_EDGE_STATE_INVALID:"+ex.getMessage());
		}
		if(bodyIncidence>Math.toRadians(12))diagnostics.add("NONLINEAR_CROSSFLOW_NEAR_LIMIT");
		if(requestedAlphaT>FIN_STALL_INCIDENCE_RAD)diagnostics.add("FIN_NORMAL_FORCE_HELD_AT_20DEG_STALL");
		List<String> methods=new ArrayList<>(List.of("SUBSONIC_SOURCE_DISTRIBUTION_V1","JORGENSEN_GALEJS_VERY_HIGH_INCIDENCE_BODY_FORCE_V1","SUBSONIC_DATCOM_FIN_V1",CENTER_OF_PRESSURE_METHOD_ID,FIN_INSTALLATION_METHOD_ID,SubsonicBaseDragModel.METHOD_ID,EngineeringSkinFrictionCorrelation.METHOD_ID));
		if(friction.finInterferenceCd()>0)methods.add(EngineeringSkinFrictionCorrelation.FIN_INTERFERENCE_METHOD_ID);
		if(finBaseCd>0)methods.add(FinTrailingEdgeBaseDragModel.METHOD_ID);
		if(finLeadingEdgeCd>0)methods.add(FinLeadingEdgePressureDragModel.METHOD_ID);
		if(boattailCd>0)methods.add(SeparatedBoattailPressureDragModel.METHOD_ID);
		AerodynamicCoefficients incidenceLoads =
				new AerodynamicCoefficients(0,cn,cy,0,-cn*arm,cy*arm);
		Map<String,AerodynamicCoefficients> components=Map.of(
				"axisymmetric-body",axial(bodyBaseCd+boattailCd+friction.bodyCd()),
				"fins",axial(finBaseCd+finLeadingEdgeCd+friction.finCd()),
				"body-fin-interference",axial(friction.finInterferenceCd()),
				"vehicle-incidence",incidenceLoads);
		Map<String,AerodynamicCoefficients> owners=Map.of(
				"BODY_BASE_PRESSURE_DRAG",axial(bodyBaseCd),
				"FIN_BASE_PRESSURE_DRAG",axial(finBaseCd),
				"FIN_LEADING_EDGE_PRESSURE_DRAG",axial(finLeadingEdgeCd),
				"BODY_SKIN_FRICTION",axial(friction.bodyCd()),
				"FIN_SKIN_FRICTION",axial(friction.finCd()),
				"FIN_SKIN_FRICTION_INTERFERENCE",axial(friction.finInterferenceCd()),
				"BOATTAIL_PRESSURE_DRAG",axial(boattailCd),
				"SUBSONIC_INCIDENCE_LOADS",incidenceLoads);
		return new Result(new AerodynamicCoefficients(ca,cn,cy,0,-cn*arm,cy*arm),
				edge,methods,diagnostics,.75,components,owners);
	}

	private static AerodynamicCoefficients axial(double coefficient){
		return new AerodynamicCoefficients(coefficient,0,0,0,0,0);
	}

	/** Pressure-resultant location of the positive axisymmetric area changes. */
	private static double potentialBodyCenterM(AeroGeometry geometry){
		double weightedX=0,weight=0;
		for(AeroComponent component:geometry.components()){
			if(component.axisymmetricProfile()==null)continue;
			List<GeometryStation> stations=component.axisymmetricProfile().stations();
			for(int i=1;i<stations.size();i++){
				GeometryStation before=stations.get(i-1),after=stations.get(i);
				double areaChange=after.areaM2()-before.areaM2();
				if(areaChange<=0)continue;
				weightedX+=areaChange*.5*(before.xM()+after.xM());
				weight+=areaChange;
			}
		}
		return weight>0?weightedX/weight:.5*geometry.references().vehicleLengthM();
	}

	/** Centroid of the Jorgensen/Allen-Perkins crossflow side-area distribution. */
	private static double crossflowBodyCenterM(AeroGeometry geometry){
		double weightedX=0,weight=0;
		for(AeroComponent component:geometry.components()){
			if(component.axisymmetricProfile()==null)continue;
			List<GeometryStation> stations=component.axisymmetricProfile().stations();
			for(int i=1;i<stations.size();i++){
				GeometryStation a=stations.get(i-1),b=stations.get(i);
				double dx=b.xM()-a.xM(),segmentWeight=dx*(a.radiusM()+b.radiusM());
				if(segmentWeight<=0)continue;
				double centroid=a.xM()+dx*(a.radiusM()+2*b.radiusM())
						/(3*(a.radiusM()+b.radiusM()));
				weightedX+=segmentWeight*centroid;
				weight+=segmentWeight;
			}
		}
		return weight>0?weightedX/weight:.5*geometry.references().vehicleLengthM();
	}

	/**
	 * Area integrals of the local fin outline.  The subsonic aerodynamic center is
	 * the quarter point of the mean aerodynamic chord:
	 * x_ac = integral(c*x_le dy)/S + integral(c^2 dy)/(4S).
	 */
	private static FinPlanformMetrics finPlanformMetrics(AeroComponent component){
		FinGeometry fin=component.finGeometry();
		List<GeometryStation> outline=fin.outline();
		if(outline.size()<3)throw new IllegalArgumentException("FIN_OUTLINE_REQUIRED_FOR_SUBSONIC_AC:"+component.id());
		double dy=fin.spanM()/FIN_PLANFORM_QUADRATURE_STRIPS;
		double area=0,leadingMoment=0,chordSquaredMoment=0;
		double firstMid=Double.NaN,lastMid=Double.NaN,firstY=Double.NaN,lastY=Double.NaN;
		for(int i=0;i<FIN_PLANFORM_QUADRATURE_STRIPS;i++){
			double y=(i+.5)*dy;
			List<Double> intersections=new ArrayList<>();
			for(int edge=0;edge<outline.size();edge++){
				GeometryStation a=outline.get(edge),b=outline.get((edge+1)%outline.size());
				double ya=a.radiusM(),yb=b.radiusM();
				if((ya<=y&&y<yb)||(yb<=y&&y<ya))
					intersections.add(a.xM()+(y-ya)*(b.xM()-a.xM())/(yb-ya));
			}
			intersections.sort(Comparator.naturalOrder());
			if(intersections.size()<2)continue;
			double leading=intersections.get(0),trailing=intersections.get(intersections.size()-1);
			double chord=trailing-leading;
			if(chord<=0)continue;
			double stripArea=chord*dy;
			area+=stripArea;
			leadingMoment+=leading*stripArea;
			chordSquaredMoment+=chord*stripArea;
			double mid=.5*(leading+trailing);
			if(Double.isNaN(firstMid)){firstMid=mid;firstY=y;}
			lastMid=mid;lastY=y;
		}
		if(area<=0)throw new IllegalArgumentException("INVALID_FIN_OUTLINE_FOR_SUBSONIC_AC:"+component.id());
		double macLeading=leadingMoment/area,macLength=chordSquaredMoment/area;
		double sweep=lastY>firstY?Math.atan2(lastMid-firstMid,lastY-firstY):0;
		return new FinPlanformMetrics(component.axialStartM()+macLeading+.25*macLength,sweep);
	}

	private record FinPlanformMetrics(double quarterMacXM,double halfChordSweepRad){}
}
