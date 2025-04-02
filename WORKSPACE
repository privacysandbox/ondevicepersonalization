load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

RULES_JVM_EXTERNAL_TAG = "4.2"

RULES_JVM_EXTERNAL_SHA = "cd1a77b7b02e8e008439ca76fd34f5b07aecb8c752961f9640dea15e9e5ba1ca"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    name = "maven",
    artifacts = [
        "androidx.annotation:annotation:1.7.1",
        "org.json:json:20230618",
        "com.google.guava:guava:32.1.1-jre",
        "com.google.protobuf:protobuf-java:3.25.1",
        "org.tensorflow:proto:1.15.0",
    ],
    fetch_sources = True,
    repositories = [
        "https://repo1.maven.org/maven2",
        "https://maven.google.com/",
    ],
)

http_archive(
    name = "setfilters",
    sha256 = "1fdca233e48307fe71f9bfdde42a8ad558b02873765be2f1e84835db59d2fdf4",
    strip_prefix = "setfilters-1.0.0",
    url = "https://github.com/google/setfilters/archive/refs/tags/v1.0.0.zip",
)

android_sdk_repository(name = "androidsdk")

