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
 *     * Neither the name of the Universitaet Karlsruhe (TH) nor the
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
package de.uka.ipd.idaho.goldenGateServer;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Modifier;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;




/**
 * Interface holding constants for GoldenGATE Component Server
 * 
 * @author sautter
 */
public interface GoldenGateServerConstants {
	
	/** The standard encoding for data transfer*/
	public static final String ENCODING = "UTF-8";
	
	/** The name of the sub folder to the root path where server components will be searched for in jar files */
	public static final String COMPONENT_FOLDER_NAME = "Components";
	
	/**
	 * Generic super class of all events that occur inside GoldenGATE Server.
	 * 
	 * @author sautter
	 */
	public class GoldenGateServerEvent {
		/*
TODO Use same approach in distributed GG Server (with direct network connections):
- pull generic server events via long polls ...
- ... de-serializing them via same mechanism ...
- ... and maybe sending list of relevant class names ...
- ... on each poll or periodically
==> merely cuts out persisting, wrapping, and periodic poll
==> implement in GgREC (Remote Event Connector) or GgREF (Remote Event Fetcher)
  ==> REC better fit, as both pulling and serving
- hold on to last 10 minutes or so of events ...
- ... as well as what requester they already went to
  ==> allows after-fact notification of checkouts, releases, etc. across instance reboots
- also hold on to last 10 minutes or so of received events
  ==> helps prevent duplicate notifications

TODO Distributed document checkout:
- set checkout user on remote checkout ...
- ... and release document on remote release
  ==> above buffering should effectively prevent double checkout ...
  ==> ... unless we have a split-brain situation ...
  ==> ... and extremely bad luck
  ==> still, local versioning should facilitate recovery ...
  ==> ... especially with emerging markup merging functionality
		 */
		
		/**
		 * Generic listener for GoldenGATE Server Events. All listeners for
		 * more specific events should extend this class and implement the
		 * <code>notify()</code> method to dispatch events to their more
		 * specific methods.
		 * 
		 * @author sautter
		 */
		public static abstract class GoldenGateServerEventListener {
			
			/**
			 * Notify the listener of a generic GoldenGATE Server Event.
			 * Listeners for more specific events should implement this
			 * method to dispatch events to their more specific methods.
			 * @param gse the event to issue the notification for
			 */
			public abstract void notify(GoldenGateServerEvent gse);
		}
		
		/**
		 * Factory object for reconstructing event instances from their JSON
		 * and parameter string representations.
		 * 
		 * @author sautter
		 */
		protected static interface EventFactory {
			
			/**
			 * Reconstruct an event from its JSON representation. If the
			 * argument JSON object does not provide all the required data,
			 * implementations of this method should return null.
			 * @param json the JSON representation of the event
			 * @return the event
			 */
			public abstract GoldenGateServerEvent getEvent(Map json);
			
			/**
			 * Reconstruct an event from its parameter string representation.
			 * If the argument string does not provide all the required data,
			 * implementations of this method should return null.
			 * @param className the name of the event class.
			 * @param paramString the parameter string representation of the
			 *        event
			 * @return the event
			 * @deprecated code for handling legacy data, use JSON
			 */
			public abstract GoldenGateServerEvent getEvent(String className, String paramString);
		}
		
		/**
		 * Reconstruct an event from its JSON representation. This method
		 * is a centralized access point to all the registered
		 * <code>EventFactory</code> instances responsible for reconstructing
		 * instances of specific sub classes. If no factory is present for
		 * the event class specified in the data, this method returns null.
		 * @param json the JSON representation of the event
		 * @return the event
		 */
		public static GoldenGateServerEvent getEvent(Map json) {
			for (Iterator fit = factories.iterator(); fit.hasNext();) {
				EventFactory factory = ((EventFactory) fit.next());
				GoldenGateServerEvent gse = factory.getEvent(json);
				if (gse != null)
					return gse;
			}
			//	TODO reconstruct generically ???
			return null;
		}
		
		/**
		 * Reconstruct an event from its parameter string representation. This
		 * method is a centralized access point to all the registered
		 * <code>EventFactory</code> instances responsible for reconstructing
		 * instances of specific sub classes. If no factory is present for
		 * the argument event class name, this method returns null.
		 * @param className the name of the event class.
		 * @param paramString the parameter string representation of the
		 *        event
		 * @return the event
		 * @deprecated code for handling legacy data, use JSON
		 */
		public static GoldenGateServerEvent getEvent(String className, String paramString) {
			for (Iterator fit = factories.iterator(); fit.hasNext();) {
				EventFactory factory = ((EventFactory) fit.next());
				GoldenGateServerEvent gse = factory.getEvent(className, paramString);
				if (gse != null)
					return gse;
			}
			//	TODO reconstruct generically ???
			return null;
		}
		
