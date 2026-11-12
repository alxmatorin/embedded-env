# embedded-ignite

Локальный узел Apache Ignite 2 с подключением через thin client.
По умолчанию кеши работают в памяти; сохранение на диск включается настройкой.
Требуется JDK 21. Все команды ниже выполняются из корня проекта.

## Запуск

```shell
./mvnw -pl embedded-ignite spring-boot:run
```

В IDEA запускай `embeded.ignite.EmbeddedIgniteApplication` с JDK 21
и **Working directory** = `$PROJECT_DIR$`. Перед запуском добавь параметры
JVM, описанные ниже. Для остановки используй `Ctrl+C` или Stop в IDEA.

Сборка и запуск JAR:

```shell
./mvnw -pl embedded-ignite -am package
java -jar embedded-ignite/target/embedded-ignite-1.0-SNAPSHOT.jar
```

## Подключение

Адрес thin client по умолчанию — `127.0.0.1:10800`.
Discovery использует `127.0.0.1:47500`, обмен между узлами — `127.0.0.1:47100`.
Для всех трёх портов диапазон автоматического подбора отключён: указанные
порты должны быть свободны.

Готовая проверка записи, чтения и удаления записи кеша:
[ignite-test](../tests/ignite-test/README.md).

## Настройки

Конфигурация: [application.yml](src/main/resources/application.yml).

| Параметр Spring | Переменная окружения | По умолчанию | Назначение |
| --- | --- | --- | --- |
| `embedded.ignite.host` | `IGNITE_HOST` | `127.0.0.1` | Локальный адрес узла и thin client |
| `embedded.ignite.port` | `IGNITE_PORT` | `10800` | Порт thin client |
| `embedded.ignite.discovery-port` | `IGNITE_DISCOVERY_PORT` | `47500` | Порт discovery на loopback |
| `embedded.ignite.communication-port` | `IGNITE_COMMUNICATION_PORT` | `47100` | Порт обмена между узлами на loopback |
| `embedded.ignite.persistence-enabled` | `IGNITE_PERSISTENCE_ENABLED` | `false` | Сохранять данные кешей на диск |
| `embedded.ignite.work-directory` | `IGNITE_WORK_DIRECTORY` | `./data/ignite` | Рабочий каталог узла |

В IDEA переменные задаются в **Environment variables**, параметры Spring —
в **Program arguments**, например `--embedded.ignite.persistence-enabled=true`.

## Хранение данных

При `persistence-enabled=false` записи кешей теряются после остановки узла.
Рабочий каталог всё равно используется для служебных файлов.

Для сохранения данных между перезапусками:

```shell
IGNITE_PERSISTENCE_ENABLED=true ./mvnw -pl embedded-ignite spring-boot:run
```

Данные сохраняются в `./data/ignite` относительно рабочей директории процесса.
При запуске командами выше это каталог в корне проекта. Для восстановления
данных используй тот же каталог; идентификатор узла задан как `embedded-ignite`.
При включённом persistence кластер активируется автоматически.

Начальный размер региона данных — 32 МиБ, максимальный — 128 МиБ.
Эти значения заданы в
[IgniteServerConfiguration](src/main/java/embeded/ignite/IgniteServerConfiguration.java).

## Параметры JVM

Ignite использует внутренние API JDK. Для `spring-boot:run` и Maven-тестов
параметры уже настроены в [pom.xml](pom.xml); собранный JAR содержит `Add-Opens`
в манифесте.

При прямом запуске main из IDEA скопируй список `--add-opens` из комментария
[EmbeddedIgniteApplication](src/main/java/embeded/ignite/EmbeddedIgniteApplication.java)
в **VM options**. Общий список хранится в свойстве `ignite.jvm.arguments`
корневого [pom.xml](../pom.xml).

При `ExceptionInInitializerError` проверь исходную причину выше в логе
и наличие этих VM options. После изменения параметров перезапусти JVM.

## Проверки кода

```shell
./mvnw -pl embedded-ignite -am test
```

Тесты запускают узел во временном каталоге на случайных портах. Проверяются
обмен данными между двумя клиентами, очистка кеша после перезапуска в режиме
памяти и сохранение записи при включённом persistence.
