package dnsserver.terminal.commands;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.github.jgonian.ipmath.Ipv4Range;
import com.github.jgonian.ipmath.Ipv6Range;

import dnsserver.main.Firewall;
import dnsserver.main.Main;
import dnsserver.terminal.CommandExecutor;

public class WhitelistCommand implements CommandExecutor{

	@Override
	public void onCommand(String[] args) {
		if(args.length == 0) {
			Main.log("Whitelist:");
			for(Ipv6Range entry : Firewall.getIpv6WhiteList()) {
				Main.log("	"+entry.toStringInRangeNotation());
			}
			for(Ipv4Range entry : Firewall.getIpv4WhiteList()) {
				Main.log("	"+entry.toStringInRangeNotation());
			}
		}else {
			if(args[0].equalsIgnoreCase("add") && args.length >= 2) {
				String ip = args[1];
				Firewall.addToWhitelist(ip);
				Main.log("added ip "+ip);
			}else {
				Main.log("/whitelist add IP");
			}
		}
	}
}
