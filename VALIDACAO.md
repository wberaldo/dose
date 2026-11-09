# Validação — Dose 1.6.0

Pacote `br.com.well.dose`, versão 1.6.0, código 8. Verificações em 08/11/2026.

## Resultados

| Verificação | Aprovadas |
|---|---:|
| Regras Java de agendamento | 18 |
| Atualização real de 1.5.0, comparando as cinco tabelas integralmente | 1 |
| Arquivo lógico, preferências, restauração e backup opcional do Android | 22 |
| SQLite, estoque, notificações, ações e backup | 25 |
| Destino persistente, botão na atividade real, cópia e proteção contra substituição | 18 |
| Trabalho executado pelo JobScheduler após encerrar o processo do app | 4 |
| Acesso revogado, data de conclusão, cópia local e desconexão | 7 |
| Documento preservado após desconectar | 1 |
| **Total** | **96** |

Registros em `verificacao/1.6.0/`. Preparações de dados não entram na contagem. Assinatura v2/v3, certificado, alinhamento zipalign e integridade do APK também foram conferidos.

## Destino salvo e botão dentro do app

Foi utilizado um provedor de conteúdo controlado no APK de testes, separado do APK entregue. Ele armazena um documento JSON em arquivo real e permite concessões persistentes de URI pelo gerenciador do Android. A concessão inicial foi preparada pelo harness de testes; o Dose executou o mesmo fluxo de obtenção e conferência da permissão usado após o seletor.

A primeira gravação foi lida de volta e comparada. O teste abriu a atividade real BackupActivity e acionou seu botão Fazer backup agora. O monitor de atividades não recebeu abertura do seletor e o destino permaneceu igual. O mesmo arquivo passou a conter o estoque atualizado, com medicamentos, histórico, preferências e cancelamentos preservados.

Ao vincular uma cópia existente, seus bytes foram preservados, o diário foi cancelado e as tentativas de gravar ou ativar o diário permaneceram bloqueadas. A restauração recuperou dados e preferências sem descontar estoque novamente. A confirmação de substituição permitiu usar os dados atuais do celular. A configuração de destino e a ativação do diário não entraram no arquivo portátil.

## Automático em segundo plano

O trabalho registrado no JobScheduler é persistente, periódico de 24 horas, com conexão e bateria suficiente. Após alterar o estoque sem fazer nova gravação manual, o app foi aberto uma vez e colocado em segundo plano; seu processo foi encerrado com `am kill`, preservando a agenda do sistema. O comando `cmd jobscheduler run -f br.com.well.dose 9101` executou o serviço pelo framework Android.

O documento recebeu o estoque novo e o serviço registrou sua conclusão. A autorização persistente, a escolha do diário e os dados sobreviveram ao reinício do processo. O backup não alterou o estoque nem confirmou novas doses. O teste força a execução do trabalho real; não representa uma espera de 24 horas nem mede o comportamento de economia de bateria de um Samsung físico.

## Falhas e recuperação

Depois de revogar o acesso persistente, a gravação falhou sem atualizar a data do último backup concluído. A mensagem não expôs o endereço privado do documento. A última cópia local conferida permaneceu recuperável e restaurou estoque e histórico mesmo sem acesso ao destino.

Desconectar removeu o destino, a autorização persistente, o trabalho diário e a cópia local. O arquivo do provedor e os medicamentos do app permaneceram. A leitura e gravação no provedor acontecem fora da interface; a cópia local usa AtomicFile. A gravação remota é conferida por releitura, mas a atomicidade e a sincronização com servidores dependem do provedor.

## Atualização e ambiente

A instalação sobre o APK 1.5.0 preservou integralmente `meds`, `doses`, `import_sources`, `import_links` e `reminder_mutes`. O esquema do banco continua na versão 4. Não é necessário desinstalar ou importar medicamentos novamente.

Android 15 / API 35 em emulador isolado, com dados fictícios. Compilado com SDK 37, mínimo API 26. A interface do sistema e o launcher apresentaram travamentos na tentativa de conferir o seletor visualmente. O teste funcional prosseguiu com provedor controlado e atividade real por instrumentação; a escolha inicial completa pelo seletor não foi validada visualmente até o fim. O provedor de testes não faz parte do APK distribuído.

A imagem não tem o aplicativo Google Drive conectado. Nenhuma conta Google real foi acessada. A seleção da conta, a primeira autorização pelo Drive e o envio efetivo à nuvem precisam ser conferidos no celular. O app confirma a gravação e releitura do documento; não afirma confirmação pelos servidores do Google.

Permissões, som real, tela cheia e restrições de bateria do fabricante permanecem sujeitos a teste no aparelho. As evidências de versões anteriores continuam na entrega. Nesta versão, a integração SQLite e as ações das notificações foram repetidas.

## Integridade

- APK: `Dose-1.6.0.apk`
- Tamanho: 279374 bytes
- SHA-256: `4c034538346fae1124587034d7a09a5f30454a311f3f2b93f176c17b2fb480b3`
- Certificado SHA-256: `0f4fac1c23c378c3ff9a6134a5905f01aee7dba215eb68e796fc0e363cd862d3`

O Git inclui código, recursos, documentação e testes fictícios. Não contém dados pessoais, PDF pessoal, importação pessoal, banco ou chave de assinatura. A chave fica somente na entrega privada do projeto para manter compatibilidade das atualizações. Nenhum remoto está configurado e não foi feito push ao GitHub.
