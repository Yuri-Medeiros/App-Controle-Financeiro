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
- Gradle/CI: execução em andamento; resultado será registrado após conclusão.
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
