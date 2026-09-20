package br.com.controlequeijos

// V7.7 — comunicação de encomendas e financeiro por cliente

import android.content.Context
import android.os.Bundle
import android.content.Intent
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
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Product(val id: Long, val name: String, val quantity: Int, val entryValue: Double, val exitValue: Double)
data class Sale(val id: Long, val productId: Long, val productName: String, val quantity: Int, val unitValue: Double, val unitCost: Double, val date: Long)
data class Expense(val id: Long, val description: String, val value: Double, val date: Long)
data class Client(val id: Long, val name: String, val phone: String, val notes: String)
data class Order(
    val id: Long,
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
private const val ORDERS = "orders"
private const val CLIENTS = "clients"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ControleQueijosApp(applicationContext) }
    }
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

private fun saveProducts(context: Context, products: List<Product>) {
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

private fun saveSales(context: Context, sales: List<Sale>) {
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

private fun saveExpenses(context: Context, expenses: List<Expense>) {
    val json = JSONArray()
    expenses.forEach { e ->
        json.put(JSONObject().apply {
            put("id", e.id); put("description", e.description); put("value", e.value); put("date", e.date)
        })
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(EXPENSE_LIST, json.toString()).apply()
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

private fun saveClients(context: Context, clients: List<Client>) {
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
            o.getLong("id"), o.getString("customerName"), o.getLong("productId"), o.getString("productName"),
            o.getInt("quantity"), o.getDouble("unitValue"), o.optDouble("unitCost", 0.0),
            o.getDouble("totalValue"), o.getDouble("paidValue"), o.getLong("orderDate"),
            o.getLong("deliveryDate"), o.getString("status"), o.optBoolean("stockApplied", false)
        )
    }
}.getOrDefault(emptyList())

private fun saveOrders(context: Context, orders: List<Order>) {
    val json = JSONArray()
    orders.forEach { o ->
        json.put(JSONObject().apply {
            put("id", o.id); put("customerName", o.customerName); put("productId", o.productId); put("productName", o.productName)
            put("quantity", o.quantity); put("unitValue", o.unitValue); put("unitCost", o.unitCost)
            put("totalValue", o.totalValue); put("paidValue", o.paidValue); put("orderDate", o.orderDate)
            put("deliveryDate", o.deliveryDate); put("status", o.status); put("stockApplied", o.stockApplied)
        })
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ORDERS, json.toString()).apply()
}

private fun dateText(time: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(time))
private fun dateOnly(time: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(time))
private fun sameDay(time: Long): Boolean = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(time)) == SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
private fun sameMonth(time: Long): Boolean = SimpleDateFormat("yyyyMM", Locale.US).format(Date(time)) == SimpleDateFormat("yyyyMM", Locale.US).format(Date())
private fun isOverdue(time: Long): Boolean = time < System.currentTimeMillis()
private fun inPeriod(time: Long, period: String) = period == "Todos" || (period == "Hoje" && sameDay(time)) || (period == "Mês" && sameMonth(time))
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
    var productDialog by remember { mutableStateOf(false) }
    var saleDialogProduct by remember { mutableStateOf<Product?>(null) }
    var orderDialogProduct by remember { mutableStateOf<Product?>(null) }
    var editingOrder by remember { mutableStateOf<Order?>(null) }
    var expenseDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var clientDialog by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<Client?>(null) }
    var paymentOrder by remember { mutableStateOf<Order?>(null) }
    var period by remember { mutableStateOf("Todos") }
    var orderFilter by remember { mutableStateOf("Todas") }
    var orderSearch by remember { mutableStateOf("") }

    fun persistProducts(p: List<Product>) { products = p; saveProducts(context, p) }
    fun persistSales(s: List<Sale>) { sales = s; saveSales(context, s) }
    fun persistExpenses(e: List<Expense>) { expenses = e; saveExpenses(context, e) }
    fun persistOrders(o: List<Order>) { orders = o; saveOrders(context, o) }
    fun persistClients(c: List<Client>) { clients = c; saveClients(context, c) }

    val filteredSales = sales.filter { inPeriod(it.date, period) }
    val filteredExpenses = expenses.filter { inPeriod(it.date, period) }
    val filteredOrders = orders.filter { inPeriod(it.orderDate, period) }
    val activeOrders = filteredOrders.filter { it.status != "Cancelada" }
    val deliveredOrders = activeOrders.filter { it.status == "Entregue" }
    val saleRevenue = filteredSales.sumOf { it.quantity * it.unitValue }
    val saleCost = filteredSales.sumOf { it.quantity * it.unitCost }
    val orderRevenue = deliveredOrders.sumOf { it.totalValue }
    val orderCost = deliveredOrders.sumOf { it.quantity * it.unitCost }
    val revenue = saleRevenue + orderRevenue
    val cost = saleCost + orderCost
    val expenseTotal = filteredExpenses.sumOf { it.value }
    val profit = revenue - cost - expenseTotal
    val ordersTotal = activeOrders.sumOf { it.totalValue }
    val ordersPaid = activeOrders.sumOf { it.paidValue }
    val ordersReceivable = ordersTotal - ordersPaid
    val pendingOrders = activeOrders.filter { it.status == "Pendente" }
    val overdueOrders = pendingOrders.filter { isOverdue(it.deliveryDate) }
    val dueTodayOrders = pendingOrders.filter { sameDay(it.deliveryDate) }
    val deliveredOrdersCount = activeOrders.count { it.status == "Entregue" }
    val visibleOrders = activeOrders.filter { o ->
        val matchesFilter = when (orderFilter) {
            "Pendentes" -> o.status == "Pendente"
            "Hoje" -> sameDay(o.deliveryDate) && o.status == "Pendente"
            "Atrasadas" -> o.status == "Pendente" && isOverdue(o.deliveryDate)
            "Entregues" -> o.status == "Entregue"
            else -> true
        }
        matchesFilter && (orderSearch.isBlank() || o.customerName.contains(orderSearch.trim(), ignoreCase = true) || o.productName.contains(orderSearch.trim(), ignoreCase = true))
    }.sortedWith(compareBy<Order> { if (it.status == "Pendente") 0 else 1 }.thenBy { it.deliveryDate }.thenBy { it.customerName.lowercase(Locale.getDefault()) })
    val pendingOrdersValue = pendingOrders.sumOf { it.totalValue }
    val averageSale = if (filteredSales.isNotEmpty()) saleRevenue / filteredSales.sumOf { it.quantity } else 0.0
    val profitMargin = if (revenue > 0.0) (profit / revenue) * 100.0 else 0.0

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("Controle Queijos — V7.7") }) }) { pad ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Painel", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    DashboardCard("💰 Faturamento", "R$ %.2f".format(revenue))
                    DashboardCard("📈 Lucro líquido", "R$ %.2f".format(profit))
                    DashboardCard("💳 Total a receber", "R$ %.2f".format(ordersReceivable))
                    DashboardCard("📋 Encomendas pendentes", pendingOrders.size.toString())
                    DashboardCard("💵 Valor das pendentes", "R$ %.2f".format(pendingOrdersValue))
                    DashboardCard("📅 Entregas hoje", dueTodayOrders.size.toString())
                    DashboardCard("⚠️ Entregas atrasadas", overdueOrders.size.toString())
                    DashboardCard("✅ Encomendas entregues", deliveredOrdersCount.toString())
                    DashboardCard("👥 Clientes", clients.size.toString())
                    if (overdueOrders.isNotEmpty()) {
                        Text("⚠️ Há encomendas com entrega atrasada.", color = MaterialTheme.colorScheme.error)
                    }
                    if (dueTodayOrders.isNotEmpty()) {
                        Text("📅 Há encomendas previstas para hoje.")
                    }
                }
                item {
                    Text("Resumo financeiro", style = MaterialTheme.typography.headlineSmall)
                    Text("Vendas realizadas: R$ %.2f".format(saleRevenue))
                    Text("Encomendas entregues: R$ %.2f".format(orderRevenue))
                    Text("Custo das mercadorias: R$ %.2f".format(cost))
                    Text("Gastos: R$ %.2f".format(expenseTotal))
                    Text("Lucro: R$ %.2f".format(profit))
                    Text("Margem sobre vendas: %.2f%%".format(profitMargin))
                    Text("Gastos lançados: " + filteredExpenses.size)
                    Text("Encomendas canceladas: " + filteredOrders.count { it.status == "Cancelada" })
                    Spacer(Modifier.height(6.dp))
                    Text("Encomendas no período: R$ %.2f".format(ordersTotal))
                    Text("Recebido de encomendas: R$ %.2f".format(ordersPaid))
                    Text("A receber: R$ %.2f".format(ordersReceivable))
                }
                item {
                    Text("Relatórios", style = MaterialTheme.typography.headlineSmall)
                    Text("Vendas: R$ %.2f".format(saleRevenue))
                    Text("Encomendas entregues: R$ %.2f".format(orderRevenue))
                    Text("Gastos: R$ %.2f".format(expenseTotal))
                    Text("Lucro: R$ %.2f".format(profit))
                    Text("Recebido: R$ %.2f".format(ordersPaid))
                    Text("A receber: R$ %.2f".format(ordersReceivable))
                    Text("Encomendas pendentes: " + pendingOrders.size)
                    Text("Valor das encomendas pendentes: R$ %.2f".format(pendingOrdersValue))
                    Text("Entregas atrasadas: " + overdueOrders.size)
                    Text("Entregas previstas para hoje: " + dueTodayOrders.size)
                    Text("Margem sobre vendas: %.2f%%".format(profitMargin))
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = {
                        val report = buildReportText(period, saleRevenue, orderRevenue, cost, expenseTotal, profit, ordersTotal, ordersPaid, ordersReceivable, products)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                        }, "Compartilhar relatório").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Compartilhar relatório") }
                }
                item {
                    Text("Período", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Todos", "Hoje", "Mês").forEach { p ->
                            if (period == p) Button(onClick = { period = p }) { Text(p) }
                            else OutlinedButton(onClick = { period = p }) { Text(p) }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { editingProduct = null; productDialog = true }) { Text("Cadastrar produto") }
                        OutlinedButton(onClick = { expenseDialog = true }) { Text("Novo gasto") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { editingClient = null; clientDialog = true }) { Text("Cadastrar cliente") }
                    OutlinedButton(onClick = { /* lista abaixo */ }) { Text("Clientes: " + clients.size) }
                }

                item { Text("Produtos / Catálogo", style = MaterialTheme.typography.headlineSmall) }
                if (products.isEmpty()) item { Text("Nenhum produto cadastrado.") }
                items(products, key = { it.id }) { p ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text("Disponibilidade: por encomenda")
                            Text("Custo: R$ %.2f/un.".format(p.entryValue))
                            Text("Saída: R$ %.2f/un.".format(p.exitValue))
                            Text("Lucro por unidade: R$ %.2f".format(p.exitValue - p.entryValue))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { saleDialogProduct = p }) { Text("Registrar venda") }
                                OutlinedButton(onClick = { orderDialogProduct = p }) { Text("Encomenda") }
                                OutlinedButton(onClick = { editingProduct = p; productDialog = true }) { Text("Editar") }
                            }
                        }
                    }
                }

                item { Text("Clientes (" + clients.size + ")", style = MaterialTheme.typography.headlineSmall) }
                if (clients.isEmpty()) item { Text("Nenhum cliente cadastrado.") }
                items(clients.sortedBy { it.name.lowercase(Locale.getDefault()) }, key = { it.id }) { client ->
                    val clientOrders = orders.filter { it.customerName.equals(client.name, ignoreCase = true) }
                    val total = clientOrders.sumOf { it.totalValue }
                    val paid = clientOrders.sumOf { it.paidValue }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(client.name, style = MaterialTheme.typography.titleMedium)
                            if (client.phone.isNotBlank()) Text("Telefone: " + client.phone)
                            Text("Compras/encomendas: " + clientOrders.size)
                            Text("Total: R$ %.2f | A receber: R$ %.2f".format(total, total - paid))
                            if (client.notes.isNotBlank()) Text("Obs.: " + client.notes)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { editingClient = client; clientDialog = true }) { Text("Editar") }
                                TextButton(onClick = {
                                    if (clientOrders.isEmpty()) persistClients(clients.filterNot { it.id == client.id })
                                }) { Text("Excluir") }
                            }
                        }
                    }
                }

