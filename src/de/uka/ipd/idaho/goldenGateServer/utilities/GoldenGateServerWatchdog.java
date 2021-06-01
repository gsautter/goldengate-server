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
package de.uka.ipd.idaho.goldenGateServer.utilities;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import de.uka.ipd.idaho.easyIO.EasyIO;
import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerNetworkMonitoringConstants;
import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection;
import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection.Connection;


/**
 * TODO document this
 * 
 * @author sautter
 */
public class GoldenGateServerWatchdog implements GoldenGateServerNetworkMonitoringConstants {
	private static File rootFolder;
	private static Settings ggWatchdogSettings;
	private static Settings ggServerSettings;
	
	private static String networkMonitoringToken;
	private static ServerConnection serverConnection;
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		
		//	get parameters
		String rootPath = "./";
		for (int a = 0; a < args.length; a++) {
			args[a] = args[a].trim();
			if (args[a].startsWith("-path="))
				rootPath = args[a].substring("-path=".length()).trim();
		}
		rootFolder = new File(rootPath);
		System.out.println(" - root folder is " + rootFolder.getAbsolutePath().replaceAll("\\\\", "/").replaceAll("\\/\\.\\/", "/"));
		
		//	read settings
		ggWatchdogSettings = Settings.loadSettings(new File(rootFolder, "config.watchdog.cnfg"));
		System.out.println(" - watchdog settings loaded");
		String ggServerSettingsPath = ggWatchdogSettings.getSetting("ggServerSettingsPath", "config.cnfg");
		if (ggServerSettingsPath.startsWith("/") || (ggServerSettingsPath.indexOf(":\\") != -1) || (ggServerSettingsPath.indexOf(":/") != -1))
			ggServerSettings = Settings.loadSettings(new File(ggServerSettingsPath));
		else ggServerSettings = Settings.loadSettings(new File(rootFolder, ggServerSettingsPath));
		System.out.println(" - server settings loaded");
		
		//	read monitoring network session token
		networkMonitoringToken = ggWatchdogSettings.getSetting("networkMonitoringToken");
		if (networkMonitoringToken == null)
			throw new RuntimeException("Network monitoring token missing");
		String serverHost = ggWatchdogSettings.getSetting("serverHost");
		if (serverHost == null)
			throw new RuntimeException("Server host name missing");
		int serverPort = Integer.parseInt(ggWatchdogSettings.getSetting("serverPort", "-1"));
		if (serverPort == -1)
			throw new RuntimeException("Server port number missing");
		serverConnection = ServerConnection.getServerConnection(serverHost, serverPort);
		
		//	ping GG Server
		testGgServer();
		
