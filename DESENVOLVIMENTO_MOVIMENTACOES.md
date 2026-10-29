# Desenvolvimento de Movimentações

## Fontes

- ESPECIFICACAO_IMPORTACAO.md: regras aprovadas da entrevista, incluindo origem, deduplicação e revisão.
- DECISOES.md, PLANEJAMENTO.md e REQUISITOS_ORIGINAIS.md: demais decisões e requisitos obrigatórios.
- CONFIGURACAO_GOOGLE.md: configuração e limites da evidência OAuth.

## Etapa 1 — iniciada em 28/10/2026

Código de domínio implementado em importing/: leitura estrita de CSV UTF-8, conta Nubank, Flash e fatura Nubank; valores em centavos e sinais preservados; datas validadas; descrições originais preservadas; falha de qualquer linha rejeita o documento inteiro antes de gravar. Não escolhe parser PDF nem inventa documentos adicionais.

Comparação sem gravação: ID da conta com conflito por diferenças do mesmo sinal e preservação de sinais opostos; multiplicidade de linhas Flash/cartão; referência de fatura explícita após confirmação. Cartão positivo é cobrança, não entrada de conta. Reconhecimento normaliza somente caixa/espaços dos nomes; pessoa usa nome/documento informado sem atrelar banco/conta, e identificação incompleta retorna confirmação necessária.

Limite deliberado: as chaves exatas não permitem descobrir toda correção possível em fontes sem ID. Diferenças retornam para revisão, nunca sobrescrevem dados. Uma descrição parecida não é unida por heurística. O módulo de comparação não elimina linhas novas de mesmo valor legítimas.

Não há nova interface, OAuth no APK ou persistência financeira concluídos nesta etapa. Nada disso deve ser anunciado como pronto. O trabalho manual cancelado foi preservado sem incorporar suas alterações incompletas. Nenhuma migração de banco nesta etapa.

## Testes

