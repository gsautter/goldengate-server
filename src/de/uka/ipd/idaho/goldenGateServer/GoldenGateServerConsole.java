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
package de.uka.ipd.idaho.goldenGateServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * @author sautter
 */
public class GoldenGateServerConsole {
	
	//	TODO facilitate ingesting script files from the file system (with '\i', as in psql)
	
	private static TreeMap validLetterCodes = new TreeMap(String.CASE_INSENSITIVE_ORDER);
	private static String currentLetterCode = "";
	public static void main(String[] args) throws Exception {
		int ncPort = 15808;
		int cTimeout = 2;
		String cAuth = null;
		boolean cKillPrevious = false;
		for (int a = 0; a < args.length; a++) {
			args[a] = args[a].trim();
			if (args[a].startsWith("-p="))
				ncPort = Integer.parseInt(args[a].substring("-p=".length()).trim());
			else if (args[a].startsWith("-t="))
				cTimeout = Integer.parseInt(args[a].substring("-t=".length()).trim());
			else if (args[a].startsWith("-a="))
				cAuth = args[a].substring("-a=".length()).trim();
			else if (args[a].equals("-k"))
				cKillPrevious = true;
		}
		if (cTimeout < 2)
			cTimeout = 2;
		
		//	start reading console input
		final BufferedReader commandReader = new BufferedReader(new InputStreamReader(System.in));
		
		//	get password if not submitted via console command
		if (cAuth == null) {
			
			//	start masking System.in
			ConsoleMasker cm = new ConsoleMasker();
			cm.start();
			
			//	read password
			System.out.print("Enter Password: ");
			cAuth = commandReader.readLine();
			
			//	stop masking System.in
			cm.exit();
		}
		
		//	connect to server
		Socket cSocket = null;
		PrintStream cOut;
		int attempts = 0;
		while (true) {
			attempts++;
			printLocalMessage("Connecting to GoldenGATE Server on port " + ncPort + ", attempt " + attempts + " ...");
			try {
				cSocket = new Socket();
				cSocket.connect(new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), ncPort), 2000);
				
				cOut = new PrintStream(cSocket.getOutputStream(), true);
				printLocalMessage(" ==> Connection successful, authenticating ...");
				cOut.println((cKillPrevious ? "KILL " : "") + cAuth);
				break;
			}
			catch (ConnectException ce) {
				if (attempts < 10) {
					printLocalMessage(" ==> Connection failed, will retry in a second");
					Thread.sleep(1000);
					continue;
				}
				else {
					printLocalError(" ==> Connection failed, no more attempts");
					printLocalError("");
					printLocalError("Unable to connect to GoldenGATE Server on port " + ncPort + ".");
					if (ncPort == 15808)
						printLocalMessage("Use the '-p=<portNumber>' argument to connect to a non-standard port");
					printLocalMessage("If you just started GoldenGATE Server, please try again in a few seconds");
					if (cSocket != null) try {
						cSocket.close();
					} catch (Exception e) { /* we'r quitting anyway, just cleaning up */ }
					return;
				}
			}
			catch (SocketTimeoutException ste) {
				printLocalError(" ==> Connection failed, only one console can be connected at a time.");
				if (cSocket != null) try {
					cSocket.close();
				} catch (Exception e) { /* we'r quitting anyway, just cleaning up */ }
				return;
			}
		}
		
		//	handle timeout
		final Socket socket = cSocket;
		final PrintStream srvOut = cOut;
		final BufferedReader srvIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		socket.setSoTimeout(1000 * cTimeout);
		String response;
		try {
			response = srvIn.readLine();
		}
		catch (SocketTimeoutException ste) {
			printLocalError("There is already another console connected (only one can be at a time),\r\n" +
					"  or the server failed to respond within " + cTimeout + " seconds.");
			if (cTimeout <= 2)
				printLocalMessage("Use the '-t=<timeoutInSeconds>' argument to specify a longer timeout");
			return;
		}
		
		//	authentication successful, initialize
