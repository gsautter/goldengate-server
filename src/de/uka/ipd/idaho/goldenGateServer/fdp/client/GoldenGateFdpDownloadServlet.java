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
package de.uka.ipd.idaho.goldenGateServer.fdp.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.uka.ipd.idaho.goldenGateServer.client.GgServerHtmlServlet;
import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection.Connection;
import de.uka.ipd.idaho.goldenGateServer.fdp.GoldenGateFdpConstants;
import de.uka.ipd.idaho.goldenGateServer.fdp.util.ByteArrayFile;
import de.uka.ipd.idaho.goldenGateServer.fdp.util.FileGroup;
import de.uka.ipd.idaho.goldenGateServer.util.BufferedLineInputStream;
import de.uka.ipd.idaho.goldenGateServer.util.LruCache;

/**
 * @author sautter
 */
public class GoldenGateFdpDownloadServlet extends GgServerHtmlServlet implements GoldenGateFdpConstants {
	private File fileGroupRoot;
	private TreeMap fileGroupsByName = new TreeMap();
	
	/** usual zero-argument constructor for class loading */
	public GoldenGateFdpDownloadServlet() {}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.easyIO.web.HtmlServlet#reInit()
	 */
	protected void reInit() throws ServletException {
		
		//	re-initialize parent class first (might have new data to use below)
		super.reInit();
		
		//	get any shared disk storage root folder
		String fileGroupRoot = this.getSetting("fileGroupRoot");
		if (fileGroupRoot == null)
			this.fileGroupRoot = null; // need to go via network
		else if (fileGroupRoot.startsWith("/") || (fileGroupRoot.indexOf(":/") != -1) || (fileGroupRoot.indexOf(":\\") != -1))
			this.fileGroupRoot = new File(fileGroupRoot);
		else this.fileGroupRoot = new File(this.dataPath, fileGroupRoot);
		
		//	check if folder actually exists
		if ((this.fileGroupRoot != null) && !this.fileGroupRoot.exists())
			this.fileGroupRoot = null;
		
		//	get file groups from backing FDP
		try {
			this.refreshFileGroupIndex();
		}
		catch(IOException ioe) {
			throw new ServletException(ioe);
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerHtmlServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerHtmlServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	get file group name
		String fileGroupName = request.getServletPath();
		if (fileGroupName == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		while (fileGroupName.startsWith("/"))
			fileGroupName = fileGroupName.substring("/".length());
		
		//	get file path and name
		String fileName = request.getPathInfo();
		if (fileName == null) {}
		else /* normalize and scrutinize file name */ {
			fileName = fileName.trim();
			fileName = fileName.replace('\\', '/');
			fileName = fileName.replaceAll("[\\/]+", "/");
			fileName = fileName.replace("/./", "/");
			while (fileName.startsWith("/"))
				fileName = fileName.substring("/".length());
			if (fileName.indexOf("../") != -1) /* catch any parent steps */ {
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}
			if (fileName.length() == 0)
				fileName = null;
		}
		
		//	no file path or name, list content of file group
		if (fileName == null) {
			this.sendFileGroupContent(fileGroupName, request, response);
			return;
		}
		
		//	get file group
		FileGroup fileGroup = ((FileGroup) this.fileGroupsByName.get(fileGroupName));
		if (fileGroup == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		//	check file name
		if (!fileGroup.acceptName(fileName)) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		//	finally ...
		this.sendFile(fileGroupName, fileName, request, response);
	}
	
	private static final SimpleDateFormat lastModifiedFormat = new SimpleDateFormat("EE, dd MMM yyyy HH:mm:ss 'GMT'Z", Locale.US);
	private void sendFile(String fileGroupName, String fileName, HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (this.fileGroupRoot == null) {
			
			//	get file content from backing FDP TODO we might want to cache this in some folder of our own (in distributed scenario)
			ByteArrayFile file = this.getFile(fileGroupName, fileName);
//			ByteArrayFile file; // TODO use this less revealing approach once we know whole thing works
//			try {
//				file = this.getFile(fileGroupName, fileName);
//			}
//			catch (IOException ioe) {
//				file = null;
//			}
			if (file == null) {
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}
			
			//	send file header
			response.setHeader("Last-Modified", lastModifiedFormat.format(new Date(file.lastMod)));
			response.setHeader("ETag", ("" + file.lastMod));
			response.setHeader("Cache-Control", "no-cache");
			response.setContentLength(file.bytes.length);
			
			//	loop data through to requester
			OutputStream os = new BufferedOutputStream(response.getOutputStream());
			os.write(file.bytes, 0, file.bytes.length);
			os.flush();
		}
		else {
			
			//	check file on shared disk folder
			File fgFolder = new File(this.fileGroupRoot, fileGroupName);
			File file = new File(fgFolder, fileName);
			if (!file.exists() || !file.isFile()) {
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}
			
			//	send file header
			response.setHeader("Last-Modified", lastModifiedFormat.format(new Date(file.lastModified())));
			response.setHeader("ETag", ("" + file.lastModified()));
			response.setHeader("Cache-Control", "no-cache");
			response.setContentLength((int) file.length());
			
			//	loop data through to requester
			InputStream is = new BufferedInputStream(new FileInputStream(file));
			OutputStream os = new BufferedOutputStream(response.getOutputStream());
			byte[] buffer = new byte[1024];
			for (int r; (r = is.read(buffer)) != -1;)
				os.write(buffer, 0, r);
			os.flush();
			
			//	close streams
			is.close();
		}
	}
	
	private static final SimpleDateFormat fileListTimeFormat;
	static {
		fileListTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
		fileListTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	}
	private static final Comparator pathAndNameOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			FileDescriptor fd1 = ((FileDescriptor) obj1);
			FileDescriptor fd2 = ((FileDescriptor) obj2);
			return fd1.name.compareTo(fd2.name);
		}
	};
	private void sendFileGroupContent(String fileGroupName, HttpServletRequest request, HttpServletResponse response) throws IOException {
		
		//	get file group content
		FileDescriptor[] fds = this.getFiles(fileGroupName, "force".equalsIgnoreCase(request.getParameter("cacheControl")));
		
		//	nothing found
		if (fds == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		Arrays.sort(fds, pathAndNameOrder);
		
		//	prepare response
		response.setHeader("Cache-Control", "no-cache");
		response.setContentType("text/html; charset=utf-8");
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), "UTF-8"));
		
