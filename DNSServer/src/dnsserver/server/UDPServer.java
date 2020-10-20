package dnsserver.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.RejectedExecutionException;

import dnsserver.main.Main;
import dnsserver.server.connection.UDPConnection;

public class UDPServer implements Server, Runnable{
	
	private final InetAddress addr;
	private final int port;
	
	private DatagramSocket socket;
	private Thread thread;
	
	public UDPServer() throws IOException {
		this(null, 53);
	}
	
	public UDPServer(InetAddress addr) throws IOException {
		this(addr, 53);
	}
	
	public UDPServer(InetAddress addr,int port) throws IOException {
		this.addr = addr;
		this.port = port;
		start();
	}
	
	public String getAddressAndPort() {
		if(this.addr == null)
			return "0.0.0.0:"+this.port;
		return this.addr.getHostAddress()+":"+this.port;
	}

	@Override
	public void run() {
		Main.log("Starting UDP socket monitor on address " + getAddressAndPort());
	    while (Main.isRunning()) {
	      DatagramPacket indp = null;
	      try {
	        byte[] in = new byte[512];
	        indp = new DatagramPacket(in, in.length);
	        indp.setLength(in.length);
	        this.socket.receive(indp);
	        
	        Main.debug("UDP connection from " + indp.getSocketAddress());
	        if (Main.isRunning())
	          Main.getUdpThreadPool().execute(new UDPConnection(this.socket, indp)); 
	      } catch (RejectedExecutionException e) {
	        if (Main.isRunning()) {
	          Main.debug("UDP thread pool exausted, rejecting connection from " + indp.getSocketAddress());
	        } 
	      } catch (SocketException e) {
	    	  Main.warn("SocketException thrown from UDP socket on address " + getAddressAndPort() + ", " + e);
	      } catch (IOException e) {
	        Main.warn("IOException thrown by UDP socket on address " + getAddressAndPort() + ", " + e);
	      } catch (Throwable t) {
	        Main.warn("Throwable thrown by UDO socket on address " + getAddressAndPort(), t);
	      } 
	    } 
	    Main.log("UDP socket monitor on address " + getAddressAndPort() + " shutdown");
	}

	@Override
	public void shutdown() {
		if(this.socket!=null) {
			this.socket.close();
			this.socket = null;
			this.thread = null;
		}
	}

	@Override
	public void start() throws IOException {
		if(this.socket == null && this.thread == null) {
			if(addr == null)
				this.socket = new DatagramSocket(port);
			else
				this.socket = new DatagramSocket(port, addr);
			
			this.thread = new Thread(this);
			this.thread.start();
		}
	}

}
