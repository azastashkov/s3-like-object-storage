package com.example.objectstorage.api;

import java.util.List;

public record MultipartUploadDetail(
        MultipartUpload upload,
        List<MultipartPart> parts
) {}
