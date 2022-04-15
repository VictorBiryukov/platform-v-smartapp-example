## Шаблон: java11

Шаблон Java 11 использует maven в качестве системы сборки проекта.

Maven: 3.6.3

Java: OpenJDK 11

Jetty: 11.0.0


### Структура проекта

    root
     ├ src                              maven source route
     │ └ main
     │   └ java                         implement your function here
     │     └ handlers
     │       └ Handler.java             handler for this function
     │
     └ pom.xml                          place dependencies of your function here


### Подключение внешних зависимостей

Внешние зависимости могут быть определены в ```./pom.xml``` проекта функции.