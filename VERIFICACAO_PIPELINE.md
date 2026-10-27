# Verificação do pipeline — 27/10/2026

## Concluído no ambiente de implementação

- 31 testes Python dos controles de distribuição: passaram. Incluem autorização, aprovação vinculada ao commit/candidato, checks pendentes/falhos, alteração do tree no merge, commit antigo, integridade do candidato, versão, paginação, rascunho privado, falha no readback e mudança de evidência antes da publicação.
- `actionlint` 1.7.7: workflows sem erros. Sintaxe Bash e `git diff --check`: passaram.
- Gradle: `assembleRelease`, `assembleCandidate`, `testReleaseUnitTest`, `testCandidateUnitTest`, `lintRelease`, `lintCandidate` e `assembleDebugAndroidTest`, com código de validação 1001: passaram.
- Testes JVM: 13 em release e 13 em candidate; zero falhas, erros ou ignorados.
- Lint: zero erros. Release tem três avisos conhecidos (versão SQLCipher disponível, qualificador de ícone redundante, ícone monocromático opcional); candidate tem cinco, incluindo os mesmos avisos de recurso nas duas origens.
- APK principal empacotado: `br.com.yurimedeiros.meufinanceiro`, nome Meu Financeiro, versão 0.1.1, código 1001.
- APK candidato empacotado: `br.com.yurimedeiros.meufinanceiro.test`, nome Meu Financeiro Teste, versão 0.1.1-test.1001, código 1001. Activity principal resolve para a classe correta.
- Assinatura real dos dois APKs: passou em `apksigner` com esquema v3, com identidades distintas e correspondentes aos pins. A identidade definitiva anterior foi preservada.
- `zipalign -c -P 16 4` e alinhamento ELF de todas as bibliotecas nativas: passaram nos dois APKs assinados.
- Certificado propositalmente incorreto: assinatura bloqueada sem criar o APK de saída.
- APK de Instrumentation recompilado com pacote `.instrumentation`, distinto do candidato `.test`; manifesto empacotado conferido.
- Script local de assinatura: usa a versão centralizada, sem nome de arquivo fixo em 0.1.0.

Os APKs desta verificação estão em `build/pipeline-validation`; não constituem uma Release aprovada/publicada nem teste de instalação no Android.

## CI real no GitHub

A execução [Verificar PR #3](https://github.com/Yuri-Medeiros/App-Controle-Financeiro/actions/runs/40570268095), no commit `ccd63fe5e9bed93fbcdd66cb6924a0b6559f67dd`, concluiu com sucesso os testes Python e todos os alvos Gradle do workflow. A primeira execução revelou `sdkmanager` fora do PATH; o setup foi corrigido para usar os componentes já instalados e a execução passou. O ajuste posterior do pacote de Instrumentation foi recompilado e conferido localmente.

## Pendências de ativação e aceitação

A API GitHub acessada pelo terminal retornou Forbidden. O provisionamento parou na leitura da lista de Secrets, antes de enviar ou modificar valores. Portanto, Secrets, ruleset e execução real de publicação não foram configurados/comprovados nesta validação.

É necessário configurar os quatro Secrets usando o script e as identidades protegidas descritos em PIPELINE.md, integrar os workflows em `work` e executar o ensaio manual de ponta a ponta. O PR inicial permanece em rascunho; nenhuma aprovação de teste ou instalação foi simulada.

Os testes Python usam respostas simuladas da API. Permissões reais do GITHUB_TOKEN, transporte via Release em rascunho, publicação, retenção de candidatos e atualização no celular precisam ser confirmados no ensaio. Nenhuma regra de faturamento ou serviço pago foi habilitada.
