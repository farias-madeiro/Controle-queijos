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

data class Product(val id: Long, val name: String, val quantity: Int, val entryValue: Double, val exitValue: Double)
private const val PREFS = "controle_queijos"
private const val PRODUCTS = "products"
private const val EXPENSES = "expenses"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ControleQueijosApp(applicationContext) }
    }
}

private fun loadProducts(context: Context): List<Product> = runCatching {
    val json = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PRODUCTS, "[]"))
    List(json.length()) { i -> val o = json.getJSONObject(i); Product(o.getLong("id"), o.getString("name"), o.getInt("quantity"), if (o.has("entryValue")) o.getDouble("entryValue") else 0.0, if (o.has("exitValue")) o.getDouble("exitValue") else o.optDouble("value", 0.0)) }
}.getOrDefault(emptyList())

private fun saveProducts(context: Context, products: List<Product>) {
    val json = JSONArray()
    products.forEach { p -> json.put(JSONObject().apply { put("id", p.id); put("name", p.name); put("quantity", p.quantity); put("entryValue", p.entryValue); put("exitValue", p.exitValue) }) }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PRODUCTS, json.toString()).apply()
}
private fun loadExpenses(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(EXPENSES, 0f).toDouble()
private fun saveExpenses(context: Context, v: Double) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(EXPENSES, v.toFloat()).apply() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ControleQueijosApp(context: Context) {
    var products by remember { mutableStateOf(loadProducts(context)) }
    var expenses by remember { mutableDoubleStateOf(loadExpenses(context)) }
    var dialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Product?>(null) }
    fun persist(p: List<Product>) { products = p; saveProducts(context, p) }
    val revenue = products.sumOf { it.quantity * it.exitValue }; val cost = products.sumOf { it.quantity * it.entryValue }; val profit = revenue - cost - expenses
    MaterialTheme { Scaffold(topBar = { TopAppBar(title = { Text("Controle Queijos — V3") }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Resumo", style = MaterialTheme.typography.headlineSmall); Text("Vendas: R$ %.2f".format(revenue)); Text("Custo das mercadorias: R$ %.2f".format(cost)); Text("Gastos: R$ %.2f".format(expenses)); Text("Lucro: R$ %.2f".format(profit)) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { expenses += 10.0; saveExpenses(context, expenses) }) { Text("Adicionar gasto R$ 10") }; OutlinedButton(onClick = { expenses = 0.0; saveExpenses(context, expenses) }) { Text("Zerar gastos") } } }
            item { Button(onClick = { editing = null; dialog = true }) { Text("Cadastrar produto") } }
            item { Text("Produtos", style = MaterialTheme.typography.headlineSmall) }
            if (products.isEmpty()) item { Text("Nenhum produto cadastrado.") }
            items(products, key = { it.id }) { p ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium); Text("Quantidade: ${p.quantity}"); Text("Entrada: R$ %.2f por unidade".format(p.entryValue)); Text("Saída: R$ %.2f por unidade".format(p.exitValue)); Text("Lucro por unidade: R$ %.2f".format(p.exitValue - p.entryValue)); Text("Lucro total: R$ %.2f".format((p.exitValue - p.entryValue) * p.quantity));
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { persist(products.map { if (it.id == p.id) it.copy(quantity = it.quantity + 1) else it }) }) { Text("+ Venda") }
                        OutlinedButton(onClick = { persist(products.map { if (it.id == p.id && it.quantity > 0) it.copy(quantity = it.quantity - 1) else it }) }) { Text("-") }
                        OutlinedButton(onClick = { editing = p; dialog = true }) { Text("Editar") }
                    }
                } }
            }
        }
    } }
    if (dialog) ProductDialog(editing, { dialog = false }, { name, q, entry, exit -> val e = editing; val updated = if (e == null) products + Product(System.currentTimeMillis(), name, q, entry, exit) else products.map { if (it.id == e.id) e.copy(name=name, quantity=q, entryValue=entry, exitValue=exit) else it }; persist(updated); dialog=false })
}

@Composable private fun ProductDialog(product: Product?, onDismiss: () -> Unit, onSave: (String, Int, Double, Double) -> Unit) {
    var name by remember(product) { mutableStateOf(product?.name ?: "") }; var quantity by remember(product) { mutableStateOf(product?.quantity?.toString() ?: "0") }; var entry by remember(product) { mutableStateOf(product?.entryValue?.toString() ?: "0") }; var exit by remember(product) { mutableStateOf(product?.exitValue?.toString() ?: "0") }
    AlertDialog(onDismissRequest=onDismiss, title={ Text(if(product==null) "Cadastrar produto" else "Editar produto") },
        text={ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(name,{name=it},label={Text("Nome")},singleLine=true); OutlinedTextField(quantity,{quantity=it.filter(Char::isDigit)},label={Text("Quantidade")},singleLine=true); OutlinedTextField(entry,{entry=it.replace(",","." )},label={Text("Valor de entrada (custo/unidade)")},singleLine=true); OutlinedTextField(exit,{exit=it.replace(",","." )},label={Text("Valor de saída (venda/unidade)")},singleLine=true) } },
        confirmButton={ Button(onClick={ val q=quantity.toIntOrNull()?:0; val e=entry.toDoubleOrNull()?:0.0; val s=exit.toDoubleOrNull()?:0.0; if(name.isNotBlank()) onSave(name.trim(),q,e,s) }) { Text("Salvar") } },
        dismissButton={ OutlinedButton(onClick=onDismiss) { Text("Cancelar") } })
}