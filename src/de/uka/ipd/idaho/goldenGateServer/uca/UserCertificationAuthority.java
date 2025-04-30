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
package de.uka.ipd.idaho.goldenGateServer.uca;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.TreeMap;

import de.uka.ipd.idaho.easyIO.EasyIO;
import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.SqlQueryResult;
import de.uka.ipd.idaho.easyIO.sql.TableDefinition;
import de.uka.ipd.idaho.easyIO.streams.CharSequenceReader;
import de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponentRegistry;
import de.uka.ipd.idaho.goldenGateServer.uaa.UserAccessAuthority;

/**
 * GoldenGATE User Certification Authority manages the assignment of
 * certificates to user names, at three different levels of holdership that
 * qualify a user to perform modifications of data object, verify such
 * modifications made by uncertified users, or change the certification status
 * of other users.
 * This class manages assignment and IO. The actual certificates for specific
 * classes of data objects come from registered certificate providers.
 * 
 * @author sautter
 */
public class UserCertificationAuthority extends AbstractGoldenGateServerComponent implements UserCertificationAuthorityConstants {
	private static final String USER_CERTIFICATION_TABLE_NAME = "GgUcaUserCertifications";
	private static final String CERTIFICATE_NAME_COLUMN_NAME = "CertificateName";
	private static final String USER_NAME_COLUMN_NAME = "UserName";
	private static final String HOLDER_LEVEL_COLUMN_NAME = "HolderLevel";
	private static final String CERTIFY_USER_COLUMN_NAME = "CertifyUser";
	private static final String CERTIFY_TIMESTAMP_COLUMN_NAME = "CertifyTime";
	private static final String UPDATE_USER_COLUMN_NAME = "UpdateUser";
	private static final String UPDATE_TIMESTAMP_COLUMN_NAME = "UpdateTime";
	
	private UserAccessAuthority uaa;
	private IoProvider io;
	
