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
package de.uka.ipd.idaho.goldenGateServer.util;


import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * Input stream for reading binary data that comes in Base64 encoding via a
 * character level connection.
 * 
 * @author sautter
 */
public class Base64InputStream extends InputStream {
	private Reader in;
	private int[] buffer = new int[3];
	private int bufferLevel = 3;
	private int bufferEnd = -1;
	
	/**
	 * Constructor
	 * @param in the Reader to read from
	 */
	public Base64InputStream(Reader in) {
		this.in = in;
	}
	
	/* (non-Javadoc)
	 * @see java.io.InputStream#read()
	 */
	public synchronized int read() throws IOException {
		if (this.bufferLevel == this.bufferEnd)
			return -1;
		
		if (this.bufferLevel == 3) {
			this.fillBuffer();
			return this.read();
		}
		else return this.buffer[this.bufferLevel++];
	}
	
	/**
	 * Close the stream. This method will simply close the underlying reader. 
	 * @see java.io.InputStream#close()
	 */
	public synchronized void close() throws IOException {
		this.in.close();
	}
	
	private synchronized void fillBuffer() throws IOException {
		char[] chars = new char[4];
		for (int c = 0; c < 4; c++) {
			int read = this.in.read();
			if (read == -1) {
				this.bufferEnd = ((c == 0) ? 0 : (c - 1));
				while (c < 4)
					chars[c++] = Base64.paddingChar;
			}
			else chars[c] = ((char) read);
		}
		boolean lastIsPad = (chars[3] == Base64.paddingChar);
		boolean secondLastIsPad = (chars[2] == Base64.paddingChar);
		int[] byteBlockCodes = {
				Base64.decodeBase64Char(chars[0]), 
				Base64.decodeBase64Char(chars[1]), 
				(secondLastIsPad ? 0 : Base64.decodeBase64Char(chars[2])), 
				(lastIsPad ? 0 : Base64.decodeBase64Char(chars[3]))
				};
		int byteBlock = (byteBlockCodes[0] << 18) + (byteBlockCodes[1] << 12) + (byteBlockCodes[2] << 6) + byteBlockCodes[3];
		this.buffer[0] = ((byteBlock >>> 16) & 255);
		this.buffer[1] = (secondLastIsPad ? -1 : ((byteBlock >>> 8) & 255));
		this.buffer[2] = (lastIsPad ? -1 : (byteBlock & 255));
		this.bufferLevel = 0;
	}
}
