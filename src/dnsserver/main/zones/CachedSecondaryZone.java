package dnsserver.main.zones;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;

import org.xbill.DNS.DClass;
import org.xbill.DNS.Record;
import org.xbill.DNS.Zone;
import org.xbill.DNS.ZoneTransferException;
import org.xbill.DNS.ZoneTransferIn;

import dnsserver.main.Main;
import dnsserver.main.zoneprovider.ZoneProvider;

public class CachedSecondaryZone {
  protected ZoneProvider zoneProvider;
  
  private SecondaryZone secondaryZone;
  
  public CachedSecondaryZone(ZoneProvider zoneProvider, SecondaryZone secondaryZone) {
    this.zoneProvider = zoneProvider;
    this.secondaryZone = secondaryZone;
    if (this.secondaryZone.getZoneCopy() != null)
      Main.log("Using stored zone data for sedondary zone " + this.secondaryZone.getZoneName()); 
  }
  
  public SecondaryZone getSecondaryZone() {
    return this.secondaryZone;
  }
  
  public void setSecondaryZone(SecondaryZone secondaryZone) {
    this.secondaryZone = secondaryZone;
  }
  
  public void update(int axfrTimeout) {
    try {
      ZoneTransferIn xfrin = ZoneTransferIn.newAXFR(this.secondaryZone.getZoneName(), this.secondaryZone.getRemoteServerAddress(), null);
      xfrin.setDClass(DClass.value(this.secondaryZone.getDclass()));
      xfrin.setTimeout(axfrTimeout);
      xfrin.run();
      List<Record> records = xfrin.getAXFR();
      if (!xfrin.isAXFR()) {
        Main.warn("Unable to transfer zone " + this.secondaryZone.getZoneName() + " from server " + this.secondaryZone.getRemoteServerAddress() + ", response is not a valid AXFR!");
        return;
      } 
      Zone axfrZone = new Zone(this.secondaryZone.getZoneName(), records.<Record>toArray(new Record[records.size()]));
      Main.debug("Zone " + this.secondaryZone.getZoneName() + " successfully transfered from server " + this.secondaryZone.getRemoteServerAddress());
      if (!axfrZone.getSOA().getName().equals(this.secondaryZone.getZoneName()))
        Main.warn("Invalid AXFR zone name in response when updating secondary zone " + this.secondaryZone.getZoneName() + ". Got zone name " + axfrZone.getSOA().getName() + " in respons."); 
      if (this.secondaryZone.getZoneCopy() == null || this.secondaryZone.getZoneCopy().getSOA().getSerial() != axfrZone.getSOA().getSerial()) {
        this.secondaryZone.setZoneCopy(axfrZone);
        this.secondaryZone.setDownloaded(new Timestamp(System.currentTimeMillis()));
        this.zoneProvider.zoneUpdated(this.secondaryZone);
        Main.log("Zone " + this.secondaryZone.getZoneName() + " successfully updated from server " + this.secondaryZone.getRemoteServerAddress());
      } else {
        Main.log("Zone " + this.secondaryZone.getZoneName() + " is already up to date with serial " + axfrZone.getSOA().getSerial());
        this.zoneProvider.zoneChecked(this.secondaryZone);
      } 
      this.secondaryZone.setDownloaded(new Timestamp(System.currentTimeMillis()));
    } catch (IOException e) {
      Main.warn("Unable to transfer zone " + this.secondaryZone.getZoneName() + " from server " + this.secondaryZone.getRemoteServerAddress() + ", " + e);
      checkExpired();
    } catch (ZoneTransferException e) {
      Main.warn("Unable to transfer zone " + this.secondaryZone.getZoneName() + " from server " + this.secondaryZone.getRemoteServerAddress() + ", " + e);
      checkExpired();
    } catch (RuntimeException e) {
      Main.warn("Unable to transfer zone " + this.secondaryZone.getZoneName() + " from server " + this.secondaryZone.getRemoteServerAddress() + ", " + e);
      checkExpired();
    } 
  }
  
  private void checkExpired() {
    if (this.secondaryZone.getDownloaded() != null && this.secondaryZone.getZoneCopy() != null && System.currentTimeMillis() - this.secondaryZone.getDownloaded().getTime() > this.secondaryZone.getZoneCopy().getSOA().getExpire() * 1000L) {
      Main.warn("AXFR copy of secondary zone " + this.secondaryZone.getZoneName() + " has expired, deleting zone data...");
      this.secondaryZone.setZoneCopy(null);
      this.secondaryZone.setDownloaded(null);
      this.zoneProvider.zoneUpdated(this.secondaryZone);
    } 
  }
}
