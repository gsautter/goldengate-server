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
package de.uka.ipd.idaho.goldenGateServer.util;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.goldenGateServer.AsynchronousWorkQueue;

/**
 * LRU cache with integrated time-based cleanup service, flexible entry weight,
 * and idle time based entry reachability reduction. This cache is mainly
 * intended for larger entry objects, as tracking access and providing flexible
 * reachability does come at a small per-entry overhead. For a cache of smaller
 * entries (e.g. individual strings or numbers), a <code>LinkedHashMap</code>
 * in access order might be the preferable option in many cases.
 * 
 * @author sautter
 */
public class LruCache {
	private final WeakReference instanceRef;
	private final String name;
	private LinkedHashMap data;
	
	private int sizeLimit;
	private int weightLimit;
	private int weakeningTimeSeconds;
	private int clearingTimeSeconds;
	private boolean putWeak;
	
	private long hitCount = 0;
	private long missCount = 0;
	
	/**
	 * @param name the name of the cache, for statistics
	 * @param sizeLimit the maximum number of entries
	 */
	public LruCache(String name, int sizeLimit) {
		this(name, sizeLimit, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
	}
	
	/**
	 * @param name the name of the cache, for statistics
	 * @param sizeLimit the maximum number of entries
	 * @param weakeningTime number of idle seconds before moving an entry value to a weak reference (setting to zero effects behavior akin to <code>WeakHashMap</code>)
	 */
	public LruCache(String name, int sizeLimit, int weakeningTime) {
		this(name, sizeLimit, Integer.MAX_VALUE, weakeningTime, Integer.MAX_VALUE);
	}
	
	/**
	 * @param name the name of the cache, for statistics
	 * @param sizeLimit the maximum number of entries
	 * @param weightLimit the maximum overall weight of the entries
	 * @param weakeningTime number of idle seconds before moving an entry value to a weak reference (setting to zero effects behavior akin to <code>WeakHashMap</code>)
	 * @param clearingTime number of idle seconds before removing an entry value altogether
	 */
	public LruCache(String name, int sizeLimit, int weightLimit, int weakeningTime, int clearingTime) {
		this.name = name;
		this.data = new LinkedHashMap((((sizeLimit * 4) + 3) / 3) /* use maximum size right away */, 0.75f /* JRE default */, true);
		this.sizeLimit = sizeLimit;
		this.weightLimit = weightLimit;
		this.weakeningTimeSeconds = weakeningTime;
		this.clearingTimeSeconds = clearingTime;
		this.putWeak = (this.weakeningTimeSeconds < 1);
		this.instanceRef = new WeakReference(this);
		registerInstance(this);
	}
	
	/* (non-Javadoc)
	 * @see java.util.Map#get(java.lang.Object)
	 */
	public synchronized Object get(Object key) {
		ValueTray vt = ((ValueTray) this.data.get(key));
		if (vt == null) {
			this.missCount++;
			return null;
		}
		Object value = vt.getValue();
		if (value == null) // value reference cleared by GC, no use holding on to tray
			this.data.remove(key);
		if (value == null)
			this.missCount++;
		else this.hitCount++;
		return value;
	}
	
	/* (non-Javadoc)
	 * @see java.util.HashMap#put(java.lang.Object, java.lang.Object)
	 */
	public synchronized Object put(Object key, Object value) {
		if (value == null)
			return this.remove(key);
		int weight = this.getWeight(value);
		if (this.weightLimit < weight)
			return value;
		ValueTray oldVt = ((ValueTray) this.data.put(key, new ValueTray(value, weight, this.putWeak)));
		if (oldVt == null)
			return null;
		Object oldValue = oldVt.getValueInternal();
		if (oldValue == null)
			return null;
		this.valueRemoved(key, oldValue, oldVt.hitCount, oldVt.lastAccessed, REMOVAL_REASON_REPLACED);
		return oldValue;
	}
	
	/* (non-Javadoc)
	 * @see java.util.HashMap#remove(java.lang.Object)
	 */
	public synchronized Object remove(Object key) {
		ValueTray oldVt = ((ValueTray) this.data.remove(key));
		if (oldVt == null)
			return null;
		Object oldValue = oldVt.getValueInternal();
		if (oldValue == null)
			return null;
		this.valueRemoved(key, oldValue, oldVt.hitCount, oldVt.lastAccessed, REMOVAL_REASON_REMOVED);
		return oldValue;
	}
	
	/* (non-Javadoc)
	 * @see java.util.HashMap#size()
	 */
	public synchronized int size() {
		return this.data.size();
	}
	
	/* (non-Javadoc)
	 * @see java.util.LinkedHashMap#clear()
	 */
	public synchronized void clear() {
		this.data.clear();
	}
	
	/* (non-Javadoc)
	 * @see java.util.HashMap#keySet()
	 */
	public synchronized ArrayList keys() {
		return new ArrayList(this.data.keySet());
	}
	
	/* (non-Javadoc)
	 * @see java.util.HashMap#values()
	 */
	public synchronized ArrayList values() {
		ArrayList values = new ArrayList(this.data.values());
		for (int v = 0; v < values.size(); v++) {
			ValueTray vt = ((ValueTray) values.get(v));
			Object value = vt.getValueInternal();
			if (value == null)
				values.remove(v--);
			else values.set(v, value); // un-tray entry values in place
		}
		return values;
	}
	
	/**
	 * Dispose of the cache. This method first removes the cache from the list
	 * of instances used by internal maintenance, and then clears out all the
	 * entries, calling <code>valueRemoved()</code> for each one of them.
	 */
	public synchronized void dispose() {
		unregisterInstance(this); // remove from maintenance cycle
		ArrayList entries = new ArrayList(this.data.entrySet()); // using entry set leaves access order untouched, saving hassle
		this.data.clear();
		for (int e = 0; e < entries.size(); e++) {
			Map.Entry entry = ((Map.Entry) entries.get(e));
			Object key = entry.getKey();
			ValueTray vt = ((ValueTray) entry.getValue());
			this.valueRemoved(key, vt.getValueInternal(), vt.hitCount, vt.lastAccessed, REMOVAL_REASON_DISPOSED);
		}
	}
	
	/**
	 * Provide the weight of a given cached value. This allows subclasses to
	 * control the overall memory footprint of a cache, mainly in scenarios of
	 * cached values varying widely in individual memory consumption. The
	 * runtime class of the arguments to this method are the same as the class
	 * of the value object handed to the <code>put()</code> method. This
	 * default implementation returns 0, subclasses are welcome to overwrite it
	 * as needed.
	 * @param value the value object whose weight to compute
	 * @return the weight of the argument value object
	 */
	protected int getWeight(Object value) {
		return 0;
	}
	
	/** constant indicating a value object was removed because a different value object was associated with its key */
	public static final String REMOVAL_REASON_REPLACED = "replaced";
	
	/** constant indicating a value object was removed via an explicit invocation of the <code>remove()</code> method */
	public static final String REMOVAL_REASON_REMOVED = "removed";
	
	/** constant indicating a value object was removed because the cache grew too large */
	public static final String REMOVAL_REASON_SIZE = "size";
	
	/** constant indicating a value object was removed because the overall content of the cache grew too heavy */
	public static final String REMOVAL_REASON_WEIGHT = "weight";
	
	/** constant indicating a value object was moved to a <code>WeakReference</code> because it was last touched before the weakening timeout, and <b>might be</b> removed by garbage collection at any point */
	public static final String REMOVAL_REASON_WEAKENED = "weakened";
	
	/** constant indicating a value object was removed because it was last touched before the clearing timeout */
	public static final String REMOVAL_REASON_TIME = "time";
	
	/** constant indicating a value object was removed because the cache is being disposed of */
	public static final String REMOVAL_REASON_DISPOSED = "disposed";
	
	/**
	 * Take additional action as needed when a value is evicted by maintenance,
	 * like e.g. persisting unstored changes to a cached value, or performing
	 * custom cleanup operations. The argument reason is one of the constants
	 * provided by this class. This default implementation does nothing,
	 * subclasses are welcome to overwrite it as needed.<br/>
	 * <b>IMPORTANT</b>: implementations of this method MUST NOT call any code
	 * synchronizing on any other object than this cache proper, as this method
	 * is called by the background maintenance thread from a context that is
	 * synchronized on this cache, and hence synchronizing on any other object
	 * would result in a (deadlock prone) lock escalation.
	 * @param key the key the value object was associated with
	 * @param value the value object that was removed
	 * @param hits the number of cache hits on the value object
	 * @param lastAccess the time the value object was last accessed
	 * @param reason the reason the argument value object was removed
	 */
	protected void valueRemoved(Object key, Object value, int hits, long lastAccess, String reason) {}
	
	private static class ValueTray {
		long created = System.currentTimeMillis();
		private boolean putWeak;
		private Object value;
		private WeakReference valueRef;
		int weight;
		long lastAccessed = this.created;
		int hitCount = 0;
		ValueTray(Object value, int weight, boolean putWeak) {
			this.putWeak = putWeak;
			if (this.putWeak)
				this.valueRef = new WeakReference(value);
			else this.value = value;
			this.weight = weight;
		}
		Object getValue() {
			this.lastAccessed = System.currentTimeMillis();
			this.hitCount++;
			Object value = this.getValueInternal();
			if ((value == null) || (this.value != null) || this.putWeak)
				return value;
			this.valueRef = null;
			this.value = value;
			return value;
		}
		Object getValueInternal() {
			if (this.value != null)
				return this.value;
			else if (this.valueRef != null)
				return this.valueRef.get();
			else return null;
		}
		boolean weaken() {
			if ((this.value != null) && (this.valueRef == null)) {
				this.valueRef = new WeakReference(this.value);
				this.value = null;
				return true;
			}
			else return false;
		}
		boolean isVoided() {
			if (this.value != null)
				return false;
			else if (this.valueRef != null)
				return (this.valueRef.get() == null);
			else return true;
		}
	}
	
	synchronized void runMaintenance(long time) {
		cacheEntriesStrong.removeAll(this.name);
		cacheEntriesWeak.removeAll(this.name);
		cacheEntryWeights.removeAll(this.name);
		if (this.data.isEmpty())
			return;
		ArrayList entries = new ArrayList(this.data.entrySet()); // using entry set leaves access order untouched
		int voidCount = 0;
		int weakenCount = 0;
		int clearCount = 0;
		int overweightCount = 0;
		int oversizeCount = 0;
		int weightSum = 0;
		for (int e = 0; e < entries.size(); e++) {
			Map.Entry entry = ((Map.Entry) entries.get(e));
			Object key = entry.getKey();
			ValueTray vt = ((ValueTray) entry.getValue());
			if (vt.isVoided()) {
				this.data.remove(key);
				voidCount++;
				entries.remove(e--);
				continue;
			}
			if (this.weightLimit < (weightSum + vt.weight)) {
				this.data.remove(key);
				overweightCount++;
				this.valueRemoved(key, vt.getValueInternal(), vt.hitCount, vt.lastAccessed, REMOVAL_REASON_WEIGHT);
				entries.remove(e--);
				continue;
			}
			if (this.sizeLimit <= e) {
				this.data.remove(key);
				oversizeCount++;
				this.valueRemoved(key, vt.getValueInternal(), vt.hitCount, vt.lastAccessed, REMOVAL_REASON_SIZE);
				entries.remove(e--);
				continue;
			}
			long vtAgeSeconds = ((time - vt.lastAccessed + 500) / 1000);
			if (this.clearingTimeSeconds < vtAgeSeconds) {
				this.data.remove(key);
				clearCount++;
				this.valueRemoved(key, vt.getValueInternal(), vt.hitCount, vt.lastAccessed, REMOVAL_REASON_TIME);
				entries.remove(e--);
				continue;
			}
			if (this.weakeningTimeSeconds < vtAgeSeconds) {
				Object value = vt.getValueInternal();
				if (vt.weaken()) {
					weakenCount++;
					this.valueRemoved(key, value, vt.hitCount, vt.lastAccessed, REMOVAL_REASON_WEAKENED);
//					System.out.println(" ==> " + vt.getValueInternal() + " in " + vt.valueRef);
				}
			}
			weightSum += vt.weight;
			if (vt.value != null)
				cacheEntriesStrong.add(this.name);
			else if (vt.valueRef instanceof WeakReference)
				cacheEntriesWeak.add(this.name);
			cacheEntryWeights.add(this.name, vt.weight);
		}
		if ((voidCount + overweightCount + oversizeCount + clearCount + weakenCount) == 0)
			return;
		System.out.println(this.name + ": " + this.data.size() + " entries retained (" + weakenCount + " weakened), " + voidCount + " swept, " + clearCount + " cleared, " + oversizeCount + " removed as too many, " + overweightCount + " removed as too heavy in " + (System.currentTimeMillis() - time) + "ms");
	}
	
	/**
	 * Retrieve the status strings of all extant instances of this class,
	 * including information on overall content and weight as well as usage
	 * and hit rate. The status strings in the returned array are in instance
	 * creation order.
	 * @return an array holding the status strings
	 */
	public static String[] getInstanceStatusStrings() {
		ArrayList extantInstances = getExtantInstances();
		ArrayList statusStrings = new ArrayList();
		for (int i = 0; i < extantInstances.size(); i++) {
			LruCache instance = ((LruCache) ((WeakReference) extantInstances.get(i)).get());
			if (instance == null)
				continue; // reclaimed during our way here, simply ignore it
			int strongCount = cacheEntriesStrong.getCount(instance.name);
			int weakCount = cacheEntriesWeak.getCount(instance.name);
			int weight = cacheEntryWeights.getCount(instance.name);
			long hitCount = instance.hitCount; // local copy hedges against concurrent modification
			long missCount = instance.missCount; // local copy hedges against concurrent modification
			long lookupCount = (hitCount + missCount);
			int hitRate = ((int) ((lookupCount == 0) ? 0 : (((hitCount * 100) + (lookupCount / 2)) / lookupCount)));
			statusStrings.add(instance.name + ": " + (strongCount + weakCount) + " entries (" + strongCount + "/" + weakCount + ") with overall weight " + weight + ", " + lookupCount + " lookups so far, hit rate " + hitRate + "% (" + hitCount + " hits vs. " + missCount + " misses)");
		}
		return ((String[]) statusStrings.toArray(new String[statusStrings.size()]));
	}
	
	private static ArrayList instances = new ArrayList();
	private static Thread maintenanceWorker = null;
//	private static AsynchronousWorkQueue maintenanceWorkerMonitor = null;
	private static Object maintenanceWorkerMonitor = null;
	private static CountingSet cacheEntriesStrong = new CountingSet(new HashMap());
	private static CountingSet cacheEntriesWeak = new CountingSet(new HashMap());
	private static CountingSet cacheEntryWeights = new CountingSet(new HashMap());
	private static synchronized void registerInstance(LruCache instance) {
		instances.add(instance.instanceRef);
		if (maintenanceWorker != null)
			return;
		maintenanceWorker = new Thread("LruCacheMaintenanceWorker") {
			public void run() {
				long lastLogTime = System.currentTimeMillis();
				while (true) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException ie) {}
					ArrayList extantInstances = getExtantInstances();
					for (int i = 0; i < extantInstances.size(); i++) {
						String instanceName = null;
						try {
							LruCache instance = ((LruCache) ((WeakReference) extantInstances.get(i)).get());
							if (instance == null)
								continue; // reclaimed during our way here, simply ignore it
							long time = System.currentTimeMillis();
							instanceName = instance.name;
							instance.runMaintenance(time);
							long perInstanceTime = (1000 / extantInstances.size()); // visit every instance once per second
							long instanceTime = (System.currentTimeMillis() - time); // how long did we work on this one?
							if (instanceTime < perInstanceTime) // still some time left, sleep some
								Thread.sleep(Math.max(1, (perInstanceTime - instanceTime)));
						}
						catch (InterruptedException ie) { /* no use logging sleeping errors */ }
						catch (Throwable t) {
							System.err.println(this.getName() + ": error running maintenance on LRU cache '" + instanceName + "' - " + t.getMessage());
							t.printStackTrace(System.err);
						}
					}
					if (noExtantInstances())
						return;
					if (maintenanceWorkerMonitor != null)
						continue; // in the back-end, we're reporting on demand
					long logTime = System.currentTimeMillis();
					if (logTime < (lastLogTime + (1000 * 60)))
						continue; // let's not be all too verbose and only log every minute
					System.out.println("LruCacheMaintenanceWorker: " + getStatusMessage());
					lastLogTime = logTime;
				}
			}
		};
		maintenanceWorker.start();
		try {
			maintenanceWorkerMonitor = new AsynchronousWorkQueue("LruCacheMaintenanceWorker") {
				public String getStatus() {
//					int strong = cacheEntriesStrong.size();
//					int weak = cacheEntriesWeak.size();
//					return (this.name + ": got " + instances.size() + " caches with total of " + (strong + weak) + " entries (" + strong + "/" + weak + ")");
					return (this.name + ": " + getStatusMessage());
				}
			};
		}
		catch (Throwable t) { /* asynchronous work queue is not available (and of no use) in web front-end */ }
	}
	private static synchronized void unregisterInstance(LruCache instance) {
		instances.remove(instance.instanceRef);
	}
	static synchronized ArrayList getExtantInstances() {
		ArrayList extantInstances = new ArrayList();
		for (int i = 0; i < instances.size(); i++) {
			WeakReference instanceRef = ((WeakReference) instances.get(i));
			if (instanceRef.get() == null)
				instances.remove(i--);
			else extantInstances.add(instanceRef);
		}
		return extantInstances; // create copy to avoid concurrent modification while reading in maintenance worker
	}
	static synchronized boolean noExtantInstances() {
		for (int i = 0; i < instances.size(); i++) {
			WeakReference instanceRef = ((WeakReference) instances.get(i));
			if (instanceRef.get() == null)
				instances.remove(i--);
		}
		if (instances.isEmpty()) /* must have cleared them all, or disposed of explicitly */ {
			maintenanceWorker = null;
			if (maintenanceWorkerMonitor != null)
				((AsynchronousWorkQueue) maintenanceWorkerMonitor).dispose();
			maintenanceWorkerMonitor = null;
			return true;
		}
		else return false;
	}
	static String getStatusMessage() {
		int strong = cacheEntriesStrong.size();
		int weak = cacheEntriesWeak.size();
		return ("got " + instances.size() + " caches with total of " + (strong + weak) + " entries (" + strong + "/" + weak + ")");
	}
