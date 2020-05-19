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
package de.uka.ipd.idaho.goldenGateServer.aep;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import de.uka.ipd.idaho.easyIO.EasyIO;
import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.SqlQueryResult;
import de.uka.ipd.idaho.easyIO.sql.TableDefinition;
import de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent;
import de.uka.ipd.idaho.goldenGateServer.AsynchronousWorkQueue;

/**
 * GoldenGATE Server Asynchronous Event Processor (AEP) is a convenience super
 * class for components that listen on events and handle them asynchronously in
 * a background process. This class provides an event queue and a service thread
 * working off events. Further, it provides a generic persistence mechanism for
 * enqueued events that facilitates recovery of unprocessed events after a
 * restart.<br>
 * It is up to sub classes to listen for the actual events and to process them.
 * 
 * @author sautter
 */
public abstract class GoldenGateAEP extends AbstractGoldenGateServerComponent {
	private static int instanceCount = 0;
	private static TreeMap instancesByName = new TreeMap();
	private static synchronized void registerInstance(GoldenGateAEP ep) {
		instanceCount++;
		System.out.println(ep.getEventProcessorName() + ": registered as instance number " + instanceCount + ".");
		instancesByName.put(ep.getEventProcessorName(), ep);
	}
	
	static void listInstances(String prefix, ComponentActionConsole cac) {
		for (Iterator enit = instancesByName.keySet().iterator(); enit.hasNext();) {
			String epName = ((String) enit.next());
			GoldenGateAEP ep = ((GoldenGateAEP) instancesByName.get(epName));
			boolean isFlushingQueue = (flushingEventHandler == ep.eventHandler);
			cac.reportResult(prefix + epName + ": " + ep.getClass().getName() + ", " + ep.eventQueue.size() + " update events pending (" + ep.eventQueue.highPriorityQueue.size() + "/" + ep.eventQueue.normPriorityQueue.size() + "/" + ep.eventQueue.lowPriorityQueue.size() + ")" + (isFlushingQueue ? " FLUSHING" : ""));
		}
	}
	
	static void checkInstances(String prefix, ComponentActionConsole cac) {
		for (Iterator enit = instancesByName.keySet().iterator(); enit.hasNext();) {
			String epName = ((String) enit.next());
			GoldenGateAEP ep = ((GoldenGateAEP) instancesByName.get(epName));
			if (ep.startEventHandler())
				cac.reportResult(prefix + epName + " (" + ep.getClass().getName() + "): worker thread restarted");
			else cac.reportResult(prefix + epName + " (" + ep.getClass().getName() + "): worker thread alive");
		}
	}
	
	private static UpdateEventHandler flushingEventHandler = null;
	private static synchronized boolean setFlushingEventHandler(UpdateEventHandler ueh, boolean flushing) {
		
		//	we need to know who's calling
		if (ueh == null)
			return false;
		
		//	there's already someone flushing, allow only one at a time
		if ((flushingEventHandler != null) && flushing)
			return (flushingEventHandler == ueh); // success only if flushing handler announces itself a second time
		
		//	start flushing (set flushing handler
		else if ((flushingEventHandler == null) && flushing) {
			flushingEventHandler = ueh;
			ueh.flushing = true;
			return true;
		}
		
		//	stop flushing (only allowed for flushing handler)
		else if ((flushingEventHandler == ueh) && !flushing) {
			flushingEventHandler = null;
			ueh.flushing = false;
			return true;
		}
		
		//	nobody there to stop flushing, or not authorized to do so
		else return false;
	}
	
	private static final Object aepPauseLock = new Object();
	private static final Set aepPausedInstances = Collections.synchronizedSet(new HashSet());
	private static boolean aepPause = false;
	static boolean setAepPause(boolean pause) {
		if (aepPause == pause)
			return false;
		else if (pause) {
			aepPause = true;
			return true;
		}
		else {
			synchronized (aepPauseLock) {
				aepPause = false;
			}
			do {
				synchronized (aepPauseLock) {
					aepPauseLock.notify();
				}
				Thread.yield();
			} while (aepPausedInstances.size() != 0);
			return true;
		}
	}
	
	private static final String DATA_ID_COLUMN_NAME = "dataId";
	private static final String DATA_ID_HASH_COLUMN_NAME = "dataIdHash";
	private static final String TIMESTAMP_COLUMN_NAME = "eventTime";
	private static final String USER_COLUMN_NAME = "eventUser";
	private static final String TYPE_COLUMN_NAME = "eventType";
	private static final String PRIORITY_COLUMN_NAME = "eventPriority";
	private static final String PARAMS_COLUMN_NAME = "eventParams";
	
	private static final String NULL_USER_NAME = "N_U_L_L";
	
	/** the name of the attribute set in the <code>dataAttributes</code> argument
	 * to the <code>doUpdate()</code> method if that method is called for the
	 * first time for the argument data object, namely 'isNewObject' */
	protected static final String IS_NEW_OBJECT_ATTRIBUTE = "isNewObject";
	
	/** high-priority marker */
	protected static final char PRIORITY_HIGH = '8';
	
	/** normal priority marker */
	protected static final char PRIORITY_NORMAL = '4';
	
	/** low-priority marker */
	protected static final char PRIORITY_LOW = '0';
	
	/** the IoProvider to use for sub class specific database interaction */
	protected IoProvider io;
	
	private final String EVENT_TABLE_NAME;
	private final String eventProcessorName;
	
	/**
	 * Constructor. The argument event processor name must consist of letters
	 * only and must not include whitespace, as among other things it serves
	 * as part of the name for the database table this component uses for
	 * persisting events.
	 * @param letterCode the letter code identifying the component
	 * @param eventProcessorName the name of the asynchronous event processor
	 */
	protected GoldenGateAEP(String letterCode, String eventProcessorName) {
		super(letterCode);
		this.eventProcessorName = eventProcessorName;
		this.EVENT_TABLE_NAME = (this.eventProcessorName + "Events");
		registerInstance(this);
	}
	
	/**
	 * Retrieve the name of the asynchronous event processor.
	 * @return the name of the event processor
	 */
	protected String getEventProcessorName() {
		return this.eventProcessorName;
	}
	
	/**
	 * Indicate whether or not events should be persisted even after they have
	 * been dequeued for processing. This default implementation returns false,
	 * indicating to persist only events that are waiting in the queue. Sub
	 * classes are welcome to overwrite this method as needed. A situation in
	 * which it is sensible to do so is if processing an event takes a
	 * considerable amount of time or is prone to hanging or failing in some
	 * way.
	 * @return true if events should be persisted even if already dequeued for
	 *            processing
	 */
	protected boolean persistProcessingEvents() {
		return false;
	}
	
