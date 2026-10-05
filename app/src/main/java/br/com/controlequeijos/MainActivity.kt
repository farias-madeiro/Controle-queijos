package br.com.controlequeijos

// V7.41 — fechamento diário com resultado líquido

import android.content.Context
import android.os.Bundle
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.text.Normalizer
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.security.MessageDigest
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

data class Product(val id: Long, val name: String, val quantity: Int, val entryValue: Double, val exitValue: Double)
data class Sale(val id: Long, val productId: Long, val productName: String, val quantity: Int, val unitValue: Double, val unitCost: Double, val date: Long)
data class Expense(val id: Long, val description: String, val value: Double, val date: Long)
data class Payment(val id: Long, val orderId: Long, val amount: Double, val date: Long, val note: String)
data class Client(val id: Long, val name: String, val phone: String, val notes: String)
data class Order(
    val id: Long,
    val customerId: Long,
    val customerName: String,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitValue: Double,
    val unitCost: Double,
    val totalValue: Double,
    val paidValue: Double,
    val orderDate: Long,
    val deliveryDate: Long,
    val status: String,
    val stockApplied: Boolean
)

private const val PREFS = "controle_queijos"
private const val PRODUCTS = "products"
private const val EXPENSES_OLD = "expenses"
private const val SALES = "sales"
private const val EXPENSE_LIST = "expense_list"
private const val PAYMENTS = "payments"
private const val ORDERS = "orders"
private const val CLIENTS = "clients"
private const val PRE_RESTORE_BACKUP = "pre_restore_backup"
private const val AUTO_BACKUP_FILE = "controle_queijos_auto_backup.json"
private const val PIN_HASH = "pin_hash"
private const val USER_NAME = "user_name"
private const val USER_ROLE = "user_role"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ControleQueijosApp(applicationContext) }
    }
}

private fun hashPin(pin: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(pin.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

private fun loadProducts(context: Context): List<Product> = runCatching {
    val json = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PRODUCTS, "[]"))
    List(json.length()) { i ->
        val o = json.getJSONObject(i)
        Product(
            o.getLong("id"), o.getString("name"), o.getInt("quantity"),
            if (o.has("entryValue")) o.getDouble("entryValue") else 0.0,
            if (o.has("exitValue")) o.getDouble("exitValue") else o.optDouble("value", 0.0)
        )
    }
}.getOrDefault(emptyList())

fun saveProducts(context: Context, products: List<Product>) {
    val json = JSONArray()
    products.forEach { p ->
        json.put(JSONObject().apply {
            put("id", p.id); put("name", p.name); put("quantity", p.quantity)
            put("entryValue", p.entryValue); put("exitValue", p.exitValue)
        })
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PRODUCTS, json.toString()).apply()
}

private fun loadSales(context: Context): List<Sale> = runCatching {
    val json = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SALES, "[]"))
    List(json.length()) { i ->
        val o = json.getJSONObject(i)
        Sale(
            o.getLong("id"), o.getLong("productId"), o.getString("productName"),
            o.getInt("quantity"), o.getDouble("unitValue"),
            if (o.has("unitCost")) o.getDouble("unitCost") else 0.0,
            o.getLong("date")
        )
    }
}.getOrDefault(emptyList())

fun saveSales(context: Context, sales: List<Sale>) {
    val json = JSONArray()
    sales.forEach { s ->
        json.put(JSONObject().apply {
            put("id", s.id); put("productId", s.productId); put("productName", s.productName)
            put("quantity", s.quantity); put("unitValue", s.unitValue); put("unitCost", s.unitCost); put("date", s.date)
        })
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SALES, json.toString()).apply()
}

private fun loadExpenses(context: Context): List<Expense> = runCatching {
    val json = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(EXPENSE_LIST, "[]"))
    if (json.length() > 0) {
        return@runCatching List(json.length()) { i ->
            val o = json.getJSONObject(i)
            Expense(o.getLong("id"), o.getString("description"), o.getDouble("value"), o.getLong("date"))
        }
    }
    val old = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(EXPENSES_OLD, 0f).toDouble()
    if (old > 0) listOf(Expense(System.currentTimeMillis(), "Gasto anterior", old, System.currentTimeMillis())) else emptyList()
}.getOrDefault(emptyList())

fun saveExpenses(context: Context, expenses: List<Expense>) {
    val json = JSONArray()
    expenses.forEach { e ->
        json.put(JSONObject().apply {
            put("id", e.id); put("description", e.description); put("value", e.value); put("date", e.date)
        })
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(EXPENSE_LIST, json.toString()).apply()
}

private fun loadPayments(context: Context): List<Payment> = runCatching {
    val json = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PAYMENTS, "[]"))
    List(json.length()) { i ->
        val o = json.getJSONObject(i)
        Payment(
            o.getLong("id"),
            o.getLong("orderId"),
            o.getDouble("amount"),
            o.getLong("date"),
            o.optString("note", "")
        )
    }
}.getOrDefault(emptyList())

fun savePayments(context: Context, payments: List<Payment>) {
    val json = JSONArray()
    payments.forEach { p ->
        json.put(JSONObject().apply {
            put("id", p.id); put("orderId", p.orderId); put("amount", p.amount); put("date", p.date); put("note", p.note)
        })
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PAYMENTS, json.toString()).apply()
}

private fun loadClients(context: Context): List<Client> = runCatching {
    val json = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CLIENTS, "[]"))
    List(json.length()) { i ->
        val o = json.getJSONObject(i)
        Client(
            o.getLong("id"),
            o.getString("name"),
            o.optString("phone", ""),
            o.optString("notes", "")
        )
    }
}.getOrDefault(emptyList())

fun saveClients(context: Context, clients: List<Client>) {
    val json = JSONArray()
    clients.forEach { c ->
        json.put(JSONObject().apply {
            put("id", c.id); put("name", c.name); put("phone", c.phone); put("notes", c.notes)
        })
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CLIENTS, json.toString()).apply()
}

private fun loadOrders(context: Context): List<Order> = runCatching {
    val json = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ORDERS, "[]"))
    List(json.length()) { i ->
        val o = json.getJSONObject(i)
        Order(
            o.getLong("id"), o.optLong("customerId", 0L), o.getString("customerName"), o.getLong("productId"), o.getString("productName"),
            o.getInt("quantity"), o.getDouble("unitValue"), o.optDouble("unitCost", 0.0),
            o.getDouble("totalValue"), o.getDouble("paidValue"), o.getLong("orderDate"),
            o.getLong("deliveryDate"), o.getString("status"), o.optBoolean("stockApplied", false)
        )
    }
}.getOrDefault(emptyList())

fun saveOrders(context: Context, orders: List<Order>) {
    val json = JSONArray()
    orders.forEach { o ->
        json.put(JSONObject().apply {
            put("id", o.id); put("customerId", o.customerId); put("customerName", o.customerName); put("productId", o.productId); put("productName", o.productName)
            put("quantity", o.quantity); put("unitValue", o.unitValue); put("unitCost", o.unitCost)
            put("totalValue", o.totalValue); put("paidValue", o.paidValue); put("orderDate", o.orderDate)
            put("deliveryDate", o.deliveryDate); put("status", o.status); put("stockApplied", o.stockApplied)
        })
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ORDERS, json.toString()).apply()
}



private fun effectivePaid(order: Order, payments: List<Payment>): Double {
    val linked = payments.filter { it.orderId == order.id }.sumOf { it.amount }
    return if (linked > 0.005) linked.coerceIn(0.0, order.totalValue) else order.paidValue.coerceIn(0.0, order.totalValue)
}

private fun buildBackupJson(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return JSONObject().apply {
        put("format", "controle-queijos-backup")
        put("version", 2)
        put("appVersion", "V7.41")
        put("createdAt", System.currentTimeMillis())
        put("data", JSONObject().apply {
            put("products", JSONArray(prefs.getString(PRODUCTS, "[]")))
            put("sales", JSONArray(prefs.getString(SALES, "[]")))
            put("expenses", JSONArray(prefs.getString(EXPENSE_LIST, "[]")))
            put("orders", JSONArray(prefs.getString(ORDERS, "[]")))
            put("clients", JSONArray(prefs.getString(CLIENTS, "[]")))
            put("payments", JSONArray(prefs.getString(PAYMENTS, "[]")))
            put("counts", JSONObject().apply {
                put("products", JSONArray(prefs.getString(PRODUCTS, "[]")).length())
                put("sales", JSONArray(prefs.getString(SALES, "[]")).length())
                put("expenses", JSONArray(prefs.getString(EXPENSE_LIST, "[]")).length())
                put("orders", JSONArray(prefs.getString(ORDERS, "[]")).length())
                put("clients", JSONArray(prefs.getString(CLIENTS, "[]")).length())
                put("payments", JSONArray(prefs.getString(PAYMENTS, "[]")).length())
            })
        })
    }.toString(2)
}

private fun writeAutomaticBackup(context: Context): Result<Long> = runCatching {
    val file = context.getFileStreamPath(AUTO_BACKUP_FILE)
    context.openFileOutput(AUTO_BACKUP_FILE, Context.MODE_PRIVATE).use { output ->
        output.write(buildBackupJson(context).toByteArray(Charsets.UTF_8))
    }
    file.lastModified()
}

private fun readAutomaticBackup(context: Context): Result<String> = runCatching {
    val file = context.getFileStreamPath(AUTO_BACKUP_FILE)
    require(file.exists() && file.length() > 0) { "Ainda não existe um backup automático." }
    file.readText(Charsets.UTF_8)
}

private fun restoreBackupJson(context: Context, text: String): Result<Unit> = runCatching {
    val root = JSONObject(text)
    require(root.optString("format") == "controle-queijos-backup") { "Arquivo de backup inválido." }
    require(root.optInt("version", 1) in 1..2) { "Versão de backup não suportada." }
    val data = root.getJSONObject("data")
    val products = JSONArray(data.getJSONArray("products").toString())
    val sales = JSONArray(data.getJSONArray("sales").toString())
    val expenses = JSONArray(data.getJSONArray("expenses").toString())
    val orders = JSONArray(data.getJSONArray("orders").toString())
    val clients = JSONArray(data.getJSONArray("clients").toString())
    val payments = if (data.has("payments")) JSONArray(data.getJSONArray("payments").toString()) else JSONArray()
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString(PRODUCTS, products.toString())
        .putString(SALES, sales.toString())
        .putString(EXPENSE_LIST, expenses.toString())
        .putString(ORDERS, orders.toString())
        .putString(CLIENTS, clients.toString())
        .putString(PAYMENTS, payments.toString())
        .apply()
}

private fun buildBackupSummary(text: String): String = runCatching {
    val root = JSONObject(text)
    val createdAt = root.optLong("createdAt", 0L)
    val data = root.getJSONObject("data")
    val counts = data.optJSONObject("counts")
    val products = counts?.optInt("products", data.optJSONArray("products")?.length() ?: 0) ?: 0
    val sales = counts?.optInt("sales", data.optJSONArray("sales")?.length() ?: 0) ?: 0
    val expenses = counts?.optInt("expenses", data.optJSONArray("expenses")?.length() ?: 0) ?: 0
    val orders = counts?.optInt("orders", data.optJSONArray("orders")?.length() ?: 0) ?: 0
    val clients = counts?.optInt("clients", data.optJSONArray("clients")?.length() ?: 0) ?: 0
    val payments = counts?.optInt("payments", data.optJSONArray("payments")?.length() ?: 0) ?: 0
    buildString {
        appendLine("O backup contém:")
        appendLine("• $clients cliente(s)")
        appendLine("• $products produto(s)")
        appendLine("• $orders encomenda(s)")
        appendLine("• $sales venda(s)")
        appendLine("• $expenses gasto(s)")
        appendLine("• $payments recebimento(s)")
        if (createdAt > 0L) appendLine("Criado em: ${dateText(createdAt)}")
    }
}.getOrDefault("Não foi possível ler o resumo deste backup.")

private fun normalizeSearch(text: String): String = Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase(Locale.getDefault())
private fun normalizePhone(text: String): String = text.filter(Char::isDigit)

private fun dateText(time: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(time))
private fun dateOnly(time: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(time))
private fun sameDay(time: Long): Boolean = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(time)) == SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
private fun sameMonth(time: Long): Boolean = SimpleDateFormat("yyyyMM", Locale.US).format(Date(time)) == SimpleDateFormat("yyyyMM", Locale.US).format(Date())
private fun isOverdue(time: Long): Boolean = time < System.currentTimeMillis()
private fun isDeliveryOverdue(order: Order): Boolean = order.status != "Entregue" && order.status != "Cancelada" && isOverdue(order.deliveryDate)
private fun sameDayOffset(time: Long, offset: Int): Boolean {
    val target = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
    val value = Calendar.getInstance().apply { timeInMillis = time }
    return target.get(Calendar.YEAR) == value.get(Calendar.YEAR) &&
        target.get(Calendar.DAY_OF_YEAR) == value.get(Calendar.DAY_OF_YEAR)
}
private fun inNextSevenDays(time: Long): Boolean {
    val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }
    return time >= start.timeInMillis && time < end.timeInMillis
}
private fun inPeriod(time: Long, period: String) = period == "Todos" ||
    (period == "Hoje" && sameDay(time)) ||
    (period == "Amanhã" && sameDayOffset(time, 1)) ||
    ((period == "7 dias" || period == "Próximos 7 dias") && inNextSevenDays(time)) ||
    (period == "Mês" && sameMonth(time))
