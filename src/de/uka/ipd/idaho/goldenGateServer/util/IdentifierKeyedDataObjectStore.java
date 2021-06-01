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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.goldenGateServer.AsynchronousWorkQueue;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerActivityLogger;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent.ComponentActionConsole;

/**
 * Component for storing and retrieving data objects by their (UU)ID, named
 * GoldenGATE Identified Keyed Storage. On purpose, this component is not a
 * GoldenGateServerComponent, so every component requiring to store data
 * objects using their (UU)ID as the key can create their own instance without
 * incurring registry conflicts. This component can either manage single-file
 * data objects, or multi-file data objects held together in one dedicated
 * folder each. Either way, data objects are stored in a folder hierarchy whose
 * steps are taken from consecutive two-character prefixes of the data object
 * (UU)ID. When the number of files in one such folder exceeds the configured
 * threshold (512 by default), the depth of the hierarchy increases by one more
 * step, moving data objects transparent to client code.
 * 
 * @author sautter
 */
public class IdentifierKeyedDataObjectStore {
	private static final String PATH_FOLDER_MARKER_FILE_NAME = ".pathFolder";
	private static final String DELETED_ZIP_FILE_EXTENSION = ".zip.old";
	
	/**
	 * Exception indicating that a data object or a specific version of one
	 * does not exist in this data object store.
	 * 
	 * @author sautter
	 */
	public static class DataObjectNotFoundException extends IOException {
		protected DataObjectNotFoundException(String dataId) {
			this(dataId, 0);
		}
		protected DataObjectNotFoundException(String dataId, int version) {
			super("Invalid data object ID '" + dataId + "'" + ((version == 0) ? (".") : (", or version '" + version + "' does not exist.")));
		}
	}
	
	/**
	 * Input stream for reading data objects in file based mode. The output
	 * stream provides the total number of data object bytes via the
	 * <code>getDataObjectSize()</code> method. Instances of this class use a
	 * <code>BufferedInputStream</code> internally, so there is no need for
	 * external buffering.
	 * 
	 * @author sautter
	 */
	public class DataObjectInputStream extends InputStream {
		private String dataId;
		private int dataVersion;
		private boolean currentVersion;
		private int dataFileSize;
		private long dataLastModified;
		private BufferedInputStream in;
		private ZipFile zipFile;
		DataObjectInputStream(String dataId, File dataFile, int version) throws IOException {
			this.dataId = dataId;
			if (doVersioning) {
				this.dataVersion = ((version == 0) ? doGetCurrentVersion(this.dataId) : version);
				this.currentVersion = (version == 0);
			}
			else {
				this.dataVersion = 0;
				this.currentVersion = true;
			}
			if (dataFile.getName().endsWith(zipFileExtension)) {
				this.zipFile = new ZipFile(dataFile);
				ZipEntry zipEntry = ((ZipEntry) this.zipFile.entries().nextElement()); // make sure to position input at contained data
//				ZipInputStream would be more elegant, but fails to read entry properties like size ...
//				ZipInputStream zipIn = new ZipInputStream(new BufferedInputStream(new FileInputStream(dataFile)));
//				ZipEntry zipEntry = zipIn.getNextEntry(); // make sure to position input at contained data
				this.dataFileSize = ((int) zipEntry.getSize());
				this.dataLastModified = zipEntry.getTime();
//				this.in = new BufferedInputStream(zipIn);
				this.in = new BufferedInputStream(this.zipFile.getInputStream(zipEntry));
			}
			else {
				this.dataFileSize = ((int) dataFile.length());
				this.dataLastModified = dataFile.lastModified();
				this.in = new BufferedInputStream(new FileInputStream(dataFile));
			}
			registerDataObjectReference(this.dataId);
		}
		public int read() throws IOException {
			return this.in.read();
		}
		public int read(byte[] b) throws IOException {
			return this.in.read(b);
		}
		public int read(byte[] b, int off, int len) throws IOException {
			return this.in.read(b, off, len);
		}
		public long skip(long n) throws IOException {
			return this.in.skip(n);
		}
		public int available() throws IOException {
			return this.in.available();
		}
		public synchronized void mark(int readlimit) {
			this.in.mark(readlimit);
		}
		public synchronized void reset() throws IOException {
			this.in.reset();
		}
		public boolean markSupported() {
			return this.in.markSupported();
		}
		public void close() throws IOException {
			if (this.in == null)
				return;
			this.in.close();
			this.in = null;
			if (this.zipFile != null)
				this.zipFile.close();
			unregisterDataObjectReference(this.dataId);
		}
		
		protected void finalize() throws Throwable {
			this.close();
		}
		
		/**
		 * Retrieve the size of the underlying data object file, in bytes.
		 * @return the size of the underlying data object file
		 */
		public int getDataObjectSize() {
			return this.dataFileSize;
		}
		
		/**
		 * Retrieve the UTC timestamp of the last modification to the data
		 * object.
		 * @return the UTC timestamp of the last modification
		 */
		public long getLastModified() {
			return this.dataLastModified;
		}
		
		/**
		 * Check whether or not this input stream provides the current version
		 * of the underlying data object. If versioning is deactivated in the
		 * backing data object store, this method always returns true.
		 * @return true if the backing data object version is the current one
		 */
		public boolean isCurrentVersion() {
			return this.currentVersion;
		}
		
		/**
		 * Retrieve the number of the underlying data object version. If this
		 * input stream was obtained for version 0 (the current version), this
		 * method still returns the absolute version number. If versioning is
		 * deactivated in the backing data object store, this method always
		 * returns 0.
		 * @return the number of the backing data object version
		 */
		public int getVersion() {
			return this.dataVersion;
		}
	}
	
	/**
	 * Output stream for writing data objects in file based mode. After closing
	 * the output stream, the now-current version of the data object becomes
	 * available via the <code>getVersion()</code> method. Instances of this
	 * class use a <code>BufferedOutputStream</code> internally, so there is no
	 * need for external buffering.
	 * 
	 * @author sautter
	 */
	public class DataObjectOutputStream extends OutputStream {
		private File dataFile;
		private String dataId;
		private int exVersion;
		private File outFile;
		private BufferedOutputStream out;
		private int version = -1;
		DataObjectOutputStream(File dataFile, String dataId, int exVersion) throws IOException {
			this.dataFile = dataFile;
			this.dataId = dataId;
			this.exVersion = exVersion;
			this.outFile = new File(this.dataFile.getParentFile(), (this.dataId + dataFileExtension + ".new"));
			this.outFile.createNewFile();
			this.out = new BufferedOutputStream(new FileOutputStream(this.outFile));
			registerDataObjectReference(this.dataId);
		}
		
		public void write(int b) throws IOException {
			this.out.write(b);
		}
		public void write(byte[] b) throws IOException {
			this.out.write(b);
		}
		public void write(byte[] b, int off, int len) throws IOException {
			this.out.write(b, off, len);
		}
		public void flush() throws IOException {
			this.out.flush();
		}
		
		/* (non-Javadoc)
		 * @see java.io.FilterOutputStream#close()
		 */
		public void close() throws IOException {
			if (this.out == null)
				return; // closed before
			
			//	close underlying stream
			this.out.close();
			this.out = null;
			
			//	get parent folder and preserve data file path and name
			File parentFolder = this.dataFile.getParentFile();
			String dataFileName = this.dataFile.getAbsolutePath();
			
			//	restore any reversibly deleted previous versions to resurrect update history
			if (doVersioning && (this.exVersion == 0)) {
				this.exVersion = restoreDataObject(this.dataId, true);
				if (this.exVersion != 0)
					logger.logInfo("Data object '" + this.dataId + "' restored on re-creation");
			}
			
			//	make way if required
			if (this.dataFile.exists()) {
				
				//	rename existing file (if any) and zip it up
				if (doVersioning) {
					File exVersionFile = new File(parentFolder, (this.dataId + "." + this.exVersion + dataFileExtension));
					this.dataFile.renameTo(exVersionFile);
					
					//	zip previous version in separate thread
					scheduleMaintenanceJob(new OldVersionZipUp(exVersionFile.getName(), exVersionFile.getAbsolutePath()));
				}
				
				//	delete existing file
				else this.dataFile.delete();
			}
			
			//	switch output file live
			this.outFile.renameTo(new File(dataFileName));
			
			//	set data object version
			if (doVersioning) {
				this.version = (this.exVersion + 1);
				dataObjectVersionCache.put(this.dataId, new Integer(this.version));
			}
			
			//	free up data object reference
			unregisterDataObjectReference(this.dataId);
			
			//	schedule parent folder for reorganization check
			scheduleMaintenanceJob(new FolderReorganizationCheck(getFolderPath(parentFolder), parentFolder));
		}
		
		protected void finalize() throws Throwable {
			this.close();
		}
		
		/**
		 * Retrieve the now-current version of the data object after the stream
		 * is closed. Before that, the this method returns -1, just as it does
		 * if file versioning is deactivated.
		 * @return the now-current version of the written data object
		 */
		public int getVersion() {
			return this.version;
		}
	}
	
	/**
	 * File object representing the storage folder of a data object in folder
	 * based mode. This class keeps track of folder creation and data object
	 * usage. Client code is recommended to call the <code>close()</code>
	 * method when done using instances of this class.
	 * 
	 * @author sautter
	 */
	public class DataObjectFolder extends File {
		DataObjectFolder(File parentFolder, String dataId) {
			super(parentFolder, dataId);
			registerDataObjectReference(dataId);
		}
		
		/**
		 * Check whether or not the data object folder exists, observing any
		 * deletions and restorations in progress.
		 * @see java.io.File#exists()
		 */
		public boolean exists() {
			
			//	wait for any deletions and restorations in progress
			while (removingDataIDs.contains(this.getName()) || restoringDataIDs.contains(this.getName())) try {
				Thread.sleep(1 * 1000);
			} catch (InterruptedException ie) {}
			
			//	check underlying folder in file system
			return super.exists();
		}
		boolean existsForDeleteRestore() {
			return super.exists(); // need to prevent loop and waiting on deletion and restoration
		}
		
