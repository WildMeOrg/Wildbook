#!/bin/bash

sudo mvn clean
mvn install -DskipTests -Dmaven.javadoc.skip=true
