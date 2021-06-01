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
 *     * Neither the name of the Universitaet Karlsruhe (TH) nor the
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
package de.uka.ipd.idaho.goldenGateServer.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.goldenGateServer.AsynchronousWorkQueue;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent.ComponentActionConsole;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Convenience console action for running asynchronous actions from the console.
 * This class is intended to run large batch updates in a separate thread so
 * they do not block the console. This class provides the functionality for
 * starting, pausing, resuming, stopping, and monitoring running update threads.
 * In addition, it writes a log file to the folder specified to the constructor,
 * named '&lt;logFilePrefix&gt;.&lt;startTime&lt;.log', where
 * &lt;logFilePrefix&gt; is the prefix specified to the constructor and
 * &lt;startTime&gt; is the time when the update thread was started. Specifying
 * null for either one of the log related constructor arguments disables
 * logging. Each component using this class must make sure that it instantiates
 * its concrete version of this class only once.
 * 
 * @author sautter
 */
public abstract class AsynchronousConsoleAction extends ComponentActionConsole {
	
	//	TODO implement logging methods, and loop through to parent component
	
	private final String command;
	
	private String explanation;
	private String label;
	private String labelDeterminer;
	private String[] arguments;
	private String[] argumentExplanations;
	
	private File logFolder;
	private String logFilePrefix;
	