		/**
		 * Creates the data object storage folder. If a data object with the ID
		 * handed to the constructor existed before and was reversibly deleted
		 * (not irreversibly destroyed), it will be restored.
		 * @see java.io.File#mkdir()
		 * @see de.uka.ipd.idaho.goldenGateServer.util.IdentifierKeyedDataObjectStore#deleteDataObject(String)
		 * @see de.uka.ipd.idaho.goldenGateServer.util.IdentifierKeyedDataObjectStore#destroyDataObject(String)
		 * @see de.uka.ipd.idaho.goldenGateServer.util.IdentifierKeyedDataObjectStore#restoreDataObject(String)
		 */
		public boolean mkdir() {
			if (this.exists() && this.isDirectory())
				return false;
			if (this.restoreForCreation())
				return true;
			boolean created = super.mkdir();
			if (created) {
				File parentFolder = this.getParentFile();
				scheduleMaintenanceJob(new FolderReorganizationCheck(getFolderPath(parentFolder), parentFolder));
			}
			return created;
		}
		private boolean restoreForCreation() {
			File deletedZipFile = new File(this.getParentFile(), (this.getName() + DELETED_ZIP_FILE_EXTENSION));
			if (deletedZipFile.exists()) try {
				restoreDataObject(this.getName(), true);
				logger.logInfo("Data object '" + this.getName() + "' restored on re-creation");
				return true;
			}
			catch (Exception e) {
				logger.logError("Error restoring data object '" + this.getName() + "' on re-creation: " + e.getMessage());
				logger.logError(e);
			}
			return false;
		}
		boolean mkdirForRestore() {
			return super.mkdir(); // no need for reorganization check on restoring, and need to prevent loop
		}
		
		/**
		 * Creates the data object storage folder as well as any required
		 * parent folders. Via the parent implementation's delegate to
		 * <code>mkdir()</code>, a previously deleted (not irreversibly
		 * destroyed) data object with the same ID as the one handed to the
		 * constructor will be restored.
		 * @see java.io.File#mkdirs()
		 * @see de.uka.ipd.idaho.goldenGateServer.util.IdentifierKeyedDataObjectStore#deleteDataObject(String)
		 * @see de.uka.ipd.idaho.goldenGateServer.util.IdentifierKeyedDataObjectStore#destroyDataObject(String)
		 * @see de.uka.ipd.idaho.goldenGateServer.util.IdentifierKeyedDataObjectStore#restoreDataObject(String)
		 */
		public boolean mkdirs() {
			return super.mkdirs();
		}
		
		/**
		 * Close the data object folder, notifying the storage manager that the
		 * reference to the data object has been discarded.
		 */
		public void close() {
			if (this.closed)
				return;
			unregisterDataObjectReference(this.getName());
			this.closed = true;
		}
		private boolean closed = false;
		
		protected void finalize() throws Throwable {
			this.close();
		}
	}
	
	private class MaintenanceWorkerThread extends Thread {
		private boolean run = true;
		boolean wait = true;
		MaintenanceWorkerThread(String name) {
			super(name + "MaintenanceWorker");
		}
		public void run() {
			
			//	give the others a little time to come up
			try {
				sleep(5 * 1000);
			} catch (InterruptedException ie) {}
			
			//	keep going as long as there is work
			while (this.run) {
				
				//	get next job
				MaintenanceJob job;
				synchronized (maintenanceQueue) {
					if (maintenanceQueue.isEmpty()) try {
						maintenanceQueue.wait();
					} catch (InterruptedException ie) {}
					if (maintenanceQueue.isEmpty())
						continue;
					job = ((MaintenanceJob) maintenanceQueue.removeFirst());
				}
				
				//	execute job
				try {
					logger.logInfo(this.getName() + ": performing " + job.name);
					long start = System.currentTimeMillis();
					boolean done = job.doMaintenance();
					long time = (System.currentTimeMillis() - start);
					if (done)
						logger.logInfo(this.getName() + ": " + job.name + " done in " + time + "ms");
					else {
						(new MaintenanceJobRescheduler(job)).start(); // re-enqueue job for another attempt in 10 seconds
						logger.logInfo(this.getName() + ": " + job.name + " suspended and re-scheduled after " + time + "ms");
					}
				}
				catch (Exception e) {
					logger.logError("Exception performing " + job.name + ": " + e.getMessage());
					logger.logError(e);
				}
				catch (Throwable t) {
					logger.logError("Error performing " + job.name + ": " + t.getMessage());
					logger.logError(t);
				}
				
				//	stop hurrying if job queue empty (no need for synchronizing here)
				if (!this.wait && maintenanceQueue.isEmpty())
					this.wait = true;
				
				//	give the others a little time
				if (this.wait) try {
					sleep(1 * 1000);
				} catch (InterruptedException ie) {}
			}
		}
		void shutdown() {
			this.run = false;
			ArrayList mjrs;
			synchronized (maintenanceJobReschedulers) {
				mjrs = new ArrayList(maintenanceJobReschedulers);
				maintenanceJobReschedulers.clear();
			}
			for (int r = 0; r < mjrs.size(); r++)
				((MaintenanceJobRescheduler) mjrs.get(r)).interrupt();
			synchronized (maintenanceQueue) {
				maintenanceQueue.clear();
				maintenanceQueue.notify();
			}
			this.interrupt();
		}
		void setWaitAfterJobs(boolean wait, ComponentActionConsole cac) {
			if (this.wait == wait)
				cac.reportError("Already " + (this.wait ? "dragging" : "hurrying") + " jobs");
			else {
				this.wait = wait;
				cac.reportResult("Started " + (this.wait ? "dragging" : "hurrying") + " jobs");
			}
		}
	}
	
	private class MaintenanceJobRescheduler extends Thread {
		private MaintenanceJob job;
		private String parentName;
		MaintenanceJobRescheduler(MaintenanceJob job) {
			this.job = job;
			this.parentName = maintenanceWorker.getName();
			synchronized (maintenanceJobReschedulers) {
				maintenanceJobReschedulers.add(this);
			}
		}
		public void run() {
			
			//	wait until re-scheduling due (some 10 seconds for now)
			try {
				sleep(10 * 1000);
			}
			catch (InterruptedException ie) {
				if (this.job != null)
					logger.logInfo(this.parentName + ": re-scheduling of " + this.job.name + " canceled on shutdown");
				return;
			}
			
			//	re-schedule job
			this.rescheduleMaintenanceJob();
			
			//	we're done
			synchronized (maintenanceJobReschedulers) {
				maintenanceJobReschedulers.remove(this);
			}
		}
		synchronized void rescheduleMaintenanceJob() {
			if (this.job == null)
				return;
			scheduleMaintenanceJob(this.job);
			logger.logInfo(this.parentName + ": " + this.job.name + " re-scheduled");
			this.job = null;
		}
	}
	
	private abstract class MaintenanceJob {
		final String name;
		MaintenanceJob(String name) {
			this.name = name;
		}
		public abstract boolean doMaintenance() throws Exception;
	}
	
	private class FolderReorganizationCheck extends MaintenanceJob {
		private String folderPath;
		private File folder;
		FolderReorganizationCheck(String folderPath, File folder) {
			super("Reorganization Check on " + folderPath);
			this.folderPath = folderPath;
			this.folder = folder;
		}
		public boolean doMaintenance() throws Exception {
			checkFolderReorganization(this.folderPath, this.folder);
			return true;
		}
	}
	
	private class FolderReorganization extends MaintenanceJob {
		private String folderPath;
		private File folder;
		private boolean isContinuation;
		FolderReorganization(String folderPath, File folder, boolean isContinuation) {
			super("Reorganization of " + folderPath);
			this.folderPath = folderPath;
			this.folder = folder;
			this.isContinuation = isContinuation;
		}
		public boolean doMaintenance() throws Exception {
			return reorganizeFolder(this.folderPath, this.folder, this.isContinuation);
		}
	}
	
	private class OldVersionZipUp extends MaintenanceJob {
		private String filePathAndName;
		OldVersionZipUp(String fileName, String filePathAndName) {
			super("Zip-Up of " + fileName);
			this.filePathAndName = filePathAndName;
		}
		public boolean doMaintenance() throws Exception {
			zipDataFile(this.filePathAndName);
			return true;
		}
	}
	
	private File rootFolder;
	private String name;
	private int minFolderDepth;
	private int maxFolderObjects;
	private String dataFileExtension;
	private String zipFileExtension;
	private boolean doVersioning;
	private boolean dataObjectsAreFiles;
	
	private Map dataObjectVersionCache = Collections.synchronizedMap(new LinkedHashMap(16, 0.75f, true) {
		protected boolean removeEldestEntry(Entry eldest) {
			return (this.size() > 8192);
		}
	});
	
	private CountingSet dataObjectReferences = new CountingSet(new LinkedHashMap());
	private LinkedHashMap lastDataObjectReferences = new LinkedHashMap();
	
	private MaintenanceWorkerThread maintenanceWorker;
	private LinkedList maintenanceQueue = new LinkedList() {
		private HashSet jobNames = new HashSet(); // keeps us duplicate free
		public Object removeFirst() {
			Object first = super.removeFirst();
			this.jobNames.remove(((MaintenanceJob) first).name);
			return first;
		}
		public void addLast(Object obj) {
			if (this.jobNames.add(((MaintenanceJob) obj).name))
				super.addLast(obj);
		}
		public void clear() {
			this.jobNames.clear();
			super.clear();
		}
	};
	private HashSet maintenanceJobReschedulers = new HashSet();
	private AsynchronousWorkQueue maintenanceQueueMonitor;
	
	private Set removingDataIDs = Collections.synchronizedSet(new HashSet());
	private Set restoringDataIDs = Collections.synchronizedSet(new HashSet());
	private Set zippingFileNames = Collections.synchronizedSet(new HashSet());
	private Set reorganizingFolderPaths = Collections.synchronizedSet(new HashSet());
	
	private GoldenGateServerActivityLogger logger;
	
	/** Constructor
	 * @param name a name for the data object store, for distinguishing instances in log and console (defaults to root folder name if null)
	 * @param rootFolder the root folder of the storage hierarchy
	 * @param logger logger for background maintenance activity
	 */
	public IdentifierKeyedDataObjectStore(String name, File rootFolder, GoldenGateServerActivityLogger logger) {
		this(name, rootFolder, null, false, logger);
	}
	
	/** Constructor
	 * @param name a name for the data object store, for distinguishing instances in log and console (defaults to root folder name if null)
	 * @param rootFolder the root folder of the storage hierarchy
	 * @param dataFileExtension the file extension for data files (null indicates folder based data objects)
	 * @param logger logger for background maintenance activity
	 */
	public IdentifierKeyedDataObjectStore(String name, File rootFolder, String dataFileExtension, GoldenGateServerActivityLogger logger) {
		this(name, rootFolder, dataFileExtension, (dataFileExtension != null), logger);
	}
	
	/** Constructor
	 * @param name a name for the data object store, for distinguishing instances in log and console (defaults to root folder name if null)
	 * @param rootFolder the root folder of the storage hierarchy
	 * @param dataFileExtension the file extension for data files (null indicates folder based data objects)
	 * @param doVersioning keep previous versions of data objects available (in file based mode only)?
	 * @param logger logger for background maintenance activity
	 */
	public IdentifierKeyedDataObjectStore(String name, File rootFolder, String dataFileExtension, boolean doVersioning, GoldenGateServerActivityLogger logger) {
		this(name, rootFolder, 2 /* need this to accommodate existing DST and IMS folder structures */, 256 /* looks good after extensive Googling */, dataFileExtension, doVersioning, logger);
	}
	
