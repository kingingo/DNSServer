package dnsserver.server.connection;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.xbill.DNS.Message;

import dnsserver.main.Main;
import dnsserver.main.Utils;

public class TCPConnection implements Runnable{

	private Socket socket;
	
	public TCPConnection(Socket socket) {
		this.socket = socket;
	}
	
	public void run() {
		try {
			DataInputStream dataIn = new DataInputStream(this.socket.getInputStream());
			
			int length = dataIn.readUnsignedShort();
			byte[] data = new byte[length];
			dataIn.readFully(data);
			
			byte[] response = null;

		    Message query = new Message(data);
		    Main.log("TCP query " + Utils.toString(query.getQuestion()) + " from " + this.socket.getRemoteSocketAddress());
		    response = Main.generateReply(query, data, length, socket, socket.getRemoteSocketAddress());
			if(response == null) return;
			
			DataOutputStream dataOut = new DataOutputStream(this.socket.getOutputStream());
			dataOut.writeShort(response.length);
			dataOut.write(response);
			dataOut.flush();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			Utils.closeSocket(this.socket);
		}
	}
}
