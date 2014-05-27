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
package de.uka.ipd.idaho.goldenGateServer.uaa.client;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;

import de.uka.ipd.idaho.gamta.Annotation;
import de.uka.ipd.idaho.gamta.util.gPath.GPath;
import de.uka.ipd.idaho.gamta.util.gPath.GPathFunction;
import de.uka.ipd.idaho.gamta.util.gPath.exceptions.GPathException;
import de.uka.ipd.idaho.gamta.util.gPath.exceptions.InvalidArgumentsException;
import de.uka.ipd.idaho.gamta.util.gPath.types.GPathObject;
import de.uka.ipd.idaho.gamta.util.gPath.types.GPathString;
import de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin;
import de.uka.ipd.idaho.goldenGate.plugins.ResourceManager;
import de.uka.ipd.idaho.goldenGate.plugins.SettingsPanel;
import de.uka.ipd.idaho.goldenGate.util.ResourceSelector;

/**
 * The authentication manager plugin offers a front-end plugin for integrating
 * the central authentication manager in GoldenGATE as a plugin. Other plugins
 * can obtain an AuthenticatedClient through the getAuthenticatedClient method.
 * Once created and logged in, this client is shared among all the requesters,
 * in the fashion of a singleton. This prevents users from having to log in for
 * each one of several client objects using an AuthenticatedClient for creating
 * another client that provides the actual functionality. Instead,
 * authentication is shared among all the functionality clients.
 * 
 * @author sautter
 */
public class AuthenticationManagerPlugin extends AbstractGoldenGatePlugin implements ResourceManager {
	
//	private JMenuItem tmAccountItem = new JMenuItem("<Not Logged In>");
	private ToolsMenuItem tmAccountItem = new ToolsMenuItem("<Not Logged In>");
	private JMenuItem mmAccountItem = new JMenuItem("<Not Logged In>");
	
//	private JMenuItem tmLoginItem = new JMenuItem("Login");
	private ToolsMenuItem tmLoginItem = new ToolsMenuItem("Login");
	private JMenuItem mmLoginItem = new JMenuItem("Login");
	
//	private JMenuItem tmLogoutItem = new JMenuItem("Logout");
	private ToolsMenuItem tmLogoutItem = new ToolsMenuItem("Logout");
	private JMenuItem mmLogoutItem = new JMenuItem("Logout");
	
