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
package de.uka.ipd.idaho.goldenGateServer.dta;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;

import de.uka.ipd.idaho.easyIO.EasyIO;
import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.SqlQueryResult;
import de.uka.ipd.idaho.easyIO.sql.TableDefinition;
import de.uka.ipd.idaho.easyIO.util.JsonParser;
import de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent;
import de.uka.ipd.idaho.goldenGateServer.AsynchronousWorkQueue;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerEventService;
import de.uka.ipd.idaho.goldenGateServer.dta.DataObjectTransitAuthority.DataObjectTransitEvent.DataObjectTransitEventListener;
import de.uka.ipd.idaho.goldenGateServer.dta.DataObjectTransitAuthority.Inspector.TransitProblem;

/**
 * Central authority to grant or deny data objects to transit either between
 * two server components or from a server component to a remote destination. It
 * is the responsibility of the source component to consult the checkpoint for
 * permission before they push a data object to its destination. Note that this
 * is <b>not</b> a mandatory check, though, but rather a centralized, unified
 * means of restricting data flow on an object by object basis where the need
 * arises. Note further that this checkpoint is <b>not</b> intended for access
 * permission checks, which depend upon a user account, but rather for checks
 * concerning only the data objects proper.<br/>
 * Actual checks are performed by registered <code>Inspector</code>s, which
 * throw <code>TransitDeniedException</code>s for data objects that are blocked
 * from transit to the requested destination.<br/>
 * Furthermore, this class acts as a notification hub for data object transit
 * events, reporting successful or failed transits to registered listeners.
 * 
 * @author sautter
 */
public class DataObjectTransitAuthority extends AbstractGoldenGateServerComponent {
	/* TODO
MAYBE ALSO: make authorities static in general ...
... with default behavior and checks by registered extensions
- NetworkAccessAuthority for session management
  - register UserAccessAuthority as extension
  - register ApiAccessAuthority as extension
  - default: reject login
  ==> will require new request syntax with parameters next to action command
- NetworkPermissionAuthority for permission management
  - register UserPermissionAuthority as extension
  - default: default argument
- DataAccessAuthority for per-data-object access rights
  - register (to-be-built-some-day) DataAccessChecker as extension
  - default: grant access
==> easier to extend
==> yet more graceful failing
==> fewer non-core dependencies
  ==> less linking effort
==> bundle those adapted authorities in dedicated package
	 */
	
	/**
	 * Event notifying about a data object transfer, or a failure of one.
	 * 
	 * @author sautter
	 */
	public static class DataObjectTransitEvent extends de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants.GoldenGateServerEvent /* for some weird reason ant doesn't build without fully qualified class name */ {
		public static final int SUCCESS_TYPE = 1;
		public static final int FAILURE_TYPE = 2;
		
		/**
		 * Specialized listener for data object transit events, receiving
		 * notifications of successful or failed transits.
		 * 
		 * @author sautter
		 */
		public static abstract class DataObjectTransitEventListener extends GoldenGateServerEventListener {
			static {
				//	register factory for event instances soon as first listener created
				registerFactory();
			}
			public void notify(GoldenGateServerEvent gse) {
				if (gse instanceof DataObjectTransitEvent) {
					DataObjectTransitEvent dote = ((DataObjectTransitEvent) gse);
					if (dote.type == SUCCESS_TYPE)
						this.dataObjectTransitSucceeded(dote);
					else if (dote.type == FAILURE_TYPE)
						this.dataObjectTransitFailed(dote);
				}
			}
			
			/**
			 * Receive notification that a data object (or detail contained in
			 * it) successfully transited from some source to some destination.
			 * @param dote the DataObjectTransitEvent providing details on the
			 *            transit
			 */
			public abstract void dataObjectTransitSucceeded(DataObjectTransitEvent dote);
			
			/**
			 * Receive notification that the transit of a data object (or a
			 * detail contained in it) from some source to some destination
			 * failed.
			 * @param dote the DataObjectTransitEvent providing details on the
			 *            transit and the reasons it failed
			 */
			public abstract void dataObjectTransitFailed(DataObjectTransitEvent dote);
		}
		
		/** The ID of the transiting data object */
		public final String dataId;
		
		/** A label for the transiting data object, e.g. for displaying in a UI */
		public final String dataLabel;
		
		/** The ID of the transiting part inside the main data object (if
		 * different from data object proper) */
		public final String detailId;
		
		/** A label for the transiting part inside the main data object, e.g.
		 * for displaying in a UI (obsolete if <code>detailId</code>) is null */
		public final String detailLabel;
		
		/** The code of the source of the transit */
		public final String source;
		
		/** The code of the destination of the transit */
		public final String destination;
		
		/** An array holding the reasons a data object transit failed (null for
		 * successful transits) */
		public final TransitFailureReason[] failureReasons;
		
		/** Constructor for success events
		 * @param sourceClassName the class name of the component issuing the event
		 * @param dataId the ID of the data object that transited
		 * @param dataLabel a label for the data object that transited
		 * @param detailId the ID of the data object detail that actually transited
		 * @param detailLabel a label for the data object detail that actually transited
		 * @param source the source of the transit
		 * @param destination the destination of the transit
		 */
		public DataObjectTransitEvent(String sourceClassName, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination) {
			this(SUCCESS_TYPE, sourceClassName, dataId, dataLabel, detailId, detailLabel, source, destination, null);
		}
		
		/** Constructor for failure events
		 * @param sourceClassName the class name of the component issuing the event
		 * @param dataId the ID of the data object whose transit failed
		 * @param dataLabel a label for the data object whose transit failed
		 * @param detailId the ID of the data object detail that actually attempted to transit
		 * @param detailLabel a label for the data object detail that actually attempted to transit
		 * @param source the source of the transit
		 * @param destination the destination of the transit
		 * @param failureSource the name of the involved pat that failed the transit
		 * @param failureType a type of the reason the transit failed
		 * @param failureDescription a description of the reason the transit failed
		 */
		public DataObjectTransitEvent(String sourceClassName, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination, String failureSource, String failureType, String failureDescription) {
			this(sourceClassName, dataId, dataLabel, detailId, detailLabel, source, destination, wrapReason(failureSource, failureType, failureDescription));
		}
		private static TransitFailureReason[] wrapReason(String source, String type, String description) {
			if (description == null)
				return null;
			TransitFailureReason[] reasons = {
				new TransitFailureReason(source, type, description)
			};
			return reasons;
		}
		
