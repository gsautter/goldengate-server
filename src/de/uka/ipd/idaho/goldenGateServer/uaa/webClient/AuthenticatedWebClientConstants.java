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
			HTML_CHAR_MAPPING.setProperty("À", "À");
			HTML_CHAR_MAPPING.setProperty("Á", "Á");
			HTML_CHAR_MAPPING.setProperty("Â", "Â");
			HTML_CHAR_MAPPING.setProperty("Ã", "Ã");
			HTML_CHAR_MAPPING.setProperty("Ä", "Ä");
			HTML_CHAR_MAPPING.setProperty("Å", "Å");
			HTML_CHAR_MAPPING.setProperty("Æ", "Æ");
			HTML_CHAR_MAPPING.setProperty("à", "à");
			HTML_CHAR_MAPPING.setProperty("á", "á");
			HTML_CHAR_MAPPING.setProperty("â", "â");
			HTML_CHAR_MAPPING.setProperty("ã", "ã");
			HTML_CHAR_MAPPING.setProperty("ä", "ä");
			HTML_CHAR_MAPPING.setProperty("å", "å");
			HTML_CHAR_MAPPING.setProperty("æ", "æ");
			
			HTML_CHAR_MAPPING.setProperty("Ç", "Ç");
			HTML_CHAR_MAPPING.setProperty("ç", "ç");
			
			HTML_CHAR_MAPPING.setProperty("È", "È");
			HTML_CHAR_MAPPING.setProperty("É", "É");
			HTML_CHAR_MAPPING.setProperty("Ê", "Ê");
			HTML_CHAR_MAPPING.setProperty("Ë", "Ë");
			HTML_CHAR_MAPPING.setProperty("è", "è");
			HTML_CHAR_MAPPING.setProperty("é", "é");
			HTML_CHAR_MAPPING.setProperty("ê", "ê");
			HTML_CHAR_MAPPING.setProperty("ë", "ë");
			
			HTML_CHAR_MAPPING.setProperty("Ì", "Ì");
			HTML_CHAR_MAPPING.setProperty("Í", "Í");
			HTML_CHAR_MAPPING.setProperty("Î", "Î");
			HTML_CHAR_MAPPING.setProperty("Ï", "Ï");
			HTML_CHAR_MAPPING.setProperty("ì", "ì");
			HTML_CHAR_MAPPING.setProperty("í", "í");
			HTML_CHAR_MAPPING.setProperty("î", "î");
			HTML_CHAR_MAPPING.setProperty("ï", "ï");
			
			HTML_CHAR_MAPPING.setProperty("Ñ", "Ñ");
			HTML_CHAR_MAPPING.setProperty("ñ", "ñ");
			
			HTML_CHAR_MAPPING.setProperty("Ò", "Ò");
			HTML_CHAR_MAPPING.setProperty("Ó", "Ó");
			HTML_CHAR_MAPPING.setProperty("Ô", "Ô");
			HTML_CHAR_MAPPING.setProperty("Õ", "Õ");
			HTML_CHAR_MAPPING.setProperty("Ö", "Ö");
			HTML_CHAR_MAPPING.setProperty("Œ", "Œ");
			HTML_CHAR_MAPPING.setProperty("Ø", "Ø");
			HTML_CHAR_MAPPING.setProperty("ò", "ò");
			HTML_CHAR_MAPPING.setProperty("ó", "ó");
			HTML_CHAR_MAPPING.setProperty("ô", "ô");
			HTML_CHAR_MAPPING.setProperty("õ", "õ");
			HTML_CHAR_MAPPING.setProperty("ö", "ö");
			HTML_CHAR_MAPPING.setProperty("œ", "œ");
			HTML_CHAR_MAPPING.setProperty("ø", "ø");
			
			HTML_CHAR_MAPPING.setProperty("Ù", "Ù");
			HTML_CHAR_MAPPING.setProperty("Ú", "Ú");
			HTML_CHAR_MAPPING.setProperty("Û", "Û");
			HTML_CHAR_MAPPING.setProperty("Ü", "Ü");
			HTML_CHAR_MAPPING.setProperty("ù", "ù");
			HTML_CHAR_MAPPING.setProperty("ú", "ú");
			HTML_CHAR_MAPPING.setProperty("û", "û");
			HTML_CHAR_MAPPING.setProperty("ü", "ü");
			
			HTML_CHAR_MAPPING.setProperty("Ý", "Ý");
			HTML_CHAR_MAPPING.setProperty("ý", "ý");
			HTML_CHAR_MAPPING.setProperty("ÿ", "ÿ");
			
			HTML_CHAR_MAPPING.setProperty("ß", "ß");
			
			HTML_CHAR_MAPPING.setProperty("€", "€");
			
			HTML_CHAR_MAPPING.setProperty("–", "-");
			HTML_CHAR_MAPPING.setProperty("—", "-");
			
			HTML_CHAR_MAPPING.setProperty("'", "'");
//			HTML_CHAR_MAPPING.setProperty("‘", "‘");
//			HTML_CHAR_MAPPING.setProperty("’", "’");
//			HTML_CHAR_MAPPING.setProperty("‚", "‚");
			HTML_CHAR_MAPPING.setProperty("‘", "'");
			HTML_CHAR_MAPPING.setProperty("’", "'");
			HTML_CHAR_MAPPING.setProperty("‚", "'");
			
//			HTML_CHAR_MAPPING.setProperty("“", "“");
//			HTML_CHAR_MAPPING.setProperty("”", "”");
//			HTML_CHAR_MAPPING.setProperty("„", "„");
//			HTML_CHAR_MAPPING.setProperty("‹", "‹");
//			HTML_CHAR_MAPPING.setProperty("›", "›");
			HTML_CHAR_MAPPING.setProperty("“", "&quot;");
			HTML_CHAR_MAPPING.setProperty("”", "&quot;");
			HTML_CHAR_MAPPING.setProperty("„", "&quot;");
			HTML_CHAR_MAPPING.setProperty("‹", "&quot;");
			HTML_CHAR_MAPPING.setProperty("›", "&quot;");
			
			HTML_CHAR_MAPPING.setProperty("†", "†");
			HTML_CHAR_MAPPING.setProperty("‡", "‡");
			
			HTML_CHAR_MAPPING.setProperty("…", "…");
			
			HTML_CHAR_MAPPING.setProperty("‰", "‰");
			HTML_CHAR_MAPPING.setProperty("™", "™");
			HTML_CHAR_MAPPING.setProperty("=", "=");
		}
	}
}