	/** Constructor
	 * @param name a name for the data object store, for distinguishing instances in log and console (defaults to root folder name if null)
	 * @param rootFolder the root folder of the storage hierarchy
	 * @param minFolderDepth the minimum number of path steps before actual data (must be at least 0 and at most 16)
	 * @param maxFolderObjects the maximum number of data objects in a folder before adding a path step to the hierarchy (must be 256 or larger)
	 * @param logger logger for background maintenance activity
	 */
	public IdentifierKeyedDataObjectStore(String name, File rootFolder, int minFolderDepth, int maxFolderObjects, GoldenGateServerActivityLogger logger) {
		this(name, rootFolder, minFolderDepth, maxFolderObjects, null, false, logger);
	}
	
	/** Constructor
	 * @param name a name for the data object store, for distinguishing instances in log and console (defaults to root folder name if null)
	 * @param rootFolder the root folder of the storage hierarchy
	 * @param minFolderDepth the minimum number of path steps before actual data (must be at least 0 and at most 16)
	 * @param maxFolderObjects the maximum number of data objects in a folder before adding a path step to the hierarchy (must be 256 or larger)
	 * @param dataFileExtension the file extension for data files (null indicates folder based data objects)
	 * @param logger logger for background maintenance activity
	 */
	public IdentifierKeyedDataObjectStore(String name, File rootFolder, int minFolderDepth, int maxFolderObjects, String dataFileExtension, GoldenGateServerActivityLogger logger) {
		this(name, rootFolder, minFolderDepth, maxFolderObjects, dataFileExtension, (dataFileExtension != null), logger);
	}
	
	/** Constructor
	 * @param name a name for the data object store, for distinguishing instances in log and console (defaults to root folder name if null)
	 * @param rootFolder the root folder of the storage hierarchy
	 * @param minFolderDepth the minimum number of path steps before actual data (must be at least 0 and at most 16)
	 * @param maxFolderObjects the maximum number of data objects in a folder before adding a path step to the hierarchy (must be 256 or larger)
	 * @param dataFileExtension the file extension for data files (null indicates folder based data objects)
	 * @param doVersioning keep previous versions of data objects available (in file based mode only)?
	 * @param logger logger for background maintenance activity
	 */
	public IdentifierKeyedDataObjectStore(String name, File rootFolder, int minFolderDepth, int maxFolderObjects, String dataFileExtension, boolean doVersioning, GoldenGateServerActivityLogger logger) {
		this.name = ((name == null) ? rootFolder.getName() : name);
		this.rootFolder = rootFolder;
		this.rootFolder.mkdirs();
		if (minFolderDepth < 0)
			throw new IllegalArgumentException("Minimum folder depth must be 0 or larger");
		if (minFolderDepth > 16)
			throw new IllegalArgumentException("Minimum folder depth must be 16 or less");
		this.minFolderDepth = minFolderDepth;
		if (maxFolderObjects < 256)
			throw new IllegalArgumentException("Maximum number of objects in folder must be 256 or larger");
		this.maxFolderObjects = maxFolderObjects;
		if (dataFileExtension == null) {
			this.dataFileExtension = "";
			this.zipFileExtension = "";
			this.doVersioning = false;
			this.dataObjectsAreFiles = false;
		}
		else {
			if (!dataFileExtension.startsWith("."))
				dataFileExtension = ("." + dataFileExtension);
			this.dataFileExtension = dataFileExtension;
			this.zipFileExtension = (dataFileExtension + ".zip");
			this.doVersioning = doVersioning;
			this.dataObjectsAreFiles = true;
		}
		this.logger = ((logger == null) ? GoldenGateServerActivityLogger.sysOut : logger);
		
		//	create and start maintenance worker
		this.maintenanceWorker = new MaintenanceWorkerThread(this.name);
		this.maintenanceWorker.start();
		
		//	link up to monitoring
		this.maintenanceQueueMonitor = new AsynchronousWorkQueue(this.name) {
			public String getStatus() {
				return (this.name + ": " + maintenanceQueue.size() + " maintenance jobs pending" + (IdentifierKeyedDataObjectStore.this.maintenanceWorker.wait ? "" : " (HURRYING)") + ", " + maintenanceJobReschedulers.size() + " ones deferred");
			}
		};
		
		//	create backup action
		this.backupAction = new AsynchronousConsoleAction("backup", "Backup data object archive of this GoldenGATE IKS", "collection", null, null) {
			protected String getActionName() {
				return (IdentifierKeyedDataObjectStore.this.name + "." + super.getActionName());
			}
			protected void performAction(String[] arguments) throws Exception {
				String backupName = ("Backup." + backupTimestamper.format(new Date()) + ".zip");
				
				String target = ((arguments.length == 0) ? null : arguments[0]);
				File backupFile;
				if (target == null)
					backupFile = new File(IdentifierKeyedDataObjectStore.this.rootFolder, backupName);
				else if ((target.indexOf(':') == -1) && !target.startsWith("/"))
					backupFile = new File(new File(IdentifierKeyedDataObjectStore.this.rootFolder, target), backupName);
				else backupFile = new File(target, backupName);
				
				boolean full = ((arguments.length < 2) || "-f".equals(arguments[1]));
				
				this.log(IdentifierKeyedDataObjectStore.this.name + ": start backing up data object archive ...");
				
				//	collect files to add to backup
				ArrayList backupFileNames = new ArrayList();
				ArrayList folderList = new ArrayList();
				folderList.add(IdentifierKeyedDataObjectStore.this.rootFolder);
				
				//	process folders
				for (int f = 0; f < folderList.size(); f++) {
					File folder = ((File) folderList.get(f));
					String folderPath = getFolderPath(folder);
					
					//	path folder, process recursively
					if (isPathFolder(folder, folderPath)) {
						File[] files = folder.listFiles(new FileFilter() {
							public boolean accept(File file) {
								return (file.isDirectory() && (file.getName().matches("[0-9A-Fa-f]{2}")));
							}
						});
						folderList.addAll(Arrays.asList(files));
					}
					
					//	use list of data files in file based mode
					else if (dataObjectsAreFiles) {
						File[] dataFiles = folder.listFiles(new FileFilter() {
							public boolean accept(File file) {
								return (true
									&& file.isFile()
									&& (file.getName().endsWith(IdentifierKeyedDataObjectStore.this.dataFileExtension) || file.getName().endsWith(IdentifierKeyedDataObjectStore.this.zipFileExtension))
									&& file.getName().matches("[0-9A-Fa-f]{32}\\..*")
								);
							}
						});
						for (int d = 0; d < dataFiles.length; d++) {
							String dataFileName = dataFiles[d].getName();
							if (dataFileName.endsWith(IdentifierKeyedDataObjectStore.this.dataFileExtension)) { /* current data object version */}
							else if (full && dataFileName.endsWith(IdentifierKeyedDataObjectStore.this.zipFileExtension)) { /* (zipped) older data object version, only include in full backup */}
							else continue; // skip anything else
							backupFileNames.add(folderPath + "/" + dataFileName);
						}
					}
					
					//	use list of data folders and recurse in folder based mode
					else {
						File[] dataFolders = folder.listFiles(new FileFilter() {
							public boolean accept(File file) {
								return (file.isDirectory() && file.getName().matches("[0-9A-Fa-f]{32}"));
							}
						});
						for (int d = 0; d < dataFolders.length; d++) {
							File[] dataFiles = dataFolders[d].listFiles();
							for (int df = 0; df < dataFiles.length; df++)
								backupFileNames.add(folderPath + "/" + dataFolders[d].getName() + "/" + dataFiles[df].getName());
						}
					}
				}
				this.log(" - got " + backupFileNames.size() + " files to backup.");
				this.enteringMainLoop("0 of " + backupFileNames.size() + " data object files added to backup.");
				
				//	create backup file
				backupFile.getParentFile().mkdirs();
				backupFile.createNewFile();
				ZipOutputStream backup = new ZipOutputStream(new FileOutputStream(backupFile));
				
				//	add data object files to backup
				for (int f = 0; this.continueAction() && (f < backupFileNames.size()); f++) {
					String backupEntryFileName = ((String) backupFileNames.get(f));
					File backupEntryFile = new File(IdentifierKeyedDataObjectStore.this.rootFolder, backupEntryFileName);
					InputStream backupEntrySource = new BufferedInputStream(new FileInputStream(backupEntryFile));
					
					ZipEntry backupEntry = new ZipEntry(backupEntryFileName);
					backupEntry.setSize(backupEntryFile.length());
					backupEntry.setTime(backupEntryFile.lastModified());
					backup.putNextEntry(backupEntry);
					
					byte[] buffer = new byte[1024];
					int read;
					while ((read = backupEntrySource.read(buffer)) != -1)
						backup.write(buffer, 0, read);
					
					backup.closeEntry();
					backupEntrySource.close();
					
					this.loopRoundComplete((f+1) + " of " + backupFileNames.size() + " data object files added to backup.");
				}
				
				//	finalize backup
				backup.flush();
				backup.close();
			}
			protected String[] getArgumentNames() {
				if (IdentifierKeyedDataObjectStore.this.dataObjectsAreFiles) {
					String[] argumentNames = {"target", "mode"};
					return argumentNames;
				}
				else {
					String[] argumentNames = {"target"};
					return argumentNames;
				}
			}
			protected String[] getArgumentExplanation(String argument) {
				if ("target".equals(argument)) {
					String[] explanation = {
							"the file to write the backup to (an absolute file path or a relative file path (relative to the archive root), will backup to archive root if not specified)"
						};
					return explanation;
				}
				else if (IdentifierKeyedDataObjectStore.this.dataObjectsAreFiles && "mode".equals(argument)) {
					String[] explanation = {
							"the backup mode, specifying what to include in the backup:",
							"'-c': backup of current data object versions, the default",
							"'-f': full backup including all versions of all data objects"
						};
					return explanation;
				}
				else return super.getArgumentExplanation(argument);
			}
			protected String checkArguments(String[] arguments) {
				if (arguments.length < 2)
					return null;
				else if (IdentifierKeyedDataObjectStore.this.dataObjectsAreFiles) {
					if (arguments.length > 2)
						return "Specify only the target and mode.";
					else if ("-c".equals(arguments[1]) || "-f".equals(arguments[1]))
						return null;
					else return ("Invalid backup mode '" + arguments[1] + "', use '-c' and '-f' only.");
				}
				else return "Specify only the target.";
			}
		};	
	}
	