		/** Constructor for failure events
		 * @param sourceClassName the class name of the component issuing the event
		 * @param dataId the ID of the data object whose transit failed
		 * @param dataLabel a label for the data object whose transit failed
		 * @param detailId the ID of the data object detail that actually attempted to transit
		 * @param detailLabel a label for the data object detail that actually attempted to transit
		 * @param source the source of the transit
		 * @param destination the destination of the transit
		 * @param failureReasons the reasons the transit failed
		 */
		public DataObjectTransitEvent(String sourceClassName, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination, TransitFailureReason[] failureReasons) {
			this(((failureReasons == null) ? SUCCESS_TYPE : FAILURE_TYPE), sourceClassName, dataId, dataLabel, detailId, detailLabel, source, destination, failureReasons);
		}
		
		private DataObjectTransitEvent(int type, String sourceClassName, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination, TransitFailureReason[] failureReasons) {
			this(type, sourceClassName, -1, buildEventId(dataId, detailId, source, destination), dataId, dataLabel, detailId, detailLabel, source, destination, failureReasons);
		}
		private static String buildEventId(String dataId, String detailId, String source, String destination) {
			return (dataId + ((detailId == null) ? "" : ("-" + detailId)) + "-" + source + "-" + destination + "-" + System.currentTimeMillis());
		}
		private DataObjectTransitEvent(int type, String sourceClassName, long eventTime, String eventId, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination, TransitFailureReason[] failureReasons) {
			super(type, false, sourceClassName, eventTime, eventId, null);
			this.dataId = dataId;
			this.dataLabel = dataLabel;
			this.detailId = detailId;
			this.detailLabel = detailLabel;
			this.source = source;
			this.destination = destination;
			this.failureReasons = failureReasons;
		}
		
		public String getParameterString() {
			String failureReasons = "";
			if (this.failureReasons != null) {
				StringBuffer frs = new StringBuffer();
				for (int r = 0; r < this.failureReasons.length; r++) {
					if (r != 0)
						frs.append(" ");
					frs.append(encodeParameter(this.failureReasons[r].toString()));
				}
				failureReasons = encodeParameter(frs.toString());
			}
			return (super.getParameterString()
					+ " " + this.dataId
					+ " " + ((this.dataLabel == null) ? "" : encodeParameter(this.dataLabel))
					+ " " + ((this.detailId == null) ? "" : this.detailId)
					+ " " + ((this.detailLabel == null) ? "" : encodeParameter(this.detailLabel))
					+ " " + encodeParameter(this.source)
					+ " " + encodeParameter(this.destination)
					+ " " + failureReasons);
		}
		
		public Map toJsonObject() {
			Map json = super.toJsonObject();
			json.put("eventClass", DataObjectTransitEvent.class.getName());
			json.put("dataId", this.dataId);
			if (this.dataLabel != null)
				json.put("dataLabel", this.dataLabel);
			if (this.detailId != null)
				json.put("detailId", this.detailId);
			if (this.detailLabel != null)
				json.put("detailLabel", this.detailLabel);
			json.put("source", this.source);
			json.put("destination", this.destination);
			if (this.failureReasons != null) {
				List frs = new ArrayList(this.failureReasons.length);
				for (int r = 0; r < this.failureReasons.length; r++)
					frs.add(this.failureReasons[r].toString());
				json.put("failureReasons", frs);
			}
			return json;
		}
		
		private static EventFactory factory = new EventFactory() {
			public GoldenGateServerEvent getEvent(Map json) {
				if (!DataObjectTransitEvent.class.getName().equals(json.get("eventClass")))
					return null;
				Number eventType = JsonParser.getNumber(json, "eventType");
				String sourceClassName = JsonParser.getString(json, "sourceClass");
				Number eventTime = JsonParser.getNumber(json, "eventTime");
				String eventId = JsonParser.getString(json, "eventId");
//				boolean isHighPriority = (JsonParser.getBoolean(json, "highPriority") != null);
				
				String dataId = JsonParser.getString(json, "dataId");
				String dataLabel = JsonParser.getString(json, "dataLabel");
				String detailId = JsonParser.getString(json, "detailId");
				String detailLabel = JsonParser.getString(json, "detailLabel");
				String source = JsonParser.getString(json, "source");
				String destination = JsonParser.getString(json, "destination");
				List failureReasons = JsonParser.getArray(json, "failureReasons");
				if (failureReasons == null)
					return new DataObjectTransitEvent(eventType.intValue(), sourceClassName, eventTime.longValue(), eventId, dataId, dataLabel, detailId, detailLabel, source, destination, null);
				TransitFailureReason[] frs = new TransitFailureReason[failureReasons.size()];
				for (int r = 0; r < failureReasons.size(); r++)
					frs[r] = TransitFailureReason.parserFailureReason(JsonParser.getString(failureReasons, r));
				return new DataObjectTransitEvent(eventType.intValue(), sourceClassName, eventTime.longValue(), eventId, dataId, dataLabel, detailId, detailLabel, source, destination, frs);
			}
			public GoldenGateServerEvent getEvent(String className, String paramString) {
				return (DataObjectTransitEvent.class.getName().equals(className) ? parseEvent(paramString) : null);
			}
		};
		static void registerFactory() {
			addFactory(factory);
		}
		static {
			//	register factory for event instances soon as first instance created
			registerFactory();
		}
		
		/**
		 * Parse a transit event from its string representation returned by the
		 * getParameterString() method.
		 * @param data the string to parse
		 * @return a transit event created from the specified data
		 * @deprecated use JSON based serialization
		 */
		public static DataObjectTransitEvent parseEvent(String data) {
			String[] dataItems = data.split("\\s");
			TransitFailureReason[] failureReasons;
			if ((dataItems.length <= 10) || "".equals(dataItems[10]))
				failureReasons = null;
			else {
				String[] frs = decodeParameter(dataItems[10]).split("\\s");
				failureReasons = new TransitFailureReason[frs.length];
				for (int r = 0; r < frs.length; r++) {
					String fr = decodeParameter(frs[r]);
					failureReasons[r] = TransitFailureReason.parserFailureReason(fr);
				}
			}
			return new DataObjectTransitEvent(Integer.parseInt(dataItems[0]), dataItems[1], Long.parseLong(dataItems[2]), dataItems[3],
					dataItems[4],
					((dataItems.length <= 5) || "".equals(dataItems[5]) ? null : decodeParameter(dataItems[5])),
					((dataItems.length <= 6) || "".equals(dataItems[6]) ? null : dataItems[6]),
					((dataItems.length <= 7) || "".equals(dataItems[7]) ? null : decodeParameter(dataItems[7])),
					((dataItems.length <= 8) || "".equals(dataItems[8]) ? null : decodeParameter(dataItems[8])),
					((dataItems.length <= 9) || "".equals(dataItems[9]) ? null : decodeParameter(dataItems[9])),
					failureReasons);
		}
	}
	
