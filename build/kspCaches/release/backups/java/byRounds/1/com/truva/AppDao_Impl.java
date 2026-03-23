package com.truva;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomDatabaseKt;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDao_Impl implements AppDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<AppEntity> __insertionAdapterOfAppEntity;

  private final EntityInsertionAdapter<ProxyEntity> __insertionAdapterOfProxyEntity;

  private final EntityInsertionAdapter<SettingsEntity> __insertionAdapterOfSettingsEntity;

  private final EntityInsertionAdapter<RegionProfileEntity> __insertionAdapterOfRegionProfileEntity;

  private final EntityDeletionOrUpdateAdapter<AppEntity> __deletionAdapterOfAppEntity;

  private final EntityDeletionOrUpdateAdapter<ProxyEntity> __deletionAdapterOfProxyEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeselectAllProxies;

  private final SharedSQLiteStatement __preparedStmtOfSelectProxy;

  private final SharedSQLiteStatement __preparedStmtOfDeselectAllRegionProfiles;

  private final SharedSQLiteStatement __preparedStmtOfSelectRegionProfile;

  public AppDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfAppEntity = new EntityInsertionAdapter<AppEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `selected_apps` (`packageName`,`label`,`isSystemApp`,`isActive`) VALUES (?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AppEntity entity) {
        statement.bindString(1, entity.getPackageName());
        statement.bindString(2, entity.getLabel());
        final int _tmp = entity.isSystemApp() ? 1 : 0;
        statement.bindLong(3, _tmp);
        final int _tmp_1 = entity.isActive() ? 1 : 0;
        statement.bindLong(4, _tmp_1);
      }
    };
    this.__insertionAdapterOfProxyEntity = new EntityInsertionAdapter<ProxyEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `proxies` (`id`,`name`,`region`,`ip`,`port`,`uuid`,`publicKey`,`shortId`,`sni`,`password`,`flow`,`security`,`network`,`fingerprint`,`path`,`isSelected`,`latency`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ProxyEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getRegion());
        statement.bindString(4, entity.getIp());
        statement.bindLong(5, entity.getPort());
        statement.bindString(6, entity.getUuid());
        statement.bindString(7, entity.getPublicKey());
        statement.bindString(8, entity.getShortId());
        statement.bindString(9, entity.getSni());
        statement.bindString(10, entity.getPassword());
        statement.bindString(11, entity.getFlow());
        statement.bindString(12, entity.getSecurity());
        statement.bindString(13, entity.getNetwork());
        statement.bindString(14, entity.getFingerprint());
        statement.bindString(15, entity.getPath());
        final int _tmp = entity.isSelected() ? 1 : 0;
        statement.bindLong(16, _tmp);
        if (entity.getLatency() == null) {
          statement.bindNull(17);
        } else {
          statement.bindLong(17, entity.getLatency());
        }
      }
    };
    this.__insertionAdapterOfSettingsEntity = new EntityInsertionAdapter<SettingsEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `settings` (`id`,`isKillSwitchEnabled`,`isGamingModeEnabled`,`isVideoOptimizationEnabled`,`isSpoofingEnabled`,`activeRegionProfileId`,`isSimSpoofEnabled`,`isGpsSpoofEnabled`,`isTimezoneSpoofEnabled`,`isLocaleSpoofEnabled`,`isDeviceIdSpoofEnabled`,`isSandboxEnabled`,`isAntiDetectionEnabled`,`isAutoSyncRegion`,`routingMode`,`isSmartRoutingEnabled`,`isUdpDirectBypass`,`adbConnectionPort`,`isSimMasked`,`sessionExpiryTime`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SettingsEntity entity) {
        statement.bindLong(1, entity.getId());
        final int _tmp = entity.isKillSwitchEnabled() ? 1 : 0;
        statement.bindLong(2, _tmp);
        final int _tmp_1 = entity.isGamingModeEnabled() ? 1 : 0;
        statement.bindLong(3, _tmp_1);
        final int _tmp_2 = entity.isVideoOptimizationEnabled() ? 1 : 0;
        statement.bindLong(4, _tmp_2);
        final int _tmp_3 = entity.isSpoofingEnabled() ? 1 : 0;
        statement.bindLong(5, _tmp_3);
        if (entity.getActiveRegionProfileId() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getActiveRegionProfileId());
        }
        final int _tmp_4 = entity.isSimSpoofEnabled() ? 1 : 0;
        statement.bindLong(7, _tmp_4);
        final int _tmp_5 = entity.isGpsSpoofEnabled() ? 1 : 0;
        statement.bindLong(8, _tmp_5);
        final int _tmp_6 = entity.isTimezoneSpoofEnabled() ? 1 : 0;
        statement.bindLong(9, _tmp_6);
        final int _tmp_7 = entity.isLocaleSpoofEnabled() ? 1 : 0;
        statement.bindLong(10, _tmp_7);
        final int _tmp_8 = entity.isDeviceIdSpoofEnabled() ? 1 : 0;
        statement.bindLong(11, _tmp_8);
        final int _tmp_9 = entity.isSandboxEnabled() ? 1 : 0;
        statement.bindLong(12, _tmp_9);
        final int _tmp_10 = entity.isAntiDetectionEnabled() ? 1 : 0;
        statement.bindLong(13, _tmp_10);
        final int _tmp_11 = entity.isAutoSyncRegion() ? 1 : 0;
        statement.bindLong(14, _tmp_11);
        statement.bindString(15, entity.getRoutingMode());
        final int _tmp_12 = entity.isSmartRoutingEnabled() ? 1 : 0;
        statement.bindLong(16, _tmp_12);
        final int _tmp_13 = entity.isUdpDirectBypass() ? 1 : 0;
        statement.bindLong(17, _tmp_13);
        statement.bindLong(18, entity.getAdbConnectionPort());
        final int _tmp_14 = entity.isSimMasked() ? 1 : 0;
        statement.bindLong(19, _tmp_14);
        statement.bindLong(20, entity.getSessionExpiryTime());
      }
    };
    this.__insertionAdapterOfRegionProfileEntity = new EntityInsertionAdapter<RegionProfileEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `region_profiles` (`profileId`,`displayName`,`isSelected`,`lastUsedAt`) VALUES (?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final RegionProfileEntity entity) {
        statement.bindString(1, entity.getProfileId());
        statement.bindString(2, entity.getDisplayName());
        final int _tmp = entity.isSelected() ? 1 : 0;
        statement.bindLong(3, _tmp);
        statement.bindLong(4, entity.getLastUsedAt());
      }
    };
    this.__deletionAdapterOfAppEntity = new EntityDeletionOrUpdateAdapter<AppEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `selected_apps` WHERE `packageName` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AppEntity entity) {
        statement.bindString(1, entity.getPackageName());
      }
    };
    this.__deletionAdapterOfProxyEntity = new EntityDeletionOrUpdateAdapter<ProxyEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `proxies` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ProxyEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__preparedStmtOfDeselectAllProxies = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE proxies SET isSelected = 0";
        return _query;
      }
    };
    this.__preparedStmtOfSelectProxy = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE proxies SET isSelected = 1 WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeselectAllRegionProfiles = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE region_profiles SET isSelected = 0";
        return _query;
      }
    };
    this.__preparedStmtOfSelectRegionProfile = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE region_profiles SET isSelected = 1, lastUsedAt = ? WHERE profileId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertApp(final AppEntity app, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfAppEntity.insert(app);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertProxy(final ProxyEntity proxy, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfProxyEntity.insert(proxy);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateSettings(final SettingsEntity settings,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfSettingsEntity.insert(settings);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertRegionProfile(final RegionProfileEntity profile,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfRegionProfileEntity.insert(profile);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteApp(final AppEntity app, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfAppEntity.handle(app);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteProxy(final ProxyEntity proxy, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfProxyEntity.handle(proxy);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object setActiveProxy(final int proxyId, final Continuation<? super Unit> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> AppDao.DefaultImpls.setActiveProxy(AppDao_Impl.this, proxyId, __cont), $completion);
  }

  @Override
  public Object setActiveRegionProfile(final String profileId,
      final Continuation<? super Unit> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> AppDao.DefaultImpls.setActiveRegionProfile(AppDao_Impl.this, profileId, __cont), $completion);
  }

  @Override
  public Object deselectAllProxies(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeselectAllProxies.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeselectAllProxies.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object selectProxy(final int proxyId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSelectProxy.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, proxyId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSelectProxy.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deselectAllRegionProfiles(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeselectAllRegionProfiles.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeselectAllRegionProfiles.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object selectRegionProfile(final String profileId, final long timestamp,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSelectRegionProfile.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, timestamp);
        _argIndex = 2;
        _stmt.bindString(_argIndex, profileId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSelectRegionProfile.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getAllApps(final Continuation<? super List<AppEntity>> $completion) {
    final String _sql = "SELECT * FROM selected_apps";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<AppEntity>>() {
      @Override
      @NonNull
      public List<AppEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfIsSystemApp = CursorUtil.getColumnIndexOrThrow(_cursor, "isSystemApp");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final List<AppEntity> _result = new ArrayList<AppEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AppEntity _item;
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final boolean _tmpIsSystemApp;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSystemApp);
            _tmpIsSystemApp = _tmp != 0;
            final boolean _tmpIsActive;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_1 != 0;
            _item = new AppEntity(_tmpPackageName,_tmpLabel,_tmpIsSystemApp,_tmpIsActive);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<AppEntity>> getAllAppsFlow() {
    final String _sql = "SELECT * FROM selected_apps";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"selected_apps"}, new Callable<List<AppEntity>>() {
      @Override
      @NonNull
      public List<AppEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfIsSystemApp = CursorUtil.getColumnIndexOrThrow(_cursor, "isSystemApp");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final List<AppEntity> _result = new ArrayList<AppEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AppEntity _item;
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final boolean _tmpIsSystemApp;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSystemApp);
            _tmpIsSystemApp = _tmp != 0;
            final boolean _tmpIsActive;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_1 != 0;
            _item = new AppEntity(_tmpPackageName,_tmpLabel,_tmpIsSystemApp,_tmpIsActive);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getActiveApps(final Continuation<? super List<AppEntity>> $completion) {
    final String _sql = "SELECT * FROM selected_apps WHERE isActive = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<AppEntity>>() {
      @Override
      @NonNull
      public List<AppEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfIsSystemApp = CursorUtil.getColumnIndexOrThrow(_cursor, "isSystemApp");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final List<AppEntity> _result = new ArrayList<AppEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AppEntity _item;
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final boolean _tmpIsSystemApp;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSystemApp);
            _tmpIsSystemApp = _tmp != 0;
            final boolean _tmpIsActive;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_1 != 0;
            _item = new AppEntity(_tmpPackageName,_tmpLabel,_tmpIsSystemApp,_tmpIsActive);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<AppEntity>> getActiveAppsFlow() {
    final String _sql = "SELECT * FROM selected_apps WHERE isActive = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"selected_apps"}, new Callable<List<AppEntity>>() {
      @Override
      @NonNull
      public List<AppEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfIsSystemApp = CursorUtil.getColumnIndexOrThrow(_cursor, "isSystemApp");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final List<AppEntity> _result = new ArrayList<AppEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AppEntity _item;
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final boolean _tmpIsSystemApp;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSystemApp);
            _tmpIsSystemApp = _tmp != 0;
            final boolean _tmpIsActive;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_1 != 0;
            _item = new AppEntity(_tmpPackageName,_tmpLabel,_tmpIsSystemApp,_tmpIsActive);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<ProxyEntity>> getAllProxies() {
    final String _sql = "SELECT * FROM proxies";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"proxies"}, new Callable<List<ProxyEntity>>() {
      @Override
      @NonNull
      public List<ProxyEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfRegion = CursorUtil.getColumnIndexOrThrow(_cursor, "region");
          final int _cursorIndexOfIp = CursorUtil.getColumnIndexOrThrow(_cursor, "ip");
          final int _cursorIndexOfPort = CursorUtil.getColumnIndexOrThrow(_cursor, "port");
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfPublicKey = CursorUtil.getColumnIndexOrThrow(_cursor, "publicKey");
          final int _cursorIndexOfShortId = CursorUtil.getColumnIndexOrThrow(_cursor, "shortId");
          final int _cursorIndexOfSni = CursorUtil.getColumnIndexOrThrow(_cursor, "sni");
          final int _cursorIndexOfPassword = CursorUtil.getColumnIndexOrThrow(_cursor, "password");
          final int _cursorIndexOfFlow = CursorUtil.getColumnIndexOrThrow(_cursor, "flow");
          final int _cursorIndexOfSecurity = CursorUtil.getColumnIndexOrThrow(_cursor, "security");
          final int _cursorIndexOfNetwork = CursorUtil.getColumnIndexOrThrow(_cursor, "network");
          final int _cursorIndexOfFingerprint = CursorUtil.getColumnIndexOrThrow(_cursor, "fingerprint");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfIsSelected = CursorUtil.getColumnIndexOrThrow(_cursor, "isSelected");
          final int _cursorIndexOfLatency = CursorUtil.getColumnIndexOrThrow(_cursor, "latency");
          final List<ProxyEntity> _result = new ArrayList<ProxyEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ProxyEntity _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpRegion;
            _tmpRegion = _cursor.getString(_cursorIndexOfRegion);
            final String _tmpIp;
            _tmpIp = _cursor.getString(_cursorIndexOfIp);
            final int _tmpPort;
            _tmpPort = _cursor.getInt(_cursorIndexOfPort);
            final String _tmpUuid;
            _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            final String _tmpPublicKey;
            _tmpPublicKey = _cursor.getString(_cursorIndexOfPublicKey);
            final String _tmpShortId;
            _tmpShortId = _cursor.getString(_cursorIndexOfShortId);
            final String _tmpSni;
            _tmpSni = _cursor.getString(_cursorIndexOfSni);
            final String _tmpPassword;
            _tmpPassword = _cursor.getString(_cursorIndexOfPassword);
            final String _tmpFlow;
            _tmpFlow = _cursor.getString(_cursorIndexOfFlow);
            final String _tmpSecurity;
            _tmpSecurity = _cursor.getString(_cursorIndexOfSecurity);
            final String _tmpNetwork;
            _tmpNetwork = _cursor.getString(_cursorIndexOfNetwork);
            final String _tmpFingerprint;
            _tmpFingerprint = _cursor.getString(_cursorIndexOfFingerprint);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final boolean _tmpIsSelected;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSelected);
            _tmpIsSelected = _tmp != 0;
            final Long _tmpLatency;
            if (_cursor.isNull(_cursorIndexOfLatency)) {
              _tmpLatency = null;
            } else {
              _tmpLatency = _cursor.getLong(_cursorIndexOfLatency);
            }
            _item = new ProxyEntity(_tmpId,_tmpName,_tmpRegion,_tmpIp,_tmpPort,_tmpUuid,_tmpPublicKey,_tmpShortId,_tmpSni,_tmpPassword,_tmpFlow,_tmpSecurity,_tmpNetwork,_tmpFingerprint,_tmpPath,_tmpIsSelected,_tmpLatency);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getAllProxiesList(final Continuation<? super List<ProxyEntity>> $completion) {
    final String _sql = "SELECT * FROM proxies";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ProxyEntity>>() {
      @Override
      @NonNull
      public List<ProxyEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfRegion = CursorUtil.getColumnIndexOrThrow(_cursor, "region");
          final int _cursorIndexOfIp = CursorUtil.getColumnIndexOrThrow(_cursor, "ip");
          final int _cursorIndexOfPort = CursorUtil.getColumnIndexOrThrow(_cursor, "port");
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfPublicKey = CursorUtil.getColumnIndexOrThrow(_cursor, "publicKey");
          final int _cursorIndexOfShortId = CursorUtil.getColumnIndexOrThrow(_cursor, "shortId");
          final int _cursorIndexOfSni = CursorUtil.getColumnIndexOrThrow(_cursor, "sni");
          final int _cursorIndexOfPassword = CursorUtil.getColumnIndexOrThrow(_cursor, "password");
          final int _cursorIndexOfFlow = CursorUtil.getColumnIndexOrThrow(_cursor, "flow");
          final int _cursorIndexOfSecurity = CursorUtil.getColumnIndexOrThrow(_cursor, "security");
          final int _cursorIndexOfNetwork = CursorUtil.getColumnIndexOrThrow(_cursor, "network");
          final int _cursorIndexOfFingerprint = CursorUtil.getColumnIndexOrThrow(_cursor, "fingerprint");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfIsSelected = CursorUtil.getColumnIndexOrThrow(_cursor, "isSelected");
          final int _cursorIndexOfLatency = CursorUtil.getColumnIndexOrThrow(_cursor, "latency");
          final List<ProxyEntity> _result = new ArrayList<ProxyEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ProxyEntity _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpRegion;
            _tmpRegion = _cursor.getString(_cursorIndexOfRegion);
            final String _tmpIp;
            _tmpIp = _cursor.getString(_cursorIndexOfIp);
            final int _tmpPort;
            _tmpPort = _cursor.getInt(_cursorIndexOfPort);
            final String _tmpUuid;
            _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            final String _tmpPublicKey;
            _tmpPublicKey = _cursor.getString(_cursorIndexOfPublicKey);
            final String _tmpShortId;
            _tmpShortId = _cursor.getString(_cursorIndexOfShortId);
            final String _tmpSni;
            _tmpSni = _cursor.getString(_cursorIndexOfSni);
            final String _tmpPassword;
            _tmpPassword = _cursor.getString(_cursorIndexOfPassword);
            final String _tmpFlow;
            _tmpFlow = _cursor.getString(_cursorIndexOfFlow);
            final String _tmpSecurity;
            _tmpSecurity = _cursor.getString(_cursorIndexOfSecurity);
            final String _tmpNetwork;
            _tmpNetwork = _cursor.getString(_cursorIndexOfNetwork);
            final String _tmpFingerprint;
            _tmpFingerprint = _cursor.getString(_cursorIndexOfFingerprint);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final boolean _tmpIsSelected;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSelected);
            _tmpIsSelected = _tmp != 0;
            final Long _tmpLatency;
            if (_cursor.isNull(_cursorIndexOfLatency)) {
              _tmpLatency = null;
            } else {
              _tmpLatency = _cursor.getLong(_cursorIndexOfLatency);
            }
            _item = new ProxyEntity(_tmpId,_tmpName,_tmpRegion,_tmpIp,_tmpPort,_tmpUuid,_tmpPublicKey,_tmpShortId,_tmpSni,_tmpPassword,_tmpFlow,_tmpSecurity,_tmpNetwork,_tmpFingerprint,_tmpPath,_tmpIsSelected,_tmpLatency);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getSelectedProxy(final Continuation<? super ProxyEntity> $completion) {
    final String _sql = "SELECT * FROM proxies WHERE isSelected = 1 LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ProxyEntity>() {
      @Override
      @Nullable
      public ProxyEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfRegion = CursorUtil.getColumnIndexOrThrow(_cursor, "region");
          final int _cursorIndexOfIp = CursorUtil.getColumnIndexOrThrow(_cursor, "ip");
          final int _cursorIndexOfPort = CursorUtil.getColumnIndexOrThrow(_cursor, "port");
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfPublicKey = CursorUtil.getColumnIndexOrThrow(_cursor, "publicKey");
          final int _cursorIndexOfShortId = CursorUtil.getColumnIndexOrThrow(_cursor, "shortId");
          final int _cursorIndexOfSni = CursorUtil.getColumnIndexOrThrow(_cursor, "sni");
          final int _cursorIndexOfPassword = CursorUtil.getColumnIndexOrThrow(_cursor, "password");
          final int _cursorIndexOfFlow = CursorUtil.getColumnIndexOrThrow(_cursor, "flow");
          final int _cursorIndexOfSecurity = CursorUtil.getColumnIndexOrThrow(_cursor, "security");
          final int _cursorIndexOfNetwork = CursorUtil.getColumnIndexOrThrow(_cursor, "network");
          final int _cursorIndexOfFingerprint = CursorUtil.getColumnIndexOrThrow(_cursor, "fingerprint");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfIsSelected = CursorUtil.getColumnIndexOrThrow(_cursor, "isSelected");
          final int _cursorIndexOfLatency = CursorUtil.getColumnIndexOrThrow(_cursor, "latency");
          final ProxyEntity _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpRegion;
            _tmpRegion = _cursor.getString(_cursorIndexOfRegion);
            final String _tmpIp;
            _tmpIp = _cursor.getString(_cursorIndexOfIp);
            final int _tmpPort;
            _tmpPort = _cursor.getInt(_cursorIndexOfPort);
            final String _tmpUuid;
            _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            final String _tmpPublicKey;
            _tmpPublicKey = _cursor.getString(_cursorIndexOfPublicKey);
            final String _tmpShortId;
            _tmpShortId = _cursor.getString(_cursorIndexOfShortId);
            final String _tmpSni;
            _tmpSni = _cursor.getString(_cursorIndexOfSni);
            final String _tmpPassword;
            _tmpPassword = _cursor.getString(_cursorIndexOfPassword);
            final String _tmpFlow;
            _tmpFlow = _cursor.getString(_cursorIndexOfFlow);
            final String _tmpSecurity;
            _tmpSecurity = _cursor.getString(_cursorIndexOfSecurity);
            final String _tmpNetwork;
            _tmpNetwork = _cursor.getString(_cursorIndexOfNetwork);
            final String _tmpFingerprint;
            _tmpFingerprint = _cursor.getString(_cursorIndexOfFingerprint);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final boolean _tmpIsSelected;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSelected);
            _tmpIsSelected = _tmp != 0;
            final Long _tmpLatency;
            if (_cursor.isNull(_cursorIndexOfLatency)) {
              _tmpLatency = null;
            } else {
              _tmpLatency = _cursor.getLong(_cursorIndexOfLatency);
            }
            _result = new ProxyEntity(_tmpId,_tmpName,_tmpRegion,_tmpIp,_tmpPort,_tmpUuid,_tmpPublicKey,_tmpShortId,_tmpSni,_tmpPassword,_tmpFlow,_tmpSecurity,_tmpNetwork,_tmpFingerprint,_tmpPath,_tmpIsSelected,_tmpLatency);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getActiveAppCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM selected_apps WHERE isActive = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<SettingsEntity> getSettingsFlow() {
    final String _sql = "SELECT * FROM settings WHERE id = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"settings"}, new Callable<SettingsEntity>() {
      @Override
      @Nullable
      public SettingsEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfIsKillSwitchEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isKillSwitchEnabled");
          final int _cursorIndexOfIsGamingModeEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isGamingModeEnabled");
          final int _cursorIndexOfIsVideoOptimizationEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isVideoOptimizationEnabled");
          final int _cursorIndexOfIsSpoofingEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isSpoofingEnabled");
          final int _cursorIndexOfActiveRegionProfileId = CursorUtil.getColumnIndexOrThrow(_cursor, "activeRegionProfileId");
          final int _cursorIndexOfIsSimSpoofEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isSimSpoofEnabled");
          final int _cursorIndexOfIsGpsSpoofEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isGpsSpoofEnabled");
          final int _cursorIndexOfIsTimezoneSpoofEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isTimezoneSpoofEnabled");
          final int _cursorIndexOfIsLocaleSpoofEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isLocaleSpoofEnabled");
          final int _cursorIndexOfIsDeviceIdSpoofEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeviceIdSpoofEnabled");
          final int _cursorIndexOfIsSandboxEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isSandboxEnabled");
          final int _cursorIndexOfIsAntiDetectionEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isAntiDetectionEnabled");
          final int _cursorIndexOfIsAutoSyncRegion = CursorUtil.getColumnIndexOrThrow(_cursor, "isAutoSyncRegion");
          final int _cursorIndexOfRoutingMode = CursorUtil.getColumnIndexOrThrow(_cursor, "routingMode");
          final int _cursorIndexOfIsSmartRoutingEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isSmartRoutingEnabled");
          final int _cursorIndexOfIsUdpDirectBypass = CursorUtil.getColumnIndexOrThrow(_cursor, "isUdpDirectBypass");
          final int _cursorIndexOfAdbConnectionPort = CursorUtil.getColumnIndexOrThrow(_cursor, "adbConnectionPort");
          final int _cursorIndexOfIsSimMasked = CursorUtil.getColumnIndexOrThrow(_cursor, "isSimMasked");
          final int _cursorIndexOfSessionExpiryTime = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionExpiryTime");
          final SettingsEntity _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final boolean _tmpIsKillSwitchEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsKillSwitchEnabled);
            _tmpIsKillSwitchEnabled = _tmp != 0;
            final boolean _tmpIsGamingModeEnabled;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsGamingModeEnabled);
            _tmpIsGamingModeEnabled = _tmp_1 != 0;
            final boolean _tmpIsVideoOptimizationEnabled;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsVideoOptimizationEnabled);
            _tmpIsVideoOptimizationEnabled = _tmp_2 != 0;
            final boolean _tmpIsSpoofingEnabled;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsSpoofingEnabled);
            _tmpIsSpoofingEnabled = _tmp_3 != 0;
            final String _tmpActiveRegionProfileId;
            if (_cursor.isNull(_cursorIndexOfActiveRegionProfileId)) {
              _tmpActiveRegionProfileId = null;
            } else {
              _tmpActiveRegionProfileId = _cursor.getString(_cursorIndexOfActiveRegionProfileId);
            }
            final boolean _tmpIsSimSpoofEnabled;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsSimSpoofEnabled);
            _tmpIsSimSpoofEnabled = _tmp_4 != 0;
            final boolean _tmpIsGpsSpoofEnabled;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfIsGpsSpoofEnabled);
            _tmpIsGpsSpoofEnabled = _tmp_5 != 0;
            final boolean _tmpIsTimezoneSpoofEnabled;
            final int _tmp_6;
            _tmp_6 = _cursor.getInt(_cursorIndexOfIsTimezoneSpoofEnabled);
            _tmpIsTimezoneSpoofEnabled = _tmp_6 != 0;
            final boolean _tmpIsLocaleSpoofEnabled;
            final int _tmp_7;
            _tmp_7 = _cursor.getInt(_cursorIndexOfIsLocaleSpoofEnabled);
            _tmpIsLocaleSpoofEnabled = _tmp_7 != 0;
            final boolean _tmpIsDeviceIdSpoofEnabled;
            final int _tmp_8;
            _tmp_8 = _cursor.getInt(_cursorIndexOfIsDeviceIdSpoofEnabled);
            _tmpIsDeviceIdSpoofEnabled = _tmp_8 != 0;
            final boolean _tmpIsSandboxEnabled;
            final int _tmp_9;
            _tmp_9 = _cursor.getInt(_cursorIndexOfIsSandboxEnabled);
            _tmpIsSandboxEnabled = _tmp_9 != 0;
            final boolean _tmpIsAntiDetectionEnabled;
            final int _tmp_10;
            _tmp_10 = _cursor.getInt(_cursorIndexOfIsAntiDetectionEnabled);
            _tmpIsAntiDetectionEnabled = _tmp_10 != 0;
            final boolean _tmpIsAutoSyncRegion;
            final int _tmp_11;
            _tmp_11 = _cursor.getInt(_cursorIndexOfIsAutoSyncRegion);
            _tmpIsAutoSyncRegion = _tmp_11 != 0;
            final String _tmpRoutingMode;
            _tmpRoutingMode = _cursor.getString(_cursorIndexOfRoutingMode);
            final boolean _tmpIsSmartRoutingEnabled;
            final int _tmp_12;
            _tmp_12 = _cursor.getInt(_cursorIndexOfIsSmartRoutingEnabled);
            _tmpIsSmartRoutingEnabled = _tmp_12 != 0;
            final boolean _tmpIsUdpDirectBypass;
            final int _tmp_13;
            _tmp_13 = _cursor.getInt(_cursorIndexOfIsUdpDirectBypass);
            _tmpIsUdpDirectBypass = _tmp_13 != 0;
            final int _tmpAdbConnectionPort;
            _tmpAdbConnectionPort = _cursor.getInt(_cursorIndexOfAdbConnectionPort);
            final boolean _tmpIsSimMasked;
            final int _tmp_14;
            _tmp_14 = _cursor.getInt(_cursorIndexOfIsSimMasked);
            _tmpIsSimMasked = _tmp_14 != 0;
            final long _tmpSessionExpiryTime;
            _tmpSessionExpiryTime = _cursor.getLong(_cursorIndexOfSessionExpiryTime);
            _result = new SettingsEntity(_tmpId,_tmpIsKillSwitchEnabled,_tmpIsGamingModeEnabled,_tmpIsVideoOptimizationEnabled,_tmpIsSpoofingEnabled,_tmpActiveRegionProfileId,_tmpIsSimSpoofEnabled,_tmpIsGpsSpoofEnabled,_tmpIsTimezoneSpoofEnabled,_tmpIsLocaleSpoofEnabled,_tmpIsDeviceIdSpoofEnabled,_tmpIsSandboxEnabled,_tmpIsAntiDetectionEnabled,_tmpIsAutoSyncRegion,_tmpRoutingMode,_tmpIsSmartRoutingEnabled,_tmpIsUdpDirectBypass,_tmpAdbConnectionPort,_tmpIsSimMasked,_tmpSessionExpiryTime);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<RegionProfileEntity>> getRegionProfilesFlow() {
    final String _sql = "SELECT * FROM region_profiles ORDER BY lastUsedAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"region_profiles"}, new Callable<List<RegionProfileEntity>>() {
      @Override
      @NonNull
      public List<RegionProfileEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfProfileId = CursorUtil.getColumnIndexOrThrow(_cursor, "profileId");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfIsSelected = CursorUtil.getColumnIndexOrThrow(_cursor, "isSelected");
          final int _cursorIndexOfLastUsedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUsedAt");
          final List<RegionProfileEntity> _result = new ArrayList<RegionProfileEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final RegionProfileEntity _item;
            final String _tmpProfileId;
            _tmpProfileId = _cursor.getString(_cursorIndexOfProfileId);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final boolean _tmpIsSelected;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSelected);
            _tmpIsSelected = _tmp != 0;
            final long _tmpLastUsedAt;
            _tmpLastUsedAt = _cursor.getLong(_cursorIndexOfLastUsedAt);
            _item = new RegionProfileEntity(_tmpProfileId,_tmpDisplayName,_tmpIsSelected,_tmpLastUsedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getSelectedRegionProfile(
      final Continuation<? super RegionProfileEntity> $completion) {
    final String _sql = "SELECT * FROM region_profiles WHERE isSelected = 1 LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<RegionProfileEntity>() {
      @Override
      @Nullable
      public RegionProfileEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfProfileId = CursorUtil.getColumnIndexOrThrow(_cursor, "profileId");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfIsSelected = CursorUtil.getColumnIndexOrThrow(_cursor, "isSelected");
          final int _cursorIndexOfLastUsedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUsedAt");
          final RegionProfileEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpProfileId;
            _tmpProfileId = _cursor.getString(_cursorIndexOfProfileId);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final boolean _tmpIsSelected;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSelected);
            _tmpIsSelected = _tmp != 0;
            final long _tmpLastUsedAt;
            _tmpLastUsedAt = _cursor.getLong(_cursorIndexOfLastUsedAt);
            _result = new RegionProfileEntity(_tmpProfileId,_tmpDisplayName,_tmpIsSelected,_tmpLastUsedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
