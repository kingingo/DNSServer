package dnsserver.main.resolver;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.Cache;
import org.xbill.DNS.Credibility;
import org.xbill.DNS.DNAMERecord;
import org.xbill.DNS.ExtendedResolver;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.NameTooLongException;
import org.xbill.DNS.RRset;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.ResolverConfig;
import org.xbill.DNS.SetResponse;
import org.xbill.DNS.Type;
import org.xbill.DNS.Zone;

import dnsserver.main.Main;
import dnsserver.main.Utils;
import dnsserver.main.zoneprovider.db.DBZoneProvider;
import dnsserver.main.zoneprovider.db.beans.DBSecondaryZone;
import dnsserver.main.zoneprovider.db.beans.DBZone;
import dnsserver.main.zones.CachedSecondaryZone;
import dnsserver.main.zones.SecondaryZone;
import lombok.Getter;

public class RecursiveResolver implements Resolver {

	public final static String[] SERVERS = new String[] { "8.8.8.8" };

	@Override
	public Message generateReply(Request request) throws Exception {
		long time = System.currentTimeMillis();
		ExtendedResolver resolver = new ExtendedResolver(SERVERS);
		Message response = resolver.send(request.getQuery());
		
		Integer rcode = response.getRcode();
		time = System.currentTimeMillis() - time;
		Main.debug("RecursiveResolver("+time+" ms): Got response " + Rcode.string(response.getHeader().getRcode()) + "("+rcode+") with " + (response.getSectionArray(1)).length + " answer, " + (response.getSectionArray(2)).length + " authoritative and " + (response.getSectionArray(3)).length + " additional records");

		if(rcode == null 
				|| rcode.intValue() == 3 
				|| rcode.intValue() == 2 
				|| (rcode.intValue() == 0 && (response.getSectionArray(1)).length == 0 && (response.getSectionArray(2)).length == 0)){
			Main.debug("Resolver RecursiveResolver ignoring request for " + request.getQuery().getQuestion().getName() + ", no matching zone found");
			return null;
		}

		((DBZoneProvider)Main.getZoneProviders().get("DBZoneProvider")).addSecondaryZone(response);
		return response;
	}

	private class MessageResolver {
		/** The lookup was successful. */
		public static final int SUCCESSFUL = 0;

		/**
		 * The lookup failed due to a data or server error. Repeating the lookup would
		 * not be helpful.
		 */
		public static final int UNRECOVERABLE = 1;

		/**
		 * The lookup failed due to a network error. Repeating the lookup may be
		 * helpful.
		 */
		public static final int TRY_AGAIN = 2;

		/** The host does not exist. */
		public static final int HOST_NOT_FOUND = 3;

		/** The host exists, but has no records associated with the queried type. */
		public static final int TYPE_NOT_FOUND = 4;

		private List<Name> searchPath;
		private int ndots;
		private boolean temporary_cache;
		private int credibility;
		private Name name;
		private int type;
		private int dclass;
		private int iterations;
		private boolean foundAlias;
		private boolean done;
		private boolean doneCurrent;
		private List<Name> aliases;
		private Record[] answers;
		private int result;
		private String error;
		private boolean nxdomain;
		private boolean badresponse;
		private String badresponse_error;
		private boolean networkerror;
		private boolean timedout;
		private boolean nametoolong;
		private boolean referral;
		private boolean cycleResults = true;
		private ExtendedResolver resolver;
		private Cache cache;
		@Getter
		private Message response;

		private MessageResolver(Message request) throws UnknownHostException {
			this.name = request.getQuestion().getName();
			this.type = request.getQuestion().getType();
			this.dclass = request.getQuestion().getDClass();
			this.credibility = Credibility.NORMAL;
			this.ndots = ResolverConfig.getCurrentConfig().ndots();
			this.resolver = new ExtendedResolver(SERVERS);
			this.cache = new Cache();
		}

		private void follow(Name name, Name oldname) {
			foundAlias = true;
			badresponse = false;
			networkerror = false;
			timedout = false;
			nxdomain = false;
			referral = false;
			iterations++;
			if (iterations >= 10 || name.equals(oldname)) {
				result = UNRECOVERABLE;
				error = "CNAME loop";
				done = true;
				return;
			}
			if (aliases == null) {
				aliases = new ArrayList<>();
			}
			aliases.add(oldname);
			lookup(name);
		}

