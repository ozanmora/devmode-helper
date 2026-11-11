# DevMode Helper (`works.mora.devmode`)

Android için One-tap geliştirici modu ve otomasyon aracı (Samsung One UI, Modlar ve Rutinler, Auto Blocker uyumlu).

## Kısayol Komutları

Kullanıcı aşağıdaki ifadeleri kullandığında ilgili eylemi doğrudan çalıştır:

- **"testflight al"**:
  Build alır, `AndroidManifest.xml`'deki sürüm ve build numarası ile git tag koyar (`vX.Y.Z-bN`), tag'i origin'e push eder ve GitHub Releases üzerinde Pre-release oluşturup APK ve `.sha256` dosyalarını yükler.
  ```bash
  ./release.sh prerelease
  ```

- **"telefona yükle"**:
  APK'yı derler, USB ile bağlı telefona yükler (`adb install -r`) ve `WRITE_SECURE_SETTINGS` iznini tek adımda otomatik verir.
  ```bash
  ./release.sh install
  ```

- **"build al"**:
  Sadece yerel APK derler (`build/devmode-helper.apk`).
  ```bash
  ./release.sh build
  ```

## Mimari ve Derleme

- **Gradle kullanılmaz**: Yalnızca Android SDK (`platforms/android-36`, `build-tools/36.0.0`) ve JDK 17+ ile `./build.sh` üzerinden derlenir.
- **Fastlane**: Dağıtım otomasyonu `fastlane/Fastfile` ve `release.sh` içerisindedir.
- **Harici kütüphane bağımlılığı yoktur**: One UI arayüzü ve bileşenleri `OneUi.java` içerisinde saf Android sınıflarıyla çizilir.
