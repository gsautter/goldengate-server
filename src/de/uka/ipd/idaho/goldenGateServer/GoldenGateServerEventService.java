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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants.GoldenGateServerEvent.GoldenGateServerEventListener;

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
	private static Map notifyingListenersByThreadID = Collections.synchronizedMap(new HashMap());
	private static final boolean DEBUG_NOTIFY = false;
	
	/**
	 * Issue notification of an event.
	 * @param gse the event to issue
	 */
	public static void notify(GoldenGateServerEvent gse) {
		if (DEBUG_NOTIFY) System.out.println("GoldenGateServerEventQueue: issuing notification on " + gse.getClass().getName());
		
		//	get set of listener classes currently in the chain of notification
		Long threadId = new Long(Thread.currentThread().getId());
		Set notifyingListeners = ((Set) notifyingListenersByThreadID.get(threadId));
		if (notifyingListeners == null) {
			notifyingListeners = Collections.synchronizedSet(new HashSet());
			notifyingListenersByThreadID.put(threadId, notifyingListeners);
		}
		
		//	notify listeners not already in chain of notification
		for (Iterator it = listeners.iterator(); it.hasNext();) {
			GoldenGateServerEventListener gsel = ((GoldenGateServerEventListener) it.next());
			if (DEBUG_NOTIFY) System.out.println("  - listener is " + gsel.getClass().getName());
			if (notifyingListeners.add(gsel.getClass().getName())) try {
				if (DEBUG_NOTIFY) System.out.println("    - not yet in notification loop, notifying");
				gsel.notify(gse);
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
			notifyingListenersByThreadID.remove(threadId);
		
		//	finish notification
		gse.notificationComplete();
		
		//	close log
		gse.closeLog();
		
		if (DEBUG_NOTIFY) System.out.println("  - event from " + gse.sourceClassName + " done");
	}
}
