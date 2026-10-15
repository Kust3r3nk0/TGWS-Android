#!/usr/bin/env python3
"""Execute pinned upstream definitions to regenerate public, deterministic JVM fixtures.

Usage: python3 tools/generate_flowseal_vectors.py --upstream ../reference-tg-ws-proxy
Requires Python 3.8+, cryptography and certifi; Python is never packaged in the APK.
The AST extraction avoids importing unrelated HTTP/2 and desktop UI dependencies.
"""
import argparse
import ast
import hashlib
import importlib
from pathlib import Path
import struct
import subprocess
import sys
from types import SimpleNamespace
from typing import Dict, List, Optional, Tuple

COMMIT = "307a384ab7f522e34bb1a045884d0bd90f40bee5"


def load_reference(root):
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    if revision != COMMIT:
        raise ValueError("Reference checkout must be Flowseal v1.11.2 at " + COMMIT)
    subprocess.run(["git", "diff", "--exit-code", "HEAD", "--", "proxy"], cwd=root,
                   check=True, stdout=subprocess.DEVNULL)
    sys.path.insert(0, str(root))
    aes = importlib.import_module("proxy._aes")
    utils = importlib.import_module("proxy.utils")
    namespace = dict(vars(utils), Cipher=aes.Cipher, algorithms=aes.algorithms, modes=aes.modes,
                     hashlib=hashlib, struct=struct, Optional=Optional, Tuple=Tuple,
                     List=List, Dict=Dict, _st_I_le=struct.Struct("<I"))
    for path, names in (
        ("proxy/bridge.py", {"CryptoCtx", "MsgSplitter"}),
        ("proxy/tg_ws_proxy.py", {"_try_handshake", "_generate_relay_init", "_build_crypto_ctx"}),
    ):
        tree = ast.parse((root / path).read_text(encoding="utf-8"))
        nodes = [node for node in tree.body if isinstance(node, (ast.FunctionDef, ast.ClassDef))
                 and node.name in names]
        if {node.name for node in nodes} != names:
            raise ValueError("Missing pinned reference definitions")
        # The original AST, including function bodies, is compiled without modification.
        exec(compile(ast.Module(body=nodes, type_ignores=[]), str(root / path), "exec"), namespace)
    return namespace


