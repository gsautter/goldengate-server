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
package de.uka.ipd.idaho.goldenGateServer.ups;

import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants;

/**
 * Constant bearer interface for GoldenGATE User Permission Server (UPS)
 * 
 * @author sautter
 */
public interface GoldenGateUpsConstants extends GoldenGateServerConstants {
	
	/** command for retrieving the permissions of the user logged in on a session */
	public static final String GET_GRANTED_PERMISSIONS = "UPS_GET_GRANTED_PERMISSIONS";
	
	
	/** command for retrieving all the users available */
	public static final String GET_USERS = "UPS_GET_USERS";
	
	/** command for retrieving all the roles available */
	public static final String GET_ROLES = "UPS_GET_ROLES";
	
	/** command for retrieving all the permissions available */
	public static final String GET_PERMISSIONS = "UPS_GET_PERMISSIONS";
	
	
	/** command for retrieving the roles of a user */
	public static final String GET_USER_ROLES = "UPS_GET_USER_ROLES";
	
	/** command for setting the roles of a user */
	public static final String SET_USER_ROLES = "UPS_SET_USER_ROLES";
	
	
	/** command for creating a role */
	public static final String CREATE_ROLE = "UPS_CREATE_ROLE";
	
	/** command for deleting a role */
	public static final String DELETE_ROLE = "UPS_DELETE_ROLE";
	
	
	/** command for retrieving the permissions of a role */
	public static final String GET_ROLE_PERMISSIONS = "UPS_GET_ROLE_PERMISSIONS";
	
	/** command for setting the permissions of a role */
	public static final String SET_ROLE_PERMISSIONS = "UPS_SET_ROLE_PERMISSIONS";
	
	
	/** command for retrieving the roles of a role */
	public static final String GET_ROLE_ROLES = "UPS_GET_ROLE_ROLES";
	
	/** command for setting the roles of a role */
	public static final String SET_ROLE_ROLES = "UPS_SET_ROLE_ROLES";
}
