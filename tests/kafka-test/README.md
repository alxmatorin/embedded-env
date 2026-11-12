# kafka-test

Проверка Kafka через отправку и чтение сообщения. Приложение запускает тест
сразу, выводит результат и закрывает контекст. REST API и веб-сервера нет.
Требуется JDK 21. Все команды ниже выполняются из корня проекта.

## Запуск

Сначала запусти [embedded-kafka](../../embedded-kafka/README.md)
либо укажи адрес уже работающего брокера.

```shell
./mvnw -pl tests/kafka-test spring-boot:run
```

В IDEA запускай `embeded.kafka.KafkaTestApplication` с JDK 21
и **Working directory** = `$PROJECT_DIR$`.

Сборка и запуск JAR:

```shell
./mvnw -pl tests/kafka-test -am package
java -jar tests/kafka-test/target/kafka-test-1.0-SNAPSHOT.jar
```

## Что проверяется

1. Через Admin API создаётся тестовый топик с одной партицией и репликацией `1`.
   Если топик уже существует, используется существующий.
2. Consumer с уникальной группой устанавливает позицию в конец каждой партиции,
   чтобы читать новые сообщения.
3. Producer отправляет строковое сообщение с уникальным ключом и ждёт
   подтверждения брокера.
4. Consumer читает сообщение и проверяет ключ и значение.
5. Consumer, producer и Admin-клиент закрываются при завершении приложения.

Топик и отправленное сообщение после проверки остаются в Kafka.
Каждый запуск использует новый ключ и отдельную consumer group.

## Настройки

Конфигурация: [application.yml](src/main/resources/application.yml).

| Параметр Spring | Переменная окружения | По умолчанию | Назначение |
| --- | --- | --- | --- |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Начальные адреса брокеров |
| `connection-test.timeout` | `CONNECTION_TEST_TIMEOUT` | `5s` | Таймауты операций и ожидания сообщения |
| `connection-test.topic` | `CONNECTION_TEST_TOPIC` | `connection-test` | Тестовый топик |

Например, другой брокер и топик:

```shell
KAFKA_BOOTSTRAP_SERVERS=localhost:9094 CONNECTION_TEST_TOPIC=smoke-test CONNECTION_TEST_TIMEOUT=10s ./mvnw -pl tests/kafka-test spring-boot:run
```

В IDEA используй **Environment variables** либо **Program arguments**,
например `--spring.kafka.bootstrap-servers=localhost:9094`.

Ключи и значения передаются строками. В `application.yml` отдельно заданы
сетевые таймауты Kafka-клиентов: например, `delivery.timeout.ms=5000`
и `request.timeout.ms=3000` у producer. Изменение `CONNECTION_TEST_TIMEOUT`
не меняет эти параметры; при необходимости переопредели их через `spring.kafka`.

## Результат

При успехе в логе появляется `kafka: OK`, при ошибке — `kafka: FAILED`
с причиной. Ошибка проверки перехватывается и логируется; отдельный ненулевой
код завершения для неё не устанавливается. Для оценки результата проверяй лог.

Приложение подключается только к Kafka. PostgreSQL, Ignite и его JVM-параметры
для этого модуля не требуются.

## Проверки кода

```shell
./mvnw -pl tests/kafka-test -am test
```

Эти тесты используют моки Kafka-клиентов, работающий брокер не требуется.
Проверяются чтение нового сообщения после уже существующих, работа с готовым
топиком, закрытие consumer и обработка ошибки отправки.
