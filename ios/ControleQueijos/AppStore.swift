import Foundation
import SwiftUI

struct Client: Identifiable, Codable {
    let id: UUID
    var name: String
    var phone: String
}

struct Order: Identifiable, Codable {
    let id: UUID
    var clientName: String
    var productName: String
    var quantity: Int
    var totalValue: Double
    var status: String
    var deliveryDate: Date
}

@MainActor
final class AppStore: ObservableObject {
    @Published var clients: [Client] = []
    @Published var orders: [Order] = []

    private let clientsKey = "controle_queijos_clients_ios"
    private let ordersKey = "controle_queijos_orders_ios"

    init() {
        load()
    }

    func load() {
        if let data = UserDefaults.standard.data(forKey: clientsKey),
           let decoded = try? JSONDecoder().decode([Client].self, from: data) {
            clients = decoded
        }
        if let data = UserDefaults.standard.data(forKey: ordersKey),
           let decoded = try? JSONDecoder().decode([Order].self, from: data) {
            orders = decoded
        }
    }

    func save() {
        if let data = try? JSONEncoder().encode(clients) {
            UserDefaults.standard.set(data, forKey: clientsKey)
        }
        if let data = try? JSONEncoder().encode(orders) {
            UserDefaults.standard.set(data, forKey: ordersKey)
        }
    }

    var pendingOrders: [Order] {
        orders.filter { $0.status != "Entregue" && $0.status != "Cancelada" }
    }

    var revenue: Double {
        orders.filter { $0.status != "Cancelada" }.reduce(0) { $0 + $1.totalValue }
    }
}
