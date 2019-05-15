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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import de.uka.ipd.idaho.easyIO.EasyIO;
import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent.ComponentAction;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent.ComponentActionConsole;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent.ComponentActionNetwork;
import de.uka.ipd.idaho.goldenGateServer.util.BufferedLineInputStream;
import de.uka.ipd.idaho.goldenGateServer.util.BufferedLineOutputStream;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * A standalone server for running GoldenGateServerComponents, exposing them
 * over the network via a ServerSocket and via console (command line)
 * 
 * @author sautter
 */
public class GoldenGateServer implements GoldenGateServerConstants {
	
	private static final String CONFIG_FILE_NAME = "config.cnfg";
	private static final String PORT_SETTING_NAME = "port";
	
	private static final int defaultNetworkInterfaceTimeout = (10 * 1000); // 10 seconds by default
	private static final int defaultNetworkConsoleTimeout = (10 * 60 * 1000); // 10 minutes by default
	
	private static GoldenGateServerComponent[] serverComponents;
	private static String[] serverComponentLoadErrors;
	private static ComponentServerConsole console;
	private static final Object consoleOutLock = new Object();
	
	private static int networkInterfaceTimeout = defaultNetworkInterfaceTimeout;
	private static int port = -1; 
	private static ServerThread serverThread = null;
	
	private static File rootFolder;
	
	private static Settings settings;
	private static Settings ioProviderSettings = new Settings();
	private static Settings environmentSettings = new Settings();
	private static boolean environmentSettingsModified = false;
	
	private static int logLevelNetwork = GoldenGateServerActivityLogger.LOG_LEVEL_WARNING;
	private static int logLevelConsole = GoldenGateServerActivityLogger.LOG_LEVEL_INFO;
	private static int logLevelBackground = GoldenGateServerActivityLogger.LOG_LEVEL_INFO;
	
