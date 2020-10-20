package dnsserver.terminal.commands;

import dnsserver.main.Main;
import dnsserver.terminal.CommandExecutor;

public class DebugCommand implements CommandExecutor{

	@Override
	public void onCommand(String[] args) {
		Main.debug = !Main.debug;
		Main.log(Main.debug ? "Debug enabled" : "Debug disabled!");
	}

}
