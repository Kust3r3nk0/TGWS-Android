#!/usr/bin/env python3
"""Reject retired Cloudflare DoH code in this project's built Debug APK."""
import argparse
from pathlib import Path
import re
import zipfile


FORBIDDEN = (b'Lokhttp3/dnsoverhttps/', b'/CloudflareDns;', b'cloudflare-dns.com/dns-query')


def verify(apk):
    with zipfile.ZipFile(apk) as archive:
        dex = [name for name in archive.namelist() if re.fullmatch(r'classes(?:[0-9]+)?\.dex', name)]
        if not dex:
            raise ValueError('APK contains no DEX files')
        for name in dex:
            if any(marker in archive.read(name) for marker in FORBIDDEN):
                raise ValueError('Retired Cloudflare DoH code is present in APK DEX')
    return len(dex)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path, default=Path('app/build/outputs/apk/debug/app-debug.apk'))
    args = parser.parse_args()
    try:
        count = verify(args.apk)
    except (ValueError, OSError, zipfile.BadZipFile):
        parser.exit(1, 'Native DNS APK contract failed; inspect build inputs (no APK contents logged)\n')
    print(f'Native DNS APK contract verified across {count} DEX files; retired DoH fixtures excluded')


if __name__ == '__main__':
    main()
