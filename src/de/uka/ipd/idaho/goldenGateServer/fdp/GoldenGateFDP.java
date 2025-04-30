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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponentRegistry;
import de.uka.ipd.idaho.goldenGateServer.fdp.util.FileGroup;
import de.uka.ipd.idaho.goldenGateServer.uaa.UserAccessAuthority;
import de.uka.ipd.idaho.goldenGateServer.util.BufferedLineInputStream;
import de.uka.ipd.idaho.goldenGateServer.util.BufferedLineOutputStream;

/**
 * GoldenGATE File Download Provider (FDP) accepts generic file based uploads
 * and makes them available for download via its web front-end.
 * 
 * @author sautter
 */
public class GoldenGateFDP extends AbstractGoldenGateServerComponent implements GoldenGateFdpConstants {
	private UserAccessAuthority uaa;
	
	private File fileGroupRoot;
	private TreeMap fileGroupsByName = new TreeMap();
	
	/** usual zero-argument constructor for class loading */
	public GoldenGateFDP() {
		super("FDP");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#initComponent()
	 */
	protected void initComponent() {
		
		//	load disk storage root folder (defaulting to own data folder)
		String fileGroupRoot = this.configuration.getSetting("fileGroupRoot");
		if (fileGroupRoot == null)
			this.fileGroupRoot = this.dataPath;
		else if (fileGroupRoot.startsWith("/") || (fileGroupRoot.indexOf(":/") != -1) || (fileGroupRoot.indexOf(":\\") != -1))
			this.fileGroupRoot = new File(fileGroupRoot);
		else this.fileGroupRoot = new File(this.dataPath, fileGroupRoot);
		this.fileGroupRoot.mkdirs();
		
		//	load file groups
		this.loadFileGroups(null);
	}
	
	void loadFileGroups(ComponentActionConsole cac) {
		this.fileGroupsByName.clear();
		
		File fgFile = new File(this.dataPath, "fileGroups.cnfg");
		try {
			BufferedReader fgBr = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(fgFile)), "UTF-8"));
			FileGroupDescriptor[] fgds = FileGroupDescriptor.readFileGroupDescriptors(fgBr);
			fgBr.close();
			for (int g = 0; g < fgds.length; g++) {
				this.fileGroupsByName.put(fgds[g].name, new FileGroup(fgds[g]));
				if (cac != null) /* runtime reload, create folder and permission right away */ {
					File fgFolder = new File(this.fileGroupRoot, fgds[g].name);
					fgFolder.mkdirs();
					this.uaa.registerPermission(this.getPermissionName(fgds[g].name));
				}
			}
			if (cac != null)
				cac.reportResult("Loaded " + this.fileGroupsByName.size() + " file groups");
		}
		catch (IOException ioe) {
			if (cac == null) {
				System.out.println("Error loading file groups from '" + fgFile.getAbsolutePath() + "': " + ioe.getMessage());
				ioe.printStackTrace(System.out);
			}
			else {
				cac.reportError("Error loading file groups from '" + fgFile.getAbsolutePath() + "': " + ioe.getMessage());
				cac.reportError(ioe);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#link()
	 */
	public void link() {
		
		//	link to UAA
		this.uaa = ((UserAccessAuthority) GoldenGateServerComponentRegistry.getServerComponent(UserAccessAuthority.class.getName()));
		if (this.uaa == null)
			throw new RuntimeException(UserAccessAuthority.class.getName());
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#linkInit()
	 */
	public void linkInit() {
		
		//	register file group permissions to UPS
		for (Iterator fgnit = this.fileGroupsByName.keySet().iterator(); fgnit.hasNext();) {
			String fgName = ((String) fgnit.next());
			File fgFolder = new File(this.fileGroupRoot, fgName);
			fgFolder.mkdirs();
			this.uaa.registerPermission(this.getPermissionName(fgName));
		}
	}
	
	String getPermissionName(String fileGroupName) {
		//	TODOnot differentiate this out into 'upload', 'update', and 'delete' permissions for each file group
		//	==> it's all write updates, and we can use FTP server for general file sharing
		fileGroupName = fileGroupName.replaceAll("[\\/\\\\\\.]+", "_");
		return ("FDP." + fileGroupName);
	}
	
	String cleanFileName(String fileName) {
		fileName = fileName.replace('\\', '/'); // normalize slashes
		if (fileName.indexOf(":") != -1) // truncate Windows style absolute path prefixes
			fileName = fileName.substring(fileName.lastIndexOf(":") + ":".length());
		while (fileName.startsWith("/")) // truncate Linux style absolute path prefixes
			fileName = fileName.substring("/".length());
		fileName = fileName.replaceAll("[\\.]{3,}", ".."); // contract sequences of dots
		while (fileName.indexOf("../") != -1) // remove any parent folder steps
			fileName = fileName.replace("../", "");
		while (fileName.indexOf("./") != -1) // remove any local folder steps
			fileName = fileName.replace("./", "");
		return fileName;
	}
	
	private static final String LIST_GROUPS_COMMAND = "listGroups";
	private static final String CLEAN_GROUP_COMMAND = "cleanGroup";
	private static final String RELOAD_GROUPS_COMMAND = "reloadGroups";
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#getActions()
	 */
	public ComponentAction[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentAction ca;
		
		//	list file groups
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return LIST_FILE_GROUPS_COMMAND;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	indicate list of file groups coming
				output.write(LIST_FILE_GROUPS_COMMAND);
				output.newLine();
				
				//	send TSV list of file groups (including filters, etc.)
				ArrayList fileGroupNames = new ArrayList(fileGroupsByName.keySet());
				for (int g = 0; g < fileGroupNames.size(); g++) {
					FileGroup fg = ((FileGroup) fileGroupsByName.get(fileGroupNames.get(g)));
					output.write(fg.descriptor.toTsvString());
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	list (visible) contents of file group
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return LIST_FILES_COMMAND;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	read file group name & check file group proper
				String fileGroupName = input.readLine();
				FileGroup fileGroup = ((FileGroup) fileGroupsByName.get(fileGroupName));
				if (fileGroup == null) {
					output.write("Invalid file group name '" + fileGroupName + "'");
					output.newLine();
					return;
				}
				
				//	check folder (just to be sure)
				File fgFolder = new File(fileGroupRoot, fileGroupName);
				if (!fgFolder.exists() || !fgFolder.isDirectory()) {
					output.write("Invalid file group name '" + fileGroupName + "'");
					output.newLine();
					return;
				}
				
				//	indicate file list coming
				output.write(LIST_FILES_COMMAND);
				output.newLine();
				listFiles(fileGroup, output);
			}
		};
		cal.add(ca);
		
		//	fetch actual data file
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_FILE_COMMAND;
			}
			public void performActionNetwork(BufferedLineInputStream input, BufferedLineOutputStream output) throws IOException {
				
				//	read file group name & check file group proper
				String fileGroupName = input.readLine();
				FileGroup fileGroup = ((FileGroup) fileGroupsByName.get(fileGroupName));
				if (fileGroup == null) {
					output.write("Invalid file group name '" + fileGroupName + "'");
					output.newLine();
					return;
				}
				
				//	check folder (just to be sure)
				File fgFolder = new File(fileGroupRoot, fileGroupName);
				if (!fgFolder.exists() || !fgFolder.isDirectory()) {
					output.write("Invalid file group name '" + fileGroupName + "'");
					output.newLine();
					return;
				}
				
				//	get and check actual file name
				String fileName = input.readLine();
				String cleanFileName = cleanFileName(fileName);
				if (cleanFileName == null) {
					output.write("Invalid file name '" + fileName + "'");
					output.newLine();
					return;
				}
				
				//	check file on disk, as well as filter
				File file = new File(fgFolder, cleanFileName);
				if (!file.exists() || file.isDirectory() || !fileGroup.accept(file)) {
					output.write("Invalid file name '" + fileName + "'");
					output.newLine();
					return;
				}
				
				//	indicate file coming
				output.write(GET_FILE_COMMAND);
				output.newLine();
				
				//	send data file as single-entry ZIP
				ZipOutputStream zip = new ZipOutputStream(output);
				
				//	send file modification time
				ZipEntry lastModZe = new ZipEntry(LAST_MODIFIED_ZIP_ENTRY_NAME);
				zip.putNextEntry(lastModZe);
				long lastMod = file.lastModified();
				for (int rs = 56; rs != -8; rs -=8)
					zip.write((int) ((lastMod >>> rs) & 0x00000000000000FFL));
				zip.flush();
				zip.closeEntry();
				
				//	send actual file
				ZipEntry ze = new ZipEntry(file.getName());
				ze.setTime(lastMod);
				zip.putNextEntry(ze);
				BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(file));
				byte[] buffer = new byte[1024];
				for (int r; (r = fileIn.read(buffer, 0, buffer.length)) != -1;)
					zip.write(buffer, 0, r);
				fileIn.close();
				zip.flush();
				zip.closeEntry();
				zip.finish();
			}
		};
		cal.add(ca);
		
		//	upload or update hosted file
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return UPDATE_FILE_COMMAND;
			}
			public void performActionNetwork(BufferedLineInputStream input, BufferedLineOutputStream output) throws IOException {
				
				// check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					logWarning("Request for invalid session - " + sessionId);
					return;
				}
				
				//	read file group name & check file group proper
				String fileGroupName = input.readLine();
				FileGroup fileGroup = ((FileGroup) fileGroupsByName.get(fileGroupName));
				if (fileGroup == null) {
					output.write("Invalid file group name '" + fileGroupName + "'");
					output.newLine();
					return;
				}
				
				//	check file group proper
				File fgFolder = new File(fileGroupRoot, fileGroupName);
				if (!fgFolder.exists() || !fgFolder.isDirectory()) {
					output.write("Invalid file group name '" + fileGroupName + "'");
					output.newLine();
					return;
				}
				
				//	check permission TODOnot check actual upload/update permission
				if (!uaa.hasSessionPermission(sessionId, getPermissionName(fileGroupName), false)) {
					output.write("Insufficient permissions to update a file");
					output.newLine();
					return;
				}
				
				//	get and check actual file name
				String fileName = input.readLine();
				String cleanFileName = cleanFileName(fileName);
				if ((cleanFileName == null) || !fileGroup.acceptName(cleanFileName)) {
					output.write("Invalid file name '" + fileName + "'");
					output.newLine();
					return;
				}
				
				//	check file on disk
				File file = new File(fgFolder, cleanFileName);
				if (file.isDirectory()) {
					output.write("Cannot update folder '" + fileName + "'");
					output.newLine();
					return;
				}
				
				//	receive and store file
				ZipInputStream zin = new ZipInputStream(input);
				byte[] buffer = new byte[1024];
				long lastMod = -1;
				for (ZipEntry ze; (ze = zin.getNextEntry()) != null;) {
					
					//	catch timestamp for subsequent file
					if (LAST_MODIFIED_ZIP_ENTRY_NAME.equals(ze.getName())) {
						lastMod = 0;
						for (int r; (r = zin.read()) != -1;) {
							lastMod <<= 8;
							lastMod |= (0x00000000000000FFL & r);
						}
						continue;
					}
					
					//	read file proper
					File fileWriting = new File(file.getParentFile(), (file.getName() + ".writing"));
					fileWriting.getParentFile().mkdirs();
					BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream(fileWriting));
					for (int r; (r = zin.read(buffer, 0, buffer.length)) != -1;)
						fileOut.write(buffer, 0, r);
					fileOut.flush();
					fileOut.close();
