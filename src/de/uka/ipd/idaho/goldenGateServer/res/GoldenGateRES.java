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
package de.uka.ipd.idaho.goldenGateServer.res;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.Map.Entry;

import de.uka.ipd.idaho.easyIO.EasyIO;
import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.SqlQueryResult;
import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.easyIO.sql.TableDefinition;
import de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerEventService;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants.GoldenGateServerEvent.GoldenGateServerEventListener;
import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection;
import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection.Connection;

/**
 * The GoldenGATE Remote Event Server provides a network interface for pulling
 * document storage events from other installed server components that store
 * documents. It connects to these components by means of bindings.
 * 
 * @author sautter
 */
public class GoldenGateRES extends AbstractGoldenGateServerComponent {
	
	/* TODO
IN THE LONG HAUL, implement AbstractResBasedReplicator extends AbstractGoldenGateServerComponent
- protected field GoldenGateRES res
- protected abstract method getEventSourceClassName() --> String
  - for filtering events
- protected abstract method handleRemoteEvent(ResRemoteEvent rre) --> void

- provide asynchronous console action diff <remoteResAlias>
  - and protected method isDiffSupported() --> boolean (default implementation returning false)
  - and protected method doDiff(RemoteEventList rel) --> void (default implementation throwing UnsupportedOperationException) 
	 */
	
	private static final String GET_EVENTS = "RES_GET_EVENTS";
	
	private static final String GET_DOMAIN_NAME = "RES_GET_DOMAIN_NAME";
	private static final String GET_CONNECTIONS = "RES_GET_CONNECTIONS";
	
	private static final String SOURCE_CLASS_NAME_ATTRIBUTE = "sourceClassName";
	private static final String SOURCE_DOMAIN_NAME_ATTRIBUTE = "sourceDomainName";
	private static final String EVENT_CLASS_NAME_ATTRIBUTE = "eventClassName";
	private static final String EVENT_TIME_ATTRIBUTE = "eventTime";
	private static final String EVENT_ID_ATTRIBUTE = "eventId";
	private static final String EVENT_TYPE_ATTRIBUTE = "eventType";
	
	/**
	 * Descriptor of an event that occurred in a remote GoldenGATE Server
	 * 
	 * @author sautter
	 */
	public static class ResRemoteEvent extends GoldenGateServerEvent {
		
		/**
		 * Listener picking up remote events from the event service
		 * 
		 * @author sautter
		 */
		public static abstract class ResRemoteEventListener extends GoldenGateServerEventListener {
			
			/* (non-Javadoc)
			 * @see de.uka.ipd.idaho.goldenGateServer.events.GoldenGateServerEventListener#notify(de.uka.ipd.idaho.goldenGateServer.events.GoldenGateServerEvent)
			 */
			public void notify(GoldenGateServerEvent gse) {
				if (gse instanceof ResRemoteEvent)
					this.notify((ResRemoteEvent) gse);
			}
			
			/**
			 * Receive notification that an event occurred on a remote
			 * GoldenGATE Server.
			 * @param rre the ResRemoteEvent describing the event that occurred
			 *            on a remote server
			 */
			public abstract void notify(ResRemoteEvent rre);
		}
		
		/** the domain name of the GoldenGATE RES where the wrapped event originally occurred */
		public final String originDomainName;
		
		/** the class name of the actual event wrapped in the remote event */
		public final String eventClassName;
		
		/** the parameter string containing the data of the wrapped event */
		public final String paramString;
		
		/** the name / alias of the GoldenGATE RES the event was fetched from (used only for notification, otherwise null) */
		public final String sourceDomainAlias;
		
		/** the host name of the GoldenGATE Server the event was fetched from (used only for notification, otherwise null) */
		public final String sourceDomainAddress;
		
		/** the port number of the GoldenGATE Server the event was fetched from (used only for notification, otherwise -1) */
		public final int sourceDomainPort;
		
		/**
		 * Constructor
		 * @param type
		 * @param sourceClassName
		 * @param eventTime
		 * @param eventId
		 * @param sourceDomainName the domain name of the GoldenGATE RES where
		 *            the wrapped event originally occurred
		 * @param eventClassName the class name of the actual event wrapped in
		 *            the remote event
		 * @param paramString the parameter string containing the data of the
		 *            wrapped event
		 */
		public ResRemoteEvent(int type, String sourceClassName, long eventTime, String eventId, String sourceDomainName, String eventClassName, String paramString) {
			super(type, sourceClassName, eventTime, eventId, null);
			this.originDomainName = sourceDomainName;
			this.eventClassName = eventClassName;
			this.paramString = paramString;
			this.sourceDomainAlias = null;
			this.sourceDomainAddress = null;
			this.sourceDomainPort = -1;
		}
		
		/**
		 * Constructor for wrapping other events
		 * @param event the GoldenGATE Server event to wrap
		 * @param sourceDomainName the domain name of the GoldenGATE RES where
		 *            the wrapped event originally occurred
		 */
		public ResRemoteEvent(GoldenGateServerEvent event, String sourceDomainName) {
			super(event.type, event.sourceClassName, event.eventTime, event.eventId, null);
			this.originDomainName = sourceDomainName;
			this.eventClassName = ((event instanceof ResRemoteEvent) ? ((ResRemoteEvent) event).eventClassName : event.getClass().getName());
			this.paramString = ((event instanceof ResRemoteEvent) ? ((ResRemoteEvent) event).paramString : event.getParameterString());
			this.sourceDomainAlias = null;
			this.sourceDomainAddress = null;
			this.sourceDomainPort = -1;
		}
		
		/**
		 * Constructor for wrapping remote events on notification, adding host
		 * connection data
		 * @param event the GoldenGATE Server event to wrap
		 * @param sourceDomainAlias the name / alias of the GoldenGATE RES the
		 *            event was fetched from (used only for notification,
		 *            otherwise null)
		 * @param sourceDomainAddress the host name of the GoldenGATE Server the
		 *            event originated from (used only for notification,
		 *            otherwise null)
		 * @param sourceDomainPort the port number of the GoldenGATE Server the
		 *            event originated from (used only for notification,
		 *            otherwise -1)
		 */
		public ResRemoteEvent(ResRemoteEvent event, String sourceDomainAlias, String sourceDomainAddress, int sourceDomainPort) {
			super(event.type, event.sourceClassName, event.eventTime, event.eventId, null);
			this.eventClassName = event.eventClassName;
			this.paramString = event.paramString;
			this.originDomainName = event.originDomainName;
			this.sourceDomainAlias = sourceDomainAlias;
			this.sourceDomainAddress = sourceDomainAddress;
			this.sourceDomainPort = sourceDomainPort;
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants.GoldenGateServerEvent#getParameterString()
		 */
		public String getParameterString() {
			return (super.getParameterString() + " " + this.originDomainName + " " + this.eventClassName + " " + encodeParameter(this.paramString));
		}
		
		/**
		 * Parse a remote event from its string representation returned by the
		 * getParameterString() method.
		 * @param data the string to parse
		 * @return a remote event created from the specified data
		 */
		public static ResRemoteEvent parseEvent(String data) {
			String[] dataItems = data.split("\\s");
			return new ResRemoteEvent(Integer.parseInt(dataItems[0]), dataItems[1], Long.parseLong(dataItems[2]), dataItems[3], dataItems[4], dataItems[5], decodeParameter(dataItems[6]));
		}
	}
	
