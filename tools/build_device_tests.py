#!/usr/bin/env python3
"""Build the isolated instrumentation APK with the same Gradle dependency graph."""
import os, pathlib, shutil, subprocess, sys
root = pathlib.Path(__file__).resolve().parents[1]
if len(sys.argv) != 2:
    raise SystemExit('Uso: python3 tools/build_device_tests.py /caminho/android-sdk')
env = {**os.environ, 'ANDROID_SDK_ROOT': str(pathlib.Path(sys.argv[1]).resolve())}
gradle = os.environ.get('DOSE_GRADLE') or str(root / ('gradlew.bat' if os.name == 'nt' else 'gradlew'))
subprocess.run([gradle, '--no-daemon', '--console', 'plain', ':app:assembleReleaseAndroidTest'], cwd=root, env=env, check=True)
out = root / 'build/device-tests/dose-tests.apk'
out.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(root / 'app/build/outputs/apk/androidTest/release/app-release-androidTest.apk', out)
print(out)
