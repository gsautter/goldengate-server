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
package de.uka.ipd.idaho.goldenGateServer;

/**
 * Logger for runtime activity of a GoldenGATE Server.
 * 
 * @author sautter
 */
public interface GoldenGateServerActivityLogger {
	
	/** log level writing no messages to any log file */
	public static final int LOG_LEVEL_OFF = 0;
	
	/** log level writing only error messages to a log file */
	public static final int LOG_LEVEL_ERROR = 1;
	
	/** log level writing only error and warning messages to a log file */
	public static final int LOG_LEVEL_WARNING = 2;
	
	/** log level writing only error, warning, and info messages to a log file */
	public static final int LOG_LEVEL_INFO = 4;
	
	/** log level writing all messages to a log file */
	public static final int LOG_LEVEL_DEBUG = 8;
	
	/**
	 * Write an error level log message. The argument message will only end up
	 * in the log file if the log context level is LOG_LEVEL_ERROR or higher.
	 * 
	 * @param message the message to write to the log
	 */
	public abstract void logError(String message);
	
	/**
	 * Write an error level log message. The argument exception or error will
	 * only end up in the log file if the context log level is LOG_LEVEL_ERROR
	 * or higher.
	 * 
	 * @param error the error to write to the log
	 */
	public abstract void logError(Throwable error);
	
	/**
	 * Write a warning level log message. The argument message will only end up
	 * in the log file if the log level is LOG_LEVEL_WARNING or higher.
	 * 
	 * @param message the message to write to the log
	 */
	public abstract void logWarning(String message);
	
	/**
	 * Write an information level log message. The argument message will only
	 * end up in the log file if the log level is LOG_LEVEL_INFO or higher.
	 * 
	 * @param message the message to write to the log
	 */
	public abstract void logInfo(String message);
	
	/**
	 * Write a debug level log message. The argument message will only end up
	 * in the log file if the log level is LOG_LEVEL_DEBUG.
	 * 
	 * @param message the message to write to the log
	 */
	public abstract void logDebug(String message);
	
	/**
	 * Write an activity log message. This method is mainly intended for
	 * conditional logging after some timeout. A standard implementation will
	 * initially treat log messages as debug messages, but after expiration of
	 * some timeout escalate them to warning messages, including past ones.
	 * Consequentially, implementations of this method should collect the log
	 * messages and output them in bulk after the timeout expires.
	 * 
	 * @param message the message to write to the log
	 */
	public abstract void logActivity(String message);
	
	/**
	 * Write a log message regardless of the log level.
	 * 
	 * @param message the message to write to the log
	 */
	public abstract void logAlways(String message);
	
	/**
	 * Write a log message reflecting an immediate outcome of an action. This
	 * method might well behave differently for console and network actions,
	 * respectively, in particular because it is the preferred way of reporting
	 * the outcome of a console interaction.
	 * 
	 * @param message the message to write to the log
	 */
	public abstract void logResult(String message);
}
