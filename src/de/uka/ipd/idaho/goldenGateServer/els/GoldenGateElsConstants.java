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
package de.uka.ipd.idaho.goldenGateServer.els;

import de.uka.ipd.idaho.goldenGateServer.util.DataObjectUpdateConstants;

/**
 * Constant bearer for GoldenGATE External Linking Service.
 * 
 * @author sautter
 */
public interface GoldenGateElsConstants extends DataObjectUpdateConstants {
	
	/** the user name GoldenGATE ELS uses to write to data objects */
	public static final String UPDATE_USER_NAME = "ExternalLinkService";
	
	/**
	 * An external link, i.e., a reference from a document or a detail inside a
	 * document to an external address or other document detail.
	 * 
	 * @author sautter
	 */
	public static class ExternalLink {
		
		/** the ID of the data object the link belongs to or in */
		public final String dataId;
		
		/** the ID of the detail inside the data object the link belongs to (if different from data object proper, otherwise null) */
		public final String detailId;
		
		/** the type of link, i.e., what it links to */
		public final String type;
		
		/** the link proper, e.g. a URL or DOI */
		public final String link;
		
		/** the status of the link ('P' for 'pending', 'H' for 'handled' (added to the data object), 'D' for 'deleted') */
		public final char status;
		
		ExternalLink(String dataId, String detailId, String type, String link) {
			this(dataId, detailId, type, link, 'P');
		}
		
		ExternalLink(String dataId, String detailId, String type, String link, char status) {
			this.dataId = dataId;
			this.detailId = detailId;
			this.type = type;
			this.link = link;
			this.status = status;
		}
	}
	
	/**
	 * Handler writing external links back to the document they belong to. The
	 * <code>handleLink()</code> method must indicate whether or not a given
	 * link was added to the underlying document. Soon as one handler returns
	 * true on that method, no further handlers will be consulted, and thus
	 * implementations of this class should be careful.
	 * 
	 * @author sautter
	 */
	public static interface ExternalLinkHandler {
		
		/**
		 * Handle an external link on data write-through, i.e., put the link
		 * into the data object it belongs to. The returned boolean indicates
		 * whether or not the link was handled by this handler. Soon as one
		 * handler returns true on this method, no further handlers will be
		 * consulted.
		 * @param data the data object the link belongs to
		 * @param link the link to handle
		 * @return true if the link was handled by the handler
		 */
		public abstract boolean handleLink(Object data, ExternalLink link);
	}
}
