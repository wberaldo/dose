# Validação — Dose 1.7.3

Execução em 10 de novembro de 2026, horário de São Paulo. Pacote `br.com.well.dose`, código de versão 12, banco versão 4.

## Alterações e causa do problema

A atualização periódica de 30 segundos reconstruía toda a janela e criava outra área de rolagem, perdendo a posição. A mesma reconstrução ocorria ao receber alterações dos dados ou retomar a atividade. Agora a atualização da aba reutiliza o ScrollView, restaura a posição antes do desenho e guarda posições independentes por aba. A data, o filtro e as posições são preservados na recriação da atividade. Mudanças de tema recriam a estrutura visual preservando a posição.

Foram acrescentados Suave, Gotas, Sinos, Pulso, Digital e Clássico, com prévia e confirmação. A opção original preserva o canal de som já configurado no Android. Cada opção nova possui um canal estável, reutilizado ao voltar a ela, e o serviço utiliza o canal da própria notificação. A escolha é incluída no backup e validada antes de restaurar dados.

## Verificações aprovadas nesta versão

| Verificação | Quantidade |
|---|---:|
| JUnit de API Drive e configuração | 25 |
| Regras Java de agendamento | 18 |
| Conversor MyTherapy | 11 |
| Validação PCM dos sete arquivos de som | 1 |
| Novos cenários nativos de toque e rolagem | 37 |
| Núcleo nativo de medicamentos, doses e notificações | 25 |
| Backup e restauração nativos | 22 |
| Regressão de temas, calendário e controles do teste | 26 |
| Alarmes, áudio, volume, estoque e cancelamento | 26 |
| **Total** | **191** |

A suíte nova esperou a atualização real de 30 segundos e confirmou que o conteúdo mudou sem substituir o ScrollView ou zerar sua posição. Conferiu também atualizações por broadcast, posições independentes das abas, retorno do cadastro na mesma tarefa da agenda e recriação da atividade.

Os sete arquivos foram abertos e iniciados pelo MediaPlayer no Android. Foram exercitados os botões reais Cancelar e Usar toque, preservação da escolha, utilização do novo som pelo serviço com uma única notificação, devolução do volume anterior, backup e restauração da preferência, recusa de opção inválida e compatibilidade de backups anteriores. A restauração de tema escuro também foi conferida ao retomar a agenda.

Na preparação da automação, a identificação dos botões do diálogo passou a usar os IDs nativos do Android. Os cenários de cadastro passaram a abrir a atividade pela própria agenda, sem criar outra tarefa; o AVD é mantido acordado. A execução final das cinco suítes nativas passou integralmente.

Também passaram compilação de release, lint vital, assinatura original, alinhamento de 16 KB, conferência de versão e configuração Google e verificação de classes DEX duplicadas nos APKs de distribuição e teste. O recurso de ícone do APK continua sendo a cápsula sem estrela.

## Ambiente e limites

AOSP Android 15/API 35 x86_64, emulador isolado sem Google Play Services e sem saída física de áudio. Os dados são fictícios. Iniciar o MediaPlayer e validar os arquivos não mede audibilidade, volume físico ou comportamento específico da One UI no Samsung. Não foi feita uma nova conexão ou operação com conta Google real nesta execução.

A limitação já documentada para áudio em segundo plano com tela cheia desativada permanece fora desta alteração; consulte [VALIDACAO.md](VALIDACAO.md). Esta rodada exercitou a reprodução com a atividade em primeiro plano e as suítes nativas indicadas, sem declarar novos testes de todos os cenários em segundo plano.

## Reproduzir

Requer JDK 17 ou mais recente com compilador e SDK Android 37.0/build-tools 37.0.0:

```bash
python3 tools/build.py --sdk /caminho/android-sdk --test
python3 -m unittest discover -s tools -p 'test_*.py' -v
python3 tools/build_device_tests.py /caminho/android-sdk
python3 tools/run_sound_scroll_tests.py --sdk /caminho/android-sdk --avd avd_isolado
```

O teste nativo substitui os dados por exemplos e deve ser executado somente em AVD dedicado. O script registra os resultados em `build/validation-1.7.3/`. A compilação requer a chave pessoal original em `signing/`; ela não é publicada no GitHub.

## Integridade

| Item | Valor |
|---|---|
| Arquivo | `Dose-1.7.3.apk` |
| Tamanho | 3.563.201 bytes |
| SHA-256 | `07b374fdc22051ce5f4bbc9c764f0362f5cad9d5b4bfeed6ec92b1706ee66fa9` |
| SHA-1 do certificado | `83:53:26:A0:B3:1A:E6:15:25:0A:77:95:4C:CA:92:45:C5:5F:62:65` |

Instale como atualização, sem desinstalar o aplicativo atual. O pacote, a assinatura, o banco e o cliente OAuth original foram preservados.
