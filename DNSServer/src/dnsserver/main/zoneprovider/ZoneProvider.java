package dnsserver.main.zoneprovider;

import java.util.Collection;

import org.xbill.DNS.Zone;

import dnsserver.main.zones.SecondaryZone;

public interface ZoneProvider{
  Collection<Zone> getPrimaryZones();
  
  Collection<SecondaryZone> getSecondaryZones();
  
  void zoneUpdated(SecondaryZone paramSecondaryZone);
  
  void zoneChecked(SecondaryZone paramSecondaryZone);
}