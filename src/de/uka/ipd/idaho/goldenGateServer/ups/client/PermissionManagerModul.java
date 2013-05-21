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
package de.uka.ipd.idaho.goldenGateServer.ups.client;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul;
import de.uka.ipd.idaho.goldenGateServer.ups.GoldenGateUpsConstants;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Modul for managing user permissions
 * 
 * @author sautter
 */
public class PermissionManagerModul extends AuthenticatedWebClientModul implements GoldenGateUpsConstants {
	
	private static final String MODE_PARAMETER = "mode";
	
	private static final String EDIT_USER = "UPS_EDIT_USER";
	private static final String EDIT_ROLE = "UPS_EDIT_ROLE";
	
	private static final String USER_NAME_PARAMETER = "userName";
	private static final String ROLE_NAME_PARAMETER = "roleName";
	
	private static final String ROLE_PARAMETER = "role";
	private static final String PERMISSION_PARAMETER = "permission";
	
	private Map upsClientCache = Collections.synchronizedMap(new HashMap());
	private GoldenGateUpsClient getUpsClient(AuthenticatedClient authClient) {
		GoldenGateUpsClient upsc = ((GoldenGateUpsClient) this.upsClientCache.get(authClient.getSessionID()));
		if (upsc == null) {
			upsc = new GoldenGateUpsClient(authClient);
			this.upsClientCache.put(authClient.getSessionID(), upsc);
		}
		return upsc;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateScf.uaa.webClient.AuthenticatedWebClientModul#getModulLabel()
	 */
	public String getModulLabel() {
		return "Roles & Permissions";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#displayFor(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient)
	 */
	public boolean displayFor(AuthenticatedClient authClient) {
		return authClient.isAdmin(); // managing permissions and roles is admin business
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateScf.uaa.webClient.AuthenticatedWebClientModul#handleRequest(de.uka.ipd.idaho.goldenGateScf.uaa.client.AuthenticatedClient, javax.servlet.http.HttpServletRequest)
	 */
	public String[] handleRequest(AuthenticatedClient authClient, HttpServletRequest request) throws IOException {
		GoldenGateUpsClient upsc = this.getUpsClient(authClient);
		StringVector messageCollector = new StringVector();
		
		String command = request.getParameter(COMMAND_PARAMETER);
		
		//	create role
		if (CREATE_ROLE.equals(command)) {
			
			//	get parameters
			String roleName = request.getParameter(ROLE_NAME_PARAMETER);
			
			//	create role
			upsc.createRole(roleName);
			messageCollector.addElement("Role '" + roleName + "' created successfully.");
		}
		
		//	delete role
		else if (DELETE_ROLE.equals(command)) {
			
			//	get parameters
			String roleName = request.getParameter(ROLE_NAME_PARAMETER);
			
			//	delete role
			upsc.deleteRole(roleName);
			messageCollector.addElement("Role '" + roleName + "' deleted successfully.");
		}
		
		//	edit a user's roles and permissions
		else if (EDIT_USER.equals(command)) {
			
			//	get parameters
			String userName = request.getParameter(USER_NAME_PARAMETER);
			String[] roles = request.getParameterValues(ROLE_PARAMETER);
			
			//	set roles
			upsc.setUserRoles(userName, roles);
			messageCollector.addElement("Roles of user '" + userName + "' changed successfully.");
		}
		
		//	edit a role's roles and permissions
		else if (EDIT_ROLE.equals(command)) {
			
			//	get parameters
			String roleName = request.getParameter(ROLE_NAME_PARAMETER);
			String[] roles = request.getParameterValues(ROLE_PARAMETER);
			String[] permissions = request.getParameterValues(PERMISSION_PARAMETER);
			
			//	set roles
			upsc.setRoleRoles(roleName, roles);
			messageCollector.addElement("Inherited roles of role '" + roleName + "' changed successfully.");
			
			//	set permissions
			upsc.setRolePermissions(roleName, permissions);
			messageCollector.addElement("Permissions of role '" + roleName + "' changed successfully.");
		}
		
		return messageCollector.toStringArray();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#writePageContent(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient, de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder)
	 */
	public void writePageContent(AuthenticatedClient authClient, HtmlPageBuilder pageBuilder) throws IOException {
//		// TODO Auto-generated method stub
//		
//	}
//
//	/* (non-Javadoc)
//	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#writePageContent(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient, javax.servlet.http.HttpServletRequest, java.lang.String, de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder)
//	 */
//	public void writePageContent(AuthenticatedClient authClient, HttpServletRequest request, String serverLink, HtmlPageBuilder pageBuilder) throws IOException {
		GoldenGateUpsClient upsc = this.getUpsClient(authClient);
		String mode = pageBuilder.request.getParameter(MODE_PARAMETER);
		
		//	edit a user's roles and permissions
		if (EDIT_USER.equals(mode)) {
			String userName = pageBuilder.request.getParameter(USER_NAME_PARAMETER);
			
			String[] roles = upsc.getRoles();
			String[] userRoles = upsc.getUserRoles(userName);
			Set userRoleSet = new HashSet(Arrays.asList(userRoles));
			
			//	open form and add command
			pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + EDIT_USER + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + USER_NAME_PARAMETER + "\" value=\"" + userName + "\">");
			
			//	build label row
			pageBuilder.writeLine("<table class=\"mainTable\">");
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableHeader\">");
			pageBuilder.writeLine("Manage the roles and permissions of user '" + userName + "'");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	open user table
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableBody\">");
			pageBuilder.writeLine("<table width=\"100%\" class=\"dataTable\">");
			
			//	build label row
			pageBuilder.writeLine("<tr>");
			
			pageBuilder.writeLine("<td class=\"dataTableHeader\">");
			pageBuilder.writeLine("Roles");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("</tr>");
			
			//	add actual user data
			for (int r = 0; r < roles.length; r++) {
				String role = ((r < roles.length) ? roles[r] : null);
				
				//	open table row
				pageBuilder.writeLine("<tr>");
				
				pageBuilder.writeLine("<td class=\"dataTableBody\">");
				pageBuilder.writeLine("<input type=\"checkbox\" name=\"" + ROLE_PARAMETER + "\" value=\"" + role + "\"" + (userRoleSet.contains(role) ? " checked" : "") + ">");
				pageBuilder.writeLine("&nbsp;");
				pageBuilder.writeLine(role);
				pageBuilder.writeLine("</td>");
				pageBuilder.writeLine("</tr>");
			}
			
			//	add button row
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td colspan=\"2\" class=\"formTableBody\">");
			pageBuilder.writeLine("<input type=\"submit\" value=\"Edit User\" class=\"submitButton\">");
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
		
		//	edit a role's roles and permissions
		else if (EDIT_ROLE.equals(mode)) {
			String roleName = pageBuilder.request.getParameter(ROLE_NAME_PARAMETER);
			
			String[] roles = upsc.getRoles();
			String[] roleRoles = upsc.getRoleRoles(roleName);
			Set roleRoleSet = new HashSet(Arrays.asList(roleRoles));
			
			String[] permissions = upsc.getPermissions();
			String[] rolePermissions = upsc.getRolePermissions(roleName);
			Set rolePermissionSet = new HashSet(Arrays.asList(rolePermissions));
			
			//	open form and add command
			pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + EDIT_ROLE + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + ROLE_NAME_PARAMETER + "\" value=\"" + roleName + "\">");
			
			//	build label row
			pageBuilder.writeLine("<table class=\"mainTable\">");
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableHeader\">");
			pageBuilder.writeLine("Manage the roles and permissions of role '" + roleName + "'");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	open user table
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableBody\">");
			pageBuilder.writeLine("<table width=\"100%\" class=\"dataTable\">");
			
