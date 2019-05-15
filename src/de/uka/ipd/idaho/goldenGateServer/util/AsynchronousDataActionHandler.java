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
package de.uka.ipd.idaho.goldenGateServer.util;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import de.uka.ipd.idaho.easyIO.EasyIO;
import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.SqlQueryResult;
import de.uka.ipd.idaho.easyIO.sql.TableColumnDefinition;
import de.uka.ipd.idaho.easyIO.sql.TableDefinition;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerActivityLogger;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent.ComponentActionConsole;

/**
 * Handler for asynchronous actions on data objects. Optionally, pending actions
 * can be persisted in a database table for failover. This class wraps a
 * dedicated background worker thread for executing the actions.
 * 
 * @author sautter
 */
public abstract class AsynchronousDataActionHandler {
	
	private final String ACTION_TABLE_NAME;
	private static final String DATA_ID_COLUMN_NAME = "dataId";
	private static final String DATA_ID_HASH_COLUMN_NAME = "dataIdHash";
	private static final String DUE_TIME_COLUMN_NAME = "dueTime";
	
	private String name;
	private String[] argumentNames;
	private GoldenGateServerActivityLogger logger;
	private IoProvider io;
	private TableColumnDefinition[] argumentColumns;
	private String argumentColumnString;
	
	private DataActionThread actionThread;
	
	/**
	 * @param name the name of the scheduler (letters only, and no spaces)
	 * @param logger the logger to report to
	 */
	public AsynchronousDataActionHandler(String name, GoldenGateServerActivityLogger host) {
		this(name, null, host, null, null);
	}
	
	/**
	 * @param name the name of the scheduler (letters only, and no spaces)
	 * @param argumentNames the names of the arguments for data actions
	 * @param logger the logger to report to
	 */
	public AsynchronousDataActionHandler(String name, String[] argumentNames, GoldenGateServerActivityLogger host) {
		this(name, argumentNames, host, null, null);
	}
	
	/**
	 * @param name the name of the scheduler (letters only, and no spaces)
	 * @param logger the logger to report to
	 * @param io the IoProvider to use for persisting pending actions
	 */
	public AsynchronousDataActionHandler(String name, GoldenGateServerActivityLogger logger, IoProvider io) {
		this(name, null, logger, io, null);
	}
	
	/**
	 * @param name the name of the scheduler (letters only, and no spaces)
	 * @param argumentColumns the column definitions for persisting the
	 *            arguments for data actions (also defines the names)
	 * @param logger the logger to report to
	 * @param io the IoProvider to use for persisting pending actions
	 */
	public AsynchronousDataActionHandler(String name, TableColumnDefinition[] argumentColumns, GoldenGateServerActivityLogger logger, IoProvider io) {
		this(name, null, logger, io, argumentColumns);
	}
	
