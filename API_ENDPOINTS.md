# API Endpoints Documentation

This document describes all REST API endpoints available in the Hybrid Feed System.

## Base URL
```
http://localhost:8080/api/v1
```

---

## Post Endpoints

### 1. Create Post
**Endpoint**: `POST /api/v1/posts`

**Description**: Create a new post. Fanout happens asynchronously in the background.

**Request Body**:
```json
{
  "userId": 1,
  "content": "Hello Twitter! This is my first post.",
  "mediaUrls": ["https://example.com/image.jpg"],
  "hashtags": ["firstpost", "hello"]
}
```

**Response** (201 Created):
```json
{
  "success": true,
  "message": "Post created successfully",
  "data": {
    "postId": "123e4567-e89b-12d3-a456-426614174000",
    "userId": 1,
    "username": "john_doe",
    "content": "Hello Twitter! This is my first post.",
    "mediaUrls": ["https://example.com/image.jpg"],
    "hashtags": ["firstpost", "hello"],
    "likeCount": 0,
    "retweetCount": 0,
    "replyCount": 0,
    "createdAt": "2025-10-12T10:30:00Z",
    "updatedAt": "2025-10-12T10:30:00Z"
  },
  "timestamp": "2025-10-12T10:30:00.123Z"
}
```

**Validation Rules**:
- `userId`: Required, not null
- `content`: Required, not blank, max 280 characters
- `mediaUrls`: Optional
- `hashtags`: Optional

---

### 2. Get Post by ID
**Endpoint**: `GET /api/v1/posts/{postId}`

**Description**: Retrieve a specific post by its UUID.

**Path Parameters**:
- `postId` (UUID): The post ID

**Response** (200 OK):
```json
{
  "success": true,
  "data": {
    "postId": "123e4567-e89b-12d3-a456-426614174000",
    "userId": 1,
    "username": "john_doe",
    "content": "Hello Twitter! This is my first post.",
    "likeCount": 42,
    "retweetCount": 10,
    "replyCount": 5,
    "createdAt": "2025-10-12T10:30:00Z"
  },
  "timestamp": "2025-10-12T10:31:00.123Z"
}
```

**Error Response** (404 Not Found):
```json
{
  "timestamp": "2025-10-12T10:31:00",
  "status": 404,
  "error": "Not Found",
  "message": "Post not found with id: 123e4567-e89b-12d3-a456-426614174000"
}
```

---

### 3. Delete Post
**Endpoint**: `DELETE /api/v1/posts/{postId}`

**Description**: Delete a post by its ID.

**Path Parameters**:
- `postId` (UUID): The post ID

**Response** (200 OK):
```json
{
  "success": true,
  "message": "Post deleted successfully",
  "timestamp": "2025-10-12T10:32:00.123Z"
}
```

---

### 4. Update Post Metrics
**Endpoint**: `PATCH /api/v1/posts/{postId}/metrics`

**Description**: Update like, retweet, and reply counts for a post.

**Path Parameters**:
- `postId` (UUID): The post ID

**Query Parameters**:
- `likeCount` (Long, optional, default: 0): New like count
- `retweetCount` (Long, optional, default: 0): New retweet count
- `replyCount` (Long, optional, default: 0): New reply count

**Example**: `PATCH /api/v1/posts/{postId}/metrics?likeCount=100&retweetCount=20`

**Response** (200 OK):
```json
{
  "success": true,
  "message": "Metrics updated successfully",
  "timestamp": "2025-10-12T10:33:00.123Z"
}
```

---

## Feed Endpoints

### 5. Get User Feed
**Endpoint**: `GET /api/v1/feed`

**Description**: Retrieve user's feed with pagination. Merges cached posts (fan-out on write) and celebrity posts (fan-out on read).

**Query Parameters**:
- `userId` (Long, required): The user ID requesting the feed
- `limit` (int, optional, default: 20, max: 100): Number of items per page
- `offset` (int, optional, default: 0): Offset for pagination

**Example**: `GET /api/v1/feed?userId=1&limit=20&offset=0`

