package dnsserver.main;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import org.xbill.DNS.DClass;
import org.xbill.DNS.Header;
import org.xbill.DNS.Message;
import org.xbill.DNS.OPTRecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.TSIG;
import org.xbill.DNS.TSIGRecord;
import org.xbill.DNS.Type;

public class Utils {

	public static Message getInternalResponse(Message query, byte[] in, int length, Socket socket, OPTRecord queryOPT) {
		int defaultResponse = 3;
	    int flags = 0;
	    Header header = query.getHeader();
	    if (header.getFlag(0))
	      return null; 
	    if (header.getRcode() != 0)
	      return errorMessage(query, 1); 
	    if (header.getOpcode() != 0)
	      return errorMessage(query, 4); 
	    TSIGRecord queryTSIG = query.getTSIG();
	    TSIG tsig = null;
	    if (queryTSIG != null) {
	      tsig = Main.TSIGs.get(queryTSIG.getName());
	      if (tsig == null || tsig.verify(query, in, length, null) != 0)
	        return formerrMessage(in); 
	    } 
	    if (queryOPT != null && (queryOPT.getFlags() & 0x8000) != 0)
	      flags = 1; 
	    Message response = new Message(query.getHeader().getID());
	    response.getHeader().setFlag(0);
	    if (query.getHeader().getFlag(7))
	      response.getHeader().setFlag(7); 
	    response.getHeader().setRcode(defaultResponse);
	    Record queryRecord = query.getQuestion();
	    response.addRecord(queryRecord, 0);
	    int type = queryRecord.getType();
	    if (type == 252 && socket != null)
	      return errorMessage(query, 5); 
	    if (!Type.isRR(type) && type != 255)
	      return errorMessage(query, 4); 
	    if (queryOPT != null) {
	      int optflags = (flags == 1) ? 32768 : 0;
	      OPTRecord opt = new OPTRecord(4096, defaultResponse, 0, optflags);
	      response.addRecord((Record)opt, 3);
	    } 
	    response.setTSIG(tsig, defaultResponse, queryTSIG);
	    return response;
	  }

	public static void showData(byte[] data) {
		System.out.println();
		String str = "";
		for (int i = 0; i < data.length; i++) {
			System.out.print(str += data[i]);
			if (str.length() > 180) {
				str = "";
				System.out.println();
			}
		}
		System.out.println();
	}

	public static Message buildErrorMessage(Header header, int rcode, Record question) {
		Message response = new Message();
		response.setHeader(header);
		for (int i = 0; i < 4; i++)
			response.removeAllRecords(i);
		if (rcode == 2)
			response.addRecord(question, 0);
		header.setRcode(rcode);
		return response;
	}

	public static Message formerrMessage(byte[] in) {
		Header header;
		try {
			header = new Header(in);
		} catch (IOException e) {
			return null;
		}
		return buildErrorMessage(header, 1, null);
	}

	public static Message errorMessage(Message query, int rcode) {
		return buildErrorMessage(query.getHeader(), rcode, query.getQuestion());
	}

	public static String toString(Record record) {
		if (record == null)
			return null;
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(record.getName()); // fcmconnection.googleapis.com.
		stringBuilder.append(" ");
		stringBuilder.append(record.getTTL()); // 0
		stringBuilder.append(" ");
		stringBuilder.append(DClass.string(record.getDClass())); // IN
		stringBuilder.append(" ");
		stringBuilder.append(Type.string(record.getType())); // A
		String rdata = record.rdataToString();
		if (!rdata.equals("")) {
			stringBuilder.append(" ");
			stringBuilder.append(rdata);
		}
		return stringBuilder.toString();
	}

	public static boolean isValidInetAddress(String address) {
		try {
			InetAddress.getByName(address);
			return true;
		} catch (UnknownHostException e) {
			return false;
		}
	}

	public static boolean isPortAvailable(int port) {
		try {
			ServerSocket srv = new ServerSocket(port);
			srv.close();
			srv = null;
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public static void closeSocket(Socket socket) {
		if (socket != null)
			try {
				socket.close();
			} catch (IOException iOException) {
			}
	}
}
