#!/usr/bin/env python3
"""Drives the app on a CI emulator: screenshots of every screen, then an end-to-end test of
the home-screen swipe capture. Writes everything to ci-out/. Exits non-zero if the swipe
test fails."""

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "io.github.wolfycodek.keepnotes"
SERVICE = PKG + "/app.notes.GestureService"
OUT = "ci-out"
FORCED = None
LOG = []
SDK = 0


def log(*a):
    line = " ".join(str(x) for x in a)
    print(line, flush=True)
    LOG.append(line)


def adb(*args, timeout=60, check=False):
    try:
        r = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        log("TIMEOUT adb", *args)
        return ""
    out = r.stdout.decode(errors="replace")
    if r.returncode != 0:
        log("adb", *args, "->", r.returncode, r.stderr.decode(errors="replace").strip())
        if check:
            raise RuntimeError("adb failed")
    return out


def sh(cmd, timeout=60):
    return adb("shell", cmd, timeout=timeout).strip()


def shot(name):
    path = os.path.join(OUT, name + ".png")
    with open(path, "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, timeout=60)
    log("screenshot", path)


def wait(s=1.0):
    time.sleep(s)


def screen_size():
    m = re.findall(r"(\d+)x(\d+)", sh("wm size"))
    w, h = m[-1]
    return int(w), int(h)


class Node:
    def __init__(self, e):
        self.text = e.get("text") or ""
        self.desc = e.get("content-desc") or ""
        self.rid = e.get("resource-id") or ""
        self.cls = e.get("class") or ""
        self.pkg = e.get("package") or ""
        self.checked = e.get("checked") == "true"
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", e.get("bounds")))
        self.bounds = (x1, y1, x2, y2)
        self.cx, self.cy = (x1 + x2) // 2, (y1 + y2) // 2

    def __repr__(self):
        return f"<{self.cls} text={self.text[:30]!r} desc={self.desc!r} id={self.rid} {self.bounds}>"


def dump(name=None):
    for _ in range(3):
        sh("rm -f /sdcard/ui.xml")
        sh("uiautomator dump /sdcard/ui.xml", timeout=90)
        xml = adb("exec-out", "cat", "/sdcard/ui.xml")
        if xml.strip().startswith("<?xml"):
            if name:
                with open(os.path.join(OUT, name + ".xml"), "w") as f:
                    f.write(xml)
            root = ET.fromstring(xml)
            return [Node(e) for e in root.iter("node")]
        wait(1)
    log("dump failed")
    return []


def find(nodes, text=None, desc=None, rid=None, contains=None, desc_has=None):
    for n in nodes:
        if desc_has is not None and desc_has not in n.desc:
            continue
        if text is not None and n.text != text:
            continue
        if desc is not None and n.desc != desc:
            continue
        if rid is not None and not n.rid.endswith(rid):
            continue
        if contains is not None and contains not in n.text and contains not in n.desc:
            continue
        return n
    return None


def tap(x, y):
    sh(f"input tap {x} {y}")


# `input tap/swipe` inject events after the accessibility input filter, so accessibility
# services never see them. The helpers below write to the emulator's touchscreen device
# instead, so the events take the same path as a real finger. Needs `adb root`.
TOUCH = None


def touch_device():
    """The emulator's touchscreen for display 0 (it has one per possible display)."""
    global TOUCH
    if TOUCH:
        return TOUCH
    out = sh("getevent -lp")
    devices, cur = [], None
    for line in out.splitlines():
        m = re.match(r"add device \d+: (\S+)", line.strip())
        if m:
            cur = {"path": m.group(1), "name": "", "axes": {}}
            devices.append(cur)
            continue
        if cur is None:
            continue
        m = re.match(r'name:\s*"(.*)"', line.strip())
        if m:
            cur["name"] = m.group(1)
        for key, name in (("X", "ABS_MT_POSITION_X"), ("Y", "ABS_MT_POSITION_Y"),
                          ("P", "ABS_MT_PRESSURE"), ("M", "ABS_MT_TOUCH_MAJOR")):
            m = re.search(name + r"\s*:.*max (\d+)", line)
            if m:
                cur["axes"][key] = int(m.group(1))
    touch = [d for d in devices if "X" in d["axes"] and "Y" in d["axes"]]
    touch.sort(key=lambda d: int(re.sub(r"\D", "", d["path"]) or 0))
    log("touch devices:", [(d["path"], d["name"]) for d in touch])
    first = [d for d in touch if re.search(r"multi_touch(_1)?$", d["name"])] or touch
    TOUCH = (first[0]["path"], first[0]["axes"])
    log("touch device:", TOUCH)
    return TOUCH


def raw_stroke(points, hold_ms=0):
    """Presses at points[0], moves through the rest, and lifts, as a real finger would."""
    dev, axes = touch_device()
    w, h = screen_size()
    sx, sy = axes["X"] / (w - 1), axes["Y"] / (h - 1)
    c = []

    def pos(x, y):
        c.append(f"sendevent {dev} 3 53 {int(x * sx)}")
        c.append(f"sendevent {dev} 3 54 {int(y * sy)}")

    c.append(f"sendevent {dev} 3 47 0")
    c.append(f"sendevent {dev} 3 57 {int(time.time()) % 60000}")
    pos(*points[0])
    if "P" in axes:
        c.append(f"sendevent {dev} 3 58 {max(1, axes['P'] // 2)}")
    if "M" in axes:
        c.append(f"sendevent {dev} 3 48 {max(1, min(axes['M'], 6))}")
    c.append(f"sendevent {dev} 1 330 1")
    c.append(f"sendevent {dev} 0 0 0")
    if hold_ms:
        c.append(f"sleep {hold_ms / 1000:.2f}")
    for x, y in points[1:]:
        pos(x, y)
        c.append(f"sendevent {dev} 0 0 0")
    c.append(f"sendevent {dev} 3 57 -1")
    c.append(f"sendevent {dev} 1 330 0")
    c.append(f"sendevent {dev} 0 0 0")
    sh("; ".join(c), timeout=60)


def real_swipe(x1, y1, x2, y2, steps=4):
    pts = [(x1 + (x2 - x1) * i / steps, y1 + (y2 - y1) * i / steps) for i in range(steps + 1)]
    raw_stroke(pts)


def real_tap(x, y):
    raw_stroke([(x, y), (x, y)])


def tap_node(nodes, **kw):
    n = find(nodes, **kw)
    if n is None:
        log("not found:", kw)
        return False
    tap(n.cx, n.cy)
    return True


def dismiss_system_dialogs(nodes):
    """Closes "X isn't responding" dialogs the slow CI emulator sometimes shows."""
    for n in nodes:
        if n.text in ("Wait",) and n.pkg == "android":
            tap(n.cx, n.cy)
            wait(1.5)
            return True
    return False


def ensure_app():
    """Brings the app back if an earlier step left it (the tour must not wander off)."""
    if PKG not in sh("dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'"):
        log("app not in front; relaunching")
        launch()
        wait(1.5)


def step(name, settle=1.2, **kw):
    """Taps the element matching kw on a fresh dump, waits, then takes a screenshot."""
    ensure_app()
    nodes = dump()
    if find(nodes, **kw) is None and dismiss_system_dialogs(nodes):
        nodes = dump()
    ok = tap_node(nodes, **kw)
    wait(settle)
    shot(name)
    return ok


def back():
    sh("input keyevent KEYCODE_BACK")
    wait(0.8)


def back_to_notes():
    """Presses back until the notes screen's menu button is showing (at most 3 times)."""
    for _ in range(3):
        back()
        if find(dump(), desc="Open navigation drawer"):
            return


def home():
    sh("input keyevent KEYCODE_HOME")
    wait(1.5)


def launch():
    sh(f"am start -W -n {PKG}/app.notes.MainActivity")
    wait(2)


def top_package():
    out = sh("dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'")
    log("resumed:", out.strip())
    return out


def touch_exploration():
    return sh("settings get secure touch_exploration_enabled")


# ---------------------------------------------------------------------------------------------
# UI tour


def ui_tour(mode):
    sh(f"cmd uimode night {'yes' if mode == 'dark' else 'no'}")
    sh(f"am force-stop {PKG}")
    wait(1)
    launch()
    wait(2)
    shot(f"{mode}-01-notes")
    nodes = dump(f"{mode}-notes")
    if mode != "dark":
        return
    tour = os.environ.get("TOUR", "full")
    # The Celebrities note, top and around the forced line.
    if step(f"{mode}-02-celebrities", text="Celebrities", settle=2):
        sh("input swipe 540 1800 540 600 300")
        wait(1)
        shot(f"{mode}-03-celebrities-scrolled")
        back_to_notes()
    if tour == "basic":
        return
    run_full_tour(mode)


def run_full_tour(mode):
    p = mode
    w, h = screen_size()
    # Celebrities opened again, scrolled to the middle.
    # Drawer
    wait(1)
    if step(f"{p}-10-drawer", desc="Open navigation drawer", settle=1.5):
        if find(dump(), text="Google Keep"):
            back()
        wait(1.5)
    # Search landing page, then a query, then a type filter.
    if step(f"{p}-11-search", desc="Search Keep"):
        sh("input text milk")
        wait(1.2)
        shot(f"{p}-12-search-milk")
        nodes = dump()
        tap_node(nodes, desc="Clear search")
        wait(0.8)
        sh("input keyevent KEYCODE_BACK")
        wait(0.8)
        if step(f"{p}-13-search-lists", desc="Lists"):
            back()
        back()
    # View toggle and sort menu.
    if step(f"{p}-14-list-view", desc="List view"):
        step(f"{p}-15-grid-view", desc="Grid view")
    if step(f"{p}-16-sort", desc="Sort"):
        back()
    # FAB menu, then a new list.
    if step(f"{p}-17-fab-menu", desc="Create a note"):
        if step(f"{p}-18-new-list", desc="List"):
            sh("input text Batteries")
            wait(0.8)
            shot(f"{p}-19-new-list-typed")
            back()
            back()
            wait(1.2)
            shot(f"{p}-20-after-new-list")
    # Live (offline failure).
    if step(f"{p}-21-live", desc="Live", settle=0.8):
        # Opens the speech recogniser (or, without one, the audio recorder): back out of it.
        wait(2)
        shot(f"{p}-21b-live-opened")
        back_to_notes()
    # Long-press selection and its colour sheet.
    nodes = dump()
    n = find(nodes, text="Oyakodon")
    if n:
        sh(f"input swipe {n.cx} {n.cy} {n.cx} {n.cy} 900")
        wait(1)
        shot(f"{p}-22-selected")
        if step(f"{p}-23-colour", desc_has="Change col"):
            back()
        if step(f"{p}-24-select-more", desc="More options"):
            back()
        back()
    # A checklist note and the editor's sheets.
    if step(f"{p}-25-checklist", text="Work", settle=2):
        if step(f"{p}-26-add-sheet", desc="Add"):
            back()
        if step(f"{p}-27-colour-sheet", desc_has="Change col"):
            back()
        if step(f"{p}-28-more-sheet", desc="More options"):
            back()
        step(f"{p}-29-format", desc="Formatting options")
        back()
    # A text note.
    if step(f"{p}-30-text-note", text="Oyakodon", settle=2):
        if step(f"{p}-31-reminder-sheet", desc="Reminder"):
            back()
        back()
    # Swipe a note to archive it.
    nodes = dump()
    sh("setprop log.tag.KeepGestures DEBUG")
    n = find(nodes, text="Oyakodon")
    if n:
        sh(f"input swipe {n.cx} {n.cy} {w - 10} {n.cy} 250")
        wait(0.6)
        shot(f"{p}-32-swiped-archive")
        wait(1)
        archived = find(dump(), text="Oyakodon") is None
        log("swipe to archive (input swipe):", archived)
        if not archived:
            n = find(dump(), text="Oyakodon")
            if n:
                real_swipe(n.cx, n.cy, w - 10, n.cy, 8)
                wait(1)
                shot(f"{p}-32b-swiped-archive-raw")
                log("swipe to archive (raw touch):", find(dump(), text="Oyakodon") is None)
        log(sh("logcat -d | grep -E 'KeepGestures: card' | tail -6"))
        wait(4)
    # Drawer destinations.
    for i, label in enumerate(["Reminders", "Archive", "Deleted", "Create new label", "Settings", "Help & feedback"]):
        nodes = dump()
        if tap_node(nodes, desc="Open navigation drawer"):
            wait(1)
            step(f"{p}-4{i}-{label.split()[0].lower()}", desc=label, settle=1.8)
            back_to_notes()
    # Hidden settings: typed into search.
    if step(f"{p}-49a-search-again", desc="Search Keep"):
        sh("input text '*#*#'")
        wait(2)
        shot(f"{p}-49b-hidden-settings")
        back_to_notes()
    # Account card.
    if step(f"{p}-50-account", desc_has="Google Account"):
        back()
    # Drawing.
    if step(f"{p}-51-fab", desc="Create a note"):
        if step(f"{p}-52-drawing", desc="Drawing"):
            sh(f"input swipe {w//4} {h//2} {3*w//4} {int(h*0.6)} 400")
            sh(f"input swipe {w//4} {int(h*0.6)} {3*w//4} {int(h*0.45)} 400")
            wait(0.8)
            shot(f"{p}-53-drawing-strokes")
            back()
            wait(1.5)
            shot(f"{p}-54-drawing-note")
            back()
            wait(1)
            shot(f"{p}-55-notes-after-drawing")


# ---------------------------------------------------------------------------------------------
# Home screen


def home_package():
    out = sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
    line = out.strip().splitlines()[-1]
    return line.split("/")[0]


def add_home_icons():
    """Puts the app's icon on home page 0 (next to Google's icons) and page 1 (so there is a
    second page to swipe to), by editing the launcher database as root."""
    launcher = home_package()
    log("launcher:", launcher)
    adb("root")
    wait(3)
    adb("wait-for-device")
    sh(f"am force-stop {launcher}")
    dbdir = f"/data/data/{launcher}/databases"
    files = sh(f"ls {dbdir}").split()
    log("launcher dbs:", files)
    have_sqlite = bool(sh("which sqlite3"))
    log("sqlite3 on device:", have_sqlite)
    intent = ("#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;"
              f"launchFlags=0x10200000;component={PKG}/app.notes.MainActivity;end")
    done = False
    for f in files:
        if not f.endswith(".db"):
            continue
        db = f"{dbdir}/{f}"
        if have_sqlite:
            tables = sh(f"sqlite3 {db} .tables")
            if "favorites" not in tables:
                continue
            rows = sh(f"sqlite3 {db} \"select screen,cellX,cellY,spanX,spanY,container,title from favorites\"")
            log(f, "rows:\n" + rows)
            sql = (f"delete from favorites where intent like '%{PKG}%';"
                   f"insert into favorites (_id,title,intent,container,screen,cellX,cellY,spanX,spanY,itemType,profileId) "
                   f"values (9001,'Keep Notes','{intent}',-100,0,1,2,1,1,0,0);"
                   f"insert into favorites (_id,title,intent,container,screen,cellX,cellY,spanX,spanY,itemType,profileId) "
                   f"values (9002,'Keep Notes','{intent}',-100,1,1,2,1,1,0,0);")
            log(sh(f"sqlite3 {db} \"{sql}\""))
            done = True
        else:
            done = add_icons_from_host(db, intent) or done
    sh(f"am force-stop {launcher}")
    home()
    wait_for_launcher()
    for f in files:
        if f.endswith(".db") and have_sqlite and "launcher" in f:
            log("after restart", f, sh(f"sqlite3 {dbdir}/{f} \"select _id,screen,cellX,cellY,container,title from favorites\""))
    return done


def wait_for_launcher():
    for _ in range(20):
        wait(1.5)
        nodes = dump()
        if find(nodes, text="Keep Notes") or find(nodes, text="Gmail"):
            return nodes
    return []


def labels_on_screen():
    nodes = dump()
    names = [n.text for n in nodes if n.text and n.pkg == home_package()]
    pages = [n.desc for n in nodes if "Page" in n.desc or "page" in n.desc]
    return names, pages


def which_page():
    names, pages = labels_on_screen()
    log("  visible:", names, pages)
    # The dock can show one predicted app, so page 0 needs two of its own icons.
    if sum(1 for a in ("Gmail", "Photos", "YouTube") if a in names) >= 2:
        return 0
    if "Keep Notes" in names:
        return 1
    return -1


def ensure_second_page(w, h):
    """Makes sure the home screen has a page 1 that a swipe from page 0 reaches."""
    home()
    sh(f"input swipe {int(w*0.85)} {int(h*0.45)} {int(w*0.15)} {int(h*0.45)} 400")
    wait(2)
    page = which_page()
    log("after baseline swipe left, page:", page)
    if page == 1:
        sh(f"input swipe {int(w*0.15)} {int(h*0.45)} {int(w*0.85)} {int(h*0.45)} 400")
        wait(2)
        log("back, page:", which_page())
        return True
    home()
    wait(1)
    nodes = dump()
    yt = find(nodes, text="YouTube")
    if not yt:
        log("no YouTube icon to drag")
        return False
    # Long-press the icon, hold it at the right edge until a new page opens, then drop it.
    sh(f"input motionevent DOWN {yt.cx} {yt.cy}")
    wait(1.5)
    x, y = yt.cx, yt.cy
    for i in range(1, 11):
        sh(f"input motionevent MOVE {x + (w - 8 - x) * i // 10} {y}")
        wait(0.05)
    wait(2.5)
    sh(f"input motionevent MOVE {w // 2} {y}")
    wait(0.8)
    sh(f"input motionevent UP {w // 2} {y}")
    wait(2)
    page = which_page()
    log("after dragging YouTube to the edge, page:", page)
    shot("home-00-created-page1")
    home()
    wait(1)
    sh(f"input swipe {int(w*0.85)} {int(h*0.45)} {int(w*0.15)} {int(h*0.45)} 400")
    wait(2)
    page = which_page()
    log("after second baseline swipe, page:", page)
    sh(f"input swipe {int(w*0.15)} {int(h*0.45)} {int(w*0.85)} {int(h*0.45)} 400")
    wait(2)
    return page == 1


def add_icons_from_host(db, intent):
    import sqlite3
    local = os.path.join(OUT, "launcher.db")
    for suffix in ["", "-wal", "-shm"]:
        if os.path.exists(local + suffix):
            os.remove(local + suffix)
        adb("pull", db + suffix, local + suffix)
    try:
        con = sqlite3.connect(local)
        names = [r[0] for r in con.execute("select name from sqlite_master where type='table'")]
        if "favorites" not in names:
            return False
        for r in con.execute("select screen,cellX,cellY,spanX,spanY,container,title from favorites"):
            log("row", r)
        con.execute(f"delete from favorites where intent like '%{PKG}%'")
        for _id, screen in [(9001, 0), (9002, 1)]:
            con.execute("insert into favorites (_id,title,intent,container,screen,cellX,cellY,spanX,spanY,itemType,profileId)"
                        " values (?,?,?,?,?,?,?,?,?,?,?)",
                        (_id, "Keep Notes", intent, -100, screen, 1, 2, 1, 1, 0, 0))
        con.commit()
        con.execute("pragma wal_checkpoint(truncate)")
        con.close()
    except Exception as e:
        log("sqlite error", e)
        return False
    owner = sh(f"stat -c %u:%g {db}")
    adb("push", local, db)
    sh(f"rm -f {db}-wal {db}-shm")
    sh(f"chown {owner} {db}; chmod 660 {db}; restorecon {db}")
    return True


# ---------------------------------------------------------------------------------------------
# End-to-end swipe test


def gesture_home(w, h, label):
    """Swipes up from the bottom edge inside Settings and reports whether home came up."""
    sh("am start -W -a android.settings.SETTINGS")
    wait(2)
    ok = False
    for steps in (6, 16):
        real_swipe(w // 2, h - 3, w // 2, int(h * 0.4), steps)
        wait(3)
        focus = top_package()
        ok = home_package() in focus
        log("  bottom swipe", steps, "steps ->", "home" if ok else "not home")
        if ok:
            break
    log("gesture-nav home", label, ":", ok)
    shot(f"nav-{label}")
    home()
    return ok


def enable_service():
    sh("settings put secure accessibility_enabled 0")
    sh(f"settings put secure enabled_accessibility_services {SERVICE}")
    sh("settings put secure accessibility_enabled 1")
    for _ in range(10):
        wait(1)
        if SERVICE.split("/")[1] in sh("dumpsys accessibility | grep -i 'Bound services'"):
            break
    log("enabled setting:", sh("settings get secure enabled_accessibility_services"))
    acc = sh("dumpsys accessibility")
    with open(os.path.join(OUT, "dumpsys-accessibility.txt"), "w") as f:
        f.write(acc)
    for line in acc.splitlines():
        if any(k in line for k in ("Bound services", "Enabled services", "Crashed", "touchExploration", "Binding")):
            log("  ", line.strip()[:400])
    log(sh("logcat -d | grep -iE 'AccessibilityManager|GestureService|keepnotes' | tail -30"))


def dismiss_stylus_sheet():
    """Gboard on Android 16 takes the raw test touches for a stylus and offers a tutorial that
    swallows typed text. Closes it if it is showing."""
    nodes = dump()
    if any("stylus" in n.text.lower() for n in nodes):
        log("closing the keyboard's stylus tutorial")
        for n in nodes:
            if n.text == "Cancel" and n.pkg != PKG:
                tap(n.cx, n.cy)
                wait(1)
                return True
    return False


# The default keypad (Force.DEFAULT_KEYPAD): centres of the 1, 9 and 0 keys as fractions of the
# screen. The bottom-right 3x3 of a four-column home screen, 0 to the left of the 7.
KEYPAD = (0.350, 0.4825, 0.771, 0.706, 0.139, 0.706)


def cell(d, w, h, k=KEYPAD):
    """The middle of a digit's keypad cell."""
    if d == 0:
        return int(w * k[4]), int(h * k[5])
    row, col = (d - 1) // 3, (d - 1) % 3
    return int(w * (k[0] + col * (k[2] - k[0]) / 2)), int(h * (k[1] + row * (k[3] - k[1]) / 2))


def swipe_from(d, direction, w, h):
    """A real page swipe starting in a digit's cell: "left" moves to the next page."""
    x, y = cell(d, w, h)
    end = int(w * 0.12) if direction == "left" else int(w * 0.88)
    real_swipe(x, y, end, y)


def open_hidden_settings():
    for attempt in range(2):
        launch()
        wait(1)
        if not tap_node(dump(), desc="Search Keep"):
            back_to_notes()
            continue
        wait(1)
        dismiss_stylus_sheet()
        sh("input text '*#*#'")
        wait(2)
        if find(dump(), text="Prime") is not None:
            return True
        dismiss_stylus_sheet()
        back_to_notes()
    return False


# Note: `uiautomator dump` suspends other accessibility services while it reads the screen,
# so Keep Notes' service is briefly off during every dump(). Never dump() while something
# depends on the service staying connected (keypad set-up mode, a pending swipe).


def tap_setting(text):
    """Taps a row of the hidden settings, scrolling it well clear of the navigation bar first.
    Scrolls slowly (no fling) and only taps once the row has stopped moving."""
    w, h = screen_size()
    wait(1.5)   # let the screen settle (the search box's keyboard closing moves the rows)
    for _ in range(5):
        n = find(dump(), text=text)
        if n is not None and n.cy < h * 0.85:
            wait(0.6)
            again = find(dump(), text=text)
            if again is not None and again.cy == n.cy:
                wait(1.2)   # the service reconnects after the dump
                log(f"tapping '{text}' at {n.cx},{n.cy}")
                tap(n.cx, n.cy)
                wait(0.4)
                shot("tapped-" + re.sub(r"[^a-z]+", "-", text.lower()).strip("-"))
                return True
            continue
        sh(f"input swipe {w//2} {int(h*0.70)} {w//2} {int(h*0.45)} 1200")
        wait(1.5)
    log("not found:", text)
    return False


def prime(label):
    """Taps Prime in the hidden settings, which should land on home page 0."""
    if not open_hidden_settings():
        log("hidden settings did not open")
        shot(f"prime-{label}-failed")
        return False
    shot(f"prime-{label}-settings")
    tap_node(dump(), text="Prime")
    wait(4)
    shot(f"prime-{label}-home")
    return True


def overlay_visible():
    """True if the practice keypad window is on screen (from the window manager's dump)."""
    out = sh("dumpsys window windows")
    with open(os.path.join(OUT, "dumpsys-window.txt"), "a") as f:
        f.write(out + "\n\n")
    blocks = re.split(r"\n\s*Window #\d+ ", out)
    for b in blocks:
        # Android 14 prints the window type as a number (2032), later versions by name.
        if PKG in b and ("ACCESSIBILITY_OVERLAY" in b or "ty=2032" in b):
            vis = re.search(r"mViewVisibility=(0x[0-9a-f]+)", b)
            log("  overlay window:", vis.group(0) if vis else "visibility not found",
                "| isOnScreen=true" if "isOnScreen=true" in b else "")
            return bool(vis) and vis.group(1) == "0x0"
    log("  no overlay window")
    return False


def digit_lines():
    return sh("logcat -d | grep -c 'KeepGestures: digit'").strip()


def find_note(text):
    """Finds a note card on the notes screen, scrolling back up to the top if needed."""
    w, h = screen_size()
    for _ in range(5):
        n = find(dump(), text=text)
        if n is not None and n.cy > h * 0.15:
            return n
        sh(f"input swipe {w//2} {int(h*0.30)} {w//2} {int(h*0.90)} 150")
        wait(1.2)
    return None


def celebrities_line():
    """Opens Celebrities and returns the line number the forced name is on (or -1)."""
    found = -1
    n = find_note("Celebrities")
    if n is None:
        log("not found: Celebrities")
    else:
        tap(n.cx, n.cy)
        wait(1.5)
        text = "\n".join(n.text for n in dump())
        m = re.search(r"(?:^|\n)(\d+)\. " + re.escape(FORCED) + r"(?:\n|$)", text)
        found = int(m.group(1)) if m else -1
        back()
    return found


def swipe_test(w, h):
    global FORCED
    FORCED = open("app/src/main/assets/celebrities.txt").readline().strip()
    home()
    nodes = dump("home-page0")
    icon = find(nodes, text="Keep Notes")
    log("icon on page 0:", icon)
    shot("home-01-page0")

    two_pages = ensure_second_page(w, h)
    log("home screen has a second page:", two_pages)
    # The raw touchscreen events must page the launcher before the service is involved.
    home()
    real_swipe(int(w*0.83), int(h*0.40), int(w*0.10), int(h*0.40))
    wait(2)
    raw_left = which_page()
    real_swipe(int(w*0.17), int(h*0.40), int(w*0.90), int(h*0.40))
    wait(2)
    raw_right = which_page()
    raw_ok = raw_left == 1 and raw_right == 0
    log("raw touch baseline: left ->", raw_left, ", right ->", raw_right)
    home()
    nodes = dump("home-page0b")
    icon = find(nodes, text="Keep Notes") or icon
    nav_base = gesture_home(w, h, "without-service")

    # Start the swipe test from a clean slate (not primed).
    sh(f"am force-stop {PKG}")
    sh(f"run-as {PKG} rm -f shared_prefs/f.xml shared_prefs/c.xml")
    sh("setprop log.tag.KeepGestures DEBUG")
    # A real phone animates; the runner turned animations off, and the launcher may only
    # report a settled page change from its animation path.
    for k in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        sh(f"settings put global {k} 1")
    enable_service()
    home()
    wait(2)
    # Not primed yet: the service must not listen at all.
    te_unprimed = touch_exploration()
    log("touch exploration on home before priming:", te_unprimed)

    # Prime from page 1: it must land on page 0 with the service listening.
    sh(f"input swipe {int(w*0.85)} {int(h*0.45)} {int(w*0.15)} {int(h*0.45)} 400")
    wait(2)
    log("before priming, page:", which_page())
    prime("1")
    page_after_prime = which_page()
    log("after priming, page:", page_after_prime)
    if page_after_prime != 0:
        home()
    te = touch_exploration()
    log("touch exploration on home after priming:", te)

    # Digit 6: second row, right cell; swipe left to page 1. (Not the top row of page 0:
    # Pixel's "At a glance" widget there pages its own cards.)
    swipe_from(6, "left", w, h)
    wait(2.5)
    shot("home-03-after-digit6")
    # Digit 1: top row, left cell of page 1; swipe right back to page 0.
    swipe_from(1, "right", w, h)
    wait(2.5)
    shot("home-04-after-digit1")

    # Taps must still reach the launcher: open the app from its icon.
    opened = False
    log(sh("logcat -d | grep KeepGestures | tail -20"))
    log("page after both swipes:", which_page())
    digits_before = sh("logcat -d | grep -c 'KeepGestures: digit'")
    if icon:
        real_tap(icon.cx, icon.cy)
        wait(3)
        opened = PKG in top_package()
    if not opened:
        log("icon tap did not open the app; launching directly")
        launch()
    log("tap on home icon opened app:", opened)
    wait(2)
    digits_after = sh("logcat -d | grep -c 'KeepGestures: digit'")
    log(sh("logcat -d | grep KeepGestures | tail -6"))
    tap_not_counted = digits_after.strip() == digits_before.strip()
    wait(1.5)
    te_in_app = touch_exploration()
    log("touch exploration while app in front:", te_in_app)
    shot("e2e-01-app")

    nodes = dump("e2e-notes")
    ok_line = False
    if tap_node(nodes, text="Celebrities"):
        wait(1.5)
        shot("e2e-02-celebrities")
        nodes = dump("e2e-celebrities")
        text = "\n".join(n.text for n in nodes)
        ok_line = bool(re.search(r"(^|\n)61\. " + re.escape(FORCED) + r"(\n|$)", text))
        log("line 61 shows", FORCED, ":", ok_line, "| found:", re.findall(r"\d+\. " + re.escape(FORCED), text))
        sh(f"input swipe {w//2} {int(h*0.8)} {w//2} {int(h*0.2)} 300")
        sh(f"input swipe {w//2} {int(h*0.8)} {w//2} {int(h*0.2)} 300")
        wait(1)
        shot("e2e-03-celebrities-line61")
        back()

    # The reveal switched the trick off: on the home screen nothing listens, swipes don't count
    # and the note keeps line 61.
    home()
    wait(2)
    te_spent = touch_exploration()
    log("touch exploration after the reveal:", te_spent)
    before = digit_lines()
    swipe_from(5, "left", w, h)    # to page 1
    wait(2.5)
    swipe_from(8, "right", w, h)   # back to page 0
    wait(2.5)
    spent_no_digits = digit_lines() == before
    launch()
    spent_line = celebrities_line()
    log("after the reveal: digits counted:", not spent_no_digits, "| forced name on line", spent_line)
    off_after_reveal = te_spent == "0" and spent_no_digits and spent_line == 61

    # Priming again switches listening back on.
    prime("2")
    te_after = touch_exploration()
    log("touch exploration after priming again:", te_after)
    # Other apps must not see touch exploration (Samsung's Recents draws a focus box if they do).
    sh("am start -W -a android.settings.SETTINGS")
    wait(2.5)
    te_other = touch_exploration()
    log("touch exploration in another app:", te_other)
    home()
    wait(2)
    te_home_again = touch_exploration()
    log("touch exploration back on the home screen:", te_home_again)
    # Gesture navigation must still work with the service running (outside the app).
    nav_svc = gesture_home(w, h, "with-service")
    log(sh("logcat -d | grep -E 'KeepGestures|TouchExplorer' | tail -60"))

    # Leaving the app after a number was entered also switches the trick off, even if the note
    # wasn't opened.
    home()
    wait(2)
    swipe_from(6, "left", w, h)   # 6
    wait(2.5)
    swipe_from(1, "right", w, h)   # 1
    wait(2.5)
    launch()
    home()
    wait(2)
    te_after_leave = touch_exploration()
    log("touch exploration after leaving the app with a number:", te_after_leave)

    # Practice overlay: allowed now the trick is off; swipes light up the keypad but don't count.
    practice_ok = overlay_on = False
    if open_hidden_settings():
        tap_setting("Show the keypad on the home screen")
        wait(1)
        shot("practice-00-settings")
        home()
        wait(2.5)
        overlay_on = overlay_visible()
        shot("practice-01-overlay")
        before = digit_lines()
        swipe_from(6, "left", w, h)   # 6
        wait(0.9)
        shot("practice-02-overlay-digit6")
        wait(2)
        practice_ok = ("KeepGestures: practice digit 6" in sh("logcat -d | grep 'practice digit'")
                       and digit_lines() == before)
        log("practice overlay shown:", overlay_on, "| practice swipe lit up without counting:", practice_ok)

    # Set the keypad up on the home screen with three taps, then check a swipe from the new 2
    # (outside the default keypad) counts as 2. Then back to the default.
    custom = (0.20, 0.30, 0.70, 0.54, 0.90, 0.54)
    if open_hidden_settings():
        tap_setting("Set up the keypad")
        wait(3)
        shot("setup-01-ask-for-1")
        for d in (1, 9, 0):
            x, y = cell(d, w, h, custom)
            sh(f"input tap {x} {y}")
            wait(1)
        shot("setup-02-done")
        wait(1)
        x, y = cell(2, w, h, custom)
        real_swipe(x, y, int(w * 0.95), y)   # turns the page back to page 0
        wait(2.5)
        shot("setup-03-swiped-new-2")
        setup_ok = "KeepGestures: practice digit 2" in sh("logcat -d | grep 'practice digit'")
        log("keypad set up on the home screen, new 2 counted:", setup_ok)
        log(sh("logcat -d | grep -E 'KeepGestures: (keypad|outside|practice)' | tail -8"))
        if open_hidden_settings():
            tap_setting("Use the default keypad")
            wait(1)
    else:
        setup_ok = False
    # Priming switches the overlay off for good.
    prime("3")
    overlay_after_prime = overlay_visible()
    log("overlay after priming:", overlay_after_prime)

    # Quick swipes straight after each other (6 then 1, both turning the page): both count.
    log("quick swipes, page before:", which_page())
    swipe_from(6, "left", w, h)
    swipe_from(1, "right", w, h)
    wait(2.5)
    shot("quick-01-after-swipes")
    log("page after quick swipes:", which_page())
    log(sh("logcat -d | grep KeepGestures | tail -24"))
    launch()
    quick_line = celebrities_line()
    log("quick swipes: forced name on line", quick_line)

    # A swipe on the page with Keep Notes (here page 1, the last), where the page can't turn,
    # still counts: 6 turns to page 1, then 3 bounces.
    prime("4")
    swipe_from(6, "left", w, h)
    wait(3)
    swipe_from(3, "left", w, h)
    wait(3)
    shot("lastpage-01-after-swipes")
    log(sh("logcat -d | grep -E 'KeepGestures: (touch ended|Keep Notes page|digit|home screen unchanged)' | tail -12"))
    launch()
    last_page_line = celebrities_line()
    log("last-page swipe: forced name on line", last_page_line)

    sh("settings put secure enabled_accessibility_services ''")
    sh("settings put secure accessibility_enabled 0")

    # Fallback with the service off: two vertical swipes on the notes list enter the number.
    sh(f"am force-stop {PKG}")
    sh(f"run-as {PKG} rm -f shared_prefs/f.xml shared_prefs/c.xml")
    # Prime with the service off: it stays in the app.
    prime("fallback")
    back_to_notes()
    wait(1)
    x, y = cell(5, w, h)
    sh(f"input swipe {x} {y} {x} {int(h*0.05)} 300")   # 5
    wait(1)
    x, y = cell(8, w, h)
    sh(f"input swipe {x} {y} {x} {int(h*0.20)} 300")   # 8
    wait(1.5)
    # Back to the top (a lone third swipe can't make a number).
    sh(f"input swipe {w//2} {int(h*0.30)} {w//2} {int(h*0.95)} 120")
    wait(1.5)
    fallback_ok = False
    n = find_note("Celebrities")
    if n is None:
        log("not found: Celebrities")
        shot("fallback-00-no-celebrities")
    else:
        tap(n.cx, n.cy)
        wait(1.5)
        text = "\n".join(n.text for n in dump("fallback-celebrities"))
        fallback_ok = bool(re.search(r"(^|\n)58\. " + re.escape(FORCED) + r"(\n|$)", text))
        log("in-app fallback, line 58 shows", FORCED, ":", fallback_ok, "| found:",
            re.findall(r"\d+\. " + re.escape(FORCED), text))
        shot("fallback-01-celebrities")
        back()
    # The reveal switched the fallback off too. (Celebrities was closed: already on the notes.)
    if not find(dump(), desc="Open navigation drawer"):
        back_to_notes()
    x, y = cell(4, w, h)
    sh(f"input swipe {x} {y} {x} {int(h*0.08)} 300")   # 4
    wait(1)
    x, y = cell(5, w, h)
    sh(f"input swipe {x} {y} {x} {int(h*0.08)} 300")   # 5
    wait(1.5)
    for _ in range(2):
        sh(f"input swipe {w//2} {int(h*0.30)} {w//2} {int(h*0.95)} 120")
        wait(1.5)
    shot("fallback-02-after-more-swipes")
    dump("fallback-after-more-swipes")
    fallback_spent_line = celebrities_line()
    log("fallback after the reveal, forced name on line", fallback_spent_line)

    # The forced name can be changed in the hidden settings. Keanu Reeves is also one of the
    # other names, so he must move to the spectator's line and Tom Hanks take his old place.
    name_ok = False
    if open_hidden_settings():
        tap_node(dump(), text="Celebrity")
        wait(1.5)
        dismiss_stylus_sheet()
        sh("input keyevent KEYCODE_MOVE_END " + " ".join(["KEYCODE_DEL"] * 20))
        sh("input text 'Keanu%sReeves'")
        wait(0.5)
        shot("celebrity-01-dialog")
        tap_node(dump(), text="Save")
        wait(1)
        shot("celebrity-02-settings")
        back_to_notes()
        if tap_node(dump(), text="Celebrities"):
            wait(1.5)
            text = "\n".join(n.text for n in dump("celebrity-note"))
            keanu = re.findall(r"(?:^|\n)(\d+)\. Keanu Reeves(?=\n|$)", text)
            default = re.findall(r"(?:^|\n)(\d+)\. " + re.escape(FORCED) + r"(?=\n|$)", text)
            log("Keanu Reeves on lines", keanu, "|", FORCED, "on lines", default)
            name_ok = keanu == ["58"] and len(default) == 1
            shot("celebrity-03-note")
            back()

    results = {
        "not_listening_before_prime": te_unprimed == "0",
        "prime_lands_on_first_page": page_after_prime == 0,
        "touch_exploration_on_home": te == "1",
        "touch_exploration_off_in_app": te_in_app == "0",
        "off_after_reveal": off_after_reveal,
        "touch_exploration_back_on_after_prime": te_after == "1",
        "off_after_leaving_with_number": te_after_leave == "0",
        "practice_overlay_shown": overlay_on,
        "practice_swipe_not_counted": practice_ok,
        "overlay_off_after_prime": not overlay_after_prime,
        "touch_exploration_off_in_other_apps": te_other == "0" and te_home_again == "1",
        "icon_tap_opened_app": opened,
        "icon_tap_not_counted_as_digit": tap_not_counted,
        "home_has_two_pages": two_pages,
        "raw_touch_pages_launcher": raw_ok,
        "forced_name_on_line_61": ok_line,
        "in_app_fallback_line_58": fallback_ok,
        "fallback_off_after_reveal": fallback_spent_line == 58,
        "quick_swipes": quick_line == 61,
        "last_page_swipe": last_page_line == 63,
        "keypad_setup_works": setup_ok,
        "celebrity_setting_changes_name": name_ok,
        "gesture_nav_home_baseline": nav_base,
        "gesture_nav_home_with_service": nav_svc,
    }
    return results


def main():
    os.makedirs(OUT, exist_ok=True)
    adb("wait-for-device")
    global SDK
    SDK = int(sh("getprop ro.build.version.sdk") or 0)
    log("android", sh("getprop ro.build.version.release"), "sdk", SDK)
    log(sh("settings get secure navigation_mode"), "= navigation_mode (0 three-button, 2 gesture)")
    sh("settings put system screen_off_timeout 1800000")
    sh("settings put global hide_error_dialogs 1")
    sh("settings put secure stylus_handwriting_enabled 0")
    sh("svc power stayon true")
    w, h = screen_size()
    log("screen", w, h, "density", sh("wm density"))
    log(adb("install", "-r", "-g", "apk/app-debug.apk", timeout=180))

    # The user's phone runs at 3 px/dp (360dp wide): take the tour at the same density so the
    # screenshots line up with theirs, then go back for the home-screen test.
    sh("wm density 480")
    wait(3)
    ui_tour("dark")
    ui_tour("light")
    sh("cmd uimode night yes")
    sh("wm density reset")
    wait(3)

    icons = add_home_icons()
    log("icons added:", icons)
    results = swipe_test(w, h)
    log("RESULTS", results)
    adb("logcat", "-d", "-b", "main,system,crash")
    with open(os.path.join(OUT, "logcat.txt"), "w") as f:
        f.write(adb("logcat", "-d", "-b", "main,system,crash", timeout=120))
    with open(os.path.join(OUT, "log.txt"), "w") as f:
        f.write("\n".join(LOG))
    crashed = "FATAL EXCEPTION" in open(os.path.join(OUT, "logcat.txt")).read()
    log("app crashed:", crashed)
    must = ("forced_name_on_line_61", "icon_tap_opened_app", "prime_lands_on_first_page", "off_after_reveal")
    if crashed or not all(results[k] for k in must):
        sys.exit(1)


if __name__ == "__main__":
    main()
