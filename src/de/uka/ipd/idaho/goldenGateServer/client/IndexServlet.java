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
import java.io.IOException;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.IoTools;

/**
 * Servlet acting as a the main entry point to the web front-end of a GoldenGATE
 * Server. This servlet uses a configuration file for obtaining the links to
 * include in the page body. By default, this file is 'config.cnfg' in the
 * surrounding web-app's context path. It can be changed to another file
 * somewhere below the web-app's context path through specifying the alternative
 * file path and name in the value of the servlet's 'configFile' parameter in
 * the web.xml. From the configuration file, the servlet reads six parameters
 * for each link to display:
 * <ul>
 * <li><b>address</b>: the URL to link to (if the URL starts with 'http://' or
 * another protocol specification, it is interpreted as absolute; otherwise, it
 * is interpreted relative to the surrounding web-app's context path; for
 * linking relative to the context path using a specific protocol, use, for
 * instance, 'https://&lt;host&gt:&lt;port&gt&lt;path&gt/&lt;remainingLink&gt;',
 * with &lt;remainingLink&gt; being the link relative to the context path, and
 * '&lt;host&gt', '&lt;port&gt' and '&lt;path&gt;' being replaced by the actual
 * host, port and context path of an incoming request at runtime; for using a
 * JavaScript link, set the address to the desired JavaStript command and
 * indicate the use of JavaScript by means of the prefix 'javascript:')</li>
 * <li><b>target</b>: the target for the link (optional parameter, defaults to
 * '_self' if not given)</li>
 * <li><b>label</b>: the label for the link, i.e., the text to click for
 * following the link (will be displayed as a heading for the description, see
 * below)</li>
 * <li><b>icon</b>: the icon for the link, i.e., a small image to display left
 * of the label (optional parameter); the value is interpreted as a path
 * relative to the data path</li>
 * <li><b>title</b>: the title for the link (optional parameter)</li>
 * <li><b>description</b>: the description text for the link, explaining in
 * more detail what to do at the link's destination (optional parameter); if
 * starting with 'file:', the remainder of the value will be interpreted as a
 * file path relative to the data path; the file is expected to contain HTML
 * code, the content of whose body tag will be included as the link's
 * description</li>
 * </ul>
 * The links appear in the page in the order specified in the configuration
 * file. In addition to these five per-link parameters, this servlet requires
 * four additional global parameters:
 * <ul>
 * <li><b>dataPath</b>: the folder containing the raw file(s) to build the
 * link page from, relative to the surrounding web-app's context path</li>
 * <li><b>basePage</b>: the address of the file containing index page's base
 * structure, relative to the data path</li>
 * <li><b>stylesheet</b>: the address of the CSS style sheet to use for laying
 * out the link page (if the address starts with 'http://', it is interpreted as
 * absolute; otherwise, it is interpreted relative to the data path)</li>
 * <li><b>links</b>: the names of the links, separated by spaces, in the order
 * the links should be displayed in the page</li>
 * </ul>
 * 
 * @author sautter
 */
public class IndexServlet extends GgServerHtmlServlet {
	
	private String[] links = new String[0];
	private HashMap linksByName = new HashMap();
	
