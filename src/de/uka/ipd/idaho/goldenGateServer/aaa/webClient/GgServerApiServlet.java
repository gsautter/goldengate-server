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
package de.uka.ipd.idaho.goldenGateServer.aaa.webClient;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import de.uka.ipd.idaho.goldenGateServer.aaa.client.ApiAuthenticationClient;
import de.uka.ipd.idaho.goldenGateServer.client.GgServerClientServlet;

/**
 * Abstract API servlet. This class provides the means for checking access
 * tokens. Implementing HTTP business methods is up to sub classes.
 * 
 * @author sautter
 */
public abstract class GgServerApiServlet extends GgServerClientServlet {
	protected ApiAuthenticationClient aac;
	
	/**
	 * (Re-)initialize the API servlet. This establishes the connection to the
	 * backing API Access Authority. Sub classes overwriting this method thus
	 * have to make the super call.
	 * @see de.uka.ipd.idaho.easyIO.web.WebServlet#reInit()
	 */
	protected void reInit() throws ServletException {
		super.reInit();
		
		//	get API authentication client
		this.aac = ApiAuthenticationClient.getInstance(this.serverConnection);
		
		//	clear cache
		this.aac.clearAccessTokenCache();
	}
	
	/**
	 * Check the API access token coming in with a request. The token can be
	 * either in the query part of the URL, prefixed with 'access_token=', or
	 * in the 'Authorization' header, following the 'Bearer' keyword. If both
	 * places contain a token, only the one from the header is checked.
	 * @param request the request to check
	 * @return true if the request is authenticated
	 */
	protected boolean hasValidToken(HttpServletRequest request) throws IOException {
		String token = this.getToken(request);
		return ((token != null) && this.aac.isValid(token));
	}
	
	/**
	 * Check if the API access token coming in with a request implies a given
	 * permission. If the token is absent or invalid, this method returns false.
	 * @param request the request to check
	 * @param permission the permission to check
	 * @return true if the request has the argument permission
	 */
	protected boolean hasPermission(HttpServletRequest request, String permission) throws IOException {
		String token = this.getToken(request);
		return ((token != null) && this.aac.hasPermission(token, permission));
	}
	
	/**
	 * Retrieve the user name associated with an API access token coming in
	 * with a request. If the token is absent or invalid, this method returns
	 * null.
	 * @param request the request to check
	 * @return the user name associated with the request
	 */
	protected String getUserName(HttpServletRequest request) throws IOException {
		String token = this.getToken(request);
		return ((token == null) ? null : this.aac.getUserNameForToken(token));
	}
	
	private String getToken(HttpServletRequest request) throws IOException {
		
		//	get token from 'Authorization' header
		String hToken = request.getHeader("Authorization");
		if (hToken != null) {
			hToken = hToken.trim();
			if (hToken.startsWith("Bearer "))
				return hToken.substring("Bearer ".length()).trim();
		}
		
		//	get token from query string
		String qToken = request.getQueryString();
		if ((qToken != null) && (qToken.indexOf("access_token=") != -1)) {
			qToken = qToken.substring(qToken.indexOf("access_token=") + "access_token=".length());
			if (qToken.indexOf("&") != -1)
				return qToken.substring(0, qToken.indexOf("&"));
			else return qToken;
		}
		
		//	nothing to work with
		return null;
	}
}
