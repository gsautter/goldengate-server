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

import java.util.Arrays;


/**
 * Utility class for base 64 encoding and decoding
 * 
 * @author sautter
 * 
 * @deprecated use de.uka.ipd.idaho.easyIO.streams.Base64
 */
public class Base64 {
	static final char paddingChar = '=';
	static final String base64chars = 
		"ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
		"abcdefghijklmnopqrstuvwxyz" +
		"0123456789" +
		"+/";
	static final int decodeBase64Char(char ch) {
		if (('A' <= ch) && (ch <= 'Z'))
			return (ch - 'A');
		else if (('a' <= ch) && (ch <= 'z'))
			return (ch - 'a' + 26);
		else if (('0' <= ch) && (ch <= '9'))
			return (ch - '0' + 52);
		else return ((ch == '+') ? 62 : 63);
	}
	
	/**
	 * Encode an array of bytes into a Base64 string
	 * @param bytes the bytes to encode
	 * @return the Base64 code of the specified bytes
	 */
	public static final String encode(int[] bytes) {
		StringBuffer base64 = new StringBuffer();
		int[] byteBlockBuffer = new int[3];
		
		for (int b = 0; b < bytes.length; b += 3) {
			int byteBlockSize = Math.min((bytes.length - b), 3);
			System.arraycopy(bytes, b, byteBlockBuffer, 0, byteBlockSize);
			if (byteBlockSize < byteBlockBuffer.length)
				Arrays.fill(byteBlockBuffer, byteBlockSize, byteBlockBuffer.length, 0);
//			while (byteBlockSize < 3) {
//				byteBlockBuffer[byteBlockSize++] = 0;
//				charBlockPadding += paddingChar;
//			}
			
			// these three bytes become one 24-bit number
			int byteBlock = (
					((byteBlockBuffer[0] & 0xFF) << 16)
					|
					((byteBlockBuffer[1] & 0xFF) << 8)
					|
					((byteBlockBuffer[2] & 0xFF) << 0)
				);
//			
//			// this 24-bit number gets separated into four 6-bit numbers
//			int[] byteBlockCodes = {
//					((byteBlock >>> 18) & 0x3F),
//					((byteBlock >>> 12) & 0x3F),
//					((byteBlock >>> 6) & 0x3F),
//					(byteBlock & 0x3F)
//				};
//			
//			// those four 6-bit numbers are used as indices into the base64 character list
//			String charBlock = (
//					"" + base64chars.charAt(byteBlockCodes[0]) +
//					"" + base64chars.charAt(byteBlockCodes[1]) +
//					"" + base64chars.charAt(byteBlockCodes[2]) +
//					"" + base64chars.charAt(byteBlockCodes[3])
//					);
//			charBlock = (charBlock.substring(0, (4 - charBlockPadding.length())) + charBlockPadding);
//			base64.append(charBlock);
			base64.append(base64chars.charAt((byteBlock >>> 18) & 0x3F));
			base64.append(base64chars.charAt((byteBlock >>> 12) & 0x3F));
			base64.append((byteBlockSize < 2) ? paddingChar : base64chars.charAt((byteBlock >>> 6) & 0x3F));
			base64.append((byteBlockSize < 3) ? paddingChar : base64chars.charAt((byteBlock >>> 0) & 0x3F));
		}
		return base64.toString();
	}
	
	/**
	 * Decode a Base64 encoded array of bytes
	 * @param base64 the Base64 string to decode
	 * @return the bytes encoded in the specified string
	 */
	public static final byte[] decode(String base64) {
		int byteCount = ((base64.length() / 4) * 3);
		if (base64.endsWith("" + paddingChar + "" + paddingChar))
			byteCount -= 2;
		else if (base64.endsWith("" + paddingChar))
			byteCount -= 1;
		byte[] bytes = new byte[byteCount];
		int bytePos = 0;
		char[] charBlock = new char[4];
		for (int c = 0; c < base64.length(); c += 4) {
			base64.getChars(c, (c + charBlock.length), charBlock, 0);
			int byteBlock = (
					(decodeBase64Char(charBlock[0]) << 18)
					|
					(decodeBase64Char(charBlock[1]) << 12)
					|
					((charBlock[2] == paddingChar) ? 0 : (decodeBase64Char(charBlock[2]) << 6))
					|
					((charBlock[3] == paddingChar) ? 0 : (decodeBase64Char(charBlock[3]) << 0))
				);
			bytes[bytePos++] = ((byte) ((byteBlock >>> 16) & 0xFF));
			if (bytePos < bytes.length)
				bytes[bytePos++] = ((byte) ((byteBlock >>> 8) & 0xFF));
			if (bytePos < bytes.length)
				bytes[bytePos++] = ((byte) ((byteBlock >>> 0) & 0xFF));
		}
		return bytes;
	}
//	public static final int[] decode(String base64) {
//		ArrayList intList = new ArrayList();
//		char[] chars = new char[4];
//		for (int b = 0; b < base64.length(); b += 4) {
//			base64.getChars(b, (b + chars.length), chars, 0);
//			boolean lastIsPad = (chars[3] == paddingChar);
//			boolean secondLastIsPad = (chars[2] == paddingChar);
//			int[] byteBlockCodes = {
//					decodeBase64Char(chars[0]), 
//					decodeBase64Char(chars[1]), 
//					(secondLastIsPad ? 0 : decodeBase64Char(chars[2])), 
//					(lastIsPad ? 0 : decodeBase64Char(chars[3]))
//					};
//			int byteBlock = ((byteBlockCodes[0] << 18) | (byteBlockCodes[1] << 12) | (byteBlockCodes[2] << 6) | byteBlockCodes[3]);
//			intList.add(new Integer((byteBlock >>> 16) & 0xFF));
//			if (!secondLastIsPad)
//				intList.add(new Integer((byteBlock >>> 8) & 0xFF));
//			if (!lastIsPad)
//				intList.add(new Integer(byteBlock & 0xFF));
//		}
//		int[] plainBytes = new int[intList.size()];
//		for (int i = 0; i < intList.size(); i++)
//			plainBytes[i] = ((Integer) intList.get(i)).intValue();
//		return plainBytes;
//	}
}