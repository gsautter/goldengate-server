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
 *     * Neither the name of the Universitaet Karlsruhe (TH) / KIT nor the
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
package de.uka.ipd.idaho.goldenGateServer.aaa.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

import de.uka.ipd.idaho.goldenGateServer.aaa.ApiAccessAuthorityConstants;
import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection.Connection;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;

/**
 * Client for managing access tokens in an API Access Authority
 * 
 * @author sautter
 */
public class ApiAccessAuthorityClient implements ApiAccessAuthorityConstants {
	private AuthenticatedClient authClient;
	
	/** Constructor
	 * @param	ac	the authenticated client to use for authentication and connection 
	 */
	public ApiAccessAuthorityClient(AuthenticatedClient ac) {
		this.authClient = ac;
	}
	
	/**
	 * Retrieve the API token of the user logged in on the underlying
	 * authenticated client.
	 * @return the API token of the user
	 */
	public String getToken() throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_TOKEN);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_TOKEN.equals(error)) {
				String token = br.readLine();
				return ("".equals(token) ? null : token);
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
	 * Create an API token for the user logged in on the underlying authenticated
	 * client. If the user already has an API token, it is replaced by a new one.
	 * @return the new API token of the user
	 */
	public String createToken() throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(CREATE_TOKEN);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (CREATE_TOKEN.equals(error))
				return br.readLine();
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
	 * Delete the API token of the user logged in on the underlying authenticated
	 * client.
	 * @return true if deletion was successful
	 */
	public boolean deleteToken() throws IOException {
		if (!this.authClient.isLoggedIn()) throw new IOException("Not logged in.");
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(DELETE_TOKEN);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			return (DELETE_TOKEN.equals(error));
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