item {
                    Text("Encomendas", style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(
                        value = orderSearch,
                        onValueChange = { orderSearch = it },
                        label = { Text("Buscar cliente ou produto") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Todas", "Pendentes", "Hoje", "Atrasadas", "Entregues").forEach { f ->
                            if (orderFilter == f) Button(onClick = { orderFilter = f }) { Text(f) }
                            else OutlinedButton(onClick = { orderFilter = f }) { Text(f) }
                        }
                    }
                    Text("Exibindo: " + visibleOrders.size + " encomenda(s)")
                }
                if (visibleOrders.isEmpty()) item { Text("Nenhuma encomenda encontrada.") }
                items(visibleOrders, key = { it.id }) { o ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(o.customerName, style = MaterialTheme.typography.titleMedium)
                            Text(o.productName + " — " + o.quantity + " un.")
                            Text("Total: R$ %.2f".format(o.totalValue))
                            Text("Pago: R$ %.2f | Saldo: R$ %.2f".format(o.paidValue, o.totalValue - o.paidValue))
                            Text("Pedido: " + dateOnly(o.orderDate) + " | Entrega: " + dateOnly(o.deliveryDate))
                            Text("Status: " + o.status + if (o.status == "Pendente" && isOverdue(o.deliveryDate)) " • ATRASADA" else "")
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
                                    Button(onClick = {
                                        persistOrders(orders.map { if (it.id == o.id) o.copy(status = "Entregue", stockApplied = false) else it })
                                    }) { Text("Marcar entregue") }
                                    OutlinedButton(onClick = {
                                        persistOrders(orders.map { if (it.id == o.id) o.copy(status = "Cancelada") else it })
                                    }) { Text("Cancelar") }
                                }
                                OutlinedButton(onClick = { editingOrder = o }) { Text("Editar") }
                                TextButton(onClick = {
                                    if (o.stockApplied) {
                                        val p = products.find { it.id == o.productId }
                                        if (p != null) persistProducts(products.map { if (it.id == p.id) p.copy(quantity = p.quantity + o.quantity) else it })
                                    }
                                    persistOrders(orders.filterNot { it.id == o.id })
                                }) { Text("Excluir") }
                            }
                        }
                    }
                }

                item { Text("Vendas registradas (" + filteredSales.size + ")", style = MaterialTheme.typography.headlineSmall) }
                if (filteredSales.isEmpty()) item { Text("Nenhuma venda no período.") }
                items(filteredSales.sortedByDescending { it.date }, key = { it.id }) { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(s.productName + " — " + s.quantity + " un.")
                            Text("R$ %.2f".format(s.quantity * s.unitValue))
                            Text(dateText(s.date), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = {
                                persistSales(sales.filterNot { it.id == s.id })
                                val p = products.find { it.id == s.productId }
                                if (p != null) persistProducts(products.map { if (it.id == p.id) p.copy(quantity = p.quantity + s.quantity) else it })
                            }) { Text("Excluir venda e devolver estoque") }
                        }
                    }
                }

                item { Text("Gastos registrados (" + filteredExpenses.size + ")", style = MaterialTheme.typography.headlineSmall) }
                if (filteredExpenses.isEmpty()) item { Text("Nenhum gasto no período.") }
                items(filteredExpenses.sortedByDescending { it.date }, key = { it.id }) { e ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(e.description)
                            Text("R$ %.2f".format(e.value))
                            Text(dateText(e.date), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { persistExpenses(expenses.filterNot { it.id == e.id }) }) { Text("Excluir gasto") }
                        }
                    }
                }
            }
        }

    if (clientDialog) ClientDialog(editingClient, { clientDialog = false }) { name, phone, notes ->
        val existing = editingClient
        val saved = if (existing == null) clients + Client(System.currentTimeMillis(), name, phone, notes)
        else clients.map { if (it.id == existing.id) existing.copy(name = name, phone = phone, notes = notes) else it }
        persistClients(saved)
        clientDialog = false
    }

    paymentOrder?.let { o ->
        PaymentDialog(o, { paymentOrder = null }) { amount ->
            val remaining = (o.totalValue - o.paidValue).coerceAtLeast(0.0)
            val newPaid = (o.paidValue + amount).coerceAtMost(o.totalValue)
            persistOrders(orders.map { if (it.id == o.id) o.copy(paidValue = newPaid) else it })
            paymentOrder = null
        }
    }

    if (productDialog) ProductDialog(editingProduct, { productDialog = false }) { name, q, entry, exit ->
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
        OrderDialog(p, null, { orderDialogProduct = null }) { customer, q, delivery, paid ->
            val now = System.currentTimeMillis()
            val total = q * p.exitValue
            val order = Order(now, customer, p.id, p.name, q, p.exitValue, p.entryValue, total, paid.coerceIn(0.0, total), now, delivery, "Pendente", false)
            persistOrders(orders + order)
            orderDialogProduct = null
        }
    }

    editingOrder?.let { o ->
        OrderDialog(products.find { it.id == o.productId } ?: Product(o.productId, o.productName, 0, o.unitCost, o.unitValue), o, { editingOrder = null }) { customer, q, delivery, paid ->
            val total = q * o.unitValue
            val updated = orders.map {
                if (it.id == o.id) it.copy(
                    customerName = customer, quantity = if (o.stockApplied) o.quantity else q,
                    totalValue = if (o.stockApplied) o.totalValue else total,
                    paidValue = paid.coerceIn(0.0, if (o.stockApplied) o.totalValue else total),
                    deliveryDate = delivery
                ) else it
            }
            persistOrders(updated)
            editingOrder = null
        }
    }

    if (expenseDialog) ExpenseDialog({ expenseDialog = false }) { description, value ->
        persistExpenses(expenses + Expense(System.currentTimeMillis(), description, value, System.currentTimeMillis()))
        expenseDialog = false
    }
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

