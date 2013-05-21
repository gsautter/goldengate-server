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
import java.io.IOException;

import de.uka.ipd.idaho.easyIO.settings.Settings;

/**
 * Abstract implementation of a server component, providing data path and letter
 * code, which are read in the init method.
 * 
 * @author sautter
 */
public abstract class AbstractGoldenGateServerComponent implements GoldenGateServerComponent {
	
	/**
	 * The name of the config file in this server component's data path. If this
	 * file does not exist, the class name of the server component is used
	 * instead. This is useful is multiple server components share a data
	 * folder.
	 */
	public static final String CONFIG_FILE_NAME = "config.cnfg";
	
	/**
	 * The name of the config file setting holding the letter code for this
	 * server component
	 */
	public static final String LETTER_CODE_SETTING_NAME = "letterCode";
	
	/** the component's data path, the folder the component's data is located in */
	protected File dataPath;
	
	/** the component's host, providing access to the shared database */
	protected GoldenGateServerComponentHost host;
	
	/** the component's configuration */
	protected Settings configuration;
	private String configurationFileName;
	
	/** the component's letter code, identifying it in the component server's console */
	protected String letterCode;
	
	/**
	 * Constructor allowing sub calsses to submit a constant default letter
	 * code. Note that any sub class needs to provide a no-argument constructor
	 * in order to allow for class loading.
	 * @param letterCode the letter code for this component
	 */
	public AbstractGoldenGateServerComponent(String letterCode) {
		this.letterCode = letterCode;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent#setDataPath(java.io.File)
	 */
	public void setDataPath(File dataPath) {
		this.dataPath = dataPath;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent#setHost(de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponentHost)
	 */
	public void setHost(GoldenGateServerComponentHost host) {
		this.host = host;
	}

	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent#init()
	 */
	public final void init() {
		File setFile = new File(this.dataPath, CONFIG_FILE_NAME);
		if (setFile.exists()) {
			this.configuration = Settings.loadSettings(setFile);
			this.configurationFileName = CONFIG_FILE_NAME;
		}
		else {
			this.configuration = Settings.loadSettings(new File(this.dataPath, (this.getClass().getName() + ".cnfg")));
			this.configurationFileName = (this.getClass().getName() + ".cnfg");
		}
		
		this.letterCode = this.configuration.getSetting(LETTER_CODE_SETTING_NAME, this.letterCode);
		
		this.initComponent();
	}
	
	/**
	 * Initialize the server component further. This method replaces init(),
	 * which is final because it reads the component's configuration and letter
	 * code, afterward calling this method. This default implementation does
	 * nothing, so sub classes may overwrite it as needed.
	 */
	protected void initComponent() {}
	
	/**
	 * Link the server component to other components via the
	 * GoldenGateServerComponentRegistry. Note: This default implementation does nothing,
	 * sub classes are welcome to overwrite it as needed.
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent#link()
	 */
	public void link() {}
	
	/**
	 * Initialize the parts of the server component that require links to other
	 * components. This method is called after all components are linked. At
	 * this point, all components are linked and ready to fully interact with.
	 * @see de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent#linkInit()
	 */
	public void linkInit() {}

	/* (non-Javadoc)
	 * @see de.goldenGateScf.ServerComponent#exit()
	 */
	public final void exit() {
		this.exitComponent();
		
		File setFile = new File(this.dataPath, this.configurationFileName);
		try {
			this.configuration.storeAsText(setFile);
		}
		catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}
	
	/**
	 * shit down the server component further. This method replaces exit(),
	 * which is final because it stores the component's configuration, calling
	 * this method before docing so. This default implementation does nothing,
	 * so sub classes may overwrite it as needed.
	 */
	protected void exitComponent() {}
	
	/* (non-Javadoc)
	 * @see de.goldenGateScf.ServerComponent#getLetterCode()
	 */
	public String getLetterCode() {
		return this.letterCode;
	}
}
