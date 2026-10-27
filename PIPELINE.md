# Atualizações pelo PC ou pelo celular

O GitHub executa a compilação, assinatura e publicação. O Codex é opcional para editar código e operar os mesmos botões. Downloads ficam em:

https://github.com/Yuri-Medeiros/App-Controle-Financeiro/releases

## Ativação inicial (uma vez)

O workflow manual só aparece quando `.github/workflows/distribute.yml` estiver na branch padrão `work`. O PR inicial permanece em rascunho até a revisão da base Android. Sua integração não representa teste no aparelho nem aprovação de uma feature futura.

1. Integrar o pipeline revisado em `work`. Em Settings → Actions → General, permitir GitHub Actions e a action oficial `actions/checkout`; não habilitar runners maiores, orçamento pago ou cobrança por excedente. As permissões de cada job já estão declaradas nos workflows.
2. Conferir `distribution-baseline.json` com a maior versão já instalada/distribuída fora do pipeline. O valor inicial é 0.1.0/código 1, correspondente ao APK anterior. Se houver outra versão distribuída, ajustar a referência antes de começar. O pipeline bloqueia versões que não superem essa referência.
3. Em Settings → Secrets and variables → Actions, configurar os quatro **repository secrets** abaixo. Base64 é só o transporte do PKCS12, não substitui a proteção do GitHub Secrets.

| Secret | Conteúdo |
| --- | --- |
| `MEU_FINANCEIRO_RELEASE_KEYSTORE_B64` | Base64 do PKCS12 definitivo **existente**, recuperado do Drive |
| `MEU_FINANCEIRO_SIGNING_PASSWORD` | Senha pessoal desse PKCS12 |
| `MEU_FINANCEIRO_TEST_KEYSTORE_B64` | Base64 do PKCS12 de teste estável, alias `meu-financeiro-test` |
| `MEU_FINANCEIRO_TEST_PASSWORD` | Senha própria do PKCS12 de teste |

A identidade definitiva é fixada por `signing/release-certificate.sha256`. A identidade de teste tem seu próprio pin em `signing/test-certificate.sha256`. Nenhum workflow cria chaves nem altera esses pins automaticamente. Preserve as duas identidades e suas senhas em armazenamento pessoal protegido. Se perder a chave de teste, recupere-a; substituir o pin exige revisão e os candidatos instalados com a chave antiga não poderão ser atualizados normalmente.

O script de configuração usa `gh` autenticado com permissão de Secrets no repositório. Ele confere o certificado local, envia por stdin sem mostrar valores e preserva secrets existentes. No Bash do PC, fornecer a senha sem eco e usar os caminhos onde você guardou as identidades:

```bash
read -rs -p 'Senha da identidade definitiva: ' MEU_FINANCEIRO_SIGNING_PASSWORD
export MEU_FINANCEIRO_SIGNING_PASSWORD
python3 tools/configure-github-signing.py \
  --release-keystore /caminho/protegido/meu-financeiro-release.p12 \
  --test-keystore /caminho/protegido/meu-financeiro-test.p12 \
  --test-password-file /caminho/protegido/test-password
unset MEU_FINANCEIRO_SIGNING_PASSWORD
```

Na execução que implementou o pipeline, as identidades estão em `/workspace/cloud-android/signing`, com permissões restritas. O arquivo `test-password` contém a senha de teste e não deve ser publicado. Esse workspace é temporário: a configuração dos Secrets e a guarda pessoal da identidade de teste precisam ser concluídas antes de descartá-lo. O PKCS12 definitivo permanece também no Drive privado, conforme `signing/README.md`.

4. Configurar uma ruleset para `work` exigindo PR e o check **Testes, lint e compilação**, sem permitir bypass habitual. Em repositório público, usar apenas os recursos gratuitos disponíveis. A aprovação de teste é validada pelo script de distribuição, inclusive para PRs do próprio proprietário, sem exigir autoaprovação de review que o GitHub não permite.
5. Criar um PR pequeno de ensaio e executar o roteiro de aceitação ao final deste documento.