		//	send page header
		bw.write("<html><head>"); bw.newLine();
		bw.write("<title>" + fileGroupName + "</title>"); bw.newLine();
		bw.write("</head><body>"); bw.newLine();
		
		//	send table header
		bw.write("<table width=\"100%\" cellspacing=\"0\" cellpadding=\"5\" align=\"center\">"); bw.newLine();
		bw.write("<tr>"); bw.newLine();
		bw.write("<td align=\"left\"><font size=\"+1\"><strong>Filename</strong></font></td>"); bw.newLine();
		bw.write("<td align=\"center\"><font size=\"+1\"><strong>Size</strong></font></td>"); bw.newLine();
		bw.write("<td align=\"right\"><font size=\"+1\"><strong>Last Modified (UTC)</strong></font></td>"); bw.newLine();
		bw.write("</tr>"); bw.newLine();
		
		//	send file list
		for (int f = 0; f < fds.length; f++) {
			bw.write("<tr" + (((f % 2) == 1) ? " bgcolor=\"#eeeeee\"" : "") + ">"); bw.newLine();
			bw.write("<td align=\"left\">&nbsp;&nbsp;"); bw.newLine();
			bw.write("<a href=\"" + request.getContextPath() + request.getServletPath() + "/" + fds[f].name + "\"><tt>" + fds[f].name + "</tt></a></td>"); bw.newLine();
			bw.write("<td align=\"right\"><tt>" + (fds[f].size / 1024) + " kb</tt></td>"); bw.newLine();
			bw.write("<td align=\"right\"><tt>" + fileListTimeFormat.format(new Date(fds[f].lastMod)) + "</tt></td>"); bw.newLine();
			bw.write("</tr>"); bw.newLine();
		}
		
