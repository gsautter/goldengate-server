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
package de.uka.ipd.idaho.goldenGateServer.uds;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;

import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants;
import de.uka.ipd.idaho.htmlXmlUtil.Parser;
import de.uka.ipd.idaho.htmlXmlUtil.TokenReceiver;
import de.uka.ipd.idaho.htmlXmlUtil.TreeNodeAttributeSet;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.Grammar;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.StandardGrammar;

/**
 * Constant bearer for GoldenGATE User Data Server
 * 
 * @author sautter
 */
public interface GoldenGateUdsConstants extends GoldenGateServerConstants {
	
	/**
	 * A set of fields to have a common header and be lain out together.
	 * 
	 * @author sautter
	 */
	public static class FieldSet {
		
		/** unique name of the field set. Consists of letters and digits only, no spaces */
		public final String name;
		
		/** label for the field set, to use in the header */
		public final String label;
		
		private ArrayList fields = new ArrayList();
		
		/** Constructor
		 * @param name unique name of the field set
		 * @param label label for the field set, to use in the header
		 */
		public FieldSet(String name, String label) {
			this.name = name;
			this.label = label;
		}
		
		/**
		 * Retrieve the fields belonging to this set.
		 * @return an array holding the fields belonging to this set
		 */
		public Field[] getFields() {
			return ((Field[]) this.fields.toArray(new Field[this.fields.size()]));
		}
		
		private void addField(Field field) {
			this.fields.add(field);
		}
		
		/**
		 * Write an XML description of this field set to a writer
		 * @param w the Writer to write to
		 * @throws IOException
		 */
		public void writeXml(Writer w) throws IOException {
			BufferedWriter bw = ((w instanceof BufferedWriter) ? ((BufferedWriter) w) : new BufferedWriter(w));
			
			bw.write("<fieldSet" + 
					" name=\"" + this.name + "\"" +
					" label=\"" + this.label + "\"" +
					">");
			bw.newLine();
			
			Field[] fields = this.getFields();
			for (int f = 0; f < fields.length; f++)
				fields[f].writeXml(bw);
			
			bw.write("</fieldSet>");
			bw.newLine();
			bw.flush();
		}
		
		/**
		 * Create one or more search field groups from the XML data provided by
		 * some Reader
		 * @param r the Reader to read from
		 * @return one or more SearchFieldGroup objects created from the XML
		 *         data provided by the specified Reader
		 * @throws IOException
		 */
		public static FieldSet[] readFieldSets(Reader r) throws IOException {
			final ArrayList fieldSets = new ArrayList();
			fieldsParser.stream(r, new TokenReceiver() {
				
				private FieldSet fieldSet = null;
				private Field field = null;
				
				public void close() throws IOException {
					if ((this.field != null) && (this.fieldSet != null))
						this.fieldSet.addField(this.field);
					this.field = null;
					
					if (this.fieldSet != null)
						fieldSets.add(this.fieldSet);
					this.fieldSet = null;
				}
				public void storeToken(String token, int treeDepth) throws IOException {
					if (fieldsGrammar.isTag(token)) {
						String tokenType = fieldsGrammar.getType(token); 
						
						if ("fieldSet".equals(tokenType)) {
							
							if (fieldsGrammar.isEndTag(token)) {
								if (this.fieldSet != null)
									fieldSets.add(this.fieldSet);
								this.fieldSet = null;
							}
							else {
								TreeNodeAttributeSet tokenAttributes = TreeNodeAttributeSet.getTagAttributes(token, fieldsGrammar);
								String name = tokenAttributes.getAttribute("name");
								if (name != null) {
									String label = tokenAttributes.getAttribute("label");
									this.fieldSet = new FieldSet(name, label);
								}
							}
						}
						else if ("field".equals(tokenType)) {
							
							if (fieldsGrammar.isEndTag(token)) {
								if ((this.fieldSet != null) && (this.field != null)) this.fieldSet.addField(this.field);
								this.field = null;
							}
							else if (fieldsGrammar.isSingularTag(token)) {
								if (this.fieldSet != null) {
									TreeNodeAttributeSet tokenAttributes = TreeNodeAttributeSet.getTagAttributes(token, fieldsGrammar);
									String name = tokenAttributes.getAttribute("name");
									if (name != null) {
										String label = tokenAttributes.getAttribute("label");
										String match = tokenAttributes.getAttribute("match");
										this.fieldSet.addField(new Field(name, label, match));
									}
								}
							}
							else {
								TreeNodeAttributeSet tokenAttributes = TreeNodeAttributeSet.getTagAttributes(token, fieldsGrammar);
								String name = tokenAttributes.getAttribute("name");
								if (name != null) {
									String label = tokenAttributes.getAttribute("label");
									String match = tokenAttributes.getAttribute("match");
									this.field = new Field(name, label, match);
								}
							}
						}
						else if ("option".equals(tokenType)) {
							if (!fieldsGrammar.isEndTag(token) && (this.field != null)) {
								TreeNodeAttributeSet tokenAttributes = TreeNodeAttributeSet.getTagAttributes(token, fieldsGrammar);
								String value = tokenAttributes.getAttribute("value");
								if (value != null)
									this.field.addOption(value);
							}
						}
					}
				}
			});
		
			return ((FieldSet[]) fieldSets.toArray(new FieldSet[fieldSets.size()]));
		}
		
