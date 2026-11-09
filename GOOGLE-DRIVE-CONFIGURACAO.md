# Conexão Google — Dose 1.7.1

O cliente OAuth Android informado pelo responsável está em `config/google-drive.json`:

`269114138083-sca5l30oio5m815ncb61le432t317iqm.apps.googleusercontent.com`

O APK oferece **Conectar conta Google**. Não foi feita uma conexão com conta real nesta entrega. O ID não permite consultar ou confirmar as configurações privadas do projeto Google Cloud.

| Campo | Valor |
|---|---|
| Pacote | `br.com.well.dose` |
| SHA-1 da assinatura | `83:53:26:A0:B3:1A:E6:15:25:0A:77:95:4C:CA:92:45:C5:5F:62:65` |
| Escopo | `https://www.googleapis.com/auth/drive.file` |
| SDK | Google Identity Services / AuthorizationClient, play-services-auth 22.0.0 |

## Testar no celular

1. Instale Dose-1.7.1.apk como atualização, sem desinstalar o app.
2. Abra **Alertas → Backup e Google Drive → Conectar conta Google**.
3. Confira o aviso sobre os dados incluídos e toque em **Escolher conta**. A próxima tela é do Google Play Services; não exige o aplicativo Drive instalado.
4. Escolha a conta configurada como usuária de teste no projeto, quando aplicável, e autorize os arquivos do Dose.
5. Confira o nome e o e-mail exibidos. Conectar a conta consulta sua identidade e a existência de uma cópia; ainda não envia o conteúdo local.
6. **Fazer backup agora** cria ou atualiza `Dose/Dose-backup.json`. A conclusão só é registrada após o servidor confirmar tamanho e MD5. Confira o arquivo no Drive da conta escolhida.
7. Se desejar, ative o diário. O Android agenda aproximadamente uma vez por dia, com rede e bateria suficiente. Conectar a conta não ativa o diário automaticamente.

A descoberta de uma cópia existente exige restaurar ou confirmar substituição antes de gravar. A restauração pede confirmação, confere integridade e importa em transação, sem descontar estoque novamente. A troca de conta pausa o diário; desconectar tenta revogar o acesso, preservando os medicamentos e arquivos já salvos.

## Conferir no Google Cloud

No mesmo projeto do cliente, habilite a Google Drive API, complete a tela de consentimento e confira os usuários de teste. O cliente deve ser do tipo **Android** e conter exatamente o pacote e SHA-1 acima. O Google identifica o APK pelo pacote/certificado; o ID armazenado localmente registra a configuração e não substitui o cadastro no servidor.

Se aparecer erro de configuração OAuth (código 10), confira esses campos e o projeto selecionado. Se a conta não estiver permitida para teste ou o consentimento tiver sido revogado, ajuste o cadastro ou reconecte no app. Se o erro persistir, informe a mensagem exibida para diagnóstico.

## Condições de uso da API

Os [termos do Drive](https://developers.google.com/workspace/drive/api/terms), seção Customer implementation, condicionam backup de conteúdo de um app ao consentimento prévio expresso do Google por escrito. Essa condição é responsabilidade do titular do projeto e é separada do cadastro OAuth e do consentimento do usuário. Esta entrega não afirma que essa aprovação foi obtida. A configuração não contém uma opção local declarando aprovação externa: um booleano no APK não comprovaria autorização do Google.

## Páginas públicas e saída do modo de teste

A apresentação do Dose, a política de privacidade e os termos estão em `docs/`, preparados para GitHub Pages. Veja [PUBLICACAO-GITHUB.md](PUBLICACAO-GITHUB.md) para publicar essa pasta e preencher os links reais no Branding do mesmo projeto Google Cloud.

No modo Testing, a conta precisa constar em Test users e a autorização para os escopos do app expira após sete dias. Para retirar esse prazo específico, complete o cadastro solicitado pelo Google e use Audience → Publish app. Uso pessoal tem exceção de verificação OAuth; essa exceção não declara aprovação do cadastro nem substitui as pendências mostradas pelo painel. A mudança de status não garante uma autorização que nunca precise ser renovada.

## Compilar

```bash
python3 tools/build.py --sdk /caminho/android-sdk --test
```

O projeto usa por padrão `config/google-drive.json`, que contém apenas dados públicos. Para outro cadastro, copie `google-drive-config.example.json` para `private/google-drive.json`, preencha cliente/pacote/SHA-1 e use:

```bash
python3 tools/build.py --sdk /caminho/android-sdk --test \
  --google-config private/google-drive.json
```

Um arquivo com `enabled: false`, cliente ausente ou pacote/certificado diferentes mantém a conexão desabilitada. Senhas, segredos de cliente e tokens não fazem parte dessa configuração. A chave de assinatura permanece privada em `signing/`, fora do Git.

## Limites da verificação

É necessário conferir em aparelho com Google Play Services: seleção e cancelamento da conta, consentimento recusado, primeiro upload, atualização do mesmo arquivo, restauração, falta de rede, acesso revogado, troca de conta e diário. Esses fluxos com uma conta real não foram executados nesta entrega. Não há mesclagem entre aparelhos nem garantia contra alterações simultâneas durante um upload.

Os backups por arquivo das versões anteriores continuam em **Abrir opções de arquivo**. O escopo drive.file permite apenas arquivos criados/autorizados para o Dose: uma exportação manual antiga não fica automaticamente acessível pela nova integração.

Referências: https://developer.android.com/identity/authorization e https://developers.google.com/workspace/drive/api/guides/manage-uploads .