		private static LinkedHashSet factories = new LinkedHashSet();
		
		/**
		 * Add a factory for reconstructing serialized instances of specific
		 * sub classes. Sub classes providing a serialized representation of
		 * their instances via the <code>toJsonObject</code> method should
		 * register a corresponding factory on loading, best from a static
		 * initializer.
		 * @param factory the factory to register
		 */
		protected static void addFactory(EventFactory factory) {
			if (factory == null)
				return;
			if (factories.add(factory))
				System.out.println("GoldenGateServerEvent: got factory " + factory.getClass().getName());
		}
		
		/**
		 * Interface for collecting log messages when reacting to an event
		 * 
		 * @author sautter
		 */
		public static interface EventLogger {
			
			/**
			 * Write a log entry providing detail information on actions taken
			 * in reaction to an event.
			 * @param logEntry the log entry to write
			 */
			public abstract void writeLog(String logEntry);
		}
		
		private EventLogger log = null;
		
		/** The class name of the component that issued the event */
		public final String sourceClassName;
		
		/** the event type, used for dispatching */
		public final int type;
		
		/** Is this event a high-priority one? */
		public final boolean isHighPriority;
		
		/** the time when the event happened */
		public final long eventTime;
		
		/**
		 * the ID of the event (defaults to a concatenation of source class
		 * name and event time; consists of letters, digits, dashes and
		 * underscores only, no whitespace in particular)
		 */
		public final String eventId;
		
		/**
		 * Constructor
		 * @param type the event type, used for dispatching
		 * @param sourceClassName the class name of the component issuing the event
		 * @param logger an EventLogger to collect log messages while the event is
		 *            being processed in listeners
		 */
		public GoldenGateServerEvent(int type, String sourceClassName, EventLogger logger) {
			this(type, false, sourceClassName, -1, null, logger);
		}
		
		/**
		 * Constructor
		 * @param type the event type, used for dispatching
		 * @param highPriority is this a high-priority event?
		 * @param sourceClassName the class name of the component issuing the event
		 * @param logger an EventLogger to collect log messages while the event is
		 *            being processed in listeners
		 */
		public GoldenGateServerEvent(int type, boolean highPriority, String sourceClassName, EventLogger logger) {
			this(type, highPriority, sourceClassName, -1, null, logger);
		}
		
		/**
		 * Constructor
		 * @param type the event type, used for dispatching
		 * @param sourceClassName the class name of the component issuing the
		 *            event
		 * @param eventTime the time when the event happened
		 * @param eventId the ID of the event (defaults to a concatenation of
		 *            source class name and event time; must consist of letters,
		 *            digits, dashes and underscores only, no whitespace in
		 *            particular)
		 * @param logger an EventLogger to collect log messages while the event
		 *            is being processed in listeners
		 */
		public GoldenGateServerEvent(int type, String sourceClassName, long eventTime, String eventId, EventLogger logger) {
			this(type, false, sourceClassName, eventTime, eventId, logger);
		}
		
		/**
		 * Constructor
		 * @param type the event type, used for dispatching
		 * @param highPriority is this a high-priority event?
		 * @param sourceClassName the class name of the component issuing the
		 *            event
		 * @param eventTime the time when the event happened
		 * @param eventId the ID of the event (defaults to a concatenation of
		 *            source class name and event time; must consist of letters,
		 *            digits, dashes and underscores only, no whitespace in
		 *            particular)
		 * @param logger an EventLogger to collect log messages while the event
		 *            is being processed in listeners
		 */
		public GoldenGateServerEvent(int type, boolean highPriority, String sourceClassName, long eventTime, String eventId, EventLogger logger) {
			this.type = type;
			this.isHighPriority = highPriority;
			this.sourceClassName = sourceClassName;
			this.eventTime = ((eventTime < 1) ? System.currentTimeMillis() : eventTime);
			this.eventId = ((eventId == null) ? (sourceClassName + "-" + this.eventTime) : eventId);
			this.log = logger;
		}
		
		/**
		 * Write a log entry, telling something about the actions taken in
		 * reaction to this event.
		 * @param logEntry the log entry to write
		 */
		public void writeLog(String logEntry) {
			if (this.log != null)
				this.log.writeLog(logEntry);
			//	TODO maybe retro-fit this to use activity logger ...
			//	TODO ... abandoning local event logger altogether
			//	==> no more anonymous event logger objects required ...
			//	==> ... while offering full range of log levels
		}
		