//		if ("WELCOME".equals(response)) {
		if ("WELCOME".equals(response) || response.startsWith("WELCOME ")) {
			
			//	crop and split valid letter codes
			if (response.startsWith("WELCOME ")) {
				String[] letterCodes = response.substring("WELCOME ".length()).split("\\s+");
				for (int c = 0; c < letterCodes.length; c++)
					validLetterCodes.put(letterCodes[c], letterCodes[c]);
			}
			
			//	print welcome message
			printLocalMessage(" ==> Authentication successful, have fun");
			printLocalMessage("");
			printLocalMessage("Global commands:");
			printLocalMessage(" - type 'exit' to exit the console");
			printLocalMessage(" - type '?' to display help information");
			printLocalMessage("");
			
			//	relay server output to console
			socket.setSoTimeout(0);
			Thread sInThread = new Thread() {
				public void run() {
					String line;
					boolean remoteExit = true;
					try {
						while ((line = srvIn.readLine()) != null) {
							if ("GOODBYE".equals(line))
								remoteExit = false;
							else if ("COMMAND_DONE".equals(line))
								printInputHeader();
							else printServerMessage(line);
						}
						
						if (remoteExit)
							printLocalError("Disconnected by server.");
						else socket.close();
						System.exit(0);
					}
					catch (IOException ioe) {
						String msg = ioe.getMessage();
						if ((msg != null) && msg.startsWith("Connection reset")) {
							printLocalError("Disconnected by server.");
							System.exit(0);
						}
						else printLocalMessage("Error reading server response: " + ioe.getMessage());
					}
				}
			};
			sInThread.start();
		}
		
		//	report lack of kill parameter
		else if ("USE_KILL".equals(response)) {
			printLocalError("There is already another console connected (only one can be at a time)\r\n" +
					"Use the '-k' argument to kill the existing connection.");
			return;
		}
		
		//	report unnecessary use kill parameter
		else if ("DONT_KILL".equals(response)) {
			printLocalError("There is no other console connected, why are you trying to kill it?\r\n" +
					"Don't be brutal unless you have to, connect normally.");
			return;
		}
		
		//	report error and exit
		else {
			printLocalError(" ==> " + response);
			return;
		}
		
		//	set to empty component prefix
		srvOut.println("cc");
		
		//	read and relay commands
		while (true) try {
			String commandString = commandReader.readLine();
			if (commandString == null)
				return;
			commandString = commandString.trim();
			
			//	handle exit command locally
			if (("".equals(currentLetterCode) && "exit".equals(commandString)) || ".exit".equals(commandString)) {
				srvOut.println("GOODBYE");
				return;
			}
			
			//	change local letter code (for displaying)
			if ("cc".equals(commandString))
				currentLetterCode = "";
			else if (commandString.startsWith("cc ")) {
//				currentLetterCode = commandString.substring("cc".length()).trim();
//				if (currentLetterCode.indexOf(' ') != -1)
//					currentLetterCode = currentLetterCode.substring(0, currentLetterCode.indexOf(' ')).trim();
				String letterCode = commandString.substring("cc ".length()).trim();
				String letterCodeSuffix = "";
				if (letterCode.indexOf(' ') != -1) {
					letterCodeSuffix = letterCode.substring(letterCode.indexOf(' '));
					letterCode = letterCode.substring(0, letterCode.indexOf(' '));
				}
				String csLetterCode = ((String) validLetterCodes.get(letterCode)); // case insensitive map normalizes case
				if (letterCode.equals(csLetterCode)) {} // input correct, including case
				else if (csLetterCode != null) /* correct case of letter code */ {
					letterCode = csLetterCode;
					commandString = ("cc " + letterCode + letterCodeSuffix);
				}
				else {
					int maxDist = (letterCode.length() / 3); // let's not get all too speculative
					int minDist = (maxDist + 1);
					ArrayList minDistLetterCodes = new ArrayList(4);
					for (Iterator lcit = validLetterCodes.keySet().iterator(); lcit.hasNext();) {
						String lc = ((String) lcit.next());
						int lcDist = StringUtils.getLevenshteinDistance(letterCode, lc, (maxDist + 1), false);
						if (maxDist < lcDist)
							continue; // too far away
						if (minDist < lcDist)
							continue; // further away than current best match
						if (lcDist < minDist) /* new (so far) unambiguous best match */ {
							minDistLetterCodes.clear();
							minDist = lcDist;
						}
						minDistLetterCodes.add(lc);
					}
					if (minDistLetterCodes.size() == 1) // we have an unambiguous best match
						letterCode = ((String) minDistLetterCodes.get(0));
					else {
						if (minDistLetterCodes.isEmpty())
							printLocalError("Invalid letter code '" + letterCode + "'");
						else {
							printLocalError("Invalid letter code '" + letterCode + "', equally similar to");
							for (int c = 0; c < minDistLetterCodes.size(); c++)
								printLocalError(" - " + minDistLetterCodes.get(c));
						}
						printInputHeader();
						continue;
					}
				}
				currentLetterCode = letterCode;
			}
			
			//	send command to server if not empty
			if (commandString.length() == 0)
				printInputHeader();
			else srvOut.println(commandString.trim());
		}
		catch (Exception e) {
			printLocalError("Error reading or executing console command: " + e.getMessage());
		}
	}
	
	private static String stackTraceFormat = null;
	static void printServerMessage(String msg) {
		
		//	catch start and end of stack traces
		if (msg.matches("T[BCN]\\:\\:")) {
			stackTraceFormat = GoldenGateServerMessageFormatter.getFormat(msg.charAt(0), msg.charAt(1));
			return; // actual stack trace only starts in next line
		}
		else if ("::T".equals(msg)) {
			stackTraceFormat = null;
			return; // only marks end of stack trace
		}
		
		//	crop format off message
		String format;
		if ((msg.length() >= 4) && msg.substring(0, 4).matches("[REWID][BCN]\\:\\:")) {
			format = GoldenGateServerMessageFormatter.getFormat(msg.charAt(0), msg.charAt(1));
			msg = msg.substring(4);
		}
		else if (stackTraceFormat != null)
			format = stackTraceFormat;
		else format = GoldenGateServerMessageFormatter.getFormat('R', 'C');
		
		//	print message
		GoldenGateServerMessageFormatter.printMessage(msg, format, INPUT_FORMAT, System.out);
	}
	
	static void printLocalMessage(String msg) {
		GoldenGateServerMessageFormatter.printMessage(msg, LOCAL_MESSAGE_FORMAT, INPUT_FORMAT, System.out);
	}
	
	static void printLocalError(String msg) {
		GoldenGateServerMessageFormatter.printMessage(msg, LOCAL_ERROR_FORMAT, INPUT_FORMAT, System.out);
	}
	
	static void printInputHeader() {
		System.out.print("\u001B[0m" + "\u001B[" + INPUT_HEADER_FORMAT + "m" + "GgServer" + ((currentLetterCode.length() == 0) ? "" : ("." + currentLetterCode)) + ">" + "\u001B[0m" + "\u001B[" + INPUT_FORMAT + "m");
	}
	
	private static final String LOCAL_MESSAGE_FORMAT = "38;5;27;40";
	private static final String LOCAL_ERROR_FORMAT = "38;5;196;40";
	private static final String INPUT_HEADER_FORMAT = "1;32;40";
	private static final String INPUT_FORMAT = "32;40";
	
	private static class ConsoleMasker extends Thread {
		private boolean running = true;
		public void run() {
			while (this.running) {
				System.out.print("\b*");
				try {
					sleep(10);
				} catch (InterruptedException ie) {}
			}
			System.out.print("\b");
		}
		public synchronized void exit() {
			this.running = false;
		}
	}
}