package br.com.controlequeijos

import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

@Serializable
data class CloudClient(val id: Long, val user_id: String, val name: String, val phone: String = "")

@Serializable
data class CloudProduct(val id: Long, val user_id: String, val name: String, val quantity: Int = 0, val entry_value: Double = 0.0, val exit_value: Double = 0.0)

@Serializable
data class CloudOrder(
    val id: Long, val user_id: String, val customer_id: Long? = null, val customer_name: String = "",
    val product_id: Long? = null, val product_name: String = "", val quantity: Int = 0,
    val unit_value: Double = 0.0, val unit_cost: Double = 0.0, val total_value: Double = 0.0,
    val paid_value: Double = 0.0, val order_date: Long = 0, val delivery_date: Long = 0,
    val status: String = "Pendente", val stock_applied: Boolean = false
)

@Serializable
data class CloudPayment(val id: Long, val user_id: String, val order_id: Long? = null, val amount: Double = 0.0, val payment_date: Long = 0, val note: String = "")

@Serializable
data class CloudSale(val id: Long, val user_id: String, val product_id: Long? = null, val product_name: String = "", val quantity: Int = 0, val unit_value: Double = 0.0, val unit_cost: Double = 0.0, val sale_date: Long = 0)

@Serializable
data class CloudExpense(val id: Long, val user_id: String, val description: String = "", val value: Double = 0.0, val expense_date: Long = 0)

data class ControleQueijosCloudData(
    val clients: List<Client>,
    val products: List<Product>,
    val orders: List<Order>,
    val payments: List<Payment>,
    val sales: List<Sale>,
    val expenses: List<Expense>
)

suspend fun uploadControleQueijos(
    products: List<Product>, sales: List<Sale>, expenses: List<Expense>,
    orders: List<Order>, clients: List<Client>, payments: List<Payment>
): Result<Unit> = runCatching {
    val userId = controleQueijosSupabase.auth.currentUserOrNull()?.id
        ?: error("Faça login na nuvem antes de sincronizar.")

    clients.forEach { c -> controleQueijosSupabase.from("cq_clients").upsert(CloudClient(c.id, userId, c.name, c.phone)) }
    products.forEach { p -> controleQueijosSupabase.from("cq_products").upsert(CloudProduct(p.id, userId, p.name, p.quantity, p.entryValue, p.exitValue)) }
    orders.forEach { o ->
        controleQueijosSupabase.from("cq_orders").upsert(
            CloudOrder(o.id, userId, o.customerId, o.customerName, o.productId, o.productName, o.quantity,
                o.unitValue, o.unitCost, o.totalValue, o.paidValue, o.orderDate, o.deliveryDate, o.status, o.stockApplied)
        )
    }
    payments.forEach { p -> controleQueijosSupabase.from("cq_payments").upsert(CloudPayment(p.id, userId, p.orderId, p.amount, p.date, p.note)) }
    sales.forEach { s -> controleQueijosSupabase.from("cq_sales").upsert(CloudSale(s.id, userId, s.productId, s.productName, s.quantity, s.unitValue, s.unitCost, s.date)) }
    expenses.forEach { e -> controleQueijosSupabase.from("cq_expenses").upsert(CloudExpense(e.id, userId, e.description, e.value, e.date)) }
}

suspend fun downloadControleQueijos(): Result<ControleQueijosCloudData> = runCatching {
    val userId = controleQueijosSupabase.auth.currentUserOrNull()?.id
        ?: error("Faça login na nuvem antes de baixar os dados.")

    val clients = controleQueijosSupabase.from("cq_clients").select { filter { eq("user_id", userId) } }.decodeList<CloudClient>()
    val products = controleQueijosSupabase.from("cq_products").select { filter { eq("user_id", userId) } }.decodeList<CloudProduct>()
    val orders = controleQueijosSupabase.from("cq_orders").select { filter { eq("user_id", userId) } }.decodeList<CloudOrder>()
    val payments = controleQueijosSupabase.from("cq_payments").select { filter { eq("user_id", userId) } }.decodeList<CloudPayment>()
    val sales = controleQueijosSupabase.from("cq_sales").select { filter { eq("user_id", userId) } }.decodeList<CloudSale>()
    val expenses = controleQueijosSupabase.from("cq_expenses").select { filter { eq("user_id", userId) } }.decodeList<CloudExpense>()

    ControleQueijosCloudData(
        clients = clients.map { Client(it.id, it.name, it.phone, "") },
        products = products.map { Product(it.id, it.name, it.quantity, it.entry_value, it.exit_value) },
        orders = orders.map { Order(it.id, it.customer_id ?: 0L, it.customer_name, it.product_id ?: 0L, it.product_name, it.quantity, it.unit_value, it.unit_cost, it.total_value, it.paid_value, it.order_date, it.delivery_date, it.status, it.stock_applied) },
        payments = payments.map { Payment(it.id, it.order_id ?: 0L, it.amount, it.payment_date, it.note) },
        sales = sales.map { Sale(it.id, it.product_id ?: 0L, it.product_name, it.quantity, it.unit_value, it.unit_cost, it.sale_date) },
        expenses = expenses.map { Expense(it.id, it.description, it.value, it.expense_date) }
    )
}

fun saveCloudDataLocally(context: android.content.Context, data: ControleQueijosCloudData) {
    saveClients(context, data.clients)
    saveProducts(context, data.products)
    saveOrders(context, data.orders)
    savePayments(context, data.payments)
    saveSales(context, data.sales)
    saveExpenses(context, data.expenses)
}
