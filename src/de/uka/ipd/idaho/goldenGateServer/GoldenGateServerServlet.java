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


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.uka.ipd.idaho.easyIO.EasyIO;
import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent.ComponentAction;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent.ComponentActionNetwork;
import de.uka.ipd.idaho.goldenGateServer.util.BufferedLineInputStream;
import de.uka.ipd.idaho.goldenGateServer.util.BufferedLineOutputStream;

/**
 * A servlet for exposing a GoldenGateServerComponent via HTTP from inside a
 * servlet container.
 * 
 * @author sautter
 */
public class GoldenGateServerServlet extends HttpServlet implements GoldenGateServerConstants, GoldenGateServerComponentHost {
	
	/**
	 * The name of the servlet's init parameter to specify the root path. If
	 * this parameter is not specified, the servlet will use the web
	 * application's default root path.
	 */
	public static final String ROOT_PATH_PARAMETER_NAME = "rootPath";
	
	private GoldenGateServerComponent[] serverComponents;
	private HashMap serverComponentActions = new HashMap();
	private HashSet clientRequestThreadIDs = new HashSet();
	
	private Settings ioProviderSettings = new Settings();
	private Settings environmentSettings = new Settings();
	
	private int logLevel = GoldenGateServerActivityLogger.LOG_LEVEL_WARNING;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponentHost#getIoProvider()
	 */
	public IoProvider getIoProvider() {
		return EasyIO.getIoProvider(this.ioProviderSettings);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponentHost#getServerProperty(java.lang.String)
	 */
	public String getServerProperty(String name) {
		return ((name == null) ? null : this.environmentSettings.getSetting(name));
	}

	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponentHost#isRequestProxied()
	 */
	public boolean isRequestProxied() {
		return false; // we don't need a proxy in a web server
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponentHost#isClientRequest()
	 */
	public boolean isClientRequest() {
		synchronized (this.clientRequestThreadIDs) {
			return (this.clientRequestThreadIDs.contains(Long.valueOf(Thread.currentThread().getId())));
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponentHost#getServerComponent(java.lang.String)
	 */
	public GoldenGateServerComponent getServerComponent(String className) {
		return GoldenGateServerComponentRegistry.getServerComponent(className);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponentHost#getServerComponents(java.lang.String)
	 */
	public GoldenGateServerComponent[] getServerComponents(String className) {
		return GoldenGateServerComponentRegistry.getServerComponents(className);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponentHost#getServerComponents()
	 */
	public GoldenGateServerComponent[] getServerComponents() {
		return GoldenGateServerComponentRegistry.getServerComponents();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerActivityLogger#logError(java.lang.String)
	 */
	public void logError(String message) {
		this.log(message, GoldenGateServerActivityLogger.LOG_LEVEL_ERROR);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerActivityLogger#logError(java.lang.Throwable)
	 */
	public void logError(Throwable error) {
		this.log(error, GoldenGateServerActivityLogger.LOG_LEVEL_ERROR);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerActivityLogger#logWarning(java.lang.String)
	 */
	public void logWarning(String message) {
		this.log(message, GoldenGateServerActivityLogger.LOG_LEVEL_WARNING);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerActivityLogger#logInfo(java.lang.String)
	 */
	public void logInfo(String message) {
		this.log(message, GoldenGateServerActivityLogger.LOG_LEVEL_INFO);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerActivityLogger#logDebug(java.lang.String)
	 */
	public void logDebug(String message) {
		this.log(message, GoldenGateServerActivityLogger.LOG_LEVEL_DEBUG);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerActivityLogger#logActivity(java.lang.String)
	 */
	public void logActivity(String message) {
		this.log(message, GoldenGateServerActivityLogger.LOG_LEVEL_DEBUG);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerActivityLogger#logAlways(java.lang.String)
	 */
	public void logAlways(String message) {
		this.log(message, -1);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerActivityLogger#logResult(java.lang.String)
	 */
	public void logResult(String message) {
		this.logInfo(message);
	}
	
	private void log(String message, int level) {
		if (level <= this.logLevel)
			System.out.println(message);
	}
	
	private void log(Throwable error, int level) {
		if (level <= this.logLevel)
			error.printStackTrace(System.out);
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.GenericServlet#init()
	 */
	public void init() throws ServletException {
		
		//	determine root path
		String rootPath = this.getInitParameter(ROOT_PATH_PARAMETER_NAME);
		if (rootPath == null) rootPath = "./";
		File rootFolder = new File(this.getServletContext().getRealPath(rootPath));
		
		//	initialize logger
		try {
			logWriter = new BufferedWriter(new FileWriter(new File(rootFolder, ("GoldenGateCS." + System.currentTimeMillis() + ".log")), true));
		}
		catch (IOException ioe) {
			System.out.println("Could not create log writer - " + ioe.getMessage());
		}
		
		//	read settings
		String configFile = this.getInitParameter("configFile");
		if (configFile == null) configFile = "config.cnfg";
		Settings settings = Settings.loadSettings(new File(rootPath, configFile));
		
		//	get database access data
		this.ioProviderSettings = settings.getSubset("EasyIO");
		
		//	get environment settings
		this.environmentSettings = settings.getSubset("ENV");
		
		//	load server components
		GoldenGateServerComponent[] loadedServerComponents = GoldenGateServerComponentLoader.loadServerComponents(new File(rootPath, COMPONENT_FOLDER_NAME));
		
		//	initialize and register components
		ArrayList serverComponentList = new ArrayList();
		for (int c = 0; c < loadedServerComponents.length; c++)
			try {
				loadedServerComponents[c].setHost(this);
				loadedServerComponents[c].init();
				serverComponentList.add(loadedServerComponents[c]);
				GoldenGateServerComponentRegistry.registerServerComponent(loadedServerComponents[c]);
			}
			catch (Throwable t) {
				System.out.println(t.getClass().getName() + " (" + t.getMessage() + ") while initializing " + ((loadedServerComponents[c] == null) ? "server component" : loadedServerComponents[c].getClass().getName()));
				t.printStackTrace(System.out);
			}
		
		//	get operational ones
		loadedServerComponents = ((GoldenGateServerComponent[]) serverComponentList.toArray(new GoldenGateServerComponent[serverComponentList.size()]));
		serverComponentList.clear();
		
		//	link components
		for (int c = 0; c < loadedServerComponents.length; c++)
			try {
				loadedServerComponents[c].link();
				serverComponentList.add(loadedServerComponents[c]);
			}
			catch (Throwable t) {
				System.out.println(t.getClass().getName() + " (" + t.getMessage() + ") while linking " + ((loadedServerComponents[c] == null) ? "server component" : loadedServerComponents[c].getClass().getName()));
				t.printStackTrace(System.out);
				GoldenGateServerComponentRegistry.unregisterServerComponent(loadedServerComponents[c]);
			}
		
		//	get operational ones
		loadedServerComponents = ((GoldenGateServerComponent[]) serverComponentList.toArray(new GoldenGateServerComponent[serverComponentList.size()]));
		serverComponentList.clear();
		
		//	link components
		for (int c = 0; c < loadedServerComponents.length; c++)
			try {
				loadedServerComponents[c].linkInit();
				serverComponentList.add(loadedServerComponents[c]);
			}
			catch (Throwable t) {
				System.out.println(t.getClass().getName() + " (" + t.getMessage() + ") while linked initialiying " + ((loadedServerComponents[c] == null) ? "server component" : loadedServerComponents[c].getClass().getName()));
				t.printStackTrace(System.out);
				GoldenGateServerComponentRegistry.unregisterServerComponent(loadedServerComponents[c]);
			}
			
		//	get operational ones
		this.serverComponents = ((GoldenGateServerComponent[]) serverComponentList.toArray(new GoldenGateServerComponent[serverComponentList.size()]));
		
		//	obtain component's network actions
		for (int c = 0; c < serverComponents.length; c++) {
			ComponentAction[] componentActions = serverComponents[c].getActions();
			for (int a = 0; a < componentActions.length; a++)
				if (componentActions[a] instanceof ComponentActionNetwork) {
					if (this.serverComponentActions.containsKey(componentActions[a].getActionCommand()))
						throw new ServletException("Duplicate network action '" + componentActions[a].getActionCommand() + "'");
					this.serverComponentActions.put(componentActions[a].getActionCommand(), componentActions[a]);
				}
		}
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.GenericServlet#destroy()
	 */
	public void destroy() {
		
		//	clear action register
		this.serverComponentActions.clear();
		
		//	shut down component
		for (int c = 0; c < this.serverComponents.length; c++)
			try {
				this.serverComponents[c].exit();
			}
			catch (Exception e) {
				this.writeLogEntry("Error exitting " + this.serverComponents[c].getClass().getName() + ":" + e.getMessage());
			}
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/plain");
		response.setHeader("Cache-Control", "no-cache");
		this.writeLogEntry("Handling request from " + request.getRemoteAddr());
		
		//	create reader & writer
		BufferedLineInputStream requestReader = new BufferedLineInputStream(request.getInputStream(), ENCODING);
		BufferedLineOutputStream responseWriter = new BufferedLineOutputStream(response.getOutputStream(), ENCODING);
		
		//	read command
		String command = requestReader.readLine();
		this.writeLogEntry("Command is " + command);
		
		//	catch 'PROXIED' property
		if ("PROXIED".equals(command)) {
			command = requestReader.readLine();
			this.writeLogEntry("Command is " + command);
		}
		
		//	get action
		ComponentActionNetwork action = ((ComponentActionNetwork) this.serverComponentActions.get(command));
		
		//	invalid action, send error
		if (action == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, ("'" + command + "' does not identify an action within this server."));
			requestReader.close();
			responseWriter.close();
			return;
		}
		
		//	process request
		else {
			this.setClientRequest();
			try {
				action.performActionNetwork(requestReader, responseWriter);
			}
			catch (IOException ioe) {
				writeLogEntry("Error handling request - " + ioe.getMessage());
			}
			finally {
				this.clearClientRequest();
			}
		}
	}
	
	private void setClientRequest() {
		synchronized (this.clientRequestThreadIDs) {
			this.clientRequestThreadIDs.add(Long.valueOf(Thread.currentThread().getId()));
		}
	}
	private void clearClientRequest() {
		synchronized (this.clientRequestThreadIDs) {
			this.clientRequestThreadIDs.remove(Long.valueOf(Thread.currentThread().getId()));
		}
	}
	
	private BufferedWriter logWriter;
	private static final String DEFAULT_LOGFILE_DATE_FORMAT = "yyyy.MM.dd HH:mm:ss";
	private static final DateFormat LOG_TIMESTAMP_FORMATTER = new SimpleDateFormat(DEFAULT_LOGFILE_DATE_FORMAT);
	
	/** write an entry to the log file of this markup process server
	 * @param	entry	the entry to write
	 */
	public void writeLogEntry(String entry) {
		String timestamp = LOG_TIMESTAMP_FORMATTER.format(new Date());
		System.out.println(timestamp + ": " + entry);
		if (logWriter != null) {
			try {
				logWriter.write(timestamp + ": " + entry);
				logWriter.newLine();
				logWriter.flush();
			} catch (IOException e) {}
		}
	}
}
