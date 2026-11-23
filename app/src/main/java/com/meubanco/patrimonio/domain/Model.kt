package com.meubanco.patrimonio.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate
import kotlin.random.Random

/** Relógio injetável (os testes fixam a data). */
object AppClock {
    var today: () -> LocalDate = { LocalDate.now() }
    var nowMs: () -> Long = { System.currentTimeMillis() }
    fun todayISO(): String = today().toString()
}

/** Lê números JSON (inclusive 25.0 ou 25.5) como Int, sem quebrar backups antigos do app em HTML. */
object LInt : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LInt", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int {
        val el = (decoder as? JsonDecoder)?.decodeJsonElement()
        if (el is JsonPrimitive) {
            el.doubleOrNull?.let { return Math.round(it).toInt() }
            el.content.toIntOrNull()?.let { return it }
        }
        return 0
    }
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

@Serializable
class Account(
    var agency: String = "0001",
    var number: String = (10000 + Random.nextInt(89999)).toString() + "-" + Random.nextInt(9),
)

@Serializable
class Purchase(
    var id: Int = 0,
    var desc: String = "",
    var totalAmount: Double = 0.0,
    @Serializable(with = LInt::class) var installmentsTotal: Int = 0,
    var installmentValue: Double = 0.0,
    @Serializable(with = LInt::class) var installmentsPaid: Int = 0,
    var dateISO: String = "",
    /** formato antigo (antes das parcelas): valor único */
    var amount: Double? = null,
)

@Serializable
class Card(
    var enabled: Boolean = true,
    var limit: Double = 1500.0,
    var invoiceCurrent: Double = 0.0,
    var pendingAmount: Double = 0.0,
    var pendingDueYM: String? = null,
    @Serializable(with = LInt::class) var closingDay: Int = 25,
    @Serializable(with = LInt::class) var dueDay: Int = 5,
    var lastClosedYM: String? = null,
    var last4: String = (1000 + Random.nextInt(8999)).toString(),
    var purchases: MutableList<Purchase> = mutableListOf(),
    var nextPurchaseId: Int = 1,
    var name: String = "Cartão principal",
)

@Serializable
class Asset(
    var id: String = "",
    var ticker: String = "",
    var name: String = "",
    var category: String = "acao", // acao | tijolo | papel
    var price: Double = 0.0,
    var yieldPct: Double = 0.0,
    var volatility: Double = 0.02,
    @Serializable(with = LInt::class) var quantity: Int = 0,
    var costBasis: Double? = null,
    var allocPct: Double? = null,
    @SerialName("_prevPrice") var prevPrice: Double? = null,
    var realPriceAt: Double? = null,
    var divAt: Double? = null,
    var divTry: Double? = null,
    var divMonthly: Double? = null,
    var valAnnualPct: Double? = null,   // valorização anualizada do preço, vinda do arquivo de cotações
    var valMonths: Int? = null,         // janela (em meses) usada nesse cálculo
)

@Serializable
class Reserve(
    var id: Int = 0,
    var name: String = "",
    var balance: Double = 0.0,
    var monthlyContribution: Double = 0.0,
    var targetValue: Double = 0.0,
    var totalContributed: Double = 0.0,
    var totalInterest: Double = 0.0,
    var startDateISO: String? = null,
    var createdOn: String? = null,
)

@Serializable
class Goal(
    var id: Int = 0,
    var name: String = "",
    var type: String = "outro",
    var target: Double = 0.0,
    var saved: Double = 0.0,
    var monthly: Double = 0.0,
    var rate: Double = 0.0,
    var completed: Boolean = false,
    var completedOn: String? = null,
    var deadlineYM: String? = null,
)

@Serializable
class SharedBill(
    var id: Int = 0,
    var name: String = "",
    var amount: Double = 0.0,
    var paidByMe: Boolean = true,
    var mySharePct: Double = 50.0,
    var date: String = "",
    var settled: Boolean = false,
)

@Serializable
class Shift(var name: String = "", var detail: String = "", var hours: Double = 7.0)

