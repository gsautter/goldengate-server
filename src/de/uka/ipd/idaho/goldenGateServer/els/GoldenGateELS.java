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
package de.uka.ipd.idaho.goldenGateServer.els;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import de.uka.ipd.idaho.easyIO.EasyIO;
import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.SqlQueryResult;
import de.uka.ipd.idaho.easyIO.sql.TableDefinition;
import de.uka.ipd.idaho.gamta.util.GamtaClassLoader;
import de.uka.ipd.idaho.gamta.util.GamtaClassLoader.ComponentInitializer;
import de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerActivityLogger;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants.GoldenGateServerEvent.EventLogger;
import de.uka.ipd.idaho.goldenGateServer.util.AsynchronousDataActionHandler;

/**
 * GoldenGATE External Linking Service collects external links that need to be
 * added to a data object (e.g. a document) or a detail therein (e.g. an
 * annotation) and writes all these links back to the data object in a single
 * update. Adding the links in the proper locations happens with the help of
 * handlers that take care of links of specific types.<br/>
 * The centralized update mechanism of this class is primarily intended for
 * adding external links that result from asynchronous data object exports to
 * other platforms.
 * 
 * @author sautter
 */
public class GoldenGateELS extends AbstractGoldenGateServerComponent implements GoldenGateElsConstants {
	private static final String LINK_TABLE_NAME = "GgElsLinks";
	private static final String DATA_ID_COLUMN_NAME = "dataId";
	private static final String DATA_ID_HASH_COLUMN_NAME = "dataIdHash";
	private static final String LINK_DETAIL_ID_COLUMN_NAME = "linkDetailId";
	private static final String LINK_TYPE_COLUMN_NAME = "linkType";
	private static final String LINK_STRING_COLUMN_NAME = "linkString";
	private static final String LINK_MODIFIED_COLUMN_NAME = "linkModified";
	private static final String LINK_STATUS_COLUMN_NAME = "linkStatus";
	
	/**
	 * Handler writing external links back to the data object they belong to.
	 * The <code>handleLink()</code> method must indicate whether or not a
	 * given link was added to the underlying data object. Soon as one handler
	 * returns true on that method, no further handlers will be consulted, and
	 * thus implementations of this class should be careful.
	 * 
	 * @author sautter
	 */
	public static abstract class LinkWriter implements GoldenGateElsConstants, EventLogger {
		protected GoldenGateELS host;
		
		void setHost(GoldenGateELS host) {
			this.host = host;
			this.init();
		}
		
		/**
		 * Initialize the link writer. This default implementation does
		 * nothing, sub classes are welcome to overwrite it as needed.
		 */
		protected void init() {}
		
		/**
		 * Exit the link writer. This default implementation does nothing, sub
		 * classes are welcome to overwrite it as needed.
		 */
		protected void exit() {}
		
		/**
		 * Indicate the priority of the link writer, on a 0-10 scale. Writers
		 * with higher priority will be consulted first. This mechanism helps
		 * writers working on the root representation of a data object to be
		 * called upon first, i.e., before ones working on potentially derived
		 * data objects.
		 * @return the priority
		 */
		public abstract int getPriority();
		
		/**
		 * Indicate whether or not the writer is able to write links to a data
		 * object with a given ID, i.e., whether or not the writer has access
		 * to the data object with the argument ID.
		 * @param dataId the ID of the data object
		 * @return true if the writer can handle links for the data object with
		 *        the argument ID
		 */
		public abstract boolean canHandleLinks(String dataId);
		
		/**
		 * Indicate whether or not the writer is able to write links to a data
		 * object with a given ID at the moment, i.e., whether or not the
		 * writer can access to the data object with the argument ID. If this
		 * method returns false after <code>canHandleLinks()</code> returned
		 * true, link handling will be postponed by some waiting period, but no
		 * other writers will be consulted. This is helpful to indicate that a
		 * data object is temporarily locked by another process or user, for
		 * instance.
		 * @param dataId the ID of the data object
		 * @return true if the writer can momentarily write links to the data
		 *        object with the argument ID
		 */
		public abstract boolean canWriteLinks(String dataId);
		