	/**
	 * A list of remote events, implemented as a plain iterator for efficiency.
	 * 
	 * @author sautter
	 */
	public static abstract class RemoteEventList {
		
		/**
		 * Check if there is another event in the list.
		 * @return true if there is another element, false otherwise
		 */
		public abstract boolean hasNextEvent();
		
		/**
		 * Retrieve the next event from the list. If there is no next event, this
		 * method returns null.
		 * @return the next event in the list
		 */
		public abstract ResRemoteEvent getNextEvent();
		
		/**
		 * Write the events in this list to a given writer. This method consumes the
		 * list, i.e., it iterates through to the last event it contains.
		 * @param out the writer to write to
		 * @throws IOException
		 */
		public void writeData(Writer out) throws IOException {
			BufferedWriter bw = ((out instanceof BufferedWriter) ? ((BufferedWriter) out) : new BufferedWriter(out));
			while (this.hasNextEvent()) {
				bw.write(this.getNextEvent().getParameterString());
				bw.newLine();
			}
			if (bw != out)
				bw.flush();
		}
		
		/**
		 * Wrap a remote event list around a reader, which provides the list's data
		 * in form of a character stream. Do not close the specified reader after
		 * this method returns. The reader is closed by the returned list after the
		 * last event is read.
		 * @param in the reader to read from
		 * @return a remote event list that makes the data from the specified reader
		 *         available as remote events
		 * @throws IOException
		 */
		public static RemoteEventList readEventList(Reader in) throws IOException {
			final BufferedReader finalBr = ((in instanceof BufferedReader) ? ((BufferedReader) in) : new BufferedReader(in));
			return new RemoteEventList() {
				private BufferedReader br = finalBr;
				private String next = null;
				public boolean hasNextEvent() {
					if (this.next != null) return true;
					else if (this.br == null) return false;
					
					try {
						this.next = this.br.readLine();
					} catch (IOException ioe) {}
					
					if (this.next == null) {
						try {
							this.br.close();
						} catch (IOException ioe) {}
						this.br = null;
						return false;
					}
					else return true;
				}
				public ResRemoteEvent getNextEvent() {
					if (!this.hasNextEvent()) return null;
					String next = this.next;
					this.next = null;
					return ResRemoteEvent.parseEvent(next);
				}
			};
		}
	}
	
	/**
	 * An RES event filter allows for components that do updates due to remote
	 * events to filter out update events that emerge while doing the update.
	 * This is meant to prevent updates from circling between mutually
	 * replicating GoldenGATE Servers.
	 * 
	 * @author sautter
	 */
	public static interface ResEventFilter {
		
		/**
		 * Filter an event before it is published in RES. This allows for
		 * components that do updates due to remote events to filter out update
		 * events that emerge while doing the update.
		 * @param gse the event to filter
		 * @return true if the event should be published according to the
		 *         filter, false otherwise
		 */
		public abstract boolean allowPublishEvent(GoldenGateServerEvent gse);
	}
	
	/** Constructor passing 'RES' as the letter code to super constructor
	 */
	public GoldenGateRES() {
		super("RES");
	}
	
	private String domainName;
	
	private DataUpdateThread dataUpdateService = null;
	
	private static final String EVENT_TABLE_NAME = "GgResData";
	
	private static final int EVENT_ID_COLUMN_LENGTH = 128;
	private static final String EVENT_PUBLICATION_TIME_ATTRIBUTE = "PublicationTime";
	private static final int SOURCE_CLASS_NAME_COLUMN_LENGTH = 128;
	private static final int EVENT_CLASS_NAME_COLUMN_LENGTH = 128;
	private static final int SOURCE_DOMAIN_NAME_COLUMN_LENGTH = 32;
	private static final String PARAMETER_STRING_COLUMN_NAME = "ParameterString";
	private static final int PARAMETER_STRING_COLUMN_LENGTH = 1620; // makes a database record 2048 bytes in total
	
	private IoProvider io;
	
