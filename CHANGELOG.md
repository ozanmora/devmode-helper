# Değişiklik günlüğü

Sürümler [semantik sürümlemeye](https://semver.org/lang/tr/) uyar; her sürüm `vX.Y.Z` etiketiyle işaretlenir.

## [1.12.0] - 2026-10-02
### Eklendi
- "Ekranı hep açık tut": geliştirme modu açıkken ekran şarjda değilken de kapanmaz; ekran zaman aşımı geçici
  olarak 24 saate çıkarılır, kapatınca eski değer geri gelir (#8).
- Olay günlüğü sayfası: olay türüne göre filtre çipleri, güne göre gruplama, kaydırırken sabit kalan filtre
  çubuğu (#7).
- Hakkında sayfası: sürüm, kaynak kodu, sürüm notları, lisans ve destek bağlantıları (#6).
- Geliştirme modu etkinken sistemin ya da başka bir uygulamanın kapattığı ayarlar (ör. ağ değişince kapanan
  kablosuz hata ayıklama, ekran zaman aşımı) yeniden açılır; bilerek kapatılanlara dokunulmaz, ayar başına en
  fazla dakikada bir (#9).

### Değişti
- Ana ekrandaki "Son olaylar" ve "Destek ol" bölümleri ayrı sayfalara taşındı (#6).
- "Ekranı açık tut" satırının adı "Şarjdayken ekranı açık tut" oldu (#8).

### Düzeltildi
- Hakkında sayfasında uygulama ikonunun üstü araç çubuğunun altında kalıyordu (#6).
- README'deki ikonun köşeleri beyazdı; saydam PNG olarak yeniden üretildi.

## [1.11.0] - 2026-10-02
### Eklendi
- Ana ekranda "Destek ol" bölümü: GitHub Sponsors ve Buy Me a Coffee bağlantıları, dokununca tarayıcıda açılır (#5).
- README'de destek bölümü (#5).

## [1.10.0] - 2026-09-29
### Eklendi
- "Test uygulamaları" ana ekran widget'ı: adb (USB/Wi‑Fi) ile yüklenen uygulamaları ikonlarıyla listeler,
  dokununca açar; yenile düğmesi, 15 dakikalık kontrol ve adb yenileme komutu (#3).
- Ana ekranda widget'ı eklemek için "Ana ekran" bölümü (#3).

## [1.9.0] - 2026-09-29
### Değişti
- Anahtarlar Samsung One UI'daki ile birebir: 32x20dp dolu hap, 16dp topuz; kapalıyken gri, açıkken mavi (#2).
- Ana anahtar çubuğu One UI'daki gibi: açıkken nötr gri zemin ve mavi yazı (#2).
- Sayfa, kart ve yazı renkleri Samsung'un açık/koyu tema jetonlarına göre (#2).

## [1.8.0] - 2026-09-29
### Eklendi
- İlk sürüm (#1): USB/kablosuz hata ayıklama ve ekranı açık tut için ana anahtar ve tek tek anahtarlar,
  Samsung Modlar ve Rutinler ile eşitleme, Auto Blocker algılama, kalıcı durum bildirimi,
  kablosuz hata ayıklama detay sayfası, One UI görünümü, ikon, Gradle'sız derleme ve CI.

[1.12.0]: https://github.com/ozanmora/devmode-helper/compare/v1.11.0...v1.12.0
[1.11.0]: https://github.com/ozanmora/devmode-helper/compare/v1.10.0...v1.11.0
[1.10.0]: https://github.com/ozanmora/devmode-helper/compare/v1.9.0...v1.10.0
[1.9.0]: https://github.com/ozanmora/devmode-helper/compare/v1.8.0...v1.9.0
[1.8.0]: https://github.com/ozanmora/devmode-helper/releases/tag/v1.8.0
