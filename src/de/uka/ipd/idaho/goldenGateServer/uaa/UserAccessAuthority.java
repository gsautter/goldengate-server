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
package de.uka.ipd.idaho.goldenGateServer.uaa;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TreeMap;

import de.uka.ipd.idaho.easyIO.EasyIO;
import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.SqlQueryResult;
import de.uka.ipd.idaho.easyIO.sql.TableDefinition;
import de.uka.ipd.idaho.easyIO.util.RandomByteSource;
import de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent;
import de.uka.ipd.idaho.goldenGateServer.uaa.data.UserList;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringRelation;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringTupel;

/**
 * GoldenGATE Server User Access Authority manages user accounts, which can be
 * modified through a remote client or console actions. In addition, it does
 * the session management and proxies access to both user permissions and user
 * data.
 * 
 * @author sautter
 */
public class UserAccessAuthority extends AbstractGoldenGateServerComponent implements UserAccessAuthorityConstants {
	
	private static final String LIST_USERS_COMMAND = "list";
	private static final String IMPORT_USERS_COMMAND = "import";
	
	private static final String CREATE_USER_COMMAND = "create";
	private static final String SET_USER_PWD_COMMAND = "setPwd";
	private static final String DELETE_USER_COMMAND = "delete";
	private static final String SET_ADMIN_COMMAND = "setAdm";
	private static final String REMOVE_ADMIN_COMMAND = "removeAdm";
	
	private static final String SESSION_TIMEOUT_SETTING_NAME = "sessionTimeout";
	private long sessionTimeout = 0;
	private Thread sessionTimeoutWatchdog = null;
	private final Object sessionTimeoutWatchdogLock = new Object();
	
	private UserPermissionAuthority upa;
	private UserDataProvider udp;
	
	private static final String USER_TABLE_NAME = "GgUaaUsers";
	private static final String USER_NAME_COLUMN_NAME = "UserName";
	private static final String PASSWORD_SALT_COLUMN_NAME = "PswdSalt";
	private static final String PASSWORD_HASH_COLUMN_NAME = "PswdHash";
	private static final String ADMIN_FLAG_COLUMN_NAME = "IsAdmin";
	
	private IoProvider io;
	
	/*
TODO Add notifications about user actions and modifications to GgServer:
- event types:
  - user created
  - user deleted
    ==> helps catch no-admin scenario ...
  - login
  - logout
  - direct permission change
  - role association change
  - group association change (once we have DAA and user groups)
  - (general) property change (e.g. user data)
- specific fields (on top of general events)
  - user name
  - property name
  - old and new property value (if applicable)
==> allows replicating user data ...
==> ... and with that real multi-master operation
  ==> should the need ever arise ...
  ==> need to figure out how to safely transfer password hash and salt, though ...
    ==> might need user replication service with extra pre-shared secret
	 */
	
