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
package de.uka.ipd.idaho.goldenGateServer.uds.client;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul;
import de.uka.ipd.idaho.goldenGateServer.uds.GoldenGateUdsConstants;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.IoTools;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Authenticated modul for managing personal data stored in a backing UDS.
 * 
 * @author sautter
 */
public class UserDataManagerModul extends AuthenticatedWebClientModul implements GoldenGateUdsConstants {
	
	private Map udsClientCache = Collections.synchronizedMap(new HashMap());
	private GoldenGateUdsClient getClient(AuthenticatedClient authClient) throws IOException {
		GoldenGateUdsClient udsc = ((GoldenGateUdsClient) this.udsClientCache.get(authClient.getSessionID()));
		if (udsc == null) {
			udsc = new GoldenGateUdsClient(authClient);
			this.udsClientCache.put(authClient.getSessionID(), udsc);
		}
		this.ensureFieldSets(udsc);
		return udsc;
	}
	
	private FieldSet[] fieldSets = null;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#getModulLabel()
	 */
	public String getModulLabel() {
		return "My Personal Data";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#displayFor(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient)
	 */
	public boolean displayFor(AuthenticatedClient authClient) {
		return true;
	}
	
	private synchronized void ensureFieldSets(GoldenGateUdsClient udsc) throws IOException {
		if (this.fieldSets == null)
			this.fieldSets = udsc.getFieldSets();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#handleRequest(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient, javax.servlet.http.HttpServletRequest)
	 */
	public String[] handleRequest(AuthenticatedClient authClient, HttpServletRequest request) throws IOException {
		StringVector messageCollector = new StringVector();
		
		//	update user data
		if (UPDATE_DATA.equals(request.getParameter(COMMAND_PARAMETER))) {
			GoldenGateUdsClient udsc = this.getClient(authClient);
			
			//	read & check data
			UserDataSet uds = new UserDataSet();
			for (int fs = 0; fs < this.fieldSets.length; fs++) {
				Field[] fields = this.fieldSets[fs].getFields();
				for (int f = 0; f < fields.length; f++) {
					String name = (this.fieldSets[fs].name + "." + fields[f].name);
					String value = request.getParameter(name);
					if (value != null) {
						if ((fields[f].match == null) || value.matches(fields[f].match))
							uds.setProperty(name, value);
						else {
							if (messageCollector.isEmpty())
								messageCollector.addElement("Personal data contains errors:");
							messageCollector.addElement(" - '" + IoTools.prepareForHtml(value, HTML_CHAR_MAPPING) + "' is no valid value for field '" + IoTools.prepareForHtml(fields[f].label, HTML_CHAR_MAPPING) + "'");
						}
					}
				}
			}
			
			//	do update
			if (messageCollector.isEmpty()) {
				udsc.updateData(uds);
				messageCollector.addElement("Personal data updated successfully.");
			}
		}
		
		return messageCollector.toStringArray();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.webClient.AuthenticatedWebClientModul#writePageContent(de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient, de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder)
	 */
	public void writePageContent(AuthenticatedClient authClient, HtmlPageBuilder pageBuilder) throws IOException {
		GoldenGateUdsClient udsc = this.getClient(authClient);
		
		//	get user data
		UserDataSet uds = udsc.getData();
		
		//	open form and add command
		pageBuilder.writeLine("<form method=\"POST\" action=\"" + pageBuilder.request.getContextPath() + pageBuilder.request.getServletPath() + "/" + this.getClass().getName() + "\">");
		pageBuilder.writeLine("<input type=\"hidden\" name=\"" + COMMAND_PARAMETER + "\" value=\"" + UPDATE_DATA + "\">");
		
		//	write field sets
		for (int fs = 0; fs < this.fieldSets.length; fs++) {
			
			//	open master table
			pageBuilder.writeLine("<table class=\"userDataTable\">");
			
			//	add head row
			pageBuilder.writeLine("<tr>");
			pageBuilder.writeLine("<td colspan=\"2\" class=\"userDataTableHeader\">");
			pageBuilder.writeLine(IoTools.prepareForHtml(this.fieldSets[fs].label, HTML_CHAR_MAPPING));
			pageBuilder.writeLine("</td>");
			pageBuilder.writeLine("</tr>");
			
			//	get fields
			Field[] fields = this.fieldSets[fs].getFields();
			for (int f = 0; f < fields.length; f++) {
				
				//	add data fields
				pageBuilder.writeLine("<tr>");
				
				pageBuilder.writeLine("<td class=\"userDataLabelCell\">");
				pageBuilder.writeLine(IoTools.prepareForHtml(fields[f].label, HTML_CHAR_MAPPING));
				pageBuilder.writeLine("</td>");
				
				String name = (this.fieldSets[fs].name + "." + fields[f].name);
				String value = pageBuilder.request.getParameter(name);
				if (value == null)
					value = uds.getProperty(name, "");
				String[] options = fields[f].getOptions();
				
				pageBuilder.writeLine("<td class=\"userDataFieldCell\">");
				
				//	text field
				if (options == null)
					pageBuilder.writeLine("<input name=\"" + name + "\" type=\"text\" value=\"" + value + "\" class=\"userDataField\">");
				
				//	checkbox
				else if (options.length == 1)
					pageBuilder.writeLine("<input name=\"" + name + "\" type=\"checkbox\" value=\"" + options[0] + "\" " + (options[0].equals(value) ? " checked" : "") + ">");
				
				//	radiobutton
				else if (options.length == 2) {
					pageBuilder.writeLine("<input name=\"" + name + "\" type=\"radio\" value=\"" + options[0] + "\" " + (!options[1].equals(value) ? " checked" : "") + ">");
					pageBuilder.writeLine(IoTools.prepareForHtml(options[0], HTML_CHAR_MAPPING));
					pageBuilder.writeLine("&nbsp;&nbsp;");
					pageBuilder.writeLine("<input name=\"" + name + "\" type=\"radio\" value=\"" + options[1] + "\" " + (options[1].equals(value) ? " checked" : "") + ">");
					pageBuilder.writeLine(IoTools.prepareForHtml(options[1], HTML_CHAR_MAPPING));
				}
				
				//	combobox
				else {
					pageBuilder.writeLine("<select name=\"" + name + "\" class=\"userDataField\">");
					for (int o = 0; o < options.length; o++) {
						pageBuilder.writeLine("<option value=\"" + options[o] + "\"" + (options[o].equals(value) ? " selected" : "") + ">");
						pageBuilder.writeLine(IoTools.prepareForHtml(options[o], HTML_CHAR_MAPPING));
						pageBuilder.writeLine("</option>");
					}
					pageBuilder.writeLine("</select>");
				}
				
				pageBuilder.writeLine("</td>");
				
				pageBuilder.writeLine("</tr>");
			}
			
			//	close table
			pageBuilder.writeLine("</table>");
			
			//	add spacer
			pageBuilder.writeLine("<br>");
		}
		
		//	add button
		pageBuilder.writeLine("<input type=\"submit\" value=\"Update Personal Data\" class=\"submitButton\">");
		
		//	close form
		pageBuilder.writeLine("</form>");
	}
}