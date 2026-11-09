# GitHub Pages e cadastro OAuth do Dose

As páginas deste projeto são documentos estáticos em português, preparados a partir do funcionamento do Dose 1.7.1. Não hospedam o aplicativo nem recebem seus dados de medicamentos.

Publicado em 9 de novembro de 2026: [repositório wberaldo/dose](https://github.com/wberaldo/dose), branch `main`, pasta `/docs`. A apresentação, a política e os termos foram abertos e conferidos no site público.

## Publicar o site

1. Envie o histórico público do projeto para um repositório sob seu controle no GitHub.
2. No repositório, abra **Settings → Pages**.
3. Em **Build and deployment → Source**, selecione **Deploy from a branch**.
4. Selecione a branch **main** e a pasta **/docs**. Salve.
5. Aguarde a publicação e utilize o endereço HTTPS efetivamente exibido pelo GitHub. Não preencha o Google Cloud com um endereço que ainda não foi publicado.

O Pages é disponibilizado pelo GitHub Free em repositórios públicos. Esta opção publica apenas `docs/`. A pasta contém `.nojekyll`, HTML, CSS e um ícone SVG, sem build adicional ou workflow personalizado. O repositório público mantém apenas código e documentação; a chave de assinatura e os dados pessoais não entram no Git.

## Preencher o Branding no Google Cloud

Use o mesmo projeto do cliente Android configurado no app. O endereço base é o endereço HTTPS real informado pelo Pages, incluindo o caminho do repositório quando houver.

| Campo no Google Cloud | Valor |
|---|---|
| App name | Dose |
| User support email | welberaldo@gmail.com |
| App logo | Pode permanecer vazio |
| Application home page | https://wberaldo.github.io/dose/ |
| Application privacy policy link | https://wberaldo.github.io/dose/privacidade.html |
| Application terms of service link | https://wberaldo.github.io/dose/termos.html |
| Authorized domains | `wberaldo.github.io`, sem protocolo ou caminho, conforme a validação do painel |
| Developer contact information | welberaldo@gmail.com |

Não cadastre `Dose.com.br` se esse domínio não estiver sob seu controle. O identificador do domínio autorizado deve ser aquele exigido pelo Google para o endereço efetivamente publicado. Se houver validação de domínio ou marca, siga a mensagem do painel; publicar as páginas não garante aprovação automática.

Mantenha o escopo `https://www.googleapis.com/auth/drive.file` em **Data Access**, coerente com o app. Não acrescente permissões gerais ao Drive para tentar liberar a publicação.

## Comprovar o controle do site, quando solicitado

No Google Search Console, utilize uma propriedade do tipo **Prefixo do URL**, com o endereço HTTPS exato do site. O método **Tag HTML** permite inserir no `<head>` de `docs/index.html` a meta tag `google-site-verification` fornecida pelo Google. Faça commit da tag, publique e só então solicite a verificação. Use o código fornecido à sua conta; este projeto não contém uma comprovação de domínio inventada.

A verificação por arquivo HTML também pode ser usada quando oferecida pelo Google, desde que o arquivo fique acessível exatamente na URL indicada. No GitHub Pages, uma propriedade de domínio inteiro por DNS exige controle do DNS; ela é diferente da propriedade de prefixo verificada por HTML.

## Sair do modo de teste

Depois de salvar o cadastro, volte a **Audience → Publish app**. A mudança para **In production** retira o prazo de sete dias específico do modo de teste. A autorização ainda pode expirar ou ser revogada por outros motivos; reconecte a conta no Dose depois da alteração para renovar o consentimento quando necessário.

O Google prevê exceção à verificação OAuth para uso pessoal. Isso não equivale a declarar uma marca verificada, aprovação do caso de uso pela API ou liberação automática de todos os campos do painel. A decisão e eventuais pendências são mostradas pelo Google.

Ao conectar uma conta, confirme a identidade exibida, faça uma cópia manual e confira `Dose/Dose-backup.json` no Drive. Somente depois ative o diário, se desejar. A publicação do site e dos commits não valida por si só o upload ou a restauração com uma conta real.

## Arquivos pessoais e manutenção

Mantenha `signing/`, `private/`, backups, relatórios, importações, bancos e ZIPs pessoais fora do repositório. O ID OAuth Android e a impressão digital pública do certificado não são a chave privada. Uma futura alteração do aplicativo deve manter a política de privacidade coerente com o acesso a dados e o comportamento efetivamente implementados.

## Referências oficiais

- [Publicar uma pasta de uma branch no GitHub Pages](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site)
- [Disponibilidade e hospedagem do GitHub Pages](https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages)
- [Branding OAuth](https://support.google.com/cloud/answer/15549049)
- [Público-alvo e status de publicação](https://support.google.com/cloud/answer/15549945)
- [Exceções de verificação OAuth](https://support.google.com/cloud/answer/13464323)
- [Verificar propriedade no Search Console](https://support.google.com/webmasters/answer/9008080)
