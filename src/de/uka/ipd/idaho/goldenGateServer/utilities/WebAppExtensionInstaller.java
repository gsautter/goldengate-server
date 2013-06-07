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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import de.uka.ipd.idaho.htmlXmlUtil.Parser;
import de.uka.ipd.idaho.htmlXmlUtil.TokenReceiver;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.Grammar;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.StandardGrammar;


/**
 * Installer tool for zipped web application extensions and updates thereof,
 * retaining files that were modified locally on updates, and files configured
 * to be retained in the <code>update.cnfg</code> config file.
 * 
 * @author sautter
 */
public class WebAppExtensionInstaller {
	public static void main(String[] args) {
		
		//	get base folder
		File basePath = (new File(".").getAbsoluteFile());
		if (".".equals(basePath.getName()))
			basePath = basePath.getParentFile();
		
		//	check arguments
		if ((args.length == 0) || (args[0] == null) || (args[0].trim().length() == 0) || args[0].trim().toLowerCase().endsWith(".zip")) {
			if ("webapps".equalsIgnoreCase(basePath.getName())) {
				System.out.println("Please specify the name of the web application to install to.");
				return;
			}
			
			//	derive web app name from folder name
			String[] nArgs = new String[args.length + 1];
			nArgs[0] = basePath.getName();
			System.arraycopy(args, 0, nArgs, 1, args.length);
			args = nArgs;
			basePath = basePath.getParentFile();
		}
		if ((args.length < 2) || (args[1] == null) || (args[1].trim().length() == 0) || !args[1].trim().toLowerCase().endsWith(".zip")) {
			System.out.println("Please specify the name of the zip file to install or update from.");
			return;
		}
		
		//	gather webapp base data
		String webAppName = args[0];
		File webAppPath = (basePath.getName().equals(webAppName) ? basePath : new File(basePath, webAppName));
		
		//	make sure base path is parent of webapp folder
		if (webAppPath == basePath)
			basePath = basePath.getParentFile();
		
		//	no webapp to install to
		if (!webAppPath.exists()) {
			System.out.println("The web application " + webAppName + " does not exist.");
			return;
		}
		
		//	find archive to extract (check update case first)
		String zipName = ((args.length == 1) ? (webAppName + ".zip") : args[1]);
		File zipFile = new File(webAppPath, zipName);
		if (!zipFile.exists())
			zipFile = new File(basePath, zipName);
		if (!zipFile.exists()) {
			System.out.println(zipName + " not found.");
			return;
		}
		
		//	get extension data
		String extName = zipName.substring(0, (zipName.length() - ".zip".length()));
		boolean extExists = (new File(webAppPath, "WEB-INF/web." + extName + ".xml")).exists();
		
		//	load file name filter
		ArrayList ignoreFileNames = new ArrayList();
		if (extExists) try {
			File ifnFile = new File(webAppPath, ("update." + extName + ".cnfg"));
			BufferedReader ifnBr = new BufferedReader(new InputStreamReader(new FileInputStream(ifnFile)));
			for (String ifn; (ifn = ifnBr.readLine()) != null;) {
				ifn = ifn.trim();
				if ((ifn.length() == 0) || ifn.startsWith("//"))
					continue;
				
				//	simple string
				if (ifn.indexOf('*') == -1) {
					ignoreFileNames.add(ifn);
					continue;
				}
				
				//	pattern
				StringBuffer ifnRegEx = new StringBuffer();
				for (int c = 0; c < ifn.length(); c++) {
					char ch = ifn.charAt(c);
					if (Character.isLetterOrDigit(ch))
						ifnRegEx.append(ch);
					else if (ch == '*')
						ifnRegEx.append(".*");
					else {
						ifnRegEx.append('\\');
						ifnRegEx.append(ch);
					}
				}
				ignoreFileNames.add(Pattern.compile(ifnRegEx.toString(), Pattern.CASE_INSENSITIVE));
			}
		} catch (IOException ioe) {}
		
		//	store original web.xml and web.cnfg
		File oWebXml = new File(webAppPath, "WEB-INF/web.original.xml");
		if (!oWebXml.exists()) try {
			File webXml = new File(webAppPath, "WEB-INF/web.xml");
			FileInputStream webXmlIn = new FileInputStream(webXml);
			InstallerUtils.updateFile(webAppPath, "WEB-INF/web.original.xml", webXmlIn, webXml.lastModified());
			webXmlIn.close();
		} catch (IOException ioe) {}
		File oWebCnfg = new File(webAppPath, "WEB-INF/web.original.cnfg");
		if (!oWebCnfg.exists()) try {
			File webCnfg = new File(webAppPath, "WEB-INF/web.cnfg");
			FileInputStream webCnfgIn = new FileInputStream(webCnfg);
			InstallerUtils.updateFile(webAppPath, "WEB-INF/web.original.cnfg", webCnfgIn, webCnfg.lastModified());
			webCnfgIn.close();
		} catch (IOException ioe) {}
		
		//	unzip webapp
		try {
			ZipInputStream webAppZip = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
			for (ZipEntry ze; (ze = webAppZip.getNextEntry()) != null;) {
				
				//	test for folders
				if (ze.isDirectory())
					continue;
				
				//	get and check name
				String zeName = ze.getName();
				if (zeName.endsWith("web.xml"))
					zeName = (zeName.substring(0, (zeName.length() - ".xml".length())) + "." + extName + ".xml");
				if (zeName.endsWith("web.cnfg"))
					zeName = (zeName.substring(0, (zeName.length() - ".cnfg".length())) + "." + extName + ".cnfg");
				if (zeName.equals("update.cnfg"))
					zeName = (zeName.substring(0, (zeName.length() - ".cnfg".length())) + "." + extName + ".cnfg");
				
				//	check ignore patterns
				for (int i = 0; i < ignoreFileNames.size(); i++) {
					Object io = ignoreFileNames.get(i);
					if (((io instanceof Pattern) && ((Pattern) io).matcher(zeName).matches()) || ((io instanceof String) && ((String) io).equalsIgnoreCase(ze.getName()))) {
						System.out.println(" - ignoring " + zeName);
						ze = null;
						break;
					}
				}
				if (ze == null)
					continue;
				
				//	get timestamp and unpack file
				long zipLastModified = ze.getTime();
				InstallerUtils.updateFile(webAppPath, zeName, webAppZip, zipLastModified);
				
				//	close current entry
				webAppZip.closeEntry();
			}
		}
		catch (IOException ioe) {
			ioe.printStackTrace(System.out);
		}
		
		//	deal with config files
		File webInf = new File(webAppPath, "WEB-INF");
		
		//	we're updating, check config files and we're done
		if (extExists) {
			File webXml = new File(webInf, "web.xml");
			File extWebXml = new File(webInf, ("web." + extName + ".xml"));
			if ((webXml.lastModified() < extWebXml.lastModified()))
				System.out.println("WEB-INF/web." + extName + ".xml has been updated, please check web.xml.");
			File webCnfg = new File(webInf, "web.cnfg");
			File extWebCnfg = new File(webInf, ("web." + extName + ".cnfg"));
			if ((webCnfg.lastModified() < extWebCnfg.lastModified()))
				System.out.println("WEB-INF/web." + extName + ".cnfg has been updated, please check web.cnfg.");
			return;
		}
		
		//	append extension web.xml
		final File webXml = new File(webInf, "web.xml");
		final File extWebXml = new File(webInf, ("web." + extName + ".xml"));
		if (extWebXml.exists()) try {
			
			//	create combined web.xml in memory
			final BufferedReader webXmlIn = new BufferedReader(new InputStreamReader(new FileInputStream(webXml), "UTF-8"));
			final StringWriter webXmlBuffer = new StringWriter();
			parser.stream(webXmlIn, new TokenReceiver() {
				public void storeToken(String token, int treeDepth) throws IOException {
					if (xml.isEndTag(token) && ("web-app".equals(xml.getType(token)))) {
						final BufferedReader extWebXmlIn = new BufferedReader(new InputStreamReader(new FileInputStream(extWebXml), "UTF-8"));
						parser.stream(extWebXmlIn, new TokenReceiver() {
							boolean inWebapp = false;
							public void storeToken(String token, int treeDepth) throws IOException {
								if (xml.isTag(token) && ("web-app".equals(xml.getType(token))))
									this.inWebapp = !xml.isEndTag(token);
								else if (this.inWebapp)
									webXmlBuffer.write(token);
							}
							public void close() throws IOException {}
						});
						extWebXmlIn.close();
					}
					webXmlBuffer.write(token);
				}
				public void close() throws IOException {}
			});
			webXmlIn.close();
			
			//	salvage timestamp
			long webXmlLastModified = Math.max(webXml.lastModified(), extWebXml.lastModified());
			
			//	store combined web.xml
			BufferedWriter webXmlOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(webXml), "UTF-8"));
			webXmlOut.write(webXmlBuffer.toString());
			webXmlOut.flush();
			webXmlOut.close();
			
