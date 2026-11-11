#!/usr/bin/env python3
"""Run on a dedicated AVD only: instrumentation replaces the app's data with fixtures."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument('--sdk', required=True)
p.add_argument('--avd', required=True)
p.add_argument('--port', type=int, default=5560)
p.add_argument('--adb-port', type=int, default=5038)
args = p.parse_args()
sdk = Path(args.sdk).resolve()
out = ROOT / 'build/validation-1.7.5'
out.mkdir(parents=True, exist_ok=True)
env = {**os.environ, 'ANDROID_SDK_ROOT': str(sdk), 'ANDROID_ADB_SERVER_PORT': str(args.adb_port)}
adb = sdk / 'platform-tools/adb'
serial = 'emulator-' + str(args.port)


def ad(*command, timeout=90, check=True):
    result = subprocess.run([str(adb), '-P', str(args.adb_port), '-s', serial, *command],
                            env=env, capture_output=True, text=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout + result.stderr


def instrument(name, runner, mode=None):
    command = [str(adb), '-P', str(args.adb_port), '-s', serial, 'shell', 'am', 'instrument', '-r', '-w']
    if mode:
        command += ['-e', 'mode', mode]
    command += ['br.com.well.dose.test/br.com.well.dose.' + runner]
    print('RUN', name, flush=True)
    path = out / (name + '.txt')
    with path.open('w') as log:
        result = subprocess.run(command, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=420)
    text = path.read_text()
    print(text[-16000:], flush=True)
    if result.returncode or 'INSTRUMENTATION_RESULT: result=PASS' not in text:
        raise RuntimeError('Android checks failed: ' + name)
    summary[name] = 'PASS'


summary = {}
subprocess.run([str(adb), '-P', str(args.adb_port), 'start-server'], env=env, check=True, capture_output=True)
log = (out / 'emulator.log').open('w')
emu = subprocess.Popen([str(sdk / 'emulator/emulator'), '-avd', args.avd, '-port', str(args.port),
                        '-no-window', '-gpu', 'swiftshader_indirect', '-no-audio', '-no-snapshot',
                        '-accel', 'off', '-cores', '2', '-memory', '1536', '-no-boot-anim',
                        '-netfast', '-crash-report-mode', 'disabled'],
                       env=env, stdout=log, stderr=subprocess.STDOUT)
try:
    deadline = time.monotonic() + 600
    while time.monotonic() < deadline:
        if emu.poll() is not None:
            raise RuntimeError((out / 'emulator.log').read_text()[-4000:])
        try:
            if ad('shell', 'getprop', 'sys.boot_completed', timeout=10, check=False).strip() == '1':
                break
        except subprocess.TimeoutExpired:
            pass
        time.sleep(4)
    else:
        raise RuntimeError('Emulator boot timed out')
    print('BOOT READY', flush=True)
    for key in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']:
        ad('shell', 'settings', 'put', 'global', key, '0')
    ad('shell', 'settings', 'put', 'system', 'screen_off_timeout', '2147483647')
    ad('shell', 'settings', 'put', 'global', 'stay_on_while_plugged_in', '3')
    ad('shell', 'input', 'keyevent', '224')
    ad('shell', 'wm', 'dismiss-keyguard')
    ad('shell', 'cmd', 'alarm', 'set-timezone', 'America/Sao_Paulo', check=False)
    print(ad('install', '--no-incremental', '-r', str(ROOT / 'build/Dose-1.7.5.apk')), flush=True)
    print(ad('install', '--no-incremental', '-r', '-t', str(ROOT / 'build/device-tests/dose-tests.apk')), flush=True)
    ad('shell', 'pm', 'grant', 'br.com.well.dose', 'android.permission.POST_NOTIFICATIONS')
    for operation in ['SCHEDULE_EXACT_ALARM', 'USE_FULL_SCREEN_INTENT', 'SYSTEM_ALERT_WINDOW']:
        ad('shell', 'appops', 'set', 'br.com.well.dose', operation, 'allow', check=False)
    ad('shell', 'cmd', 'notification', 'allow_dnd', 'br.com.well.dose')
    ad('shell', 'cmd', 'notification', 'set_dnd', 'all')
    instrument('sound-scroll', 'SoundScrollTests')
    instrument('snooze-time', 'DeviceTests', 'snooze_time')
    for mode in ['all', 'backups', 'regression', 'alarms']:
        instrument(mode, 'DeviceTests', mode)
    errors = ad('logcat', '-d', '-s', 'AndroidRuntime:E', check=False)
    (out / 'android-errors.txt').write_text(errors)
    if 'FATAL EXCEPTION' in errors:
        raise RuntimeError('Android reported a fatal exception')
    print('ALL REQUESTED AND REGRESSION CHECKS PASSED', flush=True)
finally:
    (out / 'summary.json').write_text(json.dumps(summary, indent=2))
    try:
        (out / 'android-errors.txt').write_text(ad('logcat', '-d', '-s', 'AndroidRuntime:E', timeout=10, check=False))
    except Exception:
        pass
    emu.terminate()
    try:
        emu.wait(timeout=15)
    except subprocess.TimeoutExpired:
        emu.kill()