		/**
		 * Indicate whether or not the link writer provides notifications when
		 * a data object is unlocked and becomes writable again. If this method
		 * returns true after <code>canWriteLinks()</code> returned false, the
		 * link writer <b>must</b> ensure to call <code>dataObjectUnlocked()</code>
		 * once a data object becomes writable again. In that case, the next
		 * attempt at writing links to the data object is postponed by a far
		 * longer period after <code>canWriteLinks()</code> returned false,
		 * saving considerable effort. This default implementation simply
		 * returns false. Sub classes should overwrite it to indicate so if
		 * their underlying data object storage facility supports respective
		 * notifications.
		 * @return true if the link writer provides unlocking notifications
		 */
		protected boolean providesUnlockNotifications() {
			return false;
		}
		
		synchronized boolean suspendOrReSchedulePendingLinks(String dataId) {
			if (this.canWriteLinks(dataId))
				return false; // we can write them links now
			
			//	suspend links if we have unlocking notifications ...
			if (this.providesUnlockNotifications())
				this.host.suspendPendingLinks(dataId);
			
			//	... and postpone them otherwise
			else this.host.postponePendingLinks(dataId);
			
			//	indicate "not now" to calling code
			return true;
		}
		
		/**
		 * Notify the host ELS that a data object with a given ID has been
		 * unlocked in the underlying data storage facility. If any links are
		 * suspended for the data object with the argument ID, write-through
		 * starts as soon as possible.
		 * @param dataId the ID of the data object that was unlocked
		 */
		protected synchronized void dataObjectUnlocked(String dataId) {
			this.host.unSuspendPendingLinks(dataId);
		}
		
		/**
		 * Retrieve the centrally configured user name to use for writing links
		 * to the underlying data objects. Sub classes can also use other user
		 * names, or none at all, if data storage does not require user names.
		 * @return the update user name
		 */
		protected String getUpdateUserName() {
			return this.host.updateUserName;
		}
		
		/**
		 * Write external links through to the data object they belongs to. The
		 * returned array holds the portion of the argument links that could
		 * not actually be written.
		 * @param dataId the ID of the data object to write to
		 * @param links the link to handle
		 * @param handlers the available link handlers
		 * @return an array holding the links that failed to handle
		 */
		public abstract ExternalLink[] writeLinks(String dataId, ExternalLink[] links, ExternalLinkHandler[] handlers);
		
		public void writeLog(String logEntry) {
			if (logEntry != null)
				this.host.logInfo(logEntry);
		}
	}
	
	private IoProvider io;
	private AsynchronousDataActionHandler linkHandler;
	
	private LinkWriter[] linkWriters = {};
	private LinkedHashSet linkHandlers = new LinkedHashSet();
	private Set suspendedDataIDs = Collections.synchronizedSet(new HashSet());
	private String updateUserName = UPDATE_USER_NAME;
	
	/** Zero-argument constructor handing 'ELS' to the super class */
	public GoldenGateELS() {
		super("ELS");
	}
	
