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
import OneTwo.SmartWaiting.domain.store.entity.MenuItemVo;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @InjectMocks
    private StoreService storeService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private StoreRepository storeRepository;

    @Test
    @DisplayName("식당 생성 성공 - 사장님 계정으로 식당을 생성한다.")
    void createStore_Success() {
        // given
        String email = "owner@gmail.com";
        StoreCreateRequestDto requestDto = createDefaultCreateRequestDto();
        Member mockOwner = createMockOwner(1L);
        Store mockStore = mock(Store.class);
        when(mockStore.getId()).thenReturn(10L);

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
        assertThatThrownBy(() -> storeService.createStore(requestDto, email))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("식당 생성 실패 - 사장님 권한이 아닌 회원")
    void createStore_Fail_AccessDenied_NotOwner() {
        // given
        String email = "user@gmail.com";
        StoreCreateRequestDto requestDto = createDefaultCreateRequestDto();
        Member mockUser = createMockUser(1L);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockUser));

        // when & then
        assertThatThrownBy(() -> storeService.createStore(requestDto, email))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("식당 단건 조회 성공")
    void getStore_Success() {
        // given
        Long storeId = 1L;
        Store mockStore = createMockStore(storeId, 1L, "테스트 식당", StoreCategory.KOREAN, 10, "010-1234-5678", 37.5665, 126.9780);
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
        assertThatThrownBy(() -> storeService.getStore(storeId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    @DisplayName("식당 정보 수정 성공 - 사장님 본인의 가게 정보를 수정한다.")
    void updateStore_Success() {
        // given
        Long storeId = 1L;
        String email = "owner@gmail.com";
        StoreUpdateRequestDto requestDto = createDefaultUpdateRequestDto("수정된 식당", "010-9876-5432", "JAPANESE", 15, 37.5000, 127.0000);

        Store mockStore = mock(Store.class);
        when(mockStore.getOwnerId()).thenReturn(1L);

        Member mockOwner = createMockOwner(1L);

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
        assertThatThrownBy(() -> storeService.updateStore(storeId, email, requestDto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    @DisplayName("식당 정보 수정 실패 - 본인 소유의 가게가 아님(권한 없음)")
    void updateStore_Fail_UnauthorizedOwner() {
        // given
        Long storeId = 1L;
        String email = "hacker@gmail.com";
        StoreUpdateRequestDto requestDto = createDefaultUpdateRequestDto();

        Store mockStore = mock(Store.class);
        when(mockStore.getOwnerId()).thenReturn(1L); // Store belongs to owner 1

        Member mockHacker = createMockHacker(2L); // Hacker member ID 2

        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockHacker));

        // when & then
        assertThatThrownBy(() -> storeService.updateStore(storeId, email, requestDto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED_STORE_OWNER);
    }

    @Test
    @DisplayName("식당 삭제 성공 - 사장님 본인의 가게를 삭제(soft delete)한다.")
    void deleteStore_Success() {
        // given
        Long storeId = 1L;
        String email = "owner@gmail.com";

        Store mockStore = mock(Store.class);
        when(mockStore.getOwnerId()).thenReturn(1L);

        Member mockOwner = createMockOwner(1L);

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
        assertThatThrownBy(() -> storeService.deleteStore(storeId, email))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    @DisplayName("식당 삭제 실패 - 본인 소유의 가게가 아님(권한 없음)")
    void deleteStore_Fail_UnauthorizedOwner() {
        // given
        Long storeId = 1L;
        String email = "hacker@gmail.com";

        Store mockStore = mock(Store.class);
        when(mockStore.getOwnerId()).thenReturn(1L); // Store belongs to owner 1

        Member mockHacker = createMockHacker(2L); // Hacker member ID 2

        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockHacker));

        // when & then
        assertThatThrownBy(() -> storeService.deleteStore(storeId, email))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED_STORE_OWNER);
    }

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

    private Member createMockOwner(Long id) {
        Member mockOwner = mock(Member.class);
        when(mockOwner.getId()).thenReturn(id);
        when(mockOwner.getRole()).thenReturn(UserRole.OWNER);
        return mockOwner;
    }

    private Member createMockUser(Long id) {
        Member mockUser = mock(Member.class);
        when(mockUser.getId()).thenReturn(id);
        when(mockUser.getRole()).thenReturn(UserRole.USER);
        return mockUser;
    }

    private Member createMockHacker(Long id) {
        Member mockHacker = mock(Member.class);
        when(mockHacker.getId()).thenReturn(id);
        return mockHacker;
    }

    private Store createMockStore(Long id, Long ownerId, String name, StoreCategory category, Integer averageWaiting, String phone, double latitude, double longitude) {
        Store mockStore = mock(Store.class);
        when(mockStore.getId()).thenReturn(id);
        when(mockStore.getOwnerId()).thenReturn(ownerId);
        when(mockStore.getName()).thenReturn(name);
        when(mockStore.getCategory()).thenReturn(category);
        when(mockStore.getAverageWaiting()).thenReturn(averageWaiting);
        when(mockStore.getPhone()).thenReturn(phone);
        when(mockStore.getBusinessHours()).thenReturn("09:00-21:00"); // 기본값 설정
        when(mockStore.getMenuItems()).thenReturn(Collections.emptyList()); // 기본값 설정

        GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
        Point mockPoint = gf.createPoint(new Coordinate(longitude, latitude));
        when(mockStore.getLocation()).thenReturn(mockPoint);
        return mockStore;
    }
}
