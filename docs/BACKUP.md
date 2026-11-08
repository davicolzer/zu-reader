# Backup da coleção

O Zu Reader tem duas formas de guardar e trazer de volta a coleção (fotos, vídeos, textos,
nomes, descrições, desenhos das anotações e as letras aprendidas com as correções). Nas duas,
**restaurar substitui** a coleção do celular, depois de uma confirmação, e o app reabre.

| | Google Drive (direto) | Cópia num arquivo |
|---|---|---|
| Para quem usa | "Fazer backup agora" e "Restaurar do Drive" nas Configurações | "Salvar cópia" e "Restaurar cópia" nas Configurações |
| Conta | A conta Google **que já está no celular**: na primeira vez, a tela do Google pede "Permitir". Sem login no app | Nenhuma: a pessoa escolhe onde salvar (no celular, no Drive, onde quiser) |
| Onde fica | Pasta de dados do app no Drive (escondida, só o Zu Reader enxerga; um arquivo, trocado a cada backup) | No arquivo `zu-reader-backup.zubackup`, onde a pessoa escolher |
| Precisa configurar | Sim, uma vez (abaixo) | Não |

O arquivo de backup tem a extensão própria do app, **`.zubackup`** (por dentro é um zip), com o banco de dados, as fotos e os vídeos da coleção e um
`backup.txt` com a versão. Ao restaurar, o app confere antes de mexer em qualquer coisa: recusa
arquivos que não são backup do app, caminhos que saem da pasta, banco estragado e backup de uma
versão mais nova do app.

## Segurança do backup no Drive

- **Acesso mínimo**: o app pede só o escopo `https://www.googleapis.com/auth/drive.appdata` (a
  pasta de dados do app). Ele **não vê nem mexe** nos arquivos da pessoa no Drive. O Google
  classifica esse escopo como não sensível (não precisa de auditoria de segurança).
- **Sem segredo no app**: no Android, o Google reconhece o app pelo pacote
  (`com.davicolzer.zureader`) e pelo SHA-1 da **chave que assina o APK**. Não há senha nem chave
  de API dentro do app. O acesso (token) é dado pelo Google Play Services do celular, usado só
  durante o envio e não é guardado pelo app. Tudo vai por HTTPS.
- **Só com a chave privada**: registre no Google **apenas o SHA-1 da chave privada de release**.
  Nunca registre a chave de teste (`app/debug.keystore`): ela é pública, e qualquer um poderia
  assinar um app falso com ela e pedir acesso aos backups.

## Configurar o backup no Drive (uma vez)

### 1. Chave privada de assinatura

Siga [RELEASE.md → Assinar o release](RELEASE.md#assinar-o-release): crie `zu-release.jks` (guarde
fora do repositório) e coloque os quatro secrets no GitHub. A partir daí, a release traz
`zu-reader-<versão>.apk` assinado com essa chave.

Pegue o SHA-1 dela:

```bash
keytool -list -v -keystore zu-release.jks -alias zu-reader | grep SHA1
```

> **Trocar de chave apaga a coleção do celular**: o Android não instala um APK assinado com outra
> chave por cima. Antes de trocar, use **Salvar cópia** (na versão atual), desinstale, instale a
> versão assinada com a chave privada e use **Restaurar cópia**.

### 2. Google Cloud Console (com a sua conta Google)

1. Em <https://console.cloud.google.com/>, crie um projeto (ex.: "Zu Reader").
2. **APIs e serviços → Biblioteca**: ative a **Google Drive API**.
3. **APIs e serviços → Tela de consentimento OAuth** (Google Auth Platform):
   - Tipo **Externo**; nome do app "Zu Reader"; e-mail de suporte.
   - Em **Escopos**, adicione `.../auth/drive.appdata`.
   - Em **Público**, deixe em **Teste** e adicione o seu e-mail (e o de quem for testar) como
     usuário de teste. Para liberar para todos, publique o app (o escopo não exige verificação).
4. **APIs e serviços → Credenciais → Criar credenciais → ID do cliente OAuth**:
   - Tipo **Android**; nome do pacote `com.davicolzer.zureader`; o **SHA-1 da chave privada**.

Não é preciso colocar nada no código: o app passa a conseguir o acesso assim que essa credencial
existe. Até lá, "Fazer backup agora" mostra "Não deu para entrar na conta Google" (a cópia num
arquivo funciona sempre).
