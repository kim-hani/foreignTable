package OneTwo.SmartWaiting.domain.favorite.service;

import OneTwo.SmartWaiting.common.exception.BusinessException;
import OneTwo.SmartWaiting.common.exception.ErrorCode;
import OneTwo.SmartWaiting.domain.favorite.dto.requestDto.FavoriteRequestDto;
import OneTwo.SmartWaiting.domain.favorite.dto.responseDto.FavoriteResponseDto;
import OneTwo.SmartWaiting.domain.favorite.entity.Favorite;
import OneTwo.SmartWaiting.domain.favorite.repository.FavoriteRepository;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.store.enums.StoreCategory;
import OneTwo.SmartWaiting.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @InjectMocks
    private FavoriteService favoriteService;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private MemberRepository memberRepository;

    // ================= [ 즐겨찾기 추가 (Add Favorite) ] =================

    @Test
    @DisplayName("즐겨찾기 추가 성공")
    void addFavorite_Success() {
        // given
        String email = "test@gmail.com";
        Long storeId = 1L;
        FavoriteRequestDto requestDto = createFavoriteRequestDto(storeId);

        Member mockMember = createMockMember(10L, email, "tester");
        Store mockStore = createMockStore(storeId, "테스트 식당", StoreCategory.KOREAN);
        Favorite mockFavorite = createMockFavorite(1L, mockMember, mockStore);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        when(favoriteRepository.existsByMemberIdAndStoreId(mockMember.getId(), storeId)).thenReturn(false);
        when(favoriteRepository.save(any(Favorite.class))).thenReturn(mockFavorite);

        // when
        Long result = favoriteService.addFavorite(requestDto, email);

        // then
        assertThat(result).isNotNull();
        verify(favoriteRepository, times(1)).save(any(Favorite.class));
    }

    @Test
    @DisplayName("즐겨찾기 추가 실패 - 존재하지 않는 회원")
    void addFavorite_Fail_MemberNotFound() {
        // given
        String email = "nonexistent@gmail.com";
        Long storeId = 1L;
        FavoriteRequestDto requestDto = createFavoriteRequestDto(storeId);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> favoriteService.addFavorite(requestDto, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("즐겨찾기 추가 실패 - 존재하지 않는 식당")
    void addFavorite_Fail_StoreNotFound() {
        // given
        String email = "test@gmail.com";
        Long storeId = 1L;
        FavoriteRequestDto requestDto = createFavoriteRequestDto(storeId);
        Member mockMember = createMockMember(10L, email, "tester");

        lenient().when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        lenient().when(storeRepository.findById(storeId)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> favoriteService.addFavorite(requestDto, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    @DisplayName("즐겨찾기 추가 실패 - 이미 즐겨찾기 된 식당")
    void addFavorite_Fail_AlreadyFavorite() {
        // given
        String email = "test@gmail.com";
        Long storeId = 1L;
        FavoriteRequestDto requestDto = createFavoriteRequestDto(storeId);

        Member mockMember = createMockMember(10L, email, "tester");
        Store mockStore = createMockStore(storeId, "테스트 식당", StoreCategory.KOREAN);

        lenient().when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        lenient().when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        lenient().when(favoriteRepository.existsByMemberIdAndStoreId(mockMember.getId(), storeId)).thenReturn(true);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> favoriteService.addFavorite(requestDto, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_FAVORITE);
    }

    // ================= [ 즐겨찾기 삭제 (Remove Favorite) ] =================

    @Test
    @DisplayName("즐겨찾기 삭제 성공")
    void removeFavorite_Success() {
        // given
        String email = "test@gmail.com";
        Long storeId = 1L;
        FavoriteRequestDto requestDto = createFavoriteRequestDto(storeId);

        Member mockMember = createMockMember(10L, email, "tester");
        Store mockStore = createMockStore(storeId, "테스트 식당", StoreCategory.KOREAN);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        when(favoriteRepository.existsByMemberIdAndStoreId(mockMember.getId(), storeId)).thenReturn(true);

        // void 메서드이므로 별도의 when(...).thenReturn() 설정 없이 호출 여부만 검증
        // 기존 코드에서 뒤에 .thenReturn(true); 를 꼭 붙여주세요!
        when(favoriteRepository.deleteByMemberIdAndStoreId(mockMember.getId(), storeId)).thenReturn(true);
        // when
        favoriteService.removeFavorite(requestDto, email);

        // then
        verify(favoriteRepository, times(1)).deleteByMemberIdAndStoreId(mockMember.getId(), storeId);
    }

    @Test
    @DisplayName("즐겨찾기 삭제 실패 - 존재하지 않는 회원")
    void removeFavorite_Fail_MemberNotFound() {
        // given
        String email = "nonexistent@gmail.com";
        Long storeId = 1L;
        FavoriteRequestDto requestDto = createFavoriteRequestDto(storeId);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> favoriteService.removeFavorite(requestDto, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("즐겨찾기 삭제 실패 - 존재하지 않는 식당")
    void removeFavorite_Fail_StoreNotFound() {
        // given
        String email = "test@gmail.com";
        Long storeId = 1L;
        FavoriteRequestDto requestDto = createFavoriteRequestDto(storeId);
        Member mockMember = createMockMember(10L, email, "tester");

        lenient().when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        lenient().when(storeRepository.findById(storeId)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> favoriteService.removeFavorite(requestDto, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    @DisplayName("즐겨찾기 삭제 실패 - 즐겨찾기 목록에 없는 식당")
    void removeFavorite_Fail_FavoriteNotFound() {
        // given
        String email = "test@gmail.com";
        Long storeId = 1L;
        FavoriteRequestDto requestDto = createFavoriteRequestDto(storeId);

        Member mockMember = createMockMember(10L, email, "tester");
        Store mockStore = createMockStore(storeId, "테스트 식당", StoreCategory.KOREAN);

        lenient().when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        lenient().when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));
        lenient().when(favoriteRepository.existsByMemberIdAndStoreId(mockMember.getId(), storeId)).thenReturn(false);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> favoriteService.removeFavorite(requestDto, email));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FAVORITE_NOT_FOUND);
    }

    // ================= [ 내 즐겨찾기 목록 조회 (Get My Favorites) ] =================

    @Test
    @DisplayName("내 즐겨찾기 목록 조회 성공 - 목록이 비어있는 경우")
    void getMyFavorites_Success_EmptyList() {
        // given
        String email = "test@gmail.com";
        Pageable pageable = createPageable();
        Member mockMember = createMockMember(10L, email, "tester");
        Slice<Favorite> emptySlice = new SliceImpl<>(Collections.emptyList(), pageable, false);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(favoriteRepository.findAllByMemberId(mockMember.getId(), pageable)).thenReturn(emptySlice);

        // when
        Slice<FavoriteResponseDto> result = favoriteService.getMyFavorites(email, pageable);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.hasContent()).isFalse();
    }

    @Test
    @DisplayName("내 즐겨찾기 목록 조회 성공 - 목록이 존재하는 경우")
    void getMyFavorites_Success_NonEmptyList() {
        // given
        String email = "test@gmail.com";
        Pageable pageable = createPageable();
        Member mockMember = createMockMember(10L, email, "tester");
        Store mockStore1 = createMockStore(1L, "식당1", StoreCategory.KOREAN);
        Store mockStore2 = createMockStore(2L, "식당2", StoreCategory.JAPANESE);

        Favorite mockFavorite1 = createMockFavorite(100L, mockMember, mockStore1);
        Favorite mockFavorite2 = createMockFavorite(101L, mockMember, mockStore2);

        List<Favorite> favorites = List.of(mockFavorite1, mockFavorite2);
        Slice<Favorite> favoriteSlice = new SliceImpl<>(favorites, pageable, true);

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(mockMember));
        when(favoriteRepository.findAllByMemberId(mockMember.getId(), pageable)).thenReturn(favoriteSlice);

        // when
        Slice<FavoriteResponseDto> result = favoriteService.getMyFavorites(email, pageable);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).storeName()).isEqualTo("식당1");
        assertThat(result.getContent().get(1).storeName()).isEqualTo("식당2");
        assertThat(result.hasContent()).isTrue();
    }

    @Test
    @DisplayName("내 즐겨찾기 목록 조회 실패 - 존재하지 않는 회원")
    void getMyFavorites_Fail_MemberNotFound() {
        // given
        String email = "nonexistent@gmail.com";
        Pageable pageable = createPageable();

        when(memberRepository.findByEmail(email)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> favoriteService.getMyFavorites(email, pageable));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    // ================= [ Helper Methods ] =================

    private FavoriteRequestDto createFavoriteRequestDto(Long storeId) {
        return new FavoriteRequestDto(storeId);
    }

    private Member createMockMember(Long id, String email, String nickname) {
        Member member = mock(Member.class);
        lenient().when(member.getId()).thenReturn(id);
        lenient().when(member.getEmail()).thenReturn(email);
        lenient().when(member.getNickname()).thenReturn(nickname);
        lenient().when(member.getIsDeleted()).thenReturn(false);
        return member;
    }

    private Store createMockStore(Long id, String name, StoreCategory category) {
        Store store = mock(Store.class);
        lenient().when(store.getId()).thenReturn(id);
        lenient().when(store.getName()).thenReturn(name);
        lenient().when(store.getCategory()).thenReturn(category);
        lenient().when(store.getIsDeleted()).thenReturn(false);
        return store;
    }

    private Favorite createMockFavorite(Long id, Member member, Store store) {
        Favorite favorite = mock(Favorite.class);
        lenient().when(favorite.getId()).thenReturn(id);
        lenient().when(favorite.getMember()).thenReturn(member);
        lenient().when(favorite.getStore()).thenReturn(store);
        return favorite;
    }

    private Pageable createPageable() {
        return PageRequest.of(0, 10);
    }
}