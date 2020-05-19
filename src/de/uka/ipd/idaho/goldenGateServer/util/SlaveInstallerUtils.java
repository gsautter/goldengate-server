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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants;

/**
 * Utility class for installing sub applications that run in slave JVMs under
 * the control of a component in a GoldenGATE Server.
 * 
 * @author sautter
 */
public class SlaveInstallerUtils implements GoldenGateServerConstants {
	
	/**
	 * Find a JAR with a given name, seeking upwards from the argument start
	 * folder to the GoldenGATE server root folder, including the latter. This
	 * method also considers the '*Bin' and '*Lib' folders that can come along
	 * with a component's '*Data' folder if the start folder is (a descendant
	 * of) one of the latter.
	 * @param name the name of the JAR to find
	 * @param folder the folder to seek upwards from
	 * @return a file object pointing to the located JAR
	 */
	public static File findJar(String name, File folder) {
		return findJar(name, folder, false);
	}
	
	private static File findJar(String name, File folder, boolean log) {
		if (log) System.out.println(" - checking in folder '" + folder.getAbsolutePath() + "'");
		
		//	check folder proper
		File jarFile = new File(folder, name);
		if (jarFile.exists() && jarFile.isFile())
			return jarFile;
		
		//	check associated binary folders if component data folder
		if (folder.getName().endsWith("Data")) {
			String folderName = folder.getName();
			File parentFolder = folder.getParentFile();
			
			File binFolder = new File(parentFolder, (folderName.substring(0, (folderName.length() - "Data".length())) + "Bin"));
			if (log) System.out.println(" - checking in *Bin folder '" + binFolder.getAbsolutePath() + "'");
			File binJarFile = new File(binFolder, name);
			if (binJarFile.exists() && binJarFile.isFile())
				return binJarFile;
			File libFolder = new File(parentFolder, (folderName.substring(0, (folderName.length() - "Data".length())) + "Lib"));
			if (log) System.out.println(" - checking in *Lib folder '" + libFolder.getAbsolutePath() + "'");
			File libJarFile = new File(libFolder, name);
			if (libJarFile.exists() && libJarFile.isFile())
				return libJarFile;
		}
		
		//	check JAR folders in components folder
		if (COMPONENT_FOLDER_NAME.equals(folder.getName())) {
			File binFolder = new File(folder, "Bin");
			if (log) System.out.println(" - checking in shared Bin folder '" + binFolder.getAbsolutePath() + "'");
			File binJarFile = new File(binFolder, name);
			if (binJarFile.exists() && binJarFile.isFile())
				return binJarFile;
			File libFolder = new File(folder, "Lib");
			if (log) System.out.println(" - checking in shared Lib folder '" + libFolder.getAbsolutePath() + "'");
			File libJarFile = new File(libFolder, name);
			if (libJarFile.exists() && libJarFile.isFile())
				return libJarFile;
		}
		
		//	stop recursive search once we reach root folder
		if (folder.getAbsolutePath().indexOf(COMPONENT_FOLDER_NAME) == -1) {
			if (log) System.out.println(" ==> stopping outside component folder in '" + folder.getAbsolutePath() + "'");
			return null;
		}
			
		//	recurse at most one upwards from components folder (root folder)
		return findJar(name, folder.getParentFile(), log);
	}
	
	/**
	 * Install a JAR required for a sub application in the execution folder of
	 * the latter. This method also replaces existing JARs in the target folder
	 * if a more recent version is found in from the source folder. If throwing
	 * an exception on failure is set to false, the return value indicates
	 * installation success; if it is set to true, this method will either
	 * return true or throw an exception.
	 * @param name the name of the JAR to install
	 * @param sourceFolder the folder to find the JAR from
	 * @param targetFolder the folder to install the JAR in
	 * @param throwExceptionOnFail throw an exception if installation fails?
	 * @return true if installation is successful
	 * @throws RuntimeException
	 */
	public static boolean installJar(String name, File sourceFolder, File targetFolder, boolean throwExceptionOnFail) throws RuntimeException {
		System.out.println("Installing JAR '" + name + "' in " + targetFolder.getAbsolutePath());
		
		//	locate source JAR
		File sourceJar = findJar(name, sourceFolder, true);
		if (sourceJar == null) {
			if (throwExceptionOnFail)
				throw new RuntimeException("Missing JAR: " + name);
			else {
				System.out.println(" ==> missing JAR: " + name);
				return false;
			}
		}
		System.out.println(" - found source JAR at " + sourceJar.getAbsolutePath());
		
		//	install JAR
		return installFile(sourceJar, "JAR", targetFolder, throwExceptionOnFail);
	}
	
