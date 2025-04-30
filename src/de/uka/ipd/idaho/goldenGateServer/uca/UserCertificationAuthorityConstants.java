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
package de.uka.ipd.idaho.goldenGateServer.uca;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.TreeMap;

import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants;
import de.uka.ipd.idaho.htmlXmlUtil.Parser;
import de.uka.ipd.idaho.htmlXmlUtil.TreeNode;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.TreeTools;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.Grammar;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.StandardGrammar;


/**
 * Constant bearer for UserCertificationAuthority.
 * 
 * @author sautter
 */
public interface UserCertificationAuthorityConstants extends GoldenGateServerConstants {
	
	/** action command for retrieving available certificates */
	public static final String GET_CERTIFICATES = "UCA_GET_CERTIFICATES";
	
	/** action command for retrieving the modification timestamp of the available certificates */
	public static final String GET_CERTIFICATES_LAST_MODIFIED = "UCA_GET_CERTIFICATES_LAST_MODIFIED";
	
	/** action command for retrieving a list of the certificates held the user logged in on a given session, including the holder levels */
	public static final String GET_CERTIFICATIONS = "UCA_GET_CERTIFICATIONS";
	
	/** action command for retrieving available user names to assign certificates to */
	public static final String GET_USER_NAMES = "UCA_GET_USER_NAMES";
	
	/** action command for retrieving a list of the certificates held by a given user, including the holder levels */
	public static final String GET_USER_CERTIFICATIONS = "UCA_GET_USER_CERTIFICATIONS";
	
	/** action command for set the certificates held by a given user, as well as the respective holder levels */
	public static final String SET_USER_CERTIFICATIONS = "UCA_SET_USER_CERTIFICATIONS";
	
	/** suffix for data object attributes that hold the names of users whose modifications under the auspice of some certificate require approval, to be used as '<code>&lt;certificateName&gt;_requiresApprovalFor</code>' */
	public static final String REQUIRES_APPROVAL_FOR_ATTRIBUTE_SUFFIX = "_requiresApprovalFor";
	
	/** suffix for data object attributes that hold the names of users who approved modifications under the auspice of some certificate, to be used as '<code>&lt;certificateName&gt;_approvedBy</code>' */
	public static final String APPROVED_BY_ATTRIBUTE_SUFFIX = "_approvedBy";
	
	/**
	 * Holding a given certificate marks a user as qualified to modify data
	 * objects the certificate pertains to in specific ways, without requiring
	 * a certified user of level 'verifier' or higher to later approve the
	 * modifications before the data object can be used in specific ways.
	 * There can be multiple certificates For the same class of data objects,
	 * covering different detail aspects within these object. Users certified
	 * at level 'certifier' can assign the respective certificate to other
	 * users, at all levels.
	 * 
	 * @author sautter
	 */
	public static abstract class UserCertificate {
		
		/** root node type for list of XML serialized user certificates, namely 'certificates' */
		public static final String CERTIFICATE_LIST_NODE_TYPE = "certificates";
		
		/** root node type for XML serialized user certificates, namely 'certificate' */
		public static final String CERTIFICATE_NODE_TYPE = "certificate";
		
		/** the name of the certificate, uniquely identifying it */
		public final String name;
		
		/** the label (nice name) of the certificate, e.g. for use in a UI */
		public final String label;
		
		/** the (common super) class of all data objects the certificate pertains to */
		public final Class targetClass;
		
		/** the label (nice name) for the (common super) class of all data objects the certificate pertains to */
		public final String targetLabel;
		
		/** Constructor
		 * @param name the name of the certificate, uniquely identifying it
		 * @param label the label (nice name) of the certificate, e.g. for use in a UI
		 * @param targetClass (common super) class of all data objects the certificate pertains to
		 * @param targetLabel the label (nice name) for the (common super) class of all data objects the certificate pertains to, e.g. for use in a UI
		 */
		protected UserCertificate(String name, String label, Class targetClass, String targetLabel) {
			if (!name.matches("[a-zA-Z][a-zA-Z0-9\\-\\_\\.]+"))
				throw new IllegalArgumentException("Invalid certificate name '" + name + "'");
			this.name = name;
			this.label = label;
			this.targetClass = targetClass;
			this.targetLabel = targetLabel;
		}
		
		/**
		 * Test whether or not the certificate pertains to modifications of a
		 * given data object. This method simply checks the class of the
		 * argument object. Subclasses are welcome to overwrite this method to
		 * add more fine-gained checks, but should still make the super call.
		 * @param dataObject the data object to check
		 * @return true if the certificate pertains to the argument data object
		 */
		public boolean pertainsTo(Object dataObject) {
			return this.pertainsToInstances(dataObject.getClass());
		}
		