	/**
	 * Constructor
	 * @param command the action command (defaults to 'update' if null or the
	 *            empty string is specified)
	 * @param explanation the explanation string to go into the second line of
	 *            the explanation displayed in the console (must not be null)
	 * @param label the label of the action performed, namely a name for what
	 *            the action handles (must not be null)
	 * @param logFolder the folder to write update log files to (specifying null
	 *            disables logging)
	 * @param logFilePrefix the prefix for log file names (specifying null
	 *            disables logging)
	 */
	public AsynchronousConsoleAction(String command, String explanation, String label, File logFolder, String logFilePrefix) {
		this.command = (((command == null) || (command.trim().length() == 0)) ? "update" : command);
		
		this.explanation = explanation;
		this.label = label;
		this.labelDeterminer = (label.matches("[aeiouAEIOU].*") ? "an" : "a");
		
		this.logFolder = ((logFilePrefix == null) ? null : logFolder);
		this.logFilePrefix = ((logFolder == null) ? null : logFilePrefix);
		
		this.arguments = this.getArgumentNames();
		StringVector argumentExplanations = new StringVector();
		for (int a = 0; a < this.arguments.length; a++) {
			String[] argumentExplanation = this.getArgumentExplanation(this.arguments[a]);
			for (int e = 0; e < argumentExplanation.length; e++)
				argumentExplanations.addElement(((e == 0) ? ("- <" + this.arguments[a] + ">: ") : "  - ") + argumentExplanation[e]);
		}
		this.argumentExplanations = argumentExplanations.toStringArray();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent.ComponentAction#getActionCommand()
	 */
	public final String getActionCommand() {
		return this.command;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent.ComponentActionConsole#getExplanation()
	 */
	public final String[] getExplanation() {
		StringBuffer explanationHead = new StringBuffer(this.getActionCommand() + " <action>");
		for (int a = 0; a < this.arguments.length; a++)
			explanationHead.append(" <" + this.arguments[a] + ">");
		
		StringVector explanationLines = new StringVector();
		explanationLines.addElement(explanationHead.toString());
		explanationLines.addElement(this.explanation + (this.explanation.endsWith(":") ? "" : ":"));
		explanationLines.addElement("- <action>: the actual " + this.command + " action (specifying none displays the status, as does '-c')");
		explanationLines.addElement("  - '-s': start " + this.labelDeterminer + " " + this.label + " " + this.command + " (only one can run at a time)");
		explanationLines.addElement("  - '-m': monitor a running " + this.label + " " + this.command);
		explanationLines.addElement("  - '-q': quit monitoring a running " + this.label + " " + this.command);
		explanationLines.addElement("  - '-p': pause a running " + this.label + " " + this.command);
		explanationLines.addElement("  - '-r': resume a paused " + this.label + " " + this.command);
		explanationLines.addElement("  - '-a': abort a running " + this.label + " " + this.command);
		explanationLines.addElement("  - '-c': display the current status of a running " + this.label + " " + this.command);
		
		if (this.argumentExplanations.length != 0) {
			explanationLines.addElement(" Additional arguments (may be specified only if action argument is '-s')");
			for (int ae = 0; ae < this.argumentExplanations.length; ae++)
				explanationLines.addElement(this.argumentExplanations[ae]);
		}
		
		return explanationLines.toStringArray();
	}
	
	/**
	 * Provide an array holding the names of additional arguments that may be
	 * specified when starting an asynchronous action. For each argument name
	 * returned by this method, the getArgumentExplanation() method must return
	 * an array with at least one line of explanation in order for an argument
	 * to be included in the array returned by the getExplanation() method. This
	 * default implementation returns an empty array, sub classes are welcome to
	 * overwrite it as needed.
	 * @return an array holding the names of additional arguments
	 */
	protected String[] getArgumentNames() {
		return new String[0];
	}
	
	/**
	 * Provide an array with individual lines of explanation for an individual
	 * argument. For each argument name returned by the getArgumentNames()
	 * method, this method must return an array with at least one line of
	 * explanation in order for an argument to be included in the array returned
	 * by the getExplanation() method. The first line should explain the
	 * argument in general, any subsequent line should explain specific values,
	 * especially for command modifiers. This default implementation returns an
	 * empty array, sub classes are welcome to overwrite it as needed.
	 * @param argument the argument to provide an explanation for
	 * @return an array holding the explanation for the specified argument
	 */
	protected String[] getArgumentExplanation(String argument) {
		return new String[0];
	}
	
	/**
	 * Check whether an array of argument values is appropriate for invoking the
	 * performAction method. This default method simply returns null. Sub
	 * classes are welcome to overwrite it as needed.
	 * @param arguments the array of arguments to check
	 * @return null if the arguments are OK, an error message otherwise
	 */
	protected String checkArguments(String[] arguments) {
		return null;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent.ComponentActionConsole#performActionConsole(java.lang.String[])
	 */
	public final void performActionConsole(String[] arguments) {
		if (arguments.length == 0) {
			arguments = new String[1];
			arguments[0] = "-c";
		}
		else if (!"-s".equals(arguments[0]) && (arguments.length != 1)) {
			this.reportError(" Additional arguments can only be specified for action '-s'.");
			return;
		}
		
		if ("-s".equals(arguments[0])) {
			if (this.actionThread == null) {
				String[] threadArguments = new String[arguments.length-1];
				System.arraycopy(arguments, 1, threadArguments, 0, threadArguments.length);
				String argumentError = this.checkArguments(threadArguments);
				if (argumentError != null)
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + " -s'. " + argumentError);
				else try {
					checkRunnable();
					this.actionThread = new AsynchronousActionThread(threadArguments);
					this.actionThread.setUpdateMonitor(System.out); // TODO use reportXYZ()
					this.actionThread.startUpdate();
					this.actionThreadMonitor = new AsynchronousWorkQueue(this.getActionName()) {
						public String getStatus() {
							return (this.name + ": " + actionThread.status);
						}
					};
				}
				catch (RuntimeException re) {
					this.reportError(re.getMessage());
				}
			}
			else this.reportError("There is already " + this.labelDeterminer + " " + this.label + " update running.");
		}
		else if ("-m".equals(arguments[0])) {
			if (this.actionThread == null)
				this.reportError("There is no " + this.label + " " + this.command + " running.");
			else this.actionThread.setUpdateMonitor(System.out); // TODO use reportXYZ()
		}
		else if ("-q".equals(arguments[0])) {
			if (this.actionThread == null)
				this.reportError("There is no " + this.label + " " + this.command + " running.");
			else this.actionThread.setUpdateMonitor(null);
		}
		else if ("-p".equals(arguments[0])) {
			if (this.actionThread == null)
				this.reportError("There is no " + this.label + " " + this.command + " running.");
			else if (this.actionThread.pause)
				this.reportError("The running " + this.label + " " + this.command + " is already paused.");
			else this.actionThread.pauseUpdate();
		}
		else if ("-r".equals(arguments[0])) {
			if (this.actionThread == null)
				this.reportError("There is no " + this.label + " " + this.command + " running.");
			else if (this.actionThread.pause)
				this.actionThread.resumeUpdate();
			else this.reportError("The running " + this.label + " " + this.command + " is not paused.");
		}
		else if ("-a".equals(arguments[0])) {
			if (this.actionThread == null)
				this.reportError("There is no " + this.label + " " + this.command + " running.");
			else this.actionThread.abortUpdate();
		}
		else if ("-c".equals(arguments[0])) {
			if (this.actionThread == null)
				this.reportError("There is no " + this.label + " " + this.command + " running.");
			else this.reportResult("Current update: " + actionThread.status);
		}
		else this.reportError(" Invalid action for '" + this.getActionCommand() + "', use one of '-s', '-m', '-q', '-p', '-r', '-a', or '-c'.");
	}
	
	/**
	 * Provide the name of the asynchronous action for the background process
	 * overview. This method must not return null. This default implementation
	 * simply returns the action command, sub classes are welcome to overwrite
	 * it as needed to provide a more specific or further qualified name.
	 * @return the name of the asynchronous action
	 */
	protected String getActionName() {
		return this.command;
	}
	
	/**
	 * Check whether this action object is currently performing an asynchronous
	 * action.
	 * @return true if an asynchronous action thread is running, false otherwise
	 */
	public final boolean isRunning() {
		return (this.actionThread != null);
	}
	
	/**
	 * Check external conditions for the update action to be able to execute,
	 * e.g. if a sub class specific data is given. To report an error,
	 * implementations of this method should throw runtime exceptions. This
	 * default implementation does nothing, sub classes are welcome to overwrite
	 * it as needed.
	 */
	protected void checkRunnable() {}
	
	/**
	 * Perform the actual work. Implementations of this method that contain a
	 * loop over a large number of data items should invoke the
	 * loopRoundComplete() method to allow pausing after individual loop rounds,
	 * and should check with the keepUpdating() method to allow aborting the
	 * update process.
	 * @param arguments an array holding the arguments for the action
	 * @throws Exception
	 */
	protected abstract void performAction(String[] arguments) throws Exception;
	
	/**
	 * Notify the action that the performAction() method is about to enter the
	 * main loop. This method will then pause the action if this was triggered
	 * through the console via the 'update -p' command, or invoke Thread.yield()
	 * in order to give other threads some CPU time. Implementations of the
	 * performAction() method that contain a loop over a large number of data
	 * items should invoke this method before entering the loop.
	 * @param newStatus the status string before entering the loop
	 */
	protected final void enteringMainLoop(String newStatus) {
		this.loopRoundComplete(newStatus);
	}
	
	/**
	 * Notify the action that a round of a loop in the performAction() method is
	 * complete. This method will then pause the action if this was triggered
	 * through the console via the 'update -p' command, or invoke Thread.yield()
	 * in order to give other threads some CPU time. Implementations of the
	 * performAction() method that contain a loop over a large number of data
	 * items should invoke this method to allow pausing after individual loop
	 * rounds.
	 * @param newStatus the status string after the loop round
	 */
	protected final void loopRoundComplete(String newStatus) {
		if (this.actionThread != null)
			this.actionThread.loopRoundComplete(newStatus);
	}

	/**
	 * Check whether to continue the asynchronous action. In particular, this
	 * method returns false after an action was aborted from the console via the
	 * 'update -a' command. Implementations of the performAction() method that
	 * contain a loop over a large number of data items should include an
	 * invocation of this method in their loop condition.
	 */
	protected final boolean continueAction() {
		return ((this.actionThread != null) && this.actionThread.action);
	}
	
	/**
	 * Write a log entry. The specified log entry goes to the log file if
	 * logging is enabled, and to any monitoring stream that may be installed.
	 * @param logEntry the log entry to write
	 */
	protected final void log(String logEntry) {
		if (this.actionThread != null)
			this.actionThread.out.println(logEntry);
	}
	
	/**
	 * Write the log entries for an exception. The log entries goes to the log
	 * file if logging is enabled, and to any monitoring stream that may be
	 * installed.
	 * @param logEntry the log entry to accompany the exception
	 * @param e the exception to log
	 */
	protected final void log(String logEntry, Exception e) {
		if (this.actionThread != null) {
			this.actionThread.out.println(logEntry);
			e.printStackTrace(this.actionThread.out);
		}
	}
	
	private AsynchronousActionThread actionThread = null;
	private AsynchronousWorkQueue actionThreadMonitor = null;
	
	private class AsynchronousActionThread extends Thread {
		private PrintStream out;
		private String status = "No progress done so far";
		
		private PrintStream monitor;
		private Object monitorLock = new Object();
		
		private boolean action = true;
		private boolean pause = false;
		private Object runLock = new Object();
		
		private String[] arguments;
		
		AsynchronousActionThread(String[] arguments) {
			this.arguments = arguments;
		}
		
		public void run() {
			
			//	set up log file
			OutputStream logFileOut;
			if ((logFolder == null) || (logFilePrefix == null))
				logFileOut = null;
			else try {
				File logFile = new File(logFolder, (logFilePrefix + (logFilePrefix.endsWith(".") ? "" : ".") + System.currentTimeMillis() + ".log"));
				logFile.createNewFile();
				logFileOut = new BufferedOutputStream(new FileOutputStream(logFile));
			}
			catch (IOException ioe) {
				logFileOut = null;
			}
			
			//	set up log writer
			final OutputStream logOut = logFileOut;
			this.out = new PrintStream(new OutputStream() {
				public void write(int b) {}
			}, true) {
				public void write(byte[] buf, int off, int len) {
					if (logOut != null) try {
						logOut.write(buf, off, len);
					} catch (IOException ioe) {}
					synchronized (monitorLock) {
						if (monitor != null)
							monitor.write(buf, off, len);
					}
				}
				public void write(int b) {
					if (logOut != null) try {
						logOut.write(b);
					} catch (IOException ioe) {}
					synchronized (monitorLock) {
						if (monitor != null)
							monitor.write(b);
					}
				}
				public void flush() {
					if (logOut != null) try {
						logOut.flush();
					} catch (IOException ioe) {}
					synchronized (monitorLock) {
						if (monitor != null)
							monitor.flush();
					}
				}
			};

			
			//	free up starting thread
			synchronized (this.runLock) {
				this.runLock.notify();
			}
			
			//	do the actual work
			try {
				performAction(this.arguments);
			}
			catch (Exception e) {
				this.out.println(" Exception performing action '" + command + "': " + e.getMessage());
				e.printStackTrace(this.out);
			}
			catch (Error e) {
				this.out.println(" Error performing action '" + command + "': " + e.getMessage());
				e.printStackTrace(this.out);
			}
			
			this.out.println(" - closing log file");
			if (logOut != null) try {
				logOut.flush();
				logOut.close();
			} catch (IOException ioe) {}
			
			synchronized (this.runLock) {
				this.runLock.notify();
				if (actionThreadMonitor != null)
					actionThreadMonitor.dispose();
				actionThreadMonitor = null;
				actionThread = null;
			}
		}
		void loopRoundComplete(String newStatus) {
			this.status = newStatus;
			this.out.flush();
			if (this.pause) {
				this.out.println(" - pausing");
				synchronized (this.runLock) {
					this.runLock.notify();
					try {
						this.runLock.wait();
					} catch (InterruptedException ie) {}
				}
				if (this.action)
					this.out.println(" - resuming after pause");
			}
			else Thread.yield();
		}
		void startUpdate() {
			synchronized (this.runLock) {
				this.start();
				try {
					this.runLock.wait();
				} catch (InterruptedException ie) {}
			}
		}
		void pauseUpdate() {
			this.out.println("Pausing " + label + " " + command + " after current round");
			synchronized (this.runLock) {
				this.pause = true;
				try {
					this.runLock.wait();
				} catch (InterruptedException ie) {}
			}
			this.out.println(Gamta.capitalize(label) + " " + command + " paused");
		}
		void resumeUpdate() {
			synchronized (this.runLock) {
				this.pause = false;
				this.runLock.notify();
			}
			this.out.println(Gamta.capitalize(label) + " " + command + " resumed");
		}
		void abortUpdate() {
			this.out.println("Aborting " + label + " " + command + " after current round");
			synchronized (this.runLock) {
				this.action = false;
				if (this.pause) {
					this.pause = false;
					this.runLock.notify();
				}
			}
			try {
				this.join();
			} catch (InterruptedException ie) {}
		}
		void setUpdateMonitor(PrintStream monitor) {
			synchronized (this.monitorLock) {
				this.monitor = monitor;
			}
		}
	}
}