# Dose para Android

Aplicativo nativo em português para organizar medicamentos e lembrar os horários das doses. Inspirado na proposta do CUCO, com nome, identidade visual e implementação próprios.

## Atualizar de versões anteriores

Abra o APK 1.7.1 e escolha **Atualizar**. O pacote e a chave de assinatura são os mesmos; medicamentos, histórico, estoque e preferências permanecem no aparelho. Não desinstale a versão anterior para atualizar.

## Instalar e começar

1. No celular, abra `Dose-1.7.1.apk` e siga a instalação. Se o Android solicitar, autorize a instalação de apps pela fonte usada para abrir o arquivo.
2. Abra o Dose e toque em **Cadastrar medicamento** ou **Remédios → Novo medicamento**.
3. Informe nome, concentração se desejar, quantidade por dose, unidade, horários, dias da semana e período. Use os horários da sua prescrição.
4. Na aba **Alertas**, autorize **Notificações**, **Alarmes e lembretes**, **Tela cheia na tela bloqueada** e **Tela cheia com o celular em uso**. Esta última abre a autorização do Android para aparecer sobre outros apps. Se usar Não perturbe, permita alarmes no modo ativo. Confira também a configuração de bateria.
5. Toque em **Testar alarme em 15 segundos**, bloqueie a tela e aguarde. O teste não registra uma dose nem altera o estoque. O teste abre a tela de alarme quando as permissões estão ativas e mantém apenas uma notificação, com **Encerrar teste** e **Adiar**. Nenhuma confirmação é criada no histórico.

O app inicia vazio. Não importa nem presume medicamentos, doses ou horários pessoais.

Os alarmes começam nos próximos horários após cadastrar ou importar. Um horário que já passou antes da importação não dispara um aviso retroativo; seu registro no relatório original continua disponível em Histórico.

## Backup e Google Drive

A versão 1.7.1 inclui o cliente OAuth Android informado pelo responsável pelo projeto e disponibiliza **Conectar conta Google**. Ainda não foi validada uma conta Google real nesta entrega. O ID público está em `config/google-drive.json`, com pacote e SHA-1 da assinatura original. O Google confirma o cadastro do cliente durante a autorização no celular.

O guia **GOOGLE-DRIVE-CONFIGURACAO.md** descreve o teste e as condições externas de uso da API. A configuração local não afirma que houve aprovação do Google para o caso de uso de backup. O app pede autorização da conta antes de acessar o Drive.

Após a ativação, **Alertas → Backup e Google Drive** mostra a conta conectada, **Fazer backup agora**, **Restaurar do Google Drive**, troca e desconexão de conta e um controle opcional do diário. A gravação ocorre pela API, sem abrir o seletor de Downloads. A pasta é `Dose` e o arquivo é `Dose-backup.json`. A conclusão exige confirmação de tamanho e MD5 pelo servidor. Uma cópia existente exige restaurar ou confirmar a substituição. O diário utiliza rede e bateria suficiente, com horário gerenciado pelo Android.

**Abrir opções de arquivo** mantém a exportação e restauração por arquivos já disponíveis. Esse fluxo usa o seletor Android e os locais que o aparelho disponibilizar; não solicita uma conta Google. Também preserva o destino salvo da versão 1.6, sua cópia local de recuperação e a cópia opcional da conta Android. A confirmação de gravação por um provedor de arquivos não equivale à confirmação de recebimento pelo servidor Google.

O backup inclui medicamentos, histórico, estoque, relatórios, aparência, preferências e cancelamentos por dia. Continua na versão 4 e aceita versões 1 a 4, com limite de 20 MB. A restauração não desconta estoque novamente nem dispara avisos retroativos. Autorizações Google, tokens, URIs persistentes e ativação do diário não entram no arquivo portátil.

## Registrar uma tomada na aba Hoje

Na aba **Hoje**, localize a dose na sua rotina e toque em **Tomei**. O botão aparece nas doses pendentes do dia, inclusive antes de chegar o lembrete. Se ainda faltar mais de um minuto para o horário previsto, o app mostra o medicamento, a dose e o horário e pede confirmação em **Já tomei**. **Voltar** mantém o agendamento.

Depois de confirmar, a dose aparece como **Tomado**, com o horário real, e o progresso diário é atualizado. O registro entra no Histórico, desconta o estoque uma única vez e encerra os avisos daquela dose. O horário previsto e as próximas doses continuam preservados. Não é necessário esperar uma notificação.

