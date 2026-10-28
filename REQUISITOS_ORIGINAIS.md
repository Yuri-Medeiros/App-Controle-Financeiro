# Pedido original completo

Texto fornecido pelo usuário, preservado integralmente abaixo. Decisões posteriores em DECISOES.md substituem propostas ou pendências antigas já resolvidas; os demais requisitos continuam válidos. Caminhos de arquivos antigos não garantem disponibilidade no ambiente atual.

---

Você será responsável por validar a viabilidade técnica e desenvolver um aplicativo Android pessoal de controle financeiro. As decisões já tomadas estão consolidadas abaixo.
Regra obrigatória de colaboração: consulte-me em qualquer momento de tomada de decisão. Não escolha unilateralmente alternativas de produto, arquitetura, bibliotecas, integrações, fontes de dados, regras financeiras, interfaces, prioridades ou soluções para impedimentos. Apresente o contexto, as opções, sua recomendação e aguarde minha resposta antes de executar algo que dependa dessa decisão. Não é necessário reconfirmar decisões explicitamente estabelecidas neste documento; execute-as e informe o andamento. Se encontrar ambiguidades ou contradições, consulte-me.
1. Objetivo e restrições
Construir um aplicativo de uso exclusivamente pessoal para:
- Controlar receitas, despesas, saldos e faturas.
- Planejar orçamento por categoria entre recebimentos de salário.
- Mostrar quanto ainda posso gastar com segurança.
- Controlar compras compartilhadas, empréstimos e dívidas com familiares.
- Acompanhar patrimônio, investimentos e uma meta de acumulação.
- Importar dados financeiros com o mínimo possível de digitação.
Restrições obrigatórias:
- Custo de operação de R$ 0, sem assinaturas ou serviços pagos.
- Uso somente no Android.
- Aplicativo instalável; distribuição por APK atende. Não há exigência de publicação na Play Store.
- Uso conectado é suficiente.
- Um único usuário e um aparelho principal. Sincronização simultânea entre aparelhos não faz parte da primeira versão.
- Dados locais, com backup e recuperação pela conta Google.
- Não contratar serviços, habilitar cobranças ou substituir requisitos para contornar dificuldades sem me consultar.
2. Stack proposta e validações necessárias
A recomendação apresentada foi:
Camada	Tecnologia proposta
Android nativo	Kotlin e Jetpack Compose
Banco local	Room / SQLite
Desbloqueio	Android BiometricPrompt
Autorização Google	OAuth
Consulta de e-mails	Gmail API
Backup	Google Drive API, área privada do aplicativo
Importação	Processamento local de CSV e PDF
Compartilhamento	Compartilhamento nativo do Android
Dados externos	APIs gratuitas consultadas por HTTPS


Trate essa stack como a proposta de referência. Valide-a e apresente os resultados para minha confirmação antes de iniciar a implementação. Qualquer alteração exige consulta.
Não há necessidade estabelecida de servidor, Firebase, hospedagem, domínio ou IA paga.
Validações pendentes:
- Viabilidade da autorização Gmail para um app Android de uso pessoal, considerando permissões restritas, modo de teste, renovação de autorização e eventuais exigências de verificação.
- Viabilidade e limites gratuitos do backup no Drive.
- Fontes gratuitas para cotação do Bitcoin, consulta FIPE e informações necessárias à projeção do FGTS.
- Disponibilidade real de uma fonte oficial da FIPE; não pressupor que exista API pública oficial.
- Formatos reais dos arquivos Nubank e Flash.
- Ambiente de compilação, assinatura e instalação do APK.
Não considere acesso a essas fontes garantido antes de testar. Se houver impedimento, apresente alternativas e aguarde minha decisão.
3. Importação e atualização
Instituições e documentos:
- Nubank: extrato da conta em CSV, faturas do cartão em CSV e documento PDF com saldo de investimentos.
- Flash: extrato do vale-alimentação em CSV.
- Os documentos são solicitados manualmente por mim às instituições e recebidos por e-mail.
- O aplicativo anterior usa Apps Script para ler o Gmail; ele não solicita extratos.
Fluxo decidido:
1. Solicito os documentos às instituições.
2. Abro o app e aciono “Atualizar”.
3. O app busca os novos anexos no Gmail e processa os documentos.
4. Movimentações reconhecidas podem ser importadas automaticamente.
5. Categorias desconhecidas, novas compras parceladas e possíveis pagamentos de familiares entram em uma lista de pendências.
6. Confirmações necessárias são feitas por mim.
A atualização é sob demanda, substituindo a ideia inicial de processamento automático ao chegar um e-mail.
Regras:
- Aprender classificações por estabelecimento ou descrição do extrato.
- Desconhecidos devem ser categorizados manualmente.
- Sugerir possíveis pagamentos de familiares pelo nome do remetente, sem confirmar a quitação automaticamente.
- Impedir duplicidades em reimportações e sobreposição de extratos.
- Vincular registros manuais e previsões às movimentações efetivas, evitando contagem duplicada.
- Validar comigo os critérios de reconhecimento, conciliação e deduplicação antes de implementá-los.
4. Migração e base antiga
O sistema anterior foi construído no AppSheet e usa Google Sheets como banco.
Foi disponibilizado o arquivo:
/tmp/codex-remote-attachments/01a16a4f-9a31-7109-b4ba-30281cb30003/01311ff4-9842-4b03-a9c3-356adbca2cb4/1-Financeiro-Banco-de-Dados.xlsx
Verifique se ainda está acessível. Se não estiver, solicite novo envio.
Análise já realizada:
Aba	Registros	Conteúdo
Extrato	645	Movimentações da conta entre nov/2025 e mar/2026
Movimentos	498	Principalmente vale-alimentação, entre mai/2024 e mar/2026
Investimentos	47	Aportes, rendimentos e resgates
Crédito	1.826	Compras e parcelas com vencimentos entre jun/2024 e out/2027
Pessoas	12	Cadastro de pessoas