			//	build label row
			pageBuilder.writeLine("<tr>");
			
			pageBuilder.writeLine("<td class=\"dataTableHeader\">");
			pageBuilder.writeLine("Roles");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("<td class=\"dataTableHeader\">");
			pageBuilder.writeLine("Permissions");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("</tr>");
			
			//	add actual user data
			for (int rp = 0; rp < Math.max(roles.length, permissions.length); rp++) {
				String role = ((rp < roles.length) ? roles[rp] : null);
				String permission = ((rp < permissions.length) ? permissions[rp] : null);
				
				//	open table row
				pageBuilder.writeLine("<tr>");
				
				pageBuilder.writeLine("<td class=\"dataTableBody\">");
				if (role == null)
					pageBuilder.writeLine("&nbsp;");
				else {
					pageBuilder.writeLine("<input type=\"checkbox\" name=\"" + ROLE_PARAMETER + "\" value=\"" + role + "\"" + (roleRoleSet.contains(role) ? " checked" : "") + ">");
					pageBuilder.writeLine("&nbsp;");
					pageBuilder.writeLine(role);
				}
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("<td class=\"dataTableBody\">");
				if (permission == null)
					pageBuilder.writeLine("&nbsp;");
				else {
					pageBuilder.writeLine("<input type=\"checkbox\" name=\"" + PERMISSION_PARAMETER + "\" value=\"" + permission + "\"" + (rolePermissionSet.contains(permission) ? " checked" : "") + ">");
					pageBuilder.writeLine("&nbsp;");
					pageBuilder.writeLine(permission);
				}
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("</tr>");
			}
			
