// Gera os "golden files" (resultado de referência) rodando o MOTOR DO APP ORIGINAL EM HTML (v48)
// no Node, com data fixa. Os testes em Kotlin (app/src/test) comparam o motor novo com eles.
// Uso:  node gen_golden.js ../../referencia/meu-banco-patrimonio-v48.html ../../app/src/test/resources
const fs = require("fs");
const path = require("path");
const vm = require("vm");

const htmlPath = process.argv[2];
const outDir = process.argv[3];
const html = fs.readFileSync(htmlPath, "utf8");
const m = html.match(/<script>([\s\S]*)<\/script>/);
let src = m[1];

// 1) corta a inicialização e expõe as funções internas
const initIdx = src.indexOf("/* ============================================================\n     INICIALIZAÇÃO");
if (initIdx < 0) throw new Error("bloco de inicialização não encontrado");
src = src.slice(0, initIdx) + `
globalThis.__api = {
  get state(){ return state; }, set state(v){ state = v; },
  defaultState, mergeLoadedState, simulateOneDay, computeINSS, computeIRRF, computeMonthlyIncome,
  compute13thBreakdown, computeFeriasBreakdown, getNthBusinessDay, getNationalHolidays, taxReport,
  manualInvest, sellShares, transferInvestAccounts, estimateSaleTax, scoreInvestorQuiz, payDarf,
  reserveWithdrawEstimate, withdrawFromReserve, addExtraToReserve, spendFree, allocationSlices,
  portfolioRiskProfile, recomputeFgtsBackfill, savePontoSlot, computePontoHours, shoppingDiscountFor,
  shoppingListFullTotal, syncShoppingToFoodExpense, formatBRL, estimateMonthsToGoal, addHistory,
  computeInsights, pontoExtraHoursForMonth, currentShiftForMonth, lastBusinessDay, taxDueISO, dateToISO,
  isoToDate, round2, QUIZ
};
})();`;
// render/persist viram no-ops (não há DOM)
src = src.replace("function render() {", "function render() { return;");
src = src.replace("function persist() {", "function persist() { return;");
src = src.replace("function showSuccess(title, sub) {", "function showSuccess(title, sub) { return;");
src = src.replace("function checkOnboarding() {", "function checkOnboarding() { return;");

// 2) ambiente falso (DOM mudo, data fixa)
const RealDate = Date;
const FIXED = new RealDate(2026, 10, 17, 12, 0, 0); // 17/11/2026 12:00 (hora local)
let tick = 0;
class FakeDate extends RealDate {
  constructor(...a) { if (a.length === 0) super(FIXED.getTime()); else super(...a); }
  static now() { tick += 5000; return FIXED.getTime() + tick; }
}
const dummy = new Proxy(function () {}, {
  get: (t, k) => (k === Symbol.toPrimitive ? () => "" : k === "length" ? 0 : dummy),
  set: () => true, apply: () => dummy, construct: () => dummy,
});
const doc = new Proxy({}, { get: (t, k) => (k === "querySelectorAll" ? () => [] : k === "visibilityState" ? "visible" : dummy), set: () => true });
const ctx = {
  console, Math, JSON, Object, Array, String, Number, Boolean, Set, Map, Date: FakeDate, isFinite, isNaN, parseFloat, parseInt,
  Infinity, NaN, Promise, Error, RegExp, Symbol, Uint8Array, URL,
  document: doc, navigator: { onLine: true, vibrate: () => {} }, window: { addEventListener() {}, innerWidth: 400, innerHeight: 800, print() {} },
  localStorage: { getItem: () => null, setItem() {}, removeItem() {} },
  setTimeout: () => 0, clearTimeout() {}, setInterval: () => 0, requestAnimationFrame: () => 0,
  performance: { now: () => 0 }, alert() {}, confirm: () => true, prompt: () => null,
  fetch: async () => { throw new Error("sem rede"); }, AbortController, btoa: (s) => Buffer.from(s, "binary").toString("base64"), atob: (s) => Buffer.from(s, "base64").toString("binary"),
  crypto: { getRandomValues: (a) => a },
};
ctx.globalThis = ctx;
vm.createContext(ctx);
vm.runInContext(src, ctx, { filename: "app.js" });
const api = ctx.__api;

// ---------- utilidades ----------
const clone = (o) => JSON.parse(JSON.stringify(o));
function save(name, obj) { fs.writeFileSync(path.join(outDir, name), JSON.stringify(obj, null, 1)); }
function newState(mut) {
  const s = api.defaultState();
  s.account.number = "12345-6"; s.card.last4 = "1234";
  mut(s);
  s.onboardingCompleted = true;
  api.state = s;
  return s;
}
function runOps(s, ops) {
  const log = [];
  api.state = s;
  for (const o of ops) {
    const st = api.state;
    if (o.op === "sim") for (let i = 0; i < o.days; i++) api.simulateOneDay();
    else if (o.op === "transfer") log.push(api.transferInvestAccounts(o.toInvest, o.amount));
    else if (o.op === "buy") log.push(api.manualInvest(o.id, o.qty));
    else if (o.op === "sell") log.push(clone(api.sellShares(o.id, o.qty)));
    else if (o.op === "darf") api.payDarf(o.ym);
    else if (o.op === "reserveExtra") api.addExtraToReserve(o.id, o.amount);
    else if (o.op === "reserveWithdraw") api.withdrawFromReserve(o.id, o.amount);
    else if (o.op === "spendFree") api.spendFree(o.amount, o.category);
    else throw new Error("op desconhecida " + o.op);
  }
  return log;
}

