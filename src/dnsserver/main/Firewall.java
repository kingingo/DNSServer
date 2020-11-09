package dnsserver.main;

import com.github.jgonian.ipmath.Ipv4;
import com.github.jgonian.ipmath.Ipv4Range;
import com.github.jgonian.ipmath.Ipv6;
import com.github.jgonian.ipmath.Ipv6Range;
import com.github.jgonian.ipmath.SortedResourceSet;

import lombok.Getter;

public class Firewall {


	
	@Getter
	private static SortedResourceSet<Ipv4, Ipv4Range> ipv4WhiteList = new SortedResourceSet<>();
	@Getter
	private static SortedResourceSet<Ipv4, Ipv4Range> ipv4BlackList = new SortedResourceSet<>();
	
	@Getter
	private static SortedResourceSet<Ipv6, Ipv6Range> ipv6WhiteList = new SortedResourceSet<>();
	@Getter
	private static SortedResourceSet<Ipv6, Ipv6Range> ipv6BlackList = new SortedResourceSet<>();
	
	public static void removeFromBlacklist(String ip) {
		removeFromBlacklist(ip, "");
	}
	
	public static void removeFromBlacklist(String ip, String ip_prefix) {
		if(isIpv4(ip)) {
			ipv4BlackList.remove(Ipv4.of(ip));
		}else{
			ipv6BlackList.remove(Ipv6.of(ip));
			if(!ip_prefix.isEmpty())ipv6BlackList.remove(Ipv6Range.parse(ip_prefix));
		}
	}
	
	public static void addToBlacklist(String ip) {
		addToBlacklist(ip, "");
	}
	
	public static void addToBlacklist(String ip, String ip_prefix) {
		if(isIpv4(ip)) {
			ipv4BlackList.add(Ipv4.of(ip));
		}else {
			ipv6BlackList.add(Ipv6.of(ip));
			if(!ip_prefix.isEmpty())ipv6BlackList.add(Ipv6Range.parse(ip_prefix));
		}
	}
	
	public static void addToWhitelist(String ip) {
		addToWhitelist(ip, "");
	}
	
	public static void addToWhitelist(String ip, String ip_prefix) {
		if(isIpv4(ip)) {
			ipv4WhiteList.add(Ipv4.of(ip));
		}else {
			ipv6WhiteList.add(Ipv6.of(ip));
			if(!ip_prefix.isEmpty())ipv6WhiteList.add(Ipv6Range.parse(ip_prefix));
		}
	}
	
	public static boolean isIpv4(String ip) {
		return ip.contains(".");
	}
	
	public static boolean containsBlacklist(String ip) {
		if(isIpv4(ip)) {
			return containsBlacklist(Ipv4.parse(ip));
		}else {
			return containsBlacklist(Ipv6.parse(ip));
		}
	}
	
	public static boolean containsBlacklist(Ipv6 ip) {
		return ipv6BlackList.contains(ip);
	}
	
	public static boolean containsBlacklist(Ipv4 ip) {
		return ipv4BlackList.contains(ip);
	}
	
	public static boolean containsWhitelist(String ip) {
		if(isIpv4(ip)) {
			return containsWhitelist(Ipv4.parse(ip));
		}else {
			return containsWhitelist(Ipv6.parse(ip));
		}
	}
	
	public static boolean containsWhitelist(Ipv6 ip) {
		return ipv6WhiteList.contains(ip);
	}
	
	public static boolean containsWhitelist(Ipv4 ip) {
		return ipv4WhiteList.contains(ip);
	}
}
