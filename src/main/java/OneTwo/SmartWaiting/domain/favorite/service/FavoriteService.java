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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final StoreRepository storeRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Long addFavorite(FavoriteRequestDto requestDto) {
        if(favoriteRepository.existsByMemberIdAndStoreId(requestDto.memberId(), requestDto.storeId())) {
            throw new IllegalStateException("이미 즐겨찾기 한 식당입니다.");
        }

        Store store = storeRepository.findById(requestDto.storeId())
                .orElseThrow(() -> new IllegalArgumentException("식당을 찾을 수 없습니다."));

        Member member = memberRepository.findById(requestDto.memberId())
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        Favorite favorite = Favorite.builder()
                .store(store)
                .member(member)
                .build();

        return favoriteRepository.save(favorite).getId();
    }

    @Transactional
    public void removeFavorite(FavoriteRequestDto requestDto) {
        Store store = storeRepository.findById(requestDto.storeId())
                .orElseThrow(() -> new IllegalArgumentException("식당을 찾을 수 없습니다."));

        Member member = memberRepository.findById(requestDto.memberId())
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        if(!favoriteRepository.existsByMemberIdAndStoreId(requestDto.memberId(), requestDto.storeId())) {
            throw new IllegalStateException("즐겨찾기 내역이 존재하지 않습니다.");
        }

        favoriteRepository.deleteByMemberIdAndStoreId(requestDto.memberId(), requestDto.storeId());
    }

    public List<FavoriteResponseDto> getMyFavorites(Long memberId) {
        return favoriteRepository.findAllByMemberId(memberId).stream()
                .map(FavoriteResponseDto::from)
                .collect(Collectors.toList());
    }
}
