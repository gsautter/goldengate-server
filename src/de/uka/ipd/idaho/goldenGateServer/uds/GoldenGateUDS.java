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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import de.uka.ipd.idaho.easyIO.EasyIO;
import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.SqlQueryResult;
import de.uka.ipd.idaho.easyIO.sql.TableDefinition;
import de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponentRegistry;
import de.uka.ipd.idaho.goldenGateServer.uaa.UserAccessAuthority;
import de.uka.ipd.idaho.goldenGateServer.uaa.UserDataProvider;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * The GoldenGATE User Data Server is responsible for holding the personal data,
 * like eMail address, real name, etc. Which fields are available and which data
 * they may contain depends on the configuration file of UDS.
 * 
 * @author sautter
 */
public class GoldenGateUDS extends AbstractGoldenGateServerComponent implements GoldenGateUdsConstants, UserDataProvider {
	
	private UserAccessAuthority uaa = null;
	
	private FieldSet[] fieldSets = new FieldSet[0];
	
	private IoProvider io;
	
	private static final String DATA_TABLE_NAME = "UdsData";
	private static final String FIELD_NAME_COLUMN_NAME = "FieldName";
	private static final int FIELD_NAME_COLUMN_LENGTH = 64;
	private static final String USER_NAME_COLUMN_NAME = "UserName";
	private static final String FIELD_VALUE_COLUMN_NAME = "FieldValue";
	private static final int FIELD_VALUE_COLUMN_LENGTH = 256;
	
