# Inventario de Arquivos da Integracao dARK

Este documento relaciona os arquivos criados ou alterados para a integracao dARK
no DSpace. Ele cobre a implementacao funcional, configuracao, banco de dados,
testes e documentacao. Arquivos da instalacao gerada, como `compiled/`, nao fazem
parte do codigo-fonte versionado.

## Modelo, persistencia e banco

| Arquivo | Acao | Alteracao |
| --- | --- | --- |
| `dspace-api/src/main/java/org/dspace/identifier/DARK.java` | Adicionado | Entidade Hibernate que representa a associacao local entre um dARK e um objeto DSpace. Armazena ARK, Item, estado, alvo e CIDs retornados pela API. |
| `dspace-api/src/main/java/org/dspace/identifier/service/DarkService.java` | Adicionado | Contrato de servico para criar, localizar e atualizar identificadores dARK. |
| `dspace-api/src/main/java/org/dspace/identifier/DarkServiceImpl.java` | Adicionado | Implementacao do servico; normaliza os formatos `ark:/...` e `ark:...` para a forma canonica do DSpace. |
| `dspace-api/src/main/java/org/dspace/identifier/dao/DarkDAO.java` | Adicionado | Contrato DAO para consultas por ARK e por objeto DSpace. |
| `dspace-api/src/main/java/org/dspace/identifier/dao/impl/DarkDAOImpl.java` | Adicionado | Implementacao Hibernate do DAO dARK. |
| `dspace-api/src/main/resources/org/dspace/storage/rdbms/sqlmigration/postgres/V11.0_2026.09.03__dark_identifier.sql` | Adicionado | Cria sequencia, tabela `dark`, restricoes de unicidade e indices para PostgreSQL. |
| `dspace-api/src/main/resources/org/dspace/storage/rdbms/sqlmigration/h2/V11.0_2026.09.03__dark_identifier.sql` | Adicionado | Equivalente da migracao para H2, usado em testes e desenvolvimento. |
| `dspace/config/hibernate.cfg.xml` | Alterado | Registra a entidade Hibernate `org.dspace.identifier.DARK`. |
| `dspace/config/spring/api/core-dao-services.xml` | Alterado | Registra `DarkDAOImpl` como DAO gerenciado pelo Spring. |
| `dspace/config/spring/api/core-services.xml` | Alterado | Registra `DarkServiceImpl`, `DarkClientImpl` e `DarkMetadataBuilder` como servicos Spring. |

## Provider e comunicacao com a plataforma dARK

| Arquivo | Acao | Alteracao |
| --- | --- | --- |
| `dspace-api/src/main/java/org/dspace/identifier/DarkIdentifierProvider.java` | Adicionado e alterado | Provider do `IdentifierService`: reserva, registra, consulta, atualiza e remove dARKs; persiste metadados no Item, atualiza URI publica opcional e expoe o preflight de metadados para o CLI. As alteracoes posteriores incluem a normalizacao da resposta do minter. |
| `dspace-api/src/main/java/org/dspace/identifier/dark/DarkClient.java` | Adicionado | Interface do cliente HTTP para reservar, consultar, atualizar metadados e tombar ARKs. |
| `dspace-api/src/main/java/org/dspace/identifier/dark/DarkClientImpl.java` | Adicionado | Cliente Apache HTTP para a API do minter; aplica autenticacao opcional, cabecalho de autoridade e converte `ark:/...` para `ark:...` somente nas URLs do minter. |
| `dspace-api/src/main/java/org/dspace/identifier/dark/DarkArkResponse.java` | Adicionado | DTO da resposta de um ARK retornada pela API. |
| `dspace-api/src/main/java/org/dspace/identifier/dark/DarkBatchResponse.java` | Adicionado | DTO da resposta de reserva em lote. |
| `dspace-api/src/main/java/org/dspace/identifier/dark/DarkBatchError.java` | Adicionado | DTO para erros por Item retornados na reserva em lote. |
| `dspace-api/src/main/java/org/dspace/identifier/dark/DarkIdentifierException.java` | Adicionado | Excecao especifica e codigos de erro para operacoes dARK. |
| `dspace-api/src/main/java/org/dspace/identifier/dark/DarkMetadataRequest.java` | Adicionado | DTO do payload de metadados enviado ao minter. |
| `dspace-api/src/main/java/org/dspace/identifier/dark/DarkMetadataBuilder.java` | Adicionado e alterado | Monta Level 1 e OAI-DC Level 2. A alteracao posterior troca `getProperty()` por `getArrayProperty()` para que todos os campos separados por virgula sejam lidos e relatados no preflight. |
| `dspace/config/spring/api/identifier-service.xml` | Alterado | Registra `DarkIdentifierProvider` no `IdentifierService`, com filtro sempre verdadeiro e configuracao via `dark.cfg`. |

