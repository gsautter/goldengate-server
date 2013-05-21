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
package de.uka.ipd.idaho.goldenGateServer.uaa.webClient;


import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import de.uka.ipd.idaho.goldenGateServer.uaa.UserAccessAuthorityConstants;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Modul for managing a user's own account.
 * 
 * @author sautter
 */
public class AccountManagerModul extends AuthenticatedWebClientModul implements UserAccessAuthorityConstants {
	
	private static final String NEW_PASSWORD_PARAMETER = "new" + PASSWORD_PARAMETER;
	private static final String CONFIRM_NEW_PASSWORD_PARAMETER = "confirm" + PASSWORD_PARAMETER;
	
	/* (non-Javadoc)
	 * @see de.goldenGateScf.uaa.webClient.AuthenticatedWebClientModul#getModulLabel()
	 */
	public String getModulLabel() {
		return "My Account";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#displayFor(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient)
	 */
	public boolean displayFor(AuthenticatedClient authClient) {
		return true; // anyone may change his password ...
	}
	
	/* (non-Javadoc)
	 * @see de.goldenGateScf.uaa.webClient.AuthenticatedWebClientModul#handleRequest(de.goldenGateScf.uaa.client.AuthenticatedClient, java.util.Properties)
	 */
	public String[] handleRequest(AuthenticatedClient authClient, HttpServletRequest request) throws IOException {
		StringVector messageCollector = new StringVector();
		
		//	create user
		if (SET_PWD.equals(request.getParameter(COMMAND_PARAMETER))) {
			
			//	get parameters
			String password = request.getParameter(PASSWORD_PARAMETER);
			String newPassword = request.getParameter(NEW_PASSWORD_PARAMETER);
			String confirmPassword = request.getParameter(CONFIRM_NEW_PASSWORD_PARAMETER);
			
			//	check new password
			if (newPassword.equals(confirmPassword)) {
				
				//	change password
				authClient.setPassword(password, newPassword);
				messageCollector.addElement("Password changed successfully.");
			}
			
			//	report error
			else messageCollector.addElement("New password and confirmation do not match, please try again.");
		}
		
		return messageCollector.toStringArray();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#writePageContent(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient, de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder)
	 */
	public void writePageContent(AuthenticatedClient authClient, HtmlPageBuilder pageBuilder) throws IOException {
		
		//	open form and add command
		pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
		pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + SET_PWD + "\">");
		
		//	open master table
		pageBuilder.writeLine("<table class=\"formTable\">");
		
		//	add head row
		pageBuilder.writeLine("<tr>");
		pageBuilder.writeLine("<td colspan=\"4\" class=\"formTableHeader\">");
		pageBuilder.writeLine("Please enter your existing and your new password.");
		pageBuilder.writeLine("</td>");
		pageBuilder.writeLine("</tr>");
		
		//	add fields
		pageBuilder.writeLine("<tr>");
		
		pageBuilder.writeLine("<td class=\"formTableBody\">");
		pageBuilder.writeLine("Existing Password");
		pageBuilder.writeLine("</td>");
		pageBuilder.writeLine("<td class=\"formTableBody\">");
		pageBuilder.writeLine("<input type=\"password\" name=\"" + PASSWORD_PARAMETER + "\">");
		pageBuilder.writeLine("</td>");
		
		pageBuilder.writeLine("<td class=\"formTableBody\">");
		pageBuilder.writeLine("New Password");
		pageBuilder.writeLine("</td>");
		pageBuilder.writeLine("<td class=\"formTableBody\">");
		pageBuilder.writeLine("<input type=\"password\" name=\"" + NEW_PASSWORD_PARAMETER + "\">");
		pageBuilder.writeLine("</td>");
		
		pageBuilder.writeLine("</tr>");
		
		//	add confirmation fields
		pageBuilder.writeLine("<tr>");
		
		pageBuilder.writeLine("<td class=\"formTableBody\">");
		pageBuilder.writeLine("&nbsp;");
		pageBuilder.writeLine("</td>");
		pageBuilder.writeLine("<td class=\"formTableBody\">");
		pageBuilder.writeLine("&nbsp;");
		pageBuilder.writeLine("</td>");
		
		pageBuilder.writeLine("<td class=\"formTableBody\">");
		pageBuilder.writeLine("Confirm Password");
		pageBuilder.writeLine("</td>");
		pageBuilder.writeLine("<td class=\"formTableBody\">");
		pageBuilder.writeLine("<input type=\"password\" name=\"" + CONFIRM_NEW_PASSWORD_PARAMETER + "\">");
		pageBuilder.writeLine("</td>");
		
		pageBuilder.writeLine("</tr>");
		
		//	add button
		pageBuilder.writeLine("<tr>");
		pageBuilder.writeLine("<td colspan=\"4\" class=\"formTableBody\">");
		pageBuilder.writeLine("<input type=\"submit\" value=\"Change Password\" class=\"submitButton\">");
		pageBuilder.writeLine("</td>");
		pageBuilder.writeLine("</tr>");
		
		//	close table & form
		pageBuilder.writeLine("</table>");
		pageBuilder.writeLine("</form>");
	}
}
