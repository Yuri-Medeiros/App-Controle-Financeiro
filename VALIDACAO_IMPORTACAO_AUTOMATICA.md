# Importação automática e notificação temporária — 0.21.0

A referência de cartão é obtida automaticamente do nome Nubank_AAAA-MM-DD.csv, sem confirmação no fluxo normal. A data válida do arquivo tem prioridade sobre um fallback manual. A referência não é ajustada para dia útil nem substituída pela data das movimentações: o vencimento bancário continua sendo um conceito distinto. Arquivos cujo nome não fornece uma data válida não recebem uma referência inventada e mantêm o tratamento excepcional de revisão. Conta e Flash não recebem referência de cartão.

O repositório aplica a referência às linhas e ao índice do documento na mesma transação da importação. Reimportações continuam deduplicadas, inclusive após redefinir faturas. O novo comportamento vale para novas importações; não altera a referência dos documentos já persistidos.

A notificação deixa de ter o botão X. Some após aproximadamente quatro segundos, respeitando o tempo recomendado pelas configurações de acessibilidade do Android. Enquanto o usuário arrasta a notificação, o temporizador pausa; após soltar sem dispensá-la, recomeça. Arrastar para esquerda/direita ao menos um quarto da largura (mínimo de 48 dp), ou para cima ao menos 48 dp, dispensa o aviso. Deslocamentos menores retornam suavemente à posição original. Arrastar para baixo não dispensa. Há ação semântica de dispensar para acessibilidade, sem botão visível.

Cada notificação tem identidade própria na composição. Um novo resultado começa com posição e temporizador novos; callbacks de dispensa usam a identidade do aviso, evitando remover um resultado mais recente por engano. A dispensa só remove o aviso, sem cancelar importação ou revisão de PDFs/referências excepcionais. Não são criadas notificações do sistema nem novas permissões.

Inclui os ajustes locais anteriores de Faturas: estado vazio sem descrições e sem atalho para Movimentações, botão Atualizar, atualização antes da informação, redefinição oculta sem faturas e popover de informação alinhado à direita abaixo do ícone, com textos reduzidos e ações alinhadas à margem esquerda. A troca de fatura usa o seletor do mês, sem setas de anterior/próxima. O alinhamento novo é optativo no componente compartilhado e foi aplicado somente em Faturas.

## Conferência no aparelho

1. Importar Nubank_2026-12-08.csv: deve entrar diretamente em 08/12/2026 sem pedir confirmação. Conferir que a data das compras e a regra de vencimento não mudaram.
2. Atualizar novamente e conferir que não surgem duplicatas. Após redefinir faturas, atualizar e conferir que os arquivos podem ser reimportados automaticamente, mantendo os lançamentos da conta.
3. Conferir importações de conta, Flash e PDF de custódia, que conservam suas regras próprias.
4. Aguardar o aviso sem tocar: deve sumir sozinho em cerca de quatro segundos, ou no tempo de acessibilidade configurado. Não deve haver X.
5. Dispensar para cada lado e para cima. Testar arraste pequeno e para baixo: devem manter o aviso e retornar à posição inicial.
6. Fazer nova importação enquanto outro aviso está visível e durante um arraste. O resultado novo não deve herdar a posição/temporizador anterior nem ser dispensado por callback antigo.
7. Conferir bloqueio/desbloqueio, fonte ampliada, leitor de tela e modais de revisão; os avisos não devem interferir nos registros financeiros.

Os testes JVM cobrem a prioridade da referência do nome, preservação das datas, distinção de conta/Flash e critérios de dispensa dos gestos. O teste Android foi ajustado para importar e reimportar o CSV pelo nome, sem referência manual; sua compilação não comprova execução no aparelho. Sem alteração de schema ou identidades de assinatura. Alterações permanecem locais até autorização para envio à PR.
