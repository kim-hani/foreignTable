package OneTwo.SmartWaiting.domain.upload.controller;

import OneTwo.SmartWaiting.domain.upload.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "08. 업로드(Upload) API", description = "이미지 업로드 기능")
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @Operation(summary = "리뷰 이미지 업로드", description = "리뷰에 첨부할 이미지를 S3에 업로드합니다. 최대 5장, 장당 10MB, jpg/png/webp만 허용합니다.")
    @PostMapping(value = "/review-image", consumes = "multipart/form-data")
    public ResponseEntity<List<String>> uploadReviewImages(
            @RequestParam("files") List<MultipartFile> files) {
        return ResponseEntity.ok(uploadService.uploadReviewImages(files));
    }
}
