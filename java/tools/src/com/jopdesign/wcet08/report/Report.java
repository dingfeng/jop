/*
  This file is part of JOP, the Java Optimized Processor
    see <http://www.jopdesign.com/>

  Copyright (C) 2008, Benedikt Huber (benedikt.huber@gmail.com)

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package com.jopdesign.wcet08.report;

import static com.jopdesign.wcet08.Config.sanitizeFileName;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.exception.ResourceNotFoundException;
import com.jopdesign.build.MethodInfo;
import com.jopdesign.wcet.WCETInstruction;
import com.jopdesign.wcet08.Config;
import com.jopdesign.wcet08.Project;
import com.jopdesign.wcet08.frontend.FlowGraph;
import com.jopdesign.wcet08.frontend.FlowGraph.FlowGraphEdge;
import com.jopdesign.wcet08.frontend.FlowGraph.FlowGraphNode;

/**
 * Analysis reports, using HTML framesets.
 * 
 * TODO: This is an ad-hoc implementation. Design a good report concept.
 * TODO: html resources should be bundled in this package
 * @author Benedikt Huber <benedikt.huber@gmail.com>
 *
 */
public class Report {
	static final Logger logger = Logger.getLogger(Report.class);
	
	/**
	 * Initialize the velocity engine
	 * @throws Exception
	 */
	public static void initVelocity() throws Exception  {
		Properties ps = new Properties();
		ps.put("resource.loader", "class");
		ps.put("class.resource.loader.description","velocity: wcet class resource loader"); 
		ps.put("class.resource.loader.class","org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
		Config conf = Config.instance();
		if(conf.getTemplatePath() != null) {
			ps.put("resource.loader", "file, class");
			ps.put("file.resource.loader.class","org.apache.velocity.runtime.resource.loader.FileResourceLoader");
			ps.put("file.resource.loader.path",Config.instance().getTemplatePath());
			ps.put("file.resource.loader.cache","true");
		} else {
			
		}
		Velocity.init(ps);
	}

	private Config config;	
	private Project project;
	private HashMap<MethodInfo,Vector<DetailedMethodReport>> detailedReports =
		new HashMap<MethodInfo, Vector<DetailedMethodReport>>();
	private Hashtable<String,Object> stats = new Hashtable<String, Object>();
	private ReportEntry rootReportEntry = ReportEntry.rootReportEntry("summary.html");
	private Hashtable<File,File> dotJobs = new Hashtable<File,File>();
	
	public Report(Project p) {
		this.project = p;
		this.config = Config.instance();
	}
	public void addStat(String key, Object val) { this.stats.put(key,val); }

	/**
	 * add a HTML page, with the given name, order number and link
	 * @param name the name of the page
	 * @param link the relative path to the page (e.g. <code>details/m1.html<code>)
	 */
	public void addPage(String name, String link)  {
		this.rootReportEntry.addPage(name,link);
	}
	
	public void generateFile(String templateName,File outFile, Map ctxMap) throws Exception {
		generateFile(templateName,outFile,new VelocityContext(ctxMap));
	}
	/**
	 * Write the reports to disk
	 * @throws Exception
	 */
	public void writeReport() throws Exception {
		this.addPage("logs/error.log",config.getErrorLogFile().getName());
		this.addPage("logs/info.log", config.getInfoLogFile().getName());
		generateBytecodeTable();
		generateIndex();
		generateSummary();
		generateTOC();
		generateDOT();
	}

	private void generateDOT() throws IOException {
		if(config.doInvokeDot()) {
			for(Entry<File,File> dotJob : this.dotJobs.entrySet()) {
				InvokeDot.invokeDot(dotJob.getKey(), dotJob.getValue());
			}
		} else {
			FileWriter fw = new FileWriter(config.getOutFile("Makefile"));
			fw.append("dot:\n");
			for(Entry<File,File> dotJob : this.dotJobs.entrySet()) {
				fw.append("\tdot -Tpng -o "+dotJob.getValue().getName()+" "+
											dotJob.getKey().getName()+"\n");
			}			
			fw.close();
		}
	}
	private void generateBytecodeTable() throws IOException {
		File file = config.getOutFile("Bytecode WCET Table.txt");
		FileWriter fw = new FileWriter(file);
		fw.append(WCETInstruction.toWCAString());
		fw.close();
		this.addPage("input/bytecodetable",file.getName());
	}

	private void generateIndex() throws Exception {
		generateFile("index.vm",config.getOutFile("index.html"), new VelocityContext());
	}

	private void generateTOC() throws Exception {
		VelocityContext context = new VelocityContext();
		context.put("tree", this.rootReportEntry);
		generateFile("toc.vm",config.getOutFile("toc.html"), context);
	}

	private void generateSummary() throws Exception {
		VelocityContext context = new VelocityContext();
		context.put( "classpath", config.getClassPath());
		context.put( "class", config.getRootClassName());
		context.put( "method", config.getRootMethodName());
		context.put( "errorlog", config.getErrorLogFile());
		context.put( "infolog", config.getInfoLogFile());
		context.put( "stats", stats);
		generateFile("summary.vm", config.getOutFile("summary.html"), context);
	}
	private void generateFile(String templateName, File outFile, VelocityContext ctx) 
			throws Exception 
	{
		Template template;
		try {
			template = Velocity.getTemplate(templateName);
		} catch(ResourceNotFoundException e) {
			template = Velocity.getTemplate("com/jopdesign/wcet08/report/"+templateName);
		}
		FileWriter fw = new FileWriter(outFile);
		template.merge( ctx, fw );
		fw.close();				
	}

	/**
	 * Dump the project's input (callgraph,cfgs)
	 * @throws IOException 
	 *
	 */
	public void generateInfoPages() throws IOException {
		this.addStat("#classes", project.getCallGraph().getClassInfos().size());
		this.addStat("#methods", project.getCallGraph().getImplementedMethods().size());
		generateInputOverview();
		this.addPage("details",null);
		for(MethodInfo m : project.getCallGraph().getImplementedMethods()) {
			logger.info("Generating report for method: "+m);
			FlowGraph flowGraph = project.getFlowGraph(m);
			Map<String,Object> stats = new TreeMap<String, Object>();
			stats.put("#nodes", flowGraph.getGraph().vertexSet().size() - 2 /* entry+exit */);
			stats.put("length-in-bytes", flowGraph.getNumberOfBytes());
			this.addDetailedReport(m, 
								   new DetailedMethodReport(project,m,"CFG",stats,null,null),
								   true);
			generateDetailedReport(m);
		}		
	}
	private void generateInputOverview() throws IOException {
		Hashtable<String,Object> ctx = new Hashtable<String,Object>();
		
		File cgdot = config.getOutFile("callgraph.dot");
		File cgimg = config.getOutFile("callgraph.png");
		FileWriter fw = new FileWriter(cgdot);
		project.getCallGraph().exportDOT(fw);
		fw.close();
		recordDot(cgdot,cgimg);
		ctx.put("callgraph", "callgraph.png");

		Vector<MethodReport> mrv = new Vector<MethodReport>();
		for(MethodInfo m : project.getCallGraph().getImplementedMethods()) { 
			mrv.add(new MethodReport(project,m,pageOf(m))); 
		}
		ctx.put("methods", mrv);

		
		try {
			this.generateFile("input_overview.vm", config.getOutFile("input_overview.html"),ctx);
		} catch (Exception e) {
			logger.error(e);
		}
		this.addPage("input", "input_overview.html");
	}
	
	void recordDot(File cgdot, File cgimg) {
		this.dotJobs .put(cgdot,cgimg);
	}

	private static String pageOf(MethodInfo i) { 
		return sanitizeFileName(i.getFQMethodName())+".html";
	}
	
	protected void addDetailedReport(MethodInfo m, DetailedMethodReport e, boolean prepend) {
		Vector<DetailedMethodReport> reports = this.detailedReports.get(m);
		if(reports == null) {
			reports = new Vector<DetailedMethodReport>();
			this.detailedReports.put(m,reports);
		}
		if(prepend) reports.insertElementAt(e, 0);
		else reports.add(e);
	}
	public void addDetailedReport(MethodInfo m, String key, Map<String, Object> stats, 
								 Map<FlowGraphNode, ?> nodeAnnots, 
								 Map<FlowGraphEdge, ?> edgeAnnots) {
		DetailedMethodReport re = new DetailedMethodReport(project,m,key,stats,nodeAnnots,edgeAnnots);
		this.addDetailedReport(m, re,false);
	}
	private void generateDetailedReport(MethodInfo method) {
		String page = pageOf(method);
		Hashtable<String,Object> ctx = new Hashtable<String,Object>();
		ctx.put("m", method);
		ctx.put("reports", this.detailedReports.get(method));
		for(DetailedMethodReport m: this.detailedReports.get(method)) {
			m.getGraph();
		}
		try {
			this.generateFile("cfg.vm", config.getOutFile(page), ctx);
		} catch (Exception e) {
			logger.error(e);
		}
		this.addPage("details/"+sanitizePageKey(method.getFQMethodName()),page);		
	}
	/* page keys may not contain a slash - replace it by backslash */
	private String sanitizePageKey(String s) {
		return s.replace('/', '\\');
	}
}