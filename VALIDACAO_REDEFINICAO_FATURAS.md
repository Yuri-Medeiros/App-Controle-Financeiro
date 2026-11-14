# Atualização e redefinição de Faturas — 0.20.0

O cabeçalho de Faturas tem atualização pelo Gmail ao lado do botão de informação. Usa a mesma autorização e importação geral de Movimentações, com prevenção de repetição, progresso, erros e notificações. A confirmação da referência das novas faturas está disponível nas duas telas por um único componente. O usuário pode importar e confirmar as referências sem sair de Faturas. A ação geral também pode importar extratos de conta, Flash e PDFs de custódia, conforme o fluxo já existente; não é um filtro que busque exclusivamente cartão. Durante importação/salvamento ou havendo referências pendentes, a atualização fica desabilitada.

## Reset exclusivo do cartão

Em Faturas → (i) → Redefinir faturas, o aplicativo apresenta a quantidade de lançamentos, compras, parcelas e saldos familiares vinculados. A ação só é habilitada após digitar RESETAR e não executa durante outra operação ou antes de confirmar referências pendentes. A conferência e a execução usam a sessão autenticada, com validação de geração. Os IDs das fontes, compras e documentos são conferidos novamente na transação; uma prévia desatualizada falha antes de alterar dados.

A redefinição retira da consulta todas as faturas registradas, parcelas, divisões, estornos, créditos de cartão e rateios bancários associados. Saldos familiares do cartão, inclusive devoluções geradas por seus estornos, saem da projeção ativa. O calendário manual de vencimentos de fatura também é redefinido. Pessoas, extratos bancários, Flash, patrimônio, planejamento restante e dívidas avulsas são preservados.

Os registros de cartão recebem status ARCHIVED, sem apagar os payloads originais ou os eventos financeiros anteriores. Um novo evento guarda a redefinição, os IDs arquivados e as referências dos documentos. A projeção ativa descarta os eventos do cartão; em pagamentos mistos conserva somente a alocação fora do cartão, sem reescrever o evento original no banco. Transferências usadas apenas pelo cartão ficam novamente pendentes de classificação; transferências mistas continuam reservadas para impedir reutilização integral. A conferência avisa quando há casos mistos.

Pagamentos reais de fatura na conta são mantidos e voltam a aguardar conciliação. Os índices de importação exclusivamente de cartão são removidos, com seus metadados preservados no evento de reset. A comparação de novos arquivos ignora os registros arquivados. Assim, atualizar pelo Gmail e confirmar as referências permite reimportar os mesmos arquivos, com novos IDs e conciliação contra as saídas bancárias já existentes. A repetição normal da importação continua deduplicada.

O diário original é a tabela financial_events; a lista de ações no snapshot é a projeção ativa após a redefinição. A restauração usa o diário original, incluindo os resets, para reconstruir a projeção. O backup preserva registros arquivados, eventos e fontes e verifica correspondência entre os cartões arquivados e os eventos de redefinição. Vínculos antigos de cartões arquivados não exigem que a conta ainda aponte para eles, pois uma reimportação pode ter conciliado a saída bancária com o novo registro.

Não há alteração de schema, recriação do banco, mudança de chaves ou reset dos demais módulos. Os testes JVM cobrem recebimentos mistos, liberação seletiva das fontes, estornos, repetição de reset, codec/replay e preservação de patrimônio/planejamento/dívidas avulsas. O teste Android foi ampliado para conferir transação, fontes, pessoas, reimportação, conciliação, deduplicação e backup/restauração após reset. A compilação desse teste não equivale à execução em aparelho.

## Conferência no aparelho

1. Tocar em Atualizar em Faturas, autorizar Gmail e confirmar as referências ali mesmo; conferir que o novo dado aparece sem precisar abrir Movimentações.
2. Conferir atualização repetida, referência inválida, falha de rede, bloqueio/desbloqueio e referências pendentes. Não deve haver importação duplicada ou confirmação em sessão bloqueada.
3. Abrir Redefinir faturas e conferir as quantidades. Cancelar deve manter tudo. Confirmar exige RESETAR.
4. Após redefinir, conferir ausência de faturas, parcelas e dívidas do cartão em Faturas, Pessoas, Início e Planejamento. Pessoas cadastradas, conta, patrimônio, Flash e dívidas avulsas devem continuar presentes.
5. Conferir especialmente transferências com alocação mista: a dívida avulsa conserva o que já foi pago; o extrato mantém a transferência e ela não pode ser reutilizada por inteiro.
6. Atualizar novamente, confirmar referências e conferir novas faturas sem duplicar os lançamentos da conta ou reincluir os registros arquivados.
7. Fechar/reabrir e conferir backup/restauração em instalação de teste, incluindo um segundo reset depois da reimportação.

Alterações locais; publicação depende de autorização para commit/push e do pipeline existente. A redefinição só é realizada no aparelho após a confirmação na nova versão.
