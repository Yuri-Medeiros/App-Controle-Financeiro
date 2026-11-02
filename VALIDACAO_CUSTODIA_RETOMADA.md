# Retomada, Bitcoin e custódia Nubank — 0.9.0

O bloqueio de autenticação continua ocorrendo após um minuto fora do app, fechando o banco e removendo tokens e senha de recuperação. A tela, seleção e formulários ficam em memória para retomada após autenticação por até cinco minutos contados da saída do app. O bloqueio manual descarta a retomada. Não há persistência de rascunhos em disco nem retomada após encerramento do processo. Diálogos e menus são ocultados, o teclado é recolhido e o conteúdo financeiro não fica acessível na tela bloqueada. Operações interrompidas não são reenviadas automaticamente: a retomada preserva a revisão, evitando repetir gravações.

FGTS e Bitcoin têm nome implícito e cadastro único, validados também ao gravar. Cadastros e confirmações antigos continuam legíveis no histórico. Novas confirmações diretas de saldo desses tipos são bloqueadas. Bitcoin usa os aportes/resgates vinculados ao extrato e quantidades efetivas em satoshis; FGTS possui registro manual de aportes e saques sem fabricar movimentação bancária. Saques que excedam o saldo são rejeitados. O saldo inicial continua sendo informado no cadastro.

A consulta pública BTC/BRL usa `https://api.mercadobitcoin.net/api/v4/tickers?symbols=BTC-BRL`, sem chave ou conta, conforme [documentação oficial](https://api.mercadobitcoin.net/api/v4/docs). Ela ocorre ao abrir Patrimônio, a cada cinco minutos enquanto a tela está ativa e pelo botão de atualização, com intervalo mínimo de um minuto entre tentativas. A consulta envia somente o par; nenhuma quantidade ou dado pessoal. Preço, par, limites e data são validados. Falhas preservam a última avaliação e sua data, com aviso. Uma avaliação antiga não é anunciada como cotação atual; não há compra/venda de Bitcoin pela API.

O Gmail reconhece o remetente `todomundo@nubank.com.br` e o assunto `Seu extrato de custódia de investimentos chegou`, processando anexos PDF de até 10 MB e 30 páginas com o extrator nativo do Android 15. Não há envio do PDF a terceiros. O arquivo temporário é removido em `finally`. PDF sem texto, protegido ou com layout não reconhecido falha sem gravar posições.

A prévia mostra descrição, bruto e líquido por investimento, IR/IOF, taxa B3 e quantidade quando presentes. O patrimônio usa o líquido estimado, incluindo a taxa B3 do Tesouro; os ETFs são brutos de IR como no extrato. Os seis valores do documento fornecido reconciliam R$ 36.450,24 brutos e R$ 35.997,68 líquidos. A confirmação grava o conjunto em transação e não cria entradas/saídas bancárias. Identificadores estáveis por produto tornam a reimportação idempotente; extratos posteriores atualizam a mesma posição e anteriores não sobrescrevem saldos. Cadastros manuais anteriores não são associados automaticamente pelo nome: revise-os para não somar o mesmo investimento duas vezes.

A fixture de teste contém somente as tabelas, sem CPF, endereço, e-mail, nome do titular ou conta. O PDF e os screenshots pessoais não entram no repositório.

## Roteiro no candidato

1. Iniciar um formulário e preencher parcialmente; sair por mais de um minuto e menos de cinco. Autenticar e verificar tela e preenchimento. Repetir com menu, categorização em lote e revisão de importação. Conferir ausência de conteúdo e de diálogos na tela bloqueada.
2. Sair por cinco minutos ou usar Bloquear agora; conferir descarte dos rascunhos. Encerrar o processo e conferir nova autenticação sem retomada. Verificar que senha de backup não reaparece.
3. Adicionar FGTS e Bitcoin e tentar duplicar. Conferir nome automático, ausência de edição direta de saldo, aportes/saques e rejeição de saldo negativo. Reabrir o app e conferir histórico. Testar backup/restauração incluindo dois lançamentos manuais de FGTS.
4. Abrir Patrimônio conectado e atualizar Bitcoin. Verificar valor, quantidade preservada, fonte/data; repetir sem rede e verificar aviso e preservação da última avaliação.
5. Autorizar Gmail, revisar o PDF de custódia e confirmar seis posições. Conferir os totais, atualizar novamente e conferir ausência de duplicações. Repetir com PDF incompleto e extrato anterior. A extração nativa, OAuth, lifecycle e instalação exigem teste real no Android; testes JVM não os comprovam.

Nenhuma alteração no esquema Room ou nas identidades de assinatura. Publicação definitiva depende do teste e aprovação do candidato conforme PIPELINE.md.
