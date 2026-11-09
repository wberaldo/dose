# Validação — Dose 1.5.0

Pacote `br.com.well.dose`, versão 1.5.0, código 7. Verificações em 08/11/2026.

## Verificações da recompilação entregue

| Verificação | Aprovadas |
|---|---:|
| Regras Java de agendamento | 18 |
| Conversor de relatórios com exemplos fictícios | 11 |
| Atualização real de 1.4.0, comparando integralmente as cinco tabelas | 1 |
| Backup lógico, preferências, proteção, arquivos e restauração | 22 |
| Integração SQLite, estoque, notificações, ações e backup | 25 |
| **Total** | **77** |

Preparações de dados não entram na contagem. Registros desta recompilação em `verificacao/1.5.0/`. Assinatura v2/v3, certificado, alinhamento zipalign, versão do manifesto e integridade do arquivo APK também foram conferidos.

O ambiente temporário foi reiniciado antes da disponibilização da primeira compilação 1.5.0. O projeto foi recuperado do ZIP e do histórico Git da entrega 1.4.0, as alterações 1.5.0 foram reaplicadas e o APK foi recompilado com a mesma chave. O arquivo entregue tem os testes acima executados novamente.

A execução anterior da 1.5.0 havia concluído 117 verificações, incluindo restauração pelo transporte local do Android após apagar os dados. Seus relatórios locais não sobreviveram ao reinício do ambiente. Na recompilação, o emulador voltou a apresentar travamento após os testes de backup e integração. A execução adicional foi interrompida; calendário, cancelamento por dia e transporte completo não foram repetidos até o fim nesta segunda execução. Os testes correspondentes permanecem no código. As evidências das versões anteriores continuam no ZIP.

## Atualização e dados

O APK 1.5.0 foi instalado sobre o 1.4.0 com dados fictícios. As tabelas `meds`, `doses`, `import_sources`, `import_links` e `reminder_mutes` foram comparadas integralmente antes e depois da instalação, sem diferenças. O esquema do banco permanece na versão 4. Não é necessário desinstalar ou importar medicamentos novamente.

O arquivo de backup continua na versão 4, agora com preferências opcionais. Backups anteriores continuam aceitos. A restauração preserva estoque e histórico sem descontar doses novamente, recupera dias cancelados, aparência e preferências dos lembretes. Estados temporários de testes e áudio são excluídos; horários anteriores à restauração não geram avisos retroativos. Preferências inválidas são rejeitadas antes de substituir o banco e arquivos acima de 20 MB não são aceitos.

A gravação e a leitura manual foram exercitadas com arquivo local em segundo plano e a atividade real. As intenções de criação e abertura do seletor Android foram verificadas. O provedor Google Drive não está instalado na imagem do emulador; seleção de conta/pasta e sincronização por esse provedor precisam ser conferidas no celular.

## Backup da conta do Android

O automático começa desativado. O agente exige a escolha do usuário e um transporte que informe criptografia no cliente, ou transferência direta entre aparelhos. As regras da nuvem também exigem capacidades de criptografia. Android 8 mantém o backup manual; o automático exige API 28 ou superior.

As regras incluem somente `files/cloud_backup/backup.json`. O arquivo contém uma leitura consistente das cinco coleções e uma lista explícita de preferências, com gravação atômica e limite de 20 MB. Banco bruto, preferências completas, permissões, cache e estado de reprodução não são oferecidos pelo agente.

O transporte local do Android simula capacidades de criptografia; ele não comprova envio aos servidores do Google. Nenhuma conta Google real foi conectada neste ambiente. O envio efetivo, a conta de destino e a recuperação em outro aparelho precisam ser conferidos no celular. O Android decide quando executar o automático, e a tela distingue dados preparados de confirmação de envio à nuvem.

## Ambiente e limites

Android 15 / API 35 em emulador isolado, com dados fictícios. Compilado com SDK 37, mínimo API 26. Esta execução não substitui teste em Samsung físico, Android 17, alto-falante real ou restrições de bateria do fabricante. Evidências anteriores de áudio e tela cheia estão em `verificacao/1.3.0/` e `verificacao/1.4.0/`. A integração de notificações foi repetida nesta versão. Não perturbe permanece sujeito às permissões do Android.

## Integridade

- APK: `Dose-1.5.0.apk`
- Tamanho: 271182 bytes
- SHA-256: `2293e0becfad97bedbcbe37cade3084dfe5e6ae4ea49af13241f283c99f56bcc`
- Certificado SHA-256: `0f4fac1c23c378c3ff9a6134a5905f01aee7dba215eb68e796fc0e363cd862d3`

O Git contém somente código, recursos, testes e documentação. Não inclui PDF pessoal, importação pessoal, banco de dados ou chave de assinatura. A chave fica somente na entrega privada do projeto, para manter compatibilidade das atualizações. Nenhum remoto está configurado; não foi feito push ao GitHub.
