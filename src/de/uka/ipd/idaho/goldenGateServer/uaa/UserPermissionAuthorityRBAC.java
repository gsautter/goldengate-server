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
package de.uka.ipd.idaho.goldenGateServer.uaa;

/**
 * A user permission authority that handles the assignment of permissions to
 * users by means of roles. Permissions are granted to roles, and roles are
 * assigned to users. In addition, roles can imply other roles, inheriting the
 * implied roles' permissions.
 * 
 * @author sautter
 */
public interface UserPermissionAuthorityRBAC extends UserPermissionAuthority {
	
	/**
	 * name of the role automatically granted to all users. Permissions that are
	 * to be granted to all users should be granted to this role.
	 */
	public static final String DEFAULT_ROLE_NAME = "Default";
	
	/**
	 * name of the guest role. If a user has assigned this role, he cannot have
	 * other roles. This role cannot be assigned to other roles, and no other
	 * roles can be assigned to the gust role.
	 */
	public static final String GUEST_ROLE_NAME = "Guest";
	
	/**
	 * Assign a role to a user. Each user can have multiple roles, and each role
	 * can be assigned to multiple users.
	 * @param userName the name of the user to grant the role to
	 * @param roleName the name of the role to grant
	 * @return null if the role was assigned successfully, an error message
	 *         otherwise
	 */
	public abstract String assignRole(String userName, String roleName);
	
	/**
	 * Remove a role from a user.
	 * @param userName the name of the user to revoke the role from
	 * @param roleName the name of the role to revoke
	 * @return null if the role was revoked successfully, an error message
	 *         otherwise
	 */
	public abstract String removeRole(String userName, String roleName);
	
	/**
	 * Check if a given user has a given role. Specify false for the recurse
	 * argument to check if the role is assigned to the user directly. Specify
	 * true to check if the user somehow has the role.
	 * @param userName the user name to check the role for
	 * @param roleName the name of the role to check
	 * @param recurse check implied roles as well?
	 * @return true if the user with the specified name has the specified
	 *         role, false otherwise
	 */
	public abstract boolean hasRole(String userName, String roleName, boolean recurse);
	
	/**
	 * Retrieve all roles assigned to a given user. Specify false for the
	 * recurse argument to get the role assigned to the user directly. Specify
	 * true to get all roles the user has somehow.
	 * @param userName the user name to get the roles for
	 * @param recurse list implied roles as well?
	 * @return an array holding the roles assigned to the specified user
	 */
	public abstract String[] getAssignedRoles(String userName, boolean recurse);
	
	/**
	 * Create a role.
	 * @param roleName the name of the role to create
	 * @return null if the role was created successfully, an error message
	 *         otherwise
	 */
	public abstract String createRole(String roleName);
	
	/**
	 * Delete a role.
	 * @param roleName the name of the role to delete
	 * @return null if the role was deleted successfully, an error message
	 *         otherwise
	 */
	public abstract String deleteRole(String roleName);
	
	/**
	 * Retrieve a list of all existing roles.
	 * @return an array holding the names of all existing roles
	 */
	public abstract String[] getRoles();
	
	/**
	 * Make a role imply another role. As an effect, any user having the
	 * implying role inherits the implied role.
	 * @param roleName the name of the role to imply the second role
	 * @param impliedRoleName the name of the role to be implied by the first
	 *            role
	 * @return null if the role was implied successfully, an error message
	 *         otherwise
	 */
	public abstract String implyRole(String roleName, String impliedRoleName);
	
	/**
	 * Remove a role implication.
	 * @param roleName the name of the role to remove the implication from
	 * @param impliedRoleName the name of the role to be no longer implied by
	 *            the first role
	 * @return null if the role implication was removed successfully, an error
	 *         message otherwise
	 */
	public abstract String unimplyRole(String roleName, String impliedRoleName);
	
	/**
	 * Check if a given role implies another given role. Specify false for the
	 * recurse argument to check if the second role is implied by the first one
	 * directly. Specify true to check if the first role somehow implies the
	 * second one.
	 * @param roleName the name of the role to check
	 * @param impliedRoleName the name of the role to check the implication for
	 * @param recurse evaluate implied roles transitively?
	 * @return true if the first role implies the second one, false otherwise
	 */
	public abstract boolean impliesRole(String roleName, String impliedRoleName, boolean recurse);
	
	/**
	 * Retrieve all roles implied by a given role. Specify false for the recurse
	 * argument to get the roles implied by the argument role directly. Specify
	 * true to get all roles somehow implied by the argument role.
	 * @param roleName the name of the role to list the implied roles for
	 * @param recurse list implied roles as well?
	 * @return an array holding the roles implied by the argument role
	 */
	public abstract String[] getImpliedRoles(String roleName, boolean recurse);
	
	/**
	 * Grant a permission to a role. As an effect, any user having the role has
	 * the specified permission.
	 * @param roleName the name of the role to grant the permission to
	 * @param permission the name of the permission to grant to the role
	 * @return null if the permission was granted successfully, an error message
	 *         otherwise
	 */
	public abstract String grantPermission(String roleName, String permission);
	
	/**
	 * Revoke a permission from a role. This method affects only permissions
	 * granted directly to the role with the specified name. Permissions of
	 * roles implied by the argument role are unaffected.
	 * @param roleName the name of the role to revoke the permission from
	 * @param permission the name of the permission to revoke from the role
	 * @return null if the permission was revoked successfully, an error message
	 *         otherwise
	 */
	public abstract String revokePermission(String roleName, String permission);
	
	/**
	 * Check if a given role has a given permission. Specify false for the
	 * recurse argument to check if the permission is granted to the role
	 * directly. Specify true to check if the permission is somehow granted to
	 * the role.
	 * @param roleName the name of the role to check
	 * @param permission the name of the permission to check
	 * @param recurse evaluate implied roles as well?
	 * @return true if the first role implies the second one, false otherwise
	 */
	public abstract boolean hasPermission(String roleName, String permission, boolean recurse);
	
	/**
	 * Retrieve all permissions granted to a given role. Specify false for the
	 * recurse argument to get the permissions granter to the role directly.
	 * Specify true to get all permissions somehow granted to the role.
	 * @param roleName the name of the role to list the permissions for
	 * @param recurse evaluate implied roles as well?
	 * @return an array holding the permissions granted to the specified role
	 */
	public abstract String[] getGrantedPermissions(String roleName, boolean recurse);
}