	/**
	 * This method establishes the database connection as well as the table for
	 * persisting events. Sub classes overwriting this method thus have to make
	 * the super call.
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#initComponent()
	 */
	protected void initComponent() {
		
		//	get database connection
		this.io = this.host.getIoProvider();
		if (!this.io.isJdbcAvailable())
			throw new RuntimeException(this.getEventProcessorName() + " cannot work without database access.");
		
		//	ensure data table
		TableDefinition td = new TableDefinition(EVENT_TABLE_NAME);
		td.addColumn(DATA_ID_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
		td.addColumn(DATA_ID_HASH_COLUMN_NAME, TableDefinition.INT_DATATYPE, 0);
		td.addColumn(TIMESTAMP_COLUMN_NAME, TableDefinition.BIGINT_DATATYPE, 1);
		td.addColumn(USER_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
		td.addColumn(TYPE_COLUMN_NAME, TableDefinition.CHAR_DATATYPE, 1);
		td.addColumn(PRIORITY_COLUMN_NAME, TableDefinition.CHAR_DATATYPE, 1);
		td.addColumn(PARAMS_COLUMN_NAME, TableDefinition.BIGINT_DATATYPE, 0);
		if (!this.io.ensureTable(td, true))
			throw new RuntimeException(this.getEventProcessorName() + " cannot work without database access.");
		
		//	add indexes
		this.io.indexColumn(EVENT_TABLE_NAME, DATA_ID_COLUMN_NAME);
		this.io.indexColumn(EVENT_TABLE_NAME, DATA_ID_HASH_COLUMN_NAME);
	}
	
	/**
	 * This implementation restores any pending events from the database and
	 * afterward starts the event handling thread. Sub classes overwriting this
	 * method thus have to make the super call.
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#linkInit()
	 */
	public void linkInit() {
		
		//	restore events from database (no need for synchronizing just yet, as we're starting event handler only below)
		String loadQuery = "SELECT " + DATA_ID_COLUMN_NAME + ", " + TIMESTAMP_COLUMN_NAME + ", " + USER_COLUMN_NAME + ", " + TYPE_COLUMN_NAME + ", " + PRIORITY_COLUMN_NAME + ", " + PRIORITY_COLUMN_NAME + 
				" FROM " + EVENT_TABLE_NAME +
				" ORDER BY " + TIMESTAMP_COLUMN_NAME +
				";";
		SqlQueryResult sqr = null;
		try {
			sqr = this.io.executeSelectQuery(loadQuery);
			while (sqr.next()) {
				UpdateEvent ue = new UpdateEvent(sqr.getString(0), sqr.getLong(1), (NULL_USER_NAME.equals(sqr.getString(2)) ? null : sqr.getString(2)), sqr.getString(3).charAt(0), sqr.getString(4).charAt(0), sqr.getLong(5));
				ue.persistStatus = UpdateEvent.PERSIST_STATUS_PERSISTED; // we don't want to persist this one again
				this.eventQueue.enqueue(ue);
			}
		}
		catch (SQLException sqle) {
			System.out.println(this.getEventProcessorName() + ": " + sqle.getMessage() + " while restoring events.");
			System.out.println("  query was " + loadQuery);
		}
		finally {
			if (sqr != null)
				sqr.close();
		}
		
		//	start event handler thread
		this.startEventHandler();
		System.out.println(this.getEventProcessorName() + ": event handler started");
	}
	
	boolean startEventHandler() {
		if ((this.eventHandler != null) && this.eventHandler.isAlive())
			return false;
		if (this.eventQueueMonitor != null)
			this.eventQueueMonitor.dispose();
		this.eventHandler = new UpdateEventHandler(this.getEventProcessorName() + "EventHandler");
		this.eventHandler.start();
		this.eventQueueMonitor = new AsynchronousWorkQueue(this.getEventProcessorName()) {
			public String getStatus() {
				String eventQueueStatus = (GoldenGateAEP.this.eventQueue.size() + " update events pending (" + GoldenGateAEP.this.eventQueue.highPriorityQueue.size() + "/" + GoldenGateAEP.this.eventQueue.normPriorityQueue.size() + "/" + GoldenGateAEP.this.eventQueue.lowPriorityQueue.size() + ")");
				String eventProcessorStatus;
				if (GoldenGateAEP.this.eventHandler.eventStart != -1)
					eventProcessorStatus = ("working since " + (System.currentTimeMillis() - GoldenGateAEP.this.eventHandler.eventStart) + "ms");
				else if (GoldenGateAEP.this.eventHandler.sleepStart != -1) {
					long time = System.currentTimeMillis();
					eventProcessorStatus = ("sleeping since " + (time - GoldenGateAEP.this.eventHandler.sleepStart) + "ms");
					if (time < GoldenGateAEP.this.eventHandler.sleepEnd)
						eventProcessorStatus += (", for another " + (GoldenGateAEP.this.eventHandler.sleepEnd - time) + "ms");
				}
				else if (GoldenGateAEP.this.eventHandler.eventEnd != -1)
					eventProcessorStatus = ("last event finished " + (System.currentTimeMillis() - GoldenGateAEP.this.eventHandler.eventEnd) + "ms ago");
				else eventProcessorStatus = null;
				return (this.name + ": " + eventQueueStatus + ((GoldenGateAEP.this.eventHandler == flushingEventHandler) ? ", FLUSHING" : "") + ((eventProcessorStatus == null) ? "" : (", " + eventProcessorStatus)));
			}
		};
		return true;
	}
	
	/**
	 * This method shuts down asynchronous event handling. Sub classes
	 * overwriting this method thus have to make the super call.
	 * @see de.goldenGateScf.AbstractServerComponent#exitComponent()
	 */
	protected void exitComponent() {
		
		//	shut down event handle
		if (this.eventQueueMonitor != null)
			this.eventQueueMonitor.dispose();
		if (this.eventHandler != null)
			this.eventHandler.shutdown();
		System.out.println(this.getEventProcessorName() + ": event handler shut down");
		
		//	disconnect from database
		this.io.close();
	}
	
	private static final String QUEUE_SIZE_COMMAND = "queueSize";
	private static final String FLUSH_QUEUE_COMMAND = "flushQueue";
	private static final String FLUSH_STOP_COMMAND = "flushStop";
	private static final String WAKE_UP_COMMAND = "wakeUp";
	private static final String CLEAR_QUEUE_COMMAND = "clearQueue";
	private static final String ENQUEUE_UPDATE_COMMAND = "enqueueUpdate";
	private static final String ENQUEUE_DELETION_COMMAND = "enqueueDelete";
	private static final String DUMP_STACK_COMMAND = "dumpStack";
	private static final String PERSIST_QUEUE_COMMAND = "persistQueue";
	
	//	TODO make commands public
	
	//	TODO provide getActions() method taking mapping of generic default commands to custom names and explanations
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent#getActions()
	 */
	public ComponentAction[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentAction ca;
		
		//	indicate current size of update queue
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return QUEUE_SIZE_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						QUEUE_SIZE_COMMAND,
						"Show current size of event queue, i.e., number of pending updates."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length != 0)
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
				else this.reportResult(eventQueue.size() + " update events pending (" + eventQueue.highPriorityQueue.size() + "/" + eventQueue.normPriorityQueue.size() + "/" + eventQueue.lowPriorityQueue.size() + ")");
			}
		};
		cal.add(ca);
		
