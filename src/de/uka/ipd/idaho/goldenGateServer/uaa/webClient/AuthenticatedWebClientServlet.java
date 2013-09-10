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


import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.easyIO.web.WebAppHost;
import de.uka.ipd.idaho.easyIO.web.WebAppHost.AuthenticationProvider;
import de.uka.ipd.idaho.gamta.util.GamtaClassLoader;
import de.uka.ipd.idaho.gamta.util.GamtaClassLoader.ComponentInitializer;
import de.uka.ipd.idaho.goldenGateServer.client.GgServerHtmlServlet;
import de.uka.ipd.idaho.goldenGateServer.client.GgServerWebFrontendLogger;
import de.uka.ipd.idaho.goldenGateServer.uaa.UserAccessAuthorityConstants;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.IoTools;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Servlet for interactions with the backing server that require authentication
 * via a UserAccessAuthority. This servlet handles the login procedure and then
 * loops the requests through to plug-in moduls.
 * 
 * @author sautter
 */
public class AuthenticatedWebClientServlet extends GgServerHtmlServlet implements AuthenticatedWebClientConstants, UserAccessAuthorityConstants {
	
	/** the name of the GoldenGATE Server backed authentication source this servlet registers with the WebApp Host */
	public static final String GOLDEN_GATE_SERVER_AUTHENTICATION_PROVIDER_NAME = "GgServer";
	
	private WebAppHost webAppHost;
	
	private AuthenticatedWebClientModul[] clientModuls = new AuthenticatedWebClientModul[0];
	private HashMap clientModulsByName = new HashMap();
	
	private String servletPath = null;
	private String[] cssStylesheetsToInlcude;
	private String[] javaScriptsToInclude;
	private String[] functionsToCallOnLoad;
	private String[] functionsToCallOnUnload;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerHtmlServlet#init(de.uka.ipd.idaho.easyIO.settings.Settings)
	 */
	protected void init(Settings config) {
		
		//	get moduls
		this.clientModuls = this.loadModuls(new File(this.dataFolder, "Moduls"));
		
		//	collect CSS stylesheets and java scripts to load, and functions to execute on load and unload, and reqister moduls
		StringVector cssStylesheets = new StringVector();
		StringVector javaScripts = new StringVector();
		StringVector loadCalls = new StringVector();
		StringVector unloadCalls = new StringVector();
		for (int m = 0; m < this.clientModuls.length; m++) {
			String css = this.clientModuls[m].getCssToInclude();
			if (css != null) cssStylesheets.addElementIgnoreDuplicates(css);
			
			String[] jss = this.clientModuls[m].getJavaScriptsToInclude();
			if (jss != null) javaScripts.addContent(jss);
			
			String[] lcs = this.clientModuls[m].getJavaScriptLoadCalls();
			if (lcs != null) loadCalls.addContent(lcs);
			
			String[] ucs = this.clientModuls[m].getJavaScriptUnloadCalls();
			if (ucs != null) unloadCalls.addContent(ucs);
			
			this.clientModulsByName.put(this.clientModuls[m].getClass().getName(), this.clientModuls[m]);
		}
		
		//	add own stylesheet
		String[] cssNames = super.getCssStylesheets();
		if (cssNames != null)
			cssStylesheets.addContentIgnoreDuplicates(cssNames);
		
		//	remember inclusion data
		this.cssStylesheetsToInlcude = cssStylesheets.toStringArray();
		this.javaScriptsToInclude = javaScripts.toStringArray();
		this.functionsToCallOnLoad = loadCalls.toStringArray();
		this.functionsToCallOnUnload = unloadCalls.toStringArray();
		
		//	connect to host for session handling and register authentication provider
		this.webAppHost = WebAppHost.getInstance(this.getServletContext());
		this.webAppHost.addAuthenticationProvider(new AwcAuthProvider());
	}
	
