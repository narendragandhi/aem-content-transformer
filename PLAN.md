# AEM Content Transformer - Implementation Plan

## Overview

A Java application that combines **LangGraph4j** for workflow orchestration with **Embabel** for intelligent agent logic to transform WordPress content into AEM Core Components-based pages using local LLM inference via **Ollama**.

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
│ (Embabel)   │  (Embabel)    │  (Embabel)     │  (Embabel)   │               │
└─────────────┴───────────────┴────────────────┴──────────────┴───────────────┘
                                     │
                              ┌──────┴──────┐
                              │   Ollama    │
                              │  (LLM API)  │
                              └─────────────┘
```

## Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Build Tool | Maven | 3.9+ |
| Language | Java | 17+ |
| Framework | Spring Boot | 3.3.x |
| Workflow | LangGraph4j | 1.7.9 |
| Agent Framework | Embabel | 0.2.x |
| LLM Integration | Spring AI + Ollama | 1.0.x |
| HTTP Client | OkHttp/RestClient | 4.x |
| JSON Processing | Jackson | 2.17.x |

## Project Structure

```
aem-content-transformer/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/example/aemtransformer/
│   │   │   ├── AemTransformerApplication.java
│   │   │   ├── config/
│   │   │   │   ├── OllamaConfig.java
│   │   │   │   ├── LangGraphConfig.java
│   │   │   │   └── EmbabelConfig.java
│   │   │   ├── model/
│   │   │   │   ├── WordPressContent.java
│   │   │   │   ├── ContentAnalysis.java
│   │   │   │   ├── ComponentMapping.java
│   │   │   │   └── AemPage.java
│   │   │   ├── agent/
│   │   │   │   ├── WordPressScraperAgent.java
│   │   │   │   ├── ContentAnalyzerAgent.java
│   │   │   │   ├── ComponentMapperAgent.java
│   │   │   │   └── AemGeneratorAgent.java
│   │   │   ├── workflow/
│   │   │   │   ├── TransformationState.java
│   │   │   │   ├── TransformationWorkflow.java
│   │   │   │   └── WorkflowNodes.java
│   │   │   ├── components/
│   │   │   │   ├── AemComponent.java
│   │   │   │   ├── TextComponent.java
│   │   │   │   ├── ImageComponent.java
│   │   │   │   ├── TitleComponent.java
│   │   │   │   ├── TeaserComponent.java
│   │   │   │   └── ContainerComponent.java
│   │   │   └── service/
│   │   │       ├── WordPressApiService.java
│   │   │       └── AemOutputService.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── prompts/
│   │           ├── analyze-content.txt
│   │           ├── map-components.txt
│   │           └── generate-aem.txt
│   └── test/
│       └── java/com/example/aemtransformer/
│           ├── agent/
│           └── workflow/
└── README.md
```

## Implementation Steps

### Phase 1: Project Setup

1. **Create Maven project with dependencies**
   - Spring Boot 3.3.x parent
   - LangGraph4j core and BOM
   - Embabel agent starter
   - Spring AI Ollama starter
   - Jackson for JSON
   - Lombok for boilerplate reduction
   - JUnit 5 + Mockito for testing

2. **Configure application properties**
   - Ollama base URL (default: http://localhost:11434)
   - Model selection (e.g., llama3.2, mistral, codellama)
   - WordPress source URL configuration
   - AEM output directory configuration

### Phase 2: Domain Models

3. **WordPress content models**
   ```java
   public record WordPressContent(
       Long id,
       String title,
       String content,      // HTML content
       String excerpt,
       String slug,
       String status,
       String type,         // post, page
       String featuredMedia,
       List<Long> categories,
       List<Long> tags,
       Map<String, Object> meta
   ) {}
   ```

4. **AEM component models**
   - Base `AemComponent` interface with JSON serialization
   - Core components: Title, Text, Image, Teaser, Container, List
   - Page wrapper with component tree structure

5. **Workflow state model**
   ```java
   public class TransformationState extends AgentState {
       // Input
       WordPressContent sourceContent;
       // Analysis phase
       ContentAnalysis analysis;
       // Mapping phase
       List<ComponentMapping> mappings;
       // Generation phase
       AemPage generatedPage;
       // Status
       List<String> errors;
       String currentPhase;
   }
   ```

### Phase 3: Embabel Agents

6. **WordPress Scraper Agent**
   ```java
   @Agent
   public class WordPressScraperAgent {
       @Action
       public WordPressContent scrapeContent(String url) {
           // Call WordPress REST API
           // Parse JSON response
           // Return structured content
       }
   }
   ```

7. **Content Analyzer Agent**
   - Uses LLM to analyze HTML structure
   - Identifies content blocks (headings, paragraphs, images, lists)
   - Extracts semantic meaning and relationships
   - Output: `ContentAnalysis` with block annotations

8. **Component Mapper Agent**
   - Maps analyzed blocks to AEM Core Components
   - Uses LLM for intelligent mapping decisions
   - Handles complex cases (galleries, embeds, custom blocks)
   - Output: `List<ComponentMapping>` with component types and properties

9. **AEM Generator Agent**
   - Generates AEM-compatible JSON structure
   - Creates proper node hierarchy
   - Handles component nesting and containers
   - Output: `AemPage` ready for export

### Phase 4: LangGraph4j Workflow

10. **Define StateGraph**
    ```java
    var workflow = new StateGraph<>(TransformationState.SCHEMA, TransformationState::new)
        .addNode("scrape", scraperNode)
        .addNode("analyze", analyzerNode)
        .addNode("transform", transformerNode)
        .addNode("generate", generatorNode)
        .addNode("output", outputNode)
        .addEdge(START, "scrape")
        .addEdge("scrape", "analyze")
        .addEdge("analyze", "transform")
        .addEdge("transform", "generate")
        .addConditionalEdges("generate", this::checkErrors,
            Map.of("success", "output", "retry", "transform", "fail", END))
        .addEdge("output", END);
    ```

11. **Implement workflow nodes**
    - Each node invokes corresponding Embabel agent
    - State updates propagate through the graph
    - Error handling with conditional retry logic

### Phase 5: AEM Core Components Output

12. **Component JSON structure**
    ```json
    {
      "jcr:primaryType": "cq:Page",
      "jcr:content": {
        "jcr:primaryType": "cq:PageContent",
        "sling:resourceType": "core/wcm/components/page/v3/page",
        "jcr:title": "Page Title",
        "root": {
          "jcr:primaryType": "nt:unstructured",
          "sling:resourceType": "core/wcm/components/container/v1/container",
          "text": {
            "jcr:primaryType": "nt:unstructured",
            "sling:resourceType": "core/wcm/components/text/v2/text",
            "text": "<p>Content here</p>"
          }
        }
      }
    }
    ```

13. **Output service**
    - Write JSON files to specified directory
    - Optional: Direct AEM package creation (.zip)
    - Validation of generated structure

### Phase 6: Integration & CLI

14. **Command-line interface**
    - Accept WordPress URL as input
    - Support batch processing (multiple pages)
    - Progress reporting
    - Dry-run mode for preview

15. **Spring Shell integration**
    - Interactive commands for testing
    - Agent inspection and debugging

## Key Design Decisions

### Why Both LangGraph4j and Embabel?

- **LangGraph4j**: Provides explicit, visual workflow orchestration with checkpointing and replay capabilities. Ideal for the multi-step transformation pipeline.
- **Embabel**: Provides goal-oriented, type-safe agent logic with automatic planning. Each agent can internally replan if initial approaches fail.

### Component Mapping Strategy

| WordPress Element | AEM Core Component |
|-------------------|-------------------|
| `<h1>-<h6>` | `core/wcm/components/title` |
| `<p>`, `<div>` text | `core/wcm/components/text` |
| `<img>` | `core/wcm/components/image` |
| `<ul>`, `<ol>` | `core/wcm/components/list` |
| `<a>` with preview | `core/wcm/components/teaser` |
| `<figure>` gallery | `core/wcm/components/carousel` |
| Gutenberg blocks | Mapped by block type |

### Error Handling

- Retry logic for transient failures
- Graceful degradation (skip problematic blocks)
- Detailed error logging for debugging
- Human-in-the-loop option for ambiguous mappings

## Configuration

```yaml
# application.yml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: llama3.2
          temperature: 0.3

wordpress:
  source-url: https://example.wordpress.com
  api-path: /wp-json/wp/v2

aem:
  output-path: ./output
  package-name: content-migration
  site-path: /content/mysite
```

## Testing Strategy

1. **Unit tests** for each agent using mock LLM responses
2. **Integration tests** with embedded Ollama (testcontainers)
3. **Sample WordPress content** fixtures
4. **Golden file tests** for AEM output validation

## Next Steps After Approval

1. Initialize Maven project structure
2. Add all dependencies to pom.xml
3. Create domain models
4. Implement WordPress scraping service
5. Build Embabel agents one by one
6. Wire up LangGraph4j workflow
7. Add CLI interface
8. Write tests

## Sources

- [Embabel Agent Framework](https://github.com/embabel/embabel-agent)
- [LangGraph4j](https://github.com/langgraph4j/langgraph4j)
- [Spring AI Ollama Integration](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)
- [WordPress REST API](https://developer.wordpress.org/rest-api/)
- [AEM Core Components](https://experienceleague.adobe.com/en/docs/experience-manager-core-components/using/developing/guidelines)
