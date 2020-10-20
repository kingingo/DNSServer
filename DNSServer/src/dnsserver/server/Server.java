package dnsserver.server;

import java.io.IOException;

public interface Server{

	public void shutdown();
	public void start() throws IOException;
	
}
