package com.springboot.simul;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.epsilon.egl.EglTemplateFactory;
import org.eclipse.epsilon.egl.EglTemplateFactoryModuleAdapter;
import org.eclipse.epsilon.emc.emf.EmfModel;
import org.eclipse.epsilon.emc.uml.UmlModel;
import org.eclipse.epsilon.etl.EtlModule;
import org.eclipse.epsilon.evl.EvlModule;
import org.eclipse.epsilon.evl.execute.UnsatisfiedConstraint;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

import io.github.abelgomez.cpntools.Annot;
import io.github.abelgomez.cpntools.Cpnet;
import io.github.abelgomez.cpntools.CpntoolsFactory;
import io.github.abelgomez.cpntools.CpntoolsPackage;
import io.github.abelgomez.cpntools.io.serializer.CpnToolsBuilder;

@Service
@Validated
public class SimulationServiceService {

	public void handleInputProf(String filename, String artifact) {

		try {
			getFileFrombucketProf(filename, artifact);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	public String handleInput(String filename, String artifact) {

		try {
			getFileFrombucket(filename, artifact);

			String simulResult = "";

			switch (artifact) {
			case "state":
				simulResult = simulateState();
				break;
			case "activity":
				simulResult = simulateActivity();
				break;
			case "sequence":
				simulResult = simulateSequence();
				break;
			default:
				simulResult = "invalid artifact";
				break;
			}

			return simulResult;
		} catch (Exception e) {
			return e.toString();
		}
	}

	private String simulateSequence() {

		try {
			String res = "";

			String filename = "src/main/sequence/modelseq.uml";

			File fileEvl = new File("src/main/sequence/sequenceVal.evl");

			EvlModule module = new EvlModule();

			UmlModel model = new UmlModel();
			model.setModelFile(filename);
			model.load();

			module.getContext().getModelRepository().addModel(model);

			module.parse(fileEvl);

			module.execute();

			Collection<UnsatisfiedConstraint> unsatisfiedList = module.getContext().getUnsatisfiedConstraints();

			for (UnsatisfiedConstraint constraint : unsatisfiedList) {
				res += constraint.getMessage() + "\n";
			}
			if (res.equals("")) {
				res += "Validation Errors = 0\n";

				// create the ETL module with the ETL file
				EtlModule module2 = new EtlModule();
				File fileEtl = new File("src/main/sequence/sequenceToCPN.etl");
				module2.parse(fileEtl);

				// create and load the target model, a xmi version of a Colored Petri Net
				// (not sure if it should be cpnModel.model or cpnModel.xmi)
				EmfModel modelcpn = new EmfModel();
				modelcpn.setModelFile("src/main/sequence/cpnModel.model");
				modelcpn.setMetamodelFile("src/main/sequence/cpnmeta.ecore");
				modelcpn.setName("cpnmeta");
				modelcpn.setReadOnLoad(false);
				modelcpn.setStoredOnDisposal(true);
				modelcpn.load();

				// create and load the source model, a UML Sequence Diagram
				UmlModel modeluml = new UmlModel();
				modeluml.setModelFile(filename);
				modeluml.setName("uml");
				modeluml.load();

				// add the models to the ETL module and execute it
				module2.getContext().getModelRepository().addModel(modeluml);
				module2.getContext().getModelRepository().addModel(modelcpn);

				module2.execute();
				modelcpn.dispose();

				File testTransformation = new File("src/main/sequence/cpnModel.model");
				if (testTransformation.length() == 0) {
					return res + "\nTarget model was empty. Transformation failed.";
				} else {
					res += serializeCPN();
				}

			}
			return res;
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}

	}

	private String serializeCPN() {

		try {
			ResourceSet resourceSet = new ResourceSetImpl();
			
			URI u = URI.createFileURI("src/main/sequence/cpnModel.model");
			resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("model", new XMIResourceFactoryImpl());
			resourceSet.getPackageRegistry().put(CpntoolsPackage.eNS_URI, CpntoolsPackage.eINSTANCE);
			
			Resource resource = resourceSet.getResource(u, true);
			resource.load(Collections.emptyMap());
			Cpnet net = (Cpnet) resource.getContents().get(0);
			
			net.getBinder().getPages().stream().forEach(p -> {
				p.getArcs().stream().forEach(a -> {
					Annot an = CpntoolsFactory.eINSTANCE.createAnnot();
					an.setText("n");
					a.setAnnot(an);
				});
			});
			
			net.getBinder().getPages().stream().forEach(p -> p.layout());

			CpnToolsBuilder builder = new CpnToolsBuilder(net);
			builder.serialize(new FileOutputStream(new File("src/main/sequence/cpnModel.cpn")));
			
			//return simulateCPN();
			return storeCPN();
		}

		catch (Exception e) {
			e.printStackTrace(); return e.toString();
		}

	}

	/*private String simulateCPN() {
		try {
		
			final String fileName = "src\\main\\sequence\\cpnModel.cpn";
			final PetriNet petriNet = DOMParser.parse(new URL("file:" + fileName));
			
			final HighLevelSimulator simulator = HighLevelSimulator.getHighLevelSimulator();
			
			String res = "";

			try {
				simulator.setTarget((Notifier) petriNet);
				simulator.execute();

				for (int i = 0; i < 100; i++) {
					final Binding binding = simulator.executeAndGet();
					if (binding != null)
						res+=simulator.getStep() + ": " + binding;
					else
						break;
				}
			} finally {
				simulator.destroy();
			}
			
			if(res.equals("")){return "Simulation failed";}
			else {return res;}
		}
		catch (Exception e) {
			e.printStackTrace();
			return e.toString();
		}

	}*/

	private String simulateActivity() {

		try {

			String filename = "src/main/activity/modelact.uml";
			String res = "";

			EvlModule module = new EvlModule();
			UmlModel model = new UmlModel();

			model.setModelFile(filename);
			model.load();

			module.getContext().getModelRepository().addModel(model);
			File fileEvl = new File("src/main/activity/validateActivity.evl");
			module.parse(fileEvl);
			module.execute();
			Collection<UnsatisfiedConstraint> unsatisfiedList = module.getContext().getUnsatisfiedConstraints();

			for (UnsatisfiedConstraint constraint : unsatisfiedList) {
				res += constraint.getMessage() + "\n";
			}
			if (res.equals("")) {
				res += "Validation Errors = 0\n";

				EglTemplateFactoryModuleAdapter module2 = new EglTemplateFactoryModuleAdapter(new EglTemplateFactory());
				module2.getContext().getModelRepository().addModel(model);
				File fileEgl = new File("src/main/activity/activityToNuSMV.egl");
				module2.parse(fileEgl);
				String output = (String) module2.execute();

				PrintWriter writer = new PrintWriter("src/main/activity/code.smv");
				writer.print(output);
				writer.close();

				String path = "src/main/activity";

				File buildFile = new File(path, "build.xml");
				Project p = new Project();
				p.setUserProperty("ant.file", buildFile.getAbsolutePath());
				p.init();
				ProjectHelper helper = ProjectHelper.getProjectHelper();
				p.addReference("ant.projectHelper", helper);
				helper.parse(p, buildFile);
				p.executeTarget(p.getDefaultTarget());

				res += "\n[[CTL* symbols:\n\n";
				res += "G = must always happen\n";
				res += "F = must happen eventually\n";
				res += "X = must happen immediately next\n";
				res += "X X = must happen two turns next\n";
				res += "EF = must be reached in at least one case]]\n";

				String reportpath = path + "/checkreport.txt";
				FileReader fr = new FileReader(reportpath);
				BufferedReader br = new BufferedReader(fr);
				StringBuffer sb = new StringBuffer();
				String line;
				int counter = 0;
				while ((line = br.readLine()) != null) {
					if (line.endsWith("is false")) {
						sb.append(line);
						sb.append("\n");
						counter++;
					}
				}
				if (counter == 0) {
					res += "\nModel passed\n";
				} else {
					res += "\nModel failed\n";
				}
				fr.close();
				res += "\n";
				res += sb.toString();
			}
			return res;
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}

	private String simulateState() {
		try {

			String filename = "src/main/state/modelsta.uml";
			String res = "";

			String path = "src/main/state";

			EvlModule module = new EvlModule();
			UmlModel model = new UmlModel();

			model.setModelFile(filename);
			model.load();

			module.getContext().getModelRepository().addModel(model);
			File fileEvl = new File("src/main/state/statechartval.evl");
			module.parse(fileEvl);
			module.execute();
			Collection<UnsatisfiedConstraint> unsatisfiedList = module.getContext().getUnsatisfiedConstraints();

			for (UnsatisfiedConstraint constraint : unsatisfiedList) {
				res += constraint.getMessage() + "\n";
			}
			if (res.equals("")) {
				res += "Validation Errors = 0\n";

				EglTemplateFactoryModuleAdapter module2 = new EglTemplateFactoryModuleAdapter(new EglTemplateFactory());
				module2.getContext().getModelRepository().addModel(model);
				File fileEgl = new File("src/main/state/umltoumple.egl");
				module2.parse(fileEgl);
				String output = (String) module2.execute();

				PrintWriter writer = new PrintWriter("src/main/state/smant.ump");
				writer.print(output);
				writer.close();

				File buildFile = new File(path, "build.xml");
				Project p = new Project();
				p.setUserProperty("ant.file", buildFile.getAbsolutePath());
				p.init();
				ProjectHelper helper = ProjectHelper.getProjectHelper();
				p.addReference("ant.projectHelper", helper);
				helper.parse(p, buildFile);
				p.executeTarget(p.getDefaultTarget());

				String reportpath = path + "/trace.txt";
				FileReader fr = new FileReader(reportpath);
				BufferedReader br = new BufferedReader(fr);
				StringBuffer sb = new StringBuffer();
				String line;
				while ((line = br.readLine()) != null) {
					sb.append(line);
					sb.append("\n");
				}
				fr.close();
				res += "\n";
				res += sb.toString();
			}
			return res;

		} catch (Exception e) {
			return e.toString();
		}
	}

	private String storeCPN() {
		RandomString r = new RandomString();
		String key = r.nextString();
		
		final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.EU_WEST_2).build();
		String bucketName = "myawsbucketupload";
		
		File file = new File("src/main/sequence/cpnModel.cpn");
		
		try{
			s3.putObject(bucketName, key, file);
		}
		catch (Exception e) {
			return e.toString();
		}
		
		return key;
	}
	
	private void getFileFrombucket(String key, String artifact) throws IOException {

		String path = "";

		if (artifact.equals("state")) {
			path += "src/main/state/modelsta.uml";
		} else if (artifact.equals("activity")) {
			path += "src/main/activity/modelact.uml";
		} else if (artifact.equals("sequence")) {
			path += "src/main/sequence/modelseq.uml";
		}

		if (artifact.equals("activity") || artifact.equals("state") || artifact.equals("sequence")) {

			final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.EU_WEST_2).build();
			String bucketName = "myawsbucketupload";

			S3Object o = s3.getObject(bucketName, key);
			S3ObjectInputStream s3is = o.getObjectContent();
			FileOutputStream fos = new FileOutputStream(path);

			PrintWriter writer = new PrintWriter(path);
			writer.print("");
			writer.close();

			byte[] readBuf = new byte[1024];
			int readLen;
			while ((readLen = s3is.read(readBuf)) > 0) {
				fos.write(readBuf, 0, readLen);
			}
			s3is.close();
			fos.close();
		}
	}

	private void getFileFrombucketProf(String filename, String artifact) throws IOException {

		String path = "";

		if (artifact.equals("state")) {
			path += "src/main/state/Main.java";
		}

		if (artifact.equals("state")) {

			final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.EU_WEST_2).build();
			String bucketName = "myawsbucketupload";

			S3Object o = s3.getObject(bucketName, filename);

			S3ObjectInputStream s3is = o.getObjectContent();
			FileOutputStream fos = new FileOutputStream(path);

			PrintWriter writer = new PrintWriter(path);
			writer.print("");
			writer.close();

			byte[] readBuf = new byte[1024];
			int readLen;
			while ((readLen = s3is.read(readBuf)) > 0) {
				fos.write(readBuf, 0, readLen);
			}
			s3is.close();
			fos.close();
		}
	}

}
