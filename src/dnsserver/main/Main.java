package dnsserver.main;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.OPTRecord;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.TSIG;
import org.xbill.DNS.Zone;

import dnsserver.dynamic.DynamicDNSUpdater;
import dnsserver.main.resolver.AuthoritativeResolver;
import dnsserver.main.resolver.DefaultRequest;
import dnsserver.main.resolver.FilterResolver;
import dnsserver.main.resolver.Request;
import dnsserver.main.resolver.Resolver;
import dnsserver.main.zoneprovider.ZoneProvider;
import dnsserver.main.zoneprovider.db.DBZoneProvider;
import dnsserver.main.zoneprovider.file.FileZoneProvider;
import dnsserver.main.zones.CachedPrimaryZone;
import dnsserver.main.zones.CachedSecondaryZone;
import dnsserver.main.zones.SecondaryZone;
import dnsserver.server.TCPServer;
import dnsserver.server.UDPServer;
import dnsserver.terminal.Terminal;
import lombok.Getter;

public class Main {
	public static final String[] ROOTS = new String[] { 
			"198.41.0.4",
            "199.9.14.201",
            "192.33.4.12",
            "199.7.91.13",
            "192.203.230.10",
            "192.5.5.241",
            "192.112.36.4",
            "198.97.190.53",
            "192.36.148.17",
            "192.58.128.30",
            "193.0.14.129",
            "199.7.83.42",
            "202.12.27.33"
    };
	
	@Getter
	private static Status status = Status.STARTING;
	public static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	public static boolean debug = true;

	@Getter
	private static ArrayList<String> ipWhiteList = new ArrayList<>();

	@Getter
	private static HashMap<String,Integer> ipBlackList = new HashMap<>();
	
	@Getter
	private static ThreadPoolExecutor tcpThreadPool;
	@Getter
	private static ThreadPoolExecutor udpThreadPool;
	private static ArrayList<Resolver> resolvers = new ArrayList<>();

	private static ConcurrentHashMap<Name, CachedPrimaryZone> primaryZoneMap;

	private static ConcurrentHashMap<Name, CachedSecondaryZone> secondaryZoneMap;
	private static HashMap<String, ZoneProvider> zoneProviders;
	static HashMap<Name, TSIG> TSIGs;

	private static int tcpThreadPoolMinSize = 10;
	private static int tcpThreadPoolMaxSize = 50;
	private static int udpThreadPoolMinSize = 10;
	private static int udpThreadPoolMaxSize = 50;
	private static int tcpThreadPoolShutdownTimeout = 60;
	private static int udpThreadPoolShutdownTimeout = 60;

	private static TCPServer tcpServer;
	private static UDPServer udpServer;
	private static DynamicDNSUpdater ddns_updater;

	public static boolean isBlacklisted(InetAddress adr) {
		if(ipBlackList.containsKey(adr.toString())) {
			int counter = ipBlackList.get(adr.toString());
			ipBlackList.remove(adr.toString());
			counter++;
			ipBlackList.put(adr.toString(),counter);
			return true;
		}
		return false;
	}
	
	public static boolean isWhitelisted(InetAddress adr) {
		boolean in;
		if(!(in = ipWhiteList.contains(adr.toString()))) {
			Main.warn("The adress "+adr+" tried to connect but is not whitelisted...");
			ipBlackList.put(adr.toString(),1);
		}
			
		return in;
	}
	
	public static void log(String msg) {
		System.out.println(format.format(new Date()) + " INFO | " + msg);
	}

	public static void warn(String msg) {
		warn(msg, null);
	}

	public static void warn(String msg, Throwable exception) {
		System.err.println(format.format(new Date()) +  " WARNING | " + msg);
		if (exception != null)
			exception.printStackTrace();
	}

	public static void debug(String msg) {
		debug(msg, null);
	}

	public static void debug(String msg, Throwable exception) {
		if (debug) {
			System.err.println(format.format(new Date()) + " DEBUG | " + msg);
			if (exception != null)
				exception.printStackTrace();
		}

	}

	public static boolean isRunning() {
		return getStatus() == Status.STARTED || getStatus() == Status.STARTING;
	}

	public static synchronized void reloadZones() {
		ConcurrentHashMap<Name, CachedPrimaryZone> primaryZoneMap = new ConcurrentHashMap<Name, CachedPrimaryZone>();
		ConcurrentHashMap<Name, CachedSecondaryZone> secondaryZoneMap = new ConcurrentHashMap<Name, CachedSecondaryZone>();
		for (Map.Entry<String, ZoneProvider> zoneProviderEntry : zoneProviders.entrySet()) {
			Collection<Zone> primaryZones;
			Collection<SecondaryZone> secondaryZones;
			Main.log("Getting primary zones from zone provider " + (String) zoneProviderEntry.getKey());
			try {
				primaryZones = ((ZoneProvider) zoneProviderEntry.getValue()).getPrimaryZones();
			} catch (Throwable e) {
				Main.warn("Error getting primary zones from zone provider " + (String) zoneProviderEntry.getKey(), e);
				continue;
			}
			if (primaryZones != null)
				for (Zone zone : primaryZones) {
					Main.log("Got zone " + zone.getOrigin());
					primaryZoneMap.put(zone.getOrigin(), new CachedPrimaryZone(zone, zoneProviderEntry.getValue()));
				}
			Main.log("Getting secondary zones from zone provider " + (String) zoneProviderEntry.getKey());
			try {
				secondaryZones = ((ZoneProvider) zoneProviderEntry.getValue()).getSecondaryZones();
			} catch (Throwable e) {
				Main.warn("Error getting secondary zones from zone provider " + (String) zoneProviderEntry.getKey(), e);
				continue;
			}
			if (secondaryZones != null)
				for (SecondaryZone zone : secondaryZones) {
					Main.log("Got zone " + zone.getZoneName() + " (" + zone.getRemoteServerAddress() + ")");
					CachedSecondaryZone cachedSecondaryZone = new CachedSecondaryZone(zoneProviderEntry.getValue(),
							zone);
					secondaryZoneMap.put(cachedSecondaryZone.getSecondaryZone().getZoneName(), cachedSecondaryZone);
				}
		}
		Main.primaryZoneMap = primaryZoneMap;
		Main.secondaryZoneMap = secondaryZoneMap;
	}

