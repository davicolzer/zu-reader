# Release do Zu Reader

O CI (`.github/workflows/android.yml`) tem três etapas:

1. **Testes e lint**: testes do leitor e da visão, lint do app.
2. **Build dos APKs**: APK de teste, APK e AAB de release.
3. **Publicar release**: só na `main` (ou rodando a action à mão em **Actions → Android CI → Run workflow**). Cria uma release em **Releases** com os arquivos.

## Instalar para testar

Na página da release, baixe `zu-reader-<versão>-teste.apk` no celular e abra. Ele é assinado com a
chave de teste do projeto (`app/debug.keystore`, senha pública `android`), então cada versão nova
instala por cima da anterior. O número da versão (`versionCode`) é o número da execução do CI.

A chave de teste serve só para testes. Para publicar, use uma chave própria (abaixo).

## Assinar o release

1. Crie uma chave (uma vez só; guarde o arquivo e as senhas fora do repositório):

   ```bash
   keytool -genkeypair -v -keystore zu-release.jks -alias zu-reader \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. No GitHub, em **Settings → Secrets and variables → Actions**, crie:

   | Secret | Valor |
   |---|---|
   | `ZU_KEYSTORE_BASE64` | conteúdo de `base64 -w0 zu-release.jks` |
   | `ZU_KEYSTORE_PASSWORD` | senha do keystore |
   | `ZU_KEY_ALIAS` | `zu-reader` (ou o alias usado) |
   | `ZU_KEY_PASSWORD` | senha da chave |

3. O próximo build gera o release assinado.

Para assinar localmente, exporte `ZU_KEYSTORE_PATH` (caminho do `.jks`) e as três variáveis acima e rode `./gradlew bundleRelease`.

## Antes de publicar

- Aumentar `versionCode` e `versionName` em `app/build.gradle.kts`.
- Testar o APK de release num aparelho (câmera, galeria, histórico).
- Nome na loja sem "Fez" (marca registrada) e aviso de projeto de fã.
- Ícone da loja: `docs/design/icon-512.png`.
