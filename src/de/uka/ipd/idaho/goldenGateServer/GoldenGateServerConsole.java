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
 *
 */
public class GoldenGateServerConsole {
	
	//	TODO facilitate ingesting script files from the file system (with '\i', as in psql)
	
	private static String currentLetterCode = "";
	public static void main(String[] args) throws Exception {
		int ncPort = 15808;
		int cTimeout = 2;
		for (int a = 0; a < args.length; a++) {
			args[a] = args[a].trim();
			if (args[a].startsWith("-p="))
				ncPort = Integer.parseInt(args[a].substring("-p=".length()).trim());
			if (args[a].startsWith("-t="))
				cTimeout = Integer.parseInt(args[a].substring("-t=".length()).trim());
		}
		if (cTimeout < 2)
			cTimeout = 2;
		
		System.out.print("Enter Password: ");
		final BufferedReader commandReader = new BufferedReader(new InputStreamReader(System.in));
		String password = commandReader.readLine();
		
		Socket cSocket;
		PrintStream cOut;
		int attempts = 0;
		while (true) {
			attempts++;
			System.out.println("Connecting to GoldenGATE Server on port " + ncPort + ", attempt " + attempts + " ...");
			try {
				cSocket = new Socket();
				cSocket.connect(new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), ncPort), 2000);
				
				cOut = new PrintStream(cSocket.getOutputStream(), true);
				System.out.println(" ==> Connection successful, authenticating ...");
				cOut.println(password);
				break;
			}
			catch (ConnectException ce) {
				if (attempts < 10) {
					System.out.println(" ==> Connection failed, will retry in a second");
					Thread.sleep(1000);
					continue;
				}
				else {
					System.out.println(" ==> Connection failed, no more attempts");
					System.out.println("");
					System.out.println("Unable to connect to GoldenGATE Server on port " + ncPort + ".");
					if (ncPort == 15808)
						System.out.println("Use '-p=<portNumber>' to connect to a non-standard port");
					System.out.println("If you just started GoldenGATE Server, please try again in a few seconds");
					return;
				}
			}
			catch (SocketTimeoutException ste) {
				System.out.println(" ==> Connection failed, only one console can be connected at a time.");
				return;
			}
		}
		
		final Socket socket = cSocket;
		final PrintStream sOut = cOut;
		
		final BufferedReader sIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		socket.setSoTimeout(1000 * cTimeout);
		String response;
		try {
			response = sIn.readLine();
		}
		catch (SocketTimeoutException ste) {
			System.out.println("There is already another console connected (only one can be at a time),\n" +
					"  or the server failed to respond within " + cTimeout + " seconds.");
			if (cTimeout <= 2)
				System.out.println("Use '-t=<timeoutInSeconds>' to specify a longer timeout");
			return;
		}
		
		if ("WELCOME".equals(response)) {
			System.out.println(" ==> Authentication successful, have fun");
			System.out.println("");
			System.out.println("Global commands:");
			System.out.println(" - type 'exit' to exit the console");
			System.out.println(" - type '?' to display help information");
			System.out.println("");
			socket.setSoTimeout(0);
			Thread sInThread = new Thread() {
				public void run() {
					String line;
					boolean remoteExit = true;
					try {
						while ((line = sIn.readLine()) != null) {
							if ("GOODBYE".equals(line))
								remoteExit = false;
							else if ("COMMAND_DONE".equals(line))
								System.out.print("GgServer" + ((currentLetterCode.length() == 0) ? "" : ("." + currentLetterCode)) + ">");
							else System.out.println(line);
						}
						
						if (remoteExit) {
							System.out.println("Disconnected by server.");
							System.exit(0);
						}
						else socket.close();
					}
					catch (IOException ioe) {
						String msg = ioe.getMessage();
						if ((msg != null) && msg.startsWith("Connection reset")) {
							System.out.println("Disconnected by server.");
							System.exit(0);
						}
						else System.out.println("Error reading server response: " + ioe.getMessage());
					}
				}
			};
			sInThread.start();
		}
		else {
			System.out.println(" ==> " + response);
			return;
		}
		
		sOut.println("cc");
		
		while (true) try {
			String commandString = commandReader.readLine();
			if (commandString == null)
				return;
			
			commandString = commandString.trim();
			
			if ("".equals(currentLetterCode) && "exit".equals(commandString)) {
				sOut.println("GOODBYE");
				return;
			}
			
			if ("cc".equals(commandString))
				currentLetterCode = "";
			else if (commandString.startsWith("cc ")) {
				currentLetterCode = commandString.substring("cc".length()).trim();
				if (currentLetterCode.indexOf(' ') != -1)
					currentLetterCode = currentLetterCode.substring(0, currentLetterCode.indexOf(' ')).trim();
			}
			
			if (commandString.length() != 0)
				sOut.println(commandString.trim());
		}
		catch (Exception e) {
			System.out.println("Error reading or executing console command: " + e.getMessage());
		}
	}
}
