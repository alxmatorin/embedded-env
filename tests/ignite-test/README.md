# ignite-test

Проверка Ignite через thin client: запись, чтение и удаление записи кеша.
Тест выполняется сразу при запуске, затем приложение завершается.
Требуется JDK 21. Все команды ниже выполняются из корня проекта.

## Запуск

Сначала запусти [embedded-ignite](../../embedded-ignite/README.md)
либо укажи адрес уже работающего Ignite.

```shell
./mvnw -pl tests/ignite-test spring-boot:run
```

В IDEA запускай `embeded.ignite.IgniteTestApplication` с JDK 21
и **Working directory** = `$PROJECT_DIR$`. Параметры JVM описаны ниже.

Сборка и запуск JAR:

```shell
./mvnw -pl tests/ignite-test -am package
java -jar tests/ignite-test/target/ignite-test-1.0-SNAPSHOT.jar
```

## Что проверяется

1. Открывается thin client по настроенным адресам.
2. Создаётся либо открывается тестовый кеш.
3. В кеш записывается строковое значение с уникальным ключом UUID.
4. Значение читается и сравнивается с исходным.
5. Тестовая запись удаляется в `finally`, клиент закрывается.

Кеш после проверки остаётся, тестовая запись удаляется и при несовпадении
значения. Если сервер недоступен во время удаления, ошибка попадает в лог
как ошибка проверки.

## Настройки

Конфигурация: [application.yml](src/main/resources/application.yml).

| Параметр Spring | Переменная окружения | По умолчанию | Назначение |
| --- | --- | --- | --- |
| `app.ignite.addresses` | `IGNITE_ADDRESSES` | `localhost:10800` | Адреса thin client, разделённые запятыми |
| `connection-test.timeout` | `CONNECTION_TEST_TIMEOUT` | `5s` | Таймауты handshake и запросов |
| `connection-test.cache` | `CONNECTION_TEST_CACHE` | `connection-test` | Имя тестового кеша |

Например, другой адрес и кеш:

```shell
IGNITE_ADDRESSES=localhost:10801 CONNECTION_TEST_CACHE=smoke-test CONNECTION_TEST_TIMEOUT=10s ./mvnw -pl tests/ignite-test spring-boot:run
```

В IDEA используй **Environment variables** либо **Program arguments**,
например `--app.ignite.addresses=localhost:10801`.

Хранение кеша в памяти или на диске определяется настройками сервера.
Для локального узла это `embedded.ignite.persistence-enabled` в
[embedded-ignite](../../embedded-ignite/README.md).

## Параметры JVM

Модуль использует `ignite-core`, поэтому при прямом запуске main из IDEA
ему нужны те же VM options, что и серверу Ignite. Полный список `--add-opens`
приведён в комментарии
[EmbeddedIgniteApplication](../../embedded-ignite/src/main/java/embeded/ignite/EmbeddedIgniteApplication.java)
и в свойстве `ignite.jvm.arguments` корневого [pom.xml](../../pom.xml).

Для `spring-boot:run` и Maven-тестов параметры уже заданы в [pom.xml](pom.xml).
Собранный JAR содержит `Add-Opens` в манифесте.
При `ExceptionInInitializerError` проверь VM options и исходную причину
выше в логе, затем перезапусти JVM.

## Результат

При успехе в логе появляется `ignite: OK`, при ошибке — `ignite: FAILED`
с причиной. Ошибка проверки перехватывается и логируется; отдельный ненулевой
код завершения для неё не устанавливается. Для оценки результата проверяй лог.

Приложение подключается только к Ignite; Kafka и PostgreSQL не требуются.

## Проверки кода

```shell
./mvnw -pl tests/ignite-test -am test
```

Эти тесты используют моки Ignite-клиента, работающий узел не требуется.
Проверяются обмен записью, очистка после несовпадения значения, закрытие
клиента и обработка ошибки подключения.
