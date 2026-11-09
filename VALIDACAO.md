# Validação — Dose 1.4.0

Pacote `br.com.well.dose`, versão 1.4.0, código 6. Verificações em 08/11/2026.

## Resultados

| Verificação | Aprovadas |
|---|---:|
| Regras Java de agendamento | 18 |
| Atualização real de 1.3.0 e restauração de backup 3 | 7 |
| Tomei disponível na agenda de hoje e ausente em dias futuros | 3 |
| Cancelamento por medicamento e data, persistência e backup | 12 |
| Integração SQLite, estoque, notificações, ações e backup | 25 |
| Importação por adição e compatibilidade de backup | 20 |
| Aparência, estoque simultâneo, histórico visual e backups antigos | 17 |
| Notificação única, áudio, volume e cancelamento com dois alarmes ativos | 26 |
| Calendário, temas e controles do alarme de teste | 26 |
| Cancelamento pela interface com toques reais | 3 |
| Tomei e confirmação pela interface com toques reais | 3 |
| **Total** | **160** |

Preparações de dados não entram na contagem. Registros em `verificacao/1.4.0/`. Assinatura v2/v3 e alinhamento zipalign verificados; o certificado permanece o da versão anterior.

## Tomada dentro do aplicativo

A agenda oferece Tomei nas doses pendentes de hoje, inclusive mais de cinco minutos antes do horário, sem depender de notificação. Antes do horário, a confirmação identifica medicamento, dose e horário previsto. A interface mostra Tomado e o horário real após confirmar, atualiza progresso e histórico e desconta estoque uma vez. Dias futuros não oferecem registrar tomada antecipadamente.

Na conferência visual, um medicamento fictício teve os avisos do dia cancelados; Tomei continuou disponível. Foram tocados Tomei e Já tomei no diálogo real. O banco confirmou o horário efetivo anterior ao previsto, estoque de 29 após iniciar com 30, ausência de reagendamento da dose e presença no histórico. A tela passou a mostrar 1 de 1 doses tomadas e 100%.

## Cancelamento limitado ao dia

Cancelar lembretes do dia afeta todos os horários de um único medicamento na data selecionada. O diálogo informa nome e data e explica esse alcance. Os demais medicamentos e os próximos dias mantêm seus agendamentos.

O cancelamento não registra tomada ou dose pulada e não desconta estoque. Persiste ao reabrir o banco, recalcular fuso, editar aparência ou acrescentar horários no mesmo dia. Um adiamento antigo não reativa o dia cancelado. As doses continuam disponíveis para registro manual; avisos cancelados não aparecem como próximo lembrete.

Os testes também reproduziram dois medicamentos com alarmes ativos. Cancelar o primeiro preservou notificação, serviço e áudio do segundo. Cancelar o último encerrou áudio e aviso. A transferência do serviço remove o aviso cancelado e mantém apenas os lembretes válidos.

## Dados e backup

A migração do banco de 3 para 4 acrescenta somente a tabela de cancelamentos, indexada por medicamento e data. Na atualização real, foram comparados integralmente medicamentos, doses, importações e vínculos, com estoque, histórico e cores. Preferências escolhidas na versão 1.3.0 permaneceram.

O backup versão 4 inclui os dias cancelados e aceita os formatos 1, 2 e 3. Referências inválidas de cancelamento são rejeitadas antes de substituir dados. Importação de medicamentos, ausência de alertas retroativos e aparência foram verificadas novamente. Não é necessário desinstalar ou reimportar ao atualizar.

## Ambiente e limites

Android 15 / API 35 em emulador isolado, tela 720 × 1600, com dados fictícios. Compilado com SDK 37, mínimo API 26. A tela inicial e a interface do sistema do emulador travaram durante a etapa visual; foram recuperadas antes dos toques e capturas finais. O registro final de AndroidRuntime não contém exceção fatal do Dose.

O áudio nativo foi verificado nos modos vibrar e silencioso, assim como manutenção do aviso após silenciar e restauração do volume. Esta execução não substitui teste em Samsung físico ou Android 17, alto-falante real ou restrições de bateria do fabricante. A abertura automática em tela bloqueada tem a evidência anterior em `verificacao/1.3.0/`; nesta versão foram verificados os controles, o filtro dos dias cancelados e a abertura da atividade pelo aviso. O Não perturbe continua sujeito às permissões de alarmes do Android.

## Integridade

- APK: `Dose-1.4.0.apk`
- Tamanho: 262767 bytes
- SHA-256: `8c3f410d7bcdd79f85d6f9b467b14871d39c0e227bb25069064573751c8953b4`
- Certificado SHA-256: `0f4fac1c23c378c3ff9a6134a5905f01aee7dba215eb68e796fc0e363cd862d3`

O histórico Git não inclui dados pessoais, PDF, importação pessoal ou chave de assinatura. A chave permanece somente na entrega privada do projeto para assinar futuras atualizações.
