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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import de.uka.ipd.idaho.goldenGateServer.uaa.UserAccessAuthorityConstants;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.UserAccessAuthorityClient;
import de.uka.ipd.idaho.goldenGateServer.uaa.data.UserList;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;
import de.uka.ipd.idaho.stringUtils.StringVector;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringTupel;

/**
 * Modul for managing user accounts
 * 
 * @author sautter
 */
public class UserManagerModul extends AuthenticatedWebClientModul implements UserAccessAuthorityConstants {
	private static final String EDIT_USER = "UAA_EDIT_USER";
	private static final String WAS_ADMIN_PARAMETER = "wasAdmin";
	
	private Map uaaClientCache = Collections.synchronizedMap(new HashMap());
	private UserAccessAuthorityClient getClient(AuthenticatedClient authClient) {
		UserAccessAuthorityClient uaac = ((UserAccessAuthorityClient) this.uaaClientCache.get(authClient.getSessionID()));
		if (uaac == null) {
			uaac = new UserAccessAuthorityClient(authClient);
			this.uaaClientCache.put(authClient.getSessionID(), uaac);
		}
		return uaac;
	}
	
	/* (non-Javadoc)
	 * @see de.goldenGateScf.uaa.webClient.AuthenticatedWebClientModul#getModulLabel()
	 */
	public String getModulLabel() {
		return "User Manager";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#displayFor(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient)
	 */
	public boolean displayFor(AuthenticatedClient authClient) {
		return authClient.isAdmin(); // user management is admin business
	}
	
	/* (non-Javadoc)
	 * @see de.goldenGateScf.uaa.webClient.AuthenticatedWebClientModul#handleRequest(de.goldenGateScf.uaa.client.AuthenticatedClient, java.util.Properties)
	 */
	public String[] handleRequest(AuthenticatedClient authClient, HttpServletRequest request) throws IOException {
		UserAccessAuthorityClient uaac = this.getClient(authClient);
		StringVector messageCollector = new StringVector();
		
		String command = request.getParameter(COMMAND_PARAMETER);
		
		//	create user
		if (CREATE_USER.equals(command)) {
			
			//	get & check parameters
			String userName = request.getParameter(USER_NAME_PARAMETER);
			if (userName.matches("[a-zA-Z0-9]{1,32}")) {
				String password = request.getParameter(PASSWORD_PARAMETER);
				boolean isAdmin = IS_ADMIN_PARAMETER.equals(request.getParameter(IS_ADMIN_PARAMETER));
				
				//	create user
				uaac.createUser(userName, password);
				messageCollector.addElement("User '" + userName + "' created successfully.");
				
				//	set admin property if required
				if (isAdmin) {
					uaac.setAdmin(userName, true);
					messageCollector.addElement("Admin property for user '" + userName + "' set successfully.");
				}
			}
			else messageCollector.addElement("Invalid user name, use Latin letters and digits only.");
		}
		
		//	edit a user in some fashion
		else if (EDIT_USER.equals(command)) {
			
			//	get parameters
			String userName = request.getParameter(USER_NAME_PARAMETER);
			String newPassword = request.getParameter(PASSWORD_PARAMETER);
			boolean wasAdmin = WAS_ADMIN_PARAMETER.equals(request.getParameter(WAS_ADMIN_PARAMETER));
			boolean isAdmin = IS_ADMIN_PARAMETER.equals(request.getParameter(IS_ADMIN_PARAMETER));
			
			//	set password if new one entered
			if ((newPassword != null) && (newPassword.length() != 0)) {
				uaac.changePassword(userName, newPassword);
				messageCollector.addElement("Password of user '" + userName + "' changed successfully.");
			}
			
			//	change admin property if changed
			if (wasAdmin != isAdmin) {
				uaac.setAdmin(userName, isAdmin);
				messageCollector.addElement("Admin property for user '" + userName + "' " + (isAdmin ? "set" : "removed") + " successfully.");
			}
		}
		
		//	delete user
		else if (DELETE_USER.equals(command)) {
			
			//	get parameters
			String userName = request.getParameter(USER_NAME_PARAMETER);
			
			//	delete user
			uaac.deleteUser(userName);
			messageCollector.addElement("User '" + userName + "' deleted successfully.");
		}
		
		return messageCollector.toStringArray();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#writePageContent(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient, de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder)
	 */
	public void writePageContent(AuthenticatedClient authClient, HtmlPageBuilder pageBuilder) throws IOException {
		UserAccessAuthorityClient uaac = this.getClient(authClient);
		UserList userList = uaac.getUserList();
		if (userList == null) {
			
			//	open master table
			pageBuilder.writeLine("<table class=\"dataTable\">");
			
			//	add report of no results
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"errorMessage\">");
			pageBuilder.writeLine("You do not have permission to access the user data of this GoldenGATE Server.");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	close master table
			pageBuilder.writeLine("</table>");
		}
		
		else {
			//	open form and add command
			pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + CREATE_USER + "\">");
			
			//	open master table
			pageBuilder.writeLine("<table class=\"formTable\">");
			
			//	add head row
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td colspan=\"3\" class=\"formTableHeader\">");
			pageBuilder.writeLine("Create a new user for this GoldenGATE Server");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	add data fields
			pageBuilder.writeLine("<tr>");
			
			pageBuilder.writeLine("<td class=\"formTableBody\">");
			pageBuilder.writeLine("User Name&nbsp;");
			pageBuilder.writeLine("<input type=\"text\" name=\"" + USER_NAME_PARAMETER + "\">");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("<td class=\"formTableBody\">");
			pageBuilder.writeLine("Password&nbsp;");
			pageBuilder.writeLine("<input type=\"text\" name=\"" + PASSWORD_PARAMETER + "\">");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("<td class=\"formTableBody\">");
			pageBuilder.writeLine("Admin&nbsp;");
			pageBuilder.writeLine("<input type=\"checkbox\" name=\"" + IS_ADMIN_PARAMETER + "\" value=\"" + IS_ADMIN_PARAMETER + "\">");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("</tr>");
			
			//	add create user button
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td colspan=\"3\" class=\"formTableBody\">");
			pageBuilder.writeLine("<input type=\"submit\" value=\"Create User\" class=\"submitButton\">");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	close table & form
			pageBuilder.writeLine("</table>");
			pageBuilder.writeLine("</form>");
			
