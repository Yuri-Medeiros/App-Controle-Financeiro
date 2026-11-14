# Pessoas — versão 0.19.0

A página passa a apresentar A receber, Eu devo, valores vencidos, parcelas futuras conciliadas, busca e filtros. Receber e pagar permanecem separados, sem compensação. A referência inicial acompanha a próxima fatura não quitada com o banco; o seletor permite consultar os demais meses registrados. Os números são **saldos atuais segmentados pela referência**, não uma reconstrução de como estava o saldo no passado.

Pendências de cartão são agrupadas pela referência da fatura importada da parcela vinculada, ou pelo mês do vencimento registrado da previsão. A receber e Eu devo incluem os saldos até a referência escolhida; parcelas posteriores ficam fora desses totais. Vencidos é uma parte de A receber, considerando a data atual e o vencimento confirmado da fatura quando disponível. Para dívidas fora do cartão, o corte é o vencimento da fatura selecionada; se indisponível, o último dia do mês. O botão de informação mostra a data do corte. Próximas parcelas contabiliza somente saldo familiar conciliado de cartão a receber, sem incluir dívidas avulsas futuras. A lista informa valores futuros avulsos separadamente quando não há saldo naquela referência. Não são inventados recebimentos para saldos iniciais reduzidos ou partes históricas excluídas da conciliação.

A lista prioriza pessoas com vencidos, começando pelo vencimento mais antigo. Sem pendências considera também as dívidas futuras já conciliadas, nas duas direções. Ao abrir uma pessoa, o perfil é uma página própria na aba Pessoas; Voltar retorna à lista preservando busca/filtro/referência e restaura o topo da rolagem.

No perfil, Por fatura começa recolhido. Ao expandir aparecem parte atribuída, recebido, pago ao banco, restante a receber e itens com parcelas/datas. Pagamentos bancários ainda sem rateio recebem aviso; não são atribuídos automaticamente a ninguém. Faturas antigas quitadas no banco podem continuar com saldo familiar aberto. Faturas sem saldo familiar também podem ser abertas para consultar os itens. A lista inicial limita 12 faturas e oferece Ver mais.

Receber e Pagar reutilizam o fluxo de transferências reais do extrato, com sugestão editável das dívidas mais antigas, valor integral obrigatório e confirmação. O botão de informação preserva Nova dívida. Fora do cartão mostra saldos e datas e preserva a alteração de vencimento com motivo. Histórico é aberto por botão; inclui recebimentos, pagamentos, rateios bancários, estornos e ajustes da própria pessoa, antes da paginação. Não há juros nem quitação familiar por pagamento da fatura.

Demonstrativo permite resumo sem detalhar compras ou uma única fatura com apenas as partes da pessoa. A prévia mostra exatamente o texto compartilhado; o seletor Android só abre após tocar Compartilhar. Não há envio automático. Os totais de recebimento e devolução são separados, sem expor pagamentos bancários no texto do demonstrativo.

Sem alteração de schema ou das regras de persistência financeira. Os saldos são projeções dos registros existentes. As alterações permanecem locais até autorização para commit/push.

## Conferência no Android

1. Conferir totais com Faturas, incluindo saldo anterior, recebimento parcial, parcelas futuras e dívida que você deve à mesma pessoa.
2. Trocar referência e conferir o corte informado para dívidas avulsas. Verificar que dados históricos e pagamentos não mudaram.
3. Buscar e filtrar; abrir uma pessoa e voltar pelo botão e pelo gesto Android, preservando a lista.
4. Expandir faturas; conferir as partes e o rateio bancário. Uma fatura paga ao banco não deve apagar o saldo familiar.
5. Conferir estorno após recebimento e abertura histórica parcial. Não podem surgir recebimentos fictícios ou compensações.
6. Receber um Pix distribuído por dívidas de meses diferentes; pagar uma dívida avulsa com saída real. Conferir que as fontes não podem ser reutilizadas.
7. Adicionar pessoa, registrar dívida pelo botão de informação e alterar vencimento de dívida fora do cartão com motivo.
8. Conferir histórico e demonstrativo. O resumo não deve listar compras; o detalhado deve mostrar apenas a parte daquela pessoa na fatura escolhida.
9. Conferir fonte ampliada, teclado, rolagem, troca de abas e bloqueio/desbloqueio com perfil/modal aberto. Não podem aparecer modais sobre outra aba ou disparar Voltar quando Pessoas está fora de foco.

Compilação e testes JVM não comprovam execução desse roteiro no aparelho. Publicação do candidato deve seguir o pipeline existente após autorização de envio das alterações locais.
