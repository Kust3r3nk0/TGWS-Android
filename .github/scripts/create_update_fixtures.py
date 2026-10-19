"""Generate tiny APK security fixtures locally. No signing key enters source/assets."""
import argparse
from pathlib import Path
import os
import subprocess
import tempfile
import zipfile

PACKAGE='io.github.kust3r3nk0.tgwsandroid'

def run(*args):
    result=subprocess.run(list(map(str,args)),capture_output=True,text=True)
    if result.returncode:raise RuntimeError('APK security fixture command failed: '+Path(str(args[0])).name)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--sdk',required=True);parser.add_argument('--keystore',required=True);parser.add_argument('--output',required=True)
    args=parser.parse_args()
    sdk=Path(args.sdk);output=Path(args.output);output.mkdir(parents=True,exist_ok=True)
    tools=sdk/'build-tools/36.0.0';signer=tools/'apksigner'
    with tempfile.TemporaryDirectory(prefix='tgws-update-fixtures-') as tmp:
        root=Path(tmp);newkey=root/'new.jks'
        run(Path(os.environ['JAVA_HOME'])/'bin/keytool','-genkeypair','-keystore',newkey,'-storepass','android','-keypass','android',
            '-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','365','-dname','CN=TGWS Synthetic Test','-noprompt')
        def config(key):return ['--ks',str(key),'--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android']
        for label,package in [('base',PACKAGE),('foreign','invalid.fixture.other')]:
            manifest=root/(label+'.xml')
            manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="'+package+'" android:versionCode="99" android:versionName="9.0.0"><uses-sdk android:minSdkVersion="36" android:targetSdkVersion="37"/><application android:label="Synthetic APK fixture"/></manifest>')
            run(tools/'aapt2','link','-I',sdk/'platforms/android-37.0/android.jar','--manifest',manifest,'-o',root/(label+'.apk'))
        def sign(name,key,source='base',extra=()):
            run(signer,'sign',*config(key),*extra,'--min-sdk-version','36','--v1-signing-enabled','false','--v2-signing-enabled','true',
                '--v3-signing-enabled','true','--v4-signing-enabled','false','--out',output/(name+'.apk'),root/(source+'.apk'))
            run(signer,'verify','--min-sdk-version','36',output/(name+'.apk'))
        sign('valid',args.keystore);sign('wrong-key',newkey);sign('wrong-package',args.keystore,'foreign')
        for name,allow in [('rotated','true'),('rotation-denied','false')]:
            lineage=root/(name+'.lineage')
            run(signer,'rotate','--old-signer',*config(args.keystore),'--set-installed-data',allow,'--new-signer',*config(newkey),'--out',lineage)
            sign(name,args.keystore,extra=('--next-signer',*config(newkey),'--lineage',str(lineage),'--rotation-min-sdk-version','33'))
        (output/'tampered.apk').write_bytes((output/'valid.apk').read_bytes())
        with zipfile.ZipFile(output/'tampered.apk','a') as apk:apk.writestr('tampered.txt',b'unsigned modification')
    print('Six synthetic APK security fixtures generated; temporary keys removed')

if __name__=='__main__':main()
