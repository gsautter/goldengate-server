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
import java.io.OutputStream;
import java.io.Writer;
import java.util.Arrays;

/**
 * Output stream for sending binary data through a character level connection
 * using Base64 encoding.
 * 
 * @author sautter
 * 
 * @deprecated use de.uka.ipd.idaho.easyIO.streams.Base64.EncoderOutputStream
 */
public class Base64OutputStream extends OutputStream {
	private Writer out;
	private int[] buffer = new int[3];
	private int bufferLevel = 0;
	
	/**
	 * Constructor
	 * @param out the Writer for transferring the data written to the stream
	 */
	public Base64OutputStream(Writer out) {
		this.out = out;
	}
	
	/* (non-Javadoc)
	 * @see java.io.OutputStream#write(int)
	 */
	public synchronized void write(int b) throws IOException {
		if (this.out == null)
			throw new IOException("Closed.");
		this.buffer[this.bufferLevel++] = b;
		if (this.bufferLevel == 3)
			this.flushBuffer();
	}
	
 	/**
	 * Closes this output stream. This will pad any bytes remaining in the
	 * buffer to form a full triplet, write this data to the underlying writer,
	 * and flush the underlying writer afterward. After this method has been
	 * invoked, no further bytes should be written via the write() method
	 * because once the data has been padded, sending further bytes will result
	 * in an error. This method will not close the underlying writer, though.
	 * For this purpose, please use the close(boolean) method with true as the
	 * argument.
	 * @see java.io.OutputStream#close()
	 */
	public synchronized void close() throws IOException {
		if (this.out == null)
			throw new IOException("Closed.");
		this.close(false);
	}
	
	/**
	 * Closes this output stream. This will pad any bytes remaining in the
	 * buffer to form a full triplet, write this data to the underlying writer,
	 * and flush and optionally close the underlying writer afterward. After
	 * this method has been invoked, no further bytes should be written via the
	 * write() method because once the data has been padded, sending further
	 * bytes will result in an error.
	 * @param closeWriter close the underlying writer?
	 * @see java.io.OutputStream#close()
	 */
	public synchronized void close(boolean closeWriter) throws IOException {
		if (this.out == null)
			throw new IOException("Closed.");
		
		if (this.bufferLevel != 0)
			this.flushBuffer();
		
		if (closeWriter) {
			this.out.flush();
			this.out.close();
		}
		this.out = null;
	}
	
	private synchronized void flushBuffer() throws IOException {
		if (this.out == null)
			throw new IOException("Closed.");
		
		//	pad up buffer with 0
		if (this.bufferLevel < this.buffer.length)
			Arrays.fill(this.buffer, this.bufferLevel, this.buffer.length, 0);
		
		//	these three 8-bit (ASCII) characters become one 24-bit number
		int byteBlock = (
				((this.buffer[0] & 0xFF) << 16)
				|
				((this.buffer[1] & 0xFF) << 8)
				|
				((this.buffer[2] & 0xFF) << 0)
			);
		
		//	those four 6-bit numbers are used as indices into the base64 character list
		char[] byteBlockChars = {
				Base64.base64chars.charAt((byteBlock >>> 18) & 0x3F),
				Base64.base64chars.charAt((byteBlock >>> 12) & 0x3F),
				(this.bufferLevel < 2) ? Base64.paddingChar : Base64.base64chars.charAt((byteBlock >>> 6) & 0x3F),
				(this.bufferLevel < 3) ? Base64.paddingChar : Base64.base64chars.charAt((byteBlock >>> 0) & 0x3F),
			};
		
		this.bufferLevel = 0;
		this.out.write(byteBlockChars);
	}
}
