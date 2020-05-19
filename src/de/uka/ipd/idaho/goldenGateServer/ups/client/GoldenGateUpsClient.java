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
package de.uka.ipd.idaho.goldenGateServer.ups.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection.Connection;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.goldenGateServer.ups.GoldenGateUpsConstants;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * A client for remotely accessing and managing the roles and permissions in a
 * GoldenGATE UPS.
 * 
 * @author sautter
 */
public class GoldenGateUpsClient implements GoldenGateUpsConstants {
	
	private AuthenticatedClient authClient;
	
	/** Constructor
	 * @param	ac	the authenticated client to use for authentication and connection 
	 */
	public GoldenGateUpsClient(AuthenticatedClient ac) {
		this.authClient = ac;
	}
	
	/**
	 * Retrieve the permissions of the user logged in on this client. All roles
	 * will be resolved, so this method returns the permissions a user
	 * effectively has.
	 * @return an array holding the the permissions of the user logged in on
	 *         this client
	 * @throws IOException
	 */
	public String[] getGrantedPermissions() throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_GRANTED_PERMISSIONS);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_GRANTED_PERMISSIONS.equals(error)) {
				StringVector permissions = new StringVector();
				String permission;
				while ((permission = br.readLine()) != null)
					permissions.addElementIgnoreDuplicates(permission);
				return permissions.toStringArray();
			}
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
	
	/**
	 * Retrieve all users (requires administrative priviledges)
	 * @return an array holding all users available in the backing server
	 * @throws IOException
	 */
	public String[] getUsers() throws IOException {
		return this.retrieveList(GET_USERS);
	}
	
	/**
	 * Retrieve all available roles (requires administrative priviledges)
	 * @return an array holding all roles available in the UPS
	 * @throws IOException
	 */
	public String[] getRoles() throws IOException {
		return this.retrieveList(GET_ROLES);
	}
	
	/**
	 * Retrieve all available permissions (requires administrative priviledges)
	 * @return an array holding all permissions available in the UPS
	 * @throws IOException
	 */
	public String[] getPermissions() throws IOException {
		return this.retrieveList(GET_PERMISSIONS);
	}
	
	private String[] retrieveList(String command) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(command);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (command.equals(error)) {
				StringVector list = new StringVector();
				String lisEntry;
				while ((lisEntry = br.readLine()) != null)
					list.addElementIgnoreDuplicates(lisEntry);
				return list.toStringArray();
			}
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
	
//	/**
//	 * Retrieve the permissions explicitly granted to a specific user, excluding
//	 * the ones obtained from roles (requires administrative priviledges)
//	 * @param userName the user to retrieve the permissions for (specifying null
//	 *            will result in all available permissions being returned)
//	 * @return an array holding the permissions explicitly granted to the
//	 *         specified user
//	 * @throws IOException
//	 */
//	public String[] getUserPermissions(String userName) throws IOException {
//		return this.getRolesOrPermissions(GET_USER_PERMISSIONS, userName, true);
//	}
//	
//	/**
//	 * Grant a set of permissions to a user (requires administrative
//	 * priviledges)
//	 * @param userName the user to grant the permissions to
//	 * @param permissions the permissions to grant to the specified user
//	 * @throws IOException
//	 */
//	public void grantUserPermissions(String userName, String[] permissions) throws IOException {
//		this.modifyRolesOrPermissions(GRANT_USER_PERMISSIONS, userName, permissions);
//	}
//	
//	/**
//	 * Remove a set of permissions from a user (requires administrative
//	 * priviledges). This operation will not change permissions obtained through
//	 * roles.
//	 * @param userName the user to remove the permissions from
//	 * @param permissions the permissions to remove from the specified user
//	 * @throws IOException
//	 */
//	public void removeUserPermissions(String userName, String[] permissions) throws IOException {
//		this.modifyRolesOrPermissions(REMOVE_USER_PERMISSIONS, userName, permissions);
//	}
//	
//	/**
//	 * Set the permissions of a user (requires administrative priviledges). This
//	 * operation will not change permissions obtained through roles.
//	 * @param userName the user to set the permissions for
//	 * @param permissions the permissions the specified user shall have from now
//	 *            on
//	 * @throws IOException
//	 */
//	public void setUserPermissions(String userName, String[] permissions) throws IOException {
//		this.modifyRolesOrPermissions(SET_USER_PERMISSIONS, userName, permissions);
//	}
	
	
	/**
	 * Retrieve the roles explicitly granted to a specific user, excluding the
	 * roles obtained through other roles (requires administrative priviledges)
	 * @param userName the user to retrieve the roles for (specifying null will
	 *            result in all available roles being returned)
	 * @return an array holding the roles explicitly granted to the specified
	 *         user
	 * @throws IOException
	 */
	public String[] getUserRoles(String userName) throws IOException {
		return this.getRolesOrPermissions(GET_USER_ROLES, userName, true);
	}
	
