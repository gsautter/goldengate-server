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
package de.uka.ipd.idaho.goldenGateServer;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;




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
		
		/**
		 * Generic listener for GoldenGATE Server Events. All listeners for more
		 * specirfic events should extend this class and implement the notify() method
		 * to dispatch events to their more specific methods.
		 * 
		 * @author sautter
		 */
		public static abstract class GoldenGateServerEventListener {
			
			/**
			 * Notify the listener of a generic GoldenGATE Server Event. Listeners for
			 * more specific events should implement this method to dispatch events to
			 * their more methods.
			 * @param gse the event to issue the notification for
			 */
			public abstract void notify(GoldenGateServerEvent gse);
		}
		
		/**
		 * Interface for collecting log messages when reacting to an event
		 * 
		 * @author sautter
		 */
		public interface EventLogger {
			
			/**
			 * Write a log entry providing detail information on actions taken in
			 * reaction to an event.
			 * @param logEntry the log entry to write
			 */
			public abstract void writeLog(String logEntry);
		}
		
		private EventLogger log = null;
		
		/**
		 * The class name of the component that issued the event
		 */
		public final String sourceClassName;
		
		/** the event type, used for dispatching */
		public final int type;
		
		/** the time when the event happened */
		public final long eventTime;
		
		/**
		 * the ID of the event (defaults to a concatenation of source class name
		 * and event time; consists of letters, digits, dashes and underscores
		 * only, no whitespace in particular)
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
			this(type, sourceClassName, -1, null, logger);
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
			this.type = type;
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
		 */
		public String getParameterString() {
			return (this.type + " " + this.sourceClassName + " " + this.eventTime + " " + this.eventId);
		}
		
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
			try {
				return URLEncoder.encode(paramString, ENCODING);
			}
			catch (UnsupportedEncodingException e) {
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
			try {
				return URLDecoder.decode(paramString, ENCODING);
			}
			catch (UnsupportedEncodingException e) {
				return paramString;
				// will never happen as UTF-8 is supported, but Java don't know ...
			}
		}
	}
}
