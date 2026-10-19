#!/usr/bin/env python3
"""Keep personal validation reports out of product resources and build metadata."""
from pathlib import Path
import argparse
import re
import xml.etree.ElementTree as ET


def verify(root):
    root = Path(root)
    for path in (root / 'app/src/main/res').glob('values*/strings.xml'):
        for value in ET.parse(path).getroot():
            if re.search(r'owner.*(?:validation|report)|(?:validation|report).*owner', value.get('name', ''), re.I):
                raise ValueError('Personal report resource: ' + str(path))
    for pattern in ['**/OWNER_VALIDATION*', '**/owner-validation*', '**/OWNER_TEST_REPORT*', '**/TEST_REPORT*']:
        if any((root / 'docs').glob(pattern)):
            raise ValueError('Personal or historical test report must not be published')
    workflow = (root / '.github/workflows/android-ci.yml').read_text()
    for field in ['owner_validation', 'physical_testing', 'telegram_e2e']:
        if re.search(r"['\"]" + field + r"['\"]\s*:", workflow):
            raise ValueError('Personal validation metadata must not be embedded in a build')
    template = (root / 'docs/BUILD_INFO_TEMPLATE.md').read_text()
    if set(re.findall(r'\{\{([A-Z_]+)\}\}', template)) != {'VERSION', 'BUILD_COMMIT', 'SOURCE_COMMIT', 'RUN_URL'}:
        raise ValueError('Build provenance template fields changed')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    verify(parser.parse_args().root)
    print('Public build metadata privacy contract verified')
