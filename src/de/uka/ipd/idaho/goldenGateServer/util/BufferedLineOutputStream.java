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

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * Hybrid of a BufferedOutputStream and a BufferedWriter, to facilitate writing
 * network action headers line-wise as strings, but still handle binary data
 * without the overhead of Base64. String data is read as UTF-8 encoded by
 * default.
 * 
 * @author sautter
 */
public class BufferedLineOutputStream extends BufferedOutputStream {
	private String encoding;
	private BufferedWriter writer = null;
	
	/** Constructor
	 * @param out the output stream to wrap
	 * @param encoding the character encoding for string data
	 */
	public BufferedLineOutputStream(OutputStream out, String encoding) {
		super(out);
		this.encoding = encoding;
	}
	
	/**
	 * Write a string of character data. This method converts the argument
	 * string into bytes using the encoding handed to the constructor.
	 * @param str the string to write
	 * @throws IOException
	 */
	public void write(String str) throws IOException {
		this.write(str.getBytes(this.encoding));
	}
	
	/**
	 * Write a line break. This method writes cross platform line breaks, i.e.,
	 * a carriage return ('\r') and subsequently a newline character ('\n').
	 * @throws IOException
	 */
	public void newLine() throws IOException {
		this.write((int) '\r');
		this.write((int) '\n');
	}
	
	/**
	 * Write a string of character data, succeeded by a line break. This method
	 * converts the argument string into bytes using the encoding handed to the
	 * constructor. Calling this method is equivalent to calling write() with
	 * the same argument string and newLine() immediately afterward.
	 * @param str the string to write
	 * @throws IOException
	 */
	public void writeLine(String str) throws IOException {
		this.write(str);
		this.newLine();
	}
	
//	/* (non-Javadoc)
//	 * @see java.io.BufferedOutputStream#flush()
//	 */
//	public synchronized void flush() throws IOException {
//		if (this.writer != null)
//			this.writer.flush();
//		else super.flush();
//	}
//	
//	/* (non-Javadoc)
//	 * @see java.io.FilterOutputStream#close()
//	 */
//	public void close() throws IOException {
//		if (this.writer != null) {
//			this.writer.flush();
//			this.writer.close();
//		}
//		else super.close();
//	}
//	
	/**
	 * Wrap the output stream in an actual writer, using the encoding handed to
	 * the constructor. The stream proper should not be written to by client
	 * code any more after this method has been called. At the very least, the
	 * writer must be flushed as the last action before writing directly to the
	 * output stream again.
	 * @return a BufferedWriter wrapping this output stream
	 * @throws IOException
	 */
	public BufferedWriter toWriter() throws IOException {
		if (this.writer == null)
			this.writer = new BufferedWriter(new OutputStreamWriter(this, this.encoding));
		return this.writer;
	}
}