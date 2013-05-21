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

import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent.ComponentAction;

/**
 * A user permission authority can augment the functionality of the user access
 * authority with fine-grained permissions. Permissions are granted through
 * roles, which are, in turn, assigned to users. In addition, roles can imply
 * other roles.
 * 
 * @author sautter
 */
public interface UserPermissionAuthority {
	
	/**
	 * Retrieve actions to be included in the console interface of the user
	 * access authority. This method may return null. Further, any network
	 * actions will be ignored.
	 * @return an array holding actions to be included in the console interface
	 *         of the user access authority
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent#getActions()
	 */
	public abstract ComponentAction[] getActions();
	
	/**
	 * Check if a given user has a given permission.
	 * @param userName the user name to check the permission for
	 * @param permission the permission to check
	 * @return true if the user with the specified name has the specified
	 *         permission, false otherwise
	 */
	public abstract boolean hasPermission(String userName, String permission);
	
	/**
	 * Retrieve all permissions a given user has.
	 * @param userName the user name to retrieve the permission for
	 * @return an array holding all permissions the specified user has
	 */
	public abstract String[] getPermissions(String userName);
	
	/**
	 * Make a permission known to the permission management, so it can be
	 * granted to users.
	 * @param permission the permission to add
	 * @return null if the permission was added successfully, an error message
	 *         otherwise
	 */
	public abstract String registerPermission(String permission);
	
	/**
	 * Retrieve all permissions known to the user permission authority.
	 * @return an array holding all available permissions
	 */
	public abstract String[] getPermissions();
}
