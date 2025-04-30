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
import java.util.Arrays;

/**
 * Input stream for reading binary data that comes in Base64 encoding via a
 * character level connection.
 * 
 * @author sautter
 * 
 * @deprecated use de.uka.ipd.idaho.easyIO.streams.Base64.DecoderInputStream
 */
public class Base64InputStream extends InputStream {
	private Reader in;
	private int[] buffer = {-1, -1, -1};
	private int bufferPos = this.buffer.length; // initialize to end of buffer, filling on first read()
	
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
		if (this.in == null)
			throw new IOException("Cloased.");
		if (this.bufferPos == -1)
			return -1;
		if (this.bufferPos == this.buffer.length)
			this.fillBuffer();
		if (this.buffer[this.bufferPos] == -1) {
			this.bufferPos = -1;
			return -1;
		}
		else return this.buffer[this.bufferPos++];
	}
	
	/**
	 * Close the stream. This method will simply close the underlying reader. 
	 * @see java.io.InputStream#close()
	 */
	public synchronized void close() throws IOException {
		this.in.close();
		this.in = null;
	}
	
	private synchronized void fillBuffer() throws IOException {
		Arrays.fill(this.buffer, -1);
		this.bufferPos = 0;
		
		char[] charBlock = new char[4];
		int read = this.in.read(charBlock, 0, charBlock.length);
		if (read < 2) // end of stream, or at least no complete byte
			return;
		if (read < charBlock.length)
			Arrays.fill(charBlock, read, charBlock.length, Base64.paddingChar);
		
		int byteBlock = (
				(Base64.decodeBase64Char(charBlock[0]) << 18)
				|
				(Base64.decodeBase64Char(charBlock[1]) << 12)
				|
				((charBlock[2] == Base64.paddingChar) ? 0 : (Base64.decodeBase64Char(charBlock[2]) << 6))
				|
				((charBlock[3] == Base64.paddingChar) ? 0 : (Base64.decodeBase64Char(charBlock[3]) << 0))
			);
		this.buffer[0] = ((byteBlock >>> 16) & 0xFF);
		if (charBlock[2] != Base64.paddingChar)
			this.buffer[1] = ((byteBlock >>> 8) & 0xFF);
		if (charBlock[3] == Base64.paddingChar)
			this.buffer[2] = ((byteBlock >>> 0) & 0xFF);
	}
}
