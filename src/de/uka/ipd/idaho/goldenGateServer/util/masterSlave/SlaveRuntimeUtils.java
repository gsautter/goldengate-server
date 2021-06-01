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
package de.uka.ipd.idaho.goldenGateServer.util.masterSlave;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;

import de.uka.ipd.idaho.gamta.util.ParallelJobRunner;

/**
 * Execution time utility for slave jobs, providing functionality for parameter
 * parsing and log file handling.
 * 
 * @author sautter
 */
public class SlaveRuntimeUtils implements SlaveConstants {
	
	/**
	 * Parse the arguments handed to a slave job <code>main()</code> method
	 * into a <code>Properties</code> object for easier access. Arguments that
	 * come without an <code>=</code> sign are mapped to themselves. 
	 * @param args the array of arguments to parse
	 * @return the properties object holding the parsed arguments
	 */
	public static Properties parseArguments(String[] args) {
		Properties argsMap = new Properties();
		for (int a = 0; a < args.length; a++) {
			int split = args[a].indexOf("=");
			if (split == -1)
				argsMap.setProperty(args[a], args[a]);
			else argsMap.setProperty(args[a].substring(0, split), args[a].substring(split + "=".length()));
		}
		return argsMap;
	}
	
	/**
	 * Set up logging using the arguments given in the argument map, namely the
	 * <code>LOG</code> and <code>VERBOSE</code> parameters. This method also
	 * cleans up log files older than 24 hours.
	 * @param args the argument map to use
	 * @param logFilePrefix the log file prefix to use
	 * @param cleanUpOlderThan the threshold for the last modification date of
	 *            the log files to clean up
	 * @throws IOException
	 */
	public static void setUpLogFiles(Properties args, String logFilePrefix) throws IOException {
		setUpLogFiles(args, logFilePrefix, 0);
	}
	
	/**
	 * Set up logging using the arguments given in the argument map, namely the
	 * <code>LOG</code> and <code>VERBOSE</code> parameters. If the
	 * <code>cleanUpOlderThan</code> argument is larger than 0, this method
	 * also clean up log files older than the indicated time.
	 * @param args the argument map to use
	 * @param logFilePrefix the log file prefix to use
	 * @param cleanUpOlderThan the threshold for the last modification date of
	 *            the log files to clean up
	 * @throws IOException
	 */
	public static void setUpLogFiles(Properties args, String logFilePrefix, long cleanUpOlderThan) throws IOException {
		String logPath = args.getProperty(LOG_PATH_PARAMETER);
		boolean verbose = args.containsKey(VERBOSE_PARAMETER);
		
		//	set up logging (if we have a folder)
		if (logPath != null) {
			File logFolder = new File(logPath);
			
			//	clean up log files older than 24 hours
			if (cleanUpOlderThan == 0)
				SlaveRuntimeUtils.cleanUpLogFiles(logFolder, logFilePrefix);
			else if (cleanUpOlderThan > 0)
				SlaveRuntimeUtils.cleanUpLogFiles(logFolder, logFilePrefix, cleanUpOlderThan);
			
			//	set up logging (keep error stream going to master process, though)
			SlaveRuntimeUtils.setUpLogFiles(logFolder, logFilePrefix, verbose, true);
		}
		
		//	silence standard output stream otherwise (keep error stream going to master process, though)
		else if (!verbose) System.setOut(new PrintStream(new OutputStream() {
			public void write(int b) throws IOException {}
			public void write(byte[] b) throws IOException {}
			public void write(byte[] b, int off, int len) throws IOException {}
			public void flush() throws IOException {}
			public void close() throws IOException {}
		}));
	}
	
	/**
	 * Set up the maximum number of CPU cores for parallel loop execution based
	 * on the arguments given in the argument map, namely the <code>MAXCORES</code>
	 * parameter.
	 * @param args the argument map to use
	 * @throws IOException
	 */
	public static void setUpMaxCores(Properties args) {
		String maxCoresStr = args.getProperty(MAX_CORES_PARAMETER);
		if (maxCoresStr == null)
			return;
		try {
			int maxCores = Integer.parseInt(maxCoresStr.trim());
			if (maxCores == 1)
				ParallelJobRunner.setLinear(true);
			else if (maxCores < Runtime.getRuntime().availableProcessors())
				ParallelJobRunner.setMaxCores(maxCores);
		}
		catch (NumberFormatException nfe) {}
	}
	
	/**
	 * Redirect log output written to the standard <code>System.out</code> and
	 * <code>System.err</code> streams to log files in a given folder. The log
	 * files are named <code>&lt;logFilePrefix&gt;.&lt;time&gt;.out.log</code>
	 * and <code>&lt;logFilePrefix&gt;.&lt;time&gt;.err.log</code>,
	 * respectively, with <code>&lt;time&gt;</code> being the time of the call
	 * to this method, given as (two digits each, only year in four digits)
	 * <code>&lt;year&gt;&lt;month&gt;&lt;day&gt;-&lt;hour&gt;&lt;minute&gt;</code>.
	 * If the argument folder does not exist, this method will create it. This
	 * method forks <code>System.err</code> so respective output keeps on going
	 * to the parent process, but does not fork <code>System.out</code>.
	 * @param logFolder the folder to store the log files in
	 * @param logFilePrefix the name prefix to use for the log files
	 * @throws IOException
	 */
	public static void setUpLogFiles(File logFolder, String logFilePrefix) throws IOException {
		setUpLogFiles(logFolder, logFilePrefix, false, true);
	}
	
