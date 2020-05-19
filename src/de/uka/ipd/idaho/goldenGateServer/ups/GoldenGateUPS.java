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
 *     * Neither the name of the Universitaet Karlsruhe (TH) nor the
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
package de.uka.ipd.idaho.goldenGateServer.ups;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;

import de.uka.ipd.idaho.easyIO.EasyIO;
import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.SqlQueryResult;
import de.uka.ipd.idaho.easyIO.sql.TableDefinition;
import de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent;
import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponentRegistry;
import de.uka.ipd.idaho.goldenGateServer.uaa.UserAccessAuthority;
import de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthorityRBAC;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * The GoldenGATE User Permission Server (UPS) manages roles and permissions of
 * users managed by a backing GoldenGATE UAA. The permissions have the form of
 * plain Strings, ie permission names, which can be granted to a specific user
 * or to a role. Roles, in turn, are sets of other roles and permissions, which
 * as a whole can be granted to a specific user or a role. Circles in roles are
 * forbidden and result in an error if tried to be created.
 * 
 * @author sautter
 */
public class GoldenGateUPS extends AbstractGoldenGateServerComponent implements UserPermissionAuthorityRBAC, GoldenGateUpsConstants {
	
	private static final int ROLE_NAME_MAX_LENGTH = 127;
	private static final String ROLE_NAME_PATTERN = ("[a-zA-Z][a-zA-Z0-9\\-\\_\\@\\.]{1," + (ROLE_NAME_MAX_LENGTH - 1) + "}");
	private static final int PERMISSION_NAME_MAX_LENGTH = 128;
	private static final String PERMISSION_NAME_PATTERN = ("[a-zA-Z][a-zA-Z0-9\\-\\_\\@\\.]{1," + (PERMISSION_NAME_MAX_LENGTH - 1) + "}");
	
	private UserAccessAuthority uaa = null;
	
	private static final String USER_ROLE_TABLE_NAME = "GgUpsUserRoles";
	private static final String ROLE_TABLE_NAME = "GgUpsRoles";
	private static final String ROLE_PERMISSION_TABLE_NAME = "GgUpsRolePermissions";
	
	private static final String USER_NAME_COLUMEN_NAME = "UserName";
	private static final String ROLE_NAME_COLUMEN_NAME = "RoleName";
	private static final String PERMISSION_NAME_COLUMEN_NAME = "PermissionName";
	private static final String IS_ROLE_OR_PERMISSION_COLUMEN_NAME = "Type";
	
	private IoProvider io;
	
