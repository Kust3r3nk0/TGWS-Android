#!/usr/bin/env python3
"""Fail closed before handing a production candidate to distribution."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

PACKAGE='io.github.kust3r3nk0.tgwsandroid'
PUBLISHED_DEBUG='26877620e16c5b2680023fc1fc6a285214b2e06dc998366731d9bcacd11c9fbe'
MAX_APK=80*1024*1024


def verify_payload(apk):
    with zipfile.ZipFile(apk) as archive:
        entries=archive.infolist()
        if len({e.filename for e in entries})!=len(entries) or sum(e.file_size for e in entries)>320*1024*1024:
            raise ValueError('Invalid or oversized APK payload')
        for name in ['AndroidManifest.xml','resources.arsc','classes.dex']:
            if name not in archive.namelist():raise ValueError('Incomplete application payload')
        if not re.fullmatch(rb'dex\n0[0-9]{2}\x00',archive.open('classes.dex').read(8)):
            raise ValueError('Invalid executable DEX header')
        if archive.testzip() is not None:raise ValueError('APK payload checksum failure')


def verify_metadata(badging, tree, version, code):
    match=re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging,re.M)
    if not match or match.groups()!=(PACKAGE,str(code),version):
        raise ValueError('Release identity/version mismatch')
    if re.findall(r"^sdkVersion:'(\d+)'$",badging,re.M)!=['36']:
        raise ValueError('Release must require API 36')
    if 'application-debuggable' in badging or re.search(r'android:debuggable[^\n]*=(?![^\n]*0x0\b)',tree):
        raise ValueError('Release must not be debuggable')
    if '-' in version or not re.fullmatch(r'\d+\.\d+\.\d+',version):
        raise ValueError('Production requires stable version')


def verify_certificate(output, expected):
    if not expected or not re.fullmatch(r'[a-fA-F0-9]{64}',expected):
        raise ValueError('Trusted production certificate SHA-256 is required')
    matches=re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})\s*$',output,re.M)
    if len(matches)!=1 or matches[0].lower()!=expected.lower():
        raise ValueError('Production signing certificate mismatch')
    if expected.lower()==PUBLISHED_DEBUG or re.search(r'certificate DN:.*(?:Android Debug|androiddebugkey)',output,re.I):
        raise ValueError('Debug certificate is forbidden for production')
    if 'Verified using v2 scheme (APK Signature Scheme v2): true' not in output:
        raise ValueError('APK v2 integrity verification required')
    return expected.lower()


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--apk',type=Path,required=True)
    parser.add_argument('--sdk',type=Path,required=True)
    parser.add_argument('--gradle',type=Path,default=Path('app/build.gradle.kts'))
    parser.add_argument('--certificate')
    parser.add_argument('--unsigned-candidate',action='store_true')
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    text=args.gradle.read_text()
    version=re.search(r'versionName\s*=\s*"([^"]+)"',text).group(1)
    code=int(re.search(r'versionCode\s*=\s*(\d+)',text).group(1))
    if not 0<args.apk.stat().st_size<=MAX_APK:raise ValueError('APK size outside release budget')
    verify_payload(args.apk)
    tools=args.sdk/'build-tools/36.0.0'
    def run(*cmd):return subprocess.check_output([str(x) for x in cmd],text=True,stderr=subprocess.STDOUT)
    badging=run(tools/'aapt','dump','badging',args.apk)
    tree=run(tools/'aapt','dump','xmltree',args.apk,'AndroidManifest.xml')
    verify_metadata(badging,tree,version,code)
    certificate=None
    if not args.unsigned_candidate:
        certificate=verify_certificate(run(tools/'apksigner','verify','--verbose','--print-certs',args.apk),args.certificate)
    else:
        check=subprocess.run([str(tools/'apksigner'),'verify',str(args.apk)],capture_output=True)
        if check.returncode==0:raise ValueError('Expected unsigned candidate, received signed APK')
    result=dict(schema=1,version=version,version_code=code,min_sdk=36,debuggable=False,
        size_bytes=args.apk.stat().st_size,sha256=hashlib.sha256(args.apk.read_bytes()).hexdigest(),
        production_signature_verified=certificate is not None,certificate_sha256=certificate,
        publishable=False,owner_approval_required=True)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,indent=2)+'\n')
    print('Release candidate verified; publication requires owner release gate')


if __name__=='__main__':main()