		//	ping configured URLs
		Settings urlTestSettings = ggWatchdogSettings.getSubset("urlTests");
		String[] urlTestNames = urlTestSettings.getSubsetPrefixes();
		for (int t = 0; t < urlTestNames.length; t++) {
			System.out.println("Performing URL test '" + urlTestNames[t] + "' ...");
			Settings urlTestSet = urlTestSettings.getSubset(urlTestNames[t]);
			String testUrl = urlTestSet.getSetting("url");
			if (testUrl == null) {
				System.out.println(" - test URL missing");
				continue;
			}
			String testFailServiceName = urlTestSet.getSetting("failServiceName");
			if (testFailServiceName == null) {
				System.out.println(" - test fail service name missing");
				continue;
			}
			testUrl(testUrl, testFailServiceName);
		}
//		
//		//	TODO get static test file via Tomcat
//		testUrl("http://localhost:8080/GgServer/aliveTest.txt", "tomcat9.service");
//		
//		//	TODO get static test file via Apache
//		testUrl("http://localhost/GgServer/aliveTest.txt", "apache2.service");
	}
	
	private static final DateFormat timeDateFormat = new SimpleDateFormat("HH:mm:ss", Locale.US) {
		{ this.setTimeZone(TimeZone.getTimeZone("UTC")); }
	};
	
	private static void testGgServer() throws IOException {
		
		//	ping server
		try {
			System.out.println("Pinging GoldenGATE Server ...");
			executeServerCommand(NETWORK_MONITOR_PING);
			System.out.println(" ==> success");
			
			//	check if first run of hour
			String time = timeDateFormat.format(new Date(System.currentTimeMillis()));
			if (!time.matches("[0-9]{1,2}\\:0[0-4]:[0-9]{2}"))
				return;
		}
		catch (IOException ioe) {
			System.out.println(" ==> " + ioe.getMessage());
			restartGgServer();
			return;
		}
		
		//	print statistics of server state
		String[] result;
		
		//	get list of threads groups
		result = executeServerCommand(NETWORK_MONITOR_LIST_THREAD_GROUPS);
		for (int r = 0; r < result.length; r++)
			System.out.println(result[r]);
		
		//	get list of threads
		result = executeServerCommand(NETWORK_MONITOR_LIST_THREADS);
		for (int r = 0; r < result.length; r++)
			System.out.println(result[r]);
		
		//	get size of network service thread pool
		result = executeServerCommand(NETWORK_MONITOR_POOL_SIZE);
		for (int r = 0; r < result.length; r++)
			System.out.println(result[r]);
		
		//	get list of running network actions
		result = executeServerCommand(NETWORK_MONITOR_LIST_ACTIONS);
		for (int r = 0; r < result.length; r++)
			System.out.println(result[r]);
		
		//	get list of running background queues
		result = executeServerCommand(NETWORK_MONITOR_LIST_QUEUES);
		for (int r = 0; r < result.length; r++)
			System.out.println(result[r]);
	}
	
	private static String[] executeServerCommand(String command) throws IOException {
		Connection con = null;
		try {
			con = serverConnection.getConnection();
			BufferedWriter bw = con.getWriter();
			bw.write(command);
			bw.newLine();
			bw.write(networkMonitoringToken);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!command.equals(error))
				throw new IOException(error);
			
			ArrayList resultLines = new ArrayList();
			for (String rl; (rl = br.readLine()) != null;)
				resultLines.add(rl);
			return ((String[]) resultLines.toArray(new String[resultLines.size()]));
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	private static void restartGgServer() throws IOException {
		System.out.println("Failed to ping GoldenGATE Server, restarting ...");
		String ggServerServiceName = ggWatchdogSettings.getSetting("ggServerServiceName");
		System.out.println(" - service name is " + ggServerServiceName);
		executeSystemCommand("sudo /bin/systemctl restart " + ggServerServiceName);
		
		//	wait for socket to open
		for (int wait = 1; wait <= 60; wait++) try {
			try {
				Thread.sleep(1000 * 1);
			} catch (InterruptedException ie) {}
			executeServerCommand(NETWORK_MONITOR_PING);
			System.out.println(" - ping successful on attempt " + wait);
			break;
		}
		catch (IOException ioe) {
			System.out.println(" - ping failed on attempt " + wait + ": " + ioe.getMessage());
		}
		
		//	get list of components
		String[] componentList = executeServerCommand(NETWORK_MONITOR_LIST_COMPONENTS);
		for (int c = 0; c < componentList.length; c++)
			System.out.println(componentList[c]);
		
		//	get list of errors
		String[] componentErrors = executeServerCommand(NETWORK_MONITOR_LIST_ERRORS);
		for (int e = 0; e < componentErrors.length; e++)
			System.out.println(componentErrors[e]);
		
		//	get email output data
		IoProvider io = EasyIO.getIoProvider(ggServerSettings.getSubset("EasyIO"));
		if (!io.isMessagingAvailable()) {
			System.out.println("Unable to send notification mail");
			return;
		}
		String sendNotificationToStr = ggServerSettings.getSetting("ENV.SendAdminNotificationsTo");
		if (sendNotificationToStr == null) {
			System.out.println("No address to send notification mail to");
			return;
		}
		String[] sendNotificationTo = sendNotificationToStr.split("\\s+");
		
		//	send notification mail
		String subject = ("GoldenGATE Server Restarted after Failed Ping");
		StringBuffer message = new StringBuffer();
		for (int c = 0; c < componentList.length; c++) {
			message.append(componentList[c]);
			message.append("\r\n");
		}
		message.append("\r\n");
		for (int e = 0; e < componentErrors.length; e++) {
			message.append(componentErrors[e]);
			message.append("\r\n");
		}
		try {
			io.smtpSend(subject, message.toString(), sendNotificationTo);
			System.out.println("Notification mail sent to " + Arrays.asList(sendNotificationTo));
		}
		catch (Exception e) {
			System.out.println("Failed to send notification message: " + e.getMessage());
			e.printStackTrace(System.out);
		}
	}
	
	private static void testUrl(String testUrl, String testFailServiceName) throws IOException {
		
		//	ping URL
		try {
			System.out.println("Testing URL '" + testUrl + "' ...");
			HttpURLConnection testUrlCon = ((HttpURLConnection) new URL(testUrl).openConnection());
			testUrlCon.setRequestMethod("GET");
			testUrlCon.setConnectTimeout(5000);
			testUrlCon.setReadTimeout(5000);
			InputStream testUrlIn = testUrlCon.getInputStream();
			testUrlIn.read();
			testUrlIn.close();
			System.out.println(" ==> success");
			return;
		}
		catch (FileNotFoundException fnfe) {
			System.out.println(" ==> invalid test URL " + testUrl + ": " + fnfe.getMessage());
			return;
		}
		catch (SocketTimeoutException ste) {
			System.out.println(" ==> connection timed out after 5 seconds: " + ste.getMessage());
		}
		catch (IOException e) {
			System.out.println(" ==> failed to connect to " + testUrl + ": " + e.getMessage());
		}
		
		//	restart backing service
		System.out.println(" - restarting " + testFailServiceName + " ...");
		executeSystemCommand("sudo /bin/systemctl restart " + testFailServiceName);
		
		//	wait for URL to come back up again
		for (int wait = 1; wait <= 60; wait++) try {
			try {
				Thread.sleep(1000 * 1);
			} catch (InterruptedException ie) {}
			InputStream urlIn = (new URL(testUrl)).openStream();
			urlIn.read();
			urlIn.close();
			System.out.println(" - ping successful on attempt " + wait);
			break;
		}
		catch (IOException ioe) {
			System.out.println(" - ping failed on attempt " + wait + ": " + ioe.getMessage());
		}
	}
	
	private static void executeSystemCommand(String command) throws IOException {
		Process systemCommandProcess = Runtime.getRuntime().exec(command);
		(new InputStreamRelayThread(systemCommandProcess.getInputStream(), System.out)).start();
		(new InputStreamRelayThread(systemCommandProcess.getErrorStream(), System.err)).start();
		while (true) try {
			systemCommandProcess.waitFor();
			break; // if we get here, waitFor() returned normally
		} catch (InterruptedException ie) {}
	}
	
	private static class InputStreamRelayThread extends Thread {
		private InputStream in;
		private PrintStream out;
		InputStreamRelayThread(InputStream in, PrintStream out) {
			this.in = in;
			this.out = out;
		}
		public void run() {
			try {
				for (int r; (r = this.in.read()) != -1;)
					this.out.write(r);
			}
			catch (IOException e) {
				e.printStackTrace(this.out);
			}
		}
	}
}
