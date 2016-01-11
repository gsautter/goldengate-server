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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.goldenGate.plugins.GoldenGatePluginDataProvider;
import de.uka.ipd.idaho.goldenGate.plugins.SettingsPanel;
import de.uka.ipd.idaho.goldenGate.util.DialogPanel;
import de.uka.ipd.idaho.goldenGateServer.client.ServerConnection;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * The authentication manager offers central management of accounts and
 * authentication. It can be passed its server connection data either via the
 * setHost(), setPort(), and setUseHttp() methods, or via loading account data
 * from its data provider. Other components can obtain the centralized instance
 * through the getAuthenticationManager() method. This method returns null if no
 * instance has been created so far. In this case, use the
 * getAuthenticationManager(GoldenGatePluginDataProvider) method. This will
 * create an AuthenticationManager using this very data provider. Dependent
 * components can obtain an AuthenticatedClient through the
 * getAuthenticatedClient method. Once created and logged in, this client is
 * shared among all the requesters, in the fashion of a singleton. This prevents
 * users from having to log in for each one of several client objects using an
 * AuthenticatedClient for creating another client that provides the actual
 * functionality. Instead, authentication is shared among all the functionality
 * clients.
 * 
 * @author sautter
 */
public class AuthenticationManager {
	
	private static class Account {
		String host = null;
		int port = -1;
		String userName = null;
		String password = null;
		boolean useHttp = false;
		
		Account() {}
		
		Account(String host, int port, String userName, String password) {
			this.host = host;
			this.port = port;
			this.userName = userName;
			this.password = password;
		}
		
		Account(Account model) {
			this.host = model.host;
			this.port = model.port;
			this.userName = model.userName;
			this.password = model.password;
			this.useHttp = model.useHttp;
		}
		
		String getName() {
			return (((this.userName == null) ? "" : (this.userName + "@")) + this.host + ":" + this.port + (this.useHttp ? " (via HTTP)" : ""));
		}
		
		public boolean equals(Object obj) {
			return ((obj != null) && (obj instanceof Account) && this.getName().equals(((Account) obj).getName()));
		}
	}
	
	private static final String ACCOUNT_FILE_EXTENSION = ".account.cnfg";
	
	private static final String NEW_ACCOUNT_NAME = "<Create New Account>";
	private static final String HOST_SETTING = "host";
	private static final String PORT_SETTING = "port";
	private static final String USER_NAME_SETTING = "userName";
	private static final String PASSWORD_SETTING = "password";
	private static final String USE_HTTP_SETTING = "http";
	
	private static GoldenGatePluginDataProvider dataProvider;
	
	private static StringVector accountNames = new StringVector();
	private static HashMap accountsByName = new HashMap();
	
	private static Account adHocAccount = null; // the account to use when no data provider is given
	private static Account activeAccount = null;
	private static AuthenticatedClient authClient = null;
	
	/**
	 * Test if the authentication manager has been initialized with some data
	 * provider.
	 * @return true if and only if the initialize() method has been invoked
	 *         before
	 */
	public static boolean isInitialized() {
		return (dataProvider != null);
	}
	
	/**
	 * Initialize the AuthenticationManager using the data provided by some data
	 * provider. Specifying a data provider through this method enables managing
	 * accounts beyond plain authentication. However, if this method ahs
	 * prevoiusly invoked with a different data provider, all data from the
	 * earlier provider will be discarted. Use the isInitialized() method to
	 * check if another data provider has been handed over before.
	 * @param newDataProvider the data provider to retrieve data from
	 */
	public static void initialize(GoldenGatePluginDataProvider newDataProvider) {
		accountNames.clear();
		accountsByName.clear();
		dataProvider = newDataProvider;
		
		String[] dataNames = dataProvider.getDataNames();
		
		//	read accounts
		for (int n = 0; n < dataNames.length; n++) {
			if (dataNames[n].endsWith(ACCOUNT_FILE_EXTENSION)) {
				Account account = loadAccount(dataNames[n]);
				if (account != null) {
					accountNames.addElementIgnoreDuplicates(account.getName());
					accountsByName.put(account.getName(), account);
				}
			}
		}
		
		//	creating new accounts makes sense only if data editable, or if ad-hoc authentication is required
		if (dataProvider.isDataEditable() || accountNames.isEmpty()) {
			accountNames.insertElementAt(NEW_ACCOUNT_NAME, 0);
			accountsByName.put(NEW_ACCOUNT_NAME, null);
		}
	}
	
