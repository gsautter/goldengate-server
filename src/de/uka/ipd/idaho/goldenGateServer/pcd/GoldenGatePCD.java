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
package de.uka.ipd.idaho.goldenGateServer.pcd;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent;
import de.uka.ipd.idaho.goldenGateServer.util.AsynchronousDataActionHandler;
import de.uka.ipd.idaho.goldenGateServer.util.IdentifierKeyedDataObjectStore;
import de.uka.ipd.idaho.goldenGateServer.util.LruCache;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * GoldenGATE Process Control Data Store (PCD) offers centralized storage for
 * data pertaining to the processing of individual data objects. This is to
 * offer an alternative to each consumer of process control data holding its
 * own database tables for storing its specific control data for all data
 * objects. In particular, this storage per data object can offer considerable
 * advantages over storage per consumer if the number of data objects is large.
 * 
 * @author sautter
 */
public class GoldenGatePCD extends AbstractGoldenGateServerComponent {
	private IdentifierKeyedDataObjectStore pcdStore;
	private AsynchronousDataActionHandler pcdPersister;
	
	/** usual zero-argument constructor for class loading, handing 'PCD' as the
	 * letter code to the superclass */
	public GoldenGatePCD() {
		super("PCD");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#initComponent()
	 */
	protected void initComponent() {
		
		/* TODO for locking (in case of usage across multiple JVMs):
		 * - use database table for checkout status
		 * - set on loading, clear when persisted and discarded
		 * - return read-only if checked out anywhere else
		 * - persist more eagerly, and cache more briefly
		 */
		
		//	create process control data store
		String dataFolderName = this.configuration.getSetting("dataFolderName", "ProcessControlData");
		File dataFolder;
		if (dataFolderName.startsWith("/") || (dataFolderName.indexOf(":\\") != -1) || (dataFolderName.indexOf(":/") != -1))
			dataFolder = new File(dataFolderName);
		else dataFolder = new File(this.dataPath, dataFolderName);
		dataFolder.mkdirs();
		this.pcdStore = new IdentifierKeyedDataObjectStore("ProcessControlData", dataFolder, ".txt", false, this);
		
		//	initialize delay-timed process control data persisting
		this.pcdPersister = new AsynchronousDataActionHandler("ProcessControlDataPersister", this) {
			protected void performDataAction(String dataId, String[] arguments) throws Exception {
				persistProcessControlData(dataId);
			}
		};
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#linkInit()
	 */
	public void linkInit() {
		
		//	start background persister
		this.pcdPersister.start();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#prepareExit()
	 */
	public void prepareExit() {
		
		//	persist whatever dirty process control data we have in our cache
		this.persistDirtyProcessControlData();
		
		//	have process control data store work off its pending maintenance jobs
		this.pcdStore.finishMaintenance();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#exitComponent()
	 */
	protected void exitComponent() {
		
		//	shut down background persister
		this.pcdPersister.shutdown();
		
		//	dispose cache (also persists all dirty process control data)
		this.processControlDataCache.dispose();
		
		//	shut down process control data store
		this.pcdStore.shutdown();
	}
	
	private static final String PERSIST_DATA_COMMAND = "persistAll";
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#getActions()
	 */
	public ComponentAction[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentActionConsole ca;
		
		//	TODO add any other console actions we might need
		
		//	persist all cached pending process control data
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return PERSIST_DATA_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						PERSIST_DATA_COMMAND,
						"Persist all pending process control data",
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0)
					persistDirtyProcessControlData();
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	add management actions from process control data store
		cal.addAll(Arrays.asList(this.pcdStore.getActions()));
		
		//	add management actions from process control data persister
		cal.addAll(Arrays.asList(this.pcdPersister.getActions()));
		
		//	finally ...
		return ((ComponentAction[]) cal.toArray(new ComponentAction[cal.size()]));
	}
	
	private LruCache processControlDataCache = new LruCache("PcdCache", 128, Integer.MAX_VALUE, (60 * 3) /* let regular persister do its thing under normal circumstances */, (60 * 7)) {
		protected void valueRemoved(Object key, Object value, int hits, long lastAccess, String reason) {
			persistProcessControlData(((String) key), ((ProcessControlData) value));
		}
	};
	
	void schedulePersistProcessControlData(String dataId, int in) {
		this.pcdPersister.scheduleDataAction(dataId, in);
	}
	void persistProcessControlData(String dataId) {
		this.logInfo("GoldenGatePCD: persisting process control data for data object '" + dataId + "'");
		
		//	get process control data from cache
		synchronized (this.processControlDataCache) {
			ProcessControlData pcd = ((ProcessControlData) this.processControlDataCache.get(dataId));
			if (pcd == null)
				this.logInfo(" - process control data object not found");
			else this.persistProcessControlData(dataId, pcd);
		}
	}
	void persistDirtyProcessControlData() {
		synchronized (this.processControlDataCache) {
			ArrayList pcds = this.processControlDataCache.values();
			for (int d = 0; d < pcds.size(); d++) {
				ProcessControlData pcd = ((ProcessControlData) pcds.get(d));
				this.persistProcessControlData(pcd.dataId, pcd);
			}
		}
	}
	void persistProcessControlData(String dataId, ProcessControlData pcd) {
		
		//	anything to do at all
		if (pcd.isClean()) {
			this.logInfo(" - process control data object unmodified or persisted before");
			return;
		}
		
		//	persist process control data
		try {
			
			//	store process control data
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(this.pcdStore.getOutputStream(dataId), "UTF-8"));
			pcd.writeTsv(out);
			out.flush();
			this.logInfo(" - process control data persisted");
			
			//	mark store process control data as persisted
			pcd.setClean();
		}
		catch (IOException ioe) {
			this.logError("Could not persist transit protocol for data object '" + dataId + "': " + ioe.getMessage());
			this.logError(ioe);
		}
	}
	
	/**
	 * Retrieve the process control data for a data object with a given ID. If
	 * the. If no process control data exists for the data object with the
	 * argument ID, this method creates and returns an empty object, but never
	 * null.
	 * @param dataId the ID of the data object
	 * @return the process control data
	 */
	public ProcessControlData getProcessControlData(String dataId) throws IOException {
		return this.getProcessControlData(dataId, null);
	}
	
	/**
	 * Retrieve the process control data for a detail in a data object with a
	 * given ID. If the argument detail ID is null, this method returns the
	 * process control data for the whole data object. If no process control
	 * data exists for the data object with the argument ID, this method
	 * creates and returns an empty object, but never null.
	 * @param dataId the ID of the data object
	 * @param detailId the ID of the data detail inside that object
	 * @return the process control data
	 */
	public ProcessControlData getProcessControlData(String dataId, String detailId) throws IOException {
		
		//	need to synchronize on cache, not on this component, as cache proper is synchronized ==> danger of deadlocks
		synchronized (this.processControlDataCache) {
			
			//	check cache first (must not have two objects for same data ID)
			ProcessControlData pcd = ((ProcessControlData) this.processControlDataCache.get(dataId));
			if (pcd != null)
				return ((detailId == null) ? pcd : pcd.getSubset(detailId));
			
			//	load from persistent storage ...
			if (this.pcdStore.isDataObjectAvailable(dataId)) {
				BufferedReader in = new BufferedReader(new InputStreamReader(this.pcdStore.getInputStream(dataId), "UTF-8"));
				pcd = ProcessControlData.readTsv(in, this, dataId);
			}
			
			//	or create new object
			else pcd = new ProcessControlData(this, dataId);
			
			//	cache and return process control data
			this.processControlDataCache.put(dataId, pcd);
			return ((detailId == null) ? pcd : pcd.getSubset(detailId));
		}
	}
	
	/**
	 * Container for data pertaining to the processing of individual data
	 * objects in GoldenGATE Server.
	 * 
	 * @author sautter
	 */
	public static class ProcessControlData {
		private GoldenGatePCD host;
		final String dataId;
		private ProcessControlData root;
		private String prefix;
		private Map valueTrays;
		private long createTime;
		private long cleanModTime;
		private long modTime;
		ProcessControlData(GoldenGatePCD host, String dataId) {
			this.host = host;
			this.dataId = dataId;
			this.prefix = null;
			this.root = null;
//			this.valueTrays = Collections.synchronizedMap(new LinkedHashMap());
			this.valueTrays = new LinkedHashMap(); // all accessing methods synchronized, no need for synchronizing map proper
			this.createTime = System.currentTimeMillis();
			this.cleanModTime = this.createTime;
			this.modTime = this.createTime;
		}
		private ProcessControlData(String prefix, ProcessControlData root) {
			this.host = null;
			this.dataId = null;
			this.prefix = prefix;
			this.root = root;
			this.valueTrays = null;
		}
		
		/**
		 * Retrieve a subset of the process control data set. If this process
		 * control data set is a subset of another one, prefixes are chained,
		 * with a period in between. Prefixes must consist of letters, digits,
		 * dashes, and underscores; all other characters are replaced with
		 * underscores.
		 * @param prefix the prefix to add
		 * @return the subset with the argument prefix
		 */
		public ProcessControlData getSubset(String prefix) {
			if (prefix == null)
				return this;
			prefix = prefix.trim();
			if (prefix.length() == 0)
				return this;
			prefix = prefix.replaceAll("[^0-9a-zA-Z\\_\\-]", "_");
			if (this.root == null)
				return new ProcessControlData(prefix, this);
			else return new ProcessControlData((this.prefix + "." + prefix), this.root);
		}
		
		/**
		 * Retrieve the integer associated with a given key. If no value is
		 * associated with the argument key, this method returns 0.
		 * @param key the key to get the integer for
		 * @return the integer associated with the argument key
		 */
		public int getInt(String key) {
			return this.getInt(key, 0);
		}
		
		/**
		 * Retrieve the integer associated with a given key. If no value is
		 * associated with the argument key, this method returns the argument
		 * default value.
		 * @param key the key to get the integer for
		 * @param def the value to default to
		 * @return the integer associated with the argument key
		 */
		public int getInt(String key, int def) {
			String str = this.getString(key);
			return ((str == null) ? def : Integer.parseInt(str));
		}
		
		/**
		 * Retrieve the integer associated with a given key. If no value is
		 * associated with the argument key, this method returns 0.
		 * @param key the key to get the integer for
		 * @return the integer associated with the argument key
		 */
		public long getLong(String key) {
			return this.getLong(key, 0);
		}
		
		/**
		 * Retrieve the integer associated with a given key. If no value is
		 * associated with the argument key, this method returns the argument
		 * default value.
		 * @param key the key to get the integer for
		 * @param def the value to default to
		 * @return the integer associated with the argument key
		 */
		public long getLong(String key, long def) {
			String str = this.getString(key);
			return ((str == null) ? def : Long.parseLong(str));
		}
		
		/**
		 * Retrieve the double associated with a given key. If no value is
		 * associated with the argument key, this method returns 0.
		 * @param key the key to get the double for
		 * @return the double associated with the argument key
		 */
		public double getDouble(String key) {
			return this.getDouble(key, 0);
		}
		
		/**
		 * Retrieve the double associated with a given key. If no value is
		 * associated with the argument key, this method returns the argument
		 * default value.
		 * @param key the key to get the double for
		 * @param def the value to default to
		 * @return the double associated with the argument key
		 */
		public double getDouble(String key, double def) {
			String str = this.getString(key);
			return ((str == null) ? def : Double.parseDouble(str));
		}
		
		/**
		 * Retrieve the string associated with a given key. If no value is
		 * associated with the argument key, this method returns null.
		 * @param key the key to get the string for
		 * @return the string associated with the argument key
		 */
		public String getString(String key) {
			return this.getString(key, null);
		}
		
		/**
		 * Retrieve the string associated with a given key. If no value is
		 * associated with the argument key, this method returns the argument
		 * default value.
		 * @param key the key to get the string for
		 * @param def the value to default to
		 * @return the string associated with the argument key
		 */
		public String getString(String key, String def) {
			if ((key = sanitizeKey(key)) == null)
				return null;
			else if (this.root == null)
				return this.getValue(key, def);
			else return this.root.getValue((this.prefix + "." + key), def);
		}
		private synchronized String getValue(String key, String def) {
			ValueTray vt = ((ValueTray) this.valueTrays.get(key));
			return ((vt == null) ? def : vt.value);
		}
		
		/**
		 * Check whether or not any keys are associated with values in this set
		 * or subset of process control data values.
		 * @return true if there are no keys, false otherwise
		 */
		public boolean isEmpty() {
			return (this.size() == 0);
		}
		
		/**
		 * Retrieve the number of keys associated with values in this set or
		 * subset of process control data values.
		 * @return the number of keys
		 */
		public int size() {
			return ((this.root == null) ? this.size(null) : this.root.size(this.prefix + "."));
		}
		private synchronized int size(String prefix) {
			if (prefix == null)
				return this.valueTrays.size();
			if (this.valueTrays.isEmpty())
				return 0;
			int ck = 0;
			for (Iterator kit = this.valueTrays.keySet().iterator(); kit.hasNext();) {
				String key = ((String) kit.next());
				if (key.startsWith(prefix))
					ck++;
			}
			return ck;
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		public String toString() {
			return ((this.root == null) ? this.toString(null) : this.root.toString(this.prefix + "."));
		}
		private synchronized String toString(String prefix) {
			if (this.valueTrays.isEmpty())
				return "{}";
			String[] keys = ((String[]) this.valueTrays.keySet().toArray(new String[this.valueTrays.size()]));
			Arrays.sort(keys);
			StringBuffer toStr = new StringBuffer();
			toStr.append("{");
			for (int k = 0, ck = 0; k < keys.length; k++) {
				if ((prefix == null) || keys[k].startsWith(prefix)) {
					ValueTray vt = ((ValueTray) this.valueTrays.get(keys[k]));
					if (ck++ != 0)
						toStr.append(", ");
					if (prefix == null)
						toStr.append(keys[k]);
					else toStr.append(keys[k].substring(prefix.length()));
					toStr.append("=");
					toStr.append(vt.value);
				}
				else if (0 < keys[k].compareTo(prefix)) // we're beyond anything that might start with given prefix
					break;
			}
			toStr.append("}");
			return toStr.toString();
		}
		
		/**
		 * Retrieve the keys associated with values in this set or subset of
		 * process control data values. The keys are in lexicographical order.
		 * @return an array holding the keys
		 */
		public String[] getKeys() {
			return ((this.root == null) ? this.getKeys(null) : this.root.getKeys(this.prefix + "."));
		}
		private synchronized String[] getKeys(String prefix) {
			String[] keys = ((String[]) this.valueTrays.keySet().toArray(new String[this.valueTrays.size()]));
			Arrays.sort(keys);
			if (prefix == null)
				return keys;
			int ck = 0;
			for (int k = 0; k < keys.length; k++) {
				if (keys[k].startsWith(prefix)) {
					if (ck < k)
						keys[ck++] = keys[k].substring(prefix.length());
				}
				else if (0 < keys[k].compareTo(prefix)) // we're beyond anything that might start with given prefix
					break;
			}
			return Arrays.copyOfRange(keys, 0, ck);
		}
		
		/**
		 * Associate an integer with a given key. If some other value is
		 * already associated with the argument key, this method returns that
		 * replaced value.
		 * @param key the key to associate the integer with
		 * @param value the integer to associate with the argument key
		 * @return the value previously associated with the argument key
		 */
		public String setInt(String key, int value) {
			return this.setString(key, ("" + value));
		}
		
		/**
		 * Associate an integer with a given key. If some other value is
		 * already associated with the argument key, this method returns that
		 * replaced value.
		 * @param key the key to associate the integer with
		 * @param value the integer to associate with the argument key
		 * @return the value previously associated with the argument key
		 */
		public String setLong(String key, long value) {
			return this.setString(key, ("" + value));
		}
		
		/**
		 * Associate an integer with a given key. If some other value is
		 * already associated with the argument key, this method returns that
		 * replaced value.
		 * @param key the key to associate the integer with
		 * @param value the integer to associate with the argument key
		 * @return the value previously associated with the argument key
		 */
		public String setDouble(String key, double value) {
			return this.setString(key, ("" + value));
		}
		
		/**
		 * Associate a string with a given key. If some other value is already
		 * associated with the argument key, this method returns that replaced
		 * value.
		 * @param key the key to associate the string with
		 * @param value the string to associate with the argument key
		 * @return the value previously associated with the argument key
		 */
		public String setString(String key, String value) {
			if ((key = sanitizeKey(key)) == null)
				return null;
			else if (this.root == null)
				return this.setValue(key, value);
			else return this.root.setValue((this.prefix + "." + key), value);
		}
		
		/**
		 * Clear the value associated with a given key. If a value other than a
		 * string was associated with the argument key, this method returns its
		 * string representation.
		 * @param key the key to clear
		 * @return the value previously associated with the argument key
		 */
		public String remove(String key) {
			if ((key = sanitizeKey(key)) == null)
				return null;
			else if (this.root == null)
				return this.setValue(key, null);
			else return this.root.setValue((this.prefix + "." + key), null);
		}
		
		/**
		 * Clear this set or subsubset of process control data values.
		 */
		public void clear() {
			if (this.root == null)
				this.clear(null);
			else this.root.clear(this.prefix + ".");
		}
		private synchronized void clear(String prefix) {
			boolean dirty = false;
			if (prefix == null) {
				dirty = (this.valueTrays.size() != 0);
				this.valueTrays.clear();
			}
			else for (Iterator kit = this.valueTrays.keySet().iterator(); kit.hasNext();) {
				String key = ((String) kit.next());
				if (key.startsWith(prefix)) {
					kit.remove();
					dirty = true;
				}
			}
			if (dirty)
				this.markDirty();
		}
		
		/**
		 * Clear all values last modified before a given pivot timestamp out of
		 * this set or subsubset of process control data values.
		 * @param pivotTime the timestamp to use as a pivot
		 */
		public void clearOlderThan(long pivotTime) {
			if (this.root == null)
				this.clearOlderThan("", pivotTime);
			else this.root.clearOlderThan((this.prefix + "."), pivotTime);
		}
		private synchronized void clearOlderThan(String prefix, long pivotTime) {
			boolean dirty = false;
			for (Iterator kit = this.valueTrays.keySet().iterator(); kit.hasNext();) {
				String key = ((String) kit.next());
				if (!key.startsWith(prefix))
					continue;
				ValueTray vt = ((ValueTray) this.valueTrays.get(key));
				if (vt.lastMod < pivotTime) {
					kit.remove();
					dirty = true;
				}
			}
			if (dirty)
				this.markDirty();
		}
		
		/**
		 * Persist the process control data set immediately. This offers a way
		 * for client code to indicate the end of a major operation and have
		 * the resulting data persisted right away, rather than waiting for the
		 * background process to persist it after some timeout.
		 */
		public void persist() {
			if (this.root == null) {
				if (!this.isClean() && (this.host != null))
					this.host.schedulePersistProcessControlData(this.dataId, 0);
			}
			else this.root.persist();
		}
		
		private static String sanitizeKey(String key) {
			if (key == null)
				return null;
			key = key.trim();
			if (key.length() == 0)
				return null;
			return key.replaceAll("[^0-9a-zA-Z\\_\\-]", "_");
		}
		
		private synchronized String setValue(String key, String value) {
			ValueTray vt = ((ValueTray) this.valueTrays.get(key));
			if (vt == null) {
				if (value != null) {
					vt = new ValueTray(value);
					this.valueTrays.put(key, vt);
					this.markDirty();
				}
				return null;
			}
			else {
				String old = vt.value;
				if (value == null) {
					this.valueTrays.remove(key);
					this.markDirty();
				}
				else if (!old.equals(value)) {
					vt.update(value);
					this.markDirty();
				}
				return old;
			}
		}
		
		private static class ValueTray {
			String value;
			long lastMod;
			ValueTray(String value) {
				this(value, System.currentTimeMillis());
			}
			ValueTray(String value, long lastMod) {
				this.value = value;
				this.lastMod = lastMod;
			}
			void update(String value) {
				this.value = value;
				this.lastMod = System.currentTimeMillis();
			}
		}
		
		private void markDirty() {
			this.modTime = System.currentTimeMillis();
			if (this.host != null)
				this.host.schedulePersistProcessControlData(this.dataId, (1000 * 60 * 2)); // schedule persisting in 2 minutes (barring further changes)
		}
		
		boolean isClean() {
			return (this.cleanModTime == this.modTime);
		}
		
		void setClean() {
			this.cleanModTime = this.modTime;
		}
		
		synchronized void writeTsv(BufferedWriter out) throws IOException {
			for (Iterator kit = this.valueTrays.keySet().iterator(); kit.hasNext();) {
				String key = ((String) kit.next());
				ValueTray vt = ((ValueTray) this.valueTrays.get(key));
				if (vt.value == null)
					continue; // simply omit nulls, it's the default anyway
				StringBuffer nValue = new StringBuffer();
				for (int c = 0; c < vt.value.length(); c++) {
					char ch = vt.value.charAt(c);
					if (StringUtils.SPACES.indexOf(ch) != -1) // make sure not to store any funny spaces ...
						nValue.append(' ');
					else if (ch < 33) // ... or control characters (line breaks and tabs would be detrimental ...)
						nValue.append(' ');
					else nValue.append(ch);
				}
				out.write(key + "\t" + nValue.toString().trim() + "\t" + vt.lastMod);
				out.newLine();
			}
		}
		
		static ProcessControlData readTsv(BufferedReader in, GoldenGatePCD host, String dataId) throws IOException {
			ProcessControlData pcd = new ProcessControlData(host, dataId);
			for (String line; (line = in.readLine()) != null;) {
				String[] data = line.split("\\t");
				if (data.length < 3)
					continue;
				String key = data[0];
				String value = data[1];
				long lastMod = Long.parseLong(data[2]);
				pcd.valueTrays.put(key, new ValueTray(value, lastMod));
			}
			return pcd;
		}
		
		/**
		 * Create a dummy process control data container for a given data
		 * object ID. Instances retrieved from this method are not persisted
		 * and should only be used for test purposes.
		 * @param dataId the data ID to tie the dummy instance to
		 * @return a dummy instance tied to the argument data object ID
		 */
		public static ProcessControlData createDummyInstance(String dataId) {
			return new ProcessControlData(null, dataId);
		}
	}
}
