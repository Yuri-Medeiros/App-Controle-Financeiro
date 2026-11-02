# Fluxos financeiros — 30/10/2026

Esta entrega continua a PR #9, mantendo Compose, MVVM, SQLCipher, as identidades de assinatura e o pipeline existente. A versão passa a 0.8.0. Não representa aprovação de instalação ou publicação.

## Implementado no aplicativo

| Área | Comportamento |
| --- | --- |
| Compras e parcelas | Descrição, categoria/subcategoria, número atual, quantidade, vencimentos editáveis e participantes. Divisão igual, percentual ou por valores; prévia obrigatória de cada parcela e dos saldos familiares antes de gravar. |
| Centavos | Maiores restos, ordem fixa dos participantes preservada em array no histórico. Regra confirmada nesta conversa. |
| Importações seguintes | Vínculo explícito à parcela prevista. Diferenças de valor exigem nova prévia; não se reescrevem parcelas com pagamentos ou estornos. Não há associação automática por nome ou valor. |
| Histórico familiar | Data inicial informada pelo usuário, sem presumir que compras antigas estejam abertas ou quitadas. É possível confirmar saldo inicial em aberto inferior à parte original, sem inventar recebimentos anteriores. |
| Pessoas | Totais a receber e a pagar separados, atrasos, parcelas abertas, histórico, dívidas identificadas fora do cartão e alteração de vencimento com motivo. |
| Recebimentos | Transferência importada escolhida pelo usuário; valor integral distribuído entre uma ou várias dívidas, incluindo abatimentos parciais. Não há compensação entre direções, quitação automática, juros ou multa. |
| Fatura | A obrigação integral com o banco permanece separada dos recebimentos familiares. Conciliação automática existente é preservada; pares pendentes de mesma quantia podem ser confirmados manualmente, inclusive com datas diferentes. |
| Estornos | Crédito vinculado à compra e às parcelas expressamente escolhidas. Sugestão proporcional editável, com maiores restos; fonte usada uma única vez. Pagamentos anteriores são preservados. Crédito sobre uma parte paga cria obrigação de devolver em separado; não compensa outras dívidas. |
| Limite histórico do estorno | O saldo inicial em aberto não prova recebimentos anteriores. A devolução automática usa somente pagamentos efetivamente registrados; parcelas históricas sem conciliação exigem confirmação separada de eventual valor a devolver. |
| Outros créditos | Possibilidade de confirmar outro crédito da fatura, com motivo, sem tratá-lo como estorno ou receita bancária. |
| Conflitos de importação | Versão divergente pode ser desconsiderada nos cálculos ou mantida explicitamente como operação distinta; ambas preservam o registro original. Não existe substituição silenciosa de dados financeiros vinculados. |
| Flash | Saldo documentado separado do banco; créditos de R$ 150 aos domingos podem ser explicitamente conciliados com a previsão. Não são somados ao disponível. Saldo com mesma data/hora e valores divergentes fica a confirmar. |
| Orçamento | Limites por ciclo salarial, sem acumular sobras. Categorias e subcategorias reutilizadas; limite pai cobre os filhos, sem permitir limites sobrepostos. Só a parte própria da parcela consome o orçamento no ciclo do vencimento. |
| Previsões | Uma vez, semanais, mensais ou anuais, escolhidas no cadastro. Vínculo explícito à saída real, com prevalência do valor real e sem descontar novamente a previsão. Encerrar recorrência preserva ocorrências passadas. |
| Reservas | Histórico de acréscimos/liberações, separado entre conta e investimentos. Reserva fixa aplicada uma vez por ciclo validado, acumulando. Não realiza aporte ou resgate bancário. |
| Disponível | Saldo bancário confirmado no fim de uma data, ajustado somente por movimentos posteriores; menos obrigações, reservas na conta e orçamento adicional. Não inclui salário futuro, Flash, investimentos ou familiares antes do recebimento. Déficit mantém sinal. Dados insuficientes ou vínculos ambíguos suprimem o número e indicam o que confirmar. |
| Projeções | Próximos ciclos conservadores, considerando salário somente no próprio ciclo, limites e novas reservas. Não extrapola calendários não validados. |
| Patrimônio | Saldos reais de investimentos, quantidades de BTC em satoshis, FGTS confirmado e veículos; histórico de confirmações preservado. Aportes, resgates e vendas vinculados ao extrato, incluindo custos, sem criar outra movimentação nem consumir orçamento como despesa comum. |
| Venda de veículo | Retira a avaliação do patrimônio, preserva histórico e usa somente a entrada líquida importada no banco. |
| FGTS | Saldo inicial, confirmações reais e configuração dos depósitos previstos no dia 20, com opção de interromper. Nenhuma taxa de rendimento foi inventada. Projeção financeira completa ainda depende da metodologia e das séries descritas abaixo. |
| Meta | R$ 100 mil por padrão, valor editável e seleção dos componentes. Flash e recebíveis começam desmarcados. A escolha não altera a disponibilidade financeira. Valores sem cotação são identificados como parciais. |
| Demonstrativos | Resumo sem detalhar gastos e demonstrativo por vencimento, com prévia antes do compartilhamento nativo. Nenhuma mensagem é enviada automaticamente. |

Os cards principais permanecem compactos. Detalhes e vínculos ficam no toque da movimentação; o perfil da pessoa separa aberto e histórico. Planejamento usa três seções curtas: orçamento, previsões e reservas. Não foram adicionadas dependências de runtime ou serviços pagos.

## Banco e preservação

