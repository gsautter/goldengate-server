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
package de.uka.ipd.idaho.goldenGateServer.uaa.client;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection.Connection;
import de.uka.ipd.idaho.goldenGateServer.uaa.UserAccessAuthorityConstants;
import de.uka.ipd.idaho.goldenGateServer.uaa.data.UserList;

/**
 * A client for remotely managing users in a UserAccessAuthority.
 * 
 * @author sautter
 */
public class UserAccessAuthorityClient implements UserAccessAuthorityConstants {
	
	private AuthenticatedClient authClient;
	
	/** Constructor
	 * @param	ac	the authenticated client to use for authentication and connection 
	 */
	public UserAccessAuthorityClient(AuthenticatedClient ac) {
		this.authClient = ac;
	}
	
	/** obtain the list of the users, for administrative purposes (administrative privileges required)
	 * @return the list of the users existing in the backing UserAccessAuthority
	 */
	public UserList getUserList() throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(LIST_USERS);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (LIST_USERS.equals(error))
				return UserList.readUserList(br);
			
			else throw new IOException(error);
		}
		catch (Exception e) {
			throw new IOException(e.getMessage());
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/** create a new user for the UAA (administrative privileges required) 
	 * @param	userName	the name for the new user
	 * @param	password	the password for the new user
	 * @throws IOException
	 */
	public void createUser(String userName, String password) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		//	check parameters
		if ((userName == null) || (password == null))
			throw new IOException("Invalid arguments for creating a user");
		
		//	create user
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(CREATE_USER);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(userName);
			bw.newLine();
			bw.write(password);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!CREATE_USER.equals(error))
				throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/** change a user's password (administrative privileges required) 
	 * @param	userName		the name of the user whose password to change
	 * @param	newPassword		the new password for the user
	 * @throws IOException
	 */
	public void changePassword(String userName, String newPassword) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		//	check parameters
		if ((userName == null) || (newPassword == null))
			throw new IOException("Invalid arguments for changing a user's password.");
		
		//	change password
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(SET_USER_PWD);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(userName);
			bw.newLine();
			bw.write(newPassword);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!SET_USER_PWD.equals(error))
				throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/** change a user's administator property (administrative privileges required) 
	 * @param	userName	the name of the user whose administator property to change
	 * @param	isAdmin		the new value for the administator property of the user
	 * @throws IOException
	 */
	public void setAdmin(String userName, boolean isAdmin) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		//	check login parameters
		if (userName == null)
			throw new IOException("Invalid arguments for setting or removing a user's admin property.");
		
		//	change user's admin property
		String srsCommand = (isAdmin ? SET_ADMIN : REMOVE_ADMIN);
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(srsCommand);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(userName);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!srsCommand.equals(error))
				throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/** delete a user (administrative privileges required) 
	 * @param	userName		the name of the user to delete
	 * @throws IOException
	 */
	public void deleteUser(String userName) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		//	check login parameters
		if (userName == null)
			throw new IOException("Invalid arguments for deleting a user.");
		
		//	delete user
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(DELETE_USER);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(userName);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!DELETE_USER.equals(error))
				throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
}
