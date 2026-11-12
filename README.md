Запустить Postgres — [настройки](embedded-postgres/src/main/resources/application.yml).

```shell
./mvnw -pl embedded-postgres spring-boot:run
```

Проверить миграции, запись и чтение в Postgres — [настройки](tests/postgres-test/src/main/resources/application.yml).

```shell
./mvnw -pl tests/postgres-test spring-boot:run
```

Запустить Kafka — [настройки](embedded-kafka/src/main/resources/application.yml).

```shell
./mvnw -pl embedded-kafka spring-boot:run
```

Запустить Ignite — [настройки](embedded-ignite/src/main/resources/application.yml).

```shell
./mvnw -pl embedded-ignite spring-boot:run
```

Проверить отправку и чтение сообщения Kafka — [настройки](tests/kafka-test/src/main/resources/application.yml).

```shell
./mvnw -pl tests/kafka-test spring-boot:run
```

Проверить запись, чтение и удаление записи в Ignite — [настройки](tests/ignite-test/src/main/resources/application.yml).

```shell
./mvnw -pl tests/ignite-test spring-boot:run
```