//	/**
//	 * Grant a set of roles to a user (requires administrative priviledges)
//	 * @param userName the user to grant the roles to
//	 * @param roles the roles to grant to the specified user
//	 * @throws IOException
//	 */
//	public void grantUserRoles(String userName, String[] roles) throws IOException {
//		this.modifyRolesOrPermissions(GRANT_USER_ROLES, userName, roles);
//	}
//	
//	/**
//	 * Remove a set of roles from a user (requires administrative priviledges).
//	 * This operation will not change roles obtained through other roles.
//	 * @param userName the user to remove the roles from
//	 * @param roles the roles to remove from the specified user
//	 * @throws IOException
//	 */
//	public void removeUserRoles(String userName, String[] roles) throws IOException {
//		this.modifyRolesOrPermissions(REMOVE_USER_ROLES, userName, roles);
//	}
//	
	/**
	 * Set the roles of a user (requires administrative priviledges)
	 * @param userName the user to set the roles for
	 * @param roles the roles the specified user shall have from now on
	 * @throws IOException
	 */
	public void setUserRoles(String userName, String[] roles) throws IOException {
		this.modifyRolesOrPermissions(SET_USER_ROLES, userName, roles);
	}
	
	/**
	 * create a new role for the UPS (requires administrative priviledges)
	 * @param roleName the name for the new role
	 * @throws IOException
	 */
	public void createRole(String roleName) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		//	check parameter
		if (roleName == null)
			throw new IOException("Invalid arguments for creating a role.");
		
		//	create user
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(CREATE_ROLE);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(roleName);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!CREATE_ROLE.equals(error))
				throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/**
	 * delete a role (requires administrative priviledges)
	 * @param roleName the name of the role to delete
	 * @throws IOException
	 */
	public void deleteRole(String roleName) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		//	check login parameters
		if (roleName == null)
			throw new IOException("Invalid arguments for deleting a role.");
		
		//	delete user
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(DELETE_ROLE);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(roleName);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!DELETE_ROLE.equals(error))
				throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	
	/**
	 * Retrieve the permissions explicitly granted to a specific role, excluding
	 * the ones obtained from other roles (requires administrative priviledges)
	 * @param roleName the role to retrieve the permissions for
	 * @return an array holding the permissions explicitly granted to the
	 *         specified role
	 * @throws IOException
	 */
	public String[] getRolePermissions(String roleName) throws IOException {
		return this.getRolesOrPermissions(GET_ROLE_PERMISSIONS, roleName, false);
	}
	
