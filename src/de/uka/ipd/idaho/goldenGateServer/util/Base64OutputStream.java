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

/**
 * Output stream for sending binary data through a character level connection
 * using Base64 encoding.
 * 
 * @author sautter
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
		if (this.out == null) throw new IOException("Closed.");
		
		this.buffer[this.bufferLevel++] = b;
		if (this.bufferLevel == 3)
			this.writeBuffer("");
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
		if (this.out == null) throw new IOException("Closed.");
		
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
		if (this.out == null) throw new IOException("Closed.");
		
		if (this.bufferLevel != 0) {
			String padding = "";
			while (this.bufferLevel < 3) {
				this.buffer[this.bufferLevel++] = 0;
				padding += Base64.paddingChar;
			}
			this.writeBuffer(padding);
		}
//		this.out.flush();
//		if (closeWriter)
//			this.out.close();
		if (closeWriter) {
			this.out.flush();
			this.out.close();
		}
		this.out = null;
	}
	
	private synchronized void writeBuffer(String padding) throws IOException {
		if (this.out == null) throw new IOException("Closed.");
		
		// these three 8-bit (ASCII) characters become one 24-bit number
		int byteBlock = ((this.buffer[0] & 255) << 16) + ((this.buffer[1] & 255) << 8) + (this.buffer[2] & 255);
		
		// this 24-bit number gets separated into four 6-bit numbers
		int[] byteBlockCodes = {(byteBlock >>> 18) & 63, (byteBlock >>> 12) & 63, (byteBlock >>> 6) & 63, (byteBlock & 63)};
		
		// those four 6-bit numbers are used as indices into the base64 character list
		String result = (
				"" + Base64.base64chars.charAt(byteBlockCodes[0]) +
				"" + Base64.base64chars.charAt(byteBlockCodes[1]) +
				"" + Base64.base64chars.charAt(byteBlockCodes[2]) +
				"" + Base64.base64chars.charAt(byteBlockCodes[3])
				);
		
		this.bufferLevel = 0;
//		System.out.print(result.substring(0, (4 - padding.length())) + padding);
		this.out.write(result.substring(0, (4 - padding.length())) + padding);
	}
}