		private static final Grammar fieldsGrammar = new StandardGrammar();
		private static final Parser fieldsParser = new Parser(fieldsGrammar);
	}
	
	/**
	 * A single data field.
	 * 
	 * @author sautter
	 */
	public static class Field {
		
		/** unique name of the field. Consists of letters and digits only, no spaces */
		public final String name;
		
		/** label for the field */
		public final String label;
		
		/** regular expression for validating the content of the field (null means no check) */
		public final String match;
		
		private ArrayList options = new ArrayList();
		
		/** Constructor
		 * @param name unique name of the field
		 * @param label label for the field
		 */
		public Field(String name, String label) {
			this(name, label, null);
		}
		
		/** Constructor
		 * @param name unique name of the field
		 * @param label label for the field
		 * @param match regular expression for validating the content of the field
		 */
		public Field(String name, String label, String match) {
			this.name = name;
			this.label = label;
			this.match = match;
		}
		
		/**
		 * Retrieve the values the field can have. If the field does not provide
		 * a specific set of values, this method returns null.
		 * @return an array holding the possible values for the field
		 */
		public String[] getOptions() {
			return (this.options.isEmpty() ? null : ((String[]) this.options.toArray(new String[this.options.size()])));
		}
		
		private void addOption(String option) {
			this.options.add(option);
		}
		
		/**
		 * Write an XML description of this field to a writer
		 * @param w the Writer to write to
		 * @throws IOException
		 */
		public void writeXml(Writer w) throws IOException {
			BufferedWriter bw = ((w instanceof BufferedWriter) ? ((BufferedWriter) w) : new BufferedWriter(w));
			
			if (this.options.isEmpty()) {
				bw.write("<field" + 
						" name=\"" + this.name + "\"" +
						" label=\"" + this.label + "\"" +
						((this.match == null) ? "" : (" match=\"" + this.match + "\"")) +
						"/>");
				bw.newLine();
			}
			else {
				bw.write("<field" + 
						" name=\"" + this.name + "\"" +
						" label=\"" + this.label + "\"" +
						">");
				bw.newLine();
				for (int o = 0; o < this.options.size(); o++) {
					bw.write("<option value=\"" + ((String) this.options.get(o)) + "\"/>");
					bw.newLine();
				}
				bw.write("</field>");
				bw.newLine();
			}
			
			if (bw != w)
				bw.flush();
		}
	}
	
	/**
	 * Container for user data, basically a Properties with on-board stream IO. 
	 * 
	 * @author sautter
	 */
	public static class UserDataSet extends Properties {
		public void writeData(Writer w) throws IOException {
			BufferedWriter bw = ((w instanceof BufferedWriter) ? ((BufferedWriter) w) : new BufferedWriter(w));
			for (Iterator dit = this.keySet().iterator(); dit.hasNext();) {
				String name = ((String) dit.next());
				String value = this.getProperty(name);
				if (value != null) {
//					System.out.println("UdsUserDataSet: wrote " + name + "=" + value);
					bw.write(name + "=" + value);
					bw.newLine();
				}
			}
			bw.newLine();
			if (bw != w)
				bw.flush();
		}
		public static UserDataSet readData(Reader r) throws IOException {
			BufferedReader br = ((r instanceof BufferedReader) ? ((BufferedReader) r) : new BufferedReader(r));
			UserDataSet data = new UserDataSet();
			String dataLine;
			while (((dataLine = br.readLine()) != null) && (dataLine.length() != 0)) {
				int split = dataLine.indexOf('=');
				if (split != -1) {
					String name = dataLine.substring(0, split);
					String value = dataLine.substring(split+1);
					data.setProperty(name, value);
//					System.out.println("UdsUserDataSet: read " + name + "=" + value);
				}
			}
			return data;
		}
	}
	
	public static final String GET_FIELDS = "UDS_GET_FIELDS";
	
	public static final String GET_DATA = "UDS_GET_DATA";
	
	public static final String UPDATE_DATA = "UDS_UPDATE_DATA";
}
