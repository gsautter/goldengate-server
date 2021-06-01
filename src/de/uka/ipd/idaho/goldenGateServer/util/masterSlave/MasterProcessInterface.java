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
 * Communication interface object for a slave JAR to communicate with their
 * master process via the system default IO streams. Instances of this class
 * use a dedicated thread to receive monitoring and control commands from the
 * <code>System.in</code> default input stream. Instances further provide the
 * facilities to send processing results, status information, and errors to the
 * master process. The generic <code>sendOutput()</code> method is an extension
 * point for sub classes to send messages specific to their task; respective
 * replies will be handed to the <code>handleInput()</code> method for handling
 * by sub classes. The message type prefixes and their handling need to be
 * complemented by code added to the corresponding extension points of the
 * <code>SlaveProcessInterface</code> in a respective sub class.
 * 
 * @author sautter
 */
public class MasterProcessInterface {
	
	/** command for listing the active threads in the slave JVM */
	static final String LIST_THREADS_COMMAND = "threads";
	
	/** command for listing the active thread groups in the slave JVM */
	static final String LIST_THREAD_GROUPS_COMMAND = "threadGroups";
	
	/** command for getting the stack trace of a specific thread in the slave JVM */
	static final String THREAD_STACK_COMMAND = "stack";
	
	/** command for waking up a specific suspended thread in the slave JVM */
	static final String WAKE_THREAD_COMMAND = "wake";
	
	/** command for killing a specific thread in the slave JVM, or the slave JVM proper */
	static final String KILL_THREAD_COMMAND = "kill";
	
	private PrintStream sysOut;
	private PrintStream sysErr;
	private InputStream sysIn;
	private Thread mainThread = null;
	private ThreadGroup rootThreadGroup = null;
	
	/**
	 * Constructor for slave side. Client code is strongly advised to call this
	 * constructor from its <code>main()</code> method, as that method is
	 * guaranteed to execute in the root thread of the JVM.
	 */
	public MasterProcessInterface() {
		this.sysOut = System.out;
		this.sysErr = System.err;
		this.sysIn = System.in;
		this.mainThread = Thread.currentThread();
		this.rootThreadGroup = this.mainThread.getThreadGroup();
	}
	
	Thread[] getThreads() {
		Thread[] threads = new Thread[512];
		int threadCount = this.rootThreadGroup.enumerate(threads, true);
		while (threadCount == threads.length) {
			threads = new Thread[threads.length * 2];
			threadCount = this.rootThreadGroup.enumerate(threads, true);
		}
		return threads;
	}
	
	Thread findThreads(String threadName) {
		Thread[] threads = getThreads();
		for (int t = 0; t < threads.length; t++) {
			if (threadName.equals(threads[t].getName()))
				return threads[t];
		}
		return null;
	}
	
