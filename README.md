# Zephyr
Continuations and lightweight threads for the JVM

## Features
* EasyFlow is a fast and safe continuations library that is able to detect uninstrumented and synchronized code and supports reflection
* SPI for thread implementations pluggable into standard Java threads
* Pluggable implementation for lightweight threads based on EasyFlow
* No external dependencies
* No API

## Basics

Zephyr amends the standard Java Thread, LockSupport and other classes to support pluggable thread implementations (e.g. lightweight thread implementation) and puts them in a separate package. When instrumenting application class files (using Maven or Java Agent) references to the standard Java classes are replaced with references to the corresponding amended classes.

## Building Zephyr

Clone OpenJDK repositories and check out required revisions

```
hg clone -u 55f6804b4352 http://hg.openjdk.java.net/jdk7u/jdk7u/jdk jdk7
hg clone -u 57336c319de8 http://hg.openjdk.java.net/jdk8u/jdk8u/jdk jdk8
```

or download required archives and unpack

```
mkdir -p jdk7
wget -O - http://hg.openjdk.java.net/jdk7u/jdk7u/jdk/archive/55f6804b4352.tar.gz | tar -zxf - -C jdk7 --strip-components=1
mkdir -p jdk8
wget -O - http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/archive/57336c319de8.tar.gz | tar -zxf - -C jdk8 --strip-components=1
```

Clone zephyr repository

```
git clone https://github.com/yngui/zephyr.git
```

Build the project

```
cd zephyr
mvn install -Djdk7.compilerExecutable=$JAVA_HOME7/bin/javac \
            -Djdk7.sourceDirectory=$PWD/../jdk7/src/share/classes \
            -Djdk8.compilerExecutable=$JAVA_HOME8/bin/javac \
            -Djdk8.sourceDirectory=$PWD/../jdk8/src/share/classes
```
