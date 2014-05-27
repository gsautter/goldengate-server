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
package de.uka.ipd.idaho.goldenGateServer.client;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import de.uka.ipd.idaho.goldenGateServer.GoldenGateServerConstants;
import de.uka.ipd.idaho.goldenGateServer.util.BufferedLineInputStream;
import de.uka.ipd.idaho.goldenGateServer.util.BufferedLineOutputStream;

/**
 * Factory for generic connections to the backing server, producing Connection
 * objects for Sockets and URLs.
 * 
 * @author sautter
 */
public abstract class ServerConnection implements GoldenGateServerConstants {
	
	/**
	 * Generic representation of a connection to the backend server, consisting of a
	 * writer for sending a request and a reader for receiving the response. Note
	 * that using the writer after it was flushed is not a good idea in the general
	 * case, since it may cause problems with URL based connections.
	 * 
	 * @author sautter
	 */
	public abstract static class Connection {
//		private BufferedOutputStream bos;
		private BufferedLineOutputStream blos;
		private BufferedWriter bw;
		
//		private BufferedInputStream bis;
		private BufferedLineInputStream blis;
		private BufferedReader br;
		
		/**
		 * Produce an InputStream for the underlying connection
		 * @return an InputStream for the underlying connection
		 */
		protected abstract InputStream produceInputStream() throws IOException;
//		
//		/**
//		 * Retrieve an InputStream for reading data from this connection.
//		 * If this connection is based on a URL, this method should not be
//		 * invoked before all output is written. Using both the Reader
//		 * returned by getReader() and the InputStream returned by this method
//		 * may cause errors.
//		 * @return an InputStream for reading data from this connection
//		 */
//		public InputStream getInputStream() throws IOException {
//			if (this.bis == null)
//				this.bis = new BufferedInputStream(this.produceInputStream());
//			return this.bis;
//		}
		
		/**
		 * Retrieve an InputStream for reading data from this connection.
		 * If this connection is based on a URL, this method should not be
		 * invoked before all output is written. Using both the Reader
		 * returned by getReader() and the InputStream returned by this method
		 * may cause errors.
		 * @return an InputStream for reading data from this connection
		 */
		public BufferedLineInputStream getInputStream() throws IOException {
			if (this.blis == null)
				this.blis = new BufferedLineInputStream(this.produceInputStream(), ENCODING);
			return this.blis;
		}
		
		/**
		 * Retrieve a Reader for reading data from this connection. This is a
		 * shorthand for invoking
		 * <code>new BufferedReader(new InputStreamReader(getInputStream(), ENCODING))</code>.
		 * If this connection is based on a URL, this method should not be
		 * invoked before all output is written. Using both the InputStream
		 * returned by getInputStream() and the Reader returned by this method
		 * may cause errors.
		 * @return a Reader for reading data from this connection
		 */
		public BufferedReader getReader() throws IOException {
			if (this.br == null)
				this.br = new BufferedReader(new InputStreamReader(this.getInputStream(), ENCODING));
			return this.br;
		}
		
		/**
		 * Produce an OutputStream for the underlying connection
		 * @return an OutputStream for the underlying connection
		 */
		protected abstract OutputStream produceOutputStream() throws IOException;
//		
//		/**
//		 * Retrieve an OutputStream for writing data to this connection. If this
//		 * connection is based on a URL, writing data to the OutputStream
//		 * returned by this method might not work any more after
//		 * getInputStream() or getReader() has been invoked. Using both the
//		 * Writer returned by getWriter() and the OutputStream returned by this
//		 * method may cause errors.
//		 * @return an OutputStream for writing data to this connection
//		 */
//		public OutputStream getOutputStream() throws IOException {
//			if (this.bos == null)
//				this.bos = new BufferedOutputStream(this.produceOutputStream());
//			return this.bos;
//		}
		
		/**
		 * Retrieve an OutputStream for writing data to this connection. If this
		 * connection is based on a URL, writing data to the OutputStream
		 * returned by this method might not work any more after
		 * getInputStream() or getReader() has been invoked. Using both the
		 * Writer returned by getWriter() and the OutputStream returned by this
		 * method may cause errors.
		 * @return an OutputStream for writing data to this connection
		 */
		public BufferedLineOutputStream getOutputStream() throws IOException {
			if (this.blos == null)
				this.blos = new BufferedLineOutputStream(this.produceOutputStream(), ENCODING);
			return this.blos;
		}
		
