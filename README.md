# AEM Content Transformer

A Java application that transforms WordPress content into AEM Core Components-based pages using LangGraph4j for workflow orchestration and AI-powered content analysis via Ollama.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        LangGraph4j Workflow Orchestration                    │
├─────────────┬───────────────┬────────────────┬──────────────┬───────────────┤
│   SCRAPE    │    ANALYZE    │   TRANSFORM    │   GENERATE   │    OUTPUT     │
│    NODE     │     NODE      │     NODE       │     NODE     │     NODE      │
├─────────────┼───────────────┼────────────────┼──────────────┼───────────────┤
│ WordPress   │   Content     │   Component    │   AEM JSON   │   File        │
│ Scraper     │   Analyzer    │   Mapper       │   Generator  │   Writer      │
│   Agent     │    Agent      │    Agent       │    Agent     │               │
└─────────────┴───────────────┴────────────────┴──────────────┴───────────────┘
                                     │
                              ┌──────┴──────┐
                              │   Ollama    │
                              │  (LLM API)  │
                              └─────────────┘
```

## Prerequisites

- Java 17 or higher
- Maven 3.9+
- Ollama running locally (or accessible via network)
- A WordPress site with REST API enabled

## Quick Start

### 1. Install Ollama and pull a model

```bash
# Install Ollama (https://ollama.ai)
curl -fsSL https://ollama.ai/install.sh | sh

# Pull a model
ollama pull llama3.2
```

### 2. Build the application

```bash
mvn clean package
```

### 3. Run a transformation

```bash
# Transform a single post
java -jar target/aem-content-transformer-1.0.0-SNAPSHOT.jar \
    transform https://example.wordpress.com 123 post

# Transform a single page
java -jar target/aem-content-transformer-1.0.0-SNAPSHOT.jar \
    transform https://example.wordpress.com 45 page

# Transform by full URL
java -jar target/aem-content-transformer-1.0.0-SNAPSHOT.jar \
    transform-url https://example.wordpress.com/news/my-post/
```

## Configuration

The application can be configured via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |
| `OLLAMA_MODEL` | `llama3.2` | Model to use for AI analysis |
| `AEM_OUTPUT_PATH` | `./output` | Output directory for generated JSON |
| `AEM_SITE_PATH` | `/content/mysite` | AEM site path prefix |
| `WORDPRESS_URL` | - | Default WordPress site URL |
| `TRANSFORM_ALLOWED_HOSTS` | - | Comma-separated allowlist for the REST endpoint |
| `MIGRATION_BATCH_CONCURRENCY` | `4` | Parallelism for batch processing |
| `MIGRATION_MANIFEST_FILENAME` | `manifest.jsonl` | Manifest file name under `AEM_OUTPUT_PATH` |

## Production Runbook (100k+ pages)

This section covers the recommended configuration and execution steps for large migrations with assets, tags, and fragments.

### 1) Preflight

Create `.env` from `.env.example` and run:

```bash
./scripts/preflight.sh
```

### 2) Recommended settings

```bash
# AEM paths
export AEM_SITE_PATH=/content/mysite
export AEM_TEMPLATE_PATH=/conf/mysite/settings/wcm/templates/content-page
export AEM_TAGS_ROOT=/content/cq:tags/mysite
export AEM_FRAGMENT_MODEL=/conf/mysite/settings/dam/cfm/models/article
export AEM_FRAGMENT_DAM_PATH=/content/dam/mysite/fragments
export AEM_XF_PATH=/content/experience-fragments/mysite
export AEM_XF_TEMPLATE=/conf/mysite/settings/wcm/templates/experience-fragment

# Tag warmup (optional for large tag sets)
export WP_TAG_WARMUP_ENABLED=true
export WP_TAG_WARMUP_PER_PAGE=100
export WP_TAG_WARMUP_MAX_PAGES=10

# Rate limiting
export WP_RPS=5
export AEM_RPS=2

# Asset ingestion
export AEM_ASSET_DOWNLOAD=true
export AEM_ASSET_MAX_BYTES=52428800
export AEM_ASSET_RETRY_MAX=3
export AEM_ASSET_RETRY_DELAY_MS=1000
export AEM_ASSET_DOWNLOAD_DELAY_MS=50
export AEM_ASSET_UPLOAD_DELAY_MS=100

# Asset API upload (optional)
export AEM_ASSET_API_ENABLED=true
export AEM_ASSET_API_UPLOAD_URL=https://author.example.com/api/assets{damPath}
export AEM_ASSET_API_AUTH_TYPE=basic
export AEM_ASSET_API_AUTH_USER=...
export AEM_ASSET_API_AUTH_PASSWORD=...
export AEM_ASSET_API_VERIFY_ENABLED=true
export AEM_ASSET_API_VERIFY_URL=https://author.example.com/content/dam{damPath}.json

