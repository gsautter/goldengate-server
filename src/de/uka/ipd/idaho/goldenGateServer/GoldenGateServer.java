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


import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import de.uka.ipd.idaho.easyIO.EasyIO;
import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.gamta.util.GamtaClassLoader.ComponentLoadErrorLogger;
import de.uka.ipd.idaho.gamta.util.GamtaClassLoader.ComponentLoadErrorLogger.ComponentLoadError;
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
public class GoldenGateServer implements GoldenGateServerConstants, GoldenGateServerNetworkMonitoringConstants {
	private static final String CONFIG_FILE_NAME = "config.cnfg";
	private static final String PORT_SETTING_NAME = "port";
	
	private static final int defaultNetworkInterfaceTimeout = (10 * 1000); // 10 seconds by default
	private static final int defaultNetworkConsoleTimeout = (10 * 60 * 1000); // 10 minutes by default
	
	private static ComponentHost serverComponentHost;
	private static GoldenGateServerComponent[] serverComponents;
	private static ComponentLoadError[] serverComponentLoadErrors;
	
	private static int networkInterfaceTimeout = defaultNetworkInterfaceTimeout;
	private static int port = -1; 
	private static ServerThread serverThread = null;
	
	private static File rootFolder;
	
	private static Settings settings;
	private static Settings ioProviderSettings = new Settings();
	private static Settings environmentSettings = new Settings();
	private static boolean environmentSettingsModified = false;
	private static String networkMonitoringToken = null;
	
	private static ComponentServerConsole console;
	
	//	log levels for console output
	private static int outLevelNetwork = GoldenGateServerActivityLogger.LOG_LEVEL_WARNING;
	private static int outLevelConsole = GoldenGateServerActivityLogger.LOG_LEVEL_INFO;
	private static int outLevelBackground = GoldenGateServerActivityLogger.LOG_LEVEL_WARNING;
	
	//	log levels for log file output
	private static int logLevelNetwork = GoldenGateServerActivityLogger.LOG_LEVEL_WARNING;
	private static int logLevelConsole = GoldenGateServerActivityLogger.LOG_LEVEL_INFO;
	private static int logLevelBackground = GoldenGateServerActivityLogger.LOG_LEVEL_INFO;
	
//	private static PrintStream logOut;
	private static LogStream logOut;
	private static AsynchronousWorkQueue logQueue;
	private static LogStream consoleOut;
	private static AsynchronousWorkQueue consoleQueue;
	private static PrintStream logErr;
	private static boolean formatLogs = false;
//	private static int memoryLogInterval = -1;
	
	//	network action listeners
	private static ArrayList networkActionListeners = null;
	
	//	startup memory stats
	private static long startupMaxMemory;
	private static long startupFreeMemory;
	
	/**	
	 * @return an IoProvider for accessing the SRS's database
	 */
	public static IoProvider getIoProvider() {
		return EasyIO.getIoProvider(ioProviderSettings);
	}
	
	/**
	 * Add a network action listener to this GoldenGATE Server so it receives
	 * notification about network actions in the server.
	 * @param nal the network action listener to add
	 */
	public static synchronized void addNetworkActionListener(GoldenGateServerNetworkActionListener nal) {
		if (nal == null)
			return;
		if (networkActionListeners == null)
			networkActionListeners = new ArrayList(2);
		networkActionListeners.add(nal);
	}
	
	/**
	 * Remove a network action listener from this GoldenGATE Server.
	 * @param nal the network action listener to remove
	 */
	public static synchronized void removeNetworkActionListener(GoldenGateServerNetworkActionListener nal) {
		if (nal == null)
			return;
		if (networkActionListeners == null)
			return;
		networkActionListeners.remove(nal);
		if (networkActionListeners.isEmpty())
			networkActionListeners = null;
	}
	
	static void notifyNetworkActionStarted(String command, int wait) {
		if (networkActionListeners == null)
			return;
		for (int l = 0; l < networkActionListeners.size(); l++)
			((GoldenGateServerNetworkActionListener) networkActionListeners.get(l)).networkActionStarted(command, wait);
	}
	
