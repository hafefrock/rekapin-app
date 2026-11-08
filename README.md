# Rekapin

Rekapin adalah aplikasi Android Kotlin dan Jetpack Compose untuk menganalisis video anime per segmen menggunakan Gemini File API, menyusun ringkasan/narasi, dan mengelola hasil serta seri. Aplikasi menyimpan riwayat analisis di Room; metadata fitur tambahan disimpan di SharedPreferences.

## Menjalankan aplikasi

1. Buka folder repository ini di Android Studio dan tunggu Gradle sync selesai.
2. Gunakan Android SDK yang sesuai dengan konfigurasi `compileSdk` pada `app/build.gradle.kts`.
3. Jalankan konfigurasi `app` pada emulator atau perangkat Android (minimum API 24).
4. Masukkan Gemini API key melalui Pengaturan di aplikasi. Untuk pengembangan lokal, `.env` dapat menyediakan `GEMINI_API_KEY` sebagai fallback build; jangan commit `.env` atau memasukkan API key ke source control.

## Tes dan build

GitHub Actions menjalankan unit test dan tes instrumentasi emulator, kemudian membangun APK debug. Perintah Gradle yang digunakan CI:

```sh
gradle testDebugUnitTest
# instrumentasi emulator: gradle connectedDebugAndroidTest
gradle assembleDebug
```

APK debug diunggah sebagai artifact workflow bernama `rekapin-apk`. Unduh artifact dari run GitHub Actions yang sesuai dengan commit yang diinginkan. Hasil tes unit diunggah terpisah ketika tersedia.

## Keamanan dan signing

API key yang dimasukkan pengguna dienkripsi dengan AES-GCM melalui Android Keystore. File preferences yang menyimpan key dikecualikan dari Android cloud backup dan device transfer. Berkas `.env`, API key, dan keystore tidak boleh dimasukkan ke Git.

Build release mengharapkan konfigurasi signing melalui environment (`KEYSTORE_PATH`, `STORE_PASSWORD`, dan `KEY_PASSWORD`) serta keystore dengan alias `upload`. Build debug yang dihasilkan CI bukan APK release untuk distribusi Play Store. Jangan menghapus konfigurasi signing dari Gradle untuk mengatasi kegagalan; siapkan secret signing yang benar untuk build release.
