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


## Correção da cotação — 0.9.2

A leitura passa a aceitar timestamps em segundos, milissegundos, microssegundos e nanossegundos. A validação usa a data HTTP do servidor quando disponível, incluindo Age e rejeitando cache HTTP de cinco minutos ou mais, para não depender do relógio do aparelho. Valores decimais com zeros adicionais são aceitos sem conversão para ponto flutuante.

O Mercado Bitcoin continua como fonte principal; falha de rede, HTTP, JSON, par ou data provoca tentativa automática no endpoint público `https://api.coinbase.com/v2/prices/BTC-BRL/spot`. A resposta precisa informar BRL (e BTC quando o campo base estiver presente). A [documentação de preços da Coinbase](https://main.d39g7xymw2r1c0.amplifyapp.com/sign-in-with-coinbase/docs/api-prices) descreve esse endpoint sem autenticação. Como o spot não fornece data da negociação, sua avaliação registra a hora da consulta HTTP. O histórico exibe a fonte que efetivamente respondeu. Nenhuma quantidade é enviada ou recalculada: a avaliação continua sendo preço em reais multiplicado pelos satoshis cadastrados.

O botão permite nova tentativa após falhas, sem o bloqueio silencioso de um minuto. Erros de cotação ficam separados dos erros dos formulários e identificam as fontes que falharam (HTTP, DNS, timeout, TLS ou resposta inválida). Quando ainda não há avaliação, o aviso não afirma que existe uma cotação anterior.

Os testes usam transporte controlado e JSON real para verificar failover, schemas, unidades de timestamp, relógio do celular incorreto, cache expirado, moeda errada e a avaliação exata de uma quantidade de BTC. Acesso real aos endpoints foi tentado, mas o proxy deste ambiente retornou 403 Forbidden antes de conectar aos três hosts consultados; isso não identifica a causa no aparelho. Validação de rede no S23 ainda é necessária no novo candidato.

## Redirecionamento confirmado no aparelho — 0.9.3

A captura do S23 confirmou HTTP 302 do Mercado Bitcoin. A versão anterior desabilitava redirecionamentos e não tratava Location, portanto interrompia a consulta antes de chegar ao destino. O transporte agora segue 301/302/303/307/308 por até três redirecionamentos, incluindo Location relativo, fechando cada conexão. Aceita somente HTTPS e os domínios oficiais do respectivo serviço; rejeita ciclos, destino ausente, HTTP, credenciais no URL, portas não padrão e domínios externos.

Se a v4 continuar indisponível, consulta também `https://www.mercadobitcoin.net/api/BTC/ticker/`. O parser reconhece tanto a lista v4 quanto o objeto público `ticker`, mantendo os mesmos critérios de valor e data. A Coinbase permanece como alternativa posterior. Sua mensagem genérica não permitia concluir a causa a partir da captura: agora erros de JSON, HTML e cache expirado são distinguidos, sem expor o corpo remoto nem afrouxar os critérios de moeda ou de validade.

Os testes exercitam o mesmo transporte usado em produção com conexões HTTP controladas: 302 → 200 → leitura do preço, redirecionamento entre hosts oficiais, retorno pelo ticker público, ciclos, limite de saltos, destinos inválidos, HTML e cache expirado. Isso cobre a falha observada no celular, além dos testes existentes de cálculo e precisão. As conexões dos testes são controladas; não representam consulta ao vivo aos provedores, ainda bloqueada pelo proxy deste ambiente. A correção exige um novo candidato para validação no aparelho.

## Datas brasileiras — 0.9.4

Datas completas exibidas e digitadas usam DD/MM/AAAA em todo o aplicativo: movimentações, revisão de faturas e custódia, patrimônio, previsões, reservas, parcelas, pessoas, histórico e demonstrativos compartilhados. Cotações e backups mantêm o horário, com DD/MM/AAAA HH:mm no fuso do aparelho. Referências que representam somente um mês usam MM/AAAA. Os rótulos dos campos permanecem curtos.

A leitura dos campos é estrita: 31/04 e 29/02 em ano não bissexto são rejeitados, sem ajuste silencioso e sem inversão de dia e mês. A formatação é centralizada em AppDates. Os formatos ISO do banco, backups, arquivos importados e chaves de referência permanecem compatíveis; nenhuma migração ou regravação de registros existentes foi feita. O nome original de anexos e textos importados não é reescrito.

Validar no candidato: cadastrar um patrimônio com 03/11/2026, fechar e reabrir; revisar datas de parcelas, salário, fatura, previsões e FGTS; conferir cotações e backups com horário local; compartilhar um demonstrativo e buscar uma movimentação por DD/MM/AAAA. Tentar 31/04/2026 e conferir a rejeição. Os testes cobrem entrada, ano bissexto, validação estrita, persistência ISO seguida de leitura brasileira e mudança de dia no fuso America/Sao_Paulo.

## Disponibilidade e cache do provedor — 0.9.5

A verificação das Releases confirmou que o candidato 0.9.4 foi publicado. O usuário informou o diagnóstico atual: redirecionamento sem destino na v4, HTTP 502 no ticker público e resposta inválida na Coinbase. A sequência é reproduzida em teste controlado, incluindo um preço Coinbase com mais de 18 casas decimais, que o limite anterior rejeitava. O cálculo aceita a precisão decimal da resposta e arredonda somente o saldo final em centavos; texto e expoentes continuam limitados para evitar alocações excessivas.

Foi identificado um problema independente no código: Age HTTP >= 300 descartava qualquer preço antes de ler o JSON, confundindo cache do provedor com o prazo local de retomada de tela. Agora a resposta mantém dois horários: o horário de origem do preço e o horário do servidor somado a Age. O preço em cache não recebe uma data nova artificialmente. As fontes continuam sendo consultadas em busca de uma cotação recente; se nenhuma fornecer uma, uma referência válida de até 24 horas pode calcular o saldo estimado, com aviso de cotação antiga e data real. Referências anteriores à avaliação já salva não a substituem. Dados acima de 24 horas, moedas erradas e preços inválidos continuam sendo rejeitados. O prazo de cinco minutos do cache de retomada permanece o mesmo.

Acrescenta o endpoint público de conversão da Coinbase, `https://api.coinbase.com/v2/exchange-rates?currency=BTC`, como alternativa ao spot. A resposta precisa declarar base BTC e conter rates.BRL: não se aplica uma taxa de USD como se fosse Bitcoin. [Documentação oficial](https://main.d39g7xymw2r1c0.amplifyapp.com/sign-in-with-coinbase/docs/api-exchange-rates). O corpo das conversões é limitado a 256 KB; os demais endpoints continuam limitados a 64 KB. JSON válido com Content-Type incorreto pode ser lido, mas HTML e esquemas inválidos continuam rejeitados. HTTPS e os limites de redirecionamento são mantidos.

Testes adicionais reproduzem uma resposta em cache de 15 minutos que antes falhava, conferem valor e quantidade preservada, preferência por fonte recente, conversão BTC/BRL, rejeição de USD, proteção da avaliação salva e JSON rotulado incorretamente. Esses testes são controlados e não comprovam uma consulta real no S23. As APIs continuam bloqueadas neste ambiente; a confirmação da resposta ao vivo e do saldo depende da validação no aparelho.

A validação também trata base/pair opcionais com valor JSON null como metadado ausente, sem confundir a string literal "null" com uma moeda. Os testes de JSON usam a implementação Android (android-json), porque optString difere da biblioteca JSON-java no tratamento de JSONObject.NULL. A regressão verifica essa semântica e uma resposta Coinbase com base null e preço de alta precisão; moeda BRL continua obrigatória. Essa mudança é somente da dependência de testes, sem adicionar bibliotecas ao APK.

## Ação de cotação no cabeçalho — 0.9.6

Remove o parágrafo explicativo dos provedores e o botão extenso acima dos cartões. A atualização agora fica à direita do título Patrimônio, em uma cápsula BTC com ícone de atualização, fundo ciano translúcido e contorno suave. Durante a consulta, um indicador ocupa o mesmo espaço do ícone e o botão fica desabilitado, sem mudar de largura. A descrição de acessibilidade informa a ação completa e a área de toque tem pelo menos 48 dp. O título pode quebrar linha com fonte ampliada. Validar no candidato o alinhamento, o carregamento e o toque para atualização manual; mensagens de erro e de referência antiga continuam sendo mostradas quando necessárias.

## Cadastro de patrimônio — 0.9.7

- Formulário com superfície azul escura, contorno ciano, hierarquia de texto e campos discretos.
- Tipos visíveis em duas colunas, com destaque e semântica de seleção; FGTS e Bitcoin existentes continuam indisponíveis para novo cadastro.
- Ação principal em largura total, estado de salvamento e cancelamento separado. Conteúdo rolável para telas menores e teclado aberto.
- Preservados o rascunho durante bloqueio automático, os nomes implícitos e a data DD/MM/AAAA.
- Roteiro no aparelho: abrir Adicionar patrimônio, alternar os tipos, conferir nome implícito, abrir teclado e alcançar os campos e ações; cadastrar e conferir o saldo. Validar também com fonte ampliada e após bloqueio automático.
