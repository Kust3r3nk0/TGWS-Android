# Эталонные векторы Flowseal / Flowseal reference vectors

Эталон: `Flowseal/tg-ws-proxy` **v1.11.2**, commit
`307a384ab7f522e34bb1a045884d0bd90f40bee5`.

```sh
git clone --branch v1.11.2 --depth 1 https://github.com/Flowseal/tg-ws-proxy.git ../reference-tg-ws-proxy
python3 -m venv ../flowseal-vector-venv
../flowseal-vector-venv/bin/pip install cryptography certifi
../flowseal-vector-venv/bin/python tools/generate_flowseal_vectors.py --upstream ../reference-tg-ws-proxy
git diff --exit-code -- app/src/test/resources/flowseal-v1.11.2.properties
```

Генератор проверяет commit и отсутствие изменений в `proxy/`, затем исполняет
неизменённые AST-определения `_try_handshake`, `_generate_relay_init`,
`_build_crypto_ctx`, `CryptoCtx` и `MsgSplitter` из исходного проекта.
Используется криптографический backend самого upstream. Установка его desktop UI
и HTTP/2-зависимостей не требуется. Случайные входы фиксированы; файл не содержит
пользовательских секретов. Python и файл векторов используются только для тестов.

36 векторов охватывают `ef` / `ee` / `dd`, DC 1–5 и существующий маршрут DC 203,
положительный/отрицательный DC, relay initialization и четыре независимых
направления AES-CTR. Шесть framing-векторов охватывают обычные и quick-ack
заголовки, расширенный abridged и разбиение TCP-данных.
SHA-256 исходных файлов записаны вместе с векторами.

Ужесточения относительно upstream намеренные: запрещённые префиксы входного
handshake отклоняются; тестовые DC исключены из альфы; нулевая/чрезмерная длина
и EOF внутри пакета завершают сессию. `MsgSplitter` upstream на повреждённых
данных отключает splitting и передаёт остаток как raw frame; порт так не делает.
Максимальный payload — 4 MiB. В padded intermediate сохраняется padding,
длина не обязана быть кратна четырём.

The generator validates the pinned commit and clean `proxy/` sources, then executes
the upstream functions' original AST with its own crypto backend. Deterministic
fixtures cover all three transport markers, normal/media DC 1–5 and DC 203,
relay initialization, all four cipher directions and quick-ack packet framing.
The fixture contains public test data; neither Python nor fixtures are APK dependencies.

Deliberate stricter behavior rejects reserved client prefixes, deferred test DCs,
invalid packet sizes and truncated EOF rather than forwarding malformed data.
The payload limit is 4 MiB; padded intermediate retains non-aligned padding.
These JVM checks establish protocol parity for the covered inputs. They do not
replace Android lifecycle tests or a physical-device Telegram end-to-end test.
