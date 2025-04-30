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
package de.uka.ipd.idaho.goldenGateServer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants.GoldenGateServerEvent.GoldenGateServerEventListener;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * The GoldenGATE Server Event Queue is a central publish/subscribe notification
 * service for all sorts of events that occur within a GoldenGATE Server. The
 * server component that actually issued an event can be determined from the
 * sourceClassName in the event.
 * 
 * @author sautter
 */
public class GoldenGateServerEventService implements GoldenGateServerConstants {
	
	private static LinkedHashSet listeners = new LinkedHashSet(2);
	
	/**
	 * Add a listener to the event queue so it receives notification of events
	 * @param gsel the GoldenGateServerEventListener to add
	 */
	public static void addServerEventListener(GoldenGateServerEventListener gsel) {
		if (gsel != null)
			listeners.add(gsel);
	}
	
	/**
	 * Remove a listener from the event queue
	 * @param gsel the GoldenGateServerEventListener to remove
	 */
	public static void removeServerEventListener(GoldenGateServerEventListener gsel) {
		if (gsel != null)
			listeners.remove(gsel);
	}
	
	/*
	 * To avoid cycles in notification of updates, remember for each Thread the
	 * class names of the listeners currently in the process of notification.
	 * This is to make sure that if notification triggers another write access
	 * to the document storage, all the listeners involved in the chain of
	 * invocation of that second write action do not receive notification of the
	 * latter, thus avoiding triggering a second circular invocation.
	 */
//	private static Map notifyingListenersByThreadID = Collections.synchronizedMap(new HashMap());
	private static ThreadLocal notifyingListenersByThreadID = new ThreadLocal();
	private static final boolean DEBUG_NOTIFY = false;
	
	/**
	 * Issue notification of an event.
	 * @param gse the event to issue
	 */
	public static void notify(GoldenGateServerEvent gse) {
		if (DEBUG_NOTIFY) System.out.println("GoldenGateServerEventQueue: issuing notification on " + gse.getClass().getName());
		
		//	get set of listener classes currently in the chain of notification
//		Long threadId = new Long(Thread.currentThread().getId());
//		Set notifyingListeners = ((Set) notifyingListenersByThreadID.get(threadId));
//		if (notifyingListeners == null) {
//			notifyingListeners = Collections.synchronizedSet(new HashSet());
//			notifyingListenersByThreadID.put(threadId, notifyingListeners);
//		}
		Set notifyingListeners = ((Set) notifyingListenersByThreadID.get());
		if (notifyingListeners == null) {
			notifyingListeners = Collections.synchronizedSet(new HashSet());
			notifyingListenersByThreadID.set(notifyingListeners);
		}
		
		//	do we have a specialized notifier thread?
		Thread ct = Thread.currentThread();
		EventNotifierThread ent = ((ct instanceof EventNotifierThread) ? ((EventNotifierThread) ct) : null);
		
		//	notify listeners not already in chain of notification
		long start = System.currentTimeMillis();
		long end;
		for (Iterator it = listeners.iterator(); it.hasNext();) {
			GoldenGateServerEventListener gsel = ((GoldenGateServerEventListener) it.next());
			if (DEBUG_NOTIFY) System.out.println("  - listener is " + gsel.getClass().getName());
			if (notifyingListeners.add(gsel.getClass().getName())) try {
				if (DEBUG_NOTIFY) System.out.println("    - not yet in notification loop, notifying");
					gsel.notify(gse);
					if (ent != null) {
						end = System.currentTimeMillis();
						ent.log("Notification to " + gsel.getClass().getName() + " done in " + (end - start) + "ms");
						start = end;
					}
			}
			catch (Throwable t) {
				System.out.println("GoldenGateServerEventQueue: an exception occurred during event dispatching");
				System.out.println(t.getClass().getName() + ": " + t.getMessage());
				t.printStackTrace(System.out);
				while ((t = t.getCause()) != null) {
					System.out.println("caused by");
					System.out.println(t.getClass().getName() + ": " + t.getMessage());
					t.printStackTrace(System.out);
				}
			}
			finally {
				notifyingListeners.remove(gsel.getClass().getName());
			}
		}
		
		//	if set is empty, we're returning from the root notification, so we can drop the set
		if (notifyingListeners.isEmpty())
//			notifyingListenersByThreadID.remove(threadId);
			notifyingListenersByThreadID.remove();
		
		//	finish notification
		gse.notificationComplete();
		
		//	close log
		gse.closeLog();
		
		if (DEBUG_NOTIFY) System.out.println("  - event from " + gse.sourceClassName + " done");
	}
	