private fun buildReportText(
    period: String,
    saleRevenue: Double,
    orderRevenue: Double,
    cost: Double,
    expenses: Double,
    profit: Double,
    ordersTotal: Double,
    ordersPaid: Double,
    ordersReceivable: Double,
    products: List<Product>
): String {
    return buildString {
        appendLine("CONTROLE QUEIJOS — RELATÓRIO")
        appendLine("Período: $period")
        appendLine()
        appendLine("Vendas realizadas: R$ %.2f".format(saleRevenue))
        appendLine("Encomendas entregues: R$ %.2f".format(orderRevenue))
        appendLine("Custo das mercadorias: R$ %.2f".format(cost))
        appendLine("Gastos: R$ %.2f".format(expenses))
        appendLine("Lucro: R$ %.2f".format(profit))
        appendLine()
        appendLine("Encomendas no período: R$ %.2f".format(ordersTotal))
        appendLine("Recebido de encomendas: R$ %.2f".format(ordersPaid))
        appendLine("A receber: R$ %.2f".format(ordersReceivable))
        appendLine()
        appendLine("ENCOMENDAS")
        appendLine("Controle principal: produção e entrega sob encomenda.")
        appendLine("Produtos tratados como catálogo, sem necessidade de estoque.")
    }
}


@Composable
private fun ClientDialog(client: Client?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember(client) { mutableStateOf(client?.name ?: "") }
    var phone by remember(client) { mutableStateOf(client?.phone ?: "") }
    var notes by remember(client) { mutableStateOf(client?.notes ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (client == null) "Cadastrar cliente" else "Editar cliente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Telefone/WhatsApp") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Observações") }, minLines = 2)
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onSave(name.trim(), phone.trim(), notes.trim()) }) { Text("Salvar") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun PaymentDialog(order: Order, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var value by remember(order) { mutableStateOf("") }
    val remaining = (order.totalValue - order.paidValue).coerceAtLeast(0.0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar pagamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cliente: " + order.customerName)
                Text("Saldo atual: R$ %.2f".format(remaining))
                OutlinedTextField(value, { value = it.replace(",", ".") }, label = { Text("Valor recebido") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = value.toDoubleOrNull() ?: 0.0
                if (amount > 0 && amount <= remaining + 0.005) onSave(amount)
            }) { Text("Registrar") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ProductDialog(product: Product?, onDismiss: () -> Unit, onSave: (String, Int, Double, Double) -> Unit) {
    var name by remember(product) { mutableStateOf(product?.name ?: "") }
    var quantity by remember(product) { mutableStateOf(product?.quantity?.toString() ?: "0") }
    var entry by remember(product) { mutableStateOf(product?.entryValue?.toString() ?: "0") }
    var exit by remember(product) { mutableStateOf(product?.exitValue?.toString() ?: "0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Cadastrar produto" else "Editar produto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit) }, label = { Text("Quantidade em estoque") }, singleLine = true)
                OutlinedTextField(entry, { entry = it.replace(",", ".") }, label = { Text("Valor de entrada (custo/unidade)") }, singleLine = true)
                OutlinedTextField(exit, { exit = it.replace(",", ".") }, label = { Text("Valor de saída (venda/unidade)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = quantity.toIntOrNull() ?: 0
                val e = entry.toDoubleOrNull() ?: 0.0
                val s = exit.toDoubleOrNull() ?: 0.0
                if (name.isNotBlank()) onSave(name.trim(), q, e, s)
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
private fun OrderDialog(product: Product, order: Order?, onDismiss: () -> Unit, onSave: (String, Int, Long, Double) -> Unit) {
    var customer by remember(order) { mutableStateOf(order?.customerName ?: "") }
    var quantity by remember(order) { mutableStateOf(order?.quantity?.toString() ?: "1") }
    var delivery by remember(order) { mutableStateOf(if (order == null) dateOnly(defaultDeliveryDate()) else dateOnly(order.deliveryDate)) }
    var paid by remember(order) { mutableStateOf(order?.paidValue?.toString() ?: "0") }
    var validationError by remember(order) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (order == null) "Nova encomenda" else "Editar encomenda") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Produto: " + product.name)
                Text("Valor de venda: R$ %.2f/un.".format(order?.unitValue ?: product.exitValue))
                OutlinedTextField(customer, { customer = it }, label = { Text("Nome do cliente") }, singleLine = true)
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
                if (customer.isBlank()) {
                    validationError = "Informe o nome do cliente."
                } else if (q <= 0) {
                    validationError = "Informe uma quantidade válida."
                } else {
                    onSave(customer.trim(), q, parseDate(delivery, defaultDeliveryDate()), p)
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