	/**
	 * Issue a notification about a successful data object transit.
	 * @param sourceClassName the class name of the server component that did
	 *            the transit
	 * @param dataId the ID of the data object
	 * @param dataLabel a label for the data object
	 * @param source the source of the transit
	 * @param destination the destination of the transit
	 */
	public static void notifyDataObjectTransitSuccessful(String sourceClassName, String dataId, String dataLabel, String source, String destination) {
		notifyDataObjectTransitSuccessful(sourceClassName, dataId, dataLabel, null, null, source, destination);
	}
	
	/**
	 * Issue a notification about a successful data object transit.
	 * @param sourceClassName the class name of the server component that did
	 *            the transit
	 * @param dataId the ID of the data object
	 * @param dataLabel a label for the data object
	 * @param detailId the ID of the detail within the data object that
	 *            actually transited
	 * @param detailLabel a label for the detail
	 * @param source the source of the transit
	 * @param destination the destination of the transit
	 */
	public static void notifyDataObjectTransitSuccessful(String sourceClassName, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination) {
		notifyDataObjectTransitEvent(new DataObjectTransitEvent(
				sourceClassName,
				dataId, dataLabel,
				detailId, detailLabel,
				source, destination));
	}
	
	/**
	 * Issue a notification about a failed data object transit.
	 * @param sourceClassName the class name of the server component that
	 *            attempted the transit
	 * @param dataId the ID of the data object
	 * @param dataLabel a label for the data object
	 * @param source the source of the transit
	 * @param destination the destination of the transit
	 * @param cause the exception that caused the transit to fail
	 */
	public static void notifyDataObjectTransitFailed(String sourceClassName, String dataId, String dataLabel, String source, String destination, TransitDeniedException cause) {
		notifyDataObjectTransitFailed(sourceClassName, dataId, dataLabel, null, null, source, destination, cause);
	}
	
	/**
	 * Issue a notification about a failed data object transit.
	 * @param sourceClassName the class name of the server component that
	 *            attempted the transit
	 * @param dataId the ID of the data object
	 * @param dataLabel a label for the data object
	 * @param source the source of the transit
	 * @param destination the destination of the transit
	 * @param failureSource the name of the involved component that caused the
	 *            transit to fail
	 * @param failureType a type of the reason the transit failed
	 * @param cause the exception that caused the transit to fail
	 */
	public static void notifyDataObjectTransitFailed(String sourceClassName, String dataId, String dataLabel, String source, String destination, String failureSource, String failureType, Exception cause) {
		notifyDataObjectTransitFailed(sourceClassName, dataId, dataLabel, null, null, source, destination, failureSource, failureType, cause);
	}
	
	/**
	 * Issue a notification about a failed data object transit.
	 * @param sourceClassName the class name of the server component that
	 *            attempted the transit
	 * @param dataId the ID of the data object
	 * @param dataLabel a label for the data object
	 * @param source the source of the transit
	 * @param destination the destination of the transit
	 * @param failureSource the name of the involved component that caused the
	 *            transit to fail
	 * @param failureType a type of the reason the transit failed
	 * @param failureDescription a description of what caused the transit to
	 *            fail
	 */
	public static void notifyDataObjectTransitFailed(String sourceClassName, String dataId, String dataLabel, String source, String destination, String failureSource, String failureType, String failureDescription) {
		notifyDataObjectTransitFailed(sourceClassName, dataId, dataLabel, null, null, source, destination, failureSource, failureType, failureDescription);
	}
	
	/**
	 * Issue a notification about a failed data object transit.
	 * @param sourceClassName the class name of the server component that
	 *            attempted the transit
	 * @param dataId the ID of the data object
	 * @param dataLabel a label for the data object
	 * @param source the source of the transit
	 * @param destination the destination of the transit
	 * @param failureReasons one or more reasons the transit failed (each with
	 *            source and description)
	 */
	public static void notifyDataObjectTransitFailed(String sourceClassName, String dataId, String dataLabel, String source, String destination, TransitFailureReason[] failureReasons) {
		notifyDataObjectTransitFailed(sourceClassName, dataId, dataLabel, null, null, source, destination, failureReasons);
	}
	
	/**
	 * Issue a notification about a failed data object transit.
	 * @param sourceClassName the class name of the server component that
	 *            attempted the transit
	 * @param dataId the ID of the data object
	 * @param dataLabel a label for the data object
	 * @param detailId the ID of the detail within the data object that
	 *            actually attempted to transit
	 * @param detailLabel a label for the detail
	 * @param source the source of the transit
	 * @param destination the destination of the transit
	 * @param cause the exception that caused the transit to fail
	 */
	public static void notifyDataObjectTransitFailed(String sourceClassName, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination, TransitDeniedException cause) {
		notifyDataObjectTransitFailed(sourceClassName, dataId, dataLabel, detailId, detailLabel, source, destination, ((TransitDeniedException) cause).getReasons());
	}
	
	/**
	 * Issue a notification about a failed data object transit.
	 * @param sourceClassName the class name of the server component that
	 *            attempted the transit
	 * @param dataId the ID of the data object
	 * @param dataLabel a label for the data object
	 * @param detailId the ID of the detail within the data object that
	 *            actually attempted to transit
	 * @param detailLabel a label for the detail
	 * @param source the source of the transit
	 * @param destination the destination of the transit
	 * @param failureSource the name of the involved component that caused the
	 *            transit to fail
	 * @param failureType a type of the reason the transit failed
	 * @param cause the exception that caused the transit to fail
	 */
	public static void notifyDataObjectTransitFailed(String sourceClassName, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination, String failureSource, String failureType, Exception cause) {
		if (cause instanceof TransitDeniedException)
			notifyDataObjectTransitFailed(sourceClassName, dataId, dataLabel, detailId, detailLabel, source, destination, ((TransitDeniedException) cause).getReasons());
		else notifyDataObjectTransitFailed(sourceClassName, dataId, dataLabel, detailId, detailLabel, source, destination, failureSource, failureType, cause.getMessage());
	}
	