		//	close table and page
		bw.write("</table>"); bw.newLine();
		bw.write("</body></html>"); bw.newLine();
		
		//	finish request
		bw.flush();
	}
	
	private void refreshFileGroupIndex() throws IOException {
		FileGroupDescriptor[] fgds = this.getFileGroups();
		this.fileGroupsByName.clear();
		for (int g = 0; g < fgds.length; g++)
			this.fileGroupsByName.put(fgds[g].name, new FileGroup(fgds[g]));
	}
	
	private FileGroupDescriptor[] getFileGroups() throws IOException {
		Connection con = null;
		try {
			con = this.serverConnection.getConnection();
			BufferedWriter bw = con.getWriter();
			bw.write(LIST_FILE_GROUPS_COMMAND);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (LIST_FILE_GROUPS_COMMAND.equals(error))
				return FileGroupDescriptor.readFileGroupDescriptors(br);
			else throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	private LruCache fileListCache = new LruCache("FdpFileListCache", 256, 0, (60 * 3), (60 * 15)); // timeouts in seconds, not milliseconds
	private FileDescriptor[] getFiles(String fileGroupName, boolean forceReload) throws IOException {
		FileDescriptor[] files = (forceReload ? null : ((FileDescriptor[]) fileListCache.get(fileGroupName)));
		if (files != null)
			return files;
		FileGroup fileGroup = ((FileGroup) this.fileGroupsByName.get(fileGroupName));
		if (fileGroup == null)
			return null;
		if (this.fileGroupRoot == null)
			files = getFiles(fileGroupName);
		else files = FileDescriptor.getFileDescriptors(new File(this.fileGroupRoot, fileGroup.descriptor.name), fileGroup);
		this.fileListCache.put(fileGroupName, files);
		return files;
	}
	
	private FileDescriptor[] getFiles(String fileGroupName) throws IOException {
		Connection con = null;
		try {
			con = this.serverConnection.getConnection();
			BufferedWriter bw = con.getWriter();
			bw.write(LIST_FILES_COMMAND);
			bw.newLine();
			bw.write(fileGroupName);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (LIST_FILES_COMMAND.equals(error))
				return FileDescriptor.readFileDescriptors(br);
			else throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	private ByteArrayFile getFile(String fileGroupName, String fileName) throws IOException {
		Connection con = null;
		try {
			con = this.serverConnection.getConnection();
			BufferedWriter bw = con.getWriter();
			bw.write(GET_FILE_COMMAND);
			bw.newLine();
			bw.write(fileGroupName);
			bw.newLine();
			bw.write(fileName);
			bw.newLine();
			bw.flush();
			
			BufferedLineInputStream blin = con.getInputStream();
			String error = blin.readLine();
			if (!GET_FILE_COMMAND.equals(error))
				throw new IOException(error);
			
			ZipInputStream zin = new ZipInputStream(blin);
			byte[] buffer = new byte[1024];
			long lastMod = -1;
			ByteArrayFile file = null;
			for (ZipEntry ze; (ze = zin.getNextEntry()) != null;) {
				
				//	catch timestamp for subsequent file
				if (LAST_MODIFIED_ZIP_ENTRY_NAME.equals(ze.getName())) {
					lastMod = 0;
					for (int r; (r = zin.read()) != -1;) {
						lastMod <<= 8;
						lastMod |= (0x00000000000000FFL & r);
					}
					continue;
				}
				
				//	read file proper
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				for (int r; (r = zin.read(buffer, 0, buffer.length)) != -1;)
					bytes.write(buffer, 0, r);
				bytes.flush();
				bytes.close();
//				file = new ByteArrayFile(ze.getName(), ze.getTime(), bytes.toByteArray());
				file = new ByteArrayFile(ze.getName(), ((lastMod == -1) ? ze.getTime() : lastMod), bytes.toByteArray());
				lastMod = -1;
				break; // we're only retrieving single file
			}
			return file;
		}
		finally {
			if (con != null)
				con.close();
		}
	}
}
