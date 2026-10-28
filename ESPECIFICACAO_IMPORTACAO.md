# Primeira tela: Movimentações com Gmail e importação

## Estado e autorização

A implementação manual foi cancelada pelo usuário; seus arquivos incompletos permanecem locais e não devem ser apresentados como entrega. Na entrevista posterior foi escolhida Movimentações como primeira tela e incluída a integração Gmail/importação. Este documento consolida decisões aprovadas, sem declarar implementação ou testes concluídos.

O código enviado à PR #4 ainda contém a base protegida e divisão isolada de compra; não contém autorização Gmail, importadores ou tela de revisão funcional. As aprovações abaixo não são aprovação de candidato/SHA nem publicação definitiva.

## Aquisição e fontes reais

- Atualização sob demanda pelo botão Atualizar, com gmail.readonly e AuthorizationClient já aprovados.
- Identificação automática dos e-mails; todos os documentos vêm anexados. Não há download por links nesse fluxo confirmado.
- Buscar todos os documentos correspondentes disponíveis, sem depender de não lidos ou limitar por mês. O usuário exclui mensagens; manter histórico importado no app independentemente da permanência do e-mail. Não foi aprovada importação manual por arquivo.
- Nubank: remetente observado todomundo@nubank.com.br; assuntos observados Extrato da sua conta do Nubank, Extrato da fatura do Cartão Nubank e Seu extrato de custódia de investimentos chegou.
- Flash: remetente observado noreply@flashapp.com.br; assunto Seu extrato Flash chegou!.
- Remetente/assunto localizam candidatos; validar o conteúdo do anexo. Não confundir nome exibido com endereço do remetente.
- Arquivos financeiros e dados pessoais fornecidos para análise não devem ser versionados em repositório público.

## Estruturas verificadas

- Flash: Data, Hora, Movimentação, Valor, Meio de Pagamento, Saldo; CSV UTF-8, delimitador vírgula, valores BRL textuais com vírgula decimal, aspas e espaço não separável. Sem ID. Nome original com 09-2026 pode conter setembro e outubro; não inferir período só pelo nome. Há registro zero.
- Flash, duas amostras: 9 e 17 registros, 3 ocorrências compartilhadas idênticas, união de 23. Existem duas movimentações distintas com mesma data/hora. A comparação não valida todos os casos futuros de ambiguidade.
- Conta Nubank: Data, Valor, Identificador, Descrição; 85 registros e 85 IDs únicos nesta amostra, valores com ponto decimal. Estorno com mesmo ID e sinal negativo foi informado pelo usuário, mas não está demonstrado nessa amostra.
- Cartão Nubank: date, title, amount; 127 registros na fatura de referência 2026-10-08 e 51 na de 2026-11-08. Sem ID. Valores com vírgula decimal, separador de milhar e negativos com espaço depois do sinal. Parcelas constam no título. Valores negativos incluem pagamentos e outros créditos; sinal sozinho não determina estorno.
- PDF de investimentos ainda não recebido/analisado. Não escolher biblioteca, OCR ou regras de extração por suposição.

## Deduplicação aprovada

- Flash: data, hora, descrição, valor, meio e saldo, preservando multiplicidade de linhas idênticas. Possíveis correções/divergências vão à revisão sem substituição automática.
- Conta Nubank: mesmo ID e mesmos dados é repetição; ID igual com sinal oposto preserva ambos; outras diferenças vão à revisão. Não aplicar regra de ID ao cartão.
- Fatura: mesma referência de fatura, data, título e valor com sinal, preservando multiplicidade. Parcelas de faturas diferentes ficam separadas. Divergências vão à revisão, sem sobrescrever automaticamente.
- Referência da fatura pela data no nome Nubank_AAAA-MM-DD.csv, com confirmação na primeira importação. Reutilizar vínculo confirmado nas reimportações; nome divergente/conflito exige revisão. Referência é distinta do vencimento efetivo ajustado por dia útil.

## Revisão e aprendizado aprovados