	/**
	 * Issue a notification about a failed data object transit.
	 * @param sourceClassName the class name of the server component that
	 *            attempted the transit
	 * @param dataId the ID of the data object
	 * @param dataLabel a label for the data object
	 * @param detailId the ID of the detail within the data object that
	 *            actually attempted to transit
	 * @param detailLabel a label for the detail
	 * @param source the source of the transit
	 * @param destination the destination of the transit
	 * @param failureSource the name of the involved component that caused the
	 *            transit to fail
	 * @param failureType a type of the reason the transit failed
	 * @param failureDescription a description of what caused the transit to
	 *            fail
	 */
	public static void notifyDataObjectTransitFailed(String sourceClassName, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination, String failureSource, String failureType, String failureDescription) {
		notifyDataObjectTransitEvent(new DataObjectTransitEvent(
				sourceClassName,
				dataId, dataLabel,
				detailId, detailLabel,
				source, destination,
				failureSource, failureType, failureDescription)
		);
	}
	
	/**
	 * Issue a notification about a failed data object transit.
	 * @param sourceClassName the class name of the server component that
	 *            attempted the transit
	 * @param dataId the ID of the data object
	 * @param dataLabel a label for the data object
	 * @param detailId the ID of the detail within the data object that
	 *            actually attempted to transit
	 * @param detailLabel a label for the detail
	 * @param source the source of the transit
	 * @param destination the destination of the transit
	 * @param failureReasons one or more reasons the transit failed (each with
	 *            source and description)
	 */
	public static void notifyDataObjectTransitFailed(String sourceClassName, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination, TransitFailureReason[] failureReasons) {
		notifyDataObjectTransitEvent(new DataObjectTransitEvent(
				sourceClassName,
				dataId, dataLabel,
				detailId, detailLabel,
				source, destination,
				failureReasons)
		);
	}
	
	/**
	 * Notify about a data object transit event.
	 * @param dote the data object transit event to notify about
	 */
	public static void notifyDataObjectTransitEvent(DataObjectTransitEvent dote) {
		GoldenGateServerEventService.notify(dote);
	}
	
	/**
	 * Add a data object transit event listener to the checkpoint so it
	 * receives notification of data object transits.
	 * @param dotel the data object transit event listener to add
	 */
	public static void addDataObjectTransitEventListener(DataObjectTransitEventListener dotel) {
		GoldenGateServerEventService.addServerEventListener(dotel);
	}
	
	/**
	 * Remove a data object transit listener from the checkpoint.
	 * @param dotel the data object transit event listener to remove
	 */
	public static void removeDataObjectTransitEventListener(DataObjectTransitEventListener dotel) {
		GoldenGateServerEventService.removeServerEventListener(dotel);
	}
	
	/**
	 * A single reason a data transit failed or was denied (there can be more
	 * than one).
	 * 
	 * @author sautter
	 */
	public static class TransitFailureReason {
		
		/** the source of the reason (e.g. the name of an inspector denying transit) */
		public final String source;
		
		/** the type of the reason (e.g. a typification of why an inspector denied transit) */
		public final String type;
		
		/** the description of the reason */
		public final String description;
		
		/**
		 * @param source the source of the reason
		 * @param type the type of the reason
		 * @param description the description of the reason
		 */
		public TransitFailureReason(String source, String type, String description) {
			this.source = ((source == null) ? "Generic" : source);
			this.type = ((type == null) ? "generic" : type);
			this.description = description;
		}
		public int hashCode() {
			return this.toString().hashCode();
		}
		public boolean equals(Object obj) {
			return ((obj instanceof TransitFailureReason) && this.toString().equals(obj.toString()));
		}
		public String toString() {
			if (this.toString == null)
				this.toString = (this.source + ":" + this.type + ":" + this.description);
			return this.toString;
		}
		private String toString;
		
		static TransitFailureReason parserFailureReason(String data) {
			String[] frd = data.split("\\:\\s?", 3);
			if (frd.length == 1)
				return new TransitFailureReason(null, null, data);
			else if (frd.length == 2)
				return new TransitFailureReason(frd[0], null, frd[1]);
			else return new TransitFailureReason(frd[0], frd[1], frd[2]);
		}
	}
	
	/**
	 * Exception to be thrown by <code>Guard</code>s to deny a data object
	 * transit to a requested destination. <code>Guard</code>s are strongly
	 * encouraged to include the reason for the rejection in the error message.
	 * 
	 * @author sautter
	 */
	public static class TransitDeniedException extends IOException {
		private TransitFailureReason[] reasons;
		
		/** Constructor
		 * @param reasons the reasons for denying transit
		 */
		TransitDeniedException(TransitFailureReason[] reasons) {
			super(reasons[0].toString());
			this.reasons = reasons;
		}
		
		/** Constructor
		 * @param source the source of the error message
		 * @param type the type of the error message
		 * @param message the reason for denying transit
		 */
		public TransitDeniedException(String source, String type, String message) {
			super(source + ":" + type + ":" + message);
			this.reasons = new TransitFailureReason[1];
			this.reasons[0] = new TransitFailureReason(source, type, message);
		}
		
		/** Constructor
		 * @param inspector the inspector denying transit
		 * @param type the type of the error message
		 * @param message the reason for denying transit
		 */
		public TransitDeniedException(Inspector inspector, String type, String message) {
			this(inspector.getName(), type, message);
		}
		
		/** Constructor
		 * @param source the source of the error message
		 * @param type the type of the error message
		 * @param message the reason for denying transit
		 * @param cause an underlying exception to wrap
		 */
		public TransitDeniedException(String source, String type, String message, Throwable cause) {
			super((source + ":" + type + ":" + message), cause);
			this.reasons = new TransitFailureReason[1];
			this.reasons[0] = new TransitFailureReason(source, type, message);
		}
		
		/** Constructor
		 * @param inspector the inspector denying transit
		 * @param type the type of the error message
		 * @param message the reason for denying transit
		 * @param cause an underlying exception to wrap
		 */
		public TransitDeniedException(Inspector inspector, String type, String message, Throwable cause) {
			this(inspector.getName(), type, message, cause);
		}
		
