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
package de.uka.ipd.idaho.goldenGateServer.utilities;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.goldenGate.GoldenGateConstants;
import de.uka.ipd.idaho.htmlXmlUtil.Parser;
import de.uka.ipd.idaho.htmlXmlUtil.TokenReceiver;
import de.uka.ipd.idaho.htmlXmlUtil.TreeNodeAttributeSet;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.Grammar;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.StandardGrammar;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * @author sautter
 */
public class DataUploader extends JFrame implements GoldenGateConstants {
	
	private long localTime = -1;
	private File localFolder;
	private String[] localFileNames;
	private TreeMap localFilesByName = new TreeMap();
	private HashSet selectedLocalFileNames = new HashSet();
	private JTable localFileTable;
	
	private long hostTime = -1;
	private String host;
	private String[] hostFileNames;
	private TreeMap hostFilesByName = new TreeMap();
	private HashSet selectedHostFileNames = new HashSet();
	private JTable hostFileTable;
	
	private DataUploader(String host, String folderName) throws IOException {
		super(folderName);
		this.host = host;
		this.localFolder = new File("./", ("_" + folderName));
		this.refreshFileLists();
		JButton refreshButton = new JButton("Refresh File Lists");
		refreshButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				try {
					refreshFileLists();
				}
				catch (IOException ioe) {
					ioe.printStackTrace(System.out);
				}
			}
		});
		
		
		final JButton uploadButton = new JButton("Upload Selected Files");
		uploadButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				StringVector uploaded = new StringVector();
				for (Iterator fit = selectedLocalFileNames.iterator(); fit.hasNext();) {
					String fileName = ((String) fit.next());
					System.out.println("uploading " + fileName + " ...");
					try {
						if (upload(fileName)) {
							uploaded.addElement(fileName);
							System.out.println(fileName + " uploaded scucessfully");
						}
					}
					catch (AuthenticationException aex) {
						break;
					}
					catch (IOException ioe) {
						if (JOptionPane.showConfirmDialog(DataUploader.this, ("An error occurred while uploading " + fileName + ":\n" + ioe.getMessage() + "\nContinue uploading?"), "File Update Error", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE) != JOptionPane.YES_OPTION)
							break;
					}
				}
				if (uploaded.size() != 0) try {
					uploaded.sortLexicographically();
					JOptionPane.showMessageDialog(DataUploader.this, (uploaded.size() + " files updated successfully:\n - " + uploaded.concatStrings("\n - ")), "File Upload Report", JOptionPane.PLAIN_MESSAGE);
					refreshFileLists();
				}
				catch (IOException ioe) {
					ioe.printStackTrace(System.out);
				}
			}
		});
		uploadButton.setEnabled(this.selectedLocalFileNames.size() != 0);
		this.localFileTable = new JTable(new TableModel() {
			public Class getColumnClass(int columnIndex) {
				return ((columnIndex == 0) ? Boolean.class : String.class);
			}
			public int getColumnCount() {
				return 2;
			}
			public String getColumnName(int columnIndex) {
				return ((columnIndex == 0) ? "Update" : "File Name");
			}
			public int getRowCount() {
				return localFileNames.length;
			}
			
			public Object getValueAt(int rowIndex, int columnIndex) {
				String string = localFileNames[rowIndex];
				String displayString = string;
				displayString = displayString.replaceAll("\\<", "\\&lt\\;");
				displayString = displayString.replaceAll("\\>", "\\&gt\\;");
				if (columnIndex == 0)
					return new Boolean(selectedLocalFileNames.contains(string));
				else return displayString;
			}
			public boolean isCellEditable(int rowIndex, int columnIndex) {
				return (columnIndex == 0);
			}
			public void setValueAt(Object newValue, int rowIndex, int columnIndex) {
				String string = localFileNames[rowIndex];
				Boolean selected = ((Boolean) newValue);
				if (selected.booleanValue())
					selectedLocalFileNames.add(string);
				else selectedLocalFileNames.remove(string);
				uploadButton.setEnabled(selectedLocalFileNames.size() != 0);
			}
			
			public void addTableModelListener(TableModelListener tml) {}
			public void removeTableModelListener(TableModelListener tml) {}
			
		});
		this.localFileTable.getColumnModel().getColumn(0).setMaxWidth(60);
		JScrollPane localFileTableBox = new JScrollPane(this.localFileTable);
		JPanel localFilePanel = new JPanel(new BorderLayout());
		localFilePanel.add(localFileTableBox, BorderLayout.CENTER);
		localFilePanel.add(uploadButton, BorderLayout.SOUTH);
		
		
		final JButton deleteButton = new JButton("Delete Selected Files");
		deleteButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				StringVector deleted = new StringVector();
				for (Iterator fit = selectedHostFileNames.iterator(); fit.hasNext();) {
					String fileName = ((String) fit.next());
					System.out.println("deleting " + fileName + " ...");
					try {
						if (delete(fileName)) {
							deleted.addElement(fileName);
							System.out.println(fileName + " deleted scucessfully");
						}
					}
					catch (AuthenticationException aex) {
						break;
					}
					catch (IOException ioe) {
						if (JOptionPane.showConfirmDialog(DataUploader.this, ("An error occurred while deleting " + fileName + ":\n" + ioe.getMessage() + "\nContinue deleting?"), "File Delete Error", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE) != JOptionPane.YES_OPTION)
							break;
					}
				}
				if (deleted.size() != 0) try {
					deleted.sortLexicographically();
					JOptionPane.showMessageDialog(DataUploader.this, (deleted.size() + " files deleted successfully:\n - " + deleted.concatStrings("\n - ")), "File Delete Report", JOptionPane.PLAIN_MESSAGE);
					refreshFileLists();
				}
				catch (IOException ioe) {
					ioe.printStackTrace(System.out);
				}
			}
		});
		deleteButton.setEnabled(this.selectedHostFileNames.size() != 0);
		this.hostFileTable = new JTable(new TableModel() {
			public Class getColumnClass(int columnIndex) {
				return ((columnIndex == 0) ? Boolean.class : String.class);
			}
			public int getColumnCount() {
				return 2;
			}
			public String getColumnName(int columnIndex) {
				return ((columnIndex == 0) ? "Update" : "File Name");
			}
			public int getRowCount() {
				return hostFileNames.length;
			}
			
			public Object getValueAt(int rowIndex, int columnIndex) {
				String string = hostFileNames[rowIndex];
				String displayString = string;
				displayString = displayString.replaceAll("\\<", "\\&lt\\;");
				displayString = displayString.replaceAll("\\>", "\\&gt\\;");
				if (columnIndex == 0)
					return new Boolean(selectedHostFileNames.contains(string));
				else return displayString;
			}
			public boolean isCellEditable(int rowIndex, int columnIndex) {
				return (columnIndex == 0);
			}
			public void setValueAt(Object newValue, int rowIndex, int columnIndex) {
				String string = hostFileNames[rowIndex];
				Boolean selected = ((Boolean) newValue);
				if (selected.booleanValue())
					selectedHostFileNames.add(string);
				else selectedHostFileNames.remove(string);
				deleteButton.setEnabled(selectedHostFileNames.size() != 0);
			}
			
			public void addTableModelListener(TableModelListener tml) {}
			public void removeTableModelListener(TableModelListener tml) {}
			
		});
		this.hostFileTable.getColumnModel().getColumn(0).setMaxWidth(60);
		JScrollPane hostFileTableBox = new JScrollPane(this.hostFileTable);
		JPanel hostFilePanel = new JPanel(new BorderLayout());
		hostFilePanel.add(hostFileTableBox, BorderLayout.CENTER);
		hostFilePanel.add(deleteButton, BorderLayout.SOUTH);
		
		
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, localFilePanel, hostFilePanel);
		split.setDividerLocation(0.5);
		split.setResizeWeight(0.5);
		
		this.getContentPane().setLayout(new BorderLayout());
		this.getContentPane().add(split, BorderLayout.CENTER);
		this.getContentPane().add(refreshButton, BorderLayout.SOUTH);
		
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}
	
	private void refreshFileLists() throws IOException {
		HostFile[] hostFiles = this.loadHostFileList();
		this.hostFilesByName.clear();
		for (int hf = 0; hf < hostFiles.length; hf++) {
			this.hostFilesByName.put(hostFiles[hf].name, hostFiles[hf]);
//			System.out.println("HostFile " + hostFiles[hf].name + ": size is " + hostFiles[hf].size + ", age is " + (this.hostTime - hostFiles[hf].updateTime));
		}
		this.hostFileNames = ((String[]) this.hostFilesByName.keySet().toArray(new String[this.hostFilesByName.size()]));
		
		File[] localFiles = this.loadLocalFileList();
		this.localFilesByName.clear();
		for (int lf = 0; lf < localFiles.length; lf++) {
			this.localFilesByName.put(localFiles[lf].getName(), localFiles[lf]);
//			System.out.println("LocalFile " + localFiles[lf].getName() + ": size is " + localFiles[lf].length() + ", age is " + (this.localTime - localFiles[lf].lastModified()));
		}
		this.localFileNames = ((String[]) this.localFilesByName.keySet().toArray(new String[this.localFilesByName.size()]));
		
		this.selectedHostFileNames.clear();
		for (int hf = 0; hf < this.hostFileNames.length; hf++) {
			if (!this.localFilesByName.containsKey(this.hostFileNames[hf]))
				this.selectedHostFileNames.add(this.hostFileNames[hf]);
		}
		
		this.selectedLocalFileNames.clear();
		for (int lf = 0; lf < this.localFileNames.length; lf++) {
			if (!this.hostFilesByName.containsKey(this.localFileNames[lf]))
				this.selectedLocalFileNames.add(this.localFileNames[lf]);
			else {
				File localFile = ((File) this.localFilesByName.get(this.localFileNames[lf]));
				HostFile hostFile = ((HostFile) this.hostFilesByName.get(this.localFileNames[lf]));
				if ((this.localTime - localFile.lastModified()) < (this.hostTime - hostFile.updateTime))
					this.selectedLocalFileNames.add(this.localFileNames[lf]);
			}
		}
		
		if (this.localFileTable != null) {
			this.localFileTable.validate();
			this.localFileTable.repaint();
		}
		if (this.hostFileTable != null) {
			this.hostFileTable.validate();
			this.hostFileTable.repaint();
		}
	}
	
	private static final Grammar grammar = new StandardGrammar();
	private static final Parser parser = new Parser(grammar);
	
	private HostFile[] loadHostFileList() throws IOException {
		final ArrayList hostFiles = new ArrayList();
		BufferedReader fileIndexReader = new BufferedReader(new InputStreamReader((new URL(this.host + "/xml")).openStream(), "UTF-8"));
		parser.stream(fileIndexReader, new TokenReceiver() {
			public void close() throws IOException {}
			public void storeToken(String token, int treeDepth) throws IOException {
				if (grammar.isTag(token) && !grammar.isEndTag(token)) {
					String type = grammar.getType(token);
					if ("files".equals(type)) {
						TreeNodeAttributeSet tnas = TreeNodeAttributeSet.getTagAttributes(token, grammar);
						hostTime = Long.parseLong(tnas.getAttribute("time", "-1"));
					}
					else if ("file".equals(type)) {
						TreeNodeAttributeSet tnas = TreeNodeAttributeSet.getTagAttributes(token, grammar);
						String name = tnas.getAttribute("name");
						long size = Long.parseLong(tnas.getAttribute("size", "-1"));
						long updateTime = Long.parseLong(tnas.getAttribute("updateTime", "-1"));
						hostFiles.add(new HostFile(name, size, updateTime));
					}
				}
			}
		});
		return ((HostFile[]) hostFiles.toArray(new HostFile[hostFiles.size()]));
	}
	
	private File[] loadLocalFileList() {
		this.localTime = System.currentTimeMillis();
		File[] localFiles = this.localFolder.listFiles(new FileFilter() {
			public boolean accept(File file) {
				return (file.isFile() && !file.getName().endsWith(".old"));
			}
		});
		return ((localFiles == null) ? new File[0] : localFiles);
	}
	
	private static class AuthenticationException extends IOException {
		AuthenticationException() {
			super("Authentication failed.");
		}
	}
	
	private String sessionId = null;
	
	private void addAuthData(HttpURLConnection con) throws IOException {
		if (this.sessionId != null) {
			con.setRequestProperty("Cookie", this.sessionId);
			return;
		}
		
		AuthenticationDialog ad = new AuthenticationDialog(this, "Enter Login Data", this.host);
		ad.setLocationRelativeTo(this);
		ad.setVisible(true);
		
		if (ad.userNameValue == null)
			throw new AuthenticationException();
		
		con.setRequestProperty("UserName", ad.userNameValue);
		con.setRequestProperty("Password", ad.passwordValue);
	}
	
	private void getAuthInfo(HttpURLConnection con) {
		if (this.sessionId == null)
			this.sessionId = con.getHeaderField("Set-Cookie");
	}
	
	private boolean upload(String fileName) throws IOException {
		URL url = new URL(this.host + "/" + fileName);
		HttpURLConnection con = ((HttpURLConnection) url.openConnection());
		con.setDoOutput(true);
		con.setRequestMethod("PUT");
		this.addAuthData(con);
		
		File uploadFile = ((File) this.localFilesByName.get(fileName));
		
		InputStream is = new BufferedInputStream(new FileInputStream(uploadFile));
		OutputStream os = new BufferedOutputStream(con.getOutputStream());
		
		byte[] inBuf = new byte[1024];
		int inLen = -1;
		while ((inLen = is.read(inBuf)) != -1)
			os.write(inBuf, 0, inLen);
		os.flush();
		os.close();
		is.close();
		
		if (HttpURLConnection.HTTP_CREATED == con.getResponseCode()) {
			this.getAuthInfo(con);
			return true;
		}
		else return false;
	}
	
	private boolean delete(String fileName) throws IOException {
		URL url = new URL(this.host + "/" + fileName);
		HttpURLConnection con = ((HttpURLConnection) url.openConnection());
		con.setDoOutput(true);
		con.setRequestMethod("DELETE");
		this.addAuthData(con);
		
		if (HttpURLConnection.HTTP_OK == con.getResponseCode()) {
			this.getAuthInfo(con);
			return true;
		}
		else return false;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {}
		
		//	load specific settings
		Settings config = Settings.loadSettings(new File("_DataUploader.cnfg"));
		
		//	load local parameters
		try {
			StringVector parameters = StringVector.loadList(new File("./", PARAMETER_FILE_NAME));
			for (int p = 0; p < parameters.size(); p++) try {
				String param = parameters.get(p);
				int split = param.indexOf('=');
				if (split != -1) {
					String key = param.substring(0, split).trim();
					String value = param.substring(split + 1).trim();
					if ((key.length() != 0) && (value.length() != 0))
						config.setSetting(key, value);
				}
			} catch (Exception e) {}
		} catch (Exception e) {}
		
		//	configure web access
		if (config.containsKey(PROXY_NAME)) {
			System.getProperties().put("proxySet", "true");
			System.getProperties().put("proxyHost", config.getSetting(PROXY_NAME));
			if (config.containsKey(PROXY_PORT))
				System.getProperties().put("proxyPort", config.getSetting(PROXY_PORT));
			
			if (config.containsKey(PROXY_USER) && config.containsKey(PROXY_PWD)) {
				//	initialize proxy authentication
			}
		}
		
		//	read available hosts
		String[] settingNames = config.getKeys();
		StringVector hosts = new StringVector();
		for (int s = 0; s < settingNames.length; s++) {
			if (settingNames[s].startsWith("host-"))
				hosts.addElement(config.getSetting(settingNames[s]));
		}
		if (hosts.isEmpty()) {
			JOptionPane.showMessageDialog(null, "There are no hosts to connect to.", "No Hosts", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		String host = ((String) JOptionPane.showInputDialog(null, "Select the host to connect to.", "Select Host", JOptionPane.PLAIN_MESSAGE, null, hosts.toStringArray(), hosts.get(0)));
		if (host == null)
			return;
		
		String folderName = host;
		while (folderName.endsWith("/"))
			folderName = folderName.substring(0, (folderName.length() - 1));
		folderName = folderName.substring(folderName.lastIndexOf('/') + 1);
		if ("Downloads".equals(folderName))
			folderName = "Zips";
		
		DataUploader du = new DataUploader(host, folderName);
		du.setSize(700, 600);
		du.setResizable(true);
		du.setLocationRelativeTo(null);
		du.setVisible(true);
	}
	
	private static class HostFile implements Comparable {
		String name;
		long size;
		long updateTime;
		HostFile(String name, long size, long updateTime) {
			this.name = name;
			this.size = size;
			this.updateTime = updateTime;
		}
		public int compareTo(Object obj) {
			if (obj instanceof HostFile)
				return this.name.compareTo(((HostFile) obj).name);
			else return -1;
		}
	}
	
	private static class AuthenticationDialog extends JDialog {
		
		private JTextField userNameField = new JTextField();
		private JPasswordField passwordField = new JPasswordField();
		
		String userNameValue;
		String passwordValue;
		
		AuthenticationDialog(JFrame owner, String title, String address) {
			super(owner, title, true);
			
			JPanel fieldPanel = new JPanel(new GridBagLayout());
			
			this.userNameField.setBorder(BorderFactory.createLoweredBevelBorder());
			
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