	protected void initComponent() {
		
		//	get database connection
		this.io = this.host.getIoProvider();
		if (!this.io.isJdbcAvailable())
			throw new RuntimeException("GoldenGateELS: cannot work without database access.");
		
		//	set up link table
		TableDefinition td = new TableDefinition(LINK_TABLE_NAME);
		td.addColumn(DATA_ID_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
		td.addColumn(DATA_ID_HASH_COLUMN_NAME, TableDefinition.INT_DATATYPE, 0);
		td.addColumn(LINK_DETAIL_ID_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 64);
		td.addColumn(LINK_TYPE_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
		td.addColumn(LINK_STRING_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 128);
		td.addColumn(LINK_MODIFIED_COLUMN_NAME, TableDefinition.BIGINT_DATATYPE, 0);
		td.addColumn(LINK_STATUS_COLUMN_NAME, TableDefinition.CHAR_DATATYPE, 1);
		if (!this.io.ensureTable(td, true))
			throw new RuntimeException("GoldenGateELS: cannot work without database access.");
		
		//	get update user name
		this.updateUserName = this.configuration.getSetting("updateUserName", this.updateUserName);
		
		//	add indexes
		this.io.indexColumn(LINK_TABLE_NAME, DATA_ID_COLUMN_NAME);
		this.io.indexColumn(LINK_TABLE_NAME, DATA_ID_HASH_COLUMN_NAME);
		
		//	create handler service
		this.linkHandler = new AsynchronousDataActionHandler("ElsLinkHandler", this, this.host.getIoProvider()) {
			protected void performDataAction(String dataId, String[] arguments) throws Exception {
				handleLinks(dataId);
			}
		};
		
		//	load IDs of all data objects with suspended links
		String loadQuery = "SELECT distinct(" + DATA_ID_COLUMN_NAME + ")" +
				" FROM " + LINK_TABLE_NAME +
				" WHERE " + LINK_STATUS_COLUMN_NAME + " = '" + 'S' + "'" +
				";";
		SqlQueryResult sqr = null;
		try {
			sqr = this.io.executeSelectQuery(loadQuery);
			while (sqr.next())
				this.suspendedDataIDs.add(sqr.getString(0));
		}
		catch (SQLException sqle) {
			this.logError("GoldenGateELS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while getting data object IDs with suspended links.");
			this.logError("  query was " + loadQuery);
		}
		finally {
			if (sqr != null)
				sqr.close();
		}
	}
	
	public void link() {
		
		//	load link writer plug-ins
		this.loadLinkWriters(this);
		
		//	anything to work with?
		if (this.linkWriters.length == 0)
			throw new RuntimeException("GoldenGateELS: cannot work without link writers");
	}
	
	public void linkInit() {
		
		//	start handler service
		this.linkHandler.start();
	}
	
	private synchronized void loadLinkWriters(final GoldenGateServerActivityLogger log) {
		
		//	shut down previous link writers
		for (int w = 0; w < this.linkWriters.length; w++)
			this.linkWriters[w].exit();
		
		//	load link writers
		log.logInfo("Loading link writers ...");
		Object[] linkWriters = GamtaClassLoader.loadComponents(
				dataPath,
				LinkWriter.class,
				new ComponentInitializer() {
					public void initialize(Object component, String componentJarName) throws Exception {
						File dataPath = new File(GoldenGateELS.this.dataPath, (componentJarName.substring(0, (componentJarName.length() - 4)) + "Data"));
						if (!dataPath.exists()) dataPath.mkdir();
						LinkWriter importer = ((LinkWriter) component);
						importer.setHost(GoldenGateELS.this);
						importer.init();
					}
				});
		log.logInfo("Link writers loaded");
		
		//	sort link writers by priority
		Arrays.sort(linkWriters, new Comparator() {
			public int compare(Object obj1, Object obj2) {
				return (((LinkWriter) obj2).getPriority() - ((LinkWriter) obj1).getPriority());
			}
		});
		log.logInfo("Link writers sorted");
		
		//	store link writers
		this.linkWriters = new LinkWriter[linkWriters.length];
		for (int w = 0; w < linkWriters.length; w++)
			this.linkWriters[w] = ((LinkWriter) linkWriters[w]);
		log.logInfo("Link writers stored");
	}
	
	/**
	 * Add an external link handler to participate in link handling.
	 * @param elh the link handler to add
	 */
	public void addHandler(ExternalLinkHandler elh) {
		if (elh != null)
			this.linkHandlers.add(elh);
	}
	
	/**
	 * Remove an external link handler to exclude it from link handling.
	 * @param elh the link handler to remove
	 */
	public void removeHandler(ExternalLinkHandler elh) {
		if (elh != null)
			this.linkHandlers.remove(elh);
	}
	
	protected void exitComponent() {
		
		//	shut down handler service
		this.linkHandler.shutdown();
	}
	
	private static final String REWRITE_LINKS_COMMAND = "rewriteLinks";
	private static final String RELOAD_WRITERS_COMMAND = "reloadWriters";
	
	public ComponentAction[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentActionConsole cac;
		
		//	reload link writers
		cac = new ComponentActionConsole() {
			public String getActionCommand() {
				return REWRITE_LINKS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						(REWRITE_LINKS_COMMAND + " <dataId>"),
						"Schedule the links belonging to a data object for re-writing (e.g. after a version revert):",
						"- <dataId>: the ID of the data object to re-write the links to"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1)
					scheduleLinkRewrite(arguments[0], this);
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the data object ID as the only argument.");
			}
		};
		cal.add(cac);
		
		//	reload link writers
		cac = new ComponentActionConsole() {
			public String getActionCommand() {
				return RELOAD_WRITERS_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						RELOAD_WRITERS_COMMAND,
						"Reload the link writers (i.e., the components writing links to data objects)"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0)
					loadLinkWriters(this);
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(cac);
		
		//	add actions from link handler
		cal.addAll(Arrays.asList(this.linkHandler.getActions()));
		
		//	finally ...
		return ((ComponentActionConsole[]) cal.toArray(new ComponentActionConsole[cal.size()]));
	}
	
	private void scheduleLinkRewrite(String dataId, ComponentActionConsole cac) {
		
		//	reset handled and deleted links to pending (will be suspended at first writing attempt if required)
		String markerQuery = "UPDATE " + LINK_TABLE_NAME +
				" SET " + LINK_STATUS_COLUMN_NAME + " = '" + 'P' + "'" +
				" WHERE " + DATA_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(dataId) + "'" +
				" AND " + DATA_ID_HASH_COLUMN_NAME + " = " + dataId.hashCode() + "" +
				" AND (" + LINK_STATUS_COLUMN_NAME + " = '" + 'H' + "'" +
				" OR " + LINK_STATUS_COLUMN_NAME + " = '" + 'D' + "')" +
				";";
		try {
			int rewriteLinks = this.io.executeUpdateQuery(markerQuery);
			
			//	schedule write-back action for data object
			if (rewriteLinks == 0)
				cac.reportResult("No links to re-write on data object '" + dataId + "'");
			else {
				cac.reportResult("Re-writing " + rewriteLinks + " links to data object '" + dataId + "'");
				this.linkHandler.enqueueDataAction(dataId);
			}
		}
		catch (SQLException sqle) {
			this.logError("GoldenGateELS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while marking links for re-write on '" + dataId + "'.");
			this.logError("  query was " + markerQuery);
		}
	}
	
	/**
	 * Retrieve an external link of a specific type belonging a data object with
	 * a given ID.
	 * @param dataId the ID of the data object the link belongs to
	 * @param type the type of link, i.e., what it links to
	 * @return the requested link
	 */
	public ExternalLink getExternalLink(String dataId, String type) {
		return this.getExternalLink(dataId, null, type);
	}
	
	/**
	 * Retrieve an external link of a specific type belonging a detail with a
	 * given ID that lies in a data object with a given ID.
	 * @param dataId the ID of the data object the link belongs in
	 * @param detailId the ID of the part inside the data object the link belongs to (if different from data object proper)
	 * @param type the type of link, i.e., what it links to
	 * @return the requested link
	 */
	public ExternalLink getExternalLink(String dataId, String detailId, String type) {
		String selectQuery = "SELECT " + LINK_STRING_COLUMN_NAME + ", " + LINK_STATUS_COLUMN_NAME +
				" FROM " + LINK_TABLE_NAME + 
				" WHERE " + DATA_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(dataId) + "'" + 
				" AND " + DATA_ID_HASH_COLUMN_NAME + " = " + dataId.hashCode() + 
				" AND " + LINK_DETAIL_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape((detailId == null) ? "" : detailId) + "'" +
				" AND " + LINK_TYPE_COLUMN_NAME + " = '" + EasyIO.sqlEscape(type) + "'" +
				";";
		SqlQueryResult sqr = null;
		try {
			sqr = this.io.executeSelectQuery(selectQuery);
			if (sqr.next()) {
				String link = sqr.getString(0);
				String status = sqr.getString(1);
				return new ExternalLink(dataId, (((detailId == null) || (detailId.trim().length() == 0)) ? null : detailId), type, link, status.charAt(0));
			}
			else return null;
		}
		catch (SQLException sqle) {
			this.logError("GoldenGateELS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while getting external link '" + type + "' for '" + ((detailId == null) ? dataId : (detailId + "' in '" + dataId)) + "'.");
			this.logError("  query was " + selectQuery);
			return null;
		}
		finally {
			if (sqr != null)
				sqr.close();
		}
	}
	
	/**
	 * Retrieve the external links belonging a data object with a given ID.
	 * @param dataId the ID of the data object the link belongs to
	 * @return an array holding the links
	 */
	public ExternalLink[] getExternalLinks(String dataId) {
		return this.getExternalLinks(dataId, null);
	}
	
	/**
	 * Retrieve the external links belonging a detail with a given ID that lies
	 * in a data object with a given ID.
	 * @param dataId the ID of the data object the link belongs in
	 * @param detailId the ID of the part inside the data object the link belongs to (if different from data object proper)
	 * @return an array holding the links
	 */
	public ExternalLink[] getExternalLinks(String dataId, String detailId) {
		ArrayList links = new ArrayList();
		String selectQuery = "SELECT " + LINK_DETAIL_ID_COLUMN_NAME + ", " + LINK_TYPE_COLUMN_NAME + ", " + LINK_STRING_COLUMN_NAME + ", " + LINK_STATUS_COLUMN_NAME +
				" FROM " + LINK_TABLE_NAME + 
				" WHERE " + DATA_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(dataId) + "'" + 
				" AND " + DATA_ID_HASH_COLUMN_NAME + " = " + dataId.hashCode() + 
				((detailId == null) ? "" : (" AND " + LINK_DETAIL_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(detailId) + "'")) +
				";";
		SqlQueryResult sqr = null;
		try {
			sqr = this.io.executeSelectQuery(selectQuery);
			while (sqr.next()) {
				detailId = sqr.getString(0);
				String type = sqr.getString(1);
				String link = sqr.getString(2);
				String status = sqr.getString(3);
				links.add(new ExternalLink(dataId, ((detailId.trim().length() == 0) ? null : detailId), type, link, status.charAt(0)));
			}
		}
		catch (SQLException sqle) {
			this.logError("GoldenGateELS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while getting external links for '" + ((detailId == null) ? dataId : (detailId + "' in '" + dataId)) + "'.");
			this.logError("  query was " + selectQuery);
		}
		finally {
			if (sqr != null)
				sqr.close();
		}
		return ((ExternalLink[]) links.toArray(new ExternalLink[links.size()]));
	}
	
	/**
	 * Store an external link to be added to the data object it belongs to soon
	 * as the latter is free for update.
	 * @param dataId the ID of the data object the link belongs to
	 * @param type the type of link, i.e., what it links to
	 * @param link the link proper, e.g. a URL or DOI
	 */
	public void storeExternalLink(String dataId, String type, String link) {
		this.storeExternalLink(dataId, null, type, link, false);
	}
	
	/**
	 * Store an external link to be added to the data object it belongs to soon
	 * as the latter is free for update.
	 * @param dataId the ID of the data object the link belongs to
	 * @param type the type of link, i.e., what it links to
	 * @param link the link proper, e.g. a URL or DOI
	 * @param forceWrite force link write-through even if previously written?
	 */
	public void storeExternalLink(String dataId, String type, String link, boolean forceWrite) {
		this.storeExternalLink(dataId, null, type, link, forceWrite);
	}
	
	/**
	 * Store an external link to be added to the data object it belongs to soon
	 * as the latter is free for update.
	 * @param dataId the ID of the data object the link belongs in
	 * @param detailId the ID of the part inside the data object the link belongs to (if different from data object proper)
	 * @param type the type of link, i.e., what it links to
	 * @param link the link proper, e.g. a URL or DOI
	 */
	public void storeExternalLink(String dataId, String detailId, String type, String link) {
		this.storeExternalLink(dataId, detailId, type, link, false);
	}
	
	/**
	 * Store an external link to be added to the data object it belongs to soon
	 * as the latter is free for update.
	 * @param dataId the ID of the data object the link belongs in
	 * @param detailId the ID of the part inside the data object the link belongs to (if different from data object proper)
	 * @param type the type of link, i.e., what it links to
	 * @param link the link proper, e.g. a URL or DOI
	 * @param forceWrite force link write-through even if previously written?
	 */
	public void storeExternalLink(String dataId, String detailId, String type, String link, boolean forceWrite) {
		
		//	try and load link first, and do not schedule anything if link unchanged (unless explicitly requested)
		if (!forceWrite) {
			String selectQuery = "SELECT " + LINK_STRING_COLUMN_NAME + ", " + LINK_STATUS_COLUMN_NAME +
					" FROM " + LINK_TABLE_NAME + 
					" WHERE " + DATA_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(dataId) + "'" + 
					" AND " + DATA_ID_HASH_COLUMN_NAME + " = " + dataId.hashCode() + 
					" AND " + LINK_DETAIL_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape((detailId == null) ? "" : detailId) + "'" +
					" AND " + LINK_TYPE_COLUMN_NAME + " = '" + EasyIO.sqlEscape(type) + "'" +
					" AND " + LINK_STATUS_COLUMN_NAME + " <> '" + 'D' + "'" +
					";";
			SqlQueryResult sqr = null;
			try {
				sqr = this.io.executeSelectQuery(selectQuery);
				if (sqr.next()) {
					String linkString = sqr.getString(0);
					String linkStatus = sqr.getString(1);
					if (linkString.equals(link) && (linkStatus.length() != 0)) {
						if ((linkStatus.charAt(0) == 'P') || (linkStatus.charAt(0) == 'S'))
							this.logInfo("Link '" + type + "' on '" + dataId + ((detailId == null) ? "" : ("/" + detailId)) + "' previously stored as '" + link + "'");
						else if (linkStatus.charAt(0) == 'H')
							this.logInfo("Link '" + type + "' on '" + dataId + ((detailId == null) ? "" : ("/" + detailId)) + "' previously set to '" + link + "'");
						return;
					}
				}
			}
			catch (SQLException sqle) {
				this.logError("GoldenGateELS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while getting existing '" + type + "' link for '" + dataId + "'.");
				this.logError("  query was " + selectQuery);
			}
			finally {
				if (sqr != null)
					sqr.close();
			}
		}
		
		//	persist link in database (update/restore or insert)
		long linkModTime = System.currentTimeMillis();
		boolean createLinkSuspended = this.suspendedDataIDs.contains(dataId);
		String updateQuery = "UPDATE " + LINK_TABLE_NAME +
				" SET " + LINK_STRING_COLUMN_NAME + " = '" + EasyIO.sqlEscape(link) + "'," +
				" " + LINK_MODIFIED_COLUMN_NAME + " = " + linkModTime + "," +
				" " + LINK_STATUS_COLUMN_NAME + " = '" + (createLinkSuspended ? 'S' : 'P') + "'" +
				" WHERE " + DATA_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(dataId) + "'" +
				" AND " + DATA_ID_HASH_COLUMN_NAME + " = " + dataId.hashCode() + "" +
				" AND " + LINK_DETAIL_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape((detailId == null) ? "" : detailId) + "'" +
				" AND " + LINK_TYPE_COLUMN_NAME + " = '" + EasyIO.sqlEscape(type) + "'" +
				";";
		try {
			if (this.io.executeUpdateQuery(updateQuery) == 0) {
				updateQuery = "INSERT INTO " + LINK_TABLE_NAME +
						" (" + DATA_ID_COLUMN_NAME + ", " + DATA_ID_HASH_COLUMN_NAME + ", " + LINK_DETAIL_ID_COLUMN_NAME + ", " + LINK_TYPE_COLUMN_NAME + ", " + LINK_STRING_COLUMN_NAME + ", " + LINK_MODIFIED_COLUMN_NAME + ", " + LINK_STATUS_COLUMN_NAME + ")" +
						" VALUES" + 
						" ('" + EasyIO.sqlEscape(dataId) + "', " + dataId.hashCode() + ", '" +  EasyIO.sqlEscape((detailId == null) ? "" : detailId) + "', '" + EasyIO.sqlEscape(type) + "', '" + EasyIO.sqlEscape(link) + "', " + linkModTime + ", '" + 'P' + "')" +
						";";
				this.io.executeUpdateQuery(updateQuery);
			}
		}
		catch (SQLException sqle) {
			this.logError("GoldenGateELS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while while persisting '" + type + "' link on '" + dataId + "'.");
			this.logError("  query was " + updateQuery);
		}
		
		//	schedule update for document in 5 minutes (unless data object is suspended)
		if (!createLinkSuspended)
			this.linkHandler.scheduleDataAction(dataId, (1000 * 60 * 5)); // TODO make delay configurable
	}
	
	/**
	 * Delete any external links that were previously stored to be added to the
	 * data object they belong to. This helps prevent stale links from staying
	 * in pending status after the data object they belong to was deleted
	 * before the links could actually be written. Leaving the type argument
	 * blank will delete links of all types.
	 * @param dataId the ID of the data object the target links belong to
	 * @param type the type of link, i.e., what it links to
	 */
	public void deleteExternalLinks(String dataId, String type) {
		this.deleteExternalLinks(dataId, null, type);
	}
	
	/**
	 * Delete any external links that were previously stored to be added to the
	 * data object they belong to. This helps prevent stale links from staying
	 * in pending status after the data object (or detail therein) they belong
	 * to was deleted before the links could actually be written. Leaving the
	 * type argument blank will delete links of all types.
	 * @param dataId the ID of the data object the target links belong to
	 * @param detailId the ID of the part inside the data object the target links belong to (if different from data object proper)
	 * @param type the type of link, i.e., what it links to
	 */
	public void deleteExternalLinks(String dataId, String detailId, String type) {
		
		//	mark any pending or suspended links as deleted in database
		String markerQuery = "UPDATE " + LINK_TABLE_NAME +
				" SET " + LINK_STATUS_COLUMN_NAME + " = '" + 'D' + "'" +
				" WHERE " + DATA_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(dataId) + "'" +
				" AND " + DATA_ID_HASH_COLUMN_NAME + " = " + dataId.hashCode() + "" +
				((detailId == null) ? "" : (" AND " + DATA_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(detailId) + "'")) +
				((type == null) ? "" : (" AND " + LINK_TYPE_COLUMN_NAME + " = '" + EasyIO.sqlEscape(type) + "'")) +
				" AND (" + LINK_STATUS_COLUMN_NAME + " = '" + 'P' + "'" +
				" OR " + LINK_STATUS_COLUMN_NAME + " = '" + 'S' + "')" +
				";";
		try {
			this.io.executeUpdateQuery(markerQuery);
		}
		catch (SQLException sqle) {
			this.logError("GoldenGateELS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while marking links as deleted on '" + dataId + ((detailId == null) ? "" : ("/" + detailId)) + "'.");
			this.logError("  query was " + markerQuery);
		}
	}
	
	private static class TimedExternalLink extends ExternalLink {
		final long modTime;
		TimedExternalLink(String dataId, String detailId, String linkType, String link, long modTime) {
			super(dataId, detailId, linkType, link);
			this.modTime = modTime;
		}
	}
	
	private ExternalLink[] getPendingLinks(String dataId) {
		ArrayList links = new ArrayList();
		String loadQuery = "SELECT " + LINK_DETAIL_ID_COLUMN_NAME + ", " + LINK_TYPE_COLUMN_NAME + ", " + LINK_STRING_COLUMN_NAME + ", " + LINK_MODIFIED_COLUMN_NAME +
				" FROM " + LINK_TABLE_NAME + 
				" WHERE " + DATA_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(dataId) + "'" + 
				" AND " + DATA_ID_HASH_COLUMN_NAME + " = " + dataId.hashCode() + 
				" AND (" + LINK_STATUS_COLUMN_NAME + " = '" + 'P' + "'" +
				" OR " + LINK_STATUS_COLUMN_NAME + " = '" + 'S' + "')" +
				" ORDER BY " + LINK_MODIFIED_COLUMN_NAME +
				";";
		SqlQueryResult sqr = null;
		try {
			sqr = this.io.executeSelectQuery(loadQuery);
			while (sqr.next()) {
				String detailId = sqr.getString(0);
				String type = sqr.getString(1);
				String link = sqr.getString(2);
				long modTime = sqr.getLong(3);
				links.add(new TimedExternalLink(dataId, ((detailId.trim().length() == 0) ? null : detailId), type, link, modTime));
			}
		}
		catch (SQLException sqle) {
			this.logError("GoldenGateELS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while getting external links for '" + dataId + "'.");
			this.logError("  query was " + loadQuery);
		}
		finally {
			if (sqr != null)
				sqr.close();
		}
		return ((ExternalLink[]) links.toArray(new ExternalLink[links.size()]));
	}
	
	private void handleLinks(String dataId) {
		this.logInfo("GoldenGateELS: handling external links in '" + dataId + "'");
		
		//	find link writer
		LinkWriter writer = this.findLinkWriter(dataId);
		if (writer == null) {
			this.logWarning(" ==> could not find link writer");
			return;
		}
		
		//	suspend (upon unlocking) or re-schedule (in 5 minutes) write-through if data object locked
		if (writer.suspendOrReSchedulePendingLinks(dataId))
			return;
//		if (!writer.providesUnlockNotifications()) {
//			this.linkHandler.scheduleDataAction(dataId, (1000 * 60 * 5)); // TODO_ make delay configurable, or maybe adaptive to number of links
//			this.logWarning(" ==> data object currently locked, re-scheduling");
//		}
		
		//	load unhandled links from database
		ExternalLink[] links = this.getPendingLinks(dataId);
		if (links.length == 0) {
			this.logWarning(" ==> no external links found to handle for data object '" + dataId + "'");
			return;
		}
		this.logInfo(" - got " + links.length + " external links to write");
		
		//	tray up link handlers to protect against modification
		ExternalLinkHandler[] handlers = ((ExternalLinkHandler[]) this.linkHandlers.toArray(new ExternalLinkHandler[this.linkHandlers.size()]));
		
		//	write links, and mark them as handled
		ExternalLink[] remainingLinks = writer.writeLinks(dataId, links, handlers);
		long firstRemainingModTime = Long.MAX_VALUE;
		if (remainingLinks.length == 0)
			this.logInfo(" - " + links.length + " external links written");
		
		//	log warning if some links go unhandled
		else {
			this.logWarning(" - failed to write " + remainingLinks.length + " external links:");
			for (int l = 0; l < remainingLinks.length; l++) {
				firstRemainingModTime = Math.min(firstRemainingModTime, ((TimedExternalLink) remainingLinks[l]).modTime);
				this.logWarning("   - " + remainingLinks[l].type + " " + remainingLinks[l].link + " to " + remainingLinks[l].detailId);
			}
		}
		
		//	check if any links added while writing others
		ExternalLink[] afterLinks = this.getPendingLinks(dataId);
		if (afterLinks.length > links.length) {
			this.logWarning(" - " + (afterLinks.length - links.length) + " external links added while writing");
			return;
		}
		
		//	mark any pending or suspended links as handled in database
		String markerQuery = "UPDATE " + LINK_TABLE_NAME +
				" SET " + LINK_STATUS_COLUMN_NAME + " = '" + 'H' + "'" +
				" WHERE " + DATA_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(dataId) + "'" +
				" AND " + DATA_ID_HASH_COLUMN_NAME + " = " + dataId.hashCode() + "" +
				" AND " + LINK_MODIFIED_COLUMN_NAME + " < " + firstRemainingModTime + "" +
				" AND (" + LINK_STATUS_COLUMN_NAME + " = '" + 'P' + "'" +
				" OR " + LINK_STATUS_COLUMN_NAME + " = '" + 'S' + "')" +
				";";
		try {
			this.io.executeUpdateQuery(markerQuery);
		}
		catch (SQLException sqle) {
			this.logError("GoldenGateELS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while marking links as handled on '" + dataId + "'.");
			this.logError("  query was " + markerQuery);
		}
	}
	
	private LinkWriter findLinkWriter(String dataId) {
		for (int w = 0; w < this.linkWriters.length; w++) {
			if (this.linkWriters[w].canHandleLinks(dataId))
				return this.linkWriters[w];
		}
		return null;
	}
	
	void postponePendingLinks(String dataId) {
		this.linkHandler.scheduleDataAction(dataId, (1000 * 60 * 5)); // TODO make delay configurable
		this.logWarning(" ==> data object currently locked, re-scheduled");
	}
	
	void suspendPendingLinks(String dataId) {
		
		//	mark any pending links as suspended in database
		String markerQuery = "UPDATE " + LINK_TABLE_NAME +
				" SET " + LINK_STATUS_COLUMN_NAME + " = '" + 'S' + "'" +
				" WHERE " + DATA_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(dataId) + "'" +
				" AND " + DATA_ID_HASH_COLUMN_NAME + " = " + dataId.hashCode() + "" +
				" AND " + LINK_STATUS_COLUMN_NAME + " = '" + 'P' + "'" +
				";";
		try {
			this.io.executeUpdateQuery(markerQuery);
		}
		catch (SQLException sqle) {
			this.logError("GoldenGateELS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while marking links as handled on '" + dataId + "'.");
			this.logError("  query was " + markerQuery);
			return;
		}
		
		//	mark data object ID as bearing suspended links, and schedule re-check in 24 hours
		this.suspendedDataIDs.add(dataId);
		this.linkHandler.scheduleDataAction(dataId, (1000 * 60 * 60 * 24)); // TODO make re-check period configurable
		this.logWarning(" ==> data object currently locked, suspended");
	}
	
	void unSuspendPendingLinks(String dataId) {
		if (this.suspendedDataIDs.remove(dataId))
			this.linkHandler.enqueueDataAction(dataId); // move up link handling
	}
	
	/* TODO use this in:
	 * - RefBank upload (to add RefBank reference string IDs)
	 * - Table externalization (to add table IDs)
	 */
}