	/** usual zero-argument constructor for class loading */
	public UserCertificationAuthority() {
		super("UCA");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#initComponent()
	 */
	protected void initComponent() {
		
		//	get database connection
		this.io = this.host.getIoProvider();
		if (!this.io.isJdbcAvailable())
			throw new RuntimeException("User Certification Authority cannot work without database access.");
		
		//	ensure user table
		TableDefinition td = new TableDefinition(USER_CERTIFICATION_TABLE_NAME);
		td.addColumn(CERTIFICATE_NAME_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 48);
		td.addColumn(USER_NAME_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
		td.addColumn(HOLDER_LEVEL_COLUMN_NAME, TableDefinition.CHAR_DATATYPE, 1);
		td.addColumn(CERTIFY_USER_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
		td.addColumn(CERTIFY_TIMESTAMP_COLUMN_NAME, TableDefinition.BIGINT_DATATYPE, 0);
		td.addColumn(UPDATE_USER_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
		td.addColumn(UPDATE_TIMESTAMP_COLUMN_NAME, TableDefinition.BIGINT_DATATYPE, 0);
		if (!this.io.ensureTable(td, true))
			throw new RuntimeException("User Certification Authority cannot work without database access.");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#link()
	 */
	public void link() {
		
		//	link up to UAA
		this.uaa = ((UserAccessAuthority) GoldenGateServerComponentRegistry.getServerComponent(UserAccessAuthority.class.getName()));
		if (this.uaa == null)
			throw new RuntimeException(UserAccessAuthority.class.getName());
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#exitComponent()
	 */
	protected void exitComponent() {
		
		//	log out from database
		this.io.close();
	}
	
	private static final String LIST_CERTIFICATES_COMMAND = "listCerts";
	private static final String REFETCH_CERTIFICATES_COMMAND = "refetchCerts";
	private static final String LIST_USER_CERTIFICATIONS_COMMAND = "listUserCerts";
	private static final String SET_USER_CERTIFICATION_COMMAND = "setUserCert";
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#getActions()
	 */
	public ComponentAction[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentAction ca;
		
		//	list certificates
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return LIST_CERTIFICATES_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						LIST_CERTIFICATES_COMMAND,
						"List the certificates known to the user certification authority."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					UserCertificate[] ucs = getCertificates();
					if (ucs.length == 0)
						this.reportResult("Currently, there are no certificates are available");
					else {
						this.reportResult("Currently, " + ucs.length + " certificates are available:");
						for (int c = 0; c < ucs.length; c++)
							this.reportResult(" - " + ucs[c].label + " (" + ucs[c].name + ", for " + ucs[c].targetClass.getName() + ")");
					}
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	re-fetch certificates from providers
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return REFETCH_CERTIFICATES_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						REFETCH_CERTIFICATES_COMMAND,
						"Re-fetch available certificates from providers."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					clearUserCertificates();
					UserCertificate[] ucs = getCertificates();
					if (ucs.length == 0)
						this.reportResult("Now, there are no certificates are available");
					else {
						this.reportResult("Now, " + ucs.length + " certificates are available:");
						for (int c = 0; c < ucs.length; c++)
							this.reportResult(" - " + ucs[c].label + " (" + ucs[c].name + ", for " + ucs[c].targetClass.getName() + ")");
					}
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	list certifications of user
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return LIST_USER_CERTIFICATIONS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						LIST_USER_CERTIFICATIONS_COMMAND + " <userName>",
						"List the certifications held by a given user:",
						"- <userName>: the name of the user whose certifications to list"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length != 1) {
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the user name as the only argument.");
					return;
				}
				if (!uaa.isValidUsername(arguments[0])) {
					this.reportError(" Invalid user name '" + arguments[0] + "'.");
					return;
				}
				UserCertification[] ucs = getCertifications(arguments[0]);
				if (ucs.length == 0) {
					this.reportResult("User '" + arguments[0] + "' currently has no certifications");
					return;
				}
				this.reportResult("User '" + arguments[0] + "' currently has " + ucs.length + " certifications:");
				for (int c = 0; c < ucs.length; c++) {
					UserCertificate uc = getUserCertificate(ucs[c].certificateName);
					if (uc == null)
						continue;
					this.reportResult(" - " + uc.label + " (" + uc.name + ") at level " + ucs[c].holderLevel);
				}
			}
		};
		cal.add(ca);
		
		//	set certification level of user
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return SET_USER_CERTIFICATION_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						SET_USER_CERTIFICATION_COMMAND + " <userName> <certName> <holderLevel>",
						"List the certifications held by a given user:",
						"- <userName>: the name of the user whose certification level to set",
						"- <certName>: the name of the certificate to set the holder level for",
						"- <holderLevel>: the holder level ('U', 'H', 'V', or 'C')"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length != 3) {
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the user name, certificate name, and holder level as the only arguments.");
					return;
				}
				if (!uaa.isValidUsername(arguments[0])) {
					this.reportError(" Invalid user name '" + arguments[0] + "'.");
					return;
				}
				UserCertificate cert = getUserCertificate(arguments[1]);
				if (cert == null) {
					this.reportError(" Invalid certificate name '" + arguments[1] + "'.");
					return;
				}
				if ((arguments[2].length() != 1) || ("".indexOf(arguments[2]) == -1)) {
					this.reportError(" Invalid certificate holder level '" + arguments[2] + "'.");
					return;
				}
				boolean hlModified = setUserCertification(arguments[1], arguments[0], arguments[2].charAt(0), "AdminConsole", System.currentTimeMillis());
				if (hlModified)
					this.reportResult(" Holder level set to '" + arguments[2] + "' for user '" + arguments[0] + "' and certificate '" + arguments[1] + "'.");
				else this.reportResult(" Holder level unchanged at '" + arguments[2] + "' for user '" + arguments[0] + "' and certificate '" + arguments[1] + "'.");
			}
		};
		cal.add(ca);
		
		//	get available certificates
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_CERTIFICATES;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				UserCertificate[] certs = getCertificates();
				output.write(GET_CERTIFICATES);
				output.newLine();
				if (certs.length == 0) {
					output.write("<" + UserCertificate.CERTIFICATE_LIST_NODE_TYPE + "/>");
					output.newLine();
				}
				else {
					output.write("<" + UserCertificate.CERTIFICATE_LIST_NODE_TYPE + ">");
					output.newLine();
					for (int c = 0; c < certs.length; c++) {
						output.write(certs[c].toXml());
						output.newLine();
					}
					output.write("</" + UserCertificate.CERTIFICATE_LIST_NODE_TYPE + ">");
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	get last modification of available certificates
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_CERTIFICATES_LAST_MODIFIED;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				output.write(GET_CERTIFICATES_LAST_MODIFIED);
				output.newLine();
				output.write("" + certificatesLastModified);
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	get certifications of given session user
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_CERTIFICATIONS;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check session ID and get user name
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session.");
					output.newLine();
					return;
				}
				String userName = uaa.getUserNameForSession(sessionId);
				
				//	get and send certifications
				UserCertification[] userCerts = getCertifications(userName);
				output.write(GET_CERTIFICATIONS);
				output.newLine();
				for (int c = 0; c < userCerts.length; c++) {
					output.write(userCerts[c].toTsvString());
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	get certifications of arbitrary user
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_USER_NAMES;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check session ID
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session.");
					output.newLine();
					return;
				}
				
				//	check permissions
				String userName = uaa.getUserNameForSession(sessionId);
				if (!uaa.isAdmin(userName) && !isCertifier(userName)) {
					output.write("You have no permission to see names of other users.");
					output.newLine();
					return;
				}
				
				//	send user names
				String[] userNames = uaa.getUserNames();
				output.write(GET_USER_NAMES);
				output.newLine();
				for (int n = 0; n < userNames.length; n++) {
					output.write(userNames[n]);
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	get certifications of arbitrary user
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_USER_CERTIFICATIONS;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check session ID
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session.");
					output.newLine();
					return;
				}
				
				//	check permissions
				String userName = uaa.getUserNameForSession(sessionId);
				if (!uaa.isAdmin(userName) && !isCertifier(userName)) {
					output.write("You have no permission to see certifications of other users.");
					output.newLine();
					return;
				}
				
				//	get and check subject user name
				String subjectUserName = input.readLine();
				if (!uaa.isActualUser(subjectUserName)) {
					output.write("Invalid user name '" + subjectUserName + "'.");
					output.newLine();
					return;
				}
				
				//	send certifications of subject user
				UserCertification[] subjectUserCerts = getUserCertifications(subjectUserName, (uaa.isAdmin(userName) ? null : userName));
				output.write(GET_USER_CERTIFICATIONS);
				output.newLine();
				for (int c = 0; c < subjectUserCerts.length; c++) {
					output.write(subjectUserCerts[c].toTsvString());
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	set user certifications
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return SET_USER_CERTIFICATIONS;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check session ID
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session.");
					output.newLine();
					return;
				}
				
				//	check permissions
				String userName = uaa.getUserNameForSession(sessionId);
				if (!uaa.isAdmin(userName) && !isCertifier(userName)) {
					output.write("You have no permission to see certifications of other users.");
					output.newLine();
					return;
				}
				
				//	get and check subject user name
				String subjectUserName = input.readLine();
				if (!uaa.isActualUser(subjectUserName)) {
					output.write("Invalid user name '" + subjectUserName + "'.");
					output.newLine();
					return;
				}
				if (!uaa.isAdmin(userName) && userName.equals(subjectUserName)) {
					output.write("Cannot change own certifications.");
					output.newLine();
					return;
				}
				
				//	buffer, read, and check certifications (need to buffer to catch terminating blank line)
				StringBuffer subjectUserCertData = new StringBuffer();
				for (String sucLine; (sucLine = input.readLine()) != null;) {
					if (sucLine.length() == 0)
						break;
					subjectUserCertData.append(sucLine);
					subjectUserCertData.append("\r\n");
				}
				UserCertification[] subjectUserCerts = UserCertification.readCertifications(new CharSequenceReader(subjectUserCertData));
				if (!uaa.isAdmin(userName)) {
					for (int c = 0; c < subjectUserCerts.length; c++)
						if (!isCertifier(userName, subjectUserCerts[c].certificateName)) {
							output.write("No permission to change certification '" + subjectUserCerts[c].certificateName + "'.");
							output.newLine();
							return;
						}
				}
				
				//	change certifications of subject user
				long timestamp = System.currentTimeMillis();
				for (int c = 0; c < subjectUserCerts.length; c++)
					setUserCertification(subjectUserCerts[c].certificateName, subjectUserName, subjectUserCerts[c].holderLevel, userName, timestamp);
				
				//	indicate success
				output.write(SET_USER_CERTIFICATIONS);
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	finally ...
		return ((ComponentAction[]) cal.toArray(new ComponentAction[cal.size()]));
	}
	
