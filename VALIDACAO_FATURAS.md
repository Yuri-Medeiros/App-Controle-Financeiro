# Faturas — versão 0.18.0

A segunda aba passa a ser Faturas. O botão Movimentações no Início abre a revisão existente, com importação Gmail, filtros e seleção em lote; Voltar e o ícone Início retornam ao resumo.

A fatura inicial é a primeira ainda não quitada com o banco entre as referências registradas. Setas e seletor de mês permitem consultar as demais. O total soma as compras e créditos importados e as parcelas previstas ainda sem vínculo. Uma parcela vinculada aparece somente uma vez. Importação que pode sobrepor parcelas previstas gera aviso para revisar o vínculo, sem inferir equivalência por descrição/valor. Conflitos são sinalizados; os registros conflitantes ficam fora do total importado.

Os grupos por pessoa começam recolhidos e mostram sua parte, o valor a receber e quanto foi atribuído aos pagamentos do banco. Ao expandir aparecem compras, parcelas, valores recebidos e o acesso ao recebimento. Compras sem divisão registrada permanecem em **A revisar**, inclusive se já tiverem categoria; não se presume que pertençam ao titular.

Pagamentos bancários confirmados são as duplas conciliadas entre conta e cartão, inclusive as conciliações automáticas antigas. Os pagamentos sem rateio continuam computados no total pago, mas não se atribuem automaticamente a ninguém. Registrar pagamento permite dividir uma dessas duplas com uma sugestão baseada nos recebimentos da própria fatura, limitada às partes conhecidas; o restante exige conferência. Pagamentos pendentes oferecem a conciliação já existente, inclusive manual para datas diferentes. Não há criação manual de saídas fictícias. Ratear não consome orçamento, não gera nova transferência nem quita dívidas familiares.

O rateio é um evento adicional no diário financeiro cifrado existente, sem alteração de schema ou reescrita de eventos anteriores. O pagamento só pode ser rateado uma vez. Fontes, pessoas, referência, valor integral e limites são validados em transação. Estornos posteriores preservam pagamentos reais; o limite para atribuir pagamentos antigos considera as compras originais, antes dos créditos. Backup inclui os novos eventos e valida suas fontes. O teste Android de preservação inclui reabertura, backup/restauração e rejeição de rateio repetido.

Recebimentos usam transferências efetivas do extrato, com sugestão editável das dívidas mais antigas primeiro. A distribuição integral precisa ser confirmada; não há baixa automática, compensação de dívidas opostas ou juros. Saldos iniciais históricos não viram recebimentos inventados.

Pendências anteriores exibem dívidas ainda abertas de faturas mais antigas quitadas no banco, por pessoa e mês. Próximas faturas incluem todos os compromissos registrados até a última parcela. Datas explícitas de parcelas são preservadas; calendário de ano não validado pede confirmação em Informações → Alterar vencimento.

## Conferência no Android

1. Abrir Faturas com dados importados. Conferir referência inicial, vencimento, total, pago e restante com os documentos.
2. Abrir o seletor, trocar meses nas duas direções e conferir o recolhimento inicial dos grupos.
3. Expandir uma compra compartilhada: cada participante vê somente sua parte; conferir centavos e parcela.
4. Conferir importação ainda sem responsáveis e previsão sem vínculo; revisar em Movimentações sem perder filtros, revisão ou categorização em lote. Voltar deve abrir o Início.
5. Com pagamento parcial conciliado, conferir o total pago antes do rateio. Dividir, alterar a sugestão e salvar; conferir que o total pago não duplicou e as dívidas familiares não mudaram.
6. Registrar um Pix que paga dívidas de meses diferentes. Ajustar a sugestão e conferir os saldos individuais; o valor pago ao banco não deve mudar.
7. Conferir pendências antigas após quitação bancária, estorno de parte já recebida e todas as parcelas futuras.
8. Fechar/reabrir, bloquear/desbloquear, mudar de aba com modal aberto e testar fonte repetida. Testar backup/restauração em instalação de teste separada.

Compilação e testes JVM não comprovam execução desse roteiro no aparelho. APK de teste deve ser gerado pelo workflow de distribuição na PR 11 após CI do commit atual; não alterar a identidade do app nem instalar APK unsigned.
