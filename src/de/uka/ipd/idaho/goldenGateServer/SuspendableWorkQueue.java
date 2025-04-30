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
package de.uka.ipd.idaho.goldenGateServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Central registration point for background work queues that can be suspended
 * in times of high load, especially in terms of memory requirements. Suspended
 * instances are automatically freed up to resume work once sufficient memory
 * becomes available again.
 * 
 * @author sautter
 */
public abstract class SuspendableWorkQueue {
	private static ArrayList instances = new ArrayList();
	private static final Comparator instanceOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			//	sort highest threshold first
			return (((SuspendableWorkQueue) obj2).suspendBelowMB - ((SuspendableWorkQueue) obj1).suspendBelowMB);
		}
	};
	private static int maxSuspendBelowMB = -1;
	
	/** the name of the suspendable work queue */
	public final String name;
	
	/** the amount of free memory (in MB) after a GC event below which to suspend the work queue */
	public final int suspendBelowMB;
	
	/** the amount of free memory (in MB) after a GC event above which to resume the work queue if it was suspended */
	public final int resumeAboveMB;
	
	/** Constructor
	 * @param name the name of the suspendable work queue
	 * @param suspendBelowMB the amount of free memory after a GC event below which to suspend the work queue
	 * @param resumeAboveMB the amount of free memory after a GC event above which to resume the work queue if it was suspended
	 */
	public SuspendableWorkQueue(String name, int suspendBelowMB, int resumeAboveMB) {
		this.name = name;
		this.suspendBelowMB = suspendBelowMB;
		this.resumeAboveMB = resumeAboveMB;
		synchronized (instances) {
			instances.add(this);
			if (instances.size() > 1)
				Collections.sort(instances, instanceOrder);
			maxSuspendBelowMB = Math.max(maxSuspendBelowMB, this.suspendBelowMB);
		}
	}
	
	/**
	 * Dispose of the work queue, e.g. after the underlying background process
	 * is shut down.
	 */
	public void dispose() {
		synchronized (instances) {
			instances.remove(this);
			maxSuspendBelowMB = (instances.isEmpty() ? -1 : ((SuspendableWorkQueue) instances.get(0)).suspendBelowMB);
		}
	}
	
	/**
	 * Suspend the underlying work queue. A return value of <code>true</code>
	 * has to indicate that the work queue was actually suspended as a result
	 * of the call to this method and was not already suspended in other ways.
	 * @return true if the underlying work queue was suspended as a result of
	 *            the call to this method
	 */
	public abstract boolean suspend();
	
	/**
	 * Indicate whether or not the work is actually suspended.
	 * @return true if the work queue is suspended
	 */
	public abstract boolean isSuspended();
	
	/**
	 * Allow the underlying work queue to resume operation after enough memory
	 * has been freed up again.
	 */
	public abstract void resume();
	
	static int getMaximumSuspendBelowMB() {
		return maxSuspendBelowMB;
	}
	
	static SuspendableWorkQueue[] getInstances() {
		synchronized (instances) {
			return ((SuspendableWorkQueue[]) instances.toArray(new SuspendableWorkQueue[instances.size()]));
		}
	}
}