@Serializable
class Income(
    var incomeType: String = "empregado", // empregado | autonomo | renda
    var hireDateISO: String? = null,
    var beneficioLabel: String = "Benefício social",
    var beneficioAmount: Double = 0.0,
    @Serializable(with = LInt::class) var beneficioPayDay: Int = 5,
    var baseSalary: Double = 1621.0,
    var extraHours: Double = 0.0,
    var extraHourPct: Double = 50.0,
    var holidayHours: Double = 7.0,
    var holidayPct: Double = 100.0,
    var holidayNightHours: Double = 0.0,
    var holidayNightPct: Double = 20.0,
    var sundayBonus: Double = 70.0,
    var sundayAnchorISO: String? = null,
    var sundayAnchorWorked: Boolean = true,
    var sundayMode: String = "alternado",
    var thirteenthEnabled: Boolean = true,
    var feriasEnabled: Boolean = true,
    @Serializable(with = LInt::class) var feriasMonth: Int? = null,
    var baseMonthKey: String = "",
    var baseShift: String = "noite",
    var shiftMode: String = "alternado",
    var fixedShift: String = "noite",
    var shifts: MutableMap<String, Shift> = mutableMapOf(
        "manha" to Shift("Manhã", "07:00–11:00 · intervalo · 15:00–18:00", 7.0),
        "noite" to Shift("Tarde / Noite", "11:00–15:00 · intervalo · 17:30–21:00", 7.5),
    ),
    var totalReceived: Double = 0.0,
)

@Serializable
class SecondaryIncome(
    var enabled: Boolean = false,
    var label: String = "Renda extra",
    var type: String = "salario",
    var amount: Double = 0.0,
    @Serializable(with = LInt::class) var payDay: Int = 5,
)

@Serializable
class Fgts(var balance: Double = 0.0, var lastBackfillAmount: Double = 0.0)

@Serializable
class Profile(var birthDateISO: String? = null, var sex: String? = null)

@Serializable
class Expense(var id: Int = 0, var name: String = "", var amount: Double = 0.0, var category: String = "Outros")

@Serializable
class HistoryEntry(
    var ts: String = "",
    var type: String = "",
    var label: String = "",
    var amount: Double = 0.0,
    var cats: Map<String, Double>? = null,
    var category: String? = null,
)

@Serializable
class MonthStat(
    var spent: Double = 0.0,
    var income: Double = 0.0,
    var invested: Double = 0.0,
    var cats: MutableMap<String, Double> = mutableMapOf(),
)

@Serializable
class NwPoint(var ym: String = "", var value: Double = 0.0, var invested: Double? = null)

@Serializable
class PontoEntry(
    var date: String = "",
    var in1: String = "",
    var out1: String = "",
    var in2: String = "",
    var out2: String = "",
    var hours: Double = 0.0,
)

@Serializable
class ShopItem(
    var id: Int = 0,
    var name: String = "",
    var category: String = "Geral",
    var price: Double = 0.0,
    @Serializable(with = LInt::class) var quantity: Int = 1,
    var checked: Boolean = false,
    var productId: Int? = null,
    var barcode: String = "",
    var variant: String = "",
    var weightKg: Double? = null,
    var pricePerKg: Double? = null,
)

@Serializable
class Product(
    var id: Int = 0,
    var name: String = "",
    var category: String = "Geral",
    var price: Double = 0.0,
    var barcode: String = "",
    var brand: String = "",
    var variant: String = "",
    var byWeight: Boolean = false,
)

@Serializable
class Discount(var type: String = "pct", var value: Double = 0.0)

@Serializable
class TaxEntry(var ym: String = "", var ticker: String = "", var cls: String = "", var gross: Double = 0.0, var gain: Double = 0.0)

@Serializable
class InvestorProfile(
    var answers: List<Int> = emptyList(),
    var score: Int = 0,
    var label: String = "",
    var cap: String = "",
    var doneAt: String = "",
)

@Serializable
class ReserveConfig(var annualRatePct: Double = 11.0)

@Serializable
class Market(
    var autoAssets: Boolean = true,
    var simGrowthPct: Double = 9.5,
    var assetsUpdatedAt: String? = null,
    var assetsUpdatedAtMs: Long? = null,
    var assetsError: String? = null,
    var assetsCheckedAtMs: Long? = null,
    var quotesUrl: String = "https://raw.githubusercontent.com/matheusalmeidaeca/cotacoes/main/cotacoes.json",
    var cdiUpdatedAt: String? = null,
    var cdiError: String? = null,
    var ipcaPct: Double? = null,        // IPCA acumulado em 12 meses (BrasilAPI)
    var selicPct: Double? = null,       // Selic ao ano (BrasilAPI)
    var macroUpdatedAt: String? = null,
)

