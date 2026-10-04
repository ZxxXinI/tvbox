"""ADB/UIAutomator acceptance against the debug-only local fixture activity."""
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
import uiautomator2 as u2

parser = argparse.ArgumentParser()
parser.add_argument("--serial", default="emulator-5554")
parser.add_argument("--output", type=Path, default=Path("build/acceptance/evidence"))
parser.add_argument("--start-at", type=int, default=1)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
d = u2.connect(args.serial)
results = []

def adb(*cmd):
    result = subprocess.run(["adb", "-s", args.serial, *cmd], capture_output=True, encoding="utf-8", errors="replace")
    output = result.stdout + result.stderr
    if result.returncode and not ("stopservice" in cmd and "Service stopped" in output):
        raise RuntimeError(output)
    return output

def nodes():
    return list(ET.fromstring(d.dump_hierarchy()).iter("node"))

def texts():
    return [n.get("text") for n in nodes() if n.get("text")]

def click(text):
    assert d(text=text).wait(timeout=15), (text, texts())
    d(text=text).click()
    time.sleep(.5)

def capture(name):
    (args.output / (name + ".xml")).write_text(d.dump_hierarchy(), encoding="utf-8-sig")
    d.screenshot(str(args.output / (name + ".png")))

def check(name, operation):
    try:
        detail = operation()
        capture(name)
        status = detail.get("status", "PASS") if isinstance(detail, dict) else "PASS"
        description = detail.get("detail", "") if isinstance(detail, dict) else detail
        results.append({"case": name, "status": status, "detail": description})
        print(status, name, description, flush=True)
    except Exception as exc:
        capture(name + "-failed")
        results.append({"case": name, "status": "FAIL", "detail": str(exc)})
        print("FAIL", name, str(exc), flush=True)
        raise
    finally:
        (args.output / "results.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8-sig")

def launch(theme="default"):
    adb("shell", "input", "keyevent", "224")
    adb("shell", "am", "force-stop", "com.tvbox.app")
    adb("shell", "am", "start", "-W", "-n", "com.tvbox.app/.AcceptanceActivity", "--es", "theme", theme)
    assert d(text="验收剧集01").wait(timeout=20), texts()

def ensure_bar():
    if "选集" not in texts():
        d.press("enter")
        time.sleep(.7)
    assert "选集" in texts(), texts()

def position():
    ensure_bar()
    times = [text for text in texts() if re.fullmatch(r"\d+:\d+(?::\d+)?", text)]
    assert len(times) >= 2, texts()
    values = [int(value) for value in times[0].split(":")]
    return sum(value * 60 ** power for power, value in enumerate(reversed(values)))

def pause():
    ensure_bar()
    if "暂停" in texts(): click("暂停")
    assert "播放" in texts(), texts()

def play():
    ensure_bar()
    if "播放" in texts(): click("播放")
    assert "暂停" in texts(), texts()

def open_movie():
    click("验收剧集01")
    if d(text="立即播放").wait(timeout=3): click("立即播放")
    time.sleep(2)
    ensure_bar()
    click("选集")
    d(textStartsWith="第1集").click()
    time.sleep(.6)
    assert position() >= 0

def return_home():
    for _ in range(6):
        if "历史(1)" in texts() or "继续观看" in texts(): return
        d.press("back")
        time.sleep(.5)
    raise AssertionError(texts())

def controls():
    open_movie()
    pause()
    d.press("back")
    assert "选集" not in texts()
    d.press("enter")
    time.sleep(.4)
    assert "选集" in texts() and "播放" in texts()
    focused = [n for n in nodes() if n.get("focused") == "true"]
    assert any(any(child.get("text") == "播放" for child in node.iter("node")) for node in focused)
    d.press("enter")
    time.sleep(.5)
    assert "暂停" in texts()
    click("选集")
    assert "选择集数" in texts()
    d.press("back")
    assert "选择集数" not in texts() and "选集" in texts()
    return "确认键先展开，第二次播放；选择面板返回仅关闭面板"

