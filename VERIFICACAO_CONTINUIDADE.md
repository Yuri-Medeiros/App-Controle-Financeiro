# Continuidade — 27/10/2026

## Estado preservado

O checkout iniciou sem commits ou arquivos de trabalho. Antes de sincronizar, foram inspecionados status e referências. A PR #1 está mesclada desde 27/10/2026 às 16:56 (America/Sao_Paulo), merge 69b5d3a. `work` estava em 98cbed1; `feat/primeiro-apk`, em da6fc93, continha os três documentos de continuidade ausentes em `work` e revisões de DECISOES.md. A continuação usa essa branch com merge de `work`; sem reset, clean, force push ou perda dos documentos.

Lidos integralmente PROMPT_CONTINUIDADE.md, PLANEJAMENTO.md, DECISOES.md e REQUISITOS_ORIGINAIS.md; consultados AGENTS.md, README.md, PIPELINE.md, signing/README.md, relatórios de verificação, VALIDACAO.md, CODEX_CLOUD.md e SINCRONIZACAO_CLOUD.md.

## Implementação desta etapa

`domain/PurchaseSplit.kt` divide uma compra única em centavos por partes iguais ou percentuais decimais cuja soma deve ser exatamente 100. Usa maiores restos, com desempate pela posição na lista: o chamador deve fornecer a ordem fixa de participantes registrada na compra. Produtos intermediários usam BigInteger para evitar overflow; resultados continuam Long em centavos. Não normaliza percentuais inválidos nem transforma valores negativos em positivos.

Não há mudança de banco, schema, interface, dependência ou integração. Não aplica essa regra automaticamente entre parcelas, a estornos ou pagamentos de familiares. Esses fluxos continuam dependendo das decisões e modelagem próprias. Versão incrementada para 0.2.0 conforme regra de minor para funcionalidade; isso não publica uma versão.

## Verificação

- 31 testes Python do pipeline passaram nesta execução.
- Novos testes JVM cobrem exemplo aprovado de R$ 100 entre três pessoas, valor menor que o número de participantes, maior resto antes de ordem, desempate, percentual zero, escalas decimais diferentes, Long.MAX_VALUE, valor zero e entradas inválidas. Uma varredura de 4.020 divisões iguais verifica conservação do total e diferença máxima de um centavo.
- CI real aprovada no commit 5ee132c9df8046f7bc310975e3225a6d30a31f42: [Verificar PR #9](https://github.com/Yuri-Medeiros/App-Controle-Financeiro/actions/runs/40584691382), job 122472932006. Os sete alvos exigidos concluíram com BUILD SUCCESSFUL: assembleRelease, testReleaseUnitTest, lintRelease, assembleCandidate, testCandidateUnitTest, lintCandidate e assembleDebugAndroidTest. Os logs confirmam execução dos testes das duas variantes; não foi obtido relatório XML individual do runner para recontar os testes. Instrumentation foi somente compilada.

## Impedimentos e limites

As consultas por `gh api` a Secrets (somente nomes), rulesets e Releases retornaram `Forbidden`; não foi possível comprovar a ativação. Nenhum Secret foi criado ou substituído. A PR #1 não contém comentários de aprovação na consulta pelo conector. Isso não comprova ausência de outros candidatos/execuções.

O usuário informou ter baixado a versão 0.1.1 e dois candidatos e, depois, confirmou instalação e desbloqueio no S23. A resposta não identifica qual dos APKs foi instalado; tags/SHA, timeout, atualização preservando dados e recuperação continuam sem confirmação. Não equivale a aprovação vinculada a tag/SHA. Maior versionCode instalado ainda não foi confirmado; a referência de distribuição não foi alterada por suposição.

A variável de senha de assinatura foi reportada pronta pelo ambiente, sem leitura do valor. Identidades, certificados e workflows foram preservados. Não houve assinatura, candidato ou publicação nesta etapa. Preparar candidato exige CI e o workflow existente; não entregar APK por caminho alternativo diante do bloqueio da API. Backup financeiro, OAuth, importadores e testes no aparelho não foram declarados concluídos.

## Avanço concorrente preservado

Ao abrir a PR #4, `work` havia avançado para 1d34662 (PR #3): remoção da frase final do README e versão 0.1.2. O merge preserva a remoção e a nova seção de continuidade; a versão da feature permanece 0.2.0, superior a 0.1.2 e conforme incremento minor. Nenhum código de produto remoto foi substituído.

A preparação local com `tools/setup-cloud.sh` concluiu com JDK 21.0.13, Gradle 8.14.3, plataforma 36 e Build Tools 36.0.0, preservando proxy e checksums. Os sete alvos Gradle exigidos foram iniciados. A tentativa de `gh workflow run distribute.yml --ref work -f mode=test -f pr=4` retornou Forbidden ao consultar o workflow; nenhuma execução foi disparada por esse comando.

A validação Gradle local, iniciada enquanto a CI estava pendente, foi interrompida ainda na preparação de dependências após o sucesso dos mesmos checks no GitHub, evitando duplicar a execução. Não é registrada como build local aprovado. Os commits posteriores atualizam apenas documentação/evidências; o código de domínio validado corresponde a 5ee132c.