Doses de dias futuros não oferecem esse botão. Para uma dose que já aparece como **Sem registro**, use **Já tomei • registrar horário** e informe quando ocorreu a tomada.

## Cancelar lembretes somente em um dia

No cartão de uma dose, toque em **Cancelar lembretes do dia**. Confira o medicamento e a data e confirme em **Cancelar neste dia**. O cancelamento vale para todos os horários **desse medicamento nessa data**. Não cancela outros remédios nem as doses dos próximos dias.

Os avisos e repetições desse dia são encerrados, sem marcar a dose como tomada ou pulada e sem alterar o estoque. **Tomei** continua disponível. O cartão passa a mostrar **Sem avisos no dia**. O cancelamento fica salvo ao reabrir o app, restaurar um backup ou editar os horários do medicamento, inclusive ao acrescentar horários no mesmo dia.

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
- Uma notificação por dose. O serviço de áudio usa o mesmo identificador do lembrete; não cria um aviso extra de alarme tocando. Ao parar o som, o lembrete permanece até confirmar ou adiar.
- Som contínuo por até 60 segundos, com MediaPlayer e USAGE_ALARM, separado do modo de toque de chamadas. Funciona nos modos vibrar e silencioso quando o Android permite alarmes.
- A opção **Tocar no vibrar ou silencioso** usa um volume mínimo de 70% por padrão, ajustável em Alertas. O app eleva somente o áudio de alarmes se necessário e restaura o nível anterior ao finalizar, confirmar ou adiar. Ajustes de volume feitos durante a reprodução são preservados. Há recuperação agendada do nível anterior se o processo terminar inesperadamente. Os limites mínimo e máximo do aparelho continuam valendo.
- Som próprio incluído no APK. Se um arquivo de áudio escolhido não puder ser aberto, o som contínuo usa o áudio de reserva do app. Com a opção de tocar no vibrar ou silencioso ativa, um canal sem som usa o áudio de reserva. É possível desligar o som do Dose em Alertas.
- Repetições configuráveis: inicialmente a cada 5 minutos, com até 3 repetições após o aviso inicial. É possível desativar as repetições.
- Adiamento rápido configurável pela notificação; na agenda há opções de 5, 10, 15 ou 30 minutos.
- Tela cheia com nome, dose, concentração, desenho, cores, horário e observações; Tomei e Adiar ficam fixos enquanto os detalhes podem rolar. A tela bloqueada usa a autorização de tela cheia do Android. Para abrir também sobre um app em uso, autorize aparecer sobre outros apps. Sem essa segunda autorização, o Android pode usar somente um aviso no topo enquanto o celular está desbloqueado.
- O perfil de alarme introduzido na versão 1.3.0 ativa som, tela cheia e detalhes visíveis na tela bloqueada, conforme a solicitação. Em **Alertas → Privacidade na tela bloqueada**, é possível voltar a exigir desbloqueio para mostrar detalhes e confirmar. Alterações feitas após a atualização são mantidas.
- Reagendamento após reinício, atualização do aplicativo, alteração de relógio/fuso e concessão da permissão de alarme exato.
- As próximas ocorrências ficam registradas no banco. O agendamento não depende de uma tela aberta, conexão com a internet ou serviço permanente.
- Uma verificação de manutenção separada mantém a agenda atualizada. Ela usa um alarme aproximado e não aparece como um alarme de medicamento no relógio do sistema.
- Sem resposta, a dose passa a **Sem registro** ao chegar à próxima dose do mesmo medicamento ou após 6 horas, o que acontecer antes. Isso encerra os avisos daquela ocorrência; não afirma que você deixou de tomar. Ainda é possível registrar uma tomada passada no histórico.

Na imagem Android 15 usada nesta entrega, desativar a tela cheia fez o sistema recusar o foco de áudio do serviço iniciado em segundo plano. A notificação foi entregue, mas o som contínuo não iniciou nesse cenário. Com tela cheia habilitada, a abertura automática e o início do áudio passaram, inclusive em modo vibrar com volume de alarmes zerado. Consulte `VALIDACAO.md`; confirme o comportamento no seu celular.

Se a permissão de alarme exato estiver ausente, a central sinaliza o problema e há apenas um agendamento aproximado, sujeito a atrasos do sistema. O teste de 15 segundos exige a permissão exata.

## Aparência

