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
package de.uka.ipd.idaho.goldenGateServer.enr.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.uka.ipd.idaho.easyIO.streams.CharSequenceReader;
import de.uka.ipd.idaho.goldenGateServer.aaa.webClient.GgServerApiServlet;
import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection.Connection;
import de.uka.ipd.idaho.goldenGateServer.enr.GoldenGateEnrConstants;

/**
 * Client servlet for GoldenGATE Server External Notification Receiver (ENR).
 * 
 * @author sautter
 */
public class GoldenGateEnrClientServlet extends GgServerApiServlet implements GoldenGateEnrConstants {
	
	//	GET list of notification types from back-end
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	check authentication
		boolean authorized = this.hasValidToken(request);
		
		//	get notification types (permissions only come with authorized request)
		Connection con = null;
		try {
			
			//	connect to back-end
			con = this.serverConnection.getConnection();
			BufferedWriter bw = con.getWriter();
			bw.write(GET_NOTIFICATION_TYPES);
			bw.newLine();
			bw.write(authorized ? "true" : "false");
			bw.newLine();
			bw.flush();
			
			//	get backend response
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!GET_NOTIFICATION_TYPES.equals(error))
				throw new IOException(error);
			
			//	get custom MIME type (if any)
			String responseType = request.getParameter("type");
			if (responseType == null)
				responseType = "application/json";
			response.setContentType(responseType);
			response.setCharacterEncoding("UTF-8");
			
			//	send result data
			Writer w = response.getWriter();
			char[] buffer = new char[1024];
			for (int r; (r = br.read(buffer, 0, buffer.length)) != -1;)
				w.write(buffer, 0, r);
			w.flush();
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	//	POST notification with user session
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if (this.hasValidToken(request))
			this.relayNotification(request, response);
		else response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
	}
	
	//	PUT notification with access token
	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if (this.hasValidToken(request))
			this.relayNotification(request, response);
		else response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
	}
	
	private void relayNotification(HttpServletRequest request, HttpServletResponse response) throws IOException {
		
		//	crop endpoint name from path info
		String pathInfo = request.getPathInfo();
		while (pathInfo.startsWith("/"))
			pathInfo = pathInfo.substring("/".length());
		String endpoint;
		if (pathInfo.indexOf("/") == -1) {
			endpoint = pathInfo;
			pathInfo = "";
		}
		else {
			endpoint = pathInfo.substring(0, pathInfo.indexOf("/"));
			pathInfo = pathInfo.substring(pathInfo.indexOf("/"));
			while (pathInfo.startsWith("/"))
				pathInfo = pathInfo.substring("/".length());
		}
		
		//	get user name
		String userName = this.getUserName(request);
		
		//	read request body ...
		BufferedReader br = request.getReader();
		StringWriter body = new StringWriter();
		if (br.ready()) {
			char[] buffer = new char[1024];
			for (int r; (r = br.read(buffer, 0, buffer.length)) != -1;)
				body.write(buffer, 0, r);
		}
		
		//	... making sure it's not completely empty
		if (body.getBuffer().length() == 0)
			body.write("null");
		
		//	send request to back-end
		Connection con = null;
		try {
			
			//	connect to back-end
			con = this.serverConnection.getConnection();
			BufferedWriter bBw = con.getWriter();
			bBw.write(RECEIVE_NOTIFICATION);
			bBw.newLine();
			bBw.write(endpoint);
			bBw.newLine();
			bBw.write(pathInfo);
			bBw.newLine();
			bBw.write(userName);
			bBw.newLine();
			CharSequenceReader csr = new CharSequenceReader(body.getBuffer());
			char[] buffer = new char[1024];
			for (int r; (r = csr.read(buffer, 0, buffer.length)) != -1;)
				bBw.write(buffer, 0, r);
			bBw.newLine();
			bBw.write("END_NOTIFICATION");
			bBw.newLine();
			bBw.flush();
			
			//	relay back-end response
			BufferedReader bBr = con.getReader();
			String error = bBr.readLine();
			if (!RECEIVE_NOTIFICATION.equals(error))
				response.sendError(HttpServletResponse.SC_BAD_REQUEST, error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
}
