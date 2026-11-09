# Validação — Dose 1.7.1

Pacote `br.com.well.dose`, código de versão 10, banco versão 4. Execuções em 08/11/2026, horário de São Paulo (09/11 em UTC).

## Configuração desta entrega

O cliente OAuth Android informado foi incluído em `config/google-drive.json`. A configuração técnica confere cliente, pacote e assinatura originais e disponibiliza **Conectar conta Google**. O cadastro efetivo no Google Cloud só pode ser confirmado ao autorizar no aparelho.

Foi removido `drive_backup_use_authorized`: um booleano local não comprova aprovação externa do Google. A autorização da conta continua sendo solicitada pelo Google Play Services. O envio exige backup manual ou ativação explícita do diário. As condições externas da API estão em `GOOGLE-DRIVE-CONFIGURACAO.md`.

## Verificações aprovadas

| Verificação | Quantidade |
|---|---:|
| JUnit: Drive, integridade, falhas, conta e configuração | 25 |
| Regras Java de agendamento | 18 |
| Conversor MyTherapy com exemplos fictícios | 11 |
| Núcleo nativo | 25 |
| Backup e restauração nativos | 22 |
| Configuração Google e interface de conexão | 12 |
| Backup por documento Android | 18 |
| JobScheduler de arquivo, realmente despachado em segundo plano | 4 |
| Falhas do provedor, revogação e recuperação local | 7 |
| Botão Tomei na aba Hoje | 3 |
| Cancelamento somente no dia | 12 |
| Importação nativa | 20 |
| Desenhos, cores e edição | 17 |
| Áudio, notificações, estoque, adiamento e cancelamento | 26 |
| Regressão de calendário, temas, teste de alarme e tela cheia | 26 |
| Alarme real com tela cheia, app em segundo plano e aparelho bloqueado | 3 |
| Toque real no botão Tomei da tela cheia, confirmação e estoque | 2 |
| Atualização real 1.7.0 → 1.7.1, preservando todas as linhas das cinco tabelas | 1 |
| **Total de verificações aprovadas listadas** | **252** |

As 192 verificações das suítes nativas foram executadas nesta versão. As preparações de cenário não entram no total. O alarme real em tela cheia iniciou o áudio com modo vibrar e volume de alarmes zerado. A captura `alarm-full-screen.png` mostra os detalhes e ações. O toque em Tomei foi seguido de confirmação no banco e de um único desconto de estoque.

Também foram conferidos compilação de release, lint vital, assinatura original, alinhamento, versão e configuração OAuth embutida. O APK de teste foi compilado e instalado. Os APKs de distribuição e instrumentação não contêm classes DEX duplicadas. O APK distribuído é o mesmo instalado nos testes; uma recompilação limpa produziu as mesmas entradas internas, com diferenças apenas na assinatura do contêiner. Os registros estão em `verificacao/1.7.1/`.

Os testes Drive JUnit usam transporte controlado, sem conta ou rede Google real. Exercitam criação/atualização, download, checksum/tamanho, identidade da conta, cópia remota alterada e erros redigidos. O JobScheduler exercitado com gravação real foi o de **arquivo Android**, usando provedor de teste; não equivale a um diário Google com conta autenticada.

## Falha reproduzida e limite de áudio

Duas verificações adicionais de áudio em segundo plano **não passaram** com `fullscreen=false`: lembrete normal (`verify.txt`) e teste de 15 segundos (`verify_test.txt`). A entrega da notificação ocorreu, e o teste exibiu **Encerrar teste** e **Adiar 10 min**. Entretanto o Android recusou o foco de áudio do serviço e o MediaPlayer contínuo não iniciou. Esses cenários não estão contados como aprovados na tabela. A notificação ter um som configurado não comprova reprodução audível.

O log registra `Focus request DENIED`, processo em estado de serviço em primeiro plano e início permitido pela exceção de alarme exato. O AOSP Android 15 condiciona a capacidade de áudio do serviço à condição de permissão durante o uso, em `OomAdjuster.java`, e bloqueia o foco em `HardeningEnforcer.java`. Isso explica a recusa observada; não prova comportamento idêntico em todos os fabricantes. O app respeita a recusa e registra diagnóstico. Não houve redução do target SDK nem contorno das políticas de áudio.

O perfil padrão solicitado, **com tela cheia ativada**, passou a abertura e o início do áudio em segundo plano. Mantenha as autorizações de tela cheia e confirme no aparelho. Essa limitação não foi corrigida nesta alteração de configuração OAuth.

Fontes para diagnóstico:

- https://android.googlesource.com/platform/frameworks/base/+/android-15.0.0_r1/services/core/java/com/android/server/am/OomAdjuster.java (capabilityFromFGS / isFgsAllowedWiu_forCapabilities)
- https://android.googlesource.com/platform/frameworks/base/+/android-15.0.0_r1/services/core/java/com/android/server/audio/HardeningEnforcer.java (blockFocusMethod)
- https://developer.android.com/media/optimize/audio-focus

## Ambiente e problemas dos testes

Emulador AOSP Android 15/API 35, x86_64, sem Google Play Services, sem conta e sem saída física de áudio. A inicialização do MediaPlayer foi verificada pelo registro do serviço; audibilidade, volume físico e comportamento da One UI não foram medidos. Os dados usados são fictícios.

A primeira execução da suíte de alarmes expôs uma amostra incoerente: o horário era antecipado para hoje, mas a chave da ocorrência ainda indicava amanhã. A amostra foi corrigida para manter as datas coerentes; a suíte então passou 26 verificações. Durante a recompilação incremental do APK de instrumentação foram detectadas classes duplicadas, que mantinham o teste antigo carregado. A compilação limpa removeu a duplicação e os scripts agora rejeitam esse artefato. O APK de distribuição não apresentou essa duplicação.

## Validação pendente no aparelho

Seleção/cancelamento da conta Google, consentimento, emissão e renovação de token, upload/download real, revogação, troca de conta e diário Google **não foram executados com uma conta real**. O cliente fornecido não permite consultar a configuração privada do Google Cloud. É necessário testar esses fluxos no telefone com Google Play Services, seguindo o guia incluído. Não há mesclagem entre aparelhos nem garantia contra alterações simultâneas durante um upload.

## Integridade e entrega

Os hashes, certificado e tamanho exatos estão em `verificacao/1.7.1/apk-integrity.json` e `apk-signature.txt`. A atualização real preservou medicamentos, doses, vínculos de importação, relatórios e cancelamentos. Instale como atualização, sem desinstalar.

O histórico Git contém código, testes, documentação e o ID OAuth público. A chave original fica em `signing/`, somente no ZIP pessoal. Senhas Google, tokens, banco pessoal, relatórios pessoais e importações pessoais não foram incluídos. Não há remoto GitHub nem push; o ZIP inclui o histórico Git e o patch para aplicar em um repositório existente.
