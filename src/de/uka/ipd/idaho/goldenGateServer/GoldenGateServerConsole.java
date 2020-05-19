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

/**
 * @author sautter
 */
public class GoldenGateServerConsole {
	
	//	TODO facilitate ingesting script files from the file system (with '\i', as in psql)
	
	/* TODO Only allow valid "change component":
- create list of valid component prefixes during startup ...
- .. and transmit it to console on login ...
- ... as suffix of "WELCOME" line, tab separated
- change to closest component if:
  - Levenshtein distance at most <X>
  - next closest Levenshtein distance at least <X+2>
- maybe do same for console actions ...
- ... if maybe with minimum distance of 3 to ensure intent on invasive commands
	 */
	
	private static String currentLetterCode = "";
	public static void main(String[] args) throws Exception {
		int ncPort = 15808;
		int cTimeout = 2;
		String cAuth = null;
		for (int a = 0; a < args.length; a++) {
			args[a] = args[a].trim();
			if (args[a].startsWith("-p="))
				ncPort = Integer.parseInt(args[a].substring("-p=".length()).trim());
			if (args[a].startsWith("-t="))
				cTimeout = Integer.parseInt(args[a].substring("-t=".length()).trim());
			if (args[a].startsWith("-a="))
				cAuth = args[a].substring("-a=".length()).trim();
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
				cOut.println(cAuth);
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
						printLocalMessage("Use '-p=<portNumber>' to connect to a non-standard port");
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
			printLocalError("There is already another console connected (only one can be at a time),\n" +
					"  or the server failed to respond within " + cTimeout + " seconds.");
			if (cTimeout <= 2)
				printLocalMessage("Use '-t=<timeoutInSeconds>' to specify a longer timeout");
			return;
		}
		
		//	authentication successful, initialize
		if ("WELCOME".equals(response)) {
			
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
				currentLetterCode = commandString.substring("cc".length()).trim();
				if (currentLetterCode.indexOf(' ') != -1)
					currentLetterCode = currentLetterCode.substring(0, currentLetterCode.indexOf(' ')).trim();
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
		if ((msg.length() > 4) && msg.substring(0, 4).matches("[REWID][BCN]\\:\\:")) {
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