	/** Constructor passing 'UDS' as the letter code to super constructor
	 */
	public GoldenGateUDS() {
		super("UDS");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#initComponent()
	 */
	protected void initComponent() {

		//	get and check database connection
		this.io = this.host.getIoProvider();
		if (!this.io.isJdbcAvailable())
			throw new RuntimeException("GoldenGateUDS: Cannot work without database access.");
		
		//	produce data table
		TableDefinition td = new TableDefinition(DATA_TABLE_NAME);
		td.addColumn(USER_NAME_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, UserAccessAuthority.USER_NAME_MAX_LENGTH);
		td.addColumn(FIELD_NAME_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, FIELD_NAME_COLUMN_LENGTH);
		td.addColumn(FIELD_VALUE_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, FIELD_VALUE_COLUMN_LENGTH);
		if (!this.io.ensureTable(td, true))
			throw new RuntimeException("GoldenGateUDS: Cannot work without database access.");
		
		//	index data table
		this.io.indexColumn(DATA_TABLE_NAME, USER_NAME_COLUMN_NAME);
		this.io.indexColumn(DATA_TABLE_NAME, FIELD_NAME_COLUMN_NAME);
		
		//	read fields
		this.readFieldSets();
	}
	
	private void readFieldSets() {
		String fieldSetFileName = this.configuration.getSetting("fieldSetFile", "Fields.xml");
		try {
			Reader fsr = new InputStreamReader(new FileInputStream(new File(this.dataPath, fieldSetFileName)), "UTF-8");
			FieldSet[] fieldSets = FieldSet.readFieldSets(fsr);
			fsr.close();
			this.fieldSets = fieldSets;
		}
		catch (IOException ioe) {
			System.out.println("Error reading field set definition: " + ioe.getMessage());
			ioe.printStackTrace(System.out);
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#exitComponent()
	 */
	protected void exitComponent() {
		//	disconnect from database
		this.io.close();
	}
	
	/*
	 * (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#link()
	 */
	public void link() {

		// get access authority
		this.uaa = ((UserAccessAuthority) GoldenGateServerComponentRegistry.getServerComponent(UserAccessAuthority.class.getName()));

		// check success
		if (this.uaa == null) throw new RuntimeException(UserAccessAuthority.class.getName());
		
		//	link up to access authority
		else this.uaa.setUserDataProvider(this);
	}
	
	
	private static final String UPDATE_FIELDS_COMMAND = "updateFields";
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent#getActions()
	 */
	public ComponentAction[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentAction ca;
		
		//	request for fields
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_FIELDS;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	send data
				output.write(GET_FIELDS);
				output.newLine();
				for (int f = 0; f < fieldSets.length; f++)
					fieldSets[f].writeXml(output);
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	request for user data
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_DATA;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {

				// check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				
				//	get parameter
				String userName = input.readLine();
				if (userName.length() == 0)
					userName = uaa.getUserNameForSession(sessionId);
				
				//	check permission
				if (!userName.equals(uaa.getUserNameForSession(sessionId)) && !uaa.isAdminSession(sessionId)) {
					output.write("Accessing the data of another user requires administrative priviledges.");
					output.newLine();
					return;
				}
				
				//	retrieve data
				UserDataSet uds = getData(userName);
				
				//	send data
				output.write(GET_DATA);
				output.newLine();
				uds.writeData(output);
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	user data update
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return UPDATE_DATA;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {

				// check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				
				//	get parameter
				String userName = input.readLine();
				if (userName.length() == 0)
					userName = uaa.getUserNameForSession(sessionId);
				
				//	check permission
				if (!userName.equals(uaa.getUserNameForSession(sessionId)) && !uaa.isAdminSession(sessionId)) {
					output.write("Updating the data of another user requires administrative priviledges.");
					output.newLine();
					return;
				}
				
				//	retrieve data
				UserDataSet uds = UserDataSet.readData(input);
				
				//	do update
				updateData(uds, userName);
				
				//	send data
				output.write(UPDATE_DATA);
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	reload fields to allow deploying modifications at runtime
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return UPDATE_FIELDS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = { UPDATE_FIELDS_COMMAND, "Reload the field set definitions." };
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0)
					readFieldSets();
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);

		return ((ComponentAction[]) cal.toArray(new ComponentAction[cal.size()]));
	}
	
	/**
	 * Retrieve the data fields.
	 * @return the data fields, packed in an array
	 * @throws IOException
	 */
	public FieldSet[] getFields() {
		FieldSet[] fieldSets = new FieldSet[this.fieldSets.length];
		System.arraycopy(this.fieldSets, 0, fieldSets, 0, fieldSets.length);
		return fieldSets;
	}
	
	private LinkedHashMap userDataCache = new LinkedHashMap(16, 0.75f, true) {
		protected boolean removeEldestEntry(Entry e) {
			return this.size() > 64;
		}
	};
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserDataProvider#getUserProperty(java.lang.String, java.lang.String)
	 */
	public String getUserProperty(String user, String name) {
		return this.getUserProperty(user, name, null);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserDataProvider#getUserProperty(java.lang.String, java.lang.String, java.lang.String)
	 */
	public String getUserProperty(String user, String name, String def) {
		UserDataSet uds = this.getData(user);
		return ((uds == null) ? def : uds.getProperty(name, def));
	}
	
	/**
	 * Retrieve the data of a specific user.
	 * @param userName the name of the user.
	 * @return the data of the specified user
	 */
	public UserDataSet getData(String userName) {
		UserDataSet uds = ((UserDataSet) this.userDataCache.get(userName));
		if (uds != null) return uds;
		
		String query = "SELECT " + FIELD_NAME_COLUMN_NAME + ", " + FIELD_VALUE_COLUMN_NAME + 
				" FROM " + DATA_TABLE_NAME + 
				" WHERE " + USER_NAME_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(userName) + "'" +
				";";
		
		SqlQueryResult sqr = null;
		try {
			sqr = this.io.executeSelectQuery(query);
			uds = new UserDataSet();
			while (sqr.next()) {
				String name = sqr.getString(0);
				String value = sqr.getString(1);
				if (value != null)
					uds.setProperty(name, value);
			}
			this.userDataCache.put(userName, uds);
			return uds;
		}
		catch (SQLException sqle) {
			System.out.println("GoldenGateUDS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while loading document checkout user.");
			System.out.println("  query was " + query);
			return null;
		}
		finally {
			if (sqr != null) sqr.close();
		}
	}
	
	private void updateData(UserDataSet data, String userName) {
		UserDataSet oldData = this.getData(userName);
		StringVector updateQueries = new StringVector();
		
		//	collect updates
		for (Iterator dit = data.keySet().iterator(); dit.hasNext();) {
			String name = ((String) dit.next());
			String value = data.getProperty(name);
			if ((value == null) || (value.length() == 0))
				continue;
			
			if (value.length() > FIELD_VALUE_COLUMN_LENGTH)
				value = value.substring(0, FIELD_VALUE_COLUMN_LENGTH);
			
			if (oldData.containsKey(name)) {
				if (!value.equals(oldData.getProperty(name))) {
					updateQueries.addElement("UPDATE " + DATA_TABLE_NAME + 
								" SET " + FIELD_VALUE_COLUMN_NAME + " = '" + EasyIO.sqlEscape(value) + "'" + 
								" WHERE " + USER_NAME_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(userName) + "'" +
										" AND " + FIELD_NAME_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(name) + "'" +
								";");
				}
				oldData.remove(name);
			}
			else updateQueries.addElement("INSERT INTO " + DATA_TABLE_NAME + " (" + 
					USER_NAME_COLUMN_NAME + ", " + FIELD_NAME_COLUMN_NAME + ", " + FIELD_VALUE_COLUMN_NAME + 
					") VALUES (" +
					"'" + EasyIO.sqlEscape(userName) + "', '" + EasyIO.sqlEscape(name) + "', '" + EasyIO.sqlEscape(value) + "'" + 
					");");
		}
		
//		//	trigger deletion of obsolete entries
//		for (Iterator dit = oldData.keySet().iterator(); dit.hasNext();) {
//			String name = ((String) dit.next());
//			updateQueries.addElement("DELETE FROM " + DATA_TABLE_NAME + 
//					" WHERE " + USER_NAME_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(userName) + "'" +
//							" AND " + FIELD_NAME_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(name) + "'" +
//					";");
//		}
//		TODO assess in deployment if this is necessary
		
		//	do updates
		for (int q = 0; q < updateQueries.size(); q++) {
			String updateQuery = updateQueries.get(q);
			try {
				this.io.executeUpdateQuery(updateQuery);
			}
			catch (SQLException sqle) {
				System.out.println("GoldenGateUDS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while updating existing document.");
				System.out.println("  query was " + updateQuery);
			}
		}
		
		this.userDataCache.put(userName, data);
	}
}