# AEM validation (optional but recommended)
export AEM_VALIDATION_ENABLED=true
export AEM_VALIDATION_BASE_URL=https://author.example.com
export AEM_VALIDATION_AUTH_TYPE=basic
export AEM_VALIDATION_AUTH_USER=...
export AEM_VALIDATION_AUTH_PASSWORD=...
export AEM_VALIDATION_EXTRA_PATHS=/conf/mysite/settings/wcm/templates/experience-fragment,/content/dam/mysite

# Fragments
export AEM_FRAGMENTS_ENABLED=true
export AEM_EMBED_XF_ON_PAGE=false

# Batch controls
export MIGRATION_CHECKPOINT_ENABLED=true
export MIGRATION_CHECKPOINT_INTERVAL=100
export MIGRATION_RETRY_FAILED=true
export MIGRATION_SKIP_OUTPUT_CHECK=false
```

### 3) Run batch migration

```bash
java -jar target/aem-content-transformer-1.0.0-SNAPSHOT.jar \
  batch https://example.wordpress.com posts 100 1000
```

### 4) Outputs & audit artifacts

- `output/manifest.jsonl` – per-item status
- `output/dlq.jsonl` – failures for retry
- `output/report.json` – summary and throughput
- `output/<package>/jcr_root/...` – package contents

### 5) Retry failures

Re-run with `MIGRATION_RETRY_FAILED=true`. Failed items remain in `dlq.jsonl` for review.

## Workflow Stages

### 1. Scrape
Fetches content from WordPress REST API (`/wp-json/wp/v2/posts` or `/wp-json/wp/v2/pages`).

### 2. Analyze
Parses HTML content using Jsoup and uses LLM for semantic analysis:
- Identifies content blocks (headings, paragraphs, images, etc.)
- Extracts metadata and structure
- Generates content summary

### 3. Transform
Maps content blocks to AEM Core Components:
- Headings → Title component
- Paragraphs → Text component
- Images → Image component
- Lists → List/Text component
- Galleries → Carousel component

### 4. Generate
Creates AEM-compatible JSON structure with proper JCR properties:
- `jcr:primaryType`
- `sling:resourceType`
- Component-specific properties

### 5. Output
Writes JSON files to the configured output directory.

## Output Format

Generated JSON follows AEM's content structure:

```json
{
  "jcr:primaryType": "cq:Page",
  "jcr:content": {
    "jcr:primaryType": "cq:PageContent",
    "sling:resourceType": "core/wcm/components/page/v3/page",
    "jcr:title": "My Page Title",
    "root": {
      "jcr:primaryType": "nt:unstructured",
      "sling:resourceType": "core/wcm/components/container/v1/container",
      "title_0": {
        "jcr:primaryType": "nt:unstructured",
        "sling:resourceType": "core/wcm/components/title/v3/title",
        "jcr:title": "Welcome",
        "type": "h1"
      },
      "text_1": {
        "jcr:primaryType": "nt:unstructured",
        "sling:resourceType": "core/wcm/components/text/v2/text",
        "text": "<p>Content here...</p>",
        "textIsRich": true
      }
    }
  }
}
```

## Component Mapping

| WordPress Element | AEM Core Component |
|-------------------|-------------------|
| `<h1>-<h6>` | `core/wcm/components/title` |
| `<p>`, `<div>` text | `core/wcm/components/text` |
| `<img>` | `core/wcm/components/image` |
| `<ul>`, `<ol>` | `core/wcm/components/list` |
| `<figure>` gallery | `core/wcm/components/carousel` |
| `<hr>` | `core/wcm/components/separator` |
| `<iframe>`, embeds | `core/wcm/components/embed` |

## Project Structure

```
src/main/java/com/example/aemtransformer/
├── AemTransformerApplication.java    # Main application & CLI
├── agent/                            # Agent implementations
│   ├── WordPressScraperAgent.java
│   ├── ContentAnalyzerAgent.java
│   ├── ComponentMapperAgent.java
│   └── AemGeneratorAgent.java
├── components/                       # AEM component models
│   ├── AemComponent.java
│   ├── TitleComponent.java
│   ├── TextComponent.java
│   └── ...
├── config/                           # Configuration classes
├── model/                            # Domain models
│   ├── WordPressContent.java
│   ├── ContentAnalysis.java
│   ├── ComponentMapping.java
│   └── AemPage.java
├── service/                          # Services
│   ├── WordPressApiService.java
│   ├── HtmlParserService.java
│   └── AemOutputService.java
└── workflow/                         # LangGraph4j workflow
    ├── TransformationState.java
    ├── TransformationWorkflow.java
    └── WorkflowNodes.java
```

## Development

### Running tests

```bash
mvn test
```

### Building without tests

```bash
mvn package -DskipTests
```

## License

MIT License
