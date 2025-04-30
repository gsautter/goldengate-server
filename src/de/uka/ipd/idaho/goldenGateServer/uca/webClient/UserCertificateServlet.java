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
package de.uka.ipd.idaho.goldenGateServer.uca.webClient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.uka.ipd.idaho.goldenGateServer.client.GgServerClientServlet;
import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection.Connection;
import de.uka.ipd.idaho.goldenGateServer.uca.UserCertificationAuthorityConstants;

/**
 * Servlet providing user certificates via a web front-end. This is intended as
 * a fallback for situations without an authenticated user or server connection.
 * 
 * @author sautter
 */
public class UserCertificateServlet extends GgServerClientServlet implements UserCertificationAuthorityConstants {
	
	/** usual zero-argument constructor for class loading */
	public UserCertificateServlet() {}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.easyIO.web.HtmlServlet#reInit()
	 */
	protected void reInit() throws ServletException {
		
		//	re-initialize super class
		super.reInit();
		
		//	clear cache
		this.certificates = null;
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setHeader("Cache-Control", "no-cache");
		response.setContentType("text/xml; charset=utf-8");
		UserCertificate[] ucs = this.getCertificates(!"force".equals(request.getParameter("cacheControl")));
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), "UTF-8"));
		if (ucs.length == 0) {
			bw.write("<" + UserCertificate.CERTIFICATE_LIST_NODE_TYPE + "/>");
			bw.newLine();
		}
		else {
			bw.write("<" + UserCertificate.CERTIFICATE_LIST_NODE_TYPE + ">");
			bw.newLine();
			for (int c = 0; c < ucs.length; c++) {
				bw.write(ucs[c].toXml());
				bw.newLine();
			}
			bw.write("</" + UserCertificate.CERTIFICATE_LIST_NODE_TYPE + ">");
			bw.newLine();
		}
		bw.flush();
	}
	
	private UserCertificate[] getCertificates(boolean allowCache) throws IOException {
		if (allowCache && (this.certificates != null)) {
			this.checkLastModified();
			if (this.certificates != null)
				return this.certificates;
		}
		
		Connection con = null;
		try {
			con = this.serverConnection.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_CERTIFICATES);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_CERTIFICATES.equals(error)) {
				this.certificates = UserCertificate.readCertificates(br);
				this.certificatesLastFetched = System.currentTimeMillis();
				return this.certificates;
			}
			else throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	private UserCertificate[] certificates = null;
	private long certificatesLastFetched = -1;
	
	private long lastModifiedChecked = -1;
	private synchronized void checkLastModified() throws IOException {
		long time = System.currentTimeMillis();
		if (time < (this.lastModifiedChecked + (1000 * 60 * 10) /* 10 minutes */))
			return; // we've already check in past 10 minutes
		
		//	get last modification timestamp from server
		long lastModified = this.getLastModified();
		this.lastModifiedChecked = time;
		
		//	clear outdated document list to force reload (styles proper are checked against timestamps from list)
		if (this.certificatesLastFetched < lastModified)
			this.certificates = null;
	}
	
	//	need to have our own implementation of server interaction to work without UCA client (which requires AuthenticatedClient)
	private long getLastModified() throws IOException {
		Connection con = null;
		try {
			con = this.serverConnection.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_CERTIFICATES_LAST_MODIFIED);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_CERTIFICATES_LAST_MODIFIED.equals(error))
				return Long.parseLong(br.readLine());
			else throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
}
