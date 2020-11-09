package dnsserver.terminal.commands;

import java.util.Map.Entry;

import com.github.jgonian.ipmath.Ipv4Range;
import com.github.jgonian.ipmath.Ipv6Range;

import dnsserver.main.Firewall;
import dnsserver.main.Main;
import dnsserver.terminal.CommandExecutor;

public class BlacklistCommand implements CommandExecutor{

	@Override
	public void onCommand(String[] args) {
		Main.log("Blacklist:");
		for(Ipv6Range entry : Firewall.getIpv6BlackList()) {
			Main.log("	"+entry.toStringInRangeNotation());
		}
		for(Ipv4Range entry : Firewall.getIpv4BlackList()) {
			Main.log("	"+entry.toStringInRangeNotation());
		}
	}
}