	/**
	 * Shut down the data object store, in particular terminating the internal
	 * maintenance background thread.
	 */
	public void shutdown() {
		if (this.maintenanceQueueMonitor != null)
			this.maintenanceQueueMonitor.dispose();
		this.maintenanceQueueMonitor = null;
		if (this.maintenanceWorker != null)
			this.maintenanceWorker.shutdown();
		this.maintenanceWorker = null;
	}
	
	private void rescheduleMaintenanceJobs() {
		ArrayList mjrs;
		synchronized (this.maintenanceJobReschedulers) {
			mjrs = new ArrayList(this.maintenanceJobReschedulers);
			this.maintenanceJobReschedulers.clear();
		}
		for (int r = 0; r < mjrs.size(); r++) {
			MaintenanceJobRescheduler mjr = ((MaintenanceJobRescheduler) mjrs.get(r));
			mjr.rescheduleMaintenanceJob();
			mjr.interrupt();
		}
	}
	
	private void scheduleMaintenanceJob(MaintenanceJob job) {
		synchronized (this.maintenanceQueue) {
			this.maintenanceQueue.addLast(job);
			this.maintenanceQueue.notify();
		}
	}
	
	private void registerDataObjectReference(String dataId) {
		synchronized (this.dataObjectReferences) {
			this.dataObjectReferences.add(dataId);
			this.lastDataObjectReferences.put(dataId, new Long(System.currentTimeMillis()));
		}
	}
	private void unregisterDataObjectReference(String dataId) {
		synchronized (this.dataObjectReferences) {
			if (this.dataObjectReferences.remove(dataId))
				this.lastDataObjectReferences.remove(dataId);
		}
	}
	private boolean hasDataObjectReference(String dataId) {
		synchronized (this.dataObjectReferences) {
			return this.dataObjectReferences.contains(dataId);
		}
	}
	
	private void registerReorganizingFolderPath(String folderPath) {
		synchronized (this.reorganizingFolderPaths) {
			this.reorganizingFolderPaths.add(folderPath);
		}
	}
	private void unregisterReorganizingFolderPath(String folderPath) {
		synchronized (this.reorganizingFolderPaths) {
			this.reorganizingFolderPaths.remove(folderPath);
		}
	}
	private boolean isReorganizingFolderPath(String folderPath) {
		synchronized (this.reorganizingFolderPaths) {
			return this.reorganizingFolderPaths.contains(folderPath);
		}
	}
	
	private static final SimpleDateFormat backupTimestamper = new SimpleDateFormat("yyyyMMdd-HHmm");
	private AsynchronousConsoleAction backupAction;
	
	private static final String CHECK_FOLDER_REORGANIZATION_COMMAND = "checkFolderReorg";
	private static final String MARK_PATH_FOLDERS_COMMAND = "markPathFolders";
	private static final String ZIP_OLD_VERSIONS_COMMAND = "zipOldVersions";
	private static final String SHOW_REFERENCES_COMMAND = "showRefs";
	private static final String CLEAR_REFERENCES_COMMAND = "clearRefs";
	private static final String JOB_QUEUE_SIZE_COMMAND = "jobsPending";
	private static final String RESCHEDULE_JOBS_COMMAND = "rescheduleJobs";
	private static final String HURRY_JOBS_COMMAND = "hurryJobs";
	private static final String DRAG_JOBS_COMMAND = "dragJobs";
	
	//	TODO make commands public
	
	//	TODO provide getActions() method taking mapping of generic default commands to custom names and explanations
	
	/**
	 * Retrieve the actions for accessing and managing the data object store
	 * from the component server console inside the scope of a host server
	 * component. Access though network is not possible without a respective
	 * host server component, and is not intended to be.
	 * @return an array holding actions for accessing and managing the data
	 *         object store from the component server console
	 */
	public ComponentActionConsole[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentActionConsole ca;
		
		//	backup data object archive
		cal.add(this.backupAction);
		
		//	add command triggering depth analysis and reorganization
		ca = new ComponentActionConsole() {
			private Thread checker = null;
			public String getActionCommand() {
				return CHECK_FOLDER_REORGANIZATION_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						CHECK_FOLDER_REORGANIZATION_COMMAND,
						"Check data folders for reorganization, i.e., if they contain too many data objects."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length != 0)
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
				else if (this.checker != null)
					this.reportError(" Already checking data folders for reorganization");
				else {
					final ComponentActionConsole cac = this;
					this.checker = new Thread() {
						public void run() {
							try {
								checkFolderReorganization(cac);
							}
							finally {
								checker = null;
							}
						}
					};
					this.checker.start();
				}
			}
		};
		cal.add(ca);
		