//	
//	//	TEST ONLY !!!
//	public static void main(String[] args) throws Exception {
//		runTest();
//		while (maintenanceWorker != null) {
//			Thread.sleep(1000 * 3);
//			System.gc(); // simulate subsequent GCs, as maintenance worker needs to quit when last cache instance reclaimed
//		}
//	}
//	private static void runTest() throws Exception /* extra method so we can GC after cache instance out of scope (maintenance worker needs to quit) */ {
//		LruCache lc = new LruCache("Test", 16, Integer.MAX_VALUE, 5, 10) {
//			protected void valueRemoved(Object key, Object value, int hits, long lastAccess, String reason) {
//				System.out.println("Value removed: " + key + " = " + value + " after " + hits + " hits due to " + reason);
//			}
//		};
//		for (int e = 0; e < 32; e++) {
//			lc.put(("test" + e + "key"), ("test" + e + "value"));
//			for (int l = (e-1); l >= 0; l--)
//				lc.get("test" + l + "key");
//		}
//		for (int l = 0; l < 32; l++) {
//			Object key = ("test" + l + "key");
//			System.out.println("Pre-wait: " + key + " = " + lc.get(key));
//		}
//		Thread.sleep(1000 * 6);
//		for (int l = 0; l < 32; l++) /* should all be weak by now */ {
//			Object key = ("test" + l + "key");
//			System.out.println("Pre-GC: " + key + " = " + lc.get(key));
//		}
//		System.gc();
//		for (int l = 0; l < 32; l++) /* should all be cleared by GC */ {
//			Object key = ("test" + l + "key");
//			System.out.println("Post-GC: " + key + " = " + lc.get(key));
//		}
//		lc.dispose();
//	}
}