- Lista simples: data, descrição/estabelecimento/pessoa, valor e origem; detalhes adicionais acessíveis para classificar sem poluir a lista.
- Categoria vinculada à movimentação; sempre sugerir e aguardar confirmação, sem classificação automática de novas movimentações.
- Aprendizado separado por estabelecimento/pessoa e entrada/saída; usar última categoria confirmada.
- Estabelecimentos: nome extraído, ignorando somente caixa e espaços extras. Pessoa/Pix: nome e documento apresentado quando disponível, independentemente de banco/conta. Identificação incompleta/divergente exige confirmação; nomes parecidos não são unidos automaticamente. Documento mascarado não deve ser tratado como identificador global único.
- Nome amigável/descrição opcional para ajudar identificação futura, preservando texto original.
- Categorias iniciais editáveis: Moradia, Alimentação, Transporte, Saúde, Pessoal e lazer, Educação, Presentes e doações, Tarifas e impostos. Categorias/subcategorias personalizadas permitidas. Natureza financeira separada de categoria de consumo.
- Confirmação individual e em lote; mostrar categoria de cada item antes de salvar. Lote não confirma registros sem categoria.
- Corrigir categoria altera só o registro escolhido e a sugestão futura; não reclassificar histórico confirmado automaticamente.

## Pagamentos e estornos aprovados

- Pagamento recebido na fatura é pagamento de fatura, sem categoria de consumo e sem nova receita/despesa; continua visível.
- Conciliar automaticamente com saída de pagamento de fatura na conta quando houver correspondência única de mesma data e valor absoluto. Mais de uma opção exige confirmação. Datas diferentes ficam para conciliação manual, sem janela presumida.
- Possíveis estornos do cartão exigem confirmação de natureza/vínculo, preservando compra original, sem alteração automática de categoria/divisão.
- Estorno compartilhado: preencher distribuição proporcional à divisão original, permitindo edição antes da confirmação. Preservar pagamentos realizados e registrar separadamente eventual valor a devolver, sem compensar outras dívidas automaticamente.
- Estorno não cancela parcelas futuras automaticamente; apresentar parcelas para confirmação das afetadas.
- Distribuição de centavos entre várias parcelas e detalhes não resolvidos de arredondamento de estorno não foram aprovados implicitamente.

## Dependências para entrega

1. Implementar AuthorizationClient, busca paginada de mensagens/anexos e tratamento de cancelamento/revogação; configurar projeto Google exclusivo sem billing, OAuth Externo/Em produção por uso pessoal e clientes Android para as identidades pertinentes. A existência dessa configuração não foi comprovada.
2. Implementar importadores validados com amostras, persistência de origem/ocorrências/revisão, migração e testes de preservação. Não inferir saldo inicial completo dos extratos parciais.
3. Implementar a tela real, confirmação, aprendizado e tratamento explícito de pagamentos/estornos conforme esta especificação. Escopo de pessoas/parcelamentos necessário ao fluxo deve respeitar pendências anteriores, sem simular funções inexistentes.
4. Executar testes de sobreposição, reimportação, multiplicidade, sinais, vínculo de pagamento, revisão e preservação, além dos checks existentes.
5. Preparar candidato pelo workflow existente após CI do SHA atual; consulta/acionamento via gh tinha retornado Forbidden. Verificar acesso atual antes de declarar distribuição disponível. Não contornar assinatura/pipeline nem aprovar/publicar em nome do usuário.
6. Validar OAuth e o fluxo real no S23. Instalação/desbloqueio de versão anterior foram confirmados pelo usuário; não comprovam importação.
7. Backup financeiro/restauração ainda precisam de especificação e implementação; não habilitar uso financeiro definitivo sem validar recuperação.

Já há decisões suficientes para iniciar o fluxo de CSVs de conta, Flash e fatura; não é necessário terminar todas as áreas do produto para começar. PDF de investimentos e regras financeiras pendentes não devem ser inventados ou anunciados como prontos.