def seeking():
    pause()
    before = position()
    d.press("back")
    adb("shell", "input", "keyevent", "22")
    ensure_bar()
    after = position()
    assert 9 <= after - before <= 11, (before, after)
    d.press("back")
    time.sleep(.4)
    adb("shell", "CLASSPATH=/data/local/tmp/tvbox-keyprobe.jar", "app_process", "/", "com.tvbox.acceptance.KeyProbe", "22", "3")
    ensure_bar()
    long_after = position()
    assert long_after - after >= 19, (after, long_after)
    click("下一集")
    pause()
    assert any("第2集" in text and "·" in text for text in texts())
    d.press("back")
    adb("shell", "input", "keyevent", "8")
    ensure_bar()
    assert any("第1集" in text and "·" in text for text in texts())
    d.press("back")
    adb("shell", "input", "keyevent", "10")
    ensure_bar()
    assert any("第2集" in text and "·" in text for text in texts())
    adb("shell", "input", "keyevent", "82")
    assert d(text="1.25x").wait(timeout=3), texts()
    return f"左右10秒：{before}->{after}，长按：{after}->{long_after}；数字1/3切集与菜单倍速通过"

def history_return():
    launch()
    click("历史(1)")
    click("验收剧集01")
    time.sleep(.7)
    pause()
    d.press("back")
    d.press("back")
    assert d(text="返回").wait(timeout=5)
    click("返回")
    assert d(text="历史").wait(timeout=5), texts()
    return "从历史续播，退出播放和详情后返回历史页"

def touch_gestures():
    launch()
    open_movie()
    pause()
    before = position()
    d.press("back")
    time.sleep(.4)
    adb("shell", "input", "tap", "800", "350")
    assert d(text="选集").wait(timeout=3)
    d.press("back")
    time.sleep(.4)
    adb("shell", "input", "swipe", "700", "350", "1050", "350", "400")
    ensure_bar()
    after = position()
    assert after > before + 10, (before, after)
    d.press("back")
    adb("shell", "input", "swipe", "800", "350", "800", "350", "1000")
    ensure_bar()
    assert d(text="1x").exists or d(text="1.0x").exists, texts()
    return f"单击展开操作栏、滑动调整进度{before}->{after}，长按后恢复原倍速"

def screen_on():
    launch()
    open_movie()
    pause()
    timeout = adb("shell", "settings", "get", "system", "screen_off_timeout").strip()
    plugged = adb("shell", "settings", "get", "global", "stay_on_while_plugged_in").strip()
    try:
        adb("shell", "settings", "put", "system", "screen_off_timeout", "5000")
        adb("shell", "settings", "put", "global", "stay_on_while_plugged_in", "0")
        time.sleep(7)
        power = adb("shell", "dumpsys", "power")
        assert "mWakefulness=Awake" in power
        assert "KEEP_SCREEN_ON" in adb("shell", "dumpsys", "window", "windows")
        capture("screen-on-paused")
        return_home()
        hold = adb("shell", "dumpsys", "window", "windows")
        app_window = re.search(r"Window #[^\n]*com.tvbox.app/[^\n]*\n(.*?)(?=\n  Window #|\Z)", hold, re.S)
        assert app_window and "KEEP_SCREEN_ON" not in app_window[1]
        time.sleep(12)
        power = adb("shell", "dumpsys", "power")
        if "mWakefulness=Asleep" not in power and "mWakefulness=Dozing" not in power:
            effective = re.search(r"mScreenOffTimeoutSetting=(\d+)", power)
            if effective and int(effective[1]) > 60_000:
                return {"status": "PARTIAL", "detail": "暂停页亮屏标志与退出释放通过；ROM有效超时为" + effective[1] + "ms，自动熄屏未验证；原设置已恢复"}
            raise AssertionError(power)
    finally:
        adb("shell", "settings", "put", "system", "screen_off_timeout", timeout)
        adb("shell", "settings", "put", "global", "stay_on_while_plugged_in", plugged)
        adb("shell", "input", "keyevent", "224")
    return "5秒自动锁屏下，暂停播放页仍亮屏；退出后自动熄屏；原设置已恢复"

def source_switch():
    pause()
    before = position()
    click("线路")
    assert "选择线路" in texts()
    options = [text for text in texts() if text.startswith("验收备用") and "✓" not in text]
    assert options, texts()
    click(options[0])
    time.sleep(1)
    assert "播放" in texts()
    after = position()
    assert abs(after - before) <= 1, (before, after)
    assert any("第2集" in text and "·" in text for text in texts())
    return f"暂停换线保留集数与进度：{before}->{after}"

