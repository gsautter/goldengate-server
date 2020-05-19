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
package de.uka.ipd.idaho.goldenGateServer.uaa.client;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection;
import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection.Connection;
import de.uka.ipd.idaho.goldenGateServer.uaa.UserAccessAuthorityConstants;

/**
 * Very basic client responsible for login and logout. After login, other
 * clients with more specific functionality may retrieve and use the session ID
 * via the getSessionID() method. Furthermore, other clients can use this
 * client's ServerConnection, which they can retrieve via the
 * getServerConnection() method.
 * 
 * @author sautter
 */
public class AuthenticatedClient implements UserAccessAuthorityConstants {
	
	private ServerConnection serverConnection;
	
	private String userName = null;
	private String password = null;
	private String sessionId = null;
	
	private boolean useDefaultPermissions = false;
	private LinkedHashSet permissions = null;
	
	/** Constructor
	 * @param	sCon	the connection to the server to communicate with
	 */
	private AuthenticatedClient(ServerConnection sCon) {
		this.serverConnection = sCon;
	}
	
	/**
	 * retrieve this client's session ID for authentication in more specific
	 * server interactions
	 * @return this client's session ID, or null, if not logged in.
	 */
	public String getSessionID() {
		return this.sessionId;
	}
	
	/**
	 * retrieve this client's user name
	 * @return this client's user name, or null, if not logged in.
	 */
	public String getUserName() {
		return this.userName;
	}
	
	/**
	 * retrieve a one-time connection object from this client's server
	 * connection (shortcut for getServerConnection().getConnection())
	 * @return a one-time Connection object for one interaction with the backing
	 *         server
	 */
	public Connection getConnection() throws IOException {
		return this.serverConnection.getConnection();
	}
	
	/**
	 * retrieve this client's server connection to communicate with the backing
	 * server.
	 * @return this client's server connection
	 */
	public ServerConnection getServerConnection() {
		return this.serverConnection;
	}
	
	/**
	 * check if this client has been successfully logged in via the login()
	 * method, i.e., it has a session ID. For also checking the server
	 * connection, use ensureLoggedIn()
	 * @return true if this client was successfully logged
	 */
	public boolean isLoggedIn() {
		return (this.sessionId != null);
	}
	
	/**
	 * Check if this client is authenticated with a user having administrative
	 * priviledges at the backing server. This is indicated by the session ID
	 * ending with the ADMIN_SESSION_ID_SUFFIX constant. This check is only for
	 * testing if admin operations make sense to be available, the
	 * administrative priviledges will be re-checked on server side for every
	 * administrative operation performed.
	 * @return true if this client is logged in and the user this client is
	 *         authenticated with has administrative priviledges at the backing
	 *         server
	 */
	public boolean isAdmin() {
		return ((this.sessionId != null) && this.sessionId.endsWith(ADMIN_SESSION_ID_SUFFIX));
	}
	
	/**
	 * Test whether or not the Connections returned by the ServerConnections
	 * backing this AuthenticatedClient are plain socket connections, or
	 * something else, e.g. tunneled through HTTP. This gives a hint towards
	 * whether or not the connections can time out somewhere between client and
	 * server.
	 * @return true if the Connections returned by the backing ServerConnections
	 *         are plain socket connections, false otherwise
	 */
	public boolean isDirectSocket() {
		return this.serverConnection.isDirectSocket();
	}
	
	/**
	 * Check if the user logged in on this authenticated client has a given
	 * permission. If isAdmin() returns true for the specified user name, this
	 * method returns true as well. If a user permission authority is installed
	 * in the backing server, the permission check is delegated to it; as
	 * permissions are cached on login, this delegation goes without server
	 * interaction. If no user permission authority is installed, this method
	 * returns false.
	 * @param permission the permission to check
	 * @return true if the user logged in on this authenticated client has the
	 *         specified permission, false otherwise
	 */
	public boolean hasPermission(String permission) {
		return this.hasPermission(permission, false);
	}
	
	/**
	 * Check if the user logged in on this authenticated client has a given
	 * permission. If isAdmin() returns true for the specified user name, this
	 * method returns true as well. If a user permission authority is installed
	 * in the backing server, the permission check is delegated to it; as
	 * permissions are cached on login, this delegation goes without server
	 * interaction. If no user permission authority is installed, this method
	 * returns the specified default value. The idea is to facilitate granting
	 * specific permissions to all users in case no user permission authority is
	 * installed. It is up to the invocing component to decide this.
	 * @param permission the permission to check
	 * @param grantByDefault the value to return if no user permission authority
	 *            is installed
	 * @return true if the user logged in on this authenticated client has the
	 *         specified permission, false otherwise
	 */
	public boolean hasPermission(String permission, boolean grantByDefault) {
		if (!this.isLoggedIn())
			return false;
		
		else if (this.isAdmin())
			return true;
		
		else if (this.useDefaultPermissions)
			return grantByDefault;
		
		else return this.permissions.contains(permission);
	}
	
	/**
	 * Retrieve the permissions effectively granted to the user logged in on
	 * this authenticated client. If this authenticated client is not logged in,
	 * or if there are no permissions, this method returns an empty array, but
	 * never null.
	 * @return an array holding the permissions effectively granted to the user
	 *         logged in on this authenticated client
	 */
	public String[] getPermissions() {
		return ((this.permissions == null) ? new String[0] : ((String[]) this.permissions.toArray(new String[this.permissions.size()])));
	}
	