private fun parseDate(text: String, fallback: Long): Long {
    return runCatching { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).parse(text)?.time ?: fallback }.getOrDefault(fallback)
}
private fun defaultDeliveryDate(): Long {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, 7)
    return c.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControleQueijosApp(context: Context) {
    var products by remember { mutableStateOf(loadProducts(context)) }
    var sales by remember { mutableStateOf(loadSales(context)) }
    var expenses by remember { mutableStateOf(loadExpenses(context)) }
    var orders by remember { mutableStateOf(loadOrders(context)) }
    var clients by remember { mutableStateOf(loadClients(context)) }
    var payments by remember { mutableStateOf(loadPayments(context)) }
    var productDialog by remember { mutableStateOf(false) }
    var saleDialogProduct by remember { mutableStateOf<Product?>(null) }
    var orderDialogProduct by remember { mutableStateOf<Product?>(null) }
    var editingOrder by remember { mutableStateOf<Order?>(null) }
    var expenseDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var clientDialog by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<Client?>(null) }
    var paymentOrder by remember { mutableStateOf<Order?>(null) }
    var statementClient by remember { mutableStateOf<Client?>(null) }
    var period by remember { mutableStateOf("Todos") }
    var orderFilter by remember { mutableStateOf("Todas") }
    var orderSearch by remember { mutableStateOf("") }
    var orderSort by remember { mutableStateOf("Entrega") }
    var clientSearch by remember { mutableStateOf("") }
    var clientFilter by remember { mutableStateOf("Todos") }
    var productSearch by remember { mutableStateOf("") }
    var productSort by remember { mutableStateOf("Nome") }
    var receivableSearch by remember { mutableStateOf("") }
    var receivableFilter by remember { mutableStateOf("Todos") }
    var backupMessage by remember { mutableStateOf("") }
    var deleteMessage by remember { mutableStateOf("") }
    var pendingDeleteTitle by remember { mutableStateOf("") }
    var pendingDeleteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingRestoreText by remember { mutableStateOf<String?>(null) }
    var restoreConfirmation by remember { mutableStateOf(false) }
    var lastRestoreBackupAvailable by remember { mutableStateOf(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(PRE_RESTORE_BACKUP)) }
    var autoBackupAt by remember { mutableStateOf(context.getFileStreamPath(AUTO_BACKUP_FILE).lastModified()) }
    var selectedTab by remember { mutableStateOf(0) }
    var pendingPdfText by remember { mutableStateOf("") }
    var pendingCsvText by remember { mutableStateOf("") }
    val tabTitles = listOf("🏠 Início", "👥 Clientes", "📦 Encomendas", "🚚 Entregas", "🧀 Produção", "💰 Financeiro", "⚙️ Backup", "🔄 Sincronização", "💳 A Receber")
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var hasPin by remember { mutableStateOf(prefs.getString(PIN_HASH, null).orEmpty().isNotBlank()) }
    var isUnlocked by remember { mutableStateOf(!hasPin) }
    var pinDialog by remember { mutableStateOf(true) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf(prefs.getString(USER_NAME, "Administrador") ?: "Administrador") }
    var userRole by remember { mutableStateOf(prefs.getString(USER_ROLE, "Administrador") ?: "Administrador") }
    var userDialog by remember { mutableStateOf(false) }
    var rolePinDialog by remember { mutableStateOf(false) }
    var rolePinInput by remember { mutableStateOf("") }
    var rolePinError by remember { mutableStateOf("") }
    var pendingProtectedTab by remember { mutableStateOf<Int?>(null) }
    var cloudDialog by remember { mutableStateOf(false) }
    var cloudEmail by remember { mutableStateOf("") }
    var cloudPassword by remember { mutableStateOf("") }
    var cloudMessage by remember { mutableStateOf("") }
    var cloudBusy by remember { mutableStateOf(false) }
    var syncBusy by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("") }
    var cloudLoggedIn by remember { mutableStateOf(controleQueijosSupabase.auth.currentSessionOrNull() != null) }
    val cloudScope = rememberCoroutineScope()

    LaunchedEffect(products, sales, expenses, orders, clients, payments) {
        writeAutomaticBackup(context).onSuccess { autoBackupAt = it }
    }

    if (cloudDialog) {
        AlertDialog(
            onDismissRequest = { if (!cloudBusy) cloudDialog = false },
            title = { Text("☁️ Acesso à nuvem") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Use a mesma conta Supabase nos dois celulares.")
                    OutlinedTextField(cloudEmail, { cloudEmail = it }, label = { Text("E-mail") }, singleLine = true)
                    OutlinedTextField(cloudPassword, { cloudPassword = it }, label = { Text("Senha") }, singleLine = true)
                    if (cloudMessage.isNotBlank()) Text(cloudMessage, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(enabled = !cloudBusy && cloudEmail.isNotBlank() && cloudPassword.isNotBlank(), onClick = {
                    cloudBusy = true; cloudMessage = "Conectando..."
                    cloudScope.launch {
                        runCatching {
                            controleQueijosSupabase.auth.signInWith(Email) {
                                email = cloudEmail.trim(); password = cloudPassword
                            }
                        }.onSuccess {
                            cloudLoggedIn = true; cloudMessage = "Conectado com sucesso."; cloudPassword = ""
                        }.onFailure { cloudMessage = "Não foi possível entrar: " + (it.message ?: "verifique e-mail e senha.") }
                        cloudBusy = false
                    }
                }) { Text("Entrar") }
            },
            dismissButton = {
                OutlinedButton(enabled = !cloudBusy && cloudEmail.isNotBlank() && cloudPassword.length >= 6, onClick = {
                    cloudBusy = true; cloudMessage = "Criando conta..."
                    cloudScope.launch {
                        runCatching {
                            controleQueijosSupabase.auth.signUpWith(Email) {
                                email = cloudEmail.trim(); password = cloudPassword
                            }
                        }.onSuccess { cloudMessage = "Conta criada. Se o Supabase pedir confirmação, confirme o e-mail antes de entrar." }
                         .onFailure { cloudMessage = "Não foi possível criar a conta: " + (it.message ?: "verifique os dados.") }
                        cloudBusy = false
                    }
                }) { Text("Criar conta") }
            }
        )
    }

    if (userDialog) {
        AlertDialog(
            onDismissRequest = { userDialog = false },
            title = { Text("👤 Usuário e permissões") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Defina o nome deste usuário e o nível de acesso do aparelho.")
                    OutlinedTextField(value = userName, onValueChange = { userName = it }, label = { Text("Nome") }, singleLine = true)
                    Text("Perfil de acesso", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (userRole == "Administrador") Button(onClick = { userRole = "Administrador" }) { Text("Administrador") }
                        else OutlinedButton(onClick = { rolePinInput = ""; rolePinError = ""; pendingProtectedTab = null; rolePinDialog = true }) { Text("Administrador") }
                        if (userRole == "Operador") Button(onClick = { userRole = "Operador" }) { Text("Operador") }
                        else OutlinedButton(onClick = { userRole = "Operador" }) { Text("Operador") }
                    }
                    Text("Administrador: acesso completo. Operador: rotinas do dia a dia, sem Backup e Sincronização. Para elevar Operador a Administrador, será solicitado o PIN do aplicativo.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clean = userName.trim()
                    if (clean.isNotBlank()) {
                        userName = clean
                        if (userRole == "Operador" && (selectedTab == 6 || selectedTab == 7)) selectedTab = 0
                        prefs.edit().putString(USER_NAME, clean).putString(USER_ROLE, userRole).apply()
                        userDialog = false
                    }
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { userDialog = false }) { Text("Cancelar") } }
        )
    }

    if (rolePinDialog) {
        AlertDialog(
            onDismissRequest = { rolePinDialog = false; rolePinInput = ""; rolePinError = "" },
            title = { Text("🔐 Autorizar Administrador") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Digite o PIN do aplicativo para alterar este aparelho para o perfil Administrador.")
                    OutlinedTextField(
                        value = rolePinInput,
                        onValueChange = { if (it.all(Char::isDigit) && it.length <= 8) { rolePinInput = it; rolePinError = "" } },
                        label = { Text("PIN") },
                        singleLine = true
                    )
                    if (rolePinError.isNotBlank()) Text(rolePinError, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (hasPin && hashPin(rolePinInput) == prefs.getString(PIN_HASH, "")) {
                        userRole = "Administrador"
                        prefs.edit().putString(USER_ROLE, "Administrador").apply()
                        pendingProtectedTab?.let { selectedTab = it }
                        pendingProtectedTab = null
                        rolePinDialog = false
                        userDialog = false
                        rolePinInput = ""
                        rolePinError = ""
                    } else if (!hasPin) {
                        rolePinError = "Configure um PIN do aplicativo antes de usar esta proteção."
                    } else {
                        rolePinError = "PIN incorreto."
                        rolePinInput = ""
                    }
                }) { Text("Autorizar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { rolePinDialog = false; rolePinInput = ""; rolePinError = "" }) { Text("Cancelar") }
            }
        )
    }

    if (pinDialog) {
        AlertDialog(
            onDismissRequest = { if (!hasPin) { } },
            title = { Text(if (hasPin && !isUnlocked) "🔐 Controle Queijos bloqueado" else "🔐 Proteger aplicativo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (hasPin && !isUnlocked) "Digite o PIN para acessar o aplicativo." else "Crie um PIN de 4 a 8 dígitos para proteger o acesso.")
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.all(Char::isDigit) && it.length <= 8) { pinInput = it; pinError = "" } },
                        label = { Text("PIN") },
                        singleLine = true
                    )
                    if (pinError.isNotBlank()) Text(pinError, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (!hasPin) {
                        if (pinInput.length in 4..8) {
                            prefs.edit().putString(PIN_HASH, hashPin(pinInput)).apply()
                            hasPin = true
                            isUnlocked = true
                            pinDialog = false
                            pinInput = ""
                        } else pinError = "O PIN deve ter entre 4 e 8 dígitos."
                    } else if (hashPin(pinInput) == prefs.getString(PIN_HASH, "")) {
                        isUnlocked = true
                        pinDialog = false
                        pinInput = ""
                    } else {
                        pinError = "PIN incorreto."
                        pinInput = ""
                    }
                }) { Text(if (hasPin) "Entrar" else "Criar PIN") }
            }
        )
    }

        val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null && pendingCsvText.isNotBlank()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(pendingCsvText.toByteArray(Charsets.UTF_8))
                } ?: error("Não foi possível criar o arquivo CSV.")
            }.onFailure {
                backupMessage = "Não foi possível gerar o arquivo: ${it.message ?: "erro desconhecido"}"
            }
        }
    }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null && pendingPdfText.isNotBlank()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    writeSimplePdf(output, pendingPdfText)
                } ?: error("Não foi possível criar o PDF.")
            }.onFailure {
                backupMessage = "Não foi possível gerar o PDF: ${it.message ?: "erro desconhecido"}"
            }
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(buildBackupJson(context).toByteArray(Charsets.UTF_8))
                } ?: error("Não foi possível criar o arquivo.")
            }.onSuccess {
                backupMessage = "Backup criado com sucesso."
            }.onFailure {
                backupMessage = "Não foi possível criar o backup: ${it.message ?: "erro desconhecido"}"
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Não foi possível abrir o arquivo.")
            }.onSuccess { text ->
                runCatching {
                    val root = JSONObject(text)
                    require(root.optString("format") == "controle-queijos-backup") { "Arquivo de backup inválido." }
                    require(root.optInt("version", 1) in 1..2) { "Versão de backup não suportada." }
                    val data = root.getJSONObject("data")
                    data.getJSONArray("products")
                    data.getJSONArray("sales")
                    data.getJSONArray("expenses")
                    data.getJSONArray("orders")
                    data.getJSONArray("clients")
                    if (data.has("payments")) data.getJSONArray("payments")
                }.onSuccess {
                    pendingRestoreText = text
                    restoreConfirmation = true
                }.onFailure {
                    backupMessage = "Backup inválido ou incompatível: ${it.message ?: "erro desconhecido"}"
                }
            }.onFailure {
                backupMessage = "Não foi possível ler o arquivo: ${it.message ?: "erro desconhecido"}"
            }
        }
    }

    fun persistProducts(p: List<Product>) { products = p; saveProducts(context, p) }
    fun persistSales(s: List<Sale>) { sales = s; saveSales(context, s) }
    fun persistExpenses(e: List<Expense>) { expenses = e; saveExpenses(context, e) }
    fun persistOrders(o: List<Order>) { orders = o; saveOrders(context, o) }
    fun persistClients(c: List<Client>) { clients = c; saveClients(context, c) }
    fun persistPayments(p: List<Payment>) { payments = p; savePayments(context, p) }

    val filteredSales = sales.filter { inPeriod(it.date, period) }
    val filteredExpenses = expenses.filter { inPeriod(it.date, period) }
    val filteredPayments = payments.filter { inPeriod(it.date, period) }
    val filteredOrders = orders.filter { inPeriod(it.orderDate, period) }
    val activeOrders = filteredOrders.filter { it.status != "Cancelada" }
    val deliveredOrders = activeOrders.filter { it.status == "Entregue" }
    val saleRevenue = filteredSales.sumOf { it.quantity * it.unitValue }
    val saleCost = filteredSales.sumOf { it.quantity * it.unitCost }
    val orderRevenue = deliveredOrders.sumOf { it.totalValue }
    val orderCost = deliveredOrders.sumOf { it.quantity * it.unitCost }
    val orderGrossProfit = orderRevenue - orderCost
    val revenue = saleRevenue + orderRevenue
    val cost = saleCost + orderCost
    val expenseTotal = filteredExpenses.sumOf { it.value }
    val profit = revenue - cost - expenseTotal
    val entryCostTotal = cost
    val grossProfit = revenue - cost
    val profitMargin = if (revenue > 0.0) (profit / revenue) * 100.0 else 0.0
    val ordersTotal = activeOrders.sumOf { it.totalValue }
    val ordersPaid = activeOrders.sumOf { effectivePaid(it, payments) }
    val ordersReceivable = (ordersTotal - ordersPaid).coerceAtLeast(0.0)
    val pendingOrders = activeOrders.filter { it.status == "Pendente" }
    val overdueOrders = activeOrders.filter { isDeliveryOverdue(it) }
    val dueTodayOrders = pendingOrders.filter { sameDay(it.deliveryDate) }
    val deliveredOrdersCount = activeOrders.count { it.status == "Entregue" }
    val productionOrders = activeOrders.filter { it.status == "Em produção" }
    val normalizedOrderSearch = normalizeSearch(orderSearch)
    val filteredVisibleOrders = filteredOrders.filter { o ->
        val matchesFilter = when (orderFilter) {
            "Pendentes" -> o.status == "Pendente"
            "Em produção" -> o.status == "Em produção"
            "Hoje" -> sameDay(o.deliveryDate) && o.status != "Entregue" && o.status != "Cancelada"
            "Atrasadas" -> isDeliveryOverdue(o)
            "Entregues" -> o.status == "Entregue"
            "Canceladas" -> o.status == "Cancelada"
            else -> true
        }
        matchesFilter && (normalizedOrderSearch.isBlank() || normalizeSearch(o.customerName).contains(normalizedOrderSearch) || normalizeSearch(o.productName).contains(normalizedOrderSearch))
    }
    val visibleOrders = when (orderSort) {
        "Cliente" -> filteredVisibleOrders.sortedBy { normalizeSearch(it.customerName) }
        "Produto" -> filteredVisibleOrders.sortedBy { normalizeSearch(it.productName) }
        "Mais recentes" -> filteredVisibleOrders.sortedByDescending { it.orderDate }
        else -> filteredVisibleOrders.sortedWith(compareBy<Order> { if (it.status == "Pendente") 0 else 1 }.thenBy { it.deliveryDate }.thenBy { it.customerName.lowercase(Locale.getDefault()) })
    }
    val orderStatusCounts = mapOf(
        "Pendentes" to filteredOrders.count { it.status == "Pendente" },
        "Em produção" to filteredOrders.count { it.status == "Em produção" },
        "Entregues" to filteredOrders.count { it.status == "Entregue" },
        "Canceladas" to filteredOrders.count { it.status == "Cancelada" },
        "Atrasadas" to filteredOrders.count { it.status != "Entregue" && it.status != "Cancelada" && isOverdue(it.deliveryDate) }
    )
    val pendingOrdersValue = pendingOrders.sumOf { it.totalValue }
    val clientsWithBalance = activeOrders.filter { (it.totalValue - effectivePaid(it, payments)) > 0.005 }.map { it.customerName.trim().lowercase(Locale.getDefault()) }.toSet().size
    val searchText = normalizeSearch(clientSearch)
    val phoneSearch = normalizePhone(clientSearch)
    val migratedOrders = orders.map { o -> if (o.customerId != 0L) o else clients.firstOrNull { normalizeSearch(it.name) == normalizeSearch(o.customerName) }?.let { o.copy(customerId = it.id) } ?: o }
    if (migratedOrders != orders) persistOrders(migratedOrders)
    val visibleClients = clients.filter { client ->
        val clientOrders = orders.filter {
            it.status != "Cancelada" &&
                (it.customerId == client.id ||
                    (it.customerId == 0L && normalizeSearch(it.customerName) == normalizeSearch(client.name)))
        }
        val balance = (clientOrders.sumOf { it.totalValue } - clientOrders.sumOf { effectivePaid(it, payments) }).coerceAtLeast(0.0)
        val matchesFilter = when (clientFilter) {
            "Com saldo" -> balance > 0.005
            "Sem saldo" -> balance <= 0.005
            else -> true
        }
        val matchesSearch = if (clientSearch.isBlank()) true
        else normalizeSearch(client.name).contains(searchText) ||
            (phoneSearch.isNotBlank() && normalizePhone(client.phone).contains(phoneSearch))
        matchesFilter && matchesSearch
    }.sortedBy { normalizeSearch(it.name) }
    val totalClientBalance = clients.sumOf { client ->
        val clientOrders = orders.filter {
            it.status != "Cancelada" &&
                (it.customerId == client.id ||
                    (it.customerId == 0L && normalizeSearch(it.customerName) == normalizeSearch(client.name)))
        }
        val total = clientOrders.sumOf { it.totalValue }
        val registeredReceived = clientOrders.sumOf { order ->
            payments.filter { it.orderId == order.id }.sumOf { it.amount }
        }
        val legacyPaid = clientOrders.sumOf { it.paidValue }
        val received = if (registeredReceived > 0.005) registeredReceived else legacyPaid
        (total - received).coerceAtLeast(0.0)
    }
    val averageSale = if (filteredSales.isNotEmpty()) saleRevenue / filteredSales.sumOf { it.quantity } else 0.0
    val directSalesRevenue = saleRevenue
    val deliveredOrdersRevenue = orderRevenue
    val clientReceivables = activeOrders.filter { (it.totalValue - effectivePaid(it, payments)) > 0.005 }
        .groupBy { normalizeSearch(it.customerName) }
        .map { (_, group) -> group.first().customerName.trim() to (group.sumOf { it.totalValue } - group.sumOf { effectivePaid(it, payments) }).coerceAtLeast(0.0) }
        .sortedByDescending { it.second }
    val averageOrderTicket = if (deliveredOrders.isNotEmpty()) deliveredOrdersRevenue / deliveredOrders.size else 0.0
    val averageDirectSaleTicket = if (filteredSales.isNotEmpty()) directSalesRevenue / filteredSales.size else 0.0
    val receivableOverdue = clientReceivables.sumOf { (customer, _) ->
        activeOrders.filter { normalizeSearch(it.customerName) == normalizeSearch(customer) && isOverdue(it.deliveryDate) }
            .sumOf { (it.totalValue - effectivePaid(it, payments)).coerceAtLeast(0.0) }
    }
    val receivableFuture = (ordersReceivable - receivableOverdue).coerceAtLeast(0.0)
    val directSalesReceived = filteredSales.sumOf { it.quantity * it.unitValue }
    // Recebimentos entram no caixa pela data do pagamento, mesmo quando a encomenda é antiga.
    val orderReceivedInPeriod = filteredPayments.sumOf { it.amount }
    val totalCashIn = directSalesReceived + orderReceivedInPeriod
    val totalCashOut = filteredExpenses.sumOf { it.value }
    val cashBalance = totalCashIn - totalCashOut
    val financialClosingBalance = revenue - cost - expenseTotal
    val closingReceivable = ordersReceivable
    val closingOrderCount = activeOrders.size
    val closingDeliveredCount = deliveredOrders.size
    val closingPendingCount = pendingOrders.size
    val closingOverdueCount = overdueOrders.size
    val paymentCountInPeriod = filteredPayments.count()
    val receivedAverage = if (paymentCountInPeriod > 0) orderReceivedInPeriod / paymentCountInPeriod else 0.0
    val paymentsByDate = filteredPayments.sortedByDescending { it.date }
    val cashInBySource = orderReceivedInPeriod + directSalesReceived
    val overdueReceivableOrders = activeOrders.count { it.status != "Cancelada" && isOverdue(it.deliveryDate) && (it.totalValue - effectivePaid(it, payments)) > 0.005 }

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("Controle Queijos — V7.41") }) }) { pad ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ScrollableTabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                        tabTitles.forEachIndexed { index, title ->
                            val protected = index == 6 || index == 7
                            val displayTitle = if (protected && userRole != "Administrador") "🔒 " + title else title
                            Tab(
                                enabled = true,
                                selected = selectedTab == index,
                                onClick = {
                                    if (protected && userRole != "Administrador") {
                                        rolePinInput = ""
                                        rolePinError = ""
                                        pendingProtectedTab = index
                                        rolePinDialog = true
                                    } else {
                                        selectedTab = index
                                    }
                                },
                                text = { Text(displayTitle) }
                            )
                        }
                    }
                }
                if (selectedTab == 0) item {
                    Text("Painel de controle", style = MaterialTheme.typography.headlineSmall)
                    Text("Visão rápida do seu negócio", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))

                    val todayOrders = activeOrders.filter { it.status != "Entregue" && sameDay(it.deliveryDate) }
                    val todayUnits = todayOrders.sumOf { it.quantity }
                    val todayOrderValue = todayOrders.sumOf { it.totalValue }
                    val todayEntryCost = todayOrders.sumOf { it.quantity * it.unitCost }
                    val todayGrossProfit = todayOrderValue - todayEntryCost
                    val todayMargin = if (todayOrderValue > 0.0) (todayGrossProfit / todayOrderValue) * 100.0 else 0.0
                    val todayReceived = filteredPayments.filter { sameDay(it.date) }.sumOf { it.amount }
                    val todayDirectSalesReceived = filteredSales.filter { sameDay(it.date) }.sumOf { it.quantity * it.unitValue }
                    val todayCashIn = todayReceived + todayDirectSalesReceived
                    val todayExpenses = filteredExpenses.filter { sameDay(it.date) }.sumOf { it.value }
                    val todayCashBalance = todayCashIn - todayExpenses
                    val todayNetResult = todayGrossProfit - todayExpenses
                    val todayReceivable = todayOrders.sumOf { (it.totalValue - effectivePaid(it, payments)).coerceAtLeast(0.0) }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("📅 Resumo de hoje", style = MaterialTheme.typography.titleMedium)
                            Text("Entregas previstas: ${todayOrders.size}")
                            Text("Unidades: $todayUnits")
                            Text("Valor das encomendas: R$ %.2f".format(todayOrderValue))
                            Text("Custo de entrada: R$ %.2f".format(todayEntryCost))
                            Text("Lucro bruto estimado: R$ %.2f".format(todayGrossProfit))
                            Text("Margem bruta estimada: %.2f%%".format(todayMargin))
                            Text("Recebimentos de hoje: R$ %.2f".format(todayReceived))
                            Text("Entradas de caixa hoje: R$ %.2f".format(todayCashIn))
                            Text("Saídas de caixa hoje: R$ %.2f".format(todayExpenses))
                            Text("Saldo de caixa hoje: R$ %.2f".format(todayCashBalance))
                            Text("Resultado líquido estimado: R$ %.2f".format(todayNetResult))
                            Text("A receber dessas encomendas: R$ %.2f".format(todayReceivable))
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("💰 Resumo financeiro", style = MaterialTheme.typography.titleMedium)
                            Text("Faturamento: R$ %.2f".format(revenue))
                            Text("Lucro líquido: R$ %.2f".format(profit))
                            Text("Recebido no período: R$ %.2f".format(filteredPayments.sumOf { it.amount }))
                            Text("A receber: R$ %.2f".format(ordersReceivable))
                            Text("Gastos: R$ %.2f".format(expenseTotal))
                            Text("Custo total de entrada: R$ %.2f".format(entryCostTotal))
                            Text("Lucro bruto: R$ %.2f".format(grossProfit))
                            Text("Margem líquida: %.2f%%".format(profitMargin))
                            Text("A receber em atraso: R$ %.2f".format(receivableOverdue))
                            Text("A receber futuro: R$ %.2f".format(receivableFuture))
                    Text("Recebimentos no período: R$ %.2f".format(orderReceivedInPeriod))
                    Text("Saldo de caixa: R$ %.2f".format(cashBalance))
        Text("Recebimentos registrados: $paymentCountInPeriod • Média: R$ %.2f".format(receivedAverage))
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) {
                            Text("📋 Pendentes", style = MaterialTheme.typography.labelLarge)
                            Text(pendingOrders.size.toString(), style = MaterialTheme.typography.headlineSmall)
                        }}
                        Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) {
                            Text("🏭 Produção", style = MaterialTheme.typography.labelLarge)
                            Text(productionOrders.size.toString(), style = MaterialTheme.typography.headlineSmall)
                        }}
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) {
                            Text("📅 Hoje", style = MaterialTheme.typography.labelLarge)
                            Text(dueTodayOrders.size.toString(), style = MaterialTheme.typography.headlineSmall)
                        }}
                        Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) {
                            Text("⚠️ Atrasadas", style = MaterialTheme.typography.labelLarge)
                            Text(overdueOrders.size.toString(), style = MaterialTheme.typography.headlineSmall)
                        }}
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) {
                            Text("👥 Clientes", style = MaterialTheme.typography.labelLarge)
                            Text(clients.size.toString(), style = MaterialTheme.typography.headlineSmall)
                        }}
                        Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) {
                            Text("✅ Entregues", style = MaterialTheme.typography.labelLarge)
                            Text(deliveredOrdersCount.toString(), style = MaterialTheme.typography.headlineSmall)
                        }}
                    }

                    if (overdueOrders.isNotEmpty()) {
                        Card(Modifier.fillMaxWidth()) {
                            Text(
                                "⚠️ ${overdueOrders.size} encomenda(s) com entrega atrasada.",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (dueTodayOrders.isNotEmpty()) {
                        Card(Modifier.fillMaxWidth()) {
                            Text(
                                "📅 ${dueTodayOrders.size} entrega(s) prevista(s) para hoje.",
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("💳 Contas a receber", style = MaterialTheme.typography.titleMedium)
                            Text("Total em aberto: R$ %.2f".format(ordersReceivable))
                            Text("Em atraso: R$ %.2f • %d encomenda(s)".format(receivableOverdue, overdueReceivableOrders))
                            Text("A vencer: R$ %.2f".format(receivableFuture))
                            Text("Clientes com saldo: $clientsWithBalance")
                        }
                    }

                    Text("Atalhos rápidos", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { selectedTab = 2 }, modifier = Modifier.weight(1f)) { Text("📦 Encomendas") }
                        Button(onClick = { selectedTab = 1 }, modifier = Modifier.weight(1f)) { Text("👥 Clientes") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { selectedTab = 5 }, modifier = Modifier.weight(1f)) { Text("💰 Financeiro") }
                        OutlinedButton(onClick = { selectedTab = 3 }, modifier = Modifier.weight(1f)) { Text("🚚 Entregas") }
                    }

                    Text("📊 Gráficos", style = MaterialTheme.typography.titleMedium)
                    val financialChartValues = listOf(
                        "Faturamento" to revenue.coerceAtLeast(0.0),
                        "Recebido" to filteredPayments.sumOf { it.amount }.coerceAtLeast(0.0),
                        "Gastos" to expenseTotal.coerceAtLeast(0.0),
                        "A receber" to ordersReceivable.coerceAtLeast(0.0)
                    )
                    val financialChartMax = financialChartValues.maxOfOrNull { it.second } ?: 0.0
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("💰 Visão financeira", style = MaterialTheme.typography.titleMedium)
                            financialChartValues.forEach { (label, value) ->
                                DashboardBar(label, "R$ %.2f".format(value), value, financialChartMax)
                            }
                        }
                    }

                    val statusChartValues = listOf(
                        "Pendentes" to pendingOrders.size,
                        "Em produção" to productionOrders.size,
                        "Entregues" to deliveredOrdersCount,
                        "Atrasadas" to overdueOrders.size
                    )
                    val statusChartMax = statusChartValues.maxOfOrNull { it.second } ?: 0
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📦 Encomendas por status", style = MaterialTheme.typography.titleMedium)
                            statusChartValues.forEach { (label, value) ->
                                DashboardBar(label, value.toString(), value.toDouble(), statusChartMax.toDouble())
                            }
                        }
                    }

                    val productionChartValues = activeOrders
                        .filter { it.status != "Entregue" }
                        .groupBy { normalizeSearch(it.productName) }
                        .values
                        .map { group -> group.first().productName.trim() to group.sumOf { it.quantity } }
                        .sortedByDescending { it.second }
                        .take(6)
                    val productionChartMax = productionChartValues.maxOfOrNull { it.second } ?: 0
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🧀 Produção por produto", style = MaterialTheme.typography.titleMedium)
                            if (productionChartValues.isEmpty()) {
                                Text("Nenhuma produção pendente.")
                            } else {
                                productionChartValues.forEach { (label, value) ->
                                    DashboardBar(label, "$value un.", value.toDouble(), productionChartMax.toDouble())
                                }
                            }
                        }
                    }
                }
                if (selectedTab == 3) item {
                    Text("Agenda de entregas", style = MaterialTheme.typography.headlineSmall)
                    val deliveryFilter = remember { mutableStateOf("Todas") }
                    val deliveryOrders = activeOrders.filter { it.status != "Entregue" && it.status != "Cancelada" }
                    val overdueDeliveries = deliveryOrders.filter { isOverdue(it.deliveryDate) }.sortedBy { it.deliveryDate }
                    val todayDeliveries = deliveryOrders.filter { sameDay(it.deliveryDate) && !isOverdue(it.deliveryDate) }.sortedBy { it.deliveryDate }
                    val tomorrowDeliveries = deliveryOrders.filter { sameDayOffset(it.deliveryDate, 1) }.sortedBy { it.deliveryDate }
                    val nextDaysDeliveries = deliveryOrders.filter {
                        inNextSevenDays(it.deliveryDate) && !sameDay(it.deliveryDate) && !sameDayOffset(it.deliveryDate, 1)
                    }.sortedBy { it.deliveryDate }

                    val filteredDeliveries = when (deliveryFilter.value) {
                        "Atrasadas" -> overdueDeliveries
                        "Hoje" -> todayDeliveries
                        "Amanhã" -> tomorrowDeliveries
                        "Próximos dias" -> nextDaysDeliveries
                        else -> deliveryOrders.sortedBy { it.deliveryDate }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("Todas", "Atrasadas", "Hoje", "Amanhã", "Próximos dias").forEach { filter ->
                            if (deliveryFilter.value == filter) Button(onClick = { deliveryFilter.value = filter }) { Text(filter) }
                            else OutlinedButton(onClick = { deliveryFilter.value = filter }) { Text(filter) }
                        }
                    }

                    Text(
                        "Pendentes: " + deliveryOrders.count { it.status == "Pendente" } +
                            " • Em produção: " + deliveryOrders.count { it.status == "Em produção" } +
                            " • Atrasadas: " + overdueDeliveries.size +
                            " • Hoje: " + todayDeliveries.size +
                            " • Amanhã: " + tomorrowDeliveries.size,
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (filteredDeliveries.isEmpty()) {
                        Text(
                            when (deliveryFilter.value) {
                                "Atrasadas" -> "Nenhuma entrega atrasada."
                                "Hoje" -> "Nenhuma entrega prevista para hoje."
                                "Amanhã" -> "Nenhuma entrega prevista para amanhã."
                                "Próximos dias" -> "Nenhuma entrega prevista nos próximos dias."
                                else -> "Nenhuma entrega pendente."
                            }
                        )
                    } else {
                        filteredDeliveries.take(20).forEach { order ->
                            val overdue = isOverdue(order.deliveryDate)
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        (if (overdue) "⚠️ " else "🚚 ") + order.customerName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(order.productName + " • " + order.quantity + " un.")
                                    Text("Entrega: " + dateOnly(order.deliveryDate))
                                    Text("Status: " + order.status)
                                    Text("Total: R$ %.2f • Pago: R$ %.2f".format(order.totalValue, effectivePaid(order, payments)))
                                    if (overdue) Text("ENTREGA ATRASADA", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (order.status == "Pendente") {
                                            OutlinedButton(onClick = {
                                                persistOrders(orders.map { if (it.id == order.id) it.copy(status = "Em produção") else it })
                                            }) { Text("Iniciar produção") }
                                        }
                                        if (order.status == "Em produção") {
                                            OutlinedButton(onClick = {
                                                persistOrders(orders.map { if (it.id == order.id) it.copy(status = "Entregue") else it })
                                            }) { Text("Marcar entregue") }
                                        }
                                        OutlinedButton(onClick = { paymentOrder = orders.firstOrNull { it.id == order.id } }) { Text("Pagamento") }
                                    }
                                }
                            }
                        }
                        if (filteredDeliveries.size > 20) Text("Mais " + (filteredDeliveries.size - 20) + " entrega(s) nesta lista.")
                    }
                }
                if (selectedTab == 4) item {
                    Text("Produção", style = MaterialTheme.typography.headlineSmall)
                    Text("Organize o que precisa ser produzido antes das entregas.", style = MaterialTheme.typography.bodySmall)

                    val productionFilter = remember { mutableStateOf("Todos") }
                    val productionOrdersAll = activeOrders.filter { it.status != "Entregue" && it.status != "Cancelada" }
                    val pendingProduction = productionOrdersAll.filter { it.status == "Pendente" }
                    val inProduction = productionOrdersAll.filter { it.status == "Em produção" }
                    val filteredProductionOrders = when (productionFilter.value) {
                        "A produzir" -> pendingProduction
                        "Em produção" -> inProduction
                        else -> productionOrdersAll
                    }.sortedWith(compareBy<Order> { if (isOverdue(it.deliveryDate)) 0 else 1 }.thenBy { it.deliveryDate }.thenBy { normalizeSearch(it.customerName) })

                    val productionSummary = productionOrdersAll
                        .groupBy { normalizeSearch(it.productName) }
                        .values
                        .map { group -> group.first().productName.trim() to group.sumOf { it.quantity } }
                        .sortedBy { normalizeSearch(it.first) }

                    val pendingUnits = pendingProduction.sumOf { it.quantity }
                    val productionUnits = inProduction.sumOf { it.quantity }
                    val totalUnits = productionOrdersAll.sumOf { it.quantity }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("Todos", "A produzir", "Em produção").forEach { filter ->
                            if (productionFilter.value == filter) Button(onClick = { productionFilter.value = filter }) { Text(filter) }
                            else OutlinedButton(onClick = { productionFilter.value = filter }) { Text(filter) }
                        }
                    }

                    Text(
                        "A produzir: $pendingUnits un. • Em produção: $productionUnits un. • Total: $totalUnits un.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedButton(onClick = {
                        val summary = buildString {
                            appendLine("CONTROLE QUEIJOS — RESUMO DE PRODUÇÃO")
                            appendLine("Período: $period")
                            appendLine()
                            appendLine("A produzir: $pendingUnits un.")
                            appendLine("Em produção: $productionUnits un.")
                            appendLine("Total pendente de entrega: $totalUnits un.")
                            appendLine()
                            productionSummary.forEach { (name, quantity) -> appendLine("• $name — $quantity un.") }
                        }
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, summary)
                        }, "Compartilhar resumo de produção").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Compartilhar resumo de produção") }

                    Text("Produção por produto", style = MaterialTheme.typography.titleMedium)
                    if (productionSummary.isEmpty()) {
                        Text("Nenhum produto pendente de produção/entrega.")
                    } else {
                        productionSummary.forEach { (name, quantity) ->
                            Text("• $name — $quantity un.")
                        }
                    }

                    Text("Encomendas para produção", style = MaterialTheme.typography.titleMedium)
                    if (filteredProductionOrders.isEmpty()) {
                        Text(
                            when (productionFilter.value) {
                                "A produzir" -> "Nenhuma encomenda aguardando produção."
                                "Em produção" -> "Nenhuma encomenda em produção."
                                else -> "Nenhuma encomenda pendente de produção."
                            }
                        )
                    } else {
                        filteredProductionOrders.take(20).forEach { order ->
                            val overdue = isOverdue(order.deliveryDate)
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        (if (overdue) "⚠️ " else "🧀 ") + order.productName + " — " + order.quantity + " un.",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text("Cliente: " + order.customerName)
                                    Text("Entrega: " + dateOnly(order.deliveryDate))
                                    Text("Status: " + order.status + if (overdue) " • ATRASADA" else "")
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (order.status == "Pendente") {
                                            Button(onClick = {
                                                persistOrders(orders.map { if (it.id == order.id) it.copy(status = "Em produção") else it })
                                            }) { Text("Iniciar produção") }
                                        }
                                        if (order.status == "Em produção") {
                                            Button(onClick = {
                                                persistOrders(orders.map { if (it.id == order.id) it.copy(status = "Entregue") else it })
                                            }) { Text("Concluir e entregar") }
                                        }
                                    }
                                }
                            }
                        }
                        if (filteredProductionOrders.size > 20) {
                            Text("Mais " + (filteredProductionOrders.size - 20) + " encomenda(s) nesta lista.")
                        }
                    }
                }
                if (selectedTab == 5) item {
                    Text("Resumo financeiro", style = MaterialTheme.typography.headlineSmall)
                    Text("Visão financeira detalhada no período selecionado.", style = MaterialTheme.typography.bodySmall)
                    val receivedTotal = filteredPayments.sumOf { it.amount }
                    val cashSalesReceived = saleRevenue
                    val cashOrderReceived = receivedTotal
                    val cashInflow = cashSalesReceived + cashOrderReceived
                    val cashOutflow = expenseTotal
                    val cashResult = cashInflow - cashOutflow
                    Text("Faturamento: R$ %.2f".format(saleRevenue + orderRevenue))
                    Text("Recebido no período: R$ %.2f".format(receivedTotal))
                    Text("A receber: R$ %.2f".format(ordersReceivable))
                    Text("Lucro líquido: R$ %.2f".format(profit))
                    Text("Resultado de caixa: R$ %.2f".format(cashResult))
                    Text("Gastos: R$ %.2f".format(expenseTotal))

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("💵 Fluxo de Caixa", style = MaterialTheme.typography.titleMedium)
                            Text("Entradas recebidas: R$ %.2f".format(cashInflow))
                            Text("  • Vendas diretas recebidas: R$ %.2f".format(cashSalesReceived))
                            Text("  • Recebimentos de encomendas: R$ %.2f".format(cashOrderReceived))
                            Text("Saídas / despesas pagas: R$ %.2f".format(cashOutflow))
                            Text(
                                "Saldo do período: R$ %.2f".format(cashResult),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                "O fluxo considera as vendas diretas e os recebimentos registrados no período, menos os gastos lançados.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("📒 Fechamento do período", style = MaterialTheme.typography.titleMedium)
                            Text("Faturamento: R$ %.2f".format(revenue))
                            Text("Custos de entrada: R$ %.2f".format(cost))
                            Text("Despesas: R$ %.2f".format(expenseTotal))
                            Text("Lucro líquido: R$ %.2f".format(financialClosingBalance), style = MaterialTheme.typography.titleLarge)
                            Text("Saldo de caixa: R$ %.2f".format(cashBalance))
                            Text("A receber: R$ %.2f".format(closingReceivable))
                            Text("Encomendas: $closingOrderCount • Entregues: $closingDeliveredCount")
                            Text("Pendentes: $closingPendingCount • Atrasadas: $closingOverdueCount")
                            Text("Recebimentos registrados: $paymentCountInPeriod")
                            Text("Período selecionado: $period", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                                        Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🧀 CUSTO TOTAL DAS ENCOMENDAS", style = MaterialTheme.typography.titleMedium)
                            Text("Entrada (custo): R$ %.2f".format(orderCost), style = MaterialTheme.typography.titleLarge)
                            Text("Saída (venda): R$ %.2f".format(orderRevenue))
                            Text("Lucro bruto das encomendas: R$ %.2f".format(orderGrossProfit))
                            Text("Consideradas somente encomendas entregues no período selecionado.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("📊 Indicadores do período", style = MaterialTheme.typography.titleMedium)
                            Text("Faturamento: R$ %.2f".format(revenue))
                            Text("Custo de entrada: R$ %.2f".format(entryCostTotal))
                            Text("Lucro bruto: R$ %.2f".format(grossProfit))
                            Text("Despesas: R$ %.2f".format(expenseTotal))
                            Text("Lucro líquido: R$ %.2f".format(profit))
                            Text("Margem líquida: %.2f%%".format(profitMargin))
                        }
                    }
                    Text("Recebimentos registrados: " + filteredPayments.size)
                    Text("Gastos lançados: " + filteredExpenses.size)
                    Text("Encomendas canceladas: " + filteredOrders.count { it.status == "Cancelada" })
                    Spacer(Modifier.height(8.dp))
                    Text("📊 Composição do faturamento", style = MaterialTheme.typography.titleMedium)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Vendas diretas: R$ %.2f".format(directSalesRevenue))
                            Text("Encomendas entregues: R$ %.2f".format(deliveredOrdersRevenue))
                            Text("Custos das vendas/encomendas: R$ %.2f".format(cost))
                            Text("Despesas: R$ %.2f".format(expenseTotal))
                            Text("Lucro líquido: R$ %.2f".format(profit))
                            Text("Ticket médio — encomendas entregues: R$ %.2f".format(averageOrderTicket))
                            Text("Ticket médio — vendas diretas: R$ %.2f".format(averageDirectSaleTicket))
                        }
                    }
                    Text("💳 Contas a receber por cliente", style = MaterialTheme.typography.titleMedium)
                    if (clientReceivables.isEmpty()) {
                        Text("Nenhum saldo em aberto no período.")
                    } else {
                        clientReceivables.take(15).forEach { (name, balance) ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(name, modifier = Modifier.weight(1f))
                                    Text("R$ %.2f".format(balance), style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                        if (clientReceivables.size > 15) Text("Mais " + (clientReceivables.size - 15) + " cliente(s) com saldo em aberto.")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Histórico de recebimentos", style = MaterialTheme.typography.titleMedium)
                    if (filteredPayments.isEmpty()) Text("Nenhum recebimento no período.") else filteredPayments.sortedByDescending { it.date }.take(10).forEach { payment ->
                        val order = orders.firstOrNull { it.id == payment.orderId }
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) {
                            Text("R$ %.2f".format(payment.amount), style = MaterialTheme.typography.titleMedium)
                            Text(dateText(payment.date) + if (payment.note.isNotBlank()) " • " + payment.note else "")
                            if (order != null) Text("Cliente: " + order.customerName + " • " + order.productName)
                        }}
                    }
                    Text("Histórico de gastos", style = MaterialTheme.typography.titleMedium)
                    if (filteredExpenses.isEmpty()) Text("Nenhum gasto no período.") else filteredExpenses.sortedByDescending { it.date }.take(10).forEach { expense ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) {
                            Text(expense.description, style = MaterialTheme.typography.titleMedium)
                            Text("R$ %.2f • %s".format(expense.value, dateText(expense.date)))
                        }}
                    }
                }
                if (selectedTab == 5) item {
                    Text("Relatórios", style = MaterialTheme.typography.headlineSmall)
                    Text("Vendas: R$ %.2f".format(saleRevenue))
                    Text("Encomendas entregues (saída): R$ %.2f".format(orderRevenue))
                    Text("Entrada das encomendas: R$ %.2f".format(orderCost))
                    Text("Lucro das encomendas: R$ %.2f".format(orderGrossProfit))
                    Text("Gastos: R$ %.2f".format(expenseTotal))
                    Text("Lucro: R$ %.2f".format(profit))
                    Text("Recebido de encomendas: R$ %.2f".format(ordersPaid))
                    Text("Recebimentos registrados no período: R$ %.2f".format(filteredPayments.sumOf { it.amount }))
                    Text("Quantidade de recebimentos: " + filteredPayments.size)
                    Text("A receber: R$ %.2f".format(ordersReceivable))
                    Text("Encomendas pendentes: " + pendingOrders.size)
                    Text("Em produção: " + productionOrders.size)
                    Text("Valor das encomendas pendentes: R$ %.2f".format(pendingOrdersValue))
                    Text("Entregas atrasadas: " + overdueOrders.size)
                    Text("Entregas previstas para hoje: " + dueTodayOrders.size)
                    Text("Margem sobre vendas: %.2f%%".format(profitMargin))
                    Spacer(Modifier.height(8.dp))
                    Text("📦 Relatório de encomendas", style = MaterialTheme.typography.titleMedium)
                    Text("Resumo de status, unidades, venda, custo de entrada, lucro e valores a receber.", style = MaterialTheme.typography.bodySmall)
                    val ordersReportActive = activeOrders
                    val ordersReportPending = ordersReportActive.count { it.status == "Pendente" }
                    val ordersReportProduction = ordersReportActive.count { it.status == "Em produção" }
                    val ordersReportDelivered = ordersReportActive.count { it.status == "Entregue" }
                    val ordersReportOverdue = ordersReportActive.count { isDeliveryOverdue(it) }
                    val ordersReportUnits = ordersReportActive.sumOf { it.quantity }
                    val ordersReportSales = ordersReportActive.sumOf { it.totalValue }
                    val ordersReportEntry = ordersReportActive.sumOf { it.quantity * it.unitCost }
                    val ordersReportProfit = ordersReportSales - ordersReportEntry
                    val ordersReportReceivable = ordersReportActive.sumOf { (it.totalValue - effectivePaid(it, payments)).coerceAtLeast(0.0) }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Encomendas: " + ordersReportActive.size + " • Unidades: " + ordersReportUnits)
                            Text("Pendentes: $ordersReportPending • Em produção: $ordersReportProduction • Entregues: $ordersReportDelivered")
                            Text("Atrasadas: $ordersReportOverdue")
                            Text("Saída (venda): R$ %.2f".format(ordersReportSales))
                            Text("Entrada (custo): R$ %.2f".format(ordersReportEntry))
                            Text("Lucro bruto: R$ %.2f".format(ordersReportProfit))
                            Text("A receber: R$ %.2f".format(ordersReportReceivable))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = {
                            val report = buildOrdersReportText(period, ordersReportActive)
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, report)
                            }, "Compartilhar relatório de encomendas").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }, modifier = Modifier.weight(1f)) { Text("Compartilhar") }
                        OutlinedButton(onClick = {
                            pendingPdfText = buildOrdersReportText(period, ordersReportActive)
                            exportPdfLauncher.launch("controle-queijos-encomendas-relatorio.pdf")
                        }, modifier = Modifier.weight(1f)) { Text("PDF") }
                    }
                    OutlinedButton(onClick = {
                        pendingCsvText = buildOrdersReportCsv(period, ordersReportActive)
                        exportCsvLauncher.launch("controle-queijos-encomendas-relatorio.csv")
                    }, modifier = Modifier.fillMaxWidth()) { Text("Exportar relatório de encomendas para Excel") }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = {
                            val report = buildReportText(period, activeOrders)
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, report)
                            }, "Compartilhar lista de produção").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }, modifier = Modifier.weight(1f)) { Text("Lista de produção") }
                        OutlinedButton(onClick = {
                            val report = buildFinancialReportText(
                                period, saleRevenue, orderRevenue, orderCost, cost, expenseTotal,
                                profit, profitMargin, ordersTotal, ordersPaid, ordersReceivable,
                                pendingOrders.size, productionOrders.size, overdueOrders.size, dueTodayOrders.size,
                                filteredPayments.sumOf { it.amount }, filteredPayments.size
                            )
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, report)
                            }, "Compartilhar relatório financeiro").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }, modifier = Modifier.weight(1f)) { Text("Relatório financeiro") }
                    }
                    OutlinedButton(onClick = {
                        val report = buildOperationalReportText(period, activeOrders)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                        }, "Compartilhar relatório operacional").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Relatório operacional") }
                    Spacer(Modifier.height(6.dp))
                    Text("Exportar em PDF", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = {
                            pendingPdfText = buildReportText(period, activeOrders)
                            exportPdfLauncher.launch("controle-queijos-producao.pdf")
                        }, modifier = Modifier.weight(1f)) { Text("PDF produção") }
                        OutlinedButton(onClick = {
                            pendingPdfText = buildFinancialReportText(
                                period, saleRevenue, orderRevenue, orderCost, cost, expenseTotal,
                                profit, profitMargin, ordersTotal, ordersPaid, ordersReceivable,
                                pendingOrders.size, productionOrders.size, overdueOrders.size, dueTodayOrders.size,
                                filteredPayments.sumOf { it.amount }, filteredPayments.size
                            )
                            exportPdfLauncher.launch("controle-queijos-financeiro.pdf")
                        }, modifier = Modifier.weight(1f)) { Text("PDF financeiro") }
                    }
                    OutlinedButton(onClick = {
                        pendingPdfText = buildOperationalReportText(period, activeOrders)
                        exportPdfLauncher.launch("controle-queijos-operacional.pdf")
                    }, modifier = Modifier.fillMaxWidth()) { Text("PDF operacional") }
                    Spacer(Modifier.height(8.dp))
                    Text("Exportar para Excel", style = MaterialTheme.typography.titleMedium)
                    Text("Arquivos CSV abrem normalmente no Excel, Google Planilhas e outros aplicativos de planilha.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = {
                            pendingCsvText = buildOrdersCsv(period, activeOrders, filteredPayments)
                            exportCsvLauncher.launch("controle-queijos-encomendas.csv")
                        }, modifier = Modifier.weight(1f)) { Text("Encomendas") }
                        OutlinedButton(onClick = {
                            pendingCsvText = buildFinancialCsv(period, activeOrders, filteredPayments, expenses)
                            exportCsvLauncher.launch("controle-queijos-financeiro.csv")
                        }, modifier = Modifier.weight(1f)) { Text("Financeiro") }
                    }
                    OutlinedButton(onClick = {
                        pendingCsvText = buildProductionCsv(period, activeOrders)
                        exportCsvLauncher.launch("controle-queijos-producao.csv")
                    }, modifier = Modifier.fillMaxWidth()) { Text("Produção consolidada") }
                }
                if (selectedTab == 7 && userRole == "Administrador") item {
            Text("☁️ Nuvem", style = MaterialTheme.typography.headlineSmall)
            Text("Use a mesma conta nos dois celulares para acessar os dados da empresa.", style = MaterialTheme.typography.bodySmall)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (cloudLoggedIn) "🟢 Conectado à nuvem" else "⚪ Não conectado", style = MaterialTheme.typography.titleMedium)
                    Text("O Supabase identifica o usuário e o RLS protege os dados.")
                    Button(onClick = { cloudDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (cloudLoggedIn) "Gerenciar acesso à nuvem" else "Entrar na nuvem")
                    }
                    if (cloudLoggedIn) {
                        OutlinedButton(onClick = {
                            cloudScope.launch {
                                runCatching { controleQueijosSupabase.auth.signOut() }
                                    .onSuccess { cloudLoggedIn = false; cloudMessage = "Sessão encerrada neste aparelho." }
                                    .onFailure { cloudMessage = it.message ?: "Não foi possível sair." }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Sair da nuvem") }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🔄 Sincronização na nuvem", style = MaterialTheme.typography.titleMedium)
                    Text("Envie os dados deste celular e baixe os dados da mesma conta Supabase.")
                    Button(
                        enabled = cloudLoggedIn && !syncBusy,
                        onClick = {
                            syncBusy = true
                            syncMessage = "Sincronizando..."
                            cloudScope.launch {
                                uploadControleQueijos(products, sales, expenses, orders, clients, payments)
                                    .onSuccess {
                                        downloadControleQueijos()
                                            .onSuccess { data ->
                                                saveCloudDataLocally(context, data)
                                                products = data.products
                                                sales = data.sales
                                                expenses = data.expenses
                                                orders = data.orders
                                                clients = data.clients
                                                payments = data.payments
                                                syncMessage = "Sincronização concluída. Dados atualizados."
                                            }
                                            .onFailure { syncMessage = "Falha ao baixar: " + (it.message ?: "erro desconhecido.") }
                                    }
                                    .onFailure { syncMessage = "Falha ao enviar: " + (it.message ?: "erro desconhecido.") }
                                syncBusy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (syncBusy) "Sincronizando..." else "☁️ Sincronizar agora") }
                    if (syncMessage.isNotBlank()) Text(syncMessage, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { exportBackupLauncher.launch("Controle-Queijos-Sincronizacao.json") }, modifier = Modifier.fillMaxWidth()) { Text("Exportar backup") }
                    OutlinedButton(onClick = { importBackupLauncher.launch(arrayOf("application/json", "text/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Importar backup") }
                }
            }
        }

    if (selectedTab == 8) item {
        val openReceivables = orders.filter { it.status != "Cancelada" && (it.totalValue - effectivePaid(it, payments)) > 0.005 }
        val normalizedReceivableSearch = normalizeSearch(receivableSearch)
        val visibleReceivables = openReceivables
            .filter { normalizedReceivableSearch.isBlank() || normalizeSearch(it.customerName).contains(normalizedReceivableSearch) || normalizeSearch(it.productName).contains(normalizedReceivableSearch) }
            .filter {
                when (receivableFilter) {
                    "Atrasadas" -> isDeliveryOverdue(it)
                    "Entregues" -> it.status == "Entregue"
                    "Pendentes" -> it.status != "Entregue"
                    else -> true
                }
            }
            .sortedWith(compareByDescending<Order> { isDeliveryOverdue(it) }.thenBy { it.deliveryDate })

        val totalReceivable = openReceivables.sumOf { (it.totalValue - it.paidValue).coerceAtLeast(0.0) }
        val overdueReceivable = openReceivables.filter { isDeliveryOverdue(it) }.sumOf { (it.totalValue - it.paidValue).coerceAtLeast(0.0) }
        val deliveredReceivable = openReceivables.filter { it.status == "Entregue" }.sumOf { (it.totalValue - it.paidValue).coerceAtLeast(0.0) }
        val receivableByClient = openReceivables.groupBy { normalizeSearch(it.customerName) }.map { (_, group) ->
            group.first().customerName.trim() to group.sumOf { (it.totalValue - it.paidValue).coerceAtLeast(0.0) }
        }.sortedByDescending { it.second }

        Text("💳 Contas a Receber", style = MaterialTheme.typography.headlineSmall)
        Text("Acompanhe saldos pendentes, atrasados e pagamentos dos clientes.", style = MaterialTheme.typography.bodySmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Total em aberto: R$ %.2f".format(totalReceivable), style = MaterialTheme.typography.titleLarge)
                Text("Atrasado: R$ %.2f".format(overdueReceivable))
                Text("Entregue e ainda não quitado: R$ %.2f".format(deliveredReceivable))
                Text("Clientes com saldo: " + receivableByClient.size)
            }
        }
        OutlinedTextField(
            value = receivableSearch,
            onValueChange = { receivableSearch = it },
            label = { Text("Pesquisar cliente ou produto") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Todos", "Atrasadas", "Pendentes", "Entregues").forEach { filter ->
                FilterChip(selected = receivableFilter == filter, onClick = { receivableFilter = filter }, label = { Text(filter) })
            }
        }
        Text("Por cliente", style = MaterialTheme.typography.titleMedium)
        if (receivableByClient.isEmpty()) Text("Nenhum cliente com saldo em aberto.") else receivableByClient.take(20).forEach { (name, balance) ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, modifier = Modifier.weight(1f))
                    Text("R$ %.2f".format(balance), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Text("Encomendas em aberto", style = MaterialTheme.typography.titleMedium)
        if (visibleReceivables.isEmpty()) Text("Nenhuma encomenda encontrada.") else visibleReceivables.take(30).forEach { order ->
            val balance = (order.totalValue - effectivePaid(order, payments)).coerceAtLeast(0.0)
            val overdue = isDeliveryOverdue(order)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(order.customerName, style = MaterialTheme.typography.titleMedium)
                    Text(order.productName + " • " + order.quantity + " un.")
                    Text("Total: R$ %.2f • Pago: R$ %.2f".format(order.totalValue, order.paidValue))
                    Text("Saldo: R$ %.2f".format(balance) + if (overdue) " • ATRASADA" else " • " + order.status)
                    Text("Entrega: " + dateOnly(order.deliveryDate))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { paymentOrder = order }) { Text("Registrar pagamento") }
                        OutlinedButton(onClick = {
                            statementClient = clients.firstOrNull { it.id == order.customerId }
                                ?: clients.firstOrNull { normalizeSearch(it.name) == normalizeSearch(order.customerName) }
                        }) { Text("Extrato") }
                    }
                }
            }
        }
        Text("Histórico recente de recebimentos", style = MaterialTheme.typography.titleMedium)
        val recentPayments = payments.sortedByDescending { it.date }.take(10)
        if (recentPayments.isEmpty()) Text("Nenhum recebimento registrado.") else recentPayments.forEach { payment ->
            val order = orders.firstOrNull { it.id == payment.orderId }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("R$ %.2f".format(payment.amount), style = MaterialTheme.typography.titleMedium)
                    Text(dateText(payment.date) + if (payment.note.isNotBlank()) " • " + payment.note else "")
                    if (order != null) Text("Cliente: " + order.customerName)
                }
            }
        }
    }

    if (selectedTab == 6 && userRole == "Administrador") item {            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("✅ Versão estável V7.50", style = MaterialTheme.typography.titleMedium)
                    Text("Sistema revisado para uso diário: encomendas, entregas, produção, financeiro, clientes, relatórios, PDF, CSV, backup, sincronização e proteção por PIN.")
                    Text("Usuário atual: $userName • Perfil: $userRole", style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("👤 Usuário deste aparelho", style = MaterialTheme.typography.titleMedium); Text("$userName • $userRole"); OutlinedButton(onClick = { userDialog = true }) { Text("Gerenciar usuário") } } }
                    Text("Backup e transferência", style = MaterialTheme.typography.headlineSmall)
                    Text("Backup automático e manual para proteger os dados do Controle Queijos.")
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🛡️ Backup automático", style = MaterialTheme.typography.titleMedium)
                            if (autoBackupAt > 0L) {
                                Text("Último backup automático: " + dateText(autoBackupAt))
                            } else {
                                Text("O primeiro backup automático será criado assim que os dados forem carregados.")
                            }
                            Text("O backup automático é atualizado quando os dados do aplicativo são alterados.")
                            OutlinedButton(onClick = {
                                readAutomaticBackup(context).onSuccess { text ->
                                    pendingRestoreText = text
                                    restoreConfirmation = true
                                }.onFailure {
                                    backupMessage = it.message ?: "Backup automático indisponível."
                                }
                            }) { Text("Restaurar backup automático") }
                        }
                    }
                    Text("Backup manual", style = MaterialTheme.typography.titleMedium)
                    Text("O backup manual gera um arquivo JSON que pode ser guardado em outro local ou enviado para outro celular. O backup guarda clientes, encomendas, vendas e gastos.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportBackupLauncher.launch("controle-queijos-backup.json") }) {
                            Text("Fazer backup")
                        }
                        OutlinedButton(onClick = {
                            importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }) {
                            Text("Restaurar backup")
                        }
                    }
                    if (backupMessage.isNotBlank()) {
                        Text(backupMessage)
                    }
                }
                item {
                    Text("Período", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Todos", "Hoje", "Amanhã", "Próximos 7 dias", "Mês").forEach { p ->
                            if (period == p) Button(onClick = { period = p }) { Text(p) }
                            else OutlinedButton(onClick = { period = p }) { Text(p) }
                        }
                    }
                }
                if (selectedTab == 2) item {
                    Button(onClick = { editingProduct = null; productDialog = true }) { Text("Cadastrar produto") }
                }
                if (selectedTab == 1) item {
                    Button(onClick = { editingClient = null; clientDialog = true }) { Text("Cadastrar cliente") }
                }
                if (selectedTab == 5) item {
                    OutlinedButton(onClick = { expenseDialog = true }) { Text("Novo gasto") }
                }

                if (selectedTab == 2) item {
                    Text("Produtos / Catálogo", style = MaterialTheme.typography.headlineSmall)
                    Text("O estoque é opcional. O foco continua sendo vendas e encomendas.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = productSearch, onValueChange = { productSearch = it }, label = { Text("Buscar produto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Nome", "Mais vendidos", "Mais encomendados", "Maior margem").forEach { sort ->
                            if (productSort == sort) Button(onClick = { productSort = sort }) { Text(sort) }
                            else OutlinedButton(onClick = { productSort = sort }) { Text(sort) }
                        }
                    }
                }
                val visibleProducts = products.filter { normalizeSearch(it.name).contains(normalizeSearch(productSearch)) }.let { list ->
        when (productSort) {
            "Mais vendidos" -> list.sortedByDescending { p -> sales.filter { it.productId == p.id }.sumOf { it.quantity } }
            "Mais encomendados" -> list.sortedByDescending { p -> orders.filter { it.productId == p.id && it.status != "Cancelada" }.sumOf { it.quantity } }
            "Maior margem" -> list.sortedByDescending { p -> if (p.exitValue > 0.0) (p.exitValue - p.entryValue) / p.exitValue else 0.0 }
            else -> list.sortedBy { normalizeSearch(it.name) }
        }
    }
                if (selectedTab == 2) item {
                    val productsWithStock = products.count { it.quantity > 0 }
                    val lowStock = products.count { it.quantity in 1..5 }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("📊 Resumo dos produtos", style = MaterialTheme.typography.titleMedium)
                            Text("Cadastrados: " + products.size + " • Com quantidade informada: " + productsWithStock + " • Estoque baixo: " + lowStock)
                            val totalOrderedUnits = products.sumOf { product ->
                                orders.filter { it.productId == product.id && it.status != "Cancelada" }.sumOf { it.quantity }
                            }
                            val totalSoldUnits = products.sumOf { product ->
                                sales.filter { it.productId == product.id }.sumOf { it.quantity }
                            }
                            Text("Encomendado: $totalOrderedUnits un. • Vendido: $totalSoldUnits un.")
                        }
                    }
                }
                if (selectedTab == 2 && products.isEmpty()) item { Text("Nenhum produto cadastrado.") }
                if (selectedTab == 2 && products.isNotEmpty() && visibleProducts.isEmpty()) item { Text("Nenhum produto encontrado.") }
                if (selectedTab == 2) items(visibleProducts, key = { it.id }) { p ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text("Disponibilidade: por encomenda")
                            if (p.quantity in 1..5) {
                                Text("⚠️ Quantidade informada baixa: " + p.quantity + " un.", color = MaterialTheme.colorScheme.error)
                            } else if (p.quantity > 5) {
                                Text("Quantidade informada: " + p.quantity + " un.")
                            } else {
                                Text("Quantidade: não informada")
                            }
                            Text("Custo: R$ %.2f/un.".format(p.entryValue))
                            Text("Saída: R$ %.2f/un.".format(p.exitValue))
                            Text("Lucro por unidade: R$ %.2f".format(p.exitValue - p.entryValue))
                            val margin = if (p.exitValue > 0.0) ((p.exitValue - p.entryValue) / p.exitValue) * 100.0 else 0.0
                            Text("Margem sobre a venda: %.1f%%".format(margin))
                            val soldUnits = sales.filter { it.productId == p.id }.sumOf { it.quantity }
                            val orderedUnits = orders.filter { it.productId == p.id && it.status != "Cancelada" }.sumOf { it.quantity }
                            val deliveredUnits = orders.filter { it.productId == p.id && it.status == "Entregue" }.sumOf { it.quantity }
                            val productSalesRevenue = sales.filter { it.productId == p.id }.sumOf { it.quantity * it.unitValue }
                            val productOrderRevenue = orders.filter { it.productId == p.id && it.status == "Entregue" }.sumOf { it.totalValue }
                            val productRevenue = productSalesRevenue + productOrderRevenue
                            Text("Vendido: $soldUnits un. • Encomendado: $orderedUnits un. • Entregue: $deliveredUnits un.")
                            Text("Faturamento gerado: R$ %.2f".format(productRevenue))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { saleDialogProduct = p }) { Text("Registrar venda") }
                                OutlinedButton(onClick = { orderDialogProduct = p }) { Text("Encomenda") }
                                OutlinedButton(onClick = { editingProduct = p; productDialog = true }) { Text("Editar") }
                            }
                            OutlinedButton(
                                onClick = {
                                    val hasOrders = orders.any { it.productId == p.id }
                                    val hasSales = sales.any { it.productId == p.id }
                                    if (hasOrders || hasSales) {
                                        deleteMessage = "Não é possível excluir este produto porque existem encomendas ou vendas vinculadas a ele. Exclua primeiro esses registros."
                                    } else {
                                        pendingDeleteTitle = "Excluir produto?"
                                        pendingDeleteAction = { persistProducts(products.filterNot { it.id == p.id }) }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Excluir produto") }
                        }
                    }
                }

                if (selectedTab == 1) item {
                    Text("Clientes (" + clients.size + ")", style = MaterialTheme.typography.headlineSmall)
                    Text("Total a receber: R$ %.2f".format(totalClientBalance))
                    val clientsWithOpenBalance = clients.count { client ->
                        val clientOrders = orders.filter {
                            it.status != "Cancelada" &&
                                (it.customerId == client.id ||
                                    (it.customerId == 0L && normalizeSearch(it.customerName) == normalizeSearch(client.name)))
                        }
                        val total = clientOrders.sumOf { it.totalValue }
                        val received = clientOrders.sumOf { order ->
                            val linked = payments.filter { it.orderId == order.id }.sumOf { it.amount }
                            if (linked > 0.005) linked else order.paidValue
                        }
                        (total - received) > 0.005
                    }
                    Text("Clientes com saldo: " + clientsWithOpenBalance)
                    OutlinedTextField(
                        clientSearch,
                        { clientSearch = it },
                        label = { Text("Buscar cliente ou telefone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Todos", "Com saldo", "Sem saldo").forEach { f ->
                            if (clientFilter == f) Button(onClick = { clientFilter = f }) { Text(f) }
                            else OutlinedButton(onClick = { clientFilter = f }) { Text(f) }
                        }
                    }
                    Text("Exibindo: " + visibleClients.size + " cliente(s)")
                    if (clients.isNotEmpty()) {
                        Text("Resumo dos clientes", style = MaterialTheme.typography.titleMedium)
                        Text("Com saldo: " + clientsWithOpenBalance + " • Sem saldo: " + (clients.size - clientsWithOpenBalance))
                    }
                }
                if (selectedTab == 1 && clients.isEmpty()) item {
                    Text(if (clientSearch.isBlank()) "Nenhum cliente cadastrado." else "Nenhum cliente encontrado.")
                }
                if (selectedTab == 1) items(visibleClients, key = { it.id }) { client ->
                    val allClientOrders = orders.filter {
                        it.customerId == client.id ||
                            (it.customerId == 0L && normalizeSearch(it.customerName) == normalizeSearch(client.name))
                    }.sortedByDescending { it.orderDate }
                    val clientOrders = allClientOrders.filter { it.status != "Cancelada" }
                    val total = clientOrders.sumOf { it.totalValue }
                    val registeredReceived = clientOrders.sumOf { order ->
                        payments.filter { it.orderId == order.id }.sumOf { it.amount }
                    }
                    val legacyPaid = clientOrders.sumOf { it.paidValue }
                    val received = if (registeredReceived > 0.005) registeredReceived else legacyPaid
                    val balance = (total - received).coerceAtLeast(0.0)
                    val lastOrder = allClientOrders.firstOrNull()
                    val pendingOrders = clientOrders.count { it.status == "Pendente" || it.status == "Em produção" }
                    val clientPaymentHistory = payments.filter { p -> clientOrders.any { it.id == p.orderId } }.sortedByDescending { it.date }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(client.name, style = MaterialTheme.typography.titleMedium)
                            if (client.phone.isNotBlank()) Text("Telefone: " + client.phone)
                            Text("Total comprado: R$ %.2f".format(total))
                            Text("Recebido: R$ %.2f".format(received))
                            Text("Saldo em aberto: R$ %.2f".format(balance), color = if (balance > 0.005) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            Text("Encomendas: " + clientOrders.size + " • Pendentes: " + pendingOrders)
                            val averageClientOrder = if (clientOrders.isNotEmpty()) total / clientOrders.size else 0.0
                            Text("Ticket médio: R$ %.2f".format(averageClientOrder))
                            if (lastOrder != null) Text("Última encomenda: " + dateOnly(lastOrder.orderDate) + " • " + lastOrder.productName + " • " + lastOrder.quantity + " un.")
                            if (clientPaymentHistory.isNotEmpty()) {
                                Text("Últimos recebimentos", style = MaterialTheme.typography.labelLarge)
                                clientPaymentHistory.take(5).forEach { p ->
                                    Text(dateText(p.date) + " — R$ %.2f".format(p.amount) + if (p.note.isNotBlank()) " — " + p.note else "", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (allClientOrders.isNotEmpty()) {
                                Text("Histórico de encomendas", style = MaterialTheme.typography.labelLarge)
                                allClientOrders.take(10).forEach { order ->
                                    Text(dateOnly(order.orderDate) + " • " + order.productName + " • " + order.quantity + " un. • R$ %.2f • ".format(order.totalValue) + order.status, style = MaterialTheme.typography.bodySmall)
                                }
                                if (allClientOrders.size > 10) Text("Mais " + (allClientOrders.size - 10) + " encomenda(s) no histórico.", style = MaterialTheme.typography.bodySmall)
                            }
                            if (client.notes.isNotBlank()) Text("Obs.: " + client.notes)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { editingClient = client; clientDialog = true }) { Text("Editar") }
                                OutlinedButton(onClick = { statementClient = client }) { Text("Extrato") }
                                OutlinedButton(onClick = {
                                    val message = buildClientStatementMessage(client, clientOrders, clientPaymentHistory)
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, message)
                                    }, "Compartilhar extrato").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }) { Text("Compartilhar") }
                            }
                            OutlinedButton(
                                onClick = {
                                    if (allClientOrders.isEmpty()) {
                                        pendingDeleteTitle = "Excluir cliente?"
                                        pendingDeleteAction = { persistClients(clients.filterNot { it.id == client.id }) }
                                    } else {
                                        deleteMessage = "Este cliente possui histórico de encomendas. Exclua ou cancele as encomendas antes de excluir o cadastro do cliente."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Excluir cliente") }
                        }
                    }
                }
if (selectedTab == 2) item {
                    Text("Encomendas", style = MaterialTheme.typography.headlineSmall)
                    val activeOrders = orders.filter { it.status != "Cancelada" }
                    val overdueCount = activeOrders.count { isDeliveryOverdue(it) }
                    val todayCount = activeOrders.count { it.status != "Entregue" && sameDay(it.deliveryDate) }
                    val tomorrowCount = activeOrders.count { it.status != "Entregue" && sameDayOffset(it.deliveryDate, 1) }
                    val nextSevenCount = activeOrders.count { it.status != "Entregue" && inNextSevenDays(it.deliveryDate) }
                    val pendingUnits = activeOrders.filter { it.status == "Pendente" || it.status == "Em produção" }.sumOf { it.quantity }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("📅 Agenda de entregas", style = MaterialTheme.typography.titleMedium)
                            Text("Hoje: $todayCount • Amanhã: $tomorrowCount • Próximos 7 dias: $nextSevenCount")
                            Text("Atrasadas: $overdueCount • Unidades pendentes de entrega: $pendingUnits")
                            Text("Total de encomendas ativas: " + activeOrders.size + " • Valor: R$ %.2f".format(activeOrders.sumOf { it.totalValue }))
                            Text("Recebido: R$ %.2f • A receber: R$ %.2f".format(activeOrders.sumOf { effectivePaid(it, payments) }, activeOrders.sumOf { (it.totalValue - effectivePaid(it, payments)).coerceAtLeast(0.0) }))
                        }
                    }
                    OutlinedTextField(
                        value = orderSearch,
                        onValueChange = { orderSearch = it },
                        label = { Text("Buscar cliente ou produto") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Todas", "Pendentes", "Em produção", "Hoje", "Amanhã", "7 dias", "Atrasadas", "Entregues", "Canceladas").forEach { f ->
                            if (orderFilter == f) Button(onClick = { orderFilter = f }) { Text(f) }
                            else OutlinedButton(onClick = { orderFilter = f }) { Text(f) }
                        }
                    }
                    Text("Exibindo: " + visibleOrders.size + " encomenda(s)")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("Entrega", "Mais recentes", "Cliente", "Produto").forEach { s ->
                            if (orderSort == s) Button(onClick = { orderSort = s }) { Text(s) }
                            else OutlinedButton(onClick = { orderSort = s }) { Text(s) }
                        }
                    }
                    Text(
                        "Pendentes: " + orderStatusCounts["Pendentes"] + " • Em produção: " + orderStatusCounts["Em produção"] +
                            " • Entregues: " + orderStatusCounts["Entregues"] + " • Atrasadas: " + orderStatusCounts["Atrasadas"] +
                            " • Canceladas: " + orderStatusCounts["Canceladas"],
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (selectedTab == 2 && visibleOrders.isEmpty()) item { Text("Nenhuma encomenda encontrada.") }
                if (selectedTab == 2) items(visibleOrders, key = { it.id }) { o ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(o.customerName, style = MaterialTheme.typography.titleMedium)
                            Text(o.productName + " — " + o.quantity + " un.")
                            Text("Total: R$ %.2f".format(o.totalValue))
                            Text("Pago: R$ %.2f | Saldo: R$ %.2f".format(o.paidValue, o.totalValue - o.paidValue))
                            val orderPayments = payments.filter { it.orderId == o.id }.sortedByDescending { it.date }
                            if (orderPayments.isNotEmpty()) {
                                Text("Histórico de pagamentos", style = MaterialTheme.typography.labelLarge)
                                orderPayments.take(5).forEach { p -> Text(dateText(p.date) + " — R$ %.2f".format(p.amount) + if (p.note.isNotBlank()) " — " + p.note else "", style = MaterialTheme.typography.bodySmall) }
                            }
                            Text("Pedido: " + dateOnly(o.orderDate) + " | Entrega: " + dateOnly(o.deliveryDate))
                            Text("Status: " + o.status + if (isDeliveryOverdue(o)) " • ATRASADA" else "")
                            Text(
                                if (o.totalValue - o.paidValue <= 0.005) "Pagamento: QUITADO"
                                else "Pagamento: PENDENTE — R$ %.2f".format(o.totalValue - o.paidValue)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = {
                                    val message = buildOrderMessage(o)
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, message)
                                    }, "Compartilhar encomenda").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }) { Text("Compartilhar") }
                                if (o.status != "Cancelada" && o.totalValue - o.paidValue > 0.005) {
                                    OutlinedButton(onClick = { paymentOrder = o }) { Text("Registrar pagamento") }
                                }
                                if (o.status != "Entregue" && o.status != "Cancelada") {
                                    if (o.status == "Pendente") {
                                        Button(onClick = {
                                            persistOrders(orders.map { if (it.id == o.id) o.copy(status = "Em produção") else it })
                                        }) { Text("Iniciar produção") }
                                    }
                                    if (o.status == "Em produção") {
                                        Button(onClick = {
                                            persistOrders(orders.map { if (it.id == o.id) o.copy(status = "Entregue", stockApplied = false) else it })
                                        }) { Text("Marcar entregue") }
                                    }
                                    OutlinedButton(onClick = {
                                        persistOrders(orders.map { if (it.id == o.id) o.copy(status = "Cancelada") else it })
                                    }) { Text("Cancelar") }
                                }
                                OutlinedButton(onClick = { editingOrder = o }) { Text("Editar") }
                            }
                            OutlinedButton(
                                onClick = {
                                    pendingDeleteTitle = "Excluir encomenda?"
                                    pendingDeleteAction = {
                                        if (o.stockApplied) {
                                            val p = products.find { it.id == o.productId }
                                            if (p != null) persistProducts(products.map { if (it.id == p.id) p.copy(quantity = p.quantity + o.quantity) else it })
                                        }
                                        persistOrders(orders.filterNot { it.id == o.id })
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Excluir encomenda") }
                        }
                    }
                }

                if (selectedTab == 5) item { Text("Vendas registradas (" + filteredSales.size + ")", style = MaterialTheme.typography.headlineSmall) }
                if (selectedTab == 5 && filteredSales.isEmpty()) item { Text("Nenhuma venda no período.") }
                if (selectedTab == 5) items(filteredSales.sortedByDescending { it.date }, key = { it.id }) { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(s.productName + " — " + s.quantity + " un.")
                            Text("R$ %.2f".format(s.quantity * s.unitValue))
                            Text(dateText(s.date), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(
                                onClick = {
                                    pendingDeleteTitle = "Excluir venda?"
                                    pendingDeleteAction = {
                                        persistSales(sales.filterNot { it.id == s.id })
                                        val p = products.find { it.id == s.productId }
                                        if (p != null) persistProducts(products.map { if (it.id == p.id) p.copy(quantity = p.quantity + s.quantity) else it })
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Excluir venda") }
                        }
                    }
                }

                if (selectedTab == 5) item { Text("Gastos registrados (" + filteredExpenses.size + ")", style = MaterialTheme.typography.headlineSmall) }
                if (selectedTab == 5 && filteredExpenses.isEmpty()) item { Text("Nenhum gasto no período.") }
                if (selectedTab == 5) items(filteredExpenses.sortedByDescending { it.date }, key = { it.id }) { e ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(e.description)
                            Text("R$ %.2f".format(e.value))
                            Text(dateText(e.date), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(
                                onClick = {
                                    pendingDeleteTitle = "Excluir gasto?"
                                    pendingDeleteAction = { persistExpenses(expenses.filterNot { it.id == e.id }) }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Excluir gasto") }
                        }
                    }
                }
            }
        }

    if (clientDialog) ClientDialog(editingClient, clients, { clientDialog = false }) { name, phone, notes ->
        val existing = editingClient
        val saved = if (existing == null) clients + Client(System.currentTimeMillis(), name, phone, notes)
        else clients.map { if (it.id == existing.id) existing.copy(name = name, phone = phone, notes = notes) else it }
        persistClients(saved)
        clientDialog = false
    }

    paymentOrder?.let { o ->
        PaymentDialog(o, payments, { paymentOrder = null }) { amount, note ->
            val currentPaid = effectivePaid(o, payments)
            val remaining = (o.totalValue - currentPaid).coerceAtLeast(0.0)
            val newPaid = (currentPaid + amount).coerceAtMost(o.totalValue)
            persistOrders(orders.map { if (it.id == o.id) o.copy(paidValue = newPaid) else it })
            persistPayments(payments + Payment(System.currentTimeMillis(), o.id, amount, System.currentTimeMillis(), note))
            paymentOrder = null
        }
    }

    if (productDialog) ProductDialog(editingProduct, products, { productDialog = false }) { name, q, entry, exit ->
        val e = editingProduct
        val updated = if (e == null) products + Product(System.currentTimeMillis(), name, q, entry, exit)
        else products.map { if (it.id == e.id) e.copy(name = name, quantity = q, entryValue = entry, exitValue = exit) else it }
        persistProducts(updated)
        productDialog = false
    }

    saleDialogProduct?.let { p ->
        SaleDialog(p, { saleDialogProduct = null }) { q ->
            val now = System.currentTimeMillis()
            persistSales(sales + Sale(now, p.id, p.name, q, p.exitValue, p.entryValue, now))
            saleDialogProduct = null
        }
    }

    orderDialogProduct?.let { p ->
        OrderDialog(p, null, clients, { orderDialogProduct = null }) { customer, q, delivery, paid ->
            val now = System.currentTimeMillis()
            val total = q * p.exitValue
            val customerId = clients.firstOrNull { normalizeSearch(it.name) == normalizeSearch(customer) }?.id ?: 0L
            val order = Order(now, customerId, customer, p.id, p.name, q, p.exitValue, p.entryValue, total, paid.coerceIn(0.0, total), now, delivery, "Pendente", false)
            persistOrders(orders + order)
            if (order.paidValue > 0.005) persistPayments(payments + Payment(System.currentTimeMillis(), order.id, order.paidValue, System.currentTimeMillis(), "Pagamento inicial"))
            orderDialogProduct = null
        }
    }

    editingOrder?.let { o ->
        OrderDialog(products.find { it.id == o.productId } ?: Product(o.productId, o.productName, 0, o.unitCost, o.unitValue), o, clients, { editingOrder = null }) { customer, q, delivery, paid ->
            val total = q * o.unitValue
            val updated = orders.map {
                if (it.id == o.id) it.copy(
                    customerId = clients.firstOrNull { normalizeSearch(it.name) == normalizeSearch(customer) }?.id ?: o.customerId,
                    customerName = customer, quantity = if (o.stockApplied) o.quantity else q,
                    totalValue = if (o.stockApplied) o.totalValue else total,
                    paidValue = paid.coerceIn(0.0, if (o.stockApplied) o.totalValue else total),
                    deliveryDate = delivery
                ) else it
            }
            val edited = updated.firstOrNull { it.id == o.id }
            if (edited != null) {
                val currentLinked = payments.filter { it.orderId == o.id }
                val newPaid = edited.paidValue.coerceIn(0.0, edited.totalValue)
                val linkedTotal = currentLinked.sumOf { it.amount }
                if (kotlin.math.abs(linkedTotal - newPaid) > 0.005) {
                    val withoutOld = payments.filterNot { it.orderId == o.id }
                    val replacement = if (newPaid > 0.005) {
                        withoutOld + Payment(System.currentTimeMillis(), o.id, newPaid, System.currentTimeMillis(), "Ajuste de pagamento")
                    } else withoutOld
                    persistPayments(replacement)
                }
            }
            persistOrders(updated)
            editingOrder = null
        }
    }

    if (restoreConfirmation && pendingRestoreText != null) {
        AlertDialog(
            onDismissRequest = { restoreConfirmation = false; pendingRestoreText = null },
            title = { Text("Confirmar restauração") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Os dados atuais serão substituídos pelos dados do backup.")
                    Text(buildBackupSummary(pendingRestoreText!!))
                    Text("Antes da restauração, o aplicativo salvará automaticamente um backup de segurança. Se cancelar, os dados atuais permanecerão intactos.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    val text = pendingRestoreText
                    if (text != null) {
                        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        prefs.edit().putString(PRE_RESTORE_BACKUP, buildBackupJson(context)).apply()
                        restoreBackupJson(context, text).onSuccess {
                            products = loadProducts(context)
                            sales = loadSales(context)
                            expenses = loadExpenses(context)
                            orders = loadOrders(context)
                            clients = loadClients(context)
                            payments = loadPayments(context)
                            lastRestoreBackupAvailable = true
                            backupMessage = "Backup restaurado com sucesso. Um backup de segurança foi criado antes da restauração."
                        }.onFailure {
                            backupMessage = "Não foi possível restaurar o backup."
                        }
                    }
                    restoreConfirmation = false
                    pendingRestoreText = null
                }) { Text("Restaurar") }
            },
            dismissButton = { OutlinedButton(onClick = { restoreConfirmation = false; pendingRestoreText = null }) { Text("Cancelar") } }
        )
    }

    if (lastRestoreBackupAvailable) {
        AlertDialog(
            onDismissRequest = { lastRestoreBackupAvailable = false },
            title = { Text("Desfazer última restauração?") },
            text = { Text("Deseja voltar aos dados que estavam no aplicativo antes da última restauração?") },
            confirmButton = {
                Button(onClick = {
                    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    val previous = prefs.getString(PRE_RESTORE_BACKUP, null)
                    if (previous != null) {
                        restoreBackupJson(context, previous).onSuccess {
                            products = loadProducts(context)
                            sales = loadSales(context)
                            expenses = loadExpenses(context)
                            orders = loadOrders(context)
                            clients = loadClients(context)
                            payments = loadPayments(context)
                            prefs.edit().remove(PRE_RESTORE_BACKUP).apply()
                            backupMessage = "Restauração desfeita. Os dados anteriores foram recuperados."
                        }.onFailure {
                            backupMessage = "Não foi possível recuperar os dados anteriores."
                        }
                    }
                    lastRestoreBackupAvailable = false
                }) { Text("Desfazer") }
            },
            dismissButton = { OutlinedButton(onClick = { lastRestoreBackupAvailable = false }) { Text("Manter") } }
        )
    }

    statementClient?.let { client ->
        val clientOrders = orders.filter { it.status != "Cancelada" && (it.customerId == client.id || (it.customerId == 0L && normalizeSearch(it.customerName) == normalizeSearch(client.name))) }
        val clientPayments = payments.filter { p -> clientOrders.any { it.id == p.orderId } }
        ClientStatementDialog(client, clientOrders, clientPayments) { statementClient = null }
    }

    if (deleteMessage.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { deleteMessage = "" },
            title = { Text("Exclusão não realizada") },
            text = { Text(deleteMessage) },
            confirmButton = { Button(onClick = { deleteMessage = "" }) { Text("OK") } }
        )
    }

    pendingDeleteAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingDeleteAction = null; pendingDeleteTitle = "" },
            title = { Text(pendingDeleteTitle) },
            text = { Text("Esta ação excluirá o registro salvo. Essa operação não pode ser desfeita, a menos que você tenha um backup.") },
            confirmButton = {
                Button(onClick = {
                    action()
                    pendingDeleteAction = null
                    pendingDeleteTitle = ""
                }) { Text("Excluir") }
            },
            dismissButton = { OutlinedButton(onClick = { pendingDeleteAction = null; pendingDeleteTitle = "" }) { Text("Cancelar") } }
        )
    }

    if (expenseDialog) ExpenseDialog({ expenseDialog = false }) { description, value ->
        persistExpenses(expenses + Expense(System.currentTimeMillis(), description, value, System.currentTimeMillis()))
        expenseDialog = false
    }
}

}

