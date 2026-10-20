#!/usr/bin/env python3
"""Guard the declared native-only UI contract; this is not a binary/security audit."""
import argparse
import json
from pathlib import Path
import re

# Any future embedded engine requires an explicit TGWS-16-016 implementation/review.
WEB = re.compile(r'\b(?:WebView|JavascriptInterface|addJavascriptInterface|evaluateJavascript)\b|androidx[.:/]webkit|android[./]webkit')
PROVIDER_FILE = 'app/src/main/java/io/github/kust3r3nk0/tgwsandroid/WebViewProviderReader.kt'
PROVIDER_READ = 'val info=android.webkit.WebView.getCurrentWebViewPackage()'


def verify(root):
    root = Path(root)
    files = set((root / 'app/src').rglob('*'))
    files.update(root.glob('*.gradle.kts'))
    files.update((root / 'app').glob('*.gradle.kts'))
    files.update((root / 'gradle').rglob('*.toml'))
    findings = []
    count = 0
    for path in sorted(files):
        if not path.is_file() or path.suffix not in ('.kt', '.java', '.xml', '.kts', '.toml'):
            continue
        relative = path.relative_to(root)
        if len(relative.parts) > 2 and relative.parts[:2] == ('app', 'src') and relative.parts[2].lower().endswith('test'):
            continue
        count += 1
        for number, line in enumerate(path.read_text().splitlines(), 1):
            if path.suffix == '.xml' and relative.parent.name.startswith('values'):
                # Display labels are not declarations of an embedded engine.
                line = re.sub(r'(<string\b[^>]*>).*?(</string>)', r'\1\2', line)
            # Reviewed metadata-only API: Android guarantees it does not initialize an engine.
            # Exact statement and file only; construction/imports/other calls still fail this guard.
            if relative.as_posix() == PROVIDER_FILE and line.strip() == PROVIDER_READ:
                continue
            if WEB.search(line):
                findings.append(f'{relative.as_posix()}:{number}')  # Never print source/config values.
    if count == 0:
        raise ValueError('No production sources/build declarations found')
    if findings:
        raise ValueError('Declared WebView integration requires TGWS-16-016 review: ' + ', '.join(findings))
    return {'schema': 1, 'embedded_webview': False, 'checked_files': count,
            'scope': 'production_sources_and_declared_dependencies',
            'runtime_channel_validation': 'not_applicable_to_embedded_components'}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    try:
        result = verify(args.root)
    except ValueError as error:
        parser.exit(1, str(error) + '\n')
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + '\n')
    print('Native UI declarations verified: no embedded WebView; channel certification not claimed')


if __name__ == '__main__':
    main()
