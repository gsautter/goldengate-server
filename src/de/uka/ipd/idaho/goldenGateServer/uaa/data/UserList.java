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
 *     * Neither the name of the Universitaet Karlsruhe (TH) nor the
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
package de.uka.ipd.idaho.goldenGateServer.uaa.data;


import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;

import de.uka.ipd.idaho.gamta.AnnotationUtils;
import de.uka.ipd.idaho.goldenGateServer.uaa.UserAccessAuthorityConstants;
import de.uka.ipd.idaho.htmlXmlUtil.Parser;
import de.uka.ipd.idaho.htmlXmlUtil.TokenReceiver;
import de.uka.ipd.idaho.htmlXmlUtil.TreeNodeAttributeSet;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.Grammar;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.StandardGrammar;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringRelation;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringTupel;

/**
 * 
 */

/**
 * The List of the SRS users, for administrative purposes
 * 
 * @author sautter
 */
public class UserList extends StringRelation implements UserAccessAuthorityConstants {
	
	/** the name of the XML element enclosing the list of users*/
	private static final String USERS_NODE_NAME = "users";
	
	/** the name of the XML element enclosing an individual user*/
	private static final String USER_NODE_NAME = "user";
	
	/** the field names for the user list, in the order they should be displayed (for dynamically adding fields on server side) */
	private static final String[] USER_LIST_FIELS_NAMES = {USER_NAME_PARAMETER, IS_ADMIN_PARAMETER};
	
	/** Constructor
	 */
	public UserList() {}
	
	/**	write this user list result to some writer as XML data
	 * @param	out		the Writer to write to
	 * @throws IOException
	 */
	public void writeXml(Writer out) throws IOException {
		
		//	produce writer
		BufferedWriter buf = ((out instanceof BufferedWriter) ? ((BufferedWriter) out) : new BufferedWriter(out));
		
		//	write empty data
		if (this.size() == 0) {
			
			//	write results
			buf.write("<" + USERS_NODE_NAME + "/>");
			buf.newLine();
		}
		
		//	write data
		else {
			
			//	get result field names
			buf.write("<" + USERS_NODE_NAME + ">");
			
			//	write tupels
			for (int r = 0; r < this.size(); r++) {
				StringTupel tre = this.get(r);
				buf.write("  <" + USER_NODE_NAME);
				for (int f = 0; f < USER_LIST_FIELS_NAMES.length; f++) {
					String userListField = USER_LIST_FIELS_NAMES[f];
					String userListFieldValue = tre.getValue(userListField);
					if ((userListFieldValue != null) && (userListFieldValue.length() != 0))
						buf.write(" " + userListField + "=\"" + AnnotationUtils.escapeForXml(userListFieldValue, true) + "\"");
				}
				buf.write("/>");
				buf.newLine();
			}
			
			//	close result
			buf.write("</" + USERS_NODE_NAME + ">");
			buf.newLine();
		}
		
		buf.flush();
	}
	
	/**
	 * read a user list from a reader (this method is basically for restoring a
	 * UserList from character data written out via the writeXml() method)
	 * @param in the reader to read from
	 * @return the user list read from the specified reader
	 * @throws IOException
	 */
	public static UserList readUserList(Reader in) throws IOException {
		final ArrayList userDataList = new ArrayList();
		final Grammar grammar = new StandardGrammar();
		final Parser parser = new Parser(grammar);
		
//		try {
			TokenReceiver sr = new TokenReceiver() {
				public void close() throws IOException {}
				public void storeToken(String token, int treeDepth) throws IOException {
					if (grammar.isTag(token) && !grammar.isEndTag(token)) {
						if (UserList.USER_NODE_NAME.equals(grammar.getType(token)) && !grammar.isEndTag(token)) {
							TreeNodeAttributeSet tnas = TreeNodeAttributeSet.getTagAttributes(token, grammar);
							StringTupel st = new StringTupel();
							String[] attributeNames = tnas.getAttributeNames();
							for (int a = 0; a < attributeNames.length; a++) {
								String attributeValue = tnas.getAttribute(attributeNames[a]);
								if (attributeValue != null)
									st.setValue(attributeNames[a], attributeValue);
							}
							
							if (st.size() != 0)
								userDataList.add(st);
						}
					}
				}
			};
			
			parser.stream(in, sr);
			
			UserList userList = new UserList();
			for (int e = 0; e < userDataList.size(); e++)
				userList.addElement((StringTupel) userDataList.get(e));
			return userList;
//		}
//		catch (ParseException pe) {
//			throw new IOException(pe.getMessage());
//		}
	}
}
