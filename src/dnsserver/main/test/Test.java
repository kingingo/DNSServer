package dnsserver.main.test;

import java.io.IOException;
import java.net.UnknownHostException;

import org.xbill.DNS.DohResolver;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.ZoneTransferException;
import org.xbill.DNS.ZoneTransferIn;

public class Test {

	public static void main(String[] args) {
		
		try {
			ZoneTransferIn xfr = ZoneTransferIn.newAXFR(Name.root, "192.5.5.241", null);
			xfr.run();
			for (Record r : xfr.getAXFR()) {
			    System.out.println(r);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ZoneTransferException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
//		try {
//			SimpleResolver resolver = new SimpleResolver("dns.google");
//			Message query = new Message();
//			query.
//			
//			
//		} catch (UnknownHostException e) {
//			e.printStackTrace();
//		}
	}
	
}
