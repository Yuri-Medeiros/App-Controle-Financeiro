# Faturas, previsão automática e títulos — 0.31.0

## Cabeçalho de Faturas

Com fatura selecionada, mês e ano ocupam o cabeçalho antes usado por Faturas. O próprio cabeçalho abre o seletor de referência. O vencimento aparece apenas como data, logo abaixo do total. A descrição Em aberto e o botão Registrar pagamento foram removidos. Pagamentos são obtidos dos arquivos importados e conciliados com o extrato. Dividir pagamentos continua acessível para ratear valores já importados; não cria pagamentos manuais.

## Edição de títulos

Um componente comum atende o título do lançamento, o nome do investimento em inclusão e o nome do veículo em inclusão/edição. Títulos fixos de tipos e seções permanecem fixos.

Em repouso, o título tem um lápis pequeno cinza próximo ao texto. Tocar no título/lápis abre o teclado com seleção integral. A edição usa o mesmo estilo tipográfico, com sublinhado fino ciano e sem caixa de input. Cancelar e Concluir ficam em barra compacta acima do teclado, posicionada no rodapé visível da janela. Não há checkmark no cabeçalho.

Cancelar preserva o valor anterior. Concluir atualiza somente o rascunho do formulário, fecha o teclado e retorna ao estado de leitura. O botão principal fica bloqueado durante a edição do título. A persistência depende de Salvar/Vincular/Adicionar no formulário. A descrição original do lançamento continua menor abaixo do título. Textos longos ficam limitados visualmente no estado de leitura, sem truncar os dados armazenados.

## Parcelas futuras

A descrição original n/total, junto da referência da fatura, gera as próximas parcelas imediatamente após importar. A previsão é derivada das fontes reais: tem movimento ID zero, é marcada Prevista e não fabrica CSVs nem datas de compra. Não cria parcelas anteriores à primeira numeração observada. Parcelas reais já importadas substituem previsões derivadas correspondentes, sem multiplicar as projeções pela quantidade de arquivos.

CSV não informa responsáveis. Sem rateio confirmado, a previsão fica em A revisar. Ao definir a divisão da compra, o plano confirmado passa a fornecer as próximas parcelas e seus valores por pessoa, substituindo a previsão ainda sem responsável. As obrigações do planejamento também incluem essas previsões automáticas.

Na importação seguinte, o vínculo a plano confirmado é automático somente quando há correspondência única entre fonte e previsão: mesma conta, descrição original normalizada sem a numeração, total de parcelas, número atual, referência e valor. Duas fontes disputando a mesma previsão, dois planos possíveis, outros valores ou descrições diferentes continuam em revisão. Não há tentativa de identificar compra somente pelo título editado ou pelo valor.

A mesma verificação roda depois de confirmar uma compra, permitindo ligar parcelas reais que já tenham chegado antes da atribuição dos responsáveis. O vínculo usa o evento Link existente: preserva rateios, saldos de abertura, recebimentos e estornos. A importação e os vínculos são transacionais. Reimportar o arquivo não duplica movimentações nem eventos.

Compras à vista, créditos, reembolsos, registros ignorados e conflitos não criam novas previsões automáticas. A separação por conta e início da série evita juntar compras de ciclos diferentes. Ocorrências idênticas são contadas com multiplicidade, e os vínculos ambíguos exigem revisão. Não há mudança de schema ou reescrita do diário antigo. Backup preserva fontes, planos e vínculos; o reset retira suas projeções junto com as faturas.

## Validação

Testes JVM cobrem provisionamento imediato, entrada começando em parcela intermediária, importações repetidas da mesma série, compras duplicadas, adoção do rateio confirmado, vínculo único idempotente e rejeição de coincidências ambíguas, contas, descrições ou valores diferentes. O teste integrado cifrado inclui importação, confirmação de rateio, vínculo automático, backup/restauração e não repetição de eventos. O pacote Android é compilado, sem execução no aparelho.

Conferir no Android: barra de edição sobre teclado Samsung/Gboard, seleção integral, Cancelar/Concluir, gesto Voltar, letras grandes, título do investimento e veículo, seletor de fatura no cabeçalho, vencimento abaixo do valor e projeções antes/depois de confirmar os responsáveis. Conferir também o funcionamento do desfoque nos diálogos e a conciliação de pagamentos importados.
