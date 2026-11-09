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
    super(c, "dose.db", null, 3);
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
            + " NULL,archived INTEGER NOT NULL DEFAULT 0,kind TEXT NOT NULL DEFAULT 'scheduled',"
            + "review TEXT NOT NULL DEFAULT '',icon TEXT NOT NULL DEFAULT 'capsule',"
            + "secondary_color INTEGER NOT NULL)");
    db.execSQL(
        "CREATE TABLE doses(id INTEGER PRIMARY KEY AUTOINCREMENT,med_id INTEGER NOT NULL REFERENCES"
            + " meds(id),local_key TEXT NOT NULL,scheduled INTEGER NOT NULL,expires INTEGER NOT"
            + " NULL,next_alert INTEGER NOT NULL DEFAULT 0,last_alert INTEGER NOT NULL DEFAULT"
            + " 0,alerts INTEGER NOT NULL DEFAULT 0,actual INTEGER NOT NULL DEFAULT 0,status TEXT"
            + " NOT NULL DEFAULT 'pending',snoozed INTEGER NOT NULL DEFAULT 0,name TEXT NOT"
            + " NULL,detail TEXT NOT NULL,notes TEXT NOT NULL,color INTEGER NOT NULL,amount REAL"
            + " NOT NULL,icon TEXT NOT NULL DEFAULT 'capsule',secondary_color INTEGER NOT NULL,"
            + "UNIQUE(med_id,local_key))");
    db.execSQL("CREATE INDEX dose_alert ON doses(status,next_alert)");
    db.execSQL("CREATE INDEX dose_schedule ON doses(scheduled)");
    db.execSQL("CREATE INDEX dose_med_schedule ON doses(med_id,scheduled)");
    createImportTables(db);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int old, int latest) {
    if (old < 2) {
      db.execSQL("ALTER TABLE meds ADD COLUMN kind TEXT NOT NULL DEFAULT 'scheduled'");
      db.execSQL("ALTER TABLE meds ADD COLUMN review TEXT NOT NULL DEFAULT ''");
      createImportTables(db);
    }
    if (old < 3) {
      for (String table : Arrays.asList("meds", "doses")) {
        db.execSQL("ALTER TABLE " + table + " ADD COLUMN icon TEXT NOT NULL DEFAULT 'capsule'");
        db.execSQL(
            "ALTER TABLE " + table + " ADD COLUMN secondary_color INTEGER NOT NULL DEFAULT 0");
        db.execSQL("UPDATE " + table + " SET secondary_color=color");
      }
    }
  }

  private void createImportTables(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE import_sources(id TEXT PRIMARY KEY,label TEXT NOT NULL,created INTEGER NOT"
            + " NULL,data TEXT NOT NULL)");
    db.execSQL(
        "CREATE TABLE import_links(source_id TEXT NOT NULL REFERENCES import_sources(id),item_key"
            + " TEXT NOT NULL,med_id INTEGER NOT NULL REFERENCES meds(id),PRIMARY"
            + " KEY(source_id,item_key))");
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
    m.icon = s(c, "icon");
    m.secondaryColor = (int) l(c, "secondary_color");
    m.stock = d(c, "stock");
    m.created = l(c, "created");
    m.archived = l(c, "archived") == 1;
    m.kind = s(c, "kind");
    m.review = s(c, "review");
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
    d.icon = s(c, "icon");
    d.secondaryColor = (int) l(c, "secondary_color");
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
    v.put("icon", m.icon);
    v.put("secondary_color", m.secondaryColor);
    v.put("stock", m.stock);
    v.put("created", m.created);
    v.put("archived", m.archived ? 1 : 0);
    v.put("kind", m.kind);
    v.put("review", m.review);
    return v;
  }

  public long save(Medication m, long now) {
    return save(m, now, Double.NaN);
  }

  /** Keep live stock if the editor left its original quantity unchanged. */
  public long save(Medication m, long now, double stockWhenOpened) {
    ScheduleRules.validate(m);
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      if (m.id != 0 && m.stock == stockWhenOpened) {
        Medication current = medication(m.id);
        if (current != null) m.stock = current.stock;
      }
      m.created = now; // Edits apply from now; never create retroactive alarms for new times.
      if (m.id == 0) {
        m.id = db.insertOrThrow("meds", null, values(m));
      } else {
        db.update("meds", values(m), "id=?", new String[] {"" + m.id});
        // Appearance follows pending reminders, including snoozed ones; resolved history is a
        // snapshot.
        db.execSQL(
            "UPDATE doses SET icon=?,color=?,secondary_color=? WHERE med_id=? AND status='pending'",
            new Object[] {m.icon, m.color, m.secondaryColor, m.id});
        // Rebuild only unalerted future occurrences; preserve actions and active reminders.
        db.delete(
            "doses",
            "med_id=? AND status='pending' AND scheduled>? AND last_alert=0",
            new String[] {"" + m.id, "" + now});
      }
      if (!m.enabled || m.asNeeded() || !m.review.isEmpty())
        db.execSQL(
            "UPDATE doses SET status='cancelled',next_alert=0 WHERE med_id=? AND status='pending'",
            new Object[] {m.id});
      db.setTransactionSuccessful();
      return m.id;
    } finally {
      db.endTransaction();
    }
  }

  public void setEnabled(long id, boolean enabled, boolean archive) {
    Medication current = medication(id);
    if (enabled && current != null && !current.review.isEmpty())
      throw new IllegalArgumentException(
          "Revise os dados importados antes de ativar este medicamento.");
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
          v.put("icon", m.icon);
          v.put("secondary_color", m.secondaryColor);
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
    root.put("version", 3);
    root.put("exported_at", System.currentTimeMillis());
    root.put("zone", ZoneId.systemDefault().getId());
    root.put("meds", tableJson("meds"));
    root.put("doses", tableJson("doses"));
    root.put("import_sources", tableJson("import_sources"));
    root.put("import_links", tableJson("import_links"));
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
    if (!"dose-backup".equals(root.optString("format"))
        || !Arrays.asList(1, 2, 3).contains(root.optInt("version")))
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
      m.enabled = j.getInt("enabled") == 1;
      m.kind = j.optString("kind", "scheduled");
      m.review = j.optString("review", "");
      m.color = j.getInt("color");
      m.icon = j.optString("icon", "capsule");
      m.secondaryColor = j.optInt("secondary_color", m.color);
      j.put("kind", m.kind);
      j.put("review", m.review);
      j.put("icon", m.icon);
      j.put("secondary_color", m.secondaryColor);
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
      String icon = j.optString("icon", "capsule");
      int secondary = j.optInt("secondary_color", j.getInt("color"));
      if (!MedicineStyle.valid(icon)
          || (j.getInt("color") >>> 24) != 255
          || (secondary >>> 24) != 255)
        throw new IllegalArgumentException("Aparência de dose inválida no backup.");
      j.put("icon", icon);
      j.put("secondary_color", secondary);
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
    JSONArray sources = root.optJSONArray("import_sources"),
        links = root.optJSONArray("import_links");
    if (sources == null) sources = new JSONArray();
    if (links == null) links = new JSONArray();
    if (sources.length() > 500 || links.length() > 100000)
      throw new IllegalArgumentException("Histórico de importação muito grande.");
    Set<String> sourceIds = new HashSet<>(), linkKeys = new HashSet<>();
    for (int i = 0; i < sources.length(); i++) {
      JSONObject j = sources.getJSONObject(i);
      MedicationImport parsed = new MedicationImport(j.getString("data"), LocalDate.now());
      if (!parsed.id.equals(j.getString("id")) || !sourceIds.add(parsed.id))
        throw new IllegalArgumentException("Relatório de origem inválido.");
    }
    for (int i = 0; i < links.length(); i++) {
      JSONObject j = links.getJSONObject(i);
      if (!sourceIds.contains(j.getString("source_id"))
          || !ids.contains(j.getLong("med_id"))
          || !j.getString("item_key").matches("[a-f0-9]{64}")
          || !linkKeys.add(j.getString("source_id") + j.getString("item_key")))
        throw new IllegalArgumentException("Vínculo de importação inválido.");
    }
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      db.delete("import_links", null, null);
      db.delete("import_sources", null, null);
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
            "archived",
            "kind",
            "review",
            "icon",
            "secondary_color"
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
            "amount",
            "icon",
            "secondary_color"
          });
      restoreTable(db, "import_sources", sources, new String[] {"id", "label", "created", "data"});
      restoreTable(db, "import_links", links, new String[] {"source_id", "item_key", "med_id"});
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

  private long equivalent(Medication incoming) {
    try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM meds", null)) {
      while (c.moveToNext()) {
        Medication m = med(c);
        if (m.name.trim().equalsIgnoreCase(incoming.name.trim())
            && m.strength.equalsIgnoreCase(incoming.strength)
            && m.unit.equalsIgnoreCase(incoming.unit)
            && m.amount == incoming.amount
            && m.times.equals(incoming.times)
            && m.days == incoming.days
            && m.kind.equals(incoming.kind)
            && m.end.equals(incoming.end)) return m.id;
      }
    }
    return 0;
  }

  public void previewImport(MedicationImport report) {
    for (MedicationImport.Item item : report.items) {
      try (Cursor c =
          getReadableDatabase()
              .rawQuery(
                  "SELECT med_id FROM import_links WHERE source_id=? AND item_key=?",
                  new String[] {report.id, item.key})) {
        item.existingId = c.moveToFirst() ? c.getLong(0) : equivalent(item.medication);
      }
      if (item.existingId == 0 && item.medication.enabled && !item.confirmed) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT name FROM meds", null)) {
          while (c.moveToNext())
            if (c.getString(0).equalsIgnoreCase(item.medication.name)) {
              item.medication.review =
                  "Já existe um cadastro com este nome e outros dados. Revise para evitar lembretes"
                      + " duplicados.";
              item.medication.enabled = false;
              break;
            }
        }
      }
    }
  }

  public int[] mergeImport(MedicationImport report, long now) throws JSONException {
    for (MedicationImport.Item item : report.items)
      if (item.selected) ScheduleRules.validate(item.medication);
    int added = 0, kept = 0;
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues source = new ContentValues();
      source.put("id", report.id);
      source.put("label", report.label);
      source.put("created", now);
      source.put("data", report.original.toString());
      db.insertWithOnConflict("import_sources", null, source, SQLiteDatabase.CONFLICT_IGNORE);
      for (MedicationImport.Item item : report.items) {
        if (!item.selected) continue;
        long id;
        try (Cursor c =
            db.rawQuery(
                "SELECT med_id FROM import_links WHERE source_id=? AND item_key=?",
                new String[] {report.id, item.key})) {
          id = c.moveToFirst() ? c.getLong(0) : equivalent(item.medication);
        }
        if (id == 0) {
          item.medication.created = now;
          id = db.insertOrThrow("meds", null, values(item.medication));
          added++;
        } else kept++;
        ContentValues link = new ContentValues();
        link.put("source_id", report.id);
        link.put("item_key", item.key);
        link.put("med_id", id);
        db.insertWithOnConflict("import_links", null, link, SQLiteDatabase.CONFLICT_IGNORE);
      }
      db.setTransactionSuccessful();
      return new int[] {added, kept};
    } finally {
      db.endTransaction();
    }
  }

  public List<String[]> importedSources() {
    List<String[]> rows = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT id,label,created FROM import_sources ORDER BY created DESC", null)) {
      while (c.moveToNext())
        rows.add(new String[] {c.getString(0), c.getString(1), c.getString(2)});
    }
    return rows;
  }

  public JSONObject importedSource(String id) throws JSONException {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT data FROM import_sources WHERE id=?", new String[] {id})) {
      return c.moveToFirst() ? new JSONObject(c.getString(0)) : null;
    }
  }
}
