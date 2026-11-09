# Validação — Dose 1.7.2

Execução em 9 de novembro de 2026, horário de São Paulo. Pacote `br.com.well.dose`, código de versão 11, banco versão 4.

## Alteração

O ícone principal passa a mostrar somente uma cápsula branca e verde, centralizada no fundo verde do Dose. A estrela foi removida. A interface e os scripts de distribuição identificam a versão 1.7.2. Não foram alteradas as regras de medicamentos, alarmes, estoque ou backup.

## Verificações executadas

- Compilação de release e lint vital aprovados.
- 25 testes JUnit da integração Drive aprovados, sem erros ou testes ignorados. Esses testes utilizam transporte controlado, sem uma conta Google real.
- 18 verificações das regras de agendamento aprovadas.
- Assinatura do APK verificada e comparada ao certificado original da instalação pessoal.
- Alinhamento do APK conferido com `zipalign -c -P 16 4`.
- Pacote, código 11, versão 1.7.2 e atividade principal conferidos no manifesto compilado.
- Recurso de ícone extraído do APK e comparado aos três caminhos vetoriais do código: fundo e duas metades da cápsula, sem a estrela.
- Prévia renderizada a partir do vetor Android e inspecionada visualmente.
- Verificação de classes DEX duplicadas aprovada pelo processo de compilação.

O pacote, o certificado, a versão do banco e a configuração OAuth permanecem compatíveis com a versão 1.7.1. Instale como atualização, sem desinstalar a versão anterior.

Não foi feita uma nova instalação em emulador ou aparelho físico nesta alteração visual. Os testes funcionais em Android e as limitações conhecidas da versão anterior estão em [VALIDACAO.md](VALIDACAO.md); não foram repetidos nem contados como novos testes da 1.7.2.

## Integridade do arquivo distribuído

| Item | Valor |
|---|---|
| Arquivo | `Dose-1.7.2.apk` |
| Tamanho | 2.464.921 bytes |
| SHA-256 | `3704ac855305c71ed23da4ccea09d26c8034fbc680a39adc7b36475d6bff265e` |
| SHA-1 do certificado | `83:53:26:A0:B3:1A:E6:15:25:0A:77:95:4C:CA:92:45:C5:5F:62:65` |