	/**
	 * Retrieve a SettingsPanel for editing the accounts managed by the
	 * AuthenticationManager. If no data provider has been specified through the
	 * initialize() method, this method returns null, since then it's not
	 * possible to edit accounts.
	 * @return a SettingsPanel for editing the accounts managed by the
	 *         AuthenticationManager
	 */
	public static SettingsPanel getSettingsPanel() {
		return ((dataProvider == null) ? null : new AmpSettingsPanel());
	}
	
	private static class AmpSettingsPanel extends SettingsPanel {
		private JList accountList = new JList();
		AmpSettingsPanel() {
			super("GG Server Accounts", "Manage the accounts for accessing GoldenGATE Servers");
			this.setLayout(new BorderLayout());
			
			this.accountList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			JScrollPane accountListBox = new JScrollPane(this.accountList);
			accountListBox.setViewportBorder(BorderFactory.createLoweredBevelBorder());
			accountListBox.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			
			JPanel editButtons = new JPanel(new FlowLayout(FlowLayout.CENTER));
			JButton button;
			button = new JButton("Create");
			button.setBorder(BorderFactory.createRaisedBevelBorder());
			button.setPreferredSize(new Dimension(100, 21));
			button.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					Account account = createAccount();
					if (account != null) refreshList();
				}
			});
			editButtons.add(button);
			button = new JButton("Edit");
			button.setBorder(BorderFactory.createRaisedBevelBorder());
			button.setPreferredSize(new Dimension(100, 21));
			button.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					int index = accountList.getSelectedIndex();
					if (index != -1) {
						Object selected = accountList.getSelectedValue();
						Account account = ((Account) accountsByName.get(selected));
						if ((account != null)  && editAccount(account, "Edit Account"))
							refreshList();
					}
				}
			});
			editButtons.add(button);
			button = new JButton("Delete");
			button.setBorder(BorderFactory.createRaisedBevelBorder());
			button.setPreferredSize(new Dimension(100, 21));
			button.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					int index = accountList.getSelectedIndex();
					if (index != -1) {
						Object selected = accountList.getSelectedValue();
						Account account = ((Account) accountsByName.get(selected));
						if (account != null) {
							if (deleteAccount(account))
								refreshList();
						}
					}
				}
			});
			editButtons.add(button);
			
			//	refresh list when opened
			this.addAncestorListener(new AncestorListener() {
				public void ancestorAdded(AncestorEvent event) {
					refreshList();
				}
				public void ancestorMoved(AncestorEvent event) {}
				public void ancestorRemoved(AncestorEvent event) {}
			});
			
			this.add(editButtons, BorderLayout.NORTH);
			this.add(accountListBox, BorderLayout.CENTER);
			this.refreshList();
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.goldenGate.plugins.SettingsPanel#commitChanges()
		 */
		public void commitChanges() {
			//	do nothing here, since changes are written as they are made
		}
		
		private void refreshList() {
			StringVector accounts = new StringVector();
			accounts.addContent(accountNames);
			accounts.remove(NEW_ACCOUNT_NAME);
			accounts.sortLexicographically(false, false);
			this.accountList.setModel(new DefaultComboBoxModel(accounts.toStringArray()));
		}
		
		private Account createAccount() {
			Account account = new Account("", -1, "", "");
			if (editAccount(account, "Create Account"))
				return account;
			else return null;
		}
		
		private boolean editAccount(Account account, String title) {
			String accountName = account.getName();
			
			AccountEditor ae = new AccountEditor(account, title);
			ae.setLocationRelativeTo(DialogPanel.getTopWindow());
			ae.setVisible(true);
			
			if (ae.isCommitted()) {
				accountNames.removeAll(accountName);
				accountsByName.remove(accountName);
				accountNames.addElementIgnoreDuplicates(account.getName());
				accountsByName.put(account.getName(), account);
				
				saveAccount(account);
				return true;
			}
			
			else return false;
		}
		
		private boolean deleteAccount(Account account) {
			if (account == null) return false;
			if (account.equals(activeAccount)) logout();
			accountNames.removeAll(account.getName());
			accountsByName.remove(account.getName());
			return dataProvider.deleteData(account.getName().hashCode() + ACCOUNT_FILE_EXTENSION);
		}
	}
	
	/**
	 * exit the AuthenticationManager, which will cause (a) a logout from the
	 * backing server, and (b) all dirty accounts to be saved to disc, if there
	 * is a data provider that allows writing data.
	 */
	public static void exit() {
		
		//	log out
		logout();
//		
//		//	store account data ==> no need to do that, accounts are stored as they are created or modified
//		for (int a = 0; a < accountNames.size(); a++) {
//			String accountName = accountNames.get(a);
//			Account account = ((Account) accountsByName.get(accountName));
//			saveAccount(account);
//		}
		
		//	clean up
		accountNames.clear();
		accountsByName.clear();
		dataProvider = null;
	}
	
	private static final String PERSONALIZED_ACCOUNTS_NAME = "(Personalized Accounts, Never Export)";
	static String[] getAccountResourceNames() {
		StringVector resourceNames = new StringVector();
		for (int a = 0; a < accountNames.size(); a++) {
			String accountName = accountNames.get(a);
			if ((accountName.indexOf('@') == -1) && !NEW_ACCOUNT_NAME.equals(accountName))
				resourceNames.addElementIgnoreDuplicates(accountName);
		}
		resourceNames.addElementIgnoreDuplicates(PERSONALIZED_ACCOUNTS_NAME);
		resourceNames.sortLexicographically(false, false);
		return resourceNames.toStringArray();
	}
	static String[] getAccountResourceDataNames(String accountName) {
		if (PERSONALIZED_ACCOUNTS_NAME.equals(accountName)) {
			StringVector dataNames = new StringVector();
			for (int a = 0; a < accountNames.size(); a++) {
				String testAccountName = accountNames.get(a);
				if (testAccountName.indexOf('@') != -1)
					dataNames.addElementIgnoreDuplicates(testAccountName.hashCode() + ACCOUNT_FILE_EXTENSION);
			}
			dataNames.sortLexicographically(false, false);
			return dataNames.toStringArray();
		}
		else {
			String[] dataNames = {(accountName.hashCode() + ACCOUNT_FILE_EXTENSION)};
			return dataNames;
		}
	}
	
	private static class AccountEditor extends DialogPanel {
		
		private boolean committed = false;
		
		private JTextField hostField = new JTextField();
		private JTextField portField = new JTextField();
		
		private JTextField userNameField = new JTextField();
		private JTextField passwordField = new JTextField();
		
		private JCheckBox useHttp = new JCheckBox("Use HTTP");
		
		AccountEditor(final Account data, String title) {
			super(title, true);
			
			JPanel fieldPanel = new JPanel(new GridBagLayout());
			
			this.hostField.setBorder(BorderFactory.createLoweredBevelBorder());
			this.hostField.setText(data.host);
			
			this.portField.setBorder(BorderFactory.createLoweredBevelBorder());
			if (data.port != -1) this.portField.setText("" + data.port);
			
			this.userNameField.setBorder(BorderFactory.createLoweredBevelBorder());
			if (data.userName != null) this.userNameField.setText(data.userName) ;
			
			this.passwordField.setBorder(BorderFactory.createLoweredBevelBorder());
			if (data.password != null) this.passwordField.setText(data.password);
			
			this.useHttp.setBorder(BorderFactory.createLoweredBevelBorder());
			this.useHttp.setSelected(data.useHttp);
			
			final GridBagConstraints gbc = new GridBagConstraints();
			gbc.insets.top = 2;
			gbc.insets.bottom = 2;
			gbc.insets.left = 5;
			gbc.insets.right = 5;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx = 1;
			gbc.weighty = 0;
			gbc.gridwidth = 1;
			gbc.gridheight = 1;
			gbc.gridx = 0;
			gbc.gridy = 0;
			
			gbc.gridwidth = 6;
			gbc.weightx = 4;
			fieldPanel.add(new JLabel("Please enter the data of your account."), gbc.clone());
			
			gbc.gridy++;
			gbc.gridx = 0;
			gbc.weightx = 0;
			gbc.gridwidth = 1;
			fieldPanel.add(new JLabel("Host / Port", JLabel.LEFT), gbc.clone());
			gbc.gridx = 1;
			gbc.weightx = 3;
			gbc.gridwidth = 3;
			fieldPanel.add(this.hostField, gbc.clone());
			gbc.gridx = 4;
			gbc.weightx = 1;
			gbc.gridwidth = 1;
			fieldPanel.add(this.portField, gbc.clone());
			gbc.gridx=5;
			fieldPanel.add(this.useHttp, gbc.clone());
			
			gbc.gridy++;
			gbc.gridx = 0;
			gbc.weightx = 0;
			gbc.gridwidth = 1;
			fieldPanel.add(new JLabel("User Name", JLabel.LEFT), gbc.clone());
			gbc.gridx = 1;
			gbc.weightx = 2;
			gbc.gridwidth = 2;
			fieldPanel.add(this.userNameField, gbc.clone());
			gbc.gridx = 3;
			gbc.weightx = 1;
			gbc.gridwidth = 1;
			fieldPanel.add(new JLabel("Password", JLabel.LEFT), gbc.clone());
			gbc.gridx = 4;
			gbc.weightx = 2;
			gbc.gridwidth = 2;
			fieldPanel.add(this.passwordField, gbc.clone());
			
			JButton okButton = new JButton("OK");
			okButton.setBorder(BorderFactory.createRaisedBevelBorder());
			okButton.setPreferredSize(new Dimension(100, 21));
			okButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					
					//	check data
					StringVector errors = new StringVector();
					String value;
					
					value = hostField.getText().trim();
					if (value.length() == 0) {
						errors.addElement("The specified host name is invalid.");
						hostField.setText(data.host);
					}
					
					value = portField.getText().trim();
					if (value.length() == 0) {
						errors.addElement("The specified port number is invalid.");
						portField.setText((data.port == -1) ? "" : ("" + data.port));
					}
					else try {
						Integer.parseInt(value);
					}
					catch (NumberFormatException nfe) {
						errors.addElement("The specified port number is invalid.");
						portField.setText((data.port == -1) ? "" : ("" + data.port));
					}
					
					if (useHttp.isSelected()) {
						String host = hostField.getText().trim();
						
						//	truncate protocol, it's http anyway
						if (host.indexOf("://") != -1) host = host.substring(host.indexOf("://") + 3);
						
						//	separate host and file
						int hfSplit = host.indexOf("/");
						String file = "";
						if (hfSplit != -1) {
							file = host.substring(hfSplit);
							host = host.substring(0, hfSplit);
						}
						
						int port = 80;
						try {
							port = Integer.parseInt(portField.getText().trim());
						}
						catch (NumberFormatException nfe) {
							//	swallow this one, was already checked before
						}
						
						try {
							new URL("http", host, port, file);
						}
						catch (MalformedURLException mue) {
							errors.addElement("'" + "http://" + host + ":" + port + "/" + file + "' is not a valid URL.");
						}
					}
					
					value = userNameField.getText().trim();
					data.userName = ((value.length() == 0) ? null : value);
					
					value = passwordField.getText();
					data.password = ((value.length() == 0) ? null : value);
					
					
					//	no errors, account data is OK
					if (errors.isEmpty()) {
						
						//	write data
						data.host = hostField.getText().trim();
						data.port = Integer.parseInt(portField.getText().trim());
						
						value = userNameField.getText().trim();
						data.userName = ((value.length() == 0) ? null : value);
						
						value = passwordField.getText();
						data.password = ((value.length() == 0) ? null : value);
						
						data.useHttp = useHttp.isSelected();
						
						//	we are done here
						committed = true;
						dispose();
					}
					
					else {
						String errorMessage = ("The specified account data has ");
						errorMessage += ((errors.size() == 1) ? "an error:" : (errors.size() + " errors:"));
						errorMessage += "\n - " + errors.concatStrings("\n - ");
						
						JOptionPane.showMessageDialog(AccountEditor.this, errorMessage, "Error in Account Data", JOptionPane.ERROR_MESSAGE);
					}
				}
			});
			
			JButton cancelButton = new JButton("Cancel");
			cancelButton.setBorder(BorderFactory.createRaisedBevelBorder());
			cancelButton.setPreferredSize(new Dimension(100, 21));
			cancelButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					dispose();
				}
			});
			
			JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
			buttonPanel.add(okButton);
			buttonPanel.add(cancelButton);
			
			this.setLayout(new BorderLayout());
			this.add(fieldPanel, BorderLayout.CENTER);
			this.add(buttonPanel, BorderLayout.SOUTH);
			
			//	ensure dialog is closed with button
			this.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			this.setSize(500, 150);
		}
		
		boolean isCommitted() {
			return this.committed;
		}
	}
	
	private static Account loadAccount(String name) {
		try {
			InputStream is = dataProvider.getInputStream(name + (name.endsWith(ACCOUNT_FILE_EXTENSION) ? "" : ACCOUNT_FILE_EXTENSION));
			Settings accountData = Settings.loadSettings(is);
			is.close();
			
			if (accountData.size() > 1) { // only host & port are mandatory
				String host = accountData.getSetting(HOST_SETTING);
				int port = 80;
				try {
					port = Integer.parseInt(accountData.getSetting(PORT_SETTING, "80"));
				} catch (NumberFormatException nfe) {}
				
				String userName = accountData.getSetting(USER_NAME_SETTING);
				String password = accountData.getSetting(PASSWORD_SETTING);
				
				Account account = new Account(host, port, userName, password);
				account.useHttp = accountData.containsKey(USE_HTTP_SETTING);
				
				return account;
			}
			else throw new IOException("Invalid account data in '" + name + "'");
		}
		catch (IOException ioe) {
			System.out.println(ioe.getClass().getName() + " (" + ioe.getMessage() + ") while loading account data for '" + name + "'");
			return null;
		}
	}
	
	private static void saveAccount(Account account) {
		if ((account == null) || (dataProvider == null) || !dataProvider.isDataEditable()) return;
		
		//	gather data
		Settings accountData = new Settings();
		accountData.setSetting(HOST_SETTING, account.host);
		accountData.setSetting(PORT_SETTING, ("" + account.port));
		if (account.userName != null)
			accountData.setSetting(USER_NAME_SETTING, account.userName);
		if (account.password != null)
			accountData.setSetting(PASSWORD_SETTING, account.password);
		if (account.useHttp) accountData.setSetting(USE_HTTP_SETTING, "YES");
		
		//	store account data
		if (dataProvider.isDataEditable()) try {
			OutputStream os = dataProvider.getOutputStream(account.getName().hashCode() + ACCOUNT_FILE_EXTENSION);
			accountData.storeAsText(os);
			os.flush();
			os.close();
		}
		catch (IOException ioe) {
			System.out.println(ioe.getClass().getName() + " (" + ioe.getMessage() + ") while saving account data for " + account.getName());
		}
	}
	
	/**
	 * @return the host currently connected to, or trying to connect to
	 */
	public static String getHost() {
		return ((activeAccount == null) ? ((adHocAccount == null) ? null : adHocAccount.host) : activeAccount.host);
	}
	
	/**
	 * Set the host to connect to
	 * @param host the host to connect to
	 */
	public static void setHost(String host) {
		if (adHocAccount == null)
			adHocAccount = new Account();
		adHocAccount.host = host;
	}
	
	/**
	 * @return the port currently connected on, or trying to connect on
	 */
	public static int getPort() {
		return ((activeAccount == null) ? ((adHocAccount == null) ? -1 : adHocAccount.port) : activeAccount.port);
	}
	
	/**
	 * Set the port to connect on
	 * @param port the port to connect on
	 */
	public static void setPort(int port) {
		if (adHocAccount == null)
			adHocAccount = new Account();
		adHocAccount.port = port;
	}
	
	/**
	 * Specify whether or not HTTP is in use for connections.
	 * @return true if HTTP is in use, false otherwise
	 */
	public static boolean isUsingHttp() {
		return ((activeAccount == null) ? ((adHocAccount == null) ? false : adHocAccount.useHttp) : activeAccount.useHttp);
	}
	
	/**
	 * Specify whether or not to connect via HTTP. If the argument is true and
	 * the port has not been set, the port number is automatically set to 80.
	 * @param useHttp connect via HTTP?
	 */
	public static void setUseHttp(boolean useHttp) {
		if (adHocAccount == null)
			adHocAccount = new Account();
		adHocAccount.useHttp = useHttp;
		
		//	set default HTTP port if not set before
		if ((adHocAccount.port == -1) && useHttp)
			adHocAccount.port = 80;
	}
	
	/**
	 * @return the user currently authenticated with, or trying to authenticate with
	 */
	public static String getUser() {
		return ((activeAccount == null) ? ((adHocAccount == null) ? null : adHocAccount.userName) : activeAccount.userName);
	}
	
	/**
	 * Set the user name to use for authentication
	 * @param user the user name to use for authentication
	 */
	public static void setUser(String user) {
		if (adHocAccount == null)
			adHocAccount = new Account();
		adHocAccount.userName = user;
	}
	
	/**
	 * Set the password to use for authentication. There is no getter ...
	 * @param password the password to use for authentication
	 */
	public static void setPassword(String password) {
		if (adHocAccount == null)
			adHocAccount = new Account();
		adHocAccount.password = password;
	}
	
	/**
	 * Test if the authentication manager holds complete authentication data,
	 * which is verified by the server if a connection has been established at
	 * any point in time. This method returns true if at least one login attempt
	 * succeeded, no matter if the server is currently reachable, or if the
	 * server was never reachable so far. In the latter case, login data may not
	 * be verified.
	 * @return true if at least one successful login attempt has been made, or
	 *         if complete login data is available but the server has never been
	 *         reached.
	 */
	public static boolean isAuthenticated() {
		return ((activeAccount != null) && (activeAccount.password != null));
	}
	
	/**
	 * Retrieve an AuthenticatedClient for communicating with the backing
	 * server. If this method returns an AuthenticatedClient, it is logged in
	 * and ready to use. If a connection to the backing server could not be
	 * established, this method returns null.
	 * @return an AuthenticatedClient for communicating with the backing server
	 */
	public static AuthenticatedClient getAuthenticatedClient() {
		if (ensureLoggedIn())
			return authClient;
		else return null;
	}
	
	private static boolean ensureLoggedIn() {
		
		//	check if account data complete (might have come through setters ...)
		if (activeAccount != null) {
			if (activeAccount.host == null)
				activeAccount = null;
			else if (activeAccount.port == -1)
				activeAccount = null;
		}
		
		//	no account selected so far
		if (activeAccount == null) {
			
			Account account = null;
			
			//	only ad-hoc authentication possible
			if (dataProvider == null) {
				if ((adHocAccount != null) && (adHocAccount.host != null) && (adHocAccount.port != -1))
					account = adHocAccount;
			}
			
			//	got accounts to choose
			else {
				//	get accounts to use
				ArrayList accNameList = new ArrayList();
				accNameList.addAll(Arrays.asList(accountNames.toStringArray()));
				if ((adHocAccount != null) && (adHocAccount.host != null) && (adHocAccount.port != -1))
					accNameList.add(adHocAccount.getName());
				
				if (accNameList.size() == 1) // we have little choice, no need for bugging user
					account = ((Account) accountsByName.get(accNameList.get(0)));
				
				else {
					String preSelectedAccName;
					if (accNameList.isEmpty())
						preSelectedAccName = null;
					else if (NEW_ACCOUNT_NAME.equals(accNameList.get(0)))
						preSelectedAccName = ((String) accNameList.get(1));
					else preSelectedAccName = ((String) accNameList.get(1));
					
					String[] accNames = ((String[]) accNameList.toArray(new String[accNameList.size()]));
					
					Object o = JOptionPane.showInputDialog(DialogPanel.getTopWindow(), "Please select the account to use for connection.", "Select Account", JOptionPane.QUESTION_MESSAGE, null, accNames, preSelectedAccName);
					
					//	dialog cancelled
					if (o == null) return false;
					
					//	get account, create one if asked so
					account = ((Account) accountsByName.get(o.toString()));
					
					//	check if ad-hoc account chosen
					if ((account == null) && (adHocAccount != null) && o.equals(adHocAccount.getName()))
						account = adHocAccount;
				}
			}
			
			//	create account if asked so
			if (account == null) account = createAccount();
			
			//	create dialog cancelled
			if (account == null) return false;
			
			//	remember account
			activeAccount = new Account(account);
		}
		
		//	no connector created so far
		if (authClient == null) {
			
			if (activeAccount.useHttp) {
				
				//	prepare URL
				String host = activeAccount.host;
				
				//	get protocol, defaulting to http
				String protocol = "http";
				if (host.indexOf("://") != -1) {
					protocol = host.substring(0, host.indexOf("://"));
					host = host.substring(host.indexOf("://") + "://".length());
				}
				
				//	separate host and file
				String file = "";
				int hfSplit = host.indexOf("/");
				if (hfSplit != -1) {
					file = host.substring(hfSplit);
					while (file.startsWith("/"))
						file = file.substring("/".length());
					host = host.substring(0, hfSplit);
					if (host.indexOf(':') != -1)
						host = host.substring(0, host.indexOf(':'));
				}
				
				//	create connector
				authClient = AuthenticatedClient.getAuthenticatedClient(ServerConnection.getServerConnection(protocol + "://" + host + ":" + activeAccount.port + "/" + file), true);
			}
			else authClient = AuthenticatedClient.getAuthenticatedClient(ServerConnection.getServerConnection(activeAccount.host, activeAccount.port), true);
		}
		
		//	we have been logged in once, check session
		if (authClient.isLoggedIn()) {
			try {
				
				//	server reachable, session valid
				if (authClient.ensureLoggedIn())
					return true;
				
				//	server reachable, but session expired --> re-login with last active account
				else if (authClient.login(activeAccount.userName, activeAccount.password))
					return true;
				
				//	could not re-login;
				else {
					/* clear password so authentication dialog re-opens on
					 * recursive call (might happen though login data was used
					 * successfully before, eg if password was changed on server
					 * side) */
					activeAccount.password = null;
					
					//	clear clients in order to enanble re-creation, thus get rid of old session ID
					authClient = null;
					
					//	re-try authentication
					return ensureLoggedIn();
				}
			}
			
			//	server unreachable
			catch (IOException ioe) {
				System.out.println("AuthenticationManater: error on login verification: " + ioe.getMessage());
				ioe.printStackTrace(System.out);
				
				/* indicate failure, but don't clear registers, since reason is
				 * likely that server is temporally down or network connection
				 * temporally lost, and both might become available again later
				 * on. Then, data from registers can be used to re-establich
				 * connection */
				return false;
			}
		}
		
		//	not logged in
		else {
			
			//	get authentication data
			String userName = activeAccount.userName;
			String password = activeAccount.password;
			
			//	prompt for authentication data if missing
			if ((userName == null) || (password == null)) {
				AuthenticationDialog ad = new AuthenticationDialog(("Log in to " + activeAccount.host), activeAccount.host, userName);
				ad.setLocationRelativeTo(DialogPanel.getTopWindow());
				ad.setVisible(true);
				
				if ((ad.userNameValue == null) || (ad.passwordValue == null))
					return false;
				
				userName = ad.userNameValue;
				password = ad.passwordValue;
			}
			
			/* remember user name in order to use it in prompt, for caching, or
			 * for re-connecting in case of a session timeout */
			activeAccount.userName = userName;
			
			//	attempt log in
			try {
				
				//	login successful
				if (authClient.login(userName, password)) {
					
					//	remember password in order to re-connect later on in case of a session timeout
					activeAccount.password = password;
					return true;
				}
				
				//	invalid authentication data
				else {
					
					//	clear password so authentication dialog re-opens on recursive call
					activeAccount.password = null;
					
					//	re-try authentication
					return ensureLoggedIn();
				}
			}
			
			//	could not establish connection to server
			catch (IOException ioe) {
				System.out.println("AuthenticationManater: error on login: " + ioe.getMessage());
				ioe.printStackTrace(System.out);
				
				//	remember password in order to re-try to connect later on
				activeAccount.password = password;
				
				/*
				 * DON'T retry, will irritae users, since accounts will be set
				 * by admin re-try authentication
				 */
				//return this.ensureLoggedIn();
				return false;
			}
		}
	}
	
	/**
	 * Log out from the backing server. This will render the
	 * AuthenticatedClients retrieved through the getAuthenticatedClient()
	 * method useless, any refering code will have to obtain a new
	 * AuthenticatedClient in order to access the server again. Use with care.
	 */
	public static void logout() {
		if (activeAccount != null) try {
			
			if (authClient != null)
				authClient.logout();
			authClient = null;
			
			activeAccount = null;
		}
		catch (IOException ioe) {
			JOptionPane.showMessageDialog(DialogPanel.getTopWindow(), ("An error occurred while logging out from the GoldenGATE Server at\n" + activeAccount.host + ":" + activeAccount.port + "\n" + ioe.getMessage()), ("Error on Logout"), JOptionPane.ERROR_MESSAGE);
		}
	}
	
	private static Account createAccount() {
		Account account = new Account(
				((adHocAccount == null) ? "" : ((adHocAccount.host == null) ? "" : adHocAccount.host)), 
				((adHocAccount == null) ? -1 : adHocAccount.port), 
				((adHocAccount == null) ? "" : ((adHocAccount.userName == null) ? "" : adHocAccount.userName)), 
				((adHocAccount == null) ? "" : ((adHocAccount.password == null) ? "" : adHocAccount.password))
				);
		if (adHocAccount != null) account.useHttp = adHocAccount.useHttp;
		
		AccountEditor ae = new AccountEditor(account, "Please Complete Connection And Login Data");
		ae.setLocationRelativeTo(DialogPanel.getTopWindow());
		ae.setVisible(true);
		
		if (ae.isCommitted()) {
			
			if (dataProvider != null) {
				saveAccount(account);
				accountNames.addElementIgnoreDuplicates(account.getName());
				accountsByName.put(account.getName(), account);
			}
			
			return account;
		}
		
		else return null;
	}
	
	private static class AuthenticationDialog extends DialogPanel {
		
		private JTextField userNameField = new JTextField();
		private JPasswordField passwordField = new JPasswordField();
		
		String userNameValue;
		String passwordValue;
		
		AuthenticationDialog(String title, String address, String userName) {
			super(title, true);
			
			JPanel fieldPanel = new JPanel(new GridBagLayout());
			
			this.userNameField.setBorder(BorderFactory.createLoweredBevelBorder());
			if (userName != null) this.userNameField.setText(userName) ;
			
			this.passwordField.setBorder(BorderFactory.createLoweredBevelBorder());
			this.passwordField.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					commit();
				}
			});
			
			final GridBagConstraints gbc = new GridBagConstraints();
			gbc.insets.top = 2;
			gbc.insets.bottom = 2;
			gbc.insets.left = 5;
			gbc.insets.right = 5;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx = 1;
			gbc.weighty = 0;
			gbc.gridwidth = 1;
			gbc.gridheight = 1;
			gbc.gridx = 0;
			gbc.gridy = 0;
			
			gbc.gridwidth = 6;
			gbc.weightx = 4;
			fieldPanel.add(new JLabel("Please enter your login data for the GoldeGATE Server at " + address), gbc.clone());
			
			gbc.gridy++;
			gbc.gridx = 0;
			gbc.weightx = 0;
			gbc.gridwidth = 1;
			fieldPanel.add(new JLabel("User Name", JLabel.LEFT), gbc.clone());
			gbc.gridx = 1;
			gbc.weightx = 2;
			gbc.gridwidth = 2;
			fieldPanel.add(this.userNameField, gbc.clone());
			gbc.gridx = 3;
			gbc.weightx = 1;
			gbc.gridwidth = 1;
			fieldPanel.add(new JLabel("Password", JLabel.LEFT), gbc.clone());
			gbc.gridx = 4;
			gbc.weightx = 2;
			gbc.gridwidth = 2;
			fieldPanel.add(this.passwordField, gbc.clone());
			
			JButton okButton = new JButton("OK");
			okButton.setBorder(BorderFactory.createRaisedBevelBorder());
			okButton.setPreferredSize(new Dimension(100, 21));
			okButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					commit();
				}
			});
			
			JButton cancelButton = new JButton("Cancel");
			cancelButton.setBorder(BorderFactory.createRaisedBevelBorder());
			cancelButton.setPreferredSize(new Dimension(100, 21));
			cancelButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					userNameValue = null;
					passwordValue = null;
					dispose();
				}
			});
			
			JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
			buttonPanel.add(okButton);
			buttonPanel.add(cancelButton);
			
			this.add(fieldPanel, BorderLayout.CENTER);
			this.add(buttonPanel, BorderLayout.SOUTH);
			
			//	ensure dialog is closed with button
			this.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			this.setSize(500, 110);
		}
		
		private void commit() {
			
			//	check data
			String value = this.userNameField.getText().trim();
			if (value.length() == 0)
				JOptionPane.showMessageDialog(this, "Please specify a user name.", "Error in Login Data", JOptionPane.ERROR_MESSAGE);
			
			else {
				//	get data
				this.userNameValue = value;
				this.passwordValue = new String(this.passwordField.getPassword()).trim();
				
				//	we are done here
				dispose();
			}
		}
	}
}
