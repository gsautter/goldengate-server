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
 *     * Neither the name of the Universität Karlsruhe (TH) nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY UNIVERSITÄT KARLSRUHE (TH) / KIT AND CONTRIBUTORS 
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
package de.uka.ipd.idaho.goldenGateServer.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Properties;

/**
 * Logger for requests to a GoldenGATE Server web frontend. This class is
 * initialized by IndexServlet, so the latter has to be part of the web-app for
 * this logging class to log to a file. Otherwise, the logging messages to to
 * System.out.
 * 
 * @author sautter
 */
public class GgServerWebFrontendLogger {
	
	private static Properties sessionsToUsers = new Properties();
	private static Properties usersToSessions = new Properties();
	private static Properties sessionsToAddresses = new Properties();
	private static Properties addressesToSessions = new Properties();
	private static Properties addressesToUsers = new Properties();
	private static Properties usersToAddresses = new Properties();
	
	private static File logFile = null;
	private static PrintStream pw = null;
	
	/**
	 * Write a message to the log. If the argument message contains line breaks,
	 * they are converted into '&lt;br&gt;'.
	 * @param sessionId the HTTP session the request comes from
	 * @param message the log message to write
	 */
	public static void logForSession(String sessionId, String message) {
		String userName = sessionsToUsers.getProperty(sessionId, "UNKNOWN_USER");
		String remoteAddress = sessionsToAddresses.getProperty(sessionId, "UNKNOWN_HOST");
		if (pw == null)
			System.out.println("LOG: " + prepareMessage(userName, sessionId, message));
		else pw.println(prepareMessage(userName, remoteAddress, message));
	}
	
	/**
	 * Write a message to the log. If the argument message contains line breaks,
	 * they are converted into '&lt;br&gt;'.
	 * @param userName the name of the user the log entry refers to
	 * @param message the log message to write
	 */
	public static void logForUser(String userName, String message) {
		String remoteAddress = usersToAddresses.getProperty(userName, "UNKNOWN_HOST");
		if (pw == null)
			System.out.println("LOG: " + prepareMessage(userName, remoteAddress, message));
		else pw.println(prepareMessage(userName, remoteAddress, message));
	}
	
	/**
	 * Write a message to the log. If the argument message contains line breaks,
	 * they are converted into '&lt;br&gt;'.
	 * @param remoteAddress the address the request comes from
	 * @param message the log message to write
	 */
	public static void logForAddress(String remoteAddress, String message) {
		String userName = addressesToUsers.getProperty(remoteAddress, "UNKNOWN_USER");
		if (pw == null)
			System.out.println("LOG: " + prepareMessage(userName, remoteAddress, message));
		else pw.println(prepareMessage(userName, remoteAddress, message));
	}
	
	private static String prepareMessage(String userName, String remoteAddress, String message) {
		StringBuffer msg = new StringBuffer("" + System.currentTimeMillis());
		msg.append(',');
		msg.append(userName);
		msg.append(',');
		msg.append(remoteAddress);
		msg.append(',');
		msg.append(getLoggerClassName());
		msg.append(',');
		msg.append(message.replaceAll("[\\n\\r]++", "<br>"));
		return msg.toString();
	}
	
	private static String getLoggerClassName() {
		StackTraceElement[] ste = Thread.currentThread().getStackTrace();
		String loggerClassName = null;
		boolean nextIsLoggerClassName = false;
		for (int e = 0; e < ste.length; e++) {
			if (GgServerWebFrontendLogger.class.getName().equals(ste[e].getClassName()) && ste[e].getMethodName().startsWith("logFor"))
				nextIsLoggerClassName = true;
			else if (nextIsLoggerClassName) {
				loggerClassName = ste[e].getClassName();
				e = ste.length;
			}
		}
		if (loggerClassName != null)
			loggerClassName = loggerClassName.substring(loggerClassName.lastIndexOf('.') + 1);
		return loggerClassName;
	}
	
	/**
	 * Map an HTTP session ID to a specific user. This facilitates including the
	 * user name in log messages. One of the three arguments may be null.
	 * @param sessionId the ID of the session
	 * @param userName the name of the user who has logged in
	 * @param remoteAddress the address the request comes from
	 */
	public static void setDataForSession(String sessionId, String userName, String remoteAddress) {
		if ((sessionId != null) && (userName != null)) {
			sessionsToUsers.setProperty(sessionId, userName);
			usersToSessions.setProperty(userName, sessionId);
		}
		if ((sessionId != null) && (remoteAddress != null)) {
			sessionsToAddresses.setProperty(sessionId, remoteAddress);
			addressesToSessions.setProperty(remoteAddress, sessionId);
		}
		if ((userName != null) && (remoteAddress != null)) {
			addressesToUsers.setProperty(remoteAddress, userName);
			usersToAddresses.setProperty(userName, remoteAddress);
		}
	}
	
	/**
	 * Remove an adddress / user mapping, starting with the user name.
	 * @param userName the name of the user who has logged out
	 */
	public static void unmapUser(String userName) {
		clearMapping(usersToSessions.getProperty(userName, "UNKNOWN_SESSION"), userName, usersToAddresses.getProperty(userName, "UNKNOWN_HOST"));
	}
	
	/**
	 * Remove an adddress / user mapping, starting with the address.
	 * @param remoteAddress  the address the logout comes from
	 */
	public static void unmapAddress(String remoteAddress) {
		clearMapping(addressesToSessions.getProperty(remoteAddress, "UNKNOWN_SESSION"), addressesToUsers.getProperty(remoteAddress, "UNKNOWN_USER"), remoteAddress);
	}
	
	/**
	 * Remove an adddress / user mapping, starting with the address.
	 * @param sessionId  the address the logout comes from
	 */
	public static void unmapSession(String sessionId) {
		clearMapping(sessionId, sessionsToUsers.getProperty(sessionId, "UNKNOWN_USER"), sessionsToAddresses.getProperty(sessionId, "UNKNOWN_HOST"));
	}
	
	private static void clearMapping(String sessionId, String userName, String remoteAddress) {
		sessionsToUsers.remove(sessionId);
		usersToSessions.remove(userName);
		sessionsToAddresses.remove(sessionId);
		addressesToSessions.remove(remoteAddress);
		addressesToUsers.remove(remoteAddress);
		usersToAddresses.remove(userName);
	}
	
	/**
	 * 
	 * @param logFolder
	 */
	public static void setLogFolder(File logFolder) {
		if (logFolder == null) {
			if (pw != null) {
				pw.flush();
				pw.close();
			}
			logFile = null;
			pw = null;
		}
		
		else if (logFolder.isDirectory()) {
			if (pw != null) {
				pw.flush();
				pw.close();
			}
			pw = null;
			
			logFile = new File(logFolder, ("GgServerWebFrontend." + System.currentTimeMillis() + ".log"));
			
			try {
				logFile.createNewFile();
				pw = new PrintStream(new FileOutputStream(logFile));
			}
			catch (IOException ioe) {
				System.out.println("Error creating log file '" + logFile.getAbsolutePath() + "': " + ioe.getMessage());
				ioe.printStackTrace(System.out);
			}
		}
	}
}
