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


import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * The central singleton registry for server components to find eachother, even
 * though each component is loaded and registered individually. This registry
 * offers an easy way for server components to interact if they are running in
 * the same Jave Virtual Machine. Each server component is also registered with
 * all its super classes that are assignable from GoldenGateServerComponent.
 * This facilitates finding components that are actually sub classes of the
 * looked-up class.
 * 
 * @author sautter
 */
public class GoldenGateServerComponentRegistry {
	
	private static HashMap registry = new HashMap();
	
	/**
	 * Register a server component to the registry (key is the component's class
	 * name).
	 * @param serverComponent the server component to register
	 */
	public static void registerServerComponent(GoldenGateServerComponent serverComponent) {
//		if (serverComponent != null)
//			registry.put(serverComponent.getClass().getName(), serverComponent);
		if (serverComponent == null) return;
		
		Class scClass = serverComponent.getClass();
		while (GoldenGateServerComponent.class.isAssignableFrom(scClass)) {
			LinkedHashSet scSet = ((LinkedHashSet) registry.get(scClass.getName()));
			if (scSet == null) {
				scSet = new LinkedHashSet();
				registry.put(scClass.getName(), scSet);
			}
			scSet.add(serverComponent);
			scClass = scClass.getSuperclass();
		}
	}
	
	/**
	 * Remove a server component from the registry (key is the component's class
	 * name),
	 * @param serverComponent the server component to remove
	 */
	public static void unregisterServerComponent(GoldenGateServerComponent serverComponent) {
//		if (serverComponent != null)
//			registry.remove(serverComponent.getClass().getName());
		if (serverComponent == null) return;
		
		Class scClass = serverComponent.getClass();
		while (GoldenGateServerComponent.class.isAssignableFrom(scClass)) {
			LinkedHashSet scSet = ((LinkedHashSet) registry.get(scClass.getName()));
			if (scSet != null) {
				scSet.remove(serverComponent);
				if (scSet.isEmpty())
					registry.remove(scClass.getName());
			}
			scClass = scClass.getSuperclass();
		}
	}
	
	/**
	 * Retrieve a server component from the registry. This method returns the
	 * first component found for the specified class name, thus the first
	 * element of the array that would be returned by the getServerComponents()
	 * method for the same argument.
	 * @param className the desired component's class name
	 * @return the first server component registered to the specified class name, or
	 *         null, if there is no such server component
	 */
	public static GoldenGateServerComponent getServerComponent(String className) {
//		return ((GoldenGateServerComponent) registry.get(className));
		GoldenGateServerComponent[] scs = getServerComponents(className);
		return ((scs.length == 0) ? null : scs[0]);
	}
	
	/**
	 * Retrieve all server components from the registry that are instances of a
	 * given class. If no components are found, this method returns an empty
	 * array, but never null.
	 * @param className the desired components' class name
	 * @return the server component registered to the specified class name, or
	 *         null, if there is no such server component
	 */
	public static GoldenGateServerComponent[] getServerComponents(String className) {
		LinkedHashSet scSet = ((LinkedHashSet) registry.get(className));
		return ((scSet == null) ? new GoldenGateServerComponent[0] : ((GoldenGateServerComponent[]) scSet.toArray(new GoldenGateServerComponent[scSet.size()])));
	}
	
	/**
	 * Retrieve all server components registered.
	 * @return an array holding all server components currently registered
	 */
	public static GoldenGateServerComponent[] getServerComponents() {
//		ArrayList serverComponentList = new ArrayList(registry.values());
//		return ((GoldenGateServerComponent[]) serverComponentList.toArray(new GoldenGateServerComponent[serverComponentList.size()]));
		LinkedHashSet scSet = new LinkedHashSet();
		for (Iterator scit = registry.values().iterator(); scit.hasNext();)
			scSet.addAll((LinkedHashSet) scit.next());
		return ((GoldenGateServerComponent[]) scSet.toArray(new GoldenGateServerComponent[scSet.size()]));
	}
}
