package br.com.controlequeijos

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

data class Product(val name: String, val quantity: Int, val value: Double)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ControleQueijosApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControleQueijosApp() {
    var products by remember {
        mutableStateOf(listOf(
            Product("Queijo Minas", 0, 0.0),
            Product("Queijo Mussarela", 0, 0.0),
            Product("Queijo Coalho", 0, 0.0)
        ))
    }
    var expenses by remember { mutableDoubleStateOf(0.0) }
    val revenue = products.sumOf { it.value }
    val profit = revenue - expenses

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Controle Queijos — V1") }) }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Resumo", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Vendas: R$ %.2f".format(revenue))
                    Text("Gastos: R$ %.2f".format(expenses))
                    Text("Lucro: R$ %.2f".format(profit))
                }
                item {
                    Button(onClick = { expenses += 10.0 }) {
                        Text("Adicionar gasto de R$ 10")
                    }
                }
                item {
                    Text("Produtos", style = MaterialTheme.typography.headlineSmall)
                }
                items(products) { product ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(product.name, style = MaterialTheme.typography.titleMedium)
                            Text("Quantidade: ${product.quantity}")
                            Text("Vendas: R$ %.2f".format(product.value))
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    products = products.map {
                                        if (it.name == product.name) it.copy(quantity = it.quantity + 1, value = it.value + 1.0) else it
                                    }
                                }) { Text("+ Venda") }
                                OutlinedButton(onClick = {
                                    products = products.map {
                                        if (it.name == product.name && it.quantity > 0) it.copy(quantity = it.quantity - 1, value = (it.value - 1.0).coerceAtLeast(0.0)) else it
                                    }
                                }) { Text("-") }
                            }
                        }
                    }
                }
            }
        }
    }
}
