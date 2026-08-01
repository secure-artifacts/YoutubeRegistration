#!/bin/sh
#
# Gradle start up script for UN*X
#
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
APP_HOME="`pwd -P`"
MAX_FD="maximum"
warn () {
    echo "$*"
}
die () {
    echo
    echo "$*"
    echo
    exit 1
}
if [ "$#" -gt 0 ] ; then
    if [ "$1" = "--help" ] || [ "$1" = "-h" ] ; then
        echo
        exit 0
    fi
fi
if [ "${JAVA_HOME}" = "" ] ; then
    JAVA="java"
    warn "JAVA_HOME is not set"
else
    JAVA="${JAVA_HOME}/bin/java"
fi
if [ -z "${GRADLE_WRAPPER_HOME}" ] ; then
    GRADLE_WRAPPER_HOME="${APP_HOME}/.gradle"
fi
exec "$JAVA" \
    -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
