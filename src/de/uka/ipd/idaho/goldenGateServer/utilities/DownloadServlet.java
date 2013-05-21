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
package de.uka.ipd.idaho.goldenGateServer.utilities;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.goldenGateServer.client.GgServerClientServlet;
import de.uka.ipd.idaho.goldenGateServer.client.GgServerClientServlet.ReInitializableServlet;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;

/**
 * This servlet provides a list of downloadable files on GET requests, or the
 * actual files for download. Via PUT and DELETE, users who have administrative
 * priviledges on the backing GoldenGATE Server can upload or delete files,
 * respectively.
 * 
 * @author sautter
 */
public class DownloadServlet extends GgServerClientServlet implements ReInitializableServlet {
	
	private Pattern fileNameFilter;
	private File[] fileList = new File[0];
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerClientServlet#init(de.uka.ipd.idaho.easyIO.settings.Settings)
	 */
	protected void init(Settings config) {
		this.reInit(config);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerClientServlet.ReInitializableServlet#reInit(de.uka.ipd.idaho.easyIO.settings.Settings)
	 */
	public void reInit(Settings config) {
		String fileNameFilter = config.getSetting("fileNameFilter", ".+\\.zip");
		try {
			this.fileNameFilter = Pattern.compile(fileNameFilter);
		}
		catch (PatternSyntaxException pse) {
			System.out.println("Invalid file name pattern: " + pse.getMessage());
			pse.printStackTrace(System.out);
			this.fileNameFilter = Pattern.compile(".+\\.zip");
		}
		
		this.refreshFileList();
	}
	
	private void refreshFileList() {
		this.fileList = this.dataFolder.listFiles(new FileFilter() {
			public boolean accept(File file) {
				return (file.isFile() && fileNameFilter.matcher(file.getName()).matches());
			}
		});
	}
	
	private static final SimpleDateFormat lastModifiedFormat = new SimpleDateFormat("EE, dd MMM yyyy HH:mm:ss 'GMT'Z", Locale.US);
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String downloadName = request.getPathInfo();
		
		//	request for listing
		if (downloadName == null) {
			this.writeFileListHtml(request, response);
			return;
		}
		
		//	truncate leading slash
		while (downloadName.startsWith("/"))
			downloadName = downloadName.substring(1);
		
		//	empty downlaod name, request for list
		if (downloadName.length() == 0) {
			this.writeFileListHtml(request, response);
			return;
		}
		
		//	refresh file list
		if ("refresh".equals(downloadName)) {
			this.refreshFileList();
			this.writeFileListHtml(request, response);
			return;
		}
		
		//	request for XML file list
		if ("xml".equals(downloadName)) {
			this.writeFileListXml(response);
			return;
		}
		
		//	check if file name valid
		if (this.fileNameFilter.matcher(downloadName).matches()) {
			
			//	get file
			File downloadFile = new File(this.dataFolder, downloadName);
			
			//	check if file exists
			if (downloadFile.exists()) {
				response.setHeader("Last-Modified", lastModifiedFormat.format(new Date(downloadFile.lastModified())));
				response.setHeader("ETag", ("" + downloadFile.lastModified()));
				response.setHeader("Cache-Control", "no-cache");
				response.setContentLength((int) downloadFile.length());
				
				//	loop data through to requester
				InputStream is = new BufferedInputStream(new FileInputStream(downloadFile));
				OutputStream os = new BufferedOutputStream(response.getOutputStream());
				
				byte[] inBuf = new byte[1024];
				int inLen = -1;
				while ((inLen = is.read(inBuf)) != -1)
					os.write(inBuf, 0, inLen);
				os.flush();
				
				//	close streams
				is.close();
			}
			
			//	report error
			else response.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
		
		//	report error
		else response.sendError(HttpServletResponse.SC_NOT_FOUND);
	}
	
	private void writeFileListXml(HttpServletResponse response) throws IOException {
		response.setHeader("Cache-Control", "no-cache");
		response.setContentType("text/xml; charset=utf-8");
		
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), "UTF-8"));
		bw.write("<files time=\"" + System.currentTimeMillis() + "\">");
		bw.newLine();
		
		for (int f = 0; f < this.fileList.length; f++) {
			bw.write("<file" +
					" name=\"" + this.fileList[f].getName() + "\"" +
					" size=\"" + this.fileList[f].length() + "\"" +
					" updateTime=\"" + this.fileList[f].lastModified() + "\"" +
					"/>");
			bw.newLine();
		}
		
		bw.write("</files>");
		bw.newLine();
		
		bw.flush();
	}
	
	private void writeFileListHtml(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setHeader("Cache-Control", "no-cache");
		response.setContentType("text/html; charset=utf-8");
		
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), "UTF-8"));
		bw.write("<html><head>");
		bw.newLine();
		bw.write("<title>" + this.dataPath + "</title>");
		bw.newLine();
		bw.write("</head><body>");
		bw.newLine();
		bw.write("<table width=\"100%\" cellspacing=\"0\" cellpadding=\"5\" align=\"center\">");
		bw.newLine();
		
		bw.write("<tr>");
		bw.newLine();
		bw.write("<td align=\"left\"><font size=\"+1\"><strong>Filename</strong></font></td>");
		bw.newLine();
		bw.write("<td align=\"center\"><font size=\"+1\"><strong>Size</strong></font></td>");
		bw.newLine();
		bw.write("<td align=\"right\"><font size=\"+1\"><strong>Last Modified</strong></font></td>");
		bw.newLine();
		bw.write("</tr>");
		bw.newLine();
		
		for (int f = 0; f < this.fileList.length; f++) {
			bw.write("<tr" + (((f % 2) == 1) ? " bgcolor=\"#eeeeee\"" : "") + ">");
			bw.newLine();
			bw.write("<td align=\"left\">&nbsp;&nbsp;");
			bw.newLine();
			bw.write("<a href=\"" + request.getContextPath() + request.getServletPath() + "/" + this.fileList[f].getName() + "\"><tt>" + this.fileList[f].getName() + "</tt></a></td>");
			bw.newLine();
			bw.write("<td align=\"right\"><tt>" + (this.fileList[f].length() / 1024) + " kb</tt></td>");
			bw.newLine();
			bw.write("<td align=\"right\"><tt>" + lastModifiedFormat.format(new Date(this.fileList[f].lastModified())) + "</tt></td>");
			bw.newLine();
			bw.write("</tr>");
			bw.newLine();
		}
		
		bw.write("</table>");
		bw.newLine();
		bw.write("</body></html>");
		bw.newLine();
		
		bw.flush();
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPut(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	check authentication
		if (!this.authenticate(request)) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		
		//	get target file
		String uploadName = request.getPathInfo();
		
		//	file name missing (may be login)
		if (uploadName == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		
		//	truncate leading slash
		while (uploadName.startsWith("/"))
			uploadName = uploadName.substring(1);
		
		//	store upload to disc
		File uploadFile = this.makeEmptyFile(new File(this.dataFolder, uploadName));
		
		InputStream is = new BufferedInputStream(request.getInputStream());
		OutputStream os = new BufferedOutputStream(new FileOutputStream(uploadFile));
		
		byte[] inBuf = new byte[1024];
		int inLen = -1;
		while ((inLen = is.read(inBuf)) != -1)
			os.write(inBuf, 0, inLen);
		os.flush();
		os.close();
		is.close();
		
		this.refreshFileList();
		
		//	report success
		response.setStatus(HttpServletResponse.SC_CREATED);
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doDelete(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	check authentication
		if (!this.authenticate(request)) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		
		//	get target file
		String deleteName = request.getPathInfo();
		
		//	file name missing
		if (deleteName == null)
			return;
		
		//	truncate leading slash
		while (deleteName.startsWith("/"))
			deleteName = deleteName.substring(1);
		
		//	invalidate redundant file
		this.makeEmptyFile(new File(this.dataFolder, deleteName));
		this.refreshFileList();
		
		//	report success
		response.setStatus(HttpServletResponse.SC_OK);
	}
	
	private File makeEmptyFile(File file) {
		if (file.exists()) {
			String fileName = file.getAbsolutePath();
			file.renameTo(new File(file.getAbsolutePath() + "." + System.currentTimeMillis() + ".old"));
			return new File(fileName);
		}
		else return file;
	}
	
	private HashMap sessions = new HashMap();
	private int sessionTimeout = (15 * 60 * 1000);
	
	private boolean authenticate(HttpServletRequest request) throws IOException {
		HttpSession session = request.getSession(true);
		String sessionId = session.getId();
		
		long currentTime = System.currentTimeMillis();
		Long lastActivity = ((Long) this.sessions.get(sessionId));
		if (lastActivity != null) {
			if (currentTime < (lastActivity.longValue() + this.sessionTimeout)) {
				this.sessions.put(sessionId, new Long(currentTime));
				return true;
			}
			else this.sessions.remove(sessionId);
		}
		
		String userName = request.getHeader("UserName");
		String password = request.getHeader("Password");
		if ((userName == null) || (password == null))
			return false;
		
		AuthenticatedClient authClient = AuthenticatedClient.getAuthenticatedClient(this.serverConnection, false);
		if (authClient.login(userName, password) && authClient.isAdmin()) {
			this.sessions.put(sessionId, new Long(currentTime));
			return true;
		}
		else return false;
	}
}
