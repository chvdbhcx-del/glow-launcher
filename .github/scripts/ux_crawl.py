#!/usr/bin/env python3
"""UX audit crawler: on a running emulator with Glow installed, reaches each main
launcher state, taps every clickable element there (one per run, resetting the
state in between) and records a screenshot, the UI tree, the foreground window
and any crash. Output: audit/ (PNGs, XML, report.json, report.md)."""
import hashlib, json, os, re, subprocess, sys, time

PKG = "app.glowlauncher"
OUT = "audit"
os.makedirs(OUT, exist_ok=True)
T0 = time.time()
BUDGET = int(os.environ.get("CRAWL_BUDGET_S", "3000"))
PER_STATE = int(os.environ.get("CRAWL_PER_STATE", "22"))
# Never tap these: they change the layout the rest of the walk depends on, or wipe data
SKIP = re.compile(r"(uninstall|remove|delete|reset|restore|restart|clear|удал|сброс|восстан|"
                  r"перезапус|kill|force stop|backup|резерв)", re.I)
report = []


def sh(*a, timeout=30):
    try:
        return subprocess.run(["adb", "shell", *a], capture_output=True, text=True, timeout=timeout).stdout
    except subprocess.TimeoutExpired:
        return ""


def shot(name):
    with open(f"{OUT}/{name}.png", "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, timeout=30)


def dump():
    for _ in range(3):
        sh("uiautomator", "dump", "/sdcard/ui.xml")
        x = subprocess.run(["adb", "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True, text=True).stdout
        if "<hierarchy" in x:
            return x
        time.sleep(1)
    return ""


def nodes(xml):
    res = []
    for n in re.findall(r"<node [^>]*?/?>", xml):
        g = lambda k: (re.search(rf' {k}="([^"]*)"', n) or [None, ""])[1]
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if not b:
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        res.append(dict(text=g("text"), desc=g("content-desc"), rid=g("resource-id"), cls=g("class"),
                        pkg=g("package"), clickable=g("clickable") == "true",
                        long=g("long-clickable") == "true", checkable=g("checkable") == "true",
                        checked=g("checked"), bounds=[x1, y1, x2, y2], w=x2 - x1, h=y2 - y1))
    return res


def label(n):
    return n["text"] or n["desc"] or n["rid"].split("/")[-1] or n["cls"].split(".")[-1]


def focus():
    m = re.search(r"mCurrentFocus=Window\{\S+ \S+ ([^}]*)\}", sh("dumpsys", "window"))
    return m.group(1) if m else "?"


def crashes():
    return subprocess.run(["adb", "logcat", "-d", "-b", "crash"], capture_output=True, text=True).stdout


def tap(x, y):
    sh("input", "tap", str(x), str(y))


def find(xml, *wants):
    for w in wants:
        for n in nodes(xml):
            if w and (n["text"] == w or n["desc"] == w):
                b = n["bounds"]
                return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
    return None


def tap_text(*wants):
    xy = find(dump(), *wants)
    if xy:
        tap(*xy)
        return True
    return False


size = re.findall(r"(\d+)x(\d+)", sh("wm", "size"))[-1]
W, H = int(size[0]), int(size[1])
DP = int((re.findall(r"(\d+)", sh("wm", "density")) or ["420"])[-1]) / 160.0


def home():
    sh("input", "keyevent", "KEYCODE_HOME"); time.sleep(1.2)
    sh("input", "keyevent", "KEYCODE_HOME"); time.sleep(1.5)


def to_library():
    home()
    for _ in range(5):
        sh("input", "swipe", str(W * 88 // 100), str(H // 2), str(W * 8 // 100), str(H // 2), "220")
        time.sleep(2.2)
        if "A–Z" in dump():
            return True
    return False


def to_az():
    return to_library() and tap_text("A–Z") and not time.sleep(2)


def to_lib_search():
    if not to_library():
        return False
    x = dump()
    for n in nodes(x):
        if "EditText" in n["cls"] or n["text"] in ("App Library",):
            b = n["bounds"]; tap((b[0] + b[2]) // 2, (b[1] + b[3]) // 2); time.sleep(2)
            sh("input", "text", "c"); time.sleep(2)
            return True
    return False


def to_folder():
    home()
    ok = tap_text("Google", "Folder: Google")
    time.sleep(2)
    return ok


def to_app_popup():
    home()
    x = dump()
    # first app icon on the workspace/dock that isn't a folder
    for n in nodes(x):
        if n["pkg"] == PKG and n["desc"] and n["clickable"] and "Folder" not in n["desc"] and n["h"] > 60 * DP / 2.6 and n["w"] < W / 2:
            b = n["bounds"]
            cx, cy = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
            sh("input", "swipe", str(cx), str(cy), str(cx), str(cy), "900")
            time.sleep(2)
            return True
    return False


def to_edit():
    home()
    sh("input", "swipe", str(W // 2), str(H * 30 // 100), str(W // 2), str(H * 30 // 100), "1500")
    time.sleep(2.5)
    return True


def to_edit_menu():
    to_edit()
    ok = tap_text("Edit")
    time.sleep(2)
    return ok


def to_customize():
    return to_edit_menu() and tap_text("Customize") and not time.sleep(2.5)


def to_widgets():
    return to_edit_menu() and tap_text("Add Widget", "Widgets") and not time.sleep(3.5)


def to_pill():
    home()
    return tap_text("Search") and not time.sleep(2.5)


def settings(path=()):
    def go():
        home()
        sh("am", "start", "-n", f"{PKG}/app.lawnchair.ui.preferences.PreferenceActivity"); time.sleep(4)
        for p in path:
            if not tap_text(p):
                return False
            time.sleep(2.5)
        return True
    return go


def crawl(name, reach, per_state=PER_STATE, extra=()):
    if time.time() - T0 > BUDGET:
        report.append(dict(state=name, skipped="time budget")); return []
    ok = reach()
    time.sleep(0.5)
    base = dump()
    shot(f"{name}__00-base")
    open(f"{OUT}/{name}__00-base.xml", "w").write(base)
    ns = nodes(base)
    entry = dict(state=name, reached=bool(ok), focus=focus(),
                 texts=[label(n) for n in ns if (n["text"] or n["desc"])],
                 small_targets=[f'{label(n)} {round(n["w"]/DP)}x{round(n["h"]/DP)}dp' for n in ns
                                if n["clickable"] and (n["w"] < 44 * DP or n["h"] < 44 * DP)],
                 unlabeled=[n["bounds"] for n in ns if n["clickable"] and not (n["text"] or n["desc"])],
                 taps=[])
    targets, seen = [], set()
    for n in ns:
        if not (n["clickable"] or n["long"]) or n["w"] < 8 or n["h"] < 8:
            continue
        if n["w"] >= W * 0.98 and n["h"] >= H * 0.6:
            continue  # the full-screen container itself
        key = tuple(n["bounds"])
        if key in seen:
            continue
        seen.add(key)
        if SKIP.search(label(n)):
            entry["taps"].append(dict(target=label(n), skipped="destructive")); continue
        targets.append(n)
    targets = list(extra) + targets
    print(f"[{name}] reached={ok} targets={len(targets)}", flush=True)
    for i, n in enumerate(targets[:per_state], 1):
        if time.time() - T0 > BUDGET:
            entry["taps"].append(dict(target="…", skipped="time budget")); break
        if i > 1:
            reach(); time.sleep(0.4)
        before = crashes()
        b = n["bounds"]
        cx, cy = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
        tap(cx, cy); time.sleep(2.2)
        after = dump()
        tag = f"{name}__{i:02d}"
        shot(tag)
        new_crash = crashes()[len(before):] if len(crashes()) > len(before) else ""
        f = focus()
        changed = hashlib.md5(re.sub(r'bounds="[^"]*"', "", after).encode()).hexdigest() != \
            hashlib.md5(re.sub(r'bounds="[^"]*"', "", base).encode()).hexdigest()
        rec = dict(n=i, target=label(n), bounds=b, shot=f"{tag}.png", focus=f, changed=changed,
                   crash=new_crash[:1500], new_texts=[t for t in [label(x) for x in nodes(after) if x["text"] or x["desc"]]
                                                       if t not in entry["texts"]][:25])
        if n.get("checkable"):
            tap(cx, cy); time.sleep(1)  # put the switch back
        entry["taps"].append(rec)
        print(f"  {i:02d} {label(n)[:40]!r} -> {f[:60]} changed={changed} crash={bool(new_crash)}", flush=True)
        if PKG not in f:
            sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(1)
    report.append(entry)
    return entry["texts"]


def main():
    subprocess.run(["adb", "logcat", "-b", "crash", "-c"])
    home(); time.sleep(3)
    shot("00-home")
    crawl("01-home", home)
    crawl("02-search-pill", to_pill)
    crawl("03-folder", to_folder)
    crawl("04-app-popup", to_app_popup)
    crawl("05-edit-mode", to_edit)
    crawl("06-edit-menu", to_edit_menu)
    crawl("07-customize", to_customize, per_state=30)
    crawl("08-widgets", to_widgets, per_state=10)
    crawl("09-library", to_library, per_state=26)
    crawl("10-library-az", to_az, per_state=12)
    crawl("11-library-search", to_lib_search, per_state=10)
    root_texts = crawl("12-settings", settings(), per_state=30)
    # Every top-level settings screen: base + its controls
    skip_root = {"Glow", "Settings", "Search", "Lawnchair"}
    tops = [t for t in root_texts if t not in skip_root][:16]
    for j, t in enumerate(tops, 1):
        crawl(f"13-settings-{j:02d}-{re.sub(r'[^A-Za-z0-9]+', '_', t)[:24]}", settings((t,)), per_state=14)
    # Gestures on home: double tap, swipe down, two-finger-ish pinch not possible with input; swipe up
    home(); sh("input", "tap", str(W // 2), str(H * 40 // 100)); sh("input", "tap", str(W // 2), str(H * 40 // 100))
    time.sleep(2); shot("20-gesture-double-tap"); r = dict(state="20-gestures", focus_double_tap=focus())
    home(); sh("input", "swipe", str(W // 2), str(H * 30 // 100), str(W // 2), str(H * 75 // 100), "250")
    time.sleep(2); shot("21-gesture-swipe-down"); r["focus_swipe_down"] = focus()
    home(); sh("input", "swipe", str(W // 2), str(H * 75 // 100), str(W // 2), str(H * 25 // 100), "250")
    time.sleep(2); shot("22-gesture-swipe-up"); r["focus_swipe_up"] = focus()
    home(); sh("input", "swipe", str(W * 10 // 100), str(H // 2), str(W * 90 // 100), str(H // 2), "220")
    time.sleep(2); shot("23-gesture-swipe-right-first-page"); r["focus_swipe_right"] = focus()
    report.append(r)
    # Light wallpaper legibility: a white image as wallpaper is not settable from shell; record dark only.
    json.dump(report, open(f"{OUT}/report.json", "w"), ensure_ascii=False, indent=1)
    with open(f"{OUT}/report.md", "w") as f:
        for e in report:
            f.write(f"\n## {e['state']}  reached={e.get('reached')} focus={e.get('focus')}\n")
            if e.get("small_targets"):
                f.write(f"small targets (<44dp): {e['small_targets']}\n")
            if e.get("unlabeled"):
                f.write(f"clickable without label: {len(e['unlabeled'])}\n")
            for t in e.get("taps", []):
                f.write(f"- {t.get('n','')} {t['target']!r}: {t.get('skipped') or ('changed' if t['changed'] else 'NO CHANGE')}"
                        f" focus={t.get('focus','')} {'CRASH' if t.get('crash') else ''} new={t.get('new_texts', [])[:8]}\n")
            for k, v in e.items():
                if k.startswith("focus_"):
                    f.write(f"- {k}: {v}\n")
    print(f"done in {int(time.time() - T0)}s", flush=True)


if __name__ == "__main__":
    main()
