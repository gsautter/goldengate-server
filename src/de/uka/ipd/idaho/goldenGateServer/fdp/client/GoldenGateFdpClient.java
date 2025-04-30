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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection.Connection;
import de.uka.ipd.idaho.goldenGateServer.fdp.GoldenGateFdpConstants;
import de.uka.ipd.idaho.goldenGateServer.fdp.util.ByteArrayFile;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.goldenGateServer.util.BufferedLineInputStream;
import de.uka.ipd.idaho.goldenGateServer.util.BufferedLineOutputStream;

/**
 * Client object for GoldenGATE File Download Provider (FDP).
 * 
 * @author sautter
 */
public class GoldenGateFdpClient implements GoldenGateFdpConstants {
	private AuthenticatedClient authClient;
	
	/** Constructor
	 * @param authClient the authenticated client holding the session to use
	 */
	public GoldenGateFdpClient(AuthenticatedClient authClient) {
		this.authClient = authClient;
	}
	
	/**
	 * Retrieve the descriptors of the file groups hosted by the backing FDP.
	 * @return an array holding the file group descriptors
	 */
	public FileGroupDescriptor[] getFileGroups() throws IOException {
		Connection con = null;
		try {
			con = this.authClient.getConnection();
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
	
	/**
	 * Retrieve the descriptors of the files hosted by the backing FDP within a
	 * given file group.
	 * @param fileGroupName the name of the file group whose content to list
	 * @return an array holding the file descriptors
	 */
	public FileDescriptor[] getFiles(String fileGroupName) throws IOException {
		Connection con = null;
		try {
			con = this.authClient.getConnection();
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
	
	/**
	 * Retrieve the content of a file hosted by the backing FDP.
	 * @param fileGroupName the name of the file group the file belongs to
	 * @param fileName the name of the file to retrieve
	 * @return the file (as an in-memory object)
	 */
	public ByteArrayFile getFile(String fileGroupName, String fileName) throws IOException {
		Connection con = null;
		try {
			con = this.authClient.getConnection();
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
			
			//	receive and return file
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
	
	/**
	 * Update a file hosted by the backing FDP.
	 * @param fileGroupName the name of the file group the file belongs to
	 * @param fileName the name of the file to delete
	 * @return the file (as an in-memory object)
	 */
	public boolean updateFile(String fileGroupName, String fileName, File file) throws IOException {
		return this.updateFile(fileGroupName, fileName, new BufferedInputStream(new FileInputStream(file)), file.lastModified());
	}
	
	/**
	 * Update a file hosted by the backing FDP. After sending the contents of
	 * the file, this method closes the argument stream.
	 * @param fileGroupName the name of the file group the file belongs to
	 * @param fileName the name of the file to update
	 * @param fileIn the input stream to read the file contents from
	 * @param lastMod the modification timestamp of the file
	 * @return the file (as an in-memory object)
	 */
	public boolean updateFile(String fileGroupName, String fileName, InputStream fileIn, long lastMod) throws IOException {
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedLineOutputStream blos = con.getOutputStream();
			blos.writeLine(UPDATE_FILE_COMMAND);
			blos.writeLine(this.authClient.getSessionID());
			blos.writeLine(fileGroupName);
			blos.writeLine(fileName);
			
			//	send data file as single-entry ZIP
			ZipOutputStream zip = new ZipOutputStream(blos);
			
			//	send file modification time
			ZipEntry lastModZe = new ZipEntry(LAST_MODIFIED_ZIP_ENTRY_NAME);
			zip.putNextEntry(lastModZe);
			for (int rs = 56; rs != -8; rs -=8)
				zip.write((int) ((lastMod >>> rs) & 0x00000000000000FFL));
			zip.flush();
			zip.closeEntry();
			
			//	send actual file
			ZipEntry ze = new ZipEntry(fileName);
			ze.setTime(lastMod);
//			System.out.println("Set time to " + lastMod);
//			System.out.println("get time as " + ze.getTime());
//			System.out.println(" ==> difference " + Math.abs(lastMod - ze.getTime()));
			//	TURNS OUT, inaccuracy originates from ZIP entry using _lossy_ conversion internally ...
			zip.putNextEntry(ze);
			byte[] buffer = new byte[1024];
			for (int r; (r = fileIn.read(buffer, 0, buffer.length)) != -1;)
				zip.write(buffer, 0, r);
			fileIn.close();
			zip.flush();
			zip.closeEntry();
			zip.finish();
			blos.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (UPDATE_FILE_COMMAND.equals(error))
				return true;
			else throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/**
	 * Delete a file hosted by the backing FDP.
	 * @param fileGroupName the name of the file group the file belongs to
	 * @param fileName the name of the file to delete
	 * @return the file (as an in-memory object)
	 */
	public boolean deleteFile(String fileGroupName, String fileName) throws IOException {
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			bw.write(DELETE_FILE_COMMAND);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(fileGroupName);
			bw.newLine();
			bw.write(fileName);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (DELETE_FILE_COMMAND.equals(error))
				return true;
			else throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
//	
//	public static void main(String[] args) throws Exception {
//		ServerConnection sc = ServerConnection.getServerConnection("localhost", 8015);
//		AuthenticatedClient auth = AuthenticatedClient.getAuthenticatedClient(sc);
//		GoldenGateFdpClient fdp = new GoldenGateFdpClient(auth);
//		FileGroupDescriptor[] fgds = fdp.getFileGroups();
//		for (int g = 0; g < fgds.length; g++) {
//			System.out.println(fgds[g].toTsvString());
//			FileDescriptor[] fds = fdp.getFiles(fgds[g].name);
//			for (int f = 0; f < fds.length; f++)
//				System.out.println(" - " + fds[f].toTsvString());
//		}
//		//	TODO_ne_in_GGE authenticate
//		//	TODO_ne_in_GGE test update & delete
//	}
}
