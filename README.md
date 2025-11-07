**This technology is schedule for deprecation.**

This repository will be archived and will no longer be updated.

See our [Update on Plans for Privacy Sandbox Technologies](https://privacysandbox.com/news/update-on-plans-for-privacy-sandbox-technologies/).

[Privacy Sandbox feature status](https://privacysandbox.google.com/overview/status) provides more information about the status of individual APIs and platform features.

---

# OnDevicePersonalization

This repository contains sample code for [On-Device Personalization](https://developers.google.com/privacy-sandbox/protections/on-device-personalization).


## Build Instructions

### OdpSamples

- [OdpSamples](OdpSamples) contains sample Android applications for integrating with On-Device Personalization.
- The sample Apps can be [built with Android Studio with Gradle and JDK17](OdpSamples/federated-learning.md).
- [Federated Learning tutorial](OdpSamples/federated-learning.md)

### CuckooFilter

- Download and install Bazel from http://bazel.build
- Run `bazel build ...` from the root of the repository.
- The binaries will be found in the `bazel-bin/` directory.
