# Российские мобильные операторы: справочник и проверки

[English](en/RU_MOBILE_OPERATORS.md). Версия 0.3.0-alpha, Android 16+.
**Проверка владельцем на физическом устройстве: ожидается.**

| Оператор | MCC/MNC (основной) | APN (справочно) | Логин / пароль (справочно) |
|---|---|---|---|
| МегаФон | 25002; дополнительная справочная запись 25014 | internet | Не подставлять; актуальная конфигурация SIM/Android |
| МТС | 25001 | internet.mts.ru | mts / mts |
| билайн | 25099 | internet.beeline.ru | beeline / beeline |
| T2 | 25020 | internet | Пустые |

Приложение не меняет APN, DNS, SIM, частоты, тариф или сетевой протокол. Параметры
таблицы не являются рекомендацией переписывать системную конфигурацию. У билайна
пример IPv4 в инструкции не доказывает отсутствие IPv6 у всех абонентов.
Данные активной data-SIM и оператора регистрации показываются раздельно, если Android
их предоставляет. MNP/MVNO/роуминг могут давать другое имя/PLMN. Отсутствие доступа,
телемодуля или разрешения даёт «неизвестно», не мешает прокси. Имя «Мега5G» не доказывает NR.
Маршруты выбираются по конфигурации и фактическим сетевым результатам, без правил по бренду.

## Источники

Ссылки и исходные значения перенесены из ТЗ владельца от 15.10.2026; самостоятельное
подтверждение актуальности для конкретной SIM/региона не выполнялось. Приоритет — у
текущих данных Android и оператора.

- MCC/MNC, включая дополнительные записи: https://mcc-mnc.org/mcc/250 (публичный агрегатор, не государственный реестр; при расхождениях приоритет у текущих данных Android и официального источника).
- МТС, официальные настройки: https://support.mts.ru/mts_mobilnyy_internet/nastroiki-mobilnogo-interneta/kak-nastroit-internet-na-telefone
- билайн, официальные настройки: https://moskva.beeline.ru/customers/support/mobile/mobilniy-internet/nastrojki/nastrojka-telefona/
- T2, официальные инструкции: https://karta.t2.ru/journal/article/how-to-set-up-internet и https://mycard.t2.ru/help/article/android-setup
- МегаФон, официальная публикация с упоминанием `APN=internet`: https://ceph.megafon.ru/htdocs-pk-ceph/ai/option_attrs/ac3834e1-31c5-4ccf-8258-bcfb3eb64577/d3adcd76-885a-4f19-8972-4c14860fae43/pdf/option_1820_sib_2906f.pdf (документ по конкретной тарифной опции, не универсальная текущая инструкция для всех SIM).

## Матрица RU-01–14

Модели не имитируют сеть конкретного оператора. NetworkViewTest проверяет смену сети,
late callbacks и восстановление отдельно для всех четырёх справочных карточек;
протокольные/адресные тесты проверяют общее ядро, которое не зависит от бренда.
«Модель» ниже означает ограниченный автоматический сценарий, не физический PASS.

| ID | Сценарий | Доступное автоматическое покрытие | МегаФон | МТС | билайн | T2 |
|---|---|---|---|---|---|---|
| RU-01 | LTE, listener и подключение | Listener/transport integration; реальный LTE не моделируется | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-02 | 2G/3G/LTE+/NR | NetworkViewTest: radio mapping, unknown; реальный радиоканал не моделируется | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-03 | IPv4-only | NetworkConnectorTest и локальные IPv4 TCP/TLS/H2 peers | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-04 | Dual-stack | NetworkConnectorTest: IPv6 отказ → IPv4 в ограниченном бюджете | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-05 | IPv6-only / DNS64 | NetworkConnectorTest: OS-resolved IPv6 ответ; настоящий операторский NAT64 не развёрнут | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-06 | Нет совместимого выхода | NetworkConnectorTest / RouteProbeTest: bounded failure и диагностика | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-07 | Wi-Fi ↔ cellular | NetworkViewTest для всех 4 карточек, MtprotoSoakTest/Http2SoakTest: reset без replay | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-08 | Потеря/возврат сети | NetworkViewTest: loss/late callback/recovery; cancellation/backoff tests | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-09 | DNS/TLS/Worker отказ | NetworkConnectorTest, RawWebSocketTest, RelayEndpoint/relay-only tests | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-10 | Текст/медиа/файлы | Локальные ef/ee/dd потоки/H2 flow control и media DC; официальный Telegram не запускается | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-11 | 30–60 мин, экран выключен | Локальные короткие soak и Android lifecycle; 30–60 мин screen-off НЕ ПРОВЕРЕНЫ | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-12 | Dual-SIM/eSIM | NetworkViewTest: active-data presence/unknown/wrong SIM; аппаратное переключение не моделируется | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-13 | Roaming/MVNO/PLMN | NetworkViewTest: SIM и регистрация различны, unknown и дополнительные PLMN | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-14 | Direct/relay/CF/H2 | ExtendedRoutesTest, TransportSettingsTest, RouteProbeTest и реальные локальные H2 tests | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |

Все четыре физических столбца относятся к **0.3.0-alpha** и остаются NOT TESTED.
N/A применяют только после установления недоступности условия; отсутствие результата — NOT TESTED.
Владелец ранее сообщил о проблемах прямого пути МегаФон/Мега5G в Москве и области,
затем о рабочем 0.2.0 на Wi-Fi/8443 с показанием Telegram 6 мс. Это не доказывает
работу Worker в мобильной сети, механизм DPI/allowlist/CGNAT или результаты новой версии.

## Отчёт владельца

```text
TGWS-Android: 0.3.0-alpha
Commit/build APK:
APK SHA-256:
Устройство / Android:
Оператор / регион (без точного адреса):
SIM/eSIM и data-SIM (без номера, IMSI/ICCID):
Сеть: Wi-Fi | LTE | LTE+ | 5G | другое
Адресация: IPv4 | dual-stack | IPv6-only | неизвестно
Режим: direct | relay-only | Auto CF | H2/media
Сценарий RU-XX:
Ожидалось / получено:
Результат: PASS | FAIL | NOT TESTED | N/A
Диагностика: только обезличенный экспорт
Длительность / повторяемость:
```

Владелец проверяет доступные SIM; покупать SIM всех операторов не требуется.
Не публикуйте secret, URL с ключом, IP/Cell ID/SIM IDs или содержимое Telegram.
