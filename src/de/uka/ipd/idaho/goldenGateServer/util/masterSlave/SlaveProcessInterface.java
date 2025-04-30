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
package de.uka.ipd.idaho.goldenGateServer.util.masterSlave;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

import de.uka.ipd.idaho.gamta.util.ProgressMonitor;

/**
 * Helper class that opens an interface between slave Java processes and the
 * main server. The interface provides monitoring and control functionality,
 * allowing access to the slave Java process from the main server console. The
 * <code>start()</code> method of this class starts a <code>Thread</code> that
 * reads input from <code>System.in</code>, so client code must make sure to
 * read all input relevant to its operations before calling that method. Output
 * from slave processes can be sent via the 
 * 
 * @author sautter
 */
public class SlaveProcessInterface {
	private Process slave = null;
	private String slaveName = null;
	private PrintStream toSlave;
	private InputStream slaveOut;
	private InputStream slaveErr;
	private ProgressMonitor progressMonitor;
	
	/**
	 * Constructor for the master side, for communicating with the argument
	 * slave <code>Process</code>.
	 * @param slave the slave process to interface with
	 * @param slaveName the name of the slave process
	 */
	public SlaveProcessInterface(Process slave, String slaveName) {
		this.slave = slave;
		this.slaveName = slaveName;
		this.toSlave = new PrintStream(this.slave.getOutputStream(), true);
		this.slaveOut = this.slave.getInputStream();
		this.slaveErr = this.slave.getErrorStream();
	}
	
	/**
	 * Start threads reacting to input from the connected input streams. These
	 * are the input streams obtained from <code>Process.getInputStream()</code>
	 * and <code>Process.getErrorStream()</code> methods of the slave process.
	 */
	public void start() {
		final BufferedReader sysInBr = new BufferedReader(new InputStreamReader(this.slaveOut));
		Thread sysInThread = new Thread(this.slaveName + "OutRelay") {
			public void run() {
				try {
					for (String inLine; (inLine = sysInBr.readLine()) != null;) {
						if (inLine.startsWith("PM:")) {
							if (progressMonitor == null)
								continue;
							inLine = inLine.substring("PM:".length());
							if (inLine.startsWith("S:"))
								progressMonitor.setStep(inLine.substring("S:".length()));
							else if (inLine.startsWith("I:"))
								progressMonitor.setInfo(inLine.substring("I:".length()));
							else if (inLine.startsWith("P:"))
								progressMonitor.setProgress(Integer.parseInt(inLine.substring("P:".length())));
							else if (inLine.startsWith("BP:"))
								progressMonitor.setBaseProgress(Integer.parseInt(inLine.substring("BP:".length())));
							else if (inLine.startsWith("MP:"))
								progressMonitor.setMaxProgress(Integer.parseInt(inLine.substring("MP:".length())));
						}
						else if (inLine.startsWith("EST:")) {
							handleStackTrace(inLine.substring("EST:".length()));
						}
						else if (inLine.startsWith("ERR:")) {
							handleError(inLine.substring("ERR:".length()), false);
						}
						else if (inLine.startsWith("RES:")) {
							handleResult(inLine.substring("RES:".length()));
						}
						else handleInput(inLine);
					}
				}
				catch (IOException e) {
					e.printStackTrace();
				}
				finally {
					finalizeSystemOut();
				}
			}
		};
		sysInThread.start();
		
		final BufferedReader errInBr = new BufferedReader(new InputStreamReader(this.slaveErr));
		Thread errInThread = new Thread(this.slaveName + "ErrRelay") {
			public void run() {
				try {
					for (String errLine; (errLine = errInBr.readLine()) != null;)
						handleError(errLine, true);
				}
				catch (IOException e) {
					e.printStackTrace();
				}
				finally {
					finalizeSystemErr();
				}
			}
		};
		errInThread.start();
	}
	
	/* TODO Facilitate diagnosing slave job problems under load in GG Server:
- problem basically is that responses from slave job's master process interface come out as background job info-level logging ...
- ... obscuring them between other such output (which can be considerable under hight update load) ...
- ... and increasing minimum console output log level for background jobs would filter desired debug responses indiscriminately
  ==> need to turn response to slave job diagnosis commands into console command output ...
  ==> ... and thus somehow hand over console action sending command to dedicated slave job output receiver thread
- might prefix lines sending diagnosis commands with tag like '<currentTimeMilis>@<masterLetterCode>' or something ...
- ... and prefix response lines with same tag ...
- ... adding terminating last empty line ...
- ... or maybe line containing single 0x04 byte (ASCII 'EOT' or 'end of transmission')
- at same time need to map tag to requesting console action (for receiving thread to probe if leading tag present on incoming line) ...
- ... removing mapping when terminating last line of response received
==> should be fine with local slave jobs ...
==> ... but might cause trouble with remote slave jobs that send data back in zipped streams ...
==> ... if not in slave job interfaces, but in general (envisioned as prefix based) message routing protocol of slave job master
  ==> simple solution: send binary zipped result data streams via different servlet ...
  ==> ... using upload token retrieved via text based channel in authentication header
    ==> IN FACT, ask Johannes about protecting against excessive headers that might be used for DOS
	 */
	
