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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;

import de.uka.ipd.idaho.goldenGateServer.aaa.ApiAccessAuthorityConstants;
import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection;
import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection.Connection;

/**
 * Basic client for Golden GATE Server API Access Authority, responsible for
 * authenticating API access tokens. Valid tokens will be cached for some time
 * in order to avoid all too frequent calls to the backing API Access
 * Authority.
 * 
 * @author sautter
 */
public class ApiAuthenticationClient implements ApiAccessAuthorityConstants {
	private ServerConnection sCon;
	private Map tokenDataCache = Collections.synchronizedMap(new LinkedHashMap() {
		protected boolean removeEldestEntry(Entry eldest) {
			return (this.size() > 128);
		}
	});
	
	private ApiAuthenticationClient(ServerConnection sCon) {
		this.sCon = sCon;
	}
	
	/**
	 * Check whether or not an API access token is valid.
	 * @param accessToken the API access token to check
	 * @return true if the argument API access token is valid
	 */
	public boolean isValid(String accessToken) throws IOException {
		TokenData td = this.getTokenData(accessToken);
		return (td != null);
	}
	
	/**
	 * Retrieve the user name associated with an API access token. If the
	 * argument token is invalid, this method returns null.
	 * @param accessToken the API access token to retrieve the user name for
	 * @return the user name for the argument token
	 */
	public String getUserNameForToken(String accessToken) throws IOException {
		TokenData td = this.getTokenData(accessToken);
		return ((td == null) ? null : td.userName);
	}
	
	/**
	 * Check whether or not an API access token has some permission.
	 * @param accessToken the API access token
	 * @param permission the permission to check
	 * @return true if the argument API access token has the argument permission
	 */
	public boolean hasPermission(String accessToken, String permission) throws IOException {
		TokenData td = this.getTokenData(accessToken);
		if (td == null) // invalid token
			return false;
		if (td.permissions.contains(permission)) // permission present
			return true;
		
		//	re-fetch from back-end
		this.tokenDataCache.remove(accessToken);
		td = this.getTokenData(accessToken);
		
		//	do we have the permission now?
		return ((td != null) && td.permissions.contains(permission));
	}
	
	/**
	 * Clear any cached data on API access tokens, forcing a re-fetch from the
	 * backing API Access Authority.
	 */
	public void clearAccessTokenCache() {
		this.tokenDataCache.clear();
	}
	
	private TokenData getTokenData(String accessToken) throws IOException {
		if (this.tokenDataCache.containsKey(accessToken))
			return ((TokenData) this.tokenDataCache.get(accessToken));
		
		Connection con = null;
		try {
			con = this.sCon.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_TOKEN_DATA);
			bw.newLine();
			bw.write(accessToken);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!GET_TOKEN_DATA.equals(error))
				throw new IOException(error);
			
			String userName = br.readLine();
			TokenData td = new TokenData(accessToken, userName);
			for (String permission; (permission = br.readLine()) != null;)
				td.permissions.add(permission);
			this.tokenDataCache.put(accessToken, td);
			
			return td;
		}
		catch (Exception e) {
			throw new IOException(e.getMessage());
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	private static class TokenData {
		final String token;
		final String userName;
		final LinkedHashSet permissions = new LinkedHashSet();
		TokenData(String token, String userName) {
			this.token = token;
			this.userName = userName;
		}
	}
	
	//	client pool to allow sharing sessions between different objects in the same virtual machine
	private static Map aaaClientPool = Collections.synchronizedMap(new HashMap());
	
	/**
	 * Obtain an instance of this class, wrapped around the argument server
	 * connection. If an instance already exists, this method simply returns it
	 * and does not create a new one.
	 * @param sc the server connection
	 * @return an API Access Authority backed by the argument server connection
	 */
	public static synchronized ApiAuthenticationClient getInstance(ServerConnection sc) {
		ApiAuthenticationClient aaaClient = ((ApiAuthenticationClient) aaaClientPool.get(sc));
		if (aaaClient == null) {
			aaaClient = new ApiAuthenticationClient(sc);
			aaaClientPool.put(sc, aaaClient);
		}
		return aaaClient;
	}
}
