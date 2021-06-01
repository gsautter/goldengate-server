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

/**
 * Constant bearer for slave process parameter names, most normally via the
 * arguments to the <code>main()</code> method of the slave process main JAR.
 * 
 * @author sautter
 */
public interface SlaveConstants {
	
	/** the name of the parameter for handing over the path of the data to process, namely 'DATA' */
	public static final String DATA_PATH_PARAMETER = "DATA";
	
	/** the name of the parameter for handing over the path to store processing results in, namely 'RESULT' */
	public static final String RESULT_PATH_PARAMETER = "RESULT";
	
	/** the name of the parameter for handing over the cache folder path, namely 'CACHE' */
	public static final String CACHE_PATH_PARAMETER = "CACHE";
	
	/** the name of the parameter for handing over the log folder path, namely 'LOG' */
	public static final String LOG_PATH_PARAMETER = "LOG";
	
	/** the name of the parameter indicating verbose execution (most normally indicating to loop <code>System.out</code> through to the master process), namely 'VERBOSE' */
	public static final String VERBOSE_PARAMETER = "VERBOSE";
	
	/** the name of the parameter for handing over the maximum number of CPU cores to use in parallel loop execution, namely 'MAXCORES' */
	public static final String MAX_CORES_PARAMETER = "MAXCORES";
}
