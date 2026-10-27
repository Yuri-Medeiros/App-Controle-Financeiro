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
- Nenhuma chave, senha, criptografia ou integração foi criada ou implementada. Frequência, retenção, formato do backup e fluxo de recuperação ainda precisam de decisão e testes.

## Plataforma e colaboração

- Aparelho principal informado: Samsung Galaxy S23 com Android 16.
- Compatibilidade aprovada: Android 15 (API 35) como versão mínima do aplicativo. Android 16 deve ser contemplado nos testes no aparelho principal. A API mínima 26 do APK técnico não se aplica ao produto.
- 27/10/2026: aprofundar o teste da Parallelum gratuita. Não significa aprovação de plano pago ou garantia de continuidade.
- 27/10/2026: desenvolvimento e compilação exclusivamente via Codex Cloud, sem dependência do computador do usuário. Preparação de ferramentas Android gratuitas e verificação de acesso autorizadas; não habilitar cobrança.
- Uma decisão por mensagem. Consultar antes de decisões importantes. Solicitar XLSX/CSV/PDF quando necessários à próxima validação, sem bloquear outras decisões por antecedência.
- 27/10/2026: aprovados Kotlin + Jetpack Compose, Room/SQLite e BiometricPrompt como base do aplicativo. Gmail e Drive continuam sujeitos às validações e decisões específicas. A aprovação da base não resolve automaticamente versões, Android mínimo, navegação, criptografia, assinatura ou modelo financeiro.

O procedimento em tools prepara infraestrutura. O APK técnico de teste não define nome, pacote, Android mínimo, interface ou arquitetura do produto.
