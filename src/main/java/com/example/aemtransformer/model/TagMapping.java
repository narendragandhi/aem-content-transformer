package com.example.aemtransformer.model;

import java.io.Serializable;

public record TagMapping(String tagId, String title, String path) implements Serializable {}
