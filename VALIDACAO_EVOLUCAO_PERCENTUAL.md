# Evolução percentual dos patrimônios — 0.29.0

O gráfico Evolução passa a comparar percentuais, tanto por tipo na tela Patrimônio quanto por posição na tela Investimentos. Cada série começa em 0% na primeira avaliação disponível. As legendas mostram nome e percentual com sinal e duas casas decimais. Não há grade ou escala vertical visível; as datas de atualização continuam no eixo horizontal.

O domínio vertical é compartilhado, incluindo zero, com uma faixa mínima interna de 20 pontos percentuais mais margem. Essa faixa mantém pequenas mudanças próximas, sem ampliar artificialmente oscilações mínimas. Não há deslocamento artificial de uma linha em relação à outra; percentuais iguais podem coincidir. As legendas mantêm a regra de evitar sobreposição, priorizando acima das pontas e depois posições anteriores.

## Base do percentual

A projeção encadeia as razões entre avaliações anteriores e posteriores, usando precisão decimal. O cálculo usa o histórico completo antes de selecionar as últimas oito datas visíveis; a base não muda por causa da janela exibida.

- Confirmações de saldo e novas cotações alteram o índice de avaliação. Por tipo, a razão usa o total avaliado antes/depois, ponderando as posições pelo valor.
- Cadastros, aportes, retiradas, vendas e remoções registrados alteram a base de capital, sem gerar variação percentual por si mesmos. Previsões do FGTS não geram ganhos.
- Mudanças históricas de quantidade de Bitcoin não são tratadas como rendimento. Novas cotações refletem a mudança de preço, com a quantidade atual.
- Sem avaliação, o ponto fica ausente. Ao obter a primeira base válida, a série inicia em zero. Lacunas reiniciam a base disponível, sem inventar continuidade.
- Base zero não é usada como divisor. Enquanto a base é zero, o índice acumulado é preservado. O primeiro valor positivo não gera ganho sobre zero; uma avaliação que cai de positiva a zero pode indicar -100%.
- Eventos retroativos corrigem o último ponto cronológico, conforme a política já usada no histórico nominal. Não há regravação de dados.

O botão de informação explica que esta é variação das avaliações disponíveis, não rentabilidade líquida: confirmações de saldo importadas podem conter fluxos não registrados; impostos, taxas e lucro realizado de venda não são reconstruídos. O saldo real, os movimentos e os gráficos de distribuição permanecem em reais.

## Verificação

Testes JVM cobrem valores iniciais muito diferentes com a mesma variação relativa, ponderação por tipo, encadeamento após aporte, resgate neutro, cadastros/remoções neutros, cotação e quantidade de Bitcoin, falta de cotação, base zero, perda de 100%, eventos retroativos e preservação do diário. Ainda é necessária conferência visual no Android, especialmente para séries coincidentes e fontes de acessibilidade maiores.
