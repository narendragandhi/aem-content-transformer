package com.example.aemtransformer.service;

import com.example.aemtransformer.model.ContentBlock;
import com.example.aemtransformer.model.ContentBlock.BlockType;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for parsing WordPress HTML content into structured blocks.
 */
@Slf4j
@Service
public class HtmlParserService {

    /**
     * Parses HTML content into a list of content blocks.
     */
    public List<ContentBlock> parseHtml(String html) {
        List<ContentBlock> blocks = new ArrayList<>();
        Document doc = Jsoup.parseBodyFragment(html);
        Element body = doc.body();

        AtomicInteger orderCounter = new AtomicInteger(0);

        for (Element element : body.children()) {
            ContentBlock block = parseElement(element, orderCounter.getAndIncrement());
            if (block != null) {
                blocks.add(block);
            }
        }

        log.info("Parsed {} content blocks from HTML", blocks.size());
        return blocks;
    }

    private ContentBlock parseElement(Element element, int order) {
        String tagName = element.tagName().toLowerCase();

        return switch (tagName) {
            case "h1", "h2", "h3", "h4", "h5", "h6" -> parseHeading(element, order);
            case "p" -> parseParagraph(element, order);
            case "img" -> parseImage(element, order);
            case "figure" -> parseFigure(element, order);
            case "ul", "ol" -> parseList(element, order);
            case "blockquote" -> parseQuote(element, order);
            case "pre", "code" -> parseCode(element, order);
            case "table" -> parseTable(element, order);
            case "hr" -> parseSeparator(element, order);
            case "div" -> parseDiv(element, order);
            case "iframe", "embed", "video", "audio" -> parseEmbed(element, order);
            default -> parseUnknown(element, order);
        };
    }

    private ContentBlock parseHeading(Element element, int order) {
        int level = Integer.parseInt(element.tagName().substring(1));
        return ContentBlock.builder()
                .type(BlockType.HEADING)
                .content(element.text())
                .rawHtml(element.outerHtml())
                .order(order)
                .headingLevel(level)
                .attributes(extractAttributes(element))
                .build();
    }

    private ContentBlock parseParagraph(Element element, int order) {
        Elements images = element.select("img");
        if (!images.isEmpty() && element.text().trim().isEmpty()) {
            return parseImage(images.first(), order);
        }

        return ContentBlock.builder()
                .type(BlockType.PARAGRAPH)
                .content(element.html())
                .rawHtml(element.outerHtml())
                .order(order)
                .attributes(extractAttributes(element))
                .build();
    }

    private ContentBlock parseImage(Element element, int order) {
        return ContentBlock.builder()
                .type(BlockType.IMAGE)
                .content(element.attr("alt"))
                .rawHtml(element.outerHtml())
                .order(order)
                .imageUrl(element.attr("src"))
                .imageAlt(element.attr("alt"))
                .attributes(extractAttributes(element))
                .build();
    }

    private ContentBlock parseFigure(Element element, int order) {
        Element img = element.selectFirst("img");
        Element caption = element.selectFirst("figcaption");

        Elements gallery = element.select("img");
        if (gallery.size() > 1) {
            return parseGallery(element, order);
        }

        ContentBlock.ContentBlockBuilder builder = ContentBlock.builder()
                .type(BlockType.IMAGE)
                .rawHtml(element.outerHtml())
                .order(order)
                .attributes(extractAttributes(element));

        if (img != null) {
            builder.imageUrl(img.attr("src"))
                    .imageAlt(img.attr("alt"))
                    .content(img.attr("alt"));
        }

        if (caption != null) {
            builder.imageCaption(caption.text());
        }

        return builder.build();
    }

    private ContentBlock parseGallery(Element element, int order) {
        List<ContentBlock> children = new ArrayList<>();
        Elements images = element.select("img");

        int childOrder = 0;
        for (Element img : images) {
            children.add(parseImage(img, childOrder++));
        }

        return ContentBlock.builder()
                .type(BlockType.GALLERY)
                .rawHtml(element.outerHtml())
                .order(order)
                .children(children)
                .attributes(extractAttributes(element))
                .build();
    }

