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
package de.uka.ipd.idaho.goldenGateServer.fdp.util;

import java.util.regex.Pattern;

/**
 * @author sautter
 */
public class FileNameFilter {
	//	TODO move this bugger to EasyIO ???
	private Pattern[] filters;
	private boolean[] accept;
	private boolean acceptUnmatched;
	
	/** Constructor
	 * @param filters the filter patterns to use
	 */
	public FileNameFilter(String[] filters) {
		this.filters = new Pattern[filters.length];
		this.accept = new boolean[filters.length];
		boolean acceptUnmatched = true;
		for (int f = 0; f < filters.length; f++) {
			if (filters[f].startsWith("-")) {
				this.filters[f] = Pattern.compile(filters[f].substring("-".length()));
				this.accept[f] = false;
				acceptUnmatched = true;
			}
			else {
				this.filters[f] = Pattern.compile(filters[f]);
				this.accept[f] = true;
				acceptUnmatched = false;
			}
		}
		this.acceptUnmatched = acceptUnmatched;
	}
	
	/* (non-Javadoc)
	 * @see java.io.FileFilter#accept(java.io.File)
	 */
	public boolean acceptName(String fileName) {
		if (fileName.endsWith(".old"))
			return false; // deleted or replaced
		if (fileName.endsWith(".writing"))
			return false; // in process of being received, not complete yet
		for (int f = 0; f < this.filters.length; f++) {
			if (this.filters[f].matcher(fileName).matches())
				return this.accept[f];
		}
		return this.acceptUnmatched;
	}
}
