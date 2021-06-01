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
package de.uka.ipd.idaho.goldenGateServer.https;

import java.io.IOException;
import java.net.UnknownHostException;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;

import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.utilities.ApplicationHttpsEnabler;
import de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent;

/**
 * This component adds HTTPS client capability to the GoldenGATE Server it is
 * installed in. It maintains its own list of trusted SSL certificates that can
 * be extended via the server console.
 * 
 * @author sautter
 */
public class GgServerHttpsEnabler extends AbstractGoldenGateServerComponent {
	private ApplicationHttpsEnabler httpsEnabler;
	private IoProvider io;
	
	private long trustingThreadId = -1;
	private boolean trustingThreadIdAsked = false;
	
	/** Constructor handing 'HTTPS' as the letter code to the super class */
	public GgServerHttpsEnabler() {
		super("HTTPS");
	}
	/* TODO
Add list of trusted hosts:
- store in config
- automatically accept new certificates from hosts on list ...
- ... optionally including subdomains ...
- ... unless there already is a still-valid certificate under that alias ...
- ... unless existing one close to expiration

Add auto-renew option:
- automatically accept new certificates from hosts already having existing one ...
- ... if existing one is close to expiration or expired

Use "<hostName>-0" alias to check for existing certificates ...
... also truncating any leading subdomains
	 */
	protected void initComponent() {
		this.io = this.host.getIoProvider();
		this.httpsEnabler = new ApplicationHttpsEnabler(this.dataPath, false, false) {
			protected boolean askPermissionToAccept(String hostName, X509Certificate[] chain) {
				if (Thread.currentThread().getId() == trustingThreadId) {
					trustingThreadIdAsked = true;
					return true;
				}
				else {
					boolean sendNotification = true;
					if (!host.isClientRequest()) {
						StackTraceElement[] stes = Thread.currentThread().getStackTrace();
						for (int e = 0; e < stes.length; e++) try {
							Class steClass = Class.forName(stes[e].getClassName());
							if (ComponentActionConsole.class.isAssignableFrom(steClass)) {
								sendNotification = false;
								break;
							}
						}
						catch (Throwable t) {
							logError(t);
						}
					}
					if (sendNotification)
						sendUntrustedCertificateNotification(hostName, chain, null);
					return false;
				}
			}
		};
		try {
			this.httpsEnabler.init();
		}
		catch (IOException ioe) {
			System.out.println("Could not initialize HTTPS: " + ioe.getMessage());
			ioe.printStackTrace(System.out);
			throw new RuntimeException(ioe);
		}
	}
	
	private Map lastNotificationSent = Collections.synchronizedMap(new TreeMap(String.CASE_INSENSITIVE_ORDER));
	void sendUntrustedCertificateNotification(String hostName, X509Certificate[] chain, ComponentActionConsole cac) {
		if (chain != null) {
			Long lnc = ((Long) this.lastNotificationSent.get(hostName));
			if ((lnc != null) && ((lnc.longValue() + (1000 * 60 * 60)) > System.currentTimeMillis()))
				return; // send at most one notification per remote host and hour
		}
		if (!this.io.isMessagingAvailable()) {
			if (cac == null)
				this.logError("Configuration for sending notification e-mail incomplete.");
			else cac.reportError("Configuration for sending notification e-mail incomplete.");
			return;
		}
		String adminNotifiationAddresses = this.host.getServerProperty("SendAdminNotificationsTo");
		if (adminNotifiationAddresses == null) {
			if (cac == null)
				this.logError("No admin addresses configured to send notifications to.");
			else cac.reportError("No admin addresses configured to send notifications to.");
			return;
		}
		String[] sendNotificationTo = adminNotifiationAddresses.split("\\s+");
		if (sendNotificationTo.length == 0) {
			if (cac == null)
				this.logError("No admin addresses configured to send notifications to.");
			else cac.reportError("No admin addresses configured to send notifications to.");
			return;
		}
		String subject;
		StringBuffer message = new StringBuffer();
		if (chain == null) {
			subject = "Untrusted Certificate Alert Test";
			message.append("This is a test mail !!!");
		}
		else {
			subject = ("Untrusted Certificate Alert for '" + hostName + "'");
			message.append("Remote host: " + hostName);
			message.append("\r\n");
			message.append("\r\n");
			message.append("Thread: " + Thread.currentThread().getName());
			message.append("\r\n");
			StackTraceElement[] stes = Thread.currentThread().getStackTrace();
			for (int e = 0; e < stes.length; e++) {
				message.append(" at " + stes[e].toString());
				message.append("\r\n");
			}
			message.append("\r\n");
			message.append("Certificate chain:");
			message.append("\r\n");
			for (int c = 0; c < chain.length; c++) {
				message.append(chain[c].toString());
				message.append("\r\n");
			}
		}
		try {
			this.io.smtpSend(subject, message.toString(), sendNotificationTo);
			if (chain != null)
				this.lastNotificationSent.put(hostName, new Long(System.currentTimeMillis()));
			if (cac != null)
				cac.reportResult("Test notification mail sent to " + Arrays.toString(sendNotificationTo));
		}
		catch (Exception e) {
			if (cac == null) {
				this.logError("Failed to send notification message: " + e.getMessage());
				this.logError(e);
			}
			else {
				cac.reportError("Failed to send notification message: " + e.getMessage());
				cac.reportError(e);
			}
		}
	}
	