		/**
		 * Test whether or not the certificate pertains to modifications of
		 * data objects of or derived from a given class.
		 * @param dataObjectClass the data object class to check
		 * @return true if the certificate pertains to data objects of or
		 *        derived from argument class
		 */
		public boolean pertainsToInstances(Class dataObjectClass) {
			return this.targetClass.isAssignableFrom(dataObjectClass);
		}
		
		/**
		 * Provide a more detailed description of the modifications covered by
		 * the certificate. The description may contain HTML markup.
		 * @return the description
		 */
		public abstract String getDescription();
		
		/**
		 * Serialize the certificate to XML. Non-abstract subclasses should
		 * include some attribute in the root node of the returned XML that
		 * allows their respective factory to recognize the XML. The root node
		 * of the returned XML must be of type <code>certificate</code>.
		 * @return the serialized certificate
		 */
		public abstract String toXml();
		
		private static LinkedHashSet factories = new LinkedHashSet(2);
		
		/**
		 * Add a factory to help instantiate anchors.
		 * @param dsp the provider to add
		 */
		protected static void addFactory(Factory dsaf) {
			if (dsaf != null)
				factories.add(dsaf);
		}
		
		/**
		 * Instantiate a user certificate from the parsed form of their XML
		 * serialization. If non of the registered factories can de-serialize
		 * the argument XML, this method returns a generic representation, or
		 * null if the basic attributes are missing.
		 * @param certRoot the root node of the parsed XML
		 * @return the user certificate
		 */
		public static UserCertificate getCertificate(TreeNode certRoot) {
			for (Iterator afit = factories.iterator(); afit.hasNext();) {
				UserCertificate uc = ((Factory) afit.next()).getCertificate(certRoot);
				if (uc != null)
					return uc;
			}
			return GenericUserCertificate.getGenericUserCertificate(certRoot);
		}
		
		private static class GenericUserCertificate extends UserCertificate {
			private String xml; // ensures we can re-serialize unscathed
			GenericUserCertificate(String name, String label, String targetLabel, String xml) {
				super(name, label, Object.class, targetLabel);
				this.xml = xml;
			}
			
			/* (non-Javadoc)
			 * @see de.uka.ipd.idaho.goldenGateServer.uca.UserCertificationAuthorityConstants.UserCertificate#getDescription()
			 */
			public String getDescription() {
				return this.description;
			}
			void setDescription(String desc) {
				this.description = desc;
			}
			private String description = "";
			
			/* (non-Javadoc)
			 * @see de.uka.ipd.idaho.goldenGateServer.uca.UserCertificationAuthorityConstants.UserCertificate#toXml()
			 */
			public String toXml() {
				return this.xml;
			}
			
			static GenericUserCertificate getGenericUserCertificate(TreeNode certRoot) {
				String name = certRoot.getAttribute("name");
				if (name == null)
					return null;
				String label = certRoot.getAttribute("label");
				if (label == null)
					return null;
				String targetLabel = certRoot.getAttribute("targetLabel");
				if (targetLabel == null)
					return null;
				GenericUserCertificate cert = new GenericUserCertificate(name, label, targetLabel, certRoot.treeToCode(null, XML_GRAMMAR));
				TreeNode[] descNodes = TreeTools.getAllNodesOfType(certRoot, "description");
				if (descNodes.length != 0) {
					String desc = descNodes[0].treeToCode(null, XML_GRAMMAR);
					if ("<description/>".equals(desc))
						cert.setDescription("");
					else cert.setDescription(desc.substring("<description>".length(), (desc.length() - "</description>".length())));
				}
				return cert;
			}
		}
		
		/**
		 * Factory for de-serializing more specific user certificate subclasses
		 * from their XML representations.
		 * 
		 * @author sautter
		 */
		public static interface Factory {
			
			/**
			 * De-serialize a user certificate from its XML representation. If
			 * the factory does not understand the argument XML, it should
			 * return null instead of throwing an exception. The latter should
			 * only happen if the factory recognizes the certificate, but the
			 * XML representation is compromised.
			 * @param certRoot the root node of the parsed XML
			 * @return the user certificate
			 */
			public abstract UserCertificate getCertificate(TreeNode certRoot);
		}
		
		/**
		 * Read back a series of XML serialized user certificates from a stream
		 * of XML rows. This method reads the argument stream to the end, but
		 * does not close it. Certificates that cannot be de-serialized by any
		 * of the registered factories are ignored.
		 * @param in the reader to read from
		 * @return an array holding the user certificates
		 * @throws IOException
		 */
		public static UserCertificate[] readCertificates(Reader in) throws IOException {
			TreeNode root = XML_PARSER.parse(in);
			TreeNode[] certRoots = TreeTools.getAllNodesOfType(root, CERTIFICATE_NODE_TYPE);
			ArrayList ucs = new ArrayList();
			for (int c = 0; c < certRoots.length; c++) {
				UserCertificate uc = getCertificate(certRoots[c]);
				if (uc != null)
					ucs.add(uc);
			}
			return ((UserCertificate[]) ucs.toArray(new UserCertificate[ucs.size()]));
		}
		