	private UserCertificate[] certificates = null;
	private long certificatesLastModified = -1;
	private TreeMap certificatesByName = new TreeMap();
	private TreeMap certSummariesByUserName = new TreeMap();
	
	synchronized UserCertificate[] getCertificates() {
		if (this.certificates == null) /* get and cache on demand */ {
			ArrayList certificates = new ArrayList();
			for (Iterator pit = this.certificateProviders.iterator(); pit.hasNext();) {
				UserCertificateProvier ucp = ((UserCertificateProvier) pit.next());
				UserCertificate[] ucs = ucp.getUserCertificates();
				if (ucs != null)
					certificates.addAll(Arrays.asList(ucs));
			}
			this.certificates = ((UserCertificate[]) certificates.toArray(new UserCertificate[certificates.size()]));
			for (int c = 0; c < this.certificates.length; c++)
				this.certificatesByName.put(this.certificates[c].name, this.certificates[c]);
			this.certificatesLastModified = System.currentTimeMillis();
		}
		return this.certificates;
	}
	
	synchronized UserCertificate getUserCertificate(String certName) {
		if (this.certificates == null)
			this.getCertificates();
		return ((UserCertificate) this.certificatesByName.get(certName));
	}
	
	synchronized void clearUserCertificates() {
		this.certificates = null;
		this.certificatesByName.clear();
	}
	
