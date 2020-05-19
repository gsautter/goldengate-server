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
package de.uka.ipd.idaho.goldenGateServer.client;

import javax.servlet.ServletException;

import de.uka.ipd.idaho.easyIO.web.HtmlServlet;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants;

/**
 * A generic servlet in the web front-end of a GoldenGATE Server via HTTP. This
 * servlet reads the following parameters from web.cnfg configuration file for
 * obtaining the connection parameters of the backing GoldenGATE Server:
 * <ul>
 * <li><b>serverAddress</b>: the host name of the backing GoldenGATE Server; if
 * no port is specified, the address is interpreted as a URL, thus accessing a
 * remote GoldenGATE Server via an HTTP tunnel</li>
 * <li><b>serverPort</b>: the port the backing GoldenGATE Server listens on; to
 * be omitted for HTTP tunnel connection to the backing GoldenGATE Server</li>
 * </ul>
 * Each servlet can have its data stored in a separate folder inside the
 * surrounding web-app's WEB-INF folder, its so-called data path. The default
 * data path is the web-app's WEB-INF folder itself, but a specific data path
 * can be specified as the <b>dataPath</b> parameter in the web.xml.<br>
 * For sub class specific settings and parameters, each servlet in addition has
 * an instance specific configuration file, loaded from its data path. By
 * default, this file is named <b>config.cnfg</b>, but an alternative name can
 * be specified in an the <b>configFile</b> parameter in the web.xml. Settings
 * in the servlet specific configuration file supersense global ones specified
 * in <code>web.cnfg</code>, so it is easy to work with default values in the
 * latter location and overwrite them in the more specific files as needed.
 * 
 * @author sautter
 */
public abstract class GgServerClientServlet extends HtmlServlet implements GoldenGateServerConstants {
	
	/**
	 * a connection to the backing GoldenGATE server, created from serverAddress
	 * and serverPort (for convenience)
	 */
	protected ServerConnection serverConnection;
	
	/**
	 * Initialize the GoldenGATE Server client servlet. This implementation
	 * reads the access data for the backing GoldenGATE Server. Sub classes
	 * overwriting this method thus have to make the super call.
	 * @see de.uka.ipd.idaho.easyIO.web.HtmlServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	get server access data
		String serverAddress = this.getSetting("serverAddress");
		String serverPort = this.getSetting("serverPort");
		
		//	produce server connection
		if (serverPort == null)
			this.serverConnection = ServerConnection.getServerConnection(serverAddress);
		else this.serverConnection = ServerConnection.getServerConnection(serverAddress, Integer.parseInt(serverPort));
	}
}