**Response** (200 OK):
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "postId": "123e4567-e89b-12d3-a456-426614174000",
        "authorId": 2,
        "authorUsername": "celebrity_user",
        "content": "Celebrity post!",
        "mediaUrls": [],
        "hashtags": ["celebrity"],
        "likeCount": 1000,
        "retweetCount": 500,
        "replyCount": 200,
        "createdAt": "2025-10-12T10:00:00Z",
        "celebrityPost": true
      }
    ],
    "totalItems": 20,
    "limit": 20,
    "offset": 0,
    "hasMore": true
  },
  "timestamp": "2025-10-12T10:35:00.123Z"
}
```

---

### 6. Get User Feed by Path
**Endpoint**: `GET /api/v1/feed/user/{userId}`

**Description**: Retrieve user's feed with default pagination (first page, 20 items).

**Path Parameters**:
- `userId` (Long): The user ID

**Example**: `GET /api/v1/feed/user/1`

**Response**: Same as endpoint #5

---

## User Endpoints

### 7. Create User
**Endpoint**: `POST /api/v1/users`

**Description**: Create a new user account.

**Request Body**:
```json
{
  "username": "john_doe",
  "email": "john@example.com",
  "displayName": "John Doe",
  "bio": "Software engineer passionate about technology"
}
```

**Response** (201 Created):
```json
{
  "success": true,
  "message": "User created successfully",
  "data": {
    "id": 1,
    "username": "john_doe",
    "email": "john@example.com",
    "bio": "Software engineer passionate about technology",
    "location": null,
    "website": null,
    "followerCount": 0,
    "followingCount": 0,
    "isCelebrity": false,
    "createdAt": "2025-10-13T13:40:00Z",
    "lastLoginAt": null
  },
  "timestamp": "2025-10-13T13:40:00.123Z"
}
```

**Validation**:
- Username: Required, 3-50 characters
- Email: Required, valid email format
- Display name: Optional, max 100 characters
- Bio: Optional, max 500 characters

---

### 8. Get User by ID
**Endpoint**: `GET /api/v1/users/{userId}`

**Description**: Retrieve user profile information.

**Path Parameters**:
- `userId` (Long): The user ID

**Response** (200 OK):
```json
{
  "success": true,
  "data": {
    "id": 1,
    "username": "john_doe",
    "email": "john@example.com",
    "bio": "Software engineer",
    "location": null,
    "website": null,
    "followerCount": 1500,
    "followingCount": 200,
    "isCelebrity": false,
    "createdAt": "2025-01-01T00:00:00Z",
    "lastLoginAt": null
  },
  "timestamp": "2025-10-12T10:36:00.123Z"
}
```

---

### 9. Get User by Username
**Endpoint**: `GET /api/v1/users/username/{username}`

**Description**: Retrieve user by username.

**Path Parameters**:
- `username` (String): The username

**Example**: `GET /api/v1/users/username/john_doe`

**Response**: Same as endpoint #7

---

### 10. Follow User
**Endpoint**: `POST /api/v1/users/follow`

**Description**: Follow another user. Updates follower counts and checks celebrity threshold.

**Request Body**:
```json
{
  "followerUserId": 1,
  "followedUserId": 2
}
```

**Response** (201 Created):
```json
{
  "success": true,
  "message": "Successfully followed user",
  "timestamp": "2025-10-12T10:37:00.123Z"
}
```

**Validation**:
- Cannot follow yourself (returns 400 Bad Request)
- Both user IDs must exist (returns 404 Not Found)
- Cannot follow same user twice (returns 409 Conflict)

---

### 11. Unfollow User
**Endpoint**: `DELETE /api/v1/users/follow`

**Description**: Unfollow a user.

**Request Body**:
```json
{
  "followerUserId": 1,
  "followedUserId": 2
}
```

**Response** (200 OK):
```json
{
  "success": true,
  "message": "Successfully unfollowed user",
  "timestamp": "2025-10-12T10:38:00.123Z"
}
```

---

### 12. Get Followers
**Endpoint**: `GET /api/v1/users/{userId}/followers`

**Description**: Get list of follower IDs for a user.

**Path Parameters**:
- `userId` (Long): The user ID

**Response** (200 OK):
```json
{
  "success": true,
  "data": [2, 3, 5, 7, 11],
  "timestamp": "2025-10-12T10:39:00.123Z"
}
```

---

### 13. Get Following
**Endpoint**: `GET /api/v1/users/{userId}/following`

**Description**: Get list of user IDs that a user follows.

**Path Parameters**:
- `userId` (Long): The user ID

**Response**: Same format as endpoint #11

---

### 14. Get Celebrity Following
**Endpoint**: `GET /api/v1/users/{userId}/celebrities`

**Description**: Get list of celebrity user IDs that a user follows.

**Path Parameters**:
- `userId` (Long): The user ID

**Response**: Same format as endpoint #11

---

## Error Responses

### Standard Error Format
All error responses follow this structure:

```json
{
  "timestamp": "2025-10-12T10:40:00",
  "status": 400,
  "error": "Bad Request",
  "message": "User ID is required"
}
```

### Validation Error Format
```json
{
  "timestamp": "2025-10-12T10:40:00",
  "status": 400,
  "error": "Validation Failed",
  "message": "Post content cannot be empty, User ID is required",
  "details": {
    "content": "Post content cannot be empty",
    "userId": "User ID is required"
  }
}
```

### HTTP Status Codes
- **200 OK**: Successful GET/DELETE/PATCH request
- **201 Created**: Successful POST request (resource created)
- **400 Bad Request**: Validation error or invalid input
- **404 Not Found**: Resource not found
- **409 Conflict**: Duplicate resource (e.g., already following)
- **500 Internal Server Error**: Unexpected server error

---

## Testing with cURL

### Create a User
```bash
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser1",
    "email": "test1@example.com",
    "displayName": "Test User 1",
    "bio": "This is a test user"
  }'
```

### Create a Post
```bash
curl -X POST http://localhost:8080/api/v1/posts \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "content": "My first post!",
    "hashtags": ["test"]
  }'
```

### Get User Feed
```bash
curl "http://localhost:8080/api/v1/feed?userId=1&limit=20"
```

### Follow User
```bash
curl -X POST http://localhost:8080/api/v1/users/follow \
  -H "Content-Type: application/json" \
  -d '{
    "followerUserId": 1,
    "followedUserId": 2
  }'
```

### Get User Profile
```bash
curl http://localhost:8080/api/v1/users/1
```

---

## Performance Notes

- **Post Creation**: Returns in < 50ms (fanout happens asynchronously)
- **Feed Retrieval**: < 150ms (p95), merges Redis cache + Cassandra
- **Follow/Unfollow**: < 100ms (PostgreSQL transaction)
- **Async Fanout**: Processes 9,999 followers in ~500ms (background)

---

## Authentication

**Note**: Authentication is not yet implemented. All endpoints are publicly accessible.
Future implementation will use JWT tokens with Bearer authentication.
