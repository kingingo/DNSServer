package dnsserver.terminal.commands;

import dnsserver.main.Main;
import dnsserver.main.Utils;
import dnsserver.terminal.CommandExecutor;

public class ImportCommand implements CommandExecutor{

	@Override
	public void onCommand(String[] args) {
		if(args.length == 1) {
			String directory = args[0];
			
			try {
				Utils.importZones(directory, "jdbc:mysql://localhost/ddns?serverTimezone=UTC", "root", "");
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}else {
			Main.log("/import [directory]");
		}
	}
}
