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
 *     * Neither the name of the Universität Karlsruhe (TH) / KIT nor the
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
package de.uka.ipd.idaho.goldenGateServer.aaa;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import de.uka.ipd.idaho.easyIO.EasyIO;
import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.SqlQueryResult;
import de.uka.ipd.idaho.easyIO.sql.TableDefinition;
import de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponentRegistry;
import de.uka.ipd.idaho.goldenGateServer.uaa.UserAccessAuthority;
import de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthority;

/**
 * API Access Authority is responsible for authenticating API access tokens as
 * well as for mapping them to users and permissions.
 * 
 * @author sautter
 */
public class ApiAccessAuthority extends AbstractGoldenGateServerComponent implements ApiAccessAuthorityConstants {
	private static final String TOKEN_TABLE_NAME = "GgAaaTokens";
	private static final String TOKEN_COLUMN_NAME = "Token";
	private static final int TOKEN_LENGTH = 64;
	private static final String TOKEN_HASH_COLUMN_NAME = "TokenHash";
	private static final String USER_NAME_COLUMN_NAME = "UserName";
	
	private IoProvider io;
	private UserAccessAuthority uaa;
	private UserPermissionAuthority upa;
	
	/** Constructor passing 'AAA' as the letter code to super constructor
	 */
	public ApiAccessAuthority() {
		super("AAA");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#initComponent()
	 */
	protected void initComponent() {
		
		//	get and check database connection
		this.io = this.host.getIoProvider();
		if (!this.io.isJdbcAvailable())
			throw new RuntimeException("API Access Authority cannot work without database access.");
		
		//	ensure token table
		TableDefinition td = new TableDefinition(TOKEN_TABLE_NAME);
		td.addColumn(TOKEN_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, TOKEN_LENGTH);
		td.addColumn(TOKEN_HASH_COLUMN_NAME, TableDefinition.INT_DATATYPE, 0);
		td.addColumn(USER_NAME_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
		if (!this.io.ensureTable(td, true))
			throw new RuntimeException("API Access Authority cannot work without database access.");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#link()
	 */
	public void link() {
		
		//	hook up to UAA
		this.uaa = ((UserAccessAuthority) GoldenGateServerComponentRegistry.getServerComponent(UserAccessAuthority.class.getName()));
		
		//	check success
		if (this.uaa == null) throw new RuntimeException(UserAccessAuthority.class.getName());
	}

	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#linkInit()
	 */
	public void linkInit() {
		
		//	register permission to use API access token
		this.uaa.registerPermission(USE_API_TOKEN_PERMISSION);
		
		//	get permission authority
		this.upa = this.uaa.getUserPermissionAuthority();
	}
	
	private static final String CREATE_TOKEN_COMMAND = "createToken";
	private static final String SHOW_TOKEN_COMMAND = "showToken";
	private static final String DELETE_TOKEN_COMMAND = "deleteToken";
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#getActions()
	 */
	public ComponentAction[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentAction ca;
		
		//	create user
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return CREATE_TOKEN_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						CREATE_TOKEN_COMMAND + " <userName>",
						"Create a new API token for a user:",
						"- <userName>: the name of the user to create the token for",
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1) {
					String token = createUserToken(arguments[0]);
					if (token == null)
						this.reportError(" Could not create token for user '" + arguments[0] + "'.");
					else this.reportResult(" Token for user '" + arguments[0] + "' created as " + token);
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the username as the only argument.");
			}
		};
		cal.add(ca);
		
		//	show token
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return SHOW_TOKEN_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						SHOW_TOKEN_COMMAND + " <userName>",
						"Show the API token of a user:",
						"- <userName>: the name of the user whose token to show",
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1) {
					String token = getUserToken(arguments[0]);
					if (token == null)
						this.reportError(" No token exists for user '" + arguments[0] + "'.");
					else this.reportResult(" Token for user '" + arguments[0] + "' is " + token);
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the username as the only argument.");
			}
		};
		cal.add(ca);
		
