package dnsserver.server.resolver;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Compression;
import org.xbill.DNS.DNSInput;
import org.xbill.DNS.DNSOutput;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Tokenizer;

import dnsserver.main.Main;

public class FilterResolver implements Resolver{

	@Override
	public Message generateReply(Request request) throws Exception {
		Message query = request.getQuery();
		
		if(query.getQuestion().getName().toString().startsWith("testlollol.de")) {
			Main.log("Filter "+query.getQuestion().getName().toString());
			Message response = new Message(query.getHeader().getID());
			response.addRecord(new ARecord(query.getQuestion().getName(), 1, 0, InetAddress.getByName("0.0.0.0")),0);
			return response;
		}
		return null;
	}

}