## CLI administrativo

| Arquivo | Acao | Alteracao |
| --- | --- | --- |
| `dspace-api/src/main/java/org/dspace/app/dark/DarkMint.java` | Adicionado | Implementa `dark-mint --uuid` e `dark-mint --all`; verifica provider habilitado, evita duplicidade, executa preflight antes de reservar ARK e contabiliza resultados de lote. |
| `dspace-api/src/main/java/org/dspace/app/dark/DarkMintScriptConfiguration.java` | Adicionado | Declara as opcoes `--uuid`, `--all` e `--help` do script. |
| `dspace/config/spring/api/scripts.xml` | Alterado | Registra o comando `dark-mint` para o launcher `bin/dspace`. |

## Configuracao e metadados

| Arquivo | Acao | Alteracao |
| --- | --- | --- |
| `dspace/config/modules/dark.cfg` | Adicionado e alterado | Configura habilitacao, URLs, autoridade, NAAN, autenticacao, URI primaria, metadado do dARK e mapeamento de campos. Os fallbacks atuais de autor e data sao `dc.creator, dc.contributor.author, dc.contributor` e `dc.date, dc.date.issued`. |
| `dspace/config/dspace.cfg` | Alterado | Inclui o modulo `modules/dark.cfg` na configuracao principal. |
| `dspace/config/registries/dublin-core-types.xml` | Alterado | Registra o campo Dublin Core `dc.identifier.dark`, usado para expor o identificador no Item. |

## Testes

| Arquivo | Acao | Alteracao |
| --- | --- | --- |
| `dspace-api/src/test/java/org/dspace/identifier/DarkIdentifierProviderTest.java` | Adicionado | Testa operacoes e regras do provider, incluindo registro e tratamento local de dARK. |
| `dspace-api/src/test/java/org/dspace/identifier/DarkServiceImplTest.java` | Adicionado | Testa normalizacao e comportamento do servico dARK. |
| `dspace-api/src/test/java/org/dspace/identifier/dark/DarkClientImplTest.java` | Adicionado | Testa a conversao do formato de ARK usada pelo minter. |
| `dspace-api/src/test/java/org/dspace/identifier/dark/DarkMetadataBuilderTest.java` | Adicionado e alterado | Testa a montagem do payload, combinacao de metadados e o preflight com fallbacks de autor/data. |

## Documentacao

| Arquivo | Acao | Alteracao |
| --- | --- | --- |
| `docs/dark-dspace-integration.md` | Adicionado | Guia de arquitetura, configuracao, fluxo automatico, CLI, persistencia, implantacao e operacao segura. |
| `docs/dark-modified-files.md` | Adicionado | Este inventario completo de arquivos. |

## Artefato nao funcional

| Arquivo | Acao observada | Recomendacao |
| --- | --- | --- |
| `dspace-api/javac.20260903_132954.args` | Adicionado | E um arquivo de argumentos temporarios do compilador Java e nao faz parte da funcionalidade dARK. Remova-o do indice Git antes do commit, salvo se houver uma razao externa para mantê-lo. |

## Arquivos fora deste inventario

O projeto dARK em si nao foi modificado. Alteracoes em diretorios de instalacao
ou build, por exemplo `compiled/`, sao artefatos locais de implantacao e nao
substituem os arquivos-fonte listados acima.
