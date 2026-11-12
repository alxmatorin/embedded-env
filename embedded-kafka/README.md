# embedded-kafka

Локальный Kafka-брокер в режиме KRaft: брокер и контроллер работают в одном
процессе, ZooKeeper не используется. Требуется JDK 21.
Все команды ниже выполняются из корня проекта.

## Запуск

```shell
./mvnw -pl embedded-kafka spring-boot:run
```

В IDEA запускай `embeded.kafka.EmbeddedKafkaApplication` с JDK 21
и **Working directory** = `$PROJECT_DIR$`.
Для остановки используй `Ctrl+C` в терминале или Stop в IDEA.

Сборка и запуск JAR:

```shell
./mvnw -pl embedded-kafka -am package
java -jar embedded-kafka/target/embedded-kafka-1.0-SNAPSHOT.jar
```

## Подключение

Клиентский адрес по умолчанию — `localhost:9092`, протокол — `PLAINTEXT`.
Порт `9093` используется контроллером на `127.0.0.1`.
Фактор репликации для обычных и служебных топиков задан равным `1`.

Готовая проверка отправки и чтения сообщения:
[kafka-test](../tests/kafka-test/README.md).

## Настройки

Конфигурация: [application.yml](src/main/resources/application.yml).

| Параметр Spring | Переменная окружения | По умолчанию | Назначение |
| --- | --- | --- | --- |
| `embedded.kafka.bind-host` | `KAFKA_BIND_HOST` | `127.0.0.1` | Адрес прослушивания брокера |
| `embedded.kafka.advertised-host` | `KAFKA_ADVERTISED_HOST` | `localhost` | Адрес брокера, возвращаемый клиентам |
| `embedded.kafka.port` | `KAFKA_PORT` | `9092` | Порт клиентов и брокера |
| `embedded.kafka.controller-port` | `KAFKA_CONTROLLER_PORT` | `9093` | Порт локального контроллера |
| `embedded.kafka.partitions` | `KAFKA_PARTITIONS` | `1` | Число партиций по умолчанию для автоматически создаваемых топиков |
| `embedded.kafka.data-directory` | `KAFKA_DATA_DIRECTORY` | `./data/kafka` | Каталог журналов и метаданных |

Например, отдельный экземпляр с другими портами и каталогом:

```shell
KAFKA_PORT=9094 KAFKA_CONTROLLER_PORT=9095 KAFKA_DATA_DIRECTORY=./data/kafka-9094 ./mvnw -pl embedded-kafka spring-boot:run
```

В IDEA переменные задаются в **Environment variables**, параметры Spring —
в **Program arguments**, например `--embedded.kafka.port=9094`.

`bind-host` определяет, где сервер слушает соединения; `advertised-host` —
куда клиенты подключаются после получения метаданных. Указанный
`advertised-host` должен быть доступен со стороны клиента.

## Данные

Топики, сообщения и метаданные сохраняются в `./data/kafka` относительно
рабочей директории процесса. При запуске командами выше каталог находится
в корне проекта.

При первом запуске каталог форматируется и получает идентификатор кластера.
Если `meta.properties` уже существует, форматирование не выполняется.
Для продолжения работы после перезапуска используй тот же каталог.
Для второго экземпляра задай отдельный каталог и оба свободных порта.

## Проверки кода

```shell
./mvnw -pl embedded-kafka -am test
```

Тест поднимает временный брокер на случайных портах, отправляет сообщение
и проверяет, что его можно прочитать после перезапуска брокера с тем же
каталогом данных.
