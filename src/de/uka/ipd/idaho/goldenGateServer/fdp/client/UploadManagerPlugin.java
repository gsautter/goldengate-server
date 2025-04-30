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
package de.uka.ipd.idaho.goldenGateServer.fdp.client;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import de.uka.ipd.idaho.gamta.util.swing.DialogFactory;
import de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin;
import de.uka.ipd.idaho.goldenGate.ui.GoldenGateUI;
import de.uka.ipd.idaho.goldenGate.ui.GoldenGateUI.DocumentDisplay;
import de.uka.ipd.idaho.goldenGate.ui.WindowMenuBar;
import de.uka.ipd.idaho.goldenGate.ui.WindowMenuElement;
import de.uka.ipd.idaho.goldenGate.ui.WindowMenuFunction;
import de.uka.ipd.idaho.goldenGate.util.DialogPanel;
import de.uka.ipd.idaho.goldenGateServer.fdp.GoldenGateFdpConstants.FileDescriptor;
import de.uka.ipd.idaho.goldenGateServer.fdp.GoldenGateFdpConstants.FileGroupDescriptor;
import de.uka.ipd.idaho.goldenGateServer.fdp.util.FileGroup;
import de.uka.ipd.idaho.goldenGateServer.fdp.util.FileNameFilter;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticatedClient;
import de.uka.ipd.idaho.goldenGateServer.uaa.client.AuthenticationManagerPlugin;

/**
 * @author sautter
 */
public class UploadManagerPlugin extends AbstractGoldenGatePlugin {
	private AuthenticationManagerPlugin authManager = null;
	private AuthenticatedClient authClient = null;
	private GoldenGateFdpClient fdpClient = null;
	
	private TreeMap sourceFoldersByGroupName = new TreeMap();
	
