"""Validate the installed AVD image and correct avdmanager target metadata."""
import argparse
import os
from pathlib import Path
import re
import subprocess


def configure(directory, api):
    if not re.fullmatch(r'[1-9][0-9]*(?:\.[0-9]+)?', api):
        raise ValueError('Invalid requested API')
    directory = Path(directory)
    config = (directory / 'test.avd/config.ini').read_text()
    image = re.search(r'^image\.sysdir\.1=(.*)$', config, re.M)
    if not image or not re.search(r'(?:^|/)android-' + re.escape(api) + r'/', image[1]):
        raise ValueError('AVD image does not match the requested API')
    root = directory / 'test.ini'
    data = root.read_text()
    targets = re.findall(r'^target=(.*)$', data, re.M)
    if len(targets) != 1:
        raise ValueError('Expected one AVD target')
    target = f'android-{api}'
    # Older avdmanager writes android-0 for minor-versioned system images.
    # Match metadata to the actual installed image, without modifying the image.
    if targets[0] != target:
        print(f'Correcting AVD target {targets[0]} -> {target}')
        root.write_text(re.sub(r'^target=.*$', f'target={target}', data, flags=re.M))
    print(f'AVD target matches installed image: {target}')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--api', required=True)
    args = parser.parse_args()
    configure(os.environ['ANDROID_AVD_HOME'], args.api)
    subprocess.run(['adb', 'start-server'], check=True)


if __name__ == '__main__':
    main()
