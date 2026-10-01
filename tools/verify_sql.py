"""Mirrors DbSession.kt SQL construction and runs it against real SQLite to prove correctness.

The Kotlin code builds SQL strings by hand (projection aliases, ORDER BY, LIMIT/OFFSET,
read-only wrapping, validation regex). This script reproduces that logic 1:1 and exercises it
against several schema shapes, so the generated statements are verified by an actual engine.
"""
import re
import sqlite3
import sys

FAILURES = []


def check(label, condition, detail=""):
    status = "PASS" if condition else "FAIL"
    print(f"  [{status}] {label}" + (f" -- {detail}" if detail and not condition else ""))
    if not condition:
        FAILURES.append(label)


# ---------------------------------------------------------------- Kotlin mirrors

def quote(s):
    return '"' + s.replace('"', '""') + '"'


def read_value(value):
    if value is None:
        return None
    if isinstance(value, float):
        if value == int(value) and abs(value) < 1e15:
            return str(int(value))
        return str(value)
    if isinstance(value, bytes):
        return f"<BLOB {len(value)} B>"
    if isinstance(value, int):
        return str(value)
    text = str(value)
    return text[:512] + "…" if len(text) > 512 else text


def has_row_id(conn, table):
    try:
        conn.execute(f"SELECT rowid FROM {quote(table)} LIMIT 1").fetchone()
        return True
    except sqlite3.Error:
        return False


def column_plan(conn, table):
    metas = []
    for row in conn.execute(f"PRAGMA table_xinfo({quote(table)})"):
        name, type_, pk, hidden = row[1], row[2] or "", row[5] or 0, row[6] or 0
        if name is None:
            continue
        metas.append({"name": name, "type": type_, "pk": pk, "hidden": hidden})
    metas = [m for m in metas if m["hidden"] == 0]
    identity = next((m["name"] for m in metas if m["pk"] == 1 and "INT" in m["type"].upper()), None)
    plans = []
    if identity is None and has_row_id(conn, table):
        plans.append({"name": "rowid", "expression": "rowid", "is_identity": True, "internal": True, "blob_length": False, "pk_order": 0})
    for meta in metas:
        blob = "BLOB" in meta["type"].upper()
        plans.append({
            "name": meta["name"],
            "expression": f"length({quote(meta['name'])})" if blob else quote(meta["name"]),
            "is_identity": meta["name"] == identity,
            "internal": False,
            "blob_length": blob,
            "pk_order": meta["pk"],
        })
    return plans


def order_clause(plans):
    identity = next((p for p in plans if p["is_identity"]), None)
    if identity is not None:
        return f" ORDER BY {identity['expression']}"
    keys = sorted([p for p in plans if p["pk_order"] > 0], key=lambda p: p["pk_order"])
    return " ORDER BY " + ", ".join(k["expression"] for k in keys) if keys else ""


def page_at(conn, table, offset, limit):
    """Returns (rows, has_more) exactly like DbSession.pageAt."""
    plans = column_plan(conn, table)
    assert plans, "no readable columns"
    projection = ", ".join(f"{p['expression']} AS {quote(p['name'])}" for p in plans)
    safe_offset = max(0, offset)
    sql = f"SELECT {projection} FROM {quote(table)}{order_clause(plans)} LIMIT {limit + 1} OFFSET {safe_offset}"
    visible = [p for p in plans if not p["internal"]]
    identity_index = next((i for i, p in enumerate(plans) if p["is_identity"]), -1)
    cursor = conn.execute(sql)
    rows, has_more, index = [], False, 0
    for record in cursor:
        if index == limit:
            has_more = True
            break
        values = []
        for plan in visible:
            i = plans.index(plan)
            raw = read_value(record[i])
            values.append(f"<BLOB {raw} B>" if plan["blob_length"] and raw is not None else raw)
        row_id = record[identity_index] if identity_index >= 0 and isinstance(record[identity_index], int) else None
        rows.append({"position": safe_offset + index, "id": row_id, "values": values, "sql": sql})
        index += 1
    return rows, has_more, sql


