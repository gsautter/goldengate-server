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
package de.uka.ipd.idaho.goldenGateServer.client;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;

/**
 * A generic servlet for producing HTML pages in the web front-end of a
 * GoldenGATE Server. Actual production is done by HtmlPageBuilder objects,
 * which generate web pages in a template-based fashion. The page generation
 * process basically consists of successively replacing so-called <b>signal tags</b>
 * with respective content, either generated HTML code, or HTML code loaded from
 * other files. In particular, the generation process works in the following
 * steps:
 * <ul>
 * <li>Generation starts with the so-called base page, the main HTML page
 * template (see respective parameter below)</li>
 * <li>In the base page, the signal tags (eg <i>&lt;includeFile
 * file=&quot;mySpecialContentFile&quot;&gt;</i>) are successively replaced by
 * the content generated for them</li>
 * <li>If this generated content contains further signal tags, replacement
 * works them off recursively</li>
 * </ul>
 * Beside generating the HTML code that contains the page information, it is
 * possible to include references to CSS stylesheets and JavaScript files in the
 * page head, and to trigger JavaScript based initialization. In particular,
 * before closing the head of the generated page, the page builder will obtain
 * the following information from its host servlet:
 * <ul>
 * <li>The paths and names of CSS stylesheets to include; paths are interpreted
 * relative to the surrounding web-app's context path, unless they specify a
 * protocol (eg 'http://'), in which case they are interpreted as absolute</li>
 * <li>The paths and names of JavaScript files to include; paths are
 * interpreted relative to the surrounding web-app's context path, unless they
 * specify a protocol (eg 'http://'), in which case they are interpreted as
 * absolute</li>
 * <li>JavaScript functions to call when loading the web page in the browser;
 * this is a generic way of having arbitrary JavaScipt executed on page loading</li>
 * <li>JavaScript functions to call when un-loading the web page in the
 * browser; this is a generic way of having arbitrary JavaScipt executed on page
 * un-loading</li>
 * <li>Finally, a page builder is offered to add arbitrary custom code to the
 * generated page's head, eg generated JavaScript or CSS code</li>
 * </ul>
 * In order to work properly, this servlet reads one parameters from its
 * configuration file:
 * <ul>
 * <li><b>basePage</b>: the name and path of the main template page file, to
 * start generation with; if a respective file exists in the servlets data path,
 * this file will be used, otherwise, a respective file from the surrounding
 * web-app's context path will be used (if this parameter is not specified, it
 * defaults to 'portal.html')</li>
 * </ul>
 * 
 * @author sautter
 */
public class GgServerHtmlServlet extends GgServerClientServlet {
//	
//	/**
//	 * the name and path of the main template page file, to start generation
//	 * with; if a respective file exists in the servlets data path, this file
//	 * will be used, otherwise, a respective file from the surrounding web-app's
//	 * context path will be used
//	 */
//	protected String basePage = "portal.html";
//	
//	/**
//	 * Initialize the HTML servlet. This implementation reads the base page
//	 * parameter from the settings. Sub classes overwriting this method thus
//	 * have to make the super call.
//	 * @see de.uka.ipd.idaho.goldenGateServer.client.GgServerClientServlet#init(de.uka.ipd.idaho.easyIO.settings.Settings)
//	 */
//	protected void init(Settings config) {
//		this.basePage = config.getSetting("basePage", this.basePage);
//	}
	
