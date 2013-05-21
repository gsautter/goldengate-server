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
package de.uka.ipd.idaho.goldenGateServer.uds.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection.Connection;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.goldenGateServer.uds.GoldenGateUdsConstants;

/**
 * Client for GoldenGATE UDS.
 * 
 * @author sautter
 */
public class GoldenGateUdsClient implements GoldenGateUdsConstants {
	private AuthenticatedClient authClient;
	
	/**
	 * Constructor
	 * @param ac the authenticated client to use for authentication and
	 *            connection
	 */
	public GoldenGateUdsClient(AuthenticatedClient ac) {
		this.authClient = ac;
	}
	
	/**
	 * Retrieve the data fields from the backing server.
	 * @return the data fields, packed in an array
	 * @throws IOException
	 */
	public FieldSet[] getFieldSets() throws IOException {
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_FIELDS);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_FIELDS.equals(error))
				return FieldSet.readFieldSets(br);
				
			else throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/**
	 * Retrieve the data for the user logged in on the wrapped authenticated
	 * client.
	 * @return a Properties object holding the data of the user logged in on the
	 *         wrapped authenticated client
	 * @throws IOException
	 */
	public UserDataSet getData() throws IOException {
		return this.getData(null);
	}
	
	/**
	 * Retrieve the data for a specific user. Specifying null as the user name
	 * results in fetching the data for the user logged in on the wrapped
	 * authenticated client. For other user names, this method requires
	 * administrative priviledges.
	 * @param userName the user whose data to retrieve
	 * @return a Properties object holding the data of the specified user
	 * @throws IOException
	 */
	public UserDataSet getData(String userName) throws IOException {
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_DATA);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write((userName == null) ? "" : userName);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_DATA.equals(error))
				return UserDataSet.readData(br);
			else throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/**
	 * Update the data of the user logged in on the wrapped authenticated
	 * client.
	 * @param data the new data values, stored in a Properties object
	 * @throws IOException
	 */
	public void updateData(UserDataSet data) throws IOException {
		this.updateData(data, null);
	}
	
	/**
	 * Update the data of the user logged in on the wrapped authenticated
	 * client. Specifying null as the user name results in updating the data for
	 * the user logged in on the wrapped authenticated client. For other user
	 * names, this method requires administrative priviledges.
	 * @param data the new data values, stored in a Properties object
	 * @param userName the user whose data to update
	 * @throws IOException
	 */
	public void updateData(UserDataSet data, String userName) throws IOException {
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(UPDATE_DATA);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write((userName == null) ? "" : userName);
			bw.newLine();
			data.writeData(bw);
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!UPDATE_DATA.equals(error))
				throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
}
