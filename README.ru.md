# lifecycle

Современный, легковесный и надёжный плагин управления жизненным циклом серверов и масштабированием в ноль (Scale-to-Zero) для прокси-серверов **Velocity** (Minecraft).

Спроектирован специально для контейнеризированной инфраструктуры на базе **Podman Quadlet** и **systemd**, обеспечивая автоматический запуск контейнеров по требованию, параллельный прогрев во время авторизации и выключение серверов при отсутствии игроков.

---

## Основные возможности

- **Инфраструктура Scale-to-Zero**:
  Автоматически выключает пустующие серверы (`Purpur`, `Paper`, `Forge 1.12.2`, `Fabric` и др.) для полного освобождения оперативной памяти (RAM) и процессора (CPU), удерживая активными 24/7 только прокси и легковесный NanoLimbo.
- **Параллельный прогрев при входе по поддоменам (Forced-Hosts)**:
  Перехватывает подключение на самом раннем этапе рукопожатия (`PostOrder.FIRST`) и мгновенно отдаёт команду на запуск контейнера в фоне, пока игрок вводит пароль в NanoLimbo.
- **Абсолютная защита от обхода авторизации (Zero-Bypass Auth)**:
  Включает мягкую интеграцию с `nadamu-auth`. Гарантирует, что неавторизованные игроки со статусом `PENDING_LOGIN` ни при каких обстоятельствах не будут перенесены на игровой сервер раньше успешного ввода пароля.
- **Нативная интеграция с Podman Quadlet и systemd**:
  Взаимодействует с окружением Rootless Podman на хосте через непривилегированный мост `systemd --user` webhook. Полная независимость от ядра сервера или версии Java на бэкенде.
- **Интерактивный зал ожидания (NanoLimbo Holding)**:
  Удерживает игрока в виртуальном мире NanoLimbo с анимированными Title и Actionbar сообщениями (MiniMessage) до тех пор, пока целевой сервер не ответит на TCP-пинг.
- **Гибкий сторожевой таймер простоя (Idle Watchdog)**:
  Контролирует онлайн игроков, поддерживает период защиты после старта (Startup Grace Period) и настраиваемый таймаут простоя для каждого сервера индивидуально.

---

## Архитектура работы

```
                  Игрок подключается к pvp.nada.mu
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │        Velocity Proxy        │
                  └──────────────┬───────────────┘
                                 │
                 PreLogin / InitialServer (FIRST)
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │       lifecycle       │
                  │ (Параллельный запуск mc@pvp) │
                  └──────────────┬───────────────┘
                                 │ HTTP POST
                                 ▼
    ┌─────────────────────────────────────────────────────────┐
    │     webhook bridge (systemd --user на хосте: порт 9000) │
    │     systemctl --user start mc@pvp.service               │
    └────────────────────────────┬────────────────────────────┘
                                 │
                                 ▼
    ┌─────────────────────────────────────────────────────────┐
    │        Podman Quadlet создаёт эфемерный контейнер       │
    │        (Сервер компилируется/грузится в фоне)           │
    └─────────────────────────────────────────────────────────┘
```

---

## Конфигурация (`config.yml`)

```yaml
bridge:
  url: "http://host.containers.internal:9000"
  timeout-seconds: 5

# Сервер-отстойник на время загрузки бэкенда
limbo-server: "limbo"

# Сообщения игроку
messages:
  server-starting: "<gold><b>Сервер <yellow>{server}</yellow> запускается...</b></gold><newline><gray>Пожалуйста, подождите немного</gray>"
  server-ready: "<green>Сервер готов! Подключение...</green>"
  server-failed: "<red>Не удалось запустить сервер {server}. Обратитесь к администратору.</red>"

# Список серверов
servers:
  pvp:
    idle-timeout-minutes: 10
    startup-grace-period-seconds: 180
    poll-interval-seconds: 2
    max-startup-wait-seconds: 120
  pillar:
    idle-timeout-minutes: 10
    startup-grace-period-seconds: 180
    poll-interval-seconds: 2
    max-startup-wait-seconds: 120
  forge:
    idle-timeout-minutes: 15
    startup-grace-period-seconds: 300
    poll-interval-seconds: 3
    max-startup-wait-seconds: 300
```

---

## Команды и права

| Команда | Алиасы | Право | Описание |
| :--- | :--- | :--- | :--- |
| `/lifecycle reload` | `/nlc reload` | `nadamu.lifecycle.admin` | Перезагрузка файлов конфигурации |
| `/lifecycle status` | `/nlc status` | `nadamu.lifecycle.admin` | Просмотр статусов всех управляемых серверов |
| `/lifecycle start <server>` | `/nlc start` | `nadamu.lifecycle.admin` | Ручной запуск сервера |
| `/lifecycle stop <server>` | `/nlc stop` | `nadamu.lifecycle.admin` | Ручная остановка сервера |
