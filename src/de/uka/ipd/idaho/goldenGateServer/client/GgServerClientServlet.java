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
import java.util.TreeMap;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.easyIO.web.HtmlServlet;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants;

/**
 * A generic servlet in the web front-end of a GoldenGATE Server via HTTP. This
 * servlet uses a centralized, thus easy to maintain configuration file for
 * obtaining the connection parameters of the backing GoldenGATE Server. By
 * default, this file is <b>GgServerAccess.cnfg</b> in the surrounding
 * web-app's context path. It can be changed to another file somewhere below the
 * web-app's context path through specifying the alternative file path and name
 * in the value of the servlet's <b>serverAccessFile</b> parameter in the
 * web.xml. The server access configuration file is expected to contain the
 * following parameters:
 * <ul>
 * <li><b>serverAddress</b>: the host name of the backing GoldenGATE Server;
 * if no port is specified, the address is interpreted as a URL, thus accessing
 * a remote GoldenGATE Server via an HTTP tunnel</li>
 * <li><b>serverPort</b>: the port the backing GoldenGATE Server listens on;
 * to be omitted for HTTP tunnel connection to the backing GoldenGATE Server</li>
 * </ul>
 * Each servlet can have its data stored in a separate folder below the
 * surrounding web-app's context path, its so-called data path. The default data
 * path is the web-app's context path itself, but a specific data path can be
 * specified as the <b>dataPath</b> parameter in the web.xml.<br>
 * For sub class specific settings and parameters, each servlet in addition has
 * an instance specific configuration file, loaded from its data path. By
 * default, this file is named <b>config.cnfg</b>, but an alternative name can
 * be specified in an the <b>configFile</b> parameter in the web.xml.
 * 
 * @author sautter
 */
public abstract class GgServerClientServlet extends HtmlServlet implements GoldenGateServerConstants {
	
	private static TreeMap reinitializableInstancesByName = new TreeMap();
	
	/**
	 * Retrieve the names of all instances of sub classes of this class. The
	 * returned array is sorted lexicographically.
	 * @return an array holding the names of all instances of sub classes of
	 *         this class.
	 */
	public static String[] getReInitializableInstanceNames() {
		return ((String[]) reinitializableInstancesByName.keySet().toArray(new String[reinitializableInstancesByName.size()]));
	}
	
	/**
	 * Re-initialize a specific servlet. This method is intended to facilitate
	 * refreshing the configuration of individual servlets without having to
	 * reload the entire web application or even restart the whole web server.
	 * This is useful for ingesting modified configuration files, stylesheets,
	 * etc. For security reasons, facilities invoking this method should be
	 * protected by some sort of login mechanism.
	 * @param servletName the name of the servlet to reinitialize
	 * @throws ServletException
	 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerClientServlet.ReInitializableServlet#reInit(Settings)
	 */
	public static void reInitialize(String servletName) throws ServletException {
		GgServerClientServlet scs = ((GgServerClientServlet) reinitializableInstancesByName.get(servletName));
		if ((scs != null) && (scs instanceof ReInitializableServlet))
			((ReInitializableServlet) scs).reInit(scs.loadConfig());
	}
	
//	/**
//	 * the address of the backing GoldenGATE server, as specified in the server
//	 * access configuration file; this field is provided for transparency, sub
//	 * classes are recommended to use the readily available serverConnection
//	 * instead of creating their own
//	 */
//	protected String serverAddress;
//	
//	/**
//	 * the port of the backing GoldenGATE server, as specified in the server
//	 * access configuration file, missing if accessing the backing server via an
//	 * HTTP tunnel; this field is provided for transparency, sub classes are
//	 * recommended to use the readily available serverConnection instead of
//	 * creating their own
//	 */
//	protected int serverPort;
//	
	/**
	 * a connection to the backing GoldenGATE server, created from serverAddress
	 * and serverPort (for convenience)
	 */
	protected ServerConnection serverConnection;
	
//	
//	/** the surrounding web-app's context path */
//	protected File rootFolder;
//	
//	/**
//	 * the servlet's data path as a string, relative to the root path, as
//	 * specified in the web.xml; this string is either empty, or it starts with
//	 * a '/', in accordance to the usual return values of the getContextPath()
//	 * and getServletPath() methods of HttpServletRequest)
//	 */
//	protected String dataPath;
//	
//	/** the servlet's data path as a folder */
//	protected File dataFolder;
	
	/**
	 * Initialize the GoldenGATE Server client servlet. This implementation
	 * reads the access data for the backing GoldenGATE Server. Sub classes
	 * overwriting this method thus have to make the super call.
	 * @see de.uka.ipd.idaho.easyIO.web.HtmlServlet#doInit()
	 */
	protected final void doInit() throws ServletException {
		super.doInit();
//		
//		//	get server access settings
//		String serverAccessFile = this.getInitParameter("serverAccessFile");
//		if (serverAccessFile == null)
//			serverAccessFile = "GgServerAccess.cnfg";
//		Settings serverAccessSettings = Settings.loadSettings(new File(this.rootFolder, serverAccessFile));
//		
//		//	get server access data
//		String serverAddress = serverAccessSettings.getSetting("serverAddress");
//		String serverPort = serverAccessSettings.getSetting("serverPort");
		
		//	get server access data
		String serverAddress = this.getSetting("serverAddress");
		String serverPort = this.getSetting("serverPort");
		
		//	produce server connection
		if (serverPort == null)
			this.serverConnection = ServerConnection.getServerConnection(serverAddress);
		else this.serverConnection = ServerConnection.getServerConnection(serverAddress, Integer.parseInt(serverPort));
		
		//	initialize sub class
		this.init(this.loadConfig());
		
		//	store in registry if no exception up to here
		if (this instanceof ReInitializableServlet)
			reinitializableInstancesByName.put(this.getServletName(), this);
	}
	private Settings loadConfig() {
		String configFile = this.getInitParameter("configFile");
		if (configFile == null) configFile = "config.cnfg";
		return Settings.loadSettings(new File(this.dataFolder, configFile));
	}
	
	/**
	 * Do sub class specific initialization. This method is called after server
	 * access is established. The argument settings object is the same as the
	 * one referenced by the 'config' field of this class. This default
	 * implementation does nothing, sub classes are welcome to overwrite it as
	 * needed.
	 * @param config a settings object containing the sub class specific
	 *            settings from the servlet's config file
	 */
	protected void init(Settings config) {}
	
	
	/**
	 * Interface to implement for sub classes of GgServerClientServlet that want
	 * to facilitate refreshing their configuration without having to reload the
	 * entire web application or even restart the whole web server. This is
	 * useful for ingesting modified configuration files, stylesheets, etc.
	 * Servlets implementing this interface will have their name listed in the
	 * array returned by the
	 * GgServerClientServlet.getReInitializableInstanceNames() and will be
	 * reinitializable by invoking GgServerClientServlet.reInititialize(String)
	 * with their name as the argument.
	 * 
	 * @author sautter
	 */
	public static interface ReInitializableServlet extends Servlet {
		
		/**
		 * Re-initialize the servlet. The configuration is loaded from the same
		 * location as the one specified to the init(Settings) method.
		 * @param config a settings object containing the sub class specific
		 *            settings from the servlet's config file
		 */
		public abstract void reInit(Settings config);
	}
}
