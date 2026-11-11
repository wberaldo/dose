# 1.7.3 — 10/11/2026

- Adiciona Suave, Gotas, Sinos, Pulso, Digital e Clássico, além do som original, com prévia e confirmação em Alertas.
- Interrompe a prévia ao cancelar, sair do app ou perder o foco de áudio, sem modificar o volume do aparelho.
- Mantém os avisos e o som contínuo associados ao toque escolhido, respeitando as configurações do Android e reutilizando um conjunto limitado de canais.
- Inclui a escolha do toque no backup e mantém a leitura de arquivos anteriores.
- Corrige o salto para o topo provocado pela reconstrução da tela a cada 30 segundos e nos avisos de atualização dos dados.
- Mantém a posição de cada aba e restaura a rolagem ao voltar de outro cadastro ou recriar a atividade.
- Preserva pacote, assinatura, configuração Google e banco versão 4.

# 1.7.2 — 09/11/2026

- Remove a estrela do ícone do aplicativo e centraliza uma cápsula branca e verde no fundo verde do Dose.
- Atualiza a identificação da versão e os caminhos de compilação para gerar o APK 1.7.2.
- Mantém o pacote, a assinatura original e o banco versão 4, sem mudanças nas regras de medicamentos, lembretes ou backup.
- A validação desta alteração visual está em `VALIDACAO-1.7.2.md`; os testes e limites funcionais da 1.7.1 permanecem em `VALIDACAO.md`.

# 1.7.1 — 08/11/2026

- Configura o cliente OAuth Android informado para oferecer a escolha da conta Google.
- A configuração técnica verifica cliente, pacote e assinatura. Remove a opção local que afirmava uma aprovação externa do Google: o cadastro OAuth e as condições de uso da API continuam conceitos separados, sem simular consentimento ou aprovação.
- Mantém o consentimento explícito da conta, backup manual, diário opcional e proteção de cópia existente.
- Confere classes DEX duplicadas antes de aceitar os APKs de distribuição e de teste.
- Corrige a amostra de teste de alarmes antecipados, mantendo a data da ocorrência coerente com o horário, inclusive após as 20h.
- Registra a limitação de áudio observada no Android 15 com tela cheia desativada; o perfil padrão em tela cheia passou com o aparelho bloqueado e em modo vibrar.
- Preserva a assinatura original e o banco versão 4. Conexão e envio com conta real ainda precisam de teste no aparelho.

# 1.7.0 — 08/11/2026

- Implementa autorização oficial com conta Google, identificação da conta, upload REST direto para o Drive e restauração com verificação de tamanho e MD5.
- Acrescenta diário via JobScheduler, seleção/troca de conta, revogação, proteção de cópia existente e pausa quando há necessidade de reconectar.
- Mantém o seletor de arquivos em uma tela própria, sem apresentá-lo como login Google.
- A integração Google permanece desabilitada no APK entregue até o cadastro OAuth e atendimento das condições externas do Google. Nenhuma conta real foi conectada ou validada nesta entrega.
- Migra a compilação para Gradle com o SDK oficial Google Identity Services 22.0.0. Preserva o pacote, a assinatura e o banco versão 4.

# Alterações

## 1.6.0

- Guarda o destino escolhido no Drive uma única vez, com acesso persistente ao arquivo.
- Adiciona Fazer backup agora, sem reabrir o seletor após configurar.
- Oferece backup diário pelo JobScheduler, com conexão, bateria suficiente e reagendamento após reinício.
- Permite restaurar diretamente do destino salvo.
- Protege backups existentes contra substituição até restaurar ou confirmar o uso dos dados do celular.
- Confere o arquivo após gravar, registra a última conclusão e mantém uma cópia local de recuperação.
- Mostra falhas sem afirmar que os servidores do Drive confirmaram a sincronização.
- Mantém exportação avulsa e a cópia opcional do Android.

## 1.5.0

