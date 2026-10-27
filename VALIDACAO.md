# Validação preliminar do aplicativo financeiro

Data: 27/10/2026. Nenhuma implementação iniciada; nenhuma alternativa adotada. As informações da base antiga no pedido são antecedentes fornecidos pelo usuário, não resultados revalidados.

Atualização posterior nesta mesma data: o usuário aprovou aprofundar Parallelum gratuita e preparar desenvolvimento/compilação exclusivamente no Codex Cloud. Ferramentas instaladas e APK técnico gerado e verificado; ver [estado atual do ambiente](CODEX_CLOUD.md) e [decisões aprovadas](DECISOES.md). As observações abaixo registram a inspeção inicial e devem ser lidas com essa atualização. Nenhum produto foi implementado.

## Parecer

A stack proposta é compatível, em princípio, com um app Android local de um usuário, distribuído por APK, sem servidor. A viabilidade integral ainda não está comprovada: faltam arquivos reais, autorização Google no aparelho, acesso de rede do executor e prova de compilação/instalação. Não é possível assegurar hoje todos os requisitos a R$ 0.

| Item | Evidência obtida | Situação |
|---|---|---|
| Kotlin, Compose, Room e BiometricPrompt | Compatibilidade conceitual com Android nativo e banco local | Proposta mantida para confirmação; versões e Android mínimo não escolhidos |
| Gmail | Documentação classifica gmail.readonly como restrito e prevê exceção de verificação para uso pessoal | Viável documentalmente, sem teste com a conta |
| Drive appDataFolder | Área privada acessível pelo app e escopo específico documentados | Upload, restauração e espaço disponível não testados |
| Bitcoin | Mercado Bitcoin documenta dados públicos; tentativa de ticker não trouxe cotação | Fonte candidata, não validada nem escolhida |
| FIPE | Fundação declara expressamente não disponibilizar API; Parallelum respondeu JSON de marcas pela ferramenta web | API oficial indisponível; alternativa de terceiro exige decisão |
| FGTS | BCB respondeu dados de IPCA e série candidata de TR pela ferramenta web; STF documenta fórmula com piso anual | Fontes parciais acessíveis; metodologia não aprovada |
| Arquivos | Só o texto do pedido está disponível | Importadores não validáveis ainda |
| APK | Java 21 disponível; demais ferramentas ausentes no PATH e SDK não encontrado nos locais usuais | Compilação, assinatura e instalação pendentes |

## Ambiente e limites dos testes

Pasta de projeto: `/workspace/App-Controle-Financeiro`; `git ls-files` não retornou arquivos. Nenhum AGENTS.md localizado nas buscas em /workspace e /tmp. O XLSX no caminho antigo não foi encontrado; a busca também não localizou outro XLSX nessas pastas.

`java -version`: OpenJDK 21.0.12.1. Não encontrados no PATH: javac, gradle, kotlinc, sdkmanager, adb, apksigner e emulator. Não foram instalados componentes nem aceitas licenças.

O ambiente informa rede restrita e política aplicada, sem credenciais, identidades externas ou variáveis de integração configuradas. Os hosts de Google APIs e das fontes financeiras não estão na lista permitida. Tentativas HTTPS pelo Python para Mercado Bitcoin, Parallelum, BCB e até o catálogo Android em dl.google.com falharam com `Operation not permitted`, sem resposta HTTP. Isso comprova um impedimento deste executor, não indisponibilidade das fontes. Não houve tentativa de contornar a política.

A ferramenta web conseguiu consultar documentação e retornar JSON de algumas fontes. Isso não comprova acesso pelo futuro APK nem pelo executor de compilação.

## Google: autorização e backup

Para ler mensagens/anexos, a proposta de menor acesso funcional é `gmail.readonly`. Ele permite leitura ampla da caixa; filtros do aplicativo não restringem a permissão concedida pelo Google. Não há aprovação para esse escopo ainda.

Uso exclusivamente pessoal pode dispensar a verificação de escopos restritos, com aviso de app não verificado e limite de usuários. Em modo Testing, o consentimento expira em sete dias; eventual refresh token também expira. Uma opção é Testing durante a validação e depois In Production para uso pessoal sob a exceção documentada, sem confundir publicação OAuth com publicação na Play Store. Outra é permanecer em Testing e aceitar reautorizações. Recomendação: validar a conta em Testing e decidir a transição após a prova real. Não há garantia de autorização para contas com políticas de organização ou Proteção Avançada.

No Android, a documentação recomenda AuthorizationClient para acesso aos dados Google e exige cliente Android com pacote e SHA-1 de assinatura. É candidato coerente com processamento sob demanda sem backend. Não foi criado projeto Cloud, cliente ou chave. O teste futuro deve cobrir concessão, anexos reais, novo token em outra sessão, revogação e recuperação. Não solicitar senha Google nem colocar segredo de cliente no APK.

