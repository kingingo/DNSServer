package dnsserver.terminal.commands;

import dnsserver.main.Main;
import dnsserver.terminal.CommandExecutor;

public class StopCommand implements CommandExecutor{

	@Override
	public void onCommand(String[] args) {
		Main.log("Stopping server...");
		Main.shutdown();
	}

}
