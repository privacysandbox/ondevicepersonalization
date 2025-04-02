plugins {
  id("com.android.application")
}

android {
  namespace = "com.example.odpsamplenetwork"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.example.odpsamplenetwork"
    minSdk = 35
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  sourceSets.getByName("main") {
    java.srcDir("src/main/java")
    java.srcDir("../../setfilters/setfilters/src")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_10
    targetCompatibility = JavaVersion.VERSION_1_10
  }
}

dependencies {

  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("androidx.concurrent:concurrent-futures:1.1.0")
  implementation("com.google.android.material:material:1.8.0")
  implementation("com.google.guava:guava:33.0.0-jre")
  implementation("com.google.protobuf:protobuf-java:3.25.1")
  implementation("org.tensorflow:proto:1.15.0")
  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test.ext:junit:1.1.5")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
