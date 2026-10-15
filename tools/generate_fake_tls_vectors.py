#!/usr/bin/env python3
"""Generate public Fake TLS fixtures using the exact pinned Flowseal checkout."""
import argparse
import importlib
from pathlib import Path
import subprocess
import sys

parser = argparse.ArgumentParser()
parser.add_argument('upstream', type=Path)
parser.add_argument('--output', type=Path, default=Path('app/src/test/resources/fake-tls-v1.11.2.properties'))
args = parser.parse_args()
commit = subprocess.check_output(['git', '-C', str(args.upstream), 'rev-parse', 'HEAD'], text=True).strip()
if commit != '307a384ab7f522e34bb1a045884d0bd90f40bee5':
    raise SystemExit('Expected pinned Flowseal v1.11.2 checkout')
sys.path.insert(0, str(args.upstream.resolve()))
f = importlib.import_module('proxy.fake_tls')
client = importlib.import_module('tests.test_fake_tls')._client_hello
secret = bytes.fromhex('00112233445566778899aabbccddeeff')
now = 1700000000
f.time.time = lambda: now
f.os.urandom = lambda n: bytes((i * 13 + 7) % 256 for i in range(n))
f.random.randint = lambda _a, _b: 1999
hello = client(secret, now, bytes(range(32)))
parsed = f.verify_client_hello(hello, secret)
assert parsed
response = f.build_server_hello(secret, parsed[0], parsed[1])
values = dict(secret=secret.hex(), now=now, hello=hello.hex(), response=response.hex(),
              wrapped=f.wrap_tls_record(bytes(range(256)) * 65).hex())
args.output.write_text(f'# Generated from Flowseal v1.11.2 {commit}\n' +
                       ''.join(f'{key}={value}\n' for key, value in values.items()))