	/* (non-Javadoc)
	 * @see de.goldenGateScf.AbstractServerComponent#initComponent()
	 */
	protected void initComponent() {
		System.out.println("GoldenGateRES: starting up ...");
		
		//	load domain name
		this.domainName = this.configuration.getSetting("DomainName");
		if (this.domainName == null)
			throw new RuntimeException("GoldenGATE RES cannot work without a domain name.");
		
		
		//	get and check database connection
		this.io = this.host.getIoProvider();
		if (!this.io.isJdbcAvailable())
			throw new RuntimeException("GoldenGATE RES cannot work without database access.");
		
		
		//	ensure event table
		TableDefinition td = new TableDefinition(EVENT_TABLE_NAME);
		td.addColumn(EVENT_ID_ATTRIBUTE, TableDefinition.VARCHAR_DATATYPE, EVENT_ID_COLUMN_LENGTH);
		td.addColumn(EVENT_PUBLICATION_TIME_ATTRIBUTE, TableDefinition.BIGINT_DATATYPE, 0);
		td.addColumn(EVENT_TIME_ATTRIBUTE, TableDefinition.BIGINT_DATATYPE, 0);
		td.addColumn(EVENT_TYPE_ATTRIBUTE, TableDefinition.INT_DATATYPE, 0);
		td.addColumn(SOURCE_CLASS_NAME_ATTRIBUTE, TableDefinition.VARCHAR_DATATYPE, SOURCE_CLASS_NAME_COLUMN_LENGTH);
		td.addColumn(EVENT_CLASS_NAME_ATTRIBUTE, TableDefinition.VARCHAR_DATATYPE, EVENT_CLASS_NAME_COLUMN_LENGTH);
		td.addColumn(SOURCE_DOMAIN_NAME_ATTRIBUTE, TableDefinition.VARCHAR_DATATYPE, SOURCE_DOMAIN_NAME_COLUMN_LENGTH);
		td.addColumn(PARAMETER_STRING_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, PARAMETER_STRING_COLUMN_LENGTH);
		if (!this.io.ensureTable(td, true))
			throw new RuntimeException("GoldenGATE RES cannot work without database access.");
		
		//	index event table
		this.io.indexColumn(EVENT_TABLE_NAME, EVENT_ID_ATTRIBUTE);
		this.io.indexColumn(EVENT_TABLE_NAME, EVENT_PUBLICATION_TIME_ATTRIBUTE);
		this.io.indexColumn(EVENT_TABLE_NAME, SOURCE_CLASS_NAME_ATTRIBUTE);
		this.io.indexColumn(EVENT_TABLE_NAME, SOURCE_DOMAIN_NAME_ATTRIBUTE);
		
		//	start local data update service
		synchronized (this.dataUpdateQueue) {
			this.dataUpdateService = new DataUpdateThread();
			this.dataUpdateService.start();
			try {
				this.dataUpdateQueue.wait();
			} catch (InterruptedException ie) {}
		}
		System.out.println("  - local data update service started");
		
		//	start event publisher service
		synchronized (this.eventIssuingQueue) {
			this.eventIssuerService = new EventIssuerThread();
			this.eventIssuerService.start();
			try {
				this.eventIssuingQueue.wait();
			} catch (InterruptedException ie) {}
		}
		System.out.println("  - event issuer service started");
		
		
		//	load addresses of federated RES's
		File[] remoteResFiles = this.dataPath.listFiles(new FileFilter() {
			public boolean accept(File file) {
				return (file.isFile() && file.getName().endsWith(".res.cnfg"));
			}
		});
		for (int r = 0; r < remoteResFiles.length; r++) {
			Settings remoteResData = Settings.loadSettings(remoteResFiles[r]);
			String domain = remoteResData.getSetting(RES_DOMAIN_NAME_SETTING);
			if (domain == null)
				domain = remoteResData.getSetting("alias"); // for backward compatibility, to be remove later
			String address = remoteResData.getSetting(RES_ADDRESS_SETTING);
			if ((domain != null) && (address != null)) {
				int port = -1;
				try {
					port = Integer.parseInt(remoteResData.getSetting(RES_PORT_SETTING, "-1"));
				}
				catch (NumberFormatException nfe) {}
				
				RemoteRES res = new RemoteRES(domain, address, port);
				
				try {
					res.updateInterval = Integer.parseInt(remoteResData.getSetting(RES_UPDATE_INTERVAL_SETTING, "3600"));
				} catch (NumberFormatException nfe) {}
				try {
					res.latestUpdate = Long.parseLong(remoteResData.getSetting(RES_LATEST_UPDATE_SETTING, "0"));
				} catch (NumberFormatException nfe) {}
				
				res.active = "true".equals(remoteResData.getSetting(RES_ACTIVE_SETTING, "false"));
				
				this.remoteResFederators.put(res.domainName, res);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#linkInit()
	 */
	public void linkInit() {
		GoldenGateServerEventService.addServerEventListener(new GoldenGateServerEventListener() {
			public void notify(GoldenGateServerEvent gse) {
				publishEvent(gse);
			}
		});
		
		//	start event fetcher (possible only here, as only now other components are ready to process events)
		synchronized (this.remoteResFederators) {
			this.eventFetcherService = new EventFetcherThread();
			this.eventFetcherService.start();
			try {
				this.remoteResFederators.wait();
			} catch (InterruptedException ie) {}
		}
	}
	
	/* (non-Javadoc)
	 * @see de.goldenGateScf.AbstractServerComponent#exitComponent()
	 */
	protected void exitComponent() {
		System.out.println("GoldenGateRES: shutting down ...");
		
		this.dataUpdateService.shutdown();
		System.out.println("  - data update service shut down");
		
		this.eventFetcherService.shutdown();
		System.out.println("  - event fetcher service shut down");
		
		this.eventIssuerService.shutdown();
		System.out.println("  - event issuer service shut down");
		
		System.gc();
		
		this.io.close();
		System.out.println("  - disconnected from database");
	}
	
	/* (non-Javadoc)
	 * @see de.goldenGateScf.ServerComponent#getActions()
	 */
	public ComponentAction[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentAction ca;
		
		//	list events
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_EVENTS;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	get update time limit
				String updatedSince = input.readLine();
				String sourceClassName = input.readLine();
				try {
					RemoteEventList eventList = getEventList(Long.parseLong(updatedSince), ((sourceClassName.length() == 0) ? null : sourceClassName));
					
					//	deliver response only if no errors occurred while reading data
					output.write(GET_EVENTS);
					output.newLine();
					
					//	send data
					eventList.writeData(output);
					
					//	make data go
					output.flush();
				}
				catch (IOException ioe) {
					System.out.println("GoldenGateRES: " + ioe.getClass().getName() + " (" + ioe.getMessage() + ") while listing documents.");
					
					//	report error
					output.write("GoldenGateRES: " + ioe.getClass().getName() + " (" + ioe.getMessage() + ") while listing documents.");
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		//	get domain name
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_DOMAIN_NAME;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				output.write(GET_DOMAIN_NAME);
				output.newLine();
				output.write(domainName);
				output.newLine();
			}
		};
		cal.add(ca);
		
		//	list events
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_CONNECTIONS;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				output.write(GET_CONNECTIONS);
				output.newLine();
				synchronized (remoteResFederators) {
					for (Iterator rit = remoteResFederators.values().iterator(); rit.hasNext();) {
						RemoteRES res = ((RemoteRES) rit.next());
						output.write(res.domainName + " " + res.address + ((res.port == -1) ? "" : (" " + res.port)));
						output.newLine();
					}
				}
			}
		};
		cal.add(ca);
		
		
		//	connect to remote RES
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return ADD_RES_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						ADD_RES_COMMAND + " <resAddress> <resPort>",
						"Add a remote GoldenGATE RESs to this connector to watch.",
						"- <resAddress>: The address of the remote GoldenGATE RES add (if a port is specified, the address is interpreted as a network host name, otherwise it is interpreted as a URL)",
						"- <resPort>: The port of the remote GoldenGATE RES to add (optional parameter)"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if ((arguments.length == 1) || (arguments.length == 2)) {
					String address = arguments[0];
					int port = -1;
					if (arguments.length == 2) try {
						port = Integer.parseInt(arguments[1]);
					}
					catch (NumberFormatException nfe) {
						System.out.println(" Invalid port number '" + arguments[1] + "'.");
						return;
					}
					String domainName;
					try {
						domainName = getDomainName(address, port);
					}
					catch (IOException ioe) {
						System.out.println(" Could not connect to RES at " + arguments[0] + ((port == -1) ? "" : (":" + port)) + ":");
						System.out.println(" " + ioe.getMessage());
						return;
					}
					
					RemoteRES res = new RemoteRES(domainName, address, port);
					try {
						storeRes(res);
						remoteResFederators.put(res.domainName, res);
						System.out.println(" Successfully connected to remote RES " + domainName + ".");
					}
					catch (IOException ioe) {
						System.out.println(" Could not connect to remote RES '" + res.domainName + "':");
						System.out.println(" " + ioe.getMessage());
						ioe.printStackTrace(System.out);
					}
				}
				else System.out.println(" Invalid arguments for '" + this.getActionCommand() + "', specify address and optionally a port only.");
			}
		};
		cal.add(ca);
		
