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
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Component for retrieving data objects by their (UU)ID, named GoldenGATE
 * Identified Keyed Provider. This component can provide read access to both
 * single-file data objects and multi-file data objects held together in one
 * dedicated folder each, depending upon the configuration of the underlying
 * GoldenGATE Identified Keyed Storage component.
 * 
 * @author sautter
 */
public class IdentifierKeyedDataObjectProvider {
	
	//	TODO abstract out both code and nested classes shared with IKS !!!
	
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
			if (dataFilesVersioned) {
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
				this.dataFileSize = ((int) zipEntry.getSize());
				this.dataLastModified = zipEntry.getTime();
				this.in = new BufferedInputStream(this.zipFile.getInputStream(zipEntry));
			}
			else {
				this.dataFileSize = ((int) dataFile.length());
				this.dataLastModified = dataFile.lastModified();
				this.in = new BufferedInputStream(new FileInputStream(dataFile));
			}
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
	
	private File rootFolder;
	private String dataFileExtension;
	private String zipFileExtension;
	private boolean dataFilesVersioned;
	private boolean dataObjectsAreFiles;
	
	private Map dataObjectVersionCache = Collections.synchronizedMap(new LinkedHashMap(16, 0.75f, true) {
		protected boolean removeEldestEntry(Entry eldest) {
			return (this.size() > 8192);
		}
	});
	
	/** Constructor
	 * @param rootFolder the root folder of the storage hierarchy
	 * @param logger logger for background maintenance activity
	 */
	public IdentifierKeyedDataObjectProvider(File rootFolder) {
		this(rootFolder, null, false);
	}
	
	/** Constructor
	 * @param rootFolder the root folder of the storage hierarchy
	 * @param dataFileExtension the file extension for data files (null indicates folder based data objects)
	 */
	public IdentifierKeyedDataObjectProvider(File rootFolder, String dataFileExtension) {
		this(rootFolder, dataFileExtension, (dataFileExtension != null));
	}
	
	/** Constructor
	 * @param rootFolder the root folder of the storage hierarchy
	 * @param dataFileExtension the file extension for data files (null indicates folder based data objects)
	 * @param dataFilesVersioned provides access to previous versions of data objects (in file based mode only)?
	 */
	public IdentifierKeyedDataObjectProvider(File rootFolder, String dataFileExtension, boolean dataFilesVersioned) {
		this.rootFolder = rootFolder;
		this.rootFolder.mkdirs();
		if (dataFileExtension == null) {
			this.dataFileExtension = "";
			this.zipFileExtension = "";
			this.dataFilesVersioned = false;
			this.dataObjectsAreFiles = false;
		}
		else {
			if (!dataFileExtension.startsWith("."))
				dataFileExtension = ("." + dataFileExtension);
			this.dataFileExtension = dataFileExtension;
			this.zipFileExtension = (dataFileExtension + ".zip");
			this.dataFilesVersioned = dataFilesVersioned;
			this.dataObjectsAreFiles = true;
		}
	}
	
	private Set pathFolderPaths = Collections.synchronizedSet(new HashSet());
	private boolean isPathFolder(File folder, String folderPath) {
		if (this.pathFolderPaths.contains(folderPath))
			return true;
		File pathFolderMarker = new File(folder, PATH_FOLDER_MARKER_FILE_NAME);
		if (pathFolderMarker.exists()) {
			this.pathFolderPaths.add(folderPath);
			return true;
		}
		else return false;
	}
	
	private String getPathStep(String dataId, int depth) {
		return dataId.substring((depth * 2), ((depth + 1) * 2));
	}
	
	private File getDataFile(String dataId, int version) {
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
			
			//	check zipped previous version in file mode
			if (this.dataObjectsAreFiles && (version != 0)) {
				File zipFile = new File(dataFolder, zipFileName);
				if (zipFile.exists())
					return zipFile;
			}
			
			//	return plain data file or folder (existing or not)
			return new File(dataFolder, dataFileName);
		}
		
		//	we should never get here, unless we've maxed out path length somewhere
		return null;
	}
	
	/**
	 * Obtain the storage folder for a data object in folder based mode.
	 * @param dataId the ID of the data object
	 * @return the storage folder for the data object with the specified ID
	 */
	public File getDataObjectFolder(String dataId) {
		if (this.dataObjectsAreFiles)
			throw new IllegalStateException("Cannot retrieve data object folders in file mode");
		return this.getDataFile(dataId, 0);
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
		if (!this.dataFilesVersioned && (version != 0))
			throw new IllegalStateException("Cannot check for previous data object versions with versioning deactivated");
		
		//	sanitize data object ID
		dataId = this.checkDataId(dataId);
		
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
		if (!this.dataFilesVersioned)
			throw new IllegalStateException("Cannot obtain data object version with versioning deactivated");
		
		//	sanitize data object ID
		dataId = this.checkDataId(dataId);
		
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
		if (!this.dataFilesVersioned && (version != 0))
			throw new IllegalStateException("Cannot obtain input stream for previous data object versions with versioning deactivated");
		
		//	sanitize data object ID
		dataId = this.checkDataId(dataId);
		
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
//		IdentifierKeyedDataObjectProvider ikp = new IdentifierKeyedDataObjectProvider(docFolder, "xml");
//		
//		DataObjectInputStream in = ikp.getInputStream("2180CA2B93A9118C2A25CDCE90A08981", 0);
////		DialogFactory.alert("OK to close data object", "OK to close data object");
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
//		//	dst.zipOldDocumentVersions();
////		String docId = "0000 C505 BB5D 484C 76BE 9AB6 999D EB23".replaceAll("\\s", "");
////		DocumentRoot doc = dst.loadDocument(docId, -1);
////		doc.setAttribute("testTime", ("" + System.currentTimeMillis()));
////		int version = dst.storeDocument(doc, docId);
////		System.out.println("Stored as version " + version);
//	}
}