package OneTwo.SmartWaiting.domain.store.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.enums.UserRole;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import OneTwo.SmartWaiting.domain.store.dto.requestDto.StoreCreateRequestDto;
import OneTwo.SmartWaiting.domain.store.dto.requestDto.StoreUpdateRequestDto;
import OneTwo.SmartWaiting.domain.store.dto.responseDto.StoreResponseDto;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.store.enums.StoreCategory;
import OneTwo.SmartWaiting.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @InjectMocks
    private StoreService storeService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private StoreRepository storeRepository;

    // ================= [ 식당 생성 (Create) ] =================

    @Test
    @DisplayName("식당 생성 성공 - 사장님 계정으로 식당을 생성한다.")
    void createStore_Success() {
        // given
        String email = "owner@gmail.com";
        StoreCreateRequestDto requestDto = createDefaultCreateRequestDto();

        Member mockOwner = mock(Member.class);
        lenient().when(mockOwner.getIsDeleted()).thenReturn(false);
        when(mockOwner.getId()).thenReturn(1L);
        when(mockOwner.getRole()).thenReturn(UserRole.OWNER);

        Store mockStore = mock(Store.class);
        lenient().when(mockStore.getId()).thenReturn(10L);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockOwner));
        when(storeRepository.save(any(Store.class))).thenReturn(mockStore);

        // when
        Long resultId = storeService.createStore(requestDto, email);

        // then
        assertThat(resultId).isEqualTo(10L);
        verify(storeRepository, times(1)).save(any(Store.class));
    }

    @Test
    @DisplayName("식당 생성 성공 - averageWaiting이 null일 때 기본값(10)으로 분기를 탄다 (Branch 커버리지)")
    void createStore_Success_DefaultWaiting() {
        // given
        String email = "owner@gmail.com";
        // averageWaiting을 null로 세팅하여 삼항 연산자 분기 테스트
        StoreCreateRequestDto requestDto = new StoreCreateRequestDto(
                "테스트 식당", StoreCategory.KOREAN, "010-1234-5678", 37.5665, 126.9780,
                null, Map.of("월", "09:00-21:00"), Collections.emptyList()
        );

        Member mockOwner = mock(Member.class);
        when(mockOwner.getId()).thenReturn(1L);
        when(mockOwner.getRole()).thenReturn(UserRole.OWNER);

        Store mockStore = mock(Store.class);
        lenient().when(mockStore.getId()).thenReturn(10L);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockOwner));
        when(storeRepository.save(any(Store.class))).thenReturn(mockStore);

        // when
        Long resultId = storeService.createStore(requestDto, email);

        // then
        assertThat(resultId).isEqualTo(10L);
        verify(storeRepository, times(1)).save(any(Store.class));
    }

    @Test
    @DisplayName("식당 생성 실패 - 존재하지 않는 회원")
    void createStore_Fail_MemberNotFound() {
        // given
        String email = "nonexistent@gmail.com";
        StoreCreateRequestDto requestDto = createDefaultCreateRequestDto();

        when(memberRepository.findByEmail(email)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> storeService.createStore(requestDto, email));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("식당 생성 실패 - 사장님 권한이 아닌 회원")
    void createStore_Fail_AccessDenied_NotOwner() {
        // given
        String email = "user@gmail.com";
        StoreCreateRequestDto requestDto = createDefaultCreateRequestDto();

        Member mockUser = mock(Member.class);
        lenient().when(mockUser.getIsDeleted()).thenReturn(false);
        when(mockUser.getRole()).thenReturn(UserRole.USER);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockUser));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> storeService.createStore(requestDto, email));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    // ================= [ 식당 조회 (Read) ] =================

    @Test
    @DisplayName("식당 단건 조회 성공")
    void getStore_Success() {
        // given
        Long storeId = 1L;
        Store mockStore = mock(Store.class);

        lenient().when(mockStore.getIsDeleted()).thenReturn(false);
        lenient().when(mockStore.getId()).thenReturn(storeId);
        lenient().when(mockStore.getName()).thenReturn("테스트 식당");
        lenient().when(mockStore.getCategory()).thenReturn(StoreCategory.KOREAN);
        lenient().when(mockStore.getAverageWaiting()).thenReturn(10);
        lenient().when(mockStore.getPhone()).thenReturn("010-1234-5678");
        lenient().when(mockStore.getBusinessHours()).thenReturn(Map.of("월", "09:00-21:00"));
        lenient().when(mockStore.getMenuItems()).thenReturn(Collections.emptyList());

        GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
        Point mockPoint = gf.createPoint(new Coordinate(126.9780, 37.5665));
        lenient().when(mockStore.getLocation()).thenReturn(mockPoint);

        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));

        // when
        StoreResponseDto result = storeService.getStore(storeId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.storeId()).isEqualTo(storeId);
        assertThat(result.name()).isEqualTo("테스트 식당");
    }

    @Test
    @DisplayName("식당 단건 조회 실패 - 존재하지 않는 식당")
    void getStore_Fail_NotFound() {
        // given
        Long storeId = 1L;
        when(storeRepository.findById(storeId)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> storeService.getStore(storeId));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    // ================= [ 식당 검색 (Search - Branch Coverage) ] =================

    @Test
    @DisplayName("반경 내 식당 검색 성공")
    void searchStoresAround_Success() {
        // given
        double lat = 37.5665;
        double lon = 126.9780;
        double radius = 1000.0;

        when(storeRepository.findStoresWithinRadius(lat, lon, radius)).thenReturn(Collections.emptyList());

        // when
        List<StoreResponseDto> result = storeService.searchStoresAround(lat, lon, radius);

        // then
        assertThat(result).isEmpty();
        verify(storeRepository, times(1)).findStoresWithinRadius(lat, lon, radius);
    }

    @Test
    @DisplayName("식당 카테고리로 검색 성공 (카테고리 있음 - Branch 커버리지)")
    void searchStores_WithCategory_Success() {
        // given
        String keyword = "식당";
        String category = "KOREAN"; // 카테고리 있음 분기
        Pageable pageable = PageRequest.of(0, 10);
        Slice<Store> mockSlice = new SliceImpl<>(Collections.emptyList());

        when(storeRepository.searchStoresByNameAndCategory(eq(keyword), any(StoreCategory.class), eq(pageable)))
                .thenReturn(mockSlice);

        // when
        Slice<StoreResponseDto> result = storeService.searchStores(keyword, category, pageable);

        // then
        assertThat(result).isNotNull();
        verify(storeRepository, times(1)).searchStoresByNameAndCategory(eq(keyword), any(StoreCategory.class), eq(pageable));
    }

    @Test
    @DisplayName("식당 카테고리 없이 검색 성공 (카테고리 없음 - Branch 커버리지)")
    void searchStores_WithoutCategory_Success() {
        // given
        String keyword = "식당";
        String category = null; // 카테고리 없음 분기
        Pageable pageable = PageRequest.of(0, 10);
        Slice<Store> mockSlice = new SliceImpl<>(Collections.emptyList());

        when(storeRepository.searchStoresByNameAndCategory(eq(keyword), isNull(), eq(pageable)))
                .thenReturn(mockSlice);

        // when
        Slice<StoreResponseDto> result = storeService.searchStores(keyword, category, pageable);

        // then
        assertThat(result).isNotNull();
        verify(storeRepository, times(1)).searchStoresByNameAndCategory(eq(keyword), isNull(), eq(pageable));
    }

    // ================= [ 식당 수정 (Update) ] =================

    @Test
    @DisplayName("식당 정보 수정 성공 - 사장님 본인의 가게 정보를 수정한다.")
    void updateStore_Success() {
        // given
        Long storeId = 1L;
        String email = "owner@gmail.com";
        StoreUpdateRequestDto requestDto = createDefaultUpdateRequestDto("수정된 식당", "010-9876-5432", "JAPANESE", 15, 37.5000, 127.0000);

        Store mockStore = mock(Store.class);
        lenient().when(mockStore.getIsDeleted()).thenReturn(false);
        lenient().when(mockStore.getOwnerId()).thenReturn(1L);

        Member mockOwner = mock(Member.class);
        lenient().when(mockOwner.getIsDeleted()).thenReturn(false);
        when(mockOwner.getId()).thenReturn(1L);

        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockOwner));

        // when
        storeService.updateStore(storeId, email, requestDto);

        // then
        verify(mockStore, times(1)).updateInfo(
                eq(requestDto.name()),
                eq(requestDto.phone()),
                eq(StoreCategory.from(requestDto.category())),
                eq(requestDto.averageWaiting()),
                any(Point.class),
                eq(requestDto.businessHours()),
                eq(requestDto.menuItems())
        );
    }

    @Test
    @DisplayName("식당 정보 수정 실패 - 존재하지 않는 식당")
    void updateStore_Fail_StoreNotFound() {
        // given
        Long storeId = 1L;
        String email = "owner@gmail.com";
        StoreUpdateRequestDto requestDto = createDefaultUpdateRequestDto();

        when(storeRepository.findById(storeId)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> storeService.updateStore(storeId, email, requestDto));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    @DisplayName("식당 정보 수정 실패 - 본인 소유의 가게가 아님(권한 없음)")
    void updateStore_Fail_UnauthorizedOwner() {
        // given
        Long storeId = 1L;
        String email = "hacker@gmail.com";
        StoreUpdateRequestDto requestDto = createDefaultUpdateRequestDto();

        Store mockStore = mock(Store.class);
        lenient().when(mockStore.getIsDeleted()).thenReturn(false);
        lenient().when(mockStore.getOwnerId()).thenReturn(1L); // 소유자는 1번

        Member mockHacker = mock(Member.class);
        lenient().when(mockHacker.getIsDeleted()).thenReturn(false);
        when(mockHacker.getId()).thenReturn(2L); // 해커는 2번

        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockHacker));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> storeService.updateStore(storeId, email, requestDto));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED_STORE_OWNER);
    }

    // ================= [ 식당 삭제 (Delete) ] =================

    @Test
    @DisplayName("식당 삭제 성공 - 사장님 본인의 가게를 삭제(soft delete)한다.")
    void deleteStore_Success() {
        // given
        Long storeId = 1L;
        String email = "owner@gmail.com";

        Store mockStore = mock(Store.class);
        lenient().when(mockStore.getIsDeleted()).thenReturn(false);
        lenient().when(mockStore.getOwnerId()).thenReturn(1L);

        Member mockOwner = mock(Member.class);
        lenient().when(mockOwner.getIsDeleted()).thenReturn(false);
        when(mockOwner.getId()).thenReturn(1L);

        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockOwner));

        // when
        storeService.deleteStore(storeId, email);

        // then
        verify(mockStore, times(1)).softDelete();
    }

    @Test
    @DisplayName("식당 삭제 실패 - 존재하지 않는 식당")
    void deleteStore_Fail_StoreNotFound() {
        // given
        Long storeId = 1L;
        String email = "owner@gmail.com";

        when(storeRepository.findById(storeId)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> storeService.deleteStore(storeId, email));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    @DisplayName("식당 삭제 실패 - 본인 소유의 가게가 아님(권한 없음)")
    void deleteStore_Fail_UnauthorizedOwner() {
        // given
        Long storeId = 1L;
        String email = "hacker@gmail.com";

        Store mockStore = mock(Store.class);
        lenient().when(mockStore.getIsDeleted()).thenReturn(false);
        when(mockStore.getOwnerId()).thenReturn(1L); // 소유자는 1번

        Member mockHacker = mock(Member.class);
        lenient().when(mockHacker.getIsDeleted()).thenReturn(false);
        when(mockHacker.getId()).thenReturn(2L); // 해커는 2번

        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockHacker));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> storeService.deleteStore(storeId, email));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED_STORE_OWNER);
    }

    // ================= [ Helper Methods ] =================

    private StoreCreateRequestDto createDefaultCreateRequestDto() {
        return new StoreCreateRequestDto(
                "테스트 식당",
                StoreCategory.KOREAN,
                "010-1234-5678",
                37.5665,
                126.9780,
                10,
                Map.of("월", "09:00-21:00"),
                Collections.emptyList()
        );
    }

    private StoreUpdateRequestDto createDefaultUpdateRequestDto() {
        return createDefaultUpdateRequestDto("수정된 식당", "010-9876-5432", "JAPANESE", 15, 37.5000, 127.0000);
    }

    private StoreUpdateRequestDto createDefaultUpdateRequestDto(String name, String phone, String category, Integer averageWaiting, Double latitude, Double longitude) {
        return new StoreUpdateRequestDto(
                name,
                category,
                phone,
                averageWaiting,
                latitude,
                longitude,
                Map.of("화", "10:00-22:00"),
                Collections.emptyList()
        );
    }
}