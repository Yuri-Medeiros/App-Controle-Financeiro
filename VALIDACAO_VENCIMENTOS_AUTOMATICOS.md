# Vencimentos automáticos — 0.23.0

A referência importada do nome do CSV define o mês da fatura. O vencimento padrão é dia 8 desse mês, avançando até um dia útil. Cada parcela é calculada separadamente: novembro/2026 vence dia 9; dezembro volta ao dia 8; fevereiro/2027 vence dia 10, após o Carnaval. A data da compra não determina o vencimento.

Responsáveis e parcelas mostra o vencimento calculado, sem campo de edição ou confirmação anual. A prévia mantém a revisão dos valores e responsáveis. A fatura, os indicadores de Pessoas e as obrigações de fatura do planejamento usam a mesma regra. Exceções explícitas já registradas continuam respeitadas, com ajuste para dia útil.

## Calendário

Cálculo local, sem API ou dependência de conexão: finais de semana, feriados nacionais, 9 de julho em SP e feriados municipais de Ribeirão Preto (20 de janeiro, Sexta-feira Santa, Corpus Christi e 19 de junho). Carnaval e feriados móveis usam o cálculo gregoriano da Páscoa. O último dia de expediente bancário de dezembro também é tratado como fechamento. Quarta-feira de Cinzas permanece útil. Pontos facultativos administrativos não são automaticamente feriados bancários.

Fontes consultadas em 14/11/2026:

- [Calendário bancário FEBRABAN](https://feriadosbancarios.febraban.org.br/).
- [Lei municipal 11.919/2009 — Ribeirão Preto](https://www.ribeiraopreto.sp.gov.br/legislacao-municipal/pesquisa/lei/32092).

As regras recorrentes cobrem anos futuros; decretos excepcionais ou alterações legais posteriores precisam de atualização. O calendário anual legado de salário e outras previsões permanece separado.

## Vínculos e preservação

Vincular parcela associa um lançamento importado a uma parcela já prevista, preservando a compra e os responsáveis e evitando duplicidade. Uma previsão sem recebimentos/estornos pode ter o vencimento corrigido ao vincular. Se já existem recebimentos ou estornos, o vínculo mantém a data histórica e os valores registrados; a projeção da fatura usa o vencimento automático. Alterações de valores/divisão nessas parcelas continuam bloqueadas.

Nenhuma migração, exclusão ou regravação de eventos históricos. O calendário modifica as projeções de vencimento, sem alterar referência, data de compra, montantes, pagamentos ou rateios.

## Verificação

Testes JVM cobrem fins de semana, feriados fixos e móveis, anos futuros, mudança de ano, ausência de deriva entre parcelas, referência da parcela atual, exceções e vínculo com preservação de recebimentos. No aparelho, conferir importação, abertura de A revisar, prévia de compra parcelada, vínculo a previsão e os resumos de Faturas/Pessoas. A execução em aparelho ainda não foi realizada.