@Serializable
class Settings(
    var theme: String = "dark",
    var fontSize: String = "normal",
    var highContrast: Boolean = false,
    var hapticEnabled: Boolean = true,
    var bioEnabled: Boolean = false,
    var bioCredId: String = "",
    var assistantEnabled: Boolean = true,
    var celebrationsEnabled: Boolean = true,
    var displayName: String = "",
    var hideBalance: Boolean = false,
    var pinEnabled: Boolean = false,
    var pinHash: String? = null,
    var pixConfirmThreshold: Double = 0.0,
    var pixDailyLimit: Double = 0.0,
    var autoRoundStep: Int = 0,
    var autoRoundTarget: String = "",
    var autoSavePct: Double = 0.0,
    var autoSaveTarget: String = "",
    var allocPreset: String = "moderado",
    var brokerageFee: Double = 0.0,
    var quoteRefreshMode: String = "daily",
    @Serializable(with = LInt::class) var notifyCardDays: Int = 3,
    @Serializable(with = LInt::class) var notifyBackupDays: Int = 14,
    var autoBackupEnabled: Boolean = true,
    @Serializable(with = LInt::class) var notifyGoalPct: Int = 90,
    @Serializable(with = LInt::class) var notifyFreeSpendPct: Int = 90,
    // v51
    var hiddenTabs: MutableList<String> = mutableListOf("ponto"),
    var simNoticeDismissed: Boolean = false,
    @Serializable(with = LInt::class) var lockAfterSec: Int = 0,
    var secureScreen: Boolean = false,
    var notifyBudget: Boolean = true,
    var notifyDebt: Boolean = true,
    var notifyCard: Boolean = true,
)

/**
 * Estado completo do app. Os nomes dos campos são idênticos aos do app em HTML (v48),
 * então o backup .json exportado por ele pode ser importado aqui sem conversão.
 */