## Desenvolvimento local sem Codex

Pré-requisitos opcionais para compilar no PC: JDK 21, Android SDK com plataforma 36 e Build Tools 36.0.0, `ANDROID_HOME` configurado, Python 3. O wrapper fixa Gradle e checksum. No Windows, usar `gradlew.bat`; no Bash, os comandos abaixo. Para apenas editar e enviar código, o SDK não é necessário: o GitHub compila.

```bash
git switch work
git pull --ff-only origin work
git switch -c feat/minha-feature
# Editar código e incrementar versionName em version.properties.
python3 -m unittest discover -s tools/tests -v
bash gradlew --no-daemon :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease \
  :app:assembleCandidate :app:testCandidateUnitTest :app:lintCandidate :app:assembleDebugAndroidTest
git add <arquivos-da-mudanca>
git commit -m 'Adiciona minha feature'
git push -u origin feat/minha-feature
```

Abrir o PR contra `work`, descrever o comportamento e incluir um roteiro específico de teste. Patch para correções, minor para features e major para incompatibilidades. Mudanças no banco exigem migração e teste preservando dados; não usar migração destrutiva. APK local sem assinatura não é uma entrega instalável.

## Gerar e testar um candidato

1. Aguardar **Verificar PR** terminar com sucesso no commit atual. Não há chaves de assinatura nesse workflow. Contribuições de forks devem ser revisadas e importadas para uma branch deste repositório antes de distribuir um candidato.
2. Abrir Actions → **Gerar APK de teste ou Publicar versão** → Run workflow. Selecionar branch `work`, `mode = test` e o número do PR, sem `#`.
3. Ao terminar, abrir o link **Download** no resumo da execução ou a pré-versão `teste-pr<N>-r<RUN_NUMBER>` em Releases. Baixar `Meu-Financeiro.apk`; não baixar código-fonte ZIP/TAR.
4. Instalar **Meu Financeiro Teste** (ícone laranja com T). Ele usa o pacote `.test`, banco, arquivos e Android Keystore separados. Não importa nem compartilha os dados do principal. Usar dados de teste.
5. Conferir a feature descrita no PR, desbloqueio, reabertura e persistência. Para problemas, corrigir na mesma branch e gerar outra execução/candidato.

Os candidatos usam a configuração de release, sem modo debug. Sua chave é estável para permitir atualizar o app de teste. O pipeline mantém três candidatos publicados por PR. Não há notificações ou atualizações automáticas implementadas.

## Aprovar e publicar

A pré-versão contém a frase exata para copiar em um **comentário novo** no PR:

```text
Aprovado para publicação: teste-pr<N>-r<RUN_NUMBER> <SHA_COMPLETO_DE_40_CARACTERES>
```

O comentário deve ser do proprietário ou colaborador com permissão write/maintain/admin, posterior à publicação do candidato e anterior ao merge. Um OK sem tag e SHA não libera a publicação. Com Codex, depois do seu OK explícito, ele registra essa mesma evidência no GitHub; nunca aprova por conta própria. Sem Codex, você faz o comentário pelo PC ou celular.

1. Antes de aprovar, atualizar a branch com `work` se necessário e gerar outro candidato. Para integrar sem alterações no conteúdo, usar **Create a merge commit** ou **Squash and merge**; não usar rebase merge neste fluxo. O tree Git integrado deve ser exatamente igual ao do candidato testado.
2. Comentar a frase de aprovação e integrar o PR. Não misturar outras mudanças antes da publicação: o commit integrado deve continuar no topo de `work`. Alterações ou conflitos resolvidos depois do teste exigem novo PR/candidato/OK.
3. Em Actions, executar o mesmo workflow com branch `work`, `mode = release` e o número do PR integrado. Esse botão é a operação **Publicar versão**; não depende do Codex.
4. O workflow confere aprovação, CI, candidato, seu checksum, conteúdo integrado, histórico e versão. Reexecuta testes, compila, assina e verifica a versão principal.
5. Abrir a Release definitiva `vX.Y.Z`, baixar `Meu-Financeiro.apk` e abrir no Android. Autorizar a instalação pelo navegador se solicitado e tocar **Atualizar**, sem desinstalar o principal.