	private class Link {
		final String address;
		final String target;
		final String label;
		final String icon;
		final String title;
		final String description;
		Link(String address, String target, String label, String icon, String title, String description) {
			this.address = address;
			this.target = target;
			this.label = label;
			this.icon = icon;
			this.title = title;
			this.description = description;
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerClientServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	initialize logging
		GgServerWebFrontendLogger.setLogFolder(new File(this.webInfFolder, "logs"));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.easyIO.web.HtmlServlet#reInit()
	 */
	protected void reInit() throws ServletException {
		super.reInit();
		
		//	load link names & order
		String linkNames = this.getSetting("links", "");
		this.links = linkNames.split("\\s++");
		
		//	load links
		for (int l = 0; l < this.links.length; l++) {
			Settings linkSet = this.config.getSubset(this.links[l]);
			String linkUrl = linkSet.getSetting("address");
			String linkLabel = linkSet.getSetting("label");
			if ((linkUrl != null) && (linkLabel != null))
				this.linksByName.put(this.links[l], new Link(linkUrl, linkSet.getSetting("target"), linkLabel, linkSet.getSetting("icon"), linkSet.getSetting("title"), linkSet.getSetting("description")));
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerHtmlServlet#getPageBuilder(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected HtmlPageBuilder getPageBuilder(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setContentType("text/html");
		response.setHeader("Cache-Control", "no-cache");
		return new IndexPageBuilder(this, request, response);
	}
	
	private class IndexPageBuilder extends HtmlPageBuilder {
		
		IndexPageBuilder(GgServerHtmlServlet host, HttpServletRequest request, HttpServletResponse response) throws IOException {
			super(host, request, response);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerHtmlServlet.HtmlPageBuilder#include(java.lang.String, java.lang.String)
		 */
		protected void include(String type, String tag) throws IOException {
			if ("includeBody".equals(type))
				this.includeBody();
			else super.include(type, tag);
		}
		
		private void includeBody() throws IOException {
			if ((links.length == 0) || (linksByName.isEmpty()))
				return;
			
			this.writeLine("<div class=\"linkBox\">");
			this.writeLine("<table class=\"linkTable\">");
			
			for (int l = 0; l < links.length; l++) {
				Link link = ((Link) linksByName.get(links[l]));
				if (link != null) {
					
					String linkTag;
					String linkUrl = link.address;
					
					//	java script link
					if (linkUrl.startsWith("javascript:")) {
						String onclick = linkUrl.substring("javascript:".length()).trim();
						linkUrl = ".";
						linkTag = ("<a" +
								" href=\"" + linkUrl + "\"" + 
								" onclick=\"" + onclick + "\"" + 
								((link.title == null) ? "" : (" title=\"" + IoTools.prepareForHtml(link.title) + "\"")) + 
								">");
					}
					
					//	normal link
					else {
						if (linkUrl.indexOf("://") == -1)
							linkUrl = (this.request.getContextPath() + "/" + linkUrl);
						else {
							linkUrl = linkUrl.replaceAll("\\<host\\>", this.request.getServerName());
							linkUrl = linkUrl.replaceAll("\\<port\\>", (":" + this.request.getServerPort()));
							linkUrl = linkUrl.replaceAll("\\<path\\>", this.request.getContextPath());
							int linkUrlLength;
							do {
								linkUrlLength = linkUrl.length();
								linkUrl = linkUrl.replaceAll("\\/\\/", "/");
								linkUrl = linkUrl.replaceAll("\\/\\.\\/", "/");
							} while (linkUrl.length() < linkUrlLength);
							linkUrl = linkUrl.replaceAll("\\:\\/", "://");
						}
						linkTag = ("<a" +
								" href=\"" + linkUrl + "\"" + 
								((link.title == null) ? "" : (" title=\"" + IoTools.prepareForHtml(link.title) + "\"")) + 
								((link.target == null) ? "" : (" target=\"" + link.target + "\"")) + 
								">");
					}
					
					String iconUrl = null;
					if (link.icon != null) {
						iconUrl = link.icon;
						if (iconUrl.indexOf("://") == -1)
							iconUrl = (request.getContextPath() + getRelativeDataPath() + "/" + iconUrl);
					}
					
					this.writeLine("<tr>");
					this.writeLine("<td class=\"linkIconCell\">");
					
					if (iconUrl == null) {
						this.writeLine("&nbsp;");
					}
					else {
						this.write(linkTag);
						this.write("<span class=\"linkIcon\">");
						this.write("<img class=\"linkIconImage\" src=\"" + iconUrl + "\">");
						this.write("</span>");
						this.write("</a>");
						this.newLine();
					}
					
					this.writeLine("</td>");
					this.writeLine("<td class=\"linkCell\">");
					
					this.write(linkTag);
					this.write("<span class=\"linkLabel\">");
					this.write(IoTools.prepareForHtml(link.label));
					this.write("</span>");
					this.write("</a>");
					this.newLine();
					
					if ((link.description != null) && (link.description.length() != 0)) {
						this.writeLine("<br>");
						
						this.writeLine("<span class=\"linkDescription\">");
						
						if (link.description.startsWith("file:")) {
							String descriptionFile = link.description.substring("file:".length()).trim();
							includeFile(descriptionFile);
						}
						else this.writeLine(IoTools.prepareForHtml(link.description));
						
						this.writeLine("</span>");
					}
					
					this.writeLine("</td>");
					this.writeLine("</tr>");
				}
			}
			
			this.writeLine("</table>");
			this.writeLine("</div>");
		}
	}
}