		//	import remote RES connections
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return IMPORT_RES_CONNECTIONS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						IMPORT_RES_CONNECTIONS_COMMAND + " <domain>",
						"Import connections from a remote GoldenGATE RES:",
						"- <domain>: The name of the remote GoldenGATE RES to import connections from"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1) {
					RemoteRES res = ((RemoteRES) remoteResFederators.get(arguments[0]));
					if (res == null)
						System.out.println(" No remote RES found for name " + arguments[0]);
					else {
						try {
							RemoteRES[] ress = res.getConnections();
							for (int r = 0; r < ress.length; r++) {
								if (remoteResFederators.containsKey(ress[r].domainName))
									continue;
								remoteResFederators.put(ress[r].domainName, ress[r]);
								storeRes(ress[r]);
							}
						}
						catch (IOException ioe) {
							System.out.println(" Error on importing connections from remote RES:");
							System.out.println(" " + ioe.getMessage());
							ioe.printStackTrace(System.out);
						}
					}
				}
				else System.out.println(" Invalid arguments for '" + this.getActionCommand() + "', specify the domain name of the RES to import connections from as the only argument.");
			}
		};
		cal.add(ca);
		
		//	set update interval for remote RES
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return SET_RES_UPDATE_INTERVAL_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						SET_RES_UPDATE_INTERVAL_COMMAND + " <domain> <updateInterval>",
						"Set the update interval for a remote GoldenGATE RESs this connector watches.",
						"- <domain>: The domain name of the remote GoldenGATE RES to set the update interval for",
						"- <updateInterval>: The number of seconds to wait after one request for updates until starting the next request (at least 600 seconds, for 10 minutes)",
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 2) {
					String alias = arguments[0];
					RemoteRES res = ((RemoteRES) remoteResFederators.get(alias));
					if (res == null)
						System.out.println(" No remote RES found for alias " + alias + ".");
					else {
						int updateInterval = -1;
						try {
							updateInterval = Integer.parseInt(arguments[1]);
						}
						catch (NumberFormatException nfe) {}
						if (updateInterval < 600)
							System.out.println(" The update interval has to be at least 600 seconds, for 10 minutes.");
						else {
							res.updateInterval = updateInterval;
							try {
								storeRes(res);
								System.out.println(" Update interval for remote RES " + alias + " changed successfully.");
							}
							catch (IOException e) {
								System.out.println(" Could not change update interval for remote RES '" + res.domainName + "': " + e.getClass().getName() + " (" + e.getMessage() + ")");
								e.printStackTrace(System.out);
							}
						}
					}
				}
				else System.out.println(" Invalid arguments for '" + this.getActionCommand() + "', specify alias and update interval only.");
			}
		};
		cal.add(ca);
		
		//	list connected remote RES's
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return LIST_RES_FEDS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						LIST_RES_FEDS_COMMAND,
						"List the remote GoldenGATE RESs this connector watches."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0) {
					System.out.println(" There " + ((remoteResFederators.size() == 1) ? "is" : "are") + " " + ((remoteResFederators.size() == 0) ? "no" : ("" + remoteResFederators.size())) + " remote RES" + ((remoteResFederators.size() == 1) ? "" : "'s") + " connected" + ((remoteResFederators.size() == 0) ? "." : ":"));
					synchronized (remoteResFederators) {
						for (Iterator rit = remoteResFederators.values().iterator(); rit.hasNext();) {
							RemoteRES res = ((RemoteRES) rit.next());
							System.out.println(" - " + res.domainName + " (" + (res.active ? "active" : "inactive") + ") @ " + res.address + ((res.port == -1) ? "" : (":" + res.port)));
						}
					}
				}
				else System.out.println(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	activate watching remote RES
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return ACTIVATE_RES_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						ACTIVATE_RES_COMMAND + " <domain>",
						"Activate fetching events from a remote GoldenGATE RES:",
						"- <domain>: The name of the remote GoldenGATE RES to activate"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1) {
					RemoteRES res = ((RemoteRES) remoteResFederators.get(arguments[0]));
					if (res == null)
						System.out.println(" No remote RES found for name " + arguments[0]);
					else {
						res.active = true;
						try {
							storeRes(res);
						}
						catch (IOException ioe) {
							System.out.println(" Error on activating remote RES:");
							System.out.println(" " + ioe.getMessage());
							ioe.printStackTrace(System.out);
						}
					}
				}
				else System.out.println(" Invalid arguments for '" + this.getActionCommand() + "', specify the domain name of the RES to activate as the only argument.");
			}
		};
		cal.add(ca);
		
		//	deactivate watching remote RES
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return DEACTIVATE_RES_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						DEACTIVATE_RES_COMMAND + " <domain>",
						"Deactivate fetching events from a remote GoldenGATE RES:",
						"- <domain>: The name of the remote GoldenGATE RES to deactivate"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1) {
					RemoteRES res = ((RemoteRES) remoteResFederators.get(arguments[0]));
					if (res == null)
						System.out.println(" No remote RES found for name " + arguments[0]);
					else {
						res.active = false;
						try {
							storeRes(res);
						}
						catch (IOException ioe) {
							System.out.println(" Error on deactivating remote RES:");
							System.out.println(" " + ioe.getMessage());
							ioe.printStackTrace(System.out);
						}
					}
				}
				else System.out.println(" Invalid arguments for '" + this.getActionCommand() + "', specify the domain name of the RES to activate as the only argument.");
			}
		};
		cal.add(ca);
		
		//	force instant update from remote RES
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return UPDATE_FROM_RES_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						UPDATE_FROM_RES_COMMAND + " <domain>",
						"Run an update for a specific remote GoldenGATE RES immediately:",
						"- <domain>: The name of the remote GoldenGATE RES to fetch the latest updates from"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1) {
					RemoteRES res = ((RemoteRES) remoteResFederators.get(arguments[0]));
					if (res == null)
						System.out.println(" No remote RES found for name " + arguments[0]);
					else {
						try {
							fetchRemoteEvents(res);
						}
						catch (IOException ioe) {
							System.out.println(" Error on getting remote events - " + ioe.getClass().getName() + " (" + ioe.getMessage() + ")");
							ioe.printStackTrace(System.out);
						}
					}
				}
				else System.out.println(" Invalid arguments for '" + this.getActionCommand() + "', specify the alias of the RES to update from as the only argument.");
			}
		};
		cal.add(ca);
		
		//	diff events with remote RES
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return DIFF_FROM_RES_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						DIFF_FROM_RES_COMMAND + " <domain>",
						"Run a full diff with a specific remote GoldenGATE RES, i.e., compare the event lists and fetch missing events:",
						"- <domain>: The name of the remote GoldenGATE RES to compare the event list with"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1) {
					RemoteRES res = ((RemoteRES) remoteResFederators.get(arguments[0]));
					if (res == null)
						System.out.println(" No remote RES found for alias " + arguments[0]);
					else {
						try {
							diffEvents(res);
						}
						catch (IOException ioe) {
							System.out.println("Error on getting remote event IDs - " + ioe.getClass().getName() + " (" + ioe.getMessage() + ")");
							ioe.printStackTrace(System.out);
						}
					}
				}
				else System.out.println(" Invalid arguments for '" + this.getActionCommand() + "', specify the alias of the RES to diff with as the only argument.");
			}
		};
		cal.add(ca);
		
		return ((ComponentAction[]) cal.toArray(new ComponentAction[cal.size()]));
	}
	
	
	//	LOCAL PART (FOR PUBLISHING EVENTS)
	
	/**
	 * Add an event filter to RES to take control over which events get
	 * published and which not.
	 * @param ref the event filter to add
	 */
	public void addEventFilter(ResEventFilter ref) {
		if (ref != null)
			this.eventFilters.add(ref);
	}
	private ArrayList eventFilters = new ArrayList(2);
	
	/**
	 * Publish an event through RES so it is accessible for remote GoldenGATE
	 * Servers. Events that are published locally through GoldenGATE Server
	 * event queue are published automatically.
	 * @param gse the event to publish
	 */
	public void publishEvent(final GoldenGateServerEvent gse) {
		
		//	check if original event was issued by local RES and circled back from other server
		if ((gse instanceof ResRemoteEvent) && this.domainName.equals(((ResRemoteEvent) gse).originDomainName))
			return;
		
		//	check who is publishing (drop events that are due to an event from a remote RES being issued locally)
		if ((Thread.currentThread().getId() == eventIssuerService.getId()) && !(gse instanceof ResRemoteEvent))
			return;
		
		//	check filters
		for (int f = 0; f < eventFilters.size(); f++) {
			if (!((ResEventFilter) this.eventFilters.get(f)).allowPublishEvent(gse))
				return;
		}
		
		//	enqueue data update
		this.enqueueDataUpdate(new Runnable() {
			public void run() {
				storeEvent((gse instanceof ResRemoteEvent) ? ((ResRemoteEvent) gse) : new ResRemoteEvent(gse, domainName));
			}
		});
	}
	
	private void storeEvent(final ResRemoteEvent rre) {
		
		String existQuery = "SELECT " + EVENT_ID_ATTRIBUTE + 
		" FROM " + EVENT_TABLE_NAME +
		" WHERE " + EVENT_ID_ATTRIBUTE + " LIKE '" + EasyIO.sqlEscape(rre.eventId) + "'" +
			" AND " + EVENT_CLASS_NAME_ATTRIBUTE + " LIKE '" + EasyIO.sqlEscape(rre.eventClassName) + "'" +
		";";
		
		SqlQueryResult sqr = null;
		try {
			sqr = this.io.executeSelectQuery(existQuery);
			
			//	we already know this event
			if (sqr.next()) {}
			
			//	new event
			else {
				
				//	store data in collection main table
				String insertQuery = "INSERT INTO " + EVENT_TABLE_NAME + 
						" (" + EVENT_ID_ATTRIBUTE + ", " + EVENT_PUBLICATION_TIME_ATTRIBUTE + ", " + EVENT_TIME_ATTRIBUTE + ", " + EVENT_TYPE_ATTRIBUTE + ", " + SOURCE_CLASS_NAME_ATTRIBUTE + ", " + EVENT_CLASS_NAME_ATTRIBUTE + ", " + SOURCE_DOMAIN_NAME_ATTRIBUTE + ", " + PARAMETER_STRING_COLUMN_NAME + ")" +
						" VALUES" +
						" ('" + EasyIO.sqlEscape(rre.eventId) + "', " + System.currentTimeMillis() + ", " + rre.eventTime + ", " + rre.type + ", '" + EasyIO.sqlEscape(rre.sourceClassName) + "', '" + EasyIO.sqlEscape(rre.eventClassName) + "', '" + EasyIO.sqlEscape(rre.originDomainName) + "', '" + EasyIO.sqlEscape(rre.paramString) + "'" + ")" +
						";";
				try {
					this.io.executeUpdateQuery(insertQuery);
				}
				catch (SQLException sqle) {
					System.out.println("GoldenGateRES: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while storing remote event.");
					System.out.println("  query was " + insertQuery);
				}
			}
		}
		catch (SQLException sqle) {
			System.out.println("GoldenGateRES: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while checking if remote event already known.");
			System.out.println("  query was " + existQuery);
		}
		finally {
			if (sqr != null)
				sqr.close();
		}
	}
	
	/**
	 * Background service thread for asynchronous database updates
	 * 
	 * @author sautter
	 */
	private class DataUpdateThread extends Thread {
		private boolean keepRunning = true;
		public void run() {
			
			//	wake up creator thread
			synchronized (dataUpdateQueue) {
				dataUpdateQueue.notify();
			}
			
			//	run until shutdown() is called
			while (this.keepRunning) {
				
				//	check if update waiting
				Runnable dataUpdate;
				synchronized (dataUpdateQueue) {
					
					//	wait if no indexing actions pending
					if (dataUpdateQueue.isEmpty()) {
						try {
							dataUpdateQueue.wait();
						} catch (InterruptedException ie) {}
					}
					
					//	woken up despite empty queue ==> shutdown
					if (dataUpdateQueue.isEmpty()) return;
					
					//	get update
					else dataUpdate = ((Runnable) dataUpdateQueue.removeFirst());
				}
				
				//	execute index action
				this.doDataUpdate(dataUpdate);
				
				//	give a little time to the others
				if (this.keepRunning) try {
					Thread.sleep(100);
				} catch (InterruptedException ie) {}
			}
			
			//	work off remaining updates
			while (dataUpdateQueue.size() != 0)
				this.doDataUpdate(((Runnable) dataUpdateQueue.removeFirst()));
		}
		
		private void doDataUpdate(Runnable dataUpdate) {
			try {
				dataUpdate.run();
			}
			catch (Throwable t) {
				System.out.println("Error on data update - " + t.getClass().getName() + " (" + t.getMessage() + ")");
				t.printStackTrace(System.out);
			}
		}
		
		void shutdown() {
			synchronized (dataUpdateQueue) {
				this.keepRunning = false;
				dataUpdateQueue.notify();
			}
			try {
				this.join();
			} catch (InterruptedException ie) {}
		}
	}
	
	private LinkedList dataUpdateQueue = new LinkedList();
	
	private void enqueueDataUpdate(Runnable dataUpdate) {
		synchronized (this.dataUpdateQueue) {
			this.dataUpdateQueue.addLast(dataUpdate);
			this.dataUpdateQueue.notify();
		}
	}
	
	/**
	 * Retrieve a list of remote events listed in this GoldenGATE RES. The
	 * issuedSince parameter is compared against the time when events were
	 * published locally. This is in order to prevent events that come in over
	 * multiple hops from being overshadowed (and thus not being forwarded) by
	 * events that occurred later but came in over less hops.
	 * @param publishedSince lower bound for event issuing time
	 * @param sourceClassName the source class orf the events to list
	 *            (specifying null lists events from all sources)
	 * @return a list of remote events listed in this GoldenGATE RES.
	 * @throws IOException
	 */
	public RemoteEventList getEventList(long publishedSince, String sourceClassName) throws IOException {
		if (publishedSince < 0) publishedSince = (System.currentTimeMillis() + publishedSince);
		
		StringBuffer query = new StringBuffer("SELECT " + EVENT_ID_ATTRIBUTE + ", " + EVENT_TIME_ATTRIBUTE + ", " + EVENT_TYPE_ATTRIBUTE + ", " + SOURCE_CLASS_NAME_ATTRIBUTE + ", " + EVENT_CLASS_NAME_ATTRIBUTE + ", " + SOURCE_DOMAIN_NAME_ATTRIBUTE + ", " + PARAMETER_STRING_COLUMN_NAME);
		query.append(" FROM " + EVENT_TABLE_NAME);
		query.append(" WHERE 1=1");
		if (publishedSince > 0)
			query.append(" AND " + EVENT_PUBLICATION_TIME_ATTRIBUTE + " > " + publishedSince);
		if (sourceClassName != null)
			query.append(" AND " + SOURCE_CLASS_NAME_ATTRIBUTE + " LIKE '" + sourceClassName + "'");
		query.append(" ORDER BY " + EVENT_TIME_ATTRIBUTE);
		query.append(";");
		
		SqlQueryResult sqr = null;
		try {
			sqr = this.io.executeSelectQuery(query.toString());
			final SqlQueryResult finalSqr = sqr;
			return new RemoteEventList() {
				SqlQueryResult sqr = finalSqr;
				ResRemoteEvent next = null;
				public boolean hasNextEvent() {
					if (this.next != null) return true;
					else if (this.sqr == null) return false;
					else if (this.sqr.next()) {
						String eventId = this.sqr.getString(0);
						long eventTime = Long.parseLong(this.sqr.getString(1));
						int eventType = Integer.parseInt(this.sqr.getString(2));
						String sourceClassName = this.sqr.getString(3);
						String eventClassName = this.sqr.getString(4);
						String sourceDomainName = this.sqr.getString(5);
						String paramString = this.sqr.getString(6);
						this.next = new ResRemoteEvent(eventType, sourceClassName, eventTime, eventId, sourceDomainName, eventClassName, paramString);
						return true;
					}
					else {
						this.sqr.close();
						this.sqr = null;
						return false;
					}
				}
				public ResRemoteEvent getNextEvent() {
					if (!this.hasNextEvent()) return null;
					ResRemoteEvent next = this.next;
					this.next = null;
					return next;
				}
			};
		}
		catch (SQLException sqle) {
			System.out.println("GoldenGateRES: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while listing events.");
			System.out.println("  query was " + query);
			if (sqr != null)
				sqr.close();
			throw new IOException(sqle.getMessage());
		}
	}
	
	
	//	REMOTE PART (FOR FETCHING EVENTS)
	
	private static final String RES_DOMAIN_NAME_SETTING = "domainName";
	private static final String RES_ADDRESS_SETTING = "adress";
	private static final String RES_PORT_SETTING = "port";
	private static final String RES_UPDATE_INTERVAL_SETTING = "updateInterval";
	private static final String RES_LATEST_UPDATE_SETTING = "lastUpdate";
	private static final String RES_ACTIVE_SETTING = "active";
	
	private static final String ADD_RES_COMMAND = "add";
	private static final String IMPORT_RES_CONNECTIONS_COMMAND = "importCons";
	private static final String SET_RES_UPDATE_INTERVAL_COMMAND = "setInterval";
	private static final String UPDATE_FROM_RES_COMMAND = "update";
	private static final String DIFF_FROM_RES_COMMAND = "diff";