	/**
	 * Instruct the slave JVM to report a list of its threads to the
	 * <code>handleResult()</code> method.
	 */
	public void listThreads() {
		//	TODO facilitate accepting argument console action to report result to
		this.toSlave.println(MasterProcessInterface.LIST_THREADS_COMMAND);
	}
	
	/**
	 * Instruct the slave JVM to report a list of its thread groups to the
	 * <code>handleResult()</code> method.
	 */
	public void listThreadGroups() {
		//	TODO facilitate accepting argument console action to report result to
		this.toSlave.println(MasterProcessInterface.LIST_THREAD_GROUPS_COMMAND);
	}
	
	/**
	 * Instruct the slave JVM to report the stack of one of its threads to the
	 * <code>handleResult()</code> method. Setting the argument to null directs
	 * the command to the main thread.
	 * @param threadName the name of the thread whose stack to get
	 */
	public void printThreadStack(String threadName) {
		//	TODO facilitate accepting argument console action to report result to
		this.toSlave.println(MasterProcessInterface.THREAD_STACK_COMMAND + ((threadName == null) ? "" : (":" + threadName)));
	}
	
	/**
	 * Instruct the slave JVM to wake up one of its threads by calling the
	 * <code>interrupt()</code> method. Setting the argument to null directs
	 * the command to the main thread. Because waking an arbitrary thread
	 * externally can result in inconsistent states on the slave side, this
	 * method is only intended for troubleshooting.
	 * @param threadName the name of the thread to wake
	 */
	public void wakeThread(String threadName) {
		//	TODO facilitate accepting argument console action to report result to
		this.toSlave.println(MasterProcessInterface.WAKE_THREAD_COMMAND + ((threadName == null) ? "" : (":" + threadName)));
	}
	
	/**
	 * Instruct the slave JVM to kill one of its threads by calling the
	 * <code>stop()</code> method. Setting the argument to null directs the
	 * command to the main thread and shuts down the slave JVM. Because killing
	 * an arbitrary thread externally can result in inconsistent states on the
	 * slave side, this method is only intended for troubleshooting.
	 * @param threadName the name of the thread to kill
	 */
	public void killThread(String threadName) {
		//	TODO facilitate accepting argument console action to report result to
		this.toSlave.println(MasterProcessInterface.KILL_THREAD_COMMAND + ((threadName == null) ? "" : (":" + threadName)));
		if (threadName == null)
			this.slave.destroy();
	}
	
	/**
	 * Send a line of output to the slave JVM.
	 * @param output the output to send
	 */
	public void sendOutput(String output) {
		//	TODO facilitate accepting argument console action to report result to ...
		//	TODO ... and also facilities for subclasses to register their argument console actions on custom debug functionality
		this.toSlave.println(output);
	}
	
	/**
	 * Specify a progress monitor to relay progress information from the slave
	 * process to.
	 * @param pm the progress monitor to use
	 */
	public void setProgressMonitor(ProgressMonitor pm) {
		this.progressMonitor = pm;
	}
	
	/**
	 * Handle a line of input that is not a monitoring command handled by this
	 * class proper. This method is a mounting point for subclasses to extend
	 * their slave process communication functionality.
	 * @param input the input to handle
	 */
	protected void handleInput(String input) {}
	
	/**
	 * Handle a line of result message that is not a monitoring command handled
	 * by this class proper. This method is a mounting point for subclasses to
	 * extend their slave process communication functionality.
	 * @param result the result message to handle
	 */
	protected void handleResult(String result) {}
	
	/**
	 * Handle the end of the input obtained from the <code>System.out</code>
	 * stream of the salve process. This method is a mounting point for
	 * subclasses to extend their slave process communication functionality.
	 */
	protected void finalizeSystemOut() {}
	
	/**
	 * Handle a line of an error stack trace that is not a monitoring command
	 * handled by this class proper. This method is a mounting point for
	 * subclasses to extend their slave process communication functionality.
	 * This default implementation loops through to <code>handleError()</code>,
	 * indicating the error message was received from the output stream of the
	 * slave process, and the exception was handled there. Normally, a
	 * connected master process interface terminates such a stack trace with a
	 * blank line.
	 * @param stackTraceLine the stack trace line to handle
	 */
	protected void handleStackTrace(String stackTraceLine) {
		this.handleError(stackTraceLine, false);
	}
	
	/**
	 * Handle a line of error message that is not a monitoring command handled
	 * by this class proper. This method is a mounting point for subclasses to
	 * extend their slave process communication functionality.
	 * @param error the error message to handle
	 * @param fromSysErr was the argument error received from the error stream?
	 */
	protected void handleError(String error, boolean fromSysErr) {}
	
	/**
	 * Handle the end of the input obtained from the <code>System.err</code>
	 * stream of the salve process. This method is a mounting point for
	 * subclasses to extend their slave process communication functionality.
	 */
	protected void finalizeSystemErr() {}
}
