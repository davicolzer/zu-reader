# Release do Zu Reader

O CI (`.github/workflows/android.yml`) tem três etapas:

| Etapa | No PR | Depois do merge na `main` |
|---|---|---|
| 1 · Testes e lint | roda | pulada (o código já passou no PR) |
| 2 · Build dos APKs | roda, depois dos testes | roda |
| 3 · Publicar release | não roda | roda: cria uma release em **Releases** com os arquivos |

Também dá para rodar à mão em **Actions → Android CI → Run workflow** (faz o build e publica a release).

Como a `main` não roda testes, vale proteger a branch no GitHub (**Settings → Branches**) exigindo PR com o
job "1 · Testes e lint" aprovado antes do merge.

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