A versão definitiva e o candidato são builds diferentes por terem pacotes distintos. O mesmo tree de código aprovado é exigido, mas o teste separado não comprova migração nos dados reais: mudanças de banco precisam também de testes específicos de migração.

## Assinatura, versões e falhas

- O `versionCode` é `1000 + run_number` do workflow único `distribute.yml`, compartilhado entre candidatos e versões finais. Reexecutar mantém o número. **Não renomear, excluir/recriar esse workflow nem resetar sua numeração.** Uma migração futura desse contador deve estabelecer outra referência superior antes de publicar.
- Uma versão publicada é imutável. Não substituir APK de candidato aprovado nem APK definitivo; correções usam nova versão/execução. Uma reexecução de publicação já concluída será bloqueada.
- A compilação ocorre sem secrets de assinatura. Outro runner limpo, com scripts da branch confiável, recebe o APK por uma Release em rascunho, confere o hash e só então acessa a chave apropriada. Nenhum script Gradle do PR roda no runner de assinatura. Operadores de distribuição precisam revisar o código que autorizam executar; não acionar candidatos de código não confiável.
- O APK sem assinatura é removido antes de tornar a Release pública. APK assinado, `pipeline.json` e `SHA256SUMS` são lidos de volta e comparados antes da publicação. Uma falha deixa apenas rascunho, não uma entrega anunciada.
- Se uma execução falhar, conferir os logs. Para tentar novamente, preferir **Re-run all jobs** na mesma execução. Não usar apenas Re-run failed jobs para assinatura após mudar o PR. Se iniciar outra execução definitiva com a mesma versão e houver rascunho antigo, apagar manualmente somente o rascunho incompleto após conferir sua execução de origem. Nunca apagar uma versão definitiva para republicar outro APK. Tags já existentes devem apontar para o mesmo commit; se o código mudou, use outra versão/execução, sem mover a tag antiga.
- A validação final repete os controles; se código/aprovação mudarem durante a execução, bloqueia. Publicações são serializadas; execuções em espera podem ser substituídas pelo limite de fila do GitHub, devendo ser acionadas novamente.
- Depois de publicar a versão definitiva, remover os candidatos daquele PR. Se a limpeza falhar depois da publicação, a Release definitiva continua válida; conferir antes de tentar outra execução.
- Falta de chave, senha errada, certificado divergente ou checks falhando bloqueiam. Não há regeneração automática, downgrade prometido ou apagamento de banco para corrigir falhas.
- O workflow exige repositório público e runners Linux padrão. Não usa cache, Actions artifacts, APIs de IA, Play Store nem servidor externo. Releases contêm aplicativo e metadados, nunca dados financeiros ou keystores. Logs permanecem sujeitos às condições gratuitas do GitHub. Se um serviço exigir pagamento, parar e informar; não habilitar excedentes.

## Aceitação e limites verificados

Para aceitar o pipeline, executar sem Codex: edição local → commit → PR → checks → candidato → instalação de teste → correção → novo candidato → comentário de aprovação → merge → publicação → atualização do principal preservando um registro existente. Conferir também os bloqueios por ausência de aprovação, alteração de código, falha de CI e certificado incorreto.

Os testes automatizados dos controles usam respostas simuladas da API. Eles não comprovam permissões reais de Secrets/Releases nem instalação no celular. O teste de ponta a ponta só estará concluído depois de configurar Secrets, integrar os workflows e executar o roteiro em GitHub e Android. Backup dos dados financeiros continua fora deste pipeline.