	/** Constructor passing 'UAA' as the letter code to super constructor
	 */
	public UserAccessAuthority() {
		super("UAA");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractServerComponent#initComponent()
	 */
	public void initComponent() {
		
		//	get and check database connection
		this.io = this.host.getIoProvider();
		if (!this.io.isJdbcAvailable())
			throw new RuntimeException("User Access Authority cannot work without database access.");
		
		//	ensure user table
		TableDefinition td = new TableDefinition(USER_TABLE_NAME);
		td.addColumn(USER_NAME_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, USER_NAME_MAX_LENGTH);
		td.addColumn(PASSWORD_SALT_COLUMN_NAME, TableDefinition.INT_DATATYPE, 0);
		td.addColumn(PASSWORD_HASH_COLUMN_NAME, TableDefinition.INT_DATATYPE, 0);
		td.addColumn(ADMIN_FLAG_COLUMN_NAME, TableDefinition.CHAR_DATATYPE, 1);
		if (!this.io.ensureTable(td, true))
			throw new RuntimeException("User Access Authority cannot work without database access.");
		
		//	read user data
		this.readUserData();
		
		//	read session timeout (in seconds)
		try {
			this.sessionTimeout = Integer.parseInt(this.configuration.getSetting(SESSION_TIMEOUT_SETTING_NAME, ("" + this.sessionTimeout)));
		} catch (NumberFormatException e) {}
		
		//	session timeout enabled (set to value > 0)
		if (this.sessionTimeout > 0) {
			
			//	create and start watchdog
			this.sessionTimeoutWatchdog = new Thread() {
				
				/* wait at least one minute (60,000 milliseconds) between
				 * checks, at most 5 minutes (300,000 milliseconds), default to
				 * a tenth of the configured session timeout (session timeout is
				 * in seconds, multiply to milliseconds)*/
				private final long maxWait = Math.max(Math.min((sessionTimeout * 100), 300000), 60000);
				
				public void run() {
					
					//	keep running while not shut down (session timeout will be set to 0 then)
					while (sessionTimeout > 0) {
						
						//	wait until next check
						synchronized(sessionTimeoutWatchdogLock) {
							try {
								sessionTimeoutWatchdogLock.wait(this.maxWait);
							} catch (InterruptedException ie) {}
							
							//	session timeout set to 0 ==> shutdown
							if (sessionTimeout == 0)
								return;
						}
						
						//	get session (since both session IDs and user names are mapped to the respective sessions, use HashSet for duplicate elimination)
						HashSet sessions = new HashSet(sessionsByID.values());
						
						//	get minimum time for last activity (session timeout is in seconds, multiply to milliseconds)
						long minLastActiveTime = (System.currentTimeMillis() - (sessionTimeout * 1000));
						
						//	check sessions one by one
						for (Iterator sessionIterator = sessions.iterator(); sessionIterator.hasNext();) {
							Session session = ((Session) sessionIterator.next());
							
							//	compare last activity of each session with current time
							if (session.lastActivity < minLastActiveTime) {
								
								//	session timed out, remove it
								sessionsByID.remove(session.sessionId);
							}
						}
					}
				}
			};
			this.sessionTimeoutWatchdog.start();
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractServerComponent#exitComponent()
	 */
	protected void exitComponent() {
		
		//	clean sessions
		this.sessionsByID.clear();
		
		//	shut down sessions timeout watchdog
		if (this.sessionTimeoutWatchdog != null) {
			
			//	make session timeout watchdog terminate immediately
			synchronized(this.sessionTimeoutWatchdogLock) {
				
				//	give session timeout watchdog shutdown signal
				this.sessionTimeout = 0;
				
				//	and wake it up from waiting lock
				this.sessionTimeoutWatchdogLock.notify();
			}
			
			//	wait for timeout watchdog to finish
			try {
				this.sessionTimeoutWatchdog.join();
			} catch (InterruptedException ie) {}
		}
	}

	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent#getActions()
	 */
	public ComponentAction[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentAction ca;
		
		//	list users
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return LIST_USERS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						LIST_USERS_COMMAND,
						"List the users known to the user access authority."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					this.reportResult(" Users:");
					User[] users = getUsers();
					for (int u = 0; u < users.length; u++)
						this.reportResult(" - " + users[u].userName + (users[u].isAdmin() ? "\tAdmin" : ""));
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	create user
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return CREATE_USER_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						CREATE_USER_COMMAND + " <userName> <password>",
						"Create a new user:",
						"- <userName>: the name for the new user",
						"- <password>: the password for the new user"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 2) {
					String error = createUser(SUPERUSER_NAME, SUPERUSER_PASSWORD, arguments[0], arguments[1]);
					if (error == null)
						this.reportResult(" User '" + arguments[0] + "' created successfully.");
					else this.reportError(" Error creating user '" + arguments[0] + "': " + error);
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify username and password.");
			}
		};
		cal.add(ca);
		
		//	list users
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return IMPORT_USERS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						IMPORT_USERS_COMMAND + " <userFileName>",
						"Import users from a CSV file (columns: 'name', 'password', 'isAdmin'):",
						"- <userFileName>: the path and name of the file to import"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1) {
					File userListFile = new File(arguments[0]);
					try {
						this.reportResult("Importing user data from " + userListFile.getAbsolutePath());
						StringRelation userData = StringRelation.readCsvData(userListFile, '"', true, null);
						this.reportResult(" - File read, creating users");
						for (int u = 0; u < userData.size(); u++) {
							StringTupel userNode = userData.get(u);
							String userName = userNode.getValue(USER_NAME);
							String password = userNode.getValue(PASSWORD);
							boolean isAdmin = IS_ADMIN.equals(userNode.getValue(IS_ADMIN, ""));
							if ((userName != null) && (password != null)) {
								String error = createUser(SUPERUSER_NAME, SUPERUSER_PASSWORD, userName, password, isAdmin);
								if (error == null)
									this.reportResult(" - User '" + userName + "' created successfully.");
								else this.reportError(" - Error creating user '" + userName + "': " + error);
							}
						}
					}
					catch (Exception e) {
						this.reportError(" Error importing user data - " + e.getMessage());
						this.reportError(e);
					}
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the file to import only.");
			}
		};
		cal.add(ca);
		
