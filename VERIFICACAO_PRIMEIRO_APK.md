# Verificação da base Android — 27/10/2026

## Resultado

Release compilado no Codex Cloud. Ainda **sem assinatura e não instalável**. Nenhuma chave definitiva foi criada nem salva no Drive. O fluxo de assinatura preparado interrompe quando falta a variável pessoal, o keystore ou o certificado público esperado. A variável de teste validada pelo usuário em outra execução não é uma senha de assinatura.

Escopo implementado: desbloqueio por biometria forte ou credencial Android; chave de banco aleatória protegida por operação criptográfica autenticada no Keystore; Room/SQLCipher; registro de inicialização persistente; bloqueio após 60 segundos em segundo plano; navegação e tema aprovados, com áreas financeiras vazias. Nenhum arquivo de instituição é necessário para esta base.

## Checks executados

Comando:

```bash
bash tools/gradle-cloud.sh :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease :app:assembleDebugAndroidTest
```

- Compilação Kotlin/Java e empacotamento de release: passaram.
- 13 testes JVM: passaram, sem falhas ou testes ignorados.
- Lint: zero erros; três avisos não bloqueantes nesta revisão — SQLCipher 4.20.0 disponível (versão fixa 4.19.1 mantida), qualificador v26 redundante para mínimo 35 e ausência de camada monocromática opcional para ícones temáticos. O ícone adaptativo aprovado está incluído.
- APK de teste Android: compilado; não executado.
- Manifesto empacotado: pacote `br.com.yurimedeiros.meufinanceiro`, versão 0.1.0 (código 1), mínimo 35, alvo 36, backup Android e HTTP em texto claro desabilitados. Nenhuma permissão INTERNET nesta base.
- `zipalign -c -P 16 4`: passou. Segmentos LOAD de libsqlcipher.so alinhados em 0x4000 (16 KiB) nas quatro arquiteturas empacotadas.
- `git diff --check` e sintaxe dos scripts: passaram.
- `tools/sign-release.sh` sem senha pessoal: interrompeu com código 2, sem produzir APK assinado ou gerar chave alternativa.

Testes JVM: cinco para limite de sessão/relógio; três para envelope versionado e truncamento; cinco com AES-GCM real para cifragem, adulteração, cancelamento, substituição de cipher e falha ao salvar o envelope. O uso efetivo de Keystore e BiometricPrompt não é simulado por esses testes.

Snapshot do release com ícone aprovado: SHA-256 `53d9ee4e09e91d83cd34e25d65837f394701c42db925702406e05c6cddc64a65`. Qualquer alteração posterior, incluindo ícone ou manifesto, produzirá outro hash e exigirá nova compilação.

## Estado da entrega e validações pendentes

1. Concluído: cofrinho aprovado explicitamente pelo usuário e incorporado ao ícone adaptativo. Proposta MF rejeitada e não utilizada.
2. Configurar a senha pessoal real na execução de assinatura e disponibilizar o keystore protegido. O conector Drive já respondeu e passou por envio/recuperação de arquivo temporário sem dados sensíveis, conferido byte a byte e removido ao final. Ainda falta criar e testar a recuperação da chave real, sem substituir o requisito por arquivo apenas no workspace.
3. Criar a identidade definitiva, registrar seu certificado público e conferir assinatura. Nenhum APK desta versão pode ser distribuído como instalação de uso real antes disso.
4. Instalar no Galaxy S23/Android 16 e testar biometria, PIN/senha, cancelamento, ausência de credencial, retorno em 59/60 segundos, recriação de Activity, morte do processo e alteração do bloqueio do aparelho. Compilar teste Android não comprova executá-lo.
5. Executar a Instrumentation de armazenamento cifrado: senha correta preserva registro; senha incorreta é rejeitada; cabeçalho não é SQLite em texto claro; SQLCipher ativo. O ambiente atual não dispõe de Android em execução e tem a limitação de ADB registrada em CODEX_CLOUD.md.
6. Testar atualização sobre instalação anterior com a mesma assinatura, preservando registro e chave do banco.
7. Antes de dados financeiros reais, implementar e validar backup/restauração em instalação limpa, conforme etapas já aprovadas.

Não foram implementados Gmail/Drive no app, importadores, lançamentos, valores de familiares, fórmulas financeiras, FGTS ou investimentos. Essas funções continuam com as decisões e validações próprias; a primeira versão não apresenta resultados fictícios para elas.
