#!/usr/bin/env python3
"""Dependency-free APK build with the official Android SDK and a Java 17 JDK."""
import argparse, json, os, pathlib, secrets, subprocess, zipfile, shutil, xml.etree.ElementTree as ET

ROOT=pathlib.Path(__file__).resolve().parents[1]
VERSION_NAME='1.5.0'
VERSION_CODE='7'
p=argparse.ArgumentParser()
p.add_argument('--sdk',default=os.environ.get('ANDROID_SDK_ROOT',os.environ.get('ANDROID_HOME','')))
p.add_argument('--test',action='store_true')
args=p.parse_args()
sdk=pathlib.Path(args.sdk).resolve()
platform=sdk/'platforms/android-37.0/android.jar'
if not platform.exists(): platform=sdk/'platforms/android-37/android.jar'
bt=sdk/'build-tools/37.0.0'
if not platform.exists() or not (bt/'aapt2').exists():
    raise SystemExit('Install platforms;android-37.0 and build-tools;37.0.0; pass --sdk /absolute/sdk/path.')
out=ROOT/'build'
out.mkdir(exist_ok=True)
def run(*cmd): subprocess.run([str(x) for x in cmd],cwd=ROOT,check=True)
classes=out/'classes';gen=out/'generated';dex=out/'dex'
for folder in [classes,gen,dex]:
    if folder.exists():shutil.rmtree(folder)
    folder.mkdir()
run(bt/'aapt2','compile','--dir',ROOT/'app/src/main/res','-o',out/'resources.zip')
ET.register_namespace('android','http://schemas.android.com/apk/res/android')
manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml');manifest.getroot().set('package','br.com.well.dose');manifest.write(out/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
run(bt/'aapt2','link','-I',platform,'--manifest',out/'AndroidManifest.xml','--java',gen,'--min-sdk-version','26','--target-sdk-version','37','--version-code',VERSION_CODE,'--version-name',VERSION_NAME,'-o',out/'base.apk',out/'resources.zip')
sources=list((ROOT/'app/src/main/java').rglob('*.java'))+list(gen.rglob('*.java'))
run('java','com.sun.tools.javac.Main','-encoding','UTF-8','-source','8','-target','8','-bootclasspath',str(platform)+os.pathsep+str(bt/'core-lambda-stubs.jar'),'-d',classes,*sources)
with zipfile.ZipFile(out/'classes.jar','w',zipfile.ZIP_DEFLATED) as archive:
    for file in classes.rglob('*.class'):archive.write(file,file.relative_to(classes))
run(bt/'d8','--lib',platform,'--min-api','26','--output',dex,out/'classes.jar')
shutil.copy2(out/'base.apk',out/'unaligned.apk')
with zipfile.ZipFile(out/'unaligned.apk','a',zipfile.ZIP_DEFLATED) as archive:
    for file in dex.glob('*.dex'):archive.write(file,file.name)
run(bt/'zipalign','-f','-p','4',out/'unaligned.apk',out/'aligned.apk')
signing=ROOT/'signing';signing.mkdir(exist_ok=True)
config=signing/'local-signing.json';keystore=signing/'dose-personal.jks'
if not config.exists():
    config.write_text(json.dumps({'alias':'dose-personal','password':secrets.token_urlsafe(32)}))
    config.chmod(0o600)
key=json.loads(config.read_text())
env={**os.environ,'DOSE_SIGNING_PASSWORD':key['password']}
if not keystore.exists():
    subprocess.run(['keytool','-genkeypair','-keystore',str(keystore),'-storepass:env','DOSE_SIGNING_PASSWORD','-keypass:env','DOSE_SIGNING_PASSWORD','-alias',key['alias'],'-keyalg','RSA','-keysize','3072','-validity','36500','-dname','CN=Dose Personal,OU=Personal Android App,O=Dose,C=BR'],env=env,check=True)
    keystore.chmod(0o600)
dest=out/f'Dose-{VERSION_NAME}.apk'
subprocess.run([str(bt/'apksigner'),'sign','--ks',str(keystore),'--ks-key-alias',key['alias'],'--ks-pass','env:DOSE_SIGNING_PASSWORD','--key-pass','env:DOSE_SIGNING_PASSWORD','--out',str(dest),str(out/'aligned.apk')],env=env,check=True)
run(bt/'apksigner','verify','--verbose',dest)
print(f'APK: {dest}')
if args.test:
    testclasses=out/'test-classes';testclasses.mkdir(exist_ok=True)
    src=ROOT/'app/src/test/java/br/com/well/dose/ScheduleTests.java'
    run('java','com.sun.tools.javac.Main','-encoding','UTF-8','-d',testclasses,ROOT/'app/src/main/java/br/com/well/dose/Medication.java',ROOT/'app/src/main/java/br/com/well/dose/MedicineStyle.java',ROOT/'app/src/main/java/br/com/well/dose/ScheduleRules.java',src)
    run('java','-cp',testclasses,'br.com.well.dose.ScheduleTests')