//	/**
//	 * Grant a set of permissions to a role (requires administrative
//	 * priviledges)
//	 * @param roleName the role to grant the permissions to
//	 * @param permissions the permissions to grant to the specified role
//	 * @throws IOException
//	 */
//	public void grantRolePermissions(String roleName, String[] permissions) throws IOException {
//		this.modifyRolesOrPermissions(GRANT_ROLE_PERMISSIONS, roleName, permissions);
//	}
//	
//	/**
//	 * Remove a set of permissions from a role (requires administrative
//	 * priviledges). This operation will not change permissions obtained through
//	 * other roles.
//	 * @param roleName the role to remove the permissions from
//	 * @param permissions the permissions to remove from the specified role
//	 * @throws IOException
//	 */
//	public void removeRolePermissions(String roleName, String[] permissions) throws IOException {
//		this.modifyRolesOrPermissions(REMOVE_ROLE_PERMISSIONS, roleName, permissions);
//	}
//	
	/**
	 * Set the permissions of a role (requires administrative priviledges). This
	 * operation will not change permissions obtained through other roles.
	 * @param roleName the role to set the permissions for
	 * @param permissions the permissions the specified role shall have from now
	 *            on
	 * @throws IOException
	 */
	public void setRolePermissions(String roleName, String[] permissions) throws IOException {
		this.modifyRolesOrPermissions(SET_ROLE_PERMISSIONS, roleName, permissions);
	}
	
	/**
	 * Retrieve the roles explicitly granted to a specific role, excluding the
	 * roles obtained through other roles (requires administrative priviledges)
	 * @param roleName the role to retrieve the roles for
	 * @return an array holding the roles explicitly granted to the specified
	 *         role
	 * @throws IOException
	 */
	public String[] getRoleRoles(String roleName) throws IOException {
		return this.getRolesOrPermissions(GET_ROLE_ROLES, roleName, false);
	}
	
//	/**
//	 * Grant a set of roles to a role (requires administrative priviledges).
//	 * Granting a role to itself will have no effect, but creating a circular
//	 * inheritance will result in an error.
//	 * @param roleName the role to grant the roles to
//	 * @param roles the roles to grant to the specified role
//	 * @throws IOException
//	 */
//	public void grantRoleRoles(String roleName, String[] roles) throws IOException {
//		this.modifyRolesOrPermissions(GRANT_ROLE_ROLES, roleName, roles);
//	}
//	
//	/**
//	 * Remove a set of roles from a role (requires administrative priviledges).
//	 * This operation will not change roles obtained through roles other than
//	 * the removed ones. Removing a role from itself will have no effect.
//	 * @param roleName the role to remove the roles from
//	 * @param roles the roles to remove from the specified role
//	 * @throws IOException
//	 */
//	public void removeRoleRoles(String roleName, String[] roles) throws IOException {
//		this.modifyRolesOrPermissions(REMOVE_ROLE_ROLES, roleName, roles);
//	}
//	
	/**
	 * Set the roles of a role (requires administrative priviledges). Granting a
	 * role to itself will have no effect, but creating a circular inheritance
	 * will result in an error.
	 * @param roleName the role to set the roles for
	 * @param roles the roles the specified role shall have from now on
	 * @throws IOException
	 */
	public void setRoleRoles(String roleName, String[] roles) throws IOException {
		this.modifyRolesOrPermissions(SET_ROLE_ROLES, roleName, roles);
	}
	
	private String[] getRolesOrPermissions(String command, String userOrRoleName, boolean allowNullName) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(command);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(((userOrRoleName == null) && allowNullName) ? "" : userOrRoleName);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (command.equals(error)) {
				StringVector rolesOrPermissions = new StringVector();
				String roleOrPermission;
				while ((roleOrPermission = br.readLine()) != null)
					rolesOrPermissions.addElementIgnoreDuplicates(roleOrPermission);
				return rolesOrPermissions.toStringArray();
			}
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
	
	private void modifyRolesOrPermissions(String command, String userOrRoleName, String[] rolesOrPermissions) throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(command);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(userOrRoleName);
			bw.newLine();
			if (rolesOrPermissions != null)
				for (int rp = 0; rp < rolesOrPermissions.length; rp++) {
					bw.write(rolesOrPermissions[rp]);
					bw.newLine();
				}
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!command.equals(error))
				throw new IOException(error);
		}
		catch (Exception e) {
			throw new IOException(e.getMessage());
		}
		finally {
			if (con != null)
				con.close();
		}
	}
}
