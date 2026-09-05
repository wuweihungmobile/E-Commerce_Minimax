// ERP Dashboard Endpoints
export const API_ENDPOINTS_ERP = {
  // Suppliers
  suppliers: {
    list: '/v2/dashboard/suppliers',
    detail: (id: string) => '/v2/dashboard/suppliers/' + id,
    create: '/v2/dashboard/suppliers',
    update: (id: string) => '/v2/dashboard/suppliers/' + id,
  },

  // Purchase Orders
  purchaseOrders: {
    list: '/v2/dashboard/purchase-orders',
    detail: (id: string) => '/v2/dashboard/purchase-orders/' + id,
    create: '/v2/dashboard/purchase-orders',
    update: (id: string) => '/v2/dashboard/purchase-orders/' + id,
    submit: (id: string) => '/v2/dashboard/purchase-orders/' + id + '/submit',
    receive: (id: string) => '/v2/dashboard/purchase-orders/' + id + '/receive',
    cancel: (id: string) => '/v2/dashboard/purchase-orders/' + id + '/cancel',
    // DEF-076（Sprint 129）：建單品項需綁定平台 listing，原本前端沒有任何選擇來源
    listingOptions: '/v2/dashboard/purchase-orders/listing-options',
  },

  // Inventory
  inventory: {
    list: '/v2/dashboard/inventory',
    detail: (skuId: string) => '/v2/dashboard/inventory/' + skuId,
    alerts: '/v2/dashboard/inventory/alerts',
  },

  // Stock Movements
  stockMovements: {
    list: '/v2/dashboard/stock-movements',
    create: '/v2/dashboard/stock-movements',
  },
}