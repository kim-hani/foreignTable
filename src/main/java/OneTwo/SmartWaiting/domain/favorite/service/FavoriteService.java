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
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (favoriteRepository.existsByMemberIdAndStoreId(member.getId(), requestDto.storeId())) {
            throw new BusinessException(ErrorCode.ALREADY_FAVORITE);
        }

        Store store = storeRepository.findById(requestDto.storeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        Favorite favorite = Favorite.builder()
                .store(store)
                .member(member)
                .build();

        return favoriteRepository.save(favorite).getId();
    }

    @Transactional
    public void removeFavorite(FavoriteRequestDto requestDto, String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Store store = storeRepository.findById(requestDto.storeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        if (!favoriteRepository.existsByMemberIdAndStoreId(member.getId(), store.getId())) {
            throw new BusinessException(ErrorCode.FAVORITE_NOT_FOUND);
        }

        favoriteRepository.deleteByMemberIdAndStoreId(member.getId(), requestDto.storeId());
    }

    public Slice<FavoriteResponseDto> getMyFavorites(String email, Pageable pageable) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        return favoriteRepository.findAllByMemberId(member.getId(),pageable)
                .map(FavoriteResponseDto::from);
    }
}
