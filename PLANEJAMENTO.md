# Planejamento consolidado — Meu Financeiro

Atualizado em 27/10/2026. Este documento é o índice de continuidade; não substitui os detalhes financeiros do pedido original nem cria decisões novas.

## Fontes e precedência

1. Instruções atuais do usuário na nova tarefa prevalecem sobre limitações de escopo e pausas anteriores.
2. `DECISOES.md`: decisões da entrevista; substituem propostas e pendências antigas expressamente resolvidas.
3. `REQUISITOS_ORIGINAIS.md`: texto integral do pedido inicial, incluindo contas, migração, familiares, calendário, disponível, patrimônio e os critérios mínimos de teste. Continua obrigatório onde não houve alteração posterior aprovada.
4. `README.md`, `VERIFICACAO_PRIMEIRO_APK.md`, `signing/README.md`, `CODEX_CLOUD.md` e `SINCRONIZACAO_CLOUD.md`: implementação e evidências, com limites dos testes. Parecer técnico não equivale a decisão de produto nem a autorização de mudar requisito.
5. `VALIDACAO.md`: relatório inicial histórico. Não tratar como ainda pendentes decisões posteriormente aprovadas; resultados de testes mais recentes prevalecem.

## Produto e decisões consolidadas

Aplicativo Android pessoal, nome Meu Financeiro, pacote `br.com.yurimedeiros.meufinanceiro`, mínimo Android 15, aparelho principal Galaxy S23/Android 16. Desenvolvimento, compilação e assinatura no Codex Cloud, sem dependência de computador local, CNPJ, Play Store, servidor próprio ou faturamento habilitado. Custo de operação do app deve permanecer R$ 0; não confundir isso com uma garantia de gratuidade do plano do próprio Codex.

Stack aprovada: Kotlin, Compose, Material 3, MVVM, Room/SQLite, SQLCipher, Android Keystore e BiometricPrompt. Google Identity Services/AuthorizationClient aprovado para o futuro OAuth. Recursos adicionais, integrações e mudanças importantes ainda exigem consulta.

Visual moderno, amigável e minimalista; verde-petróleo, fundos neutros e vermelho para déficits/atrasos; temas conforme o sistema; cofrinho aprovado como ícone. Cinco abas aprovadas: Início, Movimentações, Pessoas, Planejamento e Patrimônio; configurações para Google, backup e recuperação.

Segurança: biometria ou credencial Android na abertura e após 1 minuto em segundo plano. Banco cifrado; backup financeiro com senha de recuperação independente da chave local não transferível. Backup automático após alterações com app aberto e conectado, agrupando mudanças; retenção do mais recente e uma versão diária dos últimos 15 dias. Chave definitiva de assinatura protegida no Drive, senha por variável pessoal, nunca no Git/chat. Não presumir que a senha do backup financeiro seja a mesma da assinatura.

Google: projeto exclusivo sem faturamento, conta pessoal, público Externo e OAuth Em produção sob exceção de uso pessoal, com aviso de app não verificado aceito. Escopos aprovados: gmail.readonly e drive.appdata. A área privada do backup financeiro é diferente do arquivo de recuperação da chave de assinatura acessível fora do APK.

Financeiro: reais em centavos inteiros; divisões por maiores restos, desempate por ordem fixa da compra. Disponível cobre até antes do próximo salário, sem somar esse recebimento futuro. Fórmula aprovada: saldo bancário menos obrigações no período, reservas na conta e orçamento adicional não coberto pelas obrigações. Fatura integral inclui familiares; sem dupla contagem; Flash e investimentos fora do saldo usado; familiares não liberam dinheiro antes do recebimento; déficit exibido com sinal. Compromissos futuros vão ao ciclo de vencimento com alerta de déficit. Parte própria de cada parcela consome orçamento no ciclo do vencimento da fatura. Limites renovam sem acumular sobras; dinheiro não gasto continua na conta. Reservas acumulam até uso ou liberação manual, sem duplicar investimentos.

As demais regras detalhadas permanecem integralmente em REQUISITOS_ORIGINAIS.md: não resumir a modelagem apenas por este índice.

## Estado real da implementação