def validate(sql):
    """Mirror of DbSession.validate; raises ValueError when the statement is not read-only."""
    stripped = re.sub(r"(?s)--.*?$|/\*.*?\*/", " ", sql, flags=re.M).strip()
    normalized = stripped.rstrip(";").strip()
    if not (stripped.count(";") <= 1 and normalized):
        raise ValueError("Only one SQL statement is allowed")
    first = re.split(r"\s+", normalized.lstrip(), maxsplit=1)[0].lower()
    if first not in ("select", "with", "pragma"):
        raise ValueError("Only read-only SELECT, CTE, or PRAGMA statements are allowed")
    banned = re.compile(
        r"\b(insert|update|delete|replace|create|drop|alter|vacuum|reindex|attach|detach|begin|commit|rollback|savepoint|release|load_extension|pragma\s+(?!query_only|busy_timeout|table_info|table_xinfo|index_list|index_info|foreign_key_list|compile_options)\w+)\b",
        re.IGNORECASE,
    )
    if banned.search(stripped):
        raise ValueError("Write or state-changing SQL is rejected")


def execute_read_only(conn, sql, limit=5000):
    validate(sql)
    trimmed = sql.strip().rstrip(";").strip()
    head = re.split(r"\s+", trimmed, maxsplit=1)[0].lower()
    statement = trimmed if head == "pragma" else f"SELECT * FROM (\n{trimmed}\n) LIMIT {limit + 1}"
    cursor = conn.execute(statement)
    names = [d[0] for d in cursor.description]
    rows, has_more = [], False
    for record in cursor:
        if len(rows) == limit:
            has_more = True
            break
        rows.append([read_value(v) for v in record])
    return names, rows, has_more


# ---------------------------------------------------------------- fixtures

def build_database():
    conn = sqlite3.connect(":memory:")
    # Mirrors the screenshot: item(id, price, name, desc) with 106 rows.
    conn.execute("CREATE TABLE item (id INTEGER PRIMARY KEY, price REAL NOT NULL, name TEXT, desc TEXT)")
    conn.executemany(
        "INSERT INTO item (id, price, name, desc) VALUES (?,?,?,?)",
        [(i, 100.0, "test", "item desc") for i in range(1, 7)]
        + [(i, round(100 + i * 13.37, 2), f"Product-{chr(ord('B') + (i - 7))}{i - 6}", f"Description {i}") for i in range(7, 107)],
    )
    # INTEGER PRIMARY KEY declared as "INT" only, plus a generated hidden column and a blob.
    conn.execute("CREATE TABLE typed (key INT PRIMARY KEY, ratio DECIMAL(10,2), payload BLOB, label TEXT)")
    conn.executemany(
        "INSERT INTO typed VALUES (?,?,?,?)",
        [(1, 1.5, b"\x00\x01\x02\x03", "one"), (2, 2.0, b"\xff" * 300, "two"), (3, 3.25, None, None)],
    )
    conn.execute("ALTER TABLE typed ADD COLUMN upper_label TEXT GENERATED ALWAYS AS (upper(label)) VIRTUAL")
    # WITHOUT ROWID: no implicit rowid, composite primary key.
    conn.execute("CREATE TABLE settings (scope TEXT, name TEXT, value TEXT, PRIMARY KEY (scope, name)) WITHOUT ROWID")
    conn.executemany("INSERT INTO settings VALUES (?,?,?)", [("app", "theme", "dark"), ("app", "lang", "zh"), ("db", "mode", "ro")])
    # No primary key at all: must fall back to the implicit rowid.
    conn.execute("CREATE TABLE log (at TEXT, message TEXT)")
    conn.executemany("INSERT INTO log VALUES (?,?)", [(f"t{i}", f"m{i}") for i in range(250)])
    # Awkward identifier names.
    conn.execute('CREATE TABLE "we""ird" (id INTEGER PRIMARY KEY, "select" TEXT)')
    conn.execute('INSERT INTO "we""ird" VALUES (1, "ok")')
    # A view over item.
    conn.execute("CREATE VIEW cheap_items AS SELECT id, name FROM item WHERE price < 200")
    conn.commit()
    return conn