		/**
		 * Retrieve a Writer for writing data from this connection. This is a
		 * shorthand for invoking
		 * <code>new BufferedWriter(new OutputStreamWriter(this.getOutputStream(), ENCODING))</code>.
		 * If this connection is based on a URL, writing data to the Writer
		 * returned by this method might not work any more after
		 * getInputStream() or getReader() has been invoked. Using both the
		 * OutputStream returned by getOutputStream() and the Writer returned by
		 * this method may cause errors.
		 * @return a Writer for writing data to this connection
		 */
		public BufferedWriter getWriter() throws IOException {
			if (this.bw == null)
				this.bw = new BufferedWriter(new OutputStreamWriter(this.getOutputStream(), ENCODING));
			return this.bw;
		}
		
		/**
		 * Close the reader and writer
		 * @throws IOException
		 */
		public void close() throws IOException {
			if (this.bw != null)
				this.bw.close();
//			else if (this.bos != null)
//				this.bos.close();
			else if (this.blos != null)
				this.blos.close();
			
			if (this.br != null)
				this.br.close();
//			else if (this.bis != null)
//				this.bis.close();
			else if (this.blis != null)
				this.blis.close();
		}
	}
	
	/**
	 * @return true if the argument object is a server connection and connects
	 *         to the same remote address as this server connection.
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		return ((obj != null) && (obj instanceof ServerConnection) && this.toString().equals(obj.toString()));
	}
	
	/**
	 * @return a hash code for the server connection, using the result of
	 *         toString() as the basis for the hast. This is in order to have
	 *         connections for the same remote address return the same hash
	 *         code, which facilitates pooling server connections.
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return this.toString().hashCode();
	}
	
	/**
	 * @return a string representation of this server connection, which should
	 *         uniqely identify where this connection goes to. A URL based
	 *         server connection, for instance might return the URL string.
	 * @see java.lang.Object#toString()
	 */
	public abstract String toString();
	
	private static final boolean DEBUG = false;
	
	private static LinkedList connectionRequestQueue = new LinkedList();
	private static ConnectorService connectorService = null;
	
	private static class ConnectorService extends Thread {
		private boolean keepRunning = true;
		public void run() {
			while (this.keepRunning) {
				ConnectionRequest cr = null;
				synchronized(connectionRequestQueue) {
					if (connectionRequestQueue.isEmpty()) try {
						connectionRequestQueue.wait();
						if (DEBUG) System.out.println("  connector service woken up");
					} catch (InterruptedException ie) {}
					else cr = ((ConnectionRequest) connectionRequestQueue.removeFirst());
				}
				if (cr != null) {
					if (DEBUG) System.out.println("  got connection request");
					try {
						Connection con = cr.sCon.produceConnection();
						if (DEBUG) System.out.println("  got connection");
						cr.setConnection(con, null);
						if (DEBUG) System.out.println("  connection passed to request");
					}
					catch (IOException ioe) {
						ioe.printStackTrace(System.out);
						cr.setConnection(null, ioe);
					}
				}
			}
		}
		void shutdown() {
			this.keepRunning = false;
			synchronized(connectionRequestQueue) {
				connectionRequestQueue.notify();
			}
		}
	}
	
	private static class ConnectionRequest {
		ServerConnection sCon;
		private Object lock = new Object();
		private Connection con;
		private IOException ioe;
		ConnectionRequest(ServerConnection sCon) {
			this.sCon = sCon;
		}
		Connection getConnection() throws IOException {
			synchronized(connectionRequestQueue) {
				connectionRequestQueue.addLast(this);
				if (DEBUG) System.out.println("  request enqueued");
				connectionRequestQueue.notify();
				if (DEBUG) System.out.println("  connector service notified");
			}
			synchronized(this.lock) {
				if ((this.con == null) && (this.ioe == null)) {
					try {
						if (DEBUG) System.out.println("  waiting");
						this.lock.wait();
					} catch (InterruptedException ie) {}
				}
				else if (DEBUG) System.out.println("  good service is fast :-)");
			}
			if (DEBUG) System.out.println("  requester woken up");
			if (this.ioe == null) {
				if (DEBUG) System.out.println("  returning connection");
				return this.con;
			}
			else {
				if (DEBUG) System.out.println("  throwing exception " + this.ioe.getMessage());
				throw this.ioe;
			}
		}
		void setConnection(Connection con, IOException ioe) {
			synchronized(this.lock) {
				this.con = con;
				this.ioe = ioe;
				if (DEBUG) System.out.println("  connection stored in request");
				this.lock.notify();
			}
		}
	}
	
	/**
	 * Check if ServerConnection is producing Connections in a dedicated service
	 * thread, or handing them out directly.
	 * @return Returns the threadLocal.
	 */
	public static boolean isThreadLocal() {
		return (connectorService != null);
	}
	
