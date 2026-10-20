"""Create the bounded, release-owned Update Center discovery manifest."""
import argparse
import json
from pathlib import Path
import re

PACKAGE = 'io.github.kust3r3nk0.tgwsandroid'


def create(gradle, apk_name, channel):
    text = Path(gradle).read_text()
    version = re.search(r'versionName\s*=\s*"([^"]+)"', text).group(1)
    code = int(re.search(r'versionCode\s*=\s*(\d+)', text).group(1))
    minimum = int(re.search(r'minSdk\s*=\s*(\d+)', text).group(1))
    package = re.search(r'applicationId\s*=\s*"([^"]+)"', text).group(1)
    if (channel not in ('STABLE', 'PREVIEW') or minimum < 36 or package != PACKAGE or code < 1 or
            not re.fullmatch(r'TGWS-Android-[A-Za-z0-9._-]+\.apk', apk_name) or
            channel == 'STABLE' and ('-' in version or 'debug' in apk_name.lower())):
        raise ValueError('Incompatible update manifest inputs')
    return dict(schema=1, package_name=package, version_name=version, version_code=code,
                min_sdk=minimum, channel=channel, apk=apk_name)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--gradle', default='app/build.gradle.kts')
    parser.add_argument('--apk-name', required=True)
    parser.add_argument('--channel', required=True, choices=['STABLE', 'PREVIEW'])
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    Path(args.output).write_text(json.dumps(create(args.gradle, args.apk_name, args.channel), indent=2) + '\n')


if __name__ == '__main__':
    main()
