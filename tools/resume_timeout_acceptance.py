"""Run with playback_acceptance_fixture.py --alt-delay 6 and the debug harness."""
import json
import time
from pathlib import Path
import playback_adb_acceptance as ui

out = Path("build/acceptance/ui-resume-fix")
out.mkdir(parents=True, exist_ok=True)
ui.launch()
ui.open_movie()
ui.pause()
ui.d.screenshot(str(out / "controls-new.png"))
ui.return_home()
assert ui.d(text="继续观看").wait(timeout=5)
# Cold app restart removes in-memory repository caches while retaining real history preferences.
ui.launch()
start = time.monotonic()
ui.click("验收剧集01")
ui.ensure_bar()
elapsed = time.monotonic() - start
first_position = ui.position()
assert elapsed < 4, (elapsed, ui.texts())
time.sleep(5)
ui.ensure_bar()
later_position = ui.position()
assert later_position > first_position, (first_position, later_position)
assert not any("Timed out" in text for text in ui.texts())
ui.pause()
ui.d.screenshot(str(out / "resume-after-timeout.png"))
(out / "resume-after-timeout.xml").write_text(ui.d.dump_hierarchy(), encoding="utf-8-sig")
result = {"status": "PASS", "backup_delay_seconds": 6, "cold_resume_seconds": round(elapsed, 2),
          "first_position": first_position, "position_after_backup_timeout": later_position}
(out / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8-sig")
print(result, flush=True)
