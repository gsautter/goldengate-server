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
package de.uka.ipd.idaho.goldenGateServer.uaa;

/**
 * A user data provider can augment the functionality of the user access
 * authority with additional information on users.
 * 
 * @author sautter
 */
public interface UserDataProvider {
	
	/**
	 * Retrieve a property of a user. If the specified user does not exist or
	 * does not have the requested property, this method returns null. There is
	 * no guarantee that users do not change their properties, so components
	 * using these properties should re-get them every time they use them, or at
	 * least periodically.
	 * @param user the name of the user
	 * @param name the name of the property
	 * @return the value of the requested property.
	 */
	public abstract String getUserProperty(String user, String name);
	
	/**
	 * Retrieve a property of a user. If the specified user does not exist or
	 * does not have the requested property, this method returns the specified
	 * default value. There is no guarantee that users do not change their
	 * properties, so components using these properties should re-get them every
	 * time they use them, or at least periodically.
	 * @param user the name of the user
	 * @param name the name of the property
	 * @param def a default return value
	 * @return the value of the requested property.
	 */
	public abstract String getUserProperty(String user, String name, String def);
}
