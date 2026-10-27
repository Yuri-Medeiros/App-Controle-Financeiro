# Identidade de assinatura definitiva

Nenhuma chave definitiva foi criada. O processo aprovado exige chave protegida, recuperação no Google Drive e senha fornecida por variável pessoal do Codex Cloud.

`tools/sign-release.sh` recebe o caminho do keystore por `MEU_FINANCEIRO_SIGNING_KEYSTORE` e a senha por `MEU_FINANCEIRO_SIGNING_PASSWORD`. Não gera chave, não aceita senha pela linha de comando e não usa chave debug como alternativa. O alias esperado é `meu-financeiro`.

Antes da primeira assinatura, registrar em `signing/release-certificate.sha256` apenas o SHA-256 do certificado público aprovado (64 caracteres hexadecimais). Essa identidade impede assinar atualizações com outra chave por engano. A chave privada e sua senha nunca devem ser versionadas. A escolha e criação do contêiner protegido e sua cópia recuperável no Drive ainda precisam ser concluídas no ambiente com acesso configurado.

Depois da compilação e da configuração da chave correta:

```bash
bash tools/sign-release.sh
```

O script verifica o certificado, alinha páginas nativas de 16 KiB, assina e verifica o APK. Apenas então publica o arquivo instalável em `build/distributions`. O release sem assinatura não é instalável e não deve ser apresentado como entrega de uso real.

O bootstrap em nuvem usa valores pessoais acessíveis aos processos da tarefa. Não imprimir variáveis, habilitar shell tracing, guardar a senha em `.env` no repositório ou persistir chave privada descriptografada no workspace publicado. Não adicionar billing, outro serviço de assinatura ou mudar para chave descartável para contornar falta de acesso.