	/** @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#init()
	 */
	public void init() {
		
		this.tmLoginItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				logout();
				getAuthenticatedClient();
			}
		});
		this.mmLoginItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				logout();
				getAuthenticatedClient();
			}
		});
		this.tmLoginItem.setEnabled(!AuthenticationManager.isAuthenticated());
		this.mmLoginItem.setEnabled(!AuthenticationManager.isAuthenticated());
		
		
		this.tmLogoutItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				logout();
			}
		});
		this.mmLogoutItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				logout();
			}
		});
		this.tmLogoutItem.setEnabled(AuthenticationManager.isAuthenticated());
		this.mmLogoutItem.setEnabled(AuthenticationManager.isAuthenticated());
		
		
		this.tmAccountItem.setText(AuthenticationManager.isAuthenticated() ? ("Logged in on " + AuthenticationManager.getHost() + (AuthenticationManager.isUsingHttp() ? " via HTTP" : (":" + AuthenticationManager.getPort()))) : "<Not Logged In>");
		this.mmAccountItem.setText(AuthenticationManager.isAuthenticated() ? ("Logged in on " + AuthenticationManager.getHost() + (AuthenticationManager.isUsingHttp() ? " via HTTP" : (":" + AuthenticationManager.getPort()))) : "<Not Logged In>");
		
		//	initialize authentication manager if not done before
		if (!AuthenticationManager.isInitialized())
			AuthenticationManager.initialize(this.dataProvider);
		
		//	make user name available to GPath expressions
		GPath.addFunction("ggServerUserName", new GPathFunction() {
			public GPathObject execute(Annotation contextAnnotation, int contextPosition, int contextSize, GPathObject[] args) throws GPathException {
				if (args.length != 0)
					throw new InvalidArgumentsException("The function 'ggServerUserName' requires 0 arguments.");
				String userName = AuthenticationManager.getUser();
				return new GPathString((userName == null) ? "" : userName);
			}
		});
	}
	
	/** @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#getSettingsPanel()
	 */
	public SettingsPanel getSettingsPanel() {
		return AuthenticationManager.getSettingsPanel();
	}
	
	/** @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#exit()
	 */
	public void exit() {
		AuthenticationManager.exit();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#getPluginName()
	 */
	public String getPluginName() {
		return "Authentication Manager";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.ResourceManager#getDataNamesForResource(java.lang.String)
	 */
	public String[] getDataNamesForResource(String name) {
		System.out.println("AMP: getting data names for '" + name + "'");
		String[] dataNames = AuthenticationManager.getAccountResourceDataNames(name);
		for (int n = 0; n < dataNames.length; n++) {
			System.out.print(" - " + dataNames[n]);
			dataNames[n] = (dataNames[n] + "@" + this.getClass().getName());
			System.out.println(" ==> " + dataNames[n]);
		}
		return dataNames;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.ResourceManager#getRequiredResourceNames(java.lang.String, boolean)
	 */
	public String[] getRequiredResourceNames(String name, boolean recourse) {
		return new String[0];
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.ResourceManager#getResourceNames()
	 */
	public String[] getResourceNames() {
		return AuthenticationManager.getAccountResourceNames();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.ResourceManager#getSelector(java.lang.String)
	 */
	public ResourceSelector getSelector(String label) {
		return this.getSelector(label, null);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#getMainMenuTitle()
	 */
	public String getMainMenuTitle() {
		return "GG Server Accounts";
	}

	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#getMainMenuItems()
	 */
	public JMenuItem[] getMainMenuItems() {
		JMenuItem[] mis = {this.mmAccountItem, this.mmLoginItem, this.mmLogoutItem};
		return mis;
	}

	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#getToolsMenuFunctionItems(de.uka.ipd.idaho.goldenGate.GoldenGateConstants.InvokationTargetProvider)
	 */
	public JMenuItem[] getToolsMenuFunctionItems(InvokationTargetProvider targetProvider) {
		JMenuItem[] mis = {this.tmAccountItem, this.tmLoginItem, this.tmLogoutItem};
		return mis;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.ResourceManager#getToolsMenuLabel()
	 */
	public String getToolsMenuLabel() {
		return null;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.ResourceManager#getSelector(java.lang.String, java.lang.String)
	 */
	public ResourceSelector getSelector(String label, String initialSelection) {
		return new ResourceSelector(this, initialSelection, label);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.ResourceManager#getResourceTypeLabel()
	 */
	public String getResourceTypeLabel() {
		return "GG Server Account";
	}
	
	/**
	 * @return the host currently connected to, or trying to connect to
	 */
	public String getHost() {
		return AuthenticationManager.getHost();
	}
	
	/**
	 * @return the port currently connected on, or trying to connect on
	 */
	public int getPort() {
		return AuthenticationManager.getPort();
	}
	
	/**
	 * Specify whether or not HTTP is in use for connections.
	 * @return true if HTTP is in use, false otherwise
	 */
	public boolean isUsingHttp() {
		return AuthenticationManager.isUsingHttp();
	}
	
	/**
	 * @return the user currently authenticated with, or trying to authenticate with
	 */
	public String getUser() {
		return AuthenticationManager.getUser();
	}
	
	/**
	 * Test if the authentication manager holds authentication data verified by
	 * the server. This method returns true if at least one login attempt
	 * succeeded, no matter if the server is currently reachable.
	 * @return true if at least one successful login attemplt has been made
	 */
	public boolean isAuthenticated() {
		return AuthenticationManager.isAuthenticated();
	}
	
	/**
	 * Retrieve an AuthenticatedClient for communicating with the backing
	 * server. If this method returns an AuthenticatedClient, it is logged in
	 * and ready to use. If a connection to the backing server could not be
	 * established, this method returns null.
	 * @return an AuthenticatedClient for communicating with the backing server
	 */
	public AuthenticatedClient getAuthenticatedClient() {
		AuthenticatedClient ac = AuthenticationManager.getAuthenticatedClient();
		this.tmLoginItem.setEnabled(ac == null);
		this.mmLoginItem.setEnabled(ac == null);
		this.tmLogoutItem.setEnabled(ac != null);
		this.mmLogoutItem.setEnabled(ac != null);
		this.tmAccountItem.setText((ac == null) ? "<Not Logged In>" : ("Logged in on " + AuthenticationManager.getHost() + (AuthenticationManager.isUsingHttp() ? " via HTTP" : (":" + AuthenticationManager.getPort()))));
		this.mmAccountItem.setText((ac == null) ? "<Not Logged In>" : ("Logged in on " + AuthenticationManager.getHost() + (AuthenticationManager.isUsingHttp() ? " via HTTP" : (":" + AuthenticationManager.getPort()))));
		return ac;
	}
	
	private void logout() {
		AuthenticationManager.logout();
		this.tmLoginItem.setEnabled(true);
		this.mmLoginItem.setEnabled(true);
		this.tmLogoutItem.setEnabled(false);
		this.mmLogoutItem.setEnabled(false);
		this.tmAccountItem.setText("<Not Logged In>");
		this.mmAccountItem.setText("<Not Logged In>");
	}
}
