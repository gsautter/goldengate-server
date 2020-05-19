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
package de.uka.ipd.idaho.goldenGateServer;

import java.io.PrintStream;

/**
 * @author sautter
 *
 */
class GoldenGateServerMessageFormatter {
	
	static String getFormat(char logLevel, char msgSource) {
		return (getForegroundFormat(logLevel) + ";" + getBackgroundFormat(msgSource));
	}
	
	static void printMessage(String msg, int logLevel, char msgSource, String afterFormat, PrintStream out) {
		String format = getFormat(encodeLogLevel(logLevel), msgSource);
		printMessage(msg, format, afterFormat, out);
	}
	
	static void printError(Throwable error, char msgSource, PrintStream out) {
		String stackTraceFormat = getFormat('T', msgSource);
		StackTraceElement[] stes = error.getStackTrace();
		printMessage(error.toString(), stackTraceFormat, null, out);
		for (int s = 0; s < stes.length; s++)
			printMessage(("\tat " + stes[s].toString()), stackTraceFormat, null, out);
	}
	
	static void printMessage(String msg, String format, String afterFormat, PrintStream out) {
		out.println("\u001B[0m" + "\u001B[" + format + "m" + msg + "\u001B[0m" + ((afterFormat == null) ? "" : ("\u001B[" + afterFormat + "m")));
	}
	
	private static char encodeLogLevel(int level) {
		if (level == GoldenGateServerActivityLogger.LOG_LEVEL_DEBUG)
			return 'D';
		else if (level == GoldenGateServerActivityLogger.LOG_LEVEL_INFO)
			return 'I';
		else if (level == GoldenGateServerActivityLogger.LOG_LEVEL_WARNING)
			return 'W';
		else if (level == GoldenGateServerActivityLogger.LOG_LEVEL_ERROR)
			return 'E';
		else return 'R'; // result
	}
	
	private static final String ERROR_FOREGROUND_FORMAT = "38;5;9";
	private static final String WARNING_FOREGROUND_FORMAT = "38;5;3";
	private static final String INFO_FOREGROUND_FORMAT = "38;5;7";
	private static final String DEBUG_FOREGROUND_FORMAT = "38;5;248";
	private static final String MESSAGE_FOREGROUND_FORMAT = "38;5;15";
	
	private static String getForegroundFormat(char level) {
		if (level == 'E')
			return ERROR_FOREGROUND_FORMAT;
		else if (level == 'W')
			return WARNING_FOREGROUND_FORMAT;
		else if (level == 'I')
			return INFO_FOREGROUND_FORMAT;
		else if (level == 'D')
			return DEBUG_FOREGROUND_FORMAT;
		else if (level == 'T') // stack trace coming
			return ERROR_FOREGROUND_FORMAT;
		else if (level == 'R') // result
			return MESSAGE_FOREGROUND_FORMAT;
		else return MESSAGE_FOREGROUND_FORMAT; // use default format for anything unformatted
	}
	
	private static final String BACKGROUND_BACKGROUND_FORMAT = "48;5;235";
	private static final String NETWORK_BACKGROUND_FORMAT = "48;5;17";
	private static final String CONSOLE_BACKGROUND_FORMAT = "48;5;0";
	
	private static String getBackgroundFormat(char source) {
		if (source == 'C')
			return CONSOLE_BACKGROUND_FORMAT;
		else if (source == 'N')
			return NETWORK_BACKGROUND_FORMAT;
		else if (source == 'B')
			return BACKGROUND_BACKGROUND_FORMAT;
		else return CONSOLE_BACKGROUND_FORMAT; // use default format for anything unformatted
	}
}
