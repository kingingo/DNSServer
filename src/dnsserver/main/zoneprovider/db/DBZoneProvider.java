package dnsserver.main.zoneprovider.db;

import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.sql.DataSource;
import org.xbill.DNS.Zone;

import dnsserver.main.Main;
import dnsserver.main.zoneprovider.ZoneProvider;
import dnsserver.main.zoneprovider.db.beans.DBRecord;
import dnsserver.main.zoneprovider.db.beans.DBSecondaryZone;
import dnsserver.main.zoneprovider.db.beans.DBZone;
import dnsserver.main.zones.SecondaryZone;
import se.unlogic.standardutils.dao.AnnotatedDAO;
import se.unlogic.standardutils.dao.AnnotatedDAOFactory;
import se.unlogic.standardutils.dao.HighLevelQuery;
import se.unlogic.standardutils.dao.QueryParameterFactory;
import se.unlogic.standardutils.dao.SimpleAnnotatedDAOFactory;
import se.unlogic.standardutils.dao.SimpleDataSource;
import se.unlogic.standardutils.dao.TransactionHandler;

public class DBZoneProvider implements ZoneProvider {
  
  private String driver;
  
  private String url;
  
  private String username;
  
  private String password;
  
  private AnnotatedDAO<DBZone> zoneDAO;
  
  private AnnotatedDAO<DBRecord> recordDAO;
  
  private HighLevelQuery<DBZone> primaryZoneQuery;
  
  private HighLevelQuery<DBZone> secondaryZoneQuery;
  
  private QueryParameterFactory<DBZone, Integer> zoneIDQueryParameterFactory;
  
  private QueryParameterFactory<DBRecord, DBZone> recordZoneQueryParameterFactory;

  public DBZoneProvider(String url, String username, String password) throws ClassNotFoundException{
	  this("com.mysql.jdbc.Driver",url,username, password);
  }
  
  public DBZoneProvider(String driver, String url, String username, String password) throws ClassNotFoundException{
	  this.driver = driver;
	  this.url = url;
	  this.username = username;
	  this.password = password;
	  
	  init();
  }  
  
  public void init() throws ClassNotFoundException {
    SimpleDataSource simpleDataSource;
    try {
      simpleDataSource = new SimpleDataSource(this.driver, this.url, this.username, this.password);
    } catch (ClassNotFoundException e) {
      Main.warn("Unable to load JDBC driver " + this.driver, e);
      throw e;
    } 
    SimpleAnnotatedDAOFactory annotatedDAOFactory = new SimpleAnnotatedDAOFactory();
    this.zoneDAO = new AnnotatedDAO((DataSource)simpleDataSource, DBZone.class, (AnnotatedDAOFactory)annotatedDAOFactory);
    this.recordDAO = new AnnotatedDAO((DataSource)simpleDataSource, DBRecord.class, (AnnotatedDAOFactory)annotatedDAOFactory);
    QueryParameterFactory<DBZone, Boolean> zoneTypeParamFactory = this.zoneDAO.getParamFactory("secondary", boolean.class);
    QueryParameterFactory<DBZone, Boolean> enabledParamFactory = this.zoneDAO.getParamFactory("enabled", boolean.class);
    this.primaryZoneQuery = new HighLevelQuery();
    this.primaryZoneQuery.addParameter(zoneTypeParamFactory.getParameter(Boolean.valueOf(false)));
    this.primaryZoneQuery.addParameter(enabledParamFactory.getParameter(Boolean.valueOf(true)));
    this.primaryZoneQuery.addRelation(DBZone.RECORDS_RELATION);
    this.secondaryZoneQuery = new HighLevelQuery();
    this.secondaryZoneQuery.addParameter(zoneTypeParamFactory.getParameter(Boolean.valueOf(true)));
    this.secondaryZoneQuery.addParameter(enabledParamFactory.getParameter(Boolean.valueOf(true)));
    this.secondaryZoneQuery.addRelation(DBZone.RECORDS_RELATION);
    this.zoneIDQueryParameterFactory = this.zoneDAO.getParamFactory("zoneID", Integer.class);
    this.recordZoneQueryParameterFactory = this.recordDAO.getParamFactory("zone", DBZone.class);
  }
  
  public Collection<Zone> getPrimaryZones() {
    try {
      List<DBZone> dbZones = this.zoneDAO.getAll(this.primaryZoneQuery);
      if (dbZones != null) {
        ArrayList<Zone> zones = new ArrayList<Zone>(dbZones.size());
        for (DBZone dbZone : dbZones) {
          try {
            zones.addAll(dbZone.toZones());
          } catch (IOException e) {
            Main.warn("Unable to parse zone " + dbZone.getName(), e);
          } 
        } 
        return zones;
      } 
    } catch (SQLException e) {
      Main.warn("Error getting primary zones from DB zone provider", e);
    } 
    return null;
  }
  
