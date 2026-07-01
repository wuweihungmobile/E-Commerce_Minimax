// API Configuration
export const API_CONFIG = {
  baseUrl: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api',
  timeout: 30000,
}

// API Endpoints
export const API_ENDPOINTS = {
  // Auth
  auth: {
    register: '/v2/auth/register',
    login: '/v2/auth/login',
    refresh: '/v2/auth/refresh',
  },

  // Listings
  listings: {
    list: '/v2/listings',
    detail: (id: string) => '/v2/listings/' + id,
    create: '/v2/dashboard/listings',
    update: (id: string) => '/v2/dashboard/listings/' + id,
    delete: (id: string) => '/v2/dashboard/listings/' + id,
  },

  // Cart
  cart: {
    get: '/v2/cart',
    add: '/v2/cart/items',
    update: (id: string) => '/v2/cart/items/' + id,
    remove: (id: string) => '/v2/cart/items/' + id,
    clear: '/v2/cart',
    count: '/v2/cart/count',
    applyPromo: '/v2/cart/apply-promo',
    removePromo: '/v2/cart/promo',
    validatePromo: '/v2/cart/validate-promo',
  },

  // Orders
  orders: {
    list: '/v2/orders',
    detail: (id: string) => '/v2/orders/' + id,
    create: '/v2/orders',
    cancel: (id: string) => '/v2/orders/' + id + '/cancel',
  },

  // Payments
  payments: {
    create: '/v2/payments',
    mock: '/v2/payments/mock',
    callback: '/v2/payments/callback',
  },

  // Bookings
  bookings: {
    list: '/v2/bookings',
    detail: (id: string) => '/v2/bookings/' + id,
    create: '/v2/bookings',
    cancel: (id: string) => '/v2/bookings/' + id + '/cancel',
    calendar: (listingId: string) => '/v2/listings/' + listingId + '/calendar',
  },

  // Tenants (M17)
  tenants: {
    apply: '/v2/tenants/apply',
    list: '/v2/tenants',  // 取得當前用戶的店鋪列表
    detail: (id: string) => '/v2/tenants/' + id,
    update: (id: string) => '/v2/tenants/' + id,
  },

  // Products (M01)
  products: {
    list: '/v2/products',
    detail: (id: string) => '/v2/products/' + id,
    create: '/v2/products',
    update: (id: string) => '/v2/products/' + id,
    delete: (id: string) => '/v2/products/' + id,
  },

  // Rooms (M02)
  rooms: {
    list: '/v2/rooms',
    detail: (id: string) => '/v2/rooms/' + id,
    create: '/v2/rooms',
    update: (id: string) => '/v2/rooms/' + id,
    delete: (id: string) => '/v2/rooms/' + id,
  },

  // Dashboard
  dashboard: {
    analytics: '/v2/dashboard/analytics',
    listings: '/v2/dashboard/listings',
    orders: '/v2/dashboard/orders',
    bookings: '/v2/dashboard/bookings',
    tenants: {
      features: '/v2/dashboard/tenants/features',
      updateFeature: (feature: string) => '/v2/dashboard/tenants/features/' + feature,
    },
  },

  // Analytics（M14，對齊 AnalyticsController /v2/dashboard/*）
  analytics: {
    stats: '/v2/dashboard/stats',
    revenue: '/v2/dashboard/revenue',
    orders: '/v2/dashboard/orders',
    listings: '/v2/dashboard/listings',
    activity: '/v2/dashboard/activity',
  },

  // Dashboard Tenants (M17)
  dashboardTenants: {
    features: '/v2/dashboard/tenants/features',
    updateFeature: (feature: string) => '/v2/dashboard/tenants/features/' + feature,
  },

  // Pricing (M12)
  pricing: {
    rules: '/v2/pricing/rules',
    createRule: '/v2/pricing/rules',
    updateRule: (id: string) => '/v2/pricing/rules/' + id,
    deleteRule: (id: string) => '/v2/pricing/rules/' + id,
    calculate: '/v2/pricing/calculate',
    calendarPrice: '/v2/pricing/calendar/price',
  },

  // Admin
  admin: {
    tenants: {
      list: '/v2/admin/tenants',
      detail: (id: string) => '/v2/admin/tenants/' + id,
      approve: (id: string) => '/v2/admin/tenants/' + id + '/approve',
      reject: (id: string) => '/v2/admin/tenants/' + id + '/reject',
      features: (id: string) => '/v2/admin/tenants/' + id + '/feature-toggles',
      updateFeature: (tenantId: string, feature: string) => '/v2/admin/tenants/' + tenantId + '/features/' + feature,
    },
  },

  // Media (M18)
  media: {
    list: '/v2/media',
    detail: (id: string) => '/v2/media/' + id,
    upload: '/v2/media/upload',
    update: (id: string) => '/v2/media/' + id,
    delete: (id: string) => '/v2/media/' + id,
    count: '/v2/media/count',
    categories: '/v2/media/categories',
    createCategory: '/v2/media/categories',
    updateCategory: (id: string) => '/v2/media/categories/' + id,
    deleteCategory: (id: string) => '/v2/media/categories/' + id,
  },

  // Knowledge (M18)
  knowledge: {
    list: '/v2/knowledge',
    detail: (id: string) => '/v2/knowledge/' + id,
    slug: (slug: string) => '/v2/knowledge/slug/' + slug,
    create: '/v2/knowledge',
    update: (id: string) => '/v2/knowledge/' + id,
    delete: (id: string) => '/v2/knowledge/' + id,
    incrementView: (id: string) => '/v2/knowledge/' + id + '/view',
    categories: '/v2/knowledge/categories',
    createCategory: '/v2/knowledge/categories',
    updateCategory: (id: string) => '/v2/knowledge/categories/' + id,
    deleteCategory: (id: string) => '/v2/knowledge/categories/' + id,
  },

  // FAQ (M18)
  faqs: {
    list: '/v2/faqs',
    detail: (id: string) => '/v2/faqs/' + id,
    slug: (slug: string) => '/v2/faqs/slug/' + slug,
    create: '/v2/faqs',
    update: (id: string) => '/v2/faqs/' + id,
    delete: (id: string) => '/v2/faqs/' + id,
    incrementView: (id: string) => '/v2/faqs/' + id + '/view',
    categories: '/v2/faqs/categories',
    createCategory: '/v2/faqs/categories',
    updateCategory: (id: string) => '/v2/faqs/categories/' + id,
    deleteCategory: (id: string) => '/v2/faqs/categories/' + id,
  },

  // Notification Templates (M09)
  notificationTemplates: {
    list: '/v2/notification-templates',
    detail: (id: string) => '/v2/notification-templates/' + id,
    create: '/v2/dashboard/notification-templates',
    update: (id: string) => '/v2/dashboard/notification-templates/' + id,
    delete: (id: string) => '/v2/dashboard/notification-templates/' + id,
    render: '/v2/notification-templates/render',
  },
}

export default API_CONFIG