	private class AwcAuthProvider extends AuthenticationProvider {
		public String getName() {
			return GOLDEN_GATE_SERVER_AUTHENTICATION_PROVIDER_NAME;
		}
		public String getLabel() {
			return "GoldenGATE Server";
		}
		public String authenticate(HttpServletRequest request) {
			HttpSession session = request.getSession(false);
			
			//	session exists, check if we authenticated it
			if (session != null) {
				AuthenticatedClient authClient = getAuthenticatedClient(session);
				return ((authClient == null) ? null : authClient.getUserName());
			}
			
			//	get login parameters
			String userName = request.getParameter(this.getName() + "_" + USER_NAME_PARAMETER);
			String password = request.getParameter(this.getName() + "_" + PASSWORD_PARAMETER);
			System.out.println(" - username is " + userName);
			System.out.println(" - password is " + password);
			
			//	not a login request, at least not for this authentication provider
			if ((userName == null) || (password == null))
				return null;
			
			//	try authenticating agains backing server
			AuthenticatedClient authClient = AuthenticatedClient.getAuthenticatedClient(serverConnection);
			try {
				if (authClient.login(userName, password)) {
					session = request.getSession(true);
					authClients.put(session.getId(), authClient);
					GgServerWebFrontendLogger.setDataForSession(session.getId(), userName, request.getRemoteAddr());
					return userName;
				}
				else return null;
			}
			catch (IOException ioe) {
				System.out.println("Could not authenticate - GoldenGATE Server not accessible.");
				return null;
			}
		}
		public void sessionLoggingOut(HttpSession session) {
			AuthenticatedClient authClient = getAuthenticatedClient(session);
			if (authClient != null) try {
				authClient.logout();
			}
			catch (IOException ioe) {
				//	swallow this message, exception is likely due to a session timeout on backing server
				System.out.println("Exception logging out from backing server: " + ioe.getMessage());
			}
			finally {
				authClients.remove(session.getId());
			}
			GgServerWebFrontendLogger.unmapSession(session.getId());
		}
		public boolean providesLoginFields() {
			return true;
		}
		public void writeLoginFields(HtmlPageBuilder pageBuilder) throws IOException {
			pageBuilder.writeLine("<table class=\"" + this.getName() + "_loginTable loginTable\">");
			pageBuilder.writeLine("<tr>");
			
			pageBuilder.write("<td class=\"" + this.getName() + "_loginTableLabelCell loginTableLabelCell\">");
			pageBuilder.write("User&nbsp;Name");
			pageBuilder.writeLine("</td>");
			pageBuilder.write("<td class=\"" + this.getName() + "_loginTableFieldCell loginTableFieldCell\">");
			pageBuilder.write("<input type=\"text\" name=\"" + this.getName() + "_" + USER_NAME_PARAMETER + "\">");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.write("<td class=\"" + this.getName() + "_loginTableLabelCell loginTableLabelCell\">");
			pageBuilder.write("Password");
			pageBuilder.writeLine("</td>");
			pageBuilder.write("<td class=\"" + this.getName() + "_loginTableFieldCell loginTableFieldCell\">");
			pageBuilder.write("<input type=\"password\" name=\"" + this.getName() + "_" + PASSWORD_PARAMETER + "\">");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("</tr>");
			pageBuilder.writeLine("</table>");
		}
		public void writeAccountManagerHtml(HtmlPageBuilder pageBuilder) throws IOException {
			pageBuilder.writeLine("<input type=\"button\" id=\"" + this.getName() + "_ChangePasswordButton\" value=\"Change Password\" onclick=\"" + this.getName() + "_showChangePasswordFields();\">");
			
			pageBuilder.writeLine("<div id=\"" + this.getName() + "_ChangePasswordFields\" style=\"position: absolute; display: none;\" onmouseover=\"" + this.getName() + "_changePasswordFields_mouseOver(event);\" onmouseout=\"" + this.getName() + "_changePasswordFields_mouseOut(event);\">");
			pageBuilder.writeLine("<table class=\"" + this.getName() + "_ChangePasswordFieldTable\">");
			
			pageBuilder.write("<tr>");
			pageBuilder.write("<td class=\"" + this.getName() + "_ChangePasswordFieldLabelCell\">");
			pageBuilder.write("Old&nbsp;Password");
			pageBuilder.write("</td>");
			pageBuilder.write("<td class=\"" + this.getName() + "_ChangePasswordFieldCell\">");
			pageBuilder.write("<input type=\"password\" id=\"" + this.getName() + "_oldPwd_input\" name=\"" + this.getName() + "_oldPwd" + "\">");
			pageBuilder.write("</td>");
			pageBuilder.writeLine("</tr>");
			
			pageBuilder.write("<tr>");
			pageBuilder.write("<td class=\"" + this.getName() + "_ChangePasswordFieldLabelCell\">");
			pageBuilder.write("New&nbsp;Password");
			pageBuilder.write("</td>");
			pageBuilder.write("<td class=\"" + this.getName() + "_ChangePasswordFieldCell\">");
			pageBuilder.write("<input type=\"password\" id=\"" + this.getName() + "_newPwd_input\" name=\"" + this.getName() + "_newPwd" + "\">");
			pageBuilder.write("</td>");
			pageBuilder.writeLine("</tr>");
			
			pageBuilder.write("<tr>");
			pageBuilder.write("<td class=\"" + this.getName() + "_ChangePasswordFieldLabelCell\">");
			pageBuilder.write("Confirm&nbsp;Password");
			pageBuilder.write("</td>");
			pageBuilder.write("<td class=\"" + this.getName() + "_ChangePasswordFieldCell\">");
			pageBuilder.write("<input type=\"password\" id=\"" + this.getName() + "_confirmPwd_input\" name=\"" + this.getName() + "_confirmPwd" + "\">");
			pageBuilder.write("</td>");
			pageBuilder.writeLine("</tr>");
			
			pageBuilder.write("<tr>");
			pageBuilder.write("<td class=\"" + this.getName() + "_ChangePasswordButtonCell\" colspan=\"2\">");
			pageBuilder.write("<input type=\"button\" id=\"" + this.getName() + "_changePwdButton\" value=\"Change Password\" onclick=\"" + this.getName() + "_doChangePassword();\">");
			pageBuilder.write("</td>");
			pageBuilder.writeLine("</tr>");
			
			pageBuilder.writeLine("</table>");
			pageBuilder.writeLine("</div>");
			pageBuilder.writeLine("<iframe id=\"" + this.getName() + "_ChangePasswordFrame\" style=\"width: 0px; height: 0px; border-width: 0px;\"></iframe>");
			
			pageBuilder.writeLine("<script type=\"text/javascript\">");
			
			pageBuilder.writeLine("var mouseOverCpf = false;");
			pageBuilder.writeLine("function " + this.getName() + "_changePasswordFields_mouseOver(event) {");
			pageBuilder.writeLine("  var cpf = document.getElementById('" + this.getName() + "_ChangePasswordFields');");
			pageBuilder.writeLine("  if ((cpf != null) && !webAppHost_isChild(cpf, event.relatedTarget))");
			pageBuilder.writeLine("    mouseOverCpf = true;");
			pageBuilder.writeLine("}");
			pageBuilder.writeLine("function " + this.getName() + "_changePasswordFields_mouseOut(event) {");
			pageBuilder.writeLine("  if (!mouseOverCpf)");
			pageBuilder.writeLine("    return;");
			pageBuilder.writeLine("  var cpf = document.getElementById('" + this.getName() + "_ChangePasswordFields');");
			pageBuilder.writeLine("  if ((cpf != null) && !webAppHost_isChild(cpf, event.relatedTarget))");
			pageBuilder.writeLine("    cpf.style.display = 'none';");
			pageBuilder.writeLine("}");
			pageBuilder.writeLine("function " + this.getName() + "_showChangePasswordFields() {");
			pageBuilder.writeLine("  var cpb = document.getElementById('" + this.getName() + "_ChangePasswordButton');");
			pageBuilder.writeLine("  var cpf = document.getElementById('" + this.getName() + "_ChangePasswordFields');");
			pageBuilder.writeLine("  if ((cpb == null) || (cpf == null))");
			pageBuilder.writeLine("    return;");
			pageBuilder.writeLine("  mouseOverCpf = false;");
			pageBuilder.writeLine("  var opi = document.getElementById('" + this.getName() + "_oldPwd_input');");
			pageBuilder.writeLine("  if (opi != null)");
			pageBuilder.writeLine("    opi.value = '';");
			pageBuilder.writeLine("  var npi = document.getElementById('" + this.getName() + "_newPwd_input');");
			pageBuilder.writeLine("  if (npi != null)");
			pageBuilder.writeLine("    npi.value = '';");
			pageBuilder.writeLine("  var cpi = document.getElementById('" + this.getName() + "_confirmPwd_input');");
			pageBuilder.writeLine("  if (cpi != null)");
			pageBuilder.writeLine("    cpi.value = '';");
			pageBuilder.writeLine("  cpf.style.display = '';");
			pageBuilder.writeLine("  var cpbMidX = cpb.offsetLeft + (cpb.offsetWidth / 2);");
			pageBuilder.writeLine("  var cpbMidY = cpb.offsetTop + (cpb.offsetHeight / 2);");
			pageBuilder.writeLine("  var cpbObj = cpb;");
			pageBuilder.writeLine("  while (cpbObj = cpbObj.offsetParent) {");
			pageBuilder.writeLine("    cpbMidX += cpbObj.offsetLeft;");
			pageBuilder.writeLine("    cpbMidY += cpbObj.offsetTop;");
			pageBuilder.writeLine("  }");
			pageBuilder.writeLine("  cpf.style.left = cpbMidX - cpf.offsetWidth;");
			pageBuilder.writeLine("  cpf.style.top = cpbMidY;");
			pageBuilder.writeLine("  if (opi != null)");
			pageBuilder.writeLine("    opi.focus();");
			pageBuilder.writeLine("}");
			
			pageBuilder.writeLine("function " + this.getName() + "_doChangePassword() {");
			pageBuilder.writeLine("  var opi = document.getElementById('" + this.getName() + "_oldPwd_input');");
			pageBuilder.writeLine("  if (opi == null)");
			pageBuilder.writeLine("    return false;");
			pageBuilder.writeLine("  var npi = document.getElementById('" + this.getName() + "_newPwd_input');");
			pageBuilder.writeLine("  if (npi == null)");
			pageBuilder.writeLine("    return false;");
			pageBuilder.writeLine("  var cpi = document.getElementById('" + this.getName() + "_confirmPwd_input');");
			pageBuilder.writeLine("  if (cpi == null)");
			pageBuilder.writeLine("    return false;");
			pageBuilder.writeLine("  if (opi.value == npi.value) {");
			pageBuilder.writeLine("    mouseOverCpf = false;");
			pageBuilder.writeLine("    alert('The new password matches the old one.');");
			pageBuilder.writeLine("    npi.value = '';");
			pageBuilder.writeLine("    cpi.value = '';");
			pageBuilder.writeLine("    var cpfs = document.getElementById('" + this.getName() + "_ChangePasswordFields');");
			pageBuilder.writeLine("    if (cpfs != null)");
			pageBuilder.writeLine("      cpfs.style.display = '';");
			pageBuilder.writeLine("    npi.focus();");
			pageBuilder.writeLine("    return false;");
			pageBuilder.writeLine("  }");
			pageBuilder.writeLine("  if (npi.value != cpi.value) {");
			pageBuilder.writeLine("    mouseOverCpf = false;");
			pageBuilder.writeLine("    alert('The new password does not match its confirmation.');");
			pageBuilder.writeLine("    npi.value = '';");
			pageBuilder.writeLine("    cpi.value = '';");
			pageBuilder.writeLine("    var cpfs = document.getElementById('" + this.getName() + "_ChangePasswordFields');");
			pageBuilder.writeLine("    if (cpfs != null)");
			pageBuilder.writeLine("      cpfs.style.display = '';");
			pageBuilder.writeLine("    npi.focus();");
			pageBuilder.writeLine("    return false;");
			pageBuilder.writeLine("  }");
			pageBuilder.writeLine("  var cpfr = document.getElementById('" + this.getName() + "_ChangePasswordFrame');");
			pageBuilder.writeLine("  var cpfo = cpfr.contentWindow.document.getElementById('changePwdForm');");
			pageBuilder.writeLine("  if (cpfo == null) {");
			pageBuilder.writeLine("    if (cpfr.src != '" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/webAppAuthProvider/form" + "')");
			pageBuilder.writeLine("      cpfr.src = '" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/webAppAuthProvider/form" + "';");
			pageBuilder.writeLine("    window.setTimeout('" + this.getName() + "_doChangePassword()', 100);");
			pageBuilder.writeLine("    return false;");
			pageBuilder.writeLine("  }");
			pageBuilder.writeLine("  else {");
			pageBuilder.writeLine("    var opf = cpfr.contentWindow.document.getElementById('oldPwd_field');");
			pageBuilder.writeLine("    if (opf == null)");
			pageBuilder.writeLine("      return false;");
			pageBuilder.writeLine("    var npf = cpfr.contentWindow.document.getElementById('newPwd_field');");
			pageBuilder.writeLine("    if (npf == null)");
			pageBuilder.writeLine("      return false;");
			pageBuilder.writeLine("    var cpf = cpfr.contentWindow.document.getElementById('confirmPwd_field');");
			pageBuilder.writeLine("    if (cpf == null)");
			pageBuilder.writeLine("      return false;");
			pageBuilder.writeLine("    opf.value = opi.value;");
			pageBuilder.writeLine("    npf.value = npi.value;");
			pageBuilder.writeLine("    cpf.value = cpi.value;");
			pageBuilder.writeLine("    cpfo.submit();");
			pageBuilder.writeLine("    window.setTimeout('" + this.getName() + "_readChangePasswordResult()', 100);");
			pageBuilder.writeLine("    var cpfs = document.getElementById('" + this.getName() + "_ChangePasswordFields');");
			pageBuilder.writeLine("    if (cpfs != null)");
			pageBuilder.writeLine("      cpfs.style.display = 'none';");
			pageBuilder.writeLine("    return false;");
			pageBuilder.writeLine("  }");
			pageBuilder.writeLine("}");
			pageBuilder.writeLine("function " + this.getName() + "_readChangePasswordResult() {");
			pageBuilder.writeLine("  var cpfr = document.getElementById('" + this.getName() + "_ChangePasswordFrame');");
			pageBuilder.writeLine("  var cprf = cpfr.contentWindow.document.getElementById('changePwdResult_field');");
			pageBuilder.writeLine("  if (cprf == null)");
			pageBuilder.writeLine("    window.setTimeout('" + this.getName() + "_readChangePasswordResult()', 100);");
			pageBuilder.writeLine("  else {");
			pageBuilder.writeLine("    alert(cprf.value);");
			pageBuilder.writeLine("    var cpfs = document.getElementById('" + this.getName() + "_ChangePasswordFields');");
			pageBuilder.writeLine("    if (cpfs != null)");
			pageBuilder.writeLine("      cpfs.style.display = 'none';");
			pageBuilder.writeLine("  }");
			pageBuilder.writeLine("}");
			pageBuilder.writeLine("function " + this.getName() + "_resetChangePasswordFields() {");
			pageBuilder.writeLine("  var cpf = document.getElementById('webAppHostChangePasswordFields');");
			pageBuilder.writeLine("  if (cpf == null)");
			pageBuilder.writeLine("    return;");
			pageBuilder.writeLine("  cpf.style.display = 'none';");
			pageBuilder.writeLine("  mouseOverCpf = false;");
			pageBuilder.writeLine("  var opi = document.getElementById('" + this.getName() + "_oldPwd_input');");
			pageBuilder.writeLine("  if (opi != null)");
			pageBuilder.writeLine("    opi.value = '';");
			pageBuilder.writeLine("  var npi = document.getElementById('" + this.getName() + "_newPwd_input');");
			pageBuilder.writeLine("  if (npi != null)");
			pageBuilder.writeLine("    npi.value = '';");
			pageBuilder.writeLine("  var cpi = document.getElementById('" + this.getName() + "_confirmPwd_input');");
			pageBuilder.writeLine("  if (cpi != null)");
			pageBuilder.writeLine("    cpi.value = '';");
			pageBuilder.writeLine("}");
			 
			pageBuilder.writeLine("</script>");
		}
		public String getAccountManagerOnclickCall() {
			return (this.getName() + "_resetChangePasswordFields()");
		}
		public String getAccountManagerOnshowCall() {
			return (this.getName() + "_resetChangePasswordFields()");
		}
		public String getAccountManagerOnhideCall() {
			return (this.getName() + "_resetChangePasswordFields()");
		}
		public boolean handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
			
			//	check path info
			String pathInfo = request.getPathInfo();
			if (pathInfo == null)
				return false;
			
			//	request for form
			if (pathInfo.endsWith("/form")) {
				response.setContentType("text/html");
				response.setCharacterEncoding("UTF-8");
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), "UTF-8"));
				bw.write("<html><body>");
				bw.write("<form id=\"changePwdForm\" method=\"POST\" action=\"" + request.getContextPath() + request.getServletPath() + "/webAppAuthProvider/changePwd" + "\">");
				bw.write("<input type=\"hidden\" id=\"oldPwd_field\" name=\"oldPwd\" value=\"\">");
				bw.write("<input type=\"hidden\" id=\"newPwd_field\" name=\"newPwd\" value=\"\">");
				bw.write("<input type=\"hidden\" id=\"confirmPwd_field\" name=\"confirmPwd\" value=\"\">");
				bw.write("</form>");
				bw.write("</body></html>");
				bw.flush();
				return true;
			}
			