	/** usual zero-argument constructor for class loading */
	public UploadManagerPlugin() {}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#getPluginName()
	 */
	public String getPluginName() {
		return "FDP Uplaod Manager";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#getRequiredPluginClasses()
	 */
	public Class[] getRequiredPluginClasses() {
		Class[] rpcs = { AuthenticationManagerPlugin.class };
		return rpcs;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#init()
	 */
	public void init() {
		
		//	get authentication manager
		this.authManager = ((AuthenticationManagerPlugin) this.parent.getPlugin(AuthenticationManagerPlugin.class.getName()));
		
		//	load mapping of local folders to file groups
		if (this.dataProvider.isDataAvailable("fileGroupMappings.cnfg")) try {
			BufferedReader fgmBr = new BufferedReader(new InputStreamReader(this.dataProvider.getInputStream("fileGroupMappings.cnfg"), "UTF-8"));
			for (String fgmRow; (fgmRow = fgmBr.readLine()) != null;) {
				fgmRow = fgmRow.trim();
				if (fgmRow.length() == 0)
					continue;
				if (fgmRow.startsWith("//"))
					continue;
				String[] fgmData = fgmRow.split("\\t");
				if (fgmData.length < 2)
					continue;
				String fileGroupName = fgmData[0].trim();
				String localPath = fgmData[1].trim();
				String localFilterStr = ((2 < fgmData.length) ? fgmData[2].trim() : null);
				this.sourceFoldersByGroupName.put(fileGroupName, new SourceFolder(fileGroupName, localPath, ((localFilterStr == null) ? null : localFilterStr.split("\\s+"))));
			}
		}
		catch (IOException ioe) {
			System.out.println("UploadManagerPlugin: Error loading file group mappings: " + ioe.getMessage());
			ioe.printStackTrace(System.out);
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#exit()
	 */
	public void exit() {
		this.logout();
	}
	
	private static class SourceFolder {
		final String fileGroupName;
		final String localPath;
		final String[] localFilters;
		SourceFolder(String fileGroupName, String localPath, String[] localFilters) {
			this.fileGroupName = fileGroupName;
			this.localPath = localPath;
			this.localFilters = localFilters;
		}
	}
	
	private static class ConnectedFileGroup implements FileFilter {
		final FileGroup fdpFileGroup;
		final SourceFolder sourceFolder;
		final FileNameFilter sourceFilter;
		ConnectedFileGroup(FileGroup fdpFileGroup, SourceFolder sourceFolder) {
			this.fdpFileGroup = fdpFileGroup;
			this.sourceFolder = sourceFolder;
			this.sourceFilter = ((this.sourceFilter == null) ? null : new FileNameFilter(this.sourceFolder.localFilters));
		}
		public boolean accept(File file) {
			if (file.isDirectory())
				return true;
			if (!this.fdpFileGroup.accept(file))
				return false;
			if (this.sourceFilter == null)
				return true;
			return this.sourceFilter.acceptName(file.getName());
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#getMainMenuTitle()
	 */
	public String getMainMenuTitle() {
		return "GoldenGATE FDP Uplaoder";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.plugins.AbstractGoldenGatePlugin#getWindowMenuElements()
	 */
	public WindowMenuElement[] getWindowMenuElements() {
		int flags = 0;
		flags |= WindowMenuElement.PROPERTY_AVAILABLE_DESKTOP;
		flags |= WindowMenuElement.PROPERTY_REQUIRES_MAIN_WINDOW;
		flags |= WindowMenuElement.PROPERTY_REQUIRES_MASTER_DATA;
		flags |= WindowMenuElement.PROPERTY_REQUIRES_MASTER_MODE;
		flags |= WindowMenuElement.PROPERTY_RESOURCE_MANAGEMENT;
		flags = WindowMenuElement.encodePreferredMenuName(WindowMenuBar.PLUGINS_MENU_NAME, flags);
		WindowMenuElement[] wmes = {
			new WindowMenuFunction(UploadManagerPlugin.class.getName(), "main.manageFileGroups", "Manage FDP File Groups", "Manage file groups between local source folders and GoldenGATE FDP", flags) {
				public boolean checkAvailable(GoldenGateUI ggui, DocumentDisplay display) {
					return (ggui != null); // exporting configurations only needs to be available in main menu
				}
				public void execute(GoldenGateUI ggui, DocumentDisplay display) {
					manageFileGroups();
				}
			}
		};
		return wmes;
	}
	
	void manageFileGroups() {
		
		//	get local root folder
		File rootFolder = this.getRootFolder();
		if (rootFolder == null)
			return;
		
		//	check connection
		this.ensureLoggedIn();
		if (this.fdpClient == null) {
			DialogFactory.alert(("Cannot synchronize file groups with GoldenGATE FDP without authentication."), "Cannot Synchronize File Groups", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		//	get file groups from FDP and match up with local source folders
		FileGroupDescriptor[] fgds;
		try {
			fgds = this.fdpClient.getFileGroups();
		}
		catch (IOException ioe) {
			ioe.printStackTrace(System.out);
			DialogFactory.alert(("Could not fetch file groupss from GoldenGATE FDP (see log for details):\r\n  " + ioe.getMessage()), "Error Fetching File Groups", JOptionPane.ERROR_MESSAGE);
			return;
		}
		ArrayList fileGroups = new ArrayList();
		for (int g = 0; g < fgds.length; g++) {
			SourceFolder sf = ((SourceFolder) this.sourceFoldersByGroupName.get(fgds[g].name));
			if (sf == null)
				continue; // cannot connect to source
			FileGroup fg = new FileGroup(fgds[g]);
			fileGroups.add(new ConnectedFileGroup(fg, sf));
		}
		if (fileGroups.isEmpty()) {
			DialogFactory.alert(("Cannot connect any file groupss to local source folders, check configuration."), "Cannot Connect File Groups", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		//	tray up file groups and show overview dialog
		FileGroupPanel[] fileGroupPanels = new FileGroupPanel[fileGroups.size()];
		for (int g = 0; g < fileGroups.size(); g++)
			fileGroupPanels[g] = new FileGroupPanel(rootFolder, ((ConnectedFileGroup) fileGroups.get(g)));
		
		//	and open overview dialog
		FileGroupListDialog fgld = new FileGroupListDialog(fileGroupPanels);
		fgld.setVisible(true);
	}
	
	private File getRootFolder() {
		
		//	get root path
		File ggRoot;
		try {
			ggRoot = new File(".");
			
			//	check if we got the root path
			File ggJar = new File(ggRoot, "GoldenGATE.jar");
			if (ggJar.exists())
				return ggRoot;
			else {
				DialogFactory.alert("Cannot synchronize file groups without GoldenGATE root folder.", "GoldenGATE Root Folder Not Found", JOptionPane.ERROR_MESSAGE);
				return null;
			}
		}
		
		//	we may not be allowed to access the file system ...
		catch (SecurityException se) {
			DialogFactory.alert("Cannot synchronize file groups without without access to GoldenGATE root folder.", "GoldenGATE Root Folder Not Accessible", JOptionPane.ERROR_MESSAGE);
			return null;
		}
	}
	
	private boolean ensureLoggedIn() {
		
		//	test if connection alive
		if (this.authClient != null)
			try {
				//	test if connection alive
				if (this.authClient.ensureLoggedIn())
					return true;
				
				//	connection dead (eg a session timeout), make way for re-getting from auth manager
				else {
					this.fdpClient = null;
					this.authClient = null;
				}
			}
			
			//	server temporarily unreachable, re-login will be done by auth manager
			catch (IOException ioe) {
				this.fdpClient = null;
				this.authClient = null;
				return false;
			}
		
		//	got no valid connection at the moment, try and get one
		if (this.authClient == null)
			this.authClient = this.authManager.getAuthenticatedClient();
		
		//	authentication failed
		if (this.authClient == null)
			return false;
		
		//	got valid connection, flush cache if we got one
		else {
			this.fdpClient = new GoldenGateFdpClient(this.authClient);
			return true;
		}
	}
	
	private void logout() {
		try {
			this.fdpClient = null;
			if ((this.authClient != null) && this.authClient.isLoggedIn()) // might have been logged out from elsewhere
				this.authClient.logout();
			this.authClient = null;
		}
		catch (IOException ioe) {
			DialogFactory.alert(("An error occurred while logging out from GoldenGATE Server\n" + ioe.getMessage()), "Error on Logout", JOptionPane.ERROR_MESSAGE);
		}
	}
	
	private static final Comparator pathAndNameOrder = new Comparator() {
		public int compare(Object obj1, Object obj2) {
			FileDescriptor fd1 = ((FileDescriptor) obj1);
			FileDescriptor fd2 = ((FileDescriptor) obj2);
			return fd1.name.compareTo(fd2.name);
		}
	};
	
	static Dimension fileGroupListDialogSize = new Dimension(400, 250);
	static Point fileGroupListDialogLocation = null;
	private class FileGroupListDialog extends DialogPanel {
		FileGroupListDialog(FileGroupPanel[] fileGroups) {
			super("GoldenGATE Upload Manager", true);
			
			this.setLayout(new BorderLayout());
			
			JLabel label = new JLabel("Synchronize local source folders with GoldenGATE FDP", JLabel.CENTER);
			this.add(label, BorderLayout.NORTH);
			
			JPanel fileGroupPanel = new JPanel(new GridLayout(0, 1, 3, 3), true);
			for (int g = 0; g < fileGroups.length; g++)
				fileGroupPanel.add(fileGroups[g]);
			JPanel fileGroupPanelTray = new JPanel(new BorderLayout(), true);
			fileGroupPanelTray.add(fileGroupPanel, BorderLayout.NORTH);
			JScrollPane fileGroupPanelBox = new JScrollPane(fileGroupPanelTray);
			this.add(fileGroupPanelBox, BorderLayout.CENTER);
			
			JButton closeButton = new JButton("Close");
			closeButton.setBorder(BorderFactory.createRaisedBevelBorder());
			closeButton.setPreferredSize(new Dimension(100, 21));
			closeButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					dispose();
				}
			});
			JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
			buttonPanel.add(closeButton);
			this.add(buttonPanel, BorderLayout.SOUTH);
			
			if (fileGroupListDialogSize == null)
				this.setSize(400, 250);
			else this.setSize(fileGroupListDialogSize);
			if (fileGroupListDialogLocation == null)
				this.setLocationRelativeTo(this.getOwner());
			else this.setLocation(fileGroupListDialogLocation);
			this.addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					fileGroupListDialogLocation = getLocation();
					fileGroupListDialogSize = getSize();
				}
				public void windowClosed(WindowEvent e) {
					fileGroupListDialogLocation = getLocation();
					fileGroupListDialogSize = getSize();
				}
			});
		}
	}
	
	private class FileGroupPanel extends JPanel {
		final File rootFolder;
		final ConnectedFileGroup fileGroup;
		private JLabel statusLabel;
		private FileDescriptor[] localFiles = null;
		private FileDescriptor[] fdpFiles = null;
		FileGroupPanel(File rootFolder, ConnectedFileGroup fileGroup) {
			super(new BorderLayout(), true);
			this.rootFolder = rootFolder;
			this.fileGroup = fileGroup;
			
			this.statusLabel = new JLabel("<Click 'Check' to fetch overview>", JLabel.CENTER);
			JPanel labelPanel = new JPanel(new GridLayout(0, 1, 0, 3), true);
			labelPanel.add(new JLabel("<HTML><B>" + this.fileGroup.sourceFolder.fileGroupName + " / " + this.fileGroup.sourceFolder.localPath + "</B></HTML>", JLabel.CENTER));
			labelPanel.add(this.statusLabel);
			this.add(labelPanel, BorderLayout.CENTER);
			
			JButton checkButton = new JButton("Check");
			checkButton.setBorder(BorderFactory.createRaisedBevelBorder());
			checkButton.setPreferredSize(new Dimension(70, 21));
			checkButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					checkFileGroup();
				}
			});
			JButton manageButton = new JButton("Manage");
			manageButton.setBorder(BorderFactory.createRaisedBevelBorder());
			manageButton.setPreferredSize(new Dimension(70, 21));
			manageButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					manageFileGroup();
				}
			});
			
			JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER), true);
			buttonPanel.add(checkButton);
			buttonPanel.add(manageButton);
			this.add(buttonPanel, BorderLayout.EAST);
			
			this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(this.getBackground(), 2), BorderFactory.createEtchedBorder()));
		}
		private void ensureFileLists() {
			if (this.localFiles == null) {
				this.localFiles = FileDescriptor.getFileDescriptors(new File(this.rootFolder, this.fileGroup.sourceFolder.localPath), this.fileGroup);
				if ((this.localFiles != null) && (1 < this.localFiles.length))
					Arrays.sort(this.localFiles, pathAndNameOrder);
			}
			if (this.fdpFiles == null)
				this.fdpFiles = listFdpFiles(this.fileGroup);
		}
		void checkFileGroup() {
			this.ensureFileLists();
			StringBuffer status = new StringBuffer();
			status.append(this.localFiles.length + " local files");
			status.append(", ");
			if (this.fdpFiles == null)
				status.append(" FDP lookup error");
			else status.append(this.fdpFiles.length + " files on FDP");
			this.statusLabel.setText(status.toString());
		}
		void manageFileGroup() {
			this.ensureFileLists();
			FileUploadDialog fileDialog = new FileUploadDialog(this.fileGroup.fdpFileGroup.descriptor, this.localFiles, this.fdpFiles) {
				void uploadSelected() {
					FileDescriptor[] uploadFiles = this.localFiles.getSelectedFiles();
					FileDescriptor[] newFdpFiles = uploadLocalFiles(rootFolder, fileGroup, uploadFiles);
					if (newFdpFiles == null)
						return; // something went wrong
					FileGroupPanel.this.fdpFiles = newFdpFiles;
					this.fdpFiles.setFiles(FileGroupPanel.this.fdpFiles);
					for (int f = 0; f < FileGroupPanel.this.fdpFiles.length; f++)
						this.localFiles.selFileNames.remove(FileGroupPanel.this.fdpFiles[f].name);
					this.localFiles.fileTable.validate();
					this.localFiles.fileTable.repaint();
					this.localFiles.selectionChanged();
				}
				void cleanupSelected() {
					FileDescriptor[] cleanupFiles = this.fdpFiles.getSelectedFiles();
					FileDescriptor[] newFdpFiles = cleanupFdpFiles(fileGroup.fdpFileGroup.descriptor, cleanupFiles);
					if (newFdpFiles == null)
						return; // something went wrong
					FileGroupPanel.this.fdpFiles = newFdpFiles;
					this.fdpFiles.setFiles(FileGroupPanel.this.fdpFiles);
					for (int f = 0; f < FileGroupPanel.this.fdpFiles.length; f++)
						this.fdpFiles.selFileNames.remove(FileGroupPanel.this.fdpFiles[f].name);
					this.fdpFiles.selectionChanged();
				}
			};
			fileDialog.setVisible(true);
		}
	}
	
	FileDescriptor[] uploadLocalFiles(File rootFolder, ConnectedFileGroup fileGroup, FileDescriptor[] uploadFiles) {
		//	TODO add progress monitor, and upload in dedicated thread
		try {
			File localFolder = new File(rootFolder, fileGroup.sourceFolder.localPath);
			for (int f = 0; f < uploadFiles.length; f++) {
				File localFile = new File(localFolder, uploadFiles[f].name);
				this.fdpClient.updateFile(fileGroup.fdpFileGroup.descriptor.name, uploadFiles[f].name, localFile);
			}
			FileDescriptor[] fds = this.fdpClient.getFiles(fileGroup.fdpFileGroup.descriptor.name);
			Arrays.sort(fds, pathAndNameOrder);
			DialogFactory.alert((uploadFiles.length + " files uploaded to file group '" + fileGroup.fdpFileGroup.descriptor.name + "'"), "File Group Update Completed", JOptionPane.INFORMATION_MESSAGE);
			return fds;
		}
		catch (IOException ioe) {
			ioe.printStackTrace(System.out);
			DialogFactory.alert(("An error occurred while updating file group '" + fileGroup.fdpFileGroup.descriptor.name + "' on GoldenGATE FDP\n" + ioe.getMessage()), "Error Updating File Group", JOptionPane.ERROR_MESSAGE);
			return null;
		}
	}
	
	FileDescriptor[] cleanupFdpFiles(FileGroupDescriptor fileGroup, FileDescriptor[] cleanupFiles) {
		//	TODO add progress monitor, and clean up in dedicated thread
		try {
			for (int f = 0; f < cleanupFiles.length; f++)
				this.fdpClient.deleteFile(fileGroup.name, cleanupFiles[f].name);
			FileDescriptor[] fds = this.fdpClient.getFiles(fileGroup.name);
			Arrays.sort(fds, pathAndNameOrder);
			DialogFactory.alert((cleanupFiles.length + " files cleaned up from file group '" + fileGroup.name + "'"), "File Group Cleanup Completed", JOptionPane.INFORMATION_MESSAGE);
			return fds;
		}
		catch (IOException ioe) {
			ioe.printStackTrace(System.out);
			DialogFactory.alert(("An error occurred while cleaning up file group '" + fileGroup.name + "' on GoldenGATE FDP\n" + ioe.getMessage()), "Error Cleaning Up File Group", JOptionPane.ERROR_MESSAGE);
			return null;
		}
	}
	
	FileDescriptor[] listFdpFiles(ConnectedFileGroup fileGroup) {
		try {
			FileDescriptor[] fds = this.fdpClient.getFiles(fileGroup.fdpFileGroup.descriptor.name);
			Arrays.sort(fds, pathAndNameOrder);
			return fds;
		}
		catch (IOException ioe) {
			ioe.printStackTrace(System.out);
			DialogFactory.alert(("Failed to fetch content of file group '" + fileGroup.fdpFileGroup.descriptor.name + "' from GoldenGATE FDP:\r\n  " + ioe.getMessage()), "Error Fetching File Groups", JOptionPane.ERROR_MESSAGE);
			return null;
		}
	}
	
	static Dimension fileGroupDialogSize = new Dimension(900, 600);
	static Point fileGroupDialogLocation = null;
	private static class FileUploadDialog extends DialogPanel {
//		private FileGroupDescriptor fileGroup;
		FileTablePanel localFiles;
		JButton uploadButton = new JButton("Upload Selected");
		FileTablePanel fdpFiles;
		JButton cleanupButton = new JButton("Clean Up Selected");
		FileUploadDialog(FileGroupDescriptor fileGroup, FileDescriptor[] localFiles, FileDescriptor[] fdpFiles) {
			super(("Manage Files In Group " + fileGroup.name), true);
//			this.fileGroup = fileGroup;
			
			this.localFiles = new FileTablePanel(localFiles) {
				void doSelectDiff() {
					this.selFileNames.clear();
					for (int f = 0; f < this.files.size(); f++) {
						FileDescriptor fd = ((FileDescriptor) this.files.get(f));
						if (!FileUploadDialog.this.fdpFiles.contains(fd))
							this.selFileNames.add(fd.name);
					}
				}
				void selectionChanged() {
					uploadButton.setEnabled(this.selFileNames.size() != 0);
				}
			};
			this.uploadButton.setBorder(BorderFactory.createRaisedBevelBorder());
			this.uploadButton.setPreferredSize(new Dimension(120, 21));
			this.uploadButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					uploadSelected();
				}
			});
			this.localFiles.addButton(this.uploadButton);
			this.localFiles.selectionChanged();
			
			this.fdpFiles = new FileTablePanel(fdpFiles) {
				void doSelectDiff() {
					this.selFileNames.clear();
					for (int f = 0; f < this.files.size(); f++) {
						FileDescriptor fd = ((FileDescriptor) this.files.get(f));
						if (!FileUploadDialog.this.localFiles.contains(fd))
							this.selFileNames.add(fd.name);
					}
				}
				void selectionChanged() {
					cleanupButton.setEnabled(this.selFileNames.size() != 0);
				}
			};
			this.cleanupButton.setBorder(BorderFactory.createRaisedBevelBorder());
			this.cleanupButton.setPreferredSize(new Dimension(120, 21));
			this.cleanupButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					cleanupSelected();
				}
			});
			this.fdpFiles.addButton(this.cleanupButton);
			this.fdpFiles.selectionChanged();
			
			JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, this.localFiles, this.fdpFiles);
			split.setDividerLocation(0.5);
			split.setResizeWeight(0.5);
			this.add(split);
			if (fileGroupDialogSize == null)
				this.setSize(900, 600);
			else this.setSize(fileGroupDialogSize);
			if (fileGroupDialogLocation == null)
				this.setLocationRelativeTo(this.getDialog().getOwner());
			else this.setLocation(fileGroupDialogLocation);
			this.addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					fileGroupDialogLocation = getLocation();
					fileGroupDialogSize = getSize();
				}
				public void windowClosed(WindowEvent e) {
					fileGroupDialogLocation = getLocation();
					fileGroupDialogSize = getSize();
				}
			});
		}
		void uploadSelected() {}
		void cleanupSelected() {}
	}
	
	private static class FileTablePanel extends JPanel {
		static final SimpleDateFormat lastModifiedDateFormat = new UtcDateFormat("yyyy-MM-dd HH:mm");
		final ArrayList files = new ArrayList();
		private HashMap fileKeysToDescriptors = new HashMap();
		final HashSet selFileNames = new HashSet();
		final JTable fileTable;
		private JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER), true);;
		FileTablePanel(FileDescriptor[] files) {
			super(new BorderLayout(), true);
			
			for (int f = 0; f < files.length; f++) {
				this.files.add(files[f]);
				this.fileKeysToDescriptors.put(getFileKey(files[f]), files[f]);
			}
			this.fileTable = new JTable(new TableModel() {
				public Class getColumnClass(int columnIndex) {
					return ((columnIndex == 0) ? Boolean.class : String.class);
				}
				public int getColumnCount() {
					return 4;
				}
				public String getColumnName(int columnIndex) {
					if (columnIndex == 0)
						return "Selected";
					else if (columnIndex == 1)
						return "File Name";
					else if (columnIndex == 2)
						return "Size (Bytes)";
					else if (columnIndex == 3)
						return "Last Modified";
					else return null;
				}
				public int getRowCount() {
					return FileTablePanel.this.files.size();
				}
				public Object getValueAt(int rowIndex, int columnIndex) {
					FileDescriptor fd = ((FileDescriptor) FileTablePanel.this.files.get(rowIndex));
					if (columnIndex == 0)
						return new Boolean(selFileNames.contains(fd.name));
					else if (columnIndex == 1)
						return fd.name.replace("<", "&lt;").replace(">", "&gt;");
					else if (columnIndex == 2) {
						String rawSize = ("" + fd.size);
						int groups = (rawSize.length() / 3);
						int groupStart = (rawSize.length() - (groups * 3));
						StringBuffer size = new StringBuffer(rawSize.substring(0, groupStart));
						for (int s = groupStart; s < rawSize.length(); s += 3) {
							if (0 < s)
								size.append(",");
							size.append(rawSize.substring(s, (s+3)));
						}
						return size.toString();
					}
					else if (columnIndex == 3)
						return lastModifiedDateFormat.format(new Date(fd.lastMod));
					else return null;
				}
				public boolean isCellEditable(int rowIndex, int columnIndex) {
					return (columnIndex == 0);
				}
				public void setValueAt(Object newValue, int rowIndex, int columnIndex) {
					if (columnIndex != 0)
						return;
					FileDescriptor fd = ((FileDescriptor) FileTablePanel.this.files.get(rowIndex));
					if (((Boolean) newValue).booleanValue())
						selFileNames.add(fd.name);
					else selFileNames.remove(fd.name);
					selectionChanged();
				}
				public void addTableModelListener(TableModelListener tml) {}
				public void removeTableModelListener(TableModelListener tml) {}
			});
			this.fileTable.setDefaultRenderer(String.class, new DefaultTableCellRenderer() {
				public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
					Component cellComp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
					if (cellComp instanceof JLabel) {
						if (column == 1)
							((JLabel) cellComp).setHorizontalAlignment(JLabel.LEFT);
						else if (column == 2)
							((JLabel) cellComp).setHorizontalAlignment(JLabel.RIGHT);
						else if (column == 3)
							((JLabel) cellComp).setHorizontalAlignment(JLabel.CENTER);
					}
					return cellComp;
				}
			});
			TableColumnModel colModel = this.fileTable.getColumnModel();
			colModel.getColumn(0).setMinWidth(40);
			colModel.getColumn(0).setPreferredWidth(50);
			colModel.getColumn(0).setMaxWidth(60);
			colModel.getColumn(2).setMinWidth(70);
			colModel.getColumn(2).setPreferredWidth(80);
			colModel.getColumn(2).setMaxWidth(110);
			colModel.getColumn(3).setMinWidth(70);
			colModel.getColumn(3).setPreferredWidth(100);
			colModel.getColumn(3).setMaxWidth(130);
			JScrollPane fileTableBox = new JScrollPane(this.fileTable);
			
			JButton selectAll = new JButton("Select All");
			selectAll.setBorder(BorderFactory.createRaisedBevelBorder());
			selectAll.setPreferredSize(new Dimension(80, 21));
			selectAll.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					selectAll();
				}
			});
			JButton selectNone = new JButton("Select None");
			selectNone.setBorder(BorderFactory.createRaisedBevelBorder());
			selectNone.setPreferredSize(new Dimension(80, 21));
			selectNone.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					selectNone();
				}
			});
			JButton selectDiff = new JButton("Select Diff");
			selectDiff.setBorder(BorderFactory.createRaisedBevelBorder());
			selectDiff.setPreferredSize(new Dimension(80, 21));
			selectDiff.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					selectDiff();
				}
			});
			this.buttonPanel.add(selectAll);
			this.buttonPanel.add(selectNone);
			this.buttonPanel.add(selectDiff);
			
			this.add(fileTableBox, BorderLayout.CENTER);
			this.add(this.buttonPanel, BorderLayout.SOUTH);
		}
		private static String getFileKey(FileDescriptor fd) {
			return (fd.name + ":" + fd.size);
		}
		boolean contains(FileDescriptor fd) {
			FileDescriptor mfd = ((FileDescriptor) this.fileKeysToDescriptors.get(getFileKey(fd)));
			if (mfd == null)
				return false;
//			else return (Math.abs(fd.lastMod - mfd.lastMod) < 1000); // some 32-bit file systems only store timestamp down to seconds
//			long lastModDiff = Math.abs(fd.lastMod - mfd.lastMod);
//			System.out.println("File modification time difference for '" + fd.name + "' is " + lastModDiff + "ms");
//			return (lastModDiff < 1000); // some 32-bit file systems only store timestamp down to seconds
			//	==> TURNS OUT, the ZIP entries used for transfer convert timestamps internally, and it's _lossy_
			//	==> need some more tolerance, going with 3 seconds for now (which is _very_ unlikely to incur false matches in practice)
			else return (Math.abs(fd.lastMod - mfd.lastMod) < (1000 * 3)); // some 32-bit file systems only store timestamp down to seconds
		}
		void setFiles(FileDescriptor[] files) {
			this.files.clear();
			this.fileKeysToDescriptors.clear();
			for (int f = 0; f < files.length; f++) {
				this.files.add(files[f]);
				this.fileKeysToDescriptors.put(getFileKey(files[f]), files[f]);
			}
			this.fileTable.revalidate();
			this.fileTable.repaint();
		}
		FileDescriptor[] getSelectedFiles() {
			ArrayList selFiles = null;
			for (int f = 0; f < this.files.size(); f++) {
				FileDescriptor fd = ((FileDescriptor) this.files.get(f));
				if (this.selFileNames.contains(fd.name)) {
					if (selFiles == null)
						selFiles = new ArrayList();
					selFiles.add(fd);
				}
			}
			return ((selFiles == null) ? new FileDescriptor[0] : ((FileDescriptor[]) selFiles.toArray(new FileDescriptor[selFiles.size()])));
		}
		void selectAll() {
			for (int f = 0; f < this.files.size(); f++)
				this.selFileNames.add(((FileDescriptor) this.files.get(f)).name);
			this.fileTable.validate();
			this.fileTable.repaint();
			this.selectionChanged();
		}
		void selectNone() {
			this.selFileNames.clear();
			this.fileTable.validate();
			this.fileTable.repaint();
			this.selectionChanged();
		}
		void selectDiff() {
			this.doSelectDiff();
			this.fileTable.validate();
			this.fileTable.repaint();
			this.selectionChanged();
		}
		void doSelectDiff() {} // TODOne use this to wire up local/remote diff
		void selectionChanged() {} // TODOne use this to wire up selection changes
		void addButton(JButton button) {
			this.buttonPanel.add(button);
		}
	}
	
	public static void main(String[] args) throws Exception {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {}
		
		//	TODOne load file list from local folder
		File localRoot = new File("E:/GoldenGATEv3.Apps");
		File localSource = new File(localRoot, "_Updates");
		ArrayList fdList = new ArrayList();
		File[] localFiles = localSource.listFiles();
		for (int f = 0; f < localFiles.length; f++)
			fdList.add(new FileDescriptor(localFiles[f].getName(), ((int) localFiles[f].length()), localFiles[f].lastModified()));
		
		//	TODOne show file table panel so we can adjust layout
		FileDescriptor[] fds = (FileDescriptor[]) fdList.toArray(new FileDescriptor[fdList.size()]);
		FileGroupDescriptor[] fgs = FileGroupDescriptor.readFileGroupDescriptors(new BufferedReader(new StringReader(localSource.getName() + "\t.*\\.zip")));
		FileUploadDialog fileDialog = new FileUploadDialog(fgs[0], fds, fds);
		fileDialog.setVisible(true);
	}
}