	/**
	 * This implementation simply loops through to doPost(), sub classes are
	 * welcome to overwrite it as needed.
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		this.doPost(request, response);
	}
	
	/**
	 * This implementation retrieves an HtmlPageBuilder from the
	 * getPageBuilder() method and streams the configured base page through it.
	 * Sub classes requiring a more sophisticated behavior are welcome to
	 * overwrite it as needed.
	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		HtmlPageBuilder pageBuilder = this.getPageBuilder(request, response);
		if (pageBuilder != null)
			this.sendHtmlPage(pageBuilder);
//		HtmlPageBuilder pb = this.getPageBuilder(request, response);
//		if (pb != null) {
//			response.setContentType("text/html");
//			
//			File baseFile = new File(this.dataFolder, this.basePage);
//			if (!baseFile.exists())
//				baseFile = new File(this.rootFolder, this.basePage);
//			InputStream is = new ByteOrderMarkFilterInputStream(new FileInputStream(baseFile));
//			
//			try {
//				htmlParser.stream(is, pb);
//				pb.close();
//			}
//			finally {
//				is.close();
//			}
//		}
	}
	
	/**
	 * Produce a page builder for answering a given request. This method may
	 * return null if and only if the argument request has been handled in a
	 * different (non-html) way , eg by sending an error if some sort of
	 * authentication has failed, or by sending some non-html response data.
	 * This implementation returns a plain HtmlPageBuilder, enough for
	 * assembling static web pages from multiple files. Sub classes are welcome
	 * to overwrite this method in order to provide more specific page builder
	 * implementations. If sub classes want to set HTTP response headers, they
	 * should do so in the body of this method, before creating the page
	 * builder. This is because the constructor of HtmlPageBuilder retrieves a
	 * writer from the response object, which disables any further modifications
	 * to the header of the response.
	 * @param request the request to answer
	 * @param response the response by means of which to answer
	 * @return a page builder to build the answer page for the argument request,
	 *         or null, if an error has been sent for the argument request
	 * @throws IOException
	 */
	protected HtmlPageBuilder getPageBuilder(HttpServletRequest request, HttpServletResponse response) throws IOException {
		return new HtmlPageBuilder(this, request, response) {};
	}
//	
//	/** HTML grammar for extracting type information from tokens, etc */
//	protected static Html html = new Html();
//	
//	/** HTML-configured parser for handling web page templates */
//	protected static Parser htmlParser = new Parser(html);
//	
//	/**
//	 * Builder object for HTML pages. This class wraps the output stream of an
//	 * HttpServletResponse. If a servlet wants to set response properties, it
//	 * has to do so before wrapping the response object in an HtmlPageBuidler.
//	 * 
//	 * @author sautter
//	 */
//	protected static class HtmlPageBuilder extends TokenReceiver {
//		
//		/** HTML grammar for extracting type information from tokens, etc */
//		protected static Html html = GgServerHtmlServlet.html;
//		
//		/** the servlet that operates the page builder */
//		protected GgServerHtmlServlet host;
//		
//		/** the request in response to which the page is generated */
//		public final HttpServletRequest request;
//		
//		//	local status information
//		private boolean inHyperLink = false;
//		private boolean inTitle = false;
//		private String title;
//		
//		//	output writer
//		private BufferedWriter out;
//		
//		/**
//		 * Constructor
//		 * @param host the surrounding servlet (required so this class can be
//		 *            static)
//		 * @param request the HttpServletRequest to answer
//		 * @param response the HttpServletResponse to write the answer to
//		 * @throws IOException
//		 */
//		protected HtmlPageBuilder(GgServerHtmlServlet host, HttpServletRequest request, HttpServletResponse response) throws IOException {
//			this.host = host;
//			this.request = request;
//			this.out = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), "utf-8"));
//		}
//		
//		/**
//		 * Close the page this page builder builds. This will flush the enclosed
//		 * writer, triggering the page being sent to the requesting browser. The
//		 * implementations of the doPost() and doGet() methods in the
//		 * surrounding servlet invokes this method as its last action. Sub
//		 * classes overwriting the doGet() or doPost() must invoke the close()
//		 * method as well in order for the built web page being sent to the
//		 * requesting browser.
//		 * @see de.uka.ipd.idaho.htmlXmlUtil.TokenReceiver#close()
//		 */
//		public void close() throws IOException {
//			this.out.flush();
//		}
//		
//		/**
//		 * Write an HTML token (tag or textual content) to the web page built by
//		 * this page builder
//		 * @param token the token to write
//		 * @throws IOException
//		 */
//		public void storeToken(String token) throws IOException {
//			this.storeToken(token, 0);
//		}
//		
//		/**
//		 * Write a string to the page being built.
//		 * @param s the string to write
//		 * @throws IOException
//		 */
//		public void write(String s) throws IOException {
//			this.out.write(s);
//		}
//		
//		/**
//		 * Write a line break to the page being built.
//		 * @throws IOException
//		 */
//		public void newLine() throws IOException {
//			this.out.newLine();
//		}
//		
//		/**
//		 * Write a string and a subsequent lin break to the page being built.
//		 * This method is equivalent to first invoking write() and then
//		 * newLine().
//		 * @param s the string to write
//		 * @throws IOException
//		 */
//		public void writeLine(String s) throws IOException {
//			this.out.write(s);
//			this.out.newLine();
//		}
//		
//		/* (non-Javadoc)
//		 * @see de.uka.ipd.idaho.htmlXmlUtil.TokenReceiver#storeToken(java.lang.String, int)
//		 */
//		public void storeToken(String token, int treeDepth) throws IOException {
//			if (html.isTag(token)) {
//				String type = html.getType(token);
//				
//				if ("includeFile".equals(type)) {
//					TreeNodeAttributeSet as = TreeNodeAttributeSet.getTagAttributes(token, html);
//					String includeFile = as.getAttribute("file");
//					if (includeFile != null) 
//						this.includeFile(includeFile);
//				}
//				
//				else if (type.startsWith("include")) {
//					if (!html.isEndTag(token))
//						this.include(type, token);
//				}
//				
//				//	page title
//				else if ("title".equalsIgnoreCase(html.getType(token))) {
//					if (html.isEndTag(token)) {
//						this.write("<title>");
//						this.write(this.getPageTitle(this.title));
//						this.write("</title>");
//						this.newLine();
//						this.inTitle = false;
//					}
//					else this.inTitle = true;
//				}
//				
//				//	page head
//				else if ("head".equalsIgnoreCase(type) && html.isEndTag(token)) {
//					
//					//	write extensions to page head
//					this.extendPageHead();
//					
//					//	close page head
//					this.writeLine(token);
//				}
//				
//				//	start of page body
//				else if ("body".equalsIgnoreCase(type) && !html.isEndTag(token)) {
//					
//					//	include calls to doOnloadCalls() and doOnunloadCalls() functions
//					this.writeLine("<body onload=\"doOnloadCalls();\" onunload=\"doOnunloadCalls();\">");
//				}
//				
//				//	image, make link absolute
//				else if ("img".equalsIgnoreCase(type)) {
//					
//					//	check for link
//					if (token.indexOf("src=\"") != -1) {
//						
//						//	check if link absolute
//						if (token.indexOf("src=\"http://") == -1)
//							token = token.replaceAll("src\\=\\\"(\\.\\/)?", ("src=\"" + this.request.getContextPath() + "/"));
//					}
//					this.write(token);
//				}
//				
//				//	other token
//				else {
//					
//					//	make href absolute
//					if ("a".equalsIgnoreCase(type)) {
//						if (token.indexOf("href=\"./") != -1)
//							token = token.replaceAll("href\\=\\\"\\.\\/", ("href=\"" + this.request.getContextPath() + "/"));
//						else if (token.indexOf("href=\"//") != -1)
//							token = token.replaceAll("href\\=\\\"\\/\\/", ("href=\"" + "/"));
//						else if (token.indexOf("href=\"/") != -1)
//							token = token.replaceAll("href\\=\\\"\\/", ("href=\"" + this.request.getContextPath() + "/"));
//						else if (token.indexOf("href=\"") != -1) {
//							if ((token.indexOf("href=\"http://") == -1) && (token.indexOf("href=\"ftp://") == -1))
//								token = token.replaceAll("href\\=\\\"", ("href=\"" + this.request.getContextPath() + "/"));
//						}
//					}
//					
//					//	write token
//					this.write(token);
//					
//					// do not insert line break after hyperlink tags, bold tags, and span tags
//					if (!"a".equalsIgnoreCase(type) && !"b".equalsIgnoreCase(type) && !"span".equalsIgnoreCase(type))
//						this.newLine();
//					
//					//	remember being in hyperlink (for auto-activation)
//					if ("a".equals(type))
//						this.inHyperLink = !html.isEndTag(token);
//				}
//			}
//			
//			//	textual content
//			else {
//				
//				//	remove spaces from links, and activate them
//				if ((token.startsWith("http:") || token.startsWith("ftp:")) && (token.indexOf("tp: //") != -1)) {
//					String link = token.replaceAll("\\s", "");
//					if (!this.inHyperLink) this.write("<a mark=\"autoGenerated\" href=\"" + link + "\">");
//					this.write(link);
//					if (!this.inHyperLink) this.write("</a>");
//				}
//				
//				//	store title to facilitate modification by sub classes
//				else if (this.inTitle)
//					this.title = token;
//				
//				//	other token, just write it
//				else this.write(token);
//			}
//		}
//		
//		/**
//		 * Retrieve a custom title for the web page under construction in this
//		 * page builder. This default implementation simply returns the original
//		 * (the argument) title, sub classes are welcome to overwrite it as
//		 * needed.
//		 * @param title the original title, as specified in the base page
//		 * @return the title for the web page
//		 */
//		protected String getPageTitle(String title) {
//			return title;
//		}
//		
//		private void extendPageHead() throws IOException {
//			
//			//	include CSS
//			String[] cssStylesheets = this.host.getCssStylesheets();
//			if (cssStylesheets != null)
//				for (int c = 0; c < cssStylesheets.length; c++) {
//					
//					//	make stylesheet URL absolute
//					String cssStylesheetUrl = cssStylesheets[c];
//					if (cssStylesheetUrl == null)
//						continue;
//					if (cssStylesheetUrl.indexOf("://") == -1)
//						cssStylesheetUrl = this.request.getContextPath() + this.host.dataPath + "/" + cssStylesheetUrl;
//					
//					//	write link
//					this.writeLine("<link rel=\"stylesheet\" type=\"text/css\" media=\"all\" href=\"" + cssStylesheetUrl + "\"></link>");
//				}
//			
//			//	include JavaScript
//			String[] javaScriptFiles = this.host.getJavaScriptFiles();
//			if (javaScriptFiles != null)
//				for (int j = 0; j < javaScriptFiles.length; j++) {
//					
//					//	make JavaScript file URL absolute
//					String javaScriptUrl = javaScriptFiles[j];
//					if (javaScriptUrl == null)
//						continue;
//					if (javaScriptUrl.indexOf("://") == -1)
//						javaScriptUrl = this.request.getContextPath() + this.host.dataPath + "/" + javaScriptUrl;
//					
//					//	write link
//					this.writeLine("<script type=\"text/javascript\" src=\"" + javaScriptUrl + "\"></script>");
//				}
//			
//			//	get JavaScript calls for page loading
//			String[] onloadCalls = this.host.getOnloadCalls();
//			this.writeLine("<script type=\"text/javascript\">");
//			this.writeLine("function doOnloadCalls() {");
//			if (onloadCalls != null)
//				for (int f = 0; f < onloadCalls.length; f++) {
//					String call = onloadCalls[f];
//					if (call != null)
//						this.writeLine(call + (call.endsWith(";") ? "" : ";"));
//				}
//			this.writeLine("}");
//			
//			//	get JavaScript calls for page un-loading
//			String[] onunloadCalls = this.host.getOnunloadCalls();
//			this.writeLine("function doOnunloadCalls() {");
//			if (onunloadCalls != null)
//				for (int f = 0; f < onunloadCalls.length; f++) {
//					String call = onunloadCalls[f];
//					if (call != null)
//						this.writeLine(call + (call.endsWith(";") ? "" : ";"));
//				}
//			this.writeLine("}");
//			this.writeLine("</script>");
//			
//			//	write servlet specific header
//			this.host.writePageHeadExtensions(this);
//			
//			//	write page builder specific header
//			this.writePageHeadExtensions();
//		}
//		
//		/**
//		 * Write the content of a file to a page. This method expects the
//		 * specified file to contain HTML code, and it writes out the content of
//		 * the body tag, thus ignoring all information in the file's head. The
//		 * specified file is first searched for in the servlet's data path; if
//		 * it is not found there, it is searched in the surrounding web-app's
//		 * context path. If it is not found there, either, a respective error
//		 * message is written as an XML comment. The two-step hierarchical
//		 * search for the file is in order to facilitate providing files used by
//		 * multiple servlets in a central location (the web-app's context path),
//		 * while still enabling individual servlets to replace specific files
//		 * with a servlet specific version.
//		 * @param fileName the name of the file to include
//		 * @throws IOException
//		 */
//		protected void includeFile(String fileName) throws IOException {
//			this.newLine();
//			try {
//				this.host.includeFile(fileName, this);
//			}
//			catch (IOException ioe) {
//				this.writeExceptionAsXmlComment(("exception including file '" + fileName + "'"), ioe);
//			}
//			this.newLine();
//		}
//		
//		/**
//		 * Write an exception to an HTML page as an XML comment
//		 * @param label a custom message label to give implementation specific
//		 *            context information
//		 * @param e the exception to write
//		 * @throws IOException
//		 */
//		protected void writeExceptionAsXmlComment(String label, Exception e) throws IOException {
//			this.writeLine("<!-- " + label + ": " + e.getMessage());
//			StackTraceElement[] ste = e.getStackTrace();
//			for (int s = 0; s < ste.length; s++)
//				this.writeLine("  " + ste[s].toString());
//			this.writeLine("  " + label + " -->");
//		}
//		
//		/**
//		 * Handle a sub class specific signal tag. This method is the main
//		 * extension point for servlet specific implementations of
//		 * HtmlPageBuilder, as all signal tags (ie tags whose type starts with
//		 * 'include') delegate here, except for 'includeFile'. This default
//		 * implementation simply adds an XML comment indicating that the
//		 * specified tag has not been understood, thus effectively ignoring the
//		 * signal tag alltogether. Sub classes should overwrite this method to
//		 * filter out their specific signal tags and delegate to this
//		 * implementation for ones that they do not understand.
//		 * @param type the type of the signal tag
//		 * @param tag the signal tag as a whole, eg to parse attributes from
//		 * @throws IOException
//		 */
//		protected void include(String type, String tag) throws IOException {
//			this.writeLine("<!-- include tag '" + type + "' not understood -->");
//		}
//		
//		/**
//		 * Write servlet specific extensions to a page head. This may be, for
//		 * instance, meta tags, JavaScrips, or CSS style information. This default
//		 * implementation does nothing, sub classes are welcome to overwrite it as
//		 * needed.
//		 * @throws IOException
//		 */
//		protected void writePageHeadExtensions() throws IOException {}
//	}
//	
//	private void includeFile(String fileName, final HtmlPageBuilder pb) throws IOException {
//		InputStream is = null;
//		try {
//			TokenReceiver fr = new TokenReceiver() {
//				private boolean inBody = false;
//				public void close() throws IOException {}
//				public void storeToken(String token, int treeDepth) throws IOException {
//					if (html.isTag(token) && "body".equalsIgnoreCase(html.getType(token))) {
//						if (html.isEndTag(token))
//							this.inBody = false;
//						else
//							this.inBody = true;
//					}
//					else if (this.inBody) pb.storeToken(token);
//				}
//			};
//			File includeFile = new File(this.dataFolder, fileName);
//			if (!includeFile.exists())
//				includeFile = new File(this.rootFolder, fileName);
//			if (includeFile.exists()) {
//				is = new ByteOrderMarkFilterInputStream(new FileInputStream(includeFile));
//				htmlParser.stream(is, fr);
//			}
//			else pb.storeToken("<!-- file '" + fileName + "' not found -->");
//		}
//		catch (Exception e) {
//			throw new IOException(e.getMessage());
//		}
//		finally {
//			if (is != null)
//				is.close();
//		}
//	}
//	
//	/**
//	 * Retrieve an array holding the addresses of CSS stylesheets to reference
//	 * in a page head. If an address includes a protocol, eg 'http://', it is
//	 * interpreted as absolute. Otherwise, it is interpreted relative to the
//	 * servlet's data path. This default implementation returns an empty array,
//	 * sub classes are welcome to overwrite it as needed.
//	 * @return an array holding the addresses of CSS stylesheets to reference in
//	 *         a page head
//	 */
//	protected String[] getCssStylesheets() {
//		return new String[0];
//	}
//	
//	/**
//	 * Retrieve an array holding the addresses of JavaScript files to reference
//	 * in a page head. If an address includes a protocol, eg 'http://', it is
//	 * interpreted as absolute. Otherwise, it is interpreted relative to the
//	 * servlet's data path. This default implementation returns an empty array,
//	 * sub classes are welcome to overwrite it as needed.
//	 * @return an array holding the addresses of JavaScript files to reference
//	 *         in a page head
//	 */
//	protected String[] getJavaScriptFiles() {
//		return new String[0];
//	}
//	
//	/**
//	 * Write servlet specific extensions to a page head. This may be, for
//	 * instance, meta tags, JavaScrips, or CSS style information. This default
//	 * implementation does nothing, sub classes are welcome to overwrite it as
//	 * needed.
//	 * @param out the page builder to write to.
//	 * @throws IOException
//	 */
//	protected void writePageHeadExtensions(HtmlPageBuilder out) throws IOException {}
//	
//	/**
//	 * Retrieve an array holding the JavaScript commands to execute when a page
//	 * is loaded in a browser. This default implementation returns an empty
//	 * array, sub classes are welcome to overwrite it as needed.
//	 * @return an array holding the JavaScript commands to execute when a page
//	 *         is loaded in a browser
//	 */
//	protected String[] getOnloadCalls() {
//		return new String[0];
//	}
//	
//	/**
//	 * Retrieve an array holding the JavaScript commands to execute when a page
//	 * is un-loaded in a browser. This default implementation returns an empty
//	 * array, sub classes are welcome to overwrite it as needed.
//	 * @return an array holding the JavaScript commands to execute when a page
//	 *         is un-loaded in a browser
//	 */
//	protected String[] getOnunloadCalls() {
//		return new String[0];
//	}
}
