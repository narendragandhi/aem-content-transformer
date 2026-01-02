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
