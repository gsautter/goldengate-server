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
 *     * Neither the name of the Universit�t Karlsruhe (TH) nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY UNIVERSIT�T KARLSRUHE (TH) / KIT AND CONTRIBUTORS 
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


import java.util.Properties;

import de.uka.ipd.idaho.htmlXmlUtil.Parser;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.Html;

/**
 * constants for the authenticated web client
 * 
 * @author sautter
 */
public interface AuthenticatedWebClientConstants {
	
	/**
	 * The request parameter for specifying the actual action to take in an
	 * invokation of the handleRequest() method
	 */
	public static final String COMMAND_PARAMETER = "command";
	
	/**
	 * The parameter for including URL forwards in a login form
	 */
	public static final String FORWARD_URL_PARAMETER = "forwardUrl";
	
	/**
	 * Character mapping to use for HTML encoding special characters that do not
	 * display properly when represented as their equivalent HTML entity.
	 */
	public static final Properties HTML_CHAR_MAPPING = new Properties();
	
	/**
	 * HTML grammar to use for handling page content
	 */
	public static final Html HTML = new Html();
	
	/**
	 * Parser for HTML, especially for streaming page stubs and fill them with
	 * content.
	 */
	public static final Parser HTML_PARSER = new Parser(HTML);
	
	/**
	 * Helper class to bear initializer blocks, which cannot be embedded in an
	 * interface directly.
	 * 
	 * @author sautter
	 */
	public static class InitializerHelper {
		static {
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			
			HTML_CHAR_MAPPING.setProperty("�", "�");
			
			HTML_CHAR_MAPPING.setProperty("�", "�");
			
			HTML_CHAR_MAPPING.setProperty("�", "-");
			HTML_CHAR_MAPPING.setProperty("�", "-");
			
			HTML_CHAR_MAPPING.setProperty("'", "'");
//			HTML_CHAR_MAPPING.setProperty("�", "�");
//			HTML_CHAR_MAPPING.setProperty("�", "�");
//			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "'");
			HTML_CHAR_MAPPING.setProperty("�", "'");
			HTML_CHAR_MAPPING.setProperty("�", "'");
			
//			HTML_CHAR_MAPPING.setProperty("�", "�");
//			HTML_CHAR_MAPPING.setProperty("�", "�");
//			HTML_CHAR_MAPPING.setProperty("�", "�");
//			HTML_CHAR_MAPPING.setProperty("�", "�");
//			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "&quot;");
			HTML_CHAR_MAPPING.setProperty("�", "&quot;");
			HTML_CHAR_MAPPING.setProperty("�", "&quot;");
			HTML_CHAR_MAPPING.setProperty("�", "&quot;");
			HTML_CHAR_MAPPING.setProperty("�", "&quot;");
			
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			
			HTML_CHAR_MAPPING.setProperty("�", "�");
			
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("�", "�");
			HTML_CHAR_MAPPING.setProperty("=", "=");
		}
	}
}
