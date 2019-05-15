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
 *     * Neither the name of the Universität Karlsruhe (TH) / KIT nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY UNIVERSITÄT KARLSRUHE (TH) / KIT AND CONTRIBUTORS 
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

import java.util.ArrayList;

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
	
	private static final String PAUSE_EVENT_PROCESSORS_COMMAND = "pause";
	private static final String UNPAUSE_EVENT_PROCESSORS_COMMAND = "unpause";
	private static final String LIST_EVENT_PROCESSORS_COMMAND = "list";
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent#getActions()
	 */
	public ComponentAction[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentAction ca;
		
		//	pause all exporters
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
					if (GoldenGateAEP.setExpPause(true))
						this.reportResult("Event processors set to pause.");
					else this.reportResult("Event processors already pausing.");
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	un-pause all exporters
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
					if (GoldenGateAEP.setExpPause(false))
						this.reportResult("Event processors un-paused.");
					else this.reportResult("Event processors not pausing.");
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	un-pause all exporters
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
		
		return ((ComponentAction[]) cal.toArray(new ComponentAction[cal.size()]));
	}
}