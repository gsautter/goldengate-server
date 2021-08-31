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
package de.uka.ipd.idaho.goldenGateServer.ada;

import java.util.ArrayList;

import de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent;
import de.uka.ipd.idaho.goldenGateServer.util.AsynchronousDataActionHandler;
import de.uka.ipd.idaho.goldenGateServer.util.AsynchronousDataActionHandler.ConsoleInterface;

/**
 * Central controller component for Asynchronous Data Action Handlers.
 * 
 * @author sautter
 */
public class GoldenGateAdaConsole extends AbstractGoldenGateServerComponent {
	private ConsoleInterface consoleInterface;
	
	/**
	 * Constructor passing 'AEP' as the letter code to the super constructor.
	 * @param letterCode
	 */
	public GoldenGateAdaConsole() {
		super("ADA");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#initComponent()
	 */
	protected void initComponent() {
		this.consoleInterface = AsynchronousDataActionHandler.getConsoleInterface();
	}
	
	private static final String PAUSE_ACTION_HANDLERS_COMMAND = "pause";
	private static final String UNPAUSE_ACTION_HANDLERS_COMMAND = "unpause";
	private static final String LIST_ACTION_HANDLERS_COMMAND = "list";
	private static final String CHECK_ACTION_HANDLERS_ALIVE_COMMAND = "checkAlive";
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent#getActions()
	 */
	public ComponentAction[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentAction ca;
		
		//	pause all action handlers
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return PAUSE_ACTION_HANDLERS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						PAUSE_ACTION_HANDLERS_COMMAND,
						"Pause all installed data action handlers."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					if (consoleInterface.setPause(true))
						this.reportResult("Data action handlers set to pause.");
					else this.reportResult("Data action handlers already pausing.");
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	un-pause all action handlers
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return UNPAUSE_ACTION_HANDLERS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						UNPAUSE_ACTION_HANDLERS_COMMAND,
						"Un-pause all installed data action handlers."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					if (consoleInterface.setPause(false))
						this.reportResult("Data action handlers un-paused.");
					else this.reportResult("Data action handlers not pausing.");
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	list all action handlers
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return LIST_ACTION_HANDLERS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						LIST_ACTION_HANDLERS_COMMAND,
						"List all installed data action handlers."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					this.reportResult("These are the data action handlers currently installed:");
					consoleInterface.listInstances(" - ", this);
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	check if all action handlers are alive
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return CHECK_ACTION_HANDLERS_ALIVE_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						CHECK_ACTION_HANDLERS_ALIVE_COMMAND,
						"Check if all installed data action handlers are alive, and start new worker thread if not."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					this.reportResult("Checking data action handlers:");
					consoleInterface.checkInstances(" - ", this);
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		return ((ComponentAction[]) cal.toArray(new ComponentAction[cal.size()]));
	}
}