# fez-translate (Zu Reader)

App Android em Kotlin que lê textos no alfabeto Zu, do jogo Fez, a partir de uma foto e traduz o resultado.

- Plano: [docs/PLANO.md](docs/PLANO.md)
- Design: [docs/design/mockup.png](docs/design/mockup.png) (fonte em [mockup.html](docs/design/mockup.html)) e ícone [icon.svg](docs/design/icon.svg)

## Versões

| Ferramenta | Versão |
|---|---|
| JDK | 25 (LTS) |
| Gradle | 9.8.2 |
| Kotlin | 2.4.21 |
| Android Gradle Plugin | 9.5.1 |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk | 37 |
| minSdk | 26 (Android 8.0) |

O Gradle roda no JDK 25. O bytecode é gerado para Java 21, que é o que o Android aceita.

## Estrutura

```
app/        Activity, tema, telas (Compose)
core/zu/    Kotlin puro: glifos, matriz de bits e leitura (testável sem emulador)
```

## Como rodar

Requisitos: JDK 25 e Android SDK com a plataforma 37 (o Android Studio instala).

```bash
./gradlew build                 # compila, roda testes e lint
./gradlew :app:installDebug     # instala no aparelho/emulador conectado
./gradlew :core:zu:test         # só os testes do leitor
```

O CI (`.github/workflows/android.yml`) roda `./gradlew build` em cada push na `main` e em cada PR, e publica o APK de debug como artefato.

## Licenças

Fontes Silkscreen, Figtree e JetBrains Mono sob a SIL Open Font License (ver `third_party/fonts/`).

Projeto de fã, sem vínculo com a Polytron.
