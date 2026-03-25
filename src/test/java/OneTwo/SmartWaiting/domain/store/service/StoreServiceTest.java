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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
        StoreCreateRequestDto requestDto = StoreCreateRequestDto.builder()
                .name("테스트 식당")
                .category("KOREAN")
                .averageWaiting(10)
                .phone("010-1234-5678")
                .latitude(37.5665)
                .longitude(126.9780)
                .businessHours("09:00-21:00")
                .menuItems(List.of())
                .build();

        Member mockOwner = mock(Member.class);
        when(mockOwner.getId()).thenReturn(1L);
        when(mockOwner.getRole()).thenReturn(UserRole.OWNER);

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
        StoreCreateRequestDto requestDto = StoreCreateRequestDto.builder()
                .name("테스트 식당")
                .category("KOREAN")
                .averageWaiting(10)
                .phone("010-1234-5678")
                .latitude(37.5665)
                .longitude(126.9780)
                .businessHours("09:00-21:00")
                .menuItems(List.of())
                .build();

        when(memberRepository.findByEmail(email)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = Assertions.assertThrows(BusinessException.class, () -> storeService.createStore(requestDto, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("식당 생성 실패 - 사장님 권한이 아닌 회원")
    void createStore_Fail_AccessDenied_NotOwner() {
        // given
        String email = "user@gmail.com";
        StoreCreateRequestDto requestDto = StoreCreateRequestDto.builder()
                .name("테스트 식당")
                .category("KOREAN")
                .averageWaiting(10)
                .phone("010-1234-5678")
                .latitude(37.5665)
                .longitude(126.9780)
                .businessHours("09:00-21:00")
                .menuItems(List.of())
                .build();

        Member mockUser = mock(Member.class);
        when(mockUser.getRole()).thenReturn(UserRole.USER);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockUser));

        // when & then
        BusinessException exception = Assertions.assertThrows(BusinessException.class, () -> storeService.createStore(requestDto, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("식당 단건 조회 성공")
    void getStore_Success() {
        // given
        Long storeId = 1L;
        Store mockStore = mock(Store.class);
        when(mockStore.getId()).thenReturn(storeId);
        when(mockStore.getName()).thenReturn("테스트 식당");
        when(mockStore.getCategory()).thenReturn(StoreCategory.KOREAN);
        when(mockStore.getAverageWaiting()).thenReturn(10);
        when(mockStore.getPhone()).thenReturn("010-1234-5678");
        when(mockStore.getBusinessHours()).thenReturn("09:00-21:00");
        when(mockStore.getMenuItems()).thenReturn(List.of());

        GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
        Point mockPoint = gf.createPoint(new Coordinate(126.9780, 37.5665));
        when(mockStore.getLocation()).thenReturn(mockPoint);


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
        BusinessException exception = Assertions.assertThrows(BusinessException.class, () -> storeService.getStore(storeId));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    @DisplayName("식당 정보 수정 성공 - 사장님 본인의 가게 정보를 수정한다.")
    void updateStore_Success() {
        // given
        Long storeId = 1L;
        String email = "owner@gmail.com";
        StoreUpdateRequestDto requestDto = StoreUpdateRequestDto.builder()
                .name("수정된 식당")
                .phone("010-9876-5432")
                .category("JAPANESE")
                .averageWaiting(15)
                .latitude(37.5000)
                .longitude(127.0000)
                .businessHours("10:00-22:00")
                .menuItems(List.of())
                .build();

        Store mockStore = mock(Store.class);
        when(mockStore.getOwnerId()).thenReturn(1L);

        Member mockOwner = mock(Member.class);
        when(mockOwner.getId()).thenReturn(1L);

        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockOwner));

        // when
        storeService.updateStore(storeId, email, requestDto);

        // then
        verify(mockStore, times(1)).updateInfo(
                eq("수정된 식당"),
                eq("010-9876-5432"),
                eq(StoreCategory.JAPANESE),
                eq(15),
                any(Point.class),
                eq("10:00-22:00"),
                eq(List.of())
        );
    }

    @Test
    @DisplayName("식당 정보 수정 실패 - 존재하지 않는 식당")
    void updateStore_Fail_StoreNotFound() {
        // given
        Long storeId = 1L;
        String email = "owner@gmail.com";
        StoreUpdateRequestDto requestDto = StoreUpdateRequestDto.builder().build(); // 내용 무관

        when(storeRepository.findById(storeId)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = Assertions.assertThrows(BusinessException.class, () -> storeService.updateStore(storeId, email, requestDto));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    @DisplayName("식당 정보 수정 실패 - 본인 소유의 가게가 아님(권한 없음)")
    void updateStore_Fail_UnauthorizedOwner() {
        // given
        Long storeId = 1L;
        String email = "hacker@gmail.com";
        StoreUpdateRequestDto requestDto = StoreUpdateRequestDto.builder().build(); // 내용 무관

        Store mockStore = mock(Store.class);
        when(mockStore.getOwnerId()).thenReturn(1L); // Store belongs to owner 1

        Member mockHacker = mock(Member.class);
        when(mockHacker.getId()).thenReturn(2L); // Hacker member ID 2

        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockHacker));

        // when & then
        BusinessException exception = Assertions.assertThrows(BusinessException.class, () -> storeService.updateStore(storeId, email, requestDto));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED_STORE_OWNER);
    }

    @Test
    @DisplayName("식당 삭제 성공 - 사장님 본인의 가게를 삭제(soft delete)한다.")
    void deleteStore_Success() {
        // given
        Long storeId = 1L;
        String email = "owner@gmail.com";

        Store mockStore = mock(Store.class);
        when(mockStore.getOwnerId()).thenReturn(1L);

        Member mockOwner = mock(Member.class);
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
        BusinessException exception = Assertions.assertThrows(BusinessException.class, () -> storeService.deleteStore(storeId, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    @DisplayName("식당 삭제 실패 - 본인 소유의 가게가 아님(권한 없음)")
    void deleteStore_Fail_UnauthorizedOwner() {
        // given
        Long storeId = 1L;
        String email = "hacker@gmail.com";

        Store mockStore = mock(Store.class);
        when(mockStore.getOwnerId()).thenReturn(1L); // Store belongs to owner 1

        Member mockHacker = mock(Member.class);
        when(mockHacker.getId()).thenReturn(2L); // Hacker member ID 2

        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockHacker));

        // when & then
        BusinessException exception = Assertions.assertThrows(BusinessException.class, () -> storeService.deleteStore(storeId, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED_STORE_OWNER);
    }
}
