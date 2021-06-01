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
package de.uka.ipd.idaho.goldenGateServer.utilities;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import de.uka.ipd.idaho.easyIO.web.WebAppHost;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;

/**
 * Authenticated module that allows server administrators to re-initialize
 * individual servlets or the entire web application via the respective
 * methods of the WebAppHost class.
 * 
 * @author sautter
 */
public class ReInitializerModul extends AuthenticatedWebClientModul {
	private WebAppHost webAppHost;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#init()
	 */
	protected void init() {
		this.webAppHost = WebAppHost.getInstance(this.parent.getServletContext());
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#getModulLabel()
	 */
	public String getModulLabel() {
		return "Servlet Refresher";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#displayFor(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient)
	 */
	public boolean displayFor(AuthenticatedClient authClient) {
		return authClient.isAdmin();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#handleRequest(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient, javax.servlet.http.HttpServletRequest)
	 */
	public String[] handleRequest(AuthenticatedClient authClient, HttpServletRequest request) throws IOException {
		
		//	get servlet name
		String servletName = request.getParameter("servletName");
		String[] resultMessage = {null};
		
		//	re-initialize entire webapp
		if ("all".equals(servletName)) try {
			this.webAppHost.reInitialize();
			resultMessage[0] = "Web application re-initialized successfully.";
		}
		catch (ServletException se) {
			resultMessage[0] = ("Re-initialization failed: " + se.getMessage());
		}
		
		//	re-initialize specified servlet
		else if (servletName != null) try {
			this.webAppHost.reInitialize(servletName);
			resultMessage[0] = (servletName + " re-initialized successfully.");
		}
		catch (ServletException se) {
			resultMessage[0] = ("Re-initialization failed: " + se.getMessage());
		}
		
		//	explain what we've done
		return resultMessage;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#writePageHeadExtensions(de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder)
	 */
	public void writePageHeadExtensions(HtmlPageBuilder pageBuilder) throws IOException {
		pageBuilder.writeLine("<script type=\"text/javascript\">");
		pageBuilder.writeLine("function doReInitialize(servletName) {");
		pageBuilder.writeLine("  var risnf = document.getElementById('reInitializeServletName');");
		pageBuilder.writeLine("  risnf.value = servletName;");
		pageBuilder.writeLine("  var rif = document.getElementById('reInitializerForm');");
		pageBuilder.writeLine("  rif.submit();");
		pageBuilder.writeLine("}");
		pageBuilder.writeLine("</script>");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#writePageContent(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient, de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder)
	 */
	public void writePageContent(AuthenticatedClient authClient, HtmlPageBuilder pageBuilder) throws IOException {
		
		//	add invisible refresher form
		pageBuilder.writeLine("<form id=\"reInitializerForm\" method=\"GET\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
		pageBuilder.writeLine("<input id=\"reInitializeServletName\" type=\"hidden\" name=\"servletName\" value=\"\">");
		pageBuilder.writeLine("</form>");
		
		//	build label row
		pageBuilder.writeLine("<table class=\"mainTable\">");
		pageBuilder.writeLine("<tr>");
		pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableHeader\">");
		pageBuilder.writeLine("Re-Initialize Web Application or Servlets");
		pageBuilder.writeLine("</td>");
		pageBuilder.writeLine("</tr>");
		
		//	open servlet table
		pageBuilder.writeLine("<tr>");
		pageBuilder.writeLine("<td width=\"100%\" class=\"mainTableBody\">");
		pageBuilder.writeLine("<table width=\"100%\" class=\"dataTable\">");
		
		//	add servlets
		String[] servletNames = this.webAppHost.getReInitializableServletNames();
		for (int s = 0; s < servletNames.length; s++) {
			pageBuilder.writeLine("<tr>");
			
			pageBuilder.writeLine("<td class=\"dataTableBody\">");
			pageBuilder.writeLine("Re-initialize servlet " + servletNames[s]);
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("<td class=\"dataTableBody\">");
			pageBuilder.writeLine("<input type=\"button\" value=\"Re-Initialize\" class=\"submitButton\" onclick=\"doReInitialize('" + servletNames[s] + "');\">");
			pageBuilder.writeLine("</td>");
			
			pageBuilder.writeLine("</tr>");
		}
		
		//	add general refresh
		pageBuilder.writeLine("<tr>");
		
		pageBuilder.writeLine("<td class=\"dataTableBody\">");
		pageBuilder.writeLine("Re-initialize entire web application");
		pageBuilder.writeLine("</td>");
		
		pageBuilder.writeLine("<td class=\"dataTableBody\">");
		pageBuilder.writeLine("<input type=\"button\" value=\"Re-Initialize\" class=\"submitButton\" onclick=\"doReInitialize('all');\">");
		pageBuilder.writeLine("</td>");
		
		pageBuilder.writeLine("</tr>");
		
		//	close servlet table
		pageBuilder.writeLine("</table>");
		pageBuilder.writeLine("</td>");
		pageBuilder.writeLine("</tr>");
		
		//	close master table
		pageBuilder.writeLine("</table>");
	}
}