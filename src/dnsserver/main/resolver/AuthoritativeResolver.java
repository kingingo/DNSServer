package dnsserver.main.resolver;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Iterator;

import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.DNAMERecord;
import org.xbill.DNS.Header;
import org.xbill.DNS.Message;
import org.xbill.DNS.NSRecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.NameTooLongException;
import org.xbill.DNS.OPTRecord;
import org.xbill.DNS.RRset;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.SetResponse;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TSIG;
import org.xbill.DNS.TSIGRecord;
import org.xbill.DNS.Type;
import org.xbill.DNS.Zone;

import dnsserver.main.Main;
import dnsserver.main.Utils;

public class AuthoritativeResolver implements Resolver {
	
	public String getName() {
		return this.getClass().getSimpleName();
	}
	
	public Message generateReply(Request request) throws Exception {
		Message query = request.getQuery();
		
		Header header = query.getHeader();
		if (header.getFlag(0))
			return null;
		if (header.getRcode() != 0)
			return null;
		if (header.getOpcode() != 0)
			return null;
		
		TSIGRecord queryTSIG = query.getTSIG();
		TSIG tsig = null;
		if (queryTSIG != null) {
			tsig = Main.getTSIG(queryTSIG.getName());
			if (tsig == null || tsig.verify(query, request.getRawQuery(), request.getRawQueryLength(), null) != 0)
				return null;
		}
		
		Record queryRecord = query.getQuestion();
		if (queryRecord == null)
			return null;
		Name name = queryRecord.getName();
		Zone zone = findBestZone(name);
		if (zone != null) {
			Main.debug("Resolver " + getName() + " processing request for " + name + ", matching zone found ");
			int flags = 0;
			
			OPTRecord queryOPT = query.getOPT();
			if (queryOPT != null && (queryOPT.getFlags() & 0x8000) != 0)
				flags = 1;
			Message response = new Message(query.getHeader().getID());
			response.getHeader().setFlag(0);
			if (query.getHeader().getFlag(7))
				response.getHeader().setFlag(7);
			response.addRecord(queryRecord, 0);
			int type = queryRecord.getType();
			int dclass = queryRecord.getDClass();
			if (type == 252 && request.getSocket() != null)
				return doAXFR(name, query, tsig, queryTSIG, request.getSocket());
			if (!Type.isRR(type) && type != 255)
				return null;
			byte rcode = addAnswer(response, name, type, dclass, 0, flags, zone);
			if (rcode != 0 && rcode != 3)
				return Utils.errorMessage(query, rcode);
			addAdditional(response, flags);
			if (queryOPT != null) {
				int optflags = (flags == 1) ? 32768 : 0;
				OPTRecord opt = new OPTRecord(4096, rcode, 0, optflags);
				response.addRecord((Record) opt, 3);
			}
			response.setTSIG(tsig, 0, queryTSIG);
			return response;
		}
		return null;
	}

	private final void addAdditional(Message response, int flags) {
		addAdditional2(response, 1, flags);
		addAdditional2(response, 2, flags);
	}

	private byte addAnswer(Message response, Name name, int type, int dclass, int iterations, int flags, Zone zone) {
		byte rcode = 0;
		if (iterations > 6)
			return 0;
		if (type == 24 || type == 46) {
			type = 255;
			flags |= 0x2;
		}
		if (zone == null)
			zone = findBestZone(name);
		if (zone != null) {
			SetResponse sr = zone.findRecords(name, type);
			if (sr.isNXDOMAIN()) {
				response.getHeader().setRcode(3);
				if (zone != null) {
					addSOA(response, zone);
					if (iterations == 0)
						response.getHeader().setFlag(5);
				}
				rcode = 3;
			} else if (sr.isNXRRSET()) {
				if (zone != null) {
					addSOA(response, zone);
					if (iterations == 0)
						response.getHeader().setFlag(5);
				}
			} else if (sr.isDelegation()) {
				RRset nsRecords = sr.getNS();
				addRRset(nsRecords.getName(), response, nsRecords, 2, flags);
			} else if (sr.isCNAME()) {
				CNAMERecord cname = sr.getCNAME();
				RRset rrset = new RRset((Record) cname);
				addRRset(name, response, rrset, 1, flags);
				if (zone != null && iterations == 0)
					response.getHeader().setFlag(5);
				rcode = addAnswer(response, cname.getTarget(), type, dclass, iterations + 1, flags, (Zone) null);
			} else if (sr.isDNAME()) {
				Name newname;
				DNAMERecord dname = sr.getDNAME();
				RRset rrset = new RRset((Record) dname);
				addRRset(name, response, rrset, 1, flags);
				try {
					newname = name.fromDNAME(dname);
				} catch (NameTooLongException e) {
					return 6;
				}
				rrset = new RRset((Record) new CNAMERecord(name, dclass, 0L, newname));
				addRRset(name, response, rrset, 1, flags);
				if (zone != null && iterations == 0)
					response.getHeader().setFlag(5);
				rcode = addAnswer(response, newname, type, dclass, iterations + 1, flags, (Zone) null);
			} else if (sr.isSuccessful()) {
				RRset[] rrsets = (RRset[]) sr.answers().toArray();
				byte b;
				int i;
				RRset[] arrayOfRRset1;
				for (i = (arrayOfRRset1 = rrsets).length, b = 0; b < i;) {
					RRset rrset = arrayOfRRset1[b];
					addRRset(name, response, rrset, 1, flags);
					b++;
				}
				if (zone != null) {
					addNS(response, zone, flags);
					if (iterations == 0)
						response.getHeader().setFlag(5);
				}
			}
		}
		return rcode;
	}