		/**
		 * Returns the error message, always <code>source+': '+message</code>,
		 * or the return value of the (first) reason's <code>toString()</code>
		 * method.
		 * @see java.lang.Throwable#getMessage()
		 */
		public String getMessage() {
			return super.getMessage();
		}
		
		/**
		 * Retrieve the reason(s) for a failed data transfer.
		 * @return an array holding the reasons
		 */
		public TransitFailureReason[] getReasons() {
			return Arrays.copyOf(this.reasons, this.reasons.length);
		}
	}
	
	/**
	 * Inspectors are the objects performing the actual transit permission checks.
	 * 
	 * @author sautter
	 */
	public static interface Inspector {
		
		/**
		 * Get the name of the inspector.
		 * @return the name of the inspector
		 */
		public abstract String getName();
		
		/**
		 * Check whether or not a data object or a detail thereof may transit from
		 * a given source to a specific destination. The <code>detailId</code> may
		 * be null; it is merely a means intended to facilitate fine-grained checks
		 * if multiple details of the same data object intend to transit, rather
		 * than the data object as a whole.
		 * @param dataId the ID of the data object intending to transit
		 * @param detailId the ID of the detail within the data object that will
		 *            actually transit (may be null)
		 * @param source the name of the source intending to push the data object
		 * @param destination the name of the destination the source intends to
		 *            push the data object to
		 * @throws TransitDeniedException to deny transit under conditions that
		 *            cannot be listed as reasons (e.g. internal exceptions)
		 */
		public abstract TransitProblem[] getTransitProblems(String dataId, String detailId, String source, String destination) throws TransitDeniedException;
		
		/**
		 * A single data transit problem, with a type and a free text
		 * description.
		 * 
		 * @author sautter
		 */
		public static class TransitProblem {
			
			/** the type of the problem (e.g. a typification of why an inspector denied transit) */
			public final String type;
			
			/** the description of the problem */
			public final String description;
			
			/**
			 * @param type the type of the problem
			 * @param description the description of the problem
			 */
			public TransitProblem(String type, String description) {
				this.type = type;
				this.description = description;
			}
			public int hashCode() {
				return this.toString().hashCode();
			}
			public boolean equals(Object obj) {
				return ((obj instanceof TransitProblem) && this.toString().equals(obj.toString()));
			}
			public String toString() {
				if (this.toString == null)
					this.toString = (this.type + ":" + this.description);
				return this.toString;
			}
			private String toString;
		}
	}
	
	private static DataObjectTransitAuthority instance;
	private static ArrayList inspectors = new ArrayList();
	
	/**
	 * Add an inspector to the checkpoint.
	 * @param inspector the inspector to add
	 */
	public static void addInspector(Inspector inspector) {
		if ((inspector == null) || inspectors.contains(inspector))
			return;
		inspectors.add(inspector);
	}
	
	/**
	 * Remove an inspector from the checkpoint.
	 * @param inspector the inspector to remove
	 */
	public static void removeInspector(Inspector inspector) {
		inspectors.remove(inspector);
	}
	
	/**
	 * Check whether or not a data object or a detail thereof may transit from
	 * a given source to a specific destination. The <code>detailId</code> may
	 * be null; it is merely a means intended to facilitate fine-grained checks
	 * if multiple details of the same data object intend to transit, rather
	 * than the data object as a whole.
	 * @param dataId the ID of the data object intending to transit
	 * @param detailId the ID of the detail within the data object that will
	 *            actually transit (may be null)
	 * @param source the name of the source intending to push the data object
	 * @param destination the name of the destination the source intends to
	 *            push the data object to
	 * @throws TransitDeniedException if a registered inspector denies transit
	 */
	public static void checkTransit(String dataId, String detailId, String source, String destination) throws TransitDeniedException {
		
		//	check blacklist and whitelist of own instance
		if (instance != null)
			instance.checkDatObjectTransit(dataId, detailId, source, destination);
		
		//	consult any registered inspectors
		LinkedHashSet transitDenialReasons = new LinkedHashSet();
		for (int i = 0; i < inspectors.size(); i++) {
			Inspector inspector = ((Inspector) inspectors.get(i));
			TransitProblem[] gtps = inspector.getTransitProblems(dataId, detailId, source, destination);
			if (gtps == null)
				continue;
			for (int p = 0; p < gtps.length; p++)
				transitDenialReasons.add(new TransitFailureReason(inspector.getName(), gtps[p].type, gtps[p].description));
		}
		if (transitDenialReasons.size() != 0)
			throw new TransitDeniedException((TransitFailureReason[]) transitDenialReasons.toArray(new TransitFailureReason[transitDenialReasons.size()]));
	}
	
	private static final String DATA_TRANSIT_BLACKLIST_TABLE_NAME = "DtaBlacklist";
	private static final String DATA_TRANSIT_WHITELIST_TABLE_NAME = "DtaWhitelist";
	private static final String DATA_ID_ATTRIBUTE = "dataId";
	private static final String DATA_ID_HASH_ATTRIBUTE = "dataIdHash";
	private static final String DATA_DETAIL_ID_ATTRIBUTE = "detailId";
	private static final String SOURCE_ATTRIBUTE = "source";
	private static final String DESTINATION_ATTRIBUTE = "destination";
	
	private Map transitBlacklistCache = Collections.synchronizedMap(new LinkedHashMap() {
		protected boolean removeEldestEntry(Entry eldest) {
			return (this.size() > 128);
		}
	});
	private Map transitWhitelistCache = Collections.synchronizedMap(new LinkedHashMap() {
		protected boolean removeEldestEntry(Entry eldest) {
			return (this.size() > 128);
		}
	});
	
	private IoProvider io;
	private TransitRuleListCacheCleaner cacheCleaner = null;
	
	private boolean scrutinyOff = false;
	
