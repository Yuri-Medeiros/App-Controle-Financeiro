# Trabalho neste repositório

Leia README.md, PIPELINE.md e signing/README.md antes de alterar distribuição ou assinatura.

- Codex é opcional: mantenha o fluxo manual pelo PC e pelo GitHub funcional.
- Features partem de `work` e entram por PR. Incremente `version.properties` conforme a mudança.
- Execute os testes Python do pipeline e os checks Gradle indicados em PIPELINE.md. Mudanças no banco exigem migração e teste de preservação, nunca recriação destrutiva.
- Não exponha senhas, keystores ou variáveis de ambiente. Preserve as identidades e os pins existentes.
- Não habilite billing, runners maiores, créditos de IA ou excedentes pagos.
- Depois de uma solicitação de feature, prepare um candidato e forneça o link da Release para teste. Não invente aprovação de teste ou instalação no aparelho.
- Só registre a frase de aprovação em nome do usuário após OK explícito dele para aquele candidato e SHA. Se o código mudar, solicite novo teste.
- Depois da aprovação, siga os mesmos controles de merge e workflow manual usados sem Codex. Não publique por outro caminho para contornar bloqueios.
