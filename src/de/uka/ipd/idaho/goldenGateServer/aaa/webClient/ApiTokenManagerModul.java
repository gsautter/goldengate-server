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
package de.uka.ipd.idaho.goldenGateServer.aaa.webClient;


import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import de.uka.ipd.idaho.goldenGateServer.aaa.ApiAccessAuthorityConstants;
import de.uka.ipd.idaho.goldenGateServer.aaa.client.ApiAccessAuthorityClient;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Modul for managing API access token associated with user account.
 * 
 * @author sautter
 */
public class ApiTokenManagerModul extends AuthenticatedWebClientModul implements ApiAccessAuthorityConstants {
	private Map aaaClientCache = Collections.synchronizedMap(new HashMap());
	private ApiAccessAuthorityClient getClient(AuthenticatedClient authClient) {
		ApiAccessAuthorityClient aaac = ((ApiAccessAuthorityClient) this.aaaClientCache.get(authClient.getSessionID()));
		if (aaac == null) {
			aaac = new ApiAccessAuthorityClient(authClient);
			this.aaaClientCache.put(authClient.getSessionID(), aaac);
		}
		return aaac;
	}
	
	/* (non-Javadoc)
	 * @see de.goldenGateScf.uaa.webClient.AuthenticatedWebClientModul#getModulLabel()
	 */
	public String getModulLabel() {
		return "API Token Manager";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#displayFor(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient)
	 */
	public boolean displayFor(AuthenticatedClient authClient) {
		return authClient.hasPermission(USE_API_TOKEN_PERMISSION);
	}
	
	/* (non-Javadoc)
	 * @see de.goldenGateScf.uaa.webClient.AuthenticatedWebClientModul#handleRequest(de.goldenGateScf.uaa.client.AuthenticatedClient, java.util.Properties)
	 */
	public String[] handleRequest(AuthenticatedClient authClient, HttpServletRequest request) throws IOException {
		ApiAccessAuthorityClient aaac = this.getClient(authClient);
		StringVector messageCollector = new StringVector();
		
		String command = request.getParameter(COMMAND_PARAMETER);
		
		//	create token
		if (CREATE_TOKEN.equals(command)) {
			aaac.createToken();
			messageCollector.addElement("API token created successfully.");
		}
		
		//	delete user
		else if (DELETE_TOKEN.equals(command)) {
			aaac.deleteToken();
			messageCollector.addElement("API token deleted successfully.");
		}
		
		return messageCollector.toStringArray();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#writePageContent(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient, de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder)
	 */
	public void writePageContent(AuthenticatedClient authClient, HtmlPageBuilder pageBuilder) throws IOException {
		ApiAccessAuthorityClient aaac = this.getClient(authClient);
		String token = aaac.getToken();
		
		//	open master table
		pageBuilder.writeLine("<table class=\"mainTable\">");
		pageBuilder.writeLine("<tr>");
		pageBuilder.writeLine("<td class=\"mainTableBody\">");
		pageBuilder.writeLine("<table class=\"dataTable\">");
		
		//	no token created thus far, display create button
		if (token == null) {
			
			//	add head row
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td class=\"dataTableHeader\">");
			pageBuilder.writeLine("Create an API access token for this GoldenGATE Server");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	display 'no token' message
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td class=\"dataTableBody\">");
			pageBuilder.writeLine("<br/><p>You have not created an API access token for this GoldenGATE Server so far.<br/>Use 'Create New Token' below to create one.</p>");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	add create button
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td class=\"dataTableBody\">");
			pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + CREATE_TOKEN + "\">");
			pageBuilder.writeLine("<input type=\"submit\" value=\"Create New Token\" class=\"submitButton\">");
			pageBuilder.writeLine("</form>");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
		}
		
		//	display existing token and delete button
		else {
			
			//	add head row
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td colspan=\"2\" class=\"dataTableHeader\">");
			pageBuilder.writeLine("Your API access token for this GoldenGATE Server");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	display token
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td colspan=\"2\" class=\"dataTableBody\">");
			pageBuilder.writeLine("<br/><p><tt>" + token + "</tt></p>");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	add create and delete buttons
			pageBuilder.writeLine("<tr>");
			
			pageBuilder.writeLine("<td class=\"dataTableBody\">");
			pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + CREATE_TOKEN + "\">");
			pageBuilder.writeLine("<input type=\"submit\" value=\"Create New Token\" class=\"submitButton\">");
			pageBuilder.writeLine("</form>");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("<td class=\"dataTableBody\">");
			pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + DELETE_TOKEN + "\">");
			pageBuilder.writeLine("<input type=\"submit\" value=\"Delete Token\" class=\"submitButton\">");
			pageBuilder.writeLine("</form>");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("</tr>");
		}
		
		//	close table & form
		pageBuilder.writeLine("</table>");
		pageBuilder.writeLine("</td>");
		pageBuilder.writeLine("</tr>");
		pageBuilder.writeLine("</table>");
	}
}