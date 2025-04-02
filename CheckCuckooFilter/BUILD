load("@rules_java//java:defs.bzl", "java_binary")

package(default_visibility = ["//visibility:public"])

java_binary(
    name = "CheckCuckooFilter",
    srcs = glob(["src/main/**/*.java"]),
    deps = [
        "@maven//:com_google_guava_guava",
        "@maven//:org_json_json",
        "@setfilters//java/com/google/setfilters/cuckoofilter",
    ],
)
