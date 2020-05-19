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
package de.uka.ipd.idaho.goldenGateServer.uaa.webClient;


import java.io.File;
import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;

/**
 * A module for the pages offered by an AuthenticatedWebClientServlet
 * 
 * @author sautter
 */
public abstract class AuthenticatedWebClientModul implements AuthenticatedWebClientConstants {
	
	/** The servlet this modul is hosted in */
	protected AuthenticatedWebClientServlet parent;
	
	/** The folder where this modul's data is located */
	protected File dataPath;
	
	/**
	 * Make this modul know the servlet it is hosted in.
	 * @param parent the servlet hosting this modul
	 */
	public void setParent(AuthenticatedWebClientServlet parent) {
		this.parent = parent;
	}
	
	/**
	 * Make this modul know the folder where it can find it's data.
	 * @param dataPath the data folder for this modul
	 */
	public void setDataPath(File dataPath) {
		this.dataPath = dataPath;
		this.init();
	}
	
	/**
	 * Initialize this modul. This method is invoked after data path is set.
	 * This default implementation does nothing, sub classes are welcome to
	 * overwrite it as needed.
	 */
	protected void init() {}
	
	/**
	 * Retrieve a label String to represent this modul in the link line of the
	 * surrounding HTML page
	 * @return a label String representing this modul
	 */
	public abstract String getModulLabel();
	
	/**
	 * Retrieve the path and name of a CSS style sheet to include in the
	 * surrounding HTML page when displaying a page generated by this modul.
	 * This default implementation returns null, sub classes are welcome to
	 * overwrite it as needed.
	 * @return the path and file name of the CSS stylesheet used for displaying
	 *         a page generated by this modul, or null, if it does not use any
	 *         CSS
	 */
	public String getCssToInclude() {
		return null;
	}
	
	/**
	 * Retrieve the paths and names of JavaScript files to include in the
	 * surrounding HTML page when displaying a page generated by this modul.
	 * This default implementation returns null, sub classes are welcome to
	 * overwrite it as needed.
	 * @return an array holding the paths and names of the JavaScript files used
	 *         in a page generated by this modul
	 */
	public String[] getJavaScriptsToInclude() {
		return null;
	}
	
	/**
	 * Retrieve the JavaScript commands to execute when the portal main page is
	 * loaded ('onload' attribute of the html body tag). This default
	 * implementation returns null, sub classes are welcome to overwrite it as
	 * needed.
	 * @return the JavaScript commands to execute when the portal main page is
	 *         loaded ('onload' attribute of the html body tag)
	 */
	public String[] getJavaScriptLoadCalls() {
		return null;
	}
	
	/**
	 * Retrieve the JavaScript commands to execute when the portal main page is
	 * unloaded ('onunload' attribute of the html body tag). This default
	 * implementation returns null, sub classes are welcome to overwrite it as
	 * needed.
	 * @return the JavaScript commands to execute when the portal main page is
	 *         unloaded ('onunload' attribute of the html body tag)
	 */
	public String[] getJavaScriptUnloadCalls() {
		return null;
	}
	
	/**
	 * Write content to the head of an HTML page, like JavaScripts or CSS
	 * styles. This default implementation does nothing, sub classes are welcome
	 * to overwrite it as needed.
	 * @param pageBuilder the HTML page builder to write to
	 * @throws IOException
	 */
	public void writePageHeadExtensions(HtmlPageBuilder pageBuilder) throws IOException {}
	
	/**
	 * Test if the user logged in on an authenticated client has sufficient
	 * permissions for some or all actions on this modul. If so, a link to this
	 * modul will be available for for the user.
	 * @param authClient the authenticated client belonging to the session to
	 *            display the modul for
	 * @return true the argument authenticated client has permission to execute
	 *         actions on this modul, false otherwise
	 */
	public abstract boolean displayFor(AuthenticatedClient authClient);
	
	/**
	 * Test if the user logged in on an HTTP session has sufficient permissions
	 * for some or all actions on this modul. If so, a link to this modul will
	 * be available for for the user.
	 * @param session the session to display the modul for
	 * @return true the argument session has permission to execute actions on
	 *         this modul, false otherwise
	 */
	public boolean displayFor(HttpSession session) {
		AuthenticatedClient authClient = this.parent.getAuthenticatedClient(session);
		return ((authClient != null) && this.displayFor(authClient));
	}
	
	/**
	 * Handle a request. The argument authenticated client will already be
	 * logged in when this method is invoked
	 * @param authClient the authenticated client belonging to the session the
	 *            request comes from, to use for authentication when interacting
	 *            with the server component backing this modul
	 * @param request the parameters for the request to handle
	 * @return an array of messages reporting on the result of the interaction
	 *         with the backing server
	 * @throws IOException
	 */
	public abstract String[] handleRequest(AuthenticatedClient authClient, HttpServletRequest request) throws IOException;
	
	/**
	 * Handle a request. The session associated with the argument HTTP request
	 * will already be authenticated when this method is invoked.
	 * @param request the HTTP request to handle
	 * @return an array of messages reporting on the result of the interaction
	 *         with the backing server
	 * @throws IOException
	 */
	public String[] handleRequest(HttpServletRequest request) throws IOException {
		AuthenticatedClient authClient = this.parent.getAuthenticatedClient(request.getSession(false));
		if (authClient == null) {
			String[] message = {"Please authenticate against GoldenGATE Server to perform this action."};
			return message;
		}
		else return this.handleRequest(authClient, request);
	}
	
	/**
	 * Write the content of an HTML page representing this modul.
	 * @param authClient the authenticated client belonging to the session the
	 *            request comes from, to use for authentication when interacting
	 *            with the server component backing this modul
	 * @param pageBuilder the page builder to write the page to
	 * @throws IOException
	 */
	public abstract void writePageContent(AuthenticatedClient authClient, HtmlPageBuilder pageBuilder) throws IOException;
	
	/**
	 * Write the content of an HTML page representing this modul.
	 * @param pageBuilder the page builder to write the page to
	 * @throws IOException
	 */
	public void writePageContent(HtmlPageBuilder pageBuilder) throws IOException {
		AuthenticatedClient authClient = this.parent.getAuthenticatedClient(pageBuilder.request.getSession(false));
		if (authClient == null)
			throw new IOException("Please authenticate against GoldenGATE Server to perform this action.");
		this.writePageContent(authClient, pageBuilder);
	}
}