//					fileWriting.setLastModified(ze.getTime());
					if (lastMod == -1)
						fileWriting.setLastModified(ze.getTime());
					else fileWriting.setLastModified(lastMod);
					if (file.exists()) {
						File oldFile = new File(file.getAbsolutePath());
						oldFile.renameTo(new File(file.getParentFile(), (file.getName() + "." + System.currentTimeMillis() + ".old")));
					}
					fileWriting.renameTo(file);
					//	NO USE SETTING TIMESTAMP TWICE, inaccuracy comes from representation in ZIP entry using _lossy_ conversion
//					fileWriting.setLastModified(ze.getTime());
					lastMod = -1;
					break; // we're only receiving single file
				}
				
				//	report success
				output.write(UPDATE_FILE_COMMAND);
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	delete hosted file
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return DELETE_FILE_COMMAND;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				// check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					logWarning("Request for invalid session - " + sessionId);
					return;
				}
				
				//	read file group name & check file group proper
				String fileGroupName = input.readLine();
				FileGroup fileGroup = ((FileGroup) fileGroupsByName.get(fileGroupName));
				if (fileGroup == null) {
					output.write("Invalid file group name '" + fileGroupName + "'");
					output.newLine();
					return;
				}
				
				//	check file group proper
				File fgFolder = new File(fileGroupRoot, fileGroupName);
				if (!fgFolder.exists() || !fgFolder.isDirectory()) {
					output.write("Invalid file group name '" + fileGroupName + "'");
					output.newLine();
					return;
				}
				
				//	check permission TODOnot check actual delete permission
				if (!uaa.hasSessionPermission(sessionId, getPermissionName(fileGroupName), false)) {
					output.write("Insufficient permissions to delete a file");
					output.newLine();
					return;
				}
				
				//	get and check actual file name
				String fileName = input.readLine();
				String cleanFileName = cleanFileName(fileName);
				if ((cleanFileName == null) || !fileGroup.acceptName(cleanFileName)) {
					output.write("Invalid file name '" + fileName + "'");
					output.newLine();
					return;
				}
				
				//	check file on disk
				File file = new File(fgFolder, cleanFileName);
				if (!file.exists() || file.isDirectory()) {
					output.write("Invalid file name '" + fileName + "'");
					output.newLine();
					return;
				}
				
				//	delete file (actually rename to '.old') and report success
				file.renameTo(new File(file.getParentFile(), (file.getName() + "." + System.currentTimeMillis() + ".old")));
				output.write(DELETE_FILE_COMMAND);
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	offer console action listing file groups
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return LIST_GROUPS_COMMAND;
			}
			public String[] getExplanation() {
				String[] desc = {
					LIST_GROUPS_COMMAND,
					"List the file groups currently hosted by GoldenGATE FDP"
				};
				return desc;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					ArrayList fileGroupNames = new ArrayList(fileGroupsByName.keySet());
					this.reportResult("There are currently " + fileGroupNames.size() + " file groups:");
					for (int g = 0; g < fileGroupNames.size(); g++)
						this.reportResult(" - " + ((String) fileGroupNames.get(g)));
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments");
			}
		};
		cal.add(ca);
		
		//	offer console action cleaning file groups (deleting '.old' and '.new' files, etc.)
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return CLEAN_GROUP_COMMAND;
			}
			public String[] getExplanation() {
				String[] desc = {
					CLEAN_GROUP_COMMAND + " <groupName>",
					"Clean up the filtered files in a file group:",
					"- <groupName>: the name of the file group to clean",
				};
				return desc;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1)
					cleanFileGroup(arguments[0], this);
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the name of the file group to clean as the only argument");
			}
		};
		cal.add(ca);
		
		//	offer console action reloading file groups at runtime
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return RELOAD_GROUPS_COMMAND;
			}
			public String[] getExplanation() {
				String[] desc = {
					RELOAD_GROUPS_COMMAND,
					"Reload the list of file groups to be hosted by GoldenGATE FDP"
				};
				return desc;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0)
					loadFileGroups(this);
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments");
			}
		};
		cal.add(ca);
		
		//	finally ...
		return ((ComponentAction[]) cal.toArray(new ComponentAction[cal.size()]));
	}
	