	/**
	 * Specify whether or not ServerConnection instances should produce
	 * Connections in a dedicated thread. In the presence of an eager
	 * SecurityManager, for instance, in an applet, this is necessary for
	 * offering Connections to instances of classes that where loaded through
	 * subordinate class loaders instead of the system class loader itself. Even
	 * if such plugin instances originate from certified code, the
	 * SecurityManager won't allow them to connect to the backing server.
	 * Setting the threadLocal property will cause ServerConnection instances to
	 * produce Connections in a dedicated service thread, thus in code loaded
	 * entirely through the system class loader, and then hand out the
	 * Connections to the requesting threads. This circumvents the security
	 * restrictions. This property should be set as early as possible.
	 * @param threadLocal use a dedicated thread for opening connections?
	 */
	public static void setThreadLocal(boolean threadLocal) {
		if (threadLocal) {
			
			//	don't start twice
			if (connectorService == null) {
				connectorService = new ConnectorService();
				connectorService.start();
			}
		}
		else {
			
			//	check if something to shut down
			if (connectorService != null) {
				connectorService.shutdown();
				connectorService = null;
			}
		}
	}

	/**
	 * @return a Connection for one interaction with the backing server. You
	 *         should close the connection when you are done using it.
	 */
	public Connection getConnection() throws IOException {
		if (connectorService == null)
			return this.produceConnection();
		
		else {
			if (DEBUG) System.out.println("SCon: producing connection asynchronously ...");
			ConnectionRequest cr = new ConnectionRequest(this);
			if (DEBUG) System.out.println("  request created");
			return cr.getConnection();
		}
	}
	
	/**
	 * Produce a connection. The deviation from getConnection() is necessary due
	 * to the connector service thread.
	 * @return a Connection for one interaction with the backing server
	 * @throws IOException
	 */
	protected abstract Connection produceConnection() throws IOException;
	
	/**
	 * Test whether or not the Connections returned by this ServerConnections
	 * are plain socket connections, or something else, eg tunneled through
	 * HTTP. This gives a hint towards wherther or not the connections can time
	 * out somewhere between client and server.
	 * @return true if the Connections returned by this ServerConnections are
	 *         plain socket connections, false otherwise
	 */
	public abstract boolean isDirectSocket();
	
	//	connection pool to make server connections singletons for each remote address
	private static Map serverConnectionPool = Collections.synchronizedMap(new HashMap());
	
	/** obtain a ServerConnection for communication over Sockets with some host on some port 
	 * @param	host	the host to communicate with
	 * @param	port	the port to use for communication
	 * @return a ServerConnection for communication over Sockets with the specified host on the specified port 
	 */
	public static ServerConnection getServerConnection(final String host, final int port) {
		ServerConnection con = ((ServerConnection) serverConnectionPool.get(host + ":" + port));
		if (con == null) {
			con = new ServerConnection() {
				protected Connection produceConnection() throws IOException {
					System.out.println("ServerConnection: connecting to " + host + " on port " + port);
					final Socket sock = new Socket(host, port);
					return new Connection() {
						protected InputStream produceInputStream() throws IOException {
							return sock.getInputStream();
						}
						protected OutputStream produceOutputStream() throws IOException {
							return sock.getOutputStream();
						}
					};
				}
				public String toString() {
					return (host + ":" + port);
				}
				public boolean isDirectSocket() {
					return true;
				}
			};
			serverConnectionPool.put(con.toString(), con);
		}
		return con;
	}
	
	/** obtain a ServerConnection for communication over http with some server identified by a URL
	 * @param	url		the URL to communicate with (as a String)
	 * @return a ServerConnection for communication over http with the server identified by the specified URL
	 */
	public static ServerConnection getServerConnection(final String url) {
		ServerConnection con = ((ServerConnection) serverConnectionPool.get(url));
		if (con == null) {
			con = new ServerConnection() {
				protected Connection produceConnection() throws IOException {
					System.out.println("ServerConnection: connecting to " + url);
					final HttpURLConnection con = ((HttpURLConnection) (new URL(url)).openConnection());
					con.setDoOutput(true);
					con.setDoInput(true);
					con.setUseCaches(false);
					con.setRequestMethod("POST");
					con.connect();
					return new Connection() {
						protected InputStream produceInputStream() throws IOException {
							return con.getInputStream();
						}
						protected OutputStream produceOutputStream() throws IOException {
							return con.getOutputStream();
						}
					};
				}
				public String toString() {
					return (url);
				}
				public boolean isDirectSocket() {
					return false;
				}
			};
			serverConnectionPool.put(con.toString(), con);
		}
		return con;
	}
	
	/** obtain a ServerConnection for communication over http with some server identified by a URL
	 * @param	url		the URL to communicate with
	 * @return a ServerConnection for communication over http with the server identified by the specified URL
	 */
	public static ServerConnection getServerConnection(URL url) {
		return getServerConnection(url.toString()); // have to use Strings so there can be a new URL object for every new connection
	}
}
