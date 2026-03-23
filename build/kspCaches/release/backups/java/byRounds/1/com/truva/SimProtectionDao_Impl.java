package com.truva;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
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
public final class SimProtectionDao_Impl implements SimProtectionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SimProtectionEntity> __insertionAdapterOfSimProtectionEntity;

  private final EntityDeletionOrUpdateAdapter<SimProtectionEntity> __deletionAdapterOfSimProtectionEntity;

  public SimProtectionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSimProtectionEntity = new EntityInsertionAdapter<SimProtectionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `sim_protection` (`packageName`,`userId`,`isProtected`) VALUES (?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SimProtectionEntity entity) {
        statement.bindString(1, entity.getPackageName());
        statement.bindLong(2, entity.getUserId());
        final int _tmp = entity.isProtected() ? 1 : 0;
        statement.bindLong(3, _tmp);
      }
    };
    this.__deletionAdapterOfSimProtectionEntity = new EntityDeletionOrUpdateAdapter<SimProtectionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `sim_protection` WHERE `packageName` = ? AND `userId` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SimProtectionEntity entity) {
        statement.bindString(1, entity.getPackageName());
        statement.bindLong(2, entity.getUserId());
      }
    };
  }

  @Override
  public Object insertProtectedApp(final SimProtectionEntity app,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfSimProtectionEntity.insert(app);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteProtectedApp(final SimProtectionEntity app,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfSimProtectionEntity.handle(app);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<SimProtectionEntity>> getAllProtectedAppsFlow() {
    final String _sql = "SELECT * FROM sim_protection";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"sim_protection"}, new Callable<List<SimProtectionEntity>>() {
      @Override
      @NonNull
      public List<SimProtectionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfIsProtected = CursorUtil.getColumnIndexOrThrow(_cursor, "isProtected");
          final List<SimProtectionEntity> _result = new ArrayList<SimProtectionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SimProtectionEntity _item;
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final int _tmpUserId;
            _tmpUserId = _cursor.getInt(_cursorIndexOfUserId);
            final boolean _tmpIsProtected;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsProtected);
            _tmpIsProtected = _tmp != 0;
            _item = new SimProtectionEntity(_tmpPackageName,_tmpUserId,_tmpIsProtected);
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
  public Object getProtectedApp(final String packageName, final int userId,
      final Continuation<? super SimProtectionEntity> $completion) {
    final String _sql = "SELECT * FROM sim_protection WHERE packageName = ? AND userId = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, packageName);
    _argIndex = 2;
    _statement.bindLong(_argIndex, userId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<SimProtectionEntity>() {
      @Override
      @Nullable
      public SimProtectionEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfIsProtected = CursorUtil.getColumnIndexOrThrow(_cursor, "isProtected");
          final SimProtectionEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final int _tmpUserId;
            _tmpUserId = _cursor.getInt(_cursorIndexOfUserId);
            final boolean _tmpIsProtected;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsProtected);
            _tmpIsProtected = _tmp != 0;
            _result = new SimProtectionEntity(_tmpPackageName,_tmpUserId,_tmpIsProtected);
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
