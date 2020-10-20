package dnsserver.terminal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.reflections.Reflections;

import dnsserver.main.Main;

public class Terminal implements Runnable{
	private static Terminal instance;
	private static ArrayList<CommandExecutor> commands = new ArrayList<>();
	
	public static Terminal getInstance() {
		if(instance==null) {
			loadCommands();
			instance=new Terminal();
		}
		return instance;
	}
	
	public static ArrayList<CommandExecutor> getCommands(){
		return commands;
	}
	
	public static void register(CommandExecutor cmd) {
		if(!commands.contains(cmd)) {
			commands.add(cmd);
		}
	}
	
	private Thread thread;
	
	private Terminal() {
		this.thread=new Thread(this);
		this.thread.start();
	}
	
	@SuppressWarnings("deprecation")
	public void stop() {
		this.thread.stop();
	}
	
	public static void loadCommands() {
		if(!commands.isEmpty())return;
		Reflections reflections = new Reflections( "dnsserver.terminal.commands" );
		List<Class<? extends CommandExecutor>> moduleClasses = new ArrayList<>( reflections.getSubTypesOf( CommandExecutor.class ) );

		Collections.sort(moduleClasses, new Comparator<Class<? extends CommandExecutor>>() {
		    @Override
		    public int compare(Class<? extends CommandExecutor> o1, Class<? extends CommandExecutor> o2) {
		        return o1.getSimpleName().compareTo(o2.getSimpleName());
		    }
		});
		
		for ( Class<? extends CommandExecutor> clazz : moduleClasses ){
			try {
				register(clazz.newInstance());
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		
		for(CommandExecutor cmd : commands)Main.log(cmd.getCommand() + " registered");
	}
	
	private boolean onCommand(String line) {
		String[] splitted = line.split(" ");
		if(splitted.length > 0) {
			String command = splitted[0];
			String[] args = new String[splitted.length-1];
			
			for(int i = 1; i < splitted.length; i++)args[i-1]=splitted[i];
			
			for(CommandExecutor cmd : commands) {
				if(cmd.getCommand().equalsIgnoreCase(command) || cmd.isAlias(command)) {
					try { cmd.onCommand(args); }catch(Exception e) {e.printStackTrace();}
					return true;
				}
			}
			return false;
		}
		return false;
	}

	@Override
	public void run() {
		while(Main.isRunning()) {
			try {
				String line;
				while( (line=System.console().readLine())!= null ) {
					if(!this.onCommand(line)) {
						Main.log("Command not found "+line);
					}
				}

				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