			//	add button row
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td colspan=\"2\" class=\"formTableBody\">");
			pageBuilder.writeLine("<input type=\"submit\" value=\"Edit Role\" class=\"submitButton\">");
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
			String[] users = upsc.getUsers();
			String[] roles = upsc.getRoles();
			
			//	build label row
			pageBuilder.writeLine("<table class=\"mainTable\">");
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableHeader\">");
			pageBuilder.writeLine("Manage the roles and permissions of users and roles");
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	open user table
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableBody\">");
			pageBuilder.writeLine("<table width=\"100%\" class=\"dataTable\">");
			
			//	build label row
			pageBuilder.writeLine("<tr>");
			
			pageBuilder.writeLine("<td class=\"dataTableHeader\">");
			pageBuilder.writeLine("Users");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("<td class=\"dataTableHeader\">");
			pageBuilder.writeLine("Roles");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("</tr>");
			
			//	add row with form for creating role
			pageBuilder.writeLine("<tr>");
			
			pageBuilder.writeLine("<td width=\"30%\" class=\"dataTableBody\">");
			pageBuilder.writeLine("&nbsp;");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("<td width=\"70%\" class=\"dataTableBody\">");
			pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
			pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + CREATE_ROLE + "\">");
			pageBuilder.writeLine("Role Name&nbsp;");
			pageBuilder.writeLine("<input type=\"text\" name=\"" + ROLE_NAME_PARAMETER + "\">");
			pageBuilder.writeLine("&nbsp;");
			pageBuilder.writeLine("<input type=\"submit\" value=\"Create Role\" class=\"submitButton\">");
			pageBuilder.writeLine("</form>");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("</tr>");
			
			//	add actual user/role data
			for (int ur = 0; ur < Math.max(users.length, roles.length); ur++) {
				String user = ((ur < users.length) ? users[ur] : null);
				String role = ((ur < roles.length) ? roles[ur] : null);
				
				//	open table row
				pageBuilder.writeLine("<tr>");
				
				pageBuilder.writeLine("<td class=\"dataTableBody\">");
				if (user == null)
					pageBuilder.writeLine("&nbsp;");
				else {
					pageBuilder.writeLine(("<a" +
							" title=\"" + ("Edit roles and permissions of user '" + user + "'") + "\"" +
							" href=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "?" + 
										  MODE_PARAMETER + "=" + EDIT_USER + 
									"&" + USER_NAME_PARAMETER + "=" + user + 
							"\">"));
					pageBuilder.writeLine(user);
					pageBuilder.writeLine("</a>");
				}
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("<td class=\"dataTableBody\">");
				if (role == null)
					pageBuilder.writeLine("&nbsp;");
				else {
					pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
					pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + DELETE_ROLE + "\">");
					pageBuilder.writeLine("<input type=\"hidden\" name=\"" + ROLE_NAME_PARAMETER + "\" value=\"" + role + "\">");
					pageBuilder.writeLine(("<a" +
							" title=\"" + ("Edit roles and permissions of role '" + role + "'") + "\"" +
							" href=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "?" + 
										  MODE_PARAMETER + "=" + EDIT_ROLE + 
									"&" + ROLE_NAME_PARAMETER + "=" + role + 
							"\">"));
					pageBuilder.writeLine(role);
					pageBuilder.writeLine("</a>");
					pageBuilder.writeLine("&nbsp;");
					pageBuilder.writeLine("<input type=\"submit\" value=\"Delete Role\" class=\"submitButton\">");
					pageBuilder.writeLine("</form>");
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