	/**
	 * Install a resource file required for a sub application in the execution
	 * folder of the latter. This method also replaces existing JARs in the
	 * target folder if a more recent version is found in from the source
	 * folder. If throwing an exception on failure is set to false, the return
	 * value indicates installation success; if it is set to true, this method
	 * will either return true or throw an exception.
	 * @param name the name of the resource file to install
	 * @param sourceFolder the folder to find the resource file in
	 * @param targetFolder the folder to install the resource file in
	 * @param throwExceptionOnFail throw an exception if installation fails?
	 * @return true if installation is successful
	 * @throws RuntimeException
	 */
	public static boolean installFile(String name, File sourceFolder, File targetFolder, boolean throwExceptionOnFail) throws RuntimeException {
		System.out.println("Installing file '" + name + "' in " + targetFolder.getAbsolutePath());
		
		//	locate source JAR
		File sourceFile = new File(sourceFolder, name);
		if (!sourceFile.exists()) {
			if (throwExceptionOnFail)
				throw new RuntimeException("Missing file: " + name);
			else {
				System.out.println(" ==> missing file: " + name);
				return false;
			}
		}
		System.out.println(" - found source file at " + sourceFile.getAbsolutePath());
		
		//	install JAR
		return installFile(sourceFile, "file", targetFolder, throwExceptionOnFail);
	}
	
	private static boolean installFile(File sourceFile, String type, File targetFolder, boolean throwExceptionOnFail) throws RuntimeException {
		
		//	check target JAR
		File targetFile = new File(targetFolder, sourceFile.getName());
		if ((targetFile.lastModified() + 1000) > sourceFile.lastModified()) {
			System.out.println(" ==> up to date");
			return true;
		}
		
		//	copy (more recent) source JAR to target folder
		try {
			InputStream sourceIn = new BufferedInputStream(new FileInputStream(sourceFile));
			OutputStream targetOut = new BufferedOutputStream(new FileOutputStream(targetFile));
			byte[] buffer = new byte[1024];
			for (int r; (r = sourceIn.read(buffer, 0, buffer.length)) != -1;)
				targetOut.write(buffer, 0, r);
			targetOut.flush();
			targetOut.close();
			sourceIn.close();
			System.out.println(" ==> installed");
			return true;
		}
		catch (IOException ioe) {
			if (throwExceptionOnFail)
				throw new RuntimeException(("Could not install " + type + " '" + sourceFile.getName() + "': " + ioe.getMessage()), ioe);
			else {
				System.out.println(" ==> could not install " + type + " '" + sourceFile.getName() + "': " + ioe.getMessage());
				return false;
			}
		}
		catch (Exception e) {
			e.printStackTrace(System.out);
			if (throwExceptionOnFail)
				throw new RuntimeException(("Could not install " + type + " '" + sourceFile.getName() + "': " + e.getMessage()), e);
			else {
				System.out.println(" ==> could not install " + type + " '" + sourceFile.getName() + "': " + e.getMessage());
				return false;
			}
		}
	}
	