- Branch de trabalho: `feat/primeiro-apk`; PR: https://github.com/Yuri-Medeiros/App-Controle-Financeiro/pull/1, em rascunho, sem merge presumido.
- Base Android implementada: acesso autenticado com operação criptográfica, chave protegida no Keystore, Room/SQLCipher, sessão com timeout, marcador persistente de inicialização, interface inicial e abas vazias. Não existe histórico financeiro fictício.
- Release sem assinatura compilado, 13 testes JVM aprovados, lint sem erros. APK de Instrumentation apenas compilado. S23, biometria/PIN reais, instalação, atualização e restauração não testados.
- Drive do assistente conectado e testado com arquivo temporário sem segredo: upload/download idênticos e temporário removido. OAuth Google dentro do app ainda não configurado.
- Usuário informou variável pessoal real criada e ambiente publicado; execução antiga não a recebeu. Verificar nova execução sem expor valor.
- Nenhuma chave definitiva criada; nenhum backup de chave real; nenhum APK instalável entregue. Scripts de criação/assinatura interrompem quando falta configuração; não usar chave debug para contornar isso.
- Importação, contas financeiras, cartão, pessoas, dívidas, planejamento, patrimônio, meta, compartilhamento e backup financeiro ainda não implementados.

## Continuidade proposta, sem alterar prioridades aprovadas

1. Concluir primeira base: usar a variável pessoal na execução correta; preservar ou recuperar identidade existente; concluir chave protegida, backup/recuperação no Drive, assinatura e verificação. Instalar e validar no S23. Definir os detalhes restantes com o usuário se a execução exigir uma escolha importante.
2. Avançar pelo escopo original das etapas 2–4 somente conforme autorização da nova tarefa: importação/revisão, contas/categorias/cartão, responsáveis/divisões, dívidas/pagamentos; depois calendário/previsões/orçamento/reservas/disponível; depois patrimônio/meta/compartilhamento e recuperação. Backup deve ser arquitetado desde o início e testado antes de dados reais.
3. Consultar uma decisão por mensagem quando houver pendência. Continuar trabalhos independentes já autorizados sem inventar critérios financeiros ou substituir requisitos diante de falhas.

## Pendências que não podem ser presumidas resolvidas

- Assinatura definitiva e recuperação da chave real; formato/fluxo detalhado de backup financeiro, intervalo de agrupamento, recuperação e guarda da senha; testar cancelamento e retomada de autorização Google.
- OAuth real Gmail/Drive e políticas da conta pessoal: a exceção documental não comprova concessão. Verificar custo e quotas efetivos sem habilitar faturamento.
- XLSX antigo e CSVs/PDF reais ainda ausentes. Pedir quando necessários à validação/importação. Validar campos, sinais, datas, IDs, estornos, parcelamento, PDF textual/imagem/proteção e formatos Nubank/Flash. Não inventar parser com exemplos irreais.
- Deduplicação exata versus operações iguais legítimas, sobreposição, aprendizado de categorias, conciliação de manuais/previsões e reconhecimento de pagamentos familiares. Confirmar critérios antes de implementar.
- Migração: vínculos reais do Crédito, compras quitadas e compromissos abertos no corte; data inicial da conciliação familiar. Não transportar dívida presumida antes de janeiro/2026 nem inferir recebimentos ausentes.
- Interpretação documental do fechamento do cartão no dia 1 e casos de limite de data; distribuição de centavos entre várias parcelas; edição/prorrogação e preservação do histórico de dívidas/pagamentos.
- Fontes/calendário oficial de feriados de Ribeirão Preto/SP e dias úteis aplicáveis; frequência e forma de previsão de cada despesa. Valores pessoais e saldos iniciais serão configurados/confirmados, não inventados.
- Parallelum: apenas aprofundamento aprovado e consulta de marcas testada. Falta preço/veículo/mês real, limites e decisão final da fonte; Fundação FIPE declara não fornecer API. Não trocar por fonte paga ou scraping sem consulta.
- Bitcoin: fonte gratuita e cotação não comprovadas; precisão, preço de referência, aportes/vendas e conciliação a decidir.
- FGTS: validar metadados/séries, aniversário, TR, juros, distribuição de resultados, piso anual de inflação e hipóteses futuras; preservar correções e estimativas. Não aplicar taxa fixa silenciosamente.
- Veículos: histórico de avaliações e efeito da venda; seleção inicial completa dos componentes da meta e prazo apenas se necessário; demais detalhes de interface.
- Executar todos os critérios mínimos da seção 11 do pedido original em suas etapas. Não declarar testes de dispositivo, backup ou integrações executados apenas porque o código compila.

## Regra de colaboração

Uma decisão por mensagem. Para decisão nova, explicar contexto, alternativas e recomendação, e aguardar. Não reconfirmar decisões já tomadas; executar o autorizado e informar andamento. Não pedir senhas no chat, expor variáveis, substituir proxies, sobrescrever chaves, habilitar cobranças, enviar mensagens ou contratar serviços.