Outras abas incluem Saldos, Faturas, Meses, Bitcoin, Objetivo e Atualização. Faturas e Meses possuem resumos calculados por fórmulas.
Interpretação confirmada da aba Crédito:
- Cada linha contém o valor da parcela atribuído àquela pessoa.
- “Valor pago” representa o que paguei ao banco, e não o que recebi de familiares.
- Existe um identificador relacionado ao parcelamento e um identificador próprio de cada registro/parcela. Validar os vínculos reais antes de reconstruir compras.
- A aba não oferece histórico suficiente para inferir recebimentos de familiares.
- A análise encontrou 1.738 registros com valor integralmente pago ao banco e 88 sem pagamento, sem pagamentos parciais nessa aba.
- Existem parcelas futuras até outubro/2027.
- O objetivo antigo de R$ 60 mil está desatualizado.
Estratégia de migração definitiva:
- Usar arquivos das instituições desde janeiro de 2026 como fonte principal.
- Vou fornecer os extratos de todas as contas e as faturas.
- Não copiar integralmente os valores da base antiga: ela pode conter ajustes manuais.
- Usar a base antiga como referência para sugerir categorias, responsáveis e parcelamentos, com confirmação.
- Não há pendências anteriores a janeiro/2026 a transportar.
- Na primeira carga, compras de cartão em aberto e futuras devem ter responsáveis e divisões confirmados.
- Dívidas fora da fatura serão cadastradas manualmente.
- A conciliação de recebimentos de familiares começará em uma nova data, ainda a definir comigo.
- Não inferir que todo gasto histórico de familiar continua em aberto.
- Validar comigo o tratamento das compras históricas já quitadas e das pendências existentes na data de corte.
5. Contas, salário e calendário
Conta bancária
- Nubank.
- Não haverá controle de dinheiro em espécie.
- Receitas extraordinárias, como reembolsos e benefícios extras, podem ocorrer.
- Para previsão de receitas, um salário fixo é suficiente inicialmente.
Salário e ciclo
- Recebimento no dia 5 do mês, não no quinto dia útil.
- Se não for dia útil, avançar para o próximo dia útil.
- Considerar feriados aplicáveis a Ribeirão Preto — SP, além de fins de semana.
- Orçamento e planejamento seguem o período entre salários.
- Valor do salário será configurado.
Cartão
- Fechamento informado como “a fatura vira todo dia 1”.
- Vencimento no dia 8, avançando para o próximo dia útil quando necessário.
- Confirmar a interpretação do fechamento nos limites de data com os documentos reais.
- Acompanhar cada parcela na respectiva fatura, com projeção das próximas.
- O CSV mostra apenas as parcelas da fatura exportada.
- Ao reconhecer uma compra parcelada pela primeira vez, solicitar confirmação de total de parcelas, responsáveis, divisões e vencimentos.
Flash
- Benefício separado do saldo bancário e do disponível geral.
- Crédito previsto de R$ 150 todo domingo.
- Uso destinado à alimentação.
- Importar histórico desde janeiro/2026, com saldo inicial validado.
- Conciliar créditos previstos com os efetivamente importados.
6. Compras compartilhadas e familiares
Uma compra pode ser:
- Integralmente minha.
- Integralmente de um terceiro.
- Dividida entre mim e uma ou mais pessoas.
A divisão deve permitir:
- Percentuais por participante, inclusive divisão igual.
- Valores definidos manualmente.
- Aplicação da divisão às parcelas da compra.
Validar comigo regras de arredondamento e diferenças de centavos.
Obrigações distintas
- A fatura do banco é integralmente uma obrigação minha.
- A parte atribuída a um familiar gera um valor a receber.
- Se o familiar atrasar, pago a fatura integralmente e continuo com o valor a receber em aberto.
- Pagar o banco não significa quitar a dívida do familiar.
- Não cobrar juros nem multa.
Recebimentos
- Podem ser totais, parciais ou cobrir várias compras.
- O app pode sugerir a pessoa pelo remetente do Pix.
- Eu confirmo se a entrada é pagamento de dívida e escolho sua distribuição.
- Não fazer quitação automática.
Exemplo obrigatório de funcionamento:
Um Pix de R$ 500 pode quitar uma compra de R$ 300, outra de R$ 150 e abater R$ 50 de uma compra de R$ 100. Restam R$ 50 em aberto nessa última compra.
Dívidas fora do cartão
- Registrar dinheiro que emprestei e despesas que familiares pagaram por mim.
- Manter cada empréstimo ou dívida identificado por sua origem.
- Não compensar automaticamente o que devo com o que tenho a receber da mesma pessoa.
- Vencimento padrão junto da fatura.
- Permitir prorrogação manual de dívida fora do cartão; costumo escolher uma nova data dentro do mês atual.
- Preservar histórico de vencimentos e pagamentos. Consultar-me sobre restrições de edição e prorrogação.
Perfil e demonstrativos
Perfil por pessoa com:
- Total a receber.
- Total que devo.
- Valores vencidos.
- Próximas parcelas.
- Histórico de pagamentos.
Compartilhamento:
- Resumo em texto das faturas atuais e vencidas, sem detalhar cada gasto.
- Demonstrativo detalhado de uma fatura específica quando necessário.
- Prévia obrigatória antes de compartilhar.
- Botão para compartilhar pelo WhatsApp usando o compartilhamento do Android.
- Não enviar mensagens automaticamente.
7. Orçamento, disponível e reservas
Orçamento
- Limites por categoria para cada ciclo salarial.
- Somente minha parte das compras compartilhadas consome meu orçamento.
- Prever o orçamento ainda necessário para despesas do ciclo.
Categorias sugeridas, ainda ajustáveis:
- Moradia.
- Alimentação.
- Transporte.
- Saúde.
- Pessoal e lazer.
- Educação.
- Presentes e doações.
- Tarifas e impostos.
Permitir categorias e subcategorias personalizadas.
Separar a finalidade do gasto de sua natureza financeira: transferência, aporte, resgate, empréstimo ou pagamento de fatura não devem ser confundidos com categorias de consumo.
Disponível para gastar
A visão principal deve considerar:
- Saldo bancário.
- Salário previsto no período.
- Contas e faturas a pagar.
- Reservas.
- Orçamento ainda necessário até o próximo salário.
Regra conservadora:
- Valores a receber de familiares não liberam dinheiro antes do efetivo recebimento.
- Investimentos ficam integralmente reservados.
- Flash não integra o dinheiro disponível geral.
- Evitar descontar a mesma despesa como orçamento, previsão, compra e fatura.
Antes de implementar, apresente a fórmula e exemplos numéricos para minha validação, incluindo o tratamento de saldo negativo e compromissos já realizados.
Reservas
- Já existe dinheiro guardado.
- Valor padrão fixo por ciclo, com alteração para mais ou menos em ciclos específicos.
- Reserva reduz o disponível mesmo que o dinheiro continue na conta ou nos investimentos.
- Evitar descontar duas vezes um investimento que também está associado a uma reserva.
Despesas previstas
Cadastrar frequência, valor previsto e vencimento, com ajuste pelo valor real:
- Aluguel.
- Energia.
- Internet.
- Gasolina.
- Troca de óleo.
- Futebol.
Não presumir que todas sejam mensais. Consultar-me sobre frequência e forma de previsão.
8. Patrimônio e meta
Investimentos Nubank
- Acompanhar saldo total e evolução, sem detalhar cada aplicação.
- Importar o documento PDF de saldo.
- Registrar aportes e resgates.
- Todos os investimentos são considerados reservados.
Bitcoin
- Registrar quantidade de BTC.
- Obter cotação automaticamente em reais por API gratuita.
- Registrar compras/aportes e vendas.
- Preservar precisão adequada à quantidade de BTC.
- Mostrar data da cotação.
FGTS
- Saldo inicial informado e correções manuais periódicas.
- Projeção com rendimento e depósitos mensais.
- Depósito previsto no dia 20, com valor editável.
- Opção para interromper os depósitos.
- Buscar automaticamente informações necessárias ao rendimento.
- Identificar claramente saldo estimado e data da última confirmação.
- Ao informar saldo real, estabelecer nova base para a projeção e preservar o histórico de estimativas e correções.
- Validar fontes e metodologia comigo; não aplicar silenciosamente uma taxa fixa ou uma interpretação incompleta das regras do FGTS.
Veículos
- Cadastro com os dados necessários para consulta FIPE.
- Inicialmente um veículo, permitindo adicionar outros e registrar vendas.
- Atualização FIPE sob demanda.
- Validar comigo o histórico de avaliações e como a venda afeta o patrimônio.
Vendas e movimentações
Para venda de Bitcoin ou veículo:
- Registrar data, valor recebido e eventuais custos.
- Vincular a entrada à movimentação importada da conta.
- Evitar duplicidade e classificação indevida como receita recorrente.
Aportes e resgates também devem evitar duplicidade entre conta e patrimônio.
Meta
- Objetivo atual: R$ 100 mil.
- Tela com opções de seleção para escolher quais componentes entram no cálculo.
- Intenção padrão: considerar patrimônio fora do disponível para gastar.
- Flash e valores a receber começam desmarcados.
- A seleção para a meta não deve alterar silenciosamente a disponibilidade financeira.
- Prazo da meta ainda não definido; consultar-me se necessário.
9. Telas e acesso
Indicadores prioritários da tela inicial:
1. Disponível até o próximo salário.
2. Orçamento restante por categoria.
3. Valores de familiares em aberto, destacando atrasados.
Demais capacidades exigidas:
- Contas e movimentações.
- Faturas e parcelas futuras.
- Pendências de importação e conciliação.
- Pessoas e dívidas.
- Orçamentos, recorrências e reservas.
- Patrimônio e meta.
- Configuração Google, backup e recuperação.
O desenho da navegação e das telas ainda precisa ser apresentado para minha decisão.
Segurança:
- Biometria para abrir o app.
- Alternativa pelo PIN ou senha de desbloqueio do próprio Android.
- Autorização da conta Google para Gmail e Drive.
- Não armazenar minha senha Google.
- Validar armazenamento de credenciais, proteção de backups e recuperação, submetendo escolhas à minha aprovação.
10. Próximas tarefas e ordem de trabalho
Etapa 1 — Validação antes da implementação
- Inspecionar o ambiente e a base antiga.
- Solicitar os CSVs e o PDF reais ainda não fornecidos.
- Validar Gmail, Drive e fontes externas gratuitas.
- Verificar formatos, campos disponíveis e limites de importação.
- Apresentar conclusões com evidências e impedimentos.
- Consolidar comigo as decisões pendentes, a stack, o modelo financeiro e a primeira entrega.
Não contornar falhas alterando requisitos sem consulta.
Etapa 2 — Primeiro APK utilizável
Sequência proposta, a confirmar comigo:
- Estrutura Android, armazenamento local e desbloqueio.
- Importação e revisão de documentos.
- Contas, categorias, cartão e parcelas.
- Responsáveis e divisão de compras.
- Dívidas e alocação de pagamentos parciais.
Etapa 3 — Planejamento
- Ciclo salarial e dias úteis.
- Receitas e despesas previstas.
- Orçamentos e reservas.
- Cálculo validado do disponível.
- Painel inicial.
Etapa 4 — Complementos e recuperação
- Patrimônio, FGTS, Bitcoin e veículos.
- Meta configurável.
- Demonstrativos e compartilhamento.
- Backup e restauração, definidos desde a arquitetura e testados antes do uso real.
11. Critérios mínimos de validação
Preparar testes significativos para:
- Reimportação e extratos sobrepostos sem duplicação.
- Conciliação de previsão ou lançamento manual com o extrato.
- Parcelamento importado em meses sucessivos sem recriar compras.
- Divisão percentual e por valor, incluindo centavos.
- Pagamento da fatura independente do recebimento de familiares.
- Pagamento parcial e distribuição de um Pix entre várias dívidas.
- Valores a pagar e a receber da mesma pessoa mantidos separados.
- Fatura, orçamento e reservas sem dupla contagem.
- Flash separado do dinheiro disponível.
- Salário e fatura deslocados por fins de semana e feriados.
- Virada de ciclo com compromissos futuros.
- Aportes, resgates e vendas sem duplicar movimentações.
- Correção do FGTS preservando histórico.
- Meta respeitando os componentes selecionados.
- Falha de API mantendo a última informação com data visível, sem inventar atualização.
- Cancelamento de autorização Google e recuperação de acesso.
- Backup e restauração em instalação limpa.
- Instalação e atualização do APK preservando os dados.
Comece pelas validações e por um relatório das decisões pendentes. Consulte-me em cada nova decisão e aguarde minha resposta antes de executar a alternativa escolhida. O objetivo é resolver essas pendências e então iniciar o desenvolvimento, preservando todas as decisões acima.