	/**
	 * Start threads reacting to input from the connected slave process. In
	 * particular, this is <code>System.in</code>. the thread readily handles
	 * the monitoring and control commands represented by the constants in this
	 * class. It loops any other input to the <code>handleInput()</code>
	 * method, so as a way for sub classes to add more specific functionality
	 * as the need arises.
	 */
	public void start() {
		
		//	receive monitoring and control commands from System.in
		final BufferedReader sysInBr = new BufferedReader(new InputStreamReader(this.sysIn));
		Thread sysInThread = new Thread("MasterInterface") {
			public void run() {
				try {
					for (String inLine; (inLine = sysInBr.readLine()) != null;) {
						if (LIST_THREADS_COMMAND.equals(inLine)) {
							Thread[] threads = getThreads();
							sendResult("These are the currently active threads:");
							for (int t = 0; t < threads.length; t++)
								sendResult(" - " + threads[t].getName() + " (" + threads[t].getState() + ", " + threads[t].getClass().getName() + ")");
						}
						else if (LIST_THREAD_GROUPS_COMMAND.equals(inLine)) {
							ThreadGroup[] tgs = new ThreadGroup[128];
							int tgc = rootThreadGroup.enumerate(tgs, true);
							while (tgc == tgs.length) {
								tgs = new ThreadGroup[tgs.length * 2];
								tgc = rootThreadGroup.enumerate(tgs, true);
							}
							sendResult("These are the currently active thread groups:");
							for (int g = 0; g < tgs.length; g++)
								sendResult(" - " + tgs[g].getName() + " (" + tgs[g].activeCount() + " threads)");
						}
						else if (THREAD_STACK_COMMAND.equals(inLine)) {
							this.sendThreadStack(mainThread);
						}
						else if (inLine.startsWith(THREAD_STACK_COMMAND + ":")) {
							String threadName = inLine.substring(THREAD_STACK_COMMAND.length() + ":".length());
							Thread thread = findThreads(threadName);
							if (thread == null)
								sendError("Invalid thread name '" + threadName + "'");
							else this.sendThreadStack(thread);
						}
						else if (WAKE_THREAD_COMMAND.equals(inLine)) {
							this.wakeThread(mainThread);
						}
						else if (inLine.startsWith(WAKE_THREAD_COMMAND + ":")) {
							String threadName = inLine.substring(WAKE_THREAD_COMMAND.length() + ":".length());
							Thread thread = findThreads(threadName);
							if (thread == null)
								sendError("Invalid thread name '" + threadName + "'");
							else this.wakeThread(thread);
						}
						else if (KILL_THREAD_COMMAND.equals(inLine)) {
							System.exit(0);
						}
						else if (inLine.startsWith(KILL_THREAD_COMMAND + ":")) {
							String threadName = inLine.substring(KILL_THREAD_COMMAND.length() + ":".length());
							Thread thread = findThreads(threadName);
							if (thread == null) {
								sendError("Invalid thread name '" + threadName + "'");
								continue;
							}
							Thread.State ts = thread.getState();
							if (ts == Thread.State.NEW)
								sendError("Thread '" + threadName + "' is not started, cannot kill it");
							else if (ts == Thread.State.TERMINATED)
								sendError("Thread '" + threadName + "' is terminated, no use killing it");
							else {
								thread.stop();
								sendResult("Thread '" + thread.getName() + "' killed");
							}
						}
						else handleInput(inLine);
					}
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
			private void sendThreadStack(Thread thread) {
				sendResult("Thread " + thread.getName() + " (" + thread.getState() + ", " + thread.getClass().getName() + ")");
				StackTraceElement[] stes = thread.getStackTrace();
				for (int e = 0; e < stes.length; e++)
					sendResult("   " + stes[e].toString());
			}
			private void wakeThread(Thread thread) {
				Thread.State ts = thread.getState();
				if (ts == Thread.State.NEW)
					sendError("Thread '" + thread.getName() + "' is not started, cannot wake it");
				else if (ts == Thread.State.RUNNABLE)
					sendError("Thread '" + thread.getName() + "' is running, no use waking it");
				else if (ts == Thread.State.TERMINATED)
					sendError("Thread '" + thread.getName() + "' is terminated, cannot wake it any more");
				else {
					thread.interrupt();
					sendResult("Thread '" + thread.getName() + "' woken up");
				}
			}
		};
		sysInThread.start();
		
		//	close IO streams after main method terminates (would be waiting on System.in indefinitely otherwise)
		Thread shutdownGuard = new Thread("ShutdownGuard") {
			public void run() {
				
				//	wait for main thread to complete
				while (true) try {
					mainThread.join();
					break; // once we get here, join() has returned normally
				} catch (InterruptedException ie) {}
				
				//	close streams
//				sysOut.println("Main thread terminated, shutting down slave.");
				try {
					sysIn.close();
				}
				catch (Exception e) {
					sysOut.println("Error closing System.in: " + e.getMessage());
					e.printStackTrace(sysOut);
				}
				try {
					sysErr.close();
				}
				catch (Exception e) {
					sysOut.println("Error closing System.err: " + e.getMessage());
					e.printStackTrace(sysOut);
				}
				try {
					sysOut.close();
				}
				catch (Exception e) {
					sysOut.println("Error closing System.out: " + e.getMessage());
					e.printStackTrace(sysOut);
				}
				
				//	finish the job
				System.exit(0);
			}
		};
		shutdownGuard.start();
	}
	
	/**
	 * Send a line of output to the master JVM.
	 * @param output the output to send
	 */
	public void sendOutput(String output) {
		this.sysOut.println(output);
	}
	
	/**
	 * Send a line of result message to the master JVM.
	 * @param result the result message to send
	 */
	public void sendResult(String result) {
		this.sysOut.println("RES:" + result);
	}
	
	/**
	 * Send an error message to the master JVM.
	 * @param error the error message
	 */
	public void sendError(String error) {
		this.sysOut.println("ERR:" + error);
	}
	
	/**
	 * Send an error stack trace to the master JVM.
	 * @param error the error whose stack trace to send
	 */
	public void sendError(Throwable error) {
        this.sysOut.println("EST:" + error.getClass().getName() + ": " + error.getMessage());
        StackTraceElement[] stes = error.getStackTrace();
        for (int e = 0; e < stes.length; e++)
            this.sysOut.println("EST:\tat " + stes[e]);
	}
	
	/**
	 * Create a progress monitor relaying all progress information to the
	 * master JVM.
	 * @return a relaying progress monitor
	 */
	public ProgressMonitor createProgressMonitor() {
		return new ProgressMonitor() {
			public void setStep(String step) {
				sysOut.println("PM:S:" + step);
			}
			public void setInfo(String info) {
				sysOut.println("PM:I:" + info);
			}
			public void setBaseProgress(int baseProgress) {
				sysOut.println("PM:BP:" + baseProgress);
			}
			public void setMaxProgress(int maxProgress) {
				sysOut.println("PM:MP:" + maxProgress);
			}
			public void setProgress(int progress) {
				sysOut.println("PM:P:" + progress);
			}
		};
	}
	
	/**
	 * Handle a line of input that is not a monitoring command handled by this
	 * class proper. This method is a mounting point for subclasses to extend
	 * their main server communication functionality.
	 * @param input the input to handle
	 */
	protected void handleInput(String input) {}
}
