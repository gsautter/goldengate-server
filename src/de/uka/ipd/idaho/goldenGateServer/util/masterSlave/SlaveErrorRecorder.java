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
package de.uka.ipd.idaho.goldenGateServer.util.masterSlave;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Central drop-off point for stack traces of exceptions that occur in slave
 * processes, to improve overview and access.
 * 
 * @author sautter
 */
public class SlaveErrorRecorder {
	
	/**
	 * Record an error that happened in a slave process.
	 * @param letterCode the letter code of the component owning the slave
	 *            process
	 * @param dataId the ID of the data object the slave was processing when
	 *            the error occurred
	 * @param error the error to record
	 */
	public static void recordError(String letterCode, String dataId, Throwable error) {
		if (errorPath == null)
			return;
		PrintStream errorOut = getErrorFileWriter(letterCode, dataId, error.getClass().getName());
		if (errorOut == null)
			return; // something went wrong
		errorOut.println(letterCode + ": error processing '" + dataId + "'");
		error.printStackTrace(errorOut);
		errorOut.flush();
		errorOut.close();
	}
	
	/**
	 * Record an error that happened in a slave process.
	 * @param letterCode the letter code of the component owning the slave
	 *            process
	 * @param dataId the ID of the data object the slave was processing when
	 *            the error occurred
	 * @param errorClassName the class name of the error to record
	 * @param errorMessage the error message to record
	 * @param stackTrace the stack trace of the error to record
	 */
	public static void recordError(String letterCode, String dataId, String errorClassName, String errorMessage, String[] stackTrace) {
		if (errorPath == null)
			return;
		PrintStream errorOut = getErrorFileWriter(letterCode, dataId, errorClassName);
		if (errorOut == null)
			return; // something went wrong
		errorOut.println(letterCode + ": error processing '" + dataId + "'");
		errorOut.println(errorClassName + ": " + errorMessage);
		for (int t = 0; t < stackTrace.length; t++)
			errorOut.println(stackTrace[t]);
		errorOut.flush();
		errorOut.close();
	}
	
	private static File errorPath = null;
	
	/**
	 * Get the folder error report files are stored in.
	 * @return the error report path
	 */
	public static File getErrorPath() {
		return errorPath;
	}
	
	/**
	 * Set the folder to store error report files in. This method ignores null
	 * arguments.
	 * @param ep the folder to store error report files in
	 */
	public static void setErrorPath(String ep) {
		if (ep == null)
			return;
		if (ep.startsWith("/") || (ep.indexOf(":") != -1))
			errorPath = new File(ep);
		else errorPath = new File(".", ep);
		errorPath.mkdirs();
		if (!errorPath.exists()) {
			System.err.println("SlaveErrorRecorder: could not find or create error report path '" + errorPath.getAbsolutePath() + "'.");
			errorPath = null;
		}
		if (!errorPath.isDirectory()) {
			System.err.println("SlaveErrorRecorder: error report path '" + errorPath.getAbsolutePath() + "' is a file, not a folder.");
			errorPath = null;
		}
	}
	
	/**
	 * Remove the folder to store error report files in to deactivate error
	 * recording.
	 */
	public static void clearErrorPath() {
		errorPath = null;
	}
	
	private static final SimpleDateFormat errorTimestampFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
	private static PrintStream getErrorFileWriter(String letterCode, String dataId, String errorClassName) {
		errorClassName = errorClassName.substring(errorClassName.lastIndexOf(".") + ".".length());
		String errorFileName = (letterCode + "." + dataId + "." + errorClassName + "." + errorTimestampFormat.format(new Date()) + ".txt");
		File errorFile = new File(errorPath, errorFileName);
		try {
			errorFile.createNewFile();
			return new PrintStream(new BufferedOutputStream(new FileOutputStream(errorFile)), false);
		}
		catch (IOException ioe) {
			System.err.println("SlaveErrorRecorder: could not create or open error report file '" + errorFileName + "',");
			System.err.println("  error report folder is '" + errorPath.getAbsolutePath() + "'.");
			ioe.printStackTrace(System.err);
			return null;
		}
	}
}
