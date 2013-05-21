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

import de.uka.ipd.idaho.easyIO.IoProvider;

/**
 * Host object for GoldenGATE server components. The main purpose of this
 * interface is to provide IO provider objects to the components so they have
 * access to the server's database.
 * 
 * @author sautter
 */
public interface GoldenGateServerComponentHost {
	
	/**
	 * Retrieve an IoProvider for accessing the database shared among all
	 * components.
	 * @return an IoProvider configured to access the shared database
	 */
	public abstract IoProvider getIoProvider();
	
	/**
	 * Check whether the current request is proxied, i.e. whether it is coming
	 * through a proxy servlet. This propery is based on the executing service
	 * thread.
	 * @return true if the current request is proxied
	 */
	public abstract boolean isRequestProxied();
	
	/**
	 * Retrieve a global property of the GoldenGATE server environment. There is
	 * no guarantee that these properties are not changed through respective
	 * administrative facilities, so components using the properties should
	 * re-get them every time they use them, or at least periodically.
	 * @param name the name of the property
	 * @return the requested property, or null, if the property is not set
	 */
	public abstract String getServerProperty(String name);
	
	/**
	 * Retrieve a server component, e.g. from the registry. This method returns
	 * the first component found for the specified class name, thus the first
	 * element of the array that would be returned by the getServerComponents()
	 * method for the same argument.
	 * @param className the desired component's class name
	 * @return the first server component registered to the specified class
	 *         name, or null, if there is no such server component
	 */
	public abstract GoldenGateServerComponent getServerComponent(String className);
	
	/**
	 * Retrieve all server components, e.g. from the registry, that are
	 * instances of a given class. If no components are found, this method
	 * returns an empty array, but never null.
	 * @param className the desired components' class name
	 * @return the server component registered to the specified class name, or
	 *         null, if there is no such server component
	 */
	public abstract GoldenGateServerComponent[] getServerComponents(String className);
	
	/**
	 * Retrieve all server components registered.
	 * @return an array holding all server components currently registered
	 */
	public abstract GoldenGateServerComponent[] getServerComponents();
}
