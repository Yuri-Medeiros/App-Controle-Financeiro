# Decisões confirmadas

## Escopo e pausa

- Primeiro APK aprovado: instalação, desbloqueio por biometria ou PIN/senha do Android e armazenamento local com Room.
- Instrução vigente: não iniciar desenvolvimento agora. Continuar resolvendo decisões; aguardar autorização explícita para implementar. Registro de decisões não é implementação.

## Segurança aprovada

- Criar e usar chave de assinatura definitiva, com custo zero, para preservar a identidade do aplicativo nas atualizações.
- Guardar a chave de assinatura em arquivo criptografado no Google Drive do usuário, com senha de recuperação conhecida por ele; não guardar a chave no GitHub. O fluxo de acesso para assinatura pelo Codex Cloud ainda precisa de validação e detalhamento, sem dependência do computador do usuário.
- Criptografar o banco local, além do bloqueio de abertura.
- Usar SQLCipher com Room, sob modalidade gratuita. Integração, versão e licença efetiva serão verificadas antes de implementar.
- Usar Android Keystore para proteger a chave do banco local e vincular seu uso à autenticação do aparelho. A chave protegida pelo dispositivo não é transferível; recuperação em outro aparelho depende do backup restaurável.
- Criptografar o backup financeiro no Drive com senha de recuperação definida pelo usuário, para permitir restauração após perda ou troca do celular. Não presumir que seja a mesma senha do arquivo da chave de assinatura: isso não foi decidido.
- Frequência aprovada: backup automático após alterações enquanto o app estiver aberto e conectado, agrupando mudanças próximas para evitar envios excessivos. Não implica autorização ou exigência de execução em segundo plano. Intervalo de agrupamento ainda não definido.
- Retenção aprovada: backup mais recente e uma versão por dia dos últimos 15 dias, conforme ajuste do usuário à proposta de 30 dias. Dias sem backup não geram versões artificiais. Remover versões antigas somente após confirmar novo envio, conforme proposta aceita com ajuste da janela.
- Nenhuma chave, senha, criptografia ou integração foi criada ou implementada. Formato do backup e fluxo de recuperação ainda precisam de decisão e testes.

## Plataforma e colaboração

- Usuário rejeitou iniciar OAuth em modo Testing. Diretriz: distribuição e uso de produção sem custos, em conta pessoal, sem CNPJ. Publicação OAuth em produção para uso exclusivamente pessoal é o caminho documental a validar; não implica Play Store, hospedagem ou abertura ao público. Aviso de app não verificado e teste real ainda precisam ser tratados. Nenhum projeto ou publicação foi executado.
- Projeto Google Cloud exclusivo para o aplicativo aprovado, condicionado a custo zero e sem habilitar faturamento. Não reutilizar automaticamente o projeto Apps Script antigo. Criação/configuração ainda não executadas; eventual exigência de custo ou cobrança deve ser apresentada ao usuário antes de prosseguir.
- Permissão de backup financeiro aprovada: `https://www.googleapis.com/auth/drive.appdata`, limitada à área privada do aplicativo no Drive. Não concede acesso aos demais arquivos. O arquivo criptografado de recuperação da chave de assinatura será tratado separadamente, pois precisa ser acessível fora do app. Concessão OAuth e testes ainda pendentes.
- Permissão Gmail aprovada: `https://www.googleapis.com/auth/gmail.readonly`, para leitura de e-mails e anexos, sem modificar ou excluir mensagens. Usuário informado de que os filtros financeiros do app não restringem o alcance da permissão, que permite leitura de toda a caixa. Concessão OAuth real e testes ainda pendentes.
- Aparelho principal informado: Samsung Galaxy S23 com Android 16.
- Compatibilidade aprovada: Android 15 (API 35) como versão mínima do aplicativo. Android 16 deve ser contemplado nos testes no aparelho principal. A API mínima 26 do APK técnico não se aplica ao produto.
- 27/10/2026: aprofundar o teste da Parallelum gratuita. Não significa aprovação de plano pago ou garantia de continuidade.
- 27/10/2026: desenvolvimento e compilação exclusivamente via Codex Cloud, sem dependência do computador do usuário. Preparação de ferramentas Android gratuitas e verificação de acesso autorizadas; não habilitar cobrança.
- Uma decisão por mensagem. Consultar antes de decisões importantes. Solicitar XLSX/CSV/PDF quando necessários à próxima validação, sem bloquear outras decisões por antecedência.
- 27/10/2026: aprovados Kotlin + Jetpack Compose, Room/SQLite e BiometricPrompt como base do aplicativo. Gmail e Drive continuam sujeitos às validações e decisões específicas. A aprovação da base não resolve automaticamente versões, Android mínimo, navegação, criptografia, assinatura ou modelo financeiro.

O procedimento em tools prepara infraestrutura. O APK técnico de teste não define nome, pacote, Android mínimo, interface ou arquitetura do produto.
