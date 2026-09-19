package br.com.controlequeijos

import android.content.Context
import android.os.Bundle
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
    var productDialog by remember { mutableStateOf(false) }
    var saleDialogProduct by remember { mutableStateOf<Product?>(null) }
    var orderDialogProduct by remember { mutableStateOf<Product?>(null) }
    var editingOrder by remember { mutableStateOf<Order?>(null) }
    var expenseDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var period by remember { mutableStateOf("Todos") }

    fun persistProducts(p: List<Product>) { products = p; saveProducts(context, p) }
    fun persistSales(s: List<Sale>) { sales = s; saveSales(context, s) }
    fun persistExpenses(e: List<Expense>) { expenses = e; saveExpenses(context, e) }
    fun persistOrders(o: List<Order>) { orders = o; saveOrders(context, o) }

    val filteredSales = sales.filter { inPeriod(it.date, period) }
    val filteredExpenses = expenses.filter { inPeriod(it.date, period) }
    val filteredOrders = orders.filter { inPeriod(it.orderDate, period) }
    val deliveredOrders = filteredOrders.filter { it.status == "Entregue" }
    val saleRevenue = filteredSales.sumOf { it.quantity * it.unitValue }
    val saleCost = filteredSales.sumOf { it.quantity * it.unitCost }
    val orderRevenue = deliveredOrders.sumOf { it.totalValue }
    val orderCost = deliveredOrders.sumOf { it.quantity * it.unitCost }
    val revenue = saleRevenue + orderRevenue
    val cost = saleCost + orderCost
    val expenseTotal = filteredExpenses.sumOf { it.value }
    val profit = revenue - cost - expenseTotal
    val ordersTotal = filteredOrders.sumOf { it.totalValue }
    val ordersPaid = filteredOrders.sumOf { it.paidValue }
    val ordersReceivable = ordersTotal - ordersPaid

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("Controle Queijos — V5") }) }) { pad ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Resumo financeiro", style = MaterialTheme.typography.headlineSmall)
                    Text("Vendas realizadas: R$ %.2f".format(saleRevenue))
                    Text("Encomendas entregues: R$ %.2f".format(orderRevenue))
                    Text("Custo das mercadorias: R$ %.2f".format(cost))
                    Text("Gastos: R$ %.2f".format(expenseTotal))
                    Text("Lucro: R$ %.2f".format(profit))
                    Spacer(Modifier.height(6.dp))
                    Text("Encomendas no período: R$ %.2f".format(ordersTotal))
                    Text("Recebido de encomendas: R$ %.2f".format(ordersPaid))
                    Text("A receber: R$ %.2f".format(ordersReceivable))
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
                }

                item { Text("Estoque", style = MaterialTheme.typography.headlineSmall) }
                if (products.isEmpty()) item { Text("Nenhum produto cadastrado.") }
                items(products, key = { it.id }) { p ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text("Estoque: " + p.quantity)
                            Text("Entrada: R$ %.2f/un.".format(p.entryValue))
                            Text("Saída: R$ %.2f/un.".format(p.exitValue))
                            Text("Lucro por unidade: R$ %.2f".format(p.exitValue - p.entryValue))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { if (p.quantity > 0) saleDialogProduct = p }) { Text("Registrar venda") }
                                OutlinedButton(onClick = { orderDialogProduct = p }) { Text("Encomenda") }
                                OutlinedButton(onClick = { editingProduct = p; productDialog = true }) { Text("Editar") }
                            }
                        }
                    }
                }

                item { Text("Encomendas (" + filteredOrders.size + ")", style = MaterialTheme.typography.headlineSmall) }
                if (filteredOrders.isEmpty()) item { Text("Nenhuma encomenda no período.") }
                items(filteredOrders.sortedByDescending { it.orderDate }, key = { it.id }) { o ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(o.customerName, style = MaterialTheme.typography.titleMedium)
                            Text(o.productName + " — " + o.quantity + " un.")
                            Text("Total: R$ %.2f".format(o.totalValue))
                            Text("Pago: R$ %.2f | Saldo: R$ %.2f".format(o.paidValue, o.totalValue - o.paidValue))
                            Text("Pedido: " + dateOnly(o.orderDate) + " | Entrega: " + dateOnly(o.deliveryDate))
                            Text("Status: " + o.status)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (o.status != "Entregue" && o.status != "Cancelada") {
                                    Button(onClick = {
                                        val p = products.find { it.id == o.productId }
                                        if (p != null && p.quantity >= o.quantity && !o.stockApplied) {
                                            persistProducts(products.map { if (it.id == p.id) p.copy(quantity = p.quantity - o.quantity) else it })
                                            persistOrders(orders.map { if (it.id == o.id) o.copy(status = "Entregue", stockApplied = true) else it })
                                        }
                                    }) { Text("Entregar") }
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
            if (q in 1..p.quantity) {
                val now = System.currentTimeMillis()
                persistSales(sales + Sale(now, p.id, p.name, q, p.exitValue, p.entryValue, now))
                persistProducts(products.map { if (it.id == p.id) p.copy(quantity = p.quantity - q) else it })
            }
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
                Text("Estoque disponível: " + product.quantity)
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
                    { quantity = it.filter(Char::isDigit) },
                    label = { Text(if (order?.stockApplied == true) "Quantidade (entregue)" else "Quantidade") },
                    enabled = order?.stockApplied != true,
                    singleLine = true
                )
                OutlinedTextField(delivery, { delivery = it }, label = { Text("Data prevista (dd/MM/yyyy)") }, singleLine = true)
                OutlinedTextField(paid, { paid = it.replace(",", ".") }, label = { Text("Valor pago") }, singleLine = true)
                if (order == null) Text("Status inicial: Pendente")
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = quantity.toIntOrNull() ?: 0
                val p = paid.toDoubleOrNull() ?: 0.0
                if (customer.isNotBlank() && q > 0) onSave(customer.trim(), q, parseDate(delivery, defaultDeliveryDate()), p)
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