@Composable
private fun DashboardBar(label: String, valueText: String, value: Double, maxValue: Double) {
    val progress = if (maxValue > 0.0) (value / maxValue).coerceIn(0.0, 1.0).toFloat() else 0f
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(valueText, style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DashboardCard(title: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun buildOrderMessage(order: Order): String {
    val balance = (order.totalValue - order.paidValue).coerceAtLeast(0.0)
    return buildString {
        appendLine("CONTROLE QUEIJOS — ENCOMENDA")
        appendLine()
        appendLine("Cliente: " + order.customerName)
        appendLine("Produto: " + order.productName)
        appendLine("Quantidade: " + order.quantity + " un.")
        appendLine("Valor total: R$ %.2f".format(order.totalValue))
        appendLine("Valor pago: R$ %.2f".format(order.paidValue))
        appendLine("Saldo: R$ %.2f".format(balance))
        appendLine("Entrega prevista: " + dateOnly(order.deliveryDate))
        appendLine("Status: " + order.status)
        appendLine()
        appendLine("Obrigado pela preferência!")
    }
}


private fun buildClientStatementMessage(client: Client, orders: List<Order>, payments: List<Payment>): String {
    val total = orders.sumOf { it.totalValue }
    val paid = payments.sumOf { it.amount }
    val balance = (total - paid).coerceAtLeast(0.0)
    return buildString {
        appendLine("CONTROLE QUEIJOS — EXTRATO DO CLIENTE")
        appendLine()
        appendLine("Cliente: " + client.name)
        if (client.phone.isNotBlank()) appendLine("Telefone: " + client.phone)
        appendLine("Total das encomendas: R$ %.2f".format(total))
        appendLine("Recebimentos registrados: R$ %.2f".format(paid))
        appendLine("Saldo pendente: R$ %.2f".format(balance))
        appendLine()
        appendLine("RECEBIMENTOS")
        if (payments.isEmpty()) appendLine("Nenhum recebimento registrado.")
        else payments.sortedByDescending { it.date }.forEach { p ->
            appendLine(dateText(p.date) + " — R$ %.2f".format(p.amount) + if (p.note.isNotBlank()) " — " + p.note else "")
        }
        appendLine()
        appendLine("ENCOMENDAS")
        orders.sortedByDescending { it.orderDate }.forEach { order ->
            appendLine(dateOnly(order.orderDate) + " — " + order.productName + " — " + order.quantity + " un. — R$ %.2f — ".format(order.totalValue) + order.status)
        }
    }
}

private fun buildClientMessage(client: Client, orders: List<Order>, total: Double, paid: Double): String {
    val balance = (total - paid).coerceAtLeast(0.0)
    return buildString {
        appendLine("CONTROLE QUEIJOS — CLIENTE")
        appendLine()
        appendLine("Cliente: " + client.name)
        if (client.phone.isNotBlank()) appendLine("Telefone: " + client.phone)
        appendLine("Encomendas: " + orders.size)
        appendLine("Total: R$ %.2f".format(total))
        appendLine("Recebido: R$ %.2f".format(paid))
        appendLine("Saldo: R$ %.2f".format(balance))
        appendLine()
        orders.sortedByDescending { it.orderDate }.take(10).forEach {
            appendLine(dateOnly(it.orderDate) + " — " + it.productName + " (" + it.quantity + " un.) — R$ %.2f".format(it.totalValue))
        }
    }
}

private fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ") + "\""

private fun buildOrdersCsv(period: String, orders: List<Order>, payments: List<Payment>): String = buildString {
    appendLine("CONTROLE QUEIJOS — ENCOMENDAS")
    appendLine("Período;${csvCell(period)}")
    appendLine()
    appendLine("Data;Cliente;Produto;Quantidade;Valor total;Pago;Saldo;Status;Entrega")
    orders.sortedByDescending { it.orderDate }.forEach { order ->
        val paid = payments.filter { it.orderId == order.id }.sumOf { it.amount }
        val received = if (paid > 0.005) paid else order.paidValue
        appendLine(listOf(dateOnly(order.orderDate), order.customerName, order.productName, order.quantity.toString(), "%.2f".format(order.totalValue), "%.2f".format(received), "%.2f".format((order.totalValue - received).coerceAtLeast(0.0)), order.status, dateOnly(order.deliveryDate)).joinToString(";") { csvCell(it) })
    }
}

private fun buildFinancialCsv(period: String, orders: List<Order>, payments: List<Payment>, expenses: List<Expense>): String = buildString {
    appendLine("CONTROLE QUEIJOS — FINANCEIRO")
    appendLine("Período;${csvCell(period)}")
    appendLine()
    appendLine("TIPO;Data;Descrição;Cliente;Saída;Entrada;Lucro;Observação")
    orders.sortedByDescending { it.orderDate }.forEach { order ->
        val entry = order.quantity * order.unitCost
        val profit = order.totalValue - entry
        appendLine(listOf("Encomenda", dateOnly(order.orderDate), order.productName, order.customerName, "%.2f".format(order.totalValue), "%.2f".format(entry), "%.2f".format(profit), order.status).joinToString(";") { csvCell(it) })
    }
    payments.sortedByDescending { it.date }.forEach { p ->
        val order = orders.firstOrNull { it.id == p.orderId }
        appendLine(listOf("Recebimento", dateText(p.date), "Recebimento", order?.customerName ?: "", "%.2f".format(p.amount), "", "", p.note).joinToString(";") { csvCell(it) })
    }
    expenses.sortedByDescending { it.date }.forEach { e ->
        appendLine(listOf("Gasto", dateText(e.date), e.description, "", "%.2f".format(e.value), "", "", "").joinToString(";") { csvCell(it) })
    }
}

private fun buildOrdersReportCsv(period: String, orders: List<Order>): String = buildString {
    val active = orders.filter { it.status != "Cancelada" }
    appendLine("CONTROLE QUEIJOS — RELATÓRIO DE ENCOMENDAS")
    appendLine("Período;" + csvCell(period))
    appendLine()
    appendLine("Cliente;Produto;Quantidade;Venda;Entrada;Lucro bruto;Recebido;A receber;Status;Entrega")
    active.sortedByDescending { it.orderDate }.forEach { order ->
        val entry = order.quantity * order.unitCost
        val profit = order.totalValue - entry
        val balance = (order.totalValue - order.paidValue).coerceAtLeast(0.0)
        appendLine(listOf(
            order.customerName, order.productName, order.quantity.toString(),
            "%.2f".format(order.totalValue), "%.2f".format(entry), "%.2f".format(profit),
            "%.2f".format(order.paidValue), "%.2f".format(balance), order.status, dateOnly(order.deliveryDate)
        ).joinToString(";") { csvCell(it) })
    }
}

private fun buildProductionCsv(period: String, orders: List<Order>): String {
    val active = orders.filter { it.status != "Entregue" && it.status != "Cancelada" }
    val summary = active.groupBy { normalizeSearch(it.productName) }.values
        .map { it.first().productName.trim() to it.sumOf { order -> order.quantity } }
        .sortedBy { normalizeSearch(it.first) }
    return buildString {
        appendLine("CONTROLE QUEIJOS — PRODUÇÃO CONSOLIDADA")
        appendLine("Período;${csvCell(period)}")
        appendLine()
        appendLine("Produto;Quantidade")
        summary.forEach { (name, quantity) -> appendLine("${csvCell(name)};$quantity") }
    }
}

private fun writeSimplePdf(output: OutputStream, text: String) {
    val document = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val left = 40f
    val top = 52f
    val lineHeight = 18f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 12f
    }
    val lines = text.lines().flatMap { line ->
        if (line.length <= 78) listOf(line) else line.chunked(78)
    }
    var pageNumber = 1
    var index = 0
    while (index < lines.size || (lines.isEmpty() && pageNumber == 1)) {
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        val page = document.startPage(pageInfo)
        var y = top
        while (index < lines.size && y <= pageHeight - 45f) {
            page.canvas.drawText(lines[index], left, y, paint)
            y += lineHeight
            index++
        }
        document.finishPage(page)
        pageNumber++
        if (lines.isEmpty()) break
    }
    document.writeTo(output)
    document.close()
}

private fun buildFinancialReportText(
    period: String,
    saleRevenue: Double,
    orderRevenue: Double,
    orderEntryCost: Double,
    cost: Double,
    expenses: Double,
    profit: Double,
    margin: Double,
    ordersTotal: Double,
    ordersPaid: Double,
    receivable: Double,
    pending: Int,
    production: Int,
    overdue: Int,
    dueToday: Int,
    paymentsReceived: Double,
    paymentCount: Int
): String = buildString {
    appendLine("CONTROLE QUEIJOS — RELATÓRIO FINANCEIRO")
    appendLine("Período: " + period)
    appendLine()
    appendLine("Vendas realizadas: R$ %.2f".format(saleRevenue))
    appendLine("Encomendas entregues — saída: R$ %.2f".format(orderRevenue))
    appendLine("Encomendas entregues — entrada: R$ %.2f".format(orderEntryCost))
    appendLine("Lucro bruto das encomendas: R$ %.2f".format(orderRevenue - orderEntryCost))
    appendLine("Faturamento: R$ %.2f".format(saleRevenue + orderRevenue))
    appendLine("Custo total das mercadorias: R$ %.2f".format(cost))
    appendLine("Gastos: R$ %.2f".format(expenses))
    appendLine("Lucro líquido: R$ %.2f".format(profit))
    appendLine("Margem: %.2f%%".format(margin))
    appendLine()
    appendLine("Encomendas no período: R$ %.2f".format(ordersTotal))
    appendLine("Recebido de encomendas: R$ %.2f".format(ordersPaid))
    appendLine("Recebimentos registrados: R$ %.2f".format(paymentsReceived))
    appendLine("Quantidade de recebimentos: " + paymentCount)
    val averagePayment = if (paymentCount > 0) paymentsReceived / paymentCount else 0.0
    appendLine("Valor médio por recebimento: R$ %.2f".format(averagePayment))
    appendLine("A receber: R$ %.2f".format(receivable))
    val recebimento = if (ordersTotal > 0.005) (ordersPaid / ordersTotal * 100.0).coerceIn(0.0, 100.0) else 0.0
    appendLine("Percentual recebido das encomendas: %.2f%%".format(recebimento))
    appendLine()
    appendLine("Encomendas pendentes: " + pending)
    appendLine("Em produção: " + production)
    appendLine("Entregas atrasadas: " + overdue)
    appendLine("Entregas previstas para hoje: " + dueToday)
}

private fun buildOrdersReportText(period: String, orders: List<Order>): String {
    val active = orders.filter { it.status != "Cancelada" }
    val pending = active.filter { it.status == "Pendente" }
    val production = active.filter { it.status == "Em produção" }
    val delivered = active.filter { it.status == "Entregue" }
    val overdue = active.filter { isDeliveryOverdue(it) }
    val totalUnits = active.sumOf { it.quantity }
    val deliveredUnits = delivered.sumOf { it.quantity }
    val pendingUnits = active.filter { it.status != "Entregue" }.sumOf { it.quantity }
    val salesTotal = active.sumOf { it.totalValue }
    val entryTotal = active.sumOf { it.quantity * it.unitCost }
    val grossProfit = salesTotal - entryTotal
    val received = active.sumOf { it.paidValue }
    val receivable = (salesTotal - received).coerceAtLeast(0.0)
    val productSummary = active.groupBy { normalizeSearch(it.productName) }.values
        .map { group ->
            val name = group.first().productName.trim()
            val units = group.sumOf { it.quantity }
            val sales = group.sumOf { it.totalValue }
            val entry = group.sumOf { it.quantity * it.unitCost }
            Triple(name, units, sales - entry)
        }
        .sortedBy { normalizeSearch(it.first) }

    return buildString {
        appendLine("CONTROLE QUEIJOS — RELATÓRIO DE ENCOMENDAS")
        appendLine("Período: $period")
        appendLine()
        appendLine("Total de encomendas: " + active.size)
        appendLine("Pendentes: " + pending.size)
        appendLine("Em produção: " + production.size)
        appendLine("Entregues: " + delivered.size)
        appendLine("Atrasadas: " + overdue.size)
        appendLine()
        appendLine("Unidades totais: $totalUnits")
        appendLine("Unidades pendentes de entrega: $pendingUnits")
        appendLine("Unidades entregues: $deliveredUnits")
        appendLine("Valor de saída (venda): R$ %.2f".format(salesTotal))
        appendLine("Custo de entrada: R$ %.2f".format(entryTotal))
        appendLine("Lucro bruto: R$ %.2f".format(grossProfit))
        appendLine("Recebido: R$ %.2f".format(received))
        appendLine("A receber: R$ %.2f".format(receivable))
        appendLine()
        appendLine("RESUMO POR PRODUTO")
        if (productSummary.isEmpty()) appendLine("Nenhuma encomenda no período.")
        else productSummary.forEach { (name, units, profit) ->
            appendLine("• $name — $units un. — lucro bruto R$ %.2f".format(profit))
        }
        appendLine()
        appendLine("DETALHAMENTO")
        if (active.isEmpty()) appendLine("Nenhuma encomenda no período.")
        else active.sortedByDescending { it.orderDate }.forEach { order ->
            val entry = order.quantity * order.unitCost
            val profit = order.totalValue - entry
            val balance = (order.totalValue - order.paidValue).coerceAtLeast(0.0)
            appendLine("• ${order.customerName} — ${order.productName} — ${order.quantity} un.")
            appendLine("  ${order.status} • entrega ${dateOnly(order.deliveryDate)} • venda R$ %.2f • entrada R$ %.2f • lucro R$ %.2f • a receber R$ %.2f".format(order.totalValue, entry, profit, balance))
        }
    }
}

private fun buildOperationalReportText(period: String, orders: List<Order>): String {
    val active = orders.filter { it.status != "Cancelada" }
    val pending = active.filter { it.status == "Pendente" }
    val production = active.filter { it.status == "Em produção" }
    val delivered = active.filter { it.status == "Entregue" }
    val overdue = active.filter { isDeliveryOverdue(it) }
    val dueToday = active.filter { it.status != "Entregue" && sameDay(it.deliveryDate) }
    val productSummary = active.groupBy { normalizeSearch(it.productName) }.values
        .map { group -> group.first().productName.trim() to group.sumOf { it.quantity } }
        .sortedBy { normalizeSearch(it.first) }

    return buildString {
        appendLine("CONTROLE QUEIJOS — RELATÓRIO OPERACIONAL")
        appendLine("Período: $period")
        appendLine()
        val totalUnits = active.sumOf { it.quantity }
        appendLine("Encomendas: " + active.size)
        appendLine("Produtos diferentes: " + productSummary.size)
        val totalSales = active.sumOf { it.totalValue }
        val totalEntry = active.sumOf { it.quantity * it.unitCost }
        val grossProfit = totalSales - totalEntry
        val received = active.sumOf { effectivePaid(it, emptyList()) }
        val receivable = active.sumOf { (it.totalValue - effectivePaid(it, emptyList())).coerceAtLeast(0.0) }
        val marginPercent = if (totalSales > 0.0) (grossProfit / totalSales) * 100.0 else 0.0
        appendLine("Unidades a produzir/entregar: " + totalUnits)
        appendLine("Pendentes: " + pending.size)
        appendLine("Em produção: " + production.size)
        appendLine("Entregues: " + delivered.size)
        appendLine("Atrasadas: " + overdue.size)
        appendLine("Para hoje: " + dueToday.size)
        appendLine()
        appendLine("VISÃO FINANCEIRA DA PRODUÇÃO")
        appendLine("Valor de venda: R$ %.2f".format(totalSales))
        appendLine("Valor de entrada: R$ %.2f".format(totalEntry))
        appendLine("Lucro bruto estimado: R$ %.2f".format(grossProfit))
        appendLine("Margem bruta estimada: %.2f%%".format(marginPercent))
        appendLine("Valor recebido: R$ %.2f".format(received))
        appendLine("A receber: R$ %.2f".format(receivable))
        appendLine()
        appendLine("PRODUÇÃO CONSOLIDADA POR PRODUTO")
        if (productSummary.isEmpty()) appendLine("Nenhuma encomenda no período.")
        else productSummary.forEach { (name, quantity) -> appendLine("• $name — $quantity un.") }
    }
}

private fun buildReportText(period: String, orders: List<Order>): String {
    val productSummary = orders
        .filter { it.status != "Cancelada" }
        .groupBy { normalizeSearch(it.productName) }
        .values
        .map { group ->
            val name = group.first().productName.trim()
            val quantity = group.sumOf { it.quantity }
            name to quantity
        }
        .sortedBy { normalizeSearch(it.first) }

    val activeOrders = orders.filter { it.status != "Cancelada" }
    val pendingUnits = activeOrders.filter { it.status == "Pendente" }.sumOf { it.quantity }
    val productionUnits = activeOrders.filter { it.status == "Em produção" }.sumOf { it.quantity }
    val totalUnits = activeOrders.filter { it.status != "Entregue" }.sumOf { it.quantity }

    return buildString {
        appendLine("CONTROLE QUEIJOS — LISTA DE PRODUÇÃO")
        appendLine("Período: $period")
        appendLine()
        appendLine("A produzir: $pendingUnits un.")
        appendLine("Em produção: $productionUnits un.")
        appendLine("Total pendente de entrega: $totalUnits un.")
        appendLine()
        if (productSummary.isEmpty()) {
            appendLine("Nenhuma encomenda no período.")
        } else {
            productSummary.forEach { (name, quantity) ->
                appendLine("• $name — $quantity un.")
            }
        }
    }
}


@Composable
private fun ClientStatementDialog(client: Client, orders: List<Order>, payments: List<Payment>, onDismiss: () -> Unit) {
    val total = orders.sumOf { it.totalValue }
    val paid = payments.sumOf { it.amount }
    val balance = (total - paid).coerceAtLeast(0.0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extrato — " + client.name) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Text("Total das encomendas: R$ %.2f".format(total)) }
                item { Text("Recebimentos registrados: R$ %.2f".format(paid)) }
                item { Text("Saldo pendente: R$ %.2f".format(balance)) }
                item { Spacer(Modifier.height(4.dp)); Text("HISTÓRICO DE RECEBIMENTOS", style = MaterialTheme.typography.labelLarge) }
                if (payments.isEmpty()) item { Text("Nenhum recebimento registrado.") }
                else payments.sortedByDescending { it.date }.forEach { p ->
                    item { Text(dateText(p.date) + " — R$ %.2f".format(p.amount) + if (p.note.isNotBlank()) " — " + p.note else "") }
                }
                item { Spacer(Modifier.height(4.dp)); Text("HISTÓRICO DE ENCOMENDAS", style = MaterialTheme.typography.labelLarge) }
                orders.sortedByDescending { it.orderDate }.forEach { order ->
                    item { Text(dateOnly(order.orderDate) + " • " + order.productName + " • " + order.quantity + " un. • R$ %.2f • ".format(order.totalValue) + order.status) }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun ClientDialog(
    client: Client?,
    clients: List<Client>,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember(client) { mutableStateOf(client?.name ?: "") }
    var phone by remember(client) { mutableStateOf(client?.phone ?: "") }
    var notes by remember(client) { mutableStateOf(client?.notes ?: "") }
    var validationError by remember(client) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (client == null) "Cadastrar cliente" else "Editar cliente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name,
                    { name = it; validationError = "" },
                    label = { Text("Nome") },
                    singleLine = true
                )
                OutlinedTextField(
                    phone,
                    { phone = it; validationError = "" },
                    label = { Text("Telefone/WhatsApp") },
                    singleLine = true
                )
                OutlinedTextField(
                    notes,
                    { notes = it },
                    label = { Text("Observações") },
                    minLines = 2
                )
                if (validationError.isNotBlank()) {
                    Text(validationError, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val cleanName = name.trim()
                val cleanPhone = phone.trim()
                val duplicateName = clients.any {
                    it.id != client?.id && normalizeSearch(it.name) == normalizeSearch(cleanName)
                }
                val phoneDigits = normalizePhone(cleanPhone)
                val duplicatePhone = phoneDigits.isNotBlank() && clients.any {
                    it.id != client?.id && normalizePhone(it.phone) == phoneDigits
                }

                when {
                    cleanName.isBlank() -> validationError = "Informe o nome do cliente."
                    duplicateName -> validationError = "Já existe um cliente cadastrado com esse nome."
                    duplicatePhone -> validationError = "Já existe um cliente cadastrado com esse telefone."
                    else -> onSave(cleanName, cleanPhone, notes.trim())
                }
            }) { Text("Salvar") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun PaymentDialog(order: Order, payments: List<Payment>, onDismiss: () -> Unit, onSave: (Double, String) -> Unit) {
    var value by remember(order) { mutableStateOf("") }
    var note by remember(order) { mutableStateOf("") }
    val remaining = (order.totalValue - effectivePaid(order, payments)).coerceAtLeast(0.0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar pagamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cliente: " + order.customerName)
                Text("Saldo atual: R$ %.2f".format(remaining))
                OutlinedTextField(value, { value = it.replace(",", ".") }, label = { Text("Valor recebido") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Observação (opcional)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = value.toDoubleOrNull() ?: 0.0
                if (amount > 0 && amount <= remaining + 0.005) onSave(amount, note.trim())
            }) { Text("Registrar") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ProductDialog(product: Product?, products: List<Product>, onDismiss: () -> Unit, onSave: (String, Int, Double, Double) -> Unit) {
    var name by remember(product) { mutableStateOf(product?.name ?: "") }
    var quantity by remember(product) { mutableStateOf(product?.quantity?.toString() ?: "0") }
    var entry by remember(product) { mutableStateOf(product?.entryValue?.toString() ?: "0") }
    var exit by remember(product) { mutableStateOf(product?.exitValue?.toString() ?: "0") }
    var validationError by remember(product) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Cadastrar produto" else "Editar produto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it; validationError = "" }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit) }, label = { Text("Quantidade disponível (opcional)") }, singleLine = true)
                Text("Para produtos sob encomenda, você pode deixar 0 e usar apenas custo e preço de venda.")
                OutlinedTextField(entry, { entry = it.replace(",", ".") }, label = { Text("Valor de entrada (custo/unidade)") }, singleLine = true)
                OutlinedTextField(exit, { exit = it.replace(",", "."); validationError = "" }, label = { Text("Valor de saída (venda/unidade)") }, singleLine = true)
                if (validationError.isNotBlank()) {
                    Text(validationError, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val cleanName = name.trim()
                val q = quantity.toIntOrNull() ?: 0
                val e = entry.toDoubleOrNull() ?: 0.0
                val s = exit.toDoubleOrNull() ?: 0.0
                val duplicate = products.any { it.id != product?.id && normalizeSearch(it.name) == normalizeSearch(cleanName) }
                when {
                    cleanName.isBlank() -> validationError = "Informe o nome do produto."
                    duplicate -> validationError = "Já existe um produto cadastrado com esse nome."
                    q < 0 -> validationError = "A quantidade não pode ser negativa."
                    e < 0.0 -> validationError = "O valor de entrada não pode ser negativo."
                    s <= 0.0 -> validationError = "Informe um valor de saída maior que zero."
                    s < e -> validationError = "O valor de saída não pode ser menor que o custo de entrada."
                    else -> onSave(cleanName, q, e, s)
                }
            }) { Text("Salvar") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun SaleDialog(product: Product, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var quantity by remember(product) { mutableStateOf("1") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar venda — " + product.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Venda registrada sem controle de estoque.")
                Text("Valor unitário: R$ %.2f".format(product.exitValue))
                OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit) }, label = { Text("Quantidade vendida") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { quantity.toIntOrNull()?.let { if (it > 0) onSave(it) } }) { Text("Registrar") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun OrderDialog(product: Product, order: Order?, clients: List<Client>, onDismiss: () -> Unit, onSave: (String, Int, Long, Double) -> Unit) {
    var customer by remember(order) { mutableStateOf(order?.customerName ?: "") }
    var quantity by remember(order) { mutableStateOf(order?.quantity?.toString() ?: "1") }
    var delivery by remember(order) { mutableStateOf(if (order == null) dateOnly(defaultDeliveryDate()) else dateOnly(order.deliveryDate)) }
    var paid by remember(order) { mutableStateOf(order?.paidValue?.toString() ?: "0") }
    var validationError by remember(order) { mutableStateOf("") }

    val matchingClients = clients
        .filter { customer.isNotBlank() && normalizeSearch(it.name).contains(normalizeSearch(customer)) }
        .sortedBy { normalizeSearch(it.name) }
        .take(5)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (order == null) "Nova encomenda" else "Editar encomenda") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Produto: " + product.name)
                Text("Valor de venda: R$ %.2f/un.".format(order?.unitValue ?: product.exitValue))
                OutlinedTextField(
                    customer,
                    { customer = it; validationError = "" },
                    label = { Text("Nome do cliente") },
                    singleLine = true
                )
                if (matchingClients.isNotEmpty()) {
                    Text("Clientes cadastrados", style = MaterialTheme.typography.labelLarge)
                    matchingClients.forEach { client ->
                        OutlinedButton(
                            onClick = {
                                customer = client.name
                                validationError = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (client.phone.isNotBlank()) client.name + " • " + client.phone else client.name
                            )
                        }
                    }
                } else if (clients.isEmpty()) {
                    Text("Dica: cadastre o cliente na aba Clientes para vinculá-lo automaticamente à encomenda.")
                }
                OutlinedTextField(
                    quantity,
                    { quantity = it.filter(Char::isDigit); validationError = "" },
                    label = { Text(if (order?.stockApplied == true) "Quantidade (entregue)" else "Quantidade") },
                    enabled = order?.stockApplied != true,
                    singleLine = true
                )
                Text("Produção: sob encomenda — estoque não é obrigatório.")
                OutlinedTextField(delivery, { delivery = it }, label = { Text("Data prevista (dd/MM/yyyy)") }, singleLine = true)
                if (validationError.isNotBlank()) {
                    Text(validationError, color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(paid, { paid = it.replace(",", ".") }, label = { Text("Valor pago") }, singleLine = true)
                if (order == null) Text("Status inicial: Pendente")
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = quantity.toIntOrNull() ?: 0
                val p = paid.toDoubleOrNull() ?: 0.0
                val deliveryDate = parseDate(delivery, defaultDeliveryDate())
                val referenceDate = order?.orderDate ?: System.currentTimeMillis()
                if (customer.isBlank()) {
                    validationError = "Informe o nome do cliente."
                } else if (q <= 0) {
                    validationError = "Informe uma quantidade válida."
                } else if (p < 0.0) {
                    validationError = "O valor pago não pode ser negativo."
                } else if (p > (order?.totalValue ?: (q * product.exitValue)) + 0.005) {
                    validationError = "O valor pago não pode ser maior que o total da encomenda."
                } else if (deliveryDate < referenceDate - 24L * 60L * 60L * 1000L) {
                    validationError = "A data de entrega não pode ser anterior à data da encomenda."
                } else {
                    onSave(customer.trim(), q, deliveryDate, p)
                }
            }) { Text("Salvar") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
@Composable
private fun ExpenseDialog(onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var description by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo gasto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(description, { description = it }, label = { Text("Descrição") }, singleLine = true)
                OutlinedTextField(value, { value = it.replace(",", ".") }, label = { Text("Valor") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val v = value.toDoubleOrNull() ?: 0.0
                if (description.isNotBlank() && v > 0) onSave(description.trim(), v)
            }) { Text("Salvar") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}