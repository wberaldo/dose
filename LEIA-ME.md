# Dose para Android

Aplicativo nativo em português para organizar medicamentos e lembrar os horários das doses. Inspirado na proposta do CUCO, com nome, identidade visual e implementação próprios.

## Atualizar de versões anteriores

Abra o APK 1.1.0 e escolha **Atualizar**. O pacote e a chave de assinatura são os mesmos; medicamentos, histórico, estoque e preferências permanecem no aparelho. Não desinstale a versão anterior para atualizar.

## Instalar e começar

1. No celular, abra `Dose-1.1.0.apk` e siga a instalação. Se o Android solicitar, autorize a instalação de apps pela fonte usada para abrir o arquivo.
2. Abra o Dose e toque em **Cadastrar medicamento** ou **Remédios → Novo medicamento**.
3. Informe nome, concentração se desejar, quantidade por dose, unidade, horários, dias da semana e período. Use os horários da sua prescrição.
4. Na aba **Alertas**, ative **Notificações** e **Alarmes e lembretes**. Confira também **Alarme na tela bloqueada** e a configuração de bateria.
5. Toque em **Testar alarme em 15 segundos**, bloqueie a tela e aguarde. O teste não registra uma dose nem altera o estoque. Para encerrar ou adiar, expanda a notificação ou toque nela. O teste oferece **Encerrar teste** e **Adiar** mesmo com a privacidade ativada; as notificações de medicamentos continuam protegendo seus detalhes.

O app inicia vazio. Não importa nem presume medicamentos, doses ou horários pessoais.

## Importar um relatório MyTherapy

1. Instale a atualização do Dose sem desinstalar a versão anterior.
2. Baixe o arquivo de importação `.json` preparado a partir do PDF.
3. Abra **Remédios → Importar medicamentos**, ou **Alertas → Seus dados → Importar medicamentos**.
4. Escolha o `.json`. Confira os cadastros selecionados e a quantidade de novos medicamentos que terão alarmes.
5. Toque em **Importar** e confirme. Cadastros já existentes são mantidos; a importação não restaura nem substitui seu banco inteiro.

O arquivo preserva nomes, quantidades, unidades, horários, dias da semana, observações, condições informadas e os estados ativo, pausado, encerrado, futuro ou sob demanda. Inícios de tratamento ausentes não são inventados. Os novos lembretes começam após importar. Quando o relatório informa apenas dias restantes, o término é calculado como **data de geração do relatório + dias restantes**, e esse cálculo aparece na prévia.

Informações ambíguas ficam pausadas para revisão. Cadastros sob demanda não geram alarmes. O histórico mensal é mantido como resumo por dia, com estados confirmados, planejados, desconhecidos, ignorados ou interrompidos. Como esse gráfico não contém horas reais das tomadas, ele fica separado das confirmações feitas no Dose e não desconta estoque.

Em **Histórico**, abra o relatório importado para ver o resumo e o PDF original. O novo backup também inclui os relatórios importados. Backups das versões anteriores continuam sendo aceitos.

O conversor pode ser executado localmente com Python e `pdfplumber`:

```bash
python3 -m pip install pdfplumber
python3 tools/convert_mytherapy.py relatorio.pdf imports/importacao.json
```

Ele reconhece o formato em português mostrado nesta entrega. Se não reconhecer uma legenda ou estrutura do gráfico, para a conversão em vez de adivinhar os estados. O limite é 1 MB para o PDF e 1,5 MB para o arquivo de importação. O arquivo contém dados de saúde e o PDF incorporado; mantenha-o fora do repositório de código.

## Notificações e alarmes

- Agendamento nativo com `AlarmManager` e alarmes exatos autorizados pelo usuário.
- Avisos com ações independentes **Tomei** e **Adiar**. Cada ação identifica a ocorrência da dose; um duplo clique não desconta o estoque novamente.
- Agrupamento no mesmo despacho quando várias doses vencem juntas; cada uma mantém seu próprio registro e sua própria notificação.
- Som de alarme contínuo por até 60 segundos e vibração, usando o mecanismo nativo de toques do Android e respeitando configurações do canal, volume e Não perturbe.
- Som próprio incluído no APK. Se um arquivo de áudio escolhido não puder ser aberto, o som contínuo usa o áudio de reserva do app. A escolha de silenciar o canal continua sendo respeitada.
- Repetições configuráveis: inicialmente a cada 5 minutos, com até 3 repetições após o aviso inicial. É possível desativar as repetições.
- Adiamento rápido configurável pela notificação; na agenda há opções de 5, 10, 15 ou 30 minutos.
- Tela de alarme na tela bloqueada quando o sistema permitir. Com privacidade ativa, os detalhes e a confirmação exigem desbloqueio.
- Reagendamento após reinício, atualização do aplicativo, alteração de relógio/fuso e concessão da permissão de alarme exato.
- As próximas ocorrências ficam registradas no banco. O agendamento não depende de uma tela aberta, conexão com a internet ou serviço permanente.
- Uma verificação de manutenção separada mantém a agenda atualizada. Ela usa um alarme aproximado e não aparece como um alarme de medicamento no relógio do sistema.
- Sem resposta, a dose passa a **Sem registro** ao chegar à próxima dose do mesmo medicamento ou após 6 horas, o que acontecer antes. Isso encerra os avisos daquela ocorrência; não afirma que você deixou de tomar. Ainda é possível registrar uma tomada passada no histórico.

