package dnsserver.server.resolver;

import org.xbill.DNS.Message;

public interface Resolver{
  Message generateReply(Request paramRequest) throws Exception;
}