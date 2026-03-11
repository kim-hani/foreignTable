package OneTwo.SmartWaiting.domain.favorite.service;

import OneTwo.SmartWaiting.domain.favorite.dto.requestDto.FavoriteRequestDto;
import OneTwo.SmartWaiting.domain.favorite.dto.responseDto.FavoriteResponseDto;
import OneTwo.SmartWaiting.domain.favorite.entity.Favorite;
import OneTwo.SmartWaiting.domain.favorite.repository.FavoriteRepository;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.member.repository.MemberRepository;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import OneTwo.SmartWaiting.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final StoreRepository storeRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Long addFavorite(FavoriteRequestDto requestDto, String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        if (favoriteRepository.existsByMemberIdAndStoreId(member.getId(), requestDto.storeId())) {
            throw new IllegalStateException("이미 즐겨찾기 한 식당입니다.");
        }

        Store store = storeRepository.findById(requestDto.storeId())
                .orElseThrow(() -> new IllegalArgumentException("식당을 찾을 수 없습니다."));

        Favorite favorite = Favorite.builder()
                .store(store)
                .member(member)
                .build();

        return favoriteRepository.save(favorite).getId();
    }

    @Transactional
    public void removeFavorite(FavoriteRequestDto requestDto, String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        Store store = storeRepository.findById(requestDto.storeId())
                .orElseThrow(() -> new IllegalArgumentException("식당을 찾을 수 없습니다."));

        if (!favoriteRepository.existsByMemberIdAndStoreId(member.getId(), store.getId())) {
            throw new IllegalStateException("즐겨찾기 내역이 존재하지 않습니다.");
        }

        favoriteRepository.deleteByMemberIdAndStoreId(member.getId(), requestDto.storeId());
    }

    public Slice<FavoriteResponseDto> getMyFavorites(String email, Pageable pageable) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        return favoriteRepository.findAllByMemberId(member.getId(),pageable)
                .map(FavoriteResponseDto::from);
    }
}
