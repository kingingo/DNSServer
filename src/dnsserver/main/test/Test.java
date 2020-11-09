package dnsserver.main.test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

import org.xbill.DNS.DohResolver;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.MXRecord;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;
import org.xbill.DNS.Zone;
import org.xbill.DNS.ZoneTransferException;
import org.xbill.DNS.ZoneTransferIn;

import dnsserver.main.Main;
import dnsserver.main.Utils;
import dnsserver.main.zoneprovider.db.DBZoneProvider;
import dnsserver.main.zoneprovider.db.beans.DBZone;

public class Test {

	public static void main(String[] args) {
		
		
//		try {
//			DBZoneProvider provider =  new DBZoneProvider("jdbc:mysql://localhost/ddns?serverTimezone=UTC", "root", "");
////			provider.zoneChecked(zone);
//			
//			
//		} catch (ClassNotFoundException e1) {
//			e1.printStackTrace();
//		}
		
//		for(String rr : Main.ROOTS) {
//			try {
//				ZoneTransferIn xfr = ZoneTransferIn.newAXFR(Name.root,rr, null);
//				xfr.run();
//				for (Record r : xfr.getAXFR()) {
//				    System.out.println(r);
//				}
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (ZoneTransferException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
		
		try {
			String url;
			Record[] records0 = new Lookup(url="google.com",Type.NS).run();
			Record[] records1 = new Lookup(url="google.com",Type.SOA).run();
			Record[] records2 = new Lookup(url="google.com",Type.ANY).run();
			Record[] records3 = new Lookup(url="google.com",Type.AAAA).run();
			Record[] records4 = new Lookup(url="google.com",Type.A).run();
//			if(records==null) {
//			    System.out.println("records is null");
//				return;
//			}
			Record[] records = merge(records0, records1, records2,records3,records4);
			
			for (int i = 0; i < records.length; i++) {
			    Record record = records[i];
			    System.out.println(""+record);
			}
			
			Zone zone = new Zone(Name.fromString(url, Name.root), records);
			DBZone db = new DBZone(zone, true);
			ArrayList<DBZone> dbZones = new ArrayList<>();
			dbZones.add(db);
			Utils.importZones(dbZones, "jdbc:mysql://54.38.22.48/ddns?serverTimezone=UTC", "root", "daropass");
			
		}catch (TextParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	// Function to merge multiple arrays in Java 8 and above
	public static Record[] merge(Record[] ...arrays)
	{
	    Stream<Record> stream = Stream.of();
	    for (Record[] s: arrays)
	        stream = Stream.concat(stream, Arrays.stream(s));
	 
	    return stream.toArray(Record[]::new);
	}
	
}
