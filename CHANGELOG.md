# Alterações

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
