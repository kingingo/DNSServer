package dnsserver.server.connection;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import org.xbill.DNS.Message;

import dnsserver.main.Main;
import dnsserver.main.Utils;

public class UDPConnection implements Runnable{

	private DatagramPacket packet;
	private DatagramSocket socket;
	
	public UDPConnection(DatagramSocket socket, DatagramPacket packet) {
		this.packet = packet;
		this.socket = socket;
	}
	
	public void run() {
		try {
		      byte[] response = (byte[])null;

			  Message query = new Message(this.packet.getData());
			  Main.log("UDP query " + Utils.toString(query.getQuestion()) + " from " + this.packet.getSocketAddress());
			  response = Main.generateReply(query, packet.getData(), packet.getLength(), null, this.packet.getSocketAddress());
		      if (response == null) return; 
		      
		      DatagramPacket outdp = new DatagramPacket(response, response.length, this.packet.getAddress(), this.packet.getPort());
		      outdp.setData(response);
		      outdp.setLength(response.length);
		      outdp.setAddress(this.packet.getAddress());
		      outdp.setPort(this.packet.getPort());
		      try {
		        this.socket.send(outdp);
		      } catch (IOException e) {
		        Main.warn("Error sending UDP response to " + this.packet.getAddress() + ", " + e);
		      } 
		    } catch (Throwable e) {
		    	Main.warn("Error processing UDP connection from " + this.packet.getSocketAddress() + ", " + e, e);
		    } 
	}
}