		/**
		 * Convert this event into a space separated string representation for
		 * easy storage and transfer. Sub classes should overwrite this method
		 * to append their specific parameters. Sub classes that include
		 * references to non-basic data types (primitive types and strings)
		 * should at least include an ID for the referenced object to facilitate
		 * later reconstruction.<br>
		 * This implementation returns the type, source class name, event time,
		 * and event ID, in this order.
		 * @return a string representation of the event
		 * @deprecated use <code>toJsonObject()</code> instead
		 */
		public String getParameterString() {
			return (this.type + " " + this.sourceClassName + " " + this.eventTime + " " + this.eventId);
			//	TODO also offer XML based IO ...
			//	TODO ... using attribute names in line with JSON property names
			//	==> yet more flexibility in API
		}
		
		/**
		 * Convert this event into a JSON object representation for easy
		 * storage and transfer. Sub classes should overwrite this method
		 * to add their specific parameters to the map. Sub classes that
		 * include references to non-basic data types (primitive types and
		 * strings) should at least include an ID for the referenced object
		 * to facilitate later reconstruction via a factory. Further, sub
		 * classes should replace the <code>eventClass</code> property with
		 * their own class name to facilitate factory selection, as well as
		 * register a factory for reconstructing event objects from their JSON
		 * representation.<br>
		 * This implementation returns a map containing the type, source class
		 * name, event time, and event ID.
		 * @return a JSON object representation of the event
		 */
		public Map toJsonObject() {
			LinkedHashMap json = new LinkedHashMap();
			Class eventClass = this.getClass();
			while (eventClass != null) {
				if (eventClass.getName().matches(".*\\$[0-9]+")) {} // anonymous sub class, move to parent
				else if (Modifier.isAbstract(eventClass.getModifiers())) {} // abstract sub classes can exist
				else if (Modifier.isInterface(eventClass.getModifiers())) {} // should not happen, but let's be safe
				else if (!Modifier.isPublic(eventClass.getModifiers())) {} // private named sub classes can exist
				else break; // we can use this one
				if (GoldenGateServerEvent.class.isAssignableFrom(eventClass.getSuperclass()))
					eventClass = eventClass.getSuperclass();
				else eventClass = null; // WTF ... how did we get past GoldenGateServerEvent root class ???
			}
			if ((eventClass != null) && !eventClass.equals(this.getClass()))
				System.out.println("GoldenGateServerEvent: mapped " + this.getClass().getName() + " to " + eventClass.getName());
			json.put("eventClass", ((eventClass == null) ? GoldenGateServerEvent.class.getName() : eventClass.getName()));
			json.put("eventType", new Integer(this.type));
			json.put("sourceClass", this.sourceClassName);
			json.put("eventTime", new Long(this.eventTime));
			json.put("eventId", this.eventId);
			if (this.isHighPriority)
				json.put("highPriority", Boolean.TRUE);
			return json;
		}
		
		/**
		 * Perform any actions required after notification of this event has
		 * completed. This method is called right before event notification
		 * returns, independent of whether or not it terminated normally or
		 * with an exception. This default implementation does nothing, sub
		 * classes re welcome to overwrite it as needed.
		 */
		public void notificationComplete() {}
		
		/*
		 * stop the logging to prevent components handling the event asynchronously
		 * from wasting memory (used by GoldenGateServerEventQueue after synchronous
		 * notification is complete)
		 */
		void closeLog() {
			this.log = null;
		}
		
		/**
		 * URL-encode a parameter for the parameter string. This method uses
		 * URLEncoder, but handles the UnsupportedEncodingException internally.
		 * @param paramString the parameter to encode
		 * @return the encoded parameter
		 */
		public static final String encodeParameter(String paramString) {
			if (paramString == null)
				return null;
			try {
				return URLEncoder.encode(paramString, ENCODING);
			}
			catch (UnsupportedEncodingException uee) {
				return paramString;
				// will never happen as UTF-8 is supported, but Java don't know ...
			}
		}
		
		/**
		 * URL-decode a parameter from the parameter string. This method uses
		 * URLDecoder, but handles the UnsupportedEncodingException internally.
		 * @param paramString the parameter to decode
		 * @return the decoded parameter
		 */
		public static final String decodeParameter(String paramString) {
			if (paramString == null)
				return null;
			try {
				return URLDecoder.decode(paramString, ENCODING);
			}
			catch (UnsupportedEncodingException uee) {
				return paramString;
				// will never happen as UTF-8 is supported, but Java don't know ...
			}
		}
	}
}