def deterministic(label, size):
    return b"".join(hashlib.sha256((label + ":" + str(i)).encode()).digest()
                    for i in range((size + 31) // 32))[:size]


def valid_random(n, label):
    result = bytearray(deterministic(label, n))
    result[0] = 0x41
    result[4:8] = b"TGWS"
    return bytes(result)


def generate(root):
    ref = load_reference(root)
    Cipher, algorithms, modes = (ref[name] for name in ("Cipher", "algorithms", "modes"))

    def ctr(key, iv):
        return Cipher(algorithms.AES(key), modes.CTR(iv)).encryptor()

    values = {"upstream.commit": COMMIT, "upstream.tag": "v1.11.2"}
    for path in ("proxy/tg_ws_proxy.py", "proxy/bridge.py", "proxy/utils.py", "proxy/_aes.py"):
        values["upstream.sha256." + path] = hashlib.sha256((root / path).read_bytes()).hexdigest()
    routes = ref["DC_DEFAULT_IPS"]
    values["routing.count"] = len(routes)
    for index, (dc, ip) in enumerate(routes.items()):
        values["routing." + str(index) + ".dc"] = dc
        values["routing." + str(index) + ".ip"] = ip
        values["routing." + str(index) + ".ordinary"] = ",".join(ref["ws_domains"](dc, False))
        values["routing." + str(index) + ".media"] = ",".join(ref["ws_domains"](dc, True))
    secret = bytes(range(16))
    index = 0
    for tag_byte in (0xef, 0xee, 0xdd):
        tag = bytes([tag_byte]) * 4
        for dc in (1, 2, 3, 4, 5, 203):
            for media in (False, True):
                signed_dc = -dc if media else dc
                label = str(tag_byte) + ":" + str(signed_dc)
                header = bytearray(valid_random(64, "client:" + label))
                material = bytes(header[8:56])
                key = hashlib.sha256(material[:32] + secret).digest()
                stream = ctr(key, material[32:]).update(bytes(header))
                tail = tag + struct.pack("<h", signed_dc) + b"\x09\x08"
                header[56:64] = bytes(a ^ b ^ c for a, b, c in zip(tail, stream[56:], header[56:]))
                header = bytes(header)
                parsed = ref["_try_handshake"](header, secret)
                assert parsed == (dc, media, tag, material)

                relay_random = valid_random(64, "relay:" + label)
                relay_suffix = deterministic("suffix:" + label, 2)
                random_inputs = [relay_random, relay_suffix]

                def urandom(size):
                    result = random_inputs.pop(0)
                    assert len(result) == size
                    return result

                ref["os"] = SimpleNamespace(urandom=urandom)
                relay = ref["_generate_relay_init"](tag, signed_dc)
                assert not random_inputs
                payload = deterministic("payload:" + label, 153)
                crypto = ref["_build_crypto_ctx"](material, secret, relay)
                source = ctr(key, material[32:])
                source.update(bytes(64))
                client_ciphertext = source.update(payload)
                reverse = relay[8:56][::-1]
                telegram_ciphertext = ctr(reverse[:32], reverse[32:]).update(payload)
                assert crypto.clt_dec.update(client_ciphertext) == payload
                assert crypto.tg_dec.update(telegram_ciphertext) == payload
                case = {
                    "secret": secret, "tag": tag, "dc": dc, "media": str(media).lower(),
                    "signedDc": signed_dc, "header": header, "material": material,
                    "relayRandom": relay_random, "relaySuffix": relay_suffix, "relay": relay,
                    "payload": payload, "clientCiphertext": client_ciphertext,
                    "telegramCiphertext": telegram_ciphertext,
                    "toTelegram": crypto.tg_enc.update(payload),
                    "toClient": crypto.clt_enc.update(payload),
                }
                values.update({"handshake." + str(index) + "." + name: value for name, value in case.items()})
                index += 1
    values["handshake.count"] = index

    index = 0
    for tag_byte in (0xef, 0xee, 0xdd):
        for quick_ack in (False, True):
            packets = []
            for number, size in enumerate((8, 512, 20)):
                payload = deterministic("framing:" + str(number), size)
                if tag_byte == 0xef:
                    words = size // 4
                    prefix = bytes([words]) if words < 127 else b"\x7f" + words.to_bytes(3, "little")
                else:
                    prefix = size.to_bytes(4, "little")
                prefix = bytearray(prefix)
                if quick_ack:
                    prefix[0 if tag_byte == 0xef else 3] |= 0x80
                packets.append(bytes(prefix) + payload)
            plain = b"".join(packets)
            relay = valid_random(64, "framing-relay")
            enc = ctr(relay[8:40], relay[40:56])
            enc.update(bytes(64))
            cipher = enc.update(plain)
            splitter = ref["MsgSplitter"](int.from_bytes(bytes([tag_byte]) * 4, "little"))
            parts, offset = [], 0
            chunks = [1, 2, 3, 7, 129, 5, len(plain) - 147]
            for length in chunks:
                parts.extend(splitter.split(cipher[offset:offset + length], plain[offset:offset + length]))
                offset += length
            assert b"".join(parts) == cipher
            assert list(map(len, parts)) == list(map(len, packets))
            assert splitter.flush() == []
            case = {"tag": bytes([tag_byte]) * 4, "plain": plain, "cipher": cipher,
                    "chunks": ",".join(map(str, chunks)), "lengths": ",".join(map(str, map(len, parts)))}
            values.update({"framing." + str(index) + "." + name: value for name, value in case.items()})
            index += 1
    values["framing.count"] = index
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream", required=True, type=Path)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] /
                        "app/src/test/resources/flowseal-v1.11.2.properties")
    args = parser.parse_args()
    values = generate(args.upstream.resolve())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="ascii", newline="\n") as output:
        output.write("# Public deterministic test data; generated by tools/generate_flowseal_vectors.py\n")
        for key, value in values.items():
            output.write(key + "=" + (value.hex() if isinstance(value, bytes) else str(value)) + "\n")
    print("Generated", values["handshake.count"], "handshakes and", values["framing.count"],
          "framing cases from", COMMIT)


if __name__ == "__main__":
    main()
