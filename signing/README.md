# Identidade de assinatura definitiva

Nenhuma chave definitiva foi criada. O processo aprovado exige chave protegida, recuperação no Google Drive e senha fornecida por variável pessoal do Codex Cloud.

`tools/sign-release.sh` usa o keystore privado em `$FINANCE_CLOUD_ROOT/signing/meu-financeiro-release.p12`, ou o caminho explicitamente fornecido por `MEU_FINANCEIRO_SIGNING_KEYSTORE`, e a senha por `MEU_FINANCEIRO_SIGNING_PASSWORD`. Não gera chave, não aceita senha em argumento e não usa chave debug como alternativa. O alias esperado é `meu-financeiro`.

`tools/create-release-key.sh` prepara uma identidade PKCS12 com chave RSA de 3072 bits, protegida pela senha pessoal, sem qualquer chave privada em texto claro no disco. O script só executa com a variável pessoal real e recusa substituir identidade ou certificado já existentes. Esta execução não tem a senha: apenas o bloqueio de criação foi testado. Antes da criação real, confirmar que esse formato corresponde à decisão de arquivo protegido, e concluir a disponibilização da senha na execução correta.

O conector Google Drive foi validado com um arquivo temporário inofensivo: envio, recuperação por referência e comparação byte a byte passaram. O arquivo foi removido após o teste. Isso não representa um backup de chave real, nem comprova o fluxo Google do futuro aplicativo Android.

Antes da primeira assinatura, registrar em `signing/release-certificate.sha256` apenas o SHA-256 do certificado público aprovado (64 caracteres hexadecimais). Essa identidade impede assinar atualizações com outra chave por engano. A chave privada e sua senha nunca devem ser versionadas. A escolha e criação do contêiner protegido e sua cópia recuperável no Drive ainda precisam ser concluídas no ambiente com acesso configurado.

Depois da compilação e da configuração da chave correta:

```bash
bash tools/sign-release.sh
```

O script verifica o certificado, alinha páginas nativas de 16 KiB, assina e verifica o APK. Apenas então publica o arquivo instalável em `build/distributions`. O release sem assinatura não é instalável e não deve ser apresentado como entrega de uso real.

O bootstrap em nuvem usa valores pessoais acessíveis aos processos da tarefa. Não imprimir variáveis, habilitar shell tracing, guardar a senha em `.env` no repositório ou persistir chave privada descriptografada no workspace publicado. Não adicionar billing, outro serviço de assinatura ou mudar para chave descartável para contornar falta de acesso.
