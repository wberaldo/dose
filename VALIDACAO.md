# Validação — Dose 1.3.0

APK nativo, pacote `br.com.well.dose`, versão 1.3.0, código 5. Verificações realizadas em 08/11/2026.

## Resultado

| Verificação | Resultado |
|---|---|
| Regras Java de agendamento, incluindo ausência de alarmes retroativos | 18 aprovadas |
| Atualização de 1.2.0 para 1.3.0 com dados existentes | 7 aprovadas |
| Som nos modos vibrar/silencioso, aviso único, volume, ações e doses simultâneas | 23 aprovadas |
| Integração Android / SQLite / notificações / ações / estoque / backup | 25 aprovadas |
| Importação por adição, duplicatas e compatibilidade de backup | 20 aprovadas |
| Formatos, cores, histórico visual, estoque simultâneo e backups 1/2/3 | 17 aprovadas |
| Calendário, temas claro/escuro e controles do alarme de teste | 26 aprovadas |
| Alarme real com tela bloqueada: entrega, abertura automática e áudio | 3 aprovadas |
| Toque em Adiar na tela de alarme e conferência do estoque | 2 aprovadas |
| Alarme real desbloqueado e com Não perturbe permitindo alarmes: entrega, abertura automática e áudio | 3 aprovadas |
| Toque em Tomei na tela de alarme e conferência do estoque | 2 aprovadas |
| Assinatura v2 e v3, mesmo certificado da versão 1.2.0 | Verificada |
| Alinhamento com zipalign | Verificado |
| Inspeção da tela de alarme, informações, cores e botões fixos | Concluída |

Total: **146 verificações com contagem**, sem incluir as preparações dos cenários. Os registros desta execução estão em `verificacao/1.3.0/`.

## Uma notificação por dose

O serviço de reprodução usa o identificador do próprio lembrete, sem criar o aviso auxiliar “alarme tocando”. Os testes observaram as notificações efetivamente publicadas pelo Android, suas ações e o indicador de serviço em primeiro plano. Duas doses simultâneas mantêm dois lembretes, um por dose; confirmar uma preserva o lembrete da outra. O resumo de grupo eventualmente gerado pelo Android não é uma dose adicional.

Foi corrigida também a ordem do encerramento: o lembrete pendente é destacado do serviço antes de pará-lo. Silenciar ou chegar ao fim da reprodução mantém o aviso e seus botões. Confirmar ou adiar remove somente o aviso correspondente. As ações não descontam estoque duas vezes.

## Reprodução e volume

O MediaPlayer usa `USAGE_ALARM`. A reprodução foi verificada pelas configurações ativas de áudio do Android nos modos vibrar e silencioso, sem alterar o modo de toque de chamadas. Os casos com abertura automática registraram um player do Dose em estado `started`, sem silenciamento, além de uma única notificação da dose com duas ações.

Foram conferidos o volume mínimo temporário, a restauração do nível original, a preservação de um ajuste manual durante a reprodução e a recuperação agendada sem serviço ativo. O emulador tem volume mínimo de alarmes 1 e máximo 7: a solicitação de volume zero é limitada a 1 pelo próprio Android. Os testes comparam o volume anterior efetivamente aplicado. Isso não é um teste de volume zero em um Samsung físico.

A obtenção do foco de áudio possui uma tentativa inicial e novas tentativas limitadas durante a abertura do alarme. Uma recusa não é tratada como autorização para reproduzir sem foco. O som contínuo tem limite de 60 segundos; a notificação permanece pendente após silenciar.

No cenário desbloqueado, o sistema confirmou `ZEN_MODE_ALARMS`, com reprodução ativa do Dose. Não perturbe que bloqueia todos os alarmes continua obedecendo ao Android. O app oferece os atalhos para permitir alarmes e autorizar exceções; não desativa globalmente esse modo.

## Tela cheia e ações reais

Foram programadas doses fictícias para 15 segundos depois, saindo da tela principal antes do disparo. O Android abriu automaticamente `AlarmActivity` tanto com a tela bloqueada quanto desbloqueada. As janelas, notificações e áudio foram inspecionados antes de reiniciar a instrumentação.

A tela exibe nome, dose, concentração, aparência, horário previsto e observações. Os botões Tomei e Adiar ficam fora da área rolável. No cenário bloqueado, um toque real em Adiar preservou o estoque. No desbloqueado, um toque real em Tomei confirmou a dose e descontou uma unidade. Todas as capturas incluídas usam dados fictícios.

Foram concedidas ao ambiente de teste as autorizações de notificações, alarmes exatos, tela cheia e aparecer sobre outros apps. Em celular desbloqueado, o Android pode usar somente um aviso no topo se a autorização para aparecer sobre outros apps não estiver ativa. Os atalhos correspondentes estão em Alertas.

## Atualização e preservação dos dados

A versão 1.2.0 recebeu cadastros com cores personalizadas, histórico, estoque, um relatório importado, vínculos e preferências fictícias. Depois de instalar 1.3.0 por cima, os registros completos foram comparados. O esquema do banco permanece na versão 3. A atualização não exige desinstalação ou reimportação.

Na primeira execução desta atualização, o perfil ativa som, tela cheia e detalhes visíveis na tela bloqueada, conforme a solicitação. Tema e intervalo de adiamento permanecem. Escolhas feitas depois dessa atualização não são redefinidas. A opção Privacidade na tela bloqueada continua disponível para voltar a exigir desbloqueio.

Os testes de importação, ausência de alarmes retroativos, backup e formatos/cores foram executados novamente. Backups 1, 2 e 3 continuam aceitos; relatórios importados e o histórico de doses já confirmadas permanecem preservados.

## Ambiente e limites

Android 15 / API 35 em emulador isolado, tela 720 × 1600. Compilado com SDK 37, mínimo API 26. A interface do sistema do emulador precisou ser reiniciada após um bloqueio durante a conferência inicial. A validação final da interface e dos botões foi concluída; não houve exceção fatal do Dose no registro dessa etapa.

Não houve teste em Samsung físico ou Android 17 em execução, nem avaliação de alto-falante físico, economia de bateria de fabricante ou todas as versões do Android. Depois de atualizar no aparelho, conferir as permissões em Alertas e executar Testar alarme em 15 segundos com tela bloqueada e desbloqueada. Uma compilação ou teste em emulador não comprova todos os comportamentos em um celular real.

## Integridade

- APK: `Dose-1.3.0.apk`
- Tamanho: 262767 bytes
- SHA-256: `e77da5ff89f6d0d49e2e90da04caa8b1dab181bd7bce4aab21b65dd44921916a`
- Certificado SHA-256: `0f4fac1c23c378c3ff9a6134a5905f01aee7dba215eb68e796fc0e363cd862d3`

Dados pessoais, PDF, arquivo de importação e chaves de assinatura permanecem fora do histórico Git. A pasta de assinatura é incluída somente na entrega pessoal do projeto, para permitir futuras atualizações compatíveis.