			//	request actually changing password
			if (pathInfo.endsWith("/changePwd")) {
				String message = "";
				
				//	get authenticated cleint
				AuthenticatedClient authClient = getAuthenticatedClient(request.getSession(false));
				if (authClient == null)
					message = ("You are not authenticated against " + this.getLabel());
				
				//	change password
				else {
					String oldPassword = request.getParameter("oldPwd");
					String newPassword = request.getParameter("newPwd");
					String confirmPassword = request.getParameter("confirmPwd");
					if (newPassword.equals(confirmPassword)) {
						if (oldPassword.equals(newPassword))
							message = "Old and new passwords are the same, please try again.";
						else try {
							authClient.setPassword(oldPassword, newPassword);
							message = "Password changed successfully.";
						}
						catch (IOException ioe) {
							System.out.println("Could not change password of user '" + authClient.getUserName() + "'");
							ioe.printStackTrace(System.out);
							message = "Could not change password due to technical problems.";
						}
					}
					else message = "New password and confirmation do not match, please try again.";
				}
				
				//	send result
				response.setContentType("text/html");
				response.setCharacterEncoding("UTF-8");
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), "UTF-8"));
				bw.write("<html><body>");
				bw.write("<form id=\"changePwdResultForm\" action=\"#\">");
				bw.write("<input type=\"hidden\" id=\"changePwdResult_field\" name=\"changePwdResult\" value=\"" + message + "\">");
				bw.write("</form>");
				bw.write("</body></html>");
				bw.flush();
				return true;
			}
			
			//	this one's not for us
			return false;
		}
	}
	
	private AuthenticatedWebClientModul[] loadModuls(final File modulFolder) {
		
		//	get base directory
		if(!modulFolder.exists()) modulFolder.mkdir();
		
		//	load moduls
		Object[] modulObjects = GamtaClassLoader.loadComponents(
				modulFolder, 
				AuthenticatedWebClientModul.class, 
				new ComponentInitializer() {
					public void initialize(Object component, String componentJarName) throws Exception {
						((AuthenticatedWebClientModul) component).setParent(AuthenticatedWebClientServlet.this);
						File dataPath = new File(modulFolder, (componentJarName.substring(0, (componentJarName.length() - 4)) + "Data"));
						if (!dataPath.exists()) dataPath.mkdir();
						((AuthenticatedWebClientModul) component).setDataPath(dataPath);
					}
				});
		
		
		//	store & return moduls
		AuthenticatedWebClientModul[] moduls = new AuthenticatedWebClientModul[modulObjects.length];
		for (int m = 0; m < modulObjects.length; m++)
			moduls[m] = ((AuthenticatedWebClientModul) modulObjects[m]);
		return moduls;
	}
	
	private HashMap authClients = new HashMap();
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerHtmlServlet#getCssStylesheets()
	 */
	public String[] getCssStylesheets() {
		return this.cssStylesheetsToInlcude;
	}

	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerHtmlServlet#getJavaScriptFiles()
	 */
	public String[] getJavaScriptFiles() {
		return this.javaScriptsToInclude;
	}

	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerHtmlServlet#getOnloadCalls()
	 */
	public String[] getOnloadCalls() {
		return this.functionsToCallOnLoad;
	}

	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerHtmlServlet#getOnunloadCalls()
	 */
	public String[] getOnunloadCalls() {
		return this.functionsToCallOnUnload;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerHtmlServlet#getPageBuilder(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected HtmlPageBuilder getPageBuilder(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (this.servletPath == null)
			this.servletPath = request.getServletPath();
		
		//	check if request directed at webapp host
		if (this.webAppHost.handleRequest(request, response))
			return null;
		
		//	check authentication
		String userName = this.webAppHost.getUserName(request);
		if (userName == null)
			return this.webAppHost.getLoginPageBuilder(this, request, response, "includeBody", null);
		
		//	get modul name
		String modulName = request.getPathInfo();
		if (modulName != null) {
			while (modulName.startsWith("/"))
				modulName = modulName.substring(1);
			if (modulName.indexOf('/') != -1)
				modulName = modulName.substring(0, modulName.indexOf('/'));
		}
		
		//	get modul
		AuthenticatedWebClientModul modul = ((AuthenticatedWebClientModul) this.clientModulsByName.get(modulName));
		
		//	process request
		String[] messages = null;
		try {
			if (modul != null)
				messages = modul.handleRequest(request);
		}
		catch (Exception e) {
			messages = new String[1];
			messages[0] = ("Exception: " + e.getMessage());
		}
		
		//	display result
		response.setHeader("Cache-Control", "no-cache"); // no caching for modul pages
		response.setContentType("text/html");
		response.setCharacterEncoding("UTF-8");
		GgServerWebFrontendLogger.logForSession(request.getSession(false).getId(), "Request for modul " + modulName);
		return new ModulPageBuilder(messages, modulName, this, request, response);
	}
	
	/**
	 * Retrieve the authenticated client belonging to some HTTP session. If the
	 * argument session is not authenticated yet, this method returns null. This
	 * method is intended to share authentication with parts of a GoldenGATE
	 * Server web frontend that are implemented as fully blown servlets rather
	 * than authenticated web client modules.
	 * @param session the HTTP session
	 * @return the authenticated client belonging to the argument session
	 */
	public AuthenticatedClient getAuthenticatedClient(HttpSession session) {
		return ((session == null) ? null : ((AuthenticatedClient) this.authClients.get(session.getId())));
	}
	
	private class ModulPageBuilder extends HtmlPageBuilder {
		private String[] messages;
		private HttpSession session;
		private String modulName;
		private AuthenticatedWebClientModul modul;
		ModulPageBuilder(String[] messages, String modulName, GgServerHtmlServlet host, HttpServletRequest request, HttpServletResponse response) throws IOException {
			super(host, request, response);
			this.session = this.request.getSession(false);
			this.messages = messages;
			this.modulName = modulName;
			this.modul = ((AuthenticatedWebClientModul) clientModulsByName.get(this.modulName));
			if (this.modul == null) {
				for (int m = 0; m < clientModuls.length; m++) 
					if (clientModuls[m].displayFor(this.session)) {
						this.modul = clientModuls[m];
						break;
					}
			}
		}
		protected String getPageTitle(String title) {
			return ("GoldenGATE Server Manager" + ((this.modul == null) ? "" : (": " + this.modul.getModulLabel())));
		}
		protected void include(String type, String tag) throws IOException {
			if ("includeBody".equals(type)) {
				webAppHost.writeAccountManagerHtml(this, null);
				this.includeBody();
			}
			else super.include(type, tag);
		}
		void includeBody() throws IOException {
			this.writeLine("<div class=\"navigationMain\">");
			this.writeLine("<p class=\"navigation\">");
			
			AuthenticatedWebClientModul firstModul = null;
			for (int m = 0; m < clientModuls.length; m++) 
				if (clientModuls[m].displayFor(this.session)) {
					if (firstModul == null)
						firstModul = clientModuls[m];
					else this.write("&nbsp;|&nbsp;");
					this.write("<a href=\"" + this.request.getContextPath() + this.request.getServletPath() + "/" + clientModuls[m].getClass().getName() + "\">");
					this.write(clientModuls[m].getModulLabel());
					this.writeLine("</a>");
				}
			
			this.writeLine("</p>");
			this.writeLine("</div>");
			
			this.writeLine("<div class=\"main\">");
			
			if ((this.messages != null) && (this.messages.length != 0)) {
				
				//	open master table
				this.writeLine("<table class=\"messageTable\">");
				
				//	add header
				this.writeLine("<tr>");
				this.write("<td width=\"100%\" class=\"messageTableHeader\">");
				this.write("Messages");
				this.writeLine("</td>");
				this.writeLine("</tr>");
				
				//	include messages
				this.writeLine("<tr>");
				this.writeLine("<td width=\"100%\" class=\"messageTableBody\">");
				
				for (int m = 0; m < this.messages.length; m++) {
					if (this.messages[m] != null) {
						if (m != 0) this.writeLine("");
						this.write("<span class=\"message\">");
						this.write(IoTools.prepareForHtml(this.messages[m], HTML_CHAR_MAPPING));
						this.writeLine("</span>");
					}
				}
				this.writeLine("</td>");
				this.writeLine("</tr>");
				
				//	close master table
				this.writeLine("</table>");
				
				//	add spacer
				this.writeLine("<br>");
			}
			
			if (firstModul == null)
				this.writeLine("<p class=\"message\">No modules to display</p>");
			else if (this.modul == null)
				this.writeLine("<p class=\"message\">Unknown modul '" + this.modulName + "'</p>");
			else this.modul.writePageContent(this);
			this.writeLine("</div>");
		}
		protected String[] getOnloadCalls() {
			if (this.modul != null)
				return this.modul.getJavaScriptLoadCalls();
			else return super.getOnloadCalls();
		}
		protected String[] getOnunloadCalls() {
			if (this.modul != null)
				return this.modul.getJavaScriptUnloadCalls();
			else return super.getOnunloadCalls();
		}
		protected void writePageHeadExtensions() throws IOException {
			if (this.modul != null)
				this.modul.writePageHeadExtensions(this);
		}
	}
}
