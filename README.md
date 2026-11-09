# Dose

Aplicativo Android de uso pessoal para organizar medicamentos, configurar lembretes e registrar tomadas. Interface em português, dados locais e opções de backup por arquivo ou Google Drive.

Versão do aplicativo: **1.7.2**. Pacote: `br.com.well.dose`. Ícone com uma cápsula branca e verde, sem estrela.

Repositório: [wberaldo/dose](https://github.com/wberaldo/dose). Site publicado: [wberaldo.github.io/dose](https://wberaldo.github.io/dose/).

- [Política de privacidade](https://wberaldo.github.io/dose/privacidade.html)
- [Termos de uso](https://wberaldo.github.io/dose/termos.html)

## Guia e validação

- [Instalação, recursos e compilação](LEIA-ME.md)
- [Configuração Google e autorização da conta](GOOGLE-DRIVE-CONFIGURACAO.md)
- [Publicação das páginas no GitHub Pages](PUBLICACAO-GITHUB.md)
- [Validação da versão 1.7.2](VALIDACAO-1.7.2.md)
- [Validação funcional da versão 1.7.1 e limites conhecidos](VALIDACAO.md)
- [Histórico de alterações](CHANGELOG.md)

As páginas de apresentação, privacidade e termos estão em `docs/`. O GitHub Pages publica essa pasta a partir da branch `main`. O site é estático e utiliza arquivos locais, sem instalação de dependências, servidor de aplicação, formulário ou integração com dados de medicamentos.

## Compilar

Requer JDK 17, Python 3 e o SDK Android descrito no guia:

```bash
python3 tools/build.py --sdk /caminho/android-sdk --test
```

O wrapper obtém o Gradle e as dependências oficiais. A chave de assinatura original não faz parte do repositório. Ela é necessária para gerar atualizações compatíveis com a instalação pessoal existente; guarde separadamente a pasta `signing/` da entrega pessoal.

O cliente OAuth Android e o certificado público estão em `config/google-drive.json`. Essa configuração pública não contém senha Google, token, segredo de cliente ou chave de assinatura privada. Para usar outro cadastro, siga o guia Google incluído.

## Conteúdo privado

O repositório não inclui backups pessoais, banco de dados, relatórios médicos, importações pessoais, APKs ou chaves de assinatura. Os arquivos de teste utilizam amostras fictícias. O ZIP pessoal de entrega inclui a chave original e deve permanecer fora do repositório público.

## Responsável

Wellington Beraldo · [welberaldo@gmail.com](mailto:welberaldo@gmail.com)
