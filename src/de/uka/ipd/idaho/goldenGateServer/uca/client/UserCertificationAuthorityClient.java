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
package de.uka.ipd.idaho.goldenGateServer.uca.client;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection.Connection;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.goldenGateServer.uca.UserCertificationAuthorityConstants;

/**
 * Client object for GoldenGATE User Certification Authority
 * 
 * @author sautter
 */
public class UserCertificationAuthorityClient implements UserCertificationAuthorityConstants {
	private AuthenticatedClient authClient;
	
	/** Constructor
	 * @param authClient the authenticated client to use for communication
	 */
	public UserCertificationAuthorityClient(AuthenticatedClient authClient) {
		this.authClient = authClient;
	}
	
	/**
	 * Retrieve the available certificates from the backing server.
	 * @return an array holding the certificates
	 * @throws IOException
	 */
	public UserCertificate[] getCertificates() throws IOException {
		return this.getCertificates(true);
	}
	
	/**
	 * Retrieve the available certificates from the backing server.
	 * @param allowCache allow returning previously fetched certificates?
	 * @return an array holding the certificates
	 * @throws IOException
	 */
	public UserCertificate[] getCertificates(boolean allowCache) throws IOException {
		if (allowCache && (this.certificates != null))
			return Arrays.copyOf(this.certificates, this.certificates.length);
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_CERTIFICATES);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_CERTIFICATES.equals(error)) {
				this.certificates = UserCertificate.readCertificates(br);
				return Arrays.copyOf(this.certificates, this.certificates.length);
			}
			else throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	private UserCertificate[] certificates = null;
	
	/**
	 * Retrieve the certifications of the user logged in on the wrapped
	 * authenticated client.
	 * @return an array holding the certifications
	 * @throws IOException
	 */
	public UserCertification[] getCertifications() throws IOException {
		return this.getCertifications(true);
	}
	
	/**
	 * Retrieve the certifications of the user logged in on the wrapped
	 * authenticated client.
	 * @param allowCache allow returning previously fetched certifications?
	 * @return an array holding the certifications
	 * @throws IOException
	 */
	public UserCertification[] getCertifications(boolean allowCache) throws IOException {
		if (!this.authClient.ensureLoggedIn())
			throw new IOException("Not logged in.");
		
		if (allowCache && (this.userCerts != null))
			return Arrays.copyOf(this.userCerts, this.userCerts.length);
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_CERTIFICATIONS);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_CERTIFICATIONS.equals(error)) {
				this.userCerts = UserCertification.readCertifications(br);
				this.userCertSummary = UserCertificationSummary.createSummary(this.userCerts);
				this.userIsCertifier = false;
				for (int c = 0; c < this.userCerts.length; c++) {
					if (this.userCerts[c].canCertify())
						this.userIsCertifier = true;
				}
				return Arrays.copyOf(this.userCerts, this.userCerts.length);
			}
			else throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	private UserCertification[] userCerts = null;
	private UserCertificationSummary userCertSummary = null;
	private boolean userIsCertifier = false;
	
	/**
	 * Test whether or not the user logged in on the wrapped authenticated
	 * client holds a certificate with a given name.
	 * @param certName the name of the certificate to check.
	 * @return true if the subject user holds the certificate with the
	 *        argument name
	 */
	public boolean holdsCertificate(String certName) throws IOException {
		if (this.userCerts == null)
			this.getCertifications();
		return ((this.userCertSummary != null) && this.userCertSummary.holdsCertificate(certName));
	}
	
	/**
	 * Test whether or not the user logged in on the wrapped authenticated
	 * client is authorized to approve data object modifications that are under
	 * the auspice of a certificate with a given name.
	 * @param certName the name of the certificate to check.
	 * @return true if the subject user approve modifications under the
	 *        auspice of the certificate with the argument name
	 */
	public boolean canVerify(String certName) throws IOException {
		if (this.userCerts == null)
			this.getCertifications();
		return ((this.userCertSummary != null) && this.userCertSummary.canVerify(certName));
	}
	
	/**
	 * Test whether or not the user logged in on the wrapped authenticated
	 * client is authorized to assign any certificates to other users.
	 * @return true if the subject user is authorized to assign certificates
	 *            to other users
	 */
	public boolean isCertifier() throws IOException {
		if (this.userCerts == null)
			this.getCertifications();
		return this.userIsCertifier;
	}
	
	/**
	 * Test whether or not the user logged in on the wrapped authenticated
	 * client is authorized to assign a certificate with a given name to other
	 * users.
	 * @param certName the name of the certificate to check.
	 * @return true if the subject user is authorized to assign the
	 *        certificate with the argument name to other users
	 */
	public boolean canCertify(String certName) throws IOException {
		if (this.userCerts == null)
			this.getCertifications();
		return ((this.userCertSummary != null) && this.userCertSummary.canCertify(certName));
	}
	
	/**
	 * Retrieve the names of users. Unless the user logged in on the wrapped
	 * authenticated client is an administrator, they must hold some certificate
	 * at level 'certifier' for this method call to be allowed.
	 * @return an array holding the user names
	 * @throws IOException
	 */
	public String[] getUserNames() throws IOException {
		if (!this.authClient.ensureLoggedIn())
			throw new IOException("Not logged in.");
		
		if (!this.authClient.isAdmin() && !this.userIsCertifier) {
			this.getCertifications(false);
			if (this.userIsCertifier)
				throw new IOException("Not a certifier.");
		}
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_USER_NAMES);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_USER_NAMES.equals(error)) {
				ArrayList userNames = new ArrayList();
				for (String userName; (userName = br.readLine()) != null;) {
					if (userName.length() != 0)
						userNames.add(userName);
				}
				return ((String[]) userNames.toArray(new String[userNames.size()]));
			}
			else throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/**
	 * Retrieve the certifications of a given user. Unless the user logged in
	 * on the wrapped authenticated client is an administrator, the returned
	 * array only covers the certificates the logged in user themselves holds
	 * at level 'certifier'.
	 * @param userName the name of the user whose certifications to fetch
	 * @return an array holding the certifications
	 * @throws IOException
	 */
	public UserCertification[] getUserCertifications(String userName) throws IOException {
		if (!this.authClient.ensureLoggedIn())
			throw new IOException("Not logged in.");
		
		if (!this.authClient.isAdmin() && !this.userIsCertifier) {
			this.getCertifications(false);
			if (this.userIsCertifier)
				throw new IOException("Not a certifier.");
		}
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_USER_CERTIFICATIONS);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(userName);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_USER_CERTIFICATIONS.equals(error))
				return UserCertification.readCertifications(br);
			else throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/**
	 * Update the certifications of a given user. Unless the user logged in on
	 * the wrapped authenticated client is an administrator, this method only
	 * modifies holder levels for certificates the logged in user themselves
	 * holds at level 'certifier'.
	 * @param userName the name of the user whose certifications to set
	 * @param certs the certifications to set
	 * @throws IOException
	 */
	public void setUserCertifications(String userName, UserCertification[] certs) throws IOException {
		if (!this.authClient.ensureLoggedIn())
			throw new IOException("Not logged in.");
		
		if (!this.authClient.isAdmin()) {
			UserCertification[] authCerts = this.getCertifications(false);
			HashSet authCertNames = new HashSet();
			for (int c = 0; c < authCerts.length; c++) {
				if (authCerts[c].canCertify())
					authCertNames.add(authCerts[c].certificateName);
			}
			if (authCertNames.isEmpty())
				throw new IOException("Not a certifier.");
			ArrayList fCerts = new ArrayList();
			for (int c = 0; c < certs.length; c++) {
				if (authCertNames.contains(certs[c].certificateName))
					fCerts.add(certs[c]);
			}
			if (fCerts.isEmpty())
				throw new IOException("Not a certifier.");
			if (fCerts.size() < certs.length)
				certs = ((UserCertification[]) fCerts.toArray(new UserCertification[fCerts.size()]));
		}
		
		Connection con = null;
		try {
			con = this.authClient.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(SET_USER_CERTIFICATIONS);
			bw.newLine();
			bw.write(this.authClient.getSessionID());
			bw.newLine();
			bw.write(userName);
			bw.newLine();
			for (int c = 0; c < certs.length; c++) {
				bw.write(certs[c].toTsvString());
				bw.newLine();
			}
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (!SET_USER_CERTIFICATIONS.equals(error))
				throw new IOException(error);
		}
		finally {
			if (con != null)
				con.close();
		}
	}
	
	/**
	 * Retrieve the available certificates from a web based endpoint. This
	 * method is intended as a fallback for situations without an authenticated
	 * user or server connection.
	 * @param certUrl the endpoint URL to fetch the certificates from
	 * @return an array holding the certificates
	 * @throws IOException
	 */
	public static UserCertificate[] getCertificatesFromUrl(String certUrl) throws IOException {
		URL url = new URL(certUrl);
		BufferedReader br = new BufferedReader(new InputStreamReader(new BufferedInputStream(url.openStream()), "UTF-8"));
		UserCertificate[] certs = UserCertificate.readCertificates(br);
		br.close();
		return certs;
	}
}
