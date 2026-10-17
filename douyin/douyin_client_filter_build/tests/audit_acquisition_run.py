"""Read a pulled regression database without changing it."""
import collections
import sqlite3
import sys

db, task = sys.argv[1:3]
con = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
start = con.execute("SELECT min(id) FROM logs WHERE message LIKE ?", (f"%task={task}；%",)).fetchone()[0]
if start is None:
    raise SystemExit("Task not found")
rows = con.execute("SELECT id,created,message FROM logs WHERE id>=? ORDER BY id", (start,)).fetchall()
# The worker start immediately after AUTH belongs to this task; stop only at another task id.
next_start = next((i for i, r in enumerate(rows) if "[环境] task=" in r[2] and f"task={task}；" not in r[2]), None)
if next_start is not None:
    rows = rows[:next_start]
completed = [r for r in rows if r[2].startswith("[采集] 视频完成")]
scanned = [r for r in rows if r[2].startswith("[评论] 扫描第")]
saved = con.execute("SELECT count(*) FROM comments WHERE task=?", (task,)).fetchone()[0]
stops = [r for r in rows if r[2].startswith("[停止]")]
print(f"task={task} completed={len(completed)}/100 scanned={len(scanned)} new_saved={saved} elapsed={(rows[-1][1]-rows[0][1])//1000}s")
print("per_work=" + str(dict(collections.Counter(r[2].split()[-1] for r in completed))))
print("recovery_requests=" + str(sum("[评论恢复] 已核对" in r[2] for r in rows)) + " restored=" + str(sum("[评论恢复] 面板已" in r[2] for r in rows)))
for r in (stops or rows[-3:]):
    print(r[0], r[2])

if sys.argv[3:] == ["--verify"]:
    counts = [int(r[2].split()[-1]) for r in completed]
    assert len(counts) == 100, "Did not complete 100 works"
    assert all(0 <= n <= 10 for n in counts), "Per-work cap exceeded"
    assert sum(counts) == len(scanned), "Work totals and scanned rows disagree"
    opened = [r[2] for r in rows if r[2].startswith("[结果] 打开内容")]
    assert len(opened) == len(set(opened)) == 100, "Work selection repeated or skipped"
    for i, row in enumerate(rows):
        if row[2].startswith("[采集] 视频完成") and int(row[2].split()[-1]) < 10:
            assert "面板内明确底部标记=" in rows[i-1][2], "Short work finished without a bottom marker"
    assert len(stops) == 1 and "阶段=DRAIN" in stops[0][2] and "待处理AI=0" in stops[0][2], "Unexpected stop / unfinished AI"
    assert sum("[评论恢复] 面板已" in r[2] for r in rows) >= 1, "Recovery not exercised"
    assert not any("[评论异常]" in r[2] for r in rows), "Comment scan failed"
    keys = []
    for row in rows:
        if row[2].startswith("[评论解析] key="):
            prefix = row[2].split("key=", 1)[1].split("；", 1)[0]
            match = con.execute("SELECT key,ai,account FROM comments WHERE key LIKE ?", (prefix + "%",)).fetchall()
            assert len(match) == 1, "Scanned row missing from saved database"
            keys.append(match[0])
    assert len(keys) == len(scanned) == len({r[0] for r in keys}), "Missing/repeated scanned records"
    assert not any(r[1] in ("待判断", "判断中") for r in keys), "AI result still pending"
    print("PASS: 100 distinct works, cap 10, explicit bottoms, exact persisted totals, recovery, normal DRAIN stop")
    print("scanned_ai_states=" + str(dict(collections.Counter(r[1] for r in keys))))
    print("intent_accounts=" + str(sum(r[1] == "意向" and bool(r[2]) for r in keys)))
    print("database_integrity=" + con.execute("PRAGMA integrity_check").fetchone()[0])
