# RAG v1 Hybrid Retrieval

## 1. Scope

CampusCircle answers questions only with posts that the current user is allowed to view. The retrieval layer never accepts a school scope from the browser as a trusted value.

The request path is:

```text
access token
  -> current user and campus
  -> allowed nearby school IDs
  -> HybridPostRetriever
       -> Elasticsearch keyword retrieval
       -> Elasticsearch vector retrieval
       -> RRF rank fusion
  -> reload normal posts from MySQL
  -> prompt assembly, model response, and citation validation
```

MySQL remains the source of truth. Elasticsearch only stores a search projection and is never used to decide whether a deleted, hidden, or out-of-scope post may be returned.

## 2. Indexing lifecycle

```text
post create / update / delete / hide / restore
  -> transaction commits
  -> PostSearchIndexEvent(postId)
  -> direct handler, Outbox, or RocketMQ consumer
  -> reload current post state from MySQL
  -> build searchText
  -> generate embedding
  -> upsert or delete the Elasticsearch document
```

The event contains only the post ID. The consumer re-reads MySQL rather than trusting an old event payload, so repeated or delayed delivery converges to the latest state.

`PostSearchIndexService` treats indexing as an asynchronous enhancement: an Elasticsearch or embedding failure is logged and does not roll back a successful post write. The assistant then falls back to the existing SQL retrieval path.

## 3. Search document

The `campuscircle-posts` index uses one document per post.

| Field | Purpose |
| --- | --- |
| `postId` | Elasticsearch document ID and citation identity |
| `schoolId` | Mandatory permission filter for both retrieval paths |
| `categoryId`, `categoryName` | Category context and keyword relevance |
| `title`, `content` | User-facing searchable content |
| `schoolName`, `campusName`, `city` | Location context for both search and generation |
| `searchText` | A deterministic text projection used to generate the embedding |
| `updatedAt` | Index inspection and freshness diagnostics |
| `embedding` | `dense_vector` with cosine similarity |

`searchText` is built from title, category, school, campus, city, and body. Keeping this projection deterministic is important: a post update produces a new vector from a clearly defined representation.

## 4. Hybrid ranking

The keyword path uses Elasticsearch `multi_match` across title, category, `searchText`, and content. The vector path uses native `knn` over `embedding`. Both apply the same `schoolId` hard filter.

The two result lists are fused with Reciprocal Rank Fusion:

```text
score(post) += 1 / (k + rank)
```

where `k` defaults to `60` and rank starts from `1`. RRF combines rank positions rather than raw BM25 and vector scores, which avoids comparing incomparable score scales. After fusion, CampusCircle reloads the chosen post IDs from MySQL in fused order and removes any no-longer-normal post.

## 5. Local setup

Start Elasticsearch without starting the complete application stack:

```powershell
docker compose --profile search up -d elasticsearch
curl http://localhost:9200
```

For pipeline verification, add the following to the local `.env`:

```dotenv
CAMPUSCIRCLE_SEARCH_ENABLED=true
CAMPUSCIRCLE_SEARCH_EMBEDDING_PROVIDER=mock
```

The mock provider produces deterministic vectors and proves event delivery, index mapping, kNN queries, RRF, and fallback behavior. It is not a semantic embedding model and must not be used to evaluate answer quality.

For real semantic retrieval, configure an OpenAI-compatible embedding provider:

```dotenv
CAMPUSCIRCLE_SEARCH_ENABLED=true
CAMPUSCIRCLE_SEARCH_EMBEDDING_PROVIDER=openai-compatible
CAMPUSCIRCLE_SEARCH_EMBEDDING_BASE_URL=https://provider.example/v1
CAMPUSCIRCLE_SEARCH_EMBEDDING_API_KEY=local-only-secret
CAMPUSCIRCLE_SEARCH_EMBEDDING_MODEL=embedding-model-name
CAMPUSCIRCLE_SEARCH_EMBEDDING_DIMENSIONS=1024
```

The selected model's output dimension must exactly equal `CAMPUSCIRCLE_SEARCH_EMBEDDING_DIMENSIONS`. Never commit a real key.

## 6. Rebuild and operations

After enabling search for an existing database, rebuild the post projection once:

```text
POST /api/admin/search/posts/reindex
Authorization: Bearer <administrator access token>
```

The endpoint returns the number of posts processed. It is safe to run again because every document is reconciled from the current MySQL row.

Useful local checks:

```powershell
curl http://localhost:9200/campuscircle-posts/_count
curl http://localhost:9200/campuscircle-posts/_mapping
```

## 7. Failure behavior

- Elasticsearch unavailable: `HybridPostRetriever` falls back to SQL keyword retrieval.
- Embedding unavailable or invalid: the same SQL fallback applies.
- A post is deleted or hidden before an index event is consumed: reconciliation deletes its document.
- A retrieved Elasticsearch document is stale: MySQL reloading removes it from the final context.
- The generation model fails: CampusCircle returns a controlled failure result rather than inventing an answer.

## 8. Evaluation checklist

Before claiming semantic quality, create a small query set with expected post IDs and record:

1. Whether an expected result enters TopK.
2. Keyword-only, vector-only, and RRF ranks.
3. Whether the post belongs to the authorized school scope.
4. Whether the final answer cites only retrieved posts.
5. Whether SQL fallback still produces a usable result when search is stopped.
