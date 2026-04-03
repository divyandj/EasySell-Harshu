# Custom Domain Support for EasySell Storefronts

Allow 1-2 store owners to use their own domain (e.g., `www.mystore.com`) alongside the existing `store.mmproperty.in` subdomain system — **completely free, no Vercel Pro needed.**

## How It Works (Free)

Vercel's **Hobby plan** allows you to add custom domains manually via the dashboard UI. The API restriction is only for automation at scale. For 1-2 stores, manual setup is perfect.

```
                        ┌─ divyan.mmproperty.in ──→ getSubdomain() → "divyan"
User visits URL ───────┤
                        └─ www.mystore.com ────────→ resolveStoreContext() 
                                                       → Firestore lookup by customDomain
                                                       → finds "divyan"
```

## Step-by-Step: Adding a Custom Domain (Manual, One-Time)

### 1. Vercel Dashboard (you do this, ~2 min)
- Go to your Vercel project → **Settings → Domains**
- Click **Add** → type `www.mystore.com` → Add
- Vercel shows the required DNS records

### 2. Store Owner's Domain Registrar (they do this)
- Add a **CNAME** record: `www` → `cname.vercel-dns.com`
- Or for apex domain (`mystore.com`): **A record** → `76.76.21.21`

### 3. Firestore (you do this, ~1 min)
- Open the store owner's `users` document in Firebase Console
- Add field: `customDomain` = `"www.mystore.com"`

That's it. No backend API needed, no Vercel Pro needed.

---

## Proposed Code Changes

Only **2 files** need modification, plus **1 new utility file**.

### Frontend Store Resolution

#### [NEW] [storeResolver.js](file:///c:/Users/Divyan/AndroidStudioProjects/EasySellHarshu/EasySell-WEB/easysell-webapp/src/utils/storeResolver.js)

Single shared utility replacing the 3 duplicate [getSubdomain()](file:///c:/Users/Divyan/AndroidStudioProjects/EasySellHarshu/EasySell-WEB/easysell-webapp/src/context/AuthContext.js#318-329) functions:

```js
export const resolveStoreContext = () => {
  const host = window.location.hostname;
  const parts = host.split('.');

  // Local dev: store.localhost
  if (host.includes('localhost') && parts.length >= 2 && parts[0] !== 'www') {
    return { type: 'subdomain', handle: parts[0] };
  }

  // Production subdomain: store.mmproperty.in
  const rootDomain = parts.slice(-2).join('.');
  if (rootDomain === 'mmproperty.in' && parts.length >= 3 && parts[0] !== 'www') {
    return { type: 'subdomain', handle: parts[0] };
  }

  // Custom domain: anything else (www.mystore.com, mystore.com)
  if (!host.includes('localhost') && rootDomain !== 'mmproperty.in') {
    return { type: 'customDomain', domain: host };
  }

  return { type: 'main' };
};
```

---

#### [MODIFY] [AuthContext.js](file:///c:/Users/Divyan/AndroidStudioProjects/EasySellHarshu/EasySell-WEB/easysell-webapp/src/context/AuthContext.js)

- Replace local [getSubdomain()](file:///c:/Users/Divyan/AndroidStudioProjects/EasySellHarshu/EasySell-WEB/easysell-webapp/src/context/AuthContext.js#318-329) with `resolveStoreContext()`
- When `type === 'customDomain'` → query: `users WHERE customDomain == domain`
- When `type === 'subdomain'` → existing query: `users WHERE storeHandle == handle`
- Expose the resolved `storeHandle` string in context for other components

#### [MODIFY] [App.js](file:///c:/Users/Divyan/AndroidStudioProjects/EasySellHarshu/EasySell-WEB/easysell-webapp/src/App.js)

- Replace [getSubdomain()](file:///c:/Users/Divyan/AndroidStudioProjects/EasySellHarshu/EasySell-WEB/easysell-webapp/src/context/AuthContext.js#318-329) with `resolveStoreContext()`
- For custom domain visits, read the resolved handle from AuthContext instead of computing locally

#### [MODIFY] [CheckoutPage.js](file:///c:/Users/Divyan/AndroidStudioProjects/EasySellHarshu/EasySell-WEB/easysell-webapp/src/pages/CheckoutPage.js)

- Remove local [getSubdomain()](file:///c:/Users/Divyan/AndroidStudioProjects/EasySellHarshu/EasySell-WEB/easysell-webapp/src/context/AuthContext.js#318-329) definition
- Consume `storeHandle` from AuthContext instead

> [!NOTE]
> No backend changes needed. No Android changes needed. The Firestore `customDomain` field is added manually via Firebase Console for the 1-2 stores that need it.

---

## Verification Plan

### Manual Verification
1. Visit `divyan.mmproperty.in` → storefront loads (regression ✅)
2. Visit `www.mmproperty.in` → main HomePage loads (regression ✅)
3. Visit `www.customdomain.com` → correct storefront loads via Firestore lookup
4. Place an order on the custom domain → notifications and analytics still work
