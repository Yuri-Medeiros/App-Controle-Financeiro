# Compilação no Codex Cloud

## Atualização: primeiro aplicativo

Após autorização explícita do usuário, a branch de implementação passa a conter o app Kotlin/Compose, banco Room/SQLCipher e bloqueio com Android Keystore e BiometricPrompt. Ver README.md para comandos e limites atuais. A prova de infraestrutura descrita abaixo permanece como histórico; o aplicativo de produto usa Android mínimo 15 (API 35).

## Estado verificado em 27/10/2026

Preparação exclusivamente no executor Linux x86_64, sem instalação de ferramentas no computador do usuário e sem habilitar serviços pagos.

- Eclipse Temurin OpenJDK 21.0.13+9 e Gradle 8.14.3 executados.
- Android Command-line Tools 23.0, Android CLI 1.1.16717390, plataforma Android 36, Build Tools 36.0.0 e Platform Tools instalados.
- Acesso HTTPS comprovado ao catálogo Android, Google Maven e Maven Central usando a permissão de rede da ferramenta de execução.
- APK técnico compilado com javac, convertido para DEX, empacotado, alinhado e assinado. Verificação das assinaturas v2 e v3 aprovada; manifesto e atividade inicial reconhecidos por aapt2.
- Script de preparação executado novamente com sucesso sobre as ferramentas instaladas. Não foi feita reconstrução em um segundo ambiente vazio.

APK técnico nesta sessão: `/workspace/cloud-android/apk-probe.X63ash/cloud-probe.apk`.
SHA-256: `3d2897baf52391feab71f179794b8f065a8e017eac13443353ed973839a4f412`.
Não é a primeira versão do aplicativo financeiro; apenas comprova o pipeline básico do SDK. A chave descartável foi removida e não serve para distribuição futura.

## Preparação de futuras sessões

Na raiz do repositório, com acesso de rede autorizado no Codex Cloud:

```bash
bash tools/setup-cloud.sh
source tools/cloud-env.sh
bash tools/verify-cloud-apk.sh
```

O diretório padrão das ferramentas é `/workspace/cloud-android`, fora do repositório. Pode ser alterado por `FINANCE_CLOUD_ROOT`. Não depende de arquivos do computador do usuário. Os arquivos de tools e os documentos precisam permanecer no repositório remoto para sobreviver à remoção deste workspace; a existência desta pasta temporária não é garantia de persistência entre sessões. O script ainda precisa ser associado ao mecanismo de setup do ambiente, se disponível; nenhuma configuração de painel foi alterada por ferramenta.

O bootstrap valida SHA-256 dos arquivos JDK, Gradle e Command-line Tools. O launcher oficial busca o Android CLI; o script verifica o hash da versão testada e interrompe se mudar. Essa proteção implica revisão futura quando o Google trocar o binário do launcher. Platform Tools e revisões de plataforma são resolvidos pelo catálogo oficial; não há promessa de reconstrução byte a byte.

Preservar os proxies e a confiança TLS do ambiente. Não usar `curl -k`, não substituir credenciais, não editar a política local para tentar conceder acesso. Consultas às APIs financeiras e OAuth ainda dependem da política do ambiente e de testes próprios.

## Limites e próximas validações

- Não há app Compose/Room implementado, wrapper do projeto ou teste Gradle de dependências do app. A combinação final de plugins e bibliotecas será definida após confirmação da stack.
- API 36 e API mínima 26 do APK técnico não são decisões de compatibilidade do produto.
- Nenhum emulador ou instalação Android foi executado; `/dev/kvm` não foi encontrado. ADB está instalado, mas sua inicialização nesta imagem tenta criar `/home/agent/.android`, que é somente leitura. Esse problema não impediu compilar e assinar o APK. O script não declara ADB funcional nem altera HOME para contornar a restrição.
- Instalação, biometria, OAuth e atualização preservando dados exigem validação posterior em Android. Receber e instalar o APK no celular não exige computador ou Android Studio.
- Assinatura definitiva: ainda precisa de decisão sobre guarda e recuperação da chave fora do workspace descartável, por meio de recurso seguro de nuvem. Nenhuma chave real foi criada, salva no Git ou enviada em mensagens.
- O uso sem custo refere-se às ferramentas de desenvolvimento e aos serviços escolhidos para o aplicativo. Esta preparação não muda nem garante a gratuidade do plano do próprio Codex.

## Fontes oficiais

- [Android SDK e ferramentas](https://developer.android.com/tools)
- [Android CLI](https://developer.android.com/tools/agents/android-cli)
- [Assinatura de aplicativos](https://developer.android.com/studio/publish/app-signing)
- [Temurin 21](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.13%2B9)
- [Gradle 8.14.3](https://docs.gradle.org/8.14.3/release-notes.html)