			//	set timestamp
			webXml.setLastModified(webXmlLastModified);
		}
		catch (IOException ioe) {
			System.out.println("Error extending web.xml: " + ioe.getMessage());
			ioe.printStackTrace(System.out);
		}
		
		//	append extension web.cnfg
		File webCnfg = new File(webInf, "web.cnfg");
		File extWebCnfg = new File(webInf, ("web." + extName + ".cnfg"));
		if (extWebCnfg.exists()) try {
			
			//	create combined web.cnfg in memory
			BufferedReader webCnfgIn = new BufferedReader(new InputStreamReader(new FileInputStream(webCnfg), "UTF-8"));
			ArrayList webCnfgBuffer = new ArrayList();
			HashSet webCnfgFilter = new HashSet();
			for (String cnfgLine; (cnfgLine = webCnfgIn.readLine()) != null;) {
				webCnfgBuffer.add(cnfgLine);
				if ((cnfgLine.length() == 0) || cnfgLine.startsWith("//") || (cnfgLine.indexOf('=') >= cnfgLine.indexOf('"')))
					continue;
				String setName = cnfgLine.substring(0, cnfgLine.indexOf('=')).trim();
				webCnfgFilter.add(setName);
			}
			webCnfgIn.close();
			BufferedReader extWebCnfgIn = new BufferedReader(new InputStreamReader(new FileInputStream(webCnfg), "UTF-8"));
			for (String cnfgLine; (cnfgLine = extWebCnfgIn.readLine()) != null;) {
				if ((cnfgLine.length() == 0) || cnfgLine.startsWith("//") || (cnfgLine.indexOf('=') >= cnfgLine.indexOf('"'))) {
					webCnfgBuffer.add(cnfgLine);
					continue;
				}
				String setName = cnfgLine.substring(0, cnfgLine.indexOf('=')).trim();
				if (webCnfgFilter.add(setName))
					webCnfgBuffer.add(cnfgLine);
			}
			extWebCnfgIn.close();
			
			//	salvage timestamp
			long webCnfgLastModified = Math.max(webCnfg.lastModified(), extWebCnfg.lastModified());
			
			//	store combined web.cnfg
			BufferedWriter webCnfgOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(webCnfg), "UTF-8"));
			for (Iterator cit = webCnfgBuffer.iterator(); cit.hasNext();) {
				webCnfgOut.write((String) cit.next());
				webCnfgOut.newLine();
			}
			webCnfgOut.flush();
			webCnfgOut.close();
			
			//	set timestamp
			webCnfg.setLastModified(webCnfgLastModified);
		}
		catch (IOException ioe) {
			System.out.println("Error extending web.cnfg: " + ioe.getMessage());
			ioe.printStackTrace(System.out);
		}
	}
	
	private static Grammar xml = new StandardGrammar();
	private static Parser parser = new Parser(xml);
}