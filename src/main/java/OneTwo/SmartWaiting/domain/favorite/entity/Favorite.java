package OneTwo.SmartWaiting.domain.favorite.entity;

import OneTwo.SmartWaiting.common.domain.BaseEntity;
import OneTwo.SmartWaiting.domain.member.entity.Member;
import OneTwo.SmartWaiting.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "favorite",
        uniqueConstraints = {
                // 한 사람이 같은 가게를 두 번 이상 찜 할 수 없음
                @UniqueConstraint(
                        name = "uk_favorite_member_store",
                        columnNames = {"member_id", "store_id"}
                )
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Favorite extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;
}
