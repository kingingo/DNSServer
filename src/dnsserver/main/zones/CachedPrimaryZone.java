package dnsserver.main.zones;

import org.xbill.DNS.Zone;

import dnsserver.main.zoneprovider.ZoneProvider;

public class CachedPrimaryZone {
  protected Zone zone;
  
  protected ZoneProvider zoneProvider;
  
  public CachedPrimaryZone(Zone zone, ZoneProvider zoneProvider) {
    this.zone = zone;
    this.zoneProvider = zoneProvider;
  }
  
  public Zone getZone() {
    return this.zone;
  }
  
  public void setZone(Zone zone) {
    this.zone = zone;
  }
  
  public ZoneProvider getZoneProvider() {
    return this.zoneProvider;
  }
}
