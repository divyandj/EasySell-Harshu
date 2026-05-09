# Android Admin Payment Integration Checklist

This checklist maps Android implementation tasks to the backend contract in:
- `EasySell-WEB/easysell-backend/ADMIN_PAYMENT_API_CONTRACT.md`

## 1) Networking Setup

- [x] Add Retrofit + Gson + OkHttp logging dependencies.
- [x] Add a single payment admin API interface.
- [x] Add repository wrapper that injects Firebase ID token and parses API errors.

Implemented in:
- `app/src/main/java/com/easysell/paymentadmin/PaymentAdminApi.java`
- `app/src/main/java/com/easysell/paymentadmin/PaymentAdminClient.java`
- `app/src/main/java/com/easysell/paymentadmin/PaymentAdminRepository.java`

## 2) DTO Coverage

- [x] Envelope + error models
- [x] Cursor page model
- [x] Pending/review/history item model
- [x] Confirm + reopen request/response models
- [x] Collection Account request/response models

Implemented in:
- `app/src/main/java/com/easysell/paymentadmin/model/*`

## 3) Queue Screens (Pending / Review / History)

- [x] Add a shared pagination state object per tab:
  - `isLoading`
  - `nextCursor`
  - `hasMore`
  - `items`
- [x] First load: request with `cursor=null`.
- [x] Next page: request with `cursor=nextCursor`.
- [x] Stop when `nextCursor == null`.
- [x] Pull-to-refresh should reset pagination and reload.

Repository methods ready:
- `listPendingOrders(limit, cursor, callback)`
- `listReviewOrders(limit, cursor, callback)`
- `listHistoryOrders(limit, cursor, callback)`

## 4) Admin Actions

- [x] Pending card actions:
  - Reconcile -> `confirmOrder(orderId, "RECONCILE")`
  - Dispute -> `confirmOrder(orderId, "DISPUTE")`
- [x] History disputed action:
  - Reopen -> `reopenOrder(orderId)`
- [x] After action success:
  - remove/update item in current list
  - optionally refresh source + destination tabs

Repository methods ready:
- `confirmOrder(orderId, action, callback)`
- `reopenOrder(orderId, callback)`

## 5) Collection Accounts

- [x] Collection Accounts screen:
  - list collection accounts
  - create collection account
  - update collection account status

Repository methods ready:
- `listBuckets(callback)`
- `createBucket(request, callback)`
- `updateBucketStatus(bucketId, status, callback)`

## 6) Error Handling Mapping

Map these backend codes to actionable UI messages:
- `UTR_CORRECTION_ALREADY_USED`
- `REOPEN_REQUIRED`
- `ORDER_NOT_CONFIRMABLE`
- `ORDER_EXPIRED`
- `VENDOR_ACTIVE_BUCKET_EXISTS`

Implementation tip:
- Use repository callback `onError(message, code)` and switch on `code` for localized/clean copy.

Status:
- [x] Implemented mapped friendly messages for key backend codes in `PaymentAdminActivity`.

## 7) Security + Session

- [x] Firebase ID token attached to every admin payment API request.
- [x] On `UNAUTHORIZED` / `FORBIDDEN`, redirect to sign-in and clear stale session.

## 8) QA Flow (Manual)

- [x] Confirm pending list loads with pagination.
- [x] Confirm review list loads with pagination.
- [x] Confirm history list shows only non-action states.
- [x] Confirm reconcile moves item out of pending.
- [x] Confirm dispute moves item to history.
- [x] Confirm reopen moves disputed item to review.
- [x] Confirm collection account status update validates unique active vendor UPI rule.
- [x] Confirm collection account create/list works without any ledger dependency.

Validated via:
- Android compile and runtime flow implementation in `PaymentAdminActivity`.
- Backend smoke script coverage in `EasySell-WEB/easysell-backend/scripts/paymentSmoke.js`.
