import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var store: AppStore
    @State private var selectedTab = 0

    private let tabs = [
        ("🏠", "Início"),
        ("👥", "Clientes"),
        ("📦", "Encomendas"),
        ("🚚", "Entregas"),
        ("🧀", "Produção"),
        ("💰", "Financeiro"),
        ("⚙️", "Backup"),
        ("🔄", "Sincronização")
    ]

    var body: some View {
        TabView(selection: $selectedTab) {
            dashboard.tag(0).tabItem { Label("Início", systemImage: "house") }
            clients.tag(1).tabItem { Label("Clientes", systemImage: "person.2") }
            orders.tag(2).tabItem { Label("Encomendas", systemImage: "shippingbox") }
            deliveries.tag(3).tabItem { Label("Entregas", systemImage: "truck.box") }
            production.tag(4).tabItem { Label("Produção", systemImage: "gearshape.2") }
            finance.tag(5).tabItem { Label("Financeiro", systemImage: "dollarsign.circle") }
            backup.tag(6).tabItem { Label("Backup", systemImage: "externaldrive") }
            sync.tag(7).tabItem { Label("Sincronização", systemImage: "arrow.triangle.2.circlepath") }
        }
        .tint(.blue)
    }

    private var dashboard: some View {
        NavigationStack {
            List {
                Section("Painel de controle") {
                    Text("Visão rápida do seu negócio")
                    metric("Faturamento", value: store.revenue, icon: "💰")
                    metric("Encomendas pendentes", value: Double(store.pendingOrders.count), icon: "📦", money: false)
                    metric("Clientes", value: Double(store.clients.count), icon: "👥", money: false)
                }
            }
            .navigationTitle("Controle Queijos — V1 iOS")
        }
    }

    private var clients: some View {
        NavigationStack {
            List(store.clients) { client in
                VStack(alignment: .leading) {
                    Text(client.name).font(.headline)
                    Text(client.phone).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Clientes")
        }
    }

    private var orders: some View {
        NavigationStack {
            List(store.orders) { order in
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(order.productName) — \(order.quantity) un.")
                        .font(.headline)
                    Text(order.clientName)
                    Text("Status: \(order.status)")
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Encomendas")
        }
    }

    private var deliveries: some View {
        NavigationStack {
            List(store.pendingOrders) { order in
                VStack(alignment: .leading) {
                    Text(order.productName).font(.headline)
                    Text("\(order.clientName) • \(order.quantity) un.")
                    Text(order.deliveryDate.formatted(date: .abbreviated, time: .omitted))
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Entregas")
        }
    }

    private var production: some View {
        NavigationStack {
            List {
                Section("Produção consolidada") {
                    ForEach(Dictionary(grouping: store.pendingOrders, by: { $0.productName })
                        .map { ($0.key, $0.value.reduce(0) { $0 + $1.quantity }) }
                        .sorted { $0.0 < $1.0 }, id: \.0) { product, quantity in
                            HStack {
                                Text(product)
                                Spacer()
                                Text("\(quantity) un.").bold()
                            }
                    }
                }
            }
            .navigationTitle("Produção")
        }
    }

    private var finance: some View {
        NavigationStack {
            List {
                Section("Resumo financeiro") {
                    metric("Faturamento", value: store.revenue, icon: "💰")
                    metric("A receber", value: store.pendingOrders.reduce(0) { $0 + $1.totalValue }, icon: "⚠️")
                }
            }
            .navigationTitle("Financeiro")
        }
    }

    private var backup: some View {
        NavigationStack {
            List {
                Section("Proteção dos dados") {
                    Text("Backup manual e automático serão integrados nesta versão.")
                    Text("PIN e perfil de usuário também serão incorporados.")
                }
            }
            .navigationTitle("Backup")
        }
    }

    private var sync: some View {
        NavigationStack {
            List {
                Section("Sincronização") {
                    Text("A sincronização por arquivo JSON seguirá o formato utilizado na V7.50 do Android.")
                    Text("A integração em nuvem será preparada posteriormente, sem alterar os dados existentes.")
                }
            }
            .navigationTitle("Sincronização")
        }
    }

    private func metric(_ title: String, value: Double, icon: String, money: Bool = true) -> some View {
        HStack {
            Text(icon)
            VStack(alignment: .leading) {
                Text(title).font(.subheadline)
                Text(money ? value.formatted(.currency(code: "BRL")) : value.formatted())
                    .font(.title3).bold()
            }
        }
    }
}
