#!/usr/bin/env python3
"""Build Dose with Gradle, including official Google Identity AAR resources and manifests."""
import argparse, json, os, pathlib, secrets, shutil, subprocess

ROOT = pathlib.Path(__file__).resolve().parents[1]
VERSION_NAME = '1.7.0'
p = argparse.ArgumentParser()
p.add_argument('--sdk', default=os.environ.get('ANDROID_SDK_ROOT', os.environ.get('ANDROID_HOME', '')))
p.add_argument('--test', action='store_true')
p.add_argument('--google-config', help='Private developer JSON; see GOOGLE-DRIVE-CONFIGURACAO.md')
args = p.parse_args()
if not args.sdk:
    p.error('Informe --sdk ou ANDROID_SDK_ROOT.')
sdk = pathlib.Path(args.sdk).resolve()
if not (sdk / 'platforms/android-37.0/android.jar').exists() and not (sdk / 'platforms/android-37/android.jar').exists():
    p.error('Instale a plataforma Android 37.0 e build-tools 37.0.0.')
signing = ROOT / 'signing'
signing.mkdir(exist_ok=True)
config, keystore = signing / 'local-signing.json', signing / 'dose-personal.jks'
if not config.exists():
    if keystore.exists():
        raise SystemExit('Restaure local-signing.json da mesma entrega; a chave existente não será substituída.')
    config.write_text(json.dumps({'alias': 'dose-personal', 'password': secrets.token_urlsafe(32)}))
    config.chmod(0o600)
key = json.loads(config.read_text())
env = {**os.environ, 'ANDROID_SDK_ROOT': str(sdk), 'DOSE_SIGNING_PASSWORD': key['password']}
if not keystore.exists():
    subprocess.run(['keytool', '-genkeypair', '-keystore', str(keystore), '-storepass:env', 'DOSE_SIGNING_PASSWORD', '-keypass:env', 'DOSE_SIGNING_PASSWORD', '-alias', key['alias'], '-keyalg', 'RSA', '-keysize', '3072', '-validity', '36500', '-dname', 'CN=Dose Personal,OU=Personal Android App,O=Dose,C=BR'], env=env, check=True)
    keystore.chmod(0o600)
gradle = os.environ.get('DOSE_GRADLE') or str(ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew'))
cmd = [gradle, '--no-daemon', '--console', 'plain', ':app:assembleRelease']
if args.test:
    cmd.append(':app:testReleaseUnitTest')
if args.google_config:
    cmd.append('-PdoseGoogleConfig=' + str(pathlib.Path(args.google_config).resolve()))
subprocess.run(cmd, cwd=ROOT, env=env, check=True)
out = ROOT / 'build'
out.mkdir(exist_ok=True)
dest = out / ('Dose-' + VERSION_NAME + '.apk')
shutil.copy2(ROOT / 'app/build/outputs/apk/release/app-release.apk', dest)
subprocess.run([str(sdk / 'build-tools/37.0.0/apksigner'), 'verify', '--verbose', str(dest)], env=env, check=True)
print('APK:', dest)
if args.test:
    classes = out / 'test-classes'
    classes.mkdir(exist_ok=True)
    sources = [ROOT / ('app/src/main/java/br/com/well/dose/' + name + '.java') for name in ['Medication','MedicineStyle','ScheduleRules']]
    sources.append(ROOT / 'app/src/test/java/br/com/well/dose/ScheduleTests.java')
    subprocess.run(['java', 'com.sun.tools.javac.Main', '-encoding', 'UTF-8', '-d', str(classes), *map(str,sources)], check=True)
    subprocess.run(['java', '-cp', str(classes), 'br.com.well.dose.ScheduleTests'], check=True)
