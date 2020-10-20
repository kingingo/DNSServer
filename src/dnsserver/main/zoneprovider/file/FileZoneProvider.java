package dnsserver.main.zoneprovider.file;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.xbill.DNS.Name;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Zone;

import dnsserver.main.Main;
import dnsserver.main.zoneprovider.ZoneProvider;
import dnsserver.main.zones.SecondaryZone;

public class FileZoneProvider implements ZoneProvider {

	private String zoneFileDirectory;

	public FileZoneProvider(String zoneFileDirectory) {
		this.zoneFileDirectory = zoneFileDirectory;
	}

	public Collection<Zone> getPrimaryZones() {
		File zoneDir = new File(this.zoneFileDirectory);
		if (!zoneDir.exists() || !zoneDir.isDirectory()) {
			Main.warn("Zone file directory specified for FileZoneProvider does not exist!");
			return null;
		}
		if (!zoneDir.canRead()) {
			Main.warn("Zone file directory specified for FileZoneProvider is not readable!");
			return null;
		}
		File[] files = zoneDir.listFiles();

		if (files == null || files.length == 0) {
			Main.warn("No zone files found for FileZoneProvider in directory " + zoneDir.getPath());
			return null;
		}
		ArrayList<Zone> zones = new ArrayList<Zone>(files.length);

		for (int i = 0; i < files.length; i++) {
			File zoneFile = files[i];
			if (!zoneFile.canRead()) {
				Main.warn("FileZoneProvider unable to access zone file " + zoneFile);
			} else {
				try {
					Zone zone = new Zone(Name.fromString(zoneFile.getName(),Name.root), zoneFile.getPath());
					Main.debug("FileZoneProvider successfully parsed zone file " + zoneFile.getName());
					zones.add(zone);
				} catch (TextParseException e) {
					Main.warn("FileZoneProvider unable to parse zone file " + zoneFile.getName(),
							(Throwable) e);
				} catch (IOException e) {
					Main.warn("Unable to parse zone file " + zoneFile + " in FileZoneProvider", e);
				}
			}
		}

		if (!zones.isEmpty())
		      return zones; 
		return null;
	}

	public Collection<SecondaryZone> getSecondaryZones() {
		return null;
	}

	public void zoneUpdated(SecondaryZone paramSecondaryZone) {
	}

	public void zoneChecked(SecondaryZone paramSecondaryZone) {
	}
}
