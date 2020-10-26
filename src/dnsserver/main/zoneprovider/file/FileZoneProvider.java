package dnsserver.main.zoneprovider.file;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.xbill.DNS.Name;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Zone;

import dnsserver.main.Main;
import dnsserver.main.zoneprovider.ZoneChangeCallback;
import dnsserver.main.zoneprovider.ZoneProvider;
import dnsserver.main.zones.SecondaryZone;

public class FileZoneProvider extends TimerTask implements ZoneProvider {

	private String zoneFileDirectory;

	private boolean autoReloadZones;

	private Integer pollingInterval;

	private Map<String, Long> lastFileList = new HashMap<String, Long>();

	private Timer watcher;

	private ZoneChangeCallback changeCallback;

	public FileZoneProvider(String zoneFileDirector) {
		this(zoneFileDirector, true, 10);
	}

	public FileZoneProvider(String zoneFileDirector, boolean autoReloadZones, Integer pollingInterval) {
		this.zoneFileDirectory = zoneFileDirector;
		this.autoReloadZones = autoReloadZones;
		this.pollingInterval = pollingInterval;

		if (this.autoReloadZones && this.pollingInterval != null) {
			this.watcher = new Timer(true);
			this.watcher.schedule(this, 5000L, (this.pollingInterval.intValue() * 1000));
		}
	}

	@Override
	public void run() {
		if (this.changeCallback != null && hasDirectoryChanged()) {
			this.changeCallback.zoneChanged();
		}
	}

	private void updateZoneFiles(File[] files) {
		this.lastFileList = new HashMap<String, Long>();
		byte b;
		int i;
		File[] arrayOfFile;
		for (i = (arrayOfFile = files).length, b = 0; b < i;) {
			File f = arrayOfFile[b];
			this.lastFileList.put(f.getName(), Long.valueOf(f.lastModified()));
			b++;
		}
	}

	private boolean hasDirectoryChanged() {
		File folder = new File(this.zoneFileDirectory);
		File[] files = folder.listFiles();
		if (files.length != this.lastFileList.size())
			return true;
		byte b;
		int i;
		File[] arrayOfFile1;
		for (i = (arrayOfFile1 = folder.listFiles()).length, b = 0; b < i;) {
			File f = arrayOfFile1[b];
			if (!this.lastFileList.containsKey(f.getName()))
				return true;
			if (f.lastModified() > ((Long) this.lastFileList.get(f.getName())).longValue())
				return true;
			b++;
		}
		return false;
	}

	@Override
	public Collection<Zone> getPrimaryZones() {
		File zoneDir = new File(this.zoneFileDirectory);

		if (!zoneDir.exists() || !zoneDir.isDirectory()) {
			Main.warn("Zone file directory for FileZoneProvider does not exist!");
			return null;
		}

		if (!zoneDir.canRead()) {
			Main.warn("Zone file directory for FileZoneProvider is not readable");
			return null;
		}

		File[] files = zoneDir.listFiles();
		updateZoneFiles(files);

		if (files == null || files.length == 0) {
			Main.warn("No zone files found for FileZoneProvider in directory " + zoneDir.getPath());
			return null;
		}
		ArrayList<Zone> zones = new ArrayList<Zone>(files.length);

		for (File zoneFile : files) {
			if (!zoneFile.canRead()) {
				Main.warn("FileZoneProvider unable to access zone file " + zoneFile.getName());
			} else {
				Name origin;
				try {
					origin = Name.fromString(zoneFile.getName(), Name.root);
					Zone zone = new Zone(origin, zoneFile.getPath());
					zones.add(zone);
				} catch (TextParseException e) {
					Main.warn("FileZoneProvider unable to parse zone file " + zoneFile.getName(), (Throwable) e);
				} catch (IOException e) {
					Main.warn("Unable to parse zone file " + zoneFile + " in FileZoneProvider", e);
				}

			}
		}

		if (!zones.isEmpty())
			return zones;
		return null;
	}

	@Override
	public Collection<SecondaryZone> getSecondaryZones() {return null;}

	@Override
	public void zoneUpdated(SecondaryZone paramSecondaryZone) {}

	@Override
	public void zoneChecked(SecondaryZone paramSecondaryZone) {}
}
