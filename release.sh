#!/usr/bin/env bash
# DevMode Helper - Hızlı Dağıtım ve Yönetim Scripti
# Kullanım:
#   ./release.sh             -> Otomatik build alıp GitHub'da pre-release yayınlar ve tag koyar
#   ./release.sh prerelease  -> Aynı işlem (build + tag + github pre-release)
#   ./release.sh build       -> Sadece yerel APK derler
#   ./release.sh install     -> Derler, USB ile bağlı telefona yükler ve ADB ayar iznini otomatik verir

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Fastlane yolunu belirle
FASTLANE_CMD="fastlane"
if ! command -v fastlane >/dev/null 2>&1; then
    if [[ -x "/opt/homebrew/bin/fastlane" ]]; then
        FASTLANE_CMD="/opt/homebrew/bin/fastlane"
    else
        echo "❌ Fastlane bulunamadı. Lütfen fastlane kurun: brew install fastlane" >&2
        exit 1
    fi
fi

ACTION="${1:-prerelease}"

case "$ACTION" in
    build)
        echo "🔨 [DevMode Helper] APK derleniyor..."
        "$FASTLANE_CMD" build
        ;;

    prerelease|release)
        echo "🚀 [DevMode Helper] Pre-release süreci başlatılıyor..."
        echo "   1. APK derlenecek"
        echo "   2. Sürüm ve build numarası ile Git tag oluşturulup GitHub'a push edilecek"
        echo "   3. GitHub Releases üzerinde Pre-release olarak yayınlanacak (APK + SHA256)"
        echo ""
        "$FASTLANE_CMD" prerelease
        ;;

    install)
        echo "📱 [DevMode Helper] Derleme ve telefona yükleme başlatılıyor..."
        "$FASTLANE_CMD" build

        echo "🔍 ADB ile bağlı cihazlar kontrol ediliyor..."
        DEVICES=$(adb devices | grep -v "List of devices" | grep "device$" || true)
        if [[ -z "$DEVICES" ]]; then
            echo "❌ Bilgisayara bağlı bir Android cihaz bulunamadı!"
            echo "   Telefonun USB ile bağlı ve 'USB Hata Ayıklama'nın açık olduğundan emin olun."
            exit 1
        fi

        echo "📦 Uygulama telefona yükleniyor..."
        adb install -r build/devmode-helper.apk

        echo "🔑 WRITE_SECURE_SETTINGS izni veriliyor..."
        adb shell pm grant works.mora.devmode android.permission.WRITE_SECURE_SETTINGS

        echo "✅ Başarılı! DevMode Helper telefona yüklendi ve tüm sistem izinleri verildi."
        ;;

    help|--help|-h)
        echo "Kullanım: ./release.sh [prerelease|build|install]"
        echo ""
        echo "Komutlar:"
        echo "  prerelease (varsayılan) : Build alır, sürüm/build numarasıyla tag koyar ve GitHub Pre-release oluşturur."
        echo "  build                   : Sadece yerel APK derler (build/devmode-helper.apk)."
        echo "  install                 : Derler, USB ile bağlı telefona yükler ve adb izinlerini verir."
        ;;

    *)
        echo "❌ Geçersiz parametre: $ACTION"
        echo "Kullanım: ./release.sh [prerelease|build|install]"
        exit 1
        ;;
esac