//	private LruCache fileListCache = new LruCache("FdpFileListCache", 256, 0, (60 * 3), (60 * 15)); // timeouts in seconds, not milliseconds
	void listFiles(FileGroup fileGroup, BufferedWriter output) throws IOException {
		ArrayList files = new ArrayList();
		ArrayList filePrefixes = new ArrayList();
		File fgFolder = new File(this.fileGroupRoot, fileGroup.descriptor.name);
		files.add(fgFolder);
		filePrefixes.add(null);
		for (int f = 0; f < files.size(); f++) {
			File file = ((File) files.get(f));
			String filePrefix = ((String) filePrefixes.get(f));
			if (file.isDirectory()) {
				if (file.getName().endsWith(".old"))
					continue;
				File[] subFiles = file.listFiles(fileGroup);
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
				output.write(filePrefix);
				output.write(file.getName());
				output.write("\t");
				output.write("" + file.length());
				output.write("\t");
				output.write("" + file.lastModified());
				output.newLine();
			}
		}
	}
	
	void cleanFileGroup(String fileGroupName, ComponentActionConsole cac) {
		FileGroup fileGroup = ((FileGroup) this.fileGroupsByName.get(fileGroupName));
		if (fileGroup == null) {
			cac.reportError(" Invalid file group name '" + fileGroupName + "', use '" + LIST_GROUPS_COMMAND + "' to list available file groups");
			return;
		}
		//	TODO maybe run this in dedicated thread ???
		ArrayList files = new ArrayList();
		File fgFolder = new File(this.fileGroupRoot, fileGroupName);
		files.add(fgFolder);
		int folderCount = 0;
		int fileCount = 0;
		int cleanCount = 0;
		long currentTime = System.currentTimeMillis();
		for (int f = 0; f < files.size(); f++) {
			File file = ((File) files.get(f));
			if (file.isDirectory()) {
				if (file.getName().endsWith(".old"))
					continue;
				if (file != fgFolder)
					folderCount++;
				File[] subFiles = file.listFiles();
				if (subFiles != null)
					files.addAll(Arrays.asList(subFiles));
			}
			else if (file.isFile()) {
				fileCount++;
				String fileName = file.getName();
				if (fileName.endsWith(".old"))
					fileName = null; // deleted or replaced
				else if (fileName.endsWith(".writing") && ((file.lastModified() + (1000 * 60 * 5) /* idle for more than 5 minutes */) < currentTime))
					fileName = null; // upload must have stalled while file being received
				if (fileName == null) {
					file.delete();
					cleanCount++;
				}
			}
		}
		cac.reportResult("Cleaned up " + cleanCount + " of " + fileCount + " files in " + folderCount + " folders");
	}
}
