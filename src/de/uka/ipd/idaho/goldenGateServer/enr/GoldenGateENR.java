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
package de.uka.ipd.idaho.goldenGateServer.enr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FilterReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import de.uka.ipd.idaho.easyIO.util.JsonParser;
import de.uka.ipd.idaho.gamta.util.CountingSet;
import de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent;

/**
 * GoldenGATE External Notification Receiver (ENR) is a centralized service point
 * for server components that wish to receive notifications via a web facing
 * endpoint. In particular, such client components can register their notification
 * types with this component to receive respective notifications.
 * 
 * 
 * @author sautter
 */
public class GoldenGateENR extends AbstractGoldenGateServerComponent implements GoldenGateEnrConstants {
	
	/**
	 * Notification receiver to register with GoldenGATE NER.
	 * 
	 * @author sautter
	 */
	public static interface Receiver {
		
		/**
		 * Obtain a notification type object describing the notifications to be
		 * forwarded to the receiver, as well as the endpoint suffix (type name)
		 * to receive them under.
		 * @return the notification type
		 */
		public abstract NotificationType getNotificationType();
		
		/**
		 * Receive a notification. The runtime type of the argument notification
		 * is the one returned by the getNotificationType() method.
		 * @param notification the notification
		 */
		public abstract void receive(Notification notification);
	}
	
	private Map notificationTypesByName = Collections.synchronizedMap(new TreeMap());
	private Map notificationTypesToReceivers = Collections.synchronizedMap(new HashMap());
	
	private CountingSet notificationTypeCounts = new CountingSet(Collections.synchronizedMap(new TreeMap()));
	
	/** Constructor passing 'ENR' as the letter code to super constructor
	 */
	public GoldenGateENR() {
		super("ENR");
	}
	
	/**
	 * Register a receiver to subscribe to notifications of its specified type.
	 * This will also make said notification type available in the web facing
	 * notification endpoint.
	 * @param receiver the receiver to register
	 */
	public void registerReceiver(Receiver receiver) {
		NotificationType nt = receiver.getNotificationType();
		this.notificationTypesToReceivers.put(nt.name, receiver);
		this.notificationTypesByName.put(nt.name, nt);
	}
	
	/**
	 * Unregister a receiver to quit getting notifications.
	 * @param receiver the receiver to unregister
	 */
	public void unregisterReceiver(Receiver receiver) {
		NotificationType nt = receiver.getNotificationType();
		this.notificationTypesToReceivers.remove(nt.name);
		this.notificationTypesByName.remove(nt.name);
	}
	
	private static final String SHOW_STATS_COMMEND = "showStats";
	
	public ComponentAction[] getActions() {
		ArrayList cas = new ArrayList();
		ComponentAction ca;
		
		//	send out notification types
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_NOTIFICATION_TYPES;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	we're not accepting proxied requests
				if (host.isRequestProxied()) {
					output.write("Notification not allowed through HTTP tunnel proxy; use web endpoint.");
					output.newLine();
					return;
				}
				
				//	check if authorized
				boolean authorized = "true".equals(input.readLine());
				
				//	indicate data coming
				output.write(GET_NOTIFICATION_TYPES);
				output.newLine();
				
				//	send array of notification types
				output.write("[");
				output.newLine();
				for (Iterator ntit = notificationTypesByName.keySet().iterator(); ntit.hasNext();) {
					NotificationType nt = ((NotificationType) notificationTypesByName.get(ntit.next()));
					output.write(nt.toJson(authorized));
					if (ntit.hasNext())
						output.write(",");
					output.newLine();
				}
				output.write("]");
				output.newLine();
			}
		};
		cas.add(ca);
		
		//	receive notification
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return RECEIVE_NOTIFICATION;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	we're not accepting proxied requests
				if (host.isRequestProxied()) {
					output.write("Notification not allowed through HTTP tunnel proxy; use web endpoint.");
					output.newLine();
					return;
				}
				
				//	read notification type and path info
				String nTypeName = input.readLine();
				logInfo("Notification type is " + nTypeName);
				String nPathInfo = input.readLine();
				logInfo("Path info is " + nPathInfo);
				
				//	read user name
				String nUserName = input.readLine();
				logInfo("User name is " + nUserName);
				
				//	get notification type and receiver
				NotificationType nType = ((NotificationType) notificationTypesByName.get(nTypeName));
				Receiver rec = ((Receiver) notificationTypesToReceivers.get(nTypeName));
				if ((nType == null) || (rec == null)) {
					notificationTypeCounts.add("phony");
					output.write("Invalid notification type '" + nTypeName + "'.");
					output.newLine();
					return;
				}
				notificationTypeCounts.add(nType.name);
				
				//	read notification data and pass it to receiver
//				Object nData = JsonParser.parseJson(input);
				final StringBuffer nDataBuf = new StringBuffer();
				try {
					Object nData = JsonParser.parseJson(new FilterReader(input) {
						public int read() throws IOException {
							try {
								int r = super.read();
								nDataBuf.append((char) r);
								return r;
							}
							catch (IOException ioe) {
								logWarning("ENR: padding 1 space");
								return ((int) ' ');
							}
						}
						public int read(char[] cbuf, int off, int len) throws IOException {
							try {
								int r = super.read(cbuf, off, len);
								nDataBuf.append(cbuf, off, r);
								return r;
							}
							catch (IOException ioe) {
								logWarning("ENR: padding " + len + " spaces");
								Arrays.fill(cbuf, off, len, ' ');
								return len;
							}
						}
					});
					//	TODO validate against type ???
					rec.receive(new Notification(nType, nPathInfo, nUserName, nData));
					
					//	send acknowledgment
					output.write(RECEIVE_NOTIFICATION);
					output.newLine();
				}
				catch (Exception e) {
					logError("GoldenGateENR: error forwarding '" + nTypeName + "' notification: " + e.getMessage());
					logError(e);
					logError("Request data read: " + nDataBuf);
					
					//	send error (hedging against null message)
					output.write("" + e.getMessage());
					output.newLine();
				}
			}
		};
		cas.add(ca);
		
		//	console print stats
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return SHOW_STATS_COMMEND;
			}
			public String[] getExplanation() {
				String[] explanation = {
					SHOW_STATS_COMMEND,
					"Show statistics of received notifications.",
				};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length != 0) {
					this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
					return;
				}
				this.reportResult("Received " + notificationTypeCounts.size() + " notifications thus far:");
				for (Iterator ntit = notificationTypeCounts.iterator(); ntit.hasNext();) {
					String ntn = ((String) ntit.next());
					this.reportResult(" - " + ntn + ": " + notificationTypeCounts.getCount(ntn));
				}
			}
		};
		cas.add(ca);
		
		//	finally ...
		return ((ComponentAction[]) cas.toArray(new ComponentAction[cas.size()]));
	}
}