	/** zero argument constructor for class loading, handing 'DTA' to super class as the letter code */
	public DataObjectTransitAuthority() {
		super("DTA");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#initComponent()
	 */
	protected void initComponent() {
		
		//	connect to database
		this.io = this.host.getIoProvider();
		if ((this.io == null) || !this.io.isJdbcAvailable()) {
			this.io = null;
			return;
		}
		
		//	create blacklist table
		TableDefinition blTd = new TableDefinition(DATA_TRANSIT_BLACKLIST_TABLE_NAME);
		blTd.addColumn(DATA_ID_ATTRIBUTE, TableDefinition.VARCHAR_DATATYPE, 32);
		blTd.addColumn(DATA_ID_HASH_ATTRIBUTE, TableDefinition.INT_DATATYPE, 0);
		blTd.addColumn(DATA_DETAIL_ID_ATTRIBUTE, TableDefinition.VARCHAR_DATATYPE, 32);
		blTd.addColumn(SOURCE_ATTRIBUTE, TableDefinition.VARCHAR_DATATYPE, 16);
		blTd.addColumn(DESTINATION_ATTRIBUTE, TableDefinition.VARCHAR_DATATYPE, 16);
		if (!this.io.ensureTable(blTd, true)) {
			this.io = null;
			return;
		}
		
		//	index document identifiers
		this.io.indexColumn(DATA_TRANSIT_BLACKLIST_TABLE_NAME, DATA_ID_ATTRIBUTE);
		
		//	create whitelist table
		TableDefinition wlTd = new TableDefinition(DATA_TRANSIT_WHITELIST_TABLE_NAME);
		wlTd.addColumn(DATA_ID_ATTRIBUTE, TableDefinition.VARCHAR_DATATYPE, 32);
		wlTd.addColumn(DATA_ID_HASH_ATTRIBUTE, TableDefinition.INT_DATATYPE, 0);
		wlTd.addColumn(DATA_DETAIL_ID_ATTRIBUTE, TableDefinition.VARCHAR_DATATYPE, 32);
		wlTd.addColumn(SOURCE_ATTRIBUTE, TableDefinition.VARCHAR_DATATYPE, 16);
		wlTd.addColumn(DESTINATION_ATTRIBUTE, TableDefinition.VARCHAR_DATATYPE, 16);
		if (!this.io.ensureTable(wlTd, true)) {
			this.io = null;
			return;
		}
		
		//	index document identifiers
		this.io.indexColumn(DATA_TRANSIT_WHITELIST_TABLE_NAME, DATA_ID_ATTRIBUTE);
		
		//	start cache cleaner
		this.cacheCleaner = new TransitRuleListCacheCleaner();
		this.cacheCleaner.start();
		
		//	make ourselves available for blacklist/whitelist checks
		instance = this;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#exitComponent()
	 */
	protected void exitComponent() {
		
		//	close database connection
		if (this.io != null) {
			this.io.close();
			this.io = null;
		}
		
		//	shut down cache cleaner
		if (this.cacheCleaner != null)
			this.cacheCleaner.shutdown();
	}
	
	private static final String SCRUTINY_ON_COMMAND = "scrutinyOn";
	private static final String SCRUTINY_OFF_COMMAND = "scrutinyOff";
	private static final String WHITELIST_COMMAND = "whitelist";
	private static final String UN_WHITELIST_COMMAND = "unWhitelist";
	private static final String BLACKLIST_COMMAND = "blacklist";
	private static final String UN_BLACKLIST_COMMAND = "unBlacklist";
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#getActions()
	 */
	public ComponentAction[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentAction ca;
		
		//	offers action toggling guard on or off duty
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return SCRUTINY_ON_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						SCRUTINY_ON_COMMAND,
						"Activate scrutiny so data objects will be scrutinized before transiting."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0)
					scrutinyOff = false;
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return SCRUTINY_OFF_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						SCRUTINY_OFF_COMMAND,
						"Deactivate scrutiny so data objects will transit without scrutinizing."
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 0)
					scrutinyOff = true;
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify no arguments.");
			}
		};
		cal.add(ca);
		
		//	blacklisting and whitelisting only works with database
		if (this.io != null) {
			
			//	offers action telling guard to grant a specific data object transit (whitelist for urgent exports)
			ca = new ComponentActionConsole() {
				public String getActionCommand() {
					return WHITELIST_COMMAND;
				}
				public String[] getExplanation() {
					String[] explanation = {
							WHITELIST_COMMAND + " <dataId> <detailId> <source> <destination>",
							"Whitelist a document or detail for transit from a source to a destination:",
							"- <dataId>: the ID of the document to whitelist",
							"- <detailId>: the ID of the document detail to whitelist (use '*' as wildcard)",
							"- <source>: the name of the transit source to whitelist the document for (use '*' as wildcard)",
							"- <destination>: the name of the transit destination to whitelist the document for (use '*' as wildcard)"
						};
					return explanation;
				}
				public void performActionConsole(String[] arguments) {
					if (arguments.length == 4)
						addToTransitRuleList(this, arguments[0], arguments[1], arguments[2], arguments[3], DATA_TRANSIT_WHITELIST_TABLE_NAME, transitWhitelistCache);
					else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the document ID, detail ID, source, and destination (use '*' as wildcards for either of the latter three).");
				}
			};
			cal.add(ca);
			ca = new ComponentActionConsole() {
				public String getActionCommand() {
					return UN_WHITELIST_COMMAND;
				}
				public String[] getExplanation() {
					String[] explanation = {
							UN_WHITELIST_COMMAND + " <dataId> <detailId> <source> <destination>",
							"Un-whitelist a document or detail for transit from a source to a destination:",
							"- <dataId>: the ID of the document to un-whitelist",
							"- <detailId>: the ID of the document detail to un-whitelist (use '*' as wildcard, omit to un-whitelist all details)",
							"- <source>: the name of the transit source to un-whitelist the document for (use '*' as wildcard, omit to un-whitelist all sources)",
							"- <destination>: the name of the transit destination to un-whitelist the document for (use '*' as wildcard, omit to un-whitelist all destination)"
						};
					return explanation;
				}
				public void performActionConsole(String[] arguments) {
					if ((0 < arguments.length) && (arguments.length <= 4)) {
						String dataId = arguments[0];
						String detailId = ((arguments.length < 2) ? null : arguments[1]);
						String source = ((arguments.length < 3) ? null : arguments[2]);
						String destination = ((arguments.length < 4) ? null : arguments[3]);
						removeFromTransitRuleList(this, dataId, detailId, source, destination, DATA_TRANSIT_WHITELIST_TABLE_NAME, transitWhitelistCache);
					}
					else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the document ID, ond optionally detail ID, source, and destination.");
				}
			};
			cal.add(ca);
			
			//	offers action telling guard to deny a specific data object transit (blacklist, also for testing)
			ca = new ComponentActionConsole() {
				public String getActionCommand() {
					return BLACKLIST_COMMAND;
				}
				public String[] getExplanation() {
					String[] explanation = {
							BLACKLIST_COMMAND + " <docId> <mode>",
							"Blacklist a document or detail for transit from a source to a destination:",
							"- <dataId>: the ID of the document to blacklist",
							"- <detailId>: the ID of the document detail to blacklist (use '*' as wildcard)",
							"- <source>: the name of the transit source to blacklist the document for (use '*' as wildcard)",
							"- <destination>: the name of the transit destination to blacklist the document for (use '*' as wildcard)"
						};
					return explanation;
				}
				public void performActionConsole(String[] arguments) {
					if (arguments.length == 4)
						addToTransitRuleList(this, arguments[0], arguments[1], arguments[2], arguments[3], DATA_TRANSIT_BLACKLIST_TABLE_NAME, transitBlacklistCache);
					else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the document ID, detail ID, source, and destination (use '*' as wildcards for either of the latter three).");
				}
			};
			cal.add(ca);
			ca = new ComponentActionConsole() {
				public String getActionCommand() {
					return UN_BLACKLIST_COMMAND;
				}
				public String[] getExplanation() {
					String[] explanation = {
							UN_BLACKLIST_COMMAND + " <dataId> <detailId> <source> <destination>",
							"Un-blacklist a document or detail for transit from a source to a destination:",
							"- <dataId>: the ID of the document to un-blacklist",
							"- <detailId>: the ID of the document detail to un-blacklist (use '*' as wildcard, omit to un-blacklist all details)",
							"- <source>: the name of the transit source to un-blacklist the document for (use '*' as wildcard, omit to un-blacklist all sources)",
							"- <destination>: the name of the transit destination to un-blacklist the document for (use '*' as wildcard, omit to un-blacklist all destination)"
						};
					return explanation;
				}
				public void performActionConsole(String[] arguments) {
					if ((0 < arguments.length) && (arguments.length <= 4)) {
						String dataId = arguments[0];
						String detailId = ((arguments.length < 2) ? null : arguments[1]);
						String source = ((arguments.length < 3) ? null : arguments[2]);
						String destination = ((arguments.length < 4) ? null : arguments[3]);
						removeFromTransitRuleList(this, dataId, detailId, source, destination, DATA_TRANSIT_BLACKLIST_TABLE_NAME, transitBlacklistCache);
					}
					else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the document ID, ond optionally detail ID, source, and destination.");
				}
			};
			cal.add(ca);
		}
		
		//	finally ...
		return ((ComponentAction[]) cal.toArray(new ComponentAction[cal.size()]));
	}
	
	private static class TransitRuleList {
		long lastUsed = System.currentTimeMillis();
		private TreeSet transits = new TreeSet(String.CASE_INSENSITIVE_ORDER);
		private HashMap detailTransits = new HashMap();
		TransitRuleList() {}
		void dispose() {
			this.transits.clear();
			this.detailTransits.clear();
		}
		synchronized boolean contains(String detailId, String source, String destination) {
			this.lastUsed = System.currentTimeMillis();
			TreeSet transits = (((detailId == null) || (detailId.length() == 0) || "*".equals(detailId)) ? this.transits : ((TreeSet) this.detailTransits.get(detailId)));
			if (transits == null)
				return false;
			if (transits.contains(source + ">" + destination))
				return true;
			if (transits.contains("*" + ">" + destination))
				return true;
			if (transits.contains(source + ">" + "*"))
				return true;
			if (transits.contains("*" + ">" + "*"))
				return true;
			return false;
		}
		synchronized void add(String detailId, String source, String destination) {
			this.lastUsed = System.currentTimeMillis();
			if ((detailId == null) || (detailId.length() == 0) || "*".equals(detailId))
				this.transits.add(source + ">" + destination);
			else {
				TreeSet detailTransits = ((TreeSet) this.detailTransits.get(detailId));
				if (detailTransits == null) {
					detailTransits = new TreeSet(String.CASE_INSENSITIVE_ORDER);
					this.detailTransits.put(detailId, detailTransits);
				}
				detailTransits.add(source + ">" + destination);
			}
		}
	}
	
	private TransitRuleList getTransitRuleList(String dataId, String tableName, Map cacheMap) {
		if (this.io == null)
			return null;
		TransitRuleList trl = ((TransitRuleList) cacheMap.get(dataId));
		if (trl != null)
			return trl;
		synchronized (cacheMap) {
			trl = ((TransitRuleList) cacheMap.get(dataId));
			if (trl != null)
				return trl;
			String query = "SELECT " + DATA_DETAIL_ID_ATTRIBUTE + ", " + SOURCE_ATTRIBUTE + ", " + DESTINATION_ATTRIBUTE +
					" FROM " + tableName +
					" WHERE " + DATA_ID_ATTRIBUTE + " = '" + EasyIO.sqlEscape(dataId) + "' AND " + DATA_ID_HASH_ATTRIBUTE + " = " + dataId.hashCode() + "" +
					";";
			SqlQueryResult sqr = null;
			try {
				sqr = this.io.executeSelectQuery(query);
				while (sqr.next()) {
					if (trl == null) // prevent instantiating empty list
						trl = new TransitRuleList();
					String detailId = sqr.getString(0);
					String source = sqr.getString(1);
					String destination = sqr.getString(2);
					trl.add(detailId, source, destination);
				}
				/* In this specific case, calling a synchronized method from
				 * within a code block synchronized on a different object does
				 * not bear the risk of a deadlock because the guard list can
				 * only exist as a local reference at this point, so no other
				 * thread has any way of concurrently acquiring a lock on it */
				if (trl != null)
					cacheMap.put(dataId, trl);
			}
			catch (SQLException sqle) {
				this.logError("GoldenGateEPH: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while listing error protocols.");
				this.logError("  query was " + query);
			}
			finally {
				if (sqr != null)
					sqr.close();
			}
		}
		return trl;
	}
	
	private void addToTransitRuleList(ComponentActionConsole cac, String dataId, String detailId, String source, String destination, String tableName, Map cacheMap) {
		if ((detailId == null) || (detailId.trim().length() == 0))
			detailId = "*";
		TransitRuleList trl = this.getTransitRuleList(dataId, tableName, cacheMap);
		if ((trl != null) && trl.contains(detailId, source, destination)) {
			cac.reportResult(" ==> already contained");
			return;
		}
		String query = "INSERT INTO " + tableName +
				" (" + DATA_ID_ATTRIBUTE + ", " + DATA_ID_HASH_ATTRIBUTE + ", " + DATA_DETAIL_ID_ATTRIBUTE + ", " + SOURCE_ATTRIBUTE + ", " + DESTINATION_ATTRIBUTE + ")" +
				" VALUES" +
				" ('" + EasyIO.sqlEscape(dataId) + "', " + dataId.hashCode() + ", '" + EasyIO.sqlEscape(detailId) + "', '" + EasyIO.sqlEscape(source) + "', '" + EasyIO.sqlEscape(destination) + "')" +
				";";
		try {
			this.io.executeUpdateQuery(query);
			if (trl != null)
				trl.add(detailId, source, destination);
			cac.reportResult(" ==> added successfully");
		}
		catch (SQLException sqle) {
			this.logError("GoldenGateEphDTC: Error adding rule for document '" + dataId + "': " + sqle.getMessage());
			this.logError("  query was " + query);
		}
	}
	
	private void removeFromTransitRuleList(ComponentActionConsole cac, String dataId, String detailId, String source, String destination, String tableName, Map cacheMap) {
		if ((detailId != null) && (detailId.trim().length() == 0))
			detailId = null;
		String query = "DELETE FROM " + tableName +
				" WHERE " + DATA_ID_ATTRIBUTE + " = '" + EasyIO.sqlEscape(dataId) + "'" +
				" AND " + DATA_ID_HASH_ATTRIBUTE + " = " + dataId.hashCode() +
				" AND " + ((detailId == null) ? "1=1" : (DATA_DETAIL_ID_ATTRIBUTE + " = '" + EasyIO.sqlEscape(detailId) + "'")) +
				" AND " + ((source == null) ? "1=1" : (SOURCE_ATTRIBUTE + " = '" + EasyIO.sqlEscape(source) + "'")) +
				" AND " + ((destination == null) ? "1=1" : (DESTINATION_ATTRIBUTE + " = '" + EasyIO.sqlEscape(destination) + "'")) +
				";";
		try {
			int deleted = this.io.executeUpdateQuery(query);
			if (deleted != 0)
				cacheMap.remove(dataId);
		}
		catch (SQLException sqle) {
			this.logError("GoldenGateEphDTC: Error adding rule for document '" + dataId + "': " + sqle.getMessage());
			this.logError("  query was " + query);
		}
	}
	
	void checkDatObjectTransit(String dataId, String detailId, String source, String destination) throws TransitDeniedException {
		
		//	we're off duty ...
		if (this.scrutinyOff)
			return;
		
		//	check whitelist first
		TransitRuleList whiteList = this.getTransitRuleList(dataId, DATA_TRANSIT_WHITELIST_TABLE_NAME, this.transitWhitelistCache);
		if ((whiteList != null) && (detailId != null) && whiteList.contains(detailId, source, destination))
			return;
		
		//	check blacklist
		TransitRuleList blackList = this.getTransitRuleList(dataId, DATA_TRANSIT_BLACKLIST_TABLE_NAME, this.transitBlacklistCache);
		if ((blackList != null) && (detailId != null) && blackList.contains(detailId, source, destination))
			throw new TransitDeniedException("TransitAuthority", "blacklisted/detail", ("Detail '" + detailId + "' of data object '" + dataId + "' is blacklisted for transits from " + source + " to " + destination + "."));
		
		//	check for whole-document entries
		if ((whiteList != null) && whiteList.contains(null, source, destination))
			return;
		if ((blackList != null) && blackList.contains(null, source, destination))
			throw new TransitDeniedException("TransitAuthority", "blacklisted/object", ("Data object '" + dataId + "' is blacklisted for transits from " + source + " to " + destination + "."));
	}
	
	private class TransitRuleListCacheCleaner extends Thread {
		private boolean run = true;
		private long cleanupDueTime;
		TransitRuleListCacheCleaner() {
			super("DtaCacheCleaner");
		}
		public void run() {
			
			//	add ourselves to monitoring
			AsynchronousWorkQueue awq = new AsynchronousWorkQueue(this.getName()) {
				public String getStatus() {
					return (this.name + ": cleanup due in " + (cleanupDueTime - System.currentTimeMillis()) + "ms, cached are " + transitBlacklistCache.size() + " blacklists and " + transitWhitelistCache.size() + " whitelists");
				}
			};
			
			//	do the work
			while (this.run) {
				
				//	sleep for some 10 minutes
				long time = System.currentTimeMillis();
				this.cleanupDueTime = (time + (1000 * 60 * 10));
				while (time < this.cleanupDueTime) try {
					sleep(this.cleanupDueTime - time);
					break; // sleep ended normally, no need to loop
				}
				catch (InterruptedException ie) {
					if (this.run) // start over sleeping if we're supposed to continue
						time = System.currentTimeMillis();
					else break; // return immediately on shutdown
				}
				
				//	clean up everything not used in the past hour
				long staleIfLastUsedBefore = (System.currentTimeMillis() - (1000 * 60 * 60));
				
				//	clean stale entries in blacklist cache
				if (this.run && (transitBlacklistCache.size() != 0))
					synchronized (transitBlacklistCache) {
						ArrayList keys = new ArrayList(transitBlacklistCache.keySet());
						for (int k = 0; this.run && (k < keys.size()); k++) {
							Object key = keys.get(k);
							TransitRuleList trl = ((TransitRuleList) transitBlacklistCache.get(key));
							if ((trl != null) && (trl.lastUsed < staleIfLastUsedBefore)) {
								transitBlacklistCache.remove(key);
								trl.dispose();
							}
						}
					}
				
				//	clean stale entries in whitelist cache
				if (this.run && (transitWhitelistCache.size() != 0))
					synchronized (transitWhitelistCache) {
						ArrayList keys = new ArrayList(transitWhitelistCache.keySet());
						for (int k = 0; this.run && (k < keys.size()); k++) {
							Object key = keys.get(k);
							TransitRuleList trl = ((TransitRuleList) transitWhitelistCache.get(key));
							if ((trl != null) && (trl.lastUsed < staleIfLastUsedBefore)) {
								transitWhitelistCache.remove(key);
								trl.dispose();
							}
						}
					}
			}
			
			//	clean up
			awq.dispose();
		}
		synchronized void shutdown() {
			this.run = false;
			this.interrupt();
		}
	}
}
