# Solr Schema Changes for Multi-Valued Dense Vectors

## Objective

Enable support for:

```xml
<field name="vector_multivalued"
       type="knn_vector"
       indexed="true"
       stored="false"
       multiValued="true" />
```

using Solr `DenseVectorField` with HNSW indexing.

---

# 0. Full Schema Changes (Vector Indexing)

For clarity, these are the complete Solr schema changes needed for vector indexing support.

## Field types

```xml
<fieldType name="knn_vector" class="solr.DenseVectorField" vectorDimension="1024" similarityFunction="dot_product" knnAlgorithm="hnsw" />
<fieldType name="_nest_path_" class="solr.NestPathField" />
```

## Required fields

```xml
<field name="_root_" type="string" indexed="true" stored="false" docValues="false" />
<field name="_nest_path_" type="_nest_path_" indexed="true" stored="true" />

<field name="vector" type="knn_vector" indexed="true" stored="false" />
<field name="vector_multivalued" type="knn_vector" indexed="true" stored="false" multiValued="true" />
```

## Core DSpace fields must not be required

```xml
<field name="search.resourceid" type="string" indexed="true" stored="true" required="false" omitNorms="true" />
<field name="search.resourcetype" type="string" indexed="true" stored="true" required="false" omitNorms="true" />
<field name="search.uniqueid" type="string" indexed="true" stored="true" required="false" omitNorms="true" docValues="true"/>
```

## Unique key compatibility

```xml
<uniqueKey>search.uniqueid</uniqueKey>
```

Because `search.uniqueid` uses `type="string"`, `_root_` must also use `type="string"`.

---

# 1. Add `_root_` Field

Solr requires the `_root_` field when indexing nested/child documents generated internally by multi-valued vector fields.

## Added field

```xml
<field name="_root_"
       type="string"
       indexed="true"
       stored="false" />
```

## Important

The `_root_` field must use the same field type as the schema `uniqueKey`.

Current unique key:

```xml
<uniqueKey>search.uniqueid</uniqueKey>
```

Field type:

```xml
type="string"
```

Therefore `_root_` must also use:

```xml
type="string"
```

---

# 2. Keep Nested Document Support

The schema already correctly includes nested document support:

```xml
<fieldType name="_nest_path_" class="solr.NestPathField" />

<field name="_nest_path_"
       type="_nest_path_"
       indexed="true"
       stored="true" />
```

No additional changes were required here.

---

# 3. Remove `required="true"` from Core Fields

Multi-valued vector fields internally create child documents such as:

```text
Item-UUID/vector_multivalued#0
Item-UUID/vector_multivalued#1
```

These child documents do not contain all required DSpace metadata fields.

Because of this, indexing fails when fields are marked as required.

## Updated fields

### Before

```xml
<field name="search.resourceid"
       required="true" />

<field name="search.resourcetype"
       required="true" />

<field name="search.uniqueid"
       required="true" />
```

### After

```xml
<field name="search.resourceid"
       type="string"
       indexed="true"
       stored="true"
       omitNorms="true" />

<field name="search.resourcetype"
       type="string"
       indexed="true"
       stored="true"
       omitNorms="true" />

<field name="search.uniqueid"
       type="string"
       indexed="true"
       stored="true"
       omitNorms="true"
       docValues="true"/>
```

The `required="true"` attribute was removed from these fields.

---

# 4. Result

After these changes:

- Multi-valued vector indexing works correctly
- HNSW vector search remains enabled
- Solr internal child documents can be indexed successfully
- DSpace Discovery indexing completes without schema validation errors

---

# 5. Recommended Next Steps

After updating the schema:

1. Reload or restart Solr
2. Recreate the core if necessary
3. Reindex Discovery

Example:

```bash
[dspace]/bin/dspace index-discovery -b
```

---

# 6. Architectural Recommendation

Although `multiValued="true"` works, the recommended architecture for RAG/document chunking is to use explicit child documents instead of a multi-valued vector field.

Benefits include:

- Better ANN search performance
- Chunk-level scoring
- Metadata per chunk
- Easier retrieval of chunk text
- Better scalability

Example structure:

```json
{
  "search.uniqueid": "item-1",
  "title": "Document",
  "_childDocuments_": [
    {
      "id": "chunk-1",
      "vector": [...]
    },
    {
      "id": "chunk-2",
      "vector": [...]
    }
  ]
}
```
