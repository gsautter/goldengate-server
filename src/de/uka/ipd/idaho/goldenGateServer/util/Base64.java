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
package de.uka.ipd.idaho.goldenGateServer.util;

import java.util.ArrayList;


/**
 * Utility class for base 64 encoding and decoding
 * 
 * @author sautter
 */
public class Base64 {
	static final char paddingChar = '=';
	static final String base64chars = 
		"ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
		"abcdefghijklmnopqrstuvwxyz" +
		"0123456789" +
		"+-";
	static final int decodeBase64Char(char c) {
		if (('A' <= c) && (c <= 'Z'))
			return (c - 'A');
		else if (('a' <= c) && (c <= 'z'))
			return (c - 'a' + 26);
		else if (('0' <= c) && (c <= '9'))
			return (c - '0' + 52);
		else return ((c == '+') ? 62 : 63);
	}
	/**
	 * Encode an array of bytes into a Base64 string
	 * @param bytes the bytes to encode
	 * @return the Base64 code of the specified bytes
	 */
	public static final String encode(int[] bytes) {
		StringBuffer bytesAsBase64 = new StringBuffer();
		int[] byteBlockBuffer = new int[3];
		int byteBlockSize;
		String charBlockPadding = "";
		
		for (int b = 0; b < bytes.length; b += 3) {
			byteBlockSize = Math.min((bytes.length - b), 3);
			System.arraycopy(bytes, b, byteBlockBuffer, 0, byteBlockSize);
			while (byteBlockSize < 3) {
				byteBlockBuffer[byteBlockSize++] = 0;
				charBlockPadding += paddingChar;
			}
			
			// these three 8-bit (ASCII) characters become one 24-bit number
			int byteBlock = ((byteBlockBuffer[0] & 255) << 16) + ((byteBlockBuffer[1] & 255) << 8) + (byteBlockBuffer[2] & 255);
			
			// this 24-bit number gets separated into four 6-bit numbers
			int[] byteBlockCodes = {(byteBlock >>> 18) & 63, (byteBlock >>> 12) & 63, (byteBlock >>> 6) & 63, (byteBlock & 63)};
			
			// those four 6-bit numbers are used as indices into the base64 character list
			String charBlock = (
					"" + Base64.base64chars.charAt(byteBlockCodes[0]) +
					"" + Base64.base64chars.charAt(byteBlockCodes[1]) +
					"" + Base64.base64chars.charAt(byteBlockCodes[2]) +
					"" + Base64.base64chars.charAt(byteBlockCodes[3])
					);
			
			charBlock = (charBlock.substring(0, (4 - charBlockPadding.length())) + charBlockPadding);
			bytesAsBase64.append(charBlock);
		}
		return bytesAsBase64.toString();
	}
	
	/**
	 * Decode a Base64 encoded array of bytes
	 * @param base64 the Base64 string to decode
	 * @return the bytes encoded in the specified string
	 */
	public static final int[] decode(String base64) {
		ArrayList intList = new ArrayList();
		char[] chars = new char[4];
		for (int b = 0; b < base64.length(); b += 4) {
			for (int c = 0; c < 4; c++)
				chars[c] = base64.charAt(b + c);
			boolean lastIsPad = (chars[3] == Base64.paddingChar);
			boolean secondLastIsPad = (chars[2] == Base64.paddingChar);
			int[] byteBlockCodes = {
					Base64.decodeBase64Char(chars[0]), 
					Base64.decodeBase64Char(chars[1]), 
					(secondLastIsPad ? 0 : Base64.decodeBase64Char(chars[2])), 
					(lastIsPad ? 0 : Base64.decodeBase64Char(chars[3]))
					};
			int byteBlock = (byteBlockCodes[0] << 18) + (byteBlockCodes[1] << 12) + (byteBlockCodes[2] << 6) + byteBlockCodes[3];
			intList.add(new Integer((byteBlock >>> 16) & 255));
			if (!secondLastIsPad)
				intList.add(new Integer((byteBlock >>> 8) & 255));
			if (!lastIsPad)
				intList.add(new Integer(byteBlock & 255));
		}
		int[] plainBytes = new int[intList.size()];
		for (int i = 0; i < intList.size(); i++)
			plainBytes[i] = ((Integer) intList.get(i)).intValue();
		return plainBytes;
	}
}