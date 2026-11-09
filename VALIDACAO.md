# Validação — Dose 1.1.0

APK nativo compilado e assinado. Identificador `br.com.well.dose`, versão 1.1.0, código 3. Verificações realizadas em 08/11/2026.

## Resultado

| Verificação | Resultado |
|---|---|
| Regras de agendamento Java | 18 aprovadas |
| Conversor MyTherapy: datas, frequência, unidades e estados | 11 aprovadas |
| Atualização real do banco da versão 1.0.1 para 1.1.0 | 7 aprovadas |
| Integração Android / SQLite / notificações / estoque / backup | 25 aprovadas |
| Importação por adição, duplicatas e compatibilidade de backup | 20 aprovadas |
| Calendário, temas e ações do alarme de teste | 26 aprovadas |
| Arquivo convertido: cadastros, resumo mensal, términos e PDF | 7 aprovadas |
| Confirmação da importação pela tela real | 3 aprovadas |
| Consulta do resumo e renderização/paginação do PDF no Android | 3 aprovadas |
| Alarme de medicamento com processo encerrado e tela bloqueada | 3 aprovadas |
| Teste de 15 segundos com processo encerrado e tela bloqueada | 3 aprovadas |
| Instalação sobre o APK 1.0.1 | Concluída sem desinstalar |
| Assinatura v2 e v3 | Verificada; mesmo certificado da versão 1.0.1 |
| Alinhamento | Verificado com zipalign |

As linhas com contagem totalizam 126 verificações. As etapas de preparação de dados do emulador não entram nessa contagem.

## Importação

A migração foi executada instalando primeiro o APK anterior, criando medicamentos e histórico fictícios, e instalando o APK novo por cima. Cadastros, doses, horários reais de confirmação, estoque e datas de criação foram preservados. As novas tabelas começaram vazias.

Os testes de importação verificaram adição sem substituir dados existentes, detecção de duplicatas, manutenção de estoque e histórico, medicamentos semanais e sob demanda, estados inativos, rejeição de dados inválidos, reversão completa em erro de banco e preservação dos vínculos nos backups. O backup versão 2 inclui o documento original; backups versão 1 continuam aceitos.

O arquivo convertido foi lido pelo parser real e gravado no SQLite do Android. Foram comparadas as quantidades esperadas de cadastros e estados do gráfico mensal, sem criar horários reais de tomada nem descontar estoque. A regra de término usa a data de geração do relatório mais os dias restantes. O PDF incorporado teve seu SHA-256 verificado e manteve os mesmos bytes após exportar e restaurar o backup.

A prévia e a confirmação foram abertas e acionadas na tela real. Depois, o banco confirmou todos os cadastros selecionados e a ausência de alarmes para registros inativos, sob demanda ou pendentes de revisão. O resumo abriu no app; o PDF original foi renderizado com conteúdo visível e avançou à segunda página.

O conversor reconhece o formato em português desta entrega. Não é um leitor universal de PDFs: um layout ou símbolo desconhecido interrompe a conversão. A importação no aplicativo recebe o JSON preparado a partir do PDF.

## Regressões

O calendário foi medido após o layout real nos modos claro e escuro. O nome e o número do dia ficam centralizados no destaque, com contraste mínimo de 4,5:1. A escolha de aparência permanece salva e se aplica aos campos e diálogos.

Os controles **Encerrar teste** e **Adiar** acionaram os receptores reais. O adiamento agenda outro teste, e encerrar remove o aviso pendente. Medicamentos, estoque e ocorrências não foram alterados. A privacidade dos lembretes de medicamentos foi mantida. Em avisos compactos, expanda a notificação ou toque nela para abrir os controles.

O alarme de medicamento e o teste de 15 segundos foram entregues após abrir o app, voltar à tela inicial, encerrar o processo em segundo plano e bloquear a tela. O Android confirmou o início do áudio. As notificações e suas ações foram verificadas antes da instrumentação que confirma o estado persistido.

## Ambiente e limites

Android 15 / API 35 em emulador isolado, com notificações e alarmes exatos autorizados. Compilado com SDK 37; compatibilidade mínima declarada API 26. Não houve teste em Samsung físico nem em todas as versões do Android. O áudio foi confirmado pelo sistema, sem avaliação de alto-falante físico. Confira o teste de 15 segundos no próprio celular após atualizar.

A interface do sistema do emulador apresentou um bloqueio durante a automação por acessibilidade. Após recuperar essa interface, a confirmação foi repetida com toques, inspeção visual e verificação do banco, e passou. Não foi necessário alterar a aplicação para contornar o problema do emulador.

## Integridade

- APK: `Dose-1.1.0.apk`
- Tamanho: 250479 bytes
- SHA-256: `e1541671353144e747c597df3053983c16e8f05621b179a05b9d8303e7c78e6f`
- Certificado SHA-256: `0f4fac1c23c378c3ff9a6134a5905f01aee7dba215eb68e796fc0e363cd862d3`

Os registros desta execução ficam em `verificacao/1.1.0/`. O relatório pessoal, o arquivo de importação e as capturas com dados pessoais ficam fora do código e do histórico Git.
