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


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;

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
		
		Reader requestReader = null;
		BufferedWriter responseWriter = null;
		Connection con = null;
		try {
			con = this.serverConnection.getConnection();
			
			//	loop request data through to server
			requestReader = new InputStreamReader(request.getInputStream(), ENCODING);
			BufferedWriter serverWriter = con.getWriter();
			
			//	send 'PROXIED' property
			serverWriter.write("PROXIED");
			serverWriter.newLine();
			char[] outBuf = new char[1024];
			int outLen = -1;
			while ((outLen = requestReader.read(outBuf)) != -1)
				serverWriter.write(outBuf, 0, outLen);
			serverWriter.flush();
			
			//	loop server response through to requester
			BufferedReader serverReader = con.getReader();
			response.setContentType("text/plain");
			response.setHeader("Cache-Control", "no-cache");
			responseWriter = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
			
			char[] inBuf = new char[1024];
			int inLen = -1;
			while ((inLen = serverReader.read(inBuf)) != -1)
				responseWriter.write(inBuf, 0, inLen);
			responseWriter.flush();
		}
		catch (IOException ioe) {
			if (responseWriter == null)
				responseWriter = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
			responseWriter.write(ioe.getMessage());
			responseWriter.newLine();
			responseWriter.flush();
			throw ioe;
		}
		finally {
			if (con != null)
				con.close();
			if (requestReader != null)
				requestReader.close();
			if (responseWriter != null)
				responseWriter.close();
		}
	}
//	
//	private ServerConnection serverConnection;
//	
//	/** @see javax.servlet.GenericServlet#init()
//	 */
//	public void init() throws ServletException {
//		super.init();
//		String path = getServletContext().getRealPath("./");
//		
//		String configFile = this.getInitParameter("configFile");
//		if (configFile == null) configFile = "config.cnfg";
//		
//		Settings settings = Settings.loadSettings(new File(path, configFile));
//		
//		//	get server uplink data
//		String serverAddress = settings.getSetting("serverAddress");
//		int serverPort = Integer.parseInt(settings.getSetting("serverPort"));
//		
//		//	produce server uplink
//		this.serverConnection = ServerConnection.getServerConnection(serverAddress, serverPort);
//	}
//	
//	/* (non-Javadoc)
//	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
//	 */
//	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//		this.doPost(request, response);
//	}
//	
//	/* (non-Javadoc)
//	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
//	 */
//	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//		
//		Reader requestReader = null;
//		BufferedWriter responseWriter = null;
//		Connection con = null;
//		try {
//			con = this.serverConnection.getConnection();
//			
//			//	loop request data through to server
//			requestReader = new InputStreamReader(request.getInputStream(), ENCODING);
//			Writer serverWriter = con.getWriter();
//			
//			char[] outBuf = new char[1024];
//			int outLen = -1;
//			while ((outLen = requestReader.read(outBuf)) != -1)
//				serverWriter.write(outBuf, 0, outLen);
//			serverWriter.flush();
//			
//			//	loop server response through to requester
//			Reader serverReader = con.getReader();
//			responseWriter = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
//			
//			char[] inBuf = new char[1024];
//			int inLen = -1;
//			while ((inLen = serverReader.read(inBuf)) != -1)
//				responseWriter.write(inBuf, 0, inLen);
//			responseWriter.flush();
//		}
//		catch (IOException ioe) {
//			if (responseWriter == null)
//				responseWriter = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
//			responseWriter.write(ioe.getMessage());
//			responseWriter.newLine();
//			responseWriter.flush();
//			throw ioe;
//		}
//		finally {
//			if (con != null)
//				con.close();
//			if (requestReader != null)
//				requestReader.close();
//			if (responseWriter != null)
//				responseWriter.close();
//		}
////		Connection con = null;
////		try {
////			con = this.serverConnection.getConnection();
////			
////			//	loop request data through to server
////			Reader requestReader = new InputStreamReader(request.getInputStream(), ENCODING);
////			Writer serverWriter = con.getWriter();
////			
////			char[] outBuf = new char[1024];
////			int outLen = -1;
////			while ((outLen = requestReader.read(outBuf)) != -1)
////				serverWriter.write(outBuf, 0, outLen);
////			serverWriter.flush();
////			
////			//	loop server response through to requester
////			Reader serverReader = con.getReader();
////			Writer responseWriter = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
////			
////			char[] inBuf = new char[1024];
////			int inLen = -1;
////			while ((inLen = serverReader.read(inBuf)) != -1)
////				responseWriter.write(inBuf, 0, inLen);
////			responseWriter.flush();
////			
////			//	close readers & writers
////			requestReader.close();
////			responseWriter.close();
////		}
////		finally {
////			if (con != null)
////				con.close();
////		}
//	}
}