	private AsynchronousDataActionHandler(String name, String[] argumentNames, GoldenGateServerActivityLogger logger, IoProvider io, TableColumnDefinition[] argumentColumns) {
		this.name = name;
		if (argumentNames != null)
			this.argumentNames = argumentNames;
		else if (argumentColumns == null)
			this.argumentNames = new String[0];
		else {
			this.argumentNames = new String[argumentColumns.length];
			for (int a = 0; a < argumentColumns.length; a++)
				this.argumentNames[a] = argumentColumns[a].getColumnName();
		}
		this.argumentNames = ((argumentNames == null) ? new String[0] : argumentNames);
		this.ACTION_TABLE_NAME = (this.name + "Actions");
		this.logger = logger;
		this.io = io;
		this.argumentColumns = ((argumentColumns == null) ? new TableColumnDefinition[0] : argumentColumns);
		StringBuffer argColStr = new StringBuffer();
		for (int a = 0; a < this.argumentColumns.length; a++)
			argColStr.append(", " + this.argumentColumns[a].getColumnName());
		this.argumentColumnString = argColStr.toString();
		
		//	create database tables (if IO provider present)
		if (this.io != null) {
			if (!this.io.isJdbcAvailable())
				throw new RuntimeException(this.name + " cannot work without database access.");
			
			//	ensure data table
			TableDefinition td = new TableDefinition(ACTION_TABLE_NAME);
			td.addColumn(DATA_ID_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
			td.addColumn(DATA_ID_HASH_COLUMN_NAME, TableDefinition.INT_DATATYPE, 0);
			td.addColumn(DUE_TIME_COLUMN_NAME, TableDefinition.BIGINT_DATATYPE, 0);
			for (int a = 0; a < this.argumentColumns.length; a++)
				td.addColumn(this.argumentColumns[a]);
			if (!this.io.ensureTable(td, true))
				throw new RuntimeException(this.name + " cannot work without database access.");
			
			//	add indexes
			this.io.indexColumn(ACTION_TABLE_NAME, DATA_ID_COLUMN_NAME);
			this.io.indexColumn(ACTION_TABLE_NAME, DATA_ID_HASH_COLUMN_NAME);
		}
	}
	
	private static final String SCHEDULE_ACTION_COMMAND = "scheduleAction";
	private static final String ENQUEUE_ACTION_COMMAND = "enqueueAction";
	private static final String ACTIONS_PENDING_COMMAND = "actionsPending";
	private static final String CLEAR_PENDING_COMMAND = "clearPending";
	private static final String ACTION_ERRORS_COMMAND = "actionErrors";
	private static final String WORK_FAST_COMMAND = "workFast";
	private static final String WORK_SLOW_COMMAND = "workSlow";
	private static final String DUMP_STACK_COMMAND = "dumpStack";
	
	/**
	 * Retrieve actions to integrate in the console interface of the hosting
	 * server component. The returned actions are necessarily named in a rather
	 * generic fashion. Client code that wishes to provide actions with more
	 * specific names and explanations can implement them based upon the API of
	 * this class, which exposes the very same functionality as the console
	 * actions returned by this method.
	 * @return an array holding the console actions
	 */
	public ComponentActionConsole[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentActionConsole cac;
		
		//	create explanation snippets for arguments
		StringBuffer argStringBuf = new StringBuffer();
		StringBuffer scheduleArgErrorStringBuf = new StringBuffer();
		StringBuffer enqueueArgErrorStringBuf = new StringBuffer();
		final String[] argDetailStrings = new String[this.argumentNames.length];
		for (int a = 0; a < this.argumentNames.length; a++) {
			String argName = (this.argumentNames[a].substring(0, 1).toLowerCase() + this.argumentNames[a].substring(1));
			argStringBuf.append(" <" + argName + ">");
			scheduleArgErrorStringBuf.append(", ");
			if ((a+1) == this.argumentNames.length)
				scheduleArgErrorStringBuf.append("and ");
			if (this.argumentNames.length > 1)
				enqueueArgErrorStringBuf.append(", ");
			if ((a+1) == this.argumentNames.length)
				enqueueArgErrorStringBuf.append("and ");
			StringBuffer argDetailLabel = new StringBuffer();
			for (int c = 0; c < argName.length(); c++) {
				char ch = argName.charAt(c);
				if (Character.isUpperCase(ch)) {
					if (argDetailLabel.length() != 0)
						argDetailLabel.append(' ');
					argDetailLabel.append(Character.toLowerCase(ch));
				}
				else argDetailLabel.append(ch);
			}
			scheduleArgErrorStringBuf.append(argDetailLabel);
			enqueueArgErrorStringBuf.append(argDetailLabel);
			argDetailStrings[a] = (" - <" + argName + ">: the " + argDetailLabel.toString());
		}
		final String argString = argStringBuf.toString();
		final String scheduleArgErrorString = scheduleArgErrorStringBuf.toString();
		final String enqueueArgErrorString = enqueueArgErrorStringBuf.toString();
		
		//	schedule re-packing without document update
		cac = new ComponentActionConsole() {
			public String getActionCommand() {
				return SCHEDULE_ACTION_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						(SCHEDULE_ACTION_COMMAND + " <dataId> <in>" + argString),
						"Schedule the action for some data object:",
						"- <dataId>: the ID of the data object",
						"- <in>: the number of milliseconds to wait before executing the action"
					};
				explanation = concatArrays(explanation, argDetailStrings);
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length < 2)
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify at least the data ID and time to wait, plus the " + scheduleArgErrorString + ".");
				else if (arguments.length <= (2 + argumentNames.length)) {
					int in = 0;
					try {
						in = Integer.parseInt(arguments[1]);
					} catch (NumberFormatException nfe) {}
					String[] args = new String[arguments.length - 2];
					System.arraycopy(arguments, 2, args, 0, args.length);
					scheduleDataAction(arguments[0], args, in);
					this.reportResult(" Action scheduled for '" + arguments[0] + "' to execute " + ((in < 1) ? "immediately" : ("in " + in + " milliseconds")) + ".");
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify at most the data ID" + ((argumentNames.length == 0) ? " and " : ", ") + "time to wait" + scheduleArgErrorString + ".");
			}
		};
		cal.add(cac);
		
		//	schedule re-packing without document update
		cac = new ComponentActionConsole() {
			public String getActionCommand() {
				return ENQUEUE_ACTION_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						(ENQUEUE_ACTION_COMMAND + " <dataId>" + argString),
						"Enqueue an action for some data object for immediate execution:",
						"- <dataId>: the ID of the data object",
					};
				explanation = concatArrays(explanation, argDetailStrings);
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length < 1)
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify at least the data ID" + enqueueArgErrorString + ".");
				else if (arguments.length <= (1 + argumentNames.length)) {
					String[] args = new String[arguments.length - 1];
					System.arraycopy(arguments, 1, args, 0, args.length);
					scheduleDataAction(arguments[0], args, 0);
					this.reportResult(" Action enqueued for '" + arguments[0] + "'.");
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify at most the data ID" + enqueueArgErrorString + ".");
			}
		};
		cal.add(cac);
		
