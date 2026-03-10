package com.example.aemtransformer.service;

import com.example.aemtransformer.model.ContentBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HtmlParserServiceTest {

    private final HtmlParserService htmlParserService = new HtmlParserService();

    @Test
    void parseHtml_extractsCommonBlocks() {
        String html = """
                <h2>Heading</h2>
                <p>Paragraph</p>
                <ul><li>One</li><li>Two</li></ul>
                <figure>
                  <img src="a.jpg" alt="A"/>
                  <img src="b.jpg" alt="B"/>
                </figure>
                <iframe src="https://example.com/embed"></iframe>
                """;

        List<ContentBlock> blocks = htmlParserService.parseHtml(html);

        assertEquals(5, blocks.size());
        assertEquals(ContentBlock.BlockType.HEADING, blocks.get(0).getType());
        assertEquals(ContentBlock.BlockType.PARAGRAPH, blocks.get(1).getType());
        assertEquals(ContentBlock.BlockType.LIST, blocks.get(2).getType());
        assertEquals(ContentBlock.BlockType.GALLERY, blocks.get(3).getType());
        assertEquals(ContentBlock.BlockType.EMBED, blocks.get(4).getType());

        assertEquals(List.of("One", "Two"), blocks.get(2).getListItems());
        assertEquals(2, blocks.get(3).getChildren().size());
        assertEquals("https://example.com/embed", blocks.get(4).getContent());
    }
}
