# Responsáveis nos lançamentos de Faturas — 0.22.0

Ao expandir A revisar, os lançamentos importados são acionáveis. Compras positivas ainda sem vínculo exibem Definir responsáveis; tocar no item abre diretamente Responsáveis e parcelas, reutilizando PurchaseDialog e a gravação financeira de Movimentações. É possível atribuir a uma pessoa ou compartilhar, inclusive com o titular, por partes iguais, percentuais ou valores. Categoria, conciliação familiar e revisão das parcelas seguem obrigatórias conforme as regras existentes.

A projeção InvoiceItem passa a carregar o ID da movimentação original, sem persistir esse campo ou alterar schema. Esse ID é usado para obter o ReviewMovement atual; não se procura o lançamento por descrição/valor/data. Assim, lançamentos idênticos continuam independentes e uma parte exibida em um grupo não substitui o valor integral da fonte ao abrir os detalhes.

Compras já vinculadas abrem os detalhes financeiros existentes, com responsáveis e parcelas registrados; não se recria a compra nem se altera silenciosamente uma divisão com recebimentos ou estornos. Créditos negativos abrem revisão de estorno/outro crédito, sem oferecer atribuição como compra. Parcelas futuras previstas sem fonte importada não oferecem um vínculo fictício. Os controles ficam desabilitados enquanto há salvamento, e as regras do repositório continuam protegendo contra uso repetido da mesma movimentação.

Salvar fecha o formulário e atualiza os grupos da própria fatura e os saldos de Pessoas. A atribuição não lança nova movimentação bancária, não quita a dívida da pessoa e não muda o total da fatura. O usuário continua confirmando saldos históricos para não presumir recebimentos/quitação anteriores. Se a parcela já estiver prevista, a opção Vincular parcela permite associar a importação e revisar diferenças, sem duplicar a compra.

## Conferência no aparelho

1. Abrir Faturas → A revisar, expandir e tocar em um lançamento com Definir responsáveis.
2. Selecionar uma pessoa; para atribuição integral, remover Eu após adicionar a pessoa. Para compartilhar, manter os participantes e escolher a divisão.
3. Conferir categoria, conciliação familiar, número/valor/vencimentos das parcelas e prévia. Salvar e conferir a movimentação no grupo correto e os saldos em Pessoas.
4. Repetir com dois lançamentos de mesma descrição e valor: atribuir um não deve atribuir nem remover o outro.
5. Abrir uma compra já atribuída e um crédito: os detalhes devem corresponder à fonte original, sem criar outra compra ou reaplicar recebimentos.
6. Abrir um mês futuro com parcela ainda sem importação: não deve aparecer uma fonte falsa. Importar a parcela e usar Vincular parcela; conferir que não há duplicação.
7. Conferir cancelamento, fonte ampliada, teclado, bloqueio/desbloqueio e troca de abas com formulário aberto. A gravação deve continuar exigindo autenticação e os modais só aparecem na aba ativa.

Testes JVM conferem IDs distintos para itens idênticos, atribuição de somente uma das fontes, total preservado, dívida criada na pessoa escolhida e ausência de ID fictício em previsões. Estornos mantêm o ID do crédito original. Compilação não comprova execução da interface no aparelho. Alterações locais até autorização de commit/push.
