///*
// * Copyright (c) 2006-, IPD Boehm, Universitaet Karlsruhe (TH) / KIT, by Guido Sautter
// * All rights reserved.
// *
// * Redistribution and use in source and binary forms, with or without
// * modification, are permitted provided that the following conditions are met:
// *
// *     * Redistributions of source code must retain the above copyright
// *       notice, this list of conditions and the following disclaimer.
// *     * Redistributions in binary form must reproduce the above copyright
// *       notice, this list of conditions and the following disclaimer in the
// *       documentation and/or other materials provided with the distribution.
// *     * Neither the name of the Universitaet Karlsruhe (TH) / KIT nor the
// *       names of its contributors may be used to endorse or promote products
// *       derived from this software without specific prior written permission.
// *
// * THIS SOFTWARE IS PROVIDED BY UNIVERSITAET KARLSRUHE (TH) / KIT AND CONTRIBUTORS 
// * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
// * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// * ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
// * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
// */
//package de.uka.ipd.idaho.goldenGateServer.util;
//
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.LinkedHashSet;
//
//import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants.GoldenGateServerEvent;
//import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerEventService;
//import de.uka.ipd.idaho.goldenGateServer.util.DataObjectTransitCheckpoint.DataObjectTransitEvent.DataObjectTransitEventListener;
//import de.uka.ipd.idaho.goldenGateServer.util.DataObjectTransitCheckpoint.Guard.TransitProblem;
//
///**
// * Central checkpoint for data objects to pass when they transit either between
// * two server components or from a server component to a remote destination. It
// * is the responsibility of the source component to consult the checkpoint for
// * permission before they push a data object to its destination. Note that this
// * is <b>not</b> a mandatory check, though, but rather a centralized, unified
// * means of restricting data flow on an object by object basis where the need
// * arises. Note further that this checkpoint is <b>not</b> intended for access
// * permission checks, which depend upon a user account, but rather for checks
// * concerning only the data objects proper.<br/>
// * Actual checks are performed by registered <code>Guard</code>s, which throw
// * <code>TransitDeniedException</code>s for data objects that are blocked from
// * transit to the requested destination.<br/>
// * Furthermore, this class acts as a notification hub for data object transit
// * events, reporting successful or failed transits to registered listeners.
// * 
// * @author sautter
// */
//public class DataObjectTransitCheckpoint {
//	//	TODOne rename to DataObjectTransitAuthority
//	/* 
//MAYBE ALSO: make authorities static in general ...
//... with default behavior and checks by registered extensions
//- NetworkAccessAuthority for session management
//  - register UserAccessAuthority as extension
//  - register ApiAccessAuthority as extension
//  - default: reject login
//  ==> will require new request syntax with parameters next to action command
//- NetworkPermissionAuthority for permission management
//  - register UserPermissionAuthority as extension
//  - default: default argument
//- DataAccessAuthority for per-data-object access rights
//  - register (to-be-built-some-day) DataAccessChecker as extension
//  - default: grant access
//==> easier to extend
//==> yet more graceful failing
//==> fewer non-core dependencies
//  ==> less linking effort
//==> bundle those adapted authorities in dedicated package
//
//
//ALSO: Maintain glyph reference database server side:
//- export glyphs from each font to database ...
//- ... including transcript
//==> at least try this out
//	 */
//	
//	private DataObjectTransitCheckpoint() { /* no instances */ }
//	
//	/**
//	 * Event notifying about a data object transfer, or a failure of one.
//	 * 
//	 * @author sautter
//	 */
//	public static class DataObjectTransitEvent extends GoldenGateServerEvent {
//		public static final int SUCCESS_TYPE = 1;
//		public static final int FAILURE_TYPE = 2;
//		
//		/**
//		 * Specialized listener for data object transit events, receiving
//		 * notifications of successful or failed transits.
//		 * 
//		 * @author sautter
//		 */
//		public static abstract class DataObjectTransitEventListener extends GoldenGateServerEventListener {
//			public void notify(GoldenGateServerEvent gse) {
//				if (gse instanceof DataObjectTransitEvent) {
//					DataObjectTransitEvent dote = ((DataObjectTransitEvent) gse);
//					if (dote.type == SUCCESS_TYPE)
//						this.dataObjectTransitSucceeded(dote);
//					else if (dote.type == FAILURE_TYPE)
//						this.dataObjectTransitFailed(dote);
//				}
//			}
//			
//			/**
//			 * Receive notification that a data object (or detail contained in
//			 * it) successfully transited from some source to some destination.
//			 * @param dote the DataObjectTransitEvent providing details on the
//			 *            transit
//			 */
//			public abstract void dataObjectTransitSucceeded(DataObjectTransitEvent dote);
//			
//			/**
//			 * Receive notification that the transit of a data object (or a
//			 * detail contained in it) from some source to some destination
//			 * failed.
//			 * @param dote the DataObjectTransitEvent providing details on the
//			 *            transit and the reasons it failed
//			 */
//			public abstract void dataObjectTransitFailed(DataObjectTransitEvent dote);
//		}
//		
//		/** The ID of the transiting data object */
//		public final String dataId;
//		
//		/** A label for the transiting data object, e.g. for displaying in a UI */
//		public final String dataLabel;
//		
//		/** The ID of the transiting part inside the main data object (if
//		 * different from data object proper) */
//		public final String detailId;
//		
//		/** A label for the transiting part inside the main data object, e.g.
//		 * for displaying in a UI (obsolete if <code>detailId</code>) is null */
//		public final String detailLabel;
//		
//		/** The code of the source of the transit */
//		public final String source;
//		
//		/** The code of the destination of the transit */
//		public final String destination;
//		
//		/** An array holding the reasons a data object transit failed (null for
//		 * successful transits) */
//		public final TransitFailureReason[] failureReasons;
//		
//		/** Constructor for success events
//		 * @param sourceClassName the class name of the component issuing the event
//		 * @param dataId the ID of the data object that transited
//		 * @param dataLabel a label for the data object that transited
//		 * @param detailId the ID of the data object detail that actually transited
//		 * @param detailLabel a label for the data object detail that actually transited
//		 * @param source the source of the transit
//		 * @param destination the destination of the transit
//		 */
//		public DataObjectTransitEvent(String sourceClassName, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination) {
//			this(SUCCESS_TYPE, sourceClassName, dataId, dataLabel, detailId, detailLabel, source, destination, null);
//		}
//		
//		/** Constructor for failure events
//		 * @param sourceClassName the class name of the component issuing the event
//		 * @param dataId the ID of the data object whose transit failed
//		 * @param dataLabel a label for the data object whose transit failed
//		 * @param detailId the ID of the data object detail that actually attempted to transit
//		 * @param detailLabel a label for the data object detail that actually attempted to transit
//		 * @param source the source of the transit
//		 * @param destination the destination of the transit
//		 * @param failureSource the name of the involved pat that failed the transit
//		 * @param failureType a type of the reason the transit failed
//		 * @param failureDescription a description of the reason the transit failed
//		 */
//		public DataObjectTransitEvent(String sourceClassName, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination, String failureSource, String failureType, String failureDescription) {
//			this(sourceClassName, dataId, dataLabel, detailId, detailLabel, source, destination, wrapReason(failureSource, failureType, failureDescription));
//		}
//		private static TransitFailureReason[] wrapReason(String source, String type, String description) {
//			if (description == null)
//				return null;
//			TransitFailureReason[] reasons = {
//				new TransitFailureReason(source, type, description)
//			};
//			return reasons;
//		}
//		
//		/** Constructor for failure events
//		 * @param sourceClassName the class name of the component issuing the event
//		 * @param dataId the ID of the data object whose transit failed
//		 * @param dataLabel a label for the data object whose transit failed
//		 * @param detailId the ID of the data object detail that actually attempted to transit
//		 * @param detailLabel a label for the data object detail that actually attempted to transit
//		 * @param source the source of the transit
//		 * @param destination the destination of the transit
//		 * @param failureReasons the reasons the transit failed
//		 */
//		public DataObjectTransitEvent(String sourceClassName, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination, TransitFailureReason[] failureReasons) {
//			this(((failureReasons == null) ? SUCCESS_TYPE : FAILURE_TYPE), sourceClassName, dataId, dataLabel, detailId, detailLabel, source, destination, failureReasons);
//		}
//		
//		private DataObjectTransitEvent(int type, String sourceClassName, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination, TransitFailureReason[] failureReasons) {
//			this(type, sourceClassName, -1, null, dataId, dataLabel, detailId, detailLabel, source, destination, failureReasons);
//		}
//		
//		private DataObjectTransitEvent(int type, String sourceClassName, long eventTime, String eventId, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination, TransitFailureReason[] failureReasons) {
//			super(type, false, sourceClassName, eventTime, eventId, null);
//			this.dataId = dataId;
//			this.dataLabel = dataLabel;
//			this.detailId = detailId;
//			this.detailLabel = detailLabel;
//			this.source = source;
//			this.destination = destination;
//			this.failureReasons = failureReasons;
//		}
//		
//		public String getParameterString() {
//			String failureReasons = "";
//			if (this.failureReasons != null) {
//				StringBuffer frs = new StringBuffer();
//				for (int r = 0; r < this.failureReasons.length; r++) {
//					if (r != 0)
//						frs.append(" ");
//					frs.append(encodeParameter(this.failureReasons[r].toString()));
//				}
//				failureReasons = encodeParameter(frs.toString());
//			}
//			return (super.getParameterString()
//					+ " " + this.dataId
//					+ " " + ((this.dataLabel == null) ? "" : encodeParameter(this.dataLabel))
//					+ " " + ((this.detailId == null) ? "" : this.detailId)
//					+ " " + ((this.detailLabel == null) ? "" : encodeParameter(this.detailLabel))
//					+ " " + encodeParameter(this.source)
//					+ " " + encodeParameter(this.destination)
//					+ " " + failureReasons);
//		}
//		
//		/**
//		 * Parse a transit event from its string representation returned by the
//		 * getParameterString() method.
//		 * @param data the string to parse
//		 * @return a transit event created from the specified data
//		 */
//		public static DataObjectTransitEvent parseEvent(String data) {
//			String[] dataItems = data.split("\\s");
//			TransitFailureReason[] failureReasons;
//			if ("".equals(dataItems[10]))
//				failureReasons = null;
//			else {
//				String[] frs = decodeParameter(dataItems[10]).split("\\s");
//				failureReasons = new TransitFailureReason[frs.length];
//				for (int r = 0; r < frs.length; r++) {
//					String fr = decodeParameter(frs[r]);
//					String[] frd = fr.split("\\:\\s?", 3);
//					if (frd.length == 1)
//						failureReasons[r] = new TransitFailureReason(null, null, fr);
//					else if (frd.length == 2)
//						failureReasons[r] = new TransitFailureReason(frd[0], null, frd[1]);
//					else failureReasons[r] = new TransitFailureReason(frd[0], frd[1], frd[2]);
//				}
//			}
//			return new DataObjectTransitEvent(Integer.parseInt(dataItems[0]), dataItems[1], Long.parseLong(dataItems[2]), dataItems[3],
//					dataItems[4],
//					("".equals(dataItems[5]) ? null : decodeParameter(dataItems[5])),
//					("".equals(dataItems[6]) ? null : dataItems[6]),
//					("".equals(dataItems[7]) ? null : decodeParameter(dataItems[7])),
//					("".equals(dataItems[8]) ? null : decodeParameter(dataItems[8])),
//					("".equals(dataItems[9]) ? null : decodeParameter(dataItems[9])),
//					failureReasons);
//		}
//	}
//	
//	/**
//	 * Issue a notification about a successful data object transit.
//	 * @param sourceClassName the class name of the server component that did
//	 *            the transit
//	 * @param dataId the ID of the data object
//	 * @param dataLabel a label for the data object
//	 * @param source the source of the transit
//	 * @param destination the destination of the transit
//	 */
//	public static void notifyDataObjectTransitSuccessful(String sourceClassName, String dataId, String dataLabel, String source, String destination) {
//		notifyDataObjectTransitSuccessful(sourceClassName, dataId, dataLabel, null, null, source, destination);
//	}
//	
//	/**
//	 * Issue a notification about a successful data object transit.
//	 * @param sourceClassName the class name of the server component that did
//	 *            the transit
//	 * @param dataId the ID of the data object
//	 * @param dataLabel a label for the data object
//	 * @param detailId the ID of the detail within the data object that
//	 *            actually transited
//	 * @param detailLabel a label for the detail
//	 * @param source the source of the transit
//	 * @param destination the destination of the transit
//	 */
//	public static void notifyDataObjectTransitSuccessful(String sourceClassName, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination) {
//		DataObjectTransitCheckpoint.notifyDataObjectTransitEvent(new DataObjectTransitEvent(
//				sourceClassName,
//				dataId, dataLabel,
//				detailId, detailLabel,
//				source, destination));
//	}
//	
//	/**
//	 * Issue a notification about a failed data object transit.
//	 * @param sourceClassName the class name of the server component that
//	 *            attempted the transit
//	 * @param dataId the ID of the data object
//	 * @param dataLabel a label for the data object
//	 * @param source the source of the transit
//	 * @param destination the destination of the transit
//	 * @param cause the exception that caused the transit to fail
//	 */
//	public static void notifyDataObjectTransitFailed(String sourceClassName, String dataId, String dataLabel, String source, String destination, TransitDeniedException cause) {
//		notifyDataObjectTransitFailed(sourceClassName, dataId, dataLabel, null, null, source, destination, cause);
//	}
//	
//	/**
//	 * Issue a notification about a failed data object transit.
//	 * @param sourceClassName the class name of the server component that
//	 *            attempted the transit
//	 * @param dataId the ID of the data object
//	 * @param dataLabel a label for the data object
//	 * @param source the source of the transit
//	 * @param destination the destination of the transit
//	 * @param failureSource the name of the involved component that caused the
//	 *            transit to fail
//	 * @param failureType a type of the reason the transit failed
//	 * @param cause the exception that caused the transit to fail
//	 */
//	public static void notifyDataObjectTransitFailed(String sourceClassName, String dataId, String dataLabel, String source, String destination, String failureSource, String failureType, Exception cause) {
//		notifyDataObjectTransitFailed(sourceClassName, dataId, dataLabel, null, null, source, destination, failureSource, failureType, cause);
//	}
//	
//	/**
//	 * Issue a notification about a failed data object transit.
//	 * @param sourceClassName the class name of the server component that
//	 *            attempted the transit
//	 * @param dataId the ID of the data object
//	 * @param dataLabel a label for the data object
//	 * @param source the source of the transit
//	 * @param destination the destination of the transit
//	 * @param failureSource the name of the involved component that caused the
//	 *            transit to fail
//	 * @param failureType a type of the reason the transit failed
//	 * @param failureDescription a description of what caused the transit to
//	 *            fail
//	 */
//	public static void notifyDataObjectTransitFailed(String sourceClassName, String dataId, String dataLabel, String source, String destination, String failureSource, String failureType, String failureDescription) {
//		notifyDataObjectTransitFailed(sourceClassName, dataId, dataLabel, null, null, source, destination, failureSource, failureType, failureDescription);
//	}
//	
//	/**
//	 * Issue a notification about a failed data object transit.
//	 * @param sourceClassName the class name of the server component that
//	 *            attempted the transit
//	 * @param dataId the ID of the data object
//	 * @param dataLabel a label for the data object
//	 * @param source the source of the transit
//	 * @param destination the destination of the transit
//	 * @param failureReasons one or more reasons the transit failed (each with
//	 *            source and description)
//	 */
//	public static void notifyDataObjectTransitFailed(String sourceClassName, String dataId, String dataLabel, String source, String destination, TransitFailureReason[] failureReasons) {
//		notifyDataObjectTransitFailed(sourceClassName, dataId, dataLabel, null, null, source, destination, failureReasons);
//	}
//	
//	/**
//	 * Issue a notification about a failed data object transit.
//	 * @param sourceClassName the class name of the server component that
//	 *            attempted the transit
//	 * @param dataId the ID of the data object
//	 * @param dataLabel a label for the data object
//	 * @param detailId the ID of the detail within the data object that
//	 *            actually attempted to transit
//	 * @param detailLabel a label for the detail
//	 * @param source the source of the transit
//	 * @param destination the destination of the transit
//	 * @param cause the exception that caused the transit to fail
//	 */
//	public static void notifyDataObjectTransitFailed(String sourceClassName, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination, TransitDeniedException cause) {
//		DataObjectTransitCheckpoint.notifyDataObjectTransitFailed(sourceClassName, dataId, dataLabel, detailId, detailLabel, source, destination, ((TransitDeniedException) cause).getReasons());
//	}
//	
//	/**
//	 * Issue a notification about a failed data object transit.
//	 * @param sourceClassName the class name of the server component that
//	 *            attempted the transit
//	 * @param dataId the ID of the data object
//	 * @param dataLabel a label for the data object
//	 * @param detailId the ID of the detail within the data object that
//	 *            actually attempted to transit
//	 * @param detailLabel a label for the detail
//	 * @param source the source of the transit
//	 * @param destination the destination of the transit
//	 * @param failureSource the name of the involved component that caused the
//	 *            transit to fail
//	 * @param failureType a type of the reason the transit failed
//	 * @param cause the exception that caused the transit to fail
//	 */
//	public static void notifyDataObjectTransitFailed(String sourceClassName, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination, String failureSource, String failureType, Exception cause) {
//		if (cause instanceof TransitDeniedException)
//			DataObjectTransitCheckpoint.notifyDataObjectTransitFailed(sourceClassName, dataId, dataLabel, detailId, detailLabel, source, destination, ((TransitDeniedException) cause).getReasons());
//		else notifyDataObjectTransitFailed(sourceClassName, dataId, dataLabel, detailId, detailLabel, source, destination, failureSource, failureType, cause.getMessage());
//	}
//	
//	/**
//	 * Issue a notification about a failed data object transit.
//	 * @param sourceClassName the class name of the server component that
//	 *            attempted the transit
//	 * @param dataId the ID of the data object
//	 * @param dataLabel a label for the data object
//	 * @param detailId the ID of the detail within the data object that
//	 *            actually attempted to transit
//	 * @param detailLabel a label for the detail
//	 * @param source the source of the transit
//	 * @param destination the destination of the transit
//	 * @param failureSource the name of the involved component that caused the
//	 *            transit to fail
//	 * @param failureType a type of the reason the transit failed
//	 * @param failureDescription a description of what caused the transit to
//	 *            fail
//	 */
//	public static void notifyDataObjectTransitFailed(String sourceClassName, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination, String failureSource, String failureType, String failureDescription) {
//		DataObjectTransitCheckpoint.notifyDataObjectTransitEvent(new DataObjectTransitEvent(
//				sourceClassName,
//				dataId, dataLabel,
//				detailId, detailLabel,
//				source, destination,
//				failureSource, failureType, failureDescription)
//		);
//	}
//	
//	/**
//	 * Issue a notification about a failed data object transit.
//	 * @param sourceClassName the class name of the server component that
//	 *            attempted the transit
//	 * @param dataId the ID of the data object
//	 * @param dataLabel a label for the data object
//	 * @param detailId the ID of the detail within the data object that
//	 *            actually attempted to transit
//	 * @param detailLabel a label for the detail
//	 * @param source the source of the transit
//	 * @param destination the destination of the transit
//	 * @param failureReasons one or more reasons the transit failed (each with
//	 *            source and description)
//	 */
//	public static void notifyDataObjectTransitFailed(String sourceClassName, String dataId, String dataLabel, String detailId, String detailLabel, String source, String destination, TransitFailureReason[] failureReasons) {
//		DataObjectTransitCheckpoint.notifyDataObjectTransitEvent(new DataObjectTransitEvent(
//				sourceClassName,
//				dataId, dataLabel,
//				detailId, detailLabel,
//				source, destination,
//				failureReasons)
//		);
//	}
//	
//	/**
//	 * Notify about a data object transit event.
//	 * @param dote the data object transit event to notify about
//	 */
//	public static void notifyDataObjectTransitEvent(DataObjectTransitEvent dote) {
//		GoldenGateServerEventService.notify(dote);
//	}
//	
//	/**
//	 * Add a data object transit event listener to the checkpoint so it
//	 * receives notification of data object transits.
//	 * @param dotel the data object transit event listener to add
//	 */
//	public static void addDataObjectTransitEventListener(DataObjectTransitEventListener dotel) {
//		GoldenGateServerEventService.addServerEventListener(dotel);
//	}
//	
//	/**
//	 * Remove a data object transit listener from the checkpoint.
//	 * @param dotel the data object transit event listener to remove
//	 */
//	public static void removeDataObjectTransitEventListener(DataObjectTransitEventListener dotel) {
//		GoldenGateServerEventService.removeServerEventListener(dotel);
//	}
//	
//	/**
//	 * A single reason a data transit failed or was denied (there can be more
//	 * than one).
//	 * 
//	 * @author sautter
//	 */
//	public static class TransitFailureReason {
//		
//		/** the source of the reason (e.g. the name of a guard denying transit) */
//		public final String source;
//		
//		/** the type of the reason (e.g. a typification of why a guard denied transit) */
//		public final String type;
//		
//		/** the description of the reason */
//		public final String description;
//		
//		/**
//		 * @param source the source of the reason
//		 * @param type the type of the reason
//		 * @param description the description of the reason
//		 */
//		public TransitFailureReason(String source, String type, String description) {
//			this.source = ((source == null) ? "Generic" : source);
//			this.type = ((type == null) ? "generic" : type);
//			this.description = description;
//		}
//		public int hashCode() {
//			return this.toString().hashCode();
//		}
//		public boolean equals(Object obj) {
//			return ((obj instanceof TransitFailureReason) && this.toString().equals(obj.toString()));
//		}
//		public String toString() {
//			if (this.toString == null)
//				this.toString = (this.source + ":" + this.type + ":" + this.description);
//			return this.toString;
//		}
//		private String toString;
//	}
//	
//	/**
//	 * Exception to be thrown by <code>Guard</code>s to deny a data object
//	 * transit to a requested destination. <code>Guard</code>s are strongly
//	 * encouraged to include the reason for the rejection in the error message.
//	 * 
//	 * @author sautter
//	 */
//	public static class TransitDeniedException extends IOException {
//		private TransitFailureReason[] reasons;
//		
//		/** Constructor
//		 * @param reasons the reasons for denying transit
//		 */
//		TransitDeniedException(TransitFailureReason[] reasons) {
//			super(reasons[0].toString());
//			this.reasons = reasons;
//		}
//		
//		/** Constructor
//		 * @param source the source of the error message
//		 * @param type the type of the error message
//		 * @param message the reason for denying transit
//		 */
//		public TransitDeniedException(String source, String type, String message) {
//			super(source + ":" + type + ":" + message);
//			this.reasons = new TransitFailureReason[1];
//			this.reasons[0] = new TransitFailureReason(source, type, message);
//		}
//		
//		/** Constructor
//		 * @param guard the guard denying transit
//		 * @param type the type of the error message
//		 * @param message the reason for denying transit
//		 */
//		public TransitDeniedException(Guard guard, String type, String message) {
//			this(guard.getName(), type, message);
//		}
//		
//		/** Constructor
//		 * @param source the source of the error message
//		 * @param type the type of the error message
//		 * @param message the reason for denying transit
//		 * @param cause an underlying exception to wrap
//		 */
//		public TransitDeniedException(String source, String type, String message, Throwable cause) {
//			super((source + ":" + type + ":" + message), cause);
//			this.reasons = new TransitFailureReason[1];
//			this.reasons[0] = new TransitFailureReason(source, type, message);
//		}
//		
//		/** Constructor
//		 * @param guard the guard denying transit
//		 * @param type the type of the error message
//		 * @param message the reason for denying transit
//		 * @param cause an underlying exception to wrap
//		 */
//		public TransitDeniedException(Guard guard, String type, String message, Throwable cause) {
//			this(guard.getName(), type, message, cause);
//		}
//		
//		/**
//		 * Returns the error message, always <code>source+': '+message</code>,
//		 * or the return value of the (first) reason's <code>toString()</code>
//		 * method.
//		 * @see java.lang.Throwable#getMessage()
//		 */
//		public String getMessage() {
//			return super.getMessage();
//		}
//		
//		/**
//		 * Retrieve the reason(s) for a failed data transfer.
//		 * @return an array holding the reasons
//		 */
//		public TransitFailureReason[] getReasons() {
//			return Arrays.copyOf(this.reasons, this.reasons.length);
//		}
//	}
//	
//	/**
//	 * Guards are the objects performing the actual transit permission checks.
//	 * 
//	 * @author sautter
//	 */
//	public static interface Guard {
//		
//		/**
//		 * Get the name of the guard.
//		 * @return the name of the guard
//		 */
//		public abstract String getName();
//		
//		/**
//		 * Check whether or not a data object or a detail thereof may transit from
//		 * a given source to a specific destination. The <code>detailId</code> may
//		 * be null; it is merely a means intended to facilitate fine-grained checks
//		 * if multiple details of the same data object intend to transit, rather
//		 * than the data object as a whole.
//		 * @param dataId the ID of the data object intending to transit
//		 * @param detailId the ID of the detail within the data object that will
//		 *            actually transit (may be null)
//		 * @param source the name of the source intending to push the data object
//		 * @param destination the name of the destination the source intends to
//		 *            push the data object to
//		 * @throws TransitDeniedException to deny transit under conditions that
//		 *            cannot be listed as reasons (e.g. internal exceptions)
//		 */
//		public abstract TransitProblem[] getTransitProblems(String dataId, String detailId, String source, String destination) throws TransitDeniedException;
//		
//		/**
//		 * A single data transit problem, with a type and a free text
//		 * description.
//		 * 
//		 * @author sautter
//		 */
//		public static class TransitProblem {
//			
//			/** the type of the problem (e.g. a typification of why a guard denied transit) */
//			public final String type;
//			
//			/** the description of the problem */
//			public final String description;
//			
//			/**
//			 * @param type the type of the problem
//			 * @param description the description of the problem
//			 */
//			public TransitProblem(String type, String description) {
//				this.type = type;
//				this.description = description;
//			}
//		}
//	}
//	
//	/**
//	 * Add a guard to the checkpoint.
//	 * @param guard the guard to add
//	 */
//	public static void addGuard(Guard guard) {
//		if ((guard == null) || guards.contains(guard))
//			return;
//		guards.add(guard);
//	}
//	
//	/**
//	 * Remove a guard from the checkpoint.
//	 * @param guard the guard to remove
//	 */
//	public static void removeGuard(Guard guard) {
//		guards.remove(guard);
//	}
//	
//	/**
//	 * Check whether or not a data object or a detail thereof may transit from
//	 * a given source to a specific destination. The <code>detailId</code> may
//	 * be null; it is merely a means intended to facilitate fine-grained checks
//	 * if multiple details of the same data object intend to transit, rather
//	 * than the data object as a whole.
//	 * @param dataId the ID of the data object intending to transit
//	 * @param detailId the ID of the detail within the data object that will
//	 *            actually transit (may be null)
//	 * @param source the name of the source intending to push the data object
//	 * @param destination the name of the destination the source intends to
//	 *            push the data object to
//	 * @throws TransitDeniedException if a registered guard denies transit
//	 */
//	public static void checkTransit(String dataId, String detailId, String source, String destination) throws TransitDeniedException {
//		LinkedHashSet transitDenialReasons = new LinkedHashSet();
//		for (int g = 0; g < guards.size(); g++) {
//			Guard guard = ((Guard) guards.get(g));
//			TransitProblem[] gtps = guard.getTransitProblems(dataId, detailId, source, destination);
//			if (gtps == null)
//				continue;
//			for (int p = 0; p < gtps.length; p++)
//				transitDenialReasons.add(new TransitFailureReason(guard.getName(), gtps[p].type, gtps[p].description));
//		}
//		if (transitDenialReasons.size() != 0)
//			throw new TransitDeniedException((TransitFailureReason[]) transitDenialReasons.toArray(new TransitFailureReason[transitDenialReasons.size()]));
//	}
//	
//	private static ArrayList guards = new ArrayList();
//}
