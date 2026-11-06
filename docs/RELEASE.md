# Release do Zu Reader

O CI (`.github/workflows/android.yml`) gera em cada push na `main` e em cada PR:

- `zu-reader-debug`: APK de debug, para instalar direto no aparelho;
- `zu-reader-release`: APK e AAB de release (com R8). Sem os secrets abaixo, saem **sem assinatura**.

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
