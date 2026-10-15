# TGWS WSS relay

Сервер для собственного внешнего хоста; не входит в APK.
[Настройка и ограничения](../docs/MOBILE_NETWORKS.md) ·
[English](../docs/en/MOBILE_NETWORKS.md).

Проверка: `npm ci --ignore-scripts && npm test` (Node.js 22+).
Используется публичная тестовая TLS-идентичность localhost из JVM fixtures;
она не подходит для развёртывания. Production TLS key и токен хранятся вне Git.
