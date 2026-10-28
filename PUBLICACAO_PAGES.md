# Páginas públicas para o OAuth Google

Aprovado pelo usuário em 28/10/2026: usar GitHub Pages gratuito para página inicial e política de privacidade do Meu Financeiro. Não foi aprovado domínio pago, billing ou mudança do pipeline Android.

Os arquivos em docs/ são estáticos: HTML/CSS, sem bibliotecas, scripts, rastreadores, formulários ou arquivos financeiros. As páginas distinguem a base atual das integrações em desenvolvimento e não incluem dados dos CSVs, contatos pessoais extraídos dos prints, tokens, senhas ou chaves. O contato publicado é o canal público de solicitações do repositório; não enviar dados pessoais nesse canal.

## Ativação

Esta alteração é somente documentação/site. A branch docs/google-pages parte de work e pode ser revisada por PR separada. O trabalho incompleto local do aplicativo continua preservado no outro worktree. Não houve incremento de versão Android, build, assinatura ou publicação de APK, pois nenhum arquivo do aplicativo/pipeline é alterado.

Para publicar a revisão desta branch sem integrar funcionalidades do aplicativo:

1. Settings → Pages → Build and deployment.
2. Source: Deploy from a branch.
3. Branch: docs/google-pages; pasta: /docs; Save.
4. Aguardar a implantação e abrir o endereço mostrado pelo GitHub. Em repositório público, usar somente Pages gratuito; não habilitar plano/serviço pago.

Após integrar a PR de documentação em work, pode-se selecionar work /docs como origem, preservando os mesmos endereços. Não selecionar a raiz do repositório: somente docs/ precisa ser publicado.

Endereços esperados, sujeitos a implantação bem-sucedida:

- https://yuri-medeiros.github.io/App-Controle-Financeiro/
- https://yuri-medeiros.github.io/App-Controle-Financeiro/privacidade.html

Só colar no Google depois de confirmar que abrem publicamente sem login. A aceitação desses URLs e eventuais requisitos de domínio do Google ainda devem ser verificados no Console; não foi comprovada publicação OAuth ou dispensa de verificação. O nome/contato de suporte no Console deve ser preenchido pelo titular. Não enviar logotipo enquanto a exigência de verificação exibida pelo Google não estiver resolvida.

## Evidência desta execução

- Repositório confirmado como público pelo conector GitHub, com Issues habilitadas e has_pages=false antes da ativação.
- Consulta GET e tentativa POST de configuração de Pages pelo terminal retornaram Forbidden. Nenhuma ativação foi confirmada; é necessária a configuração pelo painel do titular.
- PR de documentação: https://github.com/Yuri-Medeiros/App-Controle-Financeiro/pull/5 . Arquivos enviados na branch docs/google-pages.
- HTML de ambas as páginas analisado com HTMLParser; referências locais verificadas; ausência de script, iframe e formulário conferida. git diff --check passou. Nenhum teste Android executado porque esta alteração não afeta o aplicativo.
