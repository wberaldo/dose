# Ativação da conexão Google — Dose 1.7.0

## Estado desta entrega

O código usa Google Identity Services (`play-services-auth:22.0.0`), autorização de conta e a API REST do Drive. Inclui conexão, troca e desconexão de conta, backup manual, restauração e trabalho diário. **O APK entregue ainda não está habilitado para conectar uma conta real**, pois não há projeto OAuth do responsável pelo Dose configurado nesta entrega. A tela informa essa pendência, não simula uma conta e não envia dados.

A validação local não substitui a autorização real com uma conta Google. Não foi criado projeto Google Cloud nem concedida autorização em nome do usuário.

## Dados públicos para cadastrar o cliente Android

| Campo | Valor |
|---|---|
| Nome sugerido | Dose Android pessoal |
| Pacote Android | `br.com.well.dose` |
| SHA-1 da assinatura desta entrega | `83:53:26:A0:B3:1A:E6:15:25:0A:77:95:4C:CA:92:45:C5:5F:62:65` |
| Permissão solicitada | `https://www.googleapis.com/auth/drive.file` |
| SDK de autorização | Google Identity Services, AuthorizationClient |

Não envie senha da conta Google, segredo OAuth, tokens, chave privada nem senha do keystore. O ID público do cliente Android e a confirmação do cadastro são suficientes para configurar a compilação. Esse cliente Android não utiliza um segredo de cliente no aplicativo. O Google identifica o pacote e o certificado do APK; o ID no JSON abaixo é um registro local da configuração, não substitui o cadastro no servidor Google.

## Configuração externa necessária

1. No [Google Cloud Console](https://console.cloud.google.com/), crie ou escolha um projeto sob sua responsabilidade.
2. Habilite a **Google Drive API** no projeto.
3. Em **Google Auth Platform**, configure nome do aplicativo, contato e público. Se o aplicativo estiver em teste, inclua a conta que fará o teste na lista de usuários de teste, quando aplicável. Configure os dados de privacidade exigidos pelo Google para o público escolhido.
4. Crie um **cliente OAuth do tipo Android** com o pacote e SHA-1 da tabela. Preserve a chave pessoal que acompanha esta entrega; uma assinatura diferente exige outro cadastro OAuth. Se publicar pela Play Store, cadastre também o certificado efetivo da assinatura do Google Play.
5. Confira a política de uso da API antes de ativar: os [termos do Drive](https://developers.google.com/workspace/drive/api/terms), seção Customer implementation, condicionam backup de conteúdo de um app ao consentimento prévio expresso do Google por escrito. O responsável pelo projeto deve obter e guardar essa autorização. O consentimento OAuth do usuário não substitui essa condição da API.
6. Concluído o cadastro e atendida essa condição, preencha o arquivo privado e recompile conforme abaixo. Não marque uma autorização que não foi obtida.

A escolha de conta usa a interface oficial do Google Play Services no celular. Não exige o aplicativo Drive instalado. O conector Google Drive do ChatGPT não configura as credenciais OAuth do Dose.

## Arquivo privado e compilação

Copie `google-drive-config.example.json` para `private/google-drive.json`. Preencha `android_client_id` com o ID real do cliente Android. Só então habilite as duas opções booleanas, após concluir as etapas externas.

```bash
python3 tools/build.py --sdk /caminho/android-sdk --test \
  --google-config private/google-drive.json
```

Sem `--google-config`, a compilação permanece explicitamente desabilitada para conexão Google. A validação também exige pacote e SHA-1 iguais aos do APK instalado. A pasta `private/` é ignorada pelo Git. Não há segredo de cliente ou token para incluir nesse JSON.

## Comportamento após a ativação

Em **Alertas → Backup e Google Drive**, toque em **Conectar conta Google** e escolha a conta na tela oficial. Autorize o acesso aos arquivos criados pelo Dose. A conta exibida é consultada no Drive; antes de salvar ou restaurar, seu identificador é conferido novamente.

**Fazer backup agora** cria a pasta `Dose` e um arquivo `Dose-backup.json`. As próximas gravações atualizam esse arquivo. A conclusão só é registrada depois que o servidor confirma tamanho e MD5 dos bytes enviados. Tokens ficam apenas na memória e no mecanismo de autorização do Google Play Services, nunca no JSON de backup, no banco ou nas preferências do Dose.

O diário começa desligado. Ativá-lo agenda um JobScheduler persistente, aproximadamente a cada 24 horas, com rede e bateria suficiente. O Android pode atrasar o trabalho. O serviço pede novo acesso ao Google sem abrir telas em segundo plano; se for necessário novo consentimento, pausa o diário e pede reconexão dentro do app.

Ao encontrar uma cópia existente, o Dose exige restaurar ou confirmar a substituição. Se a cópia for modificada por outro dispositivo desde a última consulta conhecida, a próxima tentativa é bloqueada para nova escolha. Não há mesclagem entre dispositivos nem garantia contra alterações simultâneas durante um upload.

**Restaurar do Google Drive** confere tamanho e MD5 antes de validar o JSON e substituir os dados locais em transação. Não confirma tomadas nem desconta estoque novamente. A conexão Google não é copiada para outro celular pelo arquivo portátil.

**Trocar conta** pausa o diário e abre a escolha oficial. **Desconectar** cancela o trabalho e tenta revogar o acesso. Os medicamentos e os arquivos já existentes no Drive são mantidos; se a revogação remota não puder ser concluída, a tela informa que o acesso também pode ser removido nas configurações da conta Google.

O escopo `drive.file` permite trabalhar com arquivos criados/autorizados para o Dose, sem acesso geral ao Drive. Arquivos exportados manualmente na versão 1.6 não ficam automaticamente visíveis à nova integração; use **Backup em arquivo** para restaurá-los e depois crie uma cópia pela conexão Google.

## Conferência obrigatória antes de considerar a integração ativa

Use um aparelho com Google Play Services e o APK recompilado com o cadastro real. Confira seleção e cancelamento de conta, autorização recusada, nome/e-mail corretos, primeiro upload, atualização do mesmo arquivo, tamanho/MD5, restauração, falta de rede, espaço insuficiente, acesso revogado, troca de conta e diário executado pelo Android. Verifique o arquivo na conta conectada. Esses testes com uma conta real **não foram executados nesta entrega**.

## Referências oficiais

- https://developer.android.com/identity/authorization
- https://developers.google.com/identity/authorization/android
- https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- https://developers.google.com/workspace/drive/api/guides/manage-uploads
- https://developers.google.com/workspace/drive/api/terms
