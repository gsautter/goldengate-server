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

import java.util.Iterator;
import java.util.Properties;

import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Data container for a single slave job, holding the name of the slave JAR to
 * execute, the maximum amount of memory to allow the slave JVM to use, as well
 * as slave job arguments holding the data, cache, and log paths, the maximum
 * number of CPU cores to use in parallel loop execution, and the 'verbose'
 * property. Sub classes are welcome to add further parameters.
 * 
 * @author sautter
 */
public class SlaveJob implements SlaveConstants {
	private static final String trueValue = "T_R_U_E";
	
	/** the identifier of the slave job, as a 32 character HEX string holding 128 bits */
	public final String slaveJobId;
	
	/** the name of the main slave JAR to execute */
	public final String slaveJarName;
	
	private int maxMemory = -1;
	private int maxCores = -1;
	private String dataPath;
	private String resultPath;
	private String logPath;
	private Properties properties = new Properties();
	
	/** Constructor
	 * @param slaveJobId the identifier of the slave job
	 * @param slaveJarName the name of the main slave JAR to execute
	 */
	public SlaveJob(String slaveJobId, String slaveJarName) {
		this.slaveJobId = slaveJobId;
		this.slaveJarName = slaveJarName;
	}
	
	/**
	 * Retrieve the maximum amount of memory to use for executing the slave job
	 * (in MB). This value becomes the <code>-Xmx</code> JVM parameter in the
	 * execution command array.
	 * @return the maximum amount of memory to use (in MB)
	 */
	public int getMaxMemory() {
		return this.maxMemory;
	}
	
	/**
	 * Set the maximum amount of memory to use for executing the slave job (in
	 * MB). This value becomes the <code>-Xmx</code> JVM parameter in the
	 * execution command array. Setting this property to a number less than 1
	 * results in the <code>-Xmx</code> JVM parameter being omitted in the
	 * execution command array.
	 * @param maxMemory the maximum amount of memory to use (in MB)
	 */
	public void setMaxMemory(int maxMemory) {
		this.maxMemory = maxMemory;
	}
	
	/**
	 * Retrieve the maximum number of CPU cores to use in parallel loop
	 * execution. This value becomes the <code>MAXCORES</code> JAR argument in
	 * the execution command array.
	 * @return the maximum number of CPU cores to use
	 */
	public int getMaxCores() {
		return this.maxCores;
	}
	
	/**
	 * Set the maximum number of CPU cores to use in parallel loop execution.
	 * This value becomes the <code>MAXCORES</code> JAR argument in the
	 * execution command array. Setting this property to a number less than 1
	 * results in the <code>MAXCORES</code> JAR argument being omitted in the
	 * execution command array.
	 * @param maxCores the maximum number of CPU cores to use
	 */
	public void setMaxCores(int maxCores) {
		this.maxCores = maxCores;
	}
	
	/**
	 * Retrieve the path of the data to be processed by the slave job. It is
	 * the responsibility of the slave job owner to provide the data at this
	 * location. If a slave job is executed on a remote machine, the execution
	 * infrastructure will mirror the data at the indicated location to the
	 * executing machine, and mirror any changes back to this locations. If
	 * the indicated location is a file, mirroring only handles this file; if
	 * it is a folder, mirroring handles the contents of the folder and any sub
	 * folders. If set, this value becomes the <code>DATA</code> JAR argument
	 * in the execution command array.
	 * @return the path of the data to be processed by the slave job
	 */
	public String getDataPath() {
		return this.dataPath;
	}
	
	/**
	 * Set the path of the data to be processed by the slave job. It is the
	 * responsibility of the slave job owner to provide the data at this
	 * location. If a slave job is executed on a remote machine, the execution
	 * infrastructure will mirror the data at the indicated location to the
	 * executing machine, and mirror any changes back to this locations. If
	 * the indicated location is a file, mirroring only handles this file; if
	 * it is a folder, mirroring handles the contents of the folder and any sub
	 * folders. If set, this value becomes the <code>DATA</code> JAR argument
	 * in the execution command array.
	 * @param dataPath the path of the data to be processed by the slave job
	 */
	public void setDataPath(String dataPath) {
		this.dataPath = dataPath;
	}
	
	/**
	 * Retrieve the path for the slave job to store processing results in. It
	 * is the responsibility of the slave job owner to further handle data at
	 * this location after processing finishes. If a slave job is executed on a
	 * remote machine, the execution infrastructure will mirror the data from
	 * the executing machine to the indicated location after the slave job
	 * terminates. If the indicated location is a file, mirroring only handles
	 * this file; if it is a folder, mirroring handles the contents of the
	 * folder and any sub folders. If set, this value becomes the
	 * <code>RESULT</code> JAR argument in the execution command array.
	 * @return the path to store processing results in
	 */
	public String getResultPath() {
		return this.resultPath;
	}
	
