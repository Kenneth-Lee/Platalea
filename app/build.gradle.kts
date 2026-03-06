import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kenny.localmanager"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.kenny.localmanager"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "0.3"
    }
    signingConfigs {
        create("release") {
            val propFile = rootProject.file("local.properties")
            if (propFile.exists()) {
                val props = Properties()
                propFile.inputStream().use { props.load(it) }
                storeFile = rootProject.file(props.getProperty("RELEASE_STORE_FILE", "app/release.keystore"))
                storePassword = props.getProperty("RELEASE_STORE_PASSWORD", "localmanager")
                keyAlias = props.getProperty("RELEASE_KEY_ALIAS", "localmanager")
                keyPassword = props.getProperty("RELEASE_KEY_PASSWORD", "localmanager")
            } else {
                storeFile = file("release.keystore")
                storePassword = "localmanager"
                keyAlias = "localmanager"
                keyPassword = "localmanager"
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "META-INF/DEPENDENCIES"
            pickFirsts += "OSGI-INF/l10n/plugin.properties"
            pickFirsts += "org/apache/sshd/sshd-version.properties"
            pickFirsts += "plugin.properties"
            pickFirsts += "org/apache/sshd/moduli"
            pickFirsts += "org/apache/sshd/common/kex/*.prime"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.bouncycastle:bcpg-jdk18on:1.77")
    implementation("org.bouncycastle:bcutil-jdk18on:1.77")
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
    implementation("org.pgpainless:pgpainless-core:1.7.6")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.apache.ftpserver:ftpserver-core:1.2.1")
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.12.0.202106070339-r")
    implementation("org.eclipse.jgit:org.eclipse.jgit.ssh.apache:5.12.0.202106070339-r") {
        exclude(group = "org.apache.sshd", module = "sshd-osgi")
    }
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
