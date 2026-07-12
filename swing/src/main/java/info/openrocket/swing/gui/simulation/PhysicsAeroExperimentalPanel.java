package info.openrocket.swing.gui.simulation;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import net.miginfocom.swing.MigLayout;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.*;
import info.openrocket.core.aerodynamics.physicsaero.integration.*;
import info.openrocket.core.document.Simulation;

/** Experimental Phase-6 table controls. Physics remains entirely in the core service. */
public final class PhysicsAeroExperimentalPanel extends JPanel {
	private final Simulation simulation;private final PhysicsAeroFeatureController controller=new PhysicsAeroFeatureController();private final AtomicBoolean cancelled=new AtomicBoolean();
	private final JCheckBox enabled=new JCheckBox("Enable Physics-Based Aerodynamics (Experimental)");private final JButton build=new JButton("Build Aerodynamic Table"),cancel=new JButton("Cancel"),resume=new JButton("Resume Checkpoint"),export=new JButton("Export Report");
	private final JProgressBar progress=new JProgressBar(0,100);private final JLabel validation=new JLabel(),stale=new JLabel("Table: not built"),hash=new JLabel("Table hash: —");private final JTextArea diagnostics=new JTextArea(12,80);private Path manifest;
	public PhysicsAeroExperimentalPanel(Simulation simulation){super(new BorderLayout());this.simulation=simulation;JPanel form=new JPanel(new MigLayout("fillx,wrap 2","[][grow,fill]",""));form.setBorder(BorderFactory.createTitledBorder(PhysicsAeroFeatureController.DISPLAY_NAME));
		var gate=new PhysicsAeroValidationGate().evaluate();validation.setText("Validation status: "+gate.status()+" — "+String.join(", ",gate.checks()));enabled.setEnabled(gate.status()==PhysicsAeroValidationGate.Status.PASS);
		form.add(enabled,"span 2");form.add(new JLabel("Validation:"));form.add(validation);form.add(new JLabel("Table status:"));form.add(stale);form.add(new JLabel("Table hash:"));form.add(hash);form.add(new JLabel("Fallback policy:"));form.add(new JComboBox<>(new String[]{"Fail invalid cell (recommended)","Named low-confidence correlation only"}));form.add(progress,"span 2,growx");
		JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));buttons.add(build);buttons.add(cancel);buttons.add(resume);buttons.add(export);form.add(buttons,"span 2");add(form,BorderLayout.NORTH);diagnostics.setEditable(false);diagnostics.setLineWrap(true);diagnostics.setWrapStyleWord(true);add(new JScrollPane(diagnostics),BorderLayout.CENTER);
		cancel.setEnabled(false);export.setEnabled(false);enabled.addActionListener(e->{try{controller.setEnabled(enabled.isSelected());append("Feature "+(enabled.isSelected()?"enabled":"disabled")+". Missing or stale tables never fall back silently.");}catch(RuntimeException ex){enabled.setSelected(false);append(ex.getMessage());}});build.addActionListener(e->startBuild(false));resume.addActionListener(e->startBuild(true));cancel.addActionListener(e->{cancelled.set(true);append("Cancellation requested; the last valid table will remain unchanged.");});export.addActionListener(e->exportManifest());}
	private void startBuild(boolean resumeRequested){if(!enabled.isSelected()){append("Enable the experimental feature first.");return;}cancelled.set(false);build.setEnabled(false);resume.setEnabled(false);cancel.setEnabled(true);progress.setValue(0);append(resumeRequested?"Resuming deterministic build from available output/checkpoint state.":"Starting full-regime table build.");
		new SwingWorker<PhysicsAeroTableService.Result,String>(){
			@Override protected PhysicsAeroTableService.Result doInBackground()throws Exception{String settings="phase6-default-adiabatic-smooth-unpowered";AeroGeometry geometry=new GeometryExtractor().extract(simulation.getActiveConfiguration().getRocket(),0,"ADIABATIC",settings);Path root=Path.of(System.getProperty("user.home"),".openrocket","physics-aero");Files.createDirectories(root);Path table=root.resolve(geometry.geometryHash()+".aero"),report=root.resolve(geometry.geometryHash()+".json");PerfectGasAir air=new PerfectGasAir();double p=101325,t=288.15;AtmosphereState atmosphere=new AtmosphereState(p,t,p/(air.gasConstant()*t),air.viscosity(t));double[]mach={0,.25,.3,.35,.7,.85,.9,.95,1,1.05,1.1,1.2,1.5,2,3,4,5,6,7};double[]alpha={-14,-10,-5,0,5,10,14};for(int i=0;i<alpha.length;i++)alpha[i]=Math.toRadians(alpha[i]);double[]beta={Math.toRadians(-5),0,Math.toRadians(5)};var request=new PhysicsAeroTableService.Request(geometry,mach,alpha,beta,atmosphere,air,settings,table,report);return new PhysicsAeroTableService().build(request,cancelled,v->SwingUtilities.invokeLater(()->progress.setValue(v)));}
			@Override protected void done(){try{var result=get();controller.attachTable(result.tableFile(),result.table().metadata().geometryHash(),result.table().metadata().settingsHash());manifest=result.manifestFile();stale.setText("Current — "+result.table().cells().size()+" cells");hash.setText(result.tableHash());export.setEnabled(true);append("Built table: "+result.tableFile());append("Validation: "+result.validation().status());}catch(Exception ex){stale.setText(cancelled.get()?"Cancelled; previous table preserved":"Build failed");append("Build failed: "+rootCause(ex).getMessage());}finally{build.setEnabled(true);resume.setEnabled(true);cancel.setEnabled(false);}}
		}.execute();}
	private void exportManifest(){if(manifest==null)return;JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new java.io.File("physics-aero-validation.json"));if(chooser.showSaveDialog(this)==JFileChooser.APPROVE_OPTION)try{Files.copy(manifest,chooser.getSelectedFile().toPath(),StandardCopyOption.REPLACE_EXISTING);append("Exported report: "+chooser.getSelectedFile());}catch(IOException ex){append("Export failed: "+ex.getMessage());}}
	private void append(String text){diagnostics.append(text+System.lineSeparator());diagnostics.setCaretPosition(diagnostics.getDocument().getLength());}
	private static Throwable rootCause(Throwable t){while(t.getCause()!=null)t=t.getCause();return t;}
}