O Drive oferece appDataFolder e escopo `drive.appdata`, sem necessidade de acesso geral ao Drive. Pasta privada não equivale a backup criptografado pelo aplicativo. O uso padrão da API é documentado como sem custo adicional, mas a documentação atual anuncia mudanças para excedentes em 2026. Limites publicados: 1.000.000 unidades/minuto/projeto, 325.000/minuto/usuário/projeto e limiar diário de 400.000.000 unidades; conferir os valores efetivos no projeto. O espaço de armazenamento da conta continua sendo uma restrição separada. Não habilitar faturamento para resolver limites.

Precisam de decisão: frequência/retenção de backups, inclusão de anexos, criptografia e recuperação da chave. Chave apenas no Android Keystore não basta para recuperar em instalação limpa após perda do aparelho. Alternativas a apresentar em detalhe: proteção padrão do serviço ou criptografia adicional com segredo de recuperação mantido pelo usuário. Nenhuma escolhida.

Fontes:
- [Escopos Gmail](https://developers.google.com/workspace/gmail/api/auth/scopes)
- [Exceção de uso pessoal](https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification)
- [Testing e produção](https://support.google.com/cloud/answer/15549945?hl=en)
- [Autorização no Android](https://developer.android.com/identity/authorization)
- [Área privada do Drive](https://developers.google.com/workspace/drive/api/guides/appdata)
- [Limites e preços do Drive](https://developers.google.com/workspace/drive/api/guides/limits)

## Fontes financeiras

**Bitcoin:** Mercado Bitcoin é candidato por oferecer dados públicos BTC/BRL. O endpoint `https://api.mercadobitcoin.net/api/v4/tickers?symbols=BTC-BRL` não pôde ser lido pela ferramenta web e falhou no executor. A tentativa de BitPreço pela web também falhou; não é uma substituição escolhida. Falta testar preço, timestamp, limites e resposta a falhas. É necessário decidir se a avaliação usa último negócio ou outro preço; uma cotação de corretora não representa preço universal de venda. [Documentação do provedor](https://api.mercadobitcoin.net/).

**FIPE:** o [site oficial](https://www.fipe.org.br/pt-br/indices/veiculos) declara que não oferece API. A [Parallelum](https://fipe.api.br/docs/comece-aqui) informa acesso sem autenticação limitado a 500 requisições/dia. A consulta web a `https://fipe.parallelum.com.br/api/v2/cars/brands` devolveu marcas em JSON. Isso é teste parcial: ainda falta buscar o veículo real, preço e mês de referência. Opções: API não oficial gratuita, sujeita a limites e continuidade, ou consulta oficial manual com registro do valor, que altera a automação desejada. Recomendação para sua avaliação: testar Parallelum, sem contratar plano; não usar endpoints internos da Fundação como API pública presumida.

**FGTS:** o [STF](https://noticias.stf.jus.br/postsnoticias/saldos-do-fgts-devem-ser-corrigidos-no-minimo-pelo-indice-da-inflacao-reafirma-stf/) reafirma TR + 3% ao ano + distribuição de resultados, com piso do IPCA por exercício e compensação pelo órgão gestor. Não equivale a aplicar simplesmente IPCA mensal ou uma taxa fixa.

A consulta web à [série 433 do BCB](https://api.bcb.gov.br/dados/serie/bcdata.sgs.433/dados/ultimos/1?formato=json) retornou data 01/09/2026 e valor 0,82; a [série candidata 226](https://api.bcb.gov.br/dados/serie/bcdata.sgs.226/dados/ultimos/1?formato=json) retornou data 23/10/2026, dataFim 23/11/2026 e valor 0,0959. Ainda é necessário validar metadados, unidades e correspondência da TR ao aniversário da conta. A consulta direta à página de rendimentos da Caixa encontrou loop de redirecionamento na ferramenta web.

Informação futura de TR, inflação e distribuição de resultados não está toda disponível antecipadamente. Alternativas metodológicas para aprovação: projeção com hipóteses explícitas e componentes conhecidos, ou horizonte limitado aos índices já publicados. Recomendação: separar saldo real, estimativa com dados observados e projeção com hipóteses; nenhum algoritmo foi escolhido. O depósito no dia 20 permanece como premissa pessoal informada, não como comprovação do crédito real.

## Arquivos e informações necessários

Reenviar o XLSX antigo e amostras originais de: extrato Nubank CSV, fatura Nubank CSV, extrato Flash CSV e PDF do saldo de investimentos. Para avaliar duplicação e parcelas, incluir dois períodos sobrepostos de conta/Flash e duas faturas consecutivas, preferencialmente com a mesma compra parcelada. Para fechamento, exemplos próximos aos dias 1 e 8. As amostras validam estrutura; a migração completa exigirá os arquivos desde janeiro/2026.

Pode ocultar dados pessoais, mantendo cabeçalhos, datas, sinais, formato de números e identificadores consistentes entre arquivos. Não enviar senha Google, tokens ou chave de assinatura. Se o PDF tiver senha, informar primeiro apenas que existe proteção.

Campos a investigar, sem pressupor presença: identificador bancário, data efetiva, descrição, valor/sinal, saldo, origem/destino de Pix, período da fatura, número/total de parcelas, estornos, codificação, separador e saldo/data do PDF. Falta saber se o PDF contém texto selecionável ou imagens; biblioteca/OCR só será proposta após inspeção.

Também faltam modelo/versão Android do aparelho e confirmação de Google Play services, existência de projeto OAuth anterior reutilizável e meios disponíveis para instalar/testar APK. A propriedade e guarda da chave de assinatura precisam ser definidas para futuras atualizações preservarem dados.

## Registro de decisões pendentes

Todas exigem consulta antes de execução; esta tabela não representa aprovação nem prioridade nova.

| Área | Decisão necessária / contexto |
|---|---|
| Stack e primeira entrega | Confirmar stack após validação e sequência das etapas 2–4; versões, Android mínimo e bibliotecas ainda abertos |
| Ambiente | Preparar ferramentas neste ambiente ou compilar em máquina Android local; recomendação: avaliar este ambiente primeiro, se acesso/licenças forem autorizados |
| Rede | Liberar os hosts necessários no fluxo de configuração ou executar os testes em ambiente permitido; nenhuma troca silenciosa de integração |
| OAuth | Projeto novo ou existente, conta pessoal/Workspace, escopos e Testing versus produção pessoal |
| Segurança | Proteção local, credenciais, criptografia do backup, guarda de chaves e recuperação sem aparelho antigo |
| Importação | Critérios de classificação, identidade de registros, duplicação exata versus operações iguais legítimas, sobreposição, estornos e conciliação de manuais/previsões |
| Migração | Vínculos reais da base antiga, tratamento de compras quitadas, pendências na data de corte e data inicial da conciliação familiar |
| Cartão | Significado exato da virada no dia 1, limites de data, vínculo entre parcelas e faturas, critérios de previsão |
| Divisões | Arredondamento, distribuição de centavos e efeito de mudanças em parcelas futuras/já pagas |
| Dívidas | Edição, prorrogação, distribuição manual de pagamentos e histórico; não compensar obrigações opostas |
| Planejamento | Frequência/forma de previsão de cada despesa, calendário oficial de feriados e dias úteis aplicáveis |
| Disponível | Horizonte, inclusão temporal do salário, compromissos futuros, saldo negativo, orçamento e reservas sem sobreposição |
| Patrimônio | Fonte e preço BTC, FIPE não oficial, histórico de avaliações/vendas, metodologia FGTS e precisão |
| Meta | Componentes iniciais além dos já desmarcados; prazo apenas se necessário à projeção |
| Interface | Navegação, telas e revisões; indicadores iniciais já definidos são preservados |
| Configuração pessoal | Salário, saldos iniciais, reservas, pessoas, recorrências, valores e veículo |

O exemplo de Pix de R$ 500 continua obrigatório: alocar 300 + 150 + 50, restando R$ 50 da terceira compra; pagamento ao banco é independente. Nenhuma regra automática de quitação será introduzida.

## Cálculo do disponível: rascunho para discussão, não aprovado

Há uma ambiguidade de horizonte: “até o próximo salário” pode terminar antes do crédito, enquanto o pedido também inclui salário previsto no período. Incluir esse salário no total de hoje pode ocultar falta de caixa antes dele. É preciso escolher os limites do ciclo e se a interface mostra, além do total, o menor saldo projetado por data.

Estrutura candidata para validação: disponível = saldo bancário atual + entradas previstas dentro do horizonte aprovado − obrigações ainda não pagas dentro desse horizonte − reservas que comprometem o saldo bancário − orçamento futuro ainda necessário e não coberto pelas obrigações. Cada obrigação deve pertencer uma única vez ao cálculo; compras já na fatura não são outra saída. Valores a receber não entram antes do recebimento; Flash e investimentos ficam fora do saldo bancário utilizado.

Exemplo hipotético sem salário futuro no horizonte: saldo 4.000, fatura 1.200 (incluindo 300 de familiar), contas 800, reserva na conta 500 e orçamento futuro adicional 600 → disponível 900. A parte de familiar não reduz a fatura devida ao banco. Uma compra de 200 já incluída nos 1.200 não é subtraída novamente; um pagamento já refletido no saldo também não.

No mesmo exemplo, trocar saldo por 2.500 resulta em −600. Proposta: mostrar déficit de 600 explicitamente; não zerar o resultado silenciosamente. Saldo bancário já negativo também entra com sinal negativo. Uma reserva mantida integralmente em investimentos não seria subtraída outra vez dos 4.000. Reserva ainda por constituir com dinheiro da conta exige tratamento distinto, a aprovar.

Se uma entrada prevista de 3.000 ocorrer só depois de uma saída de 3.500, saldo inicial 1.000 gera mínimo intermediário de −2.500, embora o saldo final seja 500. O horizonte e a apresentação desse risco precisam ser decididos antes da implementação. Os números são exemplos, não dados do usuário.

## Condições para encerrar a validação

Inspecionar arquivos reais; aprovar decisões; provar OAuth Gmail/Drive no aparelho; testar backup/recuperação e fontes escolhidas; preparar ambiente e produzir APK de validação após confirmação da stack. Antes de uso real, executar todos os critérios mínimos do pedido, incluindo instalação limpa e atualização preservando dados. Este relatório não declara esses testes concluídos.
