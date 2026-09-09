# Busca Semântica e Híbrida (beta)

## Visão geral

Esta funcionalidade adiciona dois modos beta à busca Discovery do DSpace, além da busca léxica tradicional:

- **Busca semântica:** compara o significado da consulta e dos metadados dos itens por meio de embeddings.
- **Busca híbrida:** combina o ranking da busca léxica com o ranking vetorial usando **RRF** (*Reciprocal Rank Fusion*).

A implementação usa recursos de busca vetorial do **Solr 10.1 beta**, incluindo vetores densos, múltiplos vetores por item e a combinação de rankings por RRF.

## Indexação com embeddings

Durante a indexação, `SolrVectorIndexPlugin` gera embeddings por uma API compatível com OpenAI. A API, o modelo, as chaves e a dimensão esperada são configurados em `dspace/config/modules/embeddings.cfg`.

Há dois modos de armazenamento:

1. **Vetor único:** gera um embedding do título e o armazena no campo `vector`.
2. **Multivetores:** armazena vetores no campo `vector_multivalued` para o título e para os metadados configurados, como resumo (`dc.description.abstract`) e assunto (`dc.subject`). A consulta encontra o melhor vetor-filho e devolve o item-pai.

### Chunking de textos grandes

Resumos e outros campos longos são normalizados e divididos em segmentos antes da vetorização. O tamanho do segmento, a sobreposição e a quantidade máxima de segmentos são configuráveis. Cada segmento recebe o título como contexto antes de gerar o embedding.

Esse processo evita que um resumo extenso seja representado por apenas um vetor e melhora a recuperação quando o trecho relevante está em uma parte específica do texto.

## Fluxo de busca

O REST aceita `searchType=lexical|semantic|hybrid` nos endpoints Discovery.

1. A consulta é normalizada e enviada ao serviço de embeddings.
2. **Semântica:** `SolrSemanticSearchPlugin` substitui a consulta textual por uma consulta vetorial `knn` ou `vectorSimilarity` no Solr.
3. **Híbrida:** `SolrHybridSearchPlugin` envia a consulta léxica e a vetorial ao handler `/combined` do Solr 10.1 beta. O `CombinedQuerySearchHandler` aplica RRF e retorna uma lista única ordenada.
4. O campo `score` retornado pelo Solr é incluído na resposta REST.

Em caso de indisponibilidade do serviço de embeddings, a indexação e a busca mantêm o comportamento léxico, registrando o erro.

## Ativação

Os modos são independentes e ficam desativados por padrão:

- `semantic.search.enabled=false`
- `hybrid.search.enabled=false`

Para ativá-los, é necessário configurar um serviço de embeddings compatível, garantir que a dimensão do modelo corresponda ao `DenseVectorField` do Solr, aplicar o schema/configuração do Solr e reindexar o Discovery.

Os principais parâmetros disponíveis incluem `embeddings.solr.multi.vectors`, `embeddings.search.query.parser`, `embeddings.search.topK`, `embeddings.hybrid.topK` e `embeddings.hybrid.rrf.k`.
