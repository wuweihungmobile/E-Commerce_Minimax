// 網站名稱與預設標題（Sprint 332，DEF-465）。根 layout 的 metadata、首頁、賣家後台／平台管理台 layout 的標題模板都從這裡取，
// 品牌名稱只寫一處。
//
// 頁面標題的組成（Next 的 metadata API，只有 Server Component 與 layout 能匯出）：
//   一般頁面            「頁名｜意象若水 RUOSHUI」          根 layout 的 title.template
//   賣家後台 /dashboard/* 「頁名｜賣家後台｜意象若水 RUOSHUI」  app/dashboard/layout.tsx 的 title.template
//   平台管理台 /admin/*   「頁名｜平台管理｜意象若水 RUOSHUI」  app/admin/layout.tsx 的 title.template
//   首頁                完整品牌標題（title.absolute）
// 為什麼賣家與平台管理要有區域後綴：「客服工單」「店鋪管理」「訂單詳情」在買家、賣家、平台三邊各有一頁，沒有區域後綴的話
// 分頁列、瀏覽紀錄、螢幕閱讀器念出的標題會一模一樣。

export const SITE_NAME = '意象若水 RUOSHUI'
export const SITE_TITLE = `${SITE_NAME}｜生活減法，無負擔的購物體驗`
export const SITE_DESCRIPTION = `${SITE_NAME} 電商平台`

/** 一般頁面的標題模板（%s 是頁名） */
export const TITLE_TEMPLATE = `%s｜${SITE_NAME}`
/** 賣家後台的標題模板 */
export const DASHBOARD_TITLE_TEMPLATE = `%s｜賣家後台｜${SITE_NAME}`
/** 平台管理台的標題模板 */
export const ADMIN_TITLE_TEMPLATE = `%s｜平台管理｜${SITE_NAME}`
