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

/**
 * Constant bearer for GoldenGATE Network Monitoring Interface.
 * 
 * @author sautter
 */
public interface GoldenGateServerNetworkMonitoringConstants {
	
	/** the network action command for pinging the server */
	public static final String NETWORK_MONITOR_PING = "GGS_NMI_PING";
	
	/** the network action command for retrieving a list of the active components from the server */
	public static final String NETWORK_MONITOR_LIST_COMPONENTS = "GGS_NMI_LIST_COMPONENTS";
	
	/** the network action command for retrieving a list of the component loading errors from the server */
	public static final String NETWORK_MONITOR_LIST_ERRORS = "GGS_NMI_COMPONENT_ERRORS";
	
	/** the network action command for retrieving the current network service thread pool size from the server */
	public static final String NETWORK_MONITOR_POOL_SIZE = "GGS_NMI_POOL_SIZE";
	
	/** the network action command for retrieving a list of the currently active network actions from the server */
	public static final String NETWORK_MONITOR_LIST_ACTIONS = "GGS_NMI_LIST_ACTIONS";
	
	/** the network action command for retrieving a list of the currently active threads from the server */
	public static final String NETWORK_MONITOR_LIST_THREADS = "GGS_NMI_LIST_THREADS";
	
	/** the network action command for retrieving a list of the active currently active thread groups from the server */
	public static final String NETWORK_MONITOR_LIST_THREAD_GROUPS = "GGS_NMI_LIST_THREAD_GROUPS";
	
	/** the network action command for retrieving a list of the active background queues and their status from the server */
	public static final String NETWORK_MONITOR_LIST_QUEUES = "GGS_NMI_LIST_QUEUES";
}