    private ContentBlock parseList(Element element, int order) {
        boolean isOrdered = "ol".equals(element.tagName().toLowerCase());
        List<String> items = new ArrayList<>();

        for (Element li : element.select("> li")) {
            items.add(li.html());
        }

        return ContentBlock.builder()
                .type(BlockType.LIST)
                .content(element.text())
                .rawHtml(element.outerHtml())
                .order(order)
                .listItems(items)
                .isOrdered(isOrdered)
                .attributes(extractAttributes(element))
                .build();
    }

    private ContentBlock parseQuote(Element element, int order) {
        return ContentBlock.builder()
                .type(BlockType.QUOTE)
                .content(element.html())
                .rawHtml(element.outerHtml())
                .order(order)
                .attributes(extractAttributes(element))
                .build();
    }

    private ContentBlock parseCode(Element element, int order) {
        return ContentBlock.builder()
                .type(BlockType.CODE)
                .content(element.text())
                .rawHtml(element.outerHtml())
                .order(order)
                .attributes(extractAttributes(element))
                .build();
    }

    private ContentBlock parseTable(Element element, int order) {
        return ContentBlock.builder()
                .type(BlockType.TABLE)
                .content(element.text())
                .rawHtml(element.outerHtml())
                .order(order)
                .attributes(extractAttributes(element))
                .build();
    }

    private ContentBlock parseSeparator(Element element, int order) {
        return ContentBlock.builder()
                .type(BlockType.SEPARATOR)
                .rawHtml(element.outerHtml())
                .order(order)
                .build();
    }

    private ContentBlock parseDiv(Element element, int order) {
        if (element.hasClass("wp-block-gallery") || element.hasClass("gallery")) {
            return parseGallery(element, order);
        }

        if (element.children().isEmpty() && !element.text().trim().isEmpty()) {
            return ContentBlock.builder()
                    .type(BlockType.PARAGRAPH)
                    .content(element.html())
                    .rawHtml(element.outerHtml())
                    .order(order)
                    .attributes(extractAttributes(element))
                    .build();
        }

        List<ContentBlock> children = new ArrayList<>();
        int childOrder = 0;
        for (Element child : element.children()) {
            ContentBlock childBlock = parseElement(child, childOrder++);
            if (childBlock != null) {
                children.add(childBlock);
            }
        }

        if (children.size() == 1) {
            return children.get(0);
        }

        return ContentBlock.builder()
                .type(BlockType.UNKNOWN)
                .rawHtml(element.outerHtml())
                .order(order)
                .children(children)
                .attributes(extractAttributes(element))
                .build();
    }

    private ContentBlock parseEmbed(Element element, int order) {
        Map<String, String> attrs = extractAttributes(element);
        attrs.put("embedType", element.tagName().toLowerCase());

        return ContentBlock.builder()
                .type(BlockType.EMBED)
                .content(element.attr("src"))
                .rawHtml(element.outerHtml())
                .order(order)
                .attributes(attrs)
                .build();
    }

    private ContentBlock parseUnknown(Element element, int order) {
        if (element.text().trim().isEmpty() && element.children().isEmpty()) {
            return null;
        }

        return ContentBlock.builder()
                .type(BlockType.UNKNOWN)
                .content(element.html())
                .rawHtml(element.outerHtml())
                .order(order)
                .attributes(extractAttributes(element))
                .build();
    }

    private Map<String, String> extractAttributes(Element element) {
        Map<String, String> attrs = new HashMap<>();

        if (element.hasAttr("id")) {
            attrs.put("id", element.id());
        }
        if (element.hasAttr("class")) {
            attrs.put("class", element.className());
        }
        if (element.hasAttr("style")) {
            attrs.put("style", element.attr("style"));
        }

        return attrs;
    }
}
