# Solr Schema Changes for Multi-Valued Dense Vectors

## Objective

Document Solr schema requirements for semantic search in DSpace, with explicit separation between:

- Single-vector mode (`embeddings.solr.multi.vectors=false`): one vector per item
- MultiVectors mode (`embeddings.solr.multi.vectors=true`): nested child vectors per item

## Schema Changes Always Required

### 1. Dense vector field type

```xml
<fieldType name="knn_vector" class="solr.DenseVectorField" vectorDimension="1024" similarityFunction="dot_product" knnAlgorithm="hnsw" />
```

### 2. Single-vector field

```xml
<field name="vector" type="knn_vector" indexed="true" stored="false" />
```

## Additional Schema Changes Only for MultiVectors

Apply this section only when `embeddings.solr.multi.vectors=true`.

### 1. Nested document field type

```xml
<fieldType name="_nest_path_" class="solr.NestPathField" />
```

### 2. Nested-system fields

```xml
<field name="_root_" type="string" indexed="true" stored="false" docValues="false" />
<field name="_nest_path_" type="_nest_path_" indexed="true" stored="true" />
```

### 3. Multi-valued vector field

```xml
<field name="vector_multivalued" type="knn_vector" indexed="true" stored="false" multiValued="true" />
```

### 4. Core DSpace identity fields must not be required

```xml
<field name="search.resourceid" type="string" indexed="true" stored="true" required="false" omitNorms="true" />
<field name="search.resourcetype" type="string" indexed="true" stored="true" required="false" omitNorms="true" />
<field name="search.uniqueid" type="string" indexed="true" stored="true" required="false" omitNorms="true" docValues="true"/>
```

Reason: multi-valued vectors generate child documents that do not carry all top-level required fields.

### 5. `_root_` compatibility with unique key

```xml
<uniqueKey>search.uniqueid</uniqueKey>
```

Because `search.uniqueid` is `type="string"`, `_root_` must also be `type="string"`.

## Query Behavior (Current Implementation)

### Single-vector mode (`embeddings.solr.multi.vectors=false`)

- Search field: `embeddings.solr.vector.field` (default `vector`)
- Query parser options:
  - `knn`: `{!knn f=vector topK=N}[...]`
  - `vectorSimilarity`: `{!vectorSimilarity f=vector minReturn=X}[...]`

### Multi-vector mode (`embeddings.solr.multi.vectors=true`)

- Search field: `embeddings.solr.vector.fieldMultiValued` (default `vector_multivalued`)
- Parent/child query strategy:
  - Set `allParents=*:* -_nest_path_:*`
  - Build child query (KNN or vectorSimilarity)
  - Wrap with parent join:

```text
{!parent which=$allParents score=max v=$children.q}
```

This allows matching child vector chunks while returning parent item documents.

## Validation Checklist

1. Always: `schema.xml` contains `knn_vector` and `vector`.
2. If MultiVectors enabled: `schema.xml` contains `_nest_path_`, `_root_`, and `vector_multivalued`.
3. If MultiVectors enabled: `search.resourceid`, `search.resourcetype`, and `search.uniqueid` are not required.
4. Solr core is reloaded/recreated after schema changes.
5. Discovery index is rebuilt.

Command example:

```bash
[dspace]/bin/dspace index-discovery -b
```
