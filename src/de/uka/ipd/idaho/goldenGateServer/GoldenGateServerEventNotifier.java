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
package de.uka.ipd.idaho.goldenGateServer;

import java.util.LinkedList;

import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent.ComponentActionConsole;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants.GoldenGateServerEvent;

/**
 * Utility class doing event notification in a separate thread. This is helpful
 * for events that have many listeners, especially if they are triggered by a
 * user action, as it lets the user action return in a timely manner independent
 * of how long event notification takes to complete. Events are guaranteed to
 * notify in the order they are handed to the <code>notify()</code> method.
 * GoldenGATE Server components using this class for event notification are
 * strongly recommended to use it for all their event notifications, as
 * otherwise event order cannot be guaranteed.
 * 
 * @author sautter
 */
public class GoldenGateServerEventNotifier {

	/** Constructor
	 * @param name the name for the notification thread
	 */
	public GoldenGateServerEventNotifier(String name) {
		this.eventNotifier = new EventNotifier(name);
		this.eventNotifier.start();
		//	TODO restore events from database
		//	TODO keep in mind: prepareEventNotification() will load the bulky data
	}
	
	/**
	 * Shut down the encapsulated notification thread.
	 */
	public void shutdown() {
		if (this.eventNotifier != null)
			this.eventNotifier.shutdown();
	}
	
	/**
	 * Enqueue an event for asynchronous notification. Events are guaranteed to
	 * arrive at listeners in the same order they are handed to this method.
	 * @param gse the event to notify listeners about
	 */
	public void notify(GoldenGateServerEvent gse) {
		synchronized (this.eventQueue) {
			this.eventQueue.addLast(gse); // enqueue event for asynchronous notification
			this.eventQueue.notify(); // wake up notifier thread
		}
		//	TODO persist events in database
		//	TODO add serialization overwritable method
		//	TODO keep in mind: prepareEventNotification() will load the bulky data
	}
	
	/**
	 * Prepare an event for notification. This method allows client code to
	 * load bulky event content on demand right before event notification
	 * starts, instead of having that bulky content pile up in the event
	 * queue. This default implementation returns the argument event, sub
	 * classes are welcome to overwrite it as needed.
	 * @param gse the GoldenGATE Server event to prepare for notification
	 * @return the prepared event
	 */
	protected GoldenGateServerEvent prepareEventNotification(GoldenGateServerEvent gse) throws Exception {
		return gse;
	}
	
	/**
	 * Get the current size of the event queue, i.e., the number of events in
	 * line for notification.
	 * @return the event queue size
	 */
	public int getQueueSize() {
		return this.eventQueue.size();
	}
	
	/**
	 * Retrieve a console action for checking the number of pending events
	 * enqueued for notification in this queue. The command for the returned
	 * action is 'eventsPending'.
	 * @return the console action
	 */
	public ComponentActionConsole getQueueSizeAction() {
		return new QueueSizeAction();
	}
	private class QueueSizeAction extends ComponentActionConsole {
		public String getActionCommand() {
			return "eventsPending";
		}
		public String[] getExplanation() {
			String[] explanation = {
					"eventsPending",
					"Display the number of pending events enqueued for notification."
				};
			return explanation;
		}
		public void performActionConsole(String[] arguments) {
			if (arguments.length == 0)
				System.out.println(" There are currently " + eventQueue.size() + " events enqueued for notification.");
			else System.out.println(" Invalid arguments for '" + this.getActionCommand() + "', specify no argument.");
		}
	}
	
	private LinkedList eventQueue = new LinkedList();
	
	private EventNotifier eventNotifier;
	
	private class EventNotifier extends Thread {
		private boolean shutdown = false;
		EventNotifier(String name) {
			super(name);
		}
		public void run() {
			
			//	wake up starting thread
			synchronized (this) {
				this.notify();
			}
			
			//	wait a little before starting to work
			try {
				Thread.sleep(10000);
			} catch (InterruptedException ie) {}
			
			//	run indefinitely
			while (!this.shutdown) {
				
				//	get next event, or wait until next event available
				GoldenGateServerEvent gse = null;
				synchronized (eventQueue) {
					if (eventQueue.size() == 0) try {
						eventQueue.wait();
					} catch (InterruptedException ie) {}
					if (eventQueue.size() != 0)
						gse = ((GoldenGateServerEvent) eventQueue.removeFirst());
				}
				
				//	nothing to do
				if (gse != null) try {
					gse = prepareEventNotification(gse);
					GoldenGateServerEventService.notify(gse);
				}
				catch (Exception e) {
					System.out.println(this.getName() + ": Error in notification for event " + gse.getClass().getName() + " - " + e.getMessage());
					e.printStackTrace(System.out);
				}
				catch (Throwable t) {
					System.out.println(this.getName() + ": Error in notification for event " + gse.getClass().getName() + " - " + t.getMessage());
					t.printStackTrace(System.out);
				}
			}
		}
		
		public synchronized void start() {
			super.start();
			try {
				this.wait();
			} catch (InterruptedException ie) {}
		}
		
		void shutdown() {
			synchronized (eventQueue) {
				this.shutdown = true;
				eventQueue.notify();
			}
		}
	}
}