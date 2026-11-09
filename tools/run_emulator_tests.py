#!/usr/bin/env python3
"""Run only against an isolated test AVD: the instrumentation erases app data."""
import argparse, os, pathlib, subprocess, time, json, re, xml.etree.ElementTree as ET

root=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--sdk',required=True);p.add_argument('--avd',required=True);args=p.parse_args()
sdk=pathlib.Path(args.sdk).resolve();out=root/'build/validation';out.mkdir(parents=True,exist_ok=True)
env={**os.environ,'ANDROID_SDK_ROOT':str(sdk),'ANDROID_ADB_SERVER_PORT':'5038'}
adb=sdk/'platform-tools/adb'
def command(*cmd,timeout=60,check=True):
    return subprocess.run([str(a) for a in cmd],env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=timeout,check=check).stdout
def ad(*cmd,**kw):return command(adb,'-P','5038','-s','emulator-5554',*cmd,**kw)
command(adb,'-P','5038','start-server')
log=open(out/'emulator.log','w')
emu=subprocess.Popen([str(sdk/'emulator/emulator'),'-avd',args.avd,'-no-window','-gpu','swiftshader_indirect','-no-audio','-no-snapshot','-accel','off','-cores','2','-memory','1536','-no-boot-anim','-netfast','-crash-report-mode','disabled'],env=env,stdout=log,stderr=subprocess.STDOUT)
summary={}
try:
    start=time.monotonic()
    while time.monotonic()-start<600:
        if emu.poll() is not None:raise RuntimeError(f'Emulator exited: {emu.returncode}')
        state=ad('shell','getprop','sys.boot_completed',timeout=10,check=False).strip()
        if state=='1':break
        time.sleep(4)
    else:raise RuntimeError('Emulator did not finish booting within ten minutes.')
    print('BOOT COMPLETE',flush=True)
    ad('shell','settings','put','global','window_animation_scale','0');ad('shell','settings','put','global','transition_animation_scale','0');ad('shell','settings','put','global','animator_duration_scale','0')
    ad('shell','wm','size','900x2000');ad('shell','wm','density','360')
    ad('shell','settings','put','system','screen_off_timeout','600000')
    ad('shell','cmd','alarm','set-timezone','America/Sao_Paulo',check=False)
    print(ad('install','--no-incremental','-r',root/'build/Dose-1.4.0.apk'),flush=True)
    print(ad('install','--no-incremental','-r',root/'build/device-tests/dose-tests.apk'),flush=True)
    ad('shell','pm','grant','br.com.well.dose','android.permission.POST_NOTIFICATIONS')
    ad('shell','appops','set','br.com.well.dose','SCHEDULE_EXACT_ALARM','allow')
    ad('shell','appops','set','br.com.well.dose','USE_FULL_SCREEN_INTENT','allow',check=False)
    ad('shell','appops','set','br.com.well.dose','SYSTEM_ALERT_WINDOW','allow')
    ad('shell','cmd','notification','allow_dnd','br.com.well.dose')
    ad('shell','cmd','notification','set_dnd','all')
    runner='br.com.well.dose.test/br.com.well.dose.DeviceTests'
    tests=ad('shell','am','instrument','-r','-w',runner,timeout=180);(out/'device-tests.txt').write_text(tests);print(tests,flush=True)
    if 'INSTRUMENTATION_RESULT: result=PASS' not in tests:raise RuntimeError('Device integration tests failed.')
    summary['integration']='PASS'
    today=ad('shell','am','instrument','-r','-w','-e','mode','today',runner,timeout=240)
    (out/'today.txt').write_text(today);print(today,flush=True)
    if 'INSTRUMENTATION_RESULT: result=PASS' not in today:raise RuntimeError('In-app dose confirmation checks failed.')
    summary['today']='PASS'
    cancel=ad('shell','am','instrument','-r','-w','-e','mode','cancel_day',runner,timeout=240)
    (out/'cancel-day.txt').write_text(cancel);print(cancel,flush=True)
    if 'INSTRUMENTATION_RESULT: result=PASS' not in cancel:raise RuntimeError('Day-only reminder cancellation checks failed.')
    summary['cancel_day']='PASS'
    imports=ad('shell','am','instrument','-r','-w','-e','mode','imports',runner,timeout=240)
    (out/'imports.txt').write_text(imports);print(imports,flush=True)
    if 'INSTRUMENTATION_RESULT: result=PASS' not in imports:raise RuntimeError('Medication merge, import or backup checks failed.')
    summary['imports']='PASS'
    appearance=ad('shell','am','instrument','-r','-w','-e','mode','appearance',runner,timeout=240)
    (out/'appearance.txt').write_text(appearance);print(appearance,flush=True)
    if 'INSTRUMENTATION_RESULT: result=PASS' not in appearance:raise RuntimeError('Medicine appearance or backup checks failed.')
    summary['appearance']='PASS'
    alarms=ad('shell','am','instrument','-r','-w','-e','mode','alarms',runner,timeout=300)
    (out/'alarms.txt').write_text(alarms);print(alarms,flush=True)
    if 'INSTRUMENTATION_RESULT: result=PASS' not in alarms:raise RuntimeError('Single notification, alarm audio or volume recovery checks failed.')
    summary['alarms']='PASS'
    regression=ad('shell','am','instrument','-r','-w','-e','mode','regression',runner,timeout=240)
    (out/'regression-1.4.0.txt').write_text(regression);print(regression,flush=True)
    if 'INSTRUMENTATION_RESULT: result=PASS' not in regression:raise RuntimeError('Theme, calendar or test-alarm regression failed.')
    summary['regression']='PASS'
    seed=ad('shell','am','instrument','-r','-w','-e','mode','seed',runner,timeout=60);(out/'background-seed.txt').write_text(seed)
    # Instrumentation changes process lifecycle. Open the real activity once,
    # then leave it and kill its background process as in normal app use.
    ad('shell','am','start','-W','-n','br.com.well.dose/.MainActivity')
    ad('shell','input','keyevent','3');ad('shell','am','kill','br.com.well.dose');ad('shell','input','keyevent','223')
    (out/'alarms-before-background.txt').write_text(ad('shell','dumpsys','alarm'))
    # Wait on the guest's clock, which can lag host time under software emulation.
    ad('shell','sleep','35',timeout=90)
    ad('shell','sleep','35',timeout=90)
    notices=ad('shell','dumpsys','notification','--noredact');(out/'background-notifications.txt').write_text(notices)
    if 'Lembrete de teste' not in notices:raise RuntimeError('The system did not show the scheduled test notification.')
    verify=ad('shell','am','instrument','-r','-w','-e','mode','verify',runner,timeout=60);(out/'background-verify.txt').write_text(verify);print(verify,flush=True)
    if 'INSTRUMENTATION_RESULT: result=PASS' not in verify:raise RuntimeError('Background delivery test failed.')
    summary['background']='PASS'
    seed_test=ad('shell','am','instrument','-r','-w','-e','mode','seed_test',runner,timeout=60)
    (out/'test-alarm-seed.txt').write_text(seed_test)
    ad('shell','am','start','-W','-n','br.com.well.dose/.MainActivity')
    ad('shell','input','keyevent','3');ad('shell','am','kill','br.com.well.dose');ad('shell','input','keyevent','223')
    ad('shell','sleep','25',timeout=90)
    test_notices=ad('shell','dumpsys','notification','--noredact')
    (out/'test-alarm-notifications.txt').write_text(test_notices)
    if 'Encerrar teste' not in test_notices or 'Adiar 10 min' not in test_notices:raise RuntimeError('Test alarm did not publish its actions.')
    verify_test=ad('shell','am','instrument','-r','-w','-e','mode','verify_test',runner,timeout=60)
    (out/'test-alarm-background.txt').write_text(verify_test);print(verify_test,flush=True)
    if 'INSTRUMENTATION_RESULT: result=PASS' not in verify_test:raise RuntimeError('15-second test alarm failed in background.')
    summary['test_alarm']='PASS'
    ad('shell','input','keyevent','224');ad('shell','wm','dismiss-keyguard')
    ad('shell','am','instrument','-r','-w','-e','mode','showcase',runner,timeout=60)
    ad('shell','am','start','-W','-n','br.com.well.dose/.MainActivity','--ei','tab','0')
    for tab,name,label in [(0,'agenda','Hoje'),(1,'medicamentos','Remédios'),(3,'alertas','Alertas')]:
        ad('shell','uiautomator','dump','/sdcard/dose-test-ui.xml')
        ui=ET.fromstring(ad('exec-out','cat','/sdcard/dose-test-ui.xml'))
        node=next(n for n in ui.iter('node') if n.get('text')==label)
        x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
        ad('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(3)
        with open(out/(name+'.png'),'wb') as image:subprocess.run([str(adb),'-P','5038','-s','emulator-5554','exec-out','screencap','-p'],env=env,stdout=image,check=True,timeout=30)
    errors=ad('logcat','-d','-s','AndroidRuntime:E',check=False);(out/'android-errors.txt').write_text(errors)
    if 'FATAL EXCEPTION' in errors:raise RuntimeError('Android reported a fatal app exception; inspect android-errors.txt.')
    summary['screens']='PASS';print('ALL DEVICE CHECKS PASSED',flush=True)
finally:
    try:(out/'android-errors.txt').write_text(ad('logcat','-d','-s','AndroidRuntime:E',timeout=15,check=False))
    except Exception:pass
    (out/'summary.json').write_text(json.dumps(summary,indent=2))
    emu.terminate()
    try:emu.wait(timeout=10)
    except subprocess.TimeoutExpired:emu.kill()
