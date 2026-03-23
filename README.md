# Truva – Kotlin Projesi

Gradle ile oluşturulmuş basit bir Kotlin projesi.

## Gereksinimler

- **JDK 17** veya üzeri
- (İsteğe bağlı) Gradle 8.5+ – wrapper kullanmak için gerekmez

## Kurulum

### Seçenek 1: IntelliJ IDEA / Android Studio

1. **File → Open** ile `Truva` klasörünü açın.
2. IDE projeyi Gradle projesi olarak tanıyacak ve bağımlılıkları indirecektir.

### Seçenek 2: Gradle Wrapper (komut satırı)

Gradle yüklüyse, proje klasöründe bir kez wrapper oluşturun:

```bash
cd Truva
gradle wrapper
```

Ardından:

```bash
.\gradlew run
```

### Seçenek 3: Gradle yüklüyse doğrudan

```bash
cd Truva
gradle run
```

## Proje Yapısı

```
Truva/
├── build.gradle.kts      # Build yapılandırması
├── settings.gradle.kts   # Proje adı
├── gradle.properties
├── src/
│   ├── main/kotlin/      # Ana kaynak kod
│   │   └── Main.kt
│   └── test/kotlin/      # Testler
│       └── TruvaTest.kt
└── README.md
```

## Komutlar

| Komut | Açıklama |
|-------|----------|
| `gradlew run` | Uygulamayı çalıştırır |
| `gradlew build` | Derler ve testleri çalıştırır |
| `gradlew test` | Sadece testleri çalıştırır |
| `gradlew clean` | Build çıktılarını temizler |

## Lisans

Örnek proje – serbestçe kullanabilirsiniz.
