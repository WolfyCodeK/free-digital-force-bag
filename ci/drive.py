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


def tap_node(nodes, **kw):
    n = find(nodes, **kw)
    if n is None:
        log("not found:", kw)
        return False
    tap(n.cx, n.cy)
    return True


def step(name, settle=1.2, **kw):
    """Taps the element matching kw on a fresh dump, waits, then takes a screenshot."""
    nodes = dump()
    ok = tap_node(nodes, **kw)
    wait(settle)
    shot(name)
    return ok


def back():
    sh("input keyevent KEYCODE_BACK")
    wait(0.8)


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
    if step(f"{mode}-02-celebrities", text="Celebrities"):
        sh("input swipe 540 1800 540 600 300")
        wait(1)
        shot(f"{mode}-03-celebrities-scrolled")
        back()
    if tour == "basic":
        return
    run_full_tour(mode)


def run_full_tour(mode):
    p = mode
    w, h = screen_size()
    # Celebrities opened again, scrolled to the middle.
    # Drawer
    if step(f"{p}-10-drawer", desc="Open navigation drawer"):
        back()
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
        wait(3)
    # Long-press selection and its colour sheet.
    nodes = dump()
    n = find(nodes, text="Gift ideas")
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
    if step(f"{p}-25-checklist", text="Packing"):
        if step(f"{p}-26-add-sheet", desc="Add"):
            back()
        if step(f"{p}-27-colour-sheet", desc_has="Change col"):
            back()
        if step(f"{p}-28-more-sheet", desc="More options"):
            back()
        if step(f"{p}-29-format", desc="Formatting options"):
            back()
        back()
    # A text note.
    if step(f"{p}-30-text-note", text="To do"):
        if step(f"{p}-31-reminder-sheet", desc="Reminder"):
            back()
        back()
    # Swipe a note to archive it.
    nodes = dump()
    n = find(nodes, text="Books to read") or find(nodes, text="Pasta bake")
    if n:
        sh(f"input swipe {n.cx} {n.cy} {w - 10} {n.cy} 250")
        wait(0.6)
        shot(f"{p}-32-swiped-archive")
        wait(4)
    # Drawer destinations.
    for i, label in enumerate(["Reminders", "Archive", "Deleted", "Create new label", "Settings", "Help & feedback"]):
        nodes = dump()
        if tap_node(nodes, desc="Open navigation drawer"):
            wait(1)
            step(f"{p}-4{i}-{label.split()[0].lower()}", desc=label, settle=1.5)
            back()
            wait(0.5)
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
    for ms in (120, 450):
        sh(f"input swipe {w//2} {h-3} {w//2} {int(h*0.4)} {ms}")
        wait(3)
        focus = top_package()
        ok = home_package() in focus
        log("  bottom swipe", ms, "ms ->", "home" if ok else "not home")
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
    home()
    nodes = dump("home-page0b")
    icon = find(nodes, text="Keep Notes") or icon
    nav_base = gesture_home(w, h, "without-service")

    # The UI tour opened the Celebrities note, which locks the number for 3 minutes. Start the
    # swipe test from a clean slate.
    sh(f"am force-stop {PKG}")
    sh(f"run-as {PKG} rm -f shared_prefs/f.xml shared_prefs/c.xml")
    sh("setprop log.tag.KeepGestures DEBUG")
    enable_service()
    home()
    wait(2)
    te = touch_exploration()
    log("touch exploration on home with service:", te)

    # Digit 6: second row, right cell; swipe left to page 1. (Not the top row of page 0:
    # Pixel's "At a glance" widget there pages its own cards.)
    sh(f"input swipe {int(w*0.83)} {int(h*0.40)} {int(w*0.10)} {int(h*0.40)} 300")
    wait(2.5)
    shot("home-03-after-digit6")
    log("page after digit 6 swipe:", which_page())
    # Digit 1: top row, left cell of page 1; swipe right back to page 0.
    sh(f"input swipe {int(w*0.17)} {int(h*0.17)} {int(w*0.90)} {int(h*0.17)} 300")
    wait(2.5)
    shot("home-04-after-digit1")
    log("page after digit 1 swipe:", which_page())

    # Taps must still reach the launcher: open the app from its icon.
    opened = False
    if icon:
        tap(icon.cx, icon.cy)
        wait(3)
        opened = PKG in top_package()
    if not opened:
        log("icon tap did not open the app; launching directly")
        launch()
    log("tap on home icon opened app:", opened)
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

    # Leaving the app must switch touch exploration back on for the home screen.
    home()
    wait(2)
    te_after = touch_exploration()
    log("touch exploration after leaving the app:", te_after)
    # Gesture navigation must still work with the service running (outside the app).
    nav_svc = gesture_home(w, h, "with-service")
    log(sh("logcat -d | grep -E 'KeepGestures|TouchExplorer' | tail -60"))

    sh("settings put secure enabled_accessibility_services ''")
    sh("settings put secure accessibility_enabled 0")
    results = {
        "touch_exploration_on_home": te == "1",
        "touch_exploration_off_in_app": te_in_app == "0",
        "touch_exploration_back_on_after_app": te_after == "1",
        "icon_tap_opened_app": opened,
        "home_has_two_pages": two_pages,
        "forced_name_on_line_61": ok_line,
        "gesture_nav_home_baseline": nav_base,
        "gesture_nav_home_with_service": nav_svc,
    }
    return results


def main():
    os.makedirs(OUT, exist_ok=True)
    adb("wait-for-device")
    log("android", sh("getprop ro.build.version.release"), "sdk", sh("getprop ro.build.version.sdk"))
    log(sh("settings get secure navigation_mode"), "= navigation_mode (0 three-button, 2 gesture)")
    sh("settings put system screen_off_timeout 1800000")
    sh("svc power stayon true")
    w, h = screen_size()
    log("screen", w, h, "density", sh("wm density"))
    log(adb("install", "-r", "-g", "apk/app-debug.apk", timeout=180))

    ui_tour("dark")
    ui_tour("light")
    sh("cmd uimode night yes")

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
    if crashed or not results["forced_name_on_line_61"] or not results["icon_tap_opened_app"]:
        sys.exit(1)


if __name__ == "__main__":
    main()
