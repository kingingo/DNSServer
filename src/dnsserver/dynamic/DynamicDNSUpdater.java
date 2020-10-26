package dnsserver.dynamic;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.RejectedExecutionException;

import dnsserver.main.Main;
import dnsserver.server.connection.UDPConnection;

public class DynamicDNSUpdater implements Runnable{
	
	private final InetAddress addr;
	private final int port;
	
	private DatagramSocket socket;
	private Thread thread;
	
	public DynamicDNSUpdater() throws IOException {
		this(null, 4444);
	}
	
	public DynamicDNSUpdater(InetAddress addr) throws IOException {
		this(addr, 4444);
	}
	
	public DynamicDNSUpdater(InetAddress addr,int port) throws IOException {
		this.addr = addr;
		this.port = port;
		start();
	}
	
	public String getAddressAndPort() {
		if(this.addr == null)
			return "0.0.0.0:"+this.port;
		return this.addr.getHostAddress()+":"+this.port;
	}

	public void log(String msg){
		Main.log("DynamicDNSUpdater | " + msg);
	}
	
	public void warn(String msg){
		warn(msg,null);
	}
	
	public void warn(String msg, Throwable exception){
		Main.warn("DynamicDNSUpdater | " + msg, exception);
	}	
	
	public void debug(String msg){
		Main.debug("DynamicDNSUpdater | " + msg);
	}
	
	@Override
	public void run() {
		log("Starting on address " + getAddressAndPort());
	    while (Main.isRunning()) {
	      DatagramPacket indp = null;
	      try {
	        byte[] in = new byte[512];
	        indp = new DatagramPacket(in, in.length);
	        indp.setLength(in.length);
	        this.socket.receive(indp);
	        debug("UDP connection from " + indp.getAddress().toString());
	        DataInputStream input = new DataInputStream(new ByteArrayInputStream(indp.getData()));
	        
	        String user = input.readUTF();
	        String password = input.readUTF();
	        String hostname = input.readUTF();
	        String ipv4 = input.readUTF();
	        String ipv6 = input.readUTF();
	        
	        debug("User: "+user);
	        debug("Password: "+password);
	        debug("Hostname: "+hostname);
	        debug("IPv4: "+ipv4);
	        debug("IPv6: "+ipv6);
	        if(!ipv6.isEmpty() && !ipv6.isBlank()) {
	        	InetAddress adr = InetAddress.getByName(ipv6);
	        	Main.getIpWhiteList().add(adr.toString());
	        	debug("Add IPv6 to whitelist!");
	        }
	        if(!ipv4.isEmpty() && !ipv4.isBlank()) {
	        	InetAddress adr = InetAddress.getByName(ipv4);
	        	Main.getIpWhiteList().add(adr.toString());
	        	debug("Add IPv4 to whitelist!");
	        }
	        
	      }catch (SocketException e) {
	    	  warn("SocketException thrown from UDP socket on address " + getAddressAndPort() + ", " + e);
	      } catch (IOException e) {
	        warn("IOException thrown by UDP socket on address " + getAddressAndPort() + ", " + e);
	      } catch (Throwable t) {
	        warn("Throwable thrown by UDO socket on address " + getAddressAndPort(), t);
	      } 
	    } 
	    log("DynDNS Updater monitor on address " + getAddressAndPort() + " shutdown");
	}

	public void shutdown() {
		if(this.socket!=null) {
			this.socket.close();
			this.socket = null;
			this.thread = null;
		}
	}

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