		//	check size of re-packing queue
		cac = new ComponentActionConsole() {
			public String getActionCommand() {
				return ACTIONS_PENDING_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						ACTIONS_PENDING_COMMAND,
						"Check how many data actions are scheduled for execution."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length != 0)
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
				else synchronized (dataActions) {
					this.reportResult(" " + dataActions.size() + " data actions scheduled for execution.");
				}
			}
		};
		cal.add(cac);
		
		//	offer function clearing queue
		cac = new ComponentActionConsole() {
			public String getActionCommand() {
				return CLEAR_PENDING_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						CLEAR_PENDING_COMMAND,
						"Clear the queue of scheduled data actions."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0)
					clearDataActions(this);
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(cac);
		
		//	check packing errors
		cac = new ComponentActionConsole() {
			public String getActionCommand() {
				return ACTION_ERRORS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						ACTION_ERRORS_COMMAND + " <mode>",
						"List the errors that occurred during the execution of data actions:",
						"- <mode>: set to '-t' for showing full stack traces (optional)"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length > 1) 
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify at most the mode argument.");
				else if (dataActionErrors.isEmpty())
					this.reportResult(" No packing errors occurrend for any pending archive.");
				else synchronized (dataActionErrors) {
					this.reportError(" The following packing errors occurrend for pending archives:");
					boolean showStackTraces = ((arguments.length == 1) && "-t".equals(arguments[0]));
					for (Iterator didit = dataActionErrors.keySet().iterator(); didit.hasNext();) {
						String mDocId = ((String) didit.next());
						Exception mDocEx = ((Exception) dataActionErrors.get(mDocId));
						this.reportResult(" - " + mDocId + ": " + mDocEx.getMessage());
						if (showStackTraces)
							this.reportError(mDocEx);
					}
				}
			}
		};
		cal.add(cac);
		
		//	activate fast packing
		cac = new ComponentActionConsole() {
			public String getActionCommand() {
				return WORK_FAST_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						WORK_FAST_COMMAND,
						"Activate fast working, i.e., deactivate pausing between execution of individual data actions."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length != 0) 
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
				else actionThread.setWorkFast(true, this);
			}
		};
		cal.add(cac);
		
		//	deactivate fast packing
		cac = new ComponentActionConsole() {
			public String getActionCommand() {
				return WORK_SLOW_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						WORK_SLOW_COMMAND,
						"Deactivate fast working, i.e., activate pausing between execution of individual data actions."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length != 0) 
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
				else actionThread.setWorkFast(false, this);
			}
		};
		cal.add(cac);
		
		//	offer function dumpStack printing stack trace of event processor (to help investigating hang-ups)
		cac = new ComponentActionConsole() {
			public String getActionCommand() {
				return DUMP_STACK_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						DUMP_STACK_COMMAND,
						"Dump the current stack of the action worker thread (helps investigate any hang-ups)."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length != 0)
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
				else if (actionThread != null) {
					StackTraceElement[] stes = actionThread.getStackTrace();
					this.reportResult(actionThread.getName() + ":");
					for (int e = 0; e < stes.length; e++)
						this.reportResult("  at " + stes[e].toString());
				}
			}
		};
		cal.add(cac);
		
		return ((ComponentActionConsole[]) cal.toArray(new ComponentActionConsole[cal.size()]));
	}
	
	private static String[] concatArrays(String[] array1, String[] array2) {
		if ((array1 == null) || (array1.length == 0))
			return array2;
		if ((array2 == null) || (array2.length == 0))
			return array1;
		String[] array = new String[array1.length + array2.length];
		System.arraycopy(array1, 0, array, 0, array1.length);
		System.arraycopy(array2, 0, array, array1.length, array2.length);
		return array;
	}
	
	/**
	 * Start the scheduler. This method should only be called once the code
	 * called from the <code>performAction()</code> method is ready to work.
	 */
	public void start() {
		
		//	restore scheduled actions from database (no need for synchronizing just yet, as we're starting event handler only below)
		if (this.io != null) {
			String loadQuery = "SELECT " + DATA_ID_COLUMN_NAME + ", " + DUE_TIME_COLUMN_NAME + this.argumentColumnString +
					" FROM " + ACTION_TABLE_NAME +
					" ORDER BY " + DUE_TIME_COLUMN_NAME +
					";";
			SqlQueryResult sqr = null;
			try {
				sqr = this.io.executeSelectQuery(loadQuery);
				while (sqr.next()) {
					String dataId = sqr.getString(0);
					long due = sqr.getLong(1);
					
					//	read custom arguments
					String[] arguments = new String[this.argumentColumns.length];
					int argLength = 0;
					for (int a = 0; a < this.argumentColumns.length; a++) {
						String argValue = sqr.getString(a + 2);
						if (TableDefinition.CHAR_DATATYPE.equals(this.argumentColumns[a].getDataType()))
							argValue = argValue.trim();
						if (!isDefaultValue(argValue, this.argumentColumns[a].getDataType()))
							argLength = (a+1);
						arguments[a] = argValue;
					}
					
					//	truncate custom arguments
					if (argLength < arguments.length) {
						String[] tArguments = new String[arguments.length];
						System.arraycopy(arguments, 0, tArguments, 0, tArguments.length);
						arguments = tArguments;
					}
					
					//	enqueue data action
					DataAction da = new DataAction(dataId, arguments, due);
					this.dataActions.addLast(da);
					this.dataActionsById.put(da.id, da);
				}
			}
			catch (SQLException sqle) {
				System.out.println(this.name + ": " + sqle.getMessage() + " while restoring scheduled actions.");
				System.out.println("  query was " + loadQuery);
			}
			finally {
				if (sqr != null)
					sqr.close();
			}
		}
		
		//	start action worker thread
		this.actionThread = new DataActionThread(this.name + "ActionWorker");
		this.actionThread.start();
	}
	
	private static boolean isDefaultValue(String value, String dataType) {
		if (TableDefinition.INT_DATATYPE.equals(dataType))
			return "0".equals(value);
		else if (TableDefinition.BIGINT_DATATYPE.equals(dataType))
			return "0".equals(value);
		else if (TableDefinition.REAL_DATATYPE.equals(dataType))
			return ("0".equals(value) || "0.0".equals(value));
		else if (TableDefinition.CHAR_DATATYPE.equals(dataType))
			return "".equals(value.trim());
		else if (TableDefinition.VARCHAR_DATATYPE.equals(dataType))
			return "".equals(value);
		else return false;
	}
	
	/**
	 * Shut down the scheduler. This method terminates the wrapped worker thread
	 * and thus should be called on system shutdown.
	 */
	public void shutdown() {
		if (this.actionThread != null)
			this.actionThread.shutdown();
	}
	
	private static final long millisecondsPerMonth = (1000 * 60 * 60 * 24 * 30);
	private static final String[] noArguments = {};
	
	/**
	 * Enqueue an asynchronous action on a data object for immediate (soon as
	 * possible) execution. If there are many actions in the queue, there might
	 * be some delay, though.<br>
	 * If the action was previously scheduled for the same data object (by ID)
	 * and the same arguments, a subsequent call to this method does not incur
	 * a second call to <code>performDataAction()</code>.
	 * @param dataId the ID of the data object
	 * @param arguments the arguments for the action
	 */
	public void enqueueDataAction(String dataId, String[] arguments) {
		this.doScheduleDataAction(dataId, arguments, 0);
	}
	
	/**
	 * Enqueue an asynchronous action on a data object for immediate (soon as
	 * possible) execution. If there are many actions in the queue, there might
	 * be some delay, though.<br>
	 * If the action was previously scheduled for the same data object (by ID)
	 * and the same arguments, a subsequent call to this method does not incur
	 * a second call to <code>performDataAction()</code>.<br>
	 * If the argument priority is non-zero, the action will be scheduled to
	 * have its due time in the past, moving it to the front of the queue. The
	 * argument priority specifies the number of months to move to the past.
	 * @param dataId the ID of the data object
	 * @param arguments the arguments for the action
	 * @param priority the priority of the action
	 */
	public void enqueueDataAction(String dataId, String[] arguments, int priority) {
		long ago = (millisecondsPerMonth * Math.max(priority, 0));
		this.doScheduleDataAction(dataId, arguments, -ago);
	}
	
	/**
	 * Schedule an asynchronous action on a data object. Counting from the call
	 * to this method, the <code>in</code> argument specifies the number of
	 * milliseconds until the earliest possible call to the
	 * <code>performDataAction()</code> method for the argument data ID. If
	 * many actions are scheduled, the latter call may only come a while after
	 * the argument number of milliseconds has expired.<br>
	 * If the action was previously scheduled for the same data object (by ID)
	 * and the same arguments, a subsequent call to this method adjusts the due
	 * time of the action, but does not schedule a second call to
	 * <code>performDataAction()</code>.
	 * @param dataId the ID of the data object
	 * @param in the delay (minimum) between the call to this method and the
	 *            start of the action (in milliseconds).
	 */
	public void scheduleDataAction(String dataId, int in) {
		this.doScheduleDataAction(dataId, noArguments, Math.max(in, 0));
	}
	
	/**
	 * Schedule an asynchronous action on a data object. Counting from the call
	 * to this method, the <code>in</code> argument specifies the number of
	 * milliseconds until the earliest possible call to the
	 * <code>performDataAction()</code> method for the argument data ID. If
	 * many actions are scheduled, the latter call may only come a while after
	 * the argument number of milliseconds has expired.<br>
	 * If the action was previously scheduled for the same data object (by ID)
	 * and the same arguments, a subsequent call to this method adjusts the due
	 * time of the action, but does not schedule a second call to
	 * <code>performDataAction()</code>.
	 * @param dataId the ID of the data object
	 * @param arguments the arguments for the action
	 * @param in the delay (minimum) between the call to this method and the
	 *            start of the action (in milliseconds).
	 */
	public void scheduleDataAction(String dataId, String[] arguments, int in) {
		this.doScheduleDataAction(dataId, arguments, Math.max(in, 0));
	}
	
	/**
	 * Perform a scheduled action on a data object.
	 * @param dataId the ID of the data object to perform the action on
	 * @param arguments the arguments for the action, as specified on scheduling
	 * @throws Exception
	 */
	protected abstract void performDataAction(String dataId, String[] arguments) throws Exception;
	
	/**
	 * Retrieve the number of scheduled data actions, i.e., the size of the
	 * data action queue.
	 * @return the number of scheduled data actions.
	 */
	public int getDataActionsPending() {
		synchronized (this.dataActions) {
			return this.dataActions.size();
		}
	}
	
	/**
	 * Retrieve the number of milliseconds until the next data action is due
	 * for executing. If the queue is empty, this method returns 0. A negative
	 * return value indicates that a data action is in progress.
	 * @return the number of milliseconds until the next data action is due
	 */
	public long getNextDataActionDueIn() {
		synchronized (this.dataActions) {
			return this.dataActions.getNextDueIn();
		}
	}
	
	/**
	 * Activate or deactivate fast working, i.e., whether or not the wrapped
	 * data action executor thread sleeps and yields between individual data
	 * actions. Fast working mode is mainly intended for working off occasional
	 * load peaks, not as a permanent working setup, as the sleep and yield in
	 * between individual data actions is mainly intended to free up resources
	 * for other server activity. This method is primarily intended for calling
	 * by console actions, which is why it reports back to the argument one.
	 * @param fwa activate fast working?
	 * @param cac the console action to report results and errors to
	 */
	public void setFastWorkingActive(boolean fwa, ComponentActionConsole cac) {
		this.actionThread.setWorkFast(fwa, cac);
	}
	
	/**
	 * Retrieve a map of errors that occurred while executing data actions on
	 * specific data objects. The returned map has the data object IDs as keys
	 * and the respective processing exceptions as values.
	 * If no errors occurred, this method returns an empty map, but never null.
	 * @return a map of data object IDs to respective processing errors
	 */
	public Map getDataActionErrors() {
		Map dae = new LinkedHashMap();
		synchronized (this.dataActionErrors) {
			dae.putAll(this.dataActionErrors);
		}
		return dae;
	}
	
	/**
	 * Retrieve the current stack trace of the wrapped data action worker thread.
	 * This is mainly intended for monitoring and diagnostic purposes. Before the
	 * <code>start()</code> method is called, this method returns null.
	 * @return the current stack of the action worker thread
	 */
	public StackTraceElement[] getDataActionThreadStackTrace() {
		return ((this.actionThread == null) ? null : this.actionThread.getStackTrace());
	}
	
	/**
	 * Clear the queue of scheduled data actions. This method is primarily
	 * intended for calling by console actions, which is why it reports back
	 * to the argument one.
	 * @param cac the console action to report results and errors to
	 */
	public void clearDataActions(ComponentActionConsole cac) {
		synchronized (this.dataActions) {
			this.dataActions.clear();
			this.dataActionsById.clear();
		}
		String deleteQuery = "DELETE FROM " + ACTION_TABLE_NAME + ";";
		if (this.io != null) try {
			this.io.executeUpdateQuery(deleteQuery);
		}
		catch (SQLException sqle) {
			cac.reportError(name + ": " + sqle.getMessage() + " while clearing persisted data actions.");
			cac.reportError("  query was " + deleteQuery);
		}
		cac.reportResult("Data action queue cleared.");
	}
	
	private class DataActionThread extends Thread {
		private boolean run = true;
		private boolean sleepAfterAction = true;
		DataActionThread(String name) {
			super(name);
		}
		public void run() {
			
			//	do the job until told to quit
			while (this.run) {
				
				//	get next due data action
				DataAction da;
				synchronized (dataActions) {
					da = dataActions.getFirstIfDue();
					if (da == null) {
						this.sleepAfterAction = true; // deactivate fast working if queue empty or nothing due
						try {
							dataActions.wait(dataActions.getNextDueIn());
						} catch (InterruptedException ie) {}
						continue;
					}
				}
				
				//	perform data action
				long actionStartTime = System.currentTimeMillis();
				try {
					
					//	perform scheduled data action
					performDataAction(da.dataId, da.arguments);
					
					//	mark action as done if not re-scheduled
					if (da.isInProgress()) {
						da.setDone();
						synchronized (dataActions) {
							dataActions.cleanupDone();
							dataActionsById.remove(da.id);
						}
						cleanupPerformedAction(da);
					}
					
					//	clean any recorded error
					synchronized (dataActionErrors) {
						dataActionErrors.remove(da.dataId);
					}
				}
				catch (Exception e) {
					logger.logError("Exception exporting collection '" + da.dataId + "': " + e.getMessage());
					logger.logError(e);
					
					//	mark action as erroneous if not re-scheduled, and move to end of queue
					if (da.isInProgress()) {
						da.setError();
						synchronized (dataActions) {
							dataActions.sortUp();
						}
					}
					
					//	record error
					synchronized (dataActionErrors) {
						dataActionErrors.put(da.dataId, e);
					}
				}
				
				//	sleep a little (unless in fast working mode)
				if (this.sleepAfterAction && this.run) try {
					long sleepTime = (0 + 
							1000 + // base sleep
							(System.currentTimeMillis() - actionStartTime) + // the time we just occupied the CPU or other resources
							0);
					logger.logInfo(name + ": sleeping for " + sleepTime + "ms");
					Thread.sleep(sleepTime);
				} catch (InterruptedException ie) {}
			}
		}
		
		void setWorkFast(boolean workFast, ComponentActionConsole cac) {
			if (this.sleepAfterAction != workFast) {
				if (workFast)
					cac.reportError("Already working fast");
				else cac.reportError("Not working fast");
			}
			else {
				this.sleepAfterAction = !workFast;
				if (workFast)
					cac.reportResult("Fast working activated");
				else cac.reportResult("Fast working deactivated");
			}
		}
		
		void shutdown() {
			synchronized (dataActions) {
				this.run = false;
				dataActions.clear();
				dataActions.notify();
			}
		}
	}
	
	private static String computeDataActionId(String dataId, String[] arguments) {
		if ((arguments == null) || (arguments.length == 0))
			return dataId;
		StringBuffer daId = new StringBuffer(dataId);
		for (int a = 0; a < arguments.length; a++) {
			if (arguments[a].length() == 0)
				continue;
			daId.append('-');
			daId.append(arguments[a].hashCode());
		}
		return daId.toString();
	}
	
	private class DataAction {
		final String id;
		final String dataId;
		final String[] arguments;
		long due = -1;
		DataAction(String dataId, String[] arguments) {
			this(dataId, arguments, -1);
		}
		DataAction(String dataId, String[] arguments, long due) {
			this.id = computeDataActionId(dataId, arguments);
			this.dataId = dataId;
			this.arguments = arguments;
			this.due = due;
		}
		boolean isDue() {
			return ((this.due > 0) && (this.due <= System.currentTimeMillis()));
		}
		long getDueIn() {
			return (this.due - System.currentTimeMillis());
		}
		long setDueIn(long in) {
			long d = this.due;
			this.due = (System.currentTimeMillis() + in);
			return (this.due - d);
		}
		boolean isInProgress() {
			return (this.due == 0);
		}
		void setInProgress() {
			this.due = 0;
		}
		boolean isDone() {
			return (this.due == -1);
		}
		void setDone() {
			this.due = -1;
		}
		boolean isError() {
			return (this.due == Long.MAX_VALUE);
		}
		void setError() {
			this.due = Long.MAX_VALUE;
		}
	}
	
	private DataActionBuffer dataActions = new DataActionBuffer(8);
	private HashMap dataActionsById = new HashMap();
	private LinkedHashMap dataActionErrors = new LinkedHashMap();
	
	private void doScheduleDataAction(String dataId, String[] arguments, long in) {
		if (dataId == null)
			return;
		String daId = computeDataActionId(dataId, arguments);
		DataAction da;
		String persistQuery;
		synchronized (this.dataActions) {
			da = ((DataAction) this.dataActionsById.get(daId));
			if (da == null) {
				da = new DataAction(dataId, arguments);
				da.setDueIn(in);
				this.dataActions.addLast(da);
				this.dataActionsById.put(da.id, da);
				this.dataActions.notify();
				StringBuffer argValueString = new StringBuffer();
				for (int a = 0; a < this.argumentColumns.length; a++) {
					argValueString.append(", ");
					if (TableDefinition.INT_DATATYPE.equals(this.argumentColumns[a].getDataType()))
						argValueString.append((a < arguments.length) ? Integer.parseInt(arguments[a]) : 0);
					else if (TableDefinition.BIGINT_DATATYPE.equals(this.argumentColumns[a].getDataType()))
						argValueString.append((a < arguments.length) ? Long.parseLong(arguments[a]) : 0);
					else if (TableDefinition.REAL_DATATYPE.equals(this.argumentColumns[a].getDataType()))
						argValueString.append((a < arguments.length) ? Double.parseDouble(arguments[a]) : 0.0);
					else if (TableDefinition.CHAR_DATATYPE.equals(this.argumentColumns[a].getDataType())) {
						argValueString.append("'");
						StringBuffer argValue = new StringBuffer((a < arguments.length) ? arguments[a] : "");
						while (argValue.length() < this.argumentColumns[a].getColumnLength())
							argValue.append(" ");
						if (argValue.length() > this.argumentColumns[a].getColumnLength())
							argValue.delete(this.argumentColumns[a].getColumnLength(), argValue.length());
						argValueString.append(EasyIO.sqlEscape(argValue.toString()));
						argValueString.append("'");
					}
					else if (TableDefinition.VARCHAR_DATATYPE.equals(this.argumentColumns[a].getDataType())) {
						argValueString.append("'");
						String argValue = ((a < arguments.length) ? arguments[a] : "");
						if (argValue.length() > this.argumentColumns[a].getColumnLength())
							argValue = argValue.substring(0, this.argumentColumns[a].getColumnLength());
						argValueString.append(EasyIO.sqlEscape(argValue));
						argValueString.append("'");
					}
					else argValueString.delete((argValueString.length() - ", ".length()), argValueString.length());
				}
				persistQuery = "INSERT INTO " + ACTION_TABLE_NAME +
						" (" + DATA_ID_COLUMN_NAME + ", " + DATA_ID_HASH_COLUMN_NAME + ", " + DUE_TIME_COLUMN_NAME + this.argumentColumnString + ")" +
						" VALUES" +
						" ('" + EasyIO.sqlEscape(da.dataId) + "', " + da.dataId.hashCode() + ", " + da.due + argValueString + ")" +
						";";
			}
			else {
				long shift = da.setDueIn(in);
				if (shift == 0)
					return;
				if (shift < 0)
					this.dataActions.sortDown();
				else if (shift > 0)
					this.dataActions.sortUp();
				this.dataActions.notify();
				persistQuery = "UPDATE " + ACTION_TABLE_NAME + " SET" +
						" " + DUE_TIME_COLUMN_NAME + " = " + da.due + "" +
						" WHERE " + DATA_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(da.dataId) + "'" +
						" AND " + DATA_ID_HASH_COLUMN_NAME + " = " + da.dataId.hashCode() +
						";";
			}
		}
		
		//	persist scheduled action if set up to do so
		if (this.io != null) try {
			this.io.executeUpdateQuery(persistQuery);
		}
		catch (SQLException sqle) {
			this.logger.logError(this.name + ": " + sqle.getMessage() + " while persisting scheduled action.");
			this.logger.logError("  query was " + persistQuery);
		}
	}
	
	private void cleanupPerformedAction(DataAction da) {
		if (this.io == null)
			return;
		String deleteQuery = "DELETE FROM " + ACTION_TABLE_NAME +
				" WHERE " + DATA_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(da.dataId) + "'" +
				" AND " + DATA_ID_HASH_COLUMN_NAME + " = " + da.dataId.hashCode() +
				";";
		try {
			this.io.executeUpdateQuery(deleteQuery);
		}
		catch (SQLException sqle) {
			this.logger.logError(this.name + ": " + sqle.getMessage() + " while deleting action after processing.");
			this.logger.logError("  query was " + deleteQuery);
		}
	}
	
	private class DataActionBuffer {
		private DataAction[] actions;
		private int first = 0;
		private int last = 0;
		DataActionBuffer(int capacity) {
			this.actions = new DataAction[capacity];
		}
		
		long getNextDueIn() {
			//System.out.println("DataActionBuffer.getNextDueIn(): checking action due time, first is " + this.first + ", last is " + this.last + ", actions are " + Arrays.toString(this.actions)); // TODO remove this
			
			//	anything to return?
			if (this.first == this.last)
				return 0;
			
			//	time until first action is due (queue is sorted), making sure to stay above 0
			return Math.max(1, (this.actions[this.first].getDueIn()));
		}
		
		DataAction getFirstIfDue() {
			//System.out.println("DataActionBuffer.getFirstIfDue(): getting due action, first is " + this.first + ", last is " + this.last + ", actions are " + Arrays.toString(this.actions)); // TODO remove this
			
			//	anything to return?
			if (this.first == this.last)
				return null;
			
			//	first action is due (queue is sorted)
			if (this.actions[this.first].isDue()) {
				this.actions[this.first].setInProgress();
				return this.actions[this.first];
			}
			
			//	nothing due just yet
			else return null;
		}
		
		int addLast(DataAction da) {
			//System.out.println("DataActionBuffer.addLast(): adding action, first is " + this.first + ", last is " + this.last + ", actions are " + Arrays.toString(this.actions)); // TODO remove this
			
			//	make sure we have enough room
			if (this.last == this.actions.length) {
				if (this.first == 0) { // we're aligned at 0, need to increase array
					DataAction[] as = new DataAction[this.actions.length * 2];
					System.arraycopy(this.actions, 0, as, 0, this.actions.length);
					this.actions = as;
				}
				else { // shift content to 0
					int size = this.size();
					System.arraycopy(this.actions, this.first, this.actions, 0, size);
					this.first = 0;
					this.last = size;
				}
			}
			
			//	get insert position
			int daPos;
			
			//	store new action and move out end pointer
			daPos = this.size();
			this.actions[this.last++] = da;
			//System.out.println("DataActionBuffer.addLast(): action added, first is " + this.first + ", last is " + this.last + ", actions are " + Arrays.toString(this.actions)); // TODO remove this
			
			//	nothing to sort on single element
			if (this.size() < 2)
				return daPos;
			
			//	bubble sort in new action
			DataAction tda = null;
			for (int a = (this.last-1); a > this.first; a--) {
				if (this.actions[a].due < this.actions[a-1].due) {
					tda = this.actions[a-1];
					this.actions[a-1] = this.actions[a];
					this.actions[a] = tda;
					daPos--;
				}
				else break;
			}
			//System.out.println("DataActionBuffer.addLast(): actions sorted, first is " + this.first + ", last is " + this.last + ", actions are " + Arrays.toString(this.actions)); // TODO remove this
			
			//	finally ...
			return daPos;
		}
		
		void sortUp() {
			//System.out.println("DataActionBuffer.sortUp(): sorting actions, first is " + this.first + ", last is " + this.last + ", actions are " + Arrays.toString(this.actions)); // TODO remove this
			DataAction tda;
			for (int a = (this.first+1); a < this.last; a++) {
				if (this.actions[a].due < this.actions[a-1].due) {
					tda = this.actions[a-1];
					this.actions[a-1] = this.actions[a];
					this.actions[a] = tda;
				}
				else break;
			}
			//System.out.println("DataActionBuffer.sortUp(): actions sorted, first is " + this.first + ", last is " + this.last + ", actions are " + Arrays.toString(this.actions)); // TODO remove this
		}
		
		void sortDown() {
			//System.out.println("DataActionBuffer.sortDown(): sorting actions, first is " + this.first + ", last is " + this.last + ", actions are " + Arrays.toString(this.actions)); // TODO remove this
			DataAction tda;
			for (int a = (this.last-1); a > this.first; a--) {
				if (this.actions[a].due < this.actions[a-1].due) {
					tda = this.actions[a-1];
					this.actions[a-1] = this.actions[a];
					this.actions[a] = tda;
				}
				else break;
			}
			//System.out.println("DataActionBuffer.sortDown(): actions sorted, first is " + this.first + ", last is " + this.last + ", actions are " + Arrays.toString(this.actions)); // TODO remove this
		}
		
		void cleanupDone() {
			//System.out.println("DataActionBuffer.cleanupDone(): compacting actions, first is " + this.first + ", last is " + this.last + ", actions are " + Arrays.toString(this.actions)); // TODO remove this
			int ca = this.first;
			for (int a = this.first; a < this.last; a++) {
				if (this.actions[a].isDone()) {
					this.actions[a] = null;
					if (a == this.first) {
						this.first++;
						ca++;
					}
				}
				else if (ca < a) {
					this.actions[ca++] = this.actions[a];
					this.actions[a] = null;
				}
				else ca++;
			}
			if ((ca+1) < this.last) {
				Arrays.fill(this.actions, (ca+1), this.last, null);
				this.last = (ca+1);
			}
			//System.out.println("DataActionBuffer.cleanupDone(): actions compacted, first is " + this.first + ", last is " + this.last + ", actions are " + Arrays.toString(this.actions)); // TODO remove this
		}
		
		int size() {
			return (this.last - this.first);
		}
		
		void clear() {
			Arrays.fill(this.actions, this.first, this.last, null);
			this.first = 0;
			this.last = 0;
			//System.out.println("DataActionBuffer.clear(): action sorted, first is " + this.first + ", last is " + this.last + ", actions are " + Arrays.toString(this.actions)); // TODO remove this
		}
	}
}