	/**
	 * Redirect log output written to the standard <code>System.out</code> and
	 * <code>System.err</code> streams to log files in a given folder. The log
	 * files are named <code>&lt;logFilePrefix&gt;.&lt;time&gt;.out.log</code>
	 * and <code>&lt;logFilePrefix&gt;.&lt;time&gt;.err.log</code>,
	 * respectively, with <code>&lt;time&gt;</code> being the time of the call
	 * to this method, given as (two digits each, only year in four digits)
	 * <code>&lt;year&gt;&lt;month&gt;&lt;day&gt;-&lt;hour&gt;&lt;minute&gt;</code>.
	 * If the argument folder does not exist, this method will create it.
	 * @param logFolder the folder to store the log files in
	 * @param logFilePrefix the name prefix to use for the log files
	 * @param forkSystemOut continue writing output on <code>System.out</code>
	 *            to the original output stream (e.g. for going back to the
	 *            master process via an instance of this class)?
	 * @param forkSystemErr continue writing output on <code>System.err</code>
	 *            to the original output stream (e.g. for going back to the
	 *            master process via an instance of this class)?
	 * @throws IOException
	 */
	public static void setUpLogFiles(File logFolder, String logFilePrefix, boolean forkSystemOut, boolean forkSystemErr) throws IOException {
		String logFileBaseName = (logFilePrefix + "." + LOG_TIMESTAMP_FORMATTER.format(new Date()));
		
		//	get log path
		logFolder.mkdirs();
		
		//	redirect or fork System.out
		File logFileOut = new File(logFolder, (logFileBaseName + ".out.log"));
		OutputStream sysOut = System.out;
		if (forkSystemOut)
			System.setOut(new PrintStream(new ForkOutputStream(new BufferedOutputStream(new FileOutputStream(logFileOut)), sysOut), true, "UTF-8"));
		else System.setOut(new PrintStream(new BufferedOutputStream(new FileOutputStream(logFileOut)), true, "UTF-8"));
		
		//	redirect or fork System.err
		File logFileErr = new File(logFolder, (logFileBaseName + ".err.log"));
		OutputStream sysErr = System.err;
		if (forkSystemErr)
			System.setErr(new PrintStream(new ForkOutputStream(new BufferedOutputStream(new FileOutputStream(logFileErr)), sysErr), true, "UTF-8"));
		else System.setErr(new PrintStream(new BufferedOutputStream(new FileOutputStream(logFileErr)), true, "UTF-8"));
	}
	
	private static final DateFormat LOG_TIMESTAMP_FORMATTER = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US) {
		{this.setTimeZone(TimeZone.getTimeZone("UTC"));}
	};
	
	private static class ForkOutputStream extends FilterOutputStream {
		private OutputStream fork;
		ForkOutputStream(OutputStream out, OutputStream fork) {
			super(out);
			this.fork = fork;
		}
		public synchronized void write(int b) throws IOException {
			this.out.write(b);
			this.fork.write(b);
		}
//		public void write(byte[] b) throws IOException {
//			super.write(b); // no need to fork this call, as super class loops through to three-argument version
//		}
		public synchronized void write(byte[] b, int off, int len) throws IOException {
			this.out.write(b, off, len);
			this.fork.write(b, off, len);
		}
		public synchronized void flush() throws IOException {
			this.out.flush();
			this.fork.flush();
		}
		public void close() throws IOException {
			this.out.close();
			this.fork.close();
		}
	}
	
	/**
	 * Clean up log files older than 24 hours. This method obtains a list of
	 * the files in the argument folder that start with the argument file name
	 * prefix and end in <code>.log</code>, and deletes the ones whose last
	 * modification happened more than 24 hours ago.
	 * @param logFolder the folder in which to clean up the log files
	 * @param logFilePrefix the file name prefix of the log files to clean up
	 * @return the number of files that were deleted
	 */
	public static int cleanUpLogFiles(File logFolder, String logFilePrefix) {
		long cleanUpOlderThan = (System.currentTimeMillis() - (1000 * 60 * 60 * 24));
		return cleanUpLogFiles(logFolder, logFilePrefix, cleanUpOlderThan);
	}
	
	/**
	 * Clean up log files older than 24 hours. This method obtains a list of
	 * the files in the argument folder that start with the argument file name
	 * prefix and end in <code>.log</code>, and deletes the ones whose last
	 * modification happened before the argument threshold.
	 * @param logFolder the folder in which to clean up the log files
	 * @param logFilePrefix the file name prefix of the log files to clean up
	 * @param cleanUpOlderThan the threshold for the last modification date of
	 *            the log files to clean up
	 * @return the number of files that were deleted
	 */
	public static int cleanUpLogFiles(File logFolder, final String logFilePrefix, long cleanUpOlderThan) {
		File[] logFiles = logFolder.listFiles(new FileFilter() {
			public boolean accept(File file) {
				return (file.isFile() && file.getName().startsWith(logFilePrefix) && file.getName().endsWith(".log"));
			}
		});
		if ((logFiles == null) || (logFiles.length == 0))
			return 0; // folder doesn't exist or is empty
		int deletedLogFiles = 0;
		for (int l = 0; l < logFiles.length; l++) 
			if (logFiles[l].lastModified() < cleanUpOlderThan) try {
				if (logFiles[l].delete())
					deletedLogFiles++;
			}
			catch (Exception e) {
				System.out.println("Error deleting log file '" + logFiles[l].getAbsolutePath() + "': " + e.getMessage());
			}
		return deletedLogFiles;
	}
}
