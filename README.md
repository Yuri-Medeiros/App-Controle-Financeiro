# Meu Financeiro

Aplicativo Android pessoal, com Kotlin, Jetpack Compose e Material 3. Android mínimo 15 (API 35), alvo Android 16 (API 36). Desenvolvimento e compilação no Codex Cloud; não exige Android Studio no computador do usuário.

## Estado do desenvolvimento

- Acesso por BiometricPrompt, aceitando biometria forte ou credencial do Android.
- Chave AES do Android Keystore com autorização por operação para proteger a chave aleatória do banco. Nenhuma senha Google é solicitada.
- Room 2 com SQLCipher, sem criação de banco em texto claro ou migração destrutiva.
- Fechamento da sessão e do banco após 1 minuto em segundo plano; retorno curto preserva a sessão. Após encerramento do processo, exige nova autenticação.
- Tema escuro com paleta roxa, cartões em degradê e destaques em ciano. Movimentações está em desenvolvimento; demais áreas financeiras permanecem vazias.
- Registro local de inicialização preservado entre aberturas para validar persistência; nenhum extrato, pessoa ou saldo fictício é criado.

Nesta branch, Movimentações conecta Gmail à revisão e persistência cifrada dos CSVs Nubank/Flash. Responsáveis, parcelas, pagamentos, estornos, planejamento, patrimônio manual e backup cifrado foram implementados na continuação da PR #9; veja [o estado detalhado](DESENVOLVIMENTO_COMPLEXIDADES.md). Cotações externas, metodologia de projeção do FGTS e importação do PDF ainda têm pendências explícitas. A implementação precisa de validação no S23; não confundir código nesta branch com APK já publicado. Não usar como único registro financeiro antes de validar recuperação e atualização no Android.

## Atualizar pelo PC ou pelo celular

O [pipeline de distribuição](PIPELINE.md) funciona sem Codex: PR → checks → candidato separado → aprovação → merge → APK definitivo no [GitHub Releases](https://github.com/Yuri-Medeiros/App-Controle-Financeiro/releases). O workflow manual tem os modos `test` (Gerar APK de teste) e `release` (Publicar versão). Requer integração inicial em `work` e configuração dos Secrets descritos no guia; não há publicação automática por commit.

A versão é centralizada em `version.properties`; a variante `candidate` usa pacote `.test`, ícone laranja com T e dados isolados. O identificador e a assinatura do aplicativo principal são preservados.

## Compilar na nuvem

Na pasta deste repositório:

```bash
bash tools/setup-cloud.sh
bash tools/gradle-cloud.sh :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease :app:assembleDebugAndroidTest
```

O script de Gradle usa o proxy e a confiança TLS do ambiente sem gravar credenciais no projeto. É necessária permissão de rede da ferramenta para downloads e para o daemon Gradle. O wrapper também está versionado e fixa Gradle 8.14.3 com SHA-256.

Saída de release: `app/build/outputs/apk/release/app-release-unsigned.apk`. **Esse arquivo não é instalável sem assinatura.** O projeto não escolhe uma chave debug como substituição da identidade definitiva.

Para preparar o ambiente publicado, associar `bash tools/setup-cloud.sh` ao script de instalação, executado na pasta do repositório. O vínculo com a configuração do painel ainda precisa ser feito e validado em uma nova execução; arquivos versionados não alteram automaticamente a configuração publicada.

## Assinatura

O script [tools/sign-release.sh](tools/sign-release.sh) só executa quando encontra a senha pessoal, o keystore e a impressão digital pública da identidade aprovada. Ver [signing/README.md](signing/README.md). A identidade definitiva foi criada em PKCS12 protegido e salva no Google Drive. A cópia recuperada foi comparada byte a byte e aberta com a senha pessoal; seu certificado corresponde ao SHA-256 público fixado no repositório. Preserve essa identidade em todas as atualizações.

## Verificação

Testes locais exercitam o limite exato de sessão, retorno breve, relógio monotônico regressivo, formato do envelope, autenticação AES-GCM, adulteração, cancelamento, substituição do cipher e falha de persistência.

O teste Android usa Instrumentation do sistema e banco separado para conferir Room/SQLCipher ativo, reabertura com preservação do registro, cabeçalho cifrado e rejeição de chave incorreta. Seu APK é compilado na nuvem, mas sua execução depende de Android disponível. Biometria, PIN, lifecycle, instalação/atualização e restauração não são comprovados por testes JVM.

## Segurança operacional

O arquivo com a chave cifrada fica em `noBackupFilesDir`. Backups automáticos do Android e transferência entre aparelhos pelo sistema estão desabilitados para evitar copiar banco sem a chave local não transferível. O backup criptografado e recuperação no Drive estão implementados, mas dependem de configuração OAuth/Drive e validação real em instalação limpa. A senha de recuperação e o token não são persistidos entre sessões bloqueadas. Não há botão que apague o banco ou regenere sua chave diante de falha de abertura.

Capturas de tela são permitidas por solicitação do usuário; a prévia na lista de aplicativos recentes também pode exibir o conteúdo da tela. A permissão de internet permite consultar o Gmail mediante autorização explícita de leitura. O token fica em memória e os anexos são processados no aparelho.

As licenças de SQLCipher são incluídas nos assets do APK. Dependências: AndroidX/Compose/Room sob Apache 2.0, SQLCipher Community sob licença BSD e Google Play services Auth para autorização Google. Não há configuração de faturamento ou serviço pago.

## Continuidade financeira

A camada de domínio já calcula a divisão de uma compra única por partes iguais ou percentuais decimais exatos, preservando centavos pela regra aprovada de maiores restos e ordem fixa. Ainda não está conectada às telas ou ao banco; não representa cadastro de compras nem divisão de diferenças entre parcelas. Evidências e impedimentos em [VERIFICACAO_CONTINUIDADE.md](VERIFICACAO_CONTINUIDADE.md).

## Desenvolvimento de Movimentações

O fluxo Gmail/importação inclui busca paginada de anexos, validação dos CSVs, confirmação de referência de fatura, revisão individual/em lote, sugestões de categoria, persistência no Room/SQLCipher com migração aditiva e conciliação única de pagamentos de fatura. Estornos, resolução manual de conflitos e fluxos completos de pessoas/parcelas seguem pendentes. Regras aprovadas em [ESPECIFICACAO_IMPORTACAO.md](ESPECIFICACAO_IMPORTACAO.md), configuração OAuth em [CONFIGURACAO_GOOGLE.md](CONFIGURACAO_GOOGLE.md), etapas e evidências em [DESENVOLVIMENTO_MOVIMENTACOES.md](DESENVOLVIMENTO_MOVIMENTACOES.md). O APK anterior não contém automaticamente estas mudanças; candidato e teste no aparelho continuam necessários.
