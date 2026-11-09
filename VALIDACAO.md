# Validação — Dose 1.0.1

APK nativo compilado e assinado. Identificador: `br.com.well.dose`. Versão Android do pacote: 1.0.1, código 2.

## Resultado

**75 verificações automatizadas aprovadas**, executadas nesta atualização:

| Verificação | Resultado |
|---|---|
| Regras de agendamento Java | 18 aprovadas |
| Integração Android / SQLite / notificações / estoque / backup | 25 aprovadas |
| Calendário, temas e ações do alarme de teste | 26 aprovadas |
| Alarme de medicamento com processo encerrado e tela bloqueada | 3 aprovadas |
| Teste de 15 segundos com processo encerrado e tela bloqueada | 3 aprovadas |
| Instalação sobre o APK 1.0.0 | Concluída com sucesso |
| Assinatura do APK | v2 e v3 verificadas; mesmo certificado da versão 1.0.0 |
| Alinhamento | Verificado com zipalign |
| Revisão visual | Agenda clara e escura inspecionadas |

## Correções verificadas

O nome e o número do dia selecionado foram medidos depois do layout real no Android. Os centros dos textos coincidem com o centro do destaque nos dois temas, com contraste mínimo de 4,5:1.

Os controles **Encerrar teste** e **Adiar** acionam os receptores reais da notificação. O adiamento agenda apenas outro teste; encerrar remove o aviso e o teste pendente. Medicamentos, estoque e todas as ocorrências permaneceram iguais. O aviso do serviço de som abre a tela do teste, com os dois controles.

O horário do teste e seu adiamento são gravados antes de confirmar o agendamento. A verificação também confirmou a persistência depois de encerrar o processo, evitando perder um teste programado ao fechar o aplicativo imediatamente.

O teste não contém dados de medicamentos e permite encerrar sem desbloquear. A privacidade dos lembretes de medicamentos foi mantida. Em avisos compactos, expanda a notificação ou toque nela para abrir os controles.

A aparência oferece **Seguir o celular**, **Modo claro** e **Modo escuro** em **Alertas → Aparência**. Agenda, campos de cadastro e diálogos nativos foram verificados nos dois temas. A escolha fica salva.

O alarme de medicamento e o teste de 15 segundos foram entregues após abrir o app, voltar à tela inicial, encerrar o processo em segundo plano e bloquear a tela. O Android confirmou a reprodução de áudio. As notificações foram inspecionadas antes de reiniciar a instrumentação.

## Ambiente e limites

Android 15 / API 35 em emulador isolado, com permissões de notificações e alarmes exatos concedidas. Compilado com SDK 37; compatibilidade mínima declarada API 26. Não houve teste em Samsung físico nem validação de todas as versões do Android. A reprodução foi confirmada pelo sistema, sem avaliação de alto-falante físico. Depois de instalar, confira o teste de 15 segundos no próprio celular.

## Integridade

- APK: `Dose-1.0.1.apk`
- Tamanho: 234095 bytes
- SHA-256: `412d426b02aebca615b9c393ab06ccc1d850d7a03228c098cfecd8a6d9f9ad9f`
- Certificado SHA-256: `0f4fac1c23c378c3ff9a6134a5905f01aee7dba215eb68e796fc0e363cd862d3`

Os registros estão em `verificacao/`. As capturas usam apenas o emulador de teste e não contêm dados pessoais.
