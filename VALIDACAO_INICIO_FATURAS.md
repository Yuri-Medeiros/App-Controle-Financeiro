# Início das faturas e acesso a Extratos — 0.24.0

Novos CSVs de cartão só são aceitos com referência a partir de dezembro de 2026. A referência continua sendo a do nome do arquivo, não a data das compras. Documentos anteriores são marcados como processados sem inserir seus lançamentos, evitando repetição a cada atualização. Não há exclusão automática de compras históricas já cadastradas antes desta regra.

A primeira referência aceita de cada conta é a base de abertura. Seus lançamentos de `Pagamento recebido` pertencem à fatura anterior e ficam fora das telas, da conciliação e dos totais. Somente pagamentos de fatura identificados por PaymentMatching são excluídos da projeção: estornos, reembolsos e compras permanecem; o extrato bancário não é apagado.

A ordem de chegada não interfere: se janeiro chega antes de dezembro, janeiro inicialmente é a abertura; ao importar dezembro, os pagamentos de janeiro passam a participar normalmente, e os de dezembro ficam excluídos. Um documento vazio também estabelece a referência inicial. A seleção é independente por conta.

Os dados brutos permanecem cifrados para deduplicação, recuperação e preservação do histórico. Não há migração de banco nem alteração dos eventos financeiros já gravados. O reset inclui também os pagamentos ocultos, para arquivá-los junto com as demais fontes do cartão. Backup e reimportação preservam a mesma projeção.

## Tela

- Mês e vencimento mais próximos do cabeçalho; vencimento exibe apenas a data.
- Chevron vetorial alinhado ao centro do seletor, no lugar do caractere de seta.
- Avisos agrupados em painel âmbar, com acesso a Extratos.
- Acesso no Início e título da página passam a Extratos. A abertura seleciona o mês mais recente dos registros disponíveis.

## Validação

Testes JVM cobrem o limite pela referência, a primeira fatura posterior a dezembro, a ordem inversa de chegada, múltiplas contas, documento vazio e preservação dos créditos/transferências bancárias. O teste integrado de banco cifrado cobre rejeição, deduplicação de pagamentos ocultos, backup, reset e reimportação. Ele é compilado nos checks; a execução em aparelho permanece pendente.
