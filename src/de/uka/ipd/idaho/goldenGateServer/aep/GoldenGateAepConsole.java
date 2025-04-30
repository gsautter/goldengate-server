/*
 * Copyright (c) 2006-, IPD Boehm, Universitaet Karlsruhe (TH) / KIT, by Guido Sautter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Universitaet Karlsruhe (TH) / KIT nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY UNIVERSITAET KARLSRUHE (TH) / KIT AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.uka.ipd.idaho.goldenGateServer.aep;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent;

/**
 * Central controller component for GoldenGATE AEP sub classes.
 * 
 * @author sautter
 */
public class GoldenGateAepConsole extends AbstractGoldenGateServerComponent {
	
	/**
	 * Constructor passing 'AEP' as the letter code to the super constructor.
	 * @param letterCode
	 */
	public GoldenGateAepConsole() {
		super("AEP");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#initComponent()
	 */
	protected void initComponent() {
		
		//	load list of passive starting letter codes
		File spFile = new File(this.dataPath, "startPassive.cnfg");
		if (spFile.exists()) try {
			BufferedReader spBr = new BufferedReader(new InputStreamReader(new FileInputStream(spFile), "UTF-8"));
			for (String spRow; (spRow = spBr.readLine()) != null;) {
				spRow = spRow.trim();
				if (spRow.length() == 0)
					continue;
				if (spRow.startsWith("//"))
					continue;
				GoldenGateAEP.setStartPassive(spRow);
			}
			spBr.close();
		}
		catch (IOException ioe) {
			System.out.println("GoldenGateAEP: failed to load list of instance letter codes to start passive: " + ioe.getMessage());
			ioe.printStackTrace(System.out);
		}
		
		//	load maximum list of flushing instances
		String maxFlushingInstances = this.configuration.getSetting("maxFlushingInstances");
		if (maxFlushingInstances != null) try {
			GoldenGateAEP.setMaximumFlushingEventHandlers(Integer.parseInt(maxFlushingInstances));
		}
		catch (RuntimeException re) {
			System.out.println("GoldenGateAEP: invalid number of maximum flushing instances '" + maxFlushingInstances + "': " + re.getMessage());
			re.printStackTrace(System.out);
		}
		
		//	load maximum number of flushing instances per input source
		Settings maxFlushingFromSet = this.configuration.getSubset("maxFlushingFrom");
		String[] inputSources = maxFlushingFromSet.getKeys();
		for (int s = 0; s < inputSources.length; s++) {
			String maxFlushingFrom = maxFlushingFromSet.getSetting(inputSources[s]);
			try {
				GoldenGateAEP.setMaximumEventHandlersFlushingFrom(inputSources[s], Integer.parseInt(maxFlushingFrom));
			}
			catch (RuntimeException re) {
				System.out.println("GoldenGateAEP: invalid number of maximum instances flushing from '" + inputSources[s] + "' '" + maxFlushingFrom + "': " + re.getMessage());
				re.printStackTrace(System.out);
			}
		}
		Settings maxFlushingToSet = this.configuration.getSubset("maxFlushingTo");
		String[] outputDestinations = maxFlushingToSet.getKeys();
		for (int d = 0; d < outputDestinations.length; d++) {
			String maxFlushingTo = maxFlushingToSet.getSetting(outputDestinations[d]);
			try {
				GoldenGateAEP.setMaximumEventHandlersFlushingTo(outputDestinations[d], Integer.parseInt(maxFlushingTo));
			}
			catch (RuntimeException re) {
				System.out.println("GoldenGateAEP: invalid number of maximum instances flushing to '" + outputDestinations[d] + "' '" + maxFlushingTo + "': " + re.getMessage());
				re.printStackTrace(System.out);
			}
		}
	}
	
	private static final String PAUSE_EVENT_PROCESSORS_COMMAND = "pause";
	private static final String UNPAUSE_EVENT_PROCESSORS_COMMAND = "unpause";
	private static final String LIST_EVENT_PROCESSORS_COMMAND = "list";
	private static final String CHECK_EVENT_PROCESSORS_ALIVE_COMMAND = "checkAlive";
	private static final String LIST_INPUT_SOURCES_COMMAND = "sources";
	private static final String LIST_OUTPUT_DESTINATIONS_COMMAND = "dests";
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent#getActions()
	 */
	public ComponentAction[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentAction ca;
		
		//	pause all event processors
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return PAUSE_EVENT_PROCESSORS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						PAUSE_EVENT_PROCESSORS_COMMAND,
						"Pause all installed event processors."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					if (GoldenGateAEP.setAepPause(true))
						this.reportResult("Event processors set to pause.");
					else this.reportResult("Event processors already pausing.");
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	un-pause all event processors
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return UNPAUSE_EVENT_PROCESSORS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						UNPAUSE_EVENT_PROCESSORS_COMMAND,
						"Un-pause all installed event processors."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					if (GoldenGateAEP.setAepPause(false))
						this.reportResult("Event processors un-paused.");
					else this.reportResult("Event processors not pausing.");
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	list all event processors
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return LIST_EVENT_PROCESSORS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						LIST_EVENT_PROCESSORS_COMMAND,
						"List all installed event processors."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					this.reportResult("These are the event processors currently installed:");
					GoldenGateAEP.listInstances(" - ", this);
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	check if all event processors are alive
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return CHECK_EVENT_PROCESSORS_ALIVE_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						CHECK_EVENT_PROCESSORS_ALIVE_COMMAND,
						"Check if all installed event processors are alive, and start new worker thread if not."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					this.reportResult("Checking event processors:");
					GoldenGateAEP.checkInstances(" - ", this);
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	list input sources of all event processors
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return LIST_INPUT_SOURCES_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						LIST_INPUT_SOURCES_COMMAND + " <mode>",
						"List the input sources of all installed event processors:",
						"- <mode>: set to '-l' to list the individual event processors pulling data from each input source (optional)"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					this.reportResult("These are the input sources the of event processors currently installed:");
					GoldenGateAEP.listInputSources(" - ", this, false);
				}
				else if ((arguments.length == 1) && ("-l".equals(arguments[0]))) {
					this.reportResult("These are the input sources the of event processors currently installed:");
					GoldenGateAEP.listInputSources(" - ", this, true);
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify at most mode '-l' as the only argument.");
			}
		};
		cal.add(ca);
		
		//	list output destinations of all event processors
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return LIST_OUTPUT_DESTINATIONS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						LIST_OUTPUT_DESTINATIONS_COMMAND + " <mode>",
						"List the output destinations of all installed event processors:",
						"- <mode>: set to '-l' to list the individual event processors pushing data to each output destination (optional)"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					this.reportResult("These are the input destinations the of event processors currently installed:");
					GoldenGateAEP.listOutputDestinations(" - ", this, false);
				}
				else if ((arguments.length == 1) && ("-l".equals(arguments[0]))) {
					this.reportResult("These are the input destinations the of event processors currently installed:");
					GoldenGateAEP.listOutputDestinations(" - ", this, true);
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify at most mode '-l' as the only argument.");
			}
		};
		cal.add(ca);
		
		//	finally ...
		return ((ComponentAction[]) cal.toArray(new ComponentAction[cal.size()]));
	}
}