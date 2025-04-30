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
package de.uka.ipd.idaho.goldenGateServer.uca.webClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul;
import de.uka.ipd.idaho.goldenGateServer.uca.UserCertificationAuthorityConstants;
import de.uka.ipd.idaho.goldenGateServer.uca.client.UserCertificationAuthorityClient;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.IoTools;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Module for managing user certifications.
 * 
 * @author sautter
 */
public class UserCertificationManagerModul extends AuthenticatedWebClientModul implements UserCertificationAuthorityConstants {
	private static final String MODE_PARAMETER = "mode";
	
	private static final String EDIT_USER_CERTIFICATES = "UCA_EDIT_USER_CERTIFICATES";
	
	private static final String USER_NAME_PARAMETER = "userName";
	private static final String HOLDER_LEVEL_PARAMETER_SUFFIX = "_holderLevel";
	
	private Map ucaClientCache = Collections.synchronizedMap(new HashMap());
	private UserCertificationAuthorityClient getUcaClient(AuthenticatedClient authClient) {
		UserCertificationAuthorityClient ucac = ((UserCertificationAuthorityClient) this.ucaClientCache.get(authClient.getSessionID()));
		if (ucac == null) {
			ucac = new UserCertificationAuthorityClient(authClient);
			this.ucaClientCache.put(authClient.getSessionID(), ucac);
		}
		return ucac;
	}
	
	/** usual zero-argument constructor for class loading */
	public UserCertificationManagerModul() {}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#getModulLabel()
	 */
	public String getModulLabel() {
		return "User Certifications";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#displayFor(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient)
	 */
	public boolean displayFor(AuthenticatedClient authClient) {
		if (authClient.isAdmin())
			return true;
		else try {
			return this.getUcaClient(authClient).isCertifier();
		}
		catch (IOException ioe) {
			return false;
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#handleRequest(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient, javax.servlet.http.HttpServletRequest)
	 */
	public String[] handleRequest(AuthenticatedClient authClient, HttpServletRequest request) throws IOException {
		UserCertificationAuthorityClient ucac = this.getUcaClient(authClient);
		StringVector messageCollector = new StringVector();
		
		String command = request.getParameter(COMMAND_PARAMETER);
		
		//	edit certifications of a user
		if (EDIT_USER_CERTIFICATES.equals(command)) {
			
			//	get parameters
			String userName = request.getParameter(USER_NAME_PARAMETER);
			UserCertificate[] certs = ucac.getCertificates(true);
			UserCertification[] userCerts = ucac.getUserCertifications(userName);
			UserCertificationSummary userCertSummary = UserCertificationSummary.createSummary(userCerts);
			
			//	read certifications
			ArrayList modUserCerts = new ArrayList();
			for (int c = 0; c < certs.length; c++) {
				if (!authClient.isAdmin() && !ucac.canCertify(certs[c].name))
					continue;
				String holderLevel = request.getParameter(certs[c].name + HOLDER_LEVEL_PARAMETER_SUFFIX);
				if ((holderLevel == null) || (holderLevel.length() != 1))
					continue;
				char hl = holderLevel.charAt(0);
				if ((userCertSummary == null) && (hl == UserCertification.HOLDER_LEVEL_UNCERTIFIED))
					continue; // 'null' summary implies 'uncertified' for all certificates
				if ((userCertSummary != null) && (hl == userCertSummary.getHolderLevel(certs[c].name)))
					continue; // nothing to change here
				modUserCerts.add(new UserCertification(certs[c].name, userName, hl));
			}
			
			//	update certifications
			if (modUserCerts.isEmpty())
				messageCollector.addElement("Certifications of user '" + userName + "' unmodified.");
			else {
				ucac.setUserCertifications(userName, ((UserCertification[]) modUserCerts.toArray(new UserCertification[modUserCerts.size()])));
				messageCollector.addElement(modUserCerts.size() + " certifications of user '" + userName + "' changed successfully.");
			}
		}
		
		return messageCollector.toStringArray();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#writePageContent(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient, de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder)
	 */
	public void writePageContent(AuthenticatedClient authClient, HtmlPageBuilder pageBuilder) throws IOException {
		UserCertificationAuthorityClient ucac = this.getUcaClient(authClient);
		String mode = pageBuilder.request.getParameter(MODE_PARAMETER);
		
		//	edit a user's roles and permissions
		if (EDIT_USER_CERTIFICATES.equals(mode)) {
			String userName = pageBuilder.request.getParameter(USER_NAME_PARAMETER);
			UserCertificate[] certs = ucac.getCertificates(true);
			UserCertification[] userCerts = ucac.getUserCertifications(userName);
			UserCertificationSummary userCertSummary = UserCertificationSummary.createSummary(userCerts);
			
			//	open form and add command
			pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + EDIT_USER_CERTIFICATES + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + USER_NAME_PARAMETER + "\" value=\"" + userName + "\">");
			
			//	build label row
			pageBuilder.writeLine("<table class=\"mainTable\">");
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableHeader\">");
			pageBuilder.writeLine("Manage certifications of user '" + userName + "'");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	open user table
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableBody\">");
			pageBuilder.writeLine("<table width=\"100%\" class=\"dataTable\">");
			
			//	build label row
			pageBuilder.writeLine("<tr>");
			
			pageBuilder.writeLine("<td class=\"dataTableHeader\">");
			pageBuilder.writeLine("Certificate");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("<td class=\"dataTableHeader\">");
			pageBuilder.writeLine("Holder Level");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("</tr>");
			
			//	add actual user data
			for (int c = 0; c < certs.length; c++) {
				if (!authClient.isAdmin() && !ucac.canCertify(certs[c].name))
					continue;
				char hl = ((userCertSummary == null) ? UserCertification.HOLDER_LEVEL_UNCERTIFIED : userCertSummary.getHolderLevel(certs[c].name));
				pageBuilder.writeLine("<tr>");
				
				pageBuilder.writeLine("<td class=\"dataTableBody\">");
				pageBuilder.writeLine(IoTools.prepareForHtml(certs[c].label));
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("<td class=\"dataTableBody\">");
				pageBuilder.writeLine("<select name=\"" + certs[c].name + HOLDER_LEVEL_PARAMETER_SUFFIX + "\">");
				pageBuilder.writeLine("<option value=\"" + UserCertification.HOLDER_LEVEL_UNCERTIFIED + "\"" + ((hl == UserCertification.HOLDER_LEVEL_UNCERTIFIED) ? " selected=\"selected\"" : "") + ">Uncertified</option>");
				pageBuilder.writeLine("<option value=\"" + UserCertification.HOLDER_LEVEL_HOLDER + "\"" + ((hl == UserCertification.HOLDER_LEVEL_HOLDER) ? " selected=\"selected\"" : "") + ">Holder</option>");
				pageBuilder.writeLine("<option value=\"" + UserCertification.HOLDER_LEVEL_VERIFIER + "\"" + ((hl == UserCertification.HOLDER_LEVEL_VERIFIER) ? " selected=\"selected\"" : "") + ">Verifier</option>");
				pageBuilder.writeLine("<option value=\"" + UserCertification.HOLDER_LEVEL_CERTIFIER + "\"" + ((hl == UserCertification.HOLDER_LEVEL_CERTIFIER) ? " selected=\"selected\"" : "") + ">Certifier</option>");
				pageBuilder.writeLine("</select>");
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("</tr>");
			}
			
			//	add button row
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td colspan=\"2\" class=\"formTableBody\">");
			pageBuilder.writeLine("<input type=\"submit\" value=\"Edit User Certifications\" class=\"submitButton\">");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	close user table
			pageBuilder.writeLine("</table>");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	close master table and form
			pageBuilder.writeLine("</table>");
			pageBuilder.writeLine("</form>");
		}
		
		//	show master list
		else {
			String[] userNames = ucac.getUserNames();
			
			//	build label row
			pageBuilder.writeLine("<table class=\"mainTable\">");
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableHeader\">");
			pageBuilder.writeLine("Manage certifications of users");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	open user table
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableBody\">");
			pageBuilder.writeLine("<table width=\"100%\" class=\"dataTable\">");
			