		//	add action marking path folders in existing repos
		ca = new ComponentActionConsole() {
			private Thread marker = null;
			public String getActionCommand() {
				return MARK_PATH_FOLDERS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						MARK_PATH_FOLDERS_COMMAND,
						"Mark path folders, i.e., the ones not directly containing data objects."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length != 0)
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
				else if (this.marker != null)
					this.reportError(" Already marking path folders");
				else {
					final ComponentActionConsole cac = this;
					this.marker = new Thread() {
						public void run() {
							try {
								markPathFolders(cac);
							}
							finally {
								marker = null;
							}
						}
					};
					this.marker.start();
				}
			}
		};
		cal.add(ca);
		
		//	zip up older versions of data object files (only in file based mode)
		if (this.dataObjectsAreFiles) {
			ca = new ComponentActionConsole() {
				private Thread zipper = null;
				public String getActionCommand() {
					return ZIP_OLD_VERSIONS_COMMAND;
				}
				public String[] getExplanation() {
					String[] explanation = {
							ZIP_OLD_VERSIONS_COMMAND,
							"Zip up all but the current version of each data object file."
						};
					return explanation;
				}
				public void performActionConsole(String[] arguments) {
					if (arguments.length != 0)
						this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
					else if (this.zipper != null)
						this.reportError(" Already zipping up old data object versions");
					else {
						final ComponentActionConsole cac = this;
						this.zipper = new Thread() {
							public void run() {
								try {
									zipOldVersions(cac);
								}
								finally {
									zipper = null;
								}
							}
						};
						this.zipper.start();
					}
				}
			};
			cal.add(ca);
		}
		
		//	display open references to data objects (they block reorganization)
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return SHOW_REFERENCES_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						SHOW_REFERENCES_COMMAND,
						"Show open references to data objects in this store."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length != 0)
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
				else {
					this.reportResult("There are currently references to " + dataObjectReferences.elementCount() + " data objects:");
					long time = System.currentTimeMillis();
					for (Iterator doidit = dataObjectReferences.iterator(); doidit.hasNext();) {
						String dataId = ((String) doidit.next());
						Long lastOpened = ((Long) lastDataObjectReferences.get(dataId));
						this.reportResult(" - " + dataId + ": " + dataObjectReferences.getCount(dataId) + " refs" + ((lastOpened == null) ? "" : (", last opened " + (time - lastOpened.longValue()) + "ms ago")));
					}
				}
			}
		};
		cal.add(ca);
		
		//	clear old references (more for runtime trouble shooting than anything)
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return CLEAR_REFERENCES_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						CLEAR_REFERENCES_COMMAND,
						"Clear open references to data objects in this store (USE WITH CARE)."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length != 0)
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
				else {
					dataObjectReferences.clear();
					lastDataObjectReferences.clear();
					this.reportResult("Open references cleared.");
				}
			}
		};
		cal.add(ca);
		
		//	show number of pending maintenance jobs
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return JOB_QUEUE_SIZE_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						JOB_QUEUE_SIZE_COMMAND,
						"Show the number of pending background maintenance jobs."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0)
					this.reportResult("There are currently " + maintenanceQueue.size() + " pending maintenance jobs.");
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	reschedule any deferred maintenance jobs right away
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return RESCHEDULE_JOBS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						RESCHEDULE_JOBS_COMMAND,
						"Re-schedule any deferred background maintenance jobs right away."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0)
					rescheduleMaintenanceJobs();
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	refrain from waiting between two maintenance jobs
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return HURRY_JOBS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						HURRY_JOBS_COMMAND,
						"Work off pending background maintenance jobs as fast as possible."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0)
					maintenanceWorker.setWaitAfterJobs(false, this);
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	clear old references (more for runtime trouble shooting than anything)
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return DRAG_JOBS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						DRAG_JOBS_COMMAND,
						"Work off pending background maintenance jobs at normal speed."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0)
					maintenanceWorker.setWaitAfterJobs(true, this);
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	finally ...
		return ((ComponentActionConsole[]) cal.toArray(new ComponentActionConsole[cal.size()]));
	}
	
	private void markPathFolders(ComponentActionConsole cac) {
		cac.reportResult(this.name + ": start marking path folders ...");
		ArrayList folderList = new ArrayList();
		folderList.add(this.rootFolder);
		int markFolderCount = 0;
		
		//	process folders
		for (int f = 0; f < folderList.size(); f++) {
			File folder = ((File) folderList.get(f));
			File[] files = folder.listFiles();
			if (files.length == 0)
				continue;
			boolean isDataFolder = false;
			if (!this.rootFolder.getName().equals(folder.getName())) // root folder can contain backups, etc.
				for (int cf = 0; cf < files.length; cf++) {
					if (PATH_FOLDER_MARKER_FILE_NAME.equals(files[cf].getName()))
						continue; // ignore path folder marker
					if (files[cf].isDirectory() && (files[cf].getName().matches("[0-9A-Fa-f]{2}")))
						continue; // next path steps
					isDataFolder = true;
					break;
				}
			if (isDataFolder)
				continue; // we're only after path folders
			
			//	check marker file, create it if absent
			String folderPath = this.getFolderPath(folder);
			if ((new File(folder, PATH_FOLDER_MARKER_FILE_NAME)).exists())
				this.pathFolderPaths.add(folderPath);
			else try {
				this.markPathFolder(folder, folderPath);
				markFolderCount++;
				cac.reportResult(" - marked path folder " + folderPath);
			}
			catch (IOException ioe) {
				cac.reportError("Error marking path folder " + folderPath + ":" + ioe.getMessage());
				cac.reportError(ioe);
			}
			
			//	process children recursively
			for (int cf = 0; cf < files.length; cf++) {
				if (files[cf].isDirectory() && (files[cf].getName().matches("[0-9A-Fa-f]{2}")))
					folderList.add(files[cf]);
			}
		}
		cac.reportResult("Marked " + markFolderCount + " path folders.");
	}
	
	private void checkFolderReorganization(ComponentActionConsole cac) {
		cac.reportResult(this.name + ": start checking folders for reorganization ...");
		ArrayList folderList = new ArrayList();
		folderList.add(this.rootFolder);
		int checkFolderCount = 0;
		
		//	process folders
		for (int f = 0; f < folderList.size(); f++) {
			File folder = ((File) folderList.get(f));
			String folderPath = this.getFolderPath(folder);
			
			//	path folder, process recursively
			if (this.isPathFolder(folder, folderPath)) {
				File[] files = folder.listFiles(new FileFilter() {
					public boolean accept(File file) {
						return (file.isDirectory() && file.getName().matches("[0-9A-Fa-f]{2}"));
					}
				});
				folderList.addAll(Arrays.asList(files));
			}
			
			//	check list of data files
			else {
				this.scheduleMaintenanceJob(new FolderReorganizationCheck(folderPath, folder));
				cac.reportResult(" - " + folderPath);
				checkFolderCount++;
			}
		}
		cac.reportResult("Scheduled " + checkFolderCount + " folders for reorganization checks.");
	}
	
	private void checkFolderReorganization(String folderPath, File folder) {
		if (this.isPathFolder(folder, folderPath))
			return; // this one has already been reorganized
		File[] files = folder.listFiles(); // also include '<dataId>.zip.old' files
		if (files.length > this.maxFolderObjects) {
			this.scheduleMaintenanceJob(new FolderReorganization(folderPath, folder, false));
			this.logger.logInfo("Scheduled reorganization of folder " + folderPath + " (" + files.length + " files)");
			return;
		}
		for (int f = 0; f < files.length; f++)
			if (files[f].isDirectory() && files[f].getName().matches("[0-9A-Fa-f]{2}")) {
				this.scheduleMaintenanceJob(new FolderReorganization(folderPath, folder, true));
				this.logger.logInfo("Scheduled finishing reorganization of folder " + folderPath + " (" + files.length + " files)");
				return;
			}
		this.logger.logInfo("Reorganization not required for folder " + folderPath + " (" + files.length + " files)");
	}
	
	private boolean reorganizeFolder(String folderPath, File folder, boolean isContinuation) {
		if (this.isPathFolder(folder, folderPath))
			return true; // this one has already been reorganized
		this.logger.logInfo(this.name + ": " + (this.isReorganizingFolderPath(folderPath) ? "continue" : "start") + " reorganizing folder " + folderPath + " ...");
		
		//	get and sort files
		File[] files = folder.listFiles(new FileFilter() {
			public boolean accept(File file) {
				return file.getName().matches("[0-9A-Fa-f]{32}(\\..*)?"); // also include '<dataId>.zip.old' files
			}
		});
		this.logger.logInfo(" - got " + files.length + " files to move");
		Arrays.sort(files, new Comparator() {
			public int compare(Object obj1, Object obj2) {
				File f1 = ((File) obj1);
				File f2 = ((File) obj2);
				return f1.getName().compareToIgnoreCase(f2.getName());
			}
		});
		
		//	check if we have more than one data object ID (reorganization is pointless otherwise)
		HashSet folderDataIDs = new HashSet();
		for (int f = 0; f < files.length; f++) {
			String fileDataId = files[f].getName();
			if (fileDataId.indexOf('.') != -1)
				fileDataId = fileDataId.substring(0, fileDataId.indexOf('.'));
			folderDataIDs.add(fileDataId);
		}
		if (this.isReorganizingFolderPath(folderPath) || isContinuation) // need to finish job if folder still marked as reorganizing
			this.logger.logInfo(" - continuing reorganization, " + folderDataIDs.size() + " data objects left to handle");
		else if (folderDataIDs.size() < 2) {
			this.logger.logInfo(" - reorganization pointless, only one data object");
			return true;
		}
		
		//	check if there are any open references
		for (Iterator doidit = folderDataIDs.iterator(); doidit.hasNext();) {
			String dataId = ((String) doidit.next());
			if (this.hasDataObjectReference(dataId)) {
				this.logger.logInfo(" - cannot reorganize, there are open references");
				return false;
			}
		}
		
		//	mark folder as reorganizing
		this.registerReorganizingFolderPath(folderPath);
		
		//	compute folder depth
		int depth = (("/".length() + folderPath.length()) / 3); // two characters plus one slash per path step, less leading slash
		
		//	move files to sub folders
		String subFolderName = null;
		File subFolder = null;
		int subFolderFileCount = 0;
		HashSet subFolderDataIDs = new HashSet();
		boolean subFolderMoved = true;
		boolean folderMoved = true;
		for (int f = 0; f < files.length; f++) {
			String fSubFolderName = this.getPathStep(files[f].getName(), depth);
			
			//	switch to new sub folder (files are sorted lexicographically)
			if (!fSubFolderName.equals(subFolderName)) {
				
				//	schedule reorganization for just-completed sub folder if too large
				//	(unless we only have a single data ID inside, in which case another reorganization is pointless)
				if (subFolderMoved && (subFolderDataIDs.size() > 1) && (subFolderFileCount > this.maxFolderObjects)) {
					this.scheduleMaintenanceJob(new FolderReorganization((folderPath + ((folderPath.length() == 0) ? "" : "/") + subFolderName), subFolder, false));
					this.logger.logInfo(" - scheduled reorganization of folder " + folderPath + ((folderPath.length() == 0) ? "" : "/") + subFolderName + " (" + subFolderFileCount + " files)");
				}
				
				//	create new sub folder
				subFolderName = fSubFolderName;
				subFolder = new File(folder, subFolderName);
				subFolder.mkdir();
				this.logger.logInfo(" - created sub folder " + folderPath + ((folderPath.length() == 0) ? "" : "/") + subFolderName);
				subFolderFileCount = 0;
				subFolderDataIDs.clear();
				subFolderMoved = true;
			}
			
			//	extract data ID
			String fileDataId = files[f].getName();
			if (fileDataId.indexOf('.') != -1)
				fileDataId = fileDataId.substring(0, fileDataId.indexOf('.'));
			
			//	move file, failing overall success on failure (might happen if file open for reading, so we need to come back)
			File movedFile = new File(subFolder, files[f].getName());
			this.logger.logInfo(" - moving " + folderPath + ((folderPath.length() == 0) ? "" : "/") + files[f].getName() + " to " + folderPath + ((folderPath.length() == 0) ? "" : "/") + subFolderName + "/" + files[f].getName());
			if (files[f].renameTo(movedFile)) {
				this.logger.logInfo("   ==> file moved successfully");
				subFolderFileCount++;
				subFolderDataIDs.add(fileDataId);
			}
			else {
				this.logger.logInfo("   ==> failed to move file");
				subFolderMoved = false;
				folderMoved = false;
			}
		}
		
		//	schedule reorganization for last completed sub folder if too large
		//	(unless we only have a single data ID inside, in which case another reorganization is pointless)
		if (subFolderMoved && (subFolderDataIDs.size() > 1) && (subFolderFileCount > this.maxFolderObjects)) {
			this.scheduleMaintenanceJob(new FolderReorganization((folderPath + "/" + subFolderName), subFolder, false));
			this.logger.logInfo(" - scheduled reorganization of folder " + folderPath + "/" + subFolderName + " (" + subFolderFileCount + " files)");
		}
		
		//	mark as path folder once all files moved
		if (folderMoved) {
			try {
				if (this.markPathFolder(folder, folderPath)) {
					this.unregisterReorganizingFolderPath(folderPath);
					this.logger.logInfo(" - marked path folder " + folderPath);
					return true;
				}
				else {
					this.logger.logInfo(" - failed to mark path folder " + folderPath);
					return false;
				}
			}
			catch (IOException ioe) {
				this.logger.logError("Error marking path folder " + folderPath + ":" + ioe.getMessage());
				this.logger.logError(ioe);
				return false;
			}
		}
		
		//	some file failed to move
		else return false;
	}
	
	private void zipOldVersions(ComponentActionConsole cac) {
		cac.reportResult(this.name + ": start zipping up old data object versions ...");
		ArrayList folderList = new ArrayList();
		folderList.add(this.rootFolder);
		int zipFileCount = 0;
		
		//	process folders
		for (int f = 0; f < folderList.size(); f++) {
			File folder = ((File) folderList.get(f));
			String folderPath = this.getFolderPath(folder);
			
			//	path folder, process recursively
			if (this.isPathFolder(folder, folderPath)) {
				File[] files = folder.listFiles(new FileFilter() {
					public boolean accept(File file) {
						return (file.isDirectory() && (file.getName().matches("[0-9A-Fa-f]{2}")));
					}
				});
				folderList.addAll(Arrays.asList(files));
			}
			
			//	use list of data files (if we get here, we are surely in file based mode)
			else {
				File[] dataFiles = folder.listFiles(new FileFilter() {
					public boolean accept(File file) {
						return (true
							&& file.isFile()
							&& file.getName().endsWith(dataFileExtension)
							&& file.getName().matches("[0-9A-Fa-f]{32}\\.[0-9]+\\..*")
						);
					}
				});
				for (int d = 0; d < dataFiles.length; d++) {
					this.scheduleMaintenanceJob(new OldVersionZipUp((folderPath + "/" + dataFiles[d].getName()), dataFiles[d].getAbsolutePath()));
					cac.reportResult(" - " + folderPath + "/" + dataFiles[d].getName());
					zipFileCount++;
				}
			}
		}
		cac.reportResult("Scheduled " + zipFileCount + " files for zipping up.");
	}
	
	private void zipDataFile(String filePathAndName) {
		File dataFile = new File(filePathAndName);
		if (!dataFile.exists()) {
			String dataFileName = dataFile.getName();
			String dataId = dataFileName.substring(0, dataFileName.indexOf('.'));
			File newDataFile = this.getDataFile(dataId, 0);
			if (newDataFile.exists() && newDataFile.isFile()) {
				dataFile = new File(newDataFile.getParentFile(), dataFileName);
				filePathAndName = dataFile.getAbsolutePath();
				this.logger.logInfo("Switched zipping up data file to re-organized path '" + filePathAndName + "'");
			}
			else {
				this.logger.logError("Error zipping up data file '" + filePathAndName + "': " + (dataFile.exists() ? "File is a folder." : "File does not exist."));
				return;
			}
		}
		File zipFile = new File(filePathAndName + ".zip");
		
		try {
			
			//	mark file as zipping
			this.zippingFileNames.add(filePathAndName);
			
			//	zip up data file ...
			InputStream dataIn = new BufferedInputStream(new FileInputStream(dataFile));
			ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
			ZipEntry zipEntry = new ZipEntry(dataFile.getName());
			zipEntry.setSize(dataFile.length());
			zipEntry.setTime(dataFile.lastModified());
			zipOut.putNextEntry(zipEntry);
			byte[] buffer = new byte[1024];
			for (int r; (r = dataIn.read(buffer, 0, buffer.length)) != -1;)
				zipOut.write(buffer, 0, r);
			zipOut.closeEntry();
			zipOut.flush();
			zipOut.close();
			dataIn.close();
			
			//	... and delete it afterwards
			dataFile.delete();
		}
		catch (Exception e) {
			this.logger.logError("Error zipping up data file '" + filePathAndName + "': " + e.getMessage());
			this.logger.logError(e);
		}
		finally {
			//	un-mark file as zipping
			this.zippingFileNames.remove(filePathAndName);
		}
	}
	
	private Set pathFolderPaths = Collections.synchronizedSet(new HashSet());
	private boolean isPathFolder(File folder, String folderPath) {
		if (this.pathFolderPaths.contains(folderPath))
			return true;
//		if (folderPath.length() < ((this.minFolderDepth * 3) - "/".length())) // two characters plus one slash per path step, less leading slash
//			return true; // TODOne remove this after retro-fitting existing (DST and IMS) repos with '.pathFolder' markers 
		File pathFolderMarker = new File(folder, PATH_FOLDER_MARKER_FILE_NAME);
		if (pathFolderMarker.exists()) {
			this.pathFolderPaths.add(folderPath);
			return true;
		}
		else return false;
	}
	
	private boolean markPathFolder(File folder, String folderPath) throws IOException {
		File pathFolderMarker = new File(folder, PATH_FOLDER_MARKER_FILE_NAME);
		if (pathFolderMarker.exists() || pathFolderMarker.createNewFile()) {
			this.pathFolderPaths.add(folderPath);
			return true;
		}
		else return false;
	}
	
	private String getPathStep(String dataId, int depth) {
		return dataId.substring((depth * 2), ((depth + 1) * 2));
	}
	
	private String getFolderPath(File folder) {
		String folderPath = "";
		while ((folder != null) && !this.rootFolder.getName().equals(folder.getName())) {
			folderPath = (folder.getName() + ((folderPath.length() == 0) ? "" : "/") + folderPath);
			folder = folder.getParentFile();
		}
		return folderPath;
	}
	
	private File getDataFile(String dataId, int version) {
		return this.getDataFile(dataId, version, 1);
	}
	private File getDataFile(String dataId, int version, int attempt) {
		File dataFolder = this.rootFolder;
		String folderPath = "";
		String dataFileName = (dataId + ((version == 0) ? "" : ("." + version)) + this.dataFileExtension);
		String zipFileName = (dataId + ((version == 0) ? "" : ("." + version)) + this.zipFileExtension);
		for (int depth = 0; depth < 16; depth++) {
			
			//	continue down path folders
			if (this.isPathFolder(dataFolder, folderPath)) {
				String pathStep = this.getPathStep(dataId, depth);
				dataFolder = new File(dataFolder, pathStep);
				folderPath = (folderPath + ((folderPath.length() == 0) ? "" : "/") + pathStep);
				continue;
			}
			
			//	parent folder in reorganization, wait and recurse (maintenance should be fast, and a rare event (at most once per folder))
			if (this.isReorganizingFolderPath(folderPath)) {
				if (attempt > 20)
					throw new IllegalStateException("Folder '" + folderPath + "' is reorganizing");
				try {
					Thread.sleep((attempt) * 1000);
				} catch (InterruptedException ie) {}
				return this.getDataFile(dataId, version, (attempt + 1));
			}
			
			//	check zipped previous version in file mode
			if (this.dataObjectsAreFiles && (version != 0)) {
				File zipFile = new File(dataFolder, zipFileName);
				if (zipFile.exists()) {
					
					//	zip file is being created, wait for it to finish (preventing exceptions for empty zip files)
					String pathAndFileName = (new File(dataFolder, dataFileName)).getAbsolutePath();
					while (this.zippingFileNames.contains(pathAndFileName)) try {
						Thread.sleep(100);
					} catch (InterruptedException ie) {}
					
					//	return existing zip file 
					return zipFile;
				}
			}
			
			//	return plain data file or folder (existing or not)
			return (this.dataObjectsAreFiles ? new File(dataFolder, dataFileName) : new DataObjectFolder(dataFolder, dataFileName));
		}
		
		//	we should never get here, unless we've maxed out path length somewhere
		return null;
	}
	
	/**
	 * Obtain the storage folder for a data object in folder based mode.
	 * @param dataId the ID of the data object
	 * @return the storage folder for the data object with the specified ID
	 */
	public DataObjectFolder getDataObjectFolder(String dataId) {
		if (this.dataObjectsAreFiles)
			throw new IllegalStateException("Cannot retrieve data object folders in file mode");
		return ((DataObjectFolder) this.getDataFile(dataId, 0));
	}
	
	/**
	 * Check if a data object exists.
	 * @param dataId the ID of the data object
	 * @return true if the data object exists, false otherwise
	 */
	public boolean isDataObjectAvailable(String dataId) {
		return this.isDataObjectAvailable(dataId, 0);
	}
	
	/**
	 * Check if a data object exists in a given version. Versions other than 0
	 * (current version) are only meaningful in file mode.
	 * @param dataId the ID of the data object
	 * @param version the version to check
	 * @return true if the data object exists, false otherwise
	 */
	public boolean isDataObjectAvailable(String dataId, int version) {
		if ((version != 0) && !this.dataObjectsAreFiles)
			throw new IllegalStateException("Cannot check versions on folder based data objects");
		if (!this.doVersioning && (version != 0))
			throw new IllegalStateException("Cannot check for previous data object versions with versioning deactivated");
		
		//	sanitize data object ID
		dataId = this.checkDataId(dataId);
		
		//	treat deletions in progress as already deleted, and wait for any restorations in progress
		if (this.removingDataIDs.contains(dataId))
			return false;
		while (this.restoringDataIDs.contains(dataId)) try {
			Thread.sleep(1 * 1000);
		} catch (InterruptedException ie) {}
		
		//	resolve relative version
		int fileVersion = this.computeFileVersion(dataId, version);
		if (fileVersion == -1)
			return false;
		
		//	check file
		File dataFile = this.getDataFile(dataId, fileVersion);
		return ((dataFile != null) && dataFile.exists() && (this.dataObjectsAreFiles ? dataFile.isFile() : dataFile.isDirectory()));
	}
	
	/**
	 * Retrieve the most recent version number of a data object. If no data
	 * object exists with the specified ID, this method returns 0.
	 * @param dataId the ID of the data object
	 * @return the most recent version number of the data object with the
	 *         specified ID
	 * @throws IOException
	 */
	public int getVersion(String dataId) {
		if (!this.dataObjectsAreFiles)
			throw new IllegalStateException("Cannot check versions on folder based data objects");
		if (!this.doVersioning)
			throw new IllegalStateException("Cannot obtain data object version with versioning deactivated");
		
		//	sanitize data object ID
		dataId = this.checkDataId(dataId);
		
		//	treat deletions in progress as already deleted, and wait for any restorations in progress
		if (this.removingDataIDs.contains(dataId))
			return 0;
		while (this.restoringDataIDs.contains(dataId)) try {
			Thread.sleep(1 * 1000);
		} catch (InterruptedException ie) {}
		
		//	compute and return version
		return this.doGetCurrentVersion(dataId);
	}
	
	private int doGetCurrentVersion(String dataId) {
		if (this.dataObjectVersionCache.containsKey(dataId))
			return ((Integer) this.dataObjectVersionCache.get(dataId)).intValue();
		int version = this.computeCurrentVersion(dataId);
		if (version != 0)
			this.dataObjectVersionCache.put(dataId, new Integer(version));
		return version;
	}
	
	private int computeCurrentVersion(String dataId) {
		
		//	no built-in versioning in folder mode
		if (!this.dataObjectsAreFiles)
			return 0;
		
		//	get storage folder
		dataId = this.checkDataId(dataId);
		File dataFile = this.getDataFile(dataId, 0);
		File dataFolder = dataFile.getParentFile();
		
		//	we don't even have the parent folder yet
		if (!dataFolder.exists())
			return 0;
		
		//	get files belonging to the data object
		File[] dataFiles = dataFolder.listFiles(new DataFileFilter(dataId, false));
		
		//	no files at all, current version is 0
		if (dataFiles.length == 0)
			return 0;
		
		//	find most recent version number
		int version = 0;
		for (int f = 0; f < dataFiles.length; f++) {
			String dataFileName = dataFiles[f].getName();
			dataFileName = dataFileName.substring(dataId.length() + ".".length()); // cut ID and dot
			if (dataFileName.endsWith(this.dataFileExtension)) { // there's left more than the file extension less dot, which will be the case for the most recent version
				dataFileName = dataFileName.substring(0, (dataFileName.length() - this.dataFileExtension.length())); // cut file extension
				try {
					version = Math.max(version, Integer.parseInt(dataFileName));
				} catch (NumberFormatException nfe) {}
			}
			else if (dataFileName.endsWith(this.zipFileExtension)) { // there's left more than the file extension less dot, which will be the case for the most recent version
				dataFileName = dataFileName.substring(0, (dataFileName.length() - this.zipFileExtension.length())); // cut file extension
				try {
					version = Math.max(version, Integer.parseInt(dataFileName));
				} catch (NumberFormatException nfe) {}
			}
		}
		
		//	extrapolate to most recent version
		return (version + 1);
	}
	
	private class DataFileFilter implements FileFilter {
		private String dataIdPrefix;
		private boolean alsoDeleted;
		DataFileFilter(String dataId, boolean alsoDeleted) {
			this.dataIdPrefix = (dataId + ".");
			this.alsoDeleted = alsoDeleted;
		}
		public boolean accept(File file) {
			return (true
					&& (file != null)
					&& file.isFile()
					&& file.getName().startsWith(this.dataIdPrefix)
					&& (false 
						|| file.getName().endsWith(dataFileExtension)
						|| file.getName().endsWith(zipFileExtension)
						|| (this.alsoDeleted && file.getName().endsWith(DELETED_ZIP_FILE_EXTENSION))
					)
				);
		}
	}
	
	/**
	 * Obtain an input stream for a data object when working in file mode.
	 * @param dataId the ID of the data item to load
	 * @return an input stream for the data object with the argument ID
	 * @throws DataNotFoundException if the argument data object ID is invalid
	 *             (no data object is stored with this ID)
	 * @throws IOException if any other IOException occurs
	 */
	public DataObjectInputStream getInputStream(String dataId) throws DataObjectNotFoundException, IOException {
		return this.getInputStream(dataId, 0);
	}
	
	/**
	 * Obtain an input stream for a data object when working in file mode with
	 * versioning activated. A positive version number specifically indicates
	 * an actual version, while a negative version number indicates a version
	 * backward relative to the most recent version. Version number 0 always
	 * returns an input stream for the most recent version.
	 * @param dataId the ID of the data item to load
	 * @param version the version to load
	 * @return an input stream for the data object with the argument ID
	 * @throws DataNotFoundException if the argument data object ID is invalid
	 *             (no data object is stored with this ID, or argument version
	 *             does not exist)
	 * @throws IOException if any other IOException occurs
	 */
	public DataObjectInputStream getInputStream(String dataId, int version) throws DataObjectNotFoundException, IOException {
		if (!this.dataObjectsAreFiles)
			throw new IllegalStateException("Cannot obtain input stream for folder based data objects");
		if (!this.doVersioning && (version != 0))
			throw new IllegalStateException("Cannot obtain input stream for previous data object versions with versioning deactivated");
		
		//	sanitize data object ID
		dataId = this.checkDataId(dataId);
		
		//	treat deletions in progress as already deleted, and wait for any restorations in progress
		if (this.removingDataIDs.contains(dataId))
			throw new DataObjectNotFoundException(dataId, version);
		while (this.restoringDataIDs.contains(dataId)) try {
			Thread.sleep(1 * 1000);
		} catch (InterruptedException ie) {}
		
		//	resolve relative version
		int fileVersion = this.computeFileVersion(dataId, version);
		if (fileVersion == -1)
			throw new DataObjectNotFoundException(dataId, version);
		
		//	finally ...
		try {
			File dataFile = this.getDataFile(dataId, fileVersion);
			if ((dataFile == null) || !dataFile.exists())
				throw new DataObjectNotFoundException(dataId, version);
			return new DataObjectInputStream(dataId, dataFile, fileVersion);
		}
		catch (FileNotFoundException fnfe) {
			throw new DataObjectNotFoundException(dataId, version);
		}
	}
	
	private int computeFileVersion(String dataId, int version) {
		int exVersion = this.doGetCurrentVersion(dataId);
		if (version < 0) {
			version = (exVersion + version);
			return ((version <= 0) ? -1 : version);
		}
		else if (version == exVersion)
			return 0; // current version in file names
		else if (exVersion < version)
			return -1;
		else return version;
	}
	
	/**
	 * Obtain an output stream for writing a (new version of a) data object
	 * with a given ID, working in file based mode. The number of the newly
	 * created data object version is available from the
	 * <code>getVersion()</code> of the returned output stream after the latter
	 * has been closed.
	 * @param dataId the ID of the data object to obtain an output stream for
	 * @return an output stream for writing to the data object with the
	 *            specified ID
	 * @throws IOException
	 */
	public DataObjectOutputStream getOutputStream(String dataId) throws IOException {
		if (!this.dataObjectsAreFiles)
			throw new IllegalStateException("Cannot obtain output stream for folder based data objects");
		
		//	sanitize data object ID
		dataId = this.checkDataId(dataId);
		
		//	wait for any deletions or restorations in progress
		while (this.removingDataIDs.contains(dataId) || this.restoringDataIDs.contains(dataId)) try {
			Thread.sleep(1 * 1000);
		} catch (InterruptedException ie) {}
		
		//	get data file
		File dataFile = this.getDataFile(dataId, 0);
		File parentFolder = dataFile.getParentFile();
		
		//	compute current version
		final int exVersion;
		if (dataFile.exists())
			exVersion = this.doGetCurrentVersion(dataId);
		else {
			exVersion = 0;
			parentFolder.mkdirs();
		}
		
		//	create output file and stream
		return new DataObjectOutputStream(dataFile, dataId, exVersion);
	}
	
	/**
	 * Delete a data object from storage. This method only zips up the folder
	 * or files associated with the argument data object ID, so deletion is
	 * reversible. To completely get rid of a data object, use the
	 * <code>destroyDataOject()</code> method.
	 * @param dataId the ID of the data object to delete
	 * @throws IOException if the specified data object ID is invalid (no data
	 *             object is stored by this ID) or any IOException occurs
	 */
	public void deleteDataObject(String dataId) throws IOException {
		this.removeDataOject(dataId, false);
	}
	
	/**
	 * Permanently delete a data object from storage. This method completely
	 * eradicates the folder or files associated with the argument data object
	 * ID, so deletion is irreversible. To delete in a reversible fashion, use
	 * the <code>deleteDataOject()</code> method. <b>Use with care</b>, e.g. in
	 * scenarios that require observing data privacy laws.
	 * @param dataId the ID of the data object to delete
	 * @throws IOException if the specified data object ID is invalid (no data
	 *             object is stored by this ID) or any IOException occurs
	 */
	public void destroyDataObject(String dataId) throws IOException {
		this.removeDataOject(dataId, true);
	}
	
	private void removeDataOject(String dataId, boolean eradicate) throws IOException {
		dataId = this.checkDataId(dataId);
		
		//	treat deletions in progress as already deleted, and wait for any restorations in progress (no need to duplicate former (we emulate that), but need to revert latter)
		if (this.removingDataIDs.contains(dataId))
			return;
		while (this.restoringDataIDs.contains(dataId)) try {
			Thread.sleep(1 * 1000);
		} catch (InterruptedException ie) {}
		
		//	delete data object
		try {
			this.removingDataIDs.add(dataId);
			this.doRemoveDataOject(dataId, eradicate);
		}
		finally {
			this.removingDataIDs.remove(dataId);
		}
	}
	
	private void doRemoveDataOject(String dataId, boolean eradicate) throws IOException {
		
		//	rename files in file mode
		if (this.dataObjectsAreFiles) {
			
			//	get storage folder
			File dataFile = this.getDataFile(dataId, 0);
			File parentFolder = dataFile.getParentFile();
			if (!parentFolder.exists())
				throw new DataObjectNotFoundException("Invalid data object ID '" + dataId + "'");
			
			//	get files belonging to the data object
			File[] dataFiles = parentFolder.listFiles(new DataFileFilter(dataId, eradicate)); // include previously deleted data object for eradication
			
			//	no files at all, we're done here
			if (dataFiles.length == 0)
				return;
			
			//	completely wipe data object
			if (eradicate) {
				for (int f = 0; f < dataFiles.length; f++)
					dataFiles[f].delete();
			}
			
			//	zip up all versions in <dataId>.zip.old, located in parent folder
			else {
				File zipFile = new File(parentFolder, (dataId + DELETED_ZIP_FILE_EXTENSION));
				ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
				byte[] buffer = new byte[1024];
				for (int f = 0; f < dataFiles.length; f++) {
					InputStream dataIn = new BufferedInputStream(new FileInputStream(dataFiles[f]));
					ZipEntry zipEntry = new ZipEntry(dataFiles[f].getName());
					zipEntry.setSize(dataFiles[f].length());
					zipEntry.setTime(dataFiles[f].lastModified());
					zipOut.putNextEntry(zipEntry);
					for (int r; (r = dataIn.read(buffer, 0, buffer.length)) != -1;)
						zipOut.write(buffer, 0, r);
					zipOut.closeEntry();
					dataIn.close();
					dataFiles[f].delete();
				}
				zipOut.flush();
				zipOut.close();
			}
			
			//	clear version cache
			this.dataObjectVersionCache.remove(dataId);
		}
		
		//	remove folder plus any content in folder mode
		else {
			
			//	get storage folder
			DataObjectFolder dataFolder = this.getDataObjectFolder(dataId);
			if (!dataFolder.existsForDeleteRestore()) {
				File parentFolder = dataFolder.getParentFile();
				File zipFile = new File(parentFolder, (dataId + DELETED_ZIP_FILE_EXTENSION));
				if (eradicate && zipFile.exists()) {
					zipFile.delete(); // eradicate previously deleted data object
					return;
				}
				else throw new DataObjectNotFoundException("Invalid data object ID '" + dataId + "'");
			}
			
			//	get files belonging to the data object
			//	TODO also handle sub folders (might become relevant at some point ...)
			File[] dataFiles = dataFolder.listFiles();
			
			//	completely wipe data object
			if (eradicate) {
				for (int f = 0; f < dataFiles.length; f++)
					dataFiles[f].delete();
				dataFolder.delete();
			}
			
			//	zip up folder content in <dataId>.zip.old, located in parent folder
			else {
				File parentFolder = dataFolder.getParentFile();
				File zipFile = new File(parentFolder, (dataId + DELETED_ZIP_FILE_EXTENSION));
				ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
				byte[] buffer = new byte[1024];
				for (int f = 0; f < dataFiles.length; f++) {
					InputStream dataIn = new BufferedInputStream(new FileInputStream(dataFiles[f]));
					ZipEntry zipEntry = new ZipEntry(dataFiles[f].getName());
					zipEntry.setSize(dataFiles[f].length());
					zipEntry.setTime(dataFiles[f].lastModified());
					zipOut.putNextEntry(zipEntry);
					for (int r; (r = dataIn.read(buffer, 0, buffer.length)) != -1;)
						zipOut.write(buffer, 0, r);
					zipOut.closeEntry();
					dataIn.close();
					dataFiles[f].delete();
				}
				zipOut.flush();
				zipOut.close();
				dataFolder.delete();
			}
		}
	}
	
	/**
	 * Restore a deleted data object, reverting a call of the
	 * <code>deleteDataOject()</code> with the same data object ID.
	 * @param dataId the ID of the data object to restore
	 * @throws IOException
	 */
	public void restoreDataObject(String dataId) throws IOException {
		this.restoreDataObject(dataId, false);
	}
	
	int restoreDataObject(String dataId, boolean forNewVersion) throws IOException {
		dataId = this.checkDataId(dataId);
		
		//	wait for any deletions or restorations in progress (need to revert former, and latter must be complete when we return)
		while (this.removingDataIDs.contains(dataId) || this.restoringDataIDs.contains(dataId)) try {
			Thread.sleep(1 * 1000);
		} catch (InterruptedException ie) {}
		
		//	delete data object
		try {
			this.restoringDataIDs.add(dataId);
			return this.doRestoreDataObject(dataId, forNewVersion);
		}
		finally {
			this.restoringDataIDs.remove(dataId);
		}
	}
	
	private int doRestoreDataObject(String dataId, boolean forNewVersion) throws IOException {
		
		//	rename files in file mode
		if (this.dataObjectsAreFiles) {
			
			//	get storage folder
			File dataFile = this.getDataFile(dataId, 0);
			File parentFolder = dataFile.getParentFile();
			if (!parentFolder.exists()) {
				if (forNewVersion)
					return 0;
				else throw new DataObjectNotFoundException("Invalid data object ID '" + dataId + "'");
			}
			
			//	get zip file
			File deletedZipFile = new File(parentFolder, (dataId + DELETED_ZIP_FILE_EXTENSION));
			if (!deletedZipFile.exists()) {
				if (forNewVersion)
					return 0;
				else throw new DataObjectNotFoundException("Invalid data object ID '" + dataId + "', or data object not deleted");
			}
			
			//	restore files
			ZipFile zipFile = new ZipFile(deletedZipFile);
			byte[] buffer = new byte[1024];
			for (Enumeration zee = zipFile.entries(); zee.hasMoreElements();) {
				ZipEntry zipEntry = ((ZipEntry) zee.nextElement());
				dataFile = new File(parentFolder, zipEntry.getName());
				if (dataFile.exists())
					continue;
				dataFile.createNewFile();
				InputStream zipIn = new BufferedInputStream(zipFile.getInputStream(zipEntry));
				OutputStream dataOut = new BufferedOutputStream(new FileOutputStream(dataFile));
				for (int r; (r = zipIn.read(buffer, 0, buffer.length)) != -1;)
					dataOut.write(buffer, 0, r);
				zipIn.close();
				dataOut.flush();
				dataOut.close();
				dataFile.setLastModified(zipEntry.getTime());
			}
			zipFile.close();
			
			//	delete zip file on success
			deletedZipFile.delete();
			
			//	compute current version on restoring
			return this.doGetCurrentVersion(dataId);
		}
		
		//	restore folder plus any content in folder mode
		else {
			
			//	get storage folder
			DataObjectFolder dataFolder = this.getDataObjectFolder(dataId);
			if (dataFolder.existsForDeleteRestore())
				throw new IOException("Data object '" + dataId + "' is not deleted");
			File parentFolder = dataFolder.getParentFile();
			if (!parentFolder.exists())
				throw new DataObjectNotFoundException("Invalid data object ID '" + dataId + "'");
			
			//	get zip file
			File deletedZipFile = new File(parentFolder, (dataId + DELETED_ZIP_FILE_EXTENSION));
			if (!deletedZipFile.exists())
				throw new DataObjectNotFoundException("Invalid data object ID '" + dataId + "', or data object not deleted");
			
			//	restore data folder (we've already made sure of parent folder)
			dataFolder.mkdirForRestore();
			
			//	restore files
			ZipFile zipFile = new ZipFile(deletedZipFile);
			byte[] buffer = new byte[1024];
			for (Enumeration zee = zipFile.entries(); zee.hasMoreElements();) {
				ZipEntry zipEntry = ((ZipEntry) zee.nextElement());
				File dataFile = new File(dataFolder, zipEntry.getName());
				if (dataFile.exists())
					continue;
				dataFile.createNewFile();
				InputStream zipIn = new BufferedInputStream(zipFile.getInputStream(zipEntry));
				OutputStream dataOut = new BufferedOutputStream(new FileOutputStream(dataFile));
				for (int r; (r = zipIn.read(buffer, 0, buffer.length)) != -1;)
					dataOut.write(buffer, 0, r);
				zipIn.close();
				dataOut.flush();
				dataOut.close();
				dataFile.setLastModified(zipEntry.getTime());
			}
			zipFile.close();
			
			//	delete zip file on success
			deletedZipFile.delete();
			
			//	nothing to do about versions in folder based mode
			return -1;
		}
	}
	
	/**
	 * Retrieve a list of the IDs of all the data objects stored in this IKS
	 * @return a list of the IDs of all the data objects stored in this IKS
	 */
	public String[] getDataObjectIDs() {
		return this.getDataObjectIDs(false);
	}
	
	/**
	 * Retrieve a list of the IDs of all the data objects stored in this IKS
	 * @param includeDeleted include IDs of deleted zipped-up data objects?
	 * @return a list of the IDs of all the data objects stored in this IKS
	 */
	public String[] getDataObjectIDs(final boolean includeDeleted) {
		LinkedHashSet dataObjectIDs = new LinkedHashSet();
		ArrayList folderList = new ArrayList();
		folderList.add(this.rootFolder);
		
		//	process folders
		for (int f = 0; f < folderList.size(); f++) {
			File folder = ((File) folderList.get(f));
			
			//	path folder, process recursively
			if (this.isPathFolder(folder, this.getFolderPath(folder))) {
				File[] files = folder.listFiles(new FileFilter() {
					public boolean accept(File file) {
						return (file.isDirectory() && (file.getName().matches("[0-9A-Fa-f]{2}")));
					}
				});
				folderList.addAll(Arrays.asList(files));
			}
			
			//	use list of data files in file based mode
			else if (this.dataObjectsAreFiles) {
				File[] dataFiles = folder.listFiles(new FileFilter() {
					public boolean accept(File file) {
						return (true
							&& file.isFile()
							&& (false
								|| file.getName().endsWith(dataFileExtension)
								|| file.getName().endsWith(zipFileExtension)
								|| (includeDeleted && file.getName().endsWith(DELETED_ZIP_FILE_EXTENSION))
							)
							&& file.getName().matches("[0-9A-Fa-f]{32}\\..*")
						);
					}
				});
				for (int d = 0; d < dataFiles.length; d++) {
					String dataFileName = dataFiles[d].getName();
					dataObjectIDs.add(dataFileName.substring(0, dataFileName.indexOf('.')));
				}
			}
			
			//	use list of data folders in folder based mode
			else {
				File[] dataFolders = folder.listFiles(new FileFilter() {
					public boolean accept(File file) {
						return (false
								|| (file.isDirectory() && file.getName().matches("[0-9A-Fa-f]{32}"))
								|| (includeDeleted && file.isFile() && file.getName().matches("[0-9A-Fa-f]{32}\\.zip\\.old"))
							);
					}
				});
				for (int d = 0; d < dataFolders.length; d++)
					dataObjectIDs.add(dataFolders[d].getName());
			}
		}
		
		//	return list
		return ((String[]) dataObjectIDs.toArray(new String[dataObjectIDs.size()]));
	}
	
	/* TODO in the long haul, use strategy pattern for handling data object IDs:
	 * - implement checkDataObjectId(String dataId) --> String
	 * - implement isPathFolder(File file) --> boolean
	 * - implement isDataObjectFile(File file) --> boolean
	 * - implement isDataObjectFolder(File folder) --> boolean
	 * - implement getPathFolderName(String dataId, int depth) --> String
	 * - implement getFolderPathDepth(String folderPath) --> int
	 * - take ID handler strategy object as constructor argument ...
	 * - ... warning explicitly against strategy changes
	 * - use current built-in behavior if no alternative strategy specified
	 * - use any custom strategy in file filters, etc.
	 */
	
	/* *
	 * Check (and sanitize) a data object ID. This default implementation
	 * enforces that the argument ID exclusively consists of letters A-F and
	 * a-f as well as digits (i.e., hexadecimal code), converts it to upper
	 * case, and truncates or pads it to 32 characters if required. Sub classes
	 * requiring a different behavior may overwrite this method, but it is
	 * recommended to at least enforce the exclusive use of alphanumeric
	 * characters to prevent file naming issues, as well as case insensitivity
	 * to avoid conflicts on case insensitive file systems. Also, IDs should
	 * have a certain minimum length, at least 16 characters, so there is
	 * enough room for path folders.
	 * @param dataId the data object ID to check
	 * @return the sanitized data object ID
	 * @throws IllegalArgumentException
	 */
	private String checkDataId(String dataId) throws IllegalArgumentException {
		if (dataId == null)
			throw new IllegalArgumentException("Data ID must not be null or empty.");
		String trimmedDataId = dataId.trim();
		if (trimmedDataId.length() == 0)
			throw new IllegalArgumentException("Data ID must not be null or empty.");
		if (!trimmedDataId.matches("[a-fA-F0-9]++"))
			throw new IllegalArgumentException("Invalid data ID '" + dataId + "' - data ID must consist of hex characters only.");
		trimmedDataId = trimmedDataId.toUpperCase();
		if (trimmedDataId.length() == 32)
			return trimmedDataId;
		else {
			String paddedDataId = trimmedDataId;
			while (paddedDataId.length() < 32)
				paddedDataId = (paddedDataId + trimmedDataId);
			return paddedDataId.substring(0, 32);
		}
	}
