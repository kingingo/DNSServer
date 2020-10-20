package dnsserver.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import dnsserver.main.Main;
import dnsserver.server.connection.TCPConnection;

public class TCPServer implements Server, Runnable{

	private final InetAddress addr;
	private final int port;
	
	private ServerSocket socket;
	private Thread thread;
	
	public TCPServer() throws IOException {
		this(null, 53);
	}
	
	public TCPServer(InetAddress addr) throws IOException {
		this(addr, 53);
	}
	
	public TCPServer(InetAddress addr,int port) throws IOException {
		this.addr = addr;
		this.port = port;
		start();
	}
	
	public void start() throws IOException {
		if(this.thread == null && this.socket == null) {
			if(this.addr == null)
				this.socket = new ServerSocket(port,128);
			else
				this.socket = new ServerSocket(port, 128, addr);
			this.thread = new Thread(this);
			this.thread.start();
		}
	}
	
	public String getAddressAndPort() {
		if(this.addr == null)
			return "0.0.0.0:"+this.port;
		return this.addr.getHostAddress()+":"+this.port;
	}
	
	public void shutdown() {
		if(this.socket!=null) {
			try {
				this.socket.close();
			} catch (IOException e) {}finally {
				this.socket = null;
				this.thread = null;
			}
		}
	}

	public void run() {
		Main.log("Starting TCP Socket monitor on address " + getAddressAndPort());
		while(Main.isRunning()) {
			if(this.socket == null)break;
			try {
				Main.log("TCP Server waiting for connections...");
				Socket connection = this.socket.accept();
				Main.getTcpThreadPool().execute(new TCPConnection(connection));
			}catch (SocketException e) {
				Main.log("SocketException thrown from TCP Server");
			}catch(NullPointerException e) {
				Main.warn("NullPointerException thrown from TCP Server",e);
			}catch (IOException e) {
				Main.warn("IOException thrown from TCP Server",e);
			}
		}
		Main.log("TCP socket monitor on address " + this.socket.getInetAddress() + " shutdown");
	}

}
