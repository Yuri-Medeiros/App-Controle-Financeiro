# Lançamentos e conciliação por pessoa — 0.25.0

## Perfil em Pessoas

O botão Conciliação fica no perfil de cada pessoa. Permite definir “Acompanhar valores a partir de”. Cada pessoa tem sua própria data. A configuração é persistida em evento financeiro cifrado, incluída no backup e preservada ao redefinir faturas. Não muda o schema do banco.

A data é usada nas novas divisões: parcelas anteriores ficam como histórico, sem inventar recebimentos. Datas já usadas em compras antigas são reaproveitadas por pessoa até que uma configuração individual seja salva. Alterar a configuração não reescreve dívidas, recebimentos, rateios ou estornos já registrados. Pessoas sem data prévia precisam configurá-la no perfil antes da primeira divisão.

Na mesma compra, participantes podem ter datas diferentes. A participação na fatura é preservada integralmente; somente a geração do saldo familiar respeita a data de cada pessoa. A prévia continua permitindo revisar saldos de abertura e valores por parcela, sem presumir pagamentos antigos.

## Modal do lançamento importado

Usa o mesmo contorno, fundo e ações dos modais de Patrimônio, com título editável, valor importado em destaque, data da movimentação, caixa de vínculo, vencimento automático sem edição e responsáveis com valores à direita.

Os três seletores horizontais (=, %, R$) permitem divisão igual, por percentual ou por valor. São identificados também por descrição de acessibilidade e estado selecionado. Igualdade e percentuais continuam usando centavos exatos e distribuição determinística do resto. A lista permite adicionar/remover pessoas; a última não pode ser removida.

Marcar Vincular parcela abre uma pequena seleção de previsões. A caixa só é marcada ao escolher uma parcela. Tocar fora, voltar ou Cancelar fecha a seleção sem alterar os dados e mantém a caixa falsa. A seleção importa os responsáveis e sugere os valores proporcionais; não grava nada até Vincular. Parcelas já vinculadas não aparecem. Recebimentos e estornos existentes continuam protegidos pelas validações do ledger.

Categoria e parcelas ficam em detalhes expansíveis. Categoria continua obrigatória e os detalhes abrem se ela estiver faltando ao salvar. Compras compartilhadas ou parceladas mantêm a prévia de revisão; compras próprias com uma parcela podem ser salvas diretamente. Referência, data de movimentação e valor importado não são reescritos pelo formulário.

## Verificação

Testes JVM cobrem datas independentes, exclusão do saldo familiar anterior sem mudar a participação na fatura, preservação de dívidas/recebimentos após mudança de data, compatibilidade com eventos antigos, serialização, reset e ausência de datas inventadas. O teste integrado de banco cifrado inclui backup/restauração e preservação da configuração no reset; é compilado, sem execução em aparelho neste ambiente.

Conferir no aparelho: definir datas em duas pessoas; abrir lançamento em Faturas/Extratos; abrir e cancelar o seletor por Cancelar e toque fora; selecionar previsão; comparar as três formas de divisão; conferir centavos, prévia e saldo de abertura; salvar e reabrir Pessoas, fechar/reabrir o app e restaurar backup.
