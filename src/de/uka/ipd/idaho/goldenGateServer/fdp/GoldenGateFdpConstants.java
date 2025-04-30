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
package de.uka.ipd.idaho.goldenGateServer.fdp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;

import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants;

/**
 * Constants for GoldenGATE File Download Provider (FDP).
 * 
 * @author sautter
 */
public interface GoldenGateFdpConstants extends GoldenGateServerConstants {
	
	/** the command for getting a list of all file groups hosted by a GoldenGATE FDP instance */
	public static final String LIST_FILE_GROUPS_COMMAND = "FDP_LIST_FILE_GROUPS";
	
	/** the command for getting a list of all files in a specific file group */
	public static final String LIST_FILES_COMMAND = "FDP_LIST_FILES";
	
	/** the command for getting a specific file */
	public static final String GET_FILE_COMMAND = "FDP_GET_FILE";
	
	/** the command for updating a specific file */
	public static final String UPDATE_FILE_COMMAND = "FDP_UPDATE_FILE";
	
	/** the command for deleting a specific file */
	public static final String DELETE_FILE_COMMAND = "FDP_DELETE_FILE";
	
	/** dedicated name for ZIP entries to contain the actual modification timestamp of the next file (ZIP entry timestamps are 32 bits only, and seem to be time zone sensitive) */
	public static final String LAST_MODIFIED_ZIP_ENTRY_NAME = "FileLastModifiedTimeMillisUTC";
	
	/**
	 * Descriptor of a file group hosted in GoldenGATE FDP.
	 * 
	 * @author sautter
	 */
	public static class FileGroupDescriptor {
		
		/** the name of the file group */
		public final String name;
		
		/** the filters of the file group (patterns that exclude rather than include files have a starting dash attached to them) */
		public final String[] filters;
		
		FileGroupDescriptor(String name, String[] filters) {
			this.name = name;
			this.filters = filters;
		}
		
		/**
		 * Convert the file group descriptor into a TSV string, for storage or
		 * transfer
		 * @return a TSV string representing the file group descriptor
		 */
		public String toTsvString() {
			StringBuffer tsv = new StringBuffer();
			tsv.append(this.name);
			tsv.append("\t");
			for (int f = 0; f < this.filters.length; f++) {
				if (f != 0)
					tsv.append(" ");
				tsv.append(this.filters[f]);
			}
			return tsv.toString();
		}
		
		/**
		 * Read a series of file group descriptors from their TSV form.
		 * @param br the reader to read from
		 * @return an array holding the file group descriptors
		 */
		public static FileGroupDescriptor[] readFileGroupDescriptors(BufferedReader br) throws IOException {
			ArrayList fgds = null;
			for (String fgdRow; (fgdRow = br.readLine()) != null;) {
				if (fgdRow.length() == 0)
					continue;
				if (fgdRow.startsWith("//"))
					continue;
				String[] fgdData = fgdRow.split("\\t");
				if (fgdData.length < 2)
					continue;
				if (fgds == null)
					fgds = new ArrayList();
				String fgdName = fgdData[0].trim();
				String fgdFilterStr = fgdData[1].trim();
				String[] fgdFilters = ((fgdFilterStr.length() == 0) ? new String[0] : fgdFilterStr.split("\\s+"));
				fgds.add(new FileGroupDescriptor(fgdName, fgdFilters));
			}
			return ((fgds == null) ? new FileGroupDescriptor[0] : ((FileGroupDescriptor[]) fgds.toArray(new FileGroupDescriptor[fgds.size()])));
		}
	}
	
	/**
	 * Descriptor of a file hosted in GoldenGATE FDP.
	 * 
	 * @author sautter
	 */
	public static class FileDescriptor {
		
		/** the name of the file (relative to its parent file group) */
		public final String name;
		
		/** the size of the file in bytes */
		public final int size;
		
		/** the modification timestamp of the file */
		public final long lastMod;
		
		/** Constructor
		 * @param name the name of the file (relative to its parent file group)
		 * @param size the size of the file in bytes
		 * @param lastMod the modification timestamp of the file
		 */
		public FileDescriptor(String name, int size, long lastMod) {
			this.name = name;
			this.size = size;
			this.lastMod = lastMod;
		}
		
		/**
		 * Convert the file descriptor into a TSV string, for storage or
		 * transfer
		 * @return a TSV string representing the file descriptor
		 */
		public String toTsvString() {
			StringBuffer tsv = new StringBuffer();
			tsv.append(this.name);
			tsv.append("\t");
			tsv.append(this.size);
			tsv.append("\t");
			tsv.append(this.lastMod);
			return tsv.toString();
		}
		
		/**
		 * Read a series of file descriptors from their TSV form.
		 * @param br the reader to read from
		 * @return an array holding the file descriptors
		 */
		public static FileDescriptor[] readFileDescriptors(BufferedReader br) throws IOException {
			ArrayList fds = null;
			for (String fdRow; (fdRow = br.readLine()) != null;) {
				if (fdRow.length() == 0)
					continue;
				if (fdRow.startsWith("//"))
					continue;
				String[] fdData = fdRow.split("\\t");
				if (fdData.length < 3)
					continue;
				if (fds == null)
					fds = new ArrayList();
				String fdName = fdData[0].trim();
				int fdSize = Integer.parseInt(fdData[1].trim());
				long fdLastMod = Long.parseLong(fdData[2].trim());
				fds.add(new FileDescriptor(fdName, fdSize, fdLastMod));
			}
			return ((fds == null) ? new FileDescriptor[0] : ((FileDescriptor[]) fds.toArray(new FileDescriptor[fds.size()])));
		}
		
		/**
		 * Create file descriptors for the files inside a given root folder
		 * that match a given file filter.
		 * @param baseFolder the folder whose contents to seek through
		 * @param filter the file filter to apply
		 * @return an array holding the file descriptors
		 */
		public static FileDescriptor[] getFileDescriptors(File baseFolder, FileFilter filter) {
			ArrayList fds = new ArrayList();
			ArrayList files = new ArrayList();
			ArrayList filePrefixes = new ArrayList();
			files.add(baseFolder);
			filePrefixes.add(null);
			for (int f = 0; f < files.size(); f++) {
				File file = ((File) files.get(f));
				String filePrefix = ((String) filePrefixes.get(f));
				if (file.isDirectory()) {
					if (file.getName().endsWith(".old"))
						continue;
					File[] subFiles = ((filter == null) ? file.listFiles() : file.listFiles(filter));
					if (subFiles == null)
						continue;
					String subFilePrefix = ((filePrefix == null) ? "" : (filePrefix + file.getName() + "/"));
					for (int sf = 0; sf < subFiles.length; sf++) {
						files.add(subFiles[sf]);
						filePrefixes.add(subFilePrefix);
					}
				}
				else if (file.isFile()) {
					if (file.length() == 0)
						continue; // TODO really ???
					fds.add(new FileDescriptor((filePrefix + file.getName()), ((int) file.length()), file.lastModified()));
				}
			}
			return ((FileDescriptor[]) fds.toArray(new FileDescriptor[fds.size()]));
		}
	}
}