	private static final String TRUST_COMMAND = "trust";
	private static final String TEST_NOTIFICATION_COMMAND = "testNotification";
	private static final String LIST_COMMAND = "list";
	private static final DateFormat certDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss") {
		public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition pos) {
			if (date == null)
				return toAppendTo.append("undefined");
			else return super.format(date, toAppendTo, pos);
		}
	};
	
	public ComponentAction[] getActions() {
		ComponentAction[] cas = {
				
			//	provide console action adding certificate(s) from argument URL to trusted list
			new ComponentActionConsole() {
				public String getActionCommand() {
					return TRUST_COMMAND;
				}
				public String[] getExplanation() {
					String[] explanation = {
							TRUST_COMMAND + " <host>",
							"Add the HTTPS certificates from a given host to the trusted list:",
							"- <host>: the host whose certificates to add.",
						};
					return explanation;
				}
				public void performActionConsole(String[] arguments) {
					if (arguments.length == 1)
						importCertificates(arguments[0], this);
					else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify only the domain name.");
				}
			},
			
			//	provide console action for testing notification email
			new ComponentActionConsole() {
				public String getActionCommand() {
					return TEST_NOTIFICATION_COMMAND;
				}
				public String[] getExplanation() {
					String[] explanation = {
							TEST_NOTIFICATION_COMMAND,
							"Send a test notification email to the configured administrators",
						};
					return explanation;
				}
				public void performActionConsole(String[] arguments) {
					if (arguments.length == 0)
						sendUntrustedCertificateNotification("test", null, this);
					else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
				}
			},
			
			//	provide console action listing certificate(s) on trusted list
			new ComponentActionConsole() {
				public String getActionCommand() {
					return LIST_COMMAND;
				}
				public String[] getExplanation() {
					String[] explanation = {
							LIST_COMMAND + " <mode>",
							"List the HTTPS certificates on the trusted list:",
							"- <mode>: set to '-c' to show full certificates or '-cc' full certificates chains (optional).",
						};
					return explanation;
				}
				public void performActionConsole(String[] arguments) {
					if (arguments.length > 1)
						this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify at most the mode.");
					else try {
						String mode = ((arguments.length == 1) ? arguments[0] : "-a");
						String[] aliases = httpsEnabler.getAliases();
						this.reportResult("There are currently " + aliases.length + " trusted certificates:");
						for (int a = 0; a < aliases.length; a++) {
							if ("-cc".equals(mode)) {
								X509Certificate[] chain = httpsEnabler.getCertificateChain(aliases[a]);
								this.reportResult(" - " + aliases[a] + ":");
								for (int c = 0; c < chain.length; c++)
									this.reportResult("   " + chain[c]);
							}
							else {
								X509Certificate cert = httpsEnabler.getCertificate(aliases[a]);
								if ("-c".equals(mode)) {
									this.reportResult(" - " + aliases[a] + ":");
									this.reportResult("   " + cert);
								}
								else if (cert != null) {
									this.reportResult(" - " + aliases[a] + ": " + certDateFormat.format(cert.getNotBefore()) + " - " + certDateFormat.format(cert.getNotAfter()));
								}
							}
						}
					}
					catch (KeyStoreException kse) {
						this.reportError("Could not get certificates: " + kse.getMessage());
						this.reportError(kse);
					}
				}
			}

		};
		return cas;
	}
	
	private void importCertificates(String host, ComponentActionConsole cac) {
		cac.reportResult("Getting certificates from " + host + " ...");
		
		//	mark ourselves as trusted (there is never more than one console thread)
		this.trustingThreadId = Thread.currentThread().getId();
		this.trustingThreadIdAsked = false;
		
		//	open socket to get certificates
		try {
			SSLSocket socket = (SSLSocket) HttpsURLConnection.getDefaultSSLSocketFactory().createSocket(host, 443);
			socket.setSoTimeout(10000);
			cac.reportResult(" - starting SSL handshake ...");
			socket.startHandshake();
			socket.close();
			if (this.trustingThreadIdAsked)
				cac.reportResult(" ==> " + host + " added to trusted list");
			else cac.reportResult(" ==> " + host + " already on trusted list");
		}
		catch (SSLException ssle) {
			cac.reportError(" ==> could not get certificates: " + ssle.getMessage());
			cac.reportError(ssle);
		}
		catch (UnknownHostException uhe) {
			cac.reportError(" ==> could not get certificates: " + uhe.getMessage());
			cac.reportError(uhe);
		}
		catch (IOException ioe) {
			cac.reportError(" ==> could not get certificates: " + ioe.getMessage());
			cac.reportError(ioe);
		}
		finally {
			this.trustingThreadId = -1;
		}
	}
}