		/** XML grammar to use for serialization and de-serialization */
		protected static final Grammar XML_GRAMMAR = new StandardGrammar();
		
		/** XML parser to use for serialization and de-serialization */
		protected static final Parser XML_PARSER = new Parser(XML_GRAMMAR);
	}
	
	/**
	 * Certification of a specific user, connecting a user name to a given
	 * certificate at a specific holder level.
	 * 
	 * @author sautter
	 */
	public static class UserCertification {
		
		/** certification level indicating that a given user does not hold a given certificate */
		public static final char HOLDER_LEVEL_UNCERTIFIED = 'U';
		
		/** certification level indicating that a given user holds a given certificate and is thus qualified to make respective modifications to data objects the certificate pertains to */
		public static final char HOLDER_LEVEL_HOLDER = 'H';
		
		/** certification level indicating that a given holds a given certificate and can approve data object modifications made by uncertified users */
		public static final char HOLDER_LEVEL_VERIFIER = 'V';
		
		/** certification level indicating that a given holds a given certificate, can approve data object modifications made by uncertified users, and certify other users */
		public static final char HOLDER_LEVEL_CERTIFIER = 'C';
		
		/** the name of the certificate */
		public final String certificateName;
		
		/** the name of the user holding the certificate */
		public final String userName;
		
		/** the certification level of the user with respect to the certificate */
		public final char holderLevel;
		
		/**
		 * @param certificateName
		 * @param userName
		 * @param holderLevel
		 */
		public UserCertification(String certificateName, String userName, char holderLevel) {
			this.certificateName = certificateName;
			this.userName = userName;
			this.holderLevel = holderLevel;
		}
		
		/**
		 * Test whether or not the subject user holds the subject certificate,
		 * as indicated by the holder level.
		 * @return true if the user holds the certificate
		 */
		public boolean holdsCertificate() {
			return holdsCertificate(this.holderLevel);
		}
		
		/**
		 * Test whether or not the subject user is authorized to approve data
		 * object modifications that are under the auspice of the subject
		 * certificate, as indicated by the holder level.
		 * @return true if the subject user approve modifications
		 */
		public boolean canVerify() {
			return canVerify(this.holderLevel);
		}
		
		/**
		 * Test whether or not the subject user is authorized to assign the
		 * subject certificate to other users, as indicated by holder level.
		 * @return true if the subject user assign the subject certificate to
		 *        other users
		 */
		public boolean canCertify() {
			return canCertify(this.holderLevel);
		}
		
		/**
		 * Serialize the user certification into a TSV record for transfer.
		 * @return the TSV string
		 */
		public String toTsvString() {
			return (this.certificateName + "\t" + this.userName + "\t" + this.holderLevel);
		}
		
		/**
		 * Test whether or not a given user holds a certificate, given their
		 * holder level.
		 * @param holderLevel the holder level to check
		 * @return true if the argument holder level indicates the respective
		 *        user holds a given the certificate
		 */
		public static boolean holdsCertificate(char holderLevel) {
			return ("HVC".indexOf(holderLevel) != -1);
		}
		
		/**
		 * Test whether or not a given user is authorized to approve data
		 * object modifications that are under the auspice of a given
		 * certificate, given their holder level.
		 * @param holderLevel the holder level to check
		 * @return true if the argument holder level indicates the respective
		 *        user is authorized to approve modifications
		 */
		public static boolean canVerify(char holderLevel) {
			return ("VC".indexOf(holderLevel) != -1);
		}
		
		/**
		 * Test whether or not a given user is authorized to assign a given
		 * certificate to other users, given their holder level.
		 * @param holderLevel the holder level to check
		 * @return true if the argument holder level indicates the respective
		 *        user is authorized to assign the certificate to other users
		 */
		public static boolean canCertify(char holderLevel) {
			return ("C".indexOf(holderLevel) != -1);
		}
		
