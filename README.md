# Meu Financeiro

Aplicativo Android pessoal, com Kotlin, Jetpack Compose e Material 3. Android mínimo 15 (API 35), alvo Android 16 (API 36). Desenvolvimento e compilação no Codex Cloud; não exige Android Studio no computador do usuário.

## Primeira entrega em construção

- Acesso por BiometricPrompt, aceitando biometria forte ou credencial do Android.
- Chave AES do Android Keystore com autorização por operação para proteger a chave aleatória do banco. Nenhuma senha Google é solicitada.
- Room 2 com SQLCipher, sem criação de banco em texto claro ou migração destrutiva.
- Fechamento da sessão e do banco após 1 minuto em segundo plano; retorno curto preserva a sessão. Após encerramento do processo, exige nova autenticação.
- Tema claro/escuro do sistema, paleta verde-petróleo e navegação aprovada. Áreas financeiras são estados vazios, não funções financeiras implementadas.
- Registro local de inicialização preservado entre aberturas para validar persistência; nenhum extrato, pessoa ou saldo fictício é criado.

Google, importação, planejamento financeiro, backup/recuperação e patrimônio serão implementados em etapas posteriores. Esta versão não deve receber dados financeiros reais antes de validar recuperação e atualização no Android.

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

O arquivo com a chave cifrada fica em `noBackupFilesDir`. Backups automáticos e transferência do Android estão desabilitados para evitar copiar banco sem a chave local não transferível. O backup criptografado no Drive aprovado ainda não está implementado. Não há botão que apague o banco ou regenere sua chave diante de falha de abertura.

Captura de tela e prévia com conteúdo na lista de aplicativos recentes são bloqueadas por `FLAG_SECURE`. Nenhuma permissão de internet é solicitada nesta base; integrações futuras terão configuração própria.

As licenças de SQLCipher são incluídas nos assets do APK. Dependências gratuitas: AndroidX/Compose/Room sob Apache 2.0 e SQLCipher Community sob licença BSD. Não há configuração de faturamento ou serviço pago.