	private synchronized UserCertificationSummary getCertificationSummary(String userName) {
		UserCertificationSummary ucs = ((UserCertificationSummary) this.certSummariesByUserName.get(userName));
		if (ucs == null) {
			String ucQuery = "SELECT " + CERTIFICATE_NAME_COLUMN_NAME + ", " + HOLDER_LEVEL_COLUMN_NAME +
				" FROM " + USER_CERTIFICATION_TABLE_NAME +
				" WHERE " + USER_NAME_COLUMN_NAME + " = '" + EasyIO.sqlEscape(userName) + "'" +
				";";
			SqlQueryResult sqr = null;
			try {
				sqr = this.io.executeSelectQuery(ucQuery, true);
				while (sqr.next()) {
					String certName = sqr.getString(0);
					String holderLevel = sqr.getString(1);
					if (ucs == null)
						ucs = new UserCertificationSummary(userName);
					ucs.putCertification(certName, holderLevel.charAt(0));
				}
				this.certSummariesByUserName.put(userName, ucs);
			}
			catch (SQLException sqle) {
				this.logError("UserCertificationAuthority: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while loading user certifications.");
				this.logError("  query was " + ucQuery);
			}
			finally {
				if (sqr != null)
					sqr.close();
			}
		}
		return ucs;
	}
	
	UserCertification[] getCertifications(String userName) {
		UserCertificationSummary ucs = this.getCertificationSummary(userName);
		return ((ucs == null) ? new UserCertification[0] : ucs.getCertifications());
	}
	
	boolean isCertifier(String userName) {
		UserCertification[] ucs = this.getCertifications(userName);
		for (int c = 0; c < ucs.length; c++) {
			if (ucs[c].canCertify())
				return true;
		}
		return false;
	}
	
	boolean isCertifier(String userName, String certName) {
		UserCertificationSummary ucs = this.getCertificationSummary(userName);
		return ((ucs != null) && ucs.canCertify(certName));
	}
	
	UserCertification[] getUserCertifications(String subjectUserName, String lookupUserName) {
		UserCertification[] sUcs = this.getCertifications(subjectUserName);
		
		//	admin lookup, show them all
		if (lookupUserName == null)
			return sUcs;
		
		//	filter by certifier name
		UserCertificationSummary lUcs = this.getCertificationSummary(lookupUserName);
		if (lUcs == null)
			return new UserCertification[0];
		ArrayList cUcs = new ArrayList();
		for (int c = 0; c < sUcs.length; c++) {
			if (lUcs.canCertify(sUcs[c].certificateName))
				cUcs.add(sUcs[c]);
		}
		return ((cUcs.size() == sUcs.length) ? sUcs : ((UserCertification[]) cUcs.toArray(new UserCertification[cUcs.size()])));
	}
	