		private void lookup(Name current) {
			SetResponse sr = cache.lookupRecords(current, type, credibility);

			processResponse(current, sr);
			if (done || doneCurrent) {
				return;
			}

			Record question = Record.newRecord(current, type, dclass);
			Message query = Message.newQuery(question);
			try {
				response = resolver.send(query);
			} catch (IOException e) {

				// A network error occurred. Press on.
				if (e instanceof InterruptedIOException) {
					timedout = true;
				} else {
					networkerror = true;
				}
				return;
			}
			int rcode = response.getHeader().getRcode();
			if (rcode != Rcode.NOERROR && rcode != Rcode.NXDOMAIN) {
				// The server we contacted is broken or otherwise unhelpful.
				// Press on.
				badresponse = true;
				badresponse_error = Rcode.string(rcode);
				return;
			}

			if (!query.getQuestion().equals(response.getQuestion())) {
				// The answer doesn't match the question. That's not good.
				badresponse = true;
				badresponse_error = "response does not match query";
				return;
			}

			sr = cache.addMessage(response);
			if (sr == null) {
				sr = cache.lookupRecords(current, type, credibility);
			}
			processResponse(current, sr);
		}

		private void resolve(Name current, Name suffix) {
			doneCurrent = false;
			Name tname;
			if (suffix == null) {
				tname = current;
			} else {
				try {
					tname = Name.concatenate(current, suffix);
				} catch (NameTooLongException e) {
					nametoolong = true;
					return;
				}
			}
			lookup(tname);
		}

		private void reset() {
			iterations = 0;
			foundAlias = false;
			done = false;
			doneCurrent = false;
			aliases = null;
			answers = null;
			result = -1;
			error = null;
			nxdomain = false;
			badresponse = false;
			badresponse_error = null;
			networkerror = false;
			timedout = false;
			nametoolong = false;
			referral = false;
			if (temporary_cache) {
				cache.clearCache();
			}
		}

		public Record[] run() {
			if (done) {
				reset();
			}
			if (name.isAbsolute()) {
				resolve(name, null);
			} else if (searchPath == null) {
				resolve(name, Name.root);
			} else {
				if (name.labels() > ndots) {
					resolve(name, Name.root);
				}
				if (done) {
					return answers;
				}

				for (Name value : searchPath) {
					resolve(name, value);
					if (done) {
						return answers;
					} else if (foundAlias) {
						break;
					}
				}

				resolve(name, Name.root);
			}
			if (!done) {
				if (badresponse) {
					result = TRY_AGAIN;
					error = badresponse_error;
					done = true;
				} else if (timedout) {
					result = TRY_AGAIN;
					error = "timed out";
					done = true;
				} else if (networkerror) {
					result = TRY_AGAIN;
					error = "network error";
					done = true;
				} else if (nxdomain) {
					result = HOST_NOT_FOUND;
					done = true;
				} else if (referral) {
					result = UNRECOVERABLE;
					error = "referral";
					done = true;
				} else if (nametoolong) {
					result = UNRECOVERABLE;
					error = "name too long";
					done = true;
				}
			}
			return answers;
		}

		private void processResponse(Name name, SetResponse response) {
			if (response.isSuccessful()) {
				List<RRset> rrsets = response.answers();
				List<Record> l = new ArrayList<>();

				for (RRset set : rrsets) {
					l.addAll(set.rrs(cycleResults));
				}

				result = SUCCESSFUL;
				answers = l.toArray(new Record[0]);
				done = true;
			} else if (response.isNXDOMAIN()) {
				nxdomain = true;
				doneCurrent = true;
				if (iterations > 0) {
					result = HOST_NOT_FOUND;
					done = true;
				}
			} else if (response.isNXRRSET()) {
				result = TYPE_NOT_FOUND;
				answers = null;
				done = true;
			} else if (response.isCNAME()) {
				CNAMERecord cname = response.getCNAME();
				follow(cname.getTarget(), name);
			} else if (response.isDNAME()) {
				DNAMERecord dname = response.getDNAME();
				try {
					follow(name.fromDNAME(dname), name);
				} catch (NameTooLongException e) {
					result = UNRECOVERABLE;
					error = "Invalid DNAME target";
					done = true;
				}
			} else if (response.isDelegation()) {
				// We shouldn't get a referral. Ignore it.
				referral = true;
			}
		}
	}
}
