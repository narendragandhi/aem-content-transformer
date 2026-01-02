package com.example.aemtransformer.controller;

import com.example.aemtransformer.agent.WordPressScraperAgent;
import com.example.aemtransformer.agent.ContentAnalyzerAgent;
import com.example.aemtransformer.agent.ComponentMapperAgent;
import com.example.aemtransformer.model.WordPressContent;
import com.example.aemtransformer.model.ContentAnalysis;
import com.example.aemtransformer.model.ComponentMapping;
import com.example.aemtransformer.model.AemPage;
import com.example.aemtransformer.service.AemOutputService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.nio.file.Path;
import com.example.aemtransformer.util.ParsedUrl;
import com.example.aemtransformer.util.MappingToPageConverter;

@RestController
@RequestMapping("/api/transform")
public class AemTransformController {
    @Autowired
    private WordPressScraperAgent wordPressScraperAgent;
    @Autowired
    private ContentAnalyzerAgent contentAnalyzerAgent;
    @Autowired
    private ComponentMapperAgent componentMapperAgent;
    @Autowired
    private AemOutputService aemOutputService;

    @GetMapping
    public String transform(@RequestParam String url) throws Exception {
        ParsedUrl parsed = ParsedUrl.parse(url);
        WordPressContent wpContent = wordPressScraperAgent.scrapeBySlug(parsed.baseUrl, parsed.slug, parsed.type);
        ContentAnalysis analysis = contentAnalyzerAgent.analyze(wpContent);
        List<ComponentMapping> mappings = componentMapperAgent.mapComponents(analysis);
        AemPage page = MappingToPageConverter.toAemPage(analysis, mappings);
        Path out = aemOutputService.writePage(page, parsed.slug);
        return "AEM page written to: " + out.toString();
    }
}