			//	add spacer
			pageBuilder.writeLine("<br>");
			
			//	no users
			if (userList.isEmpty()) {
				
				//	open master table
				pageBuilder.writeLine("<table class=\"mainTable\">");
				
				//	build label row
				pageBuilder.writeLine("<tr>");
				pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableHeader\">");
				pageBuilder.writeLine("GoldenGATE Server Users");
				pageBuilder.writeLine("</td>");
				pageBuilder.writeLine("</tr>");
				
				//	add report of no results
				pageBuilder.writeLine("<tr>");
				pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableBody\">");
				pageBuilder.writeLine("This GoldenGATE Server does not have any users.");
				pageBuilder.writeLine("</td>");
				pageBuilder.writeLine("</tr>");
				
				//	close master table
				pageBuilder.writeLine("</table>");
			}
			
			//	user data given
			else {
				
				//	build label row
				pageBuilder.writeLine("<table class=\"mainTable\">");
				pageBuilder.writeLine("<tr>");
				pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableHeader\">");
				pageBuilder.writeLine("GoldenGATE Server Users");
				pageBuilder.writeLine("</td>");
				pageBuilder.writeLine("</tr>");
				
				//	open user table
				pageBuilder.writeLine("<tr>");
				pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableBody\">");
				pageBuilder.writeLine("<table width=\"100%\" class=\"dataTable\">");
				
				//	build label row
				pageBuilder.writeLine("<tr>");
				
				pageBuilder.writeLine("<td class=\"dataTableHeader\">");
				pageBuilder.writeLine("User Name");
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("<td class=\"dataTableHeader\">");
				pageBuilder.writeLine("Change Password");
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("<td class=\"dataTableHeader\">");
				pageBuilder.writeLine("Admin");
				pageBuilder.writeLine("</td>");
				
				//	add edit user button
				pageBuilder.writeLine("<td class=\"dataTableHeader\">");
				pageBuilder.writeLine("Write Changes");
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("<td class=\"dataTableHeader\">");
				pageBuilder.writeLine("Delete User");
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("</tr>");
				
				//	add actual user data
				for (int u = 0; u < userList.size(); u++) {
					StringTupel userData = userList.get(u);
					String userName = userData.getValue(USER_NAME_PARAMETER);
					boolean isAdmin = IS_ADMIN_PARAMETER.equals(userData.getValue(IS_ADMIN_PARAMETER));
					
					//	open table row
					pageBuilder.writeLine("<tr>");
					
					pageBuilder.writeLine("<td class=\"dataTableBody\">");
					pageBuilder.writeLine(userName);
					pageBuilder.writeLine("</td>");
					
					//	open form for editing user and add SRS session ID, command, and user name
					pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
					pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + EDIT_USER + "\">");
					pageBuilder.writeLine("<input type=\"hidden\" name=\"" + USER_NAME_PARAMETER + "\" value=\"" + userName + "\">");
					pageBuilder.writeLine("<input type=\"hidden\" name=\"" + WAS_ADMIN_PARAMETER + "\" value=\"" + (isAdmin ? WAS_ADMIN_PARAMETER : "") + "\">");
					
					pageBuilder.writeLine("<td class=\"dataTableBody\">");
					pageBuilder.writeLine("New Password&nbsp;");
					pageBuilder.writeLine("<input type=\"text\" name=\"" + PASSWORD_PARAMETER + "\">");
					pageBuilder.writeLine("</td>");
					
					pageBuilder.writeLine("<td class=\"dataTableBody\">");
					pageBuilder.writeLine("Admin&nbsp;");
					pageBuilder.writeLine("<input type=\"checkbox\" name=\"" + IS_ADMIN_PARAMETER + "\" value=\"" + IS_ADMIN_PARAMETER + "\"" + (isAdmin ? " checked" : "") + ">");
					pageBuilder.writeLine("</td>");
					
					//	add edit user button
					pageBuilder.writeLine("<td class=\"dataTableBody\">");
					pageBuilder.writeLine("<input type=\"submit\" value=\"Edit User\" class=\"submitButton\">");
					pageBuilder.writeLine("</td>");
					
					//	close editing form
					pageBuilder.writeLine("</form>");
					
					//	open form for deleting user and add SRS session ID, command, and user name
					pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
					pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + DELETE_USER + "\">");
					pageBuilder.writeLine("<input type=\"hidden\" name=\"" + USER_NAME_PARAMETER + "\" value=\"" + userName + "\">");
					
					//	add edit user button
					pageBuilder.writeLine("<td class=\"dataTableBody\">");
					pageBuilder.writeLine("<input type=\"submit\" value=\"Delete User\" class=\"submitButton\">");
					pageBuilder.writeLine("</td>");
					
					//	close deleting form
					pageBuilder.writeLine("</form>");
					
					//	close table row
					pageBuilder.writeLine("</tr>");
				}
				
				//	close user table
				pageBuilder.writeLine("</table>");
				pageBuilder.writeLine("</td>");
				pageBuilder.writeLine("</tr>");
				
				//	close master table
				pageBuilder.writeLine("</table>");
			}
		}
	}
}