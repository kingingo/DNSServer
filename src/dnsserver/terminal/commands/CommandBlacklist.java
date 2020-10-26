package dnsserver.terminal.commands;

import java.util.Map.Entry;

import dnsserver.main.Main;
import dnsserver.terminal.CommandExecutor;

public class CommandBlacklist implements CommandExecutor{

	@Override
	public void onCommand(String[] args) {
		Main.log("Blacklist:");
		for(Entry<String, Integer> entry : Main.getIpBlackList().entrySet()) {
			Main.log("	"+entry.getKey()+" called "+entry.getValue()+" times");
		}
	}
}
