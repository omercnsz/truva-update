package com.truva;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile AppDao _appDao;

  private volatile SimProtectionDao _simProtectionDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(12) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `selected_apps` (`packageName` TEXT NOT NULL, `label` TEXT NOT NULL, `isSystemApp` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`packageName`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `proxies` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `region` TEXT NOT NULL, `ip` TEXT NOT NULL, `port` INTEGER NOT NULL, `uuid` TEXT NOT NULL, `publicKey` TEXT NOT NULL, `shortId` TEXT NOT NULL, `sni` TEXT NOT NULL, `password` TEXT NOT NULL, `flow` TEXT NOT NULL, `security` TEXT NOT NULL, `network` TEXT NOT NULL, `fingerprint` TEXT NOT NULL, `path` TEXT NOT NULL, `isSelected` INTEGER NOT NULL, `latency` INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `isKillSwitchEnabled` INTEGER NOT NULL, `isGamingModeEnabled` INTEGER NOT NULL, `isVideoOptimizationEnabled` INTEGER NOT NULL, `isSpoofingEnabled` INTEGER NOT NULL, `activeRegionProfileId` TEXT, `isSimSpoofEnabled` INTEGER NOT NULL, `isGpsSpoofEnabled` INTEGER NOT NULL, `isTimezoneSpoofEnabled` INTEGER NOT NULL, `isLocaleSpoofEnabled` INTEGER NOT NULL, `isDeviceIdSpoofEnabled` INTEGER NOT NULL, `isSandboxEnabled` INTEGER NOT NULL, `isAntiDetectionEnabled` INTEGER NOT NULL, `isAutoSyncRegion` INTEGER NOT NULL, `routingMode` TEXT NOT NULL, `isSmartRoutingEnabled` INTEGER NOT NULL, `isUdpDirectBypass` INTEGER NOT NULL, `adbConnectionPort` INTEGER NOT NULL, `isSimMasked` INTEGER NOT NULL, `sessionExpiryTime` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `region_profiles` (`profileId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `isSelected` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `sim_protection` (`packageName` TEXT NOT NULL, `userId` INTEGER NOT NULL, `isProtected` INTEGER NOT NULL, PRIMARY KEY(`packageName`, `userId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '6c04c4ddeb459e24f6a7a5098677b9cb')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `selected_apps`");
        db.execSQL("DROP TABLE IF EXISTS `proxies`");
        db.execSQL("DROP TABLE IF EXISTS `settings`");
        db.execSQL("DROP TABLE IF EXISTS `region_profiles`");
        db.execSQL("DROP TABLE IF EXISTS `sim_protection`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsSelectedApps = new HashMap<String, TableInfo.Column>(4);
        _columnsSelectedApps.put("packageName", new TableInfo.Column("packageName", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSelectedApps.put("label", new TableInfo.Column("label", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSelectedApps.put("isSystemApp", new TableInfo.Column("isSystemApp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSelectedApps.put("isActive", new TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSelectedApps = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSelectedApps = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSelectedApps = new TableInfo("selected_apps", _columnsSelectedApps, _foreignKeysSelectedApps, _indicesSelectedApps);
        final TableInfo _existingSelectedApps = TableInfo.read(db, "selected_apps");
        if (!_infoSelectedApps.equals(_existingSelectedApps)) {
          return new RoomOpenHelper.ValidationResult(false, "selected_apps(com.truva.AppEntity).\n"
                  + " Expected:\n" + _infoSelectedApps + "\n"
                  + " Found:\n" + _existingSelectedApps);
        }
        final HashMap<String, TableInfo.Column> _columnsProxies = new HashMap<String, TableInfo.Column>(17);
        _columnsProxies.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxies.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxies.put("region", new TableInfo.Column("region", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxies.put("ip", new TableInfo.Column("ip", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxies.put("port", new TableInfo.Column("port", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxies.put("uuid", new TableInfo.Column("uuid", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxies.put("publicKey", new TableInfo.Column("publicKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxies.put("shortId", new TableInfo.Column("shortId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxies.put("sni", new TableInfo.Column("sni", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxies.put("password", new TableInfo.Column("password", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxies.put("flow", new TableInfo.Column("flow", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxies.put("security", new TableInfo.Column("security", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxies.put("network", new TableInfo.Column("network", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxies.put("fingerprint", new TableInfo.Column("fingerprint", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxies.put("path", new TableInfo.Column("path", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxies.put("isSelected", new TableInfo.Column("isSelected", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProxies.put("latency", new TableInfo.Column("latency", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysProxies = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesProxies = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoProxies = new TableInfo("proxies", _columnsProxies, _foreignKeysProxies, _indicesProxies);
        final TableInfo _existingProxies = TableInfo.read(db, "proxies");
        if (!_infoProxies.equals(_existingProxies)) {
          return new RoomOpenHelper.ValidationResult(false, "proxies(com.truva.ProxyEntity).\n"
                  + " Expected:\n" + _infoProxies + "\n"
                  + " Found:\n" + _existingProxies);
        }
        final HashMap<String, TableInfo.Column> _columnsSettings = new HashMap<String, TableInfo.Column>(20);
        _columnsSettings.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("isKillSwitchEnabled", new TableInfo.Column("isKillSwitchEnabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("isGamingModeEnabled", new TableInfo.Column("isGamingModeEnabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("isVideoOptimizationEnabled", new TableInfo.Column("isVideoOptimizationEnabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("isSpoofingEnabled", new TableInfo.Column("isSpoofingEnabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("activeRegionProfileId", new TableInfo.Column("activeRegionProfileId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("isSimSpoofEnabled", new TableInfo.Column("isSimSpoofEnabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("isGpsSpoofEnabled", new TableInfo.Column("isGpsSpoofEnabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("isTimezoneSpoofEnabled", new TableInfo.Column("isTimezoneSpoofEnabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("isLocaleSpoofEnabled", new TableInfo.Column("isLocaleSpoofEnabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("isDeviceIdSpoofEnabled", new TableInfo.Column("isDeviceIdSpoofEnabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("isSandboxEnabled", new TableInfo.Column("isSandboxEnabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("isAntiDetectionEnabled", new TableInfo.Column("isAntiDetectionEnabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("isAutoSyncRegion", new TableInfo.Column("isAutoSyncRegion", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("routingMode", new TableInfo.Column("routingMode", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("isSmartRoutingEnabled", new TableInfo.Column("isSmartRoutingEnabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("isUdpDirectBypass", new TableInfo.Column("isUdpDirectBypass", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("adbConnectionPort", new TableInfo.Column("adbConnectionPort", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("isSimMasked", new TableInfo.Column("isSimMasked", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("sessionExpiryTime", new TableInfo.Column("sessionExpiryTime", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSettings = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSettings = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSettings = new TableInfo("settings", _columnsSettings, _foreignKeysSettings, _indicesSettings);
        final TableInfo _existingSettings = TableInfo.read(db, "settings");
        if (!_infoSettings.equals(_existingSettings)) {
          return new RoomOpenHelper.ValidationResult(false, "settings(com.truva.SettingsEntity).\n"
                  + " Expected:\n" + _infoSettings + "\n"
                  + " Found:\n" + _existingSettings);
        }
        final HashMap<String, TableInfo.Column> _columnsRegionProfiles = new HashMap<String, TableInfo.Column>(4);
        _columnsRegionProfiles.put("profileId", new TableInfo.Column("profileId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRegionProfiles.put("displayName", new TableInfo.Column("displayName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRegionProfiles.put("isSelected", new TableInfo.Column("isSelected", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRegionProfiles.put("lastUsedAt", new TableInfo.Column("lastUsedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysRegionProfiles = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesRegionProfiles = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoRegionProfiles = new TableInfo("region_profiles", _columnsRegionProfiles, _foreignKeysRegionProfiles, _indicesRegionProfiles);
        final TableInfo _existingRegionProfiles = TableInfo.read(db, "region_profiles");
        if (!_infoRegionProfiles.equals(_existingRegionProfiles)) {
          return new RoomOpenHelper.ValidationResult(false, "region_profiles(com.truva.RegionProfileEntity).\n"
                  + " Expected:\n" + _infoRegionProfiles + "\n"
                  + " Found:\n" + _existingRegionProfiles);
        }
        final HashMap<String, TableInfo.Column> _columnsSimProtection = new HashMap<String, TableInfo.Column>(3);
        _columnsSimProtection.put("packageName", new TableInfo.Column("packageName", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSimProtection.put("userId", new TableInfo.Column("userId", "INTEGER", true, 2, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSimProtection.put("isProtected", new TableInfo.Column("isProtected", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSimProtection = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSimProtection = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSimProtection = new TableInfo("sim_protection", _columnsSimProtection, _foreignKeysSimProtection, _indicesSimProtection);
        final TableInfo _existingSimProtection = TableInfo.read(db, "sim_protection");
        if (!_infoSimProtection.equals(_existingSimProtection)) {
          return new RoomOpenHelper.ValidationResult(false, "sim_protection(com.truva.SimProtectionEntity).\n"
                  + " Expected:\n" + _infoSimProtection + "\n"
                  + " Found:\n" + _existingSimProtection);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "6c04c4ddeb459e24f6a7a5098677b9cb", "ecee081e67cd1a09fbc9516531648a76");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "selected_apps","proxies","settings","region_profiles","sim_protection");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `selected_apps`");
      _db.execSQL("DELETE FROM `proxies`");
      _db.execSQL("DELETE FROM `settings`");
      _db.execSQL("DELETE FROM `region_profiles`");
      _db.execSQL("DELETE FROM `sim_protection`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(AppDao.class, AppDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SimProtectionDao.class, SimProtectionDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public AppDao appDao() {
    if (_appDao != null) {
      return _appDao;
    } else {
      synchronized(this) {
        if(_appDao == null) {
          _appDao = new AppDao_Impl(this);
        }
        return _appDao;
      }
    }
  }

  @Override
  public SimProtectionDao simProtectionDao() {
    if (_simProtectionDao != null) {
      return _simProtectionDao;
    } else {
      synchronized(this) {
        if(_simProtectionDao == null) {
          _simProtectionDao = new SimProtectionDao_Impl(this);
        }
        return _simProtectionDao;
      }
    }
  }
}