		//	set password
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return SET_USER_PWD_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						SET_USER_PWD_COMMAND + " <userName> <newPassword>",
						"Change the password of a user:",
						"- <userName>: the name of the user to set the password for",
						"- <newPassword>: the new password for the user"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 2) {
					String error = changePassword(SUPERUSER_NAME, SUPERUSER_PASSWORD, arguments[0], arguments[1]);
					if (error == null)
						this.reportResult(" Password of user '" + arguments[0] + "' changed successfully.");
					else this.reportError(" Error changing password for user '" + arguments[0] + "': " + error);
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify username and the new password.");
			}
		};
		cal.add(ca);
		
		//	delete user
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return DELETE_USER_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						DELETE_USER_COMMAND + " <userName>",
						"Delete a user:",
						"- <userName>: the name of the user to delete"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1) {
					String error = deleteUser(SUPERUSER_NAME, SUPERUSER_PASSWORD, arguments[0]);
					if (error == null)
						this.reportResult(" User '" + arguments[0] + "' deleted successfully.");
					else this.reportError(" Error deleting user '" + arguments[0] + "': " + error);
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify username only.");
			}
		};
		cal.add(ca);
		
		//	set admin property for user
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return SET_ADMIN_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						SET_ADMIN_COMMAND + " <userName>",
						"Grant administartive permissions to a user:",
						"- <userName>: the name of the user to grant administartive permissions to"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1) {
					String error = setAdmin(SUPERUSER_NAME, SUPERUSER_PASSWORD, arguments[0], true);
					if (error == null)
						this.reportResult(" Admin property for user '" + arguments[0] + "' set successfully.");
					else this.reportError(" Error setting admin property for user '" + arguments[0] + "': " + error);
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify username only.");
			}
		};
		cal.add(ca);
		
		//	remove admin property from user
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return REMOVE_ADMIN_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						REMOVE_ADMIN_COMMAND + " <userName>",
						"Remove administartive permissions from a user:",
						"- <userName>: the name of the user to remove administartive permissions from"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1) {
					String error = setAdmin(SUPERUSER_NAME, SUPERUSER_PASSWORD, arguments[0], false);
					if (error == null)
						this.reportResult(" Admin property for user '" + arguments[0] + "' removed successfully.");
					else this.reportError(" Error removing admin property for user '" + arguments[0] + "': " + error);
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify username only.");
			}
		};
		cal.add(ca);
		
		//	login
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return LOGIN;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				String userName = input.readLine();
				String password = input.readLine();
				
				//	attempt login
				String sessionId = login(userName, password);
				
				//	login failed
				if (sessionId == null) {
					output.write("Could not log in: Invalid user name or password.");
					output.newLine();
				}
				
				//	login successful
				else {
					output.write(this.getActionCommand());
					output.newLine();
					output.write(sessionId);
					output.newLine();
					
					//	send permissions
					String[] permissions = ((upa == null) ? null : upa.getPermissions(userName));
					if (permissions == null) {
						output.write(USE_DEFAULT_PERMISSIONS);
						output.newLine();
					}
					else for (int p = 0; p < permissions.length; p++) {
						output.write(permissions[p]);
						output.newLine();
					}
				}
			}
		};
		cal.add(ca);
		
		//	ping a session
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return PING;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				String sessionId = input.readLine();
				
				if (isValidSession(sessionId))
					output.write(this.getActionCommand());
				else output.write("Invalid session.");
				
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	logout
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return LOGOUT;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				String sessionId = input.readLine();
				
				if (isValidSession(sessionId)) {
					logout(sessionId);
					output.write(this.getActionCommand());
				}
				else output.write("Invalid session.");
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	change user's own password
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return SET_PWD;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				String sessionId = input.readLine();
				if (isValidSession(sessionId)) {
					String userName = getUserNameForSession(sessionId);
					String oldPassword = input.readLine();
					String newPassword = input.readLine();
					
					String error = changePassword(userName, oldPassword, newPassword);
					if (error == null)
						output.write(this.getActionCommand());
					else output.write(error);
				}
				else output.write("Invalid session.");
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	list users
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return LIST_USERS;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				String sessionId = input.readLine();
				if (isAdminSession(sessionId)) {
					User[] users = getUsers();
					output.write(this.getActionCommand());
					output.newLine();
					
					UserList ul = new UserList();
					for (int u = 0; u < users.length; u++) {
						StringTupel user = new StringTupel();
						user.setValue(USER_NAME_PARAMETER, users[u].userName);
						if (users[u].isAdmin())
							user.setValue(IS_ADMIN_PARAMETER, IS_ADMIN_PARAMETER);
						ul.addElement(user);
					}
					ul.writeXml(output);
				}
				else output.write("Cannot list users without admin priviledges, sorry.");
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	create user
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return CREATE_USER;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				String sessionId = input.readLine();
				if (isAdminSession(sessionId)) {
					String userName = input.readLine();
					String password = input.readLine();
					
					String error = createUser(SUPERUSER_NAME, SUPERUSER_PASSWORD, userName, password);
					
					if (error == null)
						output.write(this.getActionCommand());
					else output.write(error);
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	set password
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return SET_USER_PWD;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				String sessionId = input.readLine();
				if (isAdminSession(sessionId)) {
					String userName = input.readLine();
					String newPassword = input.readLine();
					
					String error = changePassword(SUPERUSER_NAME, SUPERUSER_PASSWORD, userName, newPassword);
					
					if (error == null)
						output.write(this.getActionCommand());
					else output.write(error);
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	delete user
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return DELETE_USER;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				String sessionId = input.readLine();
				if (isAdminSession(sessionId)) {
					String userName = input.readLine();
					
					String error = deleteUser(SUPERUSER_NAME, SUPERUSER_PASSWORD, userName);
					
					if (error == null)
						output.write(this.getActionCommand());
					else output.write(error);
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	set admin property for user
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return SET_ADMIN;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				String sessionId = input.readLine();
				if (isAdminSession(sessionId)) {
					String userName = input.readLine();
					
					String error = setAdmin(SUPERUSER_NAME, SUPERUSER_PASSWORD, userName, true);
					
					if (error == null)
						output.write(this.getActionCommand());
					else output.write(error);
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	remove admin property from user
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return REMOVE_ADMIN;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				String sessionId = input.readLine();
				if (isAdminSession(sessionId)) {
					String userName = input.readLine();
					
					String error = setAdmin(SUPERUSER_NAME, SUPERUSER_PASSWORD, userName, false);
					
					if (error == null)
						output.write(this.getActionCommand());
					else output.write(error);
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	include action from UPA (if present)
		if (this.upa != null) {
			ComponentAction[] upaActions = this.upa.getActions();
			if (upaActions != null)
				for (int a = 0; a < upaActions.length; a++) {
					if (upaActions[a] instanceof ComponentActionConsole)
						cal.add(upaActions[a]);
				}
		}
		
		return ((ComponentAction[]) cal.toArray(new ComponentAction[cal.size()]));
	}
	
	// login data for access from within the same JVM (comparison via ==)
	/** user name for administrator super user account, for performing administrative actions without login from within the same JVM */
	public static final String SUPERUSER_NAME = RandomByteSource.getGUID(); //"SUPERUSER"; random string is way safer, and since we use '==' anyway, content of string is not relevant
	
	/** user name for administrator super user account, for performing administrative actions without login from within the same JVM */
//	public static final String SUPERUSER_PASSWORD = "SUPERPASSWORD";
	static final String SUPERUSER_PASSWORD = RandomByteSource.getGUID(); //"SUPERPASSWORD"; random string  is way safer, and since we use '==' anyway, content of string is not relevant
	
	private static final String USER_NAME = "name";
	private static final String PASSWORD = "password";
	private static final String IS_ADMIN = "isAdmin";
	
	private TreeMap usersByUserNames = new TreeMap();
	
	//	default administrator user, can log in if no other administrator given
	private final User DEFAULT_ADMIN = new User("Admin", "GG", true);
	
	//	read the user data from the CSV table
	private void readUserData() {
		boolean gotAdmin = false;
		
		String query = "SELECT " + USER_NAME_COLUMN_NAME + ", " + PASSWORD_SALT_COLUMN_NAME + ", " + PASSWORD_HASH_COLUMN_NAME + ", " + ADMIN_FLAG_COLUMN_NAME + 
				" FROM " + USER_TABLE_NAME + ";";
		SqlQueryResult sqr = null;
		try {
			sqr = this.io.executeSelectQuery(query);
			while (sqr.next()) {
				User user = new User(sqr.getString(0), Integer.parseInt(sqr.getString(1)), Integer.parseInt(sqr.getString(2)), "A".equals(sqr.getString(3)));
				this.usersByUserNames.put(user.userName, user);
				gotAdmin = (gotAdmin || user.isAdmin());
			}
		}
		catch (SQLException sqle) {
			this.logError("UserAccessAuthority: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while loading users.");
			this.logError("  query was " + query);
		}
		finally {
			if (sqr != null)
				sqr.close();
		}
		if ((this.usersByUserNames.isEmpty() || !gotAdmin))
			this.usersByUserNames.put(DEFAULT_ADMIN.userName, DEFAULT_ADMIN);
	}
	
	/**
	 * Create a new user. The user name has to consist of letters and digits only.
	 * @param adminName the user name of the administrator creating the new user
	 * @param adminPassword the password of the administrator creating the new
	 *            user
	 * @param userName the name of the user to create
	 * @param password the password of the user to create
	 * @return null if the new user was created successfully, an error message
	 *         otherwise
	 */
	public String createUser(String adminName, String adminPassword, String userName, String password) {
		return this.createUser(adminName, adminPassword, userName, password, false);
	}
	
	/**
	 * Create a new user. The user name has to consist of letters and digits only.
	 * @param adminName the user name of the administrator creating the new user
	 * @param adminPassword the password of the administrator creating the new
	 *            user
	 * @param userName the name of the user to create
	 * @param password the password of the user to create
	 * @return null if the new user was created successfully, an error message
	 *         otherwise
	 */
	public String createUser(String adminName, String adminPassword, String userName, String password, boolean asAdmin) {
		if (!this.authenticate(adminName, adminPassword) || !this.isAdmin(adminName))
			return "No permission to create user.";
		
		if ((userName == null) || (password == null))
			return "Incomplete user data.";
		
		if (!userName.matches(USER_NAME_PATTERN))
			return "Invalid user name, use Latin letters, digits, '-', '_', '@', and '.' only, " + USER_NAME_MAX_LENGTH + " at max.";
		
		User user = this.getUserForName(userName);
		if (user != null)
			return "User already exists.";
		
		user = new User(userName, password, asAdmin);
		String query = "INSERT INTO " + USER_TABLE_NAME + "" +
				" (" + USER_NAME_COLUMN_NAME + ", " + PASSWORD_SALT_COLUMN_NAME + ", " + PASSWORD_HASH_COLUMN_NAME + ", " + ADMIN_FLAG_COLUMN_NAME + ")" +
				" VALUES" +
				" ('" + EasyIO.sqlEscape(userName) + "', " + user.getPasswordSalt() + ", " + user.getPasswordHash() + ", '" + (user.isAdmin() ? "A" : "U") + "');";
		try {
			this.io.executeUpdateQuery(query);
			this.usersByUserNames.put(user.userName, user); // only add user to runtime lookup map once persistence is established
		}
		catch (SQLException sqle) {
			this.logError("UserAccessAuthority: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while creating user.");
			this.logError("  query was " + query);
		}
		return null;
	}
	
	/**
	 * Change the password of a user account.
	 * @param userName the name of the user whose password to change
	 * @param oldPassword the password of the user
	 * @param newPassword the string to set the password to
	 * @return null if the password was changed successfully, an error message
	 *         otherwise
	 */
	public String changePassword(String userName, String oldPassword, String newPassword) {
		User user = this.getUserForName(userName);
		
		if ((user == null) || !user.testPassword(oldPassword))
			return "Invalid user name or password.";
		if (newPassword == null)
			return "New password is empty.";
		
		user.setPassword(newPassword);
		String query = "UPDATE " + USER_TABLE_NAME + 
				" SET " + PASSWORD_SALT_COLUMN_NAME + " = " + user.getPasswordSalt() + ", " + PASSWORD_HASH_COLUMN_NAME + " = " + user.getPasswordHash() + 
				" WHERE " + USER_NAME_COLUMN_NAME + " = '" + EasyIO.sqlEscape(userName) + "';";
		try {
			this.io.executeUpdateQuery(query);
		}
		catch (SQLException sqle) {
			this.logError("UserAccessAuthority: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while changing password of user.");
			this.logError("  query was " + query);
		}
		return null;
	}
	
	/**
	 * Change the password of a user account.
	 * @param adminName the user name of the administrator changing the password
	 * @param adminPassword the password of the administrator changing the
	 *            password
	 * @param userName the name of the user whose password to change
	 * @param newPassword the string to set the password to
	 * @return null if the password was changed successfully, an error message
	 *         otherwise
	 */
	public String changePassword(String adminName, String adminPassword, String userName, String newPassword) {
		if (!this.authenticate(adminName, adminPassword) || !this.isAdmin(adminName))
			return "No permission to modify user data.";
		
		User user = this.getUserForName(userName);
		if (user == null)
			return "User does not exist.";
		if (newPassword == null)
			return "New password is empty.";
		
		user.setPassword(newPassword);
		String query = "UPDATE " + USER_TABLE_NAME + 
				" SET " + PASSWORD_SALT_COLUMN_NAME + " = " + user.getPasswordSalt() + ", " + PASSWORD_HASH_COLUMN_NAME + " = " + user.getPasswordHash() + 
				" WHERE " + USER_NAME_COLUMN_NAME + " = '" + EasyIO.sqlEscape(userName) + "';";
		try {
			this.io.executeUpdateQuery(query);
		}
		catch (SQLException sqle) {
			this.logError("UserAccessAuthority: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while setting password for user.");
			this.logError("  query was " + query);
		}
		return null;
	}
	
	/**
	 * Change the administrator property of a user account.
	 * @param adminName the user name of the administrator changing the admin
	 *            property
	 * @param adminPassword the password of the administrator changing the admin
	 *            property
	 * @param userName the name of the user to set the administrator property
	 *            for
	 * @return null if the property was changed successfully, an error message
	 *         otherwise
	 */
	public String setAdmin(String adminName, String adminPassword, String userName, boolean isAdmin) {
		if (!this.authenticate(adminName, adminPassword) || !this.isAdmin(adminName)) return "No permission to modify user data.";
		
		User user = this.getUserForName(userName);
		if (user == null)
			return "User does not exist.";
		
		boolean wasAdmin = user.isAdmin();
		user.setAdmin(isAdmin);
		if (wasAdmin != isAdmin) {
			String query = "UPDATE " + USER_TABLE_NAME + 
					" SET " + ADMIN_FLAG_COLUMN_NAME + " = '" + (user.isAdmin() ? "A" : "U") + 
					"' WHERE " + USER_NAME_COLUMN_NAME + " = '" + EasyIO.sqlEscape(userName) + "';";
			try {
				this.io.executeUpdateQuery(query);
			}
			catch (SQLException sqle) {
				this.logError("UserAccessAuthority: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while setting admin flag for user.");
				this.logError("  query was " + query);
			}
		}
		return null;
	}
	
	/**
	 * Delete a user account.
	 * @param adminName the user name of the administrator deleting the user
	 * @param adminPassword the password of the administrator deleting the user
	 * @param userName the name of the user to delete
	 * @return null if the user was deleted successfully, an error message
	 *         otherwise
	 */
	public String deleteUser(String adminName, String adminPassword, String userName) {
		if (!this.authenticate(adminName, adminPassword) || !this.isAdmin(adminName)) return "No permission to delete user.";
		
		User user = this.getUserForName(userName);
		if (user == null)
			return "User does not exist.";
		
		String query = "DELETE FROM " + USER_TABLE_NAME + 
				" WHERE " + USER_NAME_COLUMN_NAME + " = '" + EasyIO.sqlEscape(userName) + "';";
		try {
			this.io.executeUpdateQuery(query);
			this.usersByUserNames.remove(user.userName); // only delete user once we're sure he won't exist after restart
		}
		catch (SQLException sqle) {
			this.logError("UserAccessAuthority: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while deleting user.");
			this.logError("  query was " + query);
		}
		return null;
	}
	
	/**
	 * Retrieve the user permission authority installed.
	 * @return the user permission authority installed, or null, if there is none
	 */
	public UserPermissionAuthority getUserPermissionAuthority() {
		return this.upa;
	}
	
	/**
	 * Add a user permission authority to the user access authority to augment
	 * the latter with fine-grained permissions.
	 * @param upa the user permission authority
	 */
	public void setUserPermissionAuthority(UserPermissionAuthority upa) {
		this.upa = upa;
	}
	
	/**
	 * Check whether or not a user permission authority is installed. This
	 * method is intended for checking if invoking the methods that delegate to
	 * the user permission authority makes sense.
	 * @return true if a user permission authority is installed, false otherwise
	 */
	public boolean isUserPermissionAuthorityInstalled() {
		return (this.upa != null);
	}
	
	/**
	 * Make a permission known to the permission management, so it can be
	 * granted to roles and checked for users. This method loops through to the
	 * user permission authority. If no user permission authority is registered,
	 * this method returns a respective error message.
	 * @param permission the permission to add
	 * @return null if the permission was added successfully, an error message
	 *         otherwise
	 */
	public String registerPermission(String permission) {
		if (this.upa == null)
			return "User permission authority not installed";
		else return this.upa.registerPermission(permission);
	}
	
	/**
	 * Check if a given user has a given permission. If isAdmin() returns true
	 * for the specified user name, this method returns true as well. If a user
	 * permission authority is installed, the permission check is delegated to
	 * it. If no user permission authority is installed, this method returns
	 * false.
	 * @param userName the user name to check the permission for
	 * @param permission the permission to check
	 * @return true if the user with the specified name has the specified
	 *         permission, false otherwise
	 */
	public boolean hasPermission(String userName, String permission) {
		return this.hasPermission(userName, permission, false);
	}
	
	/**
	 * Check if a given user has a given permission. If isAdmin() returns true
	 * for the specified user name, this method returns true as well. If a user
	 * permission authority is installed, the permission check is delegated to
	 * it. If no user permission authority is installed, this method returns the
	 * specified default value. The idea is to facilitate granting specific
	 * permissions to all users in case no user permission authority is
	 * installed. It is up to the invoking component to decide this.
	 * @param userName the user name to check the permission for
	 * @param permission the permission to check
	 * @param grantByDefault the value to return if no user permission authority
	 *            is installed
	 * @return true if the user with the specified name has the specified
	 *         permission, false otherwise
	 */
	public boolean hasPermission(String userName, String permission, boolean grantByDefault) {
		if (userName == null)
			return false;
		
		else if (this.isAdmin(userName))
			return true;
		
		else if (this.upa == null)
			return grantByDefault;
		
		else return this.upa.hasPermission(userName, permission);
	}
	
	/**
	 * Check if the user logged in on a given session has a given permission. If
	 * isAdminSession() returns true for the specified user name, this method
	 * returns true as well. If a user permission authority is installed, the
	 * permission check is delegated to it. If no user permission authority is
	 * installed, this method returns false.
	 * @param sessionId the ID of the session to check the permission for
	 * @param permission the permission to check
	 * @return true if the user logged in on the specified session name has the
	 *         specified permission, false otherwise
	 */
	public boolean hasSessionPermission(String sessionId, String permission) {
		return this.hasSessionPermission(sessionId, permission, false);
	}
	
	/**
	 * Check if the user logged in on a given session has a given permission.
	 * If isAdminSession() returns true for the specified user name, this method
	 * returns true as well. If a user permission authority is installed, the
	 * permission check is delegated to it. If no user permission authority is
	 * installed, this method returns the specified default value. The idea is
	 * to facilitate granting specific permissions to all users in case no user
	 * permission authority is installed. It is up to the invocing component to
	 * decide this.
	 * @param sessionId the ID of the session to check the permission for
	 * @param permission the permission to check
	 * @param grantByDefault the value to return if no user permission authority
	 *            is installed
	 * @return true if the user logged in on the specified session name has the
	 *         specified permission, false otherwise
	 */
	public boolean hasSessionPermission(String sessionId, String permission, boolean grantByDefault) {
		return this.hasPermission(this.getUserNameForSession(sessionId), permission, grantByDefault);
	}
	
	/**
	 * Retrieve the user data provider installed.
	 * @return the user data provider installed, or null, if there is none
	 */
	public UserDataProvider getUserDataProvider() {
		return this.udp;
	}
	
	/**
	 * Add a user data provider to the user access authority to augment
	 * the latter with user specific data.
	 * @param upa the user data provider
	 */
	public void setUserDataProvider(UserDataProvider udp) {
		this.udp = udp;
	}
	
	/**
	 * Check whether or not a user data provider is installed. This method is
	 * intended for checking if invocing the methods that delegate to the user
	 * data provider makes sense.
	 * @return true if a user data provider is installed, false otherwise
	 */
	public boolean isUserDataProviderInstalled() {
		return (this.udp != null);
	}
	
	/**
	 * Retrieve a property of a user. If the specified user does not exist or
	 * does not have the requested property, this method returns null. There is
	 * no guarantee that users do not change their properties, so components
	 * using these properties should re-get them every time they use them, or at
	 * least periodically.
	 * @param user the name of the user
	 * @param name the name of the property
	 * @return the value of the requested property.
	 */
	public String getUserProperty(String user, String name) {
		return ((this.udp == null) ? null : this.udp.getUserProperty(user, name));
	}
	
	/**
	 * Retrieve a property of a user. If the specified user does not exist or
	 * does not have the requested property, this method returns the specified
	 * default value. There is no guarantee that users do not change their
	 * properties, so components using these properties should re-get them every
	 * time they use them, or at least periodically.
	 * @param user the name of the user
	 * @param name the name of the property
	 * @param def a default return value
	 * @return the value of the requested property.
	 */
	public String getUserProperty(String user, String name, String def) {
		return ((this.udp == null) ? null : this.udp.getUserProperty(user, name, def));
	}
	
	/**
	 * Authenticate a user.
	 * @param userName the name of the user to check
	 * @param password the password of the user
	 * @return true if and only if the user with the specified name exists and
	 *         the specified password is valid
	 */
	public boolean authenticate(String userName, String password) {
		if ((userName == null) || (password == null))
			return false;
		
		if ((SUPERUSER_NAME == userName) && (SUPERUSER_PASSWORD == password))
			return true;
		
		User user = this.getUserForName(userName);
		return ((user != null) && user.testPassword(password));
	}
	
	/**
	 * Check if a user has administrative access.
	 * @param userName the name of the user to check
	 * @return true if the user with the specified name has administrative
	 *         access, false otherwise
	 */
	public boolean isAdmin(String userName) {
		if (userName == null)
			return false;
		
		if (SUPERUSER_NAME == userName)
			return true;
		
		User user = this.getUserForName(userName);
		if ((user != null) && user.isAdmin) return true;
		else return false;
	}
	
	/**
	 * Check if the user logged in on a session has administrative access.
	 * @param sessionId the session ID to check
	 * @return true if the user logged in on the specified session has
	 *         administrative access, false otherwise
	 */
	public boolean isAdminSession(String sessionId) {
		if (sessionId == null)
			return false;
		else return this.isAdmin(this.getUserNameForSession(sessionId));
	}
	
	/**
	 * Check if a user with a given name is an actually authenticated user
	 * (as opposed to a server internal one).
	 * @param userName the user name to check
	 * @return true if the user with the specified name is actually is an
	 *         authenticated user, false otherwise
	 */
	public boolean isActualUser(String userName) {
		return (this.getUserForName(userName) != null);
	}
	
	private User getUserForName(String userName) {
		return ((User) this.usersByUserNames.get(userName));
	}
	
	private User[] getUsers() {
		ArrayList userList = new ArrayList(this.usersByUserNames.values());
		Collections.sort(userList);
		return ((User[]) userList.toArray(new User[userList.size()]));
	}
	
	/**
	 * container for user data
	 * 
	 * @author sautter
	 */
	private static class User implements Comparable {
		final String userName;
		private int passwordSalt;
		private int passwordHash;
		private boolean isAdmin;
		User(String userName, String password, boolean isAdmin) {
			this.userName = userName;
			this.setPassword(password);
			this.isAdmin = isAdmin;
		}
		User(String userName, int passwordSalt, int passwordHash, boolean isAdmin) {
			this.userName = userName;
			this.passwordSalt = passwordSalt;
			this.passwordHash = passwordHash;
			this.isAdmin = isAdmin;
		}
		public int compareTo(Object obj) {
			return this.userName.compareTo(((User) obj).userName);
		}
		int getPasswordSalt() {
			return this.passwordSalt;
		}
		int getPasswordHash() {
			return this.passwordHash;
		}
		boolean isAdmin() {
			return this.isAdmin;
		}
		void setAdmin(boolean isAdmin) {
			this.isAdmin = isAdmin;
		}
		boolean testPassword(String password) {
			return ((password + this.passwordSalt).hashCode() == this.passwordHash);
		}
		void setPassword(String password) {
			this.passwordSalt = ((int) (Integer.MAX_VALUE * Math.random()));
			this.passwordHash = (password + this.passwordSalt).hashCode();
		}
	}
	
	private Hashtable sessionsByID = new Hashtable();
	
	/**
	 * container for session data
	 * 
	 * @author sautter
	 */
	private class Session {
		final String userName;
		final String sessionId;
		long lastActivity = System.currentTimeMillis();
		Session(String userName, String sessionId) {
			this.userName = userName;
			this.sessionId = sessionId;
		}
		public boolean equals(Object obj) {
			return ((obj != null) && (obj instanceof Session) && this.sessionId.equals(((Session) obj).sessionId));
		}
		public int hashCode() {
			return this.sessionId.hashCode();
		}
		public String toString() {
			return (this.sessionId + " (" + this.userName + ")");
		}
	}
	
	/**
	 * Log a user in.
	 * @param userName the name of the user
	 * @param password the password of the user
	 * @return the users session ID, or null, if the login attempt was
	 *         unsuccessful
	 */
	public String login(String userName, String password) {
		if (this.authenticate(userName, password)) {
			
			String sessionId = this.produceSessionID();
			
			//	make admin session ID if user is admin
			if (this.isAdmin(userName))
				sessionId = (sessionId.substring(0, (sessionId.length() - ADMIN_SESSION_ID_SUFFIX.length())) + ADMIN_SESSION_ID_SUFFIX);
			
			//	make non-admin session ID if user is not admin
			else while (sessionId.endsWith(ADMIN_SESSION_ID_SUFFIX))
				sessionId = this.produceSessionID();
			
			//	create session
			Session session = new Session(userName, sessionId);
			
			//	register session
			this.sessionsByID.put(sessionId, session);
			
			//	return session ID
			return session.sessionId;
		}
		else return null;
	}
	
	/**
	 * Check if a specific session exists.
	 * @param sessionId the ID of the session to check
	 */
	public boolean isValidSession(String sessionId) {
		
		//	get session
		Session session = ((sessionId == null) ? null : ((Session) this.sessionsByID.get(sessionId)));
		
		//	invalid session
		if (session == null)
			return false;
		
		//	valid session, remember last activity
		session.lastActivity = System.currentTimeMillis();
		return true;
	}
	
	/**
	 * Retrieve the names of all users managed by this UAA.
	 * @return an array holding the names of all users
	 */
	public String[] getUserNames() {
		return ((String[]) this.usersByUserNames.keySet().toArray(new String[this.usersByUserNames.size()]));
	}
	
	/**
	 * Check if a specific user exists.
	 * @param userName the name of the user to check
	 */
	public boolean isValidUsername(String userName) {
		return ((userName != null) && this.usersByUserNames.containsKey(userName));
	}
	
	/**
	 * Retrieve tha name of the user logged in on a specific session.
	 * @param sessionId the ID of the session to check
	 * @return the name of the user logged in on the session with the specified
	 *         ID, or null, if there is no such session
	 */
	public String getUserNameForSession(String sessionId) {
		
		//	get session
		Session session = ((sessionId == null) ? null : ((Session) this.sessionsByID.get(sessionId)));
		
		//	read user name
		return ((session == null) ? null : session.userName);
	}
	
	/**
	 * Log out a session.
	 * @param sessionId the ID of the session to log out
	 */
	public void logout(String sessionId) {
		
		//	get session
		Session session = ((sessionId == null) ? null : ((Session) this.sessionsByID.get(sessionId)));
		
		//	do logout
		if (session != null)
			this.sessionsByID.remove(session.sessionId);
	}
	
	private String produceSessionID() {
		return this.truncateId(RandomByteSource.getGUID());
	}
	
	private String truncateId(String id) {
		return (id.startsWith("0x") ? id.substring(2) : id);
	}
}