	private Message doAXFR(Name name, Message query, TSIG tsig, TSIGRecord qtsig, Socket socket) {
		boolean first = true;
		Zone zone = findBestZone(name);
		if (zone == null)
			return Utils.errorMessage(query, 5);
		boolean axfrAllowed = false;
		Iterator<?> nsIterator = (Iterator<?>) zone.getNS().rrs();
		while (nsIterator.hasNext()) {
			NSRecord record = (NSRecord) nsIterator.next();
			try {
				String nsIP = InetAddress.getByName(record.getTarget().toString()).getHostAddress();
				if (socket.getInetAddress().getHostAddress().equals(nsIP)) {
					axfrAllowed = true;
					break;
				}
			} catch (UnknownHostException e) {
				Main.warn("Unable to resolve hostname of nameserver " + record.getTarget() + " in zone "
						+ zone.getOrigin() + " while processing AXFR request from " + socket.getRemoteSocketAddress());
			}
		}
		if (!axfrAllowed) {
			Main.warn("AXFR request of zone " + zone.getOrigin() + " from " + socket.getRemoteSocketAddress()
					+ " refused!");
			return Utils.errorMessage(query, 5);
		}
		Iterator<?> it = zone.AXFR();
		try {
			DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());
			int id = query.getHeader().getID();
			while (it.hasNext()) {
				RRset rrset = (RRset) it.next();
				Message response = new Message(id);
				Header header = response.getHeader();
				header.setFlag(0);
				header.setFlag(5);
				addRRset(rrset.getName(), response, rrset, 1, 1);
				if (tsig != null) {
					tsig.applyStream(response, qtsig, first);
					qtsig = response.getTSIG();
				}
				first = false;
				byte[] out = response.toWire();
				dataOut.writeShort(out.length);
				dataOut.write(out);
			}
		} catch (IOException ex) {
			Main.warn("AXFR failed", ex);
		} finally {
			Utils.closeSocket(socket);
		}
		return null;
	}

	private final void addSOA(Message response, Zone zone) {
		response.addRecord((Record) zone.getSOA(), 2);
	}

	private final void addNS(Message response, Zone zone, int flags) {
		RRset nsRecords = zone.getNS();
		addRRset(nsRecords.getName(), response, nsRecords, 2, flags);
	}

	private void addGlue(Message response, Name name, int flags) {
		RRset a = findExactMatch(name, 1, 1, true);
		if (a == null)
			return;
		addRRset(name, response, a, 3, flags);
	}

	private void addAdditional2(Message response, int section, int flags) {
		Record[] records = response.getSectionArray(section);
		byte b;
		int i;
		Record[] arrayOfRecord1;
		for (i = (arrayOfRecord1 = records).length, b = 0; b < i;) {
			Record r = arrayOfRecord1[b];
			Name glueName = r.getAdditionalName();
			if (glueName != null)
				addGlue(response, glueName, flags);
			b++;
		}
	}

	private RRset findExactMatch(Name name, int type, int dclass, boolean glue) {
		Zone zone = findBestZone(name);
		if (zone != null)
			return zone.findExactMatch(name, type);
		return null;
	}

	private void addRRset(Name name, Message response, RRset rrset, int section, int flags) {
		for (int s = 1; s <= section; s++) {
			if (response.findRRset(name, rrset.getType(), s))
				return;
		}
		if ((flags & 0x2) == 0) {
			Iterator<?> it = (Iterator<?>) rrset.rrs();
			while (it.hasNext()) {
				Record r = (Record) it.next();
				if (r.getName().isWild() && !name.isWild())
					r = r.withName(name);
				response.addRecord(r, section);
			}
		}
		if ((flags & 0x3) != 0) {
			Iterator<?> it = (Iterator<?>) rrset.sigs();
			while (it.hasNext()) {
				Record r = (Record) it.next();
				if (r.getName().isWild() && !name.isWild())
					r = r.withName(name);
				response.addRecord(r, section);
			}
		}
	}

	private Zone findBestZone(Name name) {
		Zone foundzone = Main.getZone(name);
		if (foundzone != null)
			return foundzone;
		int labels = name.labels();
		for (int i = 1; i < labels; i++) {
			Name tname = new Name(name, i);
			foundzone = Main.getZone(tname);
			if (foundzone != null)
				return foundzone;
		}
		return null;
	}
}
