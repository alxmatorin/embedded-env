# postgres-test

Проверка PostgreSQL через JDBC: применение миграций Flyway, запись строки
и чтение записанного значения. Выполняется сразу после запуска приложения,
затем приложение завершается. Требуется JDK 21.
Все команды ниже выполняются из корня проекта.

## Запуск

Сначала запусти [embedded-postgres](../../embedded-postgres/README.md)
либо укажи адрес уже работающего PostgreSQL.

```shell
./mvnw -pl tests/postgres-test spring-boot:run
```

В IDEA запускай `embeded.postgres.PostgresTestApplication` с JDK 21
и **Working directory** = `$PROJECT_DIR$`.

Сборка и запуск JAR:

```shell
./mvnw -pl tests/postgres-test -am package
java -jar tests/postgres-test/target/postgres-test-1.0-SNAPSHOT.jar
```

## Что проверяется

1. Flyway применяет миграции из
   [src/main/resources/db/migration](src/main/resources/db/migration).
2. В транзакции в таблицу `connection_test` вставляется строка со случайным UUID.
3. Строка читается обратно и сравнивается с исходным значением.
4. Транзакция откатывается, JDBC-ресурсы закрываются.

Тестовая строка не сохраняется. Таблица `connection_test` и история миграций
остаются в базе. Схема таблицы задана в
[V1__create_connection_test.sql](src/main/resources/db/migration/V1__create_connection_test.sql).

## Настройки

Конфигурация: [application.yml](src/main/resources/application.yml).

| Параметр Spring | Переменная окружения | По умолчанию | Назначение |
| --- | --- | --- | --- |
| `spring.datasource.url` | `POSTGRES_JDBC_URL` | `jdbc:postgresql://localhost:5432/postgres` | JDBC URL базы |
| `spring.datasource.username` | `POSTGRES_USER` | `postgres` | Пользователь |
| `spring.datasource.password` | `POSTGRES_PASSWORD` | `postgres` | Пароль |
| `connection-test.timeout` | `CONNECTION_TEST_TIMEOUT` | `5s` | Таймаут SQL-запроса, минимум 1 секунда |

Например, проверка сервера на другом порту:

```shell
POSTGRES_JDBC_URL=jdbc:postgresql://localhost:5433/postgres CONNECTION_TEST_TIMEOUT=10s ./mvnw -pl tests/postgres-test spring-boot:run
```

В IDEA используй **Environment variables** либо **Program arguments**,
например `--spring.datasource.url=jdbc:postgresql://localhost:5433/postgres`.

Пул Hikari содержит до двух соединений; ожидание соединения — 5 секунд.
JDBC-таймауты подключения и чтения сокета также заданы по 5 секунд.
`CONNECTION_TEST_TIMEOUT` не является общим дедлайном всего прогона или Flyway.
Остальные параметры пула доступны в `spring.datasource.hikari`.

## Результат

При успехе в логе появляется `postgres: OK`, при ошибке — `postgres: FAILED`
с причиной. Ошибка проверки перехватывается и логируется; отдельный ненулевой
код завершения для неё не устанавливается. Для оценки результата проверяй лог.

Приложение подключается только к PostgreSQL. Kafka, Ignite и их JVM-параметры
для этого модуля не требуются.

## Проверки кода

```shell
./mvnw -pl tests/postgres-test -am test
```

Эти тесты используют моки JDBC и Flyway, работающий сервер не требуется.
Проверяются обмен строкой, откат транзакции, закрытие соединения и ошибки
миграции, подключения и возвращённого значения.