		//	delete user
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return DELETE_TOKEN_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						DELETE_TOKEN_COMMAND + " <userName>",
						"Delete an API access token:",
						"- <userName>: the name of the user whose token to delete"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1) {
					String error = deleteUserToken(arguments[0]);
					if (error == null)
						this.reportResult(" Token of user '" + arguments[0] + "' deleted successfully.");
					else this.reportError(" Error deleting token of user '" + arguments[0] + "': " + error);
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify username only.");
			}
		};
		cal.add(ca);
		
		//	create a token
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return CREATE_TOKEN;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session.");
					output.newLine();
					return;
				}
				
				String userName = uaa.getUserNameForSession(sessionId);
				if (!uaa.hasPermission(userName, USE_API_TOKEN_PERMISSION, true)) {
					output.write("Insufficient permissions for creating an API token");
					output.newLine();
					return;
				}
				
				String token = createUserToken(userName);
				if (token == null)
					token = "";
				output.write(CREATE_TOKEN);
				output.newLine();
				output.write(token);
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	retrieve a token
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_TOKEN;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session.");
					output.newLine();
					return;
				}
				
				String token = getUserToken(uaa.getUserNameForSession(sessionId));
				if (token == null)
					token = "";
				output.write(GET_TOKEN);
				output.newLine();
				output.write(token);
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	delete a token
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return DELETE_TOKEN;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session.");
					output.newLine();
					return;
				}
				
				deleteUserToken(uaa.getUserNameForSession(sessionId));
				
				output.write(DELETE_TOKEN);
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	retrieve the user name for a token
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_TOKEN_DATA;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				String token = input.readLine();
				String userName = getUserForToken(token);
				if (userName == null) {
					output.write("Invalid token");
					output.newLine();
					return;
				}
				
				//	indicate success
				output.write(GET_TOKEN_DATA);
				output.newLine();
				
				//	send user name
				output.write(userName);
				output.newLine();
				
				//	send permissions
				String[] permissions = ((upa == null) ? null : upa.getPermissions(userName));
				if (permissions == null) {
					output.write(UserAccessAuthority.USE_DEFAULT_PERMISSIONS);
					output.newLine();
				}
				else for (int p = 0; p < permissions.length; p++) {
					output.write(permissions[p]);
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	finally ...
		return ((ComponentAction[]) cal.toArray(new ComponentAction[cal.size()]));
	}
	
	private Map userNamesToTokens = Collections.synchronizedMap(new LinkedHashMap() {
		protected boolean removeEldestEntry(Entry eldest) {
			return (this.size() > 128);
		}
	});
	private Map tokensToUserNames = Collections.synchronizedMap(new LinkedHashMap() {
		protected boolean removeEldestEntry(Entry eldest) {
			return (this.size() > 128);
		}
	});
	
	/**
	 * Create a new token for a user.
	 * @param userName the name of the user to create the token for
	 * @return the newly created token
	 */
	String createUserToken(String userName) {
		String token = createToken();
		String query;
		
		String exToken = this.getUserToken(userName);
		if (exToken == null) {
			query = "INSERT INTO " + TOKEN_TABLE_NAME + " (" + TOKEN_COLUMN_NAME + ", " + TOKEN_HASH_COLUMN_NAME + ", " + USER_NAME_COLUMN_NAME + ")" +
					" VALUES" +
					" ('" + token + "', " + token.hashCode() + ", '" + EasyIO.sqlEscape(userName) + "');";
		}
		else {
			query = "UPDATE " + TOKEN_TABLE_NAME + 
					" SET " + TOKEN_COLUMN_NAME + " = '" + token + "', " + TOKEN_HASH_COLUMN_NAME + " = " + token.hashCode() +
					" WHERE " + USER_NAME_COLUMN_NAME + " = '" + EasyIO.sqlEscape(userName) + "';";
			this.tokensToUserNames.remove(exToken);
//			this.userNamesToTokens.remove(userName); // will be replaced below
		}
		
		try {
			this.io.executeUpdateQuery(query);
			this.tokensToUserNames.put(token, userName);
			this.userNamesToTokens.put(userName, token);
			return token;
		}
		catch (SQLException sqle) {
			this.logError("ApiAccessAuthority: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while creating user token.");
			this.logError("  query was " + query);
			return null;
		}
	}
	
	/**
	 * Retrieve the token of a user..
	 * @param userName the user whose token to get
	 * @return the token of the argument user
	 */
	String getUserToken(String userName) {
		if (this.userNamesToTokens.containsKey(userName))
			return ((String) this.userNamesToTokens.get(userName));
		
		String query = "SELECT " + TOKEN_COLUMN_NAME +
				" FROM " + TOKEN_TABLE_NAME + 
				" WHERE " + TOKEN_COLUMN_NAME + " = '" + EasyIO.sqlEscape(userName) + "';";
		SqlQueryResult sqr = null;
		try {
			sqr = this.io.executeSelectQuery(query);
			if (sqr.next()) {
				String token = sqr.getString(0);
				this.tokensToUserNames.put(token, userName);
				this.userNamesToTokens.put(userName, token);
				return token;
			}
		}
		catch (SQLException sqle) {
			this.logError("UserAccessAuthority: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while getting user token.");
			this.logError("  query was " + query);
		}
		finally {
			if (sqr != null)
				sqr.close();
		}
		return null;
	}
	
	/**
	 * Retrieve the user associated with a token.
	 * @param token the token whose associated user to get
	 * @return the user name associated with the argument token, or null if the
	 *         token is invalid
	 */
	String getUserForToken(String token) {
		if (this.tokensToUserNames.containsKey(token))
			return ((String) this.tokensToUserNames.get(token));
		
		String query = "SELECT " + USER_NAME_COLUMN_NAME +
				" FROM " + TOKEN_TABLE_NAME + 
				" WHERE " + TOKEN_HASH_COLUMN_NAME + " = " + token.hashCode() +
				" AND " + TOKEN_COLUMN_NAME + " = '" + EasyIO.sqlEscape(token) + "';";
		SqlQueryResult sqr = null;
		try {
			sqr = this.io.executeSelectQuery(query);
			if (sqr.next()) {
				String userName = sqr.getString(0);
				this.tokensToUserNames.put(token, userName);
				this.userNamesToTokens.put(userName, token);
				return userName;
			}
		}
		catch (SQLException sqle) {
			this.logError("UserAccessAuthority: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while getting user for token.");
			this.logError("  query was " + query);
		}
		finally {
			if (sqr != null)
				sqr.close();
		}
		return null;
	}
	
	/**
	 * Delete the token of a user.
	 * @param userName the name of the user whose token to delete
	 * @return null if the user was deleted successfully, an error message
	 *         otherwise
	 */
	String deleteUserToken(String userName) {
		String token = this.getUserToken(userName);
		String query = "DELETE FROM " + TOKEN_TABLE_NAME + 
				" WHERE " + USER_NAME_COLUMN_NAME + " = '" + EasyIO.sqlEscape(userName) + "';";
		try {
			this.io.executeUpdateQuery(query);
			this.tokensToUserNames.remove(token);
			this.userNamesToTokens.remove(userName);
		}
		catch (SQLException sqle) {
			this.logError("ApiAccessAuthority: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while deleting user token.");
			this.logError("  query was " + query);
		}
		return null;
	}
	
	private static final String tokenChars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
	private static String createToken() {
		StringBuffer token = new StringBuffer();
		while (token.length() < TOKEN_LENGTH) {
			int i = ((int) (Math.random() * Integer.MAX_VALUE));
			while (i > 0) {
				int c = (i % tokenChars.length());
				token.append(tokenChars.charAt(c));
				i = (i / tokenChars.length());
			}
		}
		if (token.length() > TOKEN_LENGTH)
			token.delete(TOKEN_LENGTH, token.length());
		return token.toString();
	}
//	
//	public static void main(String[] args) throws Exception {
//		System.out.println(createToken());
//	}
}