Em **Alertas → Aparência**, escolha **Seguir o celular** (padrão), **Modo claro** ou **Modo escuro**. A escolha fica salva e vale para agenda, cadastro, histórico, alertas, diálogos nativos e tela de alarme. A mudança de aparência não altera a cor cadastrada de um medicamento.

## Formato e cores do medicamento

Em **Remédios → menu do medicamento → Editar**, abra **Formato e cores**. A seção também aparece ao criar um cadastro.

- Há 12 desenhos: cápsula, cápsula curta, comprimidos redondo, com sulco, cruzado, oval, alongado, triangular, quadrado e losango, além de gotas e frasco.
- Cápsulas têm **Cor 1** e **Cor 2**, uma para cada metade. Os demais desenhos usam uma cor.
- A paleta contém 16 cores. **Outra cor…** permite ajustar vermelho, verde e azul para chegar a uma tonalidade personalizada.
- A prévia mostra o resultado antes de salvar. Trocar de formato preserva a segunda cor para quando você voltar a escolher uma cápsula.
- O desenho aparece na lista de medicamentos, na agenda, no histórico e nos detalhes dos lembretes, incluindo a imagem grande da notificação. A versão pública protegida da notificação continua ocultando os detalhes.

A atualização mantém os cadastros existentes com sua cor anterior. Depois de importar, você pode personalizar cada medicamento sem importar novamente. A escolha visual não preenche nem altera a dose ou a unidade automaticamente.

Salvar uma edição com o campo de estoque inalterado mantém os descontos de doses confirmadas enquanto o cadastro estava aberto. Uma nova quantidade digitada continua sendo tratada como ajuste explícito do estoque.

Os próximos lembretes recebem a aparência atualizada. Doses já confirmadas mantêm o desenho e as cores usados na época. O backup versão 4 inclui esses dados e os cancelamentos por dia; aceita restaurações das versões 1, 2 e 3.

## Samsung / One UI

Em **Configurações → Bateria → Limites de uso em segundo plano**, coloque o Dose nos aplicativos que nunca entram em suspensão. Se disponível, em **Informações do app → Bateria**, escolha **Sem restrições**. A nomenclatura varia conforme a versão do sistema.

Confira **Alertas → Volume mínimo do alarme**. O modo vibrar ou silencioso de chamadas é mantido. Para tocar durante Não perturbe, permita alarmes no modo ativo do sistema; o app oferece o atalho e a autorização de exceção de notificações. Um modo que bloqueia todos os alarmes continua sujeito à política do Android; o app não desativa globalmente o Não perturbe.

Depois de reiniciar o celular, desbloqueie-o uma vez para liberar o banco de dados privado. Após usar **Forçar parada**, abra o Dose novamente: o Android interrompe os agendamentos de apps forçados a parar.

O teste em tela bloqueada no próprio aparelho é necessário para conferir as restrições específicas do fabricante. A compilação ou um teste em emulador, isoladamente, não comprova a entrega de todos os alarmes em um celular real.

## Organização e dados

Agenda com visão por dia, progresso diário, cadastro com até 12 horários fixos, seleção de dias, início e término, observações, cores, pausa e arquivamento. Histórico com registro do horário real de tomada. Estoque opcional com desconto após confirmação e aviso visual quando há 5 unidades ou menos.

A rotina e os lembretes funcionam localmente. O manifesto solicita internet para a integração Google opcional; os dados só são enviados após autorização da conta e comando de backup manual ou ativação do diário. Não há anúncios. Backups por arquivos ou pela conta Android continuam dependendo das respectivas opções ativadas pelo usuário. A desinstalação apaga os dados locais: use **Alertas → Backup e Google Drive → Abrir opções de arquivo → Exportar outra cópia** para guardar medicamentos e histórico em JSON. **Restaurar de outro arquivo** substitui os dados atuais após confirmação e validação.

Compatibilidade mínima: Android 8.0 (API 26). Compilado com SDK Android 17 (API 37). APK universal, sem bibliotecas nativas específicas de processador. A validação executada está documentada em `VALIDACAO.md`.

## Código e compilação

As páginas públicas de apresentação, privacidade e termos ficam em `docs/`, com instruções de hospedagem no GitHub Pages em [PUBLICACAO-GITHUB.md](PUBLICACAO-GITHUB.md). Essa publicação é separada da compilação e da assinatura do APK.

O código usa Java, APIs nativas do Android, SQLite, recursos vetoriais e Google Identity Services 22.0.0. O Gradle incorpora as dependências, recursos e manifestos do SDK Google.

