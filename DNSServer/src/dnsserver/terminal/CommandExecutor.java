package dnsserver.terminal;
public interface CommandExecutor {
	public default String getCommand() {
		return getClass().getSimpleName().toLowerCase().replaceAll("command", "");
	}
	
	public default boolean isAlias(String alias) {
		return false;
	}
	
	public void onCommand(String[] args);
}
