#!/usr/bin/env python3
import json, os, pathlib, subprocess, zipfile, shutil, sys
root=pathlib.Path(__file__).resolve().parents[1]
sdk=pathlib.Path(sys.argv[1]).resolve();bt=sdk/'build-tools/37.0.0';platform=sdk/'platforms/android-37.0/android.jar'
if not platform.exists():platform=sdk/'platforms/android-37/android.jar'
out=root/'build/device-tests';out.mkdir(parents=True,exist_ok=True)
def run(*cmd):subprocess.run([str(a) for a in cmd],check=True)
classes=out/'classes';dex=out/'dex'
for path in [classes,dex]:
 if path.exists():shutil.rmtree(path)
 path.mkdir()
run(bt/'aapt2','link','-I',platform,'--manifest',root/'app/src/androidTest/AndroidManifest.xml','-o',out/'base.apk')
run('java','com.sun.tools.javac.Main','-encoding','UTF-8','-source','8','-target','8','-bootclasspath',str(platform)+os.pathsep+str(bt/'core-lambda-stubs.jar'),'-classpath',root/'build/classes','-d',classes,root/'app/src/androidTest/java/br/com/well/dose/DeviceTests.java')
with zipfile.ZipFile(out/'tests.jar','w',zipfile.ZIP_DEFLATED) as z:
 for file in classes.rglob('*.class'):z.write(file,file.relative_to(classes))
run(bt/'d8','--lib',platform,'--classpath',root/'build/classes.jar','--min-api','26','--output',dex,out/'tests.jar')
shutil.copy2(out/'base.apk',out/'unaligned.apk')
with zipfile.ZipFile(out/'unaligned.apk','a',zipfile.ZIP_DEFLATED) as z:
 for file in dex.glob('*.dex'):z.write(file,file.name)
run(bt/'zipalign','-f','4',out/'unaligned.apk',out/'aligned.apk')
key=json.loads((root/'signing/local-signing.json').read_text());env={**os.environ,'DOSE_SIGNING_PASSWORD':key['password']}
subprocess.run([str(bt/'apksigner'),'sign','--ks',str(root/'signing/dose-personal.jks'),'--ks-pass','env:DOSE_SIGNING_PASSWORD','--key-pass','env:DOSE_SIGNING_PASSWORD','--out',str(out/'dose-tests.apk'),str(out/'aligned.apk')],env=env,check=True)
print(out/'dose-tests.apk')
