# Validação — Dose 1.7.0

Pacote `br.com.well.dose`, versão 1.7.0, código 9. Verificações em 08/11/2026. Banco mantido na versão 4.

## Estado da integração Google

**O código está implementado, mas a conexão real permanece desabilitada no APK entregue.** Não foi fornecido ou criado um projeto OAuth do responsável pelo app. O cadastro Android no Google Cloud e a condição de autorização para uso de backup prevista nos termos do Drive continuam externos à entrega; veja `GOOGLE-DRIVE-CONFIGURACAO.md`.

Nenhuma conta Google real foi acessada. Não foram testados seleção de conta real, consentimento do usuário, emissão/renovação de token pelo Google Play Services, upload/download nos servidores Google, revogação real ou execução do diário conectado ao Google. Os resultados abaixo não representam homologação desses fluxos.

## Verificações locais

| Verificação | Aprovadas |
|---|---:|
| Protocolo Drive, integridade, falhas, conta e configuração (JUnit, transporte controlado) | 25 |
| Regras Java de agendamento | 18 |
| Conversão de relatório com exemplos fictícios | 11 |
| Atualização real 1.6.0 → 1.7.0, comparação integral das cinco tabelas | 1 |
| SQLite, estoque, notificações reais e ações dos botões | 25 |
| Arquivo lógico, preferências, restauração e cópia opcional do Android | 22 |
| Configuração Google pendente, atividade real e proteção dos dados | 12 |
| Destino de arquivo persistente, botão real e proteção contra substituição | 18 |
| **Total** | **132** |

Preparações de dados não entram na contagem. Registros em `verificacao/1.7.0/`. Compilação Gradle de release, testes JVM, APK de instrumentação e lint vital passaram. Assinatura e alinhamento também foram verificados.

## Testes do protocolo Google

O JUnit utiliza um transporte controlado, sem conexão externa, exercitando requisições de perfil, listagem apenas de arquivos marcados do Dose, criação da pasta, upload retomável, atualização do mesmo arquivo e download. Confere o conteúdo enviado, tamanho e MD5, bem como respostas inválidas ou incompletas, cancelamento e limites.

Casos negativos abrangem checksum ou tamanho incorretos, resposta com outro arquivo, identificador que tenta alterar o caminho, token com quebra de linha, destino de upload fora do domínio permitido, erros de quota/autorização sem vazamento da mensagem remota e configuração de pacote/certificado diferente. Também verifica a comparação de identidade da conta e a detecção de uma versão remota diferente antes de substituir uma cópia.

Esses testes validam o protocolo e suas proteções com respostas controladas; não confirmam como uma conta real responderá.

## Verificações no Android

Imagem Android 15 / API 35 AOSP em emulador isolado, sem Google Play Services, conta ou aplicativo Drive. Somente dados fictícios.

A atividade BackupActivity mostra **Configuração Google pendente**, disponibiliza o guia de ativação e não apresenta uma identidade fictícia como conectada. Tentar salvar ou ativar o diário sem configuração não registra conclusão nem agenda o trabalho 9201. O manifesto inclui internet e a atividade oficial GoogleApiActivity do SDK. O certificado em execução coincide com o das versões anteriores. O transporte nativo do Android aceita o método PATCH para atualização do Drive, verificado sem iniciar conexão. A abertura da configuração e as tentativas bloqueadas preservam os dados clínicos; o arquivo portátil não contém conta ou token Google.

A regressão de SQLite/notificações confirmou uma única baixa de estoque, ações Tomei/Adiar, persistência de adiamento, pausa/retomada, agendamento e restauração em transação. Não foi feita nova homologação de som/tela cheia no Samsung físico.

O fluxo antigo de arquivos agora abre FileBackupActivity. O teste usa um provedor de documento controlado no APK de instrumentação, fora do APK entregue, com concessão persistente pelo framework. Confirma gravação, releitura, botão real sem reabrir seletor, proteção de cópia existente e restauração. Não representa uma sincronização com servidores do Drive, nem nova conferência visual do seletor no aparelho.

## Atualização

A instalação de 1.7.0 sobre 1.6.0, sem desinstalar, preservou integralmente `meds`, `doses`, `import_sources`, `import_links` e `reminder_mutes`. A amostra continha um medicamento, 31 ocorrências e um cancelamento por dia; as duas tabelas de relatórios estavam vazias. A comparação foi feita nas linhas de todas as tabelas, após encerrar o processo. Não é necessário cadastrar ou importar novamente.

## Integridade e entrega

- APK: `Dose-1.7.0.apk`
- Tamanho: 2464937 bytes
- SHA-256: `d2f7c8be1ad1843893b96f08d19ddfd3381aa4f0bc8b4c4919efa005c534b337`
- Certificado SHA-256: `0f4fac1c23c378c3ff9a6134a5905f01aee7dba215eb68e796fc0e363cd862d3`
- Certificado SHA-1: `835326a0b31ae615250a77954cca9245c55f6265`
- APK assinado com esquema v2 e alinhamento conferido com zipalign.

A compilação inclui Google Identity Services 22.0.0 com suas dependências e recursos pelo Gradle. O recurso de configuração empacotado está desativado. A chave de assinatura permanece na pasta privada `signing/`, apenas no ZIP pessoal; não entra no Git.

O código público, os testes e a documentação são commitados localmente e acompanham o bundle Git e o patch desta versão. Não há remoto configurado nem push ao GitHub. Não foram incluídos banco bruto, dados pessoais, PDF pessoal, importações pessoais, tokens ou credenciais Google.