	/**
	 * Install a sub application in the execution folder of the latter based on
	 * its main JAR, including all dependencies listed in the 'Class-Path' line
	 * of its 'MANIFEST.MF' entry. This method also replaces existing JARs in
	 * the target folder if a more recent version is found in from the source
	 * folder. If throwing an exception on failure is set to false, the return
	 * value indicates installation success; if it is set to true, this method
	 * will either return true or throw an exception.
	 * @param name the name of the JAR to install
	 * @param sourceFolder the folder to find the JAR from
	 * @param targetFolder the folder to install the JAR in
	 * @param throwExceptionOnFail throw an exception if installation fails?
	 * @return true if installation is successful
	 * @throws RuntimeException
	 */
	public static boolean installSlaveJar(String name, File sourceFolder, File targetFolder, boolean throwExceptionOnFail) throws RuntimeException {
		System.out.println("Installing slave main JAR '" + name + "' in " + targetFolder.getAbsolutePath());
		
		//	find main JAR
		File mainSourceJar = findJar(name, sourceFolder, true);
		if (mainSourceJar == null) {
			if (throwExceptionOnFail)
				throw new RuntimeException("Missing JAR: " + name);
			else {
				System.out.println(" ==> missing JAR: " + name);
				return false;
			}
		}
		System.out.println(" - found source JAR at " + mainSourceJar.getAbsolutePath());
		
		//	read class path from MANIFEST.MF
		String mainJarClassPath = null;
		try {
			ZipInputStream mainSourceZip = new ZipInputStream(new BufferedInputStream(new FileInputStream(mainSourceJar)));
			for (ZipEntry entry; (entry = mainSourceZip.getNextEntry()) != null;) {
				if (!"META-INF/MANIFEST.MF".equals(entry.getName()))
					continue;
				System.out.println(" - found MANIFEST.MF");
				BufferedReader manifestReader = new BufferedReader(new InputStreamReader(mainSourceZip, "UTF-8"));
				StringBuffer classPath = null;
				for (String manifestLine; (manifestLine = manifestReader.readLine()) != null;) {
					if ((classPath != null) && manifestLine.startsWith(" ")) {
						System.out.println(" - found Class-Path continuation: " + manifestLine);
						classPath.append(manifestLine.substring(" ".length()));
					}
					else if (manifestLine.startsWith("Class-Path:")) {
						System.out.println(" - found Class-Path start: " + manifestLine);
						classPath = new StringBuffer();
						classPath.append(manifestLine.substring("Class-Path:".length()));
					}
					else if (classPath != null) {
						mainJarClassPath = classPath.toString().trim();
						break; // we got all we came for
					}
				}
				manifestReader.close();
				if (classPath == null)
					mainJarClassPath = ""; // main JAR might not have dependencies and Class-Path entry, but we did find the MANIFEST.MF
				break; // not needing anything but the MANIFEST.MF for now
			}
			mainSourceZip.close();
		}
		catch (IOException ioe) {
			if (throwExceptionOnFail)
				throw new RuntimeException(("Could not install JAR '" + name + "': " + ioe.getMessage()), ioe);
			else {
				System.out.println(" ==> could not install JAR '" + name + "': " + ioe.getMessage());
				return false;
			}
		}
		if (mainJarClassPath == null) {
			if (throwExceptionOnFail)
				throw new RuntimeException("Could not find MANIFEST.MF in JAR '" + name + "'");
			else {
				System.out.println(" ==> could not find MANIFEST.MF in JAR '" + name + "'");
				return false;
			}
		}
		System.out.println(" - found class path in main JAR: " + mainJarClassPath);
		
		//	install dependencies
		String[] requiredJarNames = mainJarClassPath.split("\\s+");
		for (int j = 0; j < requiredJarNames.length; j++) {
			if (!installJar(requiredJarNames[j], sourceFolder, targetFolder, throwExceptionOnFail))
				return false;
		}
		if (mainJarClassPath.length() == 0)
			System.out.println(" - no dependencies to install");
		else System.out.println("Installing main JAR '" + name + "' in " + targetFolder.getAbsolutePath());
		
		//	install main JAR proper
		return installFile(mainSourceJar, "JAR", targetFolder, throwExceptionOnFail);
	}
//	
//	//	TEST ONLY !!!	
//	public static void main(String[] args) throws Exception {
//		File sourceFolder = new File("E:/GoldenGATEv3.IMS/Components/GgServerIMIData/PdfImporterData");
//		File targetFolder = new File("E:/GoldenGATEv3.IMS/InstallTest/working/imi/SyncPDF");
//		targetFolder.mkdirs();
//		installSlaveJar("PdfImporterSlave.jar", sourceFolder, targetFolder, true);
//		installFile("fontDecoderCharset.cnfg", sourceFolder, targetFolder, true);
//		installFile("nonExistentTest.noError.txt", sourceFolder, targetFolder, false);
//		installFile("nonExistentTest.withError.txt", sourceFolder, targetFolder, true);
//	}
}
