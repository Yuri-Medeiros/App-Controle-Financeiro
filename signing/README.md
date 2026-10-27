# Identidade de assinatura definitiva

A identidade definitiva foi criada em 27/10/2026, protegida pela senha pessoal fornecida por `MEU_FINANCEIRO_SIGNING_PASSWORD`. O valor da senha não foi exibido nem gravado no repositório. Não gere outra chave para esta aplicação.

`tools/sign-release.sh` usa o keystore privado em `$FINANCE_CLOUD_ROOT/signing/meu-financeiro-release.p12`, ou o caminho explicitamente fornecido por `MEU_FINANCEIRO_SIGNING_KEYSTORE`, e a senha por `MEU_FINANCEIRO_SIGNING_PASSWORD`. Não gera chave, não aceita senha em argumento e não usa chave debug como alternativa. O alias esperado é `meu-financeiro`.

`tools/create-release-key.sh` prepara uma identidade PKCS12 com chave RSA de 3072 bits, protegida pela senha pessoal, sem qualquer chave privada em texto claro no disco. O script só executa com a variável pessoal real e recusa substituir identidade ou certificado já existentes. A criação autorizada foi concluída somente após verificar a ausência de identidade local e o estado registrado no PR. O contêiner usa o alias `meu-financeiro`.

O contêiner definitivo está no [Google Drive privado](https://drive.google.com/file/d/16rOEjg0u5DcLEF_25DqcWv9tTGrjgb_e/view). A recuperação da chave real passou: download independente, comparação byte a byte, abertura com a senha pessoal e comparação do SHA-256 do certificado com `release-certificate.sha256`. Esse backup é da identidade de assinatura; o backup do banco do aplicativo ainda não está implementado.

O arquivo `signing/release-certificate.sha256` contém apenas o SHA-256 do certificado público aprovado (64 caracteres hexadecimais). Essa identidade impede assinar atualizações com outra chave por engano. A chave privada e sua senha nunca devem ser versionadas. O contêiner original e a cópia recuperada permanecem protegidos e fora do versionamento. Nunca substitua essa identidade; em outro ambiente, recupere o mesmo arquivo do Drive e confira o certificado fixado antes de assinar.

Depois da compilação e da configuração da chave correta:

```bash
bash tools/sign-release.sh
```

O script verifica o certificado, alinha páginas nativas de 16 KiB, assina e verifica o APK. Apenas então publica o arquivo instalável em `build/distributions`. O release sem assinatura não é instalável e não deve ser apresentado como entrega de uso real.

O bootstrap em nuvem usa valores pessoais acessíveis aos processos da tarefa. Não imprimir variáveis, habilitar shell tracing, guardar a senha em `.env` no repositório ou persistir chave privada descriptografada no workspace publicado. Não adicionar billing, outro serviço de assinatura ou mudar para chave descartável para contornar falta de acesso.

## GitHub Actions

A distribuição usa `tools/sign-apk.sh` em um runner limpo, separado do build e com certificado fixado. O script local `tools/sign-release.sh` reutiliza a mesma verificação e lê a versão de `version.properties`. Para builds locais com código diferente de 1, forneça `MEU_FINANCEIRO_VERSION_CODE` com o mesmo código usado no Gradle.

A identidade de teste é distinta, alias `meu-financeiro-test`, fixada por `test-certificate.sha256`. Chaves e senhas são fornecidas por GitHub Secrets; o workflow nunca cria identidades. Consulte PIPELINE.md para configuração, guarda e aprovação.
