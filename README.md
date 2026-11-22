# Admoney (Kotlin)

App Android nativo (Kotlin + Jetpack Compose) reescrito a partir do app em HTML v48.

- `domain/` — motor financeiro (salário/INSS/IRRF, 13º, férias, FGTS, cartão, Pix, caixinhas, carteira, DARF…), portado 1:1 do JS.
- `data/` — arquivo local, cotações/CDI, backup.
- `ui/` — telas em Compose. `work/` — rotina diária e avisos.
- `tools/golden` — gera, a partir do HTML original, os resultados de referência usados nos testes (`app/src/test`).
- O backup `.json` do app em HTML pode ser importado em Configurações → Dados.

Novidades da v50: máscara de moeda, aba Planejamento (projeção, calendário, orçamento, assinaturas, independência financeira), dividendos mês a mês, importação de extrato CSV/OFX, backup em pasta de nuvem, widget e atalho, PIN com PBKDF2, cotações ao vivo, APK release.

Compilar: push no GitHub → aba Actions → artefato **MeuBanco-APK**.


## v51
- Cadastro: objetivo, reserva inicial, dívidas, PIN e notificações.
- Menu: barra inferior + gaveta, abas ocultáveis (modo simples).
- Planejamento: Dívidas (avalanche/bola de neve), Reserva de emergência, 50-30-20.
- Conta: saúde financeira, primeiros passos, aviso de Pix novo e dicas anti-golpe.
- Configurações: busca, bloqueio automático, tela segura, notificações por tipo, vários cartões, CSV, restaurar cópia.
- Extrato: busca, filtro por mês, totais, CSV. Carteira: simulador de juros. Aprender: glossário, FAQ, anti-golpe.

## v52
- Menu em 4 abas (Início, Gastos, Planejar, Investir) + Mais (Extrato, Ponto, Aprender, Configurações).
- Orçamento e Assinaturas movidos para Gastos; Planejar ficou só com projeção, calendário, dívidas, reserva, 50-30-20 e independência.
- Ponto é módulo opcional, desligado por padrão.
- Aprender enxuto: glossário, anti-golpe e sobre.
- Aviso de que o app não move dinheiro real.

## v53
- Planejar: Calculadoras (negociar dívida à vista, comparar empréstimos, poupança × Tesouro × CDB) e Renda variável.
- Metas com prazo e aporte mensal necessário.
- Alerta de Pix fora do padrão (valor alto, % do saldo, madrugada).
- Gastos: Dividir contas (casal/família), acerto por texto compartilhável.
- Mais: Resumo para o IR (entradas, dividendos, saldos).

## v54
- Investir → Metas: meta de patrimônio (ex.: R$ 250 mil) e meta de renda em dividendos (ex.: R$ 2 mil/mês), com juros compostos mês a mês.
- Aporte médio e rendimento em dividendos calculados do histórico real (todos os meses desde o primeiro aporte, inclusive os sem aporte).
- Só a carteira entra; caixinhas e metas de compra ficam de fora.
- Cenários pessimista/base/otimista, correção pela inflação, reinvestimento de dividendos e aporte crescente.
- Totais mensais de aportes e dividendos guardados à parte (o histórico guarda só 500 lançamentos).

## v55
- Inflação (IPCA 12 meses) e Selic lidas da BrasilAPI e usadas nas metas e nos comparadores; o valor digitado tem prioridade.
- Valorização dos ativos vinda do histórico de preços do repositório de cotações (hist_precos.json / hist_celular.json), média ponderada pela posição, limitada a -10%…+15% a.a.
- Repositório de cotações: novo modo `historico` (celular) e cálculo de valorização anualizada por ativo (valAnnualPct, valMonths, ret12mPct).

## Conexão com bancos (v58): servidor + Pluggy Connect
- `cloudflare/worker.js`: servidor mínimo (Cloudflare Worker). Guarda o Client Secret da Pluggy, cria o token do widget e repassa só os dados do usuário logado (Google). Testes: `node cloudflare/worker.test.mjs`.
- Secrets do GitHub: `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, `PLUGGY_CLIENT_ID`, `PLUGGY_CLIENT_SECRET`, `GOOGLE_ANDROID_CLIENT_ID`.
- Variáveis do GitHub: `BACKEND_URL` (endereço do Worker) e, para testes, `PLUGGY_SANDBOX=1`.
- Sem `BACKEND_URL` o app usa o modo avançado (chaves próprias da Pluggy).
