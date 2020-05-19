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

import java.util.Iterator;
import java.util.Map;

import de.uka.ipd.idaho.easyIO.util.JsonParser;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants;

/**
 * Constant bearer for GoldenGATE External Notification Receiver (ENR).
 * 
 * @author sautter
 */
public interface GoldenGateEnrConstants extends GoldenGateServerConstants {
	
	/**
	 * Type of notification to a receiver registered in GoldenGATE ENR. Names
	 * of notification types have to be valid as a single step in a URL path,
	 * preferably alphanumeric with a letter at the start.
	 * 
	 * @author sautter
	 */
	public static abstract class NotificationType {
		
		/** the name of the notification type */
		public final String name;
		
		/** the permission required to send notifications of this type (may be null) */
		public final String permission;
		
		//	TODO add maximum content size to avoid data swamping?
		
		/** Constructor
		 * @param name the name of the type
		 */
		protected NotificationType(String name) {
			this(name, null);
		}
		
		/** Constructor
		 * @param name the name of the type
		 * @param permission the required permission (if any)
		 */
		public NotificationType(String name, String permission) {
			this.name = name;
			this.permission = permission;
		}

		/**
		 * Retrieve a snippet of JSON describing the notification type, most
		 * likely a serialized object or array. Simple object properties
		 * (strings, numbers, booleans) should map to their data type (as a
		 * string), array elements should be mocked with explanatory strings.
		 * Optional properties and entries should have their explanatory values
		 * or entries in parentheses. For instance:<br/>
		 * <code><pre>
		 * {
		 *   'name': 'personDataUpdate',
		 *   'permission': 'UpdatePersonData',
		 *   'data': {
		 *     'identifier': 'number',
		 *     'lastName': 'string',
		 *     'firstName': 'string',
		 *     'middleName': '(string)',
		 *     'gender': '(string)',
		 *     'age': '(number)',
		 *     'married': '(boolean)'
		 *   }
		 * }</pre></code> describes a notification type for changes to personal
		 * data, with mandatory numeric identifier and last and first name
		 * strings, optional middle name and gender strings, an optional numeric
		 * age, and an optional marriage status flag.
		 * @return a JSON snippet describing the notification type
		 */
		public String toJson(boolean includePermission) {
			StringBuffer json = new StringBuffer();
			json.append("{\r\n");
			json.append("\"name\": \"" + JsonParser.escape(this.name, '"') + "\",\r\n");
			if (includePermission && (this.permission != null))
				json.append("\"permission\": \"" + JsonParser.escape(this.permission, '"') + "\",\r\n");
			json.append("\"data\": {\r\n");
			Map props = this.getDataPropertyDescriptions();
			for (Iterator pit = props.keySet().iterator(); pit.hasNext();) {
				String pn = ((String) pit.next());
				String pvd = ((String) props.get(pn));
				json.append("  \"" + JsonParser.escape(pn, '"') + "\": \"" + JsonParser.escape(pvd, '"') + "\"");
				if (pit.hasNext())
					json.append(",");
				json.append("\r\n");
			}
			json.append("  }\r\n");
			json.append("}");
			return json.toString();
		}
		
		/**
		 * Retrieve a map of property names to descriptive values describing the
		 * contents of the data portion of the notification type. The toJson()
		 * method takes care of syntax and escaping.
		 * @return a Map of property descriptions
		 * @see NotificationType#toJson();
		 */
		protected abstract Map getDataPropertyDescriptions();
	}
	
	
	/**
	 * Notification to a receiver registered in GoldenGATE ENR. The body data
	 * can be any of the JSON data types of Map (object), List (array), String,
	 * Number, Boolean, or null for an empty body. It is up to sub classes to
	 * extract and convert the data they need.
	 * 
	 * @author sautter
	 */
	public static class Notification {
		
		/** the type of the notification */
		public final NotificationType type;
		
		/** the portion of the notification URL path after the leading type */
		public final String pathInfo;
		
		/** the name of the user who sent the notification */
		public final String userName;
		
		/** the JSON body of the notification */
		public final Object data;
		
		/**
		 * @param type the notification type
		 * @param pathInfo the URL part following after the endpoint name (type)
		 * @param userName the name of the user who sent the notification
		 * @param data the notification body data
		 */
		public Notification(NotificationType type, String pathInfo, String userName, Object data) {
			this.type = type;
			this.pathInfo = pathInfo;
			this.userName = userName;
			this.data = data;
		}
	}
	
	/** the command for retrieving the notification types registered with the backing ENR */
	public static final String GET_NOTIFICATION_TYPES = "ENR_GET_NOTIFICATION_TYPES";
	
	/** the command for passing a notification through to the backing ENR */
	public static final String RECEIVE_NOTIFICATION = "ENR_RECEIVE_NOTIFICATION";
}
