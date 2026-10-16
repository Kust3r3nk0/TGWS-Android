#!/usr/bin/env python3
"""Execute the pinned upstream framing functions; no httpx/h2 installation is needed."""
import argparse
import ast
import asyncio
import os
from pathlib import Path
import struct
import subprocess
from typing import Tuple

p = argparse.ArgumentParser()
p.add_argument('upstream', type=Path)
p.add_argument('--output', type=Path, default=Path('app/src/test/resources/http-media-v1.11.2.properties'))
a = p.parse_args()
commit = subprocess.check_output(['git', '-C', str(a.upstream), 'rev-parse', 'HEAD'], text=True).strip()
assert commit == '307a384ab7f522e34bb1a045884d0bd90f40bee5'
tree = ast.parse((a.upstream / 'proxy/cf_h2.py').read_text())
nodes = [n for n in tree.body if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)) and n.name in ('_read_packet', '_encode_reply')]
assert len(nodes) == 2
scope = dict(struct=struct, os=os, Tuple=Tuple, MAX_PACKET=4*1024*1024,
             MAX_PLAIN_PADDING=256, PROTO_TAG_ABRIDGED=b'\xef'*4, PROTO_TAG_SECURE=b'\xdd'*4)
exec(compile(ast.Module(body=nodes, type_ignores=[]), 'pinned-cf_h2.py', 'exec'), scope)
os.urandom = lambda n: bytes([3])*n

class Identity:
    def update(self, data): return data

async def generate():
    result = []
    plain = bytes(8) + bytes(range(8)) + struct.pack('<I', 12) + bytes(range(12))
    encrypted = bytes(range(1, 41))
    for index, (tag, body, padding) in enumerate([(b'\xef'*4, encrypted, b''), (b'\xee'*4, encrypted, b''),
                                                 (b'\xdd'*4, plain, bytes(100)), (b'\xdd'*4, encrypted, bytes(7))]):
        payload = body + padding
        wire = bytes([len(payload)//4 | 0x80]) + payload if tag == b'\xef'*4 else struct.pack('<I', len(payload) | 0x80000000) + payload
        reader = asyncio.StreamReader(); reader.feed_data(wire); reader.feed_eof()
        decoded, quick = await scope['_read_packet'](reader, Identity(), tag)
        assert decoded == body and quick
        reply = scope['_encode_reply'](decoded, tag)
        result += [f'{index}.tag={tag.hex()}\n', f'{index}.wire={wire.hex()}\n',
                   f'{index}.body={decoded.hex()}\n', f'{index}.reply={reply.hex()}\n']
    return ''.join(result)

a.output.write_text(f'# Flowseal v1.11.2 {commit} cf_h2.py exact AST functions\n' + asyncio.run(generate()))
