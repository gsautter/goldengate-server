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
package de.uka.ipd.idaho.goldenGateServer.util;

import java.util.Map;

import de.uka.ipd.idaho.easyIO.util.JsonParser;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants;

/**
 * Bearer of data object meta data constants.
 * 
 * @author sautter
 */
public interface DataObjectUpdateConstants extends GoldenGateServerConstants {
	
	/** the attribute holding the name of the user who uploaded a data object */
	public static final String CHECKIN_USER_ATTRIBUTE = "checkinUser";
	
	/** the attribute holding the time when a data object was uploaded */
	public static final String CHECKIN_TIME_ATTRIBUTE = "checkinTime";
	
	/** the attribute holding the name of the user who holds the write lock on a data object */
	public static final String CHECKOUT_USER_ATTRIBUTE = "checkoutUser";
	
	/** the attribute holding the time when a user acquired the write lock on a data object */
	public static final String CHECKOUT_TIME_ATTRIBUTE = "checkoutTime";
	
	/** the attribute holding the name of the user who last updated a data object */
	public static final String UPDATE_USER_ATTRIBUTE = "updateUser";
	
	/** the attribute holding the time when a data object was last updated */
	public static final String UPDATE_TIME_ATTRIBUTE = "updateTime";
	
	
	/**
	 * Event for passing around notifications about operations on data objects
	 * stored in a GoldenGATE Server, namely checkout, release, update, and
	 * delete.
	 * 
	 * @author sautter
	 */
	public static class DataObjectEvent extends GoldenGateServerEvent {
		public static final int UPDATE_TYPE = 0;
		public static final int DELETE_TYPE = 1;
		public static final int CHECKOUT_TYPE = 2;
		public static final int RELEASE_TYPE = 4;
		
		/**
		 * Specialized listener for data object events, receiving notifications
		 * of data object checkout and release operations, besides update and
		 * delete operations.
		 * 
		 * @author sautter
		 */
		public static abstract class DataObjectEventListener extends GoldenGateServerEventListener {
			static {
				//	register factory for event instances soon as first listener created
				registerFactory();
			}
			
			/* (non-Javadoc)
			 * @see de.uka.ipd.idaho.goldenGateServer.events.GoldenGateServerEventListener#notify(de.uka.ipd.idaho.goldenGateServer.events.GoldenGateServerEvent)
			 */
			public void notify(GoldenGateServerEvent gse) {
				if (gse instanceof DataObjectEvent) {
					DataObjectEvent doe = ((DataObjectEvent) gse);
					if (doe.type == CHECKOUT_TYPE)
						this.dataObjectCheckedOut(doe);
					else if (doe.type == RELEASE_TYPE)
						this.dataObjectReleased(doe);
					else if (doe.type == UPDATE_TYPE)
						this.dataObjectUpdated(doe);
					else if (doe.type == DELETE_TYPE)
						this.dataObjectDeleted(doe);
				}
			}
			
			/**
			 * Receive notification that a data object was checked out by a
			 * user.
			 * @param doe the DataObjectEvent providing detail information on
			 *            the checkout
			 */
			public abstract void dataObjectCheckedOut(DataObjectEvent doe);
			
			/**
			 * Receive notification that a data object was updated (can be both
			 * a new data object or an updated version of an existing one).
			 * @param doe the DataObjectEvent providing detail information on
			 *            the update
			 */
			public abstract void dataObjectUpdated(DataObjectEvent doe);
			
			/**
			 * Receive notification that a data object was deleted.
			 * @param doe the DataObjectEvent providing detail information on
			 *            the deletion
			 */
			public abstract void dataObjectDeleted(DataObjectEvent doe);
			
			/**
			 * Receive notification that a data object was released by a user.
			 * @param doe the DataObjectEvent providing detail information on
			 *            the release
			 */
			public abstract void dataObjectReleased(DataObjectEvent doe);
		}
		
		
		/** The name of the user who caused the event */
		public final String user;
		
		/** The ID of the data object affected by the event */
		public final String dataId;
		
		/** The current version number (after an update) of the data object
		 * affected by this event, -1 for all events other than updates */
		public final int version;
		
		/**
		 * Constructor
		 * @param user the name of the user who caused the event
		 * @param dataId the ID of the data object affected by the event
		 * @param version the current version number of the data object (after
		 *            an update, -1 otherwise)
		 * @param type the event type (used for dispatching)
		 * @param sourceClassName the class name of the component issuing the
		 *            event
		 * @param eventTime the timstamp of the event
		 * @param logger an EventLogger to collect log messages while the
		 *            event is being processed in listeners
		 */
		public DataObjectEvent(String user, String dataId, int version, int type, String sourceClassName, long eventTime, EventLogger logger) {
			super(type, sourceClassName, eventTime, (dataId + "-" + type + "-" + eventTime), logger);
			this.user = user;
			this.dataId = dataId;
			this.version = version;
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants.GoldenGateServerEvent#getParameterString()
		 */
		public String getParameterString() {
			return (super.getParameterString() + " " + this.user + " " + this.dataId + " " + this.version);
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants.GoldenGateServerEvent#toJsonObject()
		 */
		public Map toJsonObject() {
			Map json = super.toJsonObject();
			json.put("eventClass", DataObjectEvent.class.getName());
			json.put("user", this.user);
			json.put("dataId", this.dataId);
			json.put("dataVersion", new Integer(this.version));
			return json;
		}
		
		private static EventFactory factory = new EventFactory() {
			public GoldenGateServerEvent getEvent(Map json) {
				if (!DataObjectEvent.class.getName().equals(json.get("eventClass")))
					return null;
				Number eventType = JsonParser.getNumber(json, "eventType");
				String sourceClassName = JsonParser.getString(json, "sourceClass");
				Number eventTime = JsonParser.getNumber(json, "eventTime");
//				String eventId = JsonParser.getString(json, "eventId");
//				boolean isHighPriority = (JsonParser.getBoolean(json, "highPriority") != null);
				
				String user = JsonParser.getString(json, "user");
				String dataId = JsonParser.getString(json, "dataId");
				Number dataVersion = JsonParser.getNumber(json, "dataVersion");
				return new DataObjectEvent(user, dataId, dataVersion.intValue(), eventType.intValue(), sourceClassName, eventTime.longValue(), null);
			}
			public GoldenGateServerEvent getEvent(String className, String paramString) {
				return null; // this is up to sub classes
			}
		};
		static void registerFactory() {
			addFactory(factory);
		}
		static {
			//	register factory for event instances soon as first instance created
			registerFactory();
		}
	}
}
