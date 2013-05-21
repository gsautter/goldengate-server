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


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;

/**
 * A server component to run in the Server Component Framework, either inside a
 * GoldenGateServerServlet, or inside the GoldenGateServer. The main purpose for a
 * server component is to offer functionality over the network (URLs or Socket
 * connections). Inside the GoldenGateServer, however, they may also offer
 * functionality for the console, e.g. administration or configuration commands.
 * 
 * @author sautter
 */
public interface GoldenGateServerComponent extends GoldenGateServerConstants {
	
	/**
	 * Make the server component know its data path
	 * @param dataPath the folder for this server component to load and store
	 *            its data
	 */
	public abstract void setDataPath(File dataPath);

	/**
	 * Make the server component know its host
	 * @param host the host for this server component to retrieve an IoProvider
	 *            from
	 */
	public abstract void setHost(GoldenGateServerComponentHost host);

	/**
	 * Initialize the server component (load data, etc.). This method is called
	 * after the data path and host are set. Since initialization order of
	 * components cannot be made any guarantees about, this method should be
	 * used only for local initialization. In particular, getting other
	 * components from the GoldenGateServerComponentRegistry should not be done
	 * here, since there is no way of making sure these components will
	 * operational after the invokation of their init() methods.
	 */
	public abstract void init();
	
	/**
	 * Link to other server components. This method is called after all
	 * components are initialized and registered. The purpose of this method is
	 * to establish links to other components via the GoldenGateServerComponentRegistry,
	 * fetch data from them, etc. At this point of the initialization procedure,
	 * all components avaiable from the registry are initialized locally.
	 * Furthermore, every component should have its local data loaded, at least
	 * the portions it offers to others via respective getters. If a required
	 * component is not found in the registry, but is essential for this
	 * component to work, this method should throw a RuntimeException indicating
	 * the class name of the missing component. Since the order in which links
	 * are established cannot be made any guarantees about, this method should
	 * establish links, but not yet interact with the linked-to components. This
	 * is because the latter may not yet be fully linked themselves.
	 */
	public abstract void link();
	
	/**
	 * Initialize the parts of the server component that require links to other
	 * components. This method is called after all components are linked. At
	 * this point, all components are linked and ready to fully interact with.
	 */
	public abstract void linkInit();
	
	/**
	 * Finalize the server component, store data, clean up memory, etc. This
	 * method is called before system shutdown.
	 */
	public abstract void exit();
	
	/**
	 * An action to perform on the server component. Implementations of this
	 * interface offer a generic way of exposing specific functions to network
	 * or console (command line) access. This interface exists to represent a
	 * common root of the two actual action interfaces, namely
	 * ComponentActionNetwork and ComponentActionConsole, plus their union
	 * interface ComponentActionFull.
	 * 
	 * @author sautter
	 */
	public interface ComponentAction {
		
		/**
		 * @return the command string identifying this action within the scope
		 *         of the backing components actions
		 */
		public abstract String getActionCommand();
	}
	
	/**
	 * An action to perform on the server component. Implementations of this
	 * interface expose specific functions to network access, getting their
	 * input from a BufferedReader and sending their output to a BufferedWriter.
	 * 
	 * @author sautter
	 */
	public interface ComponentActionNetwork extends ComponentAction {
		
		/** perform the actual action with invocation over the network
		 * @param	input	the Reader to retrieve input from
		 * @param	output	the Writer to write output to
		 * @throws IOException
		 */
		public abstract void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException;
	}
	
	/**
	 * An action to perform on the server component. Implementations of this
	 * interface expose specific functions to console access, getting their
	 * input as an array of strings and sending their output (if any) to
	 * System.out.
	 * 
	 * @author sautter
	 */
	public interface ComponentActionConsole extends ComponentAction {
		
		/** perform the actual action with invocation from the console (command line)
		 * @param	arguments	an array holding the arguments for the action
		 */
		public abstract void performActionConsole(String[] arguments);

		/**
		 * @return an array holding individual lines of an explanation text for
		 *         this action, e.g. details on parameters
		 */
		public abstract String[] getExplanation();
	}
	
	/**
	 * An action to perform on the server component. This interface exists to
	 * offer a convenient way of implementing both network and console access in
	 * a single anonymous class.
	 * 
	 * @author sautter
	 */
	public interface ComponentActionFull extends ComponentActionNetwork, ComponentActionConsole {}
	
	/**
	 * @return an array holding all the actions that can be performed on this
	 *         server component
	 */
	public abstract ComponentAction[] getActions();
	
	/**
	 * Obtain the letter code (ideally, 3 or 4 letters) identifying this server
	 * component in the console and in URLs. In the console, the component is
	 * addressed as &lt;letterCode&gt;.&lt;command&gt;, in a URL as
	 * http://&lt;host&gt;/&lt;componentServletContextPath&gt;/&lt;componentServletPath&gt;/&lt;letterCode&gt;
	 * @return the letter code identifying this server component in the console
	 *         and in a GoldenGateServerServlet.
	 */
	public abstract String getLetterCode();
}
