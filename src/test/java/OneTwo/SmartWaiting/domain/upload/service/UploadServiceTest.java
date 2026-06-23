package OneTwo.SmartWaiting.domain.upload.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @InjectMocks
    private UploadService uploadService;

    @Mock
    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(uploadService, "bucket", "test-bucket");
        ReflectionTestUtils.setField(uploadService, "region", "ap-northeast-2");
    }

    @Test
    @DisplayName("이미지 업로드 성공 - 유효한 jpeg 파일")
    void uploadReviewImages_Success() throws Exception {
        // given
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getContentType()).thenReturn("image/jpeg");
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[1024]));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // when
        List<String> urls = uploadService.uploadReviewImages(List.of(mockFile));

        // then
        assertThat(urls).hasSize(1);
        assertThat(urls.get(0)).startsWith("https://test-bucket.s3.ap-northeast-2.amazonaws.com/reviews/");
        assertThat(urls.get(0)).endsWith(".jpg");
    }

    @Test
    @DisplayName("이미지 업로드 실패 - 6장 초과")
    void uploadReviewImages_Fail_TooManyImages() {
        // given
        MultipartFile mockFile = mock(MultipartFile.class);
        List<MultipartFile> files = List.of(mockFile, mockFile, mockFile, mockFile, mockFile, mockFile);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> uploadService.uploadReviewImages(files));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_IMAGES);
    }

    @Test
    @DisplayName("이미지 업로드 실패 - 지원하지 않는 파일 형식")
    void uploadReviewImages_Fail_InvalidImageType() {
        // given
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getContentType()).thenReturn("image/gif");

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> uploadService.uploadReviewImages(List.of(mockFile)));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_IMAGE_TYPE);
    }

    @Test
    @DisplayName("이미지 업로드 실패 - 10MB 초과")
    void uploadReviewImages_Fail_ImageTooLarge() {
        // given
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getContentType()).thenReturn("image/png");
        when(mockFile.getSize()).thenReturn(11 * 1024 * 1024L);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> uploadService.uploadReviewImages(List.of(mockFile)));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_TOO_LARGE);
    }
}
