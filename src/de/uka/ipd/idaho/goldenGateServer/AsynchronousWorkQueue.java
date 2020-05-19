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

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerComponent.ComponentActionConsole;


/**
 * An asynchronous work queue is an abstract monitoring device and static
 * registry for any background working queues in a GoldenGATE Server.
 * 
 * @author sautter
 */
public abstract class AsynchronousWorkQueue {
	private static Map instancesByName = Collections.synchronizedMap(new TreeMap(String.CASE_INSENSITIVE_ORDER));
	static void listInstances(String prefix, ComponentActionConsole cac) {
		for (Iterator enit = instancesByName.keySet().iterator(); enit.hasNext();) {
			String awqName = ((String) enit.next());
			AsynchronousWorkQueue awq = ((AsynchronousWorkQueue) instancesByName.get(awqName));
			cac.reportResult(prefix + awq.getStatus());
		}
	}
	
	/** the name of the work queue */
	public final String name;
	
	/** Constructor
	 * @param name the name of the work queue
	 */
	protected AsynchronousWorkQueue(String name) {
		this.name = name;
		instancesByName.put(this.name, this);
	}
	
	/**
	 * Dispose of the asynchronous work queue instance.
	 */
	public void dispose() {
		instancesByName.remove(this.name);
	}
	
	/**
	 * Retrieve the status of the work queue, for monitoring purposes.
	 * @return the status of the work queue
	 */
	public abstract String getStatus();
	
	/* TODO Somehow cap off waiting time after jobs in AEP and ADAH:
- no use sleeping half an hour after one large job !!!
- maybe make sleeping time configurable:
  - per-instance constant
  - fraction factor of job runtime
  - absolute minimum
  - absolute maximum
- configure in AEP console ...
- ... or even more centrally in to-create work queue manager component (WQM)
- allow hot modification via server console
- maybe make defaults dependent on
  - number of work queues (more ==> sleep more)
  - number of CPU cores (more ==> sleep less)
	 */
}