@Serializable
class AppState(
    var simDateISO: String = AppClock.todayISO(),
    var account: Account = Account(),
    var card: Card = Card(),
    var extraCards: MutableList<Card> = mutableListOf(),
    var nextExtraCardId: Int = 1,
    var activeCardIdx: Int = 0,

    var activeMainTab: String = "conta",
    var activeCatTab: String = "todos",
    var showConfig: Boolean = false,

    var assets: MutableList<Asset> = DEFAULT_ASSETS.map { it.copyAsset() }.toMutableList(),
    var selectedAssetId: String? = null,
    var monthlyContribution: Double = 0.0,
    var recurringInvestEnabled: Boolean = false,
    var reinvestDividends: Boolean = false,
    var taxLedger: MutableList<TaxEntry> = mutableListOf(),
    var taxPaid: MutableMap<String, Boolean> = mutableMapOf(),
    var investorProfile: InvestorProfile? = null,
    var realizedGain: Double = 0.0,
    var investSobra: Double = 0.0,
    var monthlyWithdrawal: Double = 0.0,
    @Serializable(with = LInt::class) var contributionDay: Int = 7,
    var leftover: Double = 0.0,
    var totalContributed: Double = 0.0,
    var totalWithdrawn: Double = 0.0,
    var totalInvestedCost: Double = 0.0,
    var totalDividendsReceived: Double = 0.0,

    var market: Market = Market(),

    var reserves: MutableList<Reserve> = mutableListOf(
        Reserve(1, "Reserva de emergência", createdOn = AppClock.todayISO()),
        Reserve(2, "Viagens", createdOn = AppClock.todayISO()),
    ),
    var nextReserveId: Int = 3,
    var reserveConfig: ReserveConfig = ReserveConfig(),

    var settings: Settings = Settings(),
    /** null = backup antigo sem cadastro inicial (tratado como já concluído) */
    var onboardingCompleted: Boolean? = null,
    var pixSentTodayISO: String? = null,
    var pixSentTodayTotal: Double = 0.0,

    var celebrated: MutableMap<String, Boolean> = mutableMapOf(),
    var importedIds: MutableList<String> = mutableListOf(),
    var catRules: MutableMap<String, String> = mutableMapOf(),
    var sundaysWorked: MutableList<String> = mutableListOf(),
    var shoppingList: MutableList<ShopItem> = mutableListOf(),
    var nextShoppingId: Int = 1,
    var products: MutableList<Product> = mutableListOf(),
    var nextProductId: Int = 1,

    var goals: MutableList<Goal> = mutableListOf(),
    var nextGoalId: Int = 1,

    var profile: Profile = Profile(),
    var income: Income = Income(),
    var fgts: Fgts = Fgts(),
    var lastBackupAt: String? = null,
    var lastThirteenthFirstYear: Int? = null,
    var lastThirteenthSecondYear: Int? = null,
    var lastFeriasYear: Int? = null,
    var expenses: MutableList<Expense> = mutableListOf(
        Expense(1, "Aluguel", 400.0, "Moradia"),
        Expense(2, "Energia", 100.0, "Contas"),
        Expense(3, "Alimentação", 0.0, "Alimentação"),
        Expense(4, "Internet", 130.0, "Contas"),
        Expense(5, "Telefonia", 120.0, "Contas"),
    ),
    var nextExpenseId: Int = 6,
    var shoppingLinkedExpenseId: Int = 3,
    var cash: Double = 0.0,
    var totalExpensesPaid: Double = 0.0,

    var history: MutableList<HistoryEntry> = mutableListOf(),
    var netWorthHistory: MutableList<NwPoint> = mutableListOf(),
    var monthlyStats: MutableMap<String, MonthStat> = mutableMapOf(),
    var shoppingDiscount: Discount = Discount(),
    var lastProcessedYM: String? = null,
    var lastCarteiraProcessedYM: String? = null,
    var lastIncomePaidYM: String? = null,
    var secondaryIncome: SecondaryIncome = SecondaryIncome(),
    var lastSecondaryIncomePaidYM: String? = null,
    var lastExpensesPaidYM: String? = null,

    var timeClock: MutableList<PontoEntry> = mutableListOf(),
    var freeSpendCategories: MutableList<String> = mutableListOf("Lazer", "Imprevisto", "Presente", "Outro"),
    var freeSpendMonthlyLimit: Double = 0.0,

    // ---- novidades da versão Kotlin (ausentes em backups do HTML; assumem o padrão) ----
    var budgets: MutableMap<String, Double> = mutableMapOf(),
    var subscriptions: MutableList<Subscription> = mutableListOf(),
    var nextSubscriptionId: Int = 1,
    var fiTargetMonthly: Double = 0.0,
    var dividendBreakdown: MutableMap<String, MutableMap<String, Double>> = mutableMapOf(),
    var importedKeys: MutableList<String> = mutableListOf(),
    var budgetAlerted: MutableMap<String, Boolean> = mutableMapOf(),
    var debts: MutableList<Debt> = mutableListOf(),
    var nextDebtId: Int = 1,
    var mainGoal: String = "",
    var debtStrategy: String = "avalanche",
    var debtExtraMonthly: Double = 0.0,
    var emergencyMonthsTarget: Double = 6.0,
    var sharedBills: MutableList<SharedBill> = mutableListOf(),
    var nextSharedId: Int = 1,
    var partnerName: String = "Parceiro(a)",
    // ---- v56: contas reais (Pluggy / Meu Pluggy), somente leitura ----
    var realAccounts: MutableList<RealAccount> = mutableListOf(),
    var realTx: MutableList<RealTx> = mutableListOf(),
    var realLinks: MutableList<RealLink> = mutableListOf(),
    var realInvest: MutableList<RealInvest> = mutableListOf(),
    var realLastSyncISO: String? = null,
    var realSyncError: String? = null,
    var realToHistory: Boolean = false,
    // ---- v54: metas de investimento ----
    var contribByMonth: MutableMap<String, Double> = mutableMapOf(),   // aportes líquidos à carteira por mês (não sofre o limite de 500 lançamentos)
    var divByMonth: MutableMap<String, Double> = mutableMapOf(),       // dividendos recebidos por mês
    var flowsMigrated: Boolean = false,
    var investGoalPatrimony: Double = 0.0,
    var investGoalIncome: Double = 0.0,
    var investGrowthPct: Double? = null,
    var investContribOverride: Double? = null,
    var investYieldOverride: Double? = null,
    var investInflationOverride: Double? = null,
    var investContribGrowthPct: Double = 0.0,
    var investReinvest: Boolean = true,

    // campos legados (versões antigas do app em HTML) — só lidos na migração
    var cdb: LegacyCdb? = null,
    var hasEverStarted: Boolean? = null,
) {
    companion object {
        /** Estado de uma conta nova (ainda sem cadastro inicial). */
        fun novo(): AppState {
            val s = AppState()
            s.flowsMigrated = true
            s.onboardingCompleted = false
            s.income.sundayAnchorISO = nearestSundayOnOrAfter(AppClock.today()).toString()
            s.income.baseMonthKey = AppClock.todayISO().substring(0, 7)
            s.income.feriasMonth = AppClock.today().monthValue - 1
            return s
        }
    }
}