	/**
	 * Set the path for the slave job to store processing results in. It is the
	 * responsibility of the slave job owner to further handle data at this
	 * location after processing finishes. If a slave job is executed on a
	 * remote machine, the execution infrastructure will mirror the data from
	 * the executing machine to the indicated location after the slave job
	 * terminates. If the indicated location is a file, mirroring only handles
	 * this file; if it is a folder, mirroring handles the contents of the
	 * folder and any sub folders. If set, this value becomes the
	 * <code>RESULT</code> JAR argument in the execution command array.
	 * @param resultPath the path to store processing results in
	 */
	public void setResultPath(String resultPath) {
		this.resultPath = resultPath;
	}
	
	/**
	 * Retrieve the path for the slave job to write log output to. If a slave
	 * job is executed on a remote machine, the execution infrastructure will
	 * mirror the log files from the executing machine to the indicated
	 * location after the slave job terminates. If set, this value becomes the
	 * <code>LOG</code> JAR argument in the execution command array.
	 * @return the path to store log files in
	 */
	public String getLogPath() {
		return this.logPath;
	}
	
	/**
	 * Set the path for the slave job to write log output to. If a slave job is
	 * executed on a remote machine, the execution infrastructure will mirror
	 * the log files from the executing machine to the indicated location after
	 * the slave job terminates.If set, this value becomes the <code>LOG</code>
	 * JAR argument in the execution command array.
	 * @param logPath the path to store log files in
	 */
	public void setLogPath(String logPath) {
		this.logPath = logPath;
	}
	
	/**
	 * Retrieve a custom slave job property.
	 * @param key the name of the property
	 * @return the value of the property
	 */
	public String getProperty(String key) {
		return this.properties.getProperty(key);
	}
	
	/**
	 * Set a custom boolean slave job property.
	 * @param key the name of the property
	 * @return the previous value of the property (if any)
	 */
	public String setProperty(String key) {
		return this.setProperty(key, trueValue);
	}
	
	/**
	 * Set a custom slave job property. Setting a property to <code>null</code>
	 * removes it.
	 * @param key the name of the property
	 * @param value the value of the property
	 * @return the previous value of the property (if any)
	 */
	public String setProperty(String key, String value) {
		String oldValue = this.properties.getProperty(key);
		if (value == null)
			this.properties.remove(key);
		else if ((key.indexOf(" ") != -1) || (key.indexOf("\t") != -1))
			throw new IllegalArgumentException("Invalid property key '" + key + "' (illegal whitespace character)");
		else if (key.indexOf("=") != -1)
			throw new IllegalArgumentException("Invalid property key '" + key + "' (illegal equals character)");
		else this.properties.setProperty(key, value);
		return oldValue;
	}
	
	/**
	 * Create the command array to hand to <code>Runtime.exec()</code> to start
	 * execution of the slave job. The cache path is an argument to this method
	 * rather than a property because it is to be set by the execution engine.
	 * @param cachePath the folder to use for caching.
	 * @return the command array
	 */
	public String[] getCommand(String cachePath) {
		
		//	assemble basic command
		StringVector command = new StringVector();
		command.addElement("java");
		command.addElement("-jar");
		if (this.maxMemory > 0)
			command.addElement("-Xmx" + this.maxMemory + "m");
		if (this.maxCores > 0)
		command.addElement("-XX:ActiveProcessorCount=" + this.maxCores); // see https://stackoverflow.com/questions/33723373/can-i-set-the-number-of-threads-cpus-available-to-the-java-vm
		command.addElement(this.slaveJarName);
		
		//	add parameters general
		if (this.dataPath != null)
			command.addElement(createArgument(DATA_PATH_PARAMETER, this.dataPath)); // data path
		if (this.resultPath != null)
			command.addElement(createArgument(RESULT_PATH_PARAMETER, this.resultPath)); // result path
		if (this.logPath != null)
			command.addElement(createArgument(LOG_PATH_PARAMETER, this.logPath)); // log path
		if (cachePath != null)
			command.addElement(createArgument(CACHE_PATH_PARAMETER, cachePath)); // cache path
		if (this.properties.containsKey(VERBOSE_PARAMETER))
			command.addElement(VERBOSE_PARAMETER); // loop through all output (good for debugging)
		if (this.maxCores > 0)
			command.addElement(MAX_CORES_PARAMETER + "=" + this.maxCores); // maximum number of cores
		
		//	add custom parameters
		for (Iterator kit = this.properties.keySet().iterator(); kit.hasNext();) {
			String key = ((String) kit.next());
			if (DATA_PATH_PARAMETER.equals(key))
				continue;
			if (RESULT_PATH_PARAMETER.equals(key))
				continue;
			if (LOG_PATH_PARAMETER.equals(key))
				continue;
			if (CACHE_PATH_PARAMETER.equals(key))
				continue;
			if (VERBOSE_PARAMETER.equals(key))
				continue;
			if (MAX_CORES_PARAMETER.equals(key))
				continue;
			String value = this.properties.getProperty(key);
			command.addElement(createArgument(key, value));
		}
		
		//	finally ...
		return command.toStringArray();
	}
	
	private static String createArgument(String name, String value) {
		if (trueValue.equals(value))
			return name;
		else if ((value.indexOf(" ") != -1) || (value.indexOf("\t") != -1))
			return ("\"" + name + "=" + value + "\"");
		else return (name + "=" + value);
	}
}