	synchronized boolean setUserCertification(String certName, String subjectUserName, char holderLevel, String updateUserName, long updateTime) {
		if ("UHVC".indexOf(holderLevel) == -1)
			throw new IllegalArgumentException("Invalid certificate holder level '" + holderLevel + "'");
		UserCertificationSummary sUcs = this.getCertificationSummary(subjectUserName);
		if ((sUcs != null) && (holderLevel == sUcs.getHolderLevel(certName)))
			return false; // no changes
		
		//	update or create database entry (never remove, though, we want to retain information who withdrew certification)
		String ucUpdateQuery = "UPDATE " + USER_CERTIFICATION_TABLE_NAME +
				" SET " + HOLDER_LEVEL_COLUMN_NAME + " = '" + holderLevel + "'" +
				", " + UPDATE_USER_COLUMN_NAME + " = '" + EasyIO.sqlEscape(updateUserName) + "'" +
				", " + UPDATE_TIMESTAMP_COLUMN_NAME + " = " + updateTime + "" +
				" WHERE " + CERTIFICATE_NAME_COLUMN_NAME + " = '" + EasyIO.sqlEscape(certName) + "'" +
				" AND " + USER_NAME_COLUMN_NAME + " = '" + EasyIO.sqlEscape(subjectUserName) + "'" +
				";";
		try {
			int updated = this.io.executeUpdateQuery(ucUpdateQuery);
			if (updated != 0) {
				if (sUcs != null)
					sUcs.putCertification(certName, holderLevel);
				return true;
			}
		}
		catch (SQLException sqle) {
			this.logError("UserCertificationAuthority: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while updating user certifications.");
			this.logError("  query was " + ucUpdateQuery);
		}
		
		//	no new record for holder level 'uncertified'
		if (holderLevel == UserCertification.HOLDER_LEVEL_UNCERTIFIED)
			return false;
		
		//	store new certification record
		String ucInsertQuery = "INSERT INTO " + USER_CERTIFICATION_TABLE_NAME +
				" (" + CERTIFICATE_NAME_COLUMN_NAME + ", " + USER_NAME_COLUMN_NAME + ", " + HOLDER_LEVEL_COLUMN_NAME + ", " + CERTIFY_USER_COLUMN_NAME + ", " + CERTIFY_TIMESTAMP_COLUMN_NAME + ", " + UPDATE_USER_COLUMN_NAME + ", " + UPDATE_TIMESTAMP_COLUMN_NAME + ")" +
				" VALUES" +
				" ('" + EasyIO.sqlEscape(certName) + "', '" + EasyIO.sqlEscape(subjectUserName) + "', '" + holderLevel + "', '" + EasyIO.sqlEscape(updateUserName) + "', " + updateTime + ", '" + EasyIO.sqlEscape(updateUserName) + "', " + updateTime + ")" +
				";";
		try {
			this.io.executeUpdateQuery(ucInsertQuery);
			if (sUcs != null)
				sUcs.putCertification(certName, holderLevel);
			return true;
		}
		catch (SQLException sqle) {
			this.logError("UserCertificationAuthority: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while storing user certifications.");
			this.logError("  query was " + ucInsertQuery);
		}
		
		//	whatever went wrong ...
		return false;
	}
	
	/**
	 * Provider of the actual user certificates for arbitrary classes of data
	 * objects.
	 * 
	 * @author sautter
	 */
	public static interface UserCertificateProvier {
		
		/**
		 * Retrieve the certificates under the authority of the specific
		 * provider.
		 * @return an array holding the certificates
		 */
		public abstract UserCertificate[] getUserCertificates();
	}
	
	/**
	 * Add a user certificate provider so the certificates under its authority
	 * can be centrally managed by the certification authority.
	 * @param ucp the certificate provider to add
	 */
	public void addUserCertificateProvider(UserCertificateProvier ucp) {
		if (ucp == null)
			return;
		this.certificateProviders.add(ucp);
		this.clearUserCertificates();
	}
	
	/**
	 * Remove a user certificate provider. This should rarely happen in normal
	 * operation, as it will leave users with unresolvabel certifications.
	 * @param ucp the certificate provider to remove
	 */
	public void removeUserCertificateProvier(UserCertificateProvier ucp) {
		if (ucp == null)
			return;
		this.certificateProviders.remove(ucp);
		this.clearUserCertificates();
	}
	
	private LinkedHashSet certificateProviders = new LinkedHashSet();
}