			//	build label row
			pageBuilder.writeLine("<tr>");
			
			pageBuilder.writeLine("<td colspan=\"2\" class=\"dataTableHeader\">");
			pageBuilder.writeLine("Users");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("</tr>");
			
			//	add actual user/role data
			int userNameRows = ((userNames.length + 1) / 2);
			for (int u = 0; u < userNameRows; u++) {
				String userNameLeft = ((u < userNames.length) ? userNames[u] : null);
				String userNameRight = (((u + userNameRows) < userNames.length) ? userNames[u + userNameRows] : null);
				
				//	open table row
				pageBuilder.writeLine("<tr>");
				
				pageBuilder.writeLine("<td class=\"dataTableBody\">");
				if (userNameLeft == null)
					pageBuilder.writeLine("&nbsp;");
				else {
					pageBuilder.writeLine(("<a" +
							" title=\"" + ("Edit certifications of user '" + userNameLeft + "'") + "\"" +
							" href=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "?" + 
										  MODE_PARAMETER + "=" + EDIT_USER_CERTIFICATES + 
									"&" + USER_NAME_PARAMETER + "=" + userNameLeft + 
							"\">"));
					pageBuilder.writeLine(userNameLeft);
					pageBuilder.writeLine("</a>");
				}
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("<td class=\"dataTableBody\">");
				if (userNameRight == null)
					pageBuilder.writeLine("&nbsp;");
				else {
					pageBuilder.writeLine(("<a" +
							" title=\"" + ("Edit certifications of user '" + userNameRight + "'") + "\"" +
							" href=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "?" + 
										  MODE_PARAMETER + "=" + EDIT_USER_CERTIFICATES + 
									"&" + USER_NAME_PARAMETER + "=" + userNameRight + 
							"\">"));
					pageBuilder.writeLine(userNameRight);
					pageBuilder.writeLine("</a>");
				}
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("</tr>");
			}
			
			//	close user table
			pageBuilder.writeLine("</table>");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	close master table and form
			pageBuilder.writeLine("</table>");
		}
	}
}