Se a permissão de alarme exato estiver ausente, a central sinaliza o problema e há apenas um agendamento aproximado, sujeito a atrasos do sistema. O teste de 15 segundos exige a permissão exata.

## Aparência

Em **Alertas → Aparência**, escolha **Seguir o celular** (padrão), **Modo claro** ou **Modo escuro**. A escolha fica salva e vale para agenda, cadastro, histórico, alertas, diálogos nativos e tela de alarme. A mudança de aparência não altera a cor cadastrada de um medicamento.

## Samsung / One UI

Em **Configurações → Bateria → Limites de uso em segundo plano**, coloque o Dose nos aplicativos que nunca entram em suspensão. Se disponível, em **Informações do app → Bateria**, escolha **Sem restrições**. A nomenclatura varia conforme a versão do sistema.

Confira o volume de **Alarmes** e o canal **Alarmes de medicamentos**. Se usar Não perturbe, permita alarmes conforme sua preferência. O app não altera essas escolhas silenciosamente.

Depois de reiniciar o celular, desbloqueie-o uma vez para liberar o banco de dados privado. Após usar **Forçar parada**, abra o Dose novamente: o Android interrompe os agendamentos de apps forçados a parar.

O teste em tela bloqueada no próprio aparelho é necessário para conferir as restrições específicas do fabricante. A compilação ou um teste em emulador, isoladamente, não comprova a entrega de todos os alarmes em um celular real.

## Organização e dados

Agenda com visão por dia, progresso diário, cadastro com até 12 horários fixos, seleção de dias, início e término, observações, cores, pausa e arquivamento. Histórico com registro do horário real de tomada. Estoque opcional com desconto após confirmação e aviso visual quando há 5 unidades ou menos.

Tudo funciona localmente. O manifesto não solicita acesso à internet. Não há cadastro, anúncios nem sincronização com servidores. A desinstalação apaga os dados locais: use **Alertas → Salvar backup** para guardar medicamentos e histórico em JSON. **Restaurar backup** substitui os dados atuais após confirmação e validação do arquivo.

Compatibilidade mínima: Android 8.0 (API 26). Compilado com SDK Android 17 (API 37). APK universal, sem bibliotecas nativas específicas de processador. A validação executada está documentada em `VALIDACAO.md`.

## Código e compilação

O código usa Java e APIs nativas do Android, SQLite e recursos vetoriais. Não há dependências de execução de terceiros.

Abra a pasta no Android Studio ou use o script reproduzível. O caminho usado pelo script não depende de Gradle:

```bash
sdkmanager 'platforms;android-37.0' 'build-tools;37.0.0' 'platform-tools'
python3 tools/build.py --sdk /caminho/absoluto/android-sdk --test
```

Requer Java 17 com o compilador (`java com.sun.tools.javac.Main`) e `keytool`, Python 3 e o SDK oficial. O APK assinado sai em `build/Dose-1.1.0.apk`.

`signing/` contém a chave pessoal usada nesta versão e sua configuração local. Guarde essa pasta para assinar futuras atualizações compatíveis com o APK instalado. A chave e sua senha pertencem à entrega pessoal; a pasta `signing/` não é versionada. Guarde a chave para gerar atualizações da instalação existente. A compilação pelo Android Studio usa a configuração de assinatura escolhida no próprio Studio.

### Testes em um emulador isolado

O teste de integração apaga os dados do Dose no dispositivo de teste. Use um AVD dedicado.

```bash
python3 tools/build_device_tests.py /caminho/absoluto/android-sdk
python3 tools/run_emulator_tests.py --sdk /caminho/absoluto/android-sdk --avd meu_avd_de_teste
```

O script de emulador usa uma imagem API 35, um AVD já criado e a porta local 5038 para o servidor ADB. Os relatórios ficam em `build/validation/`. Os dados de demonstração usados para inspecionar as telas são fictícios e não fazem parte do APK distribuído.

Os testes do conversor usam exemplos fictícios e rodam com `python3 -m unittest discover -s tools -p 'test_convert_mytherapy.py' -v`.

### Histórico Git

A entrega contém `Dose-historico-git.bundle`, com os commits locais do código. Para recuperar o repositório, execute `git clone Dose-historico-git.bundle dose-codigo`. Dados pessoais, arquivos de importação, PDF e chaves de assinatura não fazem parte desse histórico. A pasta privada `signing/` está apenas no ZIP desta entrega.

Nenhum repositório remoto foi configurado e nenhum push foi realizado. Depois de definir o repositório GitHub de destino, configure o remoto nesse clone e envie os commits. Mantenha o arquivo pessoal de importação separado do código.

## Referências de implementação

- App de referência: https://play.google.com/store/apps/details?id=br.com.drcuco
- Alarmes e permissões: https://developer.android.com/develop/background-work/services/alarms
- Permissão de notificações: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- Tipos de serviço em primeiro plano: https://developer.android.com/develop/background-work/services/fgs/service-types
- Reprodução de toques e alertas: https://developer.android.com/reference/android/media/Ringtone
- Restrições para tela cheia: https://developer.android.com/about/versions/14/behavior-changes-14

Versão 1.1.0. Entrega pessoal instalável; não publicada na Play Store.