	/**
	 * Dedicated thread for doing even notification calls. Instances of this
	 * class are intended for use in components that have many listeners to
	 * notify, to free up other threads from doing so. They hold an internal
	 * event buffer to issue notification in the order events were issued.
	 * 
	 * @author sautter
	 */
	public static class EventNotifierThread extends Thread {
		private GoldenGateServerActivityLogger logger;
		private char logLevel = 'o';
		private GoldenGateServerEvent[] events = new GoldenGateServerEvent[32];
		private int first = 0;
		private int last = 0;
		private boolean run = true;
		private final Object lock = new Object();
		
		/**
		 * Constructor
		 * @param host the component whose events the thread is to issue
		 * @param logger the activity logger to use for notification tracking
		 */
		public EventNotifierThread(GoldenGateServerComponent host, GoldenGateServerActivityLogger logger) {
			super(StringUtils.capitalize(host.getLetterCode()) + "EventNotifier");
			this.logger = logger;
			
		}
		
		/**
		 * Constructor
		 * @param name the name for the thread
		 * @param logger the activity logger to use for notification tracking
		 */
		public EventNotifierThread(String name, GoldenGateServerActivityLogger logger) {
			super(name);
			this.logger = logger;
			
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		public void run() {
			while (this.run) {
				GoldenGateServerEvent gse = this.dequeueEvent();
				if (gse != null)
					GoldenGateServerEventService.notify(gse);
				else try {
					synchronized (this.lock) {
						this.lock.wait();
					}
				} catch (InterruptedException ie) {}
			}
		}
		
		/**
		 * Shut down the event notifier thread. Client components have to make
		 * sure to call this method on server shutdown.
		 */
		public void shutdown() {
			synchronized (this.lock) {
				this.run = false;
				this.lock.notify();
			}
		}
		
		/**
		 * Enqueue an event for issuing.
		 * @param gse the event to enqueue
		 */
		public void notify(GoldenGateServerEvent gse) {
			this.enqueueEvent(gse);
			synchronized (this.lock) {
				this.lock.notify();
			}
		}
		private synchronized void enqueueEvent(GoldenGateServerEvent gse) {
			if (this.last < this.events.length) {} // got room at end of array
			else if (0 < this.first) /* move events to start of array to make room at end */ {
				System.arraycopy(this.events, this.first, this.events, 0, (this.last - this.first));
				this.last -= this.first;
				this.first = 0;
			}
			else /* double array size to create room at end */ {
				GoldenGateServerEvent[] cEvents = new GoldenGateServerEvent[this.events.length * 2];
				System.arraycopy(this.events, 0, cEvents, 0, this.events.length);
				this.events = cEvents;
			}
			this.events[this.last++] = gse;
		}
		private synchronized GoldenGateServerEvent dequeueEvent() {
			if (this.first < this.last) // return next event in queue
				return this.events[this.first++];
			else /* queue is empty, reset to start and indicate emptiness */ {
				this.first = 0;
				this.last = 0;
				return null;
			}
		}
		
		/**
		 * Retrieve the number of events currently waiting to be issued.
		 * @return the number of events
		 */
		public int eventsPending() {
			return (this.last - this.first);
		}
		
		/**
		 * Retrieve the current log level.
		 * @return the log level
		 */
		public char getLogLevel() {
			return this .logLevel;
		}
		
		/**
		 * Set the log level for information during notifications performed by
		 * the thread. Allowed arguments are 'o' for 'off' (the default), 'd'
		 * for 'debug', 'i' for 'info', 'w' for 'warning', and 'e' for 'error'.
		 * This is mainly intended for tracking issues with the notification
		 * mechanism.
		 * @param logLevel the log level to set
		 */
		public void setLogLevel(char logLevel) {
			if ("odiwe".indexOf(logLevel) == -1)
				throw new IllegalArgumentException("Only log levels 'o', 'd', 'i', 'w', and 'e' are allowed");
			else this.logLevel = logLevel;
		}
		
		void log(String message) {
			if (this.logLevel == 'o') {}
			else if (this.logLevel == 'd')
				this.logger.logDebug(message);
			else if (this.logLevel == 'i')
				this.logger.logInfo(message);
			else if (this.logLevel == 'w')
				this.logger.logWarning(message);
			else if (this.logLevel == 'e')
				this.logger.logError(message);
		}
	}
}
