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
* Mercurial

Clone OpenJDK repositories and check out required revisions

```
hg clone -u 57336c319de8 http://hg.openjdk.java.net/jdk8u/jdk8u/jdk jdk8
```

or download required archives and unpack

```
mkdir -p jdk8
wget -O - http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/archive/57336c319de8.tar.gz | tar -zxf - -C jdk8 --strip-components=1
```

Clone jephyr repository

```
git clone https://github.com/yngui/jephyr.git
```

Build the project

```
cd jephyr
mvn install -Djdk8.compilerExecutable=$JAVA_HOME8/bin/javac \
            -Djdk8.sourceDirectory=$PWD/../jdk8/src/share/classes
```