- 31 testes Python do pipeline passaram nesta execução.
- Testes JVM adicionados para CSVs com aspas/quebras de linha, sinais, centavos, valores zero, UTF-8 inválido, overflow, documentos truncados, referência não confirmada, IDs repetidos, conflitos, estornos, parcelas e multiplicidade; reconhecimento de pessoas/estabelecimentos/direção também coberto.
- 19 testes novos passaram em execução JVM direta usando o compilador Kotlin 2.0.21 e JUnit 4.13.2 distribuídos com Gradle 8.14.3. Isso verifica a lógica sem Android; a CI usa o Kotlin 2.2.21 fixado pelo projeto e os sete alvos completos.
- Gradle local: os sete alvos obrigatórios passaram com BUILD SUCCESSFUL (assembleRelease, testReleaseUnitTest, lintRelease, assembleCandidate, testCandidateUnitTest, lintCandidate, assembleDebugAndroidTest).
- Relatórios XML locais: 39 testes em release e 39 em candidate, zero falhas/erros/ignorados. Lint: zero erros, três avisos release e cinco candidate, sem alteração dos avisos conhecidos de dependência/ícones. Instrumentation somente compilada, não executada.
- CI real: [Verificar PR #16](https://github.com/Yuri-Medeiros/App-Controle-Financeiro/actions/runs/40698997426), commit d835c1dbe52fc4ff40aecadc5eed675b23f15e44, concluída com sucesso. Os commits seguintes alteram somente documentação; não presumir aprovação de candidato a partir da CI.
- A primeira execução completa local preparou dependências e levou 12m22s. Os 19 testes de domínio também foram executados diretamente; não há teste de OAuth, interface ou dispositivo nessa contagem.
- Os testes versionados usam exemplos sintéticos anonimizados com estrutura baseada nas amostras reais. Nenhum CSV real ou informação bancária/pessoal foi enviado ao repositório.

## Próximas integrações já autorizadas

1. Persistência de origem, ocorrências, pendências e confirmação, com migração preservando base.
2. AuthorizationClient, leitura paginada de mensagens/anexos sob demanda e tratamento de revogação/cancelamento.
3. Tela de lista/revisão, sugestões com última confirmação e descrição opcional, confirmação em lote e histórico.
4. Tratamento aprovado de pagamentos/estornos, respeitando campos e decisões ainda pendentes de parcelas/pessoas.
5. Checks completos, candidato pelo pipeline existente e teste real no S23. Publicação definitiva exige aprovação do candidato e SHA.

A etapa 1 não é uma entrega instalável de Movimentações. PDF de investimentos e backup financeiro/restauração permanecem pendentes.

## Disponibilidade das amostras originais

Tentativa de executar os importadores com os cinco CSVs previamente enviados não pôde iniciar: os caminhos temporários /tmp/codex-remote-attachments não existem mais nesta execução, e a busca em /workspace e /tmp não localizou os arquivos. Não foram substituídos por dados inventados nem copiados do histórico de mensagens para fingir revalidação. As contagens e estruturas da análise anterior continuam registradas na especificação; executar os arquivos completos contra o código novo requer recuperar/reanexar as amostras. Isso não bloqueia o desenvolvimento independente e os testes anonimizados, mas impede declarar o teste dos arquivos reais concluído.

## Estado ao concluir a etapa 1

PR de desenvolvimento: https://github.com/Yuri-Medeiros/App-Controle-Financeiro/pull/6 , em rascunho. Código dos importadores e comparação validado; integração com Gmail/tela/persistência ainda pendente. Nenhum candidato de Movimentações foi publicado. Não usar um APK sem assinatura ou outra rota para substituir o pipeline existente. As dependências/ações do pipeline, assinaturas e schema 1 foram preservados nesta etapa.

## Integração Gmail e revisão — 28/10/2026

Nesta continuação foram integradas as PRs #5 (páginas) e #4 (divisão de compra) em work, sem publicação Android definitiva nem aprovação de candidato. A #6 incorpora a base atualizada.

Implementação adicional: AuthorizationClient com gmail.readonly; busca paginada de mensagens e anexos CSV sob demanda, validação do endereço/assunto, limite de tamanho, token somente em memória; identificação local da conta Google para separar deduplicação entre contas. Tela de Movimentações com referência de fatura explicitamente confirmada, listagem paginada, descrição original e origem, categoria sugerida pela última confirmação, descrição amigável, edição individual e confirmação em lote com categorias exibidas. Dados pendentes e confirmados são mantidos no Room/SQLCipher; schema 2 com migração aditiva preservando local_state. Cada documento é transacional; reimportações não apagam histórico. No máximo 20 faturas ficam simultaneamente aguardando referência em memória; após confirmar, Atualizar retoma a busca. Ao bloquear/encerrar o processo essas propostas são descartadas e podem ser buscadas novamente, sem confirmação presumida.

Limites desta entrega incremental: pagamentos/estornos e conflitos são preservados como pendentes e bloqueados para classificação de consumo, aguardando a próxima integração de conciliação. Não há saldo consolidado, PDF, backup financeiro/restauração ou fluxo completo de pessoas/parcelas. O vínculo de referência é reutilizado para o mesmo anexo já processado; novo anexo de fatura exige confirmação. Não é entrega final de toda a especificação.

Teste de Instrumentation ampliado para criar banco cifrado schema 1, migrar para 2, conferir preservação, importação, reimportação, conflito, rejeição atômica e persistência da confirmação. Exige execução em aparelho; compilação não representa execução. OAuth e importação com conta real ainda exigem teste no S23. Nenhum arquivo financeiro real foi versionado.

Conciliação inicial adicionada nesta etapa: pagamento recebido na fatura e saída de pagamento de fatura na conta são vinculados automaticamente apenas para correspondência única de mesma conta Google, data e valor. Ambiguidade em qualquer lado ou data diferente permanece pendente. Esses registros têm natureza de pagamento e nenhuma categoria de consumo. Estornos e resolução manual de conflitos continuam pendentes. Testes JVM cobrem correspondência única, ambiguidades, datas/valores/contas diferentes e créditos que não são pagamento.

## Correção de desbloqueio reportada no S23 — 28/10/2026

O usuário gravou duas tentativas: o prompt aceita a autenticação e a interface volta a "Não foi possível desbloquear seus dados". Confirmou a mesma falha com digital e padrão. O erro está no callback de sucesso, anterior à abertura do Room, e não demonstra falha na tela Gmail/Movimentações.

Defeito encontrado: prepareUnlock chamava Cipher.updateAAD antes da autorização por operação do Android Keystore. O provedor Android pode guardar internamente a exceção dessa chamada e só lançá-la no doFinal, mesmo após o prompt aceitar a autenticação (AndroidKeyStoreCipherSpiBase.engineUpdateAAD/engineDoFinal). A preparação agora apenas inicializa o cipher; a mesma AAD é enviada em finish, após o callback autorizar exatamente esse cipher. Alias, conteúdo da AAD, formato database-key.v1, banco e assinaturas são preservados. Nenhum reset ou fallback sem autenticação foi adicionado. Versão planejada 0.3.1.

Testes de regressão usam AES-GCM real com um provedor de teste que reproduz o cache de erro pré-autenticação do Android. Cobrem criação e leitura com autorização, compatibilidade do envelope e reprodução da falha anterior. Não equivalem a teste no S23. Mensagens de falha passam a incluir somente código fixo de etapa/tipo, sem mensagens do provedor, caminhos, senhas ou chaves, para permitir diagnóstico caso outra causa permaneça.

Referência do comportamento do provedor: https://github.com/aosp-mirror/platform_frameworks_base/blob/android15-release/keystore/java/android/security/keystore2/AndroidKeyStoreCipherSpiBase.java . O candidato teste-pr6-r6 não está aprovado; uma nova compilação e validação no S23 são necessárias.