Abra a pasta no Android Studio ou use o script. A compilação requer Gradle 8.13, obtido pelo wrapper incluído, e Android Gradle Plugin 8.13.2:

```bash
sdkmanager 'platforms;android-37.0' 'build-tools;37.0.0' 'platform-tools'
python3 tools/build.py --sdk /caminho/absoluto/android-sdk --test
```

Requer JDK 17 completo, Python 3 e o SDK oficial. O wrapper baixa o Gradle na primeira execução; o SDK Google e as dependências são obtidos dos repositórios oficiais. O APK assinado sai em `build/Dose-1.7.1.apk`.

`signing/` contém a chave pessoal usada nesta versão e sua configuração local. Guarde essa pasta para assinar futuras atualizações compatíveis com o APK instalado. A chave e sua senha pertencem à entrega pessoal; a pasta `signing/` não é versionada. Guarde a chave para gerar atualizações da instalação existente. A compilação usa a chave pessoal quando a pasta signing/ está presente. Sem a chave original, um novo APK não pode atualizar a instalação existente.

### Testes em um emulador isolado

O teste de integração apaga os dados do Dose no dispositivo de teste. Use um AVD dedicado.

```bash
python3 tools/build_device_tests.py /caminho/absoluto/android-sdk
python3 tools/run_emulator_tests.py --sdk /caminho/absoluto/android-sdk --avd meu_avd_de_teste
```

Os scripts conferem se há classes duplicadas nos APKs. Se essa verificação falhar após uma compilação incremental, execute `./gradlew clean` e compile novamente.

O script de emulador usa uma imagem API 35, um AVD já criado e a porta local 5038 para o servidor ADB. Os relatórios ficam em `build/validation/`. Os dados de demonstração usados para inspecionar as telas são fictícios e não fazem parte do APK distribuído.

Os testes do conversor usam exemplos fictícios e rodam com `python3 -m unittest discover -s tools -p 'test_convert_mytherapy.py' -v`.

### Histórico Git

A entrega contém `Dose-historico-git.bundle`, com os commits locais do código. Para recuperar o repositório, execute `git clone Dose-historico-git.bundle dose-codigo`. Dados pessoais, arquivos de importação, PDF e chaves de assinatura não fazem parte desse histórico. A pasta privada `signing/` está apenas no ZIP desta entrega.

O ZIP também contém `Dose-1.7.1.patch`, com as alterações públicas desta versão. Em um repositório que já contenha a versão 1.7.0, use `git am /caminho/Dose-1.7.1.patch` para aplicar o commit. O patch não inclui a chave de assinatura nem dados pessoais. Para começar com todo o histórico, prefira o bundle.

O projeto está publicado em [wberaldo/dose](https://github.com/wberaldo/dose), na branch `main`. Os dez commits do histórico local foram importados pela integração do GitHub, com o identificador original registrado na mensagem de cada commit e a integridade de cada árvore de arquivos conferida. Para novas alterações, prefira `git clone https://github.com/wberaldo/dose.git`; o bundle pessoal preserva o histórico local anterior à importação. Mantenha o arquivo pessoal de importação separado do código.

## Referências de implementação

- App de referência: https://play.google.com/store/apps/details?id=br.com.drcuco
- Backup Android: https://developer.android.com/identity/data/autobackup
- Arquivos e Drive pelo Android: https://developer.android.com/training/data-storage/shared/documents-files
- Trabalho periódico: https://developer.android.com/reference/android/app/job/JobInfo.Builder
- Teste do transporte de backup: https://developer.android.com/identity/data/testingbackup
- Alarmes e permissões: https://developer.android.com/develop/background-work/services/alarms
- Permissão de notificações: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- Tipos de serviço em primeiro plano: https://developer.android.com/develop/background-work/services/fgs/service-types
- Áudio de alarmes: https://developer.android.com/reference/android/media/MediaPlayer
- Abertura de atividades em segundo plano: https://developer.android.com/guide/components/activities/secure-bal
- Tela cheia: https://source.android.com/docs/core/permissions/fsi-limits
- Serviço associado ao lembrete: https://developer.android.com/reference/android/app/Service#STOP_FOREGROUND_DETACH
- Não perturbe e políticas: https://developer.android.com/reference/android/app/NotificationManager
- Restrições para tela cheia: https://developer.android.com/about/versions/14/behavior-changes-14

Versão 1.7.1. Entrega pessoal instalável; não publicada na Play Store.
