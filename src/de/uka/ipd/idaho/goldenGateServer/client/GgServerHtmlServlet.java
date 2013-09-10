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
}