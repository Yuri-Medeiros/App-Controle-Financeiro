# Lançamentos, títulos e sugestão de divisão — 0.30.0

## Origem e parcelas

A numeração vem exclusivamente da descrição original importada, por exemplo `Loja - Parcela 1/3`. O título amigável não muda essa interpretação. Sem padrão, o lançamento é à vista (1 de 1). Números inválidos não são convertidos silenciosamente em compras à vista. A quantidade e o número da parcela não têm campos de edição.

Vincular parcela só aparece quando há previsão compatível: descrição original numerada no lançamento e nas fontes da compra prevista, mesmo total, número atual e mês de referência, com parcela ainda sem vínculo. O seletor usa essas mesmas opções; a gravação revalida a regra no repositório. Novas compras também precisam corresponder à quantidade e ao número da fonte original. Eventos e compras antigos continuam sendo reproduzidos sem reescrever histórico.

## Título

O título ocupa o cabeçalho, sem borda ou fundo de input. Um lápis sem fundo abre edição em linha; a descrição original aparece menor abaixo. Concluir a edição não grava isoladamente. Salvar/Vincular grava a ação financeira e o nome amigável na mesma transação. Cancelar descarta o rascunho. O texto original do CSV permanece intacto.

O nome amigável reutiliza a identidade original já usada no app, por conta e entidade/descrição. Atualiza os nomes correspondentes e é reutilizado nas próximas importações. A fatura usa esse nome nas parcelas vinculadas, preservando o título histórico do plano como alternativa.

## Pessoas e divisão

A seleção inicial é Eu. A última pessoa não pode ser removida e o domínio rejeita uma lista vazia. Cancelar fecha e descarta a edição; cancelar a escolha de vínculo também restaura Eu e divisão igual. Nenhuma atribuição é persistida antes da confirmação.

Os ícones =, % e R$ são transparentes, sem contorno ou fundo. Cada pessoa mantém os mesmos elementos de valor em reais e percentual em todas as modalidades. Na divisão igual são fixos para leitura; na divisão por valor edita-se o valor; na percentual edita-se o percentual. As entradas definidas manualmente aparecem em ciano; sugestões usam cor secundária; divisões inválidas mostram erro e bloqueiam a gravação.

A edição fixa somente aquela pessoa. O restante é distribuído igualmente entre as pessoas ainda não editadas, com centavos determinísticos. Percentuais usam duas casas e fecham 100%; o rateio em reais usa a regra existente de maiores restos. Valores já fixados nunca são sobrescritos para esconder excesso ou diferença. Se todas as pessoas forem fixadas e a soma não fechar, é necessário corrigir. Trocar modalidade remove as fixações e sugere a distribuição atual na nova unidade; Igual redistribui igualmente.

## Verificação

Testes JVM cobrem seleção padrão e proibição de lista vazia, sobras em centavos, percentuais, múltiplas pessoas fixas, excesso/diferença, troca de modalidades, leitura da descrição original, incompatibilidades de número/total/referência e rejeição de previsões antigas criadas a partir de compras à vista. O teste integrado cifrado inclui rejeição de numeração alterada, atomicidade do título, preservação da origem e restauração do nome por backup. Execução em aparelho pendente; o pacote Android é apenas compilado.