//	private static final String DROP_RES_COMMAND = "drop";
	private static final String ACTIVATE_RES_COMMAND = "activate";
	private static final String DEACTIVATE_RES_COMMAND = "deactivate";
	private static final String LIST_RES_FEDS_COMMAND = "list";
	
	private void storeRes(RemoteRES res) throws IOException {
		Settings resData = new Settings();
		resData.setSetting(RES_DOMAIN_NAME_SETTING, res.domainName);
		resData.setSetting(RES_ADDRESS_SETTING, res.address);
		resData.setSetting(RES_PORT_SETTING, ("" + res.port));
		resData.setSetting(RES_UPDATE_INTERVAL_SETTING, ("" + res.updateInterval));
		resData.setSetting(RES_LATEST_UPDATE_SETTING, ("" + res.latestUpdate));
		resData.setSetting(RES_ACTIVE_SETTING, (res.active ? "true" : "false"));
		resData.storeAsText(this.getResFile(res));
	}
	
//	private void deleteRes(RemoteRES res) {
//		this.getResFile(res).delete();
//	}
//	
	private File getResFile(RemoteRES res) {
		return new File(this.dataPath, (res.domainName.replaceAll("[^a-zA-Z]", "_") + ".res.cnfg"));
	}
	
	private String getDomainName(String address, int port) throws IOException {
		ServerConnection sc = ((port == -1) ? ServerConnection.getServerConnection(address) : ServerConnection.getServerConnection(address, port));
		Connection con = sc.getConnection();
		try {
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_DOMAIN_NAME);
			bw.newLine();
			bw.flush();
			
			BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_DOMAIN_NAME.equals(error))
				return br.readLine();
			else throw new IOException(error);
		}
		finally {
			con.close();
		}
	}
	
	/**
	 * Representation of a single connection to a remote RES
	 * 
	 * @author sautter
	 */
	private static class RemoteRES {
		final String domainName;
		final String address;
		final int port;
		
		private ServerConnection serverConnection;
		
		long lastAttemptedLookup = 0;
		
		long latestUpdate = 0;
		long updateInterval = 3600; // 3600 seconds = 1 hour by default
		
		boolean active = false;
		
		RemoteRES(String domainName, String address, int port) {
			this.domainName = domainName;
			this.address = address;
			this.port = port;
			this.serverConnection = ((this.port == -1) ? ServerConnection.getServerConnection(this.address) : ServerConnection.getServerConnection(this.address, this.port));
		}
		
		RemoteEventList getRemoteEvents(long since, String sourceClassName) throws IOException {
			final Connection con = this.serverConnection.getConnection();
			BufferedWriter bw = con.getWriter();
			
			bw.write(GET_EVENTS);
			bw.newLine();
			bw.write("" + Math.max(0, since));
			bw.newLine();
			bw.write((sourceClassName == null) ? "" : sourceClassName);
			bw.newLine();
			bw.flush();
			
			final BufferedReader br = con.getReader();
			String error = br.readLine();
			if (GET_EVENTS.equals(error))
				return RemoteEventList.readEventList(new Reader() {
					public void close() throws IOException {
						br.close();
						con.close();
					}
					public int read(char[] cbuf, int off, int len) throws IOException {
						return br.read(cbuf, off, len);
					}
				});
			
			else {
				con.close();
				throw new IOException(error);
			}
		}
		
		RemoteRES[] getConnections() throws IOException {
			Connection con = this.serverConnection.getConnection();
			try {
				BufferedWriter bw = con.getWriter();
				
				bw.write(GET_CONNECTIONS);
				bw.newLine();
				bw.flush();
				
				BufferedReader br = con.getReader();
				String error = br.readLine();
				if (GET_CONNECTIONS.equals(error)) {
					String connectionData;
					ArrayList connections = new ArrayList();
					while ((connectionData = br.readLine()) != null) {
						String[] connectionDetails = connectionData.split("\\s++");
						if (connectionDetails.length < 2)
							continue;
						connections.add(new RemoteRES(connectionDetails[0], connectionDetails[1], (connectionDetails.length == 2) ? -1 : Integer.parseInt(connectionDetails[2])));
					}
					return ((RemoteRES[]) connections.toArray(new RemoteRES[connections.size()]));
				}
				else throw new IOException(error);
			}
			finally {
				con.close();
			}
		}
	}
	
	private TreeMap remoteResFederators = new TreeMap();
	
	private EventIssuerThread eventIssuerService = null;
	
	/**
	 * Background service thread that publishes events from remote RES's in the
	 * local event queue
	 * 
	 * @author sautter
	 */
	private class EventIssuerThread extends Thread {
		private boolean keepRunning = true;
		public void run() {
			
			//	wake up creator thread
			synchronized (eventIssuingQueue) {
				eventIssuingQueue.notify();
			}
			
			//	run until shutdown() is called
			while (this.keepRunning) {
				
				//	check if update waiting
				Runnable update;
				synchronized (eventIssuingQueue) {
					
					//	wait if no indexing actions pending
					if (eventIssuingQueue.isEmpty()) {
						try {
							eventIssuingQueue.wait();
						} catch (InterruptedException ie) {}
					}
					
					//	woken up despite empty queue ==> shutdown
					if (eventIssuingQueue.isEmpty()) return;
					
					//	get update
					else update = ((Runnable) eventIssuingQueue.removeFirst());
				}
				
				//	execute update action
				this.doUpdate(update);
				
				//	give a little time to the others
				if (this.keepRunning) try {
					Thread.sleep(100);
				} catch (InterruptedException ie) {}
			}
			
			//	work off remaining update actions
			while (eventIssuingQueue.size() != 0)
				this.doUpdate(((Runnable) eventIssuingQueue.removeFirst()));
		}
		
		private void doUpdate(Runnable dataUpdate) {
			try {
				dataUpdate.run();
			}
			catch (Throwable t) {
				System.out.println("Error on data update - " + t.getClass().getName() + " (" + t.getMessage() + ")");
				t.printStackTrace(System.out);
			}
		}
		
		void shutdown() {
			synchronized (eventIssuingQueue) {
				this.keepRunning = false;
				eventIssuingQueue.notify();
			}
			try {
				this.join();
			} catch (InterruptedException ie) {}
		}
	}
	
	private LinkedList eventIssuingQueue = new LinkedList();
	private void enqueueEventIssuing(Runnable eventIssuing) {
		synchronized (this.eventIssuingQueue) {
			this.eventIssuingQueue.addLast(eventIssuing);
			this.eventIssuingQueue.notify();
		}
	}
	
	private EventFetcherThread eventFetcherService = null;
	
	/**
	 * Background service thread for fetching events from remote RES's
	 * 
	 * @author sautter
	 */
	private class EventFetcherThread extends Thread {
		private boolean keepRunning = true;
		public void run() {
			
			//	wake up creator thread
			synchronized (remoteResFederators) {
				remoteResFederators.notify();
			}
			
			//	run until shutdown() is called
			while (this.keepRunning) {
				
				//	check if update waiting
				RemoteRES updateRes = null;
				long currentTime = System.currentTimeMillis();
				
				//	check if some RES to poll
				synchronized (remoteResFederators) {
					
					//	first, check when last tried to poll
					for (Iterator rit = remoteResFederators.values().iterator(); (updateRes == null) && rit.hasNext();) {
						RemoteRES res = ((RemoteRES) rit.next());
						if (!res.active)
							continue;
						if ((res.lastAttemptedLookup + (res.updateInterval * 1000)) < currentTime)
							updateRes = res;
					}
				}
				
				//	if so, fetch and enqueue updates
				if (updateRes != null) try {
					updateRes.lastAttemptedLookup = currentTime;
					fetchRemoteEvents(updateRes);
				}
				catch (IOException ioe) {
					System.out.println("Error on getting updates from " + updateRes.domainName + " - " + ioe.getClass().getName() + " (" + ioe.getMessage() + ")");
					ioe.printStackTrace(System.out);
				}
				
				//	give a little time to the others
				if (this.keepRunning) try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) {}
			}
		}
		
		void shutdown() {
			synchronized (remoteResFederators) {
				this.keepRunning = false;
				remoteResFederators.notify();
			}
			try {
				this.join();
			} catch (InterruptedException ie) {}
		}
	}
	
	private void fetchRemoteEvents(RemoteRES res) throws IOException {
		System.out.println("GoldenGateRES: getting events from " + res.domainName + " (" + res.address + ":" + res.port + ")");
		RemoteEventList rel = res.getRemoteEvents(res.latestUpdate, null);
		while (rel.hasNextEvent()) {
			ResRemoteEvent re = rel.getNextEvent();
			re = new ResRemoteEvent(re, res.domainName, res.address, res.port);
			res.latestUpdate = Math.max(res.latestUpdate, re.eventTime);
			this.issueEvent(re, res, rel.hasNextEvent());
		}
	}
	
	private HashMap recentEvents = new LinkedHashMap(1024, 0.9f, true) {
		protected boolean removeEldestEntry(Entry arg0) {
			return (this.size() > 1024);
		}
	};
	
	private void issueEvent(final ResRemoteEvent re, final RemoteRES res, final boolean moreToCome) {
		
		//	catch events that have circled back from the local domain
		if (this.domainName.equals(re.originDomainName))
			return;
		
		//	enqueue issuing event locally
		this.enqueueEventIssuing(new Runnable() {
			public void run() {
				try {
					
					//	check cache (event might have already come in from other remote RES)
					if (recentEvents.containsKey(re.eventId))
						return;
					
					//	catch events already stored
					String lookupQuery = "SELECT " + EVENT_ID_ATTRIBUTE + " FROM " + EVENT_TABLE_NAME + 
							" WHERE " + EVENT_ID_ATTRIBUTE + " LIKE '" + EasyIO.sqlEscape(re.eventId) + "'" +
								" AND " + SOURCE_DOMAIN_NAME_ATTRIBUTE + " LIKE '" + EasyIO.sqlEscape(re.originDomainName) + "'" + 
							";";
					SqlQueryResult sqr = null;
					try {
						sqr = io.executeSelectQuery(lookupQuery);
						if (sqr.next()) {
							recentEvents.put(re.eventId, "");
							return;
						}
					}
					catch (SQLException sqle) {
						System.out.println("GoldenGateRES: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while checking event history.");
						System.out.println("  query was " + lookupQuery);
					}
					finally {
						if (sqr != null)
							sqr.close();
					}
					
					//	issue event (will automatically loop back to own database)
					GoldenGateServerEventService.notify(re);
					
					//	store update timestamp on last event in series
					if (!moreToCome)
						storeRes(res);
				}
				catch (IOException ioe) {
					System.out.println("Error on update - " + ioe.getClass().getName() + " (" + ioe.getMessage() + ")");
					ioe.printStackTrace(System.out);
				}
			}
		});
	}
	
	private void diffEvents(RemoteRES res) throws IOException {
		
		//	collect local event IDs
		String lookupQuery = "SELECT " + EVENT_ID_ATTRIBUTE + " FROM " + EVENT_TABLE_NAME + ";";
		SqlQueryResult sqr = null;
		HashSet localEventIDs = new HashSet();
		try {
			sqr = io.executeSelectQuery(lookupQuery);
			while (sqr.next())
				localEventIDs.add(sqr.getString(0));
		}
		catch (SQLException sqle) {
			System.out.println("GoldenGateRES: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while loading event history.");
			System.out.println("  query was " + lookupQuery);
			return;
		}
		finally {
			if (sqr != null)
				sqr.close();
		}
		
		//	get remote events and do diff
		System.out.println("GoldenGateRES: getting events from " + res.domainName + " (" + res.address + ":" + res.port + ")");
		RemoteEventList rel = res.getRemoteEvents(0, null);
		while (rel.hasNextEvent()) {
			ResRemoteEvent re = rel.getNextEvent();
			re = new ResRemoteEvent(re, res.domainName, res.address, res.port);
			
			//	we've been missing this event, or it's a recent one, don't miss it
			if (localEventIDs.add(re.eventId) || (re.eventTime > res.latestUpdate))
				this.issueEvent(re, res, rel.hasNextEvent());
			
			//	remember we had this one
			res.latestUpdate = Math.max(res.latestUpdate, re.eventTime);
		}
	}
	
	/**
	 * Retrieve the names of the RES domains this RES is fetching events from.
	 * @return an array holding the remote domain names
	 */
	public String[] getRemoteDomains() {
		return ((String[]) this.remoteResFederators.keySet().toArray(new String[this.remoteResFederators.size()]));
	}
	
	/**
	 * Retrieve the address of a domain this RES is fetching events from. If
	 * this RES is not connected to the specified domain, this method returns
	 * null.
	 * @param remoteDomainAlias the name of the remote domain to retrieve the
	 *            address for
	 * @return the address of the specified remote domain
	 */
	public String getRemoteDomainAddress(String remoteDomainAlias) {
		RemoteRES res = ((RemoteRES) remoteResFederators.get(remoteDomainAlias));
		return ((res == null) ? null : res.address);
	}
	
	/**
	 * Retrieve the port of a domain this RES is fetching events from. If this
	 * RES is not connected to the specified domain, this method returns -1.
	 * @param remoteDomainAlias the name of the remote domain to retrieve the
	 *            port for
	 * @return the port of the specified remote domain
	 */
	public int getRemoteDomainPort(String remoteDomainAlias) {
		RemoteRES res = ((RemoteRES) remoteResFederators.get(remoteDomainAlias));
		return ((res == null) ? -1 : res.port);
	}
	
	/**
	 * Retrieve a list containing events that occurred at a domain this RES is
	 * fetching events from. The events in the list are sorted by their time,
	 * oldest first. If this RES is not connected to the specified domain, this
	 * method returns null.
	 * @param remoteDomainAlias the name of the remote domain to retrieve the
	 *            events from
	 * @param since the time after which (exclusive) an event has to have
	 *            occurred to be returned
	 * @param sourceClassName the class name of the component whose events are
	 *            of interest
	 * @return a list containing the events from the remote domain that meet
	 *         the specified conditions.
	 * @throws IOException
	 */
	public RemoteEventList getRemoteEventList(String remoteDomainAlias, long since, String sourceClassName) throws IOException {
		RemoteRES res = ((RemoteRES) remoteResFederators.get(remoteDomainAlias));
		return ((res == null) ? null : res.getRemoteEvents(since, sourceClassName));
	}
}