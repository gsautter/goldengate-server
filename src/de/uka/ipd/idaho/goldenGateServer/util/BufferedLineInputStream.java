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
 *     * Neither the name of the Universität Karlsruhe (TH) / KIT nor the
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
package de.uka.ipd.idaho.goldenGateServer.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Hybrid of a BufferedInputStream and a BufferedReader, to facilitate reading
 * network action headers line-wise as strings, but still handle binary data
 * without the overhead of Base64. String data is read as UTF-8 encoded by
 * default.
 * 
 * @author sautter
 */
public class BufferedLineInputStream extends BufferedInputStream {
	private static final int defaultLineBufferLength = 512;
	private String encoding;
	private BufferedReader reader = null;
	
	/** Constructor
	 * @param in the input stream to wrap
	 * @param encoding the character encoding for string data
	 */
	public BufferedLineInputStream(InputStream in, String encoding) {
		super(in);
		this.encoding = encoding;
	}
	
	/**
	 * Read a line of string data. This method reads the underlying stream up
	 * to the next carriage return ('\r'), line feed ('\n'), or carriage return
	 * immediately followed by a line feed ('\r\n'), just like the readLine()
	 * method of BufferedReader does. Then, this method decodes the bytes read
	 * into a string using the encoding handed to the constructor, and returns
	 * that string.
	 * @return the next line of string data
	 * @throws IOException
	 */
	public String readLine() throws IOException {
		return this.readLine(defaultLineBufferLength);
	}
	private String readLine(int lineBufferLength) throws IOException {
		
		//	buffer bytes
		this.mark(lineBufferLength);
		byte[] lineBuffer = new byte[lineBufferLength];
		int lineBufferLevel = this.read(lineBuffer, 0, lineBuffer.length);
		if (lineBufferLevel == -1)
			return null;
		
		//	find end of line
		int lineEnd = 0;
		while ((lineEnd < lineBufferLevel)) {
			
			//	newline, definitely line end
			if (lineBuffer[lineEnd] == '\n') {
				this.reset();
				this.skip(lineEnd + 1);
				return new String(lineBuffer, 0, lineEnd, this.encoding);
			}
			
			//	carriage return, line end, possibly with subsequent newline
			else if (lineBuffer[lineEnd] == '\r') {
				
				//	there might be a newline looming, better recurse (slow, but also a highly improbable case in the intended application)
				if ((lineEnd+1) == lineBufferLength) {
					this.reset();
					return this.readLine(lineBufferLength + 8);
				}
				
				//	return line (make sure to skip over both bytes in a cross platform line end)
				else {
					this.reset();
					this.skip(lineEnd + ((lineBuffer[lineEnd + 1] == '\n') ? 2 : 1));
					return new String(lineBuffer, 0, lineEnd, this.encoding);
				}
			}
			
			//	keep looking
			else lineEnd++;
		}
		
		//	didn't find line end by end of buffer, increase buffer size
		if (lineEnd == lineBufferLength) {
			this.reset();
			return this.readLine(lineBufferLength + defaultLineBufferLength);
		}
		
		//	we must have reached the end of the stream without encountering a carriage return or newline
		else {
			this.reset();
			this.skip(lineBufferLevel);
			return new String(lineBuffer, 0, lineBufferLevel, this.encoding);
		}
	}
	
	/**
	 * Wrap the input stream in an actual reader, using the encoding handed to
	 * the constructor. The stream proper should not be read from by client
	 * code any more after this method has been called.
	 * @return a BufferedReader wrapping this input stream
	 * @throws IOException
	 */
	public BufferedReader toReader() throws IOException {
		if (this.reader == null)
			this.reader = new BufferedReader(new InputStreamReader(this, this.encoding));
		return this.reader;
	}
	
	//	!!! TEST ONLY !!!
	public static void main(String[] args) throws IOException {
		String str = "Liné1\rLinè2\nLine3\r\n\r\nLinê4\n";
		System.out.println(str);
		System.out.println("-----");
		BufferedLineInputStream blin = new BufferedLineInputStream(new ByteArrayInputStream(str.getBytes("UTF-8")), "UTF-8");
		for (String line; (line = blin.readLine()) != null;) {
			if (line.length() == 0)
				break;
			System.out.println(line);
		}
		for (int b; (b = blin.read()) != -1;)
			System.out.print((char) b);
		System.out.println("-----");
		blin.close();
	}
}