def background():
    play()
    time.sleep(1)
    d.press("home")
    time.sleep(1)
    adb("shell", "am", "start", "-n", "com.tvbox.app/.AcceptanceActivity")
    time.sleep(.7)
    assert "播放" in texts(), texts()
    before = position()
    time.sleep(2)
    after = position()
    assert before == after, (before, after)
    return f"回前台保持暂停，进度固定为{after}秒"

def audio_focus():
    play()
    adb("shell", "am", "start-foreground-service", "-n", "com.tvbox.acceptance.probe/.FocusService")
    assert d(text="播放").wait(timeout=5), texts()
    before = position()
    adb("shell", "am", "stopservice", "-n", "com.tvbox.acceptance.probe/.FocusService")
    time.sleep(1)
    assert "播放" in texts()
    assert position() == before
    return "焦点抢占后暂停，释放焦点后未自动播放"

def continue_watching():
    return_home()
    assert d(text="继续观看").wait(timeout=5), texts()
    capture("continue-home")
    # The first matching film title belongs to the horizontal continue row.
    click("验收剧集01")
    time.sleep(1)
    ensure_bar()
    assert position() > 0
    pause()
    return_home()
    launch()
    assert d(text="继续观看").wait(timeout=5)
    return "首页直接续播，进度和入口在应用重启后保留"

def home_focus():
    d.swipe(800, 760, 800, 250, .5)
    time.sleep(.7)
    visible = [n for n in nodes() if re.fullmatch("验收剧集\\d+", n.get("text", ""))]
    assert visible
    target = visible[-1].get("text")
    before_bounds = visible[-1].get("bounds")
    click(target)
    click("返回")
    time.sleep(.8)
    assert d(text=target).exists, (target, texts())
    d.press("enter")
    assert d(text="立即播放").wait(timeout=5) and d(text=target).exists
    click("返回")
    return f"返回原影片焦点：{target}，原文本位置{before_bounds}"

def search_focus():
    launch()
    click("搜索(2)")
    d(className="android.widget.EditText").set_text("验收")
    d(text="搜索", instance=1).click()
    time.sleep(.5)
    assert d(text="验收剧集01").wait(timeout=10)
    d.swipe(800, 770, 800, 260, .5)
    time.sleep(.7)
    films = [n.get("text") for n in nodes() if re.fullmatch("验收剧集\\d+", n.get("text", ""))]
    target = films[-1]
    click(target)
    click("返回")
    time.sleep(.6)
    assert d(text=target).exists
    d.press("enter")
    assert d(text="立即播放").wait(timeout=5) and d(text=target).exists
    click("返回")
    return f"搜索结果返回定位：{target}"

def live(kind):
    launch()
    click("电视(4)" if kind == "tv" else "直播(5)")
    if kind == "platform":
        click("验收平台")
        click("验收分类")
        click("验收子分类")
        click("验收直播间")
    time.sleep(2)
    d.press("home")
    time.sleep(.6)
    adb("shell", "am", "start", "-n", "com.tvbox.app/.AcceptanceActivity")
    assert d(text="继续播放").wait(timeout=6), texts()
    click("继续播放")
    time.sleep(1)
    assert not d(text="继续播放").exists
    window = adb("shell", "dumpsys", "power")
    assert "mWakefulness=Awake" in window
    return "后台返回需手动恢复；恢复后保持屏幕唤醒"

if __name__ == "__main__":
    launch()
    if 1 < args.start_at <= 5: open_movie()
    for index, (name, op) in enumerate([
        ("01-controls", controls), ("02-seek-shortcuts", seeking), ("03-source-position", source_switch),
        ("04-background", background), ("05-audio-focus", audio_focus),
        ("06-continue-watching", continue_watching), ("07-home-focus", home_focus),
        ("08-search-focus", search_focus), ("09-tv-lifecycle", lambda: live("tv")),
        ("10-platform-lifecycle", lambda: live("platform")),
        ("12-history-return", history_return), ("13-screen-on", screen_on),
        ("14-touch-gestures", touch_gestures),
    ], 1):
        if index >= args.start_at: check(name, op)
    launch("cinema")
    check("11-cinema-continue", lambda: "影院主题继续观看可见" if d(text="继续观看").wait(timeout=8) else (_ for _ in ()).throw(AssertionError(texts())))