//	
//	public static void main(String[] args) throws Exception {
//		File docFolder = new File("./Components/GgServerDIOData/Documents/");
////		IdentifierKeyedDataObjectStore iks = new IdentifierKeyedDataObjectStore("Test", docFolder, "xml", GoldenGateServerActivityLogger.sysOut);
////		IdentifierKeyedDataObjectStore iks = new IdentifierKeyedDataObjectStore("Test", docFolder, null, GoldenGateServerActivityLogger.sysOut);
//		IdentifierKeyedDataObjectStore iks = new IdentifierKeyedDataObjectStore("Test", docFolder, 0, 256, "xml", GoldenGateServerActivityLogger.sysOut);
//		ComponentActionConsole cac = new ComponentActionConsole() {
//			public String getActionCommand() {
//				return null;
//			}
//			public String[] getExplanation() {
//				return null;
//			}
//			public void performActionConsole(String[] arguments) {}
//			public void reportResult(String message) {
//				System.out.println(message);
//			}
//			public void reportError(String message) {
//				System.out.println(message);
//			}
//			public void reportError(Throwable error) {
//				error.printStackTrace(System.out);
//			}
//		};
//		//iks.markPathFolders(cac);
//		iks.maxFolderObjects = 32; // just to get some reorganization going
//		iks.checkFolderReorganization(cac);
////		iks.zipOldDocumentVersions(cac);
//		
//		DataObjectInputStream in = iks.getInputStream("2180CA2B93A9118C2A25CDCE90A08981", 0);
//		DialogFactory.alert("OK to close data object", "OK to close data object");
//		System.out.println(in.getDataObjectSize());
//		in.close();
//		
////		DataObjectInputStream in = iks.getInputStream("2180CA2B93A9118C2A25CDCE90A08981", 0);
////		System.out.println(in.getDataObjectSize());
////		in.close();
////		DataObjectOutputStream out = iks.getOutputStream("2180CA2B93A9118C2A25CDCE90A08981");
////		out.write(0x21);
////		out.flush();
////		out.close();
////		System.out.println(out.getVersion());
//		//iks.deleteDataObject("FFF1CA60FFCDF655E279E450FFFD2C09");
//		//iks.restoreDataObject("FFF1CA60FFCDF655E279E450FFFD2C09");
////		DataObjectFolder dof = iks.getDataObjectFolder("FFF1CA60FFCDF655E279E450FFFD2C09");
////		dof.mkdirs();
//		
//		DialogFactory.alert("OK to shut down", "OK to shut down");
//		iks.shutdown();
//		
//		//	dst.zipOldDocumentVersions();
////		String docId = "0000 C505 BB5D 484C 76BE 9AB6 999D EB23".replaceAll("\\s", "");
////		DocumentRoot doc = dst.loadDocument(docId, -1);
////		doc.setAttribute("testTime", ("" + System.currentTimeMillis()));
////		int version = dst.storeDocument(doc, docId);
////		System.out.println("Stored as version " + version);
//	}
}