		/**
		 * Read back a series of TSV serialized user certifications from a
		 * stream of TSV rows. This method reads the argument stream to the
		 * end, but does not close it.
		 * @param in the reader to read from
		 * @return an array holding the user certifications
		 * @throws IOException
		 */
		public static UserCertification[] readCertifications(Reader in) throws IOException {
			BufferedReader br = ((in instanceof BufferedReader) ? ((BufferedReader) in) : new BufferedReader(in));
			ArrayList ucs = new ArrayList();
			for (String ucTsv; (ucTsv = br.readLine()) != null;) {
				String[] uc = ucTsv.split("\\t");
				if (uc.length < 3)
					continue;
				if (uc[2].length() == 0)
					ucs.add(new UserCertification(uc[0], uc[1], HOLDER_LEVEL_UNCERTIFIED));
				else ucs.add(new UserCertification(uc[0], uc[1], uc[2].charAt(0)));
			}
			return ((UserCertification[]) ucs.toArray(new UserCertification[ucs.size()]));
		}
	}
	
	/**
	 * Summary data structure for user certifications, to simplify lookups.
	 * 
	 * @author sautter
	 */
	public static class UserCertificationSummary {
		
		/** the name of the user the certification summary is for */
		public final String userName;
		
		private TreeMap certNamesToHolderLevels = new TreeMap();
		
		/** Constructor
		 * @param userName the name of the user the certification summary is for
		 */
		public UserCertificationSummary(String userName) {
			this.userName = userName;
		}
		
		/**
		 * Add or update a user certification.
		 * @param userCert the user certification to include
		 */
		public void putUserCertification(UserCertification userCert) {
			if (this.userName.equals(userCert.userName))
				this.putCertification(userCert.certificateName, userCert.holderLevel);
			else throw new IllegalArgumentException("Cannot add certification for '" + userCert.userName + "' to summary for '" + this.userName + "'");
		}
		
		synchronized void putCertification(String certName, char holderLevel) {
			if (holderLevel == UserCertification.HOLDER_LEVEL_UNCERTIFIED)
				this.certNamesToHolderLevels.remove(certName);
			else this.certNamesToHolderLevels.put(certName, new Character(holderLevel));
			this.certifications = null;
		}
		
		/**
		 * Retrieve the holder level of the subject user with regard to a
		 * certificate with a given name.
		 * @param certName the name of the certificate to retrieve the holder
		 *        level for
		 * @return the holder level
		 */
		public synchronized char getHolderLevel(String certName) {
			Character hl = ((Character) this.certNamesToHolderLevels.get(certName));
			return ((hl == null) ? UserCertification.HOLDER_LEVEL_UNCERTIFIED : hl.charValue());
		}
		
		/**
		 * Test whether or not the subject user holds a certificate with a
		 * given name.
		 * @param certName the name of the certificate to check.
		 * @return true if the subject user holds the certificate with the
		 *        argument name
		 */
		public boolean holdsCertificate(String certName) {
			return UserCertification.holdsCertificate(this.getHolderLevel(certName));
		}
		
		/**
		 * Test whether or not the subject user is authorized to approve data
		 * object modifications that are under the auspice of a certificate
		 * with a given name.
		 * @param certName the name of the certificate to check.
		 * @return true if the subject user approve modifications under the
		 *        auspice of the certificate with the argument name
		 */
		public boolean canVerify(String certName) {
			return UserCertification.canVerify(this.getHolderLevel(certName));
		}
		
		/**
		 * Test whether or not the subject user is authorized to assign a
		 * certificate with a given name to other users.
		 * @param certName the name of the certificate to check.
		 * @return true if the subject user is authorized to assign the
		 *        certificate with the argument name to other users
		 */
		public boolean canCertify(String certName) {
			return UserCertification.canCertify(this.getHolderLevel(certName));
		}
		
		/**
		 * Retrieve the individual certifications summarized in this object.
		 * @return an array holding the certifications
		 */
		public synchronized UserCertification[] getCertifications() {
			if (this.certifications == null) {
				this.certifications = new UserCertification[this.certNamesToHolderLevels.size()];
				int uci = 0;
				for (Iterator cnit = this.certNamesToHolderLevels.keySet().iterator(); cnit.hasNext();) {
					String certName = ((String) cnit.next());
					Character hl = ((Character) this.certNamesToHolderLevels.get(certName));
					this.certifications[uci++] = new UserCertification(certName, this.userName, hl.charValue());
				}
			}
			return Arrays.copyOf(this.certifications, this.certifications.length);
		}
		private UserCertification[] certifications = null;
		
		/**
		 * Create a summary of a given array of user certifications. If the
		 * argument array is empty, this method returns null.
		 * @param userCerts the certifications to summarize
		 * @return the certification summary
		 */
		public static UserCertificationSummary createSummary(UserCertification[] userCerts) {
			if (userCerts.length == 0)
				return null;
			UserCertificationSummary ucs = new UserCertificationSummary(userCerts[0].userName);
			for (int c = 0; c < userCerts.length; c++)
				ucs.putUserCertification(userCerts[c]);
			return ucs;
		}
	}
}