def main():
    conn = build_database()

    print("1. item (INTEGER PRIMARY KEY) -- first page, mirrors the reference screenshot")
    rows, has_more, sql = page_at(conn, "item", 0, 100)
    check("first page returns 100 rows", len(rows) == 100, f"got {len(rows)}")
    check("first page reports more rows", has_more is True)
    check("row identity is the id column", rows[0]["id"] == 1 and rows[99]["id"] == 100)
    check("values align with visible columns (id, price, name, desc)", len(rows[0]["values"]) == 4)
    check("first row matches the reference data", rows[0]["values"][:2] == ["1", "100"], str(rows[0]["values"]))
    check("row 7 is Product-B1 like the screenshot", rows[6]["values"][2] == "Product-B1", str(rows[6]["values"]))
    check("row 14 is Product-I8 like the screenshot", rows[13]["values"][2] == "Product-I8", str(rows[13]["values"]))
    check("positions are absolute", rows[0]["position"] == 0 and rows[99]["position"] == 99)

    print("2. item -- second page and total count")
    rows2, has_more2, _ = page_at(conn, "item", 100, 100)
    check("second page returns the remaining 6 rows", len(rows2) == 6, f"got {len(rows2)}")
    check("second page reports no more rows", has_more2 is False)
    check("second page ids continue at 101", rows2[0]["id"] == 101)
    total = conn.execute('SELECT count(*) FROM "item"').fetchone()[0]
    check("count(*) is 106 like the reference footer", total == 106, f"got {total}")
    check("footer range reads 101-106", f"{100 + 1}-{100 + len(rows2)}" == "101-106")
    check("page count is 2", (total - 1) // 100 + 1 == 2)
    check("row 1 of page 2 carries its own price", rows2[0]["values"][1] == "1450.37", str(rows2[0]["values"]))

    print("3. typed (INT alias, hidden generated column, blob column)")
    rows, _, sql = page_at(conn, "typed", 0, 100)
    check("hidden generated column is excluded", len(rows[0]["values"]) == 4, str(rows[0]["values"]))
    check("blob is reported as a byte length", rows[0]["values"][2] == "<BLOB 4 B>", str(rows[0]["values"]))
    check("300 byte blob is sized, not loaded", rows[1]["values"][2] == "<BLOB 300 B>", str(rows[1]["values"]))
    check("NULL blob stays NULL", rows[2]["values"][2] is None)
    check("identity comes from the INT primary key", rows[0]["id"] == 1)
    check("decimal value renders", rows[0]["values"][1] == "1.5", str(rows[0]["values"]))
    check("ORDER BY uses the primary key", "ORDER BY \"key\"" in sql, sql)

    print("4. settings (WITHOUT ROWID, composite primary key)")
    rows, _, sql = page_at(conn, "settings", 0, 2)
    check("rowid is absent for WITHOUT ROWID tables", rows[0]["id"] is None)
    check("LIMIT+1 detects further rows", len(rows) == 2)
    check("ORDER BY covers both primary key columns", "ORDER BY \"scope\", \"name\"" in sql, sql)
    rows_all, has_more, _ = page_at(conn, "settings", 0, 100)
    check("all three settings rows read back", len(rows_all) == 3, f"got {len(rows_all)}")
    check("composite key ordering is stable", [r["values"][0] for r in rows_all] == ["app", "app", "db"], str([r["values"] for r in rows_all]))

    print("5. log (no primary key) -- falls back to the implicit rowid")
    rows, has_more, sql = page_at(conn, "log", 0, 100)
    check("implicit rowid supplies identity", rows[0]["id"] == 1, str(rows[0]["id"]))
    check("implicit rowid is not rendered as a column", len(rows[0]["values"]) == 2)
    check("ORDER BY rowid", "ORDER BY rowid" in sql, sql)
    check("100 of 250 rows on page one", len(rows) == 100 and has_more)
    rows3, has_more3, _ = page_at(conn, "log", 200, 100)
    check("offset 200 returns the final 50 rows", len(rows3) == 50 and not has_more3, f"got {len(rows3)}")
    check("last page content is m249", rows3[-1]["values"] == ["t249", "m249"], str(rows3[-1]["values"]))

    print("6. quoted identifiers")
    rows, _, sql = page_at(conn, 'we"ird', 0, 10)
    check("table name with a quote is escaped", 'FROM "we""ird"' in sql, sql)
    check("reserved-word column reads back", rows[0]["values"] == ["1", "ok"], str(rows[0]["values"]))

    print("7. view")
    rows, _, _ = page_at(conn, "cheap_items", 0, 10)
    check("views page like tables", len(rows) == 7, str([r["values"] for r in rows]))
    check("view rows keep column order", rows[0]["values"][1] == "test", str(rows[0]["values"]))

    print("8. executeReadOnly wrapping")
    names, rows, has_more = execute_read_only(conn, "SELECT name, type FROM sqlite_master ORDER BY name")
    check("column names come from the cursor", names == ["name", "type"], str(names))
    check("sqlite_master query returns objects", len(rows) >= 6, str(len(rows)))
    names, rows, _ = execute_read_only(conn, "WITH t AS (SELECT id FROM item WHERE id < 4) SELECT * FROM t")
    check("CTE inside a subquery parses", names == ["id"] and len(rows) == 3, f"{names} {len(rows)}")
    names, rows, _ = execute_read_only(conn, "PRAGMA table_info(item)")
    check("PRAGMA runs unwrapped", names[0] == "cid" and len(rows) == 4, f"{names} {len(rows)}")
    names, rows, _ = execute_read_only(conn, "SELECT id FROM item ORDER BY id LIMIT 3")
    check("inner LIMIT is preserved", len(rows) == 3, str(len(rows)))
    names, rows, _ = execute_read_only(conn, "SELECT id FROM item -- trailing comment")
    check("trailing line comment does not swallow the wrapper", names == ["id"] and len(rows) == 106, f"{names} {len(rows)}")
    names, rows, _ = execute_read_only(conn, "SELECT id FROM item\n-- trailing comment on its own line")
    check("comment-only tail is tolerated", names == ["id"], str(names))
    names, rows, _ = execute_read_only(conn, "SELECT id FROM item /* block comment */")
    check("trailing block comment is tolerated", names == ["id"], str(names))
    names, rows, _ = execute_read_only(conn, "-- leading comment\nSELECT id FROM item")
    check("leading line comment is tolerated", names == ["id"], str(names))
    names, rows, _ = execute_read_only(conn, "SELECT '-- not a comment' AS c")
    check("comment markers inside strings are not stripped", rows == [["-- not a comment"]], str(rows))

    print("9. read-only validation")
    for allowed in ["SELECT 1", "select * from item;", "WITH x AS (SELECT 1) SELECT * FROM x", "PRAGMA table_info(item)", "PRAGMA query_only"]:
        try:
            validate(allowed)
            check(f"allowed: {allowed[:44]}", True)
        except ValueError as error:
            check(f"allowed: {allowed[:44]}", False, str(error))
    for rejected in [
        "INSERT INTO item VALUES (1,2,3,4)",
        "UPDATE item SET price = 1",
        "DELETE FROM item",
        "DROP TABLE item",
        "ALTER TABLE item RENAME TO x",
        "ATTACH DATABASE 'x' AS y",
        "PRAGMA writable_schema = 1",
        "VACUUM",
        "SELECT 1; DROP TABLE item",
        "REPLACE INTO item VALUES (1,2,3,4)",
        "CREATE INDEX i ON item(name)",
    ]:
        try:
            validate(rejected)
            check(f"rejected: {rejected[:40]}", False, "validator allowed it")
        except ValueError:
            check(f"rejected: {rejected[:40]}", True)

    print("10. row count estimate path")
    try:
        stat = conn.execute("SELECT stat FROM sqlite_stat1 WHERE tbl = ? LIMIT 1", ("item",)).fetchone()
    except sqlite3.Error:
        stat = None  # Kotlin wraps this in runCatching, matching a missing sqlite_stat1 table
    check("missing sqlite_stat1 falls back to count(*)", stat is None)
    exact = conn.execute('SELECT count(*) FROM "item"').fetchone()[0]
    check("fallback count matches", exact == 106, str(exact))
    conn.execute("ANALYZE")
    stat = conn.execute("SELECT stat FROM sqlite_stat1 WHERE tbl = ? LIMIT 1", ("item",)).fetchone()
    estimate = int(stat[0].split(" ")[0]) if stat else None
    check("after ANALYZE the estimate is used", estimate == 106, str(estimate))

    print()
    if FAILURES:
        print(f"FAILED ({len(FAILURES)}): " + "; ".join(FAILURES))
        return 1
    print("All SQL verification checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
