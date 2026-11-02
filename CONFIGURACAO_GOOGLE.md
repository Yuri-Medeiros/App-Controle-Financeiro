# Configuração Google — Meu Financeiro

Atualização em 28/10/2026. Este arquivo contém somente identificadores públicos e evidências, sem tokens, senhas ou chaves privadas.

- Projeto: `meu-financeiro-513012`.
- Gmail API: usuário confirmou ativação.
- Google Auth Platform: público Externo; print confirmou status **Em produção**. Banner de verificação permanece; foi mantido o caminho aprovado de uso pessoal, sem envio automático para análise. Concessão real ainda não testada no APK.
- Escopo: `https://www.googleapis.com/auth/gmail.readonly`; usuário confirmou inclusão.
- Não foi solicitada ativação de billing. Não presumir configuração financeira da conta inspecionada pelo assistente.

## Clientes Android

| Variante | Pacote | ID do cliente | SHA-1 do certificado |
| --- | --- | --- | --- |
| Principal | br.com.yurimedeiros.meufinanceiro | 20769744023-dgvt80dimt68v7hoiqa2rm5j3m4ecoum.apps.googleusercontent.com | FB:9B:73:67:28:CF:92:DC:2A:28:C5:25:AB:27:98:02:3C:08:6D:FB |
| Teste | br.com.yurimedeiros.meufinanceiro.test | 20769744023-udg4n9i3gckjhkknek53q55bjn6b7c0s.apps.googleusercontent.com | 79:50:E9:B6:BC:7F:64:EA:E1:ED:85:9E:75:AD:73:FF:AA:09:6D:06 |

Os JSONs recebidos confirmam projeto e IDs distintos, mas não incluem pacote/SHA-1: esses vínculos foram orientados ao usuário e devem ser validados na autorização real. SHA-1 principal extraído do APK publicado v0.1.2, conferindo hash do APK e SHA-256 do certificado contra signing/release-certificate.sha256. SHA-1 de teste extraído do candidato teste-pr4-r5, com hash do APK e pin de signing/test-certificate.sha256 conferidos. Nenhuma chave foi recriada ou substituída. Atualizações com o mesmo certificado preservam essas impressões digitais.

AuthorizationClient no Android usa a identidade de pacote/assinatura registrada; esses clientes não são um segredo de servidor nem justificam criar conta de serviço ou embutir client_secret no APK.

## Páginas públicas

- Página inicial: https://yuri-medeiros.github.io/App-Controle-Financeiro/
- Privacidade: https://yuri-medeiros.github.io/App-Controle-Financeiro/privacidade.html
- Fontes: branch docs/google-pages, PR #5. has_pages=true e implantação Actions 40696417164 concluída com sucesso. A tentativa de HTTP direto do executor foi bloqueada por política de rede; não equivale a erro do site.

Drive appdata/backup financeiro ainda não foi configurado ou implementado nesta etapa. Backup da identidade de assinatura é independente. Sem prometer recuperação financeira antes de implementar e testar restauração em instalação limpa.

## Backup privado no Drive — implementação de 30/10/2026

O app solicita separadamente `https://www.googleapis.com/auth/drive.appdata`, pelo AuthorizationClient Android já existente. Para testar, habilitar a **Google Drive API** no mesmo projeto exclusivo, mantendo faturamento desabilitado, e conferir o escopo na configuração OAuth em produção. A ativação da API não foi executada por esta tarefa.

No app: Configurações → Backup e recuperação → Gerenciar backup. Definir e guardar a senha de recuperação (independente da assinatura), confirmar e autorizar. Para recuperar uma instalação nova, buscar os backups, revisar e confirmar a restauração. Principal e candidato têm backups separados por pacote; testar apenas com dados de teste no candidato.

A implementação envia arquivos cifrados à área privada e verifica por leitura antes da retenção. Credenciais permanecem em memória; a ativação automática vale para a sessão desbloqueada. Cancelamento, revogação, expiração e restauração em instalação limpa precisam de teste real no Android.

Documentação oficial: https://developers.google.com/workspace/drive/api/guides/appdata