  public Collection<SecondaryZone> getSecondaryZones() {
    try {
      List<DBZone> dbZones = this.zoneDAO.getAll(this.secondaryZoneQuery);
      if (dbZones != null) {
        ArrayList<SecondaryZone> zones = new ArrayList<SecondaryZone>(dbZones.size());
        for (DBZone dbZone : dbZones) {
          try {
            DBSecondaryZone secondaryZone = new DBSecondaryZone(dbZone.getZoneID(), dbZone.getName(), dbZone.getPrimaryDNS(), dbZone.getDclass());
            if (dbZone.getRecords() != null) {
              secondaryZone.setZoneCopy(dbZone.toZone());
              secondaryZone.setDownloaded(dbZone.getDownloaded());
            } 
            zones.add(secondaryZone);
          } catch (IOException e) {
            Main.warn("Unable to parse zone " + dbZone.getName(), e);
          } 
        } 
        return zones;
      } 
    } catch (SQLException e) {
      Main.warn("Error getting secondary zones from DB zone provider", e);
    } 
    return null;
  }
  
  public void zoneAdd(SecondaryZone zone) {
	 
  }
  
  public void zoneUpdated(SecondaryZone zone) {
    if (!(zone instanceof DBSecondaryZone)) {
      Main.warn(zone.getClass() + " is not an instance of " + DBSecondaryZone.class + ", ignoring zone update");
      return;
    } 
    Integer zoneID = ((DBSecondaryZone)zone).getZoneID();
    TransactionHandler transactionHandler = null;
    try {
      transactionHandler = this.zoneDAO.createTransaction();
      DBZone dbZone = (DBZone)this.zoneDAO.get(new HighLevelQuery(this.zoneIDQueryParameterFactory.getParameter(zoneID), new Field[] { null }), transactionHandler);
      if (dbZone == null) {
        Main.warn("Unable to find secondary zone with zoneID " + zoneID + " in DB, ignoring zone update");
        return;
      } 
      if (!dbZone.isEnabled()) {
        Main.warn("Secondary zone with zone " + dbZone + " is disabled in DB ignoring AXFR update");
        return;
      } 
      dbZone.parse(zone.getZoneCopy(), true);
      this.zoneDAO.update(dbZone, transactionHandler, null);
      this.recordDAO.delete(new HighLevelQuery(this.recordZoneQueryParameterFactory.getParameter(dbZone), new Field[] { null }), transactionHandler);
      if (dbZone.getRecords() != null)
        for (DBRecord dbRecord : dbZone.getRecords()) {
          dbRecord.setZone(dbZone);
          this.recordDAO.add(dbRecord, transactionHandler, null);
        }  
      transactionHandler.commit();
      Main.debug("Changes in seconday zone " + dbZone + " saved");
    } catch (SQLException e) {
      Main.warn("Unable to save changes in secondary zone " + zone.getZoneName(), e);
      TransactionHandler.autoClose(transactionHandler);
    } 
  }
  
  public void zoneChecked(SecondaryZone zone) {
    if (!(zone instanceof DBSecondaryZone)) {
      Main.warn(zone.getClass() + " is not an instance of " + DBSecondaryZone.class + ", ignoring zone check");
      return;
    } 
    Integer zoneID = ((DBSecondaryZone)zone).getZoneID();
    TransactionHandler transactionHandler = null;
    try {
      transactionHandler = this.zoneDAO.createTransaction();
      DBZone dbZone = (DBZone)this.zoneDAO.get(new HighLevelQuery(this.zoneIDQueryParameterFactory.getParameter(zoneID), new Field[] { null }), transactionHandler);
      if (dbZone == null) {
        Main.warn("Unable to find secondary zone with zoneID " + zoneID + " in DB, ignoring zone check");
        return;
      } 
      if (!dbZone.isEnabled()) {
        Main.warn("Secondary zone with zone " + dbZone + " is disabled in DB ignoring zone check");
        return;
      } 
      dbZone.setDownloaded(new Timestamp(System.currentTimeMillis()));
      this.zoneDAO.update(dbZone, transactionHandler, null);
      transactionHandler.commit();
      Main.debug("Download timestamp of seconday zone " + dbZone + " updated");
    } catch (SQLException e) {
      Main.warn("Unable to update download of secondary zone " + zone.getZoneName(), e);
      TransactionHandler.autoClose(transactionHandler);
    } 
  }
  
  public void shutdown() {}
  
  public void setDriver(String driver) {
    this.driver = driver;
  }
  
  public void setUsername(String username) {
    this.username = username;
  }
  
  public void setPassword(String password) {
    this.password = password;
  }
  
  public void setUrl(String url) {
    this.url = url;
  }
}