	/**	
	 * @return an IoProvider for accessing the SRS's database
	 */
	public static IoProvider getIoProvider() {
		return EasyIO.getIoProvider(ioProviderSettings);
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException {
		System.out.println("GoldenGATE Server starting up:");
		
		StackTraceElement[] ste = Thread.currentThread().getStackTrace();
		System.out.println(" - invokation JVM stack trace:");
		for (int e = 0; e < ste.length; e++)
			System.out.println("   - " + ste[e].getClassName() + ", in " + ste[e].getMethodName() + "()");
		
		//	get parameters
		String rootPath = "./";
		boolean isDaemon = false;
		for (int a = 0; a < args.length; a++) {
			args[a] = args[a].trim();
			if (args[a].startsWith("-path="))
				rootPath = args[a].substring("-path=".length()).trim();
			else if (args[a].equals("-d"))
				isDaemon = true;
		}
		rootFolder = new File(rootPath);
		System.out.println(" - root folder is " + rootFolder.getAbsolutePath().replaceAll("\\\\", "/").replaceAll("\\/\\.\\/", "/"));
		
		//	read settings
		settings = Settings.loadSettings(new File(rootFolder, CONFIG_FILE_NAME));
		System.out.println(" - settings loaded");
		
		//	set WWW proxy
		String proxy = settings.getSetting("wwwProxy");
		if (proxy != null) {
			System.getProperties().put("proxySet", "true");
			System.getProperties().put("proxyHost", proxy);
			String proxyPort = settings.getSetting("wwwProxyPort");
			if (proxyPort != null)
				System.getProperties().put("proxyPort", proxyPort);
			System.out.println(" - www proxy configured");
		}
		
		//	prepare forking output
		final PrintStream fSystemOut;
		final PrintStream fError;
		
		//	open console (command line) interface
		if (isDaemon) {
			System.out.println(" - starting as daemon:");
			
			int ncPort = 15808;
			try {
				ncPort = Integer.parseInt(settings.getSetting("networkConsolePort", ("" + ncPort)));
				System.out.println("   - network console port is " + ncPort);
			}
			catch (NumberFormatException nfe) {
				System.out.println("   - could not read network console port, using default " + ncPort);
			}
			
			console = new SocketConsole(ncPort, settings.getSetting("networkConsolePassword", "GG"), Integer.parseInt(settings.getSetting("networkConsoleTimeout", ("" + defaultNetworkConsoleTimeout))));
			System.out.println("   - network console created");
			
			//	fork System.out to network console
			final OutputStream systemOut = System.out;
			fSystemOut = new PrintStream(new OutputStream() {
				public void write(int b) throws IOException {
					synchronized (consoleOutLock) {
						if (console.out != null)
							console.out.write(b);
					}
					systemOut.write(b);
				}
			}, true);
			System.setOut(fSystemOut);
			System.out.println("   - System.out forked");
			
			//	fork System.err to network console
			final OutputStream error = System.err;
			fError = new PrintStream(new OutputStream() {
				public void write(int b) throws IOException {
					synchronized (consoleOutLock) {
						if (console.out != null)
							console.out.write(b);
					}
					error.write(b);
				}
			}, true);
			System.setErr(fError);
			System.out.println("   - System.err forked");
		}
		else {
			System.out.println(" - starting in command shell:");
			
			console = new SystemInConsole(System.out);
			System.out.println("   - console created");
			
			//	get log timestamp
			long logTime = System.currentTimeMillis();
			
			//	fork System.out to file
			File systemOutFile = new File(rootFolder, ("GgServer.SystemOut." + logTime + ".log"));
			systemOutFile.createNewFile();
			final OutputStream systemOut = new FileOutputStream(systemOutFile);
			fSystemOut = new PrintStream(new OutputStream() {
				public void write(int b) throws IOException {
					synchronized (consoleOutLock) {
						if (console.out != null)
							console.out.write(b);
					}
					systemOut.write(b);
				}
				public void close() throws IOException {
					systemOut.flush();
					systemOut.close();
				}
			}, true);
			System.setOut(fSystemOut);
			System.out.println("   - System.out forked");
			
			//	fork System.err to file
			File errorFile = new File(rootFolder, ("GgServer.Error." + logTime + ".log"));
			errorFile.createNewFile();
			final OutputStream error = new FileOutputStream(errorFile);
			fError = new PrintStream(new OutputStream() {
				public void write(int b) throws IOException {
					synchronized (consoleOutLock) {
						if (console.out != null)
							console.out.write(b);
					}
					error.write(b);
				}
				public void close() throws IOException {
					error.flush();
					error.close();
				}
			}, true);
			System.setErr(fError);
			System.out.println("   - System.err forked");
		}
		
		//	start server
		System.out.println(" - starting componet server");
		start();
		System.out.println(" - componet server started");
		
		//	start console after startup complete
		console.start();
		System.out.println(" - console started");
		
		//	ensure proper shutdown on shutdown, and make sure log files are closed
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("GoldenGATE Server shutting down:");
				
				GoldenGateServer.stop();
				System.out.println(" - component server stopped");
				
				fSystemOut.flush();
				fSystemOut.close();
				System.out.println(" - System.out closed");
				
				fError.flush();
				fError.close();
				System.out.println(" - System.err closed");
			}
		});
		System.out.println(" - shutdown hook registered");
		
		System.out.println("GoldenGATE Server startup complete");
	}
	
	private static synchronized void start() {
		
		//	read network interface port and timeout, as well as maximum thread pool size
		try {
			port = Integer.parseInt(settings.getSetting(PORT_SETTING_NAME, ("" + port)));
			networkInterfaceTimeout = Integer.parseInt(settings.getSetting("networkInterfaceTimeout", ("" + networkInterfaceTimeout)));
			maxServiceThreadQueueSize = Integer.parseInt(settings.getSetting("maxIdleServiceThreads", ("" + maxServiceThreadQueueSize)));
		} catch (NumberFormatException nfe) {}
		
		//	get database access data
		ioProviderSettings = settings.getSubset("EasyIO");
		
		//	get environment settings
		environmentSettings = settings.getSubset("ENV");
		
		//	load server components
		GoldenGateServerComponent[] loadedServerComponents = GoldenGateServerComponentLoader.loadServerComponents(new File(rootFolder, COMPONENT_FOLDER_NAME));
		System.out.println("   - components loaded");
		
		//	initialize and register components
		ComponentHost componentHost = new ComponentHost();
		ArrayList serverComponentList = new ArrayList();
		ArrayList serverComponentLoadErrorList = new ArrayList();
		for (int c = 0; c < loadedServerComponents.length; c++)
			try {
				System.out.println("   - initializing " + loadedServerComponents[c].getLetterCode());
				loadedServerComponents[c].setHost(componentHost);
				loadedServerComponents[c].init();
				System.out.println("   - " + loadedServerComponents[c].getLetterCode() + " initialized");
				serverComponentList.add(loadedServerComponents[c]);
				GoldenGateServerComponentRegistry.registerServerComponent(loadedServerComponents[c]);
			}
			catch (Throwable t) {
				System.out.println(t.getClass().getName() + " (" + t.getMessage() + ") while initializing " + ((loadedServerComponents[c] == null) ? "server component" : loadedServerComponents[c].getClass().getName()));
				t.printStackTrace(System.out);
				if (loadedServerComponents[c] != null)
					serverComponentLoadErrorList.add(loadedServerComponents[c].getLetterCode() + ": '" + t.getMessage() + "' while initializing");
			}
		
		//	get operational ones
		loadedServerComponents = ((GoldenGateServerComponent[]) serverComponentList.toArray(new GoldenGateServerComponent[serverComponentList.size()]));
		serverComponentList.clear();
		
		//	link components
		for (int c = 0; c < loadedServerComponents.length; c++)
			try {
				System.out.println("   - linking " + loadedServerComponents[c].getLetterCode());
				loadedServerComponents[c].link();
				System.out.println("   - " + loadedServerComponents[c].getLetterCode() + " linked");
				serverComponentList.add(loadedServerComponents[c]);
			}
			catch (Throwable t) {
				System.out.println(t.getClass().getName() + " (" + t.getMessage() + ") while linking " + ((loadedServerComponents[c] == null) ? "server component" : loadedServerComponents[c].getClass().getName()));
				t.printStackTrace(System.out);
				if (loadedServerComponents[c] != null)
					serverComponentLoadErrorList.add(loadedServerComponents[c].getLetterCode() + ": '" + t.getMessage() + "' while linking");
				GoldenGateServerComponentRegistry.unregisterServerComponent(loadedServerComponents[c]);
			}
		
		//	get operational ones
		loadedServerComponents = ((GoldenGateServerComponent[]) serverComponentList.toArray(new GoldenGateServerComponent[serverComponentList.size()]));
		serverComponentList.clear();
		
		//	link initialize components
		for (int c = 0; c < loadedServerComponents.length; c++)
			try {
				System.out.println("   - linked initializing " + loadedServerComponents[c].getLetterCode());
				loadedServerComponents[c].linkInit();
				System.out.println("   - " + loadedServerComponents[c].getLetterCode() + " linked initialized");
				serverComponentList.add(loadedServerComponents[c]);
			}
			catch (Throwable t) {
				System.out.println(t.getClass().getName() + " (" + t.getMessage() + ") while linked initializing " + ((loadedServerComponents[c] == null) ? "server component" : loadedServerComponents[c].getClass().getName()));
				t.printStackTrace(System.out);
				if (loadedServerComponents[c] != null)
					serverComponentLoadErrorList.add(loadedServerComponents[c].getLetterCode() + ": '" + t.getMessage() + "' while linked initializing");
				GoldenGateServerComponentRegistry.unregisterServerComponent(loadedServerComponents[c]);
			}
			
		//	get operational ones
		serverComponents = ((GoldenGateServerComponent[]) serverComponentList.toArray(new GoldenGateServerComponent[serverComponentList.size()]));
		
		//	store errors
		serverComponentLoadErrors = ((String[]) serverComponentLoadErrorList.toArray(new String[serverComponentLoadErrorList.size()]));
		
		//	obtain local console actions
		ComponentActionConsole[] localActions = getLocalActions();
		
		HashMap localActionSet = new HashMap();
		
		for (int a = 0; a < localActions.length; a++)
			localActionSet.put(localActions[a].getActionCommand(), localActions[a]);
		
		if (localActionSet.size() != 0)
			componentActionSetsByLetterCode.put("", localActionSet);
		
		//	obtain component's console actions
		for (int c = 0; c < serverComponents.length; c++) {
			String componentLetterCode = serverComponents[c].getLetterCode();
			System.out.println("   - getting actions from " + componentLetterCode);
			ComponentAction[] componentActions = serverComponents[c].getActions();
			
			HashMap componentActionSet = new HashMap();
			
			if (componentActions != null)
				for (int a = 0; a < componentActions.length; a++) {
					if (componentActions[a] instanceof ComponentActionConsole)
						componentActionSet.put(componentActions[a].getActionCommand(), componentActions[a]);
					if (componentActions[a] instanceof ComponentActionNetwork) {
						if (serverComponentActions.containsKey(componentActions[a].getActionCommand()))
							throw new RuntimeException("Duplicate network action '" + componentActions[a].getActionCommand() + "'");
						serverComponentActions.put(componentActions[a].getActionCommand(), componentActions[a]);
					}
				}
			
			if (componentActionSet.size() != 0)
				componentActionSetsByLetterCode.put(componentLetterCode, componentActionSet);
			System.out.println("   - actions from " + componentLetterCode + " integrated");
		}
		System.out.println("   - components integrated in network and console interfaces");
		
		//	start server and wait for it
		ServerThread st = new ServerThread();
		synchronized (st) {
			st.start();
			try {
				st.wait();
			} catch (InterruptedException ie) {}
		}
		
		//	check if startup successful
		if (st.isRunning()) {
			serverThread = st;
			System.out.println("   - network interface activated");
		}
		
		//	shutdown otherwise
		else System.exit(0);
	}
	
	private static class ComponentHost implements GoldenGateServerComponentHost {
		public IoProvider getIoProvider() {
			return GoldenGateServer.getIoProvider();
		}
		
		public String getServerProperty(String name) {
			return ((name == null) ? null : environmentSettings.getSetting(name));
		}
		
		public boolean isRequestProxied() {
			synchronized (proxiedServiceThreadIDs) {
				return proxiedServiceThreadIDs.contains(Long.valueOf(Thread.currentThread().getId()));
			}
		}
		public boolean isClientRequest() {
			return (Thread.currentThread() instanceof ServiceThread);
		}

		public GoldenGateServerComponent getServerComponent(String className) {
			return GoldenGateServerComponentRegistry.getServerComponent(className);
		}
		public GoldenGateServerComponent[] getServerComponents(String className) {
			return GoldenGateServerComponentRegistry.getServerComponents(className);
		}
		public GoldenGateServerComponent[] getServerComponents() {
			return GoldenGateServerComponentRegistry.getServerComponents();
		}
		
		public void logError(String message) {
			Thread ct = Thread.currentThread();
			if (ct instanceof GoldenGateServerActivityLogger)
				((GoldenGateServerActivityLogger) ct).logError(message);
			else log(message, LOG_LEVEL_ERROR, logLevelBackground);
		}
		public void logError(Throwable error) {
			Thread ct = Thread.currentThread();
			if (ct instanceof GoldenGateServerActivityLogger)
				((GoldenGateServerActivityLogger) ct).logError(error);
			else log(error, LOG_LEVEL_ERROR, logLevelBackground);
		}
		public void logWarning(String message) {
			Thread ct = Thread.currentThread();
			if (ct instanceof GoldenGateServerActivityLogger)
				((GoldenGateServerActivityLogger) ct).logWarning(message);
			else log(message, LOG_LEVEL_WARNING, logLevelBackground);
		}
		public void logInfo(String message) {
			Thread ct = Thread.currentThread();
			if (ct instanceof GoldenGateServerActivityLogger)
				((GoldenGateServerActivityLogger) ct).logInfo(message);
			else log(message, LOG_LEVEL_INFO, logLevelBackground);
		}
		public void logDebug(String message) {
			Thread ct = Thread.currentThread();
			if (ct instanceof GoldenGateServerActivityLogger)
				((GoldenGateServerActivityLogger) ct).logDebug(message);
			else log(message, LOG_LEVEL_DEBUG, logLevelBackground);
		}
		public void logActivity(String message) {
			Thread ct = Thread.currentThread();
			if (ct instanceof GoldenGateServerActivityLogger)
				((GoldenGateServerActivityLogger) ct).logActivity(message);
		}
		public void logAlways(String message) {
			Thread ct = Thread.currentThread();
			if (ct instanceof GoldenGateServerActivityLogger)
				((GoldenGateServerActivityLogger) ct).logAlways(message);
			else log(message, -1, logLevelBackground);
		}
		public void logResult(String message) {
			Thread ct = Thread.currentThread();
			if (ct instanceof GoldenGateServerActivityLogger)
				((GoldenGateServerActivityLogger) ct).logResult(message);
			else log(message, LOG_LEVEL_INFO, logLevelBackground);
		}
	}
	
	private static void log(String message, int messageLogLevel, int contextLogLevel) {
		if (messageLogLevel <= contextLogLevel)
			System.out.println(message);
	}
	
	private static void log(Throwable error, int messageLogLevel, int contextLogLevel) {
		if (messageLogLevel <= contextLogLevel)
			error.printStackTrace(System.out);
	}
	
	private static synchronized void stop() {
		if (!isRunning())
			return;
		System.out.println("GoldenGATE Server shutting down:");
		
		//	close console
		console.close();
		System.out.println("- console closed");
		
		//	shut down server thread
		serverThread.shutdown();
		serverThread = null;
		System.out.println("- server thread shut down");
		
		//	shut down service threads
		while (serviceThreadList.size() != 0) {
			ServiceThread st = ((ServiceThread) serviceThreadList.removeFirst());
			
			//	check if thread is servicing
			if (st.isInService()) {
				
				//	re-enqueue thread so it can finish its current request
				serviceThreadList.addLast(st);
				
				try { // wait for a little to avoid overload when only servicing threads remain
					Thread.sleep(25);
				} catch (InterruptedException ie) {}
			}
			
			//	shut it down if not
			else st.shutdown();
		}
		System.out.println("- service threads terminated");
		
		//	shut down components
		System.out.println("- shutting down components ...");
		for (int c = 0; c < serverComponents.length; c++) try {
			String clc = serverComponents[c].getLetterCode();
			
			//	clear action register
			componentActionSetsByLetterCode.remove(clc);
			
			//	shut down component
			serverComponents[c].exit();
			System.out.println("  - " + clc + " shut down");
		}
		catch (Exception e) {
			System.out.println("  - error exitting " + serverComponents[c].getClass().getName() + ":" + e.getMessage());
		}
		System.out.println("- component shut down complete");
		
		//	store settings if modified
		if (environmentSettingsModified) try {
			Settings.storeSettingsAsText(new File(rootFolder, CONFIG_FILE_NAME), settings);
			System.out.println("- settings stored");
		} catch (IOException ioe) {}
	}
	
	private static boolean isRunning() {
		return (serverThread != null);
	}
	
	private static Map serverComponentActions = Collections.synchronizedMap(new HashMap());
	
	private static class ServerThread extends Thread {
		private ServerSocket serverSocket;
		
		ServerThread() {
			super("GgServerMasterThread");
		}
		
		public void run() {
			try {
				//	create and open server socket
				ServerSocket ss = new ServerSocket();
				ss.setReuseAddress(true);
				ss.bind(new InetSocketAddress(port));
				this.serverSocket = ss;
				
				//	notify startup complete
				synchronized (this) {
					this.notify();
				}
				
				//	run until shutdown() is called
				while (this.serverSocket != null) try {
					
					//	wait for incoming connections
					Socket socket = serverSocket.accept();
					socket.setSoTimeout(networkInterfaceTimeout);
					
					//	create streams
					final BufferedLineInputStream requestIn = new BufferedLineInputStream(socket.getInputStream(), ENCODING);
					final BufferedLineOutputStream responseOut = new BufferedLineOutputStream(socket.getOutputStream(), ENCODING);
					
					System.out.println(LOG_TIMESTAMP_FORMATTER.format(new Date()) + ": Handling request from " + socket.getRemoteSocketAddress());
					
					ServiceThread st = getServiceThread();
					
					//	stopping or stopped, report error
					if (st == null) {
						responseOut.write("Cannot process request, server is stopped");
						responseOut.newLine();
						
						responseOut.flush();
						socket.close();
					}
					
					//	process request
					else st.service(new ServiceRequest(socket, requestIn, responseOut));
				}
				
				//	catch Exceptions caused by singular incoming connection requests
				catch (Throwable t) {
					if ("socket closed".equals(t.getMessage()))
						System.out.println("Server socket closed.");
					else {
						System.out.println("Error handling request - " + t.getMessage());
						t.printStackTrace(System.out);
					}
				}
			}
			
			//	shut down if the server socket couldn't be opened
			catch (Throwable t) {
				System.out.println("Error creating server socket: " + t.getMessage());
				t.printStackTrace(System.out);
				
				//	notify if startup fails
				synchronized (this) {
					this.notify();
				}
			}
		}
		
		boolean isRunning() {
			return (this.serverSocket != null);
		}
		
		void shutdown() {
			ServerSocket ss = this.serverSocket;
			this.serverSocket = null;
			
			if (ss != null) try {
				ss.close();
			}
			catch (IOException ioe) {
				System.out.println("Error closing main server socket - " + ioe.getMessage());
				ioe.printStackTrace(System.out);
			}
		}
	}
	
	private static Set proxiedServiceThreadIDs = Collections.synchronizedSet(new HashSet());
	
	private static abstract class LoggingThread extends Thread implements GoldenGateServerActivityLogger {
		LoggingThread(String name) {
			super(name);
		}
		public void logError(String message) {
			this.log(message, LOG_LEVEL_ERROR);
		}
		public void logWarning(String message) {
			this.log(message, LOG_LEVEL_WARNING);
		}
		public void logInfo(String message) {
			this.log(message, LOG_LEVEL_INFO);
		}
		public void logDebug(String message) {
			this.log(message, LOG_LEVEL_DEBUG);
		}
		public void logAlways(String message) {
			this.log(message, -1);
		}
		abstract void log(String message, int messageLogLevel);
	}
	
	private static class ServiceThread extends LoggingThread {
		
		private final Object lock = new Object();
		private boolean keepRunning = true;
		
		private ServiceRequest request = null;
		
		ServiceThread() {
			super("GgServerServiceThread-" + (serviceThreadList.size() + 1));
			serviceThreadList.add(this);
		}
		
		public void run() {
			
			//	run until shutdown() is called
			while (this.keepRunning) {
				
				//	wait until notified
				synchronized(lock) {
					if (this.request == null) try { // test if request already set, might happen on creation
						this.lock.wait();
					} catch (InterruptedException ie) {}
				}
				
				//	execute action if given
				if (this.request != null) try {
					this.request.execute(this);
				}
				
				//	catch whatever might go wrong
				catch (Throwable t) {
					System.out.println("Error handling request - " + t.getClass().getName() + " (" + t.getMessage() + ")");
					t.printStackTrace(System.out);
				}
				
				//	clean up
				finally {
					this.request = null;
				}
				
				//	re-enqueue if not shut down ...
				if (this.keepRunning)
					synchronized (serviceThreadQueue) {
						
						//	but only if less than 128 idle threads in list
						if (serviceThreadQueue.size() < maxServiceThreadQueueSize)
							serviceThreadQueue.addLast(this);
						
						//	shut down otherwise
						else {
							serviceThreadList.remove(this);
							this.keepRunning = false;
						}
					}
			}
		}
		
		void service(ServiceRequest request) {
			synchronized(lock) {
				this.request = request;
				this.lock.notifyAll();
			}
		}
		
		boolean isInService() {
			return (this.request != null);
		}
		
		void shutdown() {
			this.keepRunning = false;
			this.request = null;
			synchronized(lock) {
				this.lock.notifyAll();
			}
		}
		
		public void logError(Throwable error) {
			GoldenGateServer.log(error, LOG_LEVEL_ERROR, logLevelNetwork);
		}
		public void logActivity(String message) {
			if (this.request == null)
				this.log(message, LOG_LEVEL_WARNING); // something is hinky, so generally go for warning
			else this.request.logActivity(message); // run this past request (we don't want to count any waiting time)
		}
		public void logResult(String message) {
			this.logInfo(message); // treat results as information level messages in network activity
		}
		void log(String message, int messageLogLevel) {
			GoldenGateServer.log(message, messageLogLevel, logLevelNetwork);
		}
	}
	
	private static class ServiceRequest {
		
		private Socket socket;
		private BufferedLineInputStream requestIn;
		private BufferedLineOutputStream responseOut;
		
		private long activityLogStart = -1;
		private long activityLogEnd = -1;
		private ArrayList activityLogMessages = null;
		
		ServiceRequest(Socket socket, BufferedLineInputStream requestIn, BufferedLineOutputStream responseOut) {
			this.socket = socket;
			this.requestIn = requestIn;
			this.responseOut = responseOut;
		}
		
		void execute(ServiceThread thread) throws Exception {
			Long threadId = new Long(thread.getId());
			ServiceAction threadAction = null;
			try {
				
				//	read command
				String command = this.requestIn.readLine();
				thread.logAlways("Command is " + command);
				
				//	catch 'PROXIED' property
				if ("PROXIED".equals(command)) {
					proxiedServiceThreadIDs.add(threadId);
					command = this.requestIn.readLine();
					thread.logAlways("Command is " + command);
				}
				
				//	TODO split headers off action command, starting at first '<'
				
				//	get action
				ComponentActionNetwork action = ((ComponentActionNetwork) serverComponentActions.get(command));
				
				//	invalid action, send error
				if (action == null) {
					this.responseOut.write("Invalid action '" + command + "'");
					this.responseOut.newLine();
				}
				
				//	action found
				else {
					
					//	mark action as running
					threadAction = new ServiceAction(command, thread.getName());
					
					//	TODO once we introduce headers, maybe read them here, and store in thread locale
					
					//	report action as running (this will queue us up and wait if too many other requests are on same action)
					startServiceAction(threadAction);
					
					//	set up activity logging
					this.activityLogStart = System.currentTimeMillis();
					long activityLogTimeout = action.getActivityLogTimeout();
					this.activityLogEnd = ((activityLogTimeout < 0) ? Long.MAX_VALUE : (this.activityLogStart + activityLogTimeout));
					this.activityLogMessages = new ArrayList();
					
					//	perform action
					action.performActionNetwork(this.requestIn, this.responseOut);
				}
				
				//	send response
				this.responseOut.flush();
				this.responseOut.close();
			}
			finally {
				proxiedServiceThreadIDs.remove(threadId);
				finishServiceAction(threadAction);
				this.socket.close();
			}
		}
		
		void logActivity(String message) {
			
			//	log level debug, output message right away
			if (GoldenGateServerActivityLogger.LOG_LEVEL_DEBUG <= logLevelNetwork) {
				log(message, GoldenGateServerActivityLogger.LOG_LEVEL_DEBUG, logLevelNetwork);
				return;
			}
			
			//	create time prefix
			long time = System.currentTimeMillis();
			int runTime = ((int) ((time - this.activityLogStart) & 0x7FFFFFFF));
			
			//	before debug log timeout, store message if we can
			if (time < this.activityLogEnd) {
				if (this.activityLogMessages == null)
					log((runTime + "ms: " + message), GoldenGateServerActivityLogger.LOG_LEVEL_DEBUG, logLevelNetwork);
				else this.activityLogMessages.add(runTime + "ms: " + message);
				return;
			}
			
			//	write through collected messages as warnings after timeout expired (if not done before)
			if (this.activityLogMessages != null) {
				for (int m = 0; m < this.activityLogMessages.size(); m++)
					log(((String) this.activityLogMessages.get(m)), GoldenGateServerActivityLogger.LOG_LEVEL_WARNING, logLevelNetwork);
				this.activityLogMessages = null;
			}
			
			//	this message now has warning level
			log((runTime + "ms: " + message), GoldenGateServerActivityLogger.LOG_LEVEL_WARNING, logLevelNetwork);
		}
	}
	
	private static class ServiceAction implements Comparable {
		final long startTime;
		final String command;
		final String threadName;
		private String status = "running";
		private long waited = -1;
		private long started;
		ServiceAction(String command, String threadName) {
			this.startTime = System.currentTimeMillis();
			this.command = command;
			this.threadName = threadName;
			this.started = this.startTime;
		}
		synchronized void suspend() throws InterruptedException {
			this.status = "waiting";
			this.wait();
		}
		synchronized void resume() {
			this.status = "running";
			long ctm = System.currentTimeMillis();
			this.waited = (ctm - this.started);
			this.started = ctm;
			this.notify();
		}
		public int compareTo(Object obj) {
			ServiceAction sa = ((ServiceAction) obj);
			return ((this.startTime == sa.startTime) ? this.threadName.compareTo(sa.threadName) : ((int) (this.startTime - sa.startTime)));
		}
		public String toString() {
			return (" - " + this.command + " (" + this.status + " since " + (System.currentTimeMillis() - this.started) + "ms" + ((this.waited == -1) ? "" : (" after waiting for " + this.waited + "ms")) + ")");
		}
	}
	
	private static Set activeServiceActions = Collections.synchronizedSet(new TreeSet());
	
	private static Map serviceActionCoordinators = Collections.synchronizedMap(new HashMap() {
		public Object get(Object key) {
			Object value = super.get(key);
			if (value == null) {
				value = new ServiceActionCoordinator();
				this.put(key, value);
			}
			return value;
		}
	});
	
	private static void startServiceAction(ServiceAction sa) throws InterruptedException {
		if (sa != null)
			((ServiceActionCoordinator) serviceActionCoordinators.get(sa.command)).startServiceAction(sa);
	}
	
	private static void finishServiceAction(ServiceAction sa) {
		if (sa != null)
			((ServiceActionCoordinator) serviceActionCoordinators.get(sa.command)).finishServiceAction(sa);
	}
	
	private static class ServiceActionCoordinator {
		private TreeSet runningActions = new TreeSet();
		private LinkedList queuedActions = new LinkedList();
		void startServiceAction(ServiceAction sa) throws InterruptedException {
			sa = this.doStartServiceAction(sa);
			if (sa != null)
				sa.suspend();
			//	we need to release the monitor on the coordinator before suspending on the one of the action
		}
		private synchronized ServiceAction doStartServiceAction(ServiceAction sa) throws InterruptedException {
			activeServiceActions.add(sa);
			if (this.runningActions.size() < 4) { // TODO make this threshold configurable
				this.runningActions.add(sa);
				return null;
			}
			else {
				this.queuedActions.addLast(sa);
				return sa;
			}
		}
		void finishServiceAction(ServiceAction sa) {
			sa = this.doFinishServiceAction(sa);
			if (sa != null)
				sa.resume();
			//	we need to release the monitor on the coordinator before acquiring the one on the action
		}
		private synchronized ServiceAction doFinishServiceAction(ServiceAction sa) {
			activeServiceActions.remove(sa);
			this.runningActions.remove(sa);
			if (this.queuedActions.isEmpty())
				return null;
			sa = ((ServiceAction) this.queuedActions.removeFirst());
			this.runningActions.add(sa);
			return sa;
		}
	}
	
	private static LinkedList serviceThreadList = new LinkedList(); 
	private static LinkedList serviceThreadQueue = new LinkedList();
	private static int maxServiceThreadQueueSize = 128;
	
	private static ServiceThread getServiceThread() {
		if (!isRunning())
			return null;
		
		synchronized (serviceThreadQueue) {
			if (serviceThreadQueue.isEmpty()) {
				ServiceThread st = new ServiceThread();
				st.start();
				return st;
			}
			else return ((ServiceThread) serviceThreadQueue.removeFirst());
		}
	}
	
	
	private static final String DEFAULT_LOGFILE_DATE_FORMAT = "yyyy.MM.dd HH:mm:ss";
	private static final DateFormat LOG_TIMESTAMP_FORMATTER = new SimpleDateFormat(DEFAULT_LOGFILE_DATE_FORMAT);
	
	private static final String EXIT_COMMAND = "exit";
	private static final String LIST_COMMAND = "list";
	private static final String LIST_ERRORS_COMMAND = "errors";
	private static final String POOL_SIZE_COMMAND = "poolSize";
	private static final String ACTIONS_COMMAND = "actions";
	private static final String SET_COMMAND = "set";
	private static final String SET_LOG_LEVEL_COMMAND = "setLogLevel";
	
	private static ComponentActionConsole[] getLocalActions() {
		ArrayList cal = new ArrayList();
		ComponentActionConsole ca;
		
		//	shutdown (works only when running from command line, as in daemon mode, 'exit' logs out from console)
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return EXIT_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						EXIT_COMMAND,
						"Exit GoldenGATE Component Server, shutting down the server proper as well as all embedded server components."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0)
					System.exit(0);
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	list components
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return LIST_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						LIST_COMMAND,
						"List all server components currently running in this GoldenGATE Component Server."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					this.reportResult("These " + serverComponents.length + " components are currently plugged into this GoldenGATE Component Server:");
					for (int c = 0; c < serverComponents.length; c++)
						this.reportResult("  '" + serverComponents[c].getLetterCode() + "': " + serverComponents[c].getClass().getName());
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	list component load errors
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return LIST_ERRORS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						LIST_ERRORS_COMMAND,
						"List all errors that occurred while loading server components."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					if (serverComponentLoadErrors.length == 0)
						this.reportResult("No errors occurred while loading server components.");
					else {
						this.reportResult("These " + serverComponentLoadErrors.length + " errors occurred while loading server components:");
						for (int c = 0; c < serverComponentLoadErrors.length; c++)
							this.reportResult("  " + serverComponentLoadErrors[c]);
					}
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	show current size of thread pool
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return POOL_SIZE_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						POOL_SIZE_COMMAND,
						"Output stats on the server's pool of service threads."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0)
					this.reportResult("There are currently " + serviceThreadList.size() + " service threads, " + serviceThreadQueue.size() + " of them idle");
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	show current size of thread pool
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return ACTIONS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						ACTIONS_COMMAND,
						"Output stats on currently running actions."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length != 0) {
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
					return;
				}
				synchronized (activeServiceActions) {
					this.reportResult("There are currently " + activeServiceActions.size() + " active actions:");
					for (Iterator sait = activeServiceActions.iterator(); sait.hasNext();)
						this.reportResult(sait.next().toString());
				}
			}
		};
		cal.add(ca);
		
		//	list and update environment properties
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return SET_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						(SET_COMMAND + " <name> <value>"),
						"List or set environment variables:",
						"- <name>: the name of the variable to set",
						"  (optional, omitting name and value lists variables)",
						"- <value>: the value to set the variable to",
						"  (optional, omitting value erases the variable)",
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				//	list variables
				if (arguments.length == 0) {
					String[] names = environmentSettings.getKeys();
					if (names.length == 0)
						this.reportResult("Currently, no environment variables are set.");
					else {
						this.reportResult("These environment variables are currently set:");
						for (int n = 0; n < names.length; n++)
							this.reportResult("  " + names[n] + " = " + environmentSettings.getSetting(names[n]));
					}
				}
				
				//	erase variable
				else if (arguments.length == 1) {
					String oldValue = environmentSettings.removeSetting(arguments[0]);
					if (oldValue == null)
						this.reportError("There is no environment variable named '" + arguments[0] + "'.");
					else {
						this.reportResult("Environment variable '" + arguments[0] + "' erased successfully.");
						environmentSettingsModified = true;
					}
				}
				
				//	set variable
				else if (arguments.length == 2) {
					String oldValue = environmentSettings.setSetting(arguments[0], arguments[1]);
					if (oldValue == null)
						this.reportResult("Environment variable '" + arguments[0] + "' created successfully.");
					else this.reportResult("Environment variable '" + arguments[0] + "' changed successfully.");
					environmentSettingsModified = true;
				}
				
				//	error
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify name and value at most.");
			}
		};
		cal.add(ca);
		
		//	add action for setting log levels
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return SET_LOG_LEVEL_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						(SET_LOG_LEVEL_COMMAND + " <target> <level>"),
						"List or set log levels:",
						"- <target>: the target debug area ('c' for console, 'n' for network, and 'b' for background)",
						"  (optional, omitting name and value lists debug levels)",
						"- <level>: the debug level ('d' for debug, 'i' for info, 'w' for warning, 'e' for error, and 'o' for off)",
						"  (optional, omitting value erases the variable)",
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				
				//	list all three log levels
				if (arguments.length == 0) {
					this.reportResult("Log levels currently are:");
					this.reportResult("- console: " + this.getLogLevelName(logLevelConsole));
					this.reportResult("- network: " + this.getLogLevelName(logLevelNetwork));
					this.reportResult("- background: " + this.getLogLevelName(logLevelBackground));
				}
				
				//	set log level
				else if (arguments.length == 2) {
					int logLevel = this.getLogLevel(arguments[1]);
					if (logLevel == -1) {
						this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', log level has to be either one of 'd' (debug), 'i' (info), 'w' (warning), 'e' (error), or 'o' (off).");
						return;
					}
					if ("c".equals(arguments[0])) {
						logLevelConsole = logLevel;
						this.reportResult(" Console log level set to " + this.getLogLevelName(logLevel));
					}
					else if ("n".equals(arguments[0])) {
						logLevelNetwork = logLevel;
						this.reportResult(" Network log level set to " + this.getLogLevelName(logLevel));
					}
					else if ("b".equals(arguments[0])) {
						logLevelBackground = logLevel;
						this.reportResult(" Background log level set to " + this.getLogLevelName(logLevel));
					}
					else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', log level can only be set for either one of 'c' (console), 'n' (network), or 'b' (background).");
				}
				
				//	error
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify name and value at most.");
			}
			private int getLogLevel(String logLevel) {
				if ("d".equals(logLevel))
					return GoldenGateServerActivityLogger.LOG_LEVEL_DEBUG;
				else if ("i".equals(logLevel))
					return GoldenGateServerActivityLogger.LOG_LEVEL_INFO;
				else if ("w".equals(logLevel))
					return GoldenGateServerActivityLogger.LOG_LEVEL_WARNING;
				else if ("e".equals(logLevel))
					return GoldenGateServerActivityLogger.LOG_LEVEL_ERROR;
				else if ("o".equals(logLevel))
					return GoldenGateServerActivityLogger.LOG_LEVEL_OFF;
				else return -1;
			}
			private String getLogLevelName(int logLevel) {
				if (logLevel == GoldenGateServerActivityLogger.LOG_LEVEL_DEBUG)
					return "debug";
				else if (logLevel == GoldenGateServerActivityLogger.LOG_LEVEL_INFO)
					return "info";
				else if (logLevel == GoldenGateServerActivityLogger.LOG_LEVEL_WARNING)
					return "warning";
				else if (logLevel == GoldenGateServerActivityLogger.LOG_LEVEL_ERROR)
					return "error";
				else if (logLevel == GoldenGateServerActivityLogger.LOG_LEVEL_OFF)
					return "off";
				else return (logLevel + " (invalid)");
			}
		};
		cal.add(ca);
		
		return ((ComponentActionConsole[]) cal.toArray(new ComponentActionConsole[cal.size()]));
	}
	
	private static HashMap componentActionSetsByLetterCode = new HashMap();
	
	private static abstract class ComponentServerConsole extends LoggingThread {
		
		private static final String HELP_COMMAND = "?";
		private static final String CHANGE_COMPONENT_COMMAND = "cc";
		
		String currentLetterCode = "";
		PrintStream out;
		
		private long activityLogStart = -1;
		private long activityLogEnd = -1;
		private ArrayList activityLogMessages = null;
		
		ComponentServerConsole() {
			super("GgServerConsoleThread");
		}
		
		public abstract void run();
		
		abstract void close();
		
		void executeCommand(String commandString) throws Exception {
			
			//	parse command
			String[] commandTokens = parseCommand(commandString);
			if (commandTokens.length == 0)
				return;
			
			//	setting letter code
			if (CHANGE_COMPONENT_COMMAND.equals(commandTokens[0]))
				this.currentLetterCode = ((commandTokens.length == 1) ? "" : commandTokens[1]);
			
			//	help
			else if (HELP_COMMAND.equals(commandTokens[0])) {
				
				//	global help
				if (this.currentLetterCode.length() == 0) {
					this.logResult(HELP_COMMAND);
					this.logResult("  Display this list of commands.");
					this.logResult(CHANGE_COMPONENT_COMMAND + " <letterCode>");
					this.logResult("  Set the component letter code so subsequent commands automatically go to a specific server component:");
					this.logResult("  - <letterCode>: the new component letter code");
					
					ArrayList letterCodeList = new ArrayList(componentActionSetsByLetterCode.keySet());
					Collections.sort(letterCodeList);
					for (int c = 0; c < letterCodeList.size(); c++) {
						String letterCode = letterCodeList.get(c).toString();
						
						HashMap componentActionSet = ((HashMap) componentActionSetsByLetterCode.get(letterCode));
						if (componentActionSet == null)
							continue;
						
						ArrayList actionNameList = new ArrayList(componentActionSet.keySet());
						if (actionNameList.isEmpty())
							continue;
						
						Collections.sort(actionNameList);
						this.logResult("");
						this.logResult("Commands with prefix '" + letterCode + "':");
						
						for (int a = 0; a < actionNameList.size(); a++) {
							ComponentActionConsole action = ((ComponentActionConsole) componentActionSet.get(actionNameList.get(a)));
							
							String[] explanation = action.getExplanation();
							for (int e = 0; e < explanation.length; e++)
								this.logResult("  " + ((e == 0) ? "" : "  ") + explanation[e]);
						}
					}
				}
				
				//	help for given letter code
				else {
					HashMap componentActionSet = ((HashMap) componentActionSetsByLetterCode.get(this.currentLetterCode));
					if (componentActionSet == null)
						return;
					
					ArrayList actionNameList = new ArrayList(componentActionSet.keySet());
					if (actionNameList.isEmpty())
						return;
					
					Collections.sort(actionNameList);
					this.logResult("");
					this.logResult("Commands with prefix '" + this.currentLetterCode + "':");
					
					for (int a = 0; a < actionNameList.size(); a++) {
						ComponentActionConsole action = ((ComponentActionConsole) componentActionSet.get(actionNameList.get(a)));
						
						String[] explanation = action.getExplanation();
						for (int e = 0; e < explanation.length; e++)
							this.logResult("  " + ((e == 0) ? "" : "  ") + explanation[e]);
					}
				}
			}
			
			//	other command
			else {
				
				//	identify action
				ComponentActionConsole action = getActionForCommand(commandTokens[0]);
				
				//	action not found
				if (action == null)
					this.logResult(" Unknown command '" + commandTokens[0] + "', use '" + HELP_COMMAND + "' to list available commands");
				
				//	invoke action
				else try {
					
					//	prepare action arguments
					String[] actionArguments = new String[commandTokens.length - 1];
					System.arraycopy(commandTokens, 1, actionArguments, 0, actionArguments.length);
					
					//	set up activity logging
					this.activityLogStart = System.currentTimeMillis();
					this.activityLogEnd = (this.activityLogStart + (5 * 1000));
					this.activityLogMessages = new ArrayList();
					
					//	perform action
					action.performActionConsole(actionArguments, this);
				}
				finally {
					this.activityLogStart = -1;
					this.activityLogEnd = -1;
					this.activityLogMessages = null;
				}
			}
		}
		
		public void logError(Throwable error) {
			GoldenGateServer.log(error, LOG_LEVEL_ERROR, logLevelConsole);
		}
		public void logActivity(String message) {
			
			//	log level debug, output message right away
			if (LOG_LEVEL_DEBUG <= logLevelConsole) {
				this.logDebug(message);
				return;
			}
			
			//	create time prefix
			long time = System.currentTimeMillis();
			int runTime = ((int) ((time - this.activityLogStart) & 0x7FFFFFFF));
			
			//	before debug log timeout, store message if we can
			if (time < this.activityLogEnd) {
				if (this.activityLogMessages == null)
					this.logDebug(runTime + "ms: " + message);
				else this.activityLogMessages.add(runTime + "ms: " + message);
				return;
			}
			
			//	write through collected messages as warnings after timeout expired (if not done before)
			if (this.activityLogMessages != null) {
				for (int m = 0; m < this.activityLogMessages.size(); m++)
					this.logWarning(((String) this.activityLogMessages.get(m)));
				this.activityLogMessages = null;
			}
			
			//	this message now has warning level
			this.logWarning(runTime + "ms: " + message);
		}
		public void logResult(String message) {
			this.logAlways(message); // always print the result of a console interaction
		}
		void log(String message, int messageLogLevel) {
			GoldenGateServer.log(message, messageLogLevel, logLevelConsole);
		}
		
		private static final char ESCAPER = '\\';
		private static final char QUOTER = '"';
		
		private String[] parseCommand(String command) {
			if (command == null) return new String[0];
			
			String commandString = command.trim();
			if (commandString.length() == 0) return new String[0];
			
			StringVector commandTokenCollector = new StringVector();
			StringBuffer commandToken = new StringBuffer();
			
			char currentChar;
			int escapeIndex = -1;
			boolean inQuotes = false;
			
			for (int c = 0; c < commandString.length(); c++) {
				
				currentChar = commandString.charAt(c);
				
				if (c == escapeIndex)
					commandToken.append(currentChar);
				
				else if (currentChar == ESCAPER)
					escapeIndex = (c+1);
				
				else if (inQuotes) {
					if (currentChar == QUOTER)
						inQuotes = false;
					
					else commandToken.append(currentChar);
				}
				
				else if (currentChar == QUOTER)
					inQuotes = true;
				
				else if (currentChar < 33) {
					if (commandToken.length() != 0) {
						commandTokenCollector.addElement(commandToken.toString());
						commandToken = new StringBuffer();
					}
				}
				
				else commandToken.append(currentChar);
			}
			
			if (commandToken.length() != 0)
				commandTokenCollector.addElement(commandToken.toString());
			
			return commandTokenCollector.toStringArray();
		}
		
		private ComponentActionConsole getActionForCommand(String command) {
			String letterCode;
			String actionName;
			
			//	console native action
			if (command.indexOf('.') == -1) {
				letterCode = this.currentLetterCode;
				actionName = command;
			}
			
			//	component action
			else {
				letterCode = command.substring(0, command.indexOf('.'));
				actionName = command.substring(command.indexOf('.') + 1);
			}
			
			HashMap componentActionSet = ((HashMap) componentActionSetsByLetterCode.get(letterCode));
			if (componentActionSet == null)
				return null;
			return ((ComponentActionConsole) componentActionSet.get(actionName));
		}
	}
	
	private static class SystemInConsole extends ComponentServerConsole {
		
		private BufferedReader commandReader;
		
		SystemInConsole(PrintStream out) {
			synchronized (consoleOutLock) {
				this.out = out;
			}
			this.commandReader = new BufferedReader(new InputStreamReader(System.in));
		}
		
		void close() {
			try {
				this.commandReader.close();
			} catch (IOException ioe) {}
		}
		
		public void run() {
			while (true) try {
				System.out.print("GgServer" + ((this.currentLetterCode.length() == 0) ? "" : ("." + this.currentLetterCode)) + ">");
				String commandString = this.commandReader.readLine();
				if (commandString == null)
					return;
				else this.executeCommand(commandString.trim());
			}
			catch (Exception e) {
				System.out.println("Error reading or executing console command: " + e.getMessage());
			}
		}
	}
	
	private static class SocketConsole extends ComponentServerConsole {
		
		private int port;
		private String password;
		private ServerSocket serverSocket;
		private Socket activeSocket;
		private int timeout;
		
		SocketConsole(int port, String password, int timeout) {
			this.port = port;
			this.password = password;
			this.timeout = timeout;
		}
		
		void close() {
			if (this.serverSocket != null) try {
				ServerSocket ss = this.serverSocket;
				this.serverSocket = null;
				ss.close();
			} catch (IOException ioe) {}
			
			if (this.activeSocket != null) try {
				synchronized (consoleOutLock) {
					this.out = null;
				}
				Socket as = this.activeSocket;
				this.activeSocket = null;
				as.close();
			} catch (IOException ioe) {}
		}
		
		public void run() {
			try {
				ServerSocket ss = new ServerSocket();
				ss.setReuseAddress(true);
				ss.bind(new InetSocketAddress(this.port));
				this.serverSocket = ss;
			}
			catch (Exception e) {
				System.out.println("Error opening network console: " + e.getMessage());
				return;
			}
				
			while (this.serverSocket != null) try {
				BufferedReader commandReader;
				PrintStream sOut;
				
				//	wait for incoming connections
				Socket as = this.serverSocket.accept();
				System.out.println("Network console connection from " + as.getInetAddress().getHostAddress() + ", local is " + InetAddress.getLocalHost().getHostAddress());
				if (as.getInetAddress().getHostAddress().equals("127.0.0.1") || as.getInetAddress().getHostAddress().equals(InetAddress.getLocalHost().getHostAddress())) {
					System.out.println("Network console connected.");
					sOut = new PrintStream(as.getOutputStream(), true);
					
					commandReader = new BufferedReader(new InputStreamReader(as.getInputStream()));
					as.setSoTimeout(500); // wait half a second for password, no longer
					String password = commandReader.readLine();
					if (this.password.equals(password)) {
						sOut.println("WELCOME");
					}
					else {
						System.out.println("Network console authentication failed.");
						sOut.println("Authentication failed.");
						as.close();
						throw new IOException("Network console authentication failed");
					}
				}
				else {
					as.close();
					throw new IOException("Remote network console connection not allowed");
				}
				
				this.activeSocket = as;
				this.activeSocket.setSoTimeout(this.timeout);
				synchronized (consoleOutLock) {
					this.out = sOut;
				}
				
				while (this.activeSocket != null) try {
					
					String commandString;
					try {
						commandString = commandReader.readLine();
					}
					catch (Exception e) {
						commandString = null;
					}
					
					//	client logout
					if ("GOODBYE".equals(commandString)) {
						synchronized (consoleOutLock) {
							this.out.println("GOODBYE");
							this.out = null;
						}
						this.activeSocket.close();
						this.activeSocket = null;
					}
					
					//	server shutdown
					else if (this.activeSocket == null) {
						//	ignore it here, it's done in close()
					}
					
					//	client connection lost
					else if (commandString == null) {
						synchronized (consoleOutLock) {
							this.out = null;
						}
						this.activeSocket.close();
						this.activeSocket = null;
					}
					
					//	regular command
					else {
						commandString = commandString.trim();
						try {
							this.executeCommand(commandString);
						}
						catch (Throwable t) {
							System.out.println("Error executing network console command: " + t.getMessage());
							t.printStackTrace(System.out);
						}
						synchronized (consoleOutLock) {
							this.out.println("COMMAND_DONE");
						}
					}
				}
				catch (Exception e) {
					System.out.println("Error reading network console command: " + e.getMessage());
					e.printStackTrace(System.out);
				}
			}
			catch (Exception e) {
				System.out.println("Error accepting network console connection: " + e.getMessage());
				e.printStackTrace(System.out);
			}
		}
	}
}
