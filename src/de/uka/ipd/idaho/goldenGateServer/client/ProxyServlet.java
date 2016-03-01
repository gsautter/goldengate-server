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
package de.uka.ipd.idaho.goldenGateServer.client;


import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants;
import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection.Connection;

/**
 * Servlet acting as a proxy in order to enable communicating with a GoldenGATE
 * Server via HTTP. This servlet uses a configuration file for obtaining the
 * connection parameters of the backing GoldenGATE Server. By default, this file
 * is 'config.cnfg' in the surrounding web-app's context path. It can be changed
 * to another file somewhere below the web-app's context path through specifying
 * the alternative file path and name in the value of the servlet's 'configFile'
 * parameter in the web.xml. From the configuration file, the servlet reads two
 * parameters:
 * <ul>
 * <li><b>serverAddress</b>: the host name of the backing GoldenGATE Server</li>
 * <li><b>serverPort</b>: the port the backing GoldenGATE Server listens on</li>
 * </ul>
 * @author sautter
 */
public class ProxyServlet extends GgServerClientServlet implements GoldenGateServerConstants {
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		InputStream requestIn = null;
		OutputStream responseOut = null;
		Connection con = null;
		try {
			con = this.serverConnection.getConnection();
			
			//	loop request data through to server
			requestIn = request.getInputStream();
			OutputStream serverOut = con.getOutputStream();
			
			//	send 'PROXIED' property
			serverOut.write("PROXIED\r\n".getBytes(ENCODING));
			byte[] buffer = new byte[1024];
			for (int r; (r = requestIn.read(buffer, 0, buffer.length)) != -1;)
				serverOut.write(buffer, 0, r);
			serverOut.flush();
			
			//	loop server response through to requester
			InputStream serverIn = con.getInputStream();
			response.setContentType("application/octet-stream");
			response.setHeader("Cache-Control", "no-cache");
			responseOut = new BufferedOutputStream(response.getOutputStream());
			
			for (int r; (r = serverIn.read(buffer, 0, buffer.length)) != -1;)
				responseOut.write(buffer, 0, r);
			responseOut.flush();
		}
		catch (IOException ioe) {
			if (responseOut == null)
				responseOut = new BufferedOutputStream(response.getOutputStream());
			responseOut.write(ioe.getMessage().getBytes(ENCODING));
			responseOut.write("\r\n".getBytes(ENCODING));
			responseOut.flush();
			throw ioe;
		}
		finally {
			if (con != null)
				con.close();
			if (requestIn != null)
				requestIn.close();
			if (responseOut != null)
				responseOut.close();
		}
	}
}