- Adiciona a central Backup e Google Drive em Alertas.
- Integra o backup da conta do Android, desativado até o usuário ativar no Dose.
- Permite exportar e restaurar arquivos pelo Drive ou armazenamento escolhido no seletor Android.
- Executa leitura e gravação dos arquivos fora da interface.
- Inclui preferências de aparência e lembretes no arquivo de backup, com compatibilidade com arquivos anteriores.
- Exclui estados temporários de áudio e testes e retoma lembretes somente nos próximos horários após restaurar.
- Oferece apenas o arquivo lógico ao transporte de backup; a nuvem exige proteção por criptografia do Android.

## 1.4.0

- Cancela os lembretes de um medicamento apenas na data selecionada, preservando os demais remédios e dias.
- Mantém Tomei disponível ao cancelar avisos, sem registrar dose pulada ou alterar estoque.
- Persiste os dias cancelados em backup versão 4, com restauração de backups 1, 2 e 3.

- Disponibiliza Tomei nas doses pendentes de hoje, mesmo antes do lembrete.
- Exibe nome, dose e horário ao confirmar uma tomada antecipada.
- Atualiza o estado Tomado, horário real, progresso, histórico, estoque e lembretes pela agenda.
- Mantém as doses de dias futuros sem ações de tomada ou adiamento.

## 1.3.0

- Une o aviso do serviço de áudio à notificação da dose, sem aviso auxiliar.
- Mantém o lembrete e suas ações após encerrar o som.
- Abre tela de alarme com dados e botões Tomei e Adiar fixos, inclusive sobre outros apps com autorização do Android.
- Acrescenta atalhos para permissões de tela cheia, aparecer sobre outros apps e alarmes no Não perturbe.
- Usa MediaPlayer com áudio de alarmes para tocar no modo vibrar ou silencioso.
- Oferece volume mínimo temporário, restauração do volume anterior e recuperação após encerramento inesperado.
- Na primeira atualização, ativa som, tela cheia e detalhes na tela bloqueada; escolhas posteriores do usuário são mantidas.
- Preserva cadastros, cores, estoque, histórico, relatórios e backups.

## 1.2.0

- Adiciona 12 formatos de medicamento com desenhos vetoriais.
- Permite duas cores independentes em cápsulas e uma nos demais formatos.
- Oferece paleta de 16 cores e ajuste de tonalidade personalizada.
- Mostra a prévia no editor e a aparência escolhida em medicamentos, agenda, histórico e lembretes.
- Inclui imagem colorida na notificação, mantendo a versão pública sem detalhes.
- Preserva a aparência histórica de doses confirmadas e atualiza lembretes pendentes sem perder adiamentos.
- Migra cadastros existentes preservando cores, importações, horários e estoque.
- Impede que salvar um cadastro aberto restaure o estoque anterior a uma confirmação recebida durante a edição, inclusive após girar a tela.
- Inclui aparência no backup versão 3, com restauração de backups 1 e 2.

## 1.1.0

- Importação de medicamentos com prévia, seleção e revisão antes de salvar.
- Importação por adição, mantendo cadastros, estoque e histórico existentes.
- Prevenção de duplicatas em reimportações.
- Cadastros pausados, encerrados, futuros e sob demanda preservados.
- Término calculado pela data do relatório mais os dias restantes, quando informado.
- Histórico mensal importado separado das tomadas locais, sem criar horas reais ausentes na fonte.
- Consulta do resumo e do PDF original dentro do app.
- Migração do banco e compatibilidade com backups antigos.
- Conversor MyTherapy em Python, sem dados de pacientes no código.

## 1.0.1

- Centraliza o nome e o número do dia dentro do destaque do calendário.
- Adiciona Claro, Escuro e Seguir o celular em Alertas → Aparência, com preferência persistida.
- Adapta agenda, cadastro, histórico, alertas, diálogos e tela de alarme ao modo escuro.
- Adiciona Encerrar e Adiar à notificação e à tela do teste, sem registrar doses ou alterar estoque.
- Corrige a abertura do teste ao tocar no aviso do serviço de som.
- Garante que o horário do teste seja salvo antes de permitir o encerramento do processo.
- Mantém a privacidade dos medicamentos; o teste, sem dados pessoais, pode ser encerrado na tela bloqueada.
- Usa o mesmo pacote e certificado para atualizar a versão anterior.

As verificações executadas estão descritas em VALIDACAO.md.
