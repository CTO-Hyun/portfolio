# API Examples

## Register
```
POST /api/v1/auth/register
{
  "email": "neo@example.com",
  "password": "P@ssw0rd!",
  "name": "Neo"
}
```

## Login
```
POST /api/v1/auth/login
{
  "email": "neo@example.com",
  "password": "P@ssw0rd!"
}
```
Response
```
{
  "accessToken": "jwt-token",
  "expiresIn": 3600
}
```

## Create Order
```
POST /api/v1/orders
Idempotency-Key: 572ac90d-63ba-4fbb-8245-0e7f99d4bdb8
{
  "items": [
    {"productId": 1, "quantity": 2}
  ]
}
```
Response
```
{
  "id": 42,
  "userId": 7,
  "status": "CREATED",
  "totalAmount": 20000,
  "idempotencyKey": "572ac90d-63ba-4fbb-8245-0e7f99d4bdb8",
  "items": [
    {"productId": 1, "quantity": 2, "price": 10000, "lineAmount": 20000}
  ]
}
```

## List Products
```
GET /api/v1/products?page=0&size=10
```
Response
```
{
  "content": [
    {"id": 1, "sku": "SKU-RED", "name": "Red Mug", "price": 15000},
    {"id": 2, "sku": "SKU-BLK", "name": "Black Tumbler", "price": 22000}
  ],
  "page": 0,
  "size": 10,
  "totalElements": 2
}
```

## Create Product (Admin)
```
POST /api/v1/admin/products
Authorization: Bearer <admin-token>
{
  "sku": "SKU-NEW",
  "name": "New Hoodie",
  "description": "Fleece lined",
  "price": 49000
}
```

## Adjust Stock (Admin)
```
POST /api/v1/admin/products/1/stock-adjust
Authorization: Bearer <admin-token>
{
  "delta": 15
}
```

## List Orders
```
GET /api/v1/orders
Authorization: Bearer <user-token>
```
Response
```
[
  {
    "id": 42,
    "status": "CREATED",
    "totalAmount": 20000,
    "createdAt": "2025-02-03T09:00:00Z"
  }
]
```

## Cancel Order
```
POST /api/v1/orders/42/cancel
Authorization: Bearer <user-token>
```
Response
```
{
  "id": 42,
  "status": "CANCELLED"
}
```

## Error Envelope
```
{
  "timestamp": "2025-01-02T12:55:34Z",
  "path": "/api/v1/orders",
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "details": ["items[0].quantity: must be greater than 0"]
}
```