// ---------- cenários ----------
const scenarios = [];

// A: empregado com carteira, caixinhas, cartão parcelado, ponto com hora extra, domingos, renda extra
{
  const s = newState((s) => {
    s.cash = 5000;
    s.income.baseSalary = 2800; s.income.hireDateISO = "2024-03-10"; s.income.feriasMonth = 2; // março
    s.income.shiftMode = "alternado"; s.income.baseShift = "noite"; s.income.baseMonthKey = "2026-11";
    s.secondaryIncome = { enabled: true, label: "Salário esposa", type: "salario", amount: 1300, payDay: 10 };
    s.sundaysWorked = ["2026-11-22", "2026-12-06", "2026-12-20", "2027-01-10"];
    ["2026-11-02","2026-11-03","2026-11-04","2026-12-01","2026-12-02","2027-01-05"].forEach((d, i) => {
      api.savePontoSlot(d, "in1", "11:00"); api.savePontoSlot(d, "out1", "15:00");
      api.savePontoSlot(d, "in2", "17:30"); api.savePontoSlot(d, "out2", i % 2 ? "22:15" : "21:00");
    });
    s.reserves[0].monthlyContribution = 300; s.reserves[0].targetValue = 5000;
    s.reserves[1].monthlyContribution = 120;
    s.goals = [{ id: 1, name: "Sofá", type: "movel", target: 3000, saved: 200, monthly: 150, rate: 8, completed: false, completedOn: null }];
    s.nextGoalId = 2;
    s.card.limit = 3000; s.card.closingDay = 25; s.card.dueDay = 5;
    s.card.purchases = [
      { id: 1, desc: "Geladeira", totalAmount: 2400, installmentsTotal: 6, installmentValue: 400, installmentsPaid: 1, dateISO: "2026-11-15" },
      { id: 2, desc: "Tênis", totalAmount: 250, installmentsTotal: 1, installmentValue: 250, installmentsPaid: 1, dateISO: "2026-11-16" },
    ];
    s.card.invoiceCurrent = 650; s.card.nextPurchaseId = 3;
    s.extraCards = [{ enabled: true, limit: 800, invoiceCurrent: 100, pendingAmount: 0, pendingDueYM: null, closingDay: 10, dueDay: 20, lastClosedYM: null, last4: "9999", purchases: [], nextPurchaseId: 1, name: "Cartão 2" }];
    s.expenses = [
      { id: 1, name: "Aluguel", amount: 800, category: "Moradia" }, { id: 2, name: "Energia", amount: 140, category: "Contas" },
      { id: 3, name: "Alimentação", amount: 0, category: "Alimentação" }, { id: 4, name: "Internet", amount: 100, category: "Contas" },
    ];
    s.shoppingList = [
      { id: 1, name: "Arroz", category: "mercearia", price: 28, quantity: 2, checked: true },
      { id: 2, name: "Frango", category: "carnes", price: 42.5, quantity: 1, checked: false, weightKg: 3.0, pricePerKg: 14.1667 },
    ];
    s.nextShoppingId = 3; s.shoppingDiscount = { type: "pct", value: 5 };
    api.syncShoppingToFoodExpense();
    s.recurringInvestEnabled = true; s.monthlyContribution = 800; s.reinvestDividends = true; s.contributionDay = 9;
    s.settings.brokerageFee = 2.5; s.monthlyWithdrawal = 50;
    s.assets.find((a) => a.id === "HGLG11").allocPct = 40; s.assets.find((a) => a.id === "MXRF11").allocPct = 30;
    s.assets.find((a) => a.id === "BBAS3").allocPct = 30;
    s.freeSpendMonthlyLimit = 400;
  });
  const input = clone(s);
  const ops = [
    { op: "sim", days: 20 }, { op: "transfer", toInvest: true, amount: 4000 }, { op: "buy", id: "BBAS3", qty: 20 },
    { op: "buy", id: "KNCR11", qty: 8 }, { op: "reserveExtra", id: 1, amount: 700 }, { op: "spendFree", amount: 85.9, category: "Lazer" },
    { op: "sim", days: 200 }, { op: "reserveWithdraw", id: 1, amount: 400 }, { op: "sell", id: "BBAS3", qty: 7 },
    { op: "sim", days: 150 }, { op: "sell", id: "HGLG11", qty: 2 }, { op: "sim", days: 420 },
  ];
  const log = runOps(s, ops);
  scenarios.push({ name: "A_empregado_completo", input, ops, expected: clone(api.state), log });
}

