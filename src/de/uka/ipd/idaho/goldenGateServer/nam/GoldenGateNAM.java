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
package de.uka.ipd.idaho.goldenGateServer.nam;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServer;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerActivityLogger;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerNetworkActionListener;

/**
 * GoldenGATE Network Action Monitor (NAM) gathers detailed statistics on the
 * call frequency, runtime, and waiting time of individual network actions in
 * a GoldenGATE Server and dumps them to the log every 24 hours.
 * 
 * @author sautter
 */
public class GoldenGateNAM extends AbstractGoldenGateServerComponent implements GoldenGateServerNetworkActionListener {
	private static final DateFormat durationFormater;
	static {
		durationFormater = new SimpleDateFormat("HH:mm:ss");
		durationFormater.setTimeZone(TimeZone.getTimeZone("UTC"));
	}
	
	private long lastResetTime = System.currentTimeMillis();
	private StatsDumper statsDumper;
	
	/** zero-argument constructor handing 'NAM' as the letter code to the super class */
	public GoldenGateNAM() {
		super("NAM");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#link()
	 */
	public void link() {
		GoldenGateServer.addNetworkActionListener(this);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#linkInit()
	 */
	public void linkInit() {
		
		//	start timer thread
		this.statsDumper = new StatsDumper();
		this.statsDumper.start();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#exitComponent()
	 */
	protected void exitComponent() {
		
		//	output out statistics
		this.printStats(this, true);
		
		//	shut down timer thread
		this.statsDumper.shutdown();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerNetworkActionListener#networkActionStarted(java.lang.String, int)
	 */
	public void networkActionStarted(String command, int wait) {
		((NetworkActionStats) this.networkActionStatsByCommand.get(command)).actionStarted();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerNetworkActionListener#networkActionFinished(java.lang.String, int, int)
	 */
	public void networkActionFinished(String command, int wait, int time) {
		((NetworkActionStats) this.networkActionStatsByCommand.get(command)).actionFinished(wait, time);
	}
	
	private static final String SHOW_STATS_COMMAND = "showStats";
	private static final String DUMP_STATS_COMMAND = "dumpStats";
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#getActions()
	 */
	public ComponentAction[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentAction ca;
		
		//	add show statistics console action
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return SHOW_STATS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						SHOW_STATS_COMMAND,
						"Show the statistics on network actions"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) 
					printStats(this, false);
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	add store statistics console action
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return DUMP_STATS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						DUMP_STATS_COMMAND,
						"Dump the statistics on network actions (print and reset)"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) 
					printStats(this, true);
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	finally ...
		return ((ComponentAction[]) cal.toArray(new ComponentAction[cal.size()]));
	}
	
	void printStats(GoldenGateServerActivityLogger log, boolean reset) {
		
		//	get present action commands (need to synchronize only that part)
		ArrayList nasKeys = new ArrayList();
		synchronized (this.networkActionStatsByCommand) {
			nasKeys.addAll(this.networkActionStatsByCommand.keySet());
		}
		Collections.sort(nasKeys);
		
		//	handle individual actions
		int statsTime = ((int) (System.currentTimeMillis() - this.lastResetTime));
		log.logAlways("Network action activity in the past " + durationFormater.format(new Date(statsTime)) + " (" + statsTime + "ms):");
		for (int k = 0; k < nasKeys.size(); k++) {
			NetworkActionStats nas = ((NetworkActionStats) this.networkActionStatsByCommand.get(nasKeys.get(k)));
			nas.printStats(log, statsTime);
			if (reset)
				nas.reset();
		}
		
		//	reset recording time
		if (reset)
			this.lastResetTime = System.currentTimeMillis();
	}
	
	private Map networkActionStatsByCommand = Collections.synchronizedMap(new HashMap() {
		public Object get(Object key) {
			Object value = super.get(key);
			if (value == null) {
				value = new NetworkActionStats((String) key);
				this.put(key, value);
			}
			return value;
		}
	});
	
	private class NetworkActionStats {
		final String actionCommand;
		int runCount = 0;
		long lastStartTime = 0;
		long lastFinishTime = 0;
		long runTimeSum = 0;
		int minRunTime = Integer.MAX_VALUE;
		int maxRunTime = 0;
		long waitTimeSum = 0;
		int minWaitTime = Integer.MAX_VALUE;
		int maxWaitTime = 0;
		int waitCount = 0;
		NetworkActionStats(String actionCommand) {
			this.actionCommand = actionCommand;
		}
		synchronized void actionStarted() {
			this.lastStartTime = System.currentTimeMillis();
		}
		synchronized void actionFinished(int wait, int time) {
			this.runCount++;
			this.lastFinishTime = System.currentTimeMillis();
			this.runTimeSum += time;
			this.minRunTime = Math.min(this.minRunTime, time);
			this.maxRunTime = Math.max(this.maxRunTime, time);
			if (wait > 0) {
				this.waitCount++;
				this.waitTimeSum += wait;
				this.minWaitTime = Math.min(this.minWaitTime, wait);
				this.maxWaitTime = Math.max(this.maxWaitTime, wait);
			}
		}
		synchronized void printStats(GoldenGateServerActivityLogger log, int statsTime) {
			if (this.runCount == 0)
				return;
			log.logAlways(" - " + this.actionCommand + ":");
			log.logAlways("   run " + this.runCount + " times (" + (this.runTimeSum / this.runCount) + "ms on average [" + this.minRunTime + "," + this.maxRunTime + "])");
			log.logAlways("   last started " + (System.currentTimeMillis() - this.lastStartTime) + "ms ago, last finished " + (System.currentTimeMillis() - this.lastFinishTime) + "ms ago");
			if (this.waitCount == 0)
				return;
			log.logAlways("   waited " + this.waitCount + " times (" + (this.waitTimeSum / this.waitCount) + "ms on average [" + this.minWaitTime + "," + this.maxWaitTime + "])");
		}
		synchronized void reset() {
			this.runCount = 0;
			this.lastStartTime = 0;
			this.lastFinishTime = 0;
			this.runTimeSum = 0;
			this.minRunTime = Integer.MAX_VALUE;
			this.maxRunTime = 0;
			this.waitTimeSum = 0;
			this.minWaitTime = Integer.MAX_VALUE;
			this.maxWaitTime = 0;
			this.waitCount = 0;
		}
	}
	
	private class StatsDumper extends Thread {
		private boolean run = true;
		StatsDumper() {
			super("NamStatsDumper");
		}
		public void run() {
			
			//	sleep a little up front
			try {
				sleep(10 * 1000);
			} catch (InterruptedException ie) {}
			
			//	keep going until told otherwise
			while (this.run) {
				
				//	sleep until export due next time
				long dueIn = ((lastResetTime + (24 * 60 * 60 * 1000)) - System.currentTimeMillis());
				if (dueIn > 0) synchronized (this) {
					try {
						this.wait(dueIn);
					} catch (InterruptedException ie) {}
				}
				
				//	time we print them stats? (might have been printed in the meantime)
				if ((System.currentTimeMillis() - lastResetTime) < (24 * 60 * 60 * 1000))
					continue;
				
				//	output and reset statistics
				if (this.run)
					printStats(GoldenGateNAM.this, true);
			}
		}
		synchronized void shutdown() {
			this.run = false;
			this.notify();
		}
	}
}