		//	put event handler in flushing mode
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return FLUSH_QUEUE_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						FLUSH_QUEUE_COMMAND,
						"Work off all pending updates without waiting in between (use with care)."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length != 0)
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
				else if (eventHandler != null)
					eventHandler.setFlushing(true, this);
			}
		};
		cal.add(ca);
		
		//	put event handler out of flushing mode
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return FLUSH_STOP_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						FLUSH_STOP_COMMAND,
						"Swith from flushing mode back to normal (re-activate waiting)."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length != 0)
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
				else if (eventHandler != null)
					eventHandler.setFlushing(false, this);
			}
		};
		cal.add(ca);
		
		//	wake event handler up from sleeping after finishing an event
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return WAKE_UP_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						WAKE_UP_COMMAND,
						"Wake the event handler up from sleeping after processing an event."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length != 0)
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
				else if (eventHandler != null)
					eventHandler.wakeUp(true);
			}
		};
		cal.add(ca);
		
		//	clear event queue
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return CLEAR_QUEUE_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						CLEAR_QUEUE_COMMAND + " <mode>:",
						"Discard all pending update events",
						"- <mode>: set to '-n' or '-h' to also clear normal and both normal and high priority queue, respectively (optional)."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length < 2) {
					char clearPriority = PRIORITY_LOW;
					if (arguments.length == 1) {
						if (arguments[0].matches("-[0-9]"))
							clearPriority = arguments[0].charAt(1);
						else if ("-h".equals(arguments[0]))
							clearPriority = PRIORITY_HIGH;
						else if ("-n".equals(arguments[0]))
							clearPriority = PRIORITY_NORMAL;
					}
					synchronized (eventQueue) {
						eventQueue.clear(clearPriority);
					}
					String deleteQuery = "DELETE FROM " + EVENT_TABLE_NAME +
							" WHERE " + PRIORITY_COLUMN_NAME + " <= '" + clearPriority + "'" +
							";";
					try {
						io.executeUpdateQuery(deleteQuery);
					}
					catch (SQLException sqle) {
						this.reportError(getEventProcessorName() + ": " + sqle.getMessage() + " while clearing persisted events.");
						this.reportError("  query was " + deleteQuery);
					}
					this.reportResult("Update event queue cleared, " + eventQueue.size() + " update events remain pending.");
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify at most the mode argument.");
			}
		};
		cal.add(ca);
		
		//	offer 'enqueueUpdate <dataId> <priority>?'
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return ENQUEUE_UPDATE_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						ENQUEUE_UPDATE_COMMAND + " <dataId> <user> <priority>",
						"Enqueue an update event for a data object with a given ID:",
						"- <dataId>: the ID of the data object to enqueue an update for",
						"- <user>: the user responsible for the event (optional)",
						"- <priority>: set to '-n' or '-h' to enqueue a normal or high-priority event, respectively (optional)"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0)
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify at least the data ID argument.");
				else if (arguments.length < 4) {
					String user = null;
					char priority = PRIORITY_LOW;
					if (arguments.length >= 2) {
						if ("-h".equals(arguments[1]) || "-n".equals(arguments[1]))
							priority = readPriorityArgument(arguments[1]);
						else {
							user = arguments[1];
							if (arguments.length == 3)
								priority = readPriorityArgument(arguments[2]);
						}
					}
					dataUpdated(arguments[0], false, user, priority);
					this.reportResult("Update event enqueued for data object " + arguments[0] + ".");
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify data ID, user, and priority only.");
			}
		};
		cal.add(ca);
		
		//	offer 'enqueueUpdate <dataId> <priority>?'
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return ENQUEUE_DELETION_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						ENQUEUE_DELETION_COMMAND + " <dataId> <user> <priority>",
						"Enqueue a deletion event for a data object with a given ID:",
						"- <dataId>: the ID of the data object to enqueue an deletion for",
						"- <user>: the user responsible for the event (optional)",
						"- <priority>: set to '-n' or '-h' to enqueue a normal or high-priority event, respectively (optional)"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0)
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify at least the data ID argument.");
				else if (arguments.length < 4) {
					String user = null;
					char priority = PRIORITY_LOW;
					if (arguments.length >= 2) {
						if ("-h".equals(arguments[1]) || "-n".equals(arguments[1]))
							priority = readPriorityArgument(arguments[1]);
						else {
							user = arguments[1];
							if (arguments.length == 3)
								priority = readPriorityArgument(arguments[2]);
						}
					}
					dataDeleted(arguments[0], user, priority);
					this.reportResult("Deletion event enqueued for data object " + arguments[0] + ".");
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify data ID and priority only.");
			}
		};
		cal.add(ca);
		
		//	offer function dumpStack printing stack trace of event processor (to help investigating hang-ups)
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return DUMP_STACK_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						DUMP_STACK_COMMAND,
						"Dump the current stack of the event processing thread (helps investigate any hang-ups)."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length != 0)
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
				else if (eventHandler != null) {
					StackTraceElement[] stes = eventHandler.getStackTrace();
					this.reportResult(eventHandler.getName() + ":");
					for (int e = 0; e < stes.length; e++)
						this.reportResult("  at " + stes[e].toString());
				}
			}
		};
		cal.add(ca);
		
		//	offer function persistQueue to persist event queue in case of event processor hang-up
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return PERSIST_QUEUE_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						PERSIST_QUEUE_COMMAND,
						"Persist any pending events not persisted previously."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length != 0)
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
				else if (eventHandler != null) {
					ArrayList pes = null;
					synchronized (eventQueue) {
						if (eventQueue.size() != 0)
							pes = eventQueue.getPersistEvents();
					}
					persistQueuedEvents(pes, true);
				}
			}
		};
		cal.add(ca);
		
		//	finally ...
		return ((ComponentAction[]) cal.toArray(new ComponentAction[cal.size()]));
	}
	
	private static char readPriorityArgument(String arg) {
		if ("-h".equals(arg))
			return PRIORITY_HIGH;
		else if ("-n".equals(arg))
			return PRIORITY_NORMAL;
		else return PRIORITY_LOW;
	}
	
	/**
	 * Notify this class that a data object has been updated. This method enqueues
	 * a normal priority update event by default. However, if the update is the
	 * result of another update event, the new event inherits the priority of
	 * the latter. Actual listening for update events is up to sub classes.
	 * @param dataId the ID of the data object that was updated
	 * @param isNewData was the data object with the argument ID just created?
	 * @param user the user responsible for the update
	 * @return the position at which the update event is enqueued
	 */
	protected int dataUpdated(String dataId, boolean isNewData, String user) {
		return this.dataUpdated(dataId, isNewData, user, getInheritedPriority(), 0);
	}
	
	/**
	 * Notify this class that a data object has been updated. This method enqueues
	 * a normal priority update event by default. However, if the update is the
	 * result of another update event, the new event inherits the priority of
	 * the latter. Actual listening for update events is up to sub classes.
	 * @param dataId the ID of the data object that was updated
	 * @param isNewData was the data object with the argument ID just created?
	 * @param user the user responsible for the update
	 * @param params a bit vector bundling implementation specific event
	 *            processing parameters
	 * @return the position at which the update event is enqueued
	 */
	protected int dataUpdated(String dataId, boolean isNewData, String user, long params) {
		return this.dataUpdated(dataId, isNewData, user, getInheritedPriority(), params);
	}
	
	/**
	 * Notify this class that a data object has been updated. This method enqueues
	 * an update event. Actual listening for update events is up to sub classes.
	 * @param dataId the ID of the data object that was updated
	 * @param isNewData was the data object with the argument ID just created?
	 * @param user the user responsible for the update
	 * @param priority the priority of the update
	 * @return the position at which the update event is enqueued
	 */
	protected int dataUpdated(String dataId, boolean isNewData, String user, char priority) {
		return this.dataUpdated(dataId, isNewData, user, priority, 0);
	}
	
	/**
	 * Notify this class that a data object has been updated. This method enqueues
	 * an update event. Actual listening for update events is up to sub classes.
	 * @param dataId the ID of the data object that was updated
	 * @param isNewData was the data object with the argument ID just created?
	 * @param user the user responsible for the update
	 * @param priority the priority of the update
	 * @param params a bit vector bundling implementation specific event
	 *            processing parameters
	 * @return the position at which the update event is enqueued
	 */
	protected int dataUpdated(String dataId, boolean isNewData, String user, char priority, long params) {
		return this.enqueueEvent(new UpdateEvent(dataId, user, (isNewData ? UpdateEvent.TYPE_CREATE : UpdateEvent.TYPE_UPDATE), priority, params));
	}
	
	/**
	 * Notify this class that a data object has been deleted. This method enqueues
	 * a normal priority deletion event by default. However, if the deletion is
	 * the result of another update event, the new event inherits the priority
	 * of the latter. Actual listening for deletion events is up to sub classes.
	 * @param dataId the ID of the data object that was deleted
	 * @param user the user responsible for the deletion
	 * @return the position at which the deletion event is enqueued
	 */
	protected int dataDeleted(String dataId, String user) {
		return this.dataDeleted(dataId, user, getInheritedPriority(), 0);
	}
	
	/**
	 * Notify this class that a data object has been deleted. This method enqueues
	 * a normal priority deletion event by default. However, if the deletion is
	 * the result of another update event, the new event inherits the priority
	 * of the latter. Actual listening for deletion events is up to sub classes.
	 * @param dataId the ID of the data object that was deleted
	 * @param user the user responsible for the deletion
	 * @return the position at which the deletion event is enqueued
	 */
	protected int dataDeleted(String dataId, String user, long params) {
		return this.dataDeleted(dataId, user, getInheritedPriority(), params);
	}
	
	/**
	 * Notify this class that a data object has been deleted. This method enqueues
	 * a deletion event. Actual listening for deletion events is up to sub classes.
	 * @param dataId the ID of the data object that was deleted
	 * @param user the user responsible for the deletion
	 * @param priority the priority of the deletion
	 * @return the position at which the deletion event is enqueued
	 */
	protected int dataDeleted(String dataId, String user, char priority) {
		return this.dataDeleted(dataId, user, priority, 0);
	}
	
	/**
	 * Notify this class that a data object has been deleted. This method enqueues
	 * a deletion event. Actual listening for deletion events is up to sub classes.
	 * @param dataId the ID of the data object that was deleted
	 * @param user the user responsible for the deletion
	 * @param priority the priority of the deletion
	 * @return the position at which the deletion event is enqueued
	 */
	protected int dataDeleted(String dataId, String user, char priority, long params) {
		return this.enqueueEvent(new UpdateEvent(dataId, user, UpdateEvent.TYPE_DELETE, priority, params));
	}
	
	private static char getInheritedPriority() {
		Thread t = Thread.currentThread();
		if (t instanceof UpdateEventHandler)
			return ((UpdateEventHandler) t).eventPriority;
		else return PRIORITY_NORMAL;
	}
	
	/**
	 * Load sub class specific attributes of a data object. The attributes
	 * provided by this method are the ones handed to the
	 * <code>doUpdate()</code> and <code>doDelete()</code> methods. This
	 * default implementation returns an empty object, sub classes are welcome
	 * to overwrite it as needed.
	 * @param dataId the ID of the data object
	 * @return the attributes of the data object
	 */
	protected Properties loadDataAttributes(String dataId) {
		return new Properties();
	}
	
	private static class UpdateEvent {
		static final char TYPE_CREATE = 'C';
		static final char TYPE_UPDATE = 'U';
		static final char TYPE_DELETE = 'D';
		
		static final char STATUS_QUEUED = 'Q';
		static final char STATUS_PROCESING = 'P';
		static final char STATUS_DONE = 'D';
		static final char STATUS_INVALID = 'I';
		
		static final char PERSIST_STATUS_NEW = 'N';
		static final char PERSIST_STATUS_PERSISTED = 'P';
		static final char PERSIST_STATUS_INVALID = 'I';
		static final char PERSIST_STATUS_UPDATE = 'U';
		static final char PERSIST_STATUS_CLEANUP = 'C';
		
		final String dataId;
		final DataAttributes dataAttributes = new DataAttributes(null);
		final long timestamp;
		final String user;
		final char type;
		final char priority;
		final long params;
		char status = STATUS_QUEUED;
		char persistStatus = PERSIST_STATUS_NEW;
		
		UpdateEvent(String dataId, String user, char type, char priority, long params) {
			this(dataId, System.currentTimeMillis(), user, type, priority, params);
		}
		UpdateEvent(String dataId, long timestamp, String user, char type, char priority, long params) {
			this.dataId = dataId;
			this.timestamp = timestamp;
			this.user = user;
			this.type = type;
			this.priority = priority;
			this.params = params;
		}
		
		boolean isCreation() {
			return (this.type == TYPE_CREATE);
		}
		boolean isDeletion() {
			return (this.type == TYPE_DELETE);
		}
	}
	
	private class UpdateEventQueue {
		private UpdateEventBuffer highPriorityQueue = new UpdateEventBuffer(8);
		private UpdateEventBuffer normPriorityQueue = new UpdateEventBuffer(16);
		private UpdateEventBuffer lowPriorityQueue = new UpdateEventBuffer(8);
		private HashMap eventsByDataId = new HashMap();
		private LinkedList persistQueue = new LinkedList();
		
		int enqueue(UpdateEvent ue) {
			
			//	get existing event (for aggregation and priority escalation)
			UpdateEvent exUe = ((UpdateEvent) this.eventsByDataId.get(ue.dataId));
			
			//	no existing event, enqueue new one for persisting (unless just restored from database)
			if (exUe == null) {
				if (ue.persistStatus == UpdateEvent.PERSIST_STATUS_NEW)
					this.persistQueue.add(ue);
			}
			
			//	deletion cancels creation and update
			else if (ue.isDeletion()) {
				
				//	deletion and existing creation ==> clean up altogether
				if (exUe.isCreation()) {
					this.eventsByDataId.remove(exUe.dataId);
					this.markForCleanup(exUe);
					
					//	enqueue cleanup if cancelled creation already persisted, prevent persisting otherwise
					if (exUe.persistStatus == UpdateEvent.PERSIST_STATUS_PERSISTED) {
						ue.persistStatus = UpdateEvent.PERSIST_STATUS_CLEANUP;
						this.persistQueue.add(ue);
					}
					else exUe.persistStatus = UpdateEvent.PERSIST_STATUS_INVALID;
					
					//	we're not enqueuing any events for processing
					return -1;
				}
				
				//	we have an existing deletion (might have been enqueued by batch) ==> escalate priority if applicable
				else if (exUe.isDeletion()) {
					if (exUe.priority < ue.priority) {
						
						//	escalate priority (retain original timestamp and event type, but use latest update user on two deletions)
						ue = new UpdateEvent(exUe.dataId, exUe.timestamp, ((exUe.isCreation() && (exUe.user != null)) ? exUe.user : ue.user), exUe.type, ue.priority, aggregateEventParams(exUe.params, ue.params));
						this.markForCleanup(exUe);
						
						//	enqueue update if existing event already persisted, prevent persisting it and stick with insertion otherwise
						if (exUe.persistStatus == UpdateEvent.PERSIST_STATUS_PERSISTED)
							ue.persistStatus = UpdateEvent.PERSIST_STATUS_UPDATE;
						else exUe.persistStatus = UpdateEvent.PERSIST_STATUS_INVALID;
						this.persistQueue.add(ue);
					}
				}
				
				//	deletion and existing update ==> move up deletion, using higher priority
				else {
					
					//	move up deletion
					ue = new UpdateEvent(exUe.dataId, exUe.timestamp, ((ue.user == null) ? exUe.user : ue.user), ue.type, ((char) Math.max(exUe.priority, ue.priority)), aggregateEventParams(exUe.params, ue.params));
					this.markForCleanup(exUe);
					
					//	enqueue update if existing event already persisted, prevent persisting it and stick with insertion otherwise
					if (exUe.persistStatus == UpdateEvent.PERSIST_STATUS_PERSISTED)
						ue.persistStatus = UpdateEvent.PERSIST_STATUS_UPDATE;
					else exUe.persistStatus = UpdateEvent.PERSIST_STATUS_INVALID;
					this.persistQueue.add(ue);
				}
			}
			
			//	update and existing creation or update ==> escalate priority if applicable
			else if (exUe.priority < ue.priority) {
				
				//	escalate priority (retain original timestamp and event type, but use latest update user on two updates)
				ue = new UpdateEvent(exUe.dataId, exUe.timestamp, ((exUe.isCreation() && (exUe.user != null)) ? exUe.user : ue.user), exUe.type, ue.priority, aggregateEventParams(exUe.params, ue.params));
				this.markForCleanup(exUe);
				
				//	enqueue update if existing event already persisted, prevent persisting it and stick with insertion otherwise
				if (exUe.persistStatus == UpdateEvent.PERSIST_STATUS_PERSISTED)
					ue.persistStatus = UpdateEvent.PERSIST_STATUS_UPDATE;
				else exUe.persistStatus = UpdateEvent.PERSIST_STATUS_INVALID;
				this.persistQueue.add(ue);
			}
			
			//	nothing to do at all (subsequent update events for same data object without priority escalation)
			else return -1;
			
			//	enqueue event according to priority
			int uePos;
			if (ue.priority >= PRIORITY_HIGH)
				uePos = this.highPriorityQueue.addLast(ue, (exUe == null));
			else if (ue.priority >= PRIORITY_NORMAL) {
				uePos = this.normPriorityQueue.addLast(ue, (exUe == null));
				uePos += this.highPriorityQueue.size();
			}
			else {
				uePos = this.lowPriorityQueue.addLast(ue, (exUe == null));
				uePos += this.highPriorityQueue.size();
				uePos += this.normPriorityQueue.size();
			}
			
			//	register event and indicate enqueuing position
			this.eventsByDataId.put(ue.dataId, ue);
			return uePos;
		}
		
		UpdateEvent dequeue() {
			
			//	run cleanup (at expense of event processor, not event creating thread, and thus here)
			this.highPriorityQueue.runCleanup();
			this.normPriorityQueue.runCleanup();
			this.lowPriorityQueue.runCleanup();
			
			//	retrieve event of highest priority available
			UpdateEvent ue;
			if (this.highPriorityQueue.size() != 0)
				ue = ((UpdateEvent) this.highPriorityQueue.removeFirst());
			else if (this.normPriorityQueue.size() != 0)
				ue = ((UpdateEvent) this.normPriorityQueue.removeFirst());
			else ue = ((UpdateEvent) this.lowPriorityQueue.removeFirst());
			
			//	unregister and return event
			this.eventsByDataId.remove(ue.dataId);
			return ue;
		}
		
		void markForCleanup(UpdateEvent ue) {
			ue.status = UpdateEvent.STATUS_INVALID;
			if (ue.priority >= PRIORITY_HIGH)
				this.highPriorityQueue.markForCleanup();
			else if (ue.priority >= PRIORITY_NORMAL)
				this.normPriorityQueue.markForCleanup();
			else this.lowPriorityQueue.markForCleanup();
		}
		
		void clear(char maxPriority) {
			if (maxPriority >= PRIORITY_HIGH)
				this.highPriorityQueue.clear();
			if (maxPriority >= PRIORITY_NORMAL)
				this.normPriorityQueue.clear();
			this.lowPriorityQueue.clear();
			for (Iterator diit = this.eventsByDataId.keySet().iterator(); diit.hasNext();) {
				String dataId = ((String) diit.next());
				UpdateEvent ue = ((UpdateEvent) this.eventsByDataId.get(dataId));
				if (ue.status == UpdateEvent.STATUS_INVALID)
					diit.remove();
			}
		}
		
		ArrayList getPersistEvents() {
			ArrayList pes = null;;
			for (Iterator peit = this.persistQueue.iterator(); peit.hasNext();) {
				UpdateEvent ue = ((UpdateEvent) peit.next());
				if (ue.persistStatus == UpdateEvent.PERSIST_STATUS_PERSISTED)
					peit.remove();
				else if (ue.persistStatus == UpdateEvent.PERSIST_STATUS_INVALID)
					peit.remove();
				if (pes == null)
					pes = new ArrayList();
				pes.add(ue);
			}
			return pes;
		}
		
		int size() {
			return this.eventsByDataId.size();
		}
		
		private class UpdateEventBuffer {
			private UpdateEvent[] events;
			private int first = 0;
			private int last = 0;
			private boolean eventsClean = true;
			
			UpdateEventBuffer(int capacity) {
				this.events = new UpdateEvent[capacity];
			}

			UpdateEvent removeFirst() {
				
				//	anything to return?
				if (this.first == this.last)
					throw new NoSuchElementException("The event buffer is empty.");
				
				//	get and clear first event, and switch to next one
				UpdateEvent ue = this.events[this.first];
				this.events[this.first] = null;
				this.first++;
				
				//	reset to start of array if possible
				if (this.first == this.last) {
					this.first = 0;
					this.last = 0;
				}
				
				//	finally ...
				return ue;
			}
			
			int addLast(UpdateEvent ue, boolean inOrder) {
				
				//	get insert position
				int uePos;
				
				//	make sure we have enough room
				if (this.last == this.events.length) {
					if (this.first == 0) { // we're aligned at 0, need to increase array
						UpdateEvent[] es = new UpdateEvent[this.events.length * 2];
						System.arraycopy(this.events, 0, es, 0, this.events.length);
						this.events = es;
					}
					else { // shift content to 0
						int size = this.size();
						System.arraycopy(this.events, this.first, this.events, 0, size);
						this.first = 0;
						this.last = size;
					}
				}
				
				//	store new event and move out end pointer
				uePos = this.size();
				this.events[this.last++] = ue;
				
				//	nothing to sort, and never on single element
				if (inOrder || (this.size() < 2))
					return uePos; // enqueued at very end
				
				//	bubble sort in new event if requested (happens on priority escalation)
				UpdateEvent tue = null;
				for (int e = (this.last-1); e > this.first; e--) {
					if (this.events[e].timestamp < this.events[e-1].timestamp) {
						tue = this.events[e-1];
						this.events[e-1] = this.events[e];
						this.events[e] = tue;
						uePos--;
					}
					else break;
				}
				
				//	finally ...
				return uePos;
			}
			
			int size() {
				return (this.last - this.first);
			}
			
			void clear() {
				for (int e = this.first; e < this.last; e++) {
					this.events[e].status = UpdateEvent.STATUS_INVALID;
					this.events[e].persistStatus = UpdateEvent.PERSIST_STATUS_INVALID;
				}
				Arrays.fill(this.events, this.first, this.last, null);
				this.first = 0;
				this.last = 0;
			}
			
			void markForCleanup() {
				this.eventsClean = false;
			}
			
			void runCleanup() {
				if (this.eventsClean)
					return;
				for (int e = this.first; e < this.last; e++)
					if (this.events[e].status == UpdateEvent.STATUS_INVALID) {
						this.events[e].persistStatus = UpdateEvent.PERSIST_STATUS_INVALID;
						System.arraycopy(this.events, (e+1), this.events, e, (this.last - (e+1)));
						this.events[this.last-1] = null;
						this.last--;
						e--;
					}
				this.eventsClean = true;
			}
		}
	}
	
	private static class DataAttributes extends Properties {
		DataAttributes(Properties defaults) {
			super(defaults);
		}
		void setDefaults(Properties defaults) {
			this.defaults = defaults;
		}
		Properties getDefaults() {
			return this.defaults;
		}
	}
	
	private int enqueueEvent(UpdateEvent event) {
		
		//	enqueue event and wake up handler
		synchronized (this.eventQueue) {
			
			//	enqueue event for asynchronous handling, potentially modifying it in combination with existing event for same data object
			int eventPosition = this.eventQueue.enqueue(event);
			
			//	only wake up event handler if we actually enqueued some event
			if (eventPosition != -1)
				this.eventQueue.notify();
			
			//	report back queue position
			return eventPosition;
		}
	}
	
	private UpdateEventQueue eventQueue = new UpdateEventQueue();
	private UpdateEventHandler eventHandler;
	private AsynchronousWorkQueue eventQueueMonitor;
	
	private class UpdateEventHandler extends Thread {
		private boolean running = true;
		private boolean flushing = false;
		char eventPriority = ((char) 0);
		long eventStart = -1;
		long eventEnd = -1;
		private final Object sleepLock = new Object();
		long sleepStart = -1;
		long sleepEnd = -1;
		UpdateEventHandler(String name) {
			super(name);
		}
		public void run() {
			
			//	wait a little before starting to work
			try {
				Thread.sleep(10000);
			} catch (InterruptedException ie) {}
			
			//	run indefinitely
			while (this.running) {
				
				//	check for global pausing
				this.checkPause();
				
				//	have we been globally un-paused by a shutdown interrupt?
				if (!this.running)
					return;
				
				//	get next event, or wait until next event available
				UpdateEvent ue = null;
				ArrayList pes = null;
				synchronized (eventQueue) {
					if (eventQueue.size() == 0) try {
						eventQueue.wait();
					} catch (InterruptedException ie) {}
					if (eventQueue.size() != 0) {
						ue = eventQueue.dequeue();
						ue.status = UpdateEvent.STATUS_PROCESING;
						pes = eventQueue.getPersistEvents();
					}
				}
				
				//	persist any non-persisted events still in queue (keeps any persisting delays in this thread)
				persistQueuedEvents(pes, persistProcessingEvents());
				
				//	go out of flushing mode once queue is empty
				if (this.flushing && (eventQueue.size() == 0))
					setFlushingEventHandler(this, false); 
				
				//	keep track of resource use
				long eventProcessingTime;
				
				//	nothing to do
				if (ue == null)
					eventProcessingTime = 0;
				
				//	got event to process
				else {
					long eventProcessingStart = System.currentTimeMillis();
					logInfo(getEventProcessorName() + ": got " + (ue.isDeletion() ? "delete" : "update") + " event for object '" + ue.dataId + "'");
					this.eventPriority = ue.priority;
					this.eventStart = eventProcessingStart;
					this.eventEnd = -1;
					try {
						
						//	load data attributes if not done before
						if (ue.dataAttributes.getDefaults() == null)
							ue.dataAttributes.setDefaults(loadDataAttributes(ue.dataId));
						
						//	deletion
						if (ue.isDeletion())
							doDelete(ue.dataId, ue.user, ue.dataAttributes, ue.params);
						
						//	update
						else {
							if (ue.isCreation())
								ue.dataAttributes.setProperty(IS_NEW_OBJECT_ATTRIBUTE, "true");
							doUpdate(ue.dataId, ue.user, ue.dataAttributes, ue.params);
						}
					}
					catch (Exception e) {
						logError(getEventProcessorName() + ": Error handling update event for object '" + ue.dataId + "' - " + e.getMessage());
						logError(e);
					}
					catch (Throwable t) {
						logError(getEventProcessorName() + ": Error handling update event for object '" + ue.dataId + "' - " + t.getMessage());
						logError(t);
					}
					finally {
						long eventProcessingEnd = System.currentTimeMillis();
						eventProcessingTime = (eventProcessingEnd - eventProcessingStart);
						logInfo("  - event processed in " + eventProcessingTime + "ms");
						
						//	clean up event after processing (if persisted)
						ue.status = UpdateEvent.STATUS_DONE;
						if (ue.persistStatus == UpdateEvent.PERSIST_STATUS_PERSISTED)
							cleanupPersistedEvent(ue);
						
						//	clear thread local event priority
						this.eventPriority = ((char) 0);
						this.eventStart = -1;
						this.eventEnd = eventProcessingEnd;
					}
				}
				
				//	return right away if we have a shutdown
				if (!this.running)
					return;
				
				//	go straight to next event if we're flushing
				if (this.flushing)
					continue;
				
				//	compute sleeping time, dependent on number of event processors and activity (time spent on actual event processing)
				long sleepTime = (0 + 
						250 + // base sleep
						(50 * instanceCount) + // a little extra for every instance
						eventProcessingTime + // the time we just occupied the CPU or other resources
						0);
				logInfo(getEventProcessorName() + ": sleeping for " + sleepTime + "ms");
				this.sleepStart = this.eventEnd;
				this.sleepEnd = (this.sleepStart + sleepTime);
				
				//	give the others a little time
				while (this.running && (sleepTime > 0)) try {
					synchronized (this.sleepLock) {
						this.sleepLock.wait(sleepTime);
					}
					break; // we've been woken up by regular means (sleep time over or wake-up command) rather than an exception
				}
				catch (InterruptedException ie) {
					sleepTime = (this.sleepEnd - System.currentTimeMillis());
				}
				this.sleepStart = -1;
				this.sleepEnd = -1;
//				
//				//	give the others a little time (and the export target as well), dependent on number of event processors and activity (time spent on actual exports)
//				if (!this.flushing && this.running) try {
//					long sleepTime = (0 + 
//							250 + // base sleep
//							(50 * instanceCount) + // a little extra for every instance
//							eventProcessingTime + // the time we just occupied the CPU or other resources
//							0);
//					logInfo(getEventProcessorName() + ": sleeping for " + sleepTime + "ms");
//					Thread.sleep(sleepTime);
//				} catch (InterruptedException ie) {}
			}
		}
		
		private void checkPause() {
			if (!aepPause)
				return;
			synchronized (aepPauseLock) {
				aepPausedInstances.add(this);
				logInfo(this.getName() + " pausing");
				try {
					aepPauseLock.wait();
				} catch (InterruptedException ie) {}
				logInfo(this.getName() + " un-paused");
				aepPausedInstances.remove(this);
			}
		}
		
		void shutdown() {
			synchronized (eventQueue) {
				this.running = false;
				eventQueue.clear(PRIORITY_HIGH);
				eventQueue.notify();
				this.interrupt(); // end any post export or global pause waiting immediately
			}
		}
		
		void setFlushing(boolean flushing, ComponentActionConsole cac) {
			if (flushing == this.flushing) {
				if (flushing)
					cac.reportError("Already in flushing mode.");
				else cac.reportError("Not in flushing mode.");
			}
			else if (setFlushingEventHandler(this, flushing)) {
				if (flushing) {
					cac.reportResult("Flushing mode activated.");
					this.wakeUp(false);
				}
				else cac.reportResult("Flushing mode deactivated.");
			}
			else if (flushing)
				cac.reportError("Could not activate flushing mode, only one flushing instance allowed at a time.");
			else cac.reportError("Could not interrupt flushing instance.");
		}
		
		void wakeUp(boolean unlessFlushing) {
			if (unlessFlushing && this.flushing)
				return; // not sleeping anyway
			if (this.sleepStart == -1)
				return;
			synchronized (this.sleepLock) {
				this.sleepLock.notify();
			}
		}
	}
	
	private void persistQueuedEvents(ArrayList pes, boolean persistProcessing) {
		if (pes == null)
			return;
		LinkedList ies = ((pes.size() < 5) ? null : new LinkedList()); // TODO tune threshold
		for (int e = 0; e < pes.size(); e++) {
			UpdateEvent ue = ((UpdateEvent) pes.get(e));
			if (ue.status == UpdateEvent.STATUS_QUEUED)
				this.persistQueuedEvent(ue, ies);
			else if ((ue.status == UpdateEvent.STATUS_PROCESING) && persistProcessing)
				this.persistQueuedEvent(ue, null);
		}
		if ((ies != null) && (ies.size() != 0))
			this.insertQueuedEvents(ies);
	}
	
	private void persistQueuedEvent(UpdateEvent ue, LinkedList ies) {
		
		//	event is new, persist in new record
		if (ue.persistStatus == UpdateEvent.PERSIST_STATUS_NEW) {
			
			//	small number of updates, persist right away
			if (ies == null) {
				String insertQuery = "INSERT INTO " + EVENT_TABLE_NAME +
						" (" + DATA_ID_COLUMN_NAME + ", " + DATA_ID_HASH_COLUMN_NAME + ", " + TIMESTAMP_COLUMN_NAME + ", " + USER_COLUMN_NAME + ", " + TYPE_COLUMN_NAME + ", " + PRIORITY_COLUMN_NAME + ", " + PARAMS_COLUMN_NAME + ")" +
						" VALUES" +
						" ('" + EasyIO.sqlEscape(ue.dataId) + "', " + ue.dataId.hashCode() + ", " + ue.timestamp + ", '" + EasyIO.sqlEscape((ue.user == null) ? NULL_USER_NAME : ue.user) + "', '" + ue.type + "', '" + ue.priority + "', " + ue.params + ")" +
						";";
				try {
					this.io.executeUpdateQuery(insertQuery);
					ue.persistStatus = UpdateEvent.PERSIST_STATUS_PERSISTED;
				}
				catch (SQLException sqle) {
					this.logError(getEventProcessorName() + ": " + sqle.getMessage() + " while persisting event.");
					this.logError("  query was " + insertQuery);
				}
			}
			
			//	large batch of updates, enqueue event for batch persisting
			else {
				ies.add(ue);
				if (ies.size() >= 16) // TODO tune threshold
					this.insertQueuedEvents(ies);
			}
		}
		
		//	event is update, modify existing event for same data object
		else if (ue.persistStatus == UpdateEvent.PERSIST_STATUS_UPDATE) {
			String updateQuery = "UPDATE " + EVENT_TABLE_NAME + " SET" +
					" " + USER_COLUMN_NAME + " = '" + EasyIO.sqlEscape((ue.user == null) ? NULL_USER_NAME : ue.user) + "'," +
					" " + TYPE_COLUMN_NAME + " = '" + ue.type + "'," +
					" " + PRIORITY_COLUMN_NAME + " = '" + ue.priority + "'," +
					" " + PARAMS_COLUMN_NAME + " = '" + ue.params + "'" +
					" WHERE " + DATA_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(ue.dataId) + "'" +
					" AND " + DATA_ID_HASH_COLUMN_NAME + " = " + ue.dataId.hashCode() +
					" AND " + TIMESTAMP_COLUMN_NAME + " = " + ue.timestamp +
					";";
			try {
				this.io.executeUpdateQuery(updateQuery);
				ue.persistStatus = UpdateEvent.PERSIST_STATUS_PERSISTED;
			}
			catch (SQLException sqle) {
				this.logError(getEventProcessorName() + ": " + sqle.getMessage() + " while updating persisted event.");
				this.logError("  query was " + updateQuery);
			}
		}
		
		//	event is cleanup, delete existing events for same data object
		else if (ue.persistStatus == UpdateEvent.PERSIST_STATUS_CLEANUP) {
			String deleteQuery = "DELETE FROM " + EVENT_TABLE_NAME +
					" WHERE " + DATA_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(ue.dataId) + "'" +
					" AND " + DATA_ID_HASH_COLUMN_NAME + " = " + ue.dataId.hashCode() +
					";";
			try {
				this.io.executeUpdateQuery(deleteQuery);
				ue.persistStatus = UpdateEvent.PERSIST_STATUS_PERSISTED;
			}
			catch (SQLException sqle) {
				this.logError(getEventProcessorName() + ": " + sqle.getMessage() + " while deleting persisted event after cancellation.");
				this.logError("  query was " + deleteQuery);
			}
		}
	}
	
	private void insertQueuedEvents(LinkedList ies) {
		StringBuffer insertQuery = new StringBuffer("INSERT INTO " + EVENT_TABLE_NAME);
		insertQuery.append(" (" + DATA_ID_COLUMN_NAME + ", " + DATA_ID_HASH_COLUMN_NAME + ", " + TIMESTAMP_COLUMN_NAME + ", " + USER_COLUMN_NAME + ", " + TYPE_COLUMN_NAME + ", " + PRIORITY_COLUMN_NAME + ", " + PARAMS_COLUMN_NAME + ")");
		insertQuery.append(" VALUES");
		for (Iterator ieit = ies.iterator(); ieit.hasNext();) {
			UpdateEvent ue = ((UpdateEvent) ieit.next());
			insertQuery.append(" ('" + EasyIO.sqlEscape(ue.dataId) + "', " + ue.dataId.hashCode() + ", " + ue.timestamp + ", '" + EasyIO.sqlEscape((ue.user == null) ? NULL_USER_NAME : ue.user) + "', '" + ue.type + "', '" + ue.priority + "', " + ue.params + ")");
			insertQuery.append(ieit.hasNext() ? "," : ";");
		}
		try {
			this.io.executeUpdateQuery(insertQuery.toString());
			for (Iterator ieit = ies.iterator(); ieit.hasNext();)
				((UpdateEvent) ieit.next()).persistStatus = UpdateEvent.PERSIST_STATUS_PERSISTED;
			ies.clear();
		}
		catch (SQLException sqle) {
			this.logError(getEventProcessorName() + ": " + sqle.getMessage() + " while persisting batch of events.");
			this.logError("  query was " + insertQuery.toString());
		}
	}
	
	private void cleanupPersistedEvent(UpdateEvent ue) {
		String deleteQuery = "DELETE FROM " + EVENT_TABLE_NAME +
				" WHERE " + DATA_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(ue.dataId) + "'" +
				" AND " + DATA_ID_HASH_COLUMN_NAME + " = " + ue.dataId.hashCode() +
				" AND " + TIMESTAMP_COLUMN_NAME + " = " + ue.timestamp +
				";";
		try {
			this.io.executeUpdateQuery(deleteQuery);
		}
		catch (SQLException sqle) {
			this.logError(getEventProcessorName() + ": " + sqle.getMessage() + " while deleting event after processing.");
			this.logError("  query was " + deleteQuery);
		}
	}
	
	/**
	 * Aggregate to bit vectors of implementation specific event parameters for
	 * event aggregation. This default implementation returns the bit-wise OR
	 * of the two argument parameter bit vectors. Sub classes are welcome to
	 * overwrite it with a different behavior.
	 * @param params1 the first parameter bit vector
	 * @param params2 the second parameter bit vector
	 * @return the aggregated parameter bit vector
	 */
	protected long aggregateEventParams(long params1, long params2) {
		return (params1 | params2);
	}
	
	/**
	 * Write an update to the underlying export destination. If this method is
	 * called for the first time for the data object with the argument ID,
	 * the argument <code>dataAttributes</code> object contains
	 * <code>isNewObject</code> as an additional key to indicate so. If the
	 * update event was triggered via the admin console, the <code>user</code>
	 * argument is null. In such cases, client code is advised to substitute
	 * the last actual user to update the data object.
	 * @param dataId the ID of the data object that was updated
	 * @param user the user responsible for the update (null for console
	 *            triggered update events)
	 * @param dataAttributes required attributes of the data object
	 * @param params a bit vector bundling implementation specific event
	 *            processing parameters
	 * @throws Exception
	 */
	protected abstract void doUpdate(String dataId, String user, Properties dataAttributes, long params) throws Exception;
	
	/**
	 * Write a deletion to the underlying export destination. If the
	 * update event was triggered via the admin console, the <code>user</code>
	 * argument is null. In such cases, client code is advised to substitute
	 * the last actual user to update the data object.
	 * @param dataId the ID of the data object that was deleted
	 * @param user the user responsible for the deletion (null for console
	 *            triggered deletion events)
	 * @param dataAttributes required attributes of the data object
	 * @param params a bit vector bundling implementation specific event
	 *            processing parameters
	 * @throws Exception
	 */
	protected abstract void doDelete(String dataId, String user, Properties dataAttributes, long params) throws Exception;
}