// B: benefício (renda) + cartão + vendas com isenção/ultrapassagem de R$ 20 mil em ações
{
  const s = newState((s) => {
    s.cash = 100; s.income.incomeType = "renda"; s.income.beneficioAmount = 680; s.income.beneficioPayDay = 12; s.income.beneficioLabel = "Bolsa Família";
    s.leftover = 60000; s.assets.forEach((a) => { a.costBasis = 0; });
    s.card.enabled = true; s.card.closingDay = 28; s.card.dueDay = 3; s.card.invoiceCurrent = 90;
    s.reserveConfig.annualRatePct = 10.65; s.reserves[0].balance = 1000; s.reserves[0].startDateISO = "2026-01-01"; s.reserves[0].totalInterest = 80;
    s.assets.find((a) => a.id === "ITUB4").price = 40.37; s.assets.find((a) => a.id === "TAEE11").price = 35.9;
  });
  const input = clone(s);
  const ops = [
    { op: "buy", id: "ITUB4", qty: 400 }, { op: "buy", id: "TAEE11", qty: 300 }, { op: "buy", id: "XPML11", qty: 100 },
    { op: "sim", days: 35 }, { op: "sell", id: "ITUB4", qty: 300 }, { op: "sell", id: "XPML11", qty: 40 },
    { op: "sim", days: 40 }, { op: "sell", id: "TAEE11", qty: 100 }, { op: "reserveWithdraw", id: 1, amount: 500 }, { op: "sim", days: 300 },
  ];
  const log = runOps(s, ops);
  const before = clone(api.state);
  const pend = api.taxReport().filter((r) => r.tax > 0.005 && !r.paid);
  scenarios.push({ name: "B_beneficio_vendas_ir", input, ops, expected: before, log, pendingDarfYm: pend.map((r) => r.ym) });
}

// C: autônomo / sem cartão, mês de férias dezembro, 13º, FGTS retroativo
{
  const s = newState((s) => {
    s.cash = 20000; s.income.incomeType = "empregado"; s.income.baseSalary = 7100; s.income.hireDateISO = "2020-12-01";
    s.income.feriasMonth = 11; s.income.shiftMode = "fixo"; s.income.fixedShift = "manha"; s.income.holidayHours = 8; s.income.holidayNightHours = 3;
    s.card.enabled = false;
    api.recomputeFgtsBackfill();
    s.contributionDay = 20; s.recurringInvestEnabled = true; s.monthlyContribution = 2500;
    s.assets.find((a) => a.id === "KNCR11").allocPct = 100;
  });
  const input = clone(s);
  const ops = [{ op: "sim", days: 760 }];
  runOps(s, ops);
  scenarios.push({ name: "C_clt_ferias_13o", input, ops, expected: clone(api.state), log: [] });
}

for (const sc of scenarios) save("scenario_" + sc.name + ".json", sc);

// ---------- funções puras ----------
const fn = { inss: [], irrf: [], monthly: [], holidays: {}, nth: [], quiz: [], irr13: [], ferias: [], easter: {}, lbd: [], due: [], fmt: [], pontoHours: [], months: [] };
for (const g of [0, 500, 1621, 1700, 2500, 2902.84, 3000, 4354.27, 4400, 5000, 5500, 6000, 7000, 7350, 7500, 8475.55, 9000, 12000]) {
  fn.inss.push([g, api.computeINSS(g)]);
  fn.irrf.push([g, api.computeIRRF(g, api.computeINSS(g))]);
}
for (let y = 2024; y <= 2031; y++) {
  fn.holidays[y] = api.getNationalHolidays(y).map((d) => api.dateToISO(d)).sort();
  for (let mo = 0; mo < 12; mo++) {
    fn.nth.push([y, mo, 5, api.dateToISO(api.getNthBusinessDay(y, mo, 5))]);
    fn.lbd.push([y, mo, api.dateToISO(api.lastBusinessDay(y, mo))]);
  }
}
for (const v of [0, 1, 1234.5, 0.005, 999999.99, -5.1, 1e6 + 0.255, 12.345]) fn.fmt.push([v, api.formatBRL(v)]);
const q = [[0,0,0,0,0,0,0,0],[3,3,3,3,3,3,3,3],[1,1,1,1,1,1,1,1],[3,0,3,3,3,3,3,3],[2,3,2,2,0,2,3,2],[3,3,3,3,0,3,3,3],[1,2,3,0,1,2,3,0]];
for (const a of q) fn.quiz.push([a, api.scoreInvestorQuiz(a)]);
for (const t of [["07:00","11:00","15:00","18:00"],["22:00","02:00","",""],["11:00","15:00","17:30","21:45"],["","","",""]]) fn.pontoHours.push([t, api.computePontoHours(...t)]);
save("functions.json", fn);
console.log("ok:", scenarios.map((s) => s.name).join(", "));
