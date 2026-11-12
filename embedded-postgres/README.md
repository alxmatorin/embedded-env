# embedded-postgres

Локальный PostgreSQL 17.11, запускаемый из Java через Zonky Embedded Postgres.
Сервер работает отдельным процессом и принимает обычные JDBC-подключения.
Требуется JDK 21. Все команды ниже выполняются из корня проекта.

## Запуск

```shell
./mvnw -pl embedded-postgres spring-boot:run
```

В IDEA запускай `embeded.postgres.EmbeddedPostgresApplication` с JDK 21
и **Working directory** = `$PROJECT_DIR$`.
Для остановки используй `Ctrl+C` в терминале или Stop в IDEA.

Сборка и запуск JAR:

```shell
./mvnw -pl embedded-postgres -am package
java -jar embedded-postgres/target/embedded-postgres-1.0-SNAPSHOT.jar
```

## Подключение

| Параметр | По умолчанию |
| --- | --- |
| Адрес | `127.0.0.1:5432` |
| JDBC URL | `jdbc:postgresql://localhost:5432/postgres` |
| База данных | `postgres` |
| Пользователь в тестовом клиенте | `postgres` |
| Пароль в тестовом клиенте | `postgres` |

Готовая проверка подключения, миграций и обмена данными:
[postgres-test](../tests/postgres-test/README.md).

## Настройки

Конфигурация: [application.yml](src/main/resources/application.yml).

| Параметр Spring | Переменная окружения | По умолчанию | Назначение |
| --- | --- | --- | --- |
| `embedded.postgres.host` | `POSTGRES_HOST` | `127.0.0.1` | Адрес прослушивания |
| `embedded.postgres.port` | `POSTGRES_PORT` | `5432` | Порт Postgres |
| `embedded.postgres.data-directory` | `POSTGRES_DATA_DIRECTORY` | `./data/postgres` | Каталог данных |

Например, отдельный экземпляр на другом порту и в другом каталоге:

```shell
POSTGRES_PORT=5433 POSTGRES_DATA_DIRECTORY=./data/postgres-5433 ./mvnw -pl embedded-postgres spring-boot:run
```

В IDEA переменные задаются в **Environment variables**, параметры Spring —
в **Program arguments**, например `--embedded.postgres.port=5433`.

## Данные и повторный запуск

Каталог `./data/postgres` отсчитывается от рабочей директории процесса.
При запуске командами выше он находится в корне проекта. При остановке каталог
не удаляется, данные сохраняются между перезапусками. Включены `fsync`,
`synchronous_commit` и `full_page_writes`.

Перед запуском модуль пытается освободить каталог и порт. На macOS/Linux
предыдущий `EmbeddedPostgresApplication` ищется по открытому `epg-lock`
через `lsof`. Поиск владельца занятого порта также использует `lsof`,
на Windows — PowerShell. Ошибки этих попыток записываются в лог и игнорируются;
после них выполняется обычный запуск Postgres.

Если сервер Postgres завершается, приложение закрывает Spring-контекст
и освобождает блокировку каталога. Повторный запуск и восстановление данных
проверены на macOS. Автоматическая остановка JVM по `epg-lock` на Windows
не реализована: предыдущий экземпляр следует остановить через IDEA.

При ошибке `could not lock .../epg-lock` останови экземпляр приложения,
который использует этот каталог, или укажи другой каталог данных и свободный
порт. Ошибки самого запуска Postgres продолжают прерывать запуск приложения.

## Проверки кода

```shell
./mvnw -pl embedded-postgres -am test
```

Тесты используют временные каталоги и случайные порты. Они проверяют JDBC,
сохранение данных после перезапуска и остановки процесса, замену запущенного
экземпляра и обработку ошибок завершения.