	/**
	 * Check if the connection to the backing server is still alive (if the
	 * server is up and reachable, and if the session is valid). If the session
	 * of this authenticated client is not valid any more, it attempts a
	 * re-login with the login data last specified to the login() method (if
	 * any).
	 * @return true if the backing server is up and reachable, and the session
	 *         is valid, false if the session is invalid. A communication
	 *         problem will result in an IOException being thrown.
	 * @throws IOException if any one occurs communicating with the server
	 */
	public boolean ensureLoggedIn() throws IOException {
		if (this.sessionId == null) return false;
		
		Connection con = null;
		try {
			con = this.serverConnection.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(PING);
			bw.newLine();
			bw.write(this.sessionId);
			bw.newLine();
			bw.flush();
			
			String error = con.getReader().readLine();
			if (PING.equals(error))
				return true;
			else if ((this.userName != null) && (this.password != null))
				return this.login(this.userName, this.password); // attempt re-login, e.g. after session timeout or server restart
			else return false;
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/**
	 * Log in to the backing server. Invoking this method again after a
	 * successful login will re-authenticate the client and obtain a new session
	 * ID.
	 * @param userName the user name to log in with
	 * @param password the password for the specified user name
	 * @return true if login was successful, false otherwise. The latter happens
	 *         with invalid user data, as opposed to connection problems, which
	 *         will result in an IOException being thrown.
	 * @throws IOException if any occurs communicating with the server
	 */
	public boolean login(String userName, String password) throws IOException {
		Connection con = null;
		try {
			con = this.serverConnection.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(LOGIN);
			bw.newLine();
			bw.write(userName);
			bw.newLine();
			bw.write(password);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (LOGIN.equals(error)) {
				this.sessionId = br.readLine();
				this.userName = userName;
				this.password = password;
				
				this.permissions = new LinkedHashSet();
				String permission;
				while ((permission = br.readLine()) != null)
					this.permissions.add(permission);
				if (this.permissions.contains(USE_DEFAULT_PERMISSIONS)) {
					this.useDefaultPermissions = true;
					this.permissions.clear();
				}
				return true;
			}
			else return false;
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/**
	 * Log out from the backing server.
	 * @throws IOException
	 */
	public void logout() throws IOException {
		if (this.sessionId == null) return;
		
		Connection con = null;
		try {
			con = this.serverConnection.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(LOGOUT);
			bw.newLine();
			bw.write(this.sessionId);
			bw.newLine();
			bw.flush();
			
			String error = con.getReader().readLine();
			if (LOGOUT.equals(error)) {
				this.sessionId = null;
				this.userName = null;
				this.password = null;
				
				this.useDefaultPermissions = false;
				this.permissions = null;
			}
			else throw new IOException("Logout failed: " + error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/**
	 * Set the password of the user logged in on this client.
	 * @param oldPassword the current password of the user, for security
	 *            measures
	 * @param newPassword the new password
	 * @throws IOException
	 */
	public void setPassword(String oldPassword, String newPassword) throws IOException {
		if (!this.ensureLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.serverConnection.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(SET_PWD);
			bw.newLine();
			bw.write(this.sessionId);
			bw.newLine();
			bw.write(oldPassword);
			bw.newLine();
			bw.write(newPassword);
			bw.newLine();
			bw.flush();
			
			String error = con.getReader().readLine();
			if (!SET_PWD.equals(error))
				throw new IOException("Password change failed: " + error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	//	client pool for sharing sessions between different objects in the same virtual machine
	private static Map authenticatedClientPool = Collections.synchronizedMap(new HashMap());
	
	/**
	 * Retrieve an authenticated client for a given server connection, ignoring
	 * the client pool. This is a shorthand for getAuthenticatedClient(sCon,
	 * false), intended to replace using the constructor.
	 * @param sCon the ServerConnection to use
	 * @return an authenticated client for the specified server connection
	 */
	public static AuthenticatedClient getAuthenticatedClient(ServerConnection sCon) {
		return getAuthenticatedClient(sCon, false);
	}
	
	/**
	 * Retrieve an authenticated client for a given server connection. If the
	 * allowPool parameter is set to true, and an authenticatedClient for the
	 * remote address the specified server connection point to was created
	 * earlier through this method, this very instance is returned. If it is
	 * already logged in, it can be used without further login activity. This
	 * functionality is for sharing authentication and session among a series
	 * of more specific clients in one virtual machine. However, it should be
	 * used only in client side scenarios, and if it is sure that only one user
	 * will work on the same virtual machine at a time. It should not be used
	 * in a servlet, for instance, where multiple users may be active at the
	 * same time, and should have distinct sessions in the backing server.
	 * @param sCon the ServerConnection to use
	 * @param allowPool allow using an existing authenticated client for the
	 *            remote address the specified server connection point to, which
	 *            might already be logged in.
	 * @return an authenticated client for the specified server connection
	 */
	public static AuthenticatedClient getAuthenticatedClient(ServerConnection sCon, boolean allowPool) {
		if (allowPool) {
			AuthenticatedClient ac = ((AuthenticatedClient) authenticatedClientPool.get(sCon));
			if (ac == null) {
				ac = new AuthenticatedClient(sCon);
				authenticatedClientPool.put(sCon, ac);
			}
			return ac;
		}
		else return new AuthenticatedClient(sCon);
	}
}
