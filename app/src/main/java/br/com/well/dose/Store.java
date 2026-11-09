package br.com.well.dose;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.time.*;
import java.util.*;
import org.json.*;

/** Device-private data. Dose snapshots keep the history meaningful after an edit. */
public final class Store extends SQLiteOpenHelper {
  private static Store instance;

  public static synchronized Store get(Context c) {
    if (instance == null) instance = new Store(c.getApplicationContext());
    return instance;
  }

  private Store(Context c) {
    super(c, "dose.db", null, 1);
  }

  @Override
  public void onConfigure(SQLiteDatabase db) {
    db.setForeignKeyConstraintsEnabled(true);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE meds(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,strength TEXT"
            + " NOT NULL,amount REAL NOT NULL,unit TEXT NOT NULL,notes TEXT NOT NULL,times TEXT NOT"
            + " NULL,days INTEGER NOT NULL,start TEXT NOT NULL,end TEXT NOT NULL,enabled INTEGER"
            + " NOT NULL,color INTEGER NOT NULL,stock REAL NOT NULL,created INTEGER NOT"
            + " NULL,archived INTEGER NOT NULL DEFAULT 0)");
    db.execSQL(
        "CREATE TABLE doses(id INTEGER PRIMARY KEY AUTOINCREMENT,med_id INTEGER NOT NULL REFERENCES"
            + " meds(id),local_key TEXT NOT NULL,scheduled INTEGER NOT NULL,expires INTEGER NOT"
            + " NULL,next_alert INTEGER NOT NULL DEFAULT 0,last_alert INTEGER NOT NULL DEFAULT"
            + " 0,alerts INTEGER NOT NULL DEFAULT 0,actual INTEGER NOT NULL DEFAULT 0,status TEXT"
            + " NOT NULL DEFAULT 'pending',snoozed INTEGER NOT NULL DEFAULT 0,name TEXT NOT"
            + " NULL,detail TEXT NOT NULL,notes TEXT NOT NULL,color INTEGER NOT NULL,amount REAL"
            + " NOT NULL,UNIQUE(med_id,local_key))");
    db.execSQL("CREATE INDEX dose_alert ON doses(status,next_alert)");
    db.execSQL("CREATE INDEX dose_schedule ON doses(scheduled)");
    db.execSQL("CREATE INDEX dose_med_schedule ON doses(med_id,scheduled)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int old, int latest) {
    throw new IllegalStateException("Unsupported database version");
  }

  private Medication med(Cursor c) {
    Medication m = new Medication();
    m.id = l(c, "id");
    m.name = s(c, "name");
    m.strength = s(c, "strength");
    m.amount = d(c, "amount");
    m.unit = s(c, "unit");
    m.notes = s(c, "notes");
    m.times = s(c, "times");
    m.days = (int) l(c, "days");
    m.start = s(c, "start");
    m.end = s(c, "end");
    m.enabled = l(c, "enabled") == 1;
    m.color = (int) l(c, "color");
    m.stock = d(c, "stock");
    m.created = l(c, "created");
    m.archived = l(c, "archived") == 1;
    return m;
  }

  private Dose dose(Cursor c) {
    Dose d = new Dose();
    d.id = l(c, "id");
    d.medId = l(c, "med_id");
    d.key = s(c, "local_key");
    d.scheduled = l(c, "scheduled");
    d.expires = l(c, "expires");
    d.nextAlert = l(c, "next_alert");
    d.lastAlert = l(c, "last_alert");
    d.alerts = (int) l(c, "alerts");
    d.actual = l(c, "actual");
    d.status = s(c, "status");
    d.snoozed = l(c, "snoozed") == 1;
    d.name = s(c, "name");
    d.detail = s(c, "detail");
    d.notes = s(c, "notes");
    d.color = (int) l(c, "color");
    d.amount = d(c, "amount");
    return d;
  }

  private static String s(Cursor c, String n) {
    return c.getString(c.getColumnIndexOrThrow(n));
  }

  private static long l(Cursor c, String n) {
    return c.getLong(c.getColumnIndexOrThrow(n));
  }

  private static double d(Cursor c, String n) {
    return c.getDouble(c.getColumnIndexOrThrow(n));
  }

  public List<Medication> medications() {
    List<Medication> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT * FROM meds WHERE archived=0 ORDER BY enabled DESC,name COLLATE NOCASE",
                null)) {
      while (c.moveToNext()) out.add(med(c));
    }
    return out;
  }

  public Medication medication(long id) {
    try (Cursor c =
        getReadableDatabase().rawQuery("SELECT * FROM meds WHERE id=?", new String[] {"" + id})) {
      return c.moveToFirst() ? med(c) : null;
    }
  }

  public Dose dose(long id) {
    try (Cursor c =
        getReadableDatabase().rawQuery("SELECT * FROM doses WHERE id=?", new String[] {"" + id})) {
      return c.moveToFirst() ? dose(c) : null;
    }
  }

  private ContentValues values(Medication m) {
    ContentValues v = new ContentValues();
    v.put("name", m.name.trim());
    v.put("strength", m.strength.trim());
    v.put("amount", m.amount);
    v.put("unit", m.unit);
    v.put("notes", m.notes.trim());
    v.put("times", m.times);
    v.put("days", m.days);
    v.put("start", m.start);
    v.put("end", m.end);
    v.put("enabled", m.enabled ? 1 : 0);
    v.put("color", m.color);
    v.put("stock", m.stock);
    v.put("created", m.created);
    v.put("archived", m.archived ? 1 : 0);
    return v;
  }

  public long save(Medication m, long now) {
    ScheduleRules.validate(m);
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      m.created = now; // Edits apply from now; never create retroactive alarms for new times.
      if (m.id == 0) {
        m.id = db.insertOrThrow("meds", null, values(m));
      } else {
        db.update("meds", values(m), "id=?", new String[] {"" + m.id});
        // Rebuild only unalerted future occurrences; preserve actions and active reminders.
        db.delete(
            "doses",
            "med_id=? AND status='pending' AND scheduled>? AND last_alert=0",
            new String[] {"" + m.id, "" + now});
      }
      db.setTransactionSuccessful();
      return m.id;
    } finally {
      db.endTransaction();
    }
  }

  public void setEnabled(long id, boolean enabled, boolean archive) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues v = new ContentValues();
      v.put("enabled", enabled ? 1 : 0);
      v.put("archived", archive ? 1 : 0);
      db.update("meds", v, "id=?", new String[] {"" + id});
      if (!enabled || archive)
        db.execSQL(
            "UPDATE doses SET status='cancelled',next_alert=0 WHERE med_id=? AND status='pending'",
            new Object[] {id});
      else
        db.delete(
            "doses",
            "med_id=? AND status='cancelled' AND scheduled>=?",
            new String[] {"" + id, "" + System.currentTimeMillis()});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public List<Dose> doses(String where, String[] args, String order) {
    List<Dose> out = new ArrayList<>();
    try (Cursor c = getReadableDatabase().query("doses", null, where, args, null, null, order)) {
      while (c.moveToNext()) out.add(dose(c));
    }
    return out;
  }

  public List<Dose> forDate(LocalDate date) {
    ZoneId z = ZoneId.systemDefault();
    long start = date.atStartOfDay(z).toInstant().toEpochMilli(),
        end = date.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli();
    return doses(
        "scheduled>=? AND scheduled<? AND status!='cancelled'",
        new String[] {"" + start, "" + end},
        "scheduled,id");
  }

  public List<Dose> history() {
    return doses(
        "status IN ('taken','skipped','missed')", null, "scheduled DESC,id DESC LIMIT 250");
  }

  public void maintain(long now) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      ZoneId zone = ZoneId.systemDefault();
      LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
      for (Medication m : medications()) {
        List<ScheduleRules.Occurrence> rows =
            ScheduleRules.occurrences(m, today.minusDays(1), 17, zone);
        for (int i = 0; i < rows.size(); i++) {
          ScheduleRules.Occurrence o = rows.get(i);
          long expiry =
              ScheduleRules.expiry(o.when, i + 1 < rows.size() ? rows.get(i + 1).when : 0);
          ContentValues v = new ContentValues();
          v.put("med_id", m.id);
          v.put("local_key", o.key);
          v.put("scheduled", o.when);
          v.put("expires", expiry);
          v.put("next_alert", o.when);
          v.put("name", m.name);
          v.put("detail", m.detail());
          v.put("notes", m.notes);
          v.put("color", m.color);
          v.put("amount", m.amount);
          db.insertWithOnConflict("doses", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        }
      }
      db.execSQL(
          "UPDATE doses SET expires=MIN(scheduled+?,COALESCE((SELECT MIN(b.scheduled) FROM doses b"
              + " WHERE b.med_id=doses.med_id AND b.scheduled>doses.scheduled AND"
              + " b.status!='cancelled'),scheduled+?)) WHERE status='pending'",
          new Object[] {ScheduleRules.WINDOW, ScheduleRules.WINDOW});
      db.execSQL(
          "UPDATE doses SET status='missed',next_alert=0 WHERE status='pending' AND expires<=?",
          new Object[] {now});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void rezone(long now) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      for (Dose d : doses("status='pending' AND last_alert=0", null, "scheduled")) {
        String[] p = d.key.split("@");
        long newTime =
            LocalDate.parse(p[0])
                .atTime(LocalTime.parse(p[1]))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        ContentValues v = new ContentValues();
        v.put("scheduled", newTime);
        v.put("expires", newTime + ScheduleRules.WINDOW);
        v.put("next_alert", newTime);
        db.update("doses", v, "id=?", new String[] {"" + d.id});
      }
      // Recalculate the expiry against the following dose after timezone changes.
      for (Medication m : medications()) {
        List<Dose> list =
            doses("med_id=? AND status='pending'", new String[] {"" + m.id}, "scheduled");
        for (int i = 0; i < list.size(); i++) {
          Dose d = list.get(i);
          ContentValues v = new ContentValues();
          v.put(
              "expires",
              ScheduleRules.expiry(
                  d.scheduled, i + 1 < list.size() ? list.get(i + 1).scheduled : 0));
          db.update("doses", v, "id=?", new String[] {"" + d.id});
        }
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public List<Dose> claimDue(Context c, long now) {
    SQLiteDatabase db = getWritableDatabase();
    List<Dose> out = new ArrayList<>();
    db.beginTransaction();
    try {
      for (Dose d :
          doses(
              "status='pending' AND next_alert>0 AND next_alert<=? AND expires>?",
              new String[] {"" + now, "" + now},
              "next_alert,id")) {
        d.alerts++;
        d.lastAlert = now;
        d.snoozed = false;
        d.nextAlert =
            ScheduleRules.retryAt(now, d.expires, d.alerts, Prefs.interval(c), Prefs.repeats(c));
        ContentValues v = new ContentValues();
        v.put("alerts", d.alerts);
        v.put("last_alert", now);
        v.put("next_alert", d.nextAlert);
        v.put("snoozed", 0);
        db.update("doses", v, "id=? AND status='pending'", new String[] {"" + d.id});
        out.add(d);
      }
      db.setTransactionSuccessful();
      return out;
    } finally {
      db.endTransaction();
    }
  }

  public long nextWake(long now) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT MIN(next_alert) FROM doses WHERE status='pending' AND next_alert>0 AND"
                    + " next_alert<expires",
                null)) {
      if (c.moveToFirst() && !c.isNull(0)) return Math.max(now + 1000, c.getLong(0));
    }
    return 0;
  }

  public long nextMaintenance(long now) {
    long result =
        Instant.ofEpochMilli(now)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            + 1500;
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT MIN(expires) FROM doses WHERE status='pending'", null)) {
      if (c.moveToFirst() && !c.isNull(0)) result = Math.min(result, c.getLong(0));
    }
    return Math.max(now + 1000, result);
  }

  public boolean resolve(long id, String status, long actual) {
    if (!"taken".equals(status) && !"skipped".equals(status))
      throw new IllegalArgumentException("Invalid dose action");
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      Dose d = dose(id);
      if (d == null || !(d.actionable() || "missed".equals(d.status))) return false;
      ContentValues v = new ContentValues();
      v.put("status", status);
      v.put("actual", actual);
      v.put("next_alert", 0);
      v.put("snoozed", 0);
      int changed =
          db.update("doses", v, "id=? AND status IN ('pending','missed')", new String[] {"" + id});
      if (changed == 1 && "taken".equals(status))
        db.execSQL(
            "UPDATE meds SET stock=MAX(0,stock-?) WHERE id=? AND stock>=0",
            new Object[] {d.amount, d.medId});
      db.setTransactionSuccessful();
      return changed == 1;
    } finally {
      db.endTransaction();
    }
  }

  public boolean snooze(long id, int minutes, long now) {
    Dose d = dose(id);
    if (d == null || !d.actionable()) return false;
    long next = ScheduleRules.snoozeAt(now, d.expires, minutes);
    ContentValues v = new ContentValues();
    v.put("next_alert", next);
    v.put("snoozed", 1);
    v.put("alerts", 0);
    return getWritableDatabase()
            .update("doses", v, "id=? AND status='pending'", new String[] {"" + id})
        == 1;
  }

  public void replenish(long id, double quantity) {
    if (!Double.isFinite(quantity) || quantity < 0)
      throw new IllegalArgumentException("Estoque inválido");
    ContentValues v = new ContentValues();
    v.put("stock", quantity);
    getWritableDatabase().update("meds", v, "id=?", new String[] {"" + id});
  }

  public JSONObject exportJson() throws JSONException {
    JSONObject root = new JSONObject();
    root.put("format", "dose-backup");
    root.put("version", 1);
    root.put("exported_at", System.currentTimeMillis());
    root.put("zone", ZoneId.systemDefault().getId());
    root.put("meds", tableJson("meds"));
    root.put("doses", tableJson("doses"));
    return root;
  }

  private JSONArray tableJson(String table) throws JSONException {
    JSONArray rows = new JSONArray();
    try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM " + table, null)) {
      while (c.moveToNext()) {
        JSONObject row = new JSONObject();
        for (int i = 0; i < c.getColumnCount(); i++) {
          switch (c.getType(i)) {
            case Cursor.FIELD_TYPE_INTEGER:
              row.put(c.getColumnName(i), c.getLong(i));
              break;
            case Cursor.FIELD_TYPE_FLOAT:
              row.put(c.getColumnName(i), c.getDouble(i));
              break;
            default:
              row.put(c.getColumnName(i), c.getString(i));
          }
        }
        rows.put(row);
      }
    }
    return rows;
  }

  public void importJson(JSONObject root) throws JSONException {
    if (!"dose-backup".equals(root.optString("format")) || root.optInt("version") != 1)
      throw new IllegalArgumentException("Arquivo de backup incompatível.");
    JSONArray meds = root.getJSONArray("meds"), doses = root.getJSONArray("doses");
    if (meds.length() > 500 || doses.length() > 100000)
      throw new IllegalArgumentException("Backup muito grande.");
    Set<Long> ids = new HashSet<>();
    for (int i = 0; i < meds.length(); i++) {
      JSONObject j = meds.getJSONObject(i);
      Medication m = new Medication();
      m.id = j.getLong("id");
      m.name = j.getString("name");
      m.strength = j.getString("strength");
      m.amount = j.getDouble("amount");
      m.unit = j.getString("unit");
      m.notes = j.getString("notes");
      m.times = j.getString("times");
      m.days = j.getInt("days");
      m.start = j.getString("start");
      m.end = j.getString("end");
      m.stock = j.getDouble("stock");
      ScheduleRules.validate(m);
      if (m.id < 1
          || m.id >= Alerts.SERVICE_ID
          || !ids.add(m.id)
          || j.getLong("created") < 0
          || !Arrays.asList(0, 1).contains(j.getInt("enabled"))
          || !Arrays.asList(0, 1).contains(j.getInt("archived")))
        throw new IllegalArgumentException("Identificação inválida no backup.");
    }
    Set<Long> doseIds = new HashSet<>();
    Set<String> doseKeys = new HashSet<>();
    for (int i = 0; i < doses.length(); i++) {
      JSONObject j = doses.getJSONObject(i);
      String status = j.getString("status");
      String key = j.getString("local_key");
      String[] p = key.split("@");
      if (p.length != 2) throw new IllegalArgumentException("Horário inválido no backup.");
      LocalDate.parse(p[0]);
      LocalTime.parse(p[1]);
      if (j.getLong("id") < 1
          || j.getLong("id") >= Alerts.SERVICE_ID
          || !ids.contains(j.getLong("med_id"))
          || !doseIds.add(j.getLong("id"))
          || !doseKeys.add(j.getLong("med_id") + ":" + key)
          || !Arrays.asList("pending", "taken", "skipped", "missed", "cancelled").contains(status)
          || j.getLong("scheduled") < 0
          || j.getLong("expires") < j.getLong("scheduled")
          || j.getInt("alerts") < 0
          || !Double.isFinite(j.getDouble("amount"))
          || j.getDouble("amount") <= 0
          || j.getLong("actual") < 0
          || j.getLong("next_alert") < 0
          || j.getLong("last_alert") < 0
          || !Arrays.asList(0, 1).contains(j.getInt("snoozed")))
        throw new IllegalArgumentException("Doses inválidas no backup.");
    }
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      db.delete("doses", null, null);
      db.delete("meds", null, null);
      restoreTable(
          db,
          "meds",
          meds,
          new String[] {
            "id",
            "name",
            "strength",
            "amount",
            "unit",
            "notes",
            "times",
            "days",
            "start",
            "end",
            "enabled",
            "color",
            "stock",
            "created",
            "archived"
          });
      restoreTable(
          db,
          "doses",
          doses,
          new String[] {
            "id",
            "med_id",
            "local_key",
            "scheduled",
            "expires",
            "next_alert",
            "last_alert",
            "alerts",
            "actual",
            "status",
            "snoozed",
            "name",
            "detail",
            "notes",
            "color",
            "amount"
          });
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  private void restoreTable(SQLiteDatabase db, String table, JSONArray rows, String[] columns)
      throws JSONException {
    for (int i = 0; i < rows.length(); i++) {
      JSONObject j = rows.getJSONObject(i);
      ContentValues v = new ContentValues();
      for (String k : columns) {
        Object value = j.get(k);
        if (value instanceof Number) {
          if (value instanceof Double) v.put(k, ((Number) value).doubleValue());
          else v.put(k, ((Number) value).longValue());
        } else v.put(k, value.toString());
      }
      db.insertOrThrow(table, null, v);
    }
  }
}