	static void notifyNetworkActionFinished(String command, int wait, int time) {
		if (networkActionListeners == null)
			return;
		for (int l = 0; l < networkActionListeners.size(); l++)
			((GoldenGateServerNetworkActionListener) networkActionListeners.get(l)).networkActionFinished(command, wait, time);
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException {
		System.out.println("GoldenGATE Server starting up:");
		
		StackTraceElement[] ste = Thread.currentThread().getStackTrace();
		System.out.println(" - invocation JVM stack trace:");
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
//		
//		//	read memory log interval
//		try {
//			memoryLogInterval = Integer.parseInt(settings.getSetting("memoryLogInterval", ("" + memoryLogInterval)));
//			System.out.println("   - memory log interval is " + memoryLogInterval);
//		}
//		catch (NumberFormatException nfe) {
//			System.out.println("   - could not read memory log interval '" + settings.getSetting("memoryLogInterval", ("" + memoryLogInterval)) + "'");
//		}
		
		//	hold on to original System.out and System.err
		final PrintStream systemOut = System.out;
		final PrintStream systemErr = System.err;
		
		//	open console (command line) interface
		if (isDaemon) {
			System.out.println(" - starting as daemon:");
			
			//	read monitoring network session token
			networkMonitoringToken = settings.getSetting("networkMonitoringToken");
			
			//	open network console
			int ncPort = 15808;
			try {
				ncPort = Integer.parseInt(settings.getSetting("networkConsolePort", ("" + ncPort)));
				System.out.println("   - network console port is " + ncPort);
			}
			catch (NumberFormatException nfe) {
				System.out.println("   - could not read network console port, using default " + ncPort);
			}
			console = new SocketConsole(ncPort, settings.getSetting("networkConsolePassword", "GG"), Integer.parseInt(settings.getSetting("networkConsoleTimeout", ("" + defaultNetworkConsoleTimeout))));
			consoleOut = ((SocketConsole) console).resultOut;
			System.out.println("   - network console created");
			
			//	log to system output streams (wrapper writes them to file)
//			logOut = systemOut;
			logOut = new LogStream(systemOut);
			logErr = systemErr;
		}
		else {
			System.out.println(" - starting in command shell:");
			
			console = new SystemInConsole(System.out);
			System.out.println("   - console created");
			
			//	get log timestamp
			long logTime = System.currentTimeMillis();
			
			//	create log file for System.out
			File systemOutFile = new File(rootFolder, ("GgServer.SystemOut." + logTime + ".log"));
			systemOutFile.createNewFile();
			OutputStream systemOutStream = new BufferedOutputStream(new FileOutputStream(systemOutFile));
			
			//	create log file for System.err
			File systemErrFile = new File(rootFolder, ("GgServer.Error." + logTime + ".log"));
			systemErrFile.createNewFile();
			OutputStream systemErrStream = new BufferedOutputStream(new FileOutputStream(systemErrFile));
			
			//	log to dedicated files (with auto-flushing, we want these log files up to date because the beef usually is at the end)
//			logOut = new PrintStream(systemOutStream, true);
			logOut = new LogStream(new PrintStream(systemOutStream, true));
			logErr = new PrintStream(systemErrStream, true);
			System.out.println("   - log files created");
			
			//	make sure startup output also goes to System.out and System.err (odds are we're testing, better to see it right away)
			System.setOut(new ForkPrintStream(logOut) {
				void forkLine(String s) {
					systemOut.println(s);
				}
			});
			System.out.println("   - System.out forked");
			System.setErr(new ForkPrintStream(logErr) {
				void forkLine(String s) {
					systemErr.println(s);
				}
			});
			System.out.println("   - System.err forked");
		}
		
		//	set up monitoring for log writers
		logQueue = new AsynchronousWorkQueue("LogWriter") {
			public String getStatus() {
				return (this.name + ": got " + logOut.bufferCount() + " buffers with total of " + logOut.bufferLevel() + " lines to write");
			}
		};
		if (consoleOut != null)
			consoleQueue = new AsynchronousWorkQueue("ConsoleWriter") {
				public String getStatus() {
					return (this.name + ": got " + consoleOut.bufferCount() + " buffers with total of " + consoleOut.bufferLevel() + " lines to write");
				}
			};
		
		//	read log and console output levels, as well as log formatting flag
		outLevelNetwork = readOutputLevel(settings, "console", "network", outLevelNetwork);
		outLevelConsole = readOutputLevel(settings, "console", "console", outLevelConsole);
		outLevelBackground = readOutputLevel(settings, "console", "background", outLevelBackground);
		logLevelNetwork = readOutputLevel(settings, "log", "network", logLevelNetwork);
		logLevelConsole = readOutputLevel(settings, "log", "console", logLevelConsole);
		logLevelBackground = readOutputLevel(settings, "log", "background", logLevelBackground);
		formatLogs = "true".equals(settings.getSetting("log.format", "false"));
		
		//	start server
		System.out.println(" - starting componet server");
		start();
		System.out.println(" - componet server started");
		
		//	hold on to startup version of System.out to keep logging behavior consistent
		PrintStream startSystemOut = System.out;
		
		//	redirect System.out and System.err to logging methods
		System.setOut(new RedirectPrintStream() {
			void redirectLine(String line) {
				Thread ct = Thread.currentThread();
				if (ct instanceof GoldenGateServerActivityLogger)
					((GoldenGateServerActivityLogger) ct).logInfo(line);
				else logBackground(line, GoldenGateServerActivityLogger.LOG_LEVEL_INFO);
			}
		});
		startSystemOut.println("   - System.out redirected");
		System.setErr(new RedirectPrintStream() {
			void redirectLine(String line) {
				Thread ct = Thread.currentThread();
				if (ct instanceof GoldenGateServerActivityLogger)
					((GoldenGateServerActivityLogger) ct).logError(line);
				else logBackground(line, GoldenGateServerActivityLogger.LOG_LEVEL_ERROR);
			}
		});
		startSystemOut.println("   - System.err redirected");
		
		//	start console after startup complete
		console.start();
		startSystemOut.println(" - console started");
		
		//	ensure proper shutdown on shutdown, and make sure log files are closed
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				
				//	make sure all System.out and System.err logging from here onwards goes to log file
				System.setOut((logOut == systemOut) ? logOut : new ForkPrintStream(logOut) {
					void forkLine(String s) {
						systemOut.println(s);
					}
				});
				System.setErr((logErr == systemErr) ? logErr : new ForkPrintStream(logErr) {
					void forkLine(String s) {
						systemErr.println(s);
					}
				});
				
				//	perform shutdown
				System.out.println("GoldenGATE Server shutting down:");
				GoldenGateServer.stop();
				System.out.println(" - component server stopped");
				
				//	disable log monitoring
				logQueue.dispose();
				if (consoleQueue != null)
					consoleQueue.dispose();
				
				//	close log streams
				logErr.flush();
				logErr.close();
				System.out.println(" - System.err closed");
				logOut.flush();
				logOut.close();
				System.out.println(" - System.out closed"); // have to do this beforehand, little chance of getting through afterwards ...
			}
		});
		startSystemOut.println(" - shutdown hook registered");
		
		startupMaxMemory = Runtime.getRuntime().maxMemory();
		startupFreeMemory = Runtime.getRuntime().freeMemory();
		startSystemOut.println("GoldenGATE Server startup complete");
	}
	
	private static int readOutputLevel(Settings set, String group, String detail, int def) {
		int ol = getLogLevel(set.getSetting(("outputLevel." + group + "." + detail), set.getSetting("outputLevel." + group)));
		return ((ol == -1) ? def : ol);
	}
	
	private static synchronized void start() {
		
		//	read network interface port and timeout, as well as maximum thread pool size
		try {
			port = Integer.parseInt(settings.getSetting(PORT_SETTING_NAME, ("" + port)));
			networkInterfaceTimeout = Integer.parseInt(settings.getSetting("networkInterfaceTimeout", ("" + networkInterfaceTimeout)));
			maxServiceThreadQueueSize = Integer.parseInt(settings.getSetting("maxIdleServiceThreads", ("" + maxServiceThreadQueueSize)));
		} catch (NumberFormatException nfe) {}
		
		//	get database access and email output data
		ioProviderSettings = settings.getSubset("EasyIO");
		
		//	get environment settings
		environmentSettings = settings.getSubset("ENV");
		
		//	load server components
		ComponentLoadErrorLogger serverComponentLoadErrorLogger = new ComponentLoadErrorLogger();
		GoldenGateServerComponent[] loadedServerComponents = GoldenGateServerComponentLoader.loadServerComponents(new File(rootFolder, COMPONENT_FOLDER_NAME), serverComponentLoadErrorLogger);
		System.out.println("   - components loaded");
		
		//	initialize and register components
		serverComponentHost = new ComponentHost();
		ArrayList serverComponentList = new ArrayList();
		ArrayList serverComponentLoadErrorList = new ArrayList(Arrays.asList(serverComponentLoadErrorLogger.getErrors()));
		for (int c = 0; c < loadedServerComponents.length; c++) try {
			System.out.println("   - initializing " + loadedServerComponents[c].getLetterCode());
			loadedServerComponents[c].setHost(serverComponentHost);
			loadedServerComponents[c].init();
			System.out.println("   - " + loadedServerComponents[c].getLetterCode() + " initialized");
			serverComponentList.add(loadedServerComponents[c]);
			GoldenGateServerComponentRegistry.registerServerComponent(loadedServerComponents[c]);
		}
		catch (Throwable t) {
			System.out.println(t.getClass().getName() + " (" + t.getMessage() + ") while initializing " + ((loadedServerComponents[c] == null) ? "server component" : loadedServerComponents[c].getClass().getName()));
			t.printStackTrace(System.out);
			serverComponentLoadErrorList.add(new ComponentLoadError(loadedServerComponents[c].getClass().getName(), t, "initialization"));
		}
		
		//	get operational ones
		loadedServerComponents = ((GoldenGateServerComponent[]) serverComponentList.toArray(new GoldenGateServerComponent[serverComponentList.size()]));
		serverComponentList.clear();
		
		//	link components
		for (int c = 0; c < loadedServerComponents.length; c++) try {
			System.out.println("   - linking " + loadedServerComponents[c].getLetterCode());
			loadedServerComponents[c].link();
			System.out.println("   - " + loadedServerComponents[c].getLetterCode() + " linked");
			serverComponentList.add(loadedServerComponents[c]);
		}
		catch (Throwable t) {
			System.out.println(t.getClass().getName() + " (" + t.getMessage() + ") while linking " + ((loadedServerComponents[c] == null) ? "server component" : loadedServerComponents[c].getClass().getName()));
			t.printStackTrace(System.out);
			serverComponentLoadErrorList.add(new ComponentLoadError(loadedServerComponents[c].getClass().getName(), t, "linking"));
			GoldenGateServerComponentRegistry.unregisterServerComponent(loadedServerComponents[c]);
		}
		
		//	get operational ones
		loadedServerComponents = ((GoldenGateServerComponent[]) serverComponentList.toArray(new GoldenGateServerComponent[serverComponentList.size()]));
		serverComponentList.clear();
		
		//	link initialize components
		for (int c = 0; c < loadedServerComponents.length; c++) try {
			System.out.println("   - linked initializing " + loadedServerComponents[c].getLetterCode());
			loadedServerComponents[c].linkInit();
			System.out.println("   - " + loadedServerComponents[c].getLetterCode() + " linked initialized");
			serverComponentList.add(loadedServerComponents[c]);
		}
		catch (Throwable t) {
			System.out.println(t.getClass().getName() + " (" + t.getMessage() + ") while linked initializing " + ((loadedServerComponents[c] == null) ? "server component" : loadedServerComponents[c].getClass().getName()));
			t.printStackTrace(System.out);
			serverComponentLoadErrorList.add(new ComponentLoadError(loadedServerComponents[c].getClass().getName(), t, "linked-initialization"));
			GoldenGateServerComponentRegistry.unregisterServerComponent(loadedServerComponents[c]);
		}
		
		//	get operational ones
		serverComponents = ((GoldenGateServerComponent[]) serverComponentList.toArray(new GoldenGateServerComponent[serverComponentList.size()]));
		
		//	store errors
		serverComponentLoadErrors = ((ComponentLoadError[]) serverComponentLoadErrorList.toArray(new ComponentLoadError[serverComponentLoadErrorList.size()]));
		
		//	obtain local console actions
		ComponentActionConsole[] localConsoleActions = getLocalConsoleActions();
//		Map localActionSet = Collections.synchronizedMap(new HashMap());
		Map localActionSet = Collections.synchronizedMap(new TreeMap(String.CASE_INSENSITIVE_ORDER));
		for (int a = 0; a < localConsoleActions.length; a++)
			localActionSet.put(localConsoleActions[a].getActionCommand(), localConsoleActions[a]);
		if (localActionSet.size() != 0)
			componentActionSetsByLetterCode.put("", localActionSet);
		
		//	obtain component's console actions
		for (int c = 0; c < serverComponents.length; c++) {
			String componentLetterCode = serverComponents[c].getLetterCode();
			System.out.println("   - getting actions from " + componentLetterCode);
			
			ComponentAction[] componentActions = serverComponents[c].getActions();
			if ((componentActions == null) || (componentActions.length == 0)) {
				System.out.println("   - no actions from " + componentLetterCode + " to integrate");
				continue;
			}
			
			//	handle individual actions
//			Map consoleActionSet = Collections.synchronizedMap(new HashMap());
			Map consoleActionSet = Collections.synchronizedMap(new TreeMap(String.CASE_INSENSITIVE_ORDER));
			for (int a = 0; a < componentActions.length; a++) {
				
				//	collect network actions in prefix map
				if (componentActions[a] instanceof ComponentActionConsole)
					consoleActionSet.put(componentActions[a].getActionCommand(), componentActions[a]);
				
				//	integrate network actions right away
				if (componentActions[a] instanceof ComponentActionNetwork) {
					if (serverComponentActions.containsKey(componentActions[a].getActionCommand()))
						throw new RuntimeException("Duplicate network action '" + componentActions[a].getActionCommand() + "'");
					serverComponentActions.put(componentActions[a].getActionCommand(), componentActions[a]);
				}
			}
			
			//	index network actions
			if (consoleActionSet.size() != 0)
				componentActionSetsByLetterCode.put(componentLetterCode, consoleActionSet);
			System.out.println("   - actions from " + componentLetterCode + " integrated");
		}
		System.out.println("   - components integrated in network and console interfaces");
		
		//	add network monitoring actions if token configured
		if (networkMonitoringToken != null) {
			ComponentActionNetwork[] networkMonitoringActions = getNetworkMonitoringActions();
			for (int a = 0; a < networkMonitoringActions.length; a++) {
				if (serverComponentActions.containsKey(networkMonitoringActions[a].getActionCommand()))
					throw new RuntimeException("Duplicate network action '" + networkMonitoringActions[a].getActionCommand() + "'");
				serverComponentActions.put(networkMonitoringActions[a].getActionCommand(), networkMonitoringActions[a]);
			}
		}
		
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
			return (proxiedServiceThreadIDs.get() != null);
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
			else logBackground(message, LOG_LEVEL_ERROR);
		}
		public void logError(Throwable error) {
			Thread ct = Thread.currentThread();
			if (ct instanceof GoldenGateServerActivityLogger)
				((GoldenGateServerActivityLogger) ct).logError(error);
			else logBackground(error);
		}
		public void logWarning(String message) {
			Thread ct = Thread.currentThread();
			if (ct instanceof GoldenGateServerActivityLogger)
				((GoldenGateServerActivityLogger) ct).logWarning(message);
			else logBackground(message, LOG_LEVEL_WARNING);
		}
		public void logInfo(String message) {
			Thread ct = Thread.currentThread();
			if (ct instanceof GoldenGateServerActivityLogger)
				((GoldenGateServerActivityLogger) ct).logInfo(message);
			else logBackground(message, LOG_LEVEL_INFO);
		}
		public void logDebug(String message) {
			Thread ct = Thread.currentThread();
			if (ct instanceof GoldenGateServerActivityLogger)
				((GoldenGateServerActivityLogger) ct).logDebug(message);
			else logBackground(message, LOG_LEVEL_DEBUG);
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
			else logBackground(message, -1);
		}
		public void logResult(String message) {
			Thread ct = Thread.currentThread();
			if (ct instanceof GoldenGateServerActivityLogger)
				((GoldenGateServerActivityLogger) ct).logResult(message);
			else logBackground(message, LOG_LEVEL_INFO);
		}
	}
	
	private static void logNetwork(String message, int messageLogLevel) {
		if (messageLogLevel <= logLevelNetwork)
			doLogNetwork(message, messageLogLevel);
		if (messageLogLevel <= outLevelNetwork)
			console.send(message, messageLogLevel, 'N');
	}
	private static void doLogNetwork(String message, int messageLogLevel) {
		if (formatLogs)
			GoldenGateServerMessageFormatter.printMessage(message, messageLogLevel, 'N', null, logOut);
		else logOut.println(message);
	}
	
	private static void logNetwork(Throwable error) {
		if (GoldenGateServerActivityLogger.LOG_LEVEL_ERROR <= logLevelNetwork)
			doLogNetwork(error);
		if (GoldenGateServerActivityLogger.LOG_LEVEL_ERROR <= outLevelNetwork)
			console.send(error, 'N');
	}
	private static void doLogNetwork(Throwable error) {
		if (formatLogs)
			GoldenGateServerMessageFormatter.printError(error, 'N', logOut);
		else error.printStackTrace(logOut);
	}
	
	private static void logBackground(String message, int messageLogLevel) {
		if (messageLogLevel <= logLevelBackground)
			doLogBackground(message, messageLogLevel);
		if (messageLogLevel <= outLevelBackground)
			console.send(message, messageLogLevel, 'B');
	}
	private static void doLogBackground(String message, int messageLogLevel) {
		if (formatLogs)
			GoldenGateServerMessageFormatter.printMessage(message, messageLogLevel, 'B', null, logOut);
		else logOut.println(message);
	}
	
	private static void logBackground(Throwable error) {
		if (GoldenGateServerActivityLogger.LOG_LEVEL_ERROR <= logLevelBackground)
			doLogBackground(error);
		if (GoldenGateServerActivityLogger.LOG_LEVEL_ERROR <= outLevelBackground)
			console.send(error, 'B');
	}
	private static void doLogBackground(Throwable error) {
		if (formatLogs)
			GoldenGateServerMessageFormatter.printError(error, 'B', logOut);
		else error.printStackTrace(logOut);
	}
	
	private static void logConsole(String message, int messageLogLevel) {
		if (messageLogLevel <= logLevelConsole)
			doLogConsole(message, messageLogLevel);
	}
	private static void doLogConsole(String message, int messageLogLevel) {
		if (formatLogs)
			GoldenGateServerMessageFormatter.printMessage(message, messageLogLevel, 'C', null, logOut);
		else logOut.println(message);
	}
	
	private static void logConsole(Throwable error) {
		if (GoldenGateServerActivityLogger.LOG_LEVEL_ERROR <= logLevelConsole)
			doLogConsole(error);
	}
	private static void doLogConsole(Throwable error) {
		if (formatLogs)
			GoldenGateServerMessageFormatter.printError(error, 'C', logOut);
		else error.printStackTrace(logOut);
	}
	
	private static synchronized void stop() {
		if (!isRunning())
			return;
		
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
					
					logNetwork(LOG_TIMESTAMP_FORMATTER.format(new Date()) + ": Handling request from " + socket.getRemoteSocketAddress(), GoldenGateServerActivityLogger.LOG_LEVEL_INFO);
					
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
				
				//	catch Exceptions caused by single incoming connection requests
				catch (Throwable t) {
					if ("socket closed".equals(t.getMessage()))
						logNetwork("Server socket closed.", GoldenGateServerActivityLogger.LOG_LEVEL_WARNING);
					else {
						logNetwork(("Error handling request - " + t.getMessage()), GoldenGateServerActivityLogger.LOG_LEVEL_ERROR);
						logNetwork(t);
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
	
	private static ThreadLocal proxiedServiceThreadIDs = new ThreadLocal();
	
	private static abstract class LoggingThread extends Thread implements GoldenGateServerActivityLogger {
		LoggingThread(String name) {
			super(name);
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
					this.logError("Error handling request - " + t.getClass().getName() + " (" + t.getMessage() + ")");
					this.logError(t);
				}
				
				//	clean up
				finally {
					this.request = null;
//					logOut.shrinkBuffer();
				}
				
				//	re-enqueue if not shut down ...
				if (this.keepRunning)
					synchronized (serviceThreadQueue) {
						
						//	but only if less than maximum idle threads in list
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
		
		void cancelRequest() throws Exception {
			if (this.request != null)
				this.request.cancel();
		}
		
		void shutdown() {
			this.keepRunning = false;
			this.request = null;
			synchronized(lock) {
				this.lock.notifyAll();
			}
		}
		
		public void logError(String message) {
			this.log(message, LOG_LEVEL_ERROR);
		}
		public void logError(Throwable error) {
			GoldenGateServer.logNetwork(error);
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
			GoldenGateServer.logNetwork(message, messageLogLevel);
		}
	}
	
	private static class ServiceRequest {
		private Socket socket;
		private BufferedLineInputStream requestIn;
		private BufferedLineOutputStream responseOut;
		
		private ServiceAction threadAction = null;
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
			try {
				
				//	read command
				String command = this.requestIn.readLine();
				thread.logInfo("Command is " + command);
				
				//	catch 'PROXIED' property
				if ("PROXIED".equals(command)) {
					proxiedServiceThreadIDs.set(threadId);
					command = this.requestIn.readLine();
					thread.logInfo("Command is " + command);
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
					this.threadAction = new ServiceAction(command, thread);
					
					//	TODO once we introduce headers, maybe read them here, and store in ThreadLocal
					
					//	report action as running (this will queue us up and wait if too many other requests are on same action)
					startServiceAction(this.threadAction);
					
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
				proxiedServiceThreadIDs.remove();
				finishServiceAction(this.threadAction, true);
				this.socket.close();
			}
		}
		
		void cancel() throws Exception {
			//proxiedServiceThreadIDs.remove(); // no use doing this from console thread
			finishServiceAction(this.threadAction, false);
			if (this.socket.isClosed())
				return;
			this.responseOut.write("Request to '" + this.threadAction.command + "' terminated forcefully");
			this.responseOut.newLine();
			this.responseOut.flush();
			this.socket.close();
		}
		
		void logActivity(String message) {
			
			//	log level debug, output message right away
			if (GoldenGateServerActivityLogger.LOG_LEVEL_DEBUG <= logLevelNetwork) {
				logNetwork(message, GoldenGateServerActivityLogger.LOG_LEVEL_DEBUG);
				return;
			}
			
			//	create time prefix
			long time = System.currentTimeMillis();
			int runTime = ((int) ((time - this.activityLogStart) & 0x7FFFFFFF));
			long threadId = Thread.currentThread().getId();
			
			//	before debug log timeout, store message if we can
			if (time < this.activityLogEnd) {
				if (this.activityLogMessages == null)
					logNetwork((threadId + "/" + runTime + "ms: " + message), GoldenGateServerActivityLogger.LOG_LEVEL_DEBUG);
				else this.activityLogMessages.add(threadId + "/" + runTime + "ms: " + message);
				return;
			}
			
			//	write through collected messages as warnings after timeout expired (if not done before)
			if (this.activityLogMessages != null) {
				for (int m = 0; m < this.activityLogMessages.size(); m++)
					logNetwork(((String) this.activityLogMessages.get(m)), GoldenGateServerActivityLogger.LOG_LEVEL_WARNING);
				this.activityLogMessages = null;
			}
			
			//	this message now has warning level
			logNetwork((threadId + "/" + runTime + "ms: " + message), GoldenGateServerActivityLogger.LOG_LEVEL_WARNING);
		}
	}
	
	private static class ServiceAction implements Comparable {
		final long startTime;
		final String command;
		final Thread thread;
		final String threadName;
		private String status = "running";
		long waited = -1;
		long started;
		ServiceAction(String command, Thread thread) {
			this.startTime = System.currentTimeMillis();
			this.command = command;
			this.thread = thread;
			this.threadName = this.thread.getName();
			this.started = this.startTime;
		}
		synchronized void suspend() throws InterruptedException {
			/* we've been told to resume before getting around to actually
			 * suspending despite being told to ... pathological case, but
			 * appears to happen occasionally, causing an action to wait
			 * indefinitely, as management data structures have it in running
			 * status ... */
			if (this.waited != -1)
				return;
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
			return (this.command + " (" + this.status + " since " + (System.currentTimeMillis() - this.started) + "ms" + ((this.waited == -1) ? "" : (" after waiting for " + this.waited + "ms")) + ")");
		}
		String toString(String prefix) {
			return (prefix + this.toString());
		}
		void printDetails(ComponentActionConsole cac) {
			cac.reportResult("   " + this.thread.getName() + " (" + this.thread.getState() + ")");
		}
		void dumpStack(ComponentActionConsole cac) {
			StackTraceElement[] stes = this.thread.getStackTrace();
			for (int e = 0; e < stes.length; e++)
				cac.reportResult("   " + stes[e].toString());
		}
	}
	
	private static Set activeServiceActions = Collections.synchronizedSet(new TreeSet());
	
	private static Map serviceActionCoordinators = Collections.synchronizedMap(new HashMap() {
		public Object get(Object key) {
			Object value = super.get(key);
			if (value == null) {
				value = new ServiceActionCoordinator((String) key);
				this.put(key, value);
			}
			return value;
		}
	});
	
	private static void startServiceAction(ServiceAction sa) throws InterruptedException {
		if (sa != null)
			((ServiceActionCoordinator) serviceActionCoordinators.get(sa.command)).startServiceAction(sa);
	}
	
	private static void finishServiceAction(ServiceAction sa, boolean isFinished) {
		if (sa != null)
			((ServiceActionCoordinator) serviceActionCoordinators.get(sa.command)).finishServiceAction(sa, isFinished);
	}
	
	private static class ServiceActionCoordinator {
		private TreeSet runningActions = new TreeSet();
		private LinkedList queuedActions = new LinkedList();
		private String actionCommand;
		ServiceActionCoordinator(String actionCommand) {
			this.actionCommand = actionCommand;
		}
		void startServiceAction(ServiceAction sa) throws InterruptedException {
			sa = this.doStartServiceAction(sa);
			//	we need to release the monitor on the coordinator before suspending on the one of the action
			if (sa != null)
				sa.suspend();
			notifyNetworkActionStarted(this.actionCommand, ((sa == null) ? -1 : ((int) sa.waited)));
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
		void finishServiceAction(ServiceAction sa, boolean isFinished) {
			if (isFinished)
				notifyNetworkActionFinished(this.actionCommand, ((int) sa.waited), ((int) (System.currentTimeMillis() - sa.started)));
			sa = this.doFinishServiceAction(sa, isFinished);
			//	we need to release the monitor on the coordinator before acquiring the one on the action
			if (sa != null)
				sa.resume();
		}
		private synchronized ServiceAction doFinishServiceAction(ServiceAction sa, boolean isFinished) {
			activeServiceActions.remove(sa);
			this.runningActions.remove(sa);
			if (this.queuedActions.isEmpty())
				return null;
			if (!isFinished)
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
	private static final String LIST_COMPONENTS_COMMAND = "list";
	private static final String LIST_ERRORS_COMMAND = "errors";
	private static final String POOL_SIZE_COMMAND = "poolSize";
	private static final String LIST_ACTIONS_COMMAND = "actions";
	private static final String LIST_THREADS_COMMAND = "threads";
	private static final String LIST_THREAD_GROUPS_COMMAND = "threadGroups";
	private static final String THREAD_STACK_COMMAND = "stack";
	private static final String KILL_THREAD_COMMAND = "kill";
	private static final String WAKE_THREAD_COMMAND = "wake";
	private static final String LIST_QUEUES_COMMAND = "queues";
	private static final String SHOW_MEMORY_COMMAND = "memory";
	private static final String SHOW_LOGGERS_COMMAND = "loggers";
	private static final String SET_COMMAND = "set";
	private static final String SET_LOG_LEVEL_COMMAND = "setLogLevel";
	private static final String SET_LOG_FORMAT_COMMAND = "setLogFormat";
	private static final String SET_OUT_LEVEL_COMMAND = "setOutLevel";
	
	private static ThreadGroup rootThreadGroup = null;
	static Thread[] getThreads() {
		Thread[] threads = new Thread[128];
		int threadCount = rootThreadGroup.enumerate(threads, true);
		while (threadCount == threads.length) {
			threads = new Thread[threads.length * 2];
			threadCount = rootThreadGroup.enumerate(threads, true);
		}
		return Arrays.copyOf(threads, threadCount);
	}
	static Thread findThreads(String threadName) {
		Thread[] threads = getThreads();
		for (int t = 0; t < threads.length; t++) {
			if (threadName.equals(threads[t].getName()))
				return threads[t];
		}
		return null;
	}
	
	private static ComponentActionConsole[] getLocalConsoleActions() {
		rootThreadGroup = Thread.currentThread().getThreadGroup(); // construction is called from main method ... doesn't get any more root than that
		
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
				return LIST_COMPONENTS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						LIST_COMPONENTS_COMMAND,
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
						for (int c = 0; c < serverComponentLoadErrors.length; c++) {
//							this.reportResult("  " + serverComponentLoadErrors[c]);
							this.reportResult("  " + serverComponentLoadErrors[c].phase + " of " + serverComponentLoadErrors[c].className + ":");
							this.reportResult("    " + serverComponentLoadErrors[c].error.getClass().getName() + ": " + serverComponentLoadErrors[c].error.getMessage());
						}
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
				return LIST_ACTIONS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						LIST_ACTIONS_COMMAND + " <details> <trace>",
						"Output status of currently running actions:",
						"<details>: set to '-d' to include status details of executing threads (optional)",
						"<trace>: set to '-t' to include stack traces of executing threads (optional)",
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0)
					this.listActions(false, false);
				else if (arguments.length < 3) {
					boolean details = "-d".equals(arguments[0]);
					boolean trace = "-t".equals(arguments[0]);
					if (arguments.length == 2) {
						details = (details || "-d".equals(arguments[1]));
						trace = (trace || "-t".equals(arguments[1]));
					}
					this.listActions(details, trace);
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify at most the detail and trace flags.");
			}
			private void listActions(boolean details, boolean trace) {
				synchronized (activeServiceActions) {
					this.reportResult("There are currently " + activeServiceActions.size() + " active actions:");
					for (Iterator sait = activeServiceActions.iterator(); sait.hasNext();) {
						ServiceAction sa = ((ServiceAction) sait.next());
						this.reportResult(sa.toString(" - "));
						if (details)
							sa.printDetails(this);
						if (trace)
							sa.dumpStack(this);
					}
				}
			}
		};
		cal.add(ca);
		
		//	list all active threads
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return LIST_THREADS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						LIST_THREADS_COMMAND,
						"List all active threads."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					Thread[] threads = getThreads();
					this.reportResult("These are the currently active threads:");
					for (int t = 0; t < threads.length; t++)
						this.reportResult(" - " + threads[t].getName() + " (" + threads[t].getState() + ", " + threads[t].getClass().getName() + ")");
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	list all active thread groups
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return LIST_THREAD_GROUPS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						LIST_THREAD_GROUPS_COMMAND,
						"List all active thread groups."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					ThreadGroup[] tgs = new ThreadGroup[16];
					int tgc = rootThreadGroup.enumerate(tgs, true);
					while (tgc == tgs.length) {
						tgs = new ThreadGroup[tgs.length * 2];
						tgc = rootThreadGroup.enumerate(tgs, true);
					}
					this.reportResult("These are the currently active thread groups:");
					for (int g = 0; g < tgc; g++)
						this.reportResult(" - " + tgs[g].getName() + " (" + tgs[g].activeCount() + " threads)");
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	print stack of individual thread
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return THREAD_STACK_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						THREAD_STACK_COMMAND + " <threadName>",
						"Print the stack of a specific thread:",
						"- <threadName>: the name of the thread whose stack to print"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1) {
					Thread thread = findThreads(arguments[0]);
					if (thread == null)
						this.reportError("Invalid thread name '" + arguments[0] + "'");
					else {
						this.reportResult("Thread " + thread.getName() + " (" + thread.getState() + ", " + thread.getClass().getName() + ")");
						StackTraceElement[] stes = thread.getStackTrace();
						for (int e = 0; e < stes.length; e++)
							this.reportResult("   " + stes[e].toString());
					}
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the thread name as the only argument.");
			}
		};
		cal.add(ca);
		
		//	wake up an individual thread
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return WAKE_THREAD_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						WAKE_THREAD_COMMAND + " <threadName>",
						"Wake up a specific bloced or waiting thread (use with extreme care):",
						"- <threadName>: the name of the thread to wake up"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1) {
					Thread thread = findThreads(arguments[0]);
					if (thread == null)
						this.reportError("Invalid thread name '" + arguments[0] + "'");
					else {
						Thread.State ts = thread.getState();
						if (ts == Thread.State.NEW)
							this.reportError("Thread '" + arguments[0] + "' is not started, cannot wake it");
						else if (ts == Thread.State.RUNNABLE)
							this.reportError("Thread '" + arguments[0] + "' is running, no use waking it");
						else if (ts == Thread.State.TERMINATED)
							this.reportError("Thread '" + arguments[0] + "' is terminated, cannot wake it any more");
						else {
							thread.interrupt();
							this.reportResult("Thread '" + thread.getName() + "' woken up");
						}
					}
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the thread name as the only argument.");
			}
		};
		cal.add(ca);
		
		//	kill an individual thread
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return KILL_THREAD_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						KILL_THREAD_COMMAND + " <threadName>",
						"Kill a specific thread (use with extreme care):",
						"- <threadName>: the name of the thread to kill"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1) {
					Thread thread = findThreads(arguments[0]);
					if (thread == null)
						this.reportError("Invalid thread name '" + arguments[0] + "'");
					else {
						Thread.State ts = thread.getState();
						if (ts == Thread.State.NEW)
							this.reportError("Thread '" + arguments[0] + "' is not started, cannot kill it");
						else if (ts == Thread.State.TERMINATED)
							this.reportError("Thread '" + arguments[0] + "' is terminated, no use killing it");
						else {
							if (thread instanceof ServiceThread) try {
								((ServiceThread) thread).cancelRequest();
							}
							catch (Exception e) {
								this.reportError("Error canceling request handled by thread '" + thread.getName() + "': " + e.getMessage());
								this.reportError(e);
							}
							/* Need to use Thread.stop() here despite all its
							 * implications and drawbacks ... only way of
							 * getting a thread suspended in a deadlock
							 * situation to exist and allow the other one to
							 * acquire the deadlocking monitor and continue */
							thread.stop();
							this.reportResult("Thread '" + thread.getName() + "' killed");
						}
					}
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the thread name as the only argument.");
			}
		};
		cal.add(ca);
		
		//	list all asynchronous work queues and their status
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return LIST_QUEUES_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						LIST_QUEUES_COMMAND,
						"List all asynchronous background work queues."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					this.reportResult("These are the currently active background work queues:");
					AsynchronousWorkQueue.listInstances(" - ", this);
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	show memory status
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return SHOW_MEMORY_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						(SHOW_MEMORY_COMMAND + " <runGarbageCollection>"),
						"Show memory usage:",
						"- <runGarbageCollection>: set to '-gc' to run garbage collection beforehand"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if ((arguments.length == 0) || ((arguments.length == 1) && "-gc".equals(arguments[0]))) {
					if ((arguments.length == 1) && "-gc".equals(arguments[0]))
						System.gc();
					long sMaxMem = (startupMaxMemory / (1024 * 1024));
					long sFreeMem = (startupFreeMemory / (1024 * 1024));
					this.reportResult("Startup memory usage: " + sFreeMem + " free of " + sMaxMem + " MB total (" + startupFreeMemory + " of " + startupMaxMemory + " bytes)");
					long maxMemory = Runtime.getRuntime().maxMemory();
					long freeMemory = Runtime.getRuntime().freeMemory();
					long maxMem = (maxMemory / (1024 * 1024));
					long freeMem = (freeMemory / (1024 * 1024));
					this.reportResult("Current memory usage: " + freeMem + " free of " + maxMem + " MB total (" + freeMemory + " of " + maxMemory + " bytes)");
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify at more the run-garbage-collection arguments.");
			}
		};
		cal.add(ca);
		
		//	show memory status
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return SHOW_LOGGERS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						(SHOW_LOGGERS_COMMAND + " <shrinkBuffers>"),
						"Show log buffer status:",
						"- <shrinkBuffers>: set to '-s' to shrink all buffers"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if ((arguments.length == 0) || ((arguments.length == 1) && "-s".equals(arguments[0]))) {
					logOut.showStatus(this);
					if (consoleOut != null)
						consoleOut.showStatus(this);
//					this.reportResult("Startup memory usage: " + sFreeMem + " free of " + sMaxMem + " MB total (" + startupFreeMemory + " of " + startupMaxMemory + " bytes)");
//					this.reportResult("Current memory usage: " + freeMem + " free of " + maxMem + " MB total (" + freeMemory + " of " + maxMemory + " bytes)");
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify at more the run-garbage-collection arguments.");
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
						"  (optional, omitting name and value lists current log levels)",
						"- <level>: the debug level ('d' for debug, 'i' for info, 'w' for warning, 'e' for error, and 'o' for off)",
						"  (optional, must be omitted if target is omitted)",
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				
				//	list all three log levels
				if (arguments.length == 0) {
					this.reportResult("Log levels currently are:");
					this.reportResult("- console: " + getLogLevelName(logLevelConsole));
					this.reportResult("- network: " + getLogLevelName(logLevelNetwork));
					this.reportResult("- background: " + getLogLevelName(logLevelBackground));
				}
				
				//	set log level
				else if (arguments.length == 2) {
					int logLevel = getLogLevel(arguments[1]);
					if (logLevel == -1) {
						this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', log level has to be either one of 'd' (debug), 'i' (info), 'w' (warning), 'e' (error), or 'o' (off).");
						return;
					}
					if ("c".equals(arguments[0])) {
						logLevelConsole = logLevel;
						this.reportResult(" Console log level set to " + getLogLevelName(logLevel));
					}
					else if ("n".equals(arguments[0])) {
						logLevelNetwork = logLevel;
						this.reportResult(" Network log level set to " + getLogLevelName(logLevel));
					}
					else if ("b".equals(arguments[0])) {
						logLevelBackground = logLevel;
						this.reportResult(" Background log level set to " + getLogLevelName(logLevel));
					}
					else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', log level can only be set for either one of 'c' (console), 'n' (network), or 'b' (background).");
				}
				
				//	error
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the target and log level only.");
			}
		};
		cal.add(ca);
		
		//	add action for activating and deactivating log formatting
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return SET_LOG_FORMAT_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						(SET_LOG_FORMAT_COMMAND + " <status>"),
						"Show or set log formatting status:",
						"- <status>: the log formatting status to set ('f' for formatted, 'p' for plain)",
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				
				//	list current log formatting status
				if (arguments.length == 0) {
					this.reportResult("Log formatting currently is " + (formatLogs ? "formatted" : "plain"));
				}
				
				//	set log formatting status
				else if (arguments.length == 1) {
					if ("f".equals(arguments[0])) {
						if (formatLogs)
							this.reportResult(" Already writing formatted logs");
						else {
							formatLogs = true;
							this.reportResult(" Switched to writing formatted logs");
						}
					}
					else if ("p".equals(arguments[0])) {
						if (formatLogs) {
							formatLogs = false;
							this.reportResult(" Switched to writing plain logs");
						}
						else this.reportResult(" Already writing plain logs");
					}
					else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', log formatting can only be set to 'f' for formatted or 'p' for plain.");
				}
				
				//	error
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the log formatting status at most.");
			}
		};
		cal.add(ca);
		
		//	add action for setting console output levels
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return SET_OUT_LEVEL_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						(SET_OUT_LEVEL_COMMAND + " <target> <level>"),
						"List or set output levels for console:",
						"- <target>: the target debug area ('c' for console, 'n' for network, and 'b' for background)",
						"  (optional, omitting name and value lists current output levels)",
						"- <level>: the debug level ('d' for debug, 'i' for info, 'w' for warning, 'e' for error, and 'o' for off)",
						"  (optional, must be omitted if target is omitted)",
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				
				//	list all three log levels
				if (arguments.length == 0) {
					this.reportResult("Output levels for console currently are:");
					this.reportResult("- console: " + getLogLevelName(outLevelConsole));
					this.reportResult("- network: " + getLogLevelName(outLevelNetwork));
					this.reportResult("- background: " + getLogLevelName(outLevelBackground));
				}
				
				//	set log level
				else if (arguments.length == 2) {
					int logLevel = getLogLevel(arguments[1]);
					if (logLevel == -1) {
						this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', output level has to be either one of 'd' (debug), 'i' (info), 'w' (warning), 'e' (error), or 'o' (off).");
						return;
					}
					if ("c".equals(arguments[0])) {
						outLevelConsole = logLevel;
						this.reportResult(" Console output level set to " + getLogLevelName(logLevel));
					}
					else if ("n".equals(arguments[0])) {
						outLevelNetwork = logLevel;
						this.reportResult(" Network output level set to " + getLogLevelName(logLevel));
					}
					else if ("b".equals(arguments[0])) {
						outLevelBackground = logLevel;
						this.reportResult(" Background output level set to " + getLogLevelName(logLevel));
					}
					else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', output level can only be set for either one of 'c' (console), 'n' (network), or 'b' (background).");
				}
				
				//	error
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the target and output level.");
			}
		};
		cal.add(ca);
		
		//	finally ...
		return ((ComponentActionConsole[]) cal.toArray(new ComponentActionConsole[cal.size()]));
	}
	
	private static int getLogLevel(String logLevel) {
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
	
	private static String getLogLevelName(int logLevel) {
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
	
	private static abstract class NetworkMonitoringAction extends ComponentActionNetwork {
		final String actionCommand;
		NetworkMonitoringAction(String actionCommand) {
			this.actionCommand = actionCommand;
		}
		public String getActionCommand() {
			return this.actionCommand;
		}
		public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
			if (serverComponentHost.isRequestProxied()) {
				output.write("Network monitoring requests cannot be proxied.");
				output.newLine();
				return;
			}
			String authNmt = input.readLine();
			if (!networkMonitoringToken.equals(authNmt)) {
				output.write("Invalid network monitoring token '" + authNmt + "'.");
				output.newLine();
				return;
			}
			this.doPerformActionNetwork(input, output);
		}
		abstract void doPerformActionNetwork(BufferedReader input, BufferedWriter output) throws IOException;
	}
	
	private static ComponentActionNetwork[] getNetworkMonitoringActions() {
		ArrayList cal = new ArrayList();
		ComponentActionNetwork ca;
		
		//	ping component server
		ca = new NetworkMonitoringAction(NETWORK_MONITOR_PING) {
			void doPerformActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				output.write(this.getActionCommand());
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	list components
		ca = new NetworkMonitoringAction(NETWORK_MONITOR_LIST_COMPONENTS) {
			void doPerformActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				output.write(this.getActionCommand());
				output.newLine();
				output.write(serverComponents.length + " components currently plugged into GoldenGATE Server:");
				output.newLine();
				for (int c = 0; c < serverComponents.length; c++) {
					output.write("  " + serverComponents[c].getLetterCode() + ": " + serverComponents[c].getClass().getName());
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	list component load errors
		ca = new NetworkMonitoringAction(NETWORK_MONITOR_LIST_ERRORS) {
			void doPerformActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				output.write(this.getActionCommand());
				output.newLine();
				if (serverComponentLoadErrors.length == 0) {
					output.write(serverComponentLoadErrors.length + " errors occurred while loading server components.");
					output.newLine();
				}
				else {
					output.write(serverComponentLoadErrors.length + " errors occurred while loading server components:");
					output.newLine();
					for (int c = 0; c < serverComponentLoadErrors.length; c++) {
						output.write("  " + serverComponentLoadErrors[c].phase + " of " + serverComponentLoadErrors[c].className + ":");
						output.newLine();
						output.write("    " + serverComponentLoadErrors[c].error.getClass().getName() + ": " + serverComponentLoadErrors[c].error.getMessage());
						output.newLine();
					}
				}
			}
		};
		cal.add(ca);
		
		//	show current size of thread pool
		ca = new NetworkMonitoringAction(NETWORK_MONITOR_POOL_SIZE) {
			void doPerformActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				output.write(this.getActionCommand());
				output.newLine();
				output.write(serviceThreadList.size() + " service threads overall");
				output.newLine();
				output.write(serviceThreadQueue.size() + " service threads idle");
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	show current size of thread pool
		ca = new NetworkMonitoringAction(NETWORK_MONITOR_LIST_ACTIONS) {
			void doPerformActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				output.write(this.getActionCommand());
				output.newLine();
				synchronized (activeServiceActions) {
					output.write(activeServiceActions.size() + " actions are currently active:");
					output.newLine();
					for (Iterator sait = activeServiceActions.iterator(); sait.hasNext();) {
						ServiceAction sa = ((ServiceAction) sait.next());
						output.write(sa.toString("  "));
						output.newLine();
					}
				}
			}
		};
		cal.add(ca);
		
		//	list all active threads
		ca = new NetworkMonitoringAction(NETWORK_MONITOR_LIST_THREADS) {
			void doPerformActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				output.write(this.getActionCommand());
				output.newLine();
				Thread[] threads = getThreads();
				output.write(threads.length + " threads are the currently active:");
				output.newLine();
				for (int t = 0; t < threads.length; t++) {
					output.write("  " + threads[t].getName() + " (" + threads[t].getState() + ", " + threads[t].getClass().getName() + ")");
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	list all active thread groups
		ca = new NetworkMonitoringAction(NETWORK_MONITOR_LIST_THREAD_GROUPS) {
			void doPerformActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				output.write(this.getActionCommand());
				output.newLine();
				ThreadGroup[] tgs = new ThreadGroup[16];
				int tgc = rootThreadGroup.enumerate(tgs, true);
				while (tgc == tgs.length) {
					tgs = new ThreadGroup[tgs.length * 2];
					tgc = rootThreadGroup.enumerate(tgs, true);
				}
				output.write(tgc + " thread groups are currently active:");
				output.newLine();
				for (int g = 0; g < tgc; g++) {
					output.write("  " + tgs[g].getName() + " (" + tgs[g].activeCount() + " threads)");
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	list all asynchronous work queues and their status
		ca = new NetworkMonitoringAction(NETWORK_MONITOR_LIST_QUEUES) {
			void doPerformActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				output.write(this.getActionCommand());
				output.newLine();
				output.write(AsynchronousWorkQueue.getInstanceCount() + " background work queues are currently active:");
				output.newLine();
				AsynchronousWorkQueue.listInstances("  ", output);
			}
		};
		cal.add(ca);
		
		//	finally ...
		return ((ComponentActionNetwork[]) cal.toArray(new ComponentActionNetwork[cal.size()]));
	}
	
//	private static Map componentActionSetsByLetterCode = Collections.synchronizedMap(new HashMap());
	private static Map componentActionSetsByLetterCode = Collections.synchronizedMap(new TreeMap(String.CASE_INSENSITIVE_ORDER));
	
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
		
		abstract void send(String message, int level, char source);
		
		abstract void send(Throwable error, char source);
		
		abstract void close();
		
		void executeCommand(String commandString) throws Exception {
			String[] commandTokens = parseCommand(commandString);
			if (commandTokens.length != 0)
				this.executeCommand(commandTokens);
		}
		
		void executeCommand(String[] commandTokens) throws Exception {
			
			//	setting letter code
			if (CHANGE_COMPONENT_COMMAND.equals(commandTokens[0]))
				this.currentLetterCode = ((commandTokens.length == 1) ? "" : commandTokens[1]);
			
			//	help
			else if (HELP_COMMAND.equals(commandTokens[0])) {
				
				//	global help
				if (this.currentLetterCode.length() == 0) {
					this.send(HELP_COMMAND, -1, 'C');
					this.send("  Display this list of commands.", -1, 'C');
					this.send((CHANGE_COMPONENT_COMMAND + " <letterCode>"), -1, 'C');
					this.send("  Set the component letter code so subsequent commands automatically go to a specific server component:", -1, 'C');
					this.send("  - <letterCode>: the new component letter code (use '" + LIST_COMPONENTS_COMMAND + "' for a list of components and letter codes)", -1, 'C');
					
					ArrayList letterCodeList = new ArrayList(componentActionSetsByLetterCode.keySet());
					Collections.sort(letterCodeList);
					for (int c = 0; c < letterCodeList.size(); c++) {
						String letterCode = letterCodeList.get(c).toString();
						
						Map componentActionSet = ((Map) componentActionSetsByLetterCode.get(letterCode));
						if (componentActionSet == null)
							continue;
						
						ArrayList actionNameList = new ArrayList(componentActionSet.keySet());
						if (actionNameList.isEmpty())
							continue;
						
						Collections.sort(actionNameList);
						this.send("", -1, 'C');
						this.send(("Commands with prefix '" + letterCode + "':"), -1, 'C');
						
						for (int a = 0; a < actionNameList.size(); a++) {
							ComponentActionConsole action = ((ComponentActionConsole) componentActionSet.get(actionNameList.get(a)));
							
							String[] explanation = action.getExplanation();
							for (int e = 0; e < explanation.length; e++)
								this.send(("  " + ((e == 0) ? "" : "  ") + explanation[e]), -1, 'C');
						}
					}
				}
				
				//	help for given letter code
				else {
					Map componentActionSet = ((Map) componentActionSetsByLetterCode.get(this.currentLetterCode));
					if (componentActionSet == null)
						return;
					
					ArrayList actionNameList = new ArrayList(componentActionSet.keySet());
					if (actionNameList.isEmpty())
						return;
					
					Collections.sort(actionNameList);
					this.send("", -1, 'C');
					this.send(("Commands with prefix '" + this.currentLetterCode + "':"), -1, 'C');
					
					for (int a = 0; a < actionNameList.size(); a++) {
						ComponentActionConsole action = ((ComponentActionConsole) componentActionSet.get(actionNameList.get(a)));
						
						String[] explanation = action.getExplanation();
						for (int e = 0; e < explanation.length; e++)
							this.send(("  " + ((e == 0) ? "" : "  ") + explanation[e]), -1, 'C');
					}
				}
			}
			
			//	other command
			else {
				
				//	identify action
				ComponentActionConsole action = getActionForCommand(commandTokens[0]);
				
				//	action not found
				if (action == null)
					this.send((" Unknown command '" + commandTokens[0] + "', use '" + HELP_COMMAND + "' to list available commands"), -1, 'C');
				
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
		
		public void logError(String message) {
			GoldenGateServer.logConsole(message, LOG_LEVEL_ERROR);
			this.send(message, LOG_LEVEL_ERROR, 'C');
		}
		public void logError(Throwable error) {
			GoldenGateServer.logConsole(error);
			this.send(error, 'C');
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
			GoldenGateServer.logConsole(message, messageLogLevel);
			this.send(message, messageLogLevel, 'C');
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
			
			Map componentActionSet = ((Map) componentActionSetsByLetterCode.get(letterCode));
			if (componentActionSet == null)
				return null;
			return ((ComponentActionConsole) componentActionSet.get(actionName));
		}
		
		private static final char ESCAPER = '\\';
		private static final char QUOTER = '"';
		
		private static String[] parseCommand(String command) {
			if (command == null) return new String[0];
			
			String commandString = command.trim();
			if (commandString.length() == 0)
				return new String[0];
			
			StringVector commandTokenCollector = new StringVector();
			StringBuffer commandToken = new StringBuffer();
			
			char ch;
			int escapeIndex = -1;
			boolean inQuotes = false;
			
			for (int c = 0; c < commandString.length(); c++) {
				ch = commandString.charAt(c);
				if (c == escapeIndex)
					commandToken.append(ch);
				else if (ch == ESCAPER)
					escapeIndex = (c+1);
				else if (inQuotes) {
					if (ch == QUOTER)
						inQuotes = false;
					else commandToken.append(ch);
				}
				else if (ch == QUOTER)
					inQuotes = true;
				else if (ch < 33) {
					if (commandToken.length() != 0) {
						commandTokenCollector.addElement(commandToken.toString());
						commandToken = new StringBuffer();
					}
				}
				else commandToken.append(ch);
			}
			
			if (commandToken.length() != 0)
				commandTokenCollector.addElement(commandToken.toString());
			return commandTokenCollector.toStringArray();
		}
		
		static String getLetterCodeString() {
			ArrayList letterCodeList = new ArrayList(componentActionSetsByLetterCode.keySet());
			String[] letterCodes = ((String[]) letterCodeList.toArray(new String[letterCodeList.size()]));
			Arrays.sort(letterCodes);
			StringBuffer letterCodeString = new StringBuffer();
			for (int c = 0; c < letterCodes.length; c++) {
				letterCodeString.append(" ");
				letterCodeString.append(letterCodes[c]);
			}
			return letterCodeString.toString();
		}
	}
	
	private static class SystemInConsole extends ComponentServerConsole {
		private BufferedReader commandReader;
		
		SystemInConsole(PrintStream out) {
			this.out = out;
			this.commandReader = new BufferedReader(new InputStreamReader(System.in));
		}
		
		void send(String message, int level, char source) {
			this.out.println(message);
		}
		
		void send(Throwable error, char source) {
			error.printStackTrace(this.out);
		}
		
		void close() {
			try {
				this.commandReader.close();
			} catch (IOException ioe) {}
		}
		
		public void run() {
			while (true) try {
				this.out.print("GgServer" + ((this.currentLetterCode.length() == 0) ? "" : ("." + this.currentLetterCode)) + ">");
				String commandString = this.commandReader.readLine();
				if (commandString == null)
					return;
				else this.executeCommand(commandString.trim());
			}
			catch (Exception e) {
				this.out.println("Error reading or executing console command: " + e.getMessage());
			}
		}
	}
	
	private static class SocketConsole extends ComponentServerConsole {
		private static final Object consoleOutLock = new Object();
		private LogStream resultOut;
		
		private int port;
		private String password;
		private ServerSocket serverSocket;
		private Socket activeSocket;
		private LoggingThread activeThread;
		private int timeout;
		
		SocketConsole(int port, String password, int timeout) {
			this.port = port;
			this.password = password;
			this.timeout = timeout;
			this.resultOut = new LogStream(new RedirectPrintStream() {
				void redirectLine(String line) {
					doSend(line);
				}
			});
		}
		
		void executeCommand(String[] commandTokens) throws Exception {
			if (EXIT_COMMAND.equals(commandTokens[0]) || ("." + EXIT_COMMAND).equals(commandTokens[0]))
				this.send((" Invalid command '" + commandTokens[0] + "', cannot shut down server via console."), LOG_LEVEL_ERROR, 'C');
			else super.executeCommand(commandTokens);
		}
		
		//	TODOne get rid of locking here, this is called by many threads
		void send(String message, int level, char source) {
			if (this.out == null)
				return;
//			synchronized (consoleOutLock) {
//				if (this.out == null)
//					return; // re-check after getting monitor on lock (under heavy load, stream can get lost while waiting on monitor)
//				char l;
//				if (level == LOG_LEVEL_DEBUG)
//					l = 'D';
//				else if (level == LOG_LEVEL_INFO)
//					l = 'I';
//				else if (level == LOG_LEVEL_WARNING)
//					l = 'W';
//				else if (level == LOG_LEVEL_ERROR)
//					l = 'E';
//				else l = 'R'; // result
//				this.out.println("" + l + source + "::" + message);
//			}
			char l;
			if (level == LOG_LEVEL_DEBUG)
				l = 'D';
			else if (level == LOG_LEVEL_INFO)
				l = 'I';
			else if (level == LOG_LEVEL_WARNING)
				l = 'W';
			else if (level == LOG_LEVEL_ERROR)
				l = 'E';
			else l = 'R'; // result
			this.resultOut.println("" + l + source + "::" + message); // no need to lock, buffering happens per thread
		}
		
		//	TODOne get rid of locking here, this is called by many threads
		void send(Throwable error, char source) {
			if (this.out == null)
				return;
//			synchronized (consoleOutLock) {
//				if (this.out == null)
//					return; // re-check after getting monitor on lock (under heavy load, stream can get lost while waiting on monitor)
//				this.out.println("" + 'T' + source + "::");
//				error.printStackTrace(this.out);
//				this.out.println("::T");
//			}
			this.resultOut.println("" + 'T' + source + "::");
			error.printStackTrace(this.resultOut);
			this.resultOut.println("::T"); // no need to lock, buffering happens per thread
		}
		
		//	TODOne synchronize here instead, this is only called by output writer thread
		void doSend(String line) {
			if (this.out == null)
				return;
			synchronized (consoleOutLock) {
				if (this.out != null) // re-check after getting monitor on lock (under heavy load, stream can get lost while waiting on monitor)
					this.out.println(line);
			}
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
				this.activeThread = null;
				as.close();
			} catch (IOException ioe) {}
			
			this.resultOut.close();
		}
		
		public void run() {
			
			//	create server socket for incoming console connections
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
			
			//	accept incoming console connections
			while (this.serverSocket != null) try {
				final BufferedReader commandReader;
				PrintStream sOut;
				
				//	wait for incoming connections
				Socket as = this.serverSocket.accept();
				boolean killActive;
				System.out.println("Network console connection from " + as.getInetAddress().getHostAddress() + ", local is " + InetAddress.getLocalHost().getHostAddress());
				if ("127.0.0.1".equals(as.getInetAddress().getHostAddress()) || as.getInetAddress().getHostAddress().equals(InetAddress.getLocalHost().getHostAddress())) {
					System.out.println("Network console connected.");
					sOut = new PrintStream(as.getOutputStream(), true);
					
					commandReader = new BufferedReader(new InputStreamReader(as.getInputStream()));
					as.setSoTimeout(500); // wait half a second for password, no longer
					String password = commandReader.readLine();
					if (password.startsWith("KILL ")) {
						killActive = true;
						password = password.substring("KILL ".length());
					}
					else killActive = false;
					if (this.password.equals(password)) {
						if (killActive == (this.activeSocket != null))
							sOut.println("WELCOME" + getLetterCodeString());
						else if (killActive) {
							System.out.println("Network console user unnecessarily brutal.");
							sOut.println("DONT_KILL");
							as.close();
							throw new IOException("Network console user unnecessarily brutal");
						}
						else {
							System.out.println("Network console connection occupied.");
							sOut.println("USE_KILL");
							as.close();
							throw new IOException("Network console connection occupied");
						}
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
				
				//	kill active connection if required
				if (killActive && (this.activeSocket != null)) {
					System.out.println("Network console connected killing previous one.");
					if (this.out != null)
						synchronized (consoleOutLock) {
							this.out.println("Killed by subsequent login");
							this.out = null;
						}
					this.activeThread = null;
					this.activeSocket.close();
					this.activeSocket = null;
				}
				
				//	store active connection
				this.activeSocket = as;
				this.activeSocket.setSoTimeout(this.timeout);
				synchronized (consoleOutLock) {
					this.out = sOut;
				}
				
				//	start thread to read and execute commands
				this.activeThread = new LoggingThread("ConsoleCommandExecutor") {
					public void logError(String message) {
						SocketConsole.this.logError(message);
					}
					public void logError(Throwable error) {
						SocketConsole.this.logError(error);
					}
					public void logActivity(String message) {
						SocketConsole.this.logActivity(message);
					}
					public void logResult(String message) {
						SocketConsole.this.logResult(message);
					}
					void log(String message, int messageLogLevel) {
						SocketConsole.this.log(message, messageLogLevel);
					}
					public void run() {
						while ((SocketConsole.this.activeSocket != null) && (SocketConsole.this.activeThread == this) /* terminate soon as replaced */) try {
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
									SocketConsole.this.out.println("GOODBYE");
									SocketConsole.this.out = null;
								}
								SocketConsole.this.activeThread = null;
								SocketConsole.this.activeSocket.close();
								SocketConsole.this.activeSocket = null;
							}
							
							//	server shutdown
							else if (SocketConsole.this.activeSocket == null) {
								//	ignore it here, it's done in close()
							}
							
							//	killed by new connection
							if (SocketConsole.this.activeThread != this) {
								//	ignore it here, it's done in parent thread
							}
							
							//	client connection lost
							else if (commandString == null) {
								synchronized (consoleOutLock) {
									SocketConsole.this.out = null;
								}
								SocketConsole.this.activeThread = null;
								SocketConsole.this.activeSocket.close();
								SocketConsole.this.activeSocket = null;
							}
							
							//	regular command
							else {
								commandString = commandString.trim();
								try {
									resultOut.startConsoleBreak();
									SocketConsole.this.executeCommand(commandString);
									resultOut.endConsoleBreak();
								}
								catch (Throwable t) {
									System.out.println("Error executing network console command: " + t.getMessage());
									t.printStackTrace(System.out);
								}
								synchronized (consoleOutLock) {
									SocketConsole.this.out.println("COMMAND_DONE");
								}
							}
						}
						catch (Exception e) {
							System.out.println("Error reading network console command: " + e.getMessage());
							e.printStackTrace(System.out);
						}
					}
				};
				this.resultOut.setConsoleThread(this.activeThread);
				this.activeThread.start();
			}
			catch (Exception e) {
				System.out.println("Error accepting network console connection: " + e.getMessage());
				e.printStackTrace(System.out);
			}
		}
	}
	
	private static abstract class ForkPrintStream extends PrintStream {
		private PrintStream out;
		private StringBuffer buffer = new StringBuffer();
		ForkPrintStream(PrintStream out) {
			super(new OutputStream() {
				//	stop all output from super class, we're writing to wrapped stream directly
				public void write(int b) throws IOException {}
				public void write(byte[] b) throws IOException {}
				public void write(byte[] b, int off, int len) throws IOException {}
			});
			this.out = out;
		}
		public void flush() {
			this.out.flush();
			if (this.buffer.length() != 0)
				this.forkLineWithBuffer(""); // flushes buffer
		}
		public void close() {
			this.out.close();
		}
		public void print(boolean b) {
			this.out.print(b);
			this.buffer.append(b);
		}
		public void print(char c) {
			this.out.print(c);
			this.buffer.append(c);
		}
		public void print(int i) {
			this.out.print(i);
			this.buffer.append(i);
		}
		public void print(long l) {
			this.out.print(l);
			this.buffer.append(l);
		}
		public void print(float f) {
			this.out.print(f);
			this.buffer.append(f);
		}
		public void print(double d) {
			this.out.print(d);
			this.buffer.append(d);
		}
		public void print(char[] s) {
			this.out.print(s);
			this.buffer.append(s);
		}
		public void print(String s) {
			this.out.print(s);
			this.buffer.append(s);
		}
		public void print(Object obj) {
			this.out.print(obj);
			this.buffer.append(obj);
		}
		public void println() {
			this.out.println();
			if (this.buffer.length() != 0)
				this.forkLineWithBuffer(""); // flushes buffer
		}
		public void println(boolean b) {
			this.out.println(b);
			this.forkLineWithBuffer(String.valueOf(b));
		}
		public void println(char c) {
			this.out.println(c);
			this.forkLineWithBuffer(String.valueOf(c));
		}
		public void println(int i) {
			this.out.println(i);
			this.forkLineWithBuffer(String.valueOf(i));
		}
		public void println(long l) {
			this.out.println(l);
			this.forkLineWithBuffer(String.valueOf(l));
		}
		public void println(float f) {
			this.out.println(f);
			this.forkLineWithBuffer(String.valueOf(f));
		}
		public void println(double d) {
			this.out.println(d);
			this.forkLineWithBuffer(String.valueOf(d));
		}
		public void println(char[] s) {
			this.out.println(s);
			this.buffer.append(s);
			this.forkLineWithBuffer(""); // saves us turning array into string for handover
		}
		public void println(String s) {
			this.out.println(s);
			this.forkLineWithBuffer(s);
		}
		public void println(Object obj) {
			this.out.println(obj);
			this.forkLineWithBuffer(String.valueOf(obj));
		}
		
		private void forkLineWithBuffer(String s) {
			if (this.buffer.length() == 0)
				this.forkLine(s);
			else {
				this.buffer.append(s);
				this.forkLine(this.buffer.toString());
				this.buffer.delete(0, this.buffer.length());
			}
		}
		
		public PrintStream append(CharSequence csq) {
			this.out.append(csq);
			this.buffer.append(csq);
			return this;
		}
		public PrintStream append(CharSequence csq, int start, int end) {
			this.out.append(csq, start, end);
			this.buffer.append(csq, start, end);
			return this;
		}
		public PrintStream append(char c) {
			this.out.append(c);
			this.buffer.append(c);
			return this;
		}
		
//		THESE FOUR COME BACK TO US (via append() methods)
//		public PrintStream printf(String format, Object... args) {
//			return super.printf(format, args);
//		}
//		public PrintStream printf(Locale l, String format, Object... args) {
//			return super.printf(l, format, args);
//		}
//		public PrintStream format(String format, Object... args) {
//			return super.format(format, args);
//		}
//		public PrintStream format(Locale l, String format, Object... args) {
//			return super.format(l, format, args);
//		}
		abstract void forkLine(String s);
	}
	
	private static abstract class RedirectPrintStream extends PrintStream {
		private StringBuffer buffer = new StringBuffer();
		RedirectPrintStream() {
			super(new OutputStream() {
				//	stop all output from super class, we're writing to wrapped stream directly
				public void write(int b) throws IOException {}
				public void write(byte[] b) throws IOException {}
				public void write(byte[] b, int off, int len) throws IOException {}
			});
		}
		public void flush() {
			if (this.buffer.length() != 0)
				this.redirectLineWithBuffer(""); // flushes buffer
		}
		public void close() {}
		public void print(boolean b) {
			this.buffer.append(b);
		}
		public void print(char c) {
			this.buffer.append(c);
		}
		public void print(int i) {
			this.buffer.append(i);
		}
		public void print(long l) {
			this.buffer.append(l);
		}
		public void print(float f) {
			this.buffer.append(f);
		}
		public void print(double d) {
			this.buffer.append(d);
		}
		public void print(char[] s) {
			this.buffer.append(s);
		}
		public void print(String s) {
			this.buffer.append(s);
		}
		public void print(Object obj) {
			this.buffer.append(obj);
		}
		public void println() {
			if (this.buffer.length() != 0)
				this.redirectLineWithBuffer(""); // flushes buffer
		}
		public void println(boolean b) {
			this.redirectLineWithBuffer(String.valueOf(b));
		}
		public void println(char c) {
			this.redirectLineWithBuffer(String.valueOf(c));
		}
		public void println(int i) {
			this.redirectLineWithBuffer(String.valueOf(i));
		}
		public void println(long l) {
			this.redirectLineWithBuffer(String.valueOf(l));
		}
		public void println(float f) {
			this.redirectLineWithBuffer(String.valueOf(f));
		}
		public void println(double d) {
			this.redirectLineWithBuffer(String.valueOf(d));
		}
		public void println(char[] s) {
			this.buffer.append(s);
			this.redirectLineWithBuffer(""); // saves us turning array into string for handover
		}
		public void println(String s) {
			this.redirectLineWithBuffer(s);
		}
		public void println(Object obj) {
			this.redirectLineWithBuffer(String.valueOf(obj));
		}
		
		private void redirectLineWithBuffer(String s) {
			if (this.buffer.length() == 0)
				this.redirectLine(s);
			else {
				this.buffer.append(s);
				this.redirectLine(this.buffer.toString());
				this.buffer.delete(0, this.buffer.length());
			}
		}
		
		public PrintStream append(CharSequence csq) {
			this.buffer.append(csq);
			return this;
		}
		public PrintStream append(CharSequence csq, int start, int end) {
			this.buffer.append(csq, start, end);
			return this;
		}
		public PrintStream append(char c) {
			this.buffer.append(c);
			return this;
		}
		
//		THESE FOUR COME BACK TO US (via append() methods)
//		public PrintStream printf(String format, Object... args) {
//			return super.printf(format, args);
//		}
//		public PrintStream printf(Locale l, String format, Object... args) {
//			return super.printf(l, format, args);
//		}
//		public PrintStream format(String format, Object... args) {
//			return super.format(format, args);
//		}
//		public PrintStream format(Locale l, String format, Object... args) {
//			return super.format(l, format, args);
//		}
		abstract void redirectLine(String line);
	}
	
	private static class LogStream extends RedirectPrintStream {
		PrintStream logOut; // this is where we actually flush to
		LogWriter logWriter; // this is the thread responsible for flushing
		LogStream(PrintStream logOut) {
			this.logOut = logOut;
			this.logWriter = new LogWriter();
			this.logWriter.start();
		}
		public void close() {
			super.close();
			PrintStream out = this.logOut;
			this.logOut = null; // tells writer thread to exit
			this.flushBuffers(out); // flush whatever might be left
			out.close(); // close underlying stream
		}
		
		Thread consoleThread = null; // console thread, to facilitate writing console action results directly
		void setConsoleThread(Thread consoleThread) {
			this.consoleThread = consoleThread;
		}
		
		final Object consoleBreakLock = new Object();
		boolean consoleBreak = false;
		void startConsoleBreak() {
			this.consoleBreak = true;
		}
		void endConsoleBreak() {
			this.consoleBreak = false;
			synchronized (this.consoleBreakLock) {
				this.consoleBreakLock.notify();
			}
		}
		
		//	this gets called by all the writing threads
		private ThreadLocal threadBuffer = new ThreadLocal();
		void redirectLine(String line) {
			Thread ct = Thread.currentThread();
			if (ct == this.logWriter) /* no buffering for log writer proper */ {
				this.logOut.println(line);
				return;
			}
			if (ct == this.consoleThread) /* no buffering for console response writer proper in console mode */ {
				this.logOut.println(line);
				return;
			}
			LogBuffer lb = ((LogBuffer) this.threadBuffer.get());
			if (lb == null) {
				lb = new LogBuffer();
				this.addBuffer(lb);
				this.threadBuffer.set(lb);
			}
			lb.storeLine(line);
		}
//		void shrinkBuffer() {
//			LogBuffer lb = ((LogBuffer) this.threadBuffer.get());
//			if (lb != null)
//				lb.shrinkCapacity();
//		}
		
		/* show buffers status in console (this is intentionally unsynchronized
		 * to prevent any competition or interference, at calculated risk of
		 * sometimes seeing somewhat inaccurate numbers) */
		void showStatus(ComponentActionConsole cac) {
			int count = 0;
			int sizeTotal = 0;
			int sizeMin = Integer.MAX_VALUE;
			int sizeMax = 0;
			int level = 0;
			for (int b = 0; b < this.bufferCount; b++) {
				LogBuffer buf = this.buffers[b];
				if (buf == null)
					continue; // can happen if logger thread is cleaning up
				count++;
				int size = buf.size();
				sizeTotal += size;
				sizeMin = Math.min(sizeMin, size);
				sizeMax = Math.max(sizeMax, size);
				level += buf.level();
			}
			if (count == 0)
				cac.reportResult(((this.consoleThread == null) ? "LogOut" : "ConsoleOut") + ": got no buffers thus far");
			else cac.reportResult(((this.consoleThread == null) ? "LogOut" : "ConsoleOut") + ": got " + count + " buffers holding " + level + " lines, average capacity is " + (sizeTotal / count) + " [" + sizeMin + "," + sizeMax + "]");
		}
		
		//	this thread does all the actual writing, except the final flushing
		private class LogWriter extends Thread {
			LogWriter() {
				super("LogWriter");
			}
			public void run() {
				synchronized (this) {
					this.notify(); // wake up whoever started us
				}
				PrintStream out;
				do {
					out = logOut; // need to have local copy to prevent null pointer exceptions on shutdown
					if (out != null) {
						flushBuffers(out);
//						showMemoryStatus(out, System.currentTimeMillis());
						try {
							if (consoleBreak) {
								synchronized (consoleBreakLock) {
									if (consoleBreak) // need to check again to not miss notify under any circumstance
										consoleBreakLock.wait(250); // wait at most quarter second ...
								}
								consoleBreak = false; // ... and not for all too long
							}
							else sleep(50);
						} catch (InterruptedException ie) {}
					}
				}
				while (out != null);
			}
			public synchronized void start() {
				super.start();
				try {
					this.wait(); // wait for thread to start running
				} catch (InterruptedException ie) {}
			}
		}
//		
//		//	memory monitoring (simply most convenient to put here)
//		long memoryLogDue = ((memoryLogInterval < 1) ? Long.MAX_VALUE : (System.currentTimeMillis() + (1000 * memoryLogInterval))); // simply initialize to creation time
//		private WeakReference gcIndicatorWeak = null;
//		long lastGcRunWeak = System.currentTimeMillis(); // simply initialize to creation time
//		private SoftReference gcIndicatorSoft = null;
//		long lastGcRunSoft = System.currentTimeMillis(); // simply initialize to creation time
//		private static class GcIndicator {
//			final LogStream parent;
//			final boolean weak;
//			GcIndicator(LogStream parent, boolean weak) {
//				this.parent = parent;
//				this.weak = weak;
//			}
//			protected void finalize() throws Throwable {
//				this.parent.notifyGcRunning(this.weak);
//			}
//		}
//		void notifyGcRunning(boolean weak) {
//			if (weak) {
//				this.lastGcRunWeak = System.currentTimeMillis();
//				this.gcIndicatorWeak = null;
//			}
//			else {
//				this.lastGcRunSoft = System.currentTimeMillis();
//				this.gcIndicatorSoft = null;
//			}
//		}
//		void showMemoryStatus(PrintStream out, long time) {
//			if (time < this.memoryLogDue)
//				return;
//			if (this.consoleThread != null)
//				return;
//			long maxMemory = Runtime.getRuntime().maxMemory();
//			long freeMemory = Runtime.getRuntime().freeMemory();
//			long maxMem = (maxMemory / (1024 * 1024));
//			long freeMem = (freeMemory / (1024 * 1024));
//			out.println("=== MEMORY USAGE: " + freeMem + " MB free of " + maxMem + " MB total (" + freeMemory + " of " + maxMemory + " bytes), last GC run " + (time - this.lastGcRunWeak) + "/" + (time - this.lastGcRunSoft) + "ms ago (weak/soft)");
//			this.memoryLogDue += (1000 * memoryLogInterval);
//			if (this.gcIndicatorWeak == null)
//				this.gcIndicatorWeak = new WeakReference(new GcIndicator(this, true));
//			if (this.gcIndicatorSoft == null)
//				this.gcIndicatorSoft = new SoftReference(new GcIndicator(this, false));
//		}
		
		//	this gets called exclusively by output writer thread
		private WeakReference gcIndicator = new WeakReference(new Object());
		private long lastWritten = System.currentTimeMillis(); // simply initialize to creation time
		private long freeMemoryLastWritten;
		void flushBuffers(PrintStream out) {
			long time = System.currentTimeMillis();
			if (this.gcIndicator.get() == null) {
				long freeMemory = Runtime.getRuntime().freeMemory();
				long freeMemLastWritten = (this.freeMemoryLastWritten / (1024 * 1024));
				long freeMem = (freeMemory / (1024 * 1024));
				out.println("=== GC HANGUP === (" + (time - this.lastWritten) + "ms) ===");
				out.println("=== memory usage: " + freeMem + " MB free now, " + freeMemLastWritten + " MB free before (" + freeMemory + "/" + this.freeMemoryLastWritten + " bytes) ===");
				this.gcIndicator = new WeakReference(new Object());
			}
			while (this.flushNextBuffer(out, time)) { /* keep flushing until empty */ }
			this.lastWritten = time;
			this.freeMemoryLastWritten = Runtime.getRuntime().freeMemory();
			this.cleanBuffers(out); // clean up buffers of terminated owners
		}
		private boolean flushNextBuffer(PrintStream out, long time) {
			long minTime = Long.MAX_VALUE;
			LogBuffer minBuffer = null;
			synchronized (this) {
				for (int b = 0; b < this.bufferCount; b++) {
					long bTime = this.buffers[b].nextTime();
					if (bTime < minTime) {
						minTime = bTime;
						minBuffer = this.buffers[b];
					}
				}
			}
			if (minBuffer == null)
				return false;
			while (minBuffer.nextTime() <= minTime)
				out.println(minBuffer.nextLine());
			minBuffer.shrinkCapacity();
			return true;
		}
		
		//	infrastructure holding log buffers for access on output (minimal version of array based list)
		private LogBuffer[] buffers = new LogBuffer[128];
		private int bufferCount = 0;
		private synchronized void addBuffer(LogBuffer buffer) {
			if (this.bufferCount == this.buffers.length) {
				LogBuffer[] cBuffers = new LogBuffer[this.buffers.length * 2];
				System.arraycopy(this.buffers, 0, cBuffers, 0, this.buffers.length);
				this.buffers = cBuffers;
			}
			this.buffers[this.bufferCount++] = buffer;
		}
		private synchronized void cleanBuffers(PrintStream out) {
			int cleaned = 0;
			for (int b = 0; b < this.bufferCount; b++) {
				if (!this.buffers[b].owner.isAlive()) {
					while (this.buffers[b].nextTime() != Long.MAX_VALUE)
						out.println(this.buffers[b].nextLine()); // write any remaining contents (would be shame to miss why thread died)
					cleaned++;
				}
				else if (cleaned != 0) {
					this.buffers[b - cleaned] = this.buffers[b];
					this.buffers[b] = null;
				}
			}
			this.bufferCount -= cleaned;
		}
		synchronized int bufferCount() {
			return this.bufferCount;
		}
		synchronized int bufferLevel() {
			int level = 0;
			for (int b = 0; b < this.bufferCount; b++)
				level += this.buffers[b].level();
			return level;
		}
		
		//	this stores the output per thread, so no thread needs to compete with other over IO locks
		private static class LogBuffer {
			private static final int MIN_SIZE = 64;
			private static final int MAX_PERMANENT_SIZE = 1024;
			final Thread owner;
			Thread waitingOwner = null;
			private String[] lineBuffer = new String[MIN_SIZE];
			private long[] timeBuffer = new long[MIN_SIZE];
			private int firstLine = 0;
			private int lastLine = 0;
			LogBuffer() {
				this.owner = Thread.currentThread();
			}
			
			int size() {
				return this.lineBuffer.length;
			}
			synchronized int level() {
				return (this.lastLine - this.firstLine);
			}
			synchronized long nextTime() {
				return ((this.firstLine < this.lastLine) ? this.timeBuffer[this.firstLine] : Long.MAX_VALUE);
			}
			synchronized String nextLine() {
				return ((this.firstLine < this.lastLine) ? this.lineBuffer[this.firstLine++] : null);
			}
			
			synchronized void storeLine(String line) {
				this.ensureCapacity();
				this.lineBuffer[this.lastLine] = line;
				this.timeBuffer[this.lastLine] = System.currentTimeMillis();
				this.lastLine++;
//				if (((this.lastLine - this.firstLine) % 256) == 0)
				if (((this.lastLine - this.firstLine) & 0x000000FF) == 0)
					Thread.yield(); // give the logger a chance to work off the load
//				if (((this.lastLine - this.firstLine) % 1024) == 0)
				if (((this.lastLine - this.firstLine) & 0x000003FF /* maximum permanent size less 1 */) == 0) try {
					this.waitingOwner = this.owner;
					this.wait(50); // really give the logger a chance to work off the load
					this.waitingOwner = null;
				} catch (InterruptedException ie) {}
			}
			private void ensureCapacity() {
				
				//	we're empty, move to front
				if (this.firstLine == this.lastLine) {
					this.firstLine = 0;
					this.lastLine = 0;
				}
				
				//	we still got space
				if (this.lastLine < this.lineBuffer.length)
					return;
				
				//	shift contents to front
				if (this.firstLine != 0) {
					System.arraycopy(this.lineBuffer, this.firstLine, this.lineBuffer, 0, (this.lineBuffer.length - this.firstLine));
					System.arraycopy(this.timeBuffer, this.firstLine, this.timeBuffer, 0, (this.timeBuffer.length - this.firstLine));
					this.lastLine -= this.firstLine;
					this.firstLine = 0;
					return;
				}
				
				//	double up buffers to create space
				String[] cLineBuffer = new String[this.lineBuffer.length * 2];
				System.arraycopy(this.lineBuffer, this.firstLine, cLineBuffer, 0, (this.lineBuffer.length - this.firstLine));
				long[] cTimeBuffer = new long[this.timeBuffer.length * 2];
				System.arraycopy(this.timeBuffer, this.firstLine, cTimeBuffer, 0, (this.timeBuffer.length - this.firstLine));
				this.lineBuffer = cLineBuffer;
				this.timeBuffer = cTimeBuffer;
				this.lastLine -= this.firstLine;
				this.firstLine = 0;
			}
			synchronized void shrinkCapacity() {
				if (this.waitingOwner != null)
					this.notify(); // wake up suspended owner
				if (this.lineBuffer.length < MAX_PERMANENT_SIZE)
					return; // still in bounds
				int level = this.level();
				if (this.lineBuffer.length < (level * 2))
					return; // too full to shrink
				if (MAX_PERMANENT_SIZE <= level)
					return; // still too full to shrink
				
				//	compute reduced size (lowest power of two accommodating current content)
				int sSize = MAX_PERMANENT_SIZE;
				while (((level * 2) < sSize) && (MIN_SIZE < sSize))
					sSize /= 2;
				
				//	shrink buffers
				String[] sLineBuffer = new String[sSize];
				System.arraycopy(this.lineBuffer, this.firstLine, sLineBuffer, 0, level);
				long[] sTimeBuffer = new long[sSize];
				System.arraycopy(this.timeBuffer, this.firstLine, sTimeBuffer, 0, level);
				this.lineBuffer = sLineBuffer;
				this.timeBuffer = sTimeBuffer;
				this.lastLine -= this.firstLine;
				this.firstLine = 0;
			}
		}
	}
//	
//	//	FOR TESTING ONLY !!!
//	private static class TestHelper {
//		public static void main(String[] args) throws Exception {
//			final LogStream ls = new LogStream(System.out);
//			Thread[] threads = new Thread[100];
//			final int[] round = {0};
//			for (int t = 0; t < threads.length; t++) {
//				threads[t] = new Thread() {
//					public void run() {
//						int r;
//						do {
//							synchronized (round) {
//								r = round[0]++;
//							}
//							ls.println("Thread is " + Thread.currentThread().getName());
//							ls.println("Time is " + System.currentTimeMillis());
//							try {
//								Thread.sleep(10);
//							} catch (InterruptedException ie) {}
//							ls.println("Round " + r + " done");
//						}
//						while (r < 10000);
//					}
//				};
//				threads[t].start();
//			}
//			for (int t = 0; t < threads.length; t++) try {
//				threads[t].join();
//			} catch (InterruptedException ie) {}
//			System.out.println("Got " + ls.bufferCount + " buffers");
//			for (int b = 0; b < ls.bufferCount; b++)
//				System.out.println(ls.buffers[b].owner.getName() + ": " + ls.buffers[b].lineBuffer.length);
//			ls.flush();
//			ls.println("All done");
//			ls.close();
//			System.out.println("Got " + ls.bufferCount + " buffers");
//			for (int b = 0; b < ls.bufferCount; b++)
//				System.out.println(ls.buffers[b].owner.getName() + ": " + ls.buffers[b].lineBuffer.length);
//		}
//	}
}
