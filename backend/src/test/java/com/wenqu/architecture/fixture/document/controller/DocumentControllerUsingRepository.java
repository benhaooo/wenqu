package com.wenqu.architecture.fixture.document.controller;

import com.wenqu.architecture.fixture.document.repository.DocumentRepository;

public class DocumentControllerUsingRepository {
    void run(DocumentRepository repository) {
        repository.find();
    }
}