Schema 4 com migração aditiva 3→4: tabela `financial_events`. Movimentações, documentos importados, pessoas e estado local são mantidos. Eventos financeiros são validados e acrescentados em transação; o histórico não é reescrito. Revisões alteram somente o estado de classificação dos registros envolvidos, preservando o payload original.

Os schemas 3 e 4 exportados pelo Room são versionados. Há teste SQLite real de preservação da migração e Instrumentation para banco cifrado, persistência dos vínculos, ordem de participantes, rejeição atômica e recuperação. Instrumentation precisa ser executada no Android; compilá-la não comprova migração no S23.

## Backup e restauração

Formato aprovado nesta conversa: arquivo versionado, AES-256-GCM, senha de recuperação independente da assinatura e prévia antes de restaurar. Implementação usa PBKDF2-HMAC-SHA256 com 600 mil iterações, sal aleatório de 16 bytes e nonce aleatório de 12 bytes. Cabeçalho autenticado e limites de tamanho protegem a leitura de formatos inválidos. Chave local do banco, credenciais Google e senha não entram no backup.

Dados são exportados de uma transação consistente, cifrados em memória e enviados apenas à área `drive.appdata`. Arquivo novo é lido de volta e comparado antes da retenção: mais recente e uma versão diária dos últimos 15 dias UTC. O arquivo escolhido para restauração fica protegido durante a operação. Principal e candidato usam namespaces separados por `applicationId`, inclusive na validação do conteúdo.

Senha e token ficam em memória durante a sessão; são limpos no bloqueio. Backup automático precisa ser ativado naquela sessão, agrupa alterações por 30 segundos e só inicia enquanto o app está aberto/visível. Expiração ou revogação da autorização preserva os backups e exige reautorizar. Não foi implementado armazenamento persistente da senha de recuperação.

Antes de habilitar gravação automática sobre backups existentes, o mais recente precisa abrir com a senha fornecida. Instalação nova sem movimentações exige confirmação extra para começar com os dados locais, oferecendo restaurar os anteriores.

A restauração verifica formato, pacote, estrutura, vínculos, ações e fontes antes de alterar o banco. Após confirmação, exige cópia verificada do estado atual no Drive; substitui os dados em transação sob a chave SQLCipher deste aparelho. Uma falha anterior à troca não apaga os dados locais.

Acesso real ao Drive, cancelamento/revogação e recuperação em instalação limpa continuam pendentes de teste no aparelho. A API Drive e o escopo precisam estar habilitados no projeto Google; não foram ativados por esta execução.

Referência: https://developers.google.com/workspace/drive/api/guides/appdata

## Calendário confirmado

Fontes aprovadas nesta conversa:

- Prefeitura de Ribeirão Preto: https://www.ribeiraopreto.sp.gov.br/portal/principal/feriados
- Febraban: https://portal.febraban.org.br/noticia/4413

Snapshot consultado em 30/10/2026, limitado ao ano 2026. Inclui feriados municipais/estadual e dias bancários sem expediente; não transforma automaticamente pontos facultativos municipais em feriados. Dias 5 e 8 avançam para o dia útil validado. Anos ainda não validados exigem confirmação das datas, com fonte e ano visíveis. Não há atualização remota automática do calendário anual nesta entrega.

## Pendências que não foram simuladas

1. Cotação de Bitcoin: proposta de fonte pública Coinbase enviada ao usuário; aguardando decisão. Referência consultada: https://docs.cdp.coinbase.com/coinbase-app/track-apis/prices . O endpoint BTC/BRL não pôde ser lido pela ferramenta web desta execução. Não há cotação inventada nem resposta real validada.
2. FIPE: opção gratuita Parallelum submetida à decisão; documentação consultada em https://deividfortuna.github.io/fipe/ . Nenhum plano, token pago ou scraping foi ativado. Valores manuais e histórico de venda funcionam independentemente da fonte.
3. FGTS: falta validar séries, periodicidade, TR, juros, distribuição de resultados, piso de inflação e metodologia das estimativas. Foram consultadas as fontes primárias https://www.caixa.gov.br/beneficios-trabalhador/fgts/rendimentos-perguntas/Paginas/default.aspx e https://www.fgts.gov.br/Paginas/transparencia-prestacao-contas/resultados-fgts.aspx ; a apuração anual e os créditos de distribuição não podem ser substituídos por uma taxa fixa de projeção. O app mantém apenas valores reais confirmados e configuração dos depósitos; não apresenta taxa fixa como garantia.
4. PDF de investimentos: amostra real ainda necessária para parser e importação; o Gmail continua processando os CSVs já especificados. Não foi inventado OCR/parser para um arquivo ausente.
5. Migração de responsáveis da planilha antiga: requer XLSX e confirmação dos vínculos reais; nenhuma dívida antiga foi inferida a partir de pagamentos ao banco.
6. Candidato instalável: deve seguir `PIPELINE.md`; o CLI retornou Forbidden no acesso à API GitHub. Não contornar assinatura, geração do candidato ou aprovação por tag/SHA.
7. Interface, biometria/PIN, OAuth, atualização preservando dados e restauração precisam de validação no S23. Checks de compilação não substituem essas verificações.

## Verificação desta entrega

Os resultados finais e o SHA serão registrados na PR após os checks locais. Os testes cobrem divisão, vinculação sem recriação, saldo inicial familiar, pagamento parcial/múltiplo, sentidos separados, estorno após pagamento, ajuste de parcela, dinheiro exato, disponibilidade, ciclos, calendários desconhecidos, reserva sem duplicação, recorrência sem deriva, investimentos/vendas, cifragem, senha incorreta, corrupção de backup, retenção e falhas de upload/leitura antes da limpeza.