	public static synchronized void shutdown() {
		if (isRunning()) {
			status = Status.SHUTTING_DOWN;
			log("Start shutingdown all...");
			log("stop TCP Thread pool..");
			tcpThreadPool.shutdown();
			try {
				tcpThreadPool.awaitTermination(tcpThreadPoolShutdownTimeout, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				log("Timeout waiting " + tcpThreadPoolShutdownTimeout
						+ " seconds for TCP thread pool to shutdown, forcing thread pool shutdown...");
				tcpThreadPool.shutdownNow();
			}
			log("stop TCP Server...");
			tcpServer.shutdown();
			
			log("stop UDP Thread pool..");
			udpThreadPool.shutdown();
			try {
				udpThreadPool.awaitTermination(udpThreadPoolShutdownTimeout, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				log("Timeout waiting " + udpThreadPoolShutdownTimeout
						+ " seconds for UDP thread pool to shutdown, forcing thread pool shutdown...");
				udpThreadPool.shutdownNow();
			}
			log("stop UDP Server...");
			udpServer.shutdown();
			log("stop DynDNS Updater...");
			ddns_updater.shutdown();
			log("stop Terminal...");
			Terminal.getInstance().stop();
			status = Status.SHUTDOWN;
		}
	}

	public static byte[] generateReply(Message query, byte[] in, int length, Socket socket, SocketAddress socketAddress)
			throws IOException {
		int maxLength;
		Request request = new DefaultRequest(socketAddress, query, in, length, socket);
		Message response = null;
		for (Resolver res : resolvers) {
			try {
				response = res.generateReply(request);
				if (response != null)
					break;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		OPTRecord queryOPT = query.getOPT();
		
		if (response == null) {
		      response = Utils.getInternalResponse(query, in, length, socket, queryOPT);
		      Main.log("Got no response from resolvers for query " + Utils.toString(query.getQuestion()) + " sending default response " + Rcode.string(3));
		} 
		
		if (socket != null) {
			maxLength = 65535;
		} else if (queryOPT != null) {
			maxLength = Math.max(queryOPT.getPayloadSize(), 512);
		} else {
			maxLength = 512;
		}
		return response == null ? null : response.toWire(maxLength);
	}
	
	public static Zone getZone(Name name) {
		CachedPrimaryZone cachedPrimaryZone = primaryZoneMap.get(name);
		if (cachedPrimaryZone != null)
			return cachedPrimaryZone.getZone();
		CachedSecondaryZone cachedSecondaryZone = secondaryZoneMap.get(name);
		if (cachedSecondaryZone != null && cachedSecondaryZone.getSecondaryZone().getZoneCopy() != null)
			return cachedSecondaryZone.getSecondaryZone().getZoneCopy();
		return null;
	}
	
	public static TSIG getTSIG(Name name) {
	    return TSIGs.get(name);
	}
	
	private static void addTSIG(String algstr, String namestr, String key) throws IOException {
	    Name name = Name.fromString(namestr, Name.root);
	    TSIGs.put(name, new TSIG(algstr, namestr, key));
	}

	public static void main(String[] args) {
		log("init log ...");
		Log.init();
		log("Starting Terminal...");
		Terminal.getInstance();
		
		log("Initializing TCP thread pool...");
		tcpThreadPool = new ThreadPoolExecutor(tcpThreadPoolMinSize, tcpThreadPoolMaxSize, 60L, TimeUnit.SECONDS,
				new SynchronousQueue<Runnable>(true));
		log("Initializing UDP thread pool...");
		udpThreadPool = new ThreadPoolExecutor(udpThreadPoolMinSize, udpThreadPoolMaxSize, 60L, TimeUnit.SECONDS,
				new SynchronousQueue<Runnable>(true));

		primaryZoneMap = new ConcurrentHashMap<>();
		secondaryZoneMap = new ConcurrentHashMap<>();

		TSIGs = new HashMap<Name, TSIG>();
		
		zoneProviders = new HashMap<String, ZoneProvider>();
		zoneProviders.put("Root", new FileZoneProvider("zones"));
		try {
			zoneProviders.put("Main", new DBZoneProvider("jdbc:mysql://localhost/ddns?serverTimezone=UTC", "root", ""));
		} catch (ClassNotFoundException e1) {
			e1.printStackTrace();
		}

		resolvers.add(new FilterResolver());
		resolvers.add(new AuthoritativeResolver());

		try {
			log("Start DDNS Updater");
			ddns_updater = new DynamicDNSUpdater();
			log("Start TCPServer");
			tcpServer = new TCPServer();
			log("Start UDPServer");
			udpServer = new UDPServer();
		} catch (IOException e) {
			e.printStackTrace();
		}
		reloadZones();
		status = Status.STARTED;
	}

}
