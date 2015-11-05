# Jephyr
Continuations and lightweight threads for the JVM

## Features
* EasyFlow is a fast and safe continuations library that is able to detect uninstrumented and synchronized code and supports reflection
* SPI for thread implementations pluggable into regular Java threads
* Pluggable implementation of lightweight threads based on continuations
* Support for pluggable thread implementations (and hence lightweight threads) in collections, concurrency utilities, NIO
* No external dependencies
* No additional API besides the standard one

## Basics

Jephyr amends the standard Java Thread, LockSupport and other classes to support pluggable thread implementations (e.g. lightweight thread implementation) and puts them in a separate package. When instrumenting application class files (using Maven or Java Agent) references to the standard Java classes are replaced with references to the corresponding amended classes.

## Building Jephyr

Prerequisites

* JDK 8
* Maven 3.3.3 or later

Clone jephyr repository

```
git clone https://github.com/yngui/jephyr.git
```

Build the project

```
cd jephyr
mvn clean install
```
