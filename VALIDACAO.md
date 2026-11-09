# Validação — Dose 1.2.0

APK nativo, pacote `br.com.well.dose`, versão 1.2.0, código 4. Verificações realizadas em 08/11/2026.

## Resultado

| Verificação | Resultado |
|---|---|
| Regras de agendamento Java, incluindo ausência de alarmes retroativos | 18 aprovadas |
| Atualização real da versão 1.1.0 para 1.2.0 com dados existentes | 5 aprovadas |
| Integração Android / SQLite / notificações / ações / estoque / backup | 25 aprovadas |
| Importação por adição, duplicatas e compatibilidade de backup | 20 aprovadas |
| Formatos, cores, histórico visual, estoque simultâneo e backups 1/2/3 | 17 aprovadas |
| Calendário, temas claro/escuro e ações do alarme de teste | 26 aprovadas |
| Seleção e salvamento pela interface real, após girar a tela | 4 aprovadas |
| Alarme de medicamento com processo encerrado e tela bloqueada | 3 aprovadas |
| Teste de 15 segundos com processo encerrado e tela bloqueada | 3 aprovadas |
| Assinatura v2 e v3, mesmo certificado da versão 1.1.0 | Verificada |
| Alinhamento com zipalign | Verificado |
| Inspeção visual dos formatos, paleta, ajuste personalizado e modo escuro | Concluída |

São 121 verificações nas linhas com contagem; preparações de dados e mudanças de tema para inspeção não entram no total.

## Atualização e preservação dos dados

A versão 1.1.0 foi instalada no emulador e recebeu medicamentos, histórico confirmado, estoque, um relatório importado e preferências fictícias. O APK 1.2.0 foi instalado por cima. Todos os campos anteriores dos cadastros e das doses foram comparados, assim como os documentos importados, seus vínculos e as preferências de modo escuro e privacidade.

A migração acrescenta formato e segunda cor. Os registros existentes começam como cápsula com sua cor anterior nas duas metades. Nenhum medicamento, horário, estoque, confirmação ou documento foi removido.

Backups novos preservam o visual de cada cadastro e dose. Backups antigos das versões 1 e 2 restauram com formato padrão e a cor anterior. Formatos inválidos e cores transparentes são rejeitados antes de substituir dados. Uma reimportação mantém as escolhas visuais do cadastro existente.

## Formatos e cores

Os 12 desenhos são vetoriais e distintos. A renderização foi comparada em bitmap: as duas cápsulas usam ambas as cores; os outros formatos usam apenas a cor principal. O histórico de doses já confirmadas mantém sua aparência anterior. Lembretes pendentes, inclusive adiados, recebem o novo visual sem perder horário, adiamento ou repetições.

Na interface real, foram abertos o seletor de formato, a paleta de 16 cores e o ajuste personalizado de tonalidade. Foram escolhidas cápsula curta, primeira metade vermelha e segunda metade branca. A tela foi recriada ao girar o aparelho antes de salvar; o banco e a agenda confirmaram essas escolhas. As telas claras e a lista no modo escuro foram inspecionadas. As capturas incluídas usam somente dados fictícios.

A notificação inclui uma imagem grande com a aparência do medicamento e mantém os botões Tomei e Adiar. A versão pública protegida não mostra esse desenho. A tela de alarme também respeita a privacidade antes de exibir o medicamento.

## Estoque durante uma edição

Foi reproduzida uma confirmação de dose enquanto o editor estava aberto. Salvar o formato e as cores, sem alterar o campo de estoque, preservou o desconto recebido nesse intervalo. A verificação também passou após girar a tela. Digitar explicitamente uma nova quantidade continua atualizando o estoque.

## Alarmes e limites

O alarme de medicamento, usando cápsula personalizada, e o teste de 15 segundos foram entregues após sair do app, encerrar seu processo em segundo plano e bloquear a tela. As notificações foram conferidas antes de reiniciar a instrumentação. O Android confirmou o início do áudio e o reagendamento da repetição.

Os horários anteriores ao cadastro ou à importação continuam sem gerar alarmes retroativos. Esse comportamento foi verificado novamente nos testes de agenda e de importação.

Ambiente: Android 15 / API 35 em emulador isolado, com notificações e alarmes exatos autorizados. Compilado com SDK 37, mínimo API 26. Não houve teste em Samsung físico, avaliação de alto-falante físico ou validação de todas as versões do Android. Confira o teste de 15 segundos no celular após atualizar.

A interface do sistema do emulador precisou ser reiniciada após um bloqueio. Depois disso, a seleção foi conferida com toques reais, capturas e verificação do banco; não foi necessário alterar o aplicativo para contornar esse bloqueio do emulador.

## Integridade

- APK: `Dose-1.2.0.apk`
- Tamanho: 258671 bytes
- SHA-256: `b72a736d08b6df4f11006ebc70ed8b57b24ce82a2ee6b89a5794ea73c89aa093`
- Certificado SHA-256: `0f4fac1c23c378c3ff9a6134a5905f01aee7dba215eb68e796fc0e363cd862d3`

Os registros desta execução ficam em `verificacao/1.2.0/`. Dados pessoais, PDF, arquivo de importação e chaves de assinatura permanecem fora do histórico Git.
