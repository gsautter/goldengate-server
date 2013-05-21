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


import java.io.File;

import de.uka.ipd.idaho.gamta.util.GamtaClassLoader;
import de.uka.ipd.idaho.gamta.util.GamtaClassLoader.ComponentInitializer;

/**
 * Loader utility for creating server component instances
 * 
 * @author sautter
 */
public class GoldenGateServerComponentLoader {
	
	/**
	 * load and instantiate server components from the jar files residing in
	 * some folder. The data path for each component is set to
	 * '&lt;componentFolder&gt;/&lt;componentJarName&gt;Data', where
	 * &lt;componentFolder&gt; is the specified folder and
	 * &lt;componentJarName&gt; is the name of the jar file the component was
	 * loaded from, without the '.jar' file extension. If a server component
	 * requires extra jars, place them in
	 * '&lt;componentFolder&gt;/&lt;componentJarName&gt;Bin', where
	 * &lt;componentFolder&gt; again is the specified folder and
	 * &lt;componentJarName&gt; again is the name of the jar file the component
	 * was loaded from, again without the '.jar' file extension.<br>
	 * <br>
	 * Example: Suppose the component folder is
	 * 'D:/GoldenGateServer/Components', and the server component MyComponent
	 * resides in MyComponentJar.jar, then this component will have
	 * 'D:/GoldenGateServer/Components/MyComponentJarData' as its data path
	 * (created automatically if not existing), and all jars in
	 * 'D:/GoldenGateServer/Components/MyComponentJarBin' will be placed on the
	 * class path for loading MyComponent. You have to create this folder
	 * manually in case you need to make some additional jar files available.
	 * @param componentFolder the folder containing the jar files to serch for
	 *            server components
	 * @return an array holding the server components found in the jar files in
	 *         the specified folder
	 */
	public static GoldenGateServerComponent[] loadServerComponents(final File componentFolder) {
		
		//	get base directory
		if(!componentFolder.exists()) componentFolder.mkdir();
		
		//	get components
		Object[] componentObject = GamtaClassLoader.loadComponents(
				componentFolder, 
				GoldenGateServerComponent.class, 
				new ComponentInitializer() {
					public void initialize(Object component, String componentJarName) throws Exception {
						File dataPath = new File(componentFolder, (componentJarName.substring(0, (componentJarName.length() - 4)) + "Data"));
						if (!dataPath.exists()) dataPath.mkdir();
						((GoldenGateServerComponent) component).setDataPath(dataPath);
					}
				});
		
		//	store & return components
		GoldenGateServerComponent[] components = new GoldenGateServerComponent[componentObject.length];
		for (int c = 0; c < componentObject.length; c++)
			components[c] = ((GoldenGateServerComponent) componentObject[c]);
		return components;
	}
}
