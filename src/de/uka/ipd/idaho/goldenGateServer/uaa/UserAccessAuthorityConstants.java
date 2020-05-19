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


import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants;

/**
 * Interface holding the constants for communication with a UserAccessAuthority
 * 
 * @author sautter
 */
public interface UserAccessAuthorityConstants extends GoldenGateServerConstants {
	
	/** the maximum number of characters in a user name, namely 32 */
	public static final int USER_NAME_MAX_LENGTH = 32;
	
	/** the pattern (as a string) for validating user names (first character has to be a Latin letter, all subsequent characters may also be digits or one of '-', '_', '@', or '.') */
	public static final String USER_NAME_PATTERN = ("[a-zA-Z][a-zA-Z0-9\\-\\_\\@\\.]{1," + (USER_NAME_MAX_LENGTH - 1) + "}");
	
	/** the login command*/
	public static final String LOGIN = "LOGIN";
	
	/** the ping command*/
	public static final String PING = "PING";
	
	/** the logout command*/
	public static final String LOGOUT = "LOGOUT";
	
	/** the command for setting the password of the user logged in on a session*/
	public static final String SET_PWD = "SET_PWD";
	
	
	/** the permission string indicating that a user permission authority is not installed and default permissions are to be used*/
	public static final String USE_DEFAULT_PERMISSIONS = "UseDefaultPermissions";
	
	
	/** the user access control command for creating a user*/
	public static final String CREATE_USER = "UAA_CREATE_USER";
	
	/** the user access control command for setting the password of a user*/
	public static final String SET_USER_PWD = "UAA_SET_USER_PWD";
	
	/** the user access control command for deleting a user*/
	public static final String DELETE_USER = "UAA_DELETE_USER";
	
	/** the user access control command for setting the admin property of a user*/
	public static final String SET_ADMIN = "UAA_SET_ADMIN";
	
	/** the user access control command for removing the admin property of a user*/
	public static final String REMOVE_ADMIN = "UAA_REMOVE_ADMIN";
	
	/** the user access control command for listing users*/
	public static final String LIST_USERS = "UAA_LIST_USERS";
	
	
	/** the user name parameter for login and admin operations*/
	public static final String USER_NAME_PARAMETER = "userName";
	
	/** the password parameter for login and admin operations*/
	public static final String PASSWORD_PARAMETER = "pswd";
	
	/** the admin property parameter for admin operations*/
	public static final String IS_ADMIN_PARAMETER = "isAdmin";
	
	
	/** the suffix of the session ID indicating that the logged in user has administrative priviledges*/
	public static final String ADMIN_SESSION_ID_SUFFIX = "AAAA";
}