@Serializable
class Subscription(
    var id: Int = 0,
    var name: String = "",
    var amount: Double = 0.0,
    @Serializable(with = LInt::class) var day: Int = 1,
    var category: String = "Lazer",
    var active: Boolean = true,
    var lastChargedYM: String? = null,
)

@Serializable
class Debt(
    var id: Int = 0,
    var name: String = "",
    var kind: String = "cartao", // cartao | emprestimo | cheque | financiamento | outro
    var balance: Double = 0.0,
    var ratePct: Double = 0.0,   // juros ao mês
    var minPayment: Double = 0.0,
)

@Serializable
class LegacyCdb(
    var balance: Double = 0.0,
    var monthlyContribution: Double = 0.0,
    var monthlyExpenseRef: Double? = null,
    var monthlyExpense: Double? = null,
    var monthsTarget: Double? = null,
    var annualRatePct: Double? = null,
)

fun Asset.copyAsset() = Asset(
    id, ticker, name, category, price, yieldPct, volatility, quantity, costBasis, allocPct,
    prevPrice, realPriceAt, divAt, divTry, divMonthly,
)

val DEFAULT_ASSETS: List<Asset> = listOf(
    Asset("BBAS3", "BBAS3", "Banco do Brasil", "acao", 27.0, 0.70, 0.030),
    Asset("ITUB4", "ITUB4", "Itaú Unibanco", "acao", 36.0, 0.60, 0.030),
    Asset("TAEE11", "TAEE11", "Taesa", "acao", 36.0, 0.80, 0.020),
    Asset("CPFE3", "CPFE3", "CPFL Energia", "acao", 38.0, 0.65, 0.025),
    Asset("CMIG4", "CMIG4", "Cemig", "acao", 11.0, 0.60, 0.030),
    Asset("BBSE3", "BBSE3", "BB Seguridade", "acao", 35.0, 0.70, 0.025),
    Asset("VBBR3", "VBBR3", "Vibra Energia", "acao", 22.0, 0.65, 0.030),
    Asset("HGLG11", "HGLG11", "CSHG Logística", "tijolo", 160.0, 0.75, 0.015),
    Asset("KNRI11", "KNRI11", "Kinea Renda Imobiliária", "tijolo", 150.0, 0.65, 0.015),
    Asset("XPML11", "XPML11", "XP Malls", "tijolo", 95.0, 0.75, 0.015),
    Asset("VISC11", "VISC11", "Vinci Shopping Centers", "tijolo", 105.0, 0.70, 0.015),
    Asset("BTLG11", "BTLG11", "BTG Pactual Logística", "tijolo", 100.0, 0.75, 0.015),
    Asset("GGRC11", "GGRC11", "GGR Covepi Renda", "tijolo", 95.0, 0.80, 0.018),
    Asset("RBRP11", "RBRP11", "RBR Properties", "tijolo", 65.0, 0.70, 0.018),
    Asset("MXRF11", "MXRF11", "Maxi Renda", "papel", 9.5, 0.90, 0.010),
    Asset("KNCR11", "KNCR11", "Kinea Rendimentos", "papel", 98.0, 0.90, 0.008),
    Asset("IRDM11", "IRDM11", "Iridium Recebíveis", "papel", 70.0, 0.95, 0.012),
    Asset("VGIR11", "VGIR11", "Valora RE III", "papel", 8.8, 0.90, 0.008),
    Asset("KNIP11", "KNIP11", "Kinea Índices de Preços", "papel", 85.0, 0.85, 0.010),
    Asset("HGCR11", "HGCR11", "CSHG Recebíveis Imob.", "papel", 90.0, 0.90, 0.010),
)