	/** Constructor passing 'UPS' as the letter code to super constructor
	 */
	public GoldenGateUPS() {
		super("UPS");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateScf.AbstractServerComponent#initComponent()
	 */
	protected void initComponent() {
		
		//	get and check database connection
		this.io = this.host.getIoProvider();
		if (!this.io.isJdbcAvailable())
			throw new RuntimeException("GgServerUPS cannot work without database access.");
		
		//	ensure user -> role table
		TableDefinition urTd = new TableDefinition(USER_ROLE_TABLE_NAME);
		urTd.addColumn(USER_NAME_COLUMEN_NAME, TableDefinition.VARCHAR_DATATYPE, UserAccessAuthority.USER_NAME_MAX_LENGTH);
		urTd.addColumn(ROLE_NAME_COLUMEN_NAME, TableDefinition.VARCHAR_DATATYPE, ROLE_NAME_MAX_LENGTH);
		if (!this.io.ensureTable(urTd, true))
			throw new RuntimeException("GgServerUPS cannot work without database access.");
		
		//	ensure roles table
		TableDefinition rTd = new TableDefinition(ROLE_TABLE_NAME);
		rTd.addColumn(ROLE_NAME_COLUMEN_NAME, TableDefinition.VARCHAR_DATATYPE, ROLE_NAME_MAX_LENGTH);
		if (!this.io.ensureTable(rTd, true))
			throw new RuntimeException("GgServerUPS cannot work without database access.");
		
		//	ensure roles -> roles & permissions table
		TableDefinition rpTd = new TableDefinition(ROLE_PERMISSION_TABLE_NAME);
		rpTd.addColumn(ROLE_NAME_COLUMEN_NAME, TableDefinition.VARCHAR_DATATYPE, ROLE_NAME_MAX_LENGTH);
		rpTd.addColumn(IS_ROLE_OR_PERMISSION_COLUMEN_NAME, TableDefinition.CHAR_DATATYPE, 1);
		rpTd.addColumn(PERMISSION_NAME_COLUMEN_NAME, TableDefinition.VARCHAR_DATATYPE, PERMISSION_NAME_MAX_LENGTH);
		if (!this.io.ensureTable(rpTd, true))
			throw new RuntimeException("GgServerUPS cannot work without database access.");
		
		
		//	load all roles (cannot be loaded lazily due to listing)
		String query = "SELECT " + ROLE_NAME_COLUMEN_NAME + " FROM " + ROLE_TABLE_NAME + ";";
		try {
			SqlQueryResult sqr = this.io.executeSelectQuery(query);
			while (sqr.next()) {
				String roleName = sqr.getString(0);
				Role role = this.loadRole(roleName);
				this.roles.put(roleName, role);
			}
		}
		catch (SQLException sqle) {
			System.out.println("GgServerUPS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while loading roles.");
			System.out.println("  query was " + query);
		}
		
		//	make sure built-in roles exist
		if (!this.roles.containsKey(DEFAULT_ROLE_NAME)) {
			Role defaultRole = new Role(DEFAULT_ROLE_NAME);
			query = "INSERT INTO " + ROLE_TABLE_NAME + " (" + ROLE_NAME_COLUMEN_NAME + ") VALUES ('" + EasyIO.sqlEscape(DEFAULT_ROLE_NAME) + "');";
			try {
				this.io.executeUpdateQuery(query);
				this.roles.put(DEFAULT_ROLE_NAME, defaultRole);
			}
			catch (SQLException sqle) {
				System.out.println("GgServerUPS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while creating default role.");
				System.out.println("  query was " + query);
			}
		}
		if (!this.roles.containsKey(GUEST_ROLE_NAME)) {
			Role guestRole = new Role(GUEST_ROLE_NAME);
			query = "INSERT INTO " + ROLE_TABLE_NAME + " (" + ROLE_NAME_COLUMEN_NAME + ") VALUES ('" + EasyIO.sqlEscape(GUEST_ROLE_NAME) + "');";
			try {
				this.io.executeUpdateQuery(query);
				this.roles.put(GUEST_ROLE_NAME, guestRole);
			}
			catch (SQLException sqle) {
				System.out.println("GgServerUPS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while creating guest role.");
				System.out.println("  query was " + query);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.AbstractGoldenGateServerComponent#link()
	 */
	public void link() {
		
		//	get access authority
		this.uaa = ((UserAccessAuthority) GoldenGateServerComponentRegistry.getServerComponent(UserAccessAuthority.class.getName()));
		
		//	check success
		if (this.uaa == null) throw new RuntimeException(UserAccessAuthority.class.getName());
		
		//	link up to access authority
		else this.uaa.setUserPermissionAuthority(this);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateScf.ServerComponent#getActions()
	 */
	public ComponentAction[] getActions() {
		ArrayList cal = new ArrayList();
		ComponentAction ca;
		
		//	get the permissions for a user logged in on a session
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return GET_GRANTED_PERMISSIONS;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				
				//	get user name
				String userName = input.readLine();
				
				//	get permissions
				String[] permissions = getPermissions(userName);
				
				//	indicate permissions coming
				output.write(GET_GRANTED_PERMISSIONS);
				output.newLine();
				
				//	send permissions
				for (int p = 0; p < permissions.length; p++) {
					output.write(permissions[p]);
					output.newLine();
				}
			}
		};
		cal.add(ca);
		
		
		ca = new ListAction(GET_USERS) {
			String[] getList() throws IOException {
				return uaa.getUserNames();
			}
		};
		cal.add(ca);
		
		ca = new ListAction(GET_ROLES) {
			String[] getList() throws IOException {
				return getRoles();
			}
		};
		cal.add(ca);
		
		ca = new ListAction(GET_PERMISSIONS) {
			String[] getList() throws IOException {
				return getPermissions();
			}
		};
		cal.add(ca);
		
		
		ca = new GetRolesOrPermissionsAction(GET_USER_ROLES) {
			String[] getRolesOrPermissions(String userOrRoleName) throws IOException {
				return ((userOrRoleName.length() == 0) ? getRoles() : getAssignedRoles(userOrRoleName, false));
			}
		};
		cal.add(ca);
		
		ca = new ModifyRolesOrPermissionsAction(SET_USER_ROLES) {
			String performAction(String userOrRoleName, String[] rolesOrPermissions) {
				HashSet removeRoles = new HashSet(Arrays.asList(getImpliedRoles(userOrRoleName, false)));
				HashSet addRoles = new HashSet(Arrays.asList(rolesOrPermissions));
				HashSet keepRoles = new HashSet(removeRoles);
				keepRoles.retainAll(addRoles);
				removeRoles.removeAll(keepRoles);
				addRoles.removeAll(keepRoles);
				String error = removeRoles(userOrRoleName, ((String[]) removeRoles.toArray(new String[removeRoles.size()])));
				return ((error == null) ? assignRoles(userOrRoleName, ((String[]) addRoles.toArray(new String[addRoles.size()]))) : error);
			}
		};
		cal.add(ca);
		
		
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return CREATE_ROLE;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				else if (!uaa.isAdminSession(sessionId)) {
					output.write("Administrative priviledges required");
					output.newLine();
					return;
				}
				
				//	get role name
				String roleName = input.readLine();
				
				//	create role
				String error = createRole(roleName);
				
				//	send response
				output.write((error == null) ? CREATE_ROLE : error);
				output.newLine();
			}
		};
		cal.add(ca);
		
		ca = new ComponentActionNetwork() {
			public String getActionCommand() {
				return DELETE_ROLE;
			}
			public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
				
				//	check authentication
				String sessionId = input.readLine();
				if (!uaa.isValidSession(sessionId)) {
					output.write("Invalid session (" + sessionId + ")");
					output.newLine();
					return;
				}
				else if (!uaa.isAdminSession(sessionId)) {
					output.write("Administrative priviledges required");
					output.newLine();
					return;
				}
				
				//	get role name
				String roleName = input.readLine();
				
				//	delete role
				String error = deleteRole(roleName);
				
				//	send response
				output.write((error == null) ? DELETE_ROLE : error);
				output.newLine();
			}
		};
		cal.add(ca);
		
		
		
		ca = new GetRolesOrPermissionsAction(GET_ROLE_PERMISSIONS) {
			String[] getRolesOrPermissions(String userOrRoleName) throws IOException {
				return getGrantedPermissions(userOrRoleName, false);
			}
		};
		cal.add(ca);
		
		ca = new ModifyRolesOrPermissionsAction(SET_ROLE_PERMISSIONS) {
			String performAction(String userOrRoleName, String[] rolesOrPermissions) {
				HashSet removePermissions = new HashSet(Arrays.asList(getGrantedPermissions(userOrRoleName, false)));
				HashSet addPermissions = new HashSet(Arrays.asList(rolesOrPermissions));
				HashSet keepPermissions = new HashSet(removePermissions);
				keepPermissions.retainAll(addPermissions);
				removePermissions.removeAll(keepPermissions);
				addPermissions.removeAll(keepPermissions);
				String error = revokePermissions(userOrRoleName, ((String[]) removePermissions.toArray(new String[removePermissions.size()])));
				return ((error == null) ? grantPermissions(userOrRoleName, ((String[]) addPermissions.toArray(new String[addPermissions.size()]))) : error);
			}
		};
		cal.add(ca);
		
		
		ca = new GetRolesOrPermissionsAction(GET_ROLE_ROLES) {
			String[] getRolesOrPermissions(String userOrRoleName) throws IOException {
				return getImpliedRoles(userOrRoleName, false);
			}
		};
		cal.add(ca);
		
		ca = new ModifyRolesOrPermissionsAction(SET_ROLE_ROLES) {
			String performAction(String userOrRoleName, String[] rolesOrPermissions) {
				HashSet removeRoles = new HashSet(Arrays.asList(getImpliedRoles(userOrRoleName, false)));
				HashSet addRoles = new HashSet(Arrays.asList(rolesOrPermissions));
				HashSet keepRoles = new HashSet(removeRoles);
				keepRoles.retainAll(addRoles);
				removeRoles.removeAll(keepRoles);
				addRoles.removeAll(keepRoles);
				String error = unimplyRoles(userOrRoleName, ((String[]) removeRoles.toArray(new String[removeRoles.size()])));
				return ((error == null) ? implyRoles(userOrRoleName, ((String[]) addRoles.toArray(new String[addRoles.size()]))) : error);
			}
		};
		cal.add(ca);
		
		
		ca = new ListRolesOrPermissionsActionConsole(LIST_USER_ROLES_COMMAND, "user", "role") {
			String[] getRolesOrPermissions(String userOrRoleName) {
				return getAssignedRoles(userOrRoleName, false);
			}
		};
		cal.add(ca);
		
		ca = new ModifyRolesOrPermissionsActionConsole(ASSIGN_USER_ROLE_COMMAND, "user", "role", "assign", "to") {
			String performAction(String userOrRoleName, String roleOrPermission) {
				return assignRole(userOrRoleName, roleOrPermission);
			}
		};
		cal.add(ca);
		
		ca = new ModifyRolesOrPermissionsActionConsole(REMOVE_USER_ROLE_COMMAND, "user", "role", "remove", "from") {
			String performAction(String userOrRoleName, String roleOrPermission) {
				return removeRole(userOrRoleName, roleOrPermission);
			}
		};
		cal.add(ca);
		
		
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return CREATE_ROLE_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						CREATE_ROLE_COMMAND + " <roleName>",
						"Create a new role:",
						"- <roleName>: the name for the new role"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1) {
					String error = createRole(arguments[0]);
					if (error == null)
						this.reportResult(" Role '" + arguments[0] + "' created successfully.");
					else this.reportError(" Error creating role '" + arguments[0] + "': " + error);
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the role name only.");
			}
		};
		cal.add(ca);
		
		ca = new ComponentActionConsole() {
			public String getActionCommand() {
				return DELETE_ROLE_COMMAND;
			}
			public String[] getExplanation() {
				String[] explanation = {
						DELETE_ROLE_COMMAND + " <roleName>",
						"Delete new role:",
						"- <roleName>: the name of the role to delete"
					};
				return explanation;
			}
			public void performActionConsole(String[] arguments) {
				if (arguments.length == 1) {
					String error = deleteRole(arguments[0]);
					if (error == null)
						this.reportResult(" Role '" + arguments[0] + "' deleted successfully.");
					else this.reportError(" Error deleting role '" + arguments[0] + "': " + error);
				}
				else this.reportError(" Invalid arguments for '" + this.getActionCommand() + "', specify the role name only.");
			}
		};
		cal.add(ca);
		
		
		ca = new ListRolesOrPermissionsActionConsole(LIST_ROLE_PERMISSIONS_COMMAND, "role", "permission") {
			String[] getRolesOrPermissions(String userOrRoleName) {
				return getGrantedPermissions(userOrRoleName, false);
			}
		};
		cal.add(ca);
		
		ca = new ModifyRolesOrPermissionsActionConsole(GRANT_ROLE_PERMISSION_COMMAND, "role", "permission", "grant", "to") {
			String performAction(String userOrRoleName, String roleOrPermission) {
				return grantPermission(userOrRoleName, roleOrPermission);
			}
		};
		cal.add(ca);
		
		ca = new ModifyRolesOrPermissionsActionConsole(REVOKE_ROLE_PERMISSION_COMMAND, "role", "permission", "revoke", "from") {
			String performAction(String userOrRoleName, String roleOrPermission) {
				return revokePermission(userOrRoleName, roleOrPermission);
			}
		};
		cal.add(ca);
		
		
		ca = new ListRolesOrPermissionsActionConsole(LIST_IMPLIED_ROLES_COMMAND, "role", "role") {
			String[] getRolesOrPermissions(String userOrRoleName) {
				return getImpliedRoles(userOrRoleName, false);
			}
		};
		cal.add(ca);
		
		ca = new ModifyRolesOrPermissionsActionConsole(IMPLY_ROLE_COMMAND, "role", "role", "imply", "") {
			String performAction(String userOrRoleName, String roleOrPermission) {
				return implyRole(userOrRoleName, roleOrPermission);
			}
		};
		cal.add(ca);
		
		ca = new ModifyRolesOrPermissionsActionConsole(UNIMPLY_ROLE_COMMAND, "role", "role", "unimply", "") {
			String performAction(String userOrRoleName, String roleOrPermission) {
				return unimplyRole(userOrRoleName, roleOrPermission);
			}
		};
		cal.add(ca);
		
		return ((ComponentAction[]) cal.toArray(new ComponentAction[cal.size()]));
	}
	
	private static final String LIST_USER_ROLES_COMMAND = "assignedRoles";
	private static final String ASSIGN_USER_ROLE_COMMAND = "assignRole";
	private static final String REMOVE_USER_ROLE_COMMAND = "removeRole";
	
	private static final String CREATE_ROLE_COMMAND = "createRole";
	private static final String DELETE_ROLE_COMMAND = "deleteRole";
	
	private static final String LIST_ROLE_PERMISSIONS_COMMAND = "grantedPerms";
	private static final String GRANT_ROLE_PERMISSION_COMMAND = "grantPerm";
	private static final String REVOKE_ROLE_PERMISSION_COMMAND = "revokePerm";
	
	private static final String LIST_IMPLIED_ROLES_COMMAND = "impliedRoles";
	private static final String IMPLY_ROLE_COMMAND = "implyRole";
	private static final String UNIMPLY_ROLE_COMMAND = "unimplyRole";
	
	private abstract class ListAction extends ComponentActionNetwork {
		private String actionCommand;
		ListAction(String actionCommand) {
			this.actionCommand = actionCommand;
		}
		public String getActionCommand() {
			return this.actionCommand;
		}
		public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
			
			//	check authentication
			String sessionId = input.readLine();
			if (!uaa.isValidSession(sessionId)) {
				output.write("Invalid session (" + sessionId + ")");
				output.newLine();
				return;
			}
			else if (!uaa.isAdminSession(sessionId)) {
				output.write("Administrative priviledges required");
				output.newLine();
				return;
			}
			
			//	get list
			String[] list;
			try {
				list = getList();
			}
			catch (IOException ioe) {
				output.write(ioe.getMessage());
				output.newLine();
				return;
			}
			
			//	indicate success
			output.write(this.actionCommand);
			output.newLine();
			
			//	send list
			for (int l = 0; l < list.length; l++) {
				output.write(list[l]);
				output.newLine();
			}
		}
		abstract String[] getList() throws IOException;
	}
	
	private abstract class GetRolesOrPermissionsAction extends ComponentActionNetwork {
		private String actionCommand;
		GetRolesOrPermissionsAction(String actionCommand) {
			this.actionCommand = actionCommand;
		}
		public String getActionCommand() {
			return this.actionCommand;
		}
		public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
			
			//	check authentication
			String sessionId = input.readLine();
			if (!uaa.isValidSession(sessionId)) {
				output.write("Invalid session (" + sessionId + ")");
				output.newLine();
				return;
			}
			else if (!uaa.isAdminSession(sessionId)) {
				output.write("Administrative priviledges required");
				output.newLine();
				return;
			}
			
			//	get user name
			String userOrRoleName = input.readLine();
			
			//	get roles or permissions
			String[] rolesOrPermissions;
			try {
				rolesOrPermissions = getRolesOrPermissions(userOrRoleName);
			}
			catch (IOException ioe) {
				output.write(ioe.getMessage());
				output.newLine();
				return;
			}
			
			//	indicate success
			output.write(this.actionCommand);
			output.newLine();
			
			//	send permissions
			for (int rp = 0; rp < rolesOrPermissions.length; rp++) {
				output.write(rolesOrPermissions[rp]);
				output.newLine();
			}
		}
		abstract String[] getRolesOrPermissions(String userOrRoleName) throws IOException;
	}
	
	private abstract class ModifyRolesOrPermissionsAction extends ComponentActionNetwork {
		private String actionCommand;
		ModifyRolesOrPermissionsAction(String actionCommand) {
			this.actionCommand = actionCommand;
		}
		public String getActionCommand() {
			return this.actionCommand;
		}
		public void performActionNetwork(BufferedReader input, BufferedWriter output) throws IOException {
			
			//	check authentication
			String sessionId = input.readLine();
			if (!uaa.isValidSession(sessionId)) {
				output.write("Invalid session (" + sessionId + ")");
				output.newLine();
				return;
			}
			else if (!uaa.isAdminSession(sessionId)) {
				output.write("Administrative priviledges required");
				output.newLine();
				return;
			}
			
			//	get user name
			String userOrRoleName = input.readLine();
			
			//	get roles or permissions
			StringVector rolesOrPermissions = new StringVector();
			String roleOrPermission;
			while (((roleOrPermission = input.readLine()) != null) && (roleOrPermission.length() != 0))
				rolesOrPermissions.addElementIgnoreDuplicates(roleOrPermission);
			
			//	perform actual action
			String error = this.performAction(userOrRoleName, rolesOrPermissions.toStringArray());
			
			//	indicate success
			if (error == null) {
				output.write(this.actionCommand);
				output.newLine();
			}
			
			//	indicate error
			else {
				output.write(error);
				output.newLine();
			}
		}
		abstract String performAction(String userOrRoleName, String[] rolesOrPermissions);
	}
	
	private abstract class ModifyRolesOrPermissionsActionConsole extends ComponentActionConsole {
		private String actionCommand;
		private String subject;
		private String object;
		private String action;
		private String actionPrep;
		ModifyRolesOrPermissionsActionConsole(String actionCommand, String subject, String object, String action, String actionPreposition) {
			this.actionCommand = actionCommand;
			this.subject = subject;
			this.object = object;
			this.action = action;
			this.actionPrep = actionPreposition;
		}
		public String getActionCommand() {
			return this.actionCommand;
		}
		public String[] getExplanation() {
			String[] explanation = {
					this.getActionCommand() + " <" + this.subject + "Name> <" + this.object + ">",
					this.action + " a " + this.object + " " + this.actionPrep + " a " + this.subject + ":",
					"- <" + this.subject + "Name>: the name of the " + this.subject + " to " + this.action + " the " + this.object + " " + this.actionPrep,
					"- <" + this.object + ">: the " + this.object + " to " + this.action
				};
			return explanation;
		}
		public void performActionConsole(String[] arguments) {
			if (arguments.length == 2) {
				String error = this.performAction(arguments[0], arguments[1]);
				if (error == null)
					this.reportResult(" " + this.object + " '" + arguments[1] + "' " + this.action + "ed successfully " + this.actionPrep + " " + this.subject + " '" + arguments[0] + "'.");
				else {
					this.reportError(" Error " + this.action + "ing " + this.object + " '" + arguments[1] + "' " + this.actionPrep + " " + this.subject + " '" + arguments[0] + "': " + error);
					this.reportError(error);
				}
			}
			else this.reportError(this.getActionCommand() + " requires 2 arguments: the " + this.subject + " to modify, and the " + this.object + " to " + this.action + ".");
		}
		abstract String performAction(String userOrRoleName, String roleOrPermission);
	}
	
	private abstract class ListRolesOrPermissionsActionConsole extends ComponentActionConsole {
		private String actionCommand;
		private String subject;
		private String object;
		ListRolesOrPermissionsActionConsole(String actionCommand, String subject, String object) {
			this.actionCommand = actionCommand;
			this.subject = subject;
			this.object = object;
		}
		public String getActionCommand() {
			return this.actionCommand;
		}
		public String[] getExplanation() {
			String[] explanation = {
					this.getActionCommand() + " <" + this.subject + "Name>",
					"List the " + this.object + "s of a " + this.subject + ":",
					"- <" + this.subject + "Name>: the name of the " + this.subject + " to list the " + this.object + "s for"
				};
			return explanation;
		}
		public void performActionConsole(String[] arguments) {
			if (arguments.length == 1) {
				try {
					String[] rolesOrPermissions = this.getRolesOrPermissions(arguments[0]);
					this.reportResult(this.object + "s of " + this.subject + " '" + arguments[0] + "'");
					for (int rp = 0; rp < rolesOrPermissions.length; rp++)
						this.reportResult("  - " + rolesOrPermissions[rp]);
				}
				catch (IOException ioe) {
					this.reportError(ioe.getMessage());
				}
				
			}
			else this.reportError(this.getActionCommand() + " requires 1 argument: the " + this.subject + " to list the " + this.object + "s for.");
		}
		abstract String[] getRolesOrPermissions(String userOrRoleName) throws IOException;
	}
	
	private class User {
		final String name;
		TreeSet roles = new TreeSet();
		User(String name) {
			this.name = name;
		}
	}
	
	private class Role {
		final String name;
		TreeSet roles = new TreeSet();
		TreeSet permissions = new TreeSet();
		Role(String name) {
			this.name = name;
		}
	}
	
	private TreeMap users = new TreeMap();
	private TreeMap roles = new TreeMap();
	private TreeSet permissions = new TreeSet();
	
	//	get all roles effectively implied by the ones in the start set
	private TreeSet resolveRoleNames(TreeSet startRoles) {
		TreeSet effectiveRoles = new TreeSet();
		
		for (Iterator rit = startRoles.iterator(); rit.hasNext();) {
			String roleName = rit.next().toString();
			Role role = this.getRole(roleName);
			if (role != null) {
				effectiveRoles.add(roleName);
				effectiveRoles.addAll(this.resolveRoleNames(role.roles));
			}
		}
		
		return effectiveRoles;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthority#getPermissions()
	 */
	public String[] getPermissions() {
		TreeSet permissions = new TreeSet(this.permissions);
		return ((String[]) permissions.toArray(new String[permissions.size()]));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthorityRBAC#getRoles()
	 */
	public String[] getRoles() {
		TreeSet roleNames = new TreeSet(this.roles.keySet());
		return ((String[]) roleNames.toArray(new String[roleNames.size()]));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthority#getPermissions(java.lang.String)
	 */
	public String[] getPermissions(String userName) {
		User user = this.getUser(userName);
		if (user == null) return new String[0];
		
		StringVector permissions = new StringVector();
		
		String[] roleNames = this.getAssignedRoles(userName, true);
		for (int r = 0; r < roleNames.length; r++) {
			Role role = this.getRole(roleNames[r]);
			if (role != null) {
				for (Iterator pit = role.permissions.iterator(); pit.hasNext();) {
					String permission = ((String) pit.next());
					if (this.permissions.contains(permission))
						permissions.addElementIgnoreDuplicates(permission);
				}
			}
		}
		
		return permissions.toStringArray();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthority#hasPermission(java.lang.String, java.lang.String)
	 */
	public boolean hasPermission(String userName, String permission) {
		User user = this.getUser(userName);
		if (user == null) return false;
		
		String[] roleNames = this.getAssignedRoles(userName, true);
		for (int r = 0; r < roleNames.length; r++) {
			Role role = this.getRole(roleNames[r]);
			if ((role != null) && role.permissions.contains(permission))
				return true;
		}
		
		return false;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthorityRBAC#getAssignedRoles(java.lang.String, boolean)
	 */
	public String[] getAssignedRoles(String userName, boolean recurse) {
		User user = this.getUser(userName);
		if (user == null) return new String[0];
		
		TreeSet roles = new TreeSet();
		if (user.roles.contains(GUEST_ROLE_NAME))
			roles.add(GUEST_ROLE_NAME);
		
		else {
			roles.add(DEFAULT_ROLE_NAME);
			roles.addAll(user.roles);
			if (recurse)
				roles.addAll(this.resolveRoleNames(user.roles));
		}
		return ((String[]) roles.toArray(new String[roles.size()]));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthorityRBAC#hasRole(java.lang.String, java.lang.String, boolean)
	 */
	public boolean hasRole(String userName, String roleName, boolean recurse) {
		User user = this.getUser(userName);
		if (user == null)
			return false;
		else if (user.roles.contains(roleName))
			return true;
		else if (recurse)
			return this.resolveRoleNames(user.roles).contains(roleName);
		else return false;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthorityRBAC#assignRole(java.lang.String, java.lang.String)
	 */
	public String assignRole(String userName, String roleName) {
		String[] roles = {roleName};
		return this.assignRoles(userName, roles);
	}
	private String assignRoles(String userName, String[] roles) {
		User user = this.getUser(userName);
		if (user == null)
			return ("User '" + userName + "' does not exist");
		for (int r = 0; r < roles.length; r++) {
			if (!user.roles.add(roles[r]))
				continue;
			String query = "INSERT INTO " + USER_ROLE_TABLE_NAME + " (" + USER_NAME_COLUMEN_NAME + ", " + ROLE_NAME_COLUMEN_NAME + ") VALUES ('" + EasyIO.sqlEscape(userName) + "', '" + EasyIO.sqlEscape(roles[r]) + "');";
			try {
				this.io.executeUpdateQuery(query);
			}
			catch (SQLException sqle) {
				this.logError("GgServerUPS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while assigning role '" + roles[r] + "' to user '" + userName + "'.");
				this.logError("  query was " + query);
				return "Database access error, see log for details";
			}
		}
		return null;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthorityRBAC#removeRole(java.lang.String, java.lang.String)
	 */
	public String removeRole(String userName, String roleName) {
		String[] roles = {roleName};
		return this.removeRoles(userName, roles);
	}
	private String removeRoles(String userName, String[] roles) {
		User user = this.getUser(userName);
		if (user == null)
			return ("User '" + userName + "' does not exist");
		for (int r = 0; r < roles.length; r++) {
			if (!user.roles.remove(roles[r]))
				continue;
			String query = "DELETE FROM " + USER_ROLE_TABLE_NAME + " WHERE " + USER_NAME_COLUMEN_NAME + " = '" + EasyIO.sqlEscape(userName) + "' AND " + ROLE_NAME_COLUMEN_NAME + " = '" + EasyIO.sqlEscape(roles[r]) + "';";
			try {
				this.io.executeUpdateQuery(query);
			}
			catch (SQLException sqle) {
				this.logError("GgServerUPS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while revoking role '" + roles[r] + "' from user '" + userName + "'.");
				this.logError("  query was " + query);
				return "Database access error, see log for details";
			}
		}
		return null;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthorityRBAC#createRole(java.lang.String)
	 */
	public String createRole(String roleName) {
		if (roleName == null)
			return "Role names must not be null.";
		else if (GUEST_ROLE_NAME.equals(roleName))
			return ("Cannot re-create built-in '" + GUEST_ROLE_NAME + "' role.");
		else if (DEFAULT_ROLE_NAME.equals(roleName))
			return ("Cannot re-create built-in '" + DEFAULT_ROLE_NAME + "' role.");
		else if (!roleName.matches(ROLE_NAME_PATTERN))
			return "Invalid role name, use Latin letters, digits, '-', '_', '@', and '.' only, " + ROLE_NAME_MAX_LENGTH + " at max.";
		else {
			Role role = this.getRole(roleName);
			if (role == null) {
				role = new Role(roleName);
				String query = "INSERT INTO " + ROLE_TABLE_NAME + " (" + ROLE_NAME_COLUMEN_NAME + ") VALUES ('" + EasyIO.sqlEscape(role.name) + "');";
				try {
					this.io.executeUpdateQuery(query);
					this.roles.put(roleName, role);
					return null;
				}
				catch (SQLException sqle) {
					this.logError("GgServerUPS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while creating role '" + roleName + "'.");
					this.logError("  query was " + query);
					return "Database access error, see log for details";
				}
			}
			else return ("Role '" + roleName + "' already exists.");
		}
	}
	
	/*
	 * Delete a role administrated by the UPS. This will delete the specified
	 * role, and UPS will forget which roles and permissions were granted to the
	 * specified one, but UPS will remember which users and roles the specified
	 * role was granted to, and it will be readily available as before when it
	 * is created again.
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthorityRBAC#deleteRole(java.lang.String)
	 */
	public String deleteRole(String roleName) {
		if (roleName == null) return "Role names must not be null.";
		else if (GUEST_ROLE_NAME.equals(roleName))
			return ("Cannot delete built-in '" + GUEST_ROLE_NAME + "' role.");
		else if (DEFAULT_ROLE_NAME.equals(roleName))
			return ("Cannot delete built-in '" + DEFAULT_ROLE_NAME + "' role.");
		else if (!this.roles.containsKey(roleName))
			return ("Role '" + roleName + "' does not exists.");
		else {
			String query = "DELETE FROM " + ROLE_PERMISSION_TABLE_NAME + " WHERE " + ROLE_NAME_COLUMEN_NAME + " = '" + EasyIO.sqlEscape(roleName) + "';";
			try {
				this.io.executeUpdateQuery(query);
			}
			catch (SQLException sqle) {
				this.logError("GgServerUPS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while deleting role '" + roleName + "'.");
				this.logError("  query was " + query);
				return "Database access error, see log for details";
			}
			query = "DELETE FROM " + ROLE_TABLE_NAME + " WHERE " + ROLE_NAME_COLUMEN_NAME + " = '" + EasyIO.sqlEscape(roleName) + "';";
			try {
				this.io.executeUpdateQuery(query);
				return null;
			}
			catch (SQLException sqle) {
				this.logError("GgServerUPS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while deleting role '" + roleName + "'.");
				this.logError("  query was " + query);
				return "Database access error, see log for details";
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthorityRBAC#getImpliedRoles(java.lang.String, boolean)
	 */
	public String[] getImpliedRoles(String roleName, boolean recurse) {
		TreeSet roles = new TreeSet();
		if (roleName != null) {
			Role role = this.getRole(roleName);
			if (role != null)
				roles.addAll(role.roles);
			if (recurse)
				roles.addAll(this.resolveRoleNames(role.roles));
		}
		return ((String[]) roles.toArray(new String[roles.size()]));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthorityRBAC#impliesRole(java.lang.String, java.lang.String, boolean)
	 */
	public boolean impliesRole(String roleName, String impliedRoleName, boolean recurse) {
		Role role = this.getRole(roleName);
		if (role == null)
			return false;
		else if (role.roles.contains(roleName))
			return true;
		else if (recurse)
			return this.resolveRoleNames(role.roles).contains(roleName);
		else return false;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthorityRBAC#implyRole(java.lang.String, java.lang.String)
	 */
	public String implyRole(String roleName, String impliedRoleName) {
		String[] roles = {impliedRoleName};
		return this.implyRoles(roleName, roles);
	}
	private String implyRoles(String roleName, String[] roles) {
		Role role = this.getRole(roleName);
		if (role == null)
			return ("Role '" + roleName + "' does not exist");
		
		TreeSet rolesToAdd = new TreeSet();
		StringVector circleRoles = new StringVector();
		
		for (int r = 0; r < roles.length; r++) {
			if (GUEST_ROLE_NAME.equals(roles[r]))
				return ("Cannot grant built-in '" + GUEST_ROLE_NAME + "' role to another role.");
			
			else if (DEFAULT_ROLE_NAME.equals(roles[r]))
				continue;
			
			else if (!role.roles.contains(roles[r]) // nothing to check
				&&
				!role.name.equals(roles[r]) // still nothing to check
				&&
				this.roles.containsKey(roles[r])) // role is valid
			{
				rolesToAdd.add(roles[r]);
				
				TreeSet testSet = new TreeSet();
				testSet.add(roles[r]);
				TreeSet effectSet = this.resolveRoleNames(testSet);
				if (effectSet.contains(roleName))
					circleRoles.addElementIgnoreDuplicates(roles[r]);
			}
		}
		
		//	catch circular references
		if (circleRoles.size() != 0)
			return ("Granting any one of the roles {" + circleRoles.concatStrings(", ") + "} to role '" + roleName + "' would cause a circular dependence.");
		
		for (int r = 0; r < roles.length; r++) {
			if (!role.roles.add(roles[r]))
				continue;
			String query = "INSERT INTO " + ROLE_PERMISSION_TABLE_NAME + " (" + ROLE_NAME_COLUMEN_NAME + ", " + IS_ROLE_OR_PERMISSION_COLUMEN_NAME + ", " + PERMISSION_NAME_COLUMEN_NAME + ") VALUES ('" + EasyIO.sqlEscape(role.name) + "', 'R', '" + EasyIO.sqlEscape(roles[r]) + "');";
			try {
				this.io.executeUpdateQuery(query);
			}
			catch (SQLException sqle) {
				this.logError("GgServerUPS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while implying role '" + roles[r] + "' with role '" + roleName + "'.");
				this.logError("  query was " + query);
				return "Database access error, see log for details";
			}
		}
		return null;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthorityRBAC#unimplyRole(java.lang.String, java.lang.String)
	 */
	public String unimplyRole(String roleName, String impliedRoleName) {
		String[] roles = {impliedRoleName};
		return this.unimplyRoles(roleName, roles);
	}
	private String unimplyRoles(String roleName, String[] roles) {
		Role role = this.getRole(roleName);
		if (role == null)
			return ("Role '" + roleName + "' does not exist");
		for (int r = 0; r < roles.length; r++) {
			if (!role.roles.remove(roles[r]))
				continue;
			String query = "DELETE FROM " + ROLE_PERMISSION_TABLE_NAME + " WHERE " + ROLE_NAME_COLUMEN_NAME + " = '" + EasyIO.sqlEscape(role.name) + "' AND " + IS_ROLE_OR_PERMISSION_COLUMEN_NAME + " = 'R' AND " + PERMISSION_NAME_COLUMEN_NAME + " = '" + EasyIO.sqlEscape(roles[r]) + "';";
			try {
				this.io.executeUpdateQuery(query);
			}
			catch (SQLException sqle) {
				this.logError("GgServerUPS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while removing implied role '" + roles[r] + "' from role '" + roleName + "'.");
				this.logError("  query was " + query);
				return "Database access error, see log for details";
			}
		}
		return null;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthority#registerPermission(java.lang.String)
	 */
	public String registerPermission(String permission) {
		if (permission == null)
			return "Permission names must not be null.";
		else if (!permission.matches(PERMISSION_NAME_PATTERN))
			return "Invalid role name, use Latin letters, digits, '-', '_', '@', and '.' only, " + PERMISSION_NAME_MAX_LENGTH + " at max.";
		else if (this.permissions.add(permission))
			return null;
		else return ("Permission '" + permission + "' already exists.");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthorityRBAC#getGrantedPermissions(java.lang.String, boolean)
	 */
	public String[] getGrantedPermissions(String roleName, boolean recurse) {
		Role role = this.getRole(roleName);
		if (role == null) return new String[0];
		
		TreeSet subRoles = new TreeSet();
		if (role.roles.contains(GUEST_ROLE_NAME))
			subRoles.add(GUEST_ROLE_NAME);
		
		else if (recurse) {
			subRoles.addAll(role.roles);
			subRoles.addAll(this.resolveRoleNames(role.roles));
		}
		
		StringVector permissions = new StringVector();
		
		for (Iterator pit = role.permissions.iterator(); pit.hasNext();) {
			String permission = ((String) pit.next());
			if (this.permissions.contains(permission))
				permissions.addElementIgnoreDuplicates(permission);
		}
		
		for (Iterator rit = subRoles.iterator(); rit.hasNext();) {
			Role subRole = this.getRole(rit.next().toString());
			if (subRole != null)
				for (Iterator pit = subRole.permissions.iterator(); pit.hasNext();) {
					String permission = ((String) pit.next());
					if (this.permissions.contains(permission))
						permissions.addElementIgnoreDuplicates(permission);
				}
		}
		
		return permissions.toStringArray();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthorityRBAC#hasPermission(java.lang.String, java.lang.String, boolean)
	 */
	public boolean hasPermission(String roleName, String permission, boolean recurse) {
		Role role = this.getRole(roleName);
		if (role == null)
			return false;
		else if (role.permissions.contains(permission))
			return true;
		else if (!recurse)
			return false;
		
		String[] roleNames = this.getImpliedRoles(roleName, true);
		for (int r = 0; r < roleNames.length; r++) {
			role = this.getRole(roleNames[r]);
			if ((role != null) && role.permissions.contains(permission))
				return true;
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthorityRBAC#grantPermission(java.lang.String, java.lang.String)
	 */
	public String grantPermission(String roleName, String permission) {
		String[] permissions = {permission};
		return this.grantPermissions(roleName, permissions);
	}
	private String grantPermissions(String roleName, String[] permissions) {
		Role role = this.getRole(roleName);
		if (role == null)
			return ("Role '" + roleName + "' does not exist");
		for (int p = 0; p < permissions.length; p++) {
			if (!role.permissions.add(permissions[p]))
				continue;
			String query = "INSERT INTO " + ROLE_PERMISSION_TABLE_NAME + " (" + ROLE_NAME_COLUMEN_NAME + ", " + IS_ROLE_OR_PERMISSION_COLUMEN_NAME + ", " + PERMISSION_NAME_COLUMEN_NAME + ") VALUES ('" + EasyIO.sqlEscape(role.name) + "', 'P', '" + EasyIO.sqlEscape(permissions[p]) + "');";
			try {
				this.io.executeUpdateQuery(query);
			}
			catch (SQLException sqle) {
				this.logError("GgServerUPS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while adding permission '" + permissions[p] + "' to role '" + roleName + "'.");
				this.logError("  query was " + query);
				return "Database access error, see log for details";
			}
		}
		return null;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGateServer.uaa.UserPermissionAuthorityRBAC#revokePermission(java.lang.String, java.lang.String)
	 */
	public String revokePermission(String roleName, String permission) {
		String[] permissions = {permission};
		return this.revokePermissions(roleName, permissions);
	}
	private String revokePermissions(String roleName, String[] permissions) {
		Role role = this.getRole(roleName);
		if (role == null)
			return ("Role '" + roleName + "' does not exist");
		for (int p = 0; p < permissions.length; p++) {
			if (!role.permissions.remove(permissions[p]))
				continue;
			String query = "DELETE FROM " + ROLE_PERMISSION_TABLE_NAME + " WHERE " + ROLE_NAME_COLUMEN_NAME + " = '" + EasyIO.sqlEscape(role.name) + "' AND " + IS_ROLE_OR_PERMISSION_COLUMEN_NAME + " = 'P' AND " + PERMISSION_NAME_COLUMEN_NAME + " = '" + EasyIO.sqlEscape(permissions[p]) + "';";
			try {
				this.io.executeUpdateQuery(query);
			}
			catch (SQLException sqle) {
				this.logError("GgServerUPS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while removing permission '" + permissions[p] + "' from role '" + roleName + "'.");
				this.logError("  query was " + query);
				return "Database access error, see log for details";
			}
		}
		return null;
	}
	
	private Role getRole(String roleName) {
		return ((Role) this.roles.get(roleName));
	}
	
	private Role loadRole(String roleName) {
		String query = "SELECT " + IS_ROLE_OR_PERMISSION_COLUMEN_NAME + ", " + PERMISSION_NAME_COLUMEN_NAME + " FROM " + ROLE_PERMISSION_TABLE_NAME + " WHERE " + ROLE_NAME_COLUMEN_NAME + " = '" + EasyIO.sqlEscape(roleName) + "';";
		try {
			SqlQueryResult sqr = this.io.executeSelectQuery(query);
			Role role = new Role(roleName);
			while (sqr.next())
				("R".equals(sqr.getString(0)) ? role.roles : role.permissions).add(sqr.getString(1));
			return role;
		}
		catch (SQLException sqle) {
			this.logError("GgServerUPS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while loading role '" + roleName + "'.");
			this.logError("  query was " + query);
			return null;
		}
	}
	
	private User getUser(String userName) {
		User user = ((User) this.users.get(userName));
		
		//	user not loaded so far
		if (user == null) {
			user = this.loadUser(userName);
			this.users.put(user.name, user);
		}
		
		//	return what we finally got
		return user;
	}
	
	private User loadUser(String userName) {
		String query = "SELECT " + ROLE_NAME_COLUMEN_NAME + " FROM " + USER_ROLE_TABLE_NAME + " WHERE " + USER_NAME_COLUMEN_NAME + " = '" + EasyIO.sqlEscape(userName) + "';";
		try {
			SqlQueryResult sqr = this.io.executeSelectQuery(query);
			User user = new User(userName);
			while (sqr.next())
				user.roles.add(sqr.getString(0));
			return user;
		}
		catch (SQLException sqle) {
			this.logError("GgServerUPS: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while loading user '" + userName + "'.");
			this.logError("  query was " + query);
			return null;
		}
	}
	
	//	!!! for test purposes only !!!
	public static void main(String[] args) throws Exception {
		GoldenGateUPS ups = new GoldenGateUPS();
		File dataPath = new File("E:/GoldenGATEv3.Server/Components/GgServerUPSData/");
		if (!dataPath.exists()) dataPath.mkdirs();
		ups.setDataPath(dataPath);
		ups.initComponent();
		
		System.out.println(ups.createRole("test1"));
		System.out.println(ups.createRole("test2"));
		System.out.println(ups.createRole("test3"));
		System.out.println(ups.implyRole("test1", "test2"));
		System.out.println(ups.implyRole("test2", "test3"));
		System.out.println(ups.implyRole("test1", "